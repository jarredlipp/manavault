#!/usr/bin/env python3
"""No-network test of SQLite atomic catalog generation and symbol encoding."""
import gzip, sqlite3, tempfile, sys, base64
from pathlib import Path
from io import BytesIO
from PIL import Image
sys.path.insert(0,str(Path(__file__).parent))
from build_native_catalog import build,collector,normalized
from build_symbol_index import signature

cards=[
 {'id':'printing1','name':'Webstrike Elite','set':'dft','collector_number':'0345','illustration_id':'art-a','oracle_id':'oracle-a','lang':'en','image_uris':{'normal':'https://cards.scryfall.io/normal/front/0/0/a.jpg'}},
 {'id':'printing2','name':'Webstrike Elite','set':'dft','collector_number':'0345','illustration_id':'art-b','oracle_id':'oracle-a','lang':'en'},
 {'id':'printing3','name':'Far // Away','set':'dgm','collector_number':'125','oracle_id':'oracle-b','lang':'en'},
 {'id':'printing4','name':'Jace','set':'twa','collector_number':'0005','type_line':'Token Planeswalker — Jace','lang':'en'},
]
assert collector('0345')=='345' and collector('T0005')=='t5'
assert normalized('Far // Away')=='far away'
with tempfile.TemporaryDirectory() as temp:
 path=Path(temp)/'catalog.sqlite.gz'
 assert build(iter(cards),path,minimum=4)==4
 with gzip.open(path,'rb') as f:unpacked=f.read()
 target=Path(temp)/'catalog.sqlite';target.write_bytes(unpacked)
 con=sqlite3.connect(target)
 assert con.execute('SELECT COUNT(*) FROM printings WHERE setCode=? AND collectorKey=?',('dft','345')).fetchone()[0]==2
 assert con.execute('SELECT COUNT(*) FROM printings WHERE nameKey=?',('far away',)).fetchone()[0]==1
 assert con.execute('SELECT COUNT(*) FROM printings WHERE illustrationId=?',('art-b',)).fetchone()[0]==1
 assert len(con.execute("PRAGMA index_list('printings')").fetchall())>=6
 con.close()
 image=Image.new('RGB',(488,680),(240,240,240))
 for y in range(350,390):
  for x in range(420,445):image.putpixel((x,y),(5,5,5))
 bio=BytesIO();image.save(bio,format='JPEG')
 encoded=signature(bio.getvalue());assert len(base64.b64decode(encoded))==1728
print('Offline catalog: atomic build, duplicate printing preservation, indexes and symbol descriptor passed')
