#!/usr/bin/env python3
"""Strict, standalone validation of the three assets that must ship in the APK.

This is also used as a cheap preflight by the Gradle asset preparation step.
It must not accept a JSON placeholder or a partial/failed Scryfall download.
"""
import argparse
import base64
import gzip
import json
import sqlite3
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'app/src/main/assets'


def verify(assets=ASSETS, minimum_printings=100000, minimum_references=12, minimum_bytes=2000000):
    assets = Path(assets)
    art_file = assets / 'visual-index.json'
    symbol_file = assets / 'symbol-index.json'
    db_file = assets / 'printing-catalog.dat'
    problems = []
    artworks = symbols = rows = 0
    try:
        art = json.loads(art_file.read_text(encoding='utf8'))
        items = art.get('cards', [])
        if art.get('format') != 'manavault-visual-v2' or not isinstance(items, list):
            raise ValueError('invalid artwork index format')
        for card in items:
            if not isinstance(card, dict) or not isinstance(card.get('f'), list) or len(card['f']) != 192:
                raise ValueError('artwork fingerprint must contain 192 RGB values')
            if 'e' in card and (not isinstance(card['e'],list) or len(card['e'])!=128):
                raise ValueError('visual descriptor must contain 128 brightness values')
        artworks = len(items)
        if artworks < minimum_references:
            problems.append(f'artwork index contains {artworks} references (minimum {minimum_references})')
    except (OSError, ValueError, KeyError, TypeError) as exc:
        problems.append(f'artwork index missing/invalid: {exc}')
    try:
        sym = json.loads(symbol_file.read_text(encoding='utf8'))
        items = sym.get('sets', [])
        if sym.get('format') != 'manavault-symbol-v3' or not isinstance(items, list):
            raise ValueError('invalid dedicated Scryfall set-icon index version')
        coverage = sym.get('coverage') or {}
        eligible = coverage.get('icon_eligible', 0)
        if not isinstance(eligible, int) or eligible < minimum_references:
            raise ValueError('missing physical-set coverage inventory')
        if len(items) / eligible < .85:
            raise ValueError(f'only {len(items)} of {eligible} available Scryfall set icons were generated')
        seen = set()
        for item in items:
            if not isinstance(item, dict) or not isinstance(item.get('set'), str) or not isinstance(item.get('shape'), str):
                raise ValueError('missing Scryfall expansion icon reference')
            if item['set'] in seen:
                raise ValueError('duplicate expansion icon entry')
            seen.add(item['set'])
            if len(base64.b64decode(item['shape'], validate=True)) != 576:
                raise ValueError('SVG-derived set-icon mask must contain 576 bytes')
        symbols = len(items)
        if symbols < minimum_references:
            problems.append(f'symbol index contains {symbols} sets (minimum {minimum_references})')
    except (OSError, ValueError, KeyError, TypeError, base64.binascii.Error) as exc:
        problems.append(f'set-symbol index missing/invalid: {exc}')
    try:
        if db_file.stat().st_size < minimum_bytes:
            raise ValueError('compressed catalog is too small')
        with tempfile.TemporaryDirectory(prefix='mv-asset-check-') as folder:
            target = Path(folder)/'printings.sqlite'
            with gzip.open(db_file, 'rb') as source, target.open('wb') as dest:
                while part := source.read(1024*1024):
                    dest.write(part)
            con = sqlite3.connect(f'file:{target}?mode=ro', uri=True)
            try:
                rows = con.execute('SELECT COUNT(*) FROM printings').fetchone()[0]
                references = con.execute('SELECT COUNT(*) FROM reference_fts').fetchone()[0]
                if references < minimum_printings:
                    raise ValueError(f'Incomplete offline OCR reference index: {references} rows')
                match = con.execute("SELECT COUNT(*) FROM reference_fts WHERE reference_fts MATCH 'wizard'").fetchone()[0]
                if not isinstance(match,int):
                    raise ValueError('Offline reference full-text search unavailable')
                integrity = con.execute('PRAGMA quick_check').fetchone()[0]
                if integrity != 'ok':
                    raise ValueError(f'SQLite integrity check failed: {integrity}')
                required = {'idx_names','idx_set_collector','idx_collector','idx_sets','idx_illustration','idx_oracle'}
                found = {i[1] for i in con.execute("PRAGMA index_list('printings')")}
                if not required.issubset(found):
                    raise ValueError(f'Missing catalog indexes: {sorted(required-found)}')
            finally:
                con.close()
        if rows < minimum_printings:
            problems.append(f'catalog contains only {rows} printings (minimum {minimum_printings})')
    except (OSError, ValueError, sqlite3.DatabaseError, EOFError) as exc:
        problems.append(f'native printing catalog missing/invalid: {exc}')
    return problems, {'printings': rows, 'artworks': artworks, 'sets': symbols}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--assets', type=Path, default=ASSETS)
    parser.add_argument('--minimum-printings', type=int, default=100000)
    parser.add_argument('--minimum-references', type=int, default=12)
    parser.add_argument('--minimum-bytes', type=int, default=2000000)
    args = parser.parse_args()
    problems, counts = verify(args.assets, args.minimum_printings, args.minimum_references, args.minimum_bytes)
    if problems:
        print('Offline assets NOT READY:')
        for item in problems:
            print('  -', item)
        raise SystemExit(1)
    print(f"Offline assets verified: {counts['printings']} printings, {counts['artworks']} artwork references, {counts['sets']} set-symbol groups")


if __name__ == '__main__':
    main()
