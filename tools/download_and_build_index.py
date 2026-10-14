#!/usr/bin/env python3
"""Fetch Scryfall Unique Artwork bulk metadata, then build local fingerprints.
Run: pip install requests pillow && python tools/download_and_build_index.py --limit 0
This downloads artwork on the machine running it (e.g. GitHub Actions), not inside this ZIP.
"""
import argparse, json, gzip, os, subprocess, sys, time
from pathlib import Path
import requests

def main():
 p=argparse.ArgumentParser();p.add_argument('--limit',type=int,default=0);p.add_argument('--output',default='app/src/main/assets/visual-index.json');a=p.parse_args()
 s=requests.Session();s.headers.update({'User-Agent':'ManaVault-IndexBuilder/0.2 (personal MTG scanner)','Accept':'application/json;q=0.9,*/*;q=0.8'})
 meta=s.get('https://api.scryfall.com/bulk-data/unique_artwork',timeout=60);meta.raise_for_status();obj=meta.json()
 url=obj.get('jsonl_download_uri') or obj.get('download_uri')
 if not url:raise RuntimeError('Scryfall metadata has no bulk download URI')
 print('Downloading Unique Artwork bulk export:',url,flush=True)
 raw=Path('unique-artwork-bulk'+('.gz' if '.gz' in url else '.json'))
 with s.get(url,stream=True,timeout=120) as r:
  r.raise_for_status()
  with raw.open('wb') as f:
   for chunk in r.iter_content(1024*1024):
    if chunk:f.write(chunk)
 print('Downloaded',raw.stat().st_size,'bytes',flush=True)
 subprocess.run([sys.executable,'tools/build_visual_index.py',str(raw),a.output,'--limit',str(a.limit),'--delay','0.10'],check=True)
 raw.unlink(missing_ok=True)
 data=json.loads(Path(a.output).read_text());count=len(data['cards']);print('Built',count,'artwork fingerprints',flush=True)
 if count<30000:raise RuntimeError(f'Only {count} artworks indexed; expected at least 30,000 for full index')
if __name__=='__main__':main()
