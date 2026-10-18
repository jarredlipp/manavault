#!/usr/bin/env python3
"""Offline fixture test: 128-value perceptual descriptors ship alongside RGB."""
from pathlib import Path
from unittest.mock import patch
from io import BytesIO
import tempfile,gzip,sqlite3,json,sys
from PIL import Image
sys.path.insert(0,str(Path(__file__).parent))
import build_native_catalog,build_artwork_index
cards=[]
for index in range(14):
    cards.append({'name':f'Fixture Bird {index}', 'id':f'fixture-{index}',
                  'illustration_id':f'illustration-{index}',
                  'image_uris':{'art_crop':f'https://cards.scryfall.io/art{index}.jpg'},
                  'set':'tst','collector_number':str(index),'oracle_text':'Flying'})
bitmap=Image.new('RGB',(80,80),(32,60,110))
for y in range(50):
    for x in range(80):bitmap.putpixel((x,y),(x*3,y*4,40))
buffer=BytesIO();bitmap.save(buffer,format='JPEG');payload=buffer.getvalue()
class Response:
    content=payload
    def raise_for_status(self):pass
with tempfile.TemporaryDirectory() as directory:
    root=Path(directory)
    catalog=root/'test.sqlite.gz';build_native_catalog.build(cards,catalog,minimum=14)
    target=root/'art.json'
    with patch.object(build_artwork_index.requests,'get',return_value=Response()):
        assert build_artwork_index.build(catalog,target,max_refs=12,workers=1)==12
    data=json.loads(target.read_text())
    assert all(len(c['f'])==192 and len(c['e'])==128 for c in data['cards'])
    assert all(0<=v<=255 for c in data['cards'] for v in c['e'])
    assert data['cards'][0]['illustration_id'].startswith('illustration-')
print('Visual descriptor fixture passed: 12 Scryfall-like art references with RGB and contrast-normalized 128-dimensional fingerprints')
