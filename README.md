# ManaVault v5.12.10

Android source for a local Magic: The Gathering collection manager, CameraX fixed-stand stack scanner, and Commander deck studio.

## Commander deck studio

**Create from any Commander** searches Scryfall for an eligible commander, whether or not you own it. Choose one of three starting points:

- **Generate from catalog:** looks up legal Scryfall cards, ranks them using the commander's rules text (artifacts, energy, Vehicles, spells, tokens, counters, sacrifice, graveyard, creature tribe, etc.), then suggests a Commander deck with 63 nonlands and 36 lands when sufficient matches are available.
- **Use owned cards:** builds a starter list from unallocated physical cards in the collection and flags incomplete decks.
- **Start blank:** creates an editable deck with the commander selected but no other cards.

Saved decks can be edited as text, added to through **Search any legal card**, exported as a decklist, and compared against owned quantities. **Building a deck never adds cards to the physical collection.** A catalog-powered build requires an internet connection at creation time; saved lists remain editable offline. Every generated deck is a *suggested starting point*: verify Commander legality, land balance, and power level before playing.

Suggested additions for existing decks explain *why* they match the commander's abilities. Card color identity, banned printings, off-color lands with basic land types, tokens, and singleton rules are considered. Cards with printed multiple-copy exemptions can still be used in owned-card starters.

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
