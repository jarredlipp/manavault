# ManaVault v5.12.16 — Android collection export repair

**Critical data-safety repair based on v5.12.15.** The old `Export backup` and `Download JSON backup` buttons used a `blob:` URL, which Android WebView may silently ignore. Both buttons now invoke Android's Storage Access Framework (`ACTION_CREATE_DOCUMENT`) through a dedicated JavaScript bridge. The user picks a location, the app writes the exact persisted `manavault-v1` JSON without changing the collection, and rereads the saved file to verify SHA-256 and length before announcing success. Explicit failure/cancellation is shown in the app and in an Android message; a browser-only fallback labels its result as **unverified**. The same backup JSON remains import-compatible.

**Restore safeguards:** The import dialog states the number of card copies and warns that import replaces existing data. Restored records are written and reread successfully from localStorage *before* replacing the in-memory collection. Failed or cancelled imports leave the old inventory in place. Collection deletion remains a separate confirmation action.

**Test:** `node tools/check_backup_export_51216.js` and `javac -d /tmp/mv-backup-test app/src/main/java/com/manavault/app/NativeBackupWriter.java tools/NativeBackupWriterTest.java && java -cp /tmp/mv-backup-test com.manavault.app.NativeBackupWriterTest`. Existing scanner, deck, save, and undo tests continue to pass. The GitHub workflow runs export tests before expensive offline catalog generation.

Application ID remains `com.manavault.app`, storage key stays `manavault-v1`, and Android version code increases from 99 to **100**. **Do not uninstall the current APK to update** if it contains collection data; in-place updates require the same signing certificate. A full APK build and device storage-provider test are pending.

---

# ManaVault v5.12.15 — Safer collection quantity controls

This is a full source ZIP built on v5.12.14. It preserves existing collection storage (`manavault-v1`), scanner, printing database, Commander Studio and build pipeline. Android application ID remains `com.manavault.app`; version code increases from 98 to 99.

**Last-copy protection:** In Collection, tapping `−` when quantity is 1 opens a modal naming the exact card and printing, with `Keep card` focused by default. Only `Remove final copy` deletes the entry. The same binding is used for full and filtered collection lists.

**Undo:** Every `−` quantity change is reversible from a persistent Undo banner on the Collection page and an on-screen Undo notice. The latest twenty decrement actions are journaled in a separate local-storage key (`mv_collection_quantity_undo_v1`) so undo can also work after restarting the app. A final-copy undo restores all printing metadata and the original row position. Undo does not overwrite unrelated subsequent quantity additions. Importing a backup or clearing all collection data resets old undo history. Storage errors roll back the attempted mutation rather than silently losing a card.

**Testing:** `node tools/check_collection_quantity_safety_51215.js`; additional project tests in `tools/`. No physical APK compilation has been performed in this workspace. Back up your collection before updating; retain the same Android signing certificate for an in-place install.

---

# ManaVault v5.12.15

Android source for a local Magic: The Gathering collection manager, CameraX fixed-stand stack scanner, and Commander deck studio.

## v5.12.15 – printing recovery and artwork-reference alignment

- **Joined footer markers:** candidate-specific parser recognizes tightly merged set/language strings such as `EMNEN`, `SOIEN`, and `MH1EN` without interpreting arbitrary artist words as expansions.
- **Illustrator corroboration for older cards:** when the title is known, a repeatedly supported collector number can be combined with a distinctive printed illustrator surname to verify a unique card-name-scoped printing. Conflicting complete fractions, missing surname, or duplicate treatments do not verify automatically.
- **Better visual verification:** the APK artwork-index generator now downloads the **complete Scryfall card image** (`image_normal` or `image_small`), then crops exactly once to match the camera crop. `image_url` is the already-cropped Scryfall `art_crop`; it was incorrectly being cropped a second time in previous builds. The optional import builder also handles that correctly.
- **Correct manual-learning references:** favor full-card `normal` images when recording a selected printing; an `art_crop` fallback is never cropped again. The incorrectly scaled learned-artwork cache gets a separate filename, **without altering your collection or scan receipts**.
- **Older set-symbol search:** slightly broader sampling around the type bar, while preserving stringent score/separation checks so similar icons do not cause incorrect edition assignments.
- **Better manual review:** sorting by observed set code, collector numeral, and illustrator surname presents better candidates first but never hides other possible printings or claims certainty.
- **Targeted artwork seed:** adds Soul-Guide Lantern, Compulsion, Lone Wolf, Arbor Elf and Rakdos Shred-Freak to priority illustrations. The bundled artwork library is still limited (450 references); older symbol-free sets and reprints with identical art may legitimately require manual confirmation.
- **Unchanged:** CameraX stack/save behavior, Commander Studio, collection local-storage key, package name `com.manavault.app`, and scanner success receipts. Android version code **98**. An update over an installed APK still requires a matching signing certificate; export a collection backup before installing.

