#!/usr/bin/env python3
"""Zero-dependency Android DEX wiring audit for ManaVault.

Usage: python tools/verify_scanner_wiring.py /path/to/app-debug.apk
    or python tools/verify_scanner_wiring.py /path/to/ManaVault-Android-debug.zip

Fails if scanner helper classes are packaged but absent from the DEX method
references of the live NativeScannerActivity's dex. This is a *necessary*,
not sufficient, condition for complete runtime integration.
"""
import argparse
import contextlib
import json
from pathlib import Path
import shutil
import struct
import tempfile
import zipfile

ACTIVITY = 'Lcom/manavault/app/NativeScannerActivity;'
HELPERS = (
    'Lcom/manavault/app/scanner/HandheldCameraXAnalyzer;',
    'Lcom/manavault/app/scanner/PrintingEvidenceResolver;',
    'Lcom/manavault/app/scanner/ScannerStartupCoordinator;',
)
SINGLETON_POLICY_FIELDS = ('authoritativePrintingCount', 'completeCatalog')


def uleb(data, pos):
    value, shift = 0, 0
    for _ in range(5):
        b = data[pos]
        pos += 1
        value |= (b & 127) << shift
        if not b & 128:
            return value, pos
        shift += 7
    raise ValueError('Malformed ULEB128')


def parse_dex(data, name):
    if data[:4] != b'dex\n':
        raise ValueError(f'{name}: not DEX')
    def u16(offset): return struct.unpack_from('<H', data, offset)[0]
    def u32(offset): return struct.unpack_from('<I', data, offset)[0]
    count, start = u32(56), u32(60)
    string_indices = [u32(start + 4 * i) for i in range(count)]
    strings = []
    for pos in string_indices:
        _, start_string = uleb(data, pos)
        end_string = data.index(b'\0', start_string)
        strings.append(data[start_string:end_string].decode('utf-8', 'replace'))
    count, start = u32(64), u32(68)
    types = [strings[u32(start + 4 * i)] for i in range(count)]
    count, start = u32(88), u32(92)
    methods = [(types[u16(start + 8 * i)], strings[u32(start + 8 * i + 4)]) for i in range(count)]
    count, start = u32(96), u32(100)
    defined = [types[u32(start + 32 * i)] for i in range(count)]
    return {'dex': name, 'defined': defined, 'methods': methods, 'strings': strings}


def analyze(apk):
    with zipfile.ZipFile(apk) as z:
        dex_names = sorted(n for n in z.namelist() if n.startswith('classes') and n.endswith('.dex'))
        if not dex_names:
            raise ValueError('No DEX files found in the APK')
        dexes = [parse_dex(z.read(n), n) for n in dex_names]
        owners = [d for d in dexes if ACTIVITY in d['defined']]
        defined = set().union(*(set(d['defined']) for d in dexes))
        if len(owners) != 1:
            raise ValueError(f'Expected one NativeScannerActivity definition, found {len(owners)}')
        owner = owners[0]
        external_references = sorted(set(c for c, method in owner['methods'] if c in HELPERS))
        # A helper may be called through a separate adapter; this check is
        # intentionally strict to catch silently unwired production builds.
        helper_presence = {h.split('/')[-1][:-1]: h in defined for h in HELPERS}
        resolver_owner = [d for d in dexes if 'Lcom/manavault/app/scanner/PrintingEvidenceResolver$Evidence;' in d['defined']]
        evidence_strings = set(resolver_owner[0]['strings']) if resolver_owner else set()
        field_presence = {f: f in evidence_strings for f in SINGLETON_POLICY_FIELDS}
        versions = sorted({s for d in dexes for s in d['strings'] if s.startswith('CameraX · ManaVault ')})
        result = {
            'apk': str(apk),
            'activity_dex': owner['dex'],
            'dex_count': len(dexes),
            'version_labels': versions,
            'helper_classes_bundled': helper_presence,
            'helper_method_references_in_activity_dex': [x.split('/')[-1][:-1] for x in external_references],
            'single_printing_resolver_fields': field_presence,
            'missing_live_scanner_method_references': [h.split('/')[-1][:-1] for h in HELPERS if h not in external_references],
            'checks_passed': all(helper_presence.values()) and len(external_references) == len(HELPERS) and all(field_presence.values()),
        }
        return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('input', type=Path)
    p.add_argument('--json', type=Path, help='Optional output report location')
    args = p.parse_args()
    if not args.input.exists():
        p.error(f'not found: {args.input}')
    with tempfile.TemporaryDirectory() as t:
        if args.input.suffix.lower() == '.apk':
            path = args.input
            report_name = args.input.name
        else:
            with zipfile.ZipFile(args.input) as z:
                members = [n for n in z.namelist() if n.lower().endswith('.apk') and not n.endswith('/')]
                if len(members) != 1:
                    p.error(f'Expected one APK inside ZIP; found {len(members)}')
                report_name = members[0]
                path = Path(t)/'app.apk'
                with z.open(members[0]) as inf, path.open('wb') as outf:
                    shutil.copyfileobj(inf, outf)
        result = analyze(path)
        result['apk'] = f'{args.input.name}::{report_name}'
    print(json.dumps(result, indent=2))
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    if not result['checks_passed']:
        print('\nFAIL: APK scanner integrations are missing or stale. Do NOT release this build.')
        return 2
    print('\nPASS: Required DEX references/fields exist. Device tests are still required.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
