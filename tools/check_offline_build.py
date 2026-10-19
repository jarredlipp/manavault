#!/usr/bin/env python3
"""Validate that packaging rejects placeholders and accepts sound generated assets.
No network or Android SDK required.
"""
import base64
import gzip
import json
import sqlite3
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from verify_offline_assets import verify
from build_native_catalog import build

sample = [
    {'id': f'p{i}', 'name': f'Test card {i}', 'set': 'tst', 'collector_number': str(i),
     'illustration_id': f'art{i}', 'oracle_id': f'ora{i}', 'lang': 'en'}
    for i in range(12)
]
with tempfile.TemporaryDirectory() as d:
    assets=Path(d)
    missing,_=verify(assets,12,12,0)
    assert len(missing)==3,missing
    build(iter(sample),assets/'printing-catalog.dat',minimum=12)
    (assets/'visual-index.json').write_text(json.dumps({'format':'manavault-visual-v2','cards':[
        {'id':f'p{i}','name':f'Test {i}','f':[0]*192} for i in range(12)
    ]}))
    sig=base64.b64encode(bytes([255]*576)).decode('ascii')
    (assets/'symbol-index.json').write_text(json.dumps({'format':'manavault-symbol-v3','coverage':{'icon_eligible':12},'sets':[
        {'set':f's{i}','shape':sig} for i in range(12)
    ]}))
    errors,data=verify(assets,12,12,0)
    assert not errors,errors
    assert data=={'printings':12,'artworks':12,'sets':12},data
    (assets/'visual-index.json').write_text('{"format":"manavault-visual-v2","cards":[]}')
    errors,_=verify(assets,12,12,0)
    assert any('artwork index contains 0' in x for x in errors),errors
    (assets/'visual-index.json').write_text(json.dumps({'format':'manavault-visual-v2','cards':[
        {'id':f'p{i}','name':f'Test {i}','f':[0]*192} for i in range(12)
    ]}))
    (assets/'symbol-index.json').write_text(json.dumps({'format':'manavault-symbol-v3','coverage':{'icon_eligible':12},'sets':[
        {'set':f's{i}','shape':'AA=='} for i in range(12)
    ]}))
    errors,_=verify(assets,12,12,0)
    assert any('576 bytes' in x for x in errors),errors

project=Path(__file__).resolve().parent.parent
buildgradle=(project/'app/build.gradle').read_text()
workflow=(project/'.github/workflows/build-apk.yml').read_text()
ensure=(project/'tools/ensure_offline_assets.sh').read_text()
assert "dependsOn tasks.named('prepareOfflineAssets')" in buildgradle
assert "tasks.named('preBuild').configure" in buildgradle
assert "it.name.endsWith('Assets')" in buildgradle
assert "bash tools/ensure_offline_assets.sh" in workflow
assert 'tools/verify_offline_assets.py' in ensure
print('Offline build regression passed: empty references fail, valid catalog/artwork/symbol assets pass, malformed symbols fail, Gradle and workflow preparation wired')
