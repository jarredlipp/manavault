#!/usr/bin/env python3
"""Strict, dependency-free ManaVault APK scanner wiring verifier.

Unlike a DEX method_ids scan, this decodes method bodies from class_data/code_item.
A method present in the DEX constant pool does NOT mean it is called. This
verifier proves that the live activity calls the new camera pipeline and that
its analyzer invokes the exact-printing resolver; it does not prove behavioral
correctness on a phone.
"""
import argparse
import json
from pathlib import Path
import shutil
import struct
import tempfile
import zipfile

APP = 'Lcom/manavault/app/'
ACTIVITY = APP + 'NativeScannerActivity;'
ANALYZER = APP + 'scanner/HandheldCameraXAnalyzer;'
STARTUP = APP + 'scanner/ScannerStartupCoordinator;'
RESOLVER = APP + 'scanner/PrintingEvidenceResolver;'
LIGHTING = APP + 'scanner/CameraXLightingController;'
BRIDGE = APP + 'scanner/NativeScanCameraSession;'
EVIDENCE = RESOLVER[:-1] + '$Evidence;'


def uleb(blob, pos):
    result = 0
    for shift in range(0, 35, 7):
        b = blob[pos]
        pos += 1
        result |= (b & 0x7f) << shift
        if b & 0x80 == 0:
            return result, pos
    raise ValueError('malformed DEX LEB128')


def instruction_width(insns, pos):
    """Size of an instruction in 16-bit code units, including payloads."""
    w = insns[pos]
    op = w & 255
    if op == 0:
        signature = w >> 8
        if signature == 0x01:  # packed-switch-payload
            return 4 + 2 * insns[pos + 1]
        if signature == 0x02:  # sparse-switch-payload
            return 2 + 4 * insns[pos + 1]
        if signature == 0x03:  # fill-array-data-payload
            element_width = insns[pos + 1]
            size = insns[pos + 2] | (insns[pos + 3] << 16)
            return 4 + (element_width * size + 1) // 2
        if signature != 0:
            raise ValueError('unknown DEX payload at code unit ' + str(pos))
        return 1
    if op in {0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1a,
              0x1c, 0x1f, 0x20, 0x22, 0x23, 0x29}:
        return 2
    if 0x2d <= op <= 0x3d or 0x44 <= op <= 0x6d or 0x90 <= op <= 0xaf or 0xd0 <= op <= 0xe2:
        return 2
    if op in {0x03, 0x06, 0x09, 0x14, 0x17, 0x1b, 0x24, 0x25,
              0x26, 0x2a, 0x2b, 0x2c, 0xfc, 0xfd}:
        return 3
    if op in {0xfa, 0xfb}:
        return 4
    if op == 0x18:
        return 5
    if 0x6e <= op <= 0x72 or 0x74 <= op <= 0x78:
        return 3
    if op in {0xfe, 0xff}:
        return 2
    # Standard one-unit instructions; unrecognized or optimized opcodes fail
    # closed instead of treating their operands as possible fake invokes.
    if op <= 0x12 or op in {0x1d,0x1e,0x21,0x27,0x28} or \
       0x7b <= op <= 0x8f or 0xb0 <= op <= 0xcf:
        return 1
    raise ValueError('unsupported DEX opcode 0x%02x in method body' % op)


def invoked_method_indices(insns):
    calls = []
    pos = 0
    while pos < len(insns):
        opcode = insns[pos] & 255
        width = instruction_width(insns, pos)
        if pos + width > len(insns):
            raise ValueError('truncated DEX instruction')
        if 0x6e <= opcode <= 0x72 or 0x74 <= opcode <= 0x78 or opcode in {0xfa, 0xfb}:
            calls.append(insns[pos + 1])
        pos += width
    return calls


def parse_dex(blob, name):
    if not blob.startswith(b'dex\n'):
        raise ValueError(name + ' is not a DEX file')
    u16 = lambda at: struct.unpack_from('<H', blob, at)[0]
    u32 = lambda at: struct.unpack_from('<I', blob, at)[0]
    str_count, str_off = u32(0x38), u32(0x3c)
    strings = []
    for i in range(str_count):
        start = u32(str_off + i*4)
        _, start = uleb(blob, start)
        end = blob.index(b'\0', start)
        strings.append(blob[start:end].decode('utf-8', 'replace'))
    typ_count, typ_off = u32(0x40), u32(0x44)
    types = [strings[u32(typ_off + i*4)] for i in range(typ_count)]
    meth_count, meth_off = u32(0x58), u32(0x5c)
    methods = []
    for i in range(meth_count):
        class_idx, proto_idx, string_idx = struct.unpack_from('<HHI', blob, meth_off+i*8)
        methods.append((types[class_idx], strings[string_idx]))
    n_classes, class_off = u32(0x60), u32(0x64)
    classes = {}
    for c in range(n_classes):
        at = class_off+c*32
        typename = types[u32(at)]
        data_off = u32(at+24)
        calls = set()
        per_method = {}
        if data_off:
            counts, pos = [], data_off
            for _ in range(4):
                n, pos = uleb(blob,pos)
                counts.append(n)
            for _ in range(counts[0]+counts[1]):
                _, pos = uleb(blob,pos)
                _, pos = uleb(blob,pos)
            for count in counts[2:]:
                method_id = 0
                for _ in range(count):
                    delta, pos = uleb(blob,pos)
                    method_id += delta
                    _, pos = uleb(blob,pos)  # access flags
                    code_off, pos = uleb(blob,pos)
                    if code_off:
                        n_insns = u32(code_off+12)
                        fmt = '<%dH' % n_insns
                        insns = struct.unpack_from(fmt,blob,code_off+16)
                        found = {(methods[m][0],methods[m][1]) for m in invoked_method_indices(insns)}
                        calls |= found
                        per_method[methods[method_id][1]] = found
        classes[typename]={'calls':calls,'methods':per_method}
    return {'name':name,'classes':classes,'strings':strings}


