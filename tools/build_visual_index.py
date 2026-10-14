#!/usr/bin/env python3
"""Build a compact ManaVault artwork index from a local Scryfall bulk file.
Requires: pip install requests pillow. Usage: python tools/build_visual_index.py bulk.json output.json --limit 1000
Supports JSON arrays and JSONL/JSONL.GZ. Downloads art_crop images politely and caches them.
This is an optional index-building utility; it does not ship prebuilt artwork data.
"""
import argparse, gzip, hashlib, io, json, time
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
    out=[];seen=set()
    for c in cards(args.bulk):
        name=c.get('name');url=(c.get('image_uris') or {}).get('art_crop') or ((c.get('card_faces') or [{}])[0].get('image_uris') or {}).get('art_crop')
        if not name or not url or url in seen:continue
        seen.add(url);path=cache/(hashlib.sha256(url.encode()).hexdigest()+'.img')
        try:
            if not path.exists():
                r=session.get(url,timeout=20);r.raise_for_status();path.write_bytes(r.content);time.sleep(args.delay)
            im=Image.open(path).convert('RGB').resize((8,8),Image.Resampling.BILINEAR)
            f=[value for pixel in im.getdata() for value in pixel]
            out.append({'name':name,'id':c.get('id'),'set':c.get('set'),'f':f})
        except Exception as e:print('Skipped',name,str(e))
        if len(out)%100==0 and out:print('Indexed',len(out))
        if len(out)>=args.limit:break
    Path(args.output).write_text(json.dumps({'format':'manavault-visual-v1','cards':out},separators=(',',':')))
    print('Saved',len(out),'references to',args.output)
if __name__=='__main__':main()
