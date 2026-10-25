#!/usr/bin/env python3
"""Fail quickly on mismatched native scanner source before expensive catalog downloads.

This is a narrowly scoped Java source contract check, not a full Android compile.
It catches missing scanner policy classes and set-symbol methods that caused
v5.12.31/v5.12.32 GitHub Actions Javac failures.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/com/manavault/app'
tools = root / 'tools'
required = {
    'PhysicalCopyPolicy.java': ('needsCopyConfirmation',),
    'CaptureIdentityPolicy.java': ('needsIdentityReview',),
    'SymbolShapeMatcher.java': ('normalizeInk', 'extractInk', 'score'),
}
for filename, symbols in required.items():
    path = java / filename
    if not path.is_file():
        raise SystemExit(f'MISSING native scanner dependency: {path.relative_to(root)}')
    source = path.read_text(encoding='utf-8')
    for symbol in symbols:
        if symbol not in source:
            raise SystemExit(f'INCOMPATIBLE native scanner dependency: {filename} lacks {symbol}')

scanner = (java / 'NativeScannerActivity.java').read_text(encoding='utf-8')
verifier = (java / 'PrintingFingerprintVerifier.java').read_text(encoding='utf-8')
assert 'PhysicalCopyPolicy.needsCopyConfirmation' in scanner
assert 'CaptureIdentityPolicy.needsIdentityReview' in scanner
assert 'SymbolShapeMatcher.normalizeInk' in verifier

with TemporaryDirectory(prefix='mv-scanner-contract-') as temp:
    output = Path(temp)
    sources = [java / filename for filename in required]
    sources.extend([
        tools / 'PhysicalCopyPolicyTest.java',
        tools / 'CaptureIdentityPolicyTest.java',
    ])
    subprocess.run(['javac', '-d', str(output), *(str(s) for s in sources)], check=True)
    for test in ('PhysicalCopyPolicyTest', 'CaptureIdentityPolicyTest'):
        subprocess.run(['java', '-cp', str(output), f'com.manavault.app.{test}'], check=True)
    harness = output / 'com/manavault/app/SymbolContractProbe.java'
    harness.parent.mkdir(parents=True, exist_ok=True)
    harness.write_text('''package com.manavault.app;
class SymbolContractProbe {
    public static void main(String[] args) {
        byte[] ink = new byte[SymbolShapeMatcher.SIZE];
        for (int y=7;y<17;y++) for (int x=7;x<17;x++) ink[y*24+x]=(byte)255;
        byte[] normalized = SymbolShapeMatcher.normalizeInk(ink);
        if (normalized==null || SymbolShapeMatcher.score(normalized,normalized)<.95)
            throw new AssertionError("Symbol contract result is invalid");
    }
}
''', encoding='utf-8')
    subprocess.run(['javac', '-cp', str(output), '-d', str(output), str(harness)], check=True)
    subprocess.run(['java', '-cp', str(output), 'com.manavault.app.SymbolContractProbe'], check=True)
print('Native scanner compile contracts PASS: copy safety, capture identity, symbol normalization')
