## v5.13.5 targeted Saga title-bar OCR repair

See [RECOGNITION-v5.13.5.md](RECOGNITION-v5.13.5.md). A decorative Saga
emblem + joined mana digit can cause `O The Long Reach of Night3` to miss the
name catalog entirely. Only a unique exact catalog identity is proposed, and
printing still requires independent set/collector verification or manual review.
The existing v5.13.4 complete multi-session diagnostic archive is unchanged.

## v5.13.4 complete diagnostic history and selective OCR recovery

See [RECOGNITION-v5.13.4.md](RECOGNITION-v5.13.4.md). Every new diagnostic
message is now written to an Android-private per-session archive; the full
ZIP export covers all recent sessions instead of copying the last 150 lines.
CameraX can accept up to 300 captures per session, selective traditional-frame
footer OCR is added, and physical audit retention is expanded to 1,200 records.
No previous source features or full printing artwork database are removed.

## v5.13.3 accuracy-and-throughput refinement

See [RECOGNITION-v5.13.3.md](RECOGNITION-v5.13.3.md) for review-only
collector OCR corrections, categorized slow-scan telemetry, physical printing
accuracy audit, and regression tests. Existing core scanner features and
installed artwork database remain unchanged.

## v5.13.2 recognition update

See [RECOGNITION-v5.13.2.md](RECOGNITION-v5.13.2.md) for the bounded full-art
candidate search, lower-title rescue, native set/collector lookup, diagnostics,
and safety limitations. The already installed complete artwork database is
reused; no new database build is required for this version.

# ManaVault v5.13.1 — Split-card title recovery and recognition safeguard

This is the complete project, with no collection/deck features or historical tests removed.
The recognition update introduces conservative rotated-title gates, catalog-based
OCR correction, offline rules-text review suggestions, captured-photo quality
metrics, adaptive footer OCR, and an honest physical-card throughput benchmark.
See [SCANNER-FIX-v5.13.1.md](SCANNER-FIX-v5.13.1.md) for this release, and [SCANNER-OVERHAUL-v5.13.0.md](SCANNER-OVERHAUL-v5.13.0.md) for the prior engine update.

**The full artwork index must still be generated and installed separately**, and
an APK/phone test is required before treating source tests as performance claims.

## v5.12.33 — Physical-footer-first title integrity and split-card OCR

* When native OCR produces a *tentative* title, the resolver reads the photographed set/collector footer **before** looking up printings for the guessed title. Contradicting title/footer evidence produces a visible review choice, not an automatic collection mutation. The precise collector/set is still cross-checked against the offline Scryfall printing catalog.
* Three-or-more-face split-card guesses cannot become automatic captures on normal photographic title evidence. Non-title rules/flavor/artist OCR remains review-only, even if it coincides with real card printing data.
* True sideways split cards now have orientation-aware paired-title crops: two upper/lower sideways panels on portrait captures and left/right title strips when the scan image was already rotated into landscape. Both printed faces must corroborate the name.
* The native live camera now explicitly reports whether the **complete** printing-level artwork database is installed, its version and face count, and the last indexed artwork comparison (rather than showing only the bundled starter reference count).
* Adds regression tests and an early Gradle source contract for the new split-layout class. All existing features and tests remain intact. This update is **source-tested, not yet APK-compiled or phone-benchmarked**. See [RECOGNITION-v5.12.33.md](RECOGNITION-v5.12.33.md).

## v5.12.32 — Accuracy-first indexed artwork and printing OCR

* Preserves the full v5.12.31 feature set, native CameraX scanner, existing decks/collection, all tests, and complete artwork database architecture.
* A weak artwork name search now uses indexed one-bit hash-neighbor/offset-crop probes rather than abandoning the correct card after small framing changes. Candidate-specific artwork rescoring compares adjusted artwork crops **without moving text-box or footer evidence**.
* Known collector numbers of the already identified card enable a single extra enlarged/contrast footer OCR attempt when rapid OCR fails. Similar-looking set symbols must compete at the same photographed location; ambiguous icons remain manual-review cases. Same-art printings are ranked for review but not auto-verified by artwork.
* The enlarged complete artwork index must be installed using the separate workflow and in-app download to enable its advanced lookup; until then the smaller bundled image library remains.
* Regression checks are provided in `tools/check_51232_accuracy.py` and `tools/check_51232_symbol_gate.js`. **APK build and real-device speed/accuracy are still unverified.** See [RECOGNITION-v5.12.32.md](RECOGNITION-v5.12.32.md).

## v5.12.31 — Complete, printing-level offline artwork database

The native scanner can use a separate compressed SQLite visual index for **all available English paper printings and both card faces**. Each printing remains distinct even when Scryfall illustration IDs and artwork are identical. Candidate comparisons use artwork color, full-frame layout, title, rules/text box and footer, with independently observed set/collector marks required when the artwork is shared. No collection-format change. This release adds a dedicated GitHub Actions **Build full printing artwork database** workflow, a SHA-256-verified app download button, and subsequent delta updates. **Run that GitHub workflow and publish its release first; the full index is not in the source ZIP or ordinary APK.** See [FULL-ARTWORK-v5.12.31.md](FULL-ARTWORK-v5.12.31.md). The camera remains native-only. Source tests passed; full APK compilation and 120k-image index generation remain to be verified.

## v5.12.28 — Native CameraX-only scanner

The Android app now uses **only its native CameraX / ML Kit scanner**. Retired browser-video capture, optional uploaded-photo OCR, multi-card photo scanner and remote Tesseract.js loader are removed from the Android UI/source. Browser WebView camera permissions are denied. Manual card search, batch text import, deck integration, and during-scan printing save/verification remain supported. The collection storage key and Android package name are unchanged.

This is a surface-area reduction and static security hardening, **not an antivirus clearance or proof of false-positive detections**. Scan the package with Microsoft Defender before installing or executing it. See `NATIVE-CAMERA-v5.12.28.md` for scope, test results, and limitations.

## v5.12.26 legacy printing and title recovery

See `RECOGNITION-v5.12.26.md` for observed performance, changed evidence paths, and safety limitations.

## v5.12.25 scanner verification/copy controls

See `COPY-AND-EDITION-v5.12.25.md` for user-visible copy choices and cautious offline printing improvements.

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

App ID: `com.manavault.app`. Current version code: **118**. Collection storage key remains `manavault-v1`.


## v5.12.27 scanner duplicate safety

See `DUPLICATE-SAFETY-v5.12.27.md` for the conservative consecutive-name copy gate,
Webstrike-style two-line OCR recovery, and offline artwork diagnostics.


## v5.12.29 identity integrity update

See [IDENTITY-INTEGRITY-v5.12.29.md](IDENTITY-INTEGRITY-v5.12.29.md). The native-only scanner now protects distinct new cards from single-artwork-hash false duplicate rejection, restricts ambiguous split-face aliases, and offers physical-footer verification for a uniquely reconstructed stylized two-line title. On-camera review remains mandatory when evidence conflicts. No collection migration is required.
