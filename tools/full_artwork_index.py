#!/usr/bin/env python3
"""Full, resumable, printing-level offline artwork fingerprint builder.

Use against the already downloaded Scryfall native printing-catalog.dat.
No printing cap; every English paper printing and available face is inventoried.
Image downloads are for the build machine only; phones receive compressed vectors.

  python tools/full_artwork_index.py --catalog app/src/main/assets/printing-catalog.dat \
    --output shard-0.sqlite --shard 0 --shards 8

Each shard is resumable; a previous merged database can seed unchanged rows.
"""
import argparse
import concurrent.futures
import gzip
import hashlib
import io
import json
import os
import sqlite3
import tempfile
import time
from pathlib import Path
from PIL import Image, ImageOps

SCHEMA = 1
REGIONS = {
    'art': (.09,.15,.91,.49),
    'wide': (.07,.12,.93,.77),
    'title': (.07,.025,.93,.155),
    'type': (.07,.60,.93,.73),
    'text': (.075,.72,.925,.90),
    'footer': (.04,.905,.96,.985),
}
FIELDS = tuple(REGIONS)


def normalize_name(value):
    import re, unicodedata
    text = ''.join(x for x in unicodedata.normalize('NFKD', str(value or '').casefold()) if not unicodedata.combining(x))
    return re.sub(r'[^a-z0-9]+',' ',text).strip()


def treatment(card):
    effects = {str(e).lower() for e in (card.get('frame_effects') or [])}
    if card.get('full_art') or card.get('textless') or card.get('border_color') == 'borderless':return 'fullart'
    if 'showcase' in effects or 'extendedart' in effects or 'inverted' in effects:return 'showcase'
    return 'standard'


def valid_url(url):
    from urllib.parse import urlsplit
    try:
        parsed = urlsplit(url)
        return parsed.scheme == 'https' and parsed.hostname == 'cards.scryfall.io' and not parsed.username and not parsed.password
    except (ValueError, TypeError):return False


def photo_regions(image):
    image = ImageOps.exif_transpose(image).convert('RGB')
    w,h=image.size
    if w<80 or h<110:raise ValueError('card artwork reference too small')
    out={}
    for key,(x0,y0,x1,y1) in REGIONS.items():
        crop=image.crop((round(w*x0),round(h*y0),round(w*x1),round(h*y1)))
        gray=crop.convert('L').resize((8,8),Image.Resampling.BILINEAR)
        raw=list(gray.getdata());mean=sum(raw)/64
        std=max(16,(sum((p-mean)**2 for p in raw)/64)**.5)
        out[key]=bytes(max(0,min(255,round(128+(p-mean)*48/std))) for p in raw)
    # Preserve normalized COLOR in addition to luminance. Two showcase arts can
    # have very similar brightness but visibly different hues and treatment.
    ax0,ay0,ax1,ay1=REGIONS['art']
    art=image.crop((round(w*ax0),round(h*ay0),round(w*ax1),round(h*ay1)))
    pixels=list(art.resize((8,8),Image.Resampling.BILINEAR).getdata())
    channels=[[pixel[c] for pixel in pixels] for c in range(3)]
    means=[sum(v)/64 for v in channels]
    spreads=[max(20,(sum((v-means[c])**2 for v in channels[c])/64)**.5) for c in range(3)]
    out['artcolor']=bytes(max(0,min(255,round(128+(channels[c][i]-means[c])*48/spreads[c])))
       for i in range(64) for c in range(3))
    # 64-bit difference hash of the ART crop, retained for SQLite's band-index.
    a=REGIONS['art']
    patch=image.crop((round(w*a[0]),round(h*a[1]),round(w*a[2]),round(h*a[3])))
    values=list(patch.convert('L').resize((9,8),Image.Resampling.BILINEAR).getdata())
    bits=0
    for y in range(8):
        for x in range(8):
            if values[9*y+x]>values[9*y+x+1]:bits|=1<<(8*y+x)
    return out, f'{bits:016x}'


def create_schema(conn):
    conn.executescript('''PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;
    CREATE TABLE IF NOT EXISTS artwork (
      printingId TEXT NOT NULL, face INTEGER NOT NULL,
      nameKey TEXT NOT NULL, setCode TEXT NOT NULL, collectorKey TEXT NOT NULL,
      illustrationId TEXT, treatment TEXT NOT NULL, url TEXT NOT NULL,
      art BLOB NOT NULL, artcolor BLOB NOT NULL, wide BLOB NOT NULL, title BLOB NOT NULL,
      type BLOB NOT NULL, text BLOB NOT NULL, footer BLOB NOT NULL,
      dhash TEXT NOT NULL, PRIMARY KEY(printingId,face));
    CREATE TABLE IF NOT EXISTS unavailable (
      printingId TEXT NOT NULL, face INTEGER NOT NULL, url TEXT NOT NULL,
      detail TEXT NOT NULL, PRIMARY KEY(printingId,face));
    CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
    ''')


