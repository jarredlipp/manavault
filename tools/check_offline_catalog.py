#!/usr/bin/env python3
"""No-network test of SQLite atomic catalog generation and symbol encoding."""
import gzip, sqlite3, tempfile, sys, base64
from pathlib import Path
# The CI/build installs the real ijson streaming parser. For no-network
# developer checks, permit a tiny fixture-only stand-in if ijson is absent.
# Production bulk data still requires ijson and never uses json.load().
try:
 import ijson
except ImportError:
 import json as _json
 import types as _types
 sys.modules['ijson']=_types.SimpleNamespace(items=lambda file,prefix: iter(_json.load(file)))
from io import BytesIO
from PIL import Image
sys.path.insert(0,str(Path(__file__).parent))
from build_native_catalog import build,bulk_cards,collector,normalized,download_bulk_to_file
from build_symbol_index import signature

cards=[
 {'id':'printing1','name':'Webstrike Elite','set':'dft','collector_number':'0345','illustration_id':'art-a','oracle_id':'oracle-a','oracle_text':'When you cycle this card destroy target artifact or enchantment', 'type_line':'Creature — Insect Archer','lang':'en','image_uris':{'normal':'https://cards.scryfall.io/normal/front/0/0/a.jpg'}},
 {'id':'printing2','name':'Webstrike Elite','set':'dft','collector_number':'0345','illustration_id':'art-b','oracle_id':'oracle-a','lang':'en'},
 {'id':'printing3','name':'Far // Away','set':'dgm','collector_number':'125','oracle_id':'oracle-b','oracle_text':'Return target creature to its owners hand. Target player sacrifices a creature.', 'type_line':'Instant','lang':'en'},
 {'id':'printing4','name':'Jace','set':'twa','collector_number':'0005','type_line':'Token Planeswalker — Jace','lang':'en'},
]
assert collector('0345')=='345' and collector('T0005')=='t5'
assert normalized('Far // Away')=='far away'
# Scryfall delivers a large JSON array, sometimes gzip-wrapped by the CDN.
# Confirm the builder accepts either form, plus an optional JSONL feed.
import json
plain_array=json.dumps(cards).encode('utf8')
plain_lines=b'\n'.join(json.dumps(card).encode('utf8') for card in cards)+b'\n'
for label,payload in [
 ('json-array',plain_array),
 ('gzip-json-array',gzip.compress(plain_array)),
 ('jsonl',plain_lines),
 ('gzip-jsonl',gzip.compress(plain_lines)),
]:
 decoded=list(bulk_cards(BytesIO(payload)))
 assert decoded==cards,f'bulk parsing failed for {label}'
# Regression for CI's "read of closed file": the HTTP response must be
# closed *before* gzip/ijson consumes the downloaded catalog. Also verify
# an HTTP-compressed response already decoded by iter_content remains valid.
class FakeResponse:
 def __init__(self,payload):self.payload=payload;self.closed=False
 def iter_content(self,chunk_size=262144):
  assert not self.closed
  for i in range(0,len(self.payload),11):yield self.payload[i:i+11]
 def close(self):self.closed=True
for label,payload in [('remote-gzip-file',gzip.compress(plain_array)),
                      ('remote-plain-json',plain_array)]:
 with tempfile.TemporaryDirectory() as td:
  response=FakeResponse(payload)
  path=Path(td)/'download.bin'
  assert download_bulk_to_file(response,path)==len(payload)
  response.close()
  with path.open('rb') as stream:
   assert list(bulk_cards(stream))==cards,label
# Exercise the *real CLI main* with a fake HTTP session: the response closes
# on leaving the HTTP context, but the local file must remain readable.
# This catches accidental regressions to parsing response.raw directly.
from unittest.mock import patch
import build_native_catalog as catalog_module
class FakeNetworkResponse(FakeResponse):
 def __init__(self,payload, metadata=False):
  super().__init__(payload);self.metadata=metadata
 def json(self):
  return {'download_uri':'https://example.invalid/bulk', 'updated_at':'test-time'}
 def raise_for_status(self):pass
 def __enter__(self):return self
 def __exit__(self,*exc):self.close()
 @property
 def raw(self):raise AssertionError('main must not parse HTTP response.raw')
class FakeSession:
 def __init__(self,payload):self.payload=payload;self.headers={};self.responses=[]
 def __enter__(self):return self
 def __exit__(self,*exc):pass
 def get(self,url,**kw):
  resp=FakeNetworkResponse(self.payload if 'bulk' in url else b'{}')
  self.responses.append(resp)
  return resp
with tempfile.TemporaryDirectory() as td:
 target=Path(td)/'via-http.sqlite.gz'
 session=FakeSession(gzip.compress(plain_array))
 with patch.object(catalog_module.requests,'Session',return_value=session),patch.object(sys,'argv',['build_native_catalog.py','--output',str(target),'--min','4']):
  catalog_module.main()
 assert target.exists()
 with gzip.open(target,'rb') as stream:
  path=Path(td)/'catalog.sqlite'
  path.write_bytes(stream.read())
 con=sqlite3.connect(path)
 assert con.execute('SELECT COUNT(*) FROM printings').fetchone()[0]==4
 con.close()
 assert any(r.closed for r in session.responses)
# --input supports both raw and compressed files, independent of extension.
with tempfile.TemporaryDirectory() as td:
 compressed=Path(td)/'raw-without-gz-extension.data'
 compressed.write_bytes(gzip.compress(plain_array))
 target=Path(td)/'from-input.sqlite.gz'
 with patch.object(sys,'argv',['build_native_catalog.py','--input',str(compressed),'--output',str(target),'--min','4']):
  catalog_module.main()
 assert target.exists()
try:
 list(bulk_cards(BytesIO(b'<!DOCTYPE html><html>error</html>')))
 raise AssertionError('Non-JSON bulk response was accepted')
except ValueError as exc:
 assert 'not JSON/JSONL' in str(exc)
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
 assert con.execute('SELECT COUNT(*) FROM reference_fts').fetchone()[0]==4
 assert con.execute("SELECT COUNT(*) FROM reference_fts WHERE reference_fts MATCH 'sacrifices AND return'").fetchone()[0]==1
 assert con.execute("SELECT COUNT(*) FROM reference_fts WHERE reference_fts MATCH 'artifact AND enchantment'").fetchone()[0]==1
 con.close()
 image=Image.new('RGB',(488,680),(240,240,240))
 for y in range(350,390):
  for x in range(420,445):image.putpixel((x,y),(5,5,5))
 bio=BytesIO();image.save(bio,format='JPEG')
 encoded=signature(bio.getvalue());assert len(base64.b64decode(encoded))==1728
print('Offline catalog: network-response-safe spooling, plain/gzip JSON+JSONL streaming, invalid payload rejection, atomic build, duplicate printing preservation, indexes and symbol descriptor passed')
