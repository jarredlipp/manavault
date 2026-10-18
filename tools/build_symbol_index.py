#!/usr/bin/env python3
"""Build a bounded offline index of actual printed type-bar set symbols.

Reference samples are official Scryfall *normal card images*, not standalone SVG
icons, so perspective/frame geometry matches the native symbol verifier.
Every fingerprint is normalized independently. Missing images are omitted.
"""
import argparse, base64, concurrent.futures, gzip, json, os, sqlite3, tempfile
from collections import defaultdict
from pathlib import Path
from io import BytesIO
import requests
from PIL import Image

PRIORITY=('dft','hou','stx','war','m19','ktk','bfz','akh','dom','mom','eld','dsk','wot','fin','vow','ogw','c17','c18','tsr','ody','nem','inv','ons','mmq','ulg','7ed','8ed','9ed','10e','m10','m11','m12','m13','m14','m15')

def signature(data):
    img=Image.open(BytesIO(data)).convert('RGB')
    w,h=img.size
    values=[]
    for ycenter in (.515,.548,.580):
        x=round(w*.82);y=round(h*ycenter)
        crop=img.crop((x,y,min(w,x+round(w*.16)),min(h,y+round(h*.075))))
        low=crop.resize((24,24),Image.Resampling.BILINEAR)
        lum=[.299*r+.587*g+.114*b for r,g,b in low.getdata()]
        avg=sum(lum)/576
        zero=[v-avg for v in lum]
        length=sum(v*v for v in zero)**.5
        values.extend([max(-127,min(127,round(v*127/length))) for v in zero] if length>1e-6 else [0]*576)
    return base64.b64encode(bytes(v%256 for v in values)).decode('ascii')

def catalog_rows(path):
    with gzip.open(path,'rb') as inp:
        with tempfile.NamedTemporaryFile(suffix='.sqlite') as f:
            while (chunk:=inp.read(1024*1024)):f.write(chunk)
            f.flush()
            db=sqlite3.connect(f.name)
            yield from db.execute("SELECT setCode,data FROM printings WHERE length(setCode) BETWEEN 2 AND 5")
            db.close()

def choose(path,max_sets):
    grouped=defaultdict(list)
    for set_code,raw in catalog_rows(path):
        try: c=json.loads(raw)
        except ValueError:continue
        url=c.get('image_normal') or ''
        if not url.startswith('https://cards.scryfall.io/normal/'):continue
        if c.get('digital') or c.get('layout') not in ('normal','transform','modal_dfc','adventure','leveler'):continue
        # One frame per set is a fallback; prefer regular 2015 frames over showcases.
        preference=(0 if c.get('border_color')=='black' else 1, 0 if c.get('frame')=='2015' else 1,0 if not c.get('full_art') else 1,0 if c.get('rarity')=='common' else 1)
        grouped[set_code].append((preference,url))
    available=sorted(grouped,key=lambda x:(0 if x in PRIORITY else 1,PRIORITY.index(x) if x in PRIORITY else 9999,-len(grouped[x]),x))
    selected={key:[url for _,url in sorted(set(grouped[key]))[:2]] for key in available[:max_sets]}
    return selected

def build(catalog,output,max_sets=240,workers=8):
    selected=choose(catalog,max_sets)
    session=requests.Session();session.headers['User-Agent']='ManaVault/5.11.5 offline set-symbol reference builder'
    def task(item):
        set_code,urls=item;found=[]
        for url in urls:
            try:
                r=session.get(url,timeout=(8,15));r.raise_for_status()
                if len(r.content)>4_000_000:continue
                found.append(signature(r.content))
            except (requests.RequestException,ValueError,OSError):pass
        return set_code,found
    sets=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        for set_code,sigs in executor.map(task,selected.items()):
            if sigs:sets.append({'set':set_code,'f':sigs})
    if len(sets)<12:raise ValueError(f'Only {len(sets)} expansion-symbol references; refusing empty index')
    payload={'format':'manavault-symbol-v1','sets':sets}
    out=Path(output);out.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.NamedTemporaryFile('w',encoding='utf8',dir=out.parent,delete=False) as f:
        json.dump(payload,f,separators=(',',':'));stage=f.name
    os.replace(stage,out)
    print('Bundled set-symbol references:',len(sets),'of',len(selected),'attempted')
    return len(sets)

def main():
    p=argparse.ArgumentParser();p.add_argument('--catalog',default='app/src/main/assets/printing-catalog.sqlite.gz');p.add_argument('--output',default='app/src/main/assets/symbol-index.json');p.add_argument('--max-sets',type=int,default=240);p.add_argument('--workers',type=int,default=8);a=p.parse_args()
    build(a.catalog,a.output,a.max_sets,a.workers)
if __name__=='__main__':main()
