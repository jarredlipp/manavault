# ManaVault v4.6 — Fast candidate recognition

## Build
Upload the contents of this folder to the root of the GitHub repository. Run the **Build ManaVault Android APK** GitHub Actions workflow and install the debug APK artifact.

## Changes from v4.5
- Exact local-catalog name OCR enters printing review after the first matching read; fuzzy results require two agreeing reads.
- Eliminated artwork-only review that could reintroduce a previously scanned card.
- Reset recognition candidates when a new scene is detected or a scan is rejected/resumed.
- Standard OCR gets priority over alternate title regions.
- Reduced idle delay between OCR passes from 550 ms to 90 ms; actual Tesseract processing remains the dominant latency.
- Printing confirmation remains required to avoid incorrect set/collector-number saves.
- One README only.

## Test
Scan Living Lands, then Jace token, then Defile. Confirm the title and printing on each. Jace tokens/emblems may not exist in the regular Scryfall card-name catalog and may still require a dedicated token catalog; this update does not claim token coverage. Check that Living Lands never reappears as a visual candidate when Defile is shown.

## Limits
The scanner still uses Tesseract.js and optional network card lookups. This is a latency reduction, **not** a proven sub-100-ms recognizer. Artwork-index packaging and bulk-data availability must be verified separately.
