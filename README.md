# ManaVault v5.12.19 — Collector-number OCR recovery

Android Magic: The Gathering collection scanner, Commander deck studio, and inventory manager.

## Changes in this version

- A complete, zero-padded collector OCR reading outranks stray single digits. Two independently credible conflicting collector readings still require review.
- Rarity only vetoes a set/collector match when physically paired with the *matching* collector number (including fractions like `086/254 C`). Unrelated copyright and flavor text cannot falsely create a rarity conflict.
- Supports a narrowly scoped `L0264` / `LO264` OCR recovery for a **known collector number** of the recognized card; it does not guess set codes.
- **Correct printing** in Collection: select another known set/collector entry, or look up an exact set code + collector number (useful for Forest and other cards with thousands of printings), and move one or more copies after confirmation. Preserves card condition/foil and does not edit existing decks. No deleting/rescanning necessary.
- Collection rows show set code, collector number, and a price-source label. Unknown prices are displayed as unavailable, not fabricated as $0.
- **Refresh exact-printing prices** requests Scryfall USD values using stored printing UUIDs. It validates name, set, and collector before changing a saved price. Newly scanned, unpriced/stale printings are queued *after the scanner closes*, avoiding network work during the scan.
- Normal and foil prices are not substituted for each other. **Scryfall USD reference is not the same metric as live TCGplayer Market Price**. ManaVault does not currently have direct TCGplayer Market Price access; the screenshot's $0.45 cannot be guaranteed without that feed.
- Existing stack scanning, source-file export, backup/import format, deck studio, and one-copy removal confirmation are preserved.

## Build an Android APK

1. Upload the complete source contents, including hidden `.github/`, to your GitHub repository.
2. Open **Actions → Build ManaVault Android APK → Run workflow**, or push the code to `main`.
3. Download the APK artifact from the completed workflow run and extract it.
4. **Backup your collection before updating.** Install over the existing app only if Android accepts the signature. **Do not uninstall** a data-bearing installation when the update is rejected: uninstalling can delete its local collection. For future seamless updates, configure a consistent APK signing key.

The GitHub workflow builds the offline Scryfall catalog, SVG symbol reference atlas, and artwork references. Those generated large assets are deliberately not checked into this source ZIP.

## Verification

`node tools/check_517_evidence_price.js` tests strong/weak collector competition, rarity contradictions, variant collisions, exact price-ID matching, and non-destructive printing correction. The workflow runs it **before** generating the large offline assets. The existing `tools/check_*.js`, token/title Java compilation smoke tests, symbol-matcher Java tests, and stack-gate tests remain available.

**Important:** This project was tested locally using JavaScript/Java smoke tests, not a complete Android SDK build or a physical-camera session. Real-device OCR, symbol detection, offline asset generation and APK compilation require the next GitHub test build. Don't treat automatic verification as infallible; confirm representative printings, especially older editions and basic lands.

App ID: `com.manavault.app`. Version code: **101**. Collection storage key remains `manavault-v1`.
