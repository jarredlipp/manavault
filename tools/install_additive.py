#!/usr/bin/env python3
"""Safely merge scanner helpers/tests without touching ManaVault app features.

Only adds missing files, updates identical files, or replaces UNMODIFIED files
from the prior 2026-11-08 scanner patch (backed up first). Refuses changed files.
It DOES NOT patch NativeScannerActivity without reviewing its real source.
"""
from pathlib import Path
import argparse
import hashlib
import shutil
import sys

ROOT=Path(__file__).resolve().parent.parent
SOURCE=Path('app/src/main/java/com/manavault/app/scanner')
# Only known, unmodified files from the November 12 recovery package are upgradable.
PRIOR_AUDIT={
    # Exact unmodified helper hashes from the Nov 13 APK audit source kit.
    'app/src/main/java/com/manavault/app/scanner/HandheldCameraXAnalyzer.java':
        'cf630ffeea0dcb1b8a53959dca6dd60ebe6e90bf3b5202b06c5a688a0a8310e4',
    'app/src/main/java/com/manavault/app/scanner/HandheldScanCoordinator.java':
        '1d1ea4b378b45d369585c6ded2d6f419d34565250c8cc3b5a6465f58165e789f',
    'tests/run-android-adapter-compile.sh':
        '5e5ab07c6217d6e6268b79dd96ffbca27a86620987330e184e921b37577fc4e3',
}
PRIOR_RECOVERY={
  'app/src/main/java/com/manavault/app/scanner/HandheldCameraXAnalyzer.java':'2cb9f352ebfe3d754078b43f4bd37695c90c11c5a815326439bcd6371f2426da',
  'app/src/main/java/com/manavault/app/scanner/HandheldScanCoordinator.java':'12d93e072a3e0a358ccffb06b3b2f0627a1ed694145f89408c4f69c5dd1e0a68',
  'tests/run-core-tests.sh':'d91c477c86cb0aa9d78282ad74b27ac02122087595dbbc31e9f6bbcda8bd29b4',
  'tools/diagnostics_report.py':'df36fd68a064e7fe9057302911eb68ab0fc9396ca2d6abbf299f24d79f7028da'
}

# Exact hashes from the user's Nov 8 patch, never a wildcard permission to overwrite.
PRIOR={
    'HandheldCameraXAnalyzer.java':'0c1c9f288bce4bffb9ff2d3d22e3c7f00f436e7bfaa79145531e47710dfa409f',
    'HandheldScanCoordinator.java':'13b32d06bdaccae08acfcaca035225e37f4621b65f13e491f18ff62dc5372cfc',
    'PerspectiveRectifier.java':'a34cb93ec40e4dcb511445bc88a4ec1c1367ef1474f8e8d885fd853866ba235e',
    'FrameQualityEstimator.java':'be317d1e5540c9397f699b07a6cc6f89015098d17b2dd4ce3168ab10060b33da',
    'PrintingEvidenceResolver.java':'70ee0bfdfee6aec9270e6306b3e0ee10ebfdc30354adabd295b9d28521932630',
    'tests/CameraAdapterTest.java':'ab51e280fd181926c1fb91ec2853a446754679f7c5416399bd7a1aeed9d506ad',
    'tests/ScannerCoreTest.java':'2cdf0a18b41eb114ab40f14b9db4a4b1a29ff137dd5c05f4dcc802e8ce5347d7',
    'tests/run-android-adapter-compile.sh':'5deffbc2236a9e3d54801ed6c7458c91d7e2d3db2bd589607e269f421ee126e8',
    'tests/run-core-tests.sh':'8cb899e1bfcc004f05d1f283838f568cd932ef5d682f2facbe79a0a9001c6b06',
    '.github/workflows/handheld-scanner-core-tests.yml':'68ef9aca6de2dd42a34d14bb17c7462ca888711ae84f0416c1b903b4862abeff',
    'tests/stubs/androidx/annotation/NonNull.java':'264db10b7fe8909ea432a314ca4635c149ac0edc40b9b42c55bc5604c8ddd611',
    'tests/stubs/androidx/camera/core/CameraControl.java':'9e747415fb451c0a589b48b854d923c54a78094137327f3b0a936a202181d981',
    'tests/stubs/androidx/camera/core/FocusMeteringAction.java':'b6e11abe45c7130fcd72f3867c21e38c24c4add7064e8b178bc22412321b80c8',
    'tests/stubs/androidx/camera/core/ImageAnalysis.java':'ad6a330fb2e1187a3550b8c81b960107f30fcca59b1004f12ff1a7e8db76dfcf',
    'tests/stubs/androidx/camera/core/ImageProxy.java':'65cc3754f25427f3ee97978e94c3008527757d9b5374f0a3fcf83a8ecde98e70',
    'tests/stubs/androidx/camera/core/SurfaceOrientedMeteringPointFactory.java':'683af61de285afcbb29556429ab1ea46fd78f1f8730a064b678633eb94588f6f'
}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def collect():
    paths=list((ROOT/SOURCE).glob('*.java'))
    paths+=list((ROOT/'tests').rglob('*.java'))
    paths+=list((ROOT/'tests').glob('*.sh'))
    paths+=[ROOT/'.github/workflows/handheld-scanner-core-tests.yml']
    paths+=[ROOT/'tools/diagnostics_report.py',ROOT/'tools/check_camera_wiring.py',
            ROOT/'tools/validate_scanner_release.sh']
    return sorted(paths)
def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('repo',type=Path,help='extracted EXISTING ManaVault Gradle source root')
    ap.add_argument('--dry-run',action='store_true')
    args=ap.parse_args()
    repo=args.repo.resolve()
    if not (repo/'app').is_dir() or not ((repo/'settings.gradle').is_file() or
                                      (repo/'settings.gradle.kts').is_file()):
        sys.exit('Error: target is not the existing ManaVault Gradle app; nothing changed')
    if not list((repo/'app/src/main/java').rglob('NativeScannerActivity.java')):
        sys.exit('Error: NativeScannerActivity.java not found; refusing to target wrong project')
    changes=[];conflicts=[]
    for src in collect():
        rel=src.relative_to(ROOT);dest=repo/rel
        if not dest.exists():action='ADD'
        elif sha(dest)==sha(src):action='UNCHANGED'
        elif (PRIOR.get(str(rel),PRIOR.get(rel.name))==sha(dest)
              or PRIOR_RECOVERY.get(str(rel))==sha(dest)
              or PRIOR_AUDIT.get(str(rel))==sha(dest)):action='UPGRADE_WITH_BACKUP'
        else:
            action='CONFLICT';conflicts.append(str(rel))
        changes.append((action,src,dest,rel))
    for action,_,_,rel in changes:print(f'{action:20s} {rel}')
    if conflicts:
        sys.exit('Error: existing customized files were NOT overwritten: '+', '.join(conflicts))
    if args.dry_run:
        print('DRY RUN: no files changed. NativeScannerActivity still requires integration.')
        return
    backup=repo/'tools/scanner_patch_backups/2026-11-13-camera-recovery'
    for action,src,dest,rel in changes:
        if action=='UNCHANGED':continue
        if action=='UPGRADE_WITH_BACKUP':
            original=backup/rel;original.parent.mkdir(parents=True,exist_ok=True)
            if not original.exists():shutil.copy2(dest,original)
        dest.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(src,dest)
    print('Core helper files installed. Existing app files and collections preserved.')
    print('NOT DONE: wire NativeScannerActivity and startup adapters, build APK, test device.')
if __name__=='__main__':main()
