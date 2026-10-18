# ManaVault v5.11.5 — Native Offline Printing Engine

Android CameraX Magic: The Gathering collection scanner. The app scans the **top card of a stack**. It will not accept the next card until the current card has been verified and saved (success sound), explicitly saved unverified, or resolved/skipped in immediate review.

## What's changed

- **Native SQLite printing catalog:** GitHub Actions builds an indexed, read-only SQLite database from Scryfall's current `default_cards` bulk export, and bundles it compressed into the APK. On first run the native database is extracted to app-private storage **before scanning**. Indexed by normalized name, set, collector number, illustration ID, and oracle ID. Multiple records for a set+collector are preserved and never interpreted as one printing.
- **Local lookup before WebView:** The live resolver calls native SQLite first and falls back to existing persistent IndexedDB. IndexedDB is upgraded in-place to include additional indexes, without erasing collections or previously downloaded records. The existing A/B catalog store and manual Scryfall refresh remain available.
- **Bundled set-symbol fingerprints:** The build uses representative official Scryfall card images to create signatures for up to 240 expansions (depending on reference availability). The live matcher consults these signatures without a network request and requires a matching collector number before symbol evidence can verify an exact printing.
- **Expanded artwork seed:** The build creates up to 300 compact art-crop fingerprints deduplicated by illustration ID, in addition to learned/persisted artwork and symbol fingerprints. Bundled artwork is only used when *all* remaining visual candidates have references and a printed identifier independently corroborates the winner.
- **Fast, safe live resolution:** No network downloads during the live verification path. Readable set + collector number remains the strongest evidence. Ambiguous variants trigger **immediate** review, not guessed printings or an end-of-batch process. A success sound follows a completed collection save.
- **Search:** Existing collection/catalog search by name, mana color/identity, set, rarity, general card type **and creature subtype** remains intact.

## Build in GitHub Actions

Upload the **contents** of this source ZIP to GitHub and run **Build ManaVault Android APK**. The workflow installs Python dependencies and:

1. Downloads the Scryfall bulk printing catalog **during the GitHub build**, indexes it into `printing-catalog.sqlite.gz`, and validates at least 100,000 printings.
2. Downloads a bounded set of official reference images and generates `visual-index.json` and `symbol-index.json` with compact fingerprints.
3. Rejects missing/incomplete reference libraries and runs regression tests.
4. Runs Gradle and uploads the resulting APK.

**Important:** This clean source archive intentionally does not include the large generated SQLite file or downloaded reference fingerprints. A direct local Gradle build will fail its validation until those assets are generated. Native Android compilation and real-device recognition have not been tested in this environment.

Build dependencies for these generators are `requests`, `Pillow`, and `ijson` (installed by the workflow). Because the artwork and symbol references are built from public Scryfall images, building requires network access and may take several minutes. No user collection is uploaded by these build scripts.

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
