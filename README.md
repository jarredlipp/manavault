# ManaVault v2.2 — Visual-index prototype

This release adds an **artwork-first local fingerprint lookup** to the v2.1 Android app. It also retains OCR, zoom, and camera diagnostics. The GitHub workflow is included.

**Important:** The APK does NOT include a complete card-art database. Artwork-first recognition only works after you generate and import an index. Otherwise v2.1 OCR-first hybrid scanning remains active. The 8×8 RGB comparison is a baseline, not a trained image-recognition model, and should not be trusted for unattended identification or exact printings.

## Build APK
Upload the contents of this folder to the GitHub repository root and commit to `main`; GitHub Actions produces the debug APK.

## Generate an optional visual index on your PC
1. Download Scryfall's `unique_artwork` bulk data from https://api.scryfall.com/bulk-data/unique-artwork . Use the actual bulk download link (`download_uri` or `jsonl_download_uri`) returned by the metadata endpoint.
2. Install Python and run `pip install requests pillow`.
3. Run `python tools/build_visual_index.py your-bulk-file.json manavault-index.json --limit 1000` (supports `.jsonl.gz` too).
4. Transfer `manavault-index.json` to your phone. In **Scan cards**, select **Load visual index** and choose that file.

For a full index, raise `--limit` gradually. Building a complete index requires downloading many artwork images and can consume substantial time/storage. Respect Scryfall terms and download limits. Fingerprints are small but 8×8 RGB similarity can be unreliable under perspective, glare and alternate artwork; all visual-only matches require confirmation.

## Limitations
- No offline artwork index is bundled. This is a working integration point, not TCGplayer-grade recognition.
- Visual-only matches require confirmation. OCR may still be slow if no reliable visual match is found.
- Exact set/printing recognition is not implemented.
- No device test or Android build was performed in this environment.
