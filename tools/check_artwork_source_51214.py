#!/usr/bin/env python3
"""Catch the historical twice-cropped Scryfall art_crop seed bug."""
from pathlib import Path
from PIL import Image
import sys
sys.path.insert(0,str(Path(__file__).parent))
import build_artwork_index

image=Image.new('RGB',(100,100))
for y in range(100):
 for x in range(100):image.putpixel((x,y),(x*2,y*2,(x+y)))
full=build_artwork_index.reference_art_region(image,'https://cards.scryfall.io/normal/front/sample.jpg')
art=build_artwork_index.reference_art_region(image,'https://cards.scryfall.io/art_crop/front/sample.jpg')
assert full.size==(82,34),full.size
assert art.size==(100,100),art.size
assert full.getpixel((0,0))!=art.getpixel((0,0))
rows=[{'name':'Soul-Guide Lantern','nameKey':'soul guide lantern','id':'id1','illustration_id':'art1',
 'image_normal':'https://cards.scryfall.io/normal/front/id1.jpg',
 'image_url':'https://cards.scryfall.io/art_crop/front/id1.jpg','lang':'en'}]
chosen=build_artwork_index.names(rows,1)
assert '/normal/' in chosen[0][3],chosen
print('v5.12.14 reference imagery: full-card normal URLs, one-crop matching, art_crop fallback preserved')