Offline tests: `node tools/check_514_printing_recovery.js`, `python3 tools/check_artwork_source_51214.py`, the other `tools/check_*.js` / `tools/check_*.py` suites, and native Java smoke tests. **No complete Android APK build or real-camera test was run in this environment.**

## v5.12.13 stylized title recovery and footer safety

- **Webstrike Elite photographed title:** The yellow title guide now matches the actual OCR crop. The OCR crop covers the top 23.5% of the card and can include both WEBSTRIKE and ELITE. If enhanced/contrast OCR cannot read a title, the scanner tries the original-color crop (on the first two rotations) before proceeding.
- **Partial two-line titles:** A unique leading word such as `WEBSTRIKE` can suggest the complete catalog title. This is provisional: automatic saving still requires repeated recognition plus independent printing evidence.
- **No footer/artist-as-title errors:** Printed set/language/illustrator fragments such as `DFT *EN SAM McKENZIE`, copyright lines, and `Of the Coast` are rejected from native and web title matching. Previously scanned erroneous collection entries are not modified automatically.
- **Better review instead of retries:** After repeated unreadable title observations, a captured card reaches physical footer matching. A uniquely recognized set and collector can populate the review with the suggested edition and artwork, but cannot automatically verify a card without corroborating its title. Colliding printings remain review choices.
- **Faster fallback:** Skip extra rules-text OCR for obvious footer noise. Preserve the immediate structured set+collector fast path on cards with valid title reads. Review panel is opaque for legibility.
- **No icon-source changes:** This version preserves v5.12.12's bundled Scryfall set-icon system; the separate Draftsim downloader is not integrated. It does not alter the local collection database or deck lists.

**Checks:** `node tools/check_title_footer_51213.js`, `python3 tools/check_name_layout_51211.py`, and existing scanner/build fixture checks. A full Android APK build and device camera test are still required. Back up your scanned collection before installing; an in-place update requires the same APK signing certificate.

## v5.12.12 dedicated Scryfall set-symbol atlas

