# ManaVault recognition reliability patch — 2026-10-20

## Implemented
- Native OCR rejects common rules/keyword fragments (including "Cycling" and Exava's "Each other creature...") as card titles.
- Native title selection uses the actual height of the title crop and geometric placement to limit stray artwork/rules OCR; the expanded two-line title crop remains intact.
- Native OCR maintains rotated-card and split/alternate-name support already present in the project.
- JavaScript rejects the same non-title fragments before name matching, name-only fallback, and printing verification.
- Catalog-unavailable and verification-exception paths no longer silently create name-only collection entries; explicit user approval is required.
- Native retry receipts track repeated same-name failures and stop automatic OCR retry cycles after the second retry. Stacking a different card re-arms the scanner.
- New `tools/check_recognition_reliability.js` regression checks the observed failures and safety gates.

## Not yet fully implemented / limitations
- Enlarging the offline bundled artwork index requires a Scryfall source catalog and downloaded reference data. This ZIP retains the preexisting index and its existing on-device caching implementation; no additional 1000+ images or fingerprints are claimed.
- Existing layout-aware rotation and printing-evidence paths were preserved, not replaced by a newly trained classifier.
- No APK was compiled here: Android SDK/Gradle were not installed in the current environment. Run the GitHub Actions `build-apk.yml` workflow.
- Existing `tools/check_catalog_recovery.js` expects silent name-only saves during catalog warmup; that assertion intentionally conflicts with the revised explicit-consent safety policy.
- Existing `tools/check_scanner.js` expects a GitHub workflow step referencing `ensure_offline_assets.sh`; the uploaded workflow did not contain it, unrelated to this recognition patch.

## Tests run
- `javac TitleReadingPolicy.java`: passed
- `node tools/check_recognition_reliability.js`: passed
- `node tools/check_title_footer_51213.js`: passed
- `node tools/check_scan_recovery.js`: passed
- `node tools/check_5127_recognition.js`: passed
- `python tools/check_name_layout_51211.py`: passed

Test on a real device with Exava, Web-Slinger Elite, sideways battles, two-line showcase titles, and same-name reprints before relying on unattended bulk collection scans.
