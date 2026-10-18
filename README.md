# ManaVault v5.11.7 — Scryfall Bulk-Data Decompression Fix

Android CameraX Magic: The Gathering collection scanner. The app scans the **top card of a stack**. It will not accept the next card until the current card has been verified and saved (success sound), explicitly saved unverified, or resolved/skipped in immediate review.


## v5.11.7: Fix for UnicodeDecodeError during asset preparation

The previous GitHub build failed in `tools/build_native_catalog.py` with `UnicodeDecodeError: ... invalid start byte 0x8b`. This happens when the bulk file is gzip-compressed but the JSON parser treats the compressed bytes as UTF-8. The streaming catalog parser now examines the actual first bytes of the downloaded data and transparently decompresses gzip if needed. It supports plain/gzip JSON arrays and plain/gzip JSONL, and explicitly rejects unexpected HTML/error payloads. Downloading, decompression, and catalog creation remain streamed/atomic, so failed builds do not replace valid assets.

The build still creates the SQLite catalog, artwork seed, and symbol library, verifies each, and only then compiles the APK; it does **not** bypass the strict asset check. Unit/regression tests use synthetic payloads and do not replace a real Scryfall download or Android compilation.

## Build-failure repair

The prior ZIP contained empty `visual-index.json` and `symbol-index.json` placeholders and no compressed SQLite database. The GitHub workflow generated those assets, but if GitHub used an **older workflow** (often because the hidden `.github` directory wasn't uploaded), Gradle failed with `Artwork index has fewer than 12 references`.

**Now Gradle itself runs `tools/ensure_offline_assets.sh` before `:app:preBuild`, even if an old workflow invokes `gradle :app:assembleDebug` directly.** The script creates a private Python environment, downloads/builds the native printing database, generates artwork and set-symbol fingerprints, and validates all three before allowing an APK to build. The current GitHub workflow calls the same script explicitly. If Scryfall is unavailable, the build stops with a useful generator error rather than producing an APK with empty reference data. Generated assets are reused on later builds when valid.

## What's changed

- **Native SQLite printing catalog:** GitHub Actions builds an indexed, read-only SQLite database from Scryfall's current `default_cards` bulk export, and bundles it compressed into the APK. On first run the native database is extracted to app-private storage **before scanning**. Indexed by normalized name, set, collector number, illustration ID, and oracle ID. Multiple records for a set+collector are preserved and never interpreted as one printing.
- **Local lookup before WebView:** The live resolver calls native SQLite first and falls back to existing persistent IndexedDB. IndexedDB is upgraded in-place to include additional indexes, without erasing collections or previously downloaded records. The existing A/B catalog store and manual Scryfall refresh remain available.
- **Bundled set-symbol fingerprints:** The build uses representative official Scryfall card images to create signatures for up to 240 expansions (depending on reference availability). The live matcher consults these signatures without a network request and requires a matching collector number before symbol evidence can verify an exact printing.
- **Expanded artwork seed:** The build creates up to 300 compact art-crop fingerprints deduplicated by illustration ID, in addition to learned/persisted artwork and symbol fingerprints. Bundled artwork is only used when *all* remaining visual candidates have references and a printed identifier independently corroborates the winner.
- **Fast, safe live resolution:** No network downloads during the live verification path. Readable set + collector number remains the strongest evidence. Ambiguous variants trigger **immediate** review, not guessed printings or an end-of-batch process. A success sound follows a completed collection save.
- **Search:** Existing collection/catalog search by name, mana color/identity, set, rarity, general card type **and creature subtype** remains intact.

## Build in GitHub Actions

Upload **all** ZIP contents (including the hidden `.github/workflows/build-apk.yml` file) to GitHub and run **Build ManaVault Android APK**. If your repository still has an older workflow that simply invokes Gradle, the new Gradle preparation task also generates required assets automatically.

1. The first network-enabled build downloads Scryfall's current `default_cards` bulk catalog and produces `printing-catalog.sqlite.gz` (100,000+ indexed printings).
2. It generates a bounded artwork index and set-symbol library from reference images.
3. `verify_offline_assets.py` verifies the SQLite contents/indexes, validates fingerprint shapes, and rejects incomplete results.
4. Gradle assembles the APK and the workflow uploads it.

**These large generated files are intentionally omitted from the clean source ZIP.** The first build therefore needs internet access to Scryfall and can take several minutes, not just 9 seconds. No collection data is uploaded. Android compilation and device scanning remain unverified here.

If your GitHub job still fails, inspect the **Prepare and verify offline printing + visual reference assets** step (or the new `:app:prepareOfflineAssets` Gradle task) for the specific error, rather than bypassing the asset check.

## Local tests

```
node tools/check_scanner.js
node tools/check_auto_save.js
node tools/check_fast_scan.js
node tools/check_search_filters.js
node tools/check_scan_recovery.js
node tools/check_offline_catalog.js
python3 tools/check_offline_catalog.py
bash tools/check_stack_gate.sh
bash tools/check_reference_cache.sh
```

Export an existing collection backup before installing a new APK. Card price and foil condition still require separate validation. Some reprints share identical artwork/symbols and still require reading printed markings or immediate manual review.
