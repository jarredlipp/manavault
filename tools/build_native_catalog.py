#!/usr/bin/env python3
"""Produce a compact read-only SQLite printing index from Scryfall bulk data.

CI uses Scryfall's official bulk endpoint, not individual card API calls.
The build is atomic. Can also read a saved bulk JSON/JSONL with --input for tests.
"""
import argparse, gzip, json, os, sqlite3, tempfile
from pathlib import Path
import requests

FIELDS = ('id','name','oracle_id','illustration_id','set','set_name','collector_number','type_line','colors','color_identity','mana_cost','rarity','layout','frame','border_color','full_art','textless','promo','security_stamp','finishes','frame_effects','released_at','image_url','image_small','image_normal','scryfall_uri','lang','digital','foil','nonfoil','prices')

def normalized(name):
    import unicodedata, re
    return re.sub(r'[^a-z0-9]+',' ',''.join(c for c in unicodedata.normalize('NFKD',str(name or '').lower()) if not unicodedata.combining(c))).strip()

def collector(number):
    import re
    match = re.fullmatch(r'([a-z]?)(\d{1,5})([a-z]?)',str(number or '').strip().lower())
    return (match[1]+str(int(match[2]))+match[3]) if match else str(number or '').strip().lower()

def record(c):
    faces=c.get('card_faces') or []
    face=faces[0] if faces else {}
    uri=c.get('image_uris') or face.get('image_uris') or {}
    data={key:c.get(key) for key in FIELDS}
    data.update(illustration_id=c.get('illustration_id') or face.get('illustration_id') or '', image_url=uri.get('art_crop') or '',image_small=uri.get('small') or '',image_normal=uri.get('normal') or '')
    data['nameKey']=normalized(c['name']);data['collectorKey']=collector(c.get('collector_number'));data['setCollector']=str(c.get('set','')).lower()+'#'+str(c.get('collector_number','')).lower()
    return data

def bulk_cards(source):
    try:
        import ijson
    except ImportError as e:
        raise SystemExit('Install ijson for streaming the Scryfall bulk file: python -m pip install ijson') from e
    # Accept both the official JSON array bulk and newline-delimited test input.
    import io
    if not isinstance(source,io.BufferedReader): source=io.BufferedReader(source,buffer_size=262144)
    data=source.peek(1024)[:1024]
    if data.lstrip().startswith(b'['):
        yield from ijson.items(source,'item')
    else:
        for line in source:
            if line.strip():yield json.loads(line)

def build(rows, output, minimum=100000, source_stamp=''):
    output=Path(output);output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as tmp:
        dbpath=Path(tmp)/'catalog.sqlite'
        con=sqlite3.connect(dbpath)
        con.executescript('''PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF;
          CREATE TABLE printings (id TEXT PRIMARY KEY,nameKey TEXT NOT NULL,setCode TEXT NOT NULL,collectorKey TEXT NOT NULL,illustrationId TEXT,oracleId TEXT, data TEXT NOT NULL);
          CREATE TABLE info(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        ''')
        count=0;batch=[]
        for obj in rows:
            if not isinstance(obj,dict) or not obj.get('id') or not obj.get('name'):continue
            card=record(obj)
            batch.append((card['id'],card['nameKey'],str(card.get('set') or '').lower(),card['collectorKey'],card.get('illustration_id') or '',card.get('oracle_id') or '',json.dumps(card,ensure_ascii=False,separators=(',',':'))))
            if len(batch)==750:
                con.executemany('INSERT OR REPLACE INTO printings VALUES(?,?,?,?,?,?,?)',batch);count+=len(batch);batch=[]
        if batch:con.executemany('INSERT OR REPLACE INTO printings VALUES(?,?,?,?,?,?,?)',batch);count+=len(batch)
        count=con.execute('SELECT COUNT(*) FROM printings').fetchone()[0]
        if count<minimum:raise ValueError(f'Only {count} printing rows; need at least {minimum}; previous output retained')
        con.executescript('''CREATE INDEX idx_names ON printings(nameKey);
         CREATE INDEX idx_set_collector ON printings(setCode,collectorKey);
         CREATE INDEX idx_collector ON printings(collectorKey);
         CREATE INDEX idx_sets ON printings(setCode);
         CREATE INDEX idx_illustration ON printings(illustrationId);
         CREATE INDEX idx_oracle ON printings(oracleId);
        ''')
        con.executemany('INSERT INTO info VALUES(?,?)',[('count',str(count)),('source',source_stamp)]);con.commit();con.execute('VACUUM');con.close()
        stage=Path(tmp)/'catalog.sqlite.gz'
        with open(dbpath,'rb') as inf, gzip.open(stage,'wb',compresslevel=7) as outf:
            while (block:=inf.read(1024*1024)):outf.write(block)
        os.replace(stage,output)
        print(f'Offline native catalog: {count} printings, {output.stat().st_size/1048576:.1f} MiB gzipped')
        return count

def main():
    p=argparse.ArgumentParser();p.add_argument('--output',default='app/src/main/assets/printing-catalog.sqlite.gz');p.add_argument('--input');p.add_argument('--min',type=int,default=100000);args=p.parse_args()
    if args.input:
        opener=gzip.open if args.input.endswith('.gz') else open
        with opener(args.input,'rb') as stream:build(bulk_cards(stream),args.output,args.min)
    else:
        session=requests.Session();session.headers['User-Agent']='ManaVault/5.11.5 (offline card index builder)'
        meta=session.get('https://api.scryfall.com/bulk-data/default_cards',timeout=30);meta.raise_for_status()
        info=meta.json();url=info.get('download_uri') or info.get('jsonl_download_uri')
        if not url:raise RuntimeError('Missing Scryfall bulk download URI')
        with session.get(url,stream=True,timeout=(20,120)) as resp:
            resp.raise_for_status();resp.raw.decode_content=True
            build(bulk_cards(resp.raw),args.output,args.min,info.get('updated_at',''))

if __name__=='__main__':main()