def final_indexes(db):
    db.executescript('''CREATE INDEX IF NOT EXISTS art_by_name ON artwork(nameKey);
    CREATE INDEX IF NOT EXISTS art_by_set_collector ON artwork(setCode,collectorKey);
    CREATE INDEX IF NOT EXISTS art_by_illustration ON artwork(illustrationId);
    CREATE TABLE IF NOT EXISTS art_band (
      band INTEGER NOT NULL, code INTEGER NOT NULL,
      printingId TEXT NOT NULL, face INTEGER NOT NULL,
      PRIMARY KEY(band,code,printingId,face));
    CREATE INDEX IF NOT EXISTS art_band_search ON art_band(band,code);
    DELETE FROM art_band;''')
    for (pid,face,hashhex) in db.execute('SELECT printingId,face,dhash FROM artwork'):
        h=int(hashhex,16)
        db.executemany('INSERT INTO art_band VALUES(?,?,?,?)',[(i,(h>>(8*i))&255,pid,face) for i in range(8)])
    db.commit()


def iter_catalog(path,shard=0,shards=1):
    # Existing compressed SQLite printing catalog is much smaller than bulk JSON.
    with tempfile.TemporaryDirectory(prefix='mv-printings-') as temp:
        full=Path(temp)/'catalog.sqlite'
        with gzip.open(path,'rb') as inp,full.open('wb') as out:
            while data:=inp.read(1<<20):out.write(data)
        con=sqlite3.connect(f'file:{full}?mode=ro',uri=True)
        try:
            for (raw,) in con.execute('SELECT data FROM printings'):
                obj=json.loads(raw)
                if obj.get('digital') or (obj.get('lang') or 'en')!='en':continue
                pid=obj.get('id','')
                if not pid or (int.from_bytes(hashlib.sha256(pid.encode()).digest()[:8],'big')%shards)!=shard:continue
                uri=obj.get('image_normal') or ''
                yield obj,0,uri,obj.get('illustration_id') or ''
                back=obj.get('image_normal_back') or ''
                if back:yield obj,1,back,obj.get('illustration_id_back') or ''
        finally:con.close()


def fetch_image(url,cache):
    import requests
    key=hashlib.sha256(url.encode()).hexdigest()
    path=Path(cache)/key[:2]/(key+'.img')
    if path.is_file():return path.read_bytes()
    path.parent.mkdir(parents=True,exist_ok=True)
    for attempt in range(3):
        try:
            r=requests.get(url,timeout=(12,35),headers={'User-Agent':'ManaVault full offline artwork index (personal MTG collection tool)'},allow_redirects=False)
            if r.status_code in (429,500,502,503,504):
                time.sleep((attempt+1)*1.5);continue
            r.raise_for_status()
            if int(r.headers.get('Content-Length','0') or 0)>3_000_000 or len(r.content)>3_000_000:
                raise ValueError('Oversized reference')
            if not r.content:raise ValueError('Empty reference')
            path.write_bytes(r.content)
            return r.content
        except (requests.RequestException,ValueError):
            if attempt==2:raise
            time.sleep(attempt+1)
    raise ValueError('Scryfall reference temporarily unavailable')


