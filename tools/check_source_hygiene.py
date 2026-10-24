#!/usr/bin/env python3
"""Static project hygiene checks, not a malware scan or antivirus certification."""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
retired = (
    'index.html', 'AndroidManifest.xml', 'MainActivity.java', 'build (1).gradle',
    'build-apk.yml', 'build_visual_index.py', 'download_and_build_index.py',
    'card-db.js', 'tools/check_commander_creator_5128.js',
    'BUILD-FIX-INSTRUCTIONS.txt', 'UPLOAD-INSTRUCTIONS.txt',
)
for item in retired:
    assert not (root / item).exists(), f'Retired/unreviewed duplicate still present: {item}'
needed = (
    'settings.gradle', 'build.gradle', 'app/build.gradle',
    'app/src/main/AndroidManifest.xml',
    'app/src/main/java/com/manavault/app/MainActivity.java',
    'app/src/main/java/com/manavault/app/NativeScannerActivity.java',
    'app/src/main/assets/index.html', 'app/src/main/assets/card-db.js',
    'app/src/main/assets/commander-creator.js',
    'app/src/main/assets/collection-safety.js',
    '.github/workflows/build-apk.yml', 'tools/ensure_offline_assets.sh',
)
for item in needed:
    assert (root / item).is_file(), f'Active app or build file missing: {item}'
manifest=(root / 'app/src/main/AndroidManifest.xml').read_text()
assert 'android:usesCleartextTraffic="false"' in manifest
assert 'android:exported="false"' in manifest
java=(root/'app/src/main/java/com/manavault/app/MainActivity.java').read_text()
for rule in (
    'settings.setAllowFileAccess(false)',
    'settings.setAllowFileAccessFromFileURLs(false)',
    'settings.setAllowUniversalAccessFromFileURLs(false)',
    'settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)',
    'settings.setSafeBrowsingEnabled(true)',
    'request.isForMainFrame()',
    '"https".equalsIgnoreCase(scheme)',
    '"http".equalsIgnoreCase(scheme)',
):
    assert rule in java, f'Android WebView security setting missing: {rule}'
html=(root/'app/src/main/assets/index.html').read_text()
assert 'frame-src \'none\'' in html
assert 'object-src \'none\'' in html
assert 'manavault-v1' in html, 'Collection key must be preserved'
assert 'window.mvNativeScanLive' in html, 'Native live capture bridge missing'
assert 'window.mvNativeLaunchNow' in html, 'Native CameraX launch bridge missing'
assert 'tesseract.js' not in html.lower(), 'Legacy CDN OCR loader reintroduced'
assert 'getUserMedia' not in html, 'WebView camera capture reintroduced'
assert 'id="liveVideo"' not in html and 'id="liveStart"' not in html, 'Old browser camera UI reintroduced'
assert "script-src 'self' 'unsafe-inline'" in html, 'Remote JavaScript must be blocked by policy'
assert 'runOnUiThread(request::deny)' in java, 'WebView media permissions must be denied'
gradle=(root/'app/build.gradle').read_text()
version=re.search(r"versionName\s+'([^']+)'",gradle).group(1)
assert 'ManaVault '+version in java
assert 'ManaVault v'+version in html
workflow=(root/'.github/workflows/build-apk.yml').read_text()
assert 'tools/check_source_hygiene.py' in workflow
assert 'tools/ensure_offline_assets.sh' in workflow
print('Source hygiene PASSED: no retired duplicates; Android app and workflow intact; WebView guards present; version and collection key consistent.')
print('NOTICE: Static hygiene is not an antivirus scan. Browser camera and remote Tesseract loader are absent.')
