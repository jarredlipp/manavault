#!/usr/bin/env python3
"""Build a compact ManaVault artwork index from a local Scryfall bulk file.
Requires: pip install requests pillow. Usage: python tools/build_visual_index.py bulk.json output.json --limit 1000
Supports JSON arrays and JSONL/JSONL.GZ. Downloads art_crop images politely and caches them.
This is an optional index-building utility; it does not ship prebuilt artwork data.
"""
import argparse, gzip, hashlib, io, json, time
from concurrent.futures import ThreadPoolExecutor, as_completed
from threading import Lock
from pathlib import Path
import requests
from PIL import Image

def cards(path):
    opener=gzip.open if str(path).endswith('.gz') else open
    with opener(path,'rt',encoding='utf8') as f:
        first=f.read(1);f.seek(0)
        if first=='[':
            yield from json.load(f)
        else:
            for line in f:
                if line.strip():yield json.loads(line)

def main():
    a=argparse.ArgumentParser();a.add_argument('bulk');a.add_argument('output');a.add_argument('--limit',type=int,default=1000);a.add_argument('--delay',type=float,default=.15);args=a.parse_args()
    cache=Path('.manavault-art-cache');cache.mkdir(exist_ok=True)
    session=requests.Session();session.headers.update({'User-Agent':'ManaVaultVisualIndexer/0.1 (personal noncommercial tool)','Accept':'image/*'})
    out=[];seen=set();items=[]
    for c in cards(args.bulk):
        name=c.get('name');url=(c.get('image_uris') or {}).get('art_crop') or ((c.get('card_faces') or [{}])[0].get('image_uris') or {}).get('art_crop')
        if not name or not url or url in seen:continue
        seen.add(url);items.append((c,url))
        if args.limit>0 and len(items)>=args.limit:break
    print('Artwork images to process:',len(items),flush=True)
    def process(item):
        c,url=item
        path=cache/(hashlib.sha256(url.encode()).hexdigest()+'.img')
        try:
            if not path.exists():
                r=requests.get(url,timeout=40,headers={'User-Agent':'ManaVaultVisualIndexer/0.4 (personal MTG scanner)'})
                r.raise_for_status();path.write_bytes(r.content)
            im=Image.open(path).convert('RGB')
            # Optional import uses the same physical artwork window as CameraX.
            w,h=im.size
            im=im.crop((round(w*.09),round(h*.15),round(w*.91),round(h*.49)))
            features=[value for pixel in im.resize((8,8),Image.Resampling.BILINEAR).getdata() for value in pixel]
            return {'name':c['name'],'id':c.get('id'),'set':c.get('set'),'f':features}
        except Exception as e:
            return ('error',c.get('name'),str(e))
    errors=0
    with ThreadPoolExecutor(max_workers=6) as pool:
        for result in pool.map(process,items):
            if isinstance(result,dict):out.append(result)
            else:
                errors+=1
                if errors<=15:print('Skipped',result[1],result[2],flush=True)
            if len(out)>0 and len(out)%1000==0:print('Indexed',len(out),'failed',errors,flush=True)
    print('Finished artwork extraction:',len(out),'failed',errors,flush=True)
    Path(args.output).write_text(json.dumps({'format':'manavault-visual-v2','cards':out},separators=(',',':')))
    print('Saved',len(out),'references to',args.output)
if __name__=='__main__':main()
