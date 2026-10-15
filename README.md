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

## v2.9 fixed-phone-stand scanning profile
- Adds an enabled-by-default **Fixed phone stand** option for the card placement shown in the supplied screenshot: card top ~29% down the preview and card width ~76%.
- Updates the guide and crops the title from the normalized card image, not the blank area above the card.
- Defaults the OCR strip to 35 pixels high and 295 pixels wide in the 320×440 normalized card canvas (existing custom settings persist).
- Allows a complete card name appearing as a standalone substring within noisy OCR text to become a candidate, requiring the normal multi-read confirmation logic.
- Disable fixed stand mode when scanning handheld or using the future Raspberry Pi camera.
- This is a calibrated prototype. The camera video aspect ratio and CSS preview may differ on devices, so confirm the live OCR title preview aligns before batch scanning. Not compiled or tested on hardware.


## v3.1 scanner OCR test changes
- Fixed-stand OCR now crops from a 960x1320 working image instead of a 320x440 downsample.
- OCR title coordinates are scaled consistently with the working image.
- Removed the hard contrast stretch that exaggerated noisy edges on dark card titles.
- Live OCR crop preview and calibration sliders now sit directly beneath the camera preview.
- Camera start retries loading the bundled visual index. **The provided repository has no visual-index.json and the workflow does not generate it**, so artwork matching still requires a separately generated index.
- Existing saved calibration settings are retained. Press Reset OCR alignment to use new defaults.

This is a source-level test update; Android APK compilation and on-device OCR accuracy have not been verified.

## v3.2 — Alternate-layout OCR and artwork index build

- After three unsuccessful standard title OCR reads, the live scanner rotates through additional regions: bottom title (full-art basic lands), upper-center showcase/poster title, very top borderless title, and lower-middle alternate title. The currently scanned region appears beside Last OCR. This is a heuristic fallback, not a trained layout classifier.
- Fixed artwork-first matching crop coordinates for the 960x1320 live card image. Prior code incorrectly sampled the old 320x440 crop coordinates.
- GitHub Actions now attempts to generate and bundle `visual-index.json` from Scryfall's Unique Artwork bulk export before building. This requires tens of thousands of image downloads, can be slow, and is dependent on network/API availability. Build will fail if the index cannot be produced, rather than silently ship with zero references.
- All image-based matching remains experimental; confirm uncertain cards and exact printings. Not tested on device or built as an APK in this environment.
