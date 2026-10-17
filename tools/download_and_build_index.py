#!/usr/bin/env python3
"""Fetch Scryfall Unique Artwork bulk metadata, then build local fingerprints.
Run: pip install requests pillow && python tools/download_and_build_index.py --limit 0
This downloads artwork on the machine running it (e.g. GitHub Actions), not inside this ZIP.
"""
import argparse, json, gzip, os, subprocess, sys, time
from pathlib import Path
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

def main():
 p=argparse.ArgumentParser();p.add_argument('--limit',type=int,default=0);p.add_argument('--output',default='app/src/main/assets/visual-index.json');a=p.parse_args()
 s=requests.Session();s.mount('https://',HTTPAdapter(max_retries=Retry(total=5,backoff_factor=1,status_forcelist=[429,500,502,503,504])));s.headers.update({'User-Agent':'ManaVault-IndexBuilder/0.2 (personal MTG scanner)','Accept':'application/json;q=0.9,*/*;q=0.8'})
 meta=s.get('https://api.scryfall.com/bulk-data/unique_artwork',timeout=60);meta.raise_for_status();obj=meta.json()
 url=obj.get('jsonl_download_uri') or obj.get('download_uri')
 if not url:
  listing=s.get('https://api.scryfall.com/bulk-data',timeout=60);listing.raise_for_status()
  options=[x for x in listing.json().get('data',[]) if x.get('type')=='unique_artwork']
  url=next(((x.get('jsonl_download_uri') or x.get('download_uri')) for x in options if (x.get('jsonl_download_uri') or x.get('download_uri'))),None)
 if not url:
  print('Bulk metadata omitted download_uri; using paginated unique-art Scryfall search fallback',flush=True)
  raw=Path('unique-artwork-bulk.json')
  next_url='https://api.scryfall.com/cards/search?q=game%3Apaper&unique=art&order=name'
  count=0
  with raw.open('w',encoding='utf8') as f:
   f.write('[');first=True
   while next_url:
    response=s.get(next_url,timeout=90);response.raise_for_status();data=response.json()
    if not isinstance(data.get('data'),list):raise RuntimeError('Scryfall unique-art page has no data')
    for card in data['data']:
     if not first:f.write(',')
     json.dump(card,f,separators=(',',':'));first=False;count+=1
    next_url=data.get('next_page') if data.get('has_more') else None
    if data.get('has_more') and not next_url:raise RuntimeError('Scryfall art page omitted next_page')
    if count%2000<175:print('Fetched',count,'unique art entries',flush=True)
    if next_url:time.sleep(0.15)
   f.write(']')
  print('Paginated art export:',count,'records',flush=True)
  subprocess.run([sys.executable,'tools/build_visual_index.py',str(raw),a.output,'--limit',str(a.limit),'--delay','0'],check=True)
  raw.unlink(missing_ok=True)
  data=json.loads(Path(a.output).read_text());count=len(data['cards'])
  if count<30000:raise RuntimeError(f'Only {count} artworks indexed; expected at least 30,000')
  print('Built',count,'artwork fingerprints',flush=True)
  return
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
