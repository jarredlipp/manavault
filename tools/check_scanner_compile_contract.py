#!/usr/bin/env python3
"""Fast, self-contained native scanner Java contract check before offline downloads.

Production contract validation must not depend on separate Java unit-test files,
because those tests are run independently in CI and may be missing from a patch-
based GitHub checkout. A missing production class still fails immediately with an
explicit path. The built-in JVM probe checks behavioral and signature contracts.
This is not a complete Android compilation.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/com/manavault/app'
required = {
    'PhysicalCopyPolicy.java': ('needsCopyConfirmation',),
    'CaptureIdentityPolicy.java': ('needsIdentityReview',),
    'SymbolShapeMatcher.java': ('normalizeInk', 'extractInk', 'score'),
    'SplitCardLayoutPolicy.java': ('panels', 'titleRotation', 'zones'),
    'NativeScannerActivity.java': ('PhysicalCopyPolicy.needsCopyConfirmation', 'CaptureIdentityPolicy.needsIdentityReview'),
    'PrintingFingerprintVerifier.java': ('SymbolShapeMatcher.normalizeInk', 'SplitCardLayoutPolicy.panels'),
    'MainActivity.java': ('request.optBoolean("footerFirst",false)',),
}
for filename, symbols in required.items():
    path = java / filename
    if not path.is_file():
        raise SystemExit(f'MISSING native scanner dependency: {path.relative_to(root)}')
    source = path.read_text(encoding='utf-8')
    for symbol in symbols:
        if symbol not in source:
            raise SystemExit(f'INCOMPATIBLE native scanner dependency: {filename} lacks {symbol}')

# Run a compiled, self-contained contract probe: no dependence on standalone
# regression test files. The standalone tests remain in the repository and CI.
probe_source = '''package com.manavault.app;
public final class ScannerContractProbe {
    static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
    static byte[] rgb(int value) {
        byte[] bytes = new byte[192];
        java.util.Arrays.fill(bytes, (byte)value);
        return bytes;
    }
    public static void main(String[] args) {
        require(PhysicalCopyPolicy.needsCopyConfirmation("Wardens of the Cycle", "WARDENS of the CYCLE"),
            "repeated physical copy requires confirmation");
        require(!PhysicalCopyPolicy.needsCopyConfirmation("Wardens of the Cycle", "Savannah Lions"),
            "a different card advances automatically");
        long art = 0x1122334455667788L;
        require(!CaptureIdentityPolicy.needsIdentityReview("Stillness in Motion", "Endless Sands",
            art, art, 0x3333L, 0x9999L, 0x122334455L, 0x955223344L, rgb(10), rgb(95)),
            "artwork hash collision must not drop a new card");
        require(CaptureIdentityPolicy.needsIdentityReview("Depraved Harvester", "There // They're // Their",
            art, art, 0x12345L, 0x12345L, 0x45678L, 0x45678L, rgb(60), rgb(61)),
            "conflicting OCR over identical frame requires review");
        byte[] ink = new byte[SymbolShapeMatcher.SIZE];
        for (int y=7; y<17; y++) for (int x=7; x<17; x++)
            ink[y*SymbolShapeMatcher.SIDE+x] = (byte)255;
        byte[] normalized = SymbolShapeMatcher.normalizeInk(ink);
        require(normalized != null && SymbolShapeMatcher.score(normalized, normalized) > .95,
            "symbol normalization contract failed");
        require(SplitCardLayoutPolicy.panels(820,1145)[1][1] > .49f,
            "portrait split title panels should be upper/lower");
        require(SplitCardLayoutPolicy.titleRotation(820,1145) == 90,
            "portrait split title must be rotated upright");
        System.out.println("Native scanner compile contracts PASS: copy, identity, symbol normalization");
    }
}
'''
with TemporaryDirectory(prefix='mv-scanner-contract-') as temp:
    output = Path(temp)
    probe = output / 'ScannerContractProbe.java'
    probe.write_text(probe_source, encoding='utf-8')
    production = [java / filename for filename in (
        'PhysicalCopyPolicy.java', 'CaptureIdentityPolicy.java', 'SymbolShapeMatcher.java',
        'SplitCardLayoutPolicy.java'
    )]
    subprocess.run(['javac', '-d', str(output), *(str(s) for s in production), str(probe)], check=True)
    subprocess.run(['java', '-cp', str(output), 'com.manavault.app.ScannerContractProbe'], check=True)
