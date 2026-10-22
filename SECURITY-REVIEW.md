# ManaVault v5.12.21 — source security review

## Scope and provenance

This is a **static source-code review and cleanup**, not an antivirus clearance or a Microsoft false-positive verdict. The replacement is based on the previously supplied v5.12.20 project source, compared against the locally uploaded v5.12.17 source and the public GitHub `main` file list / selected live source files (which identified the repo as v5.12.19 when checked). The environment could read public GitHub pages but could not download or clone the GitHub `main` ZIP directly, so this package is **not represented as a byte-for-byte clone of the live branch**. Back up or preserve any later GitHub-only changes before overwriting them.

The prior Windows Defender alert was `Trojan:Win32/MalUri.A!cl`. Windows reported two files under the downloaded v5.12.20 ZIP:

- `manavault-main/index.html` (obsolete root-level browser prototype; **not** the Android asset)
- `manavault-main/tools/check_commander_creator_5128.js` (an old development test using Node.js `vm` to run trusted local Commander source)

Both paths were removed from this package. This removal reduces the previously reported exposure, but **does not prove those files were false positives** and does not establish that this package will be accepted by Defender.

## Findings and mitigations

1. **Multiple misleading duplicate entry points** — Removed old root `index.html`, `MainActivity.java`, and `AndroidManifest.xml`. The Android app now has only the intended runtime copies under `app/src/main/`.
2. **Duplicate build files** — Removed root `build (1).gradle` and root `build-apk.yml`. The active paths are `build.gradle` and `.github/workflows/build-apk.yml`.
3. **Duplicate build/data scripts** — Removed root `build_visual_index.py`, `download_and_build_index.py`, and `card-db.js`, retaining `tools/` generators and the real `app/src/main/assets/card-db.js`.
4. **Flagged development test** — Removed `tools/check_commander_creator_5128.js`, **not** `app/src/main/assets/commander-creator.js`. Other deck and scanner tests remain, but that exact targeted Commander-generator test is not preserved.
5. **Android WebView navigation** — Added guards against `file:`, `content:`, `intent:`, `javascript:`, and other non-HTTP(S) external navigation, unsafe mixed content, local-file script access, automatic popup windows, and external iframe navigation; enable WebView Safe Browsing. The Android file picker is preserved.
6. **In-app document embedding** — A narrow content security policy blocks iframe/object embedding. It does not block legitimate camera/scan scripts.
7. **Release regression guard** — New `tools/check_source_hygiene.py` checks expected layout, missing obsolete files, basic WebView hardening, app version consistency, and preserved storage key. GitHub Actions runs it and several OCR tests before any asset downloads and APK compilation.
8. **No change to collection storage** — Package name remains `com.manavault.app`, and collection key remains `manavault-v1`. There are no source changes intended to delete or migrate user inventory.

## Remaining trust boundaries / open work

- Legacy browser OCR still dynamically loads `tesseract.js@5` from `cdn.jsdelivr.net` in its fallback flow. A third-party script loaded into a privileged Android WebView is a supply-chain exposure. The native CameraX/ML Kit scanner does not require that script for title OCR. A **future** audited release should vendor a pinned, checksummed Tesseract.js bundle and worker/wasm dependencies locally before removing the CDN. This was not done here because offline dependencies could not be independently fetched and validated without jeopardizing browser OCR functionality.
- The build downloads public Scryfall metadata and images, and installs Python packages into a temporary virtual environment to generate SQLite/artwork/symbol assets. Review these upstream dependencies and ideally pin versions and hashes in a subsequent release. The public GitHub Actions used in the workflow are version-tagged, not hash pinned.
- Dynamic JavaScript tests elsewhere under `tools/` use `vm` for local unit tests. They are **not** included inside the APK. Running trusted local code in tests is not by itself proof of malware, but the source should be scanned by your antivirus before executing build scripts.
- No commercial antivirus, Microsoft Defender scanning, Android SDK build, Google Play Protect check, or physical-device test occurred in this environment.

## Checks completed in this environment

- Source tree duplication/known-path audit: passed.
- JS regression checks: **28/28 passed**.
- Python regression checks: **14/14 passed**.
- New source-hygiene check: passed.
- Source ZIP integrity and absence of the two exact flagged paths: verified when packaged.

## Recommended independent verification

1. Keep the older Defender-flagged v5.12.20 ZIP quarantined/deleted; never add Defender exclusions.
2. Scan this new source package before extracting or executing it. If Defender flags it, leave it blocked and share the exact detection filenames rather than bypassing the alert.
3. Commit the cleanup to a new branch first. Run GitHub Actions and review its logs, dependencies and generated Android APK.
4. Back up your collection via ManaVault before installing any new APK. Debug APK signatures may differ across builds; **do not uninstall your data-bearing app** to force an update.
5. For an authoritative verdict on the prior alert, send the prior detection/sample to Microsoft Security Intelligence. A negative quick scan of Windows is reassuring for that computer, but is not a verdict on the code.
