# ManaVault v4.4 — Scryfall database and artwork-index repair

This project retains the v4.3 OCR alignment and camera UI. It targets two persistent failures:

- **Missing Scryfall bulk download URI**: The Android printing database first requests `default_cards` bulk metadata. If no `download_uri` exists in either metadata endpoint, it falls back to Scryfall's paginated card API (`unique=prints`) and imports all pages into a staging IndexedDB store. The previous complete database is retained until at least 100,000 distinct printings have been imported. The fallback may take several minutes and requires uninterrupted connectivity. The Scryfall API may rate-limit requests.
- **Missing artwork index (404)**: The GitHub workflow generates `app/src/main/assets/visual-index.json` before compilation, validates 30,000+ fingerprints, then checks the built APK for `assets/visual-index.json`. If the Scryfall unique-artwork bulk metadata lacks a download URI, the builder falls back to paginated Scryfall card search with `unique=art`. Artwork thumbnails are downloaded in a six-worker pool, with an Actions cache to reuse downloaded files across runs. The first index build can be lengthy and may fail if the image host throttles downloads. The workflow intentionally fails rather than produce an APK missing its required index.

## Build

1. Extract the ZIP and copy all files into the root of your GitHub ManaVault repository. Preserve `.github/workflows/build-apk.yml`.
2. Commit and run **Actions → Build ManaVault Android APK**.
3. Confirm the **Generate bundled Scryfall artwork index** and **Verify visual index packaged in APK** steps pass.
4. Download the `ManaVault-Android-debug` artifact, install the APK, and open ManaVault on Wi-Fi.
5. Verify the offline printing count and the artwork index reference count in scanner diagnostics.

## Limitations

- The live Scryfall API and image servers were not reachable from the development environment, so the new fallback routes have **not** been tested end-to-end.
- The APK has not been compiled or tested on the Samsung phone here.
- Artwork recognition still uses the existing 8×8 RGB fingerprint matcher; neural matching is not included.
- Scryfall bulk and card APIs may impose throttling, rate limits, or restrictions. The artwork index generation can take a long time.
- Existing camera calibration, flash controls, and Commander/collection functionality are retained.

The source ZIP intentionally contains **one README.md** and no pre-generated artwork index; the index is created by GitHub Actions during the build.
