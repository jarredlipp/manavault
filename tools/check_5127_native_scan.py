"""Source contract checks for rotation, outer-border tracking and reference parity.
Device camera geometry still requires a real CameraX test.
"""
from pathlib import Path
root=Path(__file__).resolve().parent.parent
java=root/'app/src/main/java/com/manavault/app'
scanner=(java/'NativeScannerActivity.java').read_text()
edge=(java/'CardQuadDetector.java').read_text()
visual=(java/'VisualMatcher.java').read_text()
symbols=(java/'PrintingFingerprintVerifier.java').read_text()
assert 'int[] rotations=titleRotationOrder(card.getWidth(),card.getHeight())' in scanner
assert scanner.index('if(rotationIndex<3){\n                readTitle') < scanner.index('enqueue(bestTentative,"ocr-candidate"'), 'try all orientations before unknown title'
assert 'previousOcrCount>=2 && normalized.length()>=3' in scanner, 'short titles should reach review'
assert 'Imgproc.RETR_LIST' in edge and 'hasEnclosingCardEdge(edgePixels' in edge
assert 'area>hint.areaFraction*.86f' in edge
assert 'readSymbolMask(card,x,y,size[0],size[1])' in symbols and 'SymbolShapeMatcher.score(icon,mask)' in symbols
assert 'learned-artwork-v51214.json' in visual
assert 'entry.e=perceptualDescriptor(crop)' in visual
assert 'new Entry(name,id,fingerprint(crop))' in visual
print('v5.12.7 native source checks: 4-way title rotation, Jace short OCR, perimeter guard, offline SVG icon shape ranking, art reference crop alignment passed.')
