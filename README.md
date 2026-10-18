# ManaVault v5.12.2 — Offline catalog and footer matching fix

This source project builds an Android CameraX APK with stack scanning, one-card-at-a-time saving and immediate printing review.

## Changes
- A packaged native SQLite catalog now uses `printing-catalog.dat` (gzip-compressed bytes) rather than the previously missing `.gz` asset. It is generated during GitHub Actions / Gradle preparation, validated, and checked inside the finished APK.
- Footer OCR runs on an isolated native worker, so slow artwork or network comparisons cannot block printed-identifier extraction.
- Native catalog queries take priority without waiting for IndexedDB metadata while bulk data imports.
- A collector number and set code read in **different footer OCR lines** can now uniquely verify a printing when they agree with an identified card. Conflicting or duplicate editions still require review.
- Failure diagnostics report why an offline catalog cannot be opened.

## Building
Upload the ZIP contents, including `.github`, to your repository. Run the **Build ManaVault Android APK** workflow. The source ZIP does not contain the large generated catalog or artwork assets; GitHub generates these before compiling. Back up your collection before installing.

## Verification
Run `node tools/check_crossline_5122.js` and the other `tools/check_*.js` tests. Offline build tests require Python packages `requests` and `ijson`. Android APK compilation and on-device scanner speed have not been verified here.
