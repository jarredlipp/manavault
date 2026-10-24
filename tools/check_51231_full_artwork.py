#!/usr/bin/env python3
"""Fixture/regression checks for complete, printing-granular artwork indexing."""
import argparse
import gzip
import hashlib
import io
import json
import sqlite3
import sys
import tempfile
from pathlib import Path
from PIL import Image, ImageDraw

sys.path.insert(0,str(Path(__file__).resolve().parent))
from build_native_catalog import build as build_catalog
from full_artwork_index import build,merge,photo_regions,valid_url,make_delta,iter_catalog


def fixture_image(label,flavor,collector,*,alternate=False):
    im=Image.new('RGB',(744,1040),(229,221,193)); d=ImageDraw.Draw(im)
    d.rectangle((45,140,698,508),fill=(25,80,120) if not alternate else (90,30,115))
    d.ellipse((140,155,580,490),fill=(23,182,125) if not alternate else (208,171,30))
    d.rectangle((40,745,700,937),fill=(237,237,226))
    d.text((90,35),label,fill=(10,10,10),stroke_width=2)
    d.text((90,790),flavor,fill=(10,10,10),stroke_width=3)
    d.text((90,955),str(collector),fill=(15,15,15),stroke_width=2)
    output=io.BytesIO();im.save(output,format='PNG');return output.getvalue()


def sample(pid,name,num,url,art='shared',back=''):
    return dict(id=pid,name=name,lang='en',digital=False,set='xyz',collector_number=str(num),
        illustration_id=art,frame_effects=[],image_uris={'normal':url},
        card_faces=([{'name':name,'illustration_id':art,'image_uris':{'normal':url}},
                     {'name':name+' (back)','illustration_id':art+'-back','image_uris':{'normal':back}}] if back else []))


def unit_tests():
    assert valid_url('https://cards.scryfall.io/normal/front/test.png')
    assert not valid_url('https://not-scryfall.example/file.png')
    assert not valid_url('http://cards.scryfall.io/file.jpg')
    urls={
        'https://cards.scryfall.io/normal/front/a.png':fixture_image('Same Art','Ability: draw a card.',101),
        'https://cards.scryfall.io/normal/front/b.png':fixture_image('Same Art','Ability: destroy a card.',102),
        'https://cards.scryfall.io/normal/front/c.png':fixture_image('Same Art','Ability: draw a card.',103),
        'https://cards.scryfall.io/normal/front/d.png':fixture_image('Other Art','Flying',104,alternate=True),
        'https://cards.scryfall.io/normal/back/e.png':fixture_image('Back Face','Transform',105,alternate=True),
    }
    # One illustration appears in three printings. Two differ only in text;
    # the third has the same rendered image but a distinct database printing ID.
    images=list(urls.values())
    a,_=photo_regions(Image.open(io.BytesIO(images[0])))
    b,_=photo_regions(Image.open(io.BytesIO(images[1])))
    c,_=photo_regions(Image.open(io.BytesIO(images[2])))
    assert a['art']==b['art'], 'Shared illustration unexpectedly differs'
    assert a['text']!=b['text'], 'Rules-box difference lost'
    assert a['art']==c['art'] and a['text']==c['text'], 'Fixture must include visually identical printings'
    rows=[
        sample('p1','Sample Card',101,list(urls)[0]),
        sample('p2','Sample Card',102,list(urls)[1]),
        sample('p3','Sample Card',103,list(urls)[0]),  # absolutely identical artwork and text
        sample('p4','Other Card',104,list(urls)[3],art='other',back=list(urls)[4]),
    ]
    # Never deduplicate rows by illustration ID, display name, or art URL.
    with tempfile.TemporaryDirectory() as folder:
        loc=Path(folder);catalog=loc/'native-catalog.gz'
        build_catalog(rows,catalog,minimum=4)
        def fetch(url,cache):return urls[url]
        shard=loc/'shard.sqlite'
        stats=build(catalog,shard,workers=2,fetch=fetch)
        assert stats['scanned']==5,stats
        con=sqlite3.connect(shard)
        try:
            assert con.execute('SELECT COUNT(*) FROM artwork').fetchone()[0]==5
            same=list(con.execute("SELECT collectorKey,art,text,footer FROM artwork WHERE nameKey='sample card' ORDER BY printingId"))
            assert len(same)==3 and same[0][1]==same[1][1] and same[0][2]!=same[1][2]
            assert same[0][1:]==same[2][1:], 'Printings with identical images should have equal descriptors'
            assert {r[0] for r in same}=={'101','102','103'}
            assert con.execute("SELECT COUNT(*) FROM artwork WHERE printingId='p4'").fetchone()[0]==2
        finally:con.close()
        def reject(*_):raise AssertionError('Image downloaded even though fingerprint was seeded')
        second=loc/'seeded.sqlite'
        seeded=build(catalog,second,seed=shard,workers=2,fetch=reject)
        assert seeded['indexed']==5 and seeded['unavailable']==0,seeded
        result=merge([second],loc/'complete.sqlite',loc/'manavault-artwork.sqlite.gz',loc/'manavault-artwork-manifest.json')
        assert result['indexed_faces']==5 and result['unavailable_faces']==0
        con=sqlite3.connect(loc/'complete.sqlite')
        try:
            assert con.execute('SELECT COUNT(*) FROM art_band').fetchone()[0]==40
            idx={x[1] for x in con.execute("PRAGMA index_list('artwork')")}
            assert {'art_by_name','art_by_set_collector','art_by_illustration'}.issubset(idx)
            assert con.execute('PRAGMA quick_check').fetchone()[0]=='ok'
        finally:con.close()
        actual=hashlib.sha256((loc/'manavault-artwork.sqlite.gz').read_bytes()).hexdigest()
        assert actual==result['sha256'] 
        # Version-to-version delta: one updated textbox, one new edition,
        # one removed edition. Unchanged references must not be transmitted.
        current=loc/'next.sqlite'
        import shutil
        shutil.copy2(loc/'complete.sqlite',current)
        db=sqlite3.connect(current)
        db.execute("UPDATE artwork SET text=? WHERE printingId='p2' AND face=0",(bytes([77])*64,))
        db.execute("DELETE FROM artwork WHERE printingId='p3'")
        db.execute("INSERT INTO artwork SELECT 'p5',face,'sample card',setCode,'999',illustrationId,treatment,url,art,artcolor,wide,title,type,text,footer,dhash FROM artwork WHERE printingId='p1'")
        db.execute("UPDATE meta SET value='20261024T235959Z' WHERE key='version'")
        db.commit();db.close()
        diff=make_delta(loc/'complete.sqlite',current,loc/'delta.sqlite',loc/'delta.sqlite.gz')
        assert diff['updated_faces']==2,diff
        assert diff['removed_faces']==1,diff
        db=sqlite3.connect(loc/'delta.sqlite')
        assert db.execute('SELECT COUNT(*) FROM artwork').fetchone()[0]==2
        assert db.execute('SELECT printingId FROM removed').fetchone()[0]=='p3'
        db.close()
        assert hashlib.sha256((loc/'delta.sqlite.gz').read_bytes()).hexdigest()==diff['sha256']
        assert sum(diff['updated_faces'] for _ in [0])<result['indexed_faces']

        print('v5.12.31 full art index: 5 printing faces, same-art distinct text, identical-image separate collectors,')
        print('  back-face references, SQLite indexes, seed reuse and manifest validation PASSED')


