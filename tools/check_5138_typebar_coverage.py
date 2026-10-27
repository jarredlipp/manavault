#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=(root/'app/src/main/java/com/manavault/app/PrintingFingerprintVerifier.java').read_text()
assert 'float[] ys={.49f,.545f,.60f,.655f,.71f,.765f,.825f,.885f,.935f};' in p
assert 'coLocatedAmbiguous=strongest<.82 || colocated<.085' in p
assert 'readSymbolMasks(card,x,y,size[0],size[1])' in p
assert 'BundledSetSymbols.icon(set)' in p
assert 'readCoreSetWordmark' in p
html=(root/'app/src/main/assets/index.html').read_text()
assert 'if(result.coLocatedAmbiguous===true)' in html
assert 'if(bySet.size<2||bySet.size>96)' in html
print('v5.13.8 type-bar position expansion and false-positive guard PASS')
