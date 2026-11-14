#!/usr/bin/env python3
"""Strict static guard for the native production wiring of camera improvements.

These checks are intentionally conservative. Static strings do not prove correct
runtime operation; a pass means only that the expected integration hooks exist.
"""
import argparse
from pathlib import Path
import sys

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('source_root',type=Path)
a=p.parse_args()
root=a.source_root
activities=list((root/'app/src/main/java').rglob('NativeScannerActivity.java'))
if len(activities)!=1:sys.exit('BLOCKED: expected exactly one NativeScannerActivity.java in app source')
source=activities[0].read_text(encoding='utf-8',errors='replace')
# It is valid to move these methods into a bridge, but then update this guard
# to inspect that actual bridge and its call sites rather than disabling it.
files=[x for x in (root/'app/src/main/java').rglob('*.java')
       if x.name not in {'HandheldCameraXAnalyzer.java','CameraXLightingController.java',
            'CardTextRegions.java','ScannerStartupCoordinator.java'}]
source_all='\n'.join(x.read_text(encoding='utf-8',errors='replace') for x in files)
checks={
    'live activity uses handheld analyzer': 'new HandheldCameraXAnalyzer(' in source,
    'live analyzer receives bound camera info': 'getCameraInfo()' in source_all,
    'user-facing torch mode connected': '.setTorchMode(' in source_all,
    'recognition reads independent OCR crops': 'textRegions' in source_all,
    'recognition uses original color capture': 'rectifiedRgb' in source_all,
    'warm offline engine coordinator connected': 'ScannerStartupCoordinator' in source_all,
    'review completion resumes live scanner': '.completeManualReview(' in source_all,
}
for title,present in checks.items():print(('PASS' if present else 'NOT CONNECTED')+': '+title)
if not all(checks.values()):sys.exit(2)
print('PASS: source hooks located. Production build and camera-device testing still required.')
