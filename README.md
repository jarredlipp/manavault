# ManaVault v5.12.8

Android source for a local Magic: The Gathering collection manager, CameraX fixed-stand stack scanner, and Commander deck studio.

## Commander deck studio

**Create from any Commander** searches Scryfall for an eligible commander, whether or not you own it. Choose one of three starting points:

- **Generate from catalog:** looks up legal Scryfall cards, ranks them using the commander's rules text (artifacts, energy, Vehicles, spells, tokens, counters, sacrifice, graveyard, creature tribe, etc.), then suggests a Commander deck with 63 nonlands and 36 lands when sufficient matches are available.
- **Use owned cards:** builds a starter list from unallocated physical cards in the collection and flags incomplete decks.
- **Start blank:** creates an editable deck with the commander selected but no other cards.

Saved decks can be edited as text, added to through **Search any legal card**, exported as a decklist, and compared against owned quantities. **Building a deck never adds cards to the physical collection.** A catalog-powered build requires an internet connection at creation time; saved lists remain editable offline. Every generated deck is a *suggested starting point*: verify Commander legality, land balance, and power level before playing.

Suggested additions for existing decks explain *why* they match the commander's abilities. Card color identity, banned printings, off-color lands with basic land types, tokens, and singleton rules are considered. Cards with printed multiple-copy exemptions can still be used in owned-card starters.

## Camera and printing recognition

Retains v5.12.7 card-edge improvements, 1920×1440 CameraX high-resolution option, fixed-stand stack detection, live collection writes and save sounds, low-light controls, printing footer parsing, symbol/artwork references, and immediate ambiguous-printing review. The camera code is not modified by the deck studio update.

## Build and tests

Include hidden `.github/workflows/build-apk.yml` when uploading the source to GitHub. The workflow generates the packaged Scryfall catalog and visual reference assets before building the Android APK. Those large generated databases are not in this source ZIP.

Run `node tools/check_deck_intelligence_5128.js`, `node tools/check_commander_creator_5128.js`, and the existing `node tools/check_*.js` checks. Android compilation, real-camera recognition accuracy, and the new deck interface on a device are **not yet independently verified**. Back up your collection before installing a newly built APK.
