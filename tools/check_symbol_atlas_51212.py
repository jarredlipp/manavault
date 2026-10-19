#!/usr/bin/env python3
"""Offline end-to-end fixture: all physical sets, SVG masks, coverage and failures."""
import base64, gzip, json, sqlite3, sys, tempfile
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_native_catalog import build as build_db
import build_symbol_index as icons

SHAPES = [
    b'<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><path fill="black" d="M10 10 L90 50 L10 90 Z"/></svg>',
    b'<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><path fill="black" d="M10 10 L90 10 L90 90 L10 90 Z"/></svg>',
]

class Response:
    def __init__(self, data): self.content = data
    def raise_for_status(self): pass

with tempfile.TemporaryDirectory() as folder:
    root = Path(folder)
    dbfile = root/'catalog.dat'
    cards = ({'id':f'p{i}','name':f'Sample {i}', 'set':f'z{i:02}',
              'collector_number':str(i), 'oracle_id':f'o{i}', 'lang':'en'} for i in range(26))
    build_db(cards,dbfile,minimum=26)
    rows = [{'code':f'z{i:02}', 'name':f'Test Set {i}', 'icon_svg_uri':f'https://svgs.scryfall.io/sets/z{i:02}.svg'} for i in range(26)]
    rows.append({'code':'unavailable', 'name':'Digital', 'icon_svg_uri':'https://svgs.scryfall.io/sets/digital.svg'})
    seen=[]
    def download(uri, **kwargs):
        seen.append(uri)
        return Response(SHAPES[int(uri.rsplit('z',1)[-1].split('.')[0]) % 2])
    out = root/'symbols.json'
    result = icons.build(dbfile,out,workers=2,set_rows=rows,http_get=download)
    atlas=json.loads(out.read_text())
    assert result==26 and len(seen)==26
    assert atlas['format']=='manavault-symbol-v3'
    assert atlas['coverage']['icon_eligible']==26 and atlas['coverage']['downloaded']==26
    assert atlas['coverage']['ratio']==1
    assert len({entry['set'] for entry in atlas['sets']})==26
    assert all(len(base64.b64decode(entry['shape']))==576 for entry in atlas['sets'])
    assert atlas['sets'][0]['shape'] != atlas['sets'][1]['shape']
    assert 'unavailable' not in [entry['set'] for entry in atlas['sets']]
    # The builder must reject a suspiciously incomplete download, not
    # silently release an APK containing only the successful 12 icons.
    def partial(uri, **kwargs):
        if int(uri.rsplit('z',1)[-1].split('.')[0])%2:
            raise ValueError('missing')
        return Response(SHAPES[0])
    try:
        icons.build(dbfile,root/'bad.json',workers=2,set_rows=rows,http_get=partial)
        raise AssertionError('partial set-icon catalog incorrectly accepted')
    except ValueError as error:
        assert 'coverage' in str(error)
    assert not (root/'bad.json').exists()
print('v5.12.12: all-set Scryfall SVG coverage, source validation, masks, and incomplete-download rejection passed')