def build(catalog,output,shard=0,shards=1,workers=6,cache='.manavault-artwork-cache',seed=None,fetch=None,max_items=0):
    if not (0<=shard<shards):raise ValueError('Invalid shard')
    path=Path(output);path.parent.mkdir(parents=True,exist_ok=True)
    db=sqlite3.connect(path)
    create_schema(db)
    old=None;tmpdir=None
    if seed and Path(seed).exists():
        old=sqlite3.connect(f'file:{seed}?mode=ro',uri=True)
    def one(task):
        c,face,url,illust=task
        pid=c['id'];key=(pid,face)
        if not valid_url(url):return ('unavailable',pid,face,url,'No supported Scryfall image URL')
        # First use the local shard; then the previous complete release. This
        # makes weekly generation fetch only new/changed printing artwork.
        expected=(url,normalize_name(c.get('name')),str(c.get('set') or ''),
                  str(c.get('collectorKey') or c.get('collector_number') or '').lower(),
                  illust,treatment(c))
        local=db_cache.get(key)
        if local==expected:return None
        if old_cache.get(key) is not None:
            previous=old_cache[key]
            if previous[7]==url:
                # The visual may be unchanged while Scryfall corrects the
                # printing's collector number, set code, name or treatment.
                # Carry over ONLY the costly pixels, refresh all metadata.
                return ('copied',(pid,face,expected[1],expected[2],expected[3],
                    expected[4],expected[5],url,*previous[8:]))
        try:
            raw=(fetch(url,cache) if fetch else fetch_image(url,cache))
            with Image.open(io.BytesIO(raw)) as img:features,dhash=photo_regions(img)
            return ('artwork',(pid,face,normalize_name(c.get('name')),str(c.get('set') or ''),
                str(c.get('collectorKey') or c.get('collector_number') or '').lower(),
                illust,treatment(c),url,features['art'],features['artcolor'],*(features[field] for field in FIELDS if field!='art'),dhash))
        except Exception as error:
            return ('unavailable',pid,face,url,str(error)[:160])

    # SQLite connections are not used by worker threads. Cache only the
    # handful of rows for the current batch in dictionaries before submitting.
    scanned=inserted=unavailable=unchanged=0
    iterator=iter_catalog(catalog,shard,shards)
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        while True:
            tasks=[]
            try:
                for _ in range(256):
                    if max_items and scanned+len(tasks)>=max_items:break
                    tasks.append(next(iterator))
            except StopIteration:pass
            if not tasks:break
            keys={(c['id'],face) for c,face,_,_ in tasks}
            db_cache={}
            old_cache={}
            for pid,face in keys:
                row=db.execute('SELECT url,nameKey,setCode,collectorKey,illustrationId,treatment FROM artwork WHERE printingId=? AND face=?',(pid,face)).fetchone()
                if row:db_cache[(pid,face)]=row
                if old:
                    try:
                        original=old.execute('SELECT * FROM artwork WHERE printingId=? AND face=?',(pid,face)).fetchone()
                        if original:old_cache[(pid,face)]=original
                    except sqlite3.DatabaseError:pass
            for result in pool.map(one,tasks):
                scanned+=1
                if result is None:unchanged+=1;continue
                mode=result[0]
                if mode in ('artwork','copied'):
                    row=result[1]
                    db.execute('INSERT OR REPLACE INTO artwork VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',row)
                    db.execute('DELETE FROM unavailable WHERE printingId=? AND face=?',row[:2])
                    inserted+=1
                else:
                    _,pid,face,url,why=result
                    db.execute('INSERT OR REPLACE INTO unavailable VALUES(?,?,?,?)',(pid,face,url,why))
                    unavailable+=1
            db.commit()
            if scanned%2048==0:print(f'Shard {shard}/{shards}: {scanned} visited · {inserted} indexed · {unchanged} cached · {unavailable} unavailable',flush=True)
            if max_items and scanned>=max_items:break
    db.execute('INSERT OR REPLACE INTO meta VALUES(?,?)',('schema',str(SCHEMA)))
    db.execute('INSERT OR REPLACE INTO meta VALUES(?,?)',('shard',str(shard)))
    db.execute('INSERT OR REPLACE INTO meta VALUES(?,?)',('shards',str(shards)))
    db.commit()
    db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
    db.close()
    if old:old.close()
    print(f'FINISHED shard={shard}/{shards}: scanned={scanned} new_or_seed={inserted} reused={unchanged} unavailable={unavailable}',flush=True)
    return {'scanned':scanned,'indexed':inserted,'reused':unchanged,'unavailable':unavailable}


def make_delta(previous, current, target, compressed):
    """Compact SQLite delta for added/changed/removed printing fingerprints."""
    old=sqlite3.connect(f'file:{Path(previous).resolve()}?mode=ro',uri=True)
    try:
        base=old.execute("SELECT value FROM meta WHERE key='version'").fetchone()
        if not base or not base[0]:raise ValueError('Prior artwork index missing version')
        from_version=base[0]
    finally:old.close()
    target=Path(target)
    if target.exists():target.unlink()
    db=sqlite3.connect(target)
    create_schema(db)
    db.execute('CREATE TABLE removed(printingId TEXT NOT NULL, face INTEGER NOT NULL, PRIMARY KEY(printingId,face))')
    db.execute('ATTACH DATABASE ? AS prior',(str(Path(previous).resolve()),))
    db.execute('ATTACH DATABASE ? AS current',(str(Path(current).resolve()),))
    db.execute('''INSERT INTO artwork SELECT n.* FROM current.artwork n
      LEFT JOIN prior.artwork p ON p.printingId=n.printingId AND p.face=n.face
      WHERE p.printingId IS NULL OR p.url!=n.url OR p.art!=n.art OR p.artcolor!=n.artcolor
        OR p.wide!=n.wide OR p.title!=n.title OR p.type!=n.type
        OR p.text!=n.text OR p.footer!=n.footer''')
    db.execute('''INSERT INTO removed SELECT p.printingId,p.face FROM prior.artwork p
      LEFT JOIN current.artwork n ON n.printingId=p.printingId AND n.face=p.face
      WHERE n.printingId IS NULL''')
    version=db.execute("SELECT value FROM current.meta WHERE key='version'").fetchone()[0]
    db.execute('INSERT OR REPLACE INTO meta VALUES(?,?)',('schema',str(SCHEMA)))
    db.execute('INSERT OR REPLACE INTO meta VALUES(?,?)',('from_version',from_version))
    db.execute('INSERT OR REPLACE INTO meta VALUES(?,?)',('version',version))
    updated=db.execute('SELECT COUNT(*) FROM artwork').fetchone()[0]
    removed=db.execute('SELECT COUNT(*) FROM removed').fetchone()[0]
    db.commit();db.execute('DETACH DATABASE prior');db.execute('DETACH DATABASE current')
    db.execute('VACUUM');db.close()
    with target.open('rb') as inp,Path(compressed).open('wb') as raw:
        with gzip.GzipFile(fileobj=raw,mode='wb',compresslevel=7,mtime=0) as output:
            while chunk:=inp.read(1<<20):output.write(chunk)
    return {'file':Path(compressed).name,'from':from_version,
        'sha256':hashlib.sha256(Path(compressed).read_bytes()).hexdigest(),
        'compressed_bytes':Path(compressed).stat().st_size,
        'updated_faces':updated,'removed_faces':removed}


