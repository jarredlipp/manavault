#!/usr/bin/env python3
"""Build a compact, non-proprietary Scryfall artwork fingerprint seed index.

The CI workflow tries this on each build. Missing artwork or a network failure never
removes the bundled placeholder, and the Android app always retains OCR fallback.
Only tiny 8x8 RGB fingerprints are packaged, not copyrighted image files.
"""
import argparse
import io
import json
import os
import tempfile
import time
from pathlib import Path

import requests
from PIL import Image
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

SEED_NAMES = [
    'Forest', 'Plains', 'Island', 'Swamp', 'Mountain',
    'Tuinvale Treefolk', 'Rosethorn Acolyte', 'Freewind Equenaut',
    'Webstrike Elite', 'Invasion of Muraganda', 'Prismite',
    'Cryptic Spires', 'Act of Treason', 'New Horizons',
    'Beastbreaker of Bala Ged', 'Canyon Lurkers',
    'Ultros, Obnoxious Octopus', 'Read the Bones', 'Consume Strength',
    'Sliver Hivelord', 'Malevolent Noble', 'Garenbrig Carver',
    'Light of Day', 'Compulsion', 'Desperate Farmer',
    'Naturalize', 'Plummet', 'Sparktongue Dragon',
    'Spore Frog', 'Jace, the Mind Sculptor',
    'Shorikai, Genesis Engine', 'Huatli, Poet of Unity',
    'Saheeli, Radiant Creator', 'Wick, the Whorled Mind',
    'Sol Ring', 'Arcane Signet', 'Counterspell',
    'Llanowar Elves', 'Colossal Dreadmaw', 'Lightning Bolt',
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', default='app/src/main/assets/visual-index.json')
    parser.add_argument('--max', type=int, default=len(SEED_NAMES))
    args = parser.parse_args()
    output = Path(args.output)
    session = requests.Session()
    retry = Retry(total=2, backoff_factor=.5, status_forcelist=[429, 500, 502, 503, 504])
    session.mount('https://', HTTPAdapter(max_retries=retry))
    session.headers.update({'User-Agent': 'ManaVault/5.9.2 (noncommercial native scanner test index)',
                            'Accept': 'application/json, image/*;q=0.8'})
    entries, failed = [], []
    for name in SEED_NAMES[:max(0, args.max)]:
        try:
            response = session.get('https://api.scryfall.com/cards/named',
                                   params={'exact': name}, timeout=10)
            response.raise_for_status()
            card = response.json()
            url = (card.get('image_uris') or {}).get('art_crop')
            if not url:
                for face in card.get('card_faces') or []:
                    url = (face.get('image_uris') or {}).get('art_crop')
                    if url:
                        break
            if not url:
                raise ValueError('No artwork reference')
            image = session.get(url, timeout=12)
            image.raise_for_status()
            if len(image.content) > 3_000_000:
                raise ValueError('Image too large')
            with Image.open(io.BytesIO(image.content)) as original:
                thumb = original.convert('RGB').resize((8, 8), Image.Resampling.BILINEAR)
                rgb = [v for pixel in thumb.getdata() for v in pixel]
            entries.append({'name': card['name'], 'id': card['id'], 'set': card.get('set', ''), 'f': rgb})
        except (requests.RequestException, ValueError, KeyError, OSError) as ex:
            failed.append((name, str(ex)[:100]))
        time.sleep(.15)
    if not entries:
        print('No seed artworks downloaded; leaving existing index untouched and retaining OCR fallback.')
        return
    # Preserve an explicitly supplied comprehensive index rather than replacing
    # it with a smaller starter set.
    if output.exists():
        try:
            previous = json.loads(output.read_text(encoding='utf8'))
            if len(previous.get('cards', [])) > len(entries):
                print('Keeping the existing larger artwork index.');return
        except (ValueError, OSError):
            pass
    output.parent.mkdir(parents=True, exist_ok=True)
    payload = {'format': 'manavault-visual-v1', 'cards': entries}
    with tempfile.NamedTemporaryFile('w', encoding='utf8', dir=output.parent,
                                     delete=False, suffix='.json') as temp:
        json.dump(payload, temp, separators=(',', ':'))
        temp_path = Path(temp.name)
    os.replace(temp_path, output)
    print(f'Bundled {len(entries)} starter artworks; {len(failed)} unavailable.')
    for name, error in failed[:8]:
        print(f'  Skip {name}: {error}')


if __name__ == '__main__':
    main()