- **Complete eligible set coverage, not a 280-set cap.** At APK build time, `tools/build_symbol_index.py` obtains set metadata and `icon_svg_uri` from the official **Scryfall Sets API** for every set occurring in the bundled native printing catalog. The icon SVGs are rendered to compact 24×24 silhouette masks (576 bytes per set) and stored in `symbol-index.json`. A minimum of 85% of eligible SVG icons must be generated or the build fails explicitly. Asset verification also rejects partial catalogs and superseded v2 indexes.
- **Offline symbol-first fallback.** The native scanner searches a small bounded set-symbol area on the type line, comparing color-independent silhouettes of physically observed icons with the candidate-set library. It does not download reference images during the scan. Symbols rank candidate sets but cannot by themselves distinguish visually identical symbols or printings. Strong, uniquely ranked symbol evidence must still satisfy the existing independent card-identity and printing safeguards.
- **Coverage auditing against Draftsim:** The [Draftsim set-symbol page](https://draftsim.com/mtg-set-symbols/) is a *human audit reference*, not an image source. Generated `symbol-index.json` includes `coverage.missing_set_codes` and `coverage.failed_icons` for manual cross-checking against Draftsim; no Draftsim page images are embedded or redistributed.
- The camera diagnostics now distinguish `Scryfall symbol icons: loaded/eligible physical sets` from the separate artwork-index count. Real-device symbol accuracy and full GitHub/Scryfall asset download are still unverified. Older core sets without a distinctive icon or number must remain eligible for manual confirmation rather than a guessed printing.

**Build:** The GitHub workflow and Gradle pre-build both generate and verify the Scryfall catalog, dedicated icon atlas, and artwork seed. SVG rendering requires `cairosvg` (installed in the build virtual environment). A missing or incomplete download fails the build rather than shipping a fake or tiny atlas. Run `python3 tools/check_symbol_atlas_51212.py`, `python3 tools/check_symbol_shape_java_51212.py`, and `python3 tools/check_symbol_focus_51211.py` for no-network fixture regression checks.

## Commander deck studio

**Create from any Commander** searches Scryfall for an eligible commander, whether or not you own it. Choose one of three starting points:

- **Generate from catalog:** looks up legal Scryfall cards, ranks them using the commander's rules text (artifacts, energy, Vehicles, spells, tokens, counters, sacrifice, graveyard, creature tribe, etc.), then suggests a Commander deck with 63 nonlands and 36 lands when sufficient matches are available.
- **Use owned cards:** builds a starter list from unallocated physical cards in the collection and flags incomplete decks.
- **Start blank:** creates an editable deck with the commander selected but no other cards.

Saved decks can be edited as text, added to through **Search any legal card**, exported as a decklist, and compared against owned quantities. **Building a deck never adds cards to the physical collection.** A catalog-powered build requires an internet connection at creation time; saved lists remain editable offline. Every generated deck is a *suggested starting point*: verify Commander legality, land balance, and power level before playing.

Suggested additions for existing decks explain *why* they match the commander's abilities. Card color identity, banned printings, off-color lands with basic land types, tokens, and singleton rules are considered. Cards with printed multiple-copy exemptions can still be used in owned-card starters.

## v5.12.11 orientation, title and offline visual improvements

- Landscape-oriented stacked battle cards are checked at 90°/270° before scanning their sideways rules text.
- Wider 21.3%-height, 95.6%-width title OCR captures two-line showcase names such as Webstrike Elite.
- Title-only exact alias recovery for Æther/Aether/Ether and isolated leading OCR glyphs on Invasion; no fuzzy auto-identification.
- Fully offline set-symbol ranking uses narrower, better aligned type-bar templates from up to 280 sets in the superseded v5.12.11 format. The generated atlas is v2 and the build automatically replaces older references.
- Artwork seed expanded from 300 to 450 candidate illustrations; with all competing references present, a highly distinctive bundled illustration can support unique printing selection even when footer OCR fails; indistinguishable reprints still require review.

## v5.12.10 scanner identification and review reduction

This is a focused scanner follow-up to v5.12.9. **It does not change the Commander Studio.**

- **Named tokens including Jace:** both the native CameraX name recognizer and the browser-side name matcher now consult the complete bundled SQLite printing index when the general public card-name list omits a title. The lookup is exact-name-only and cannot guess a printing. Token-planewalker detection reads a wider type bar; the footer parser recognizes token numbers printed as `T 0005`.
- **Collector number versus set total:** a standalone `185`, `205`, `269`, or `297` is no longer sufficient evidence to stop the final footer OCR rescue crop. When the OCR identifies a set code but sees only a denominator or other low-confidence number, a uniquely catalogued printing for an exact card title in that set can resolve automatically. Complete contradictory fractions, explicit rarity-prefixed contrary numbers, and multiple variants of the same named card in that set still require immediate visual review.
- **One-character OCR lookalikes:** obvious `I` / `l` text recognition errors such as `Arbor EIf` can generate a near-exact name candidate. This is not treated as an exact OCR result; repeated reads and physical printing evidence remain necessary.
- **Performance:** card-title matching still uses the existing camera/edge detection and offline indexed catalog. An extra footer crop is used only where the fast footer reads lack a credible identifier. Remote artwork downloads remain outside the live save path.

Existing 1920×1440 CameraX, automatic exposure assistance, fixed-stand stack detection, collection save receipts, success sounds, translucent result cards, printing review, and Commander Studio are retained.

## v5.12.9 Java build repair retained

The corrected `CardQuadDetector.java` edge-hit counter still compiles. The GitHub workflow now adds an *additional* native-name Java smoke test before generating the offline database, to catch the Jace lookup integration before an expensive APK build.

## Build and tests

Include hidden `.github/workflows/build-apk.yml` when uploading the source to GitHub. The workflow generates the packaged Scryfall catalog and visual reference assets before building the Android APK. Those large generated databases are not in this source ZIP.

Run `node tools/check_token_footer_51210.js`, `python3 tools/check_token_java_51210.py`, `node tools/check_deck_intelligence_5128.js`, `node tools/check_commander_creator_5128.js`, and the existing `node tools/check_*.js` checks. Android compilation, real-camera recognition accuracy, and the new deck interface on a device are **not yet independently verified**. Back up your collection before installing a newly built APK.
