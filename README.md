# ManaVault Android — v4.7 source update

Build: push the project contents to the repository root, then run **Build ManaVault Android APK** in GitHub Actions. Download the debug APK from workflow artifacts.

## v4.7 changes
- Stronger OCR-to-catalog candidate matching: rejects very short/noisy guesses and checks whole title substrings; no unknown-name fallback.
- Confirms that the fetched card name agrees with the candidate name.
- Gives the calibrated standard title more opportunities before switching to alternate layouts.
- Repairs nested-object parsing in the streamed Scryfall JSON importer.
- Removes the broken paginated fallback that returned HTTP 404; an unsuccessful bulk metadata request now preserves the existing catalog and reports its real failure.

## Known limitations
- **500 ms recognition is a performance target, not validated on a device.**
- A complete visual-index.json is NOT bundled; artwork recognition still requires a separately generated/imported index. Do not mistake OCR fallback for an operational visual index.
- The Scryfall bulk download requires connectivity and adequate phone storage; metadata/network behavior has not been device-tested.
- Exact printing still requires review.

## Test
Start with Defile, Living Lands, Webstrike Elite and Spirit Summons. Compare the title shown in the OCR crop with the selected candidate; reject false matches and report diagnostic logs.


## v4.9
Unified fixed-stand and automatic framing, OCR mana-cost normalization, camera/OCR watchdog with bounded restart attempts, stale session protection, and torch failure disabling. Performance goal remains 500 ms; not verified on device.
