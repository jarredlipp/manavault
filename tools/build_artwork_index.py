#!/usr/bin/env python3
"""Bundled artwork fingerprint seed, deduplicated by Scryfall illustration ID.
The full printing catalog remains available for exact set/collector lookups.
This bounded art seed is advisory, never authoritative for variant/foil identity.
"""
import argparse, base64, concurrent.futures, gzip, json, os, sqlite3, tempfile
from collections import Counter, defaultdict
from io import BytesIO
from pathlib import Path
import requests
from PIL import Image

IMPORTANT_NAMES='''Forest Plains Island Swamp Mountain'''.split() + [
'Webstrike Elite','Light of Day','River Hoopoe','Read the Bones','Professor of Zoomancy',
'Far // Away','Never // Return','Tuinvale Treefolk // Oaken Boon','Rosethorn Acolyte // Seasonal Ritual',
'Desperate Farmer // Depraved Harvester','Fugitive Codebreaker','Jolting Merfolk',
'Sliver Hivelord','Sliver Legion','Sliver Overlord','Sliver Gravemother',
'Wick, the Whorled Mind','Huatli, Poet of Unity','Saheeli, Radiant Creator',
'Shorikai, Genesis Engine','Sol Ring','Arcane Signet','Counterspell','Lightning Bolt',
'Colossal Dreadmaw','Llanowar Elves','Defile','Duress','Thief\'s Knife',
'Vampiric Rites','Warden of Geometries','Lazotep Reaver','Tigereye Cameo',
'Enigma Drake','Ultros, Obnoxious Octopus','Skymarch Bloodletter',
'Rakdos Shred-Freak','Knightly Valor','Deep Reconnaissance','Bloodied Ghost',
'Prismite','Aether Adept','Joyful Stormsculptor','Bloodfire Expert',
'Jace','Invasion of Muraganda // Primordial Plasm','Hada Spy Patrol',
'Living Lands','Stillness in Motion','Webstrike Elite','Fanatic of Xenagos',
]

def names(records,max_refs):
    priority=set(x.casefold() for x in IMPORTANT_NAMES)
    grouped=defaultdict(dict);frequency=Counter()
    for c in records:
        name=c.get('name','');key=c.get('nameKey','');art=c.get('illustration_id') or ''
        image=c.get('image_url') or ''
        if not name or not key or not art or not image.startswith('https://cards.scryfall.io/'):
            continue
        if c.get('digital') or c.get('lang') not in (None,'en'):continue
        frequency[key]+=1
        if len(grouped[key])<4 and art not in grouped[key]:
            grouped[key][art]=(c['id'],name,c.get('set',''),image,art)
    order=sorted(grouped,key=lambda k:(0 if k in priority else 1,-frequency[k],k))
    output=[]
    for key in order:
        for group in grouped[key].values():
            output.append(group)
            if len(output)>=max_refs:return output
    return output

def catalog_cards(path):
    with gzip.open(path,'rb') as source:
        with tempfile.NamedTemporaryFile(suffix='.sqlite') as file:
            while(chunk:=source.read(1024*1024)):file.write(chunk)
            file.flush();db=sqlite3.connect(file.name)
            for (raw,) in db.execute('SELECT data FROM printings'):
                yield json.loads(raw)
            db.close()

def crop_reference_art(image):
    '''Match VisualMatcher.artRegion() coordinates on the Android card crop.'''
    full=image.convert('RGB')
    width,height=full.size
    return full.crop((round(width*.09),round(height*.15),
                      round(width*.91),round(height*.49)))

def build(catalog,output,max_refs=300,workers=8):
    chosen=names(catalog_cards(catalog),max_refs)
    def download(item):
        id,name,set_code,url,art=item
        try:
            result=requests.get(url,timeout=(10,17),headers={'User-Agent':'ManaVault/5.12.0 artwork reference seed'})
            result.raise_for_status()
            if len(result.content)>3_000_000:return None
            with Image.open(BytesIO(result.content)) as image:
                # image_url is a *full card*, while VisualMatcher.artRegion()
                # fingerprints only x=9%-91%, y=15%-49%. Matching a card-art
                # crop against a whole-card thumbnail made every score poor.
                image=crop_reference_art(image)
                bitmap=image.resize((8,8),Image.Resampling.BILINEAR)
                rgb=[v for pixel in bitmap.getdata() for v in pixel]
                details=image.convert('RGB').resize((16,8),Image.Resampling.BILINEAR)
                brightness=[(r*299+g*587+b*114)/1000 for r,g,b in details.getdata()]
                mean=sum(brightness)/128
                sigma=max(16,(sum((v-mean)**2 for v in brightness)/128)**.5)
                signature=[max(0,min(255,round(128+(v-mean)*48/sigma))) for v in brightness]
            return {'id':id,'name':name,'set':set_code,'illustration_id':art,'f':rgb,'e':signature}
        except (OSError,requests.RequestException,ValueError):return None
    cards=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        for result in executor.map(download,chosen):
            if result:cards.append(result)
    if len(cards)<12:raise ValueError('Insufficient artwork references; refusing broken seed index')
    path=Path(output);path.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.NamedTemporaryFile('w',encoding='utf8',dir=path.parent,delete=False) as tmp:
        json.dump({'format':'manavault-visual-v2','cards':cards},tmp,separators=(',',':'));stage=tmp.name
    os.replace(stage,path)
    print('Bundled artwork fingerprints:',len(cards),'of',len(chosen),'attempted')
    return len(cards)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--catalog',default='app/src/main/assets/printing-catalog.dat');p.add_argument('--output',default='app/src/main/assets/visual-index.json');p.add_argument('--max',type=int,default=300);p.add_argument('--workers',type=int,default=8);a=p.parse_args()
    build(a.catalog,a.output,a.max,a.workers)