def merge(shard_files,output,compressed,manifest,previous=None,delta=None,delta_compressed=None):
    import datetime
    out=Path(output);out.parent.mkdir(parents=True,exist_ok=True)
    if out.exists():out.unlink()
    db=sqlite3.connect(out)
    create_schema(db)
    for name in shard_files:
        other=sqlite3.connect(f'file:{Path(name).resolve()}?mode=ro',uri=True)
        try:
            for row in other.execute('SELECT * FROM artwork'):
                db.execute('INSERT INTO artwork VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',row)
            for row in other.execute('SELECT * FROM unavailable'):
                db.execute('INSERT OR REPLACE INTO unavailable VALUES(?,?,?,?)',row)
            db.commit()
        finally:other.close()
    total=db.execute('SELECT COUNT(*) FROM artwork').fetchone()[0]
    missing=db.execute('SELECT COUNT(*) FROM unavailable').fetchone()[0]
    if total<1:raise ValueError('No artwork descriptors generated')
    final_indexes(db)
    version=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    db.execute('INSERT OR REPLACE INTO meta VALUES(?,?)',('version',version))
    db.execute('INSERT OR REPLACE INTO meta VALUES(?,?)',('schema',str(SCHEMA)))
    db.commit()
    assert db.execute('PRAGMA quick_check').fetchone()[0]=='ok'
    assert db.execute('SELECT COUNT(*) FROM art_band').fetchone()[0]==8*total
    db.execute('VACUUM');db.close()
    zipped=Path(compressed)
    with out.open('rb') as inp,zipped.open('wb') as raw:
        with gzip.GzipFile(fileobj=raw,mode='wb',compresslevel=7,mtime=0) as zipout:
            while part:=inp.read(1<<20):zipout.write(part)
    sha=hashlib.sha256(zipped.read_bytes()).hexdigest()
    report={'schema':SCHEMA,'version':version,'file':zipped.name,'sha256':sha,
            'compressed_bytes':zipped.stat().st_size,'indexed_faces':total,'unavailable_faces':missing,
            'note':'Artwork descriptors are advisory; set/collector text overrides matching artwork.'}
    if previous and Path(previous).is_file() and delta and delta_compressed:
        report['delta']=make_delta(previous,out,delta,delta_compressed)
    Path(manifest).write_text(json.dumps(report,indent=2)+'\n')
    return report


def main():
    p=argparse.ArgumentParser()
    cmd=p.add_subparsers(dest='cmd',required=True)
    b=cmd.add_parser('build');b.add_argument('--catalog',default='app/src/main/assets/printing-catalog.dat');b.add_argument('--output',required=True)
    b.add_argument('--shard',type=int,default=0);b.add_argument('--shards',type=int,default=1)
    b.add_argument('--workers',type=int,default=6);b.add_argument('--cache',default='.manavault-artwork-cache')
    b.add_argument('--seed');b.add_argument('--max-items',type=int,default=0)
    m=cmd.add_parser('merge');m.add_argument('--output',required=True);m.add_argument('--compressed',required=True)
    m.add_argument('--manifest',required=True);m.add_argument('--previous');m.add_argument('--delta');m.add_argument('--delta-compressed');m.add_argument('shards',nargs='+')
    a=p.parse_args()
    if a.cmd=='build':build(a.catalog,a.output,a.shard,a.shards,a.workers,a.cache,a.seed,max_items=a.max_items)
    else:print(json.dumps(merge(a.shards,a.output,a.compressed,a.manifest,a.previous,a.delta,a.delta_compressed),indent=2))
if __name__=='__main__':main()