def verify_complete_catalog(catalog,db_path):
    """Reject a supposedly 'full' index if a shard or available face vanished."""
    conn=sqlite3.connect(db_path)
    try:
        indexed={(pid,face) for pid,face in conn.execute('SELECT printingId,face FROM artwork')}
        missing={(pid,face) for pid,face in conn.execute('SELECT printingId,face FROM unavailable')}
        expected={(item['id'],face) for item,face,_,_ in iter_catalog(catalog)}
        if indexed.intersection(missing):raise AssertionError('Artwork indexed and unavailable simultaneously')
        if indexed.union(missing)!=expected:
            difference=expected-indexed-missing
            raise AssertionError(f'Full art index incomplete: {len(difference)} missing printing faces; sample {list(difference)[:4]}')
        print(f'Full Scryfall coverage verified: {len(indexed)} image fingerprints + {len(missing)} explicitly unavailable = {len(expected)} printing faces')
    finally:conn.close()

def verify_publication(db_path,manifest_path):
    conn=sqlite3.connect(db_path)
    manifest=json.loads(Path(manifest_path).read_text())
    try:
        rows=conn.execute('SELECT COUNT(*) FROM artwork').fetchone()[0]
        assert rows==manifest['indexed_faces']
        assert conn.execute('SELECT COUNT(*) FROM art_band').fetchone()[0]==rows*8
        assert conn.execute('PRAGMA quick_check').fetchone()[0]=='ok'
        assert conn.execute('SELECT value FROM meta WHERE key="schema"').fetchone()[0]=='1'
        assert conn.execute('SELECT value FROM meta WHERE key="version"').fetchone()[0]==manifest['version']
        print('Full artwork release VERIFIED:',rows,'indexed faces,',manifest['unavailable_faces'],'unavailable')
    finally:conn.close()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--verify-published',nargs=2);p.add_argument('--verify-catalog',nargs=2)
    a=p.parse_args()
    if a.verify_published:verify_publication(*a.verify_published)
    elif a.verify_catalog:verify_complete_catalog(*a.verify_catalog)
    else:unit_tests()
