#!/usr/bin/env python3
"""Create a complete OFFLINE expansion-symbol shape atlas from Scryfall Sets API.

Unlike the previous sample-from-card-photo builder, this uses each set's
`icon_svg_uri` and emits a compact, normalized 24x24 alpha mask. Draftsim's
published set list is a human coverage checklist, NOT an image download source.
No network requests are performed during scanning.
"""
import argparse
import base64
import concurrent.futures
import gzip
import json
import os
import sqlite3
import tempfile
import time
from io import BytesIO
from pathlib import Path
from urllib.parse import urlparse

import cairosvg
import requests
from PIL import Image

FORMAT = 'manavault-symbol-v3'
SHAPE_PIXELS = 24 * 24
API = 'https://api.scryfall.com/sets'
USER_AGENT = 'ManaVault/5.12.12 (Magic card printing catalog; offline set icon preparation)'


def all_catalog_sets(path):
    """Return physical catalog set codes. The gzip input is the build asset."""
    with tempfile.TemporaryDirectory(prefix='mv-symbol-sets-') as temp:
        database = Path(temp) / 'catalog.sqlite'
        with gzip.open(path, 'rb') as source, database.open('wb') as output:
            while block := source.read(1024 * 1024):
                output.write(block)
        connection = sqlite3.connect(f'file:{database}?mode=ro', uri=True)
        try:
            return {code.lower() for (code,) in connection.execute('SELECT DISTINCT setCode FROM printings')
                    if isinstance(code, str) and code.strip()}
        finally:
            connection.close()


def sets_api(session=None):
    """Scryfall's canonical, structured set IDs and their individual SVG icons."""
    session = session or requests.Session()
    rows = []
    next_url = API
    visited = set()
    while next_url:
        if next_url in visited:
            raise ValueError('Scryfall sets pagination loop')
        visited.add(next_url)
        response = session.get(next_url, headers={'User-Agent': USER_AGENT, 'Accept': 'application/json'}, timeout=(10, 35))
        response.raise_for_status()
        data = response.json()
        if not isinstance(data.get('data'), list):
            raise ValueError('Scryfall returned an invalid sets response')
        rows.extend(data['data'])
        next_url = data.get('next_page') if data.get('has_more') else None
        if next_url and urlparse(next_url).hostname != 'api.scryfall.com':
            raise ValueError('Unexpected Scryfall sets pagination host')
        if next_url:
            time.sleep(.10)
    return rows


def icon_mask(svg_bytes):
    """Normalize vector icon to an antialiased foreground mask, independent of color."""
    if len(svg_bytes) > 300_000 or b'<svg' not in svg_bytes[:4096].lower():
        raise ValueError('Invalid or unexpectedly large Scryfall SVG')
    png = cairosvg.svg2png(bytestring=svg_bytes, output_width=128, output_height=128)
    image = Image.open(BytesIO(png)).convert('RGBA')
    alpha = image.getchannel('A')
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError('Empty expansion icon')
    fitted = alpha.crop(bounds)
    width, height = fitted.size
    scale = min(20 / width, 20 / height)
    fitted = fitted.resize((max(1, round(width * scale)), max(1, round(height * scale))), Image.Resampling.LANCZOS)
    icon = Image.new('L', (24, 24), 0)
    icon.paste(fitted, ((24 - fitted.width) // 2, (24 - fitted.height) // 2))
    raw = icon.tobytes()
    if len(raw) != SHAPE_PIXELS or sum(1 for b in raw if b > 90) < 16:
        raise ValueError('Expansion icon is too sparse')
    return base64.b64encode(raw).decode('ascii')


def fetch_icon(entry, timeout=(8, 24), http_get=None):
    code, icon_url, name = entry
    parsed = urlparse(icon_url)
    if parsed.scheme != 'https' or parsed.hostname not in ('svgs.scryfall.io', 'cards.scryfall.io'):
        return code, name, None, 'untrusted icon host'
    getter = http_get or requests.get
    for attempt in range(3):
        try:
            response = getter(icon_url, timeout=timeout, headers={'User-Agent': USER_AGENT, 'Accept': 'image/svg+xml'})
            response.raise_for_status()
            return code, name, icon_mask(response.content), None
        except (requests.RequestException, ValueError, OSError) as error:
            problem = str(error)[:160]
            if attempt < 2:
                time.sleep(.35 * (attempt + 1))
    return code, name, None, problem


def build(catalog, output, workers=4, set_rows=None, http_get=None, minimum_coverage=.85):
    codes = all_catalog_sets(catalog)
    metadata = set_rows if set_rows is not None else sets_api()
    available = {}
    for row in metadata:
        code = str(row.get('code') or '').lower()
        icon = row.get('icon_svg_uri') or ''
        if code in codes and icon and code not in available:
            available[code] = (code, icon, row.get('name') or code)
    if len(available) < 12:
        raise ValueError(f'Only {len(available)} catalog sets have usable Scryfall icon URLs')
    refs = []
    missing_icons = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        for code, name, shape, problem in pool.map(lambda item: fetch_icon(item, http_get=http_get), available.values()):
            if shape:
                refs.append({'set': code, 'name': name, 'shape': shape})
            else:
                missing_icons.append({'set': code, 'reason': problem})
    ratio = len(refs) / len(available)
    if ratio < minimum_coverage:
        raise ValueError(f'Scryfall symbol coverage {len(refs)}/{len(available)} ({ratio:.0%}) is below {minimum_coverage:.0%}. Missing: {missing_icons[:8]}')
    payload = {
        'format': FORMAT, 'source': 'Scryfall Sets API icon_svg_uri',
        'coverage': {'catalog_set_count': len(codes), 'icon_eligible': len(available),
                     'downloaded': len(refs), 'ratio': round(ratio, 4),
                     'missing_set_codes': sorted(codes - set(available)),
                     'failed_icons': missing_icons},
        'sets': sorted(refs, key=lambda row: row['set'])
    }
    target = Path(output)
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode='w', encoding='utf8', dir=target.parent, delete=False) as temp:
        json.dump(payload, temp, separators=(',', ':'))
        stage = temp.name
    os.replace(stage, target)
    print(f'Prepared {len(refs)} / {len(available)} available physical-set SVG symbols ({ratio:.1%}); {len(codes)} catalog sets.')
    print('Draftsim audit: compare Draftsim set names with the missing_set_codes in the generated atlas coverage section; no Draftsim images were downloaded.')
    return len(refs)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--catalog', default='app/src/main/assets/printing-catalog.dat')
    parser.add_argument('--output', default='app/src/main/assets/symbol-index.json')
    parser.add_argument('--workers', type=int, default=4)
    # --max-sets retained as a harmless legacy CLI option. Do not truncate the catalog.
    parser.add_argument('--max-sets', type=int, default=0)
    args = parser.parse_args()
    build(args.catalog, args.output, workers=args.workers)


if __name__ == '__main__':
    main()
