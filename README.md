# ManaVault v5.12.24 — Continuous scan throughput and recognition safeguards

Android Magic: The Gathering collection scanner, Commander deck studio, and inventory manager.

## New in v5.12.24 — continuous stack throughput

- Stable next-card scene changes can be detected while the previous card's printing is being saved in the WebView. The new card is never recognized/saved before the earlier capture receives its final receipt.
- StackChangeGate requires multiple consistent artwork/whole-card/title changes, but removes the fixed 850 ms blind cooldown. Matching titles are permitted as separate cards after a real physical stack transition or explicit Same copy confirmation.
- Real throughput timing now includes inter-capture gap, scene-to-capture delay, live verification, manual selection wait, and persistence receipt latency; slow gaps and session summaries appear in the scanner diagnostics.
- When a user manually selects an exact printing, a small local artwork fingerprint is learned from the saved card photograph on a background thread. Card images are never uploaded for learning.
- v5.12.24 integrates test coverage for ordering, stale receipts, same-copy prevention, and end-to-end telemetry into GitHub Actions. **The full APK and live-camera performance still require a GitHub/phone test.**
- See `THROUGHPUT-v5.12.24.md` for implementation and test instructions.

## Source-security cleanup (v5.12.21)

- Removed obsolete, duplicate root-level app/build files. The Android app loads only `app/src/main/assets/index.html`.
- Removed the two exact files named in the Defender archive alert: obsolete root `index.html` and the unused `tools/check_commander_creator_5128.js` test. The live Commander creator and app HTML remain in `app/src/main/assets/`.
- Hardened Android WebView against unsafe navigation schemes, mixed content, external iframe navigation, and file-URL access.
- Added a repeatable source-hygiene check in GitHub Actions. See `SECURITY-REVIEW.md` and `GITHUB-REPLACEMENT.md`. These changes do not constitute malware/antivirus certification.

## October 21 additional reliability fixes

- **Prevent the Light of Day → Volrath false save:** a printed flavor-text attribution on a portrait card must never be accepted as an exact title from a 180° inverted OCR pass. Native OCR blocks this and the JavaScript verifier has a second guard.
- **Improve sideways battles and split cards:** perpendicular 90°/270° rescue passes use a wider title band to reach the sideways title, even if the physical cardboard's perimeter is portrait. Regular upright card scans retain the fast narrow band.
- Preserve rapid in-scan saving and card stacking. No post-scan processing was added.
- Older-frame exact printing identification still needs additional evidence when the printed set/collector/illustrator was not actually resolved. **This patch does not claim to solve those ambiguous editions.**
- If a prior trial saved a card called **Volrath** while photographing **Light of Day**, correct the collection entry manually; this update cannot repair past inventory automatically.

## Changes inherited from v5.12.19

- A complete, zero-padded collector OCR reading outranks stray single digits. Two independently credible conflicting collector readings still require review.
- Rarity only vetoes a set/collector match when physically paired with the *matching* collector number (including fractions like `086/254 C`). Unrelated copyright and flavor text cannot falsely create a rarity conflict.
- Supports a narrowly scoped `L0264` / `LO264` OCR recovery for a **known collector number** of the recognized card; it does not guess set codes.
- **Correct printing** in Collection: select another known set/collector entry, or look up an exact set code + collector number (useful for Forest and other cards with thousands of printings), and move one or more copies after confirmation. Preserves card condition/foil and does not edit existing decks. No deleting/rescanning necessary.
- Collection rows show set code, collector number, and a price-source label. Unknown prices are displayed as unavailable, not fabricated as $0.
- **Refresh exact-printing prices** requests Scryfall USD values using stored printing UUIDs. It validates name, set, and collector before changing a saved price. Newly scanned, unpriced/stale printings are queued *after the scanner closes*, avoiding network work during the scan.
- Normal and foil prices are not substituted for each other. **Scryfall USD reference is not the same metric as live TCGplayer Market Price**. ManaVault does not currently have direct TCGplayer Market Price access; the screenshot's $0.45 cannot be guaranteed without that feed.
- Existing stack scanning, source-file export, backup/import format, deck studio, and one-copy removal confirmation are preserved.
- GitHub Actions explicitly prepares and verifies the offline catalog/artwork/symbol assets before Gradle builds the APK (Gradle also checks them).

## Build an Android APK

1. Follow `GITHUB-REPLACEMENT.md` to **delete the obsolete paths** in GitHub, then upload/commit the replacement source including hidden `.github/`. Uploading alone does not delete old files.
2. Open **Actions → Build ManaVault Android APK → Run workflow**, or push the code to `main`.
3. Download the APK artifact from the completed workflow run and extract it.
4. **Backup your collection before updating.** Install over the existing app only if Android accepts the signature. **Do not uninstall** a data-bearing installation when the update is rejected: uninstalling can delete its local collection. For future seamless updates, configure a consistent APK signing key.

The GitHub workflow builds the offline Scryfall catalog, SVG symbol reference atlas, and artwork references. Those generated large assets are deliberately not checked into this source ZIP.

## Verification

`python tools/check_title_rescue_guard.py` and `node tools/check_51220_inverted_title.js` test inverted-portrait safety and sideways title compatibility. `node tools/check_517_evidence_price.js` tests strong/weak collector competition, rarity contradictions, variant collisions, exact price-ID matching, and non-destructive printing correction. The workflow runs it **before** generating the large offline assets. The existing `tools/check_*.js`, token/title Java compilation smoke tests, symbol-matcher Java tests, and stack-gate tests remain available.

**Important:** This project was tested locally using JavaScript/Java smoke tests, not a complete Android SDK build or a physical-camera session. Real-device OCR, symbol detection, offline asset generation and APK compilation require the next GitHub test build. Don't treat automatic verification as infallible; confirm representative printings, especially older editions and basic lands.

App ID: `com.manavault.app`. Version code: **105**. Collection storage key remains `manavault-v1`.
