# ManaVault v2.8 — Scanner, Printing Review, Deck Integration

Based on v2.7. New:

- Experimental automatic camera card framing toggle (off by default; keep manual title crop sliders for fallback).
- Printing review on visual and OCR matches: retrieves alternate printings from Scryfall and lets the user select the set and collector number before saving. Artwork alone cannot determine exact printing. Foil status still needs separate verification.
- Continuous-scanner deck insertions and deck search/import now distinguish Scryfall printing IDs instead of merging printings with the same name. Collection inventory already keys by card ID.
- Preserves the v2.7 full unique-artwork fingerprint builder, bulk card database, camera controls, sound and deck destination UI.

**Important limitations:** Automatic framing uses a lightweight image-edge heuristic, not a trained card detector. It is opt-in and untested on real devices. Printing selection requires network access for Scryfall's prints API; if offline, the suggested printing can still be selected, but exact edition is not independently verified. This does not yet implement set-symbol OCR, collector-number OCR, foil detection, or a full offline printing database. Full artwork indexing is still a lengthy GitHub Actions build; no prebuilt index or APK is included.

## Build

Extract the ZIP contents to your GitHub repository root, commit, and run `.github/workflows/build-apk.yml`. The workflow downloads artwork fingerprints and compiles the APK. Its run summary displays actual APK size. The source ZIP is not the APK.

## Testing

Back up your collection before installing. Start the scanner, optionally enable automatic framing, scan a card, choose the correct printing in the review, and confirm the card appears in the chosen deck/collection. Test the sound, crop and duplicate prevention separately.
