#!/usr/bin/env python3
"""A double-faced printed card must index both artworks under one printing ID."""
import sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'tools'))
from build_native_catalog import record
from build_artwork_index import names
example={
 'id':'test-vow-104','name':'Desperate Farmer // Depraved Harvester',
 'nameKey':'desperate farmer depraved harvester','set':'vow','lang':'en','digital':False,
 'collector_number':'104','card_faces':[
   {'illustration_id':'front-104','image_uris':{'normal':'https://cards.scryfall.io/normal/front.jpg'}},
   {'illustration_id':'back-104','image_uris':{'normal':'https://cards.scryfall.io/normal/back.jpg'}}
 ]
}
row=record(example)
assert row['illustration_id']=='front-104'
assert row['illustration_id_back']=='back-104'
assert row['image_normal_back'].endswith('back.jpg')
items=names([row],4)
assert len(items)==2, items
assert items[0][0]==items[1][0]=='test-vow-104'
assert {x[-1] for x in items}=={'front-104','back-104'}
print('v5.12.23 reverse-face artwork indexing passed')
