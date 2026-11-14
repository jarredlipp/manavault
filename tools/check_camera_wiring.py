#!/usr/bin/env python3
"""Fail closed when the production ManaVault source has not adopted the scanner.

This is NOT a semantic Java compiler: inspect actual live camera, OCR and
persistent-save adapters on the final APK/device in addition to this guard.
"""
import argparse
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('source_root',type=Path)
a=p.parse_args()
java=a.source_root/'app/src/main/java'
activities=list(java.rglob('NativeScannerActivity.java'))
if len(activities)!=1:
    p.error('expected exactly one NativeScannerActivity.java in app/src/main/java')
activity=activities[0].read_text(encoding='utf-8',errors='replace')
app_sources=[(f,f.read_text(encoding='utf-8',errors='replace')) for f in java.rglob('*.java')]
helpers={'HandheldCameraXAnalyzer.java','CardTextRegions.java','ColorCardRectifier.java',
         'PrintingEvidenceResolver.java','NativeScanCameraSession.java'}
adapters='\n'.join(text for f,text in app_sources if f.name not in helpers)
# The bridge may be used, but the new API needs to be invoked from the
# actual activity, not just present in a helper file.
uses_bridge=('NativeScanCameraSession.start(' in activity or
             'new HandheldCameraXAnalyzer(' in activity)
checks={
    'NativeScannerActivity actually binds the new camera analyzer':uses_bridge,
    'torch mode is controlled from live scanner UI':'.setTorchMode(' in activity,
    'manual review completion resumes scanner':'.completeManualReview(' in activity,
    'live recognition adapter reads card-specific OCR regions':'textRegions' in adapters,
    'live recognition adapter keeps RGB artwork data':'rectifiedRgb' in adapters,
    'startup coordination is reachable from the activity':
       ('ScannerStartupCoordinator' in activity or 'NativeScanCameraSession' in activity),
    'authoritative eligible paper-printing count is supplied to recognition':
       ('authoritativePrintingCount' in adapters or 'new PrintingEvidenceResolver.Evidence(' in adapters),
}
for label,ok in checks.items():
    print(('PASS' if ok else 'NOT CONNECTED')+': '+label)
if not all(checks.values()):
    raise SystemExit('BLOCK RELEASE: actual live scanner does not meet required source wiring')
print('Source-level checks passed. APK and device-level verification still required.')
