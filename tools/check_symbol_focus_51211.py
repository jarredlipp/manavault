#!/usr/bin/env python3
"""Dedicated Scryfall SVG silhouette and native scanner integration preflight."""
import base64,sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'tools'))
from build_symbol_index import icon_mask
sample=b'<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><path d="M10 10 L90 50 L10 90 Z" fill="black" /></svg>'
raw=base64.b64decode(icon_mask(sample))
assert len(raw)==576 and sum(n>100 for n in raw)>16
native=(root/'app/src/main/java/com/manavault/app/PrintingFingerprintVerifier.java').read_text()
assert 'SymbolShapeMatcher.score(icon,mask)' in native
assert 'private static byte[] readSymbolMask' in native
assert 'scryfall-svg-icon-v3' in native
assert 'manavault-symbol-v3' in (root/'tools/verify_offline_assets.py').read_text()
print('Dedicated Scryfall set-icon shape atlas preflight passed')
