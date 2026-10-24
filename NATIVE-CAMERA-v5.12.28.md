# ManaVault v5.12.28 — Native CameraX-only implementation

## Removed

- Legacy browser `getUserMedia()` continuous camera and its live OCR/zoom/torch/calibration loops.
- Optional browser photo OCR and grid-photo processing, including the remote Tesseract.js CDN JavaScript loader.
- Browser webcam permission grants. All WebView media requests are denied; Android runtime camera permission still supports CameraX.
- Deprecated scanner panels, browser camera diagnostics and outdated instructions. Native scanner diagnostics remain.

## Retained

- The native `NativeScannerActivity` / CameraX + ML Kit pipeline and live JavaScript callback `window.mvNativeScanLive`.
- The printing resolver, immediate printing review, collector OCR, Scryfall printing catalog, set symbols and offline artwork assets.
- Collection and deck functions, manual Scryfall search, batch-text import, backups, and on-device storage format.
- Native scan-stack behavior and duplicate-copy safeguards from v5.12.27.

## Scope and safety

This is source cleanup, **not a finding that Defender was incorrect**. We have no trusted verdict explaining the second detection. The patch contains only the modified Android HTML, Java WebView activity, Gradle version, source-hygiene check, and release notes. No JavaScript test harnesses or executable binaries are included in the patch. Microsoft Defender and/or independent antivirus should still inspect it; never use an antivirus exclusion to proceed.

## GitHub installation

1. Preserve the repository as a backup branch.
2. Scan the patch ZIP before extracting. If Defender flags it, stop.
3. Upload each file to the exact path shown inside the ZIP, replacing the existing file. Do not create a nested `manavault-main` folder.
4. Commit the change. Run GitHub Actions, then install and test the produced APK without removing existing app data.
5. Check that Scan cards shows a native CameraX launch button and **no** legacy video or upload-photo OCR controls. Verify collection import, backup, printing review, and save during scanning.

## Build verification

Full Android compilation and Defender scanning were not available in this environment. Source tests and static checks cannot guarantee device behavior or malware absence.