def audit(apk):
    with zipfile.ZipFile(apk) as z:
        dexes = [parse_dex(z.read(f),f) for f in sorted(z.namelist()) if f.startswith('classes') and f.endswith('.dex')]
    classes = {}
    for dex in dexes:
        for k,v in dex['classes'].items():
            if k in classes:
                raise ValueError('duplicate class in APK: '+k)
            classes[k] = v
    # R8/D8 may generate synthetic lambdas, so include these classes in the
    # activity call root. A helper class simply sitting in the APK is NOT a call.
    activity_roots = [k for k in classes if k == ACTIVITY or
                      k.startswith(ACTIVITY[:-1]+'$$ExternalSyntheticLambda') or
                      k.startswith(ACTIVITY[:-1]+'$')]
    if ACTIVITY not in classes:
        raise ValueError('NativeScannerActivity class is missing')
    activity_calls = set().union(*(classes[k]['calls'] for k in activity_roots))
    analyzer_calls = classes.get(ANALYZER,{}).get('calls',set())
    bridge_calls = classes.get(BRIDGE,{}).get('calls',set())
    resolver_classes = [k for k in classes if k.startswith(RESOLVER[:-1])]
    all_strings = set().union(*(set(d['strings']) for d in dexes))
    resolver_fields = {k:k in all_strings for k in ('authoritativePrintingCount','completeCatalog')}
    def called(calls,cls):
        return sorted(set(m for c,m in calls if c == cls))
    actual = {
        'native_activity_present':True,
        'activity_direct_analyzer_calls':called(activity_calls,ANALYZER),
        'activity_bridge_calls':called(activity_calls,BRIDGE),
        'bridge_analyzer_calls':called(bridge_calls,ANALYZER),
        'bridge_startup_calls':called(bridge_calls,STARTUP),
        'activity_direct_startup_calls':called(activity_calls,STARTUP),
        'analyzer_direct_resolver_calls':called(analyzer_calls,RESOLVER),
        'analyzer_direct_lighting_calls':called(analyzer_calls,LIGHTING),
        'eligible_singleton_fields':resolver_fields,
        'helper_classes_present':{
            'HandheldCameraXAnalyzer':ANALYZER in classes,
            'ScannerStartupCoordinator':STARTUP in classes,
            'PrintingEvidenceResolver':RESOLVER in classes,
            'CameraXLightingController':LIGHTING in classes,
        },
        'dex_files':[d['name'] for d in dexes],
    }
    actual['checks_passed'] = all(actual['helper_classes_present'].values()) and \
        (
          bool(actual['activity_direct_analyzer_calls']) and bool(actual['activity_direct_startup_calls'])
          or bool(actual['activity_bridge_calls']) and bool(actual['bridge_analyzer_calls']) and bool(actual['bridge_startup_calls'])
        ) and \
        bool(actual['analyzer_direct_resolver_calls']) and \
        bool(actual['analyzer_direct_lighting_calls']) and \
        all(resolver_fields.values())
    return actual


def main():
    a=argparse.ArgumentParser(description=__doc__)
    a.add_argument('input',type=Path,help='APK or ZIP containing exactly one APK')
    a.add_argument('--json',type=Path)
    args=a.parse_args()
    if not args.input.is_file():
        a.error('file not found: '+str(args.input))
    with tempfile.TemporaryDirectory() as tmp:
        if args.input.suffix.lower()=='.apk':
            path=args.input
        else:
            with zipfile.ZipFile(args.input) as z:
                members=[n for n in z.namelist() if n.lower().endswith('.apk')]
                if len(members)!=1:
                    a.error('expected exactly one APK in ZIP')
                path=Path(tmp)/'app.apk'
                with z.open(members[0]) as src, open(path,'wb') as dst:
                    shutil.copyfileobj(src,dst)
        data=audit(path)
    data['input']=args.input.name
    print(json.dumps(data,indent=2))
    if args.json:
        args.json.parent.mkdir(parents=True,exist_ok=True)
        args.json.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    if not data['checks_passed']:
        print('FAIL: NativeScannerActivity is not wired to the new scanner pipeline. Block release.')
        return 2
    print('PASS: Native scanner calls are wired in DEX. Device test required.')
    return 0

if __name__=='__main__':
    raise SystemExit(main())
