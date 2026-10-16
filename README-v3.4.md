# ManaVault v3.4 scanner diagnostics + flash toggle

Based on v3.3. Includes a Flash ON/OFF control on the continuous camera panel, where the Android WebView exposes `MediaStreamTrack.getCapabilities().torch`. If it does not, the button displays "Flash: unavailable" rather than pretending it works. Some Android devices require a native Camera2/CameraX camera pipeline for torch support; this build does not yet provide that native fallback.

Includes an explicit Save diagnostic frame + OCR crop button below the live calibration view. It saves PNGs when the WebView supports downloads. Images may include personal surroundings: share only after reviewing them.

The artwork-index build now validates at least 30,000 fingerprints and verifies `assets/visual-index.json` is actually present in the compiled APK. A successful APK build must not omit the index silently. Artwork generation requires downloading tens of thousands of reference images and may fail due to network/time limits. No prebuilt artwork data is included in this project ZIP.

Oracle card database automatic download and daily update check from v3.3 remain. Exact printing database auto-sync, stable-frame gating, native torch fallback, and fully automatic card-edge perspective correction are NOT implemented in this revision.

Test: upload extracted project files at repository root, commit, build via GitHub Actions. Confirm "Verify visual index packaged in APK" step. Open Scan cards, start camera, check flash button. On supported tracks toggle and confirm LED; on unsupported tracks it will remain disabled.
