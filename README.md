# ManaVault v4.5 — OCR and Scryfall regression repair

## Changes
- OCR: the camera image visible behind the green and yellow guides is now a canvas rendered from the exact same source as the OCR card crop. Software zoom is applied once in canvas coordinates rather than also transforming the video with CSS. The live title reference uses the same crop as standard-title OCR.
- Added **Test this exact OCR crop** button to read the currently displayed calibration strip.
- Scryfall: restored support for `jsonl_download_uri` used in v2.4, while retaining `download_uri` for JSON exports. Added a streaming JSONL parser for full printing records.
- Fixed paginated fallback from invalid `/cards?order=name...` to `/cards/search?q=game%3Apaper&unique=prints&order=name`.
- Artwork builder: prefers `jsonl_download_uri` when available. The workflow continues to require >=30,000 artwork references and verifies `assets/visual-index.json` inside the APK.

## Regression investigation
The older v2.4 importer used `meta.jsonl_download_uri || meta.download_uri` and imported newline-delimited JSON. In v4.0 the new importer was changed to look for `download_uri` only and assumed a JSON array. If the Scryfall metadata response supplies `jsonl_download_uri` but not `download_uri`, v4.0–v4.4 incorrectly reject the valid response. The v4.4 fallback used `/cards?order=name&unique=prints`, which is not the Scryfall card-search endpoint, causing the observed 404. This version restores the older working URI behavior and supports both formats.

## Testing
1. Upload all project files to the GitHub repository, preserving `.github/workflows/`. Build using GitHub Actions and install **the APK from the latest successful run**, not an older artifact.
2. On first launch with Wi-Fi, watch printing database sync; check whether it imports more than 100,000 records.
3. Under the fixed camera, position Living Lands and adjust yellow guide. Verify that the enlarged preview shows the same pixels. Press **Test this exact OCR crop** and inspect the text.
4. Check the Actions step **Verify visual index packaged in APK**. If it fails, share that step's logs. A successful APK from this workflow must contain `assets/visual-index.json`.

## Limits
No on-device or GitHub Actions end-to-end build was completed in this environment. Artwork indexing requires downloading many images and may take a long time or fail under API limits. This is still the existing 8x8 visual index, not the proposed neural model. There is only one README.
