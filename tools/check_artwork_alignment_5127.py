"""Offline fixture checks that the bundled art index matches Android's art crop."""
from importlib.util import spec_from_file_location,module_from_spec
from pathlib import Path
from PIL import Image, ImageDraw
p=Path(__file__).parent/'build_artwork_index.py'
spec=spec_from_file_location('build_artwork_index',p)
m=module_from_spec(spec);spec.loader.exec_module(m)
w,h=1000,1400
image=Image.new('RGB',(w,h),'black')
d=ImageDraw.Draw(image)
d.rectangle((w*.09,h*.15,w*.91,h*.49),fill=(204,40,30))
d.rectangle((w*.09,h*.49,w*.91,h*.86),fill=(90,40,160))
cropped=m.crop_reference_art(image)
assert cropped.size==(round(w*.91)-round(w*.09),round(h*.49)-round(h*.15))
assert cropped.resize((8,8)).getpixel((4,4))==(204,40,30)
assert image.resize((8,8)).getpixel((4,4))!=(204,40,30)
print('Artwork reference crop and native VisualMatcher.artRegion alignment passed.')
