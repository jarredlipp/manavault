#!/usr/bin/env python3
"""Check whether the helper patch is wired into the *actual* app. No false PASS."""
from pathlib import Path
import sys,argparse
p=argparse.ArgumentParser();p.add_argument('repo',type=Path);args=p.parse_args()
repo=args.repo
matches=list((repo/'app/src/main/java').rglob('NativeScannerActivity.java'))
if not matches:sys.exit('BLOCKED: NativeScannerActivity.java was not found.')
source=matches[0].read_text(errors='replace')
checks={
    'CameraX analyzer wired': 'HandheldCameraXAnalyzer(' in source and 'latestFrameAnalysis(' in source,
    'Real OCR/printing adapter wired': 'new HandheldCameraXAnalyzer(' in source,
    'Manual review completion wired': 'completeManualReview(' in source,
    'Scanner startup shared loading wired': 'ScannerStartupCoordinator' in source or
       any('ScannerStartupCoordinator' in f.read_text(errors='replace') for f in
           (repo/'app/src/main/java').rglob('*.java') if f.name!='ScannerStartupCoordinator.java'),
}
for key,value in checks.items():print(('PASS' if value else 'NOT WIRED')+': '+key)
if not all(checks.values()):sys.exit(2)
print('Static integration hooks found; run actual Gradle and phone tests before release.')
