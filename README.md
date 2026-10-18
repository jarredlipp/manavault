# ManaVault v5.12.4

Android source project for stack-scanning Magic cards. The scanner uses native CameraX and the locally indexed Scryfall printing catalog; the WebView stores collection entries.

## Changes from v5.12.3

- Successful scan receipts display a **translucent**, dismissible card panel over the live camera, including the captured thumbnail, saved name, verified set and collector number, quantity, and locally available price. The panel fades after approximately 1.45 seconds. It appears **only after** the collection write is acknowledged, alongside the existing success sound.
- Uncertain identities and printings open a **visual two-column candidate picker**, resembling the supplied TCGplayer reference. Candidate cards display Scryfall thumbnails when available, name, set, and collector number. A filter searches names, sets, and collector numbers. The picker shows eight candidates at first, with **Show more matches** for longer results.
- Selecting a printing passes its **exact printing ID** into the existing collection-save pipeline; choosing a proposed card name triggers a new identification attempt. Retry, skip, name correction and explicitly unverified saving remain available. The scanner stays paused until the current card is handled.
- Reference thumbnails load asynchronously and are never required before a selection can be made. A slow/offline network shows text placeholders; it cannot hold up the printing save. The image loader accepts only Scryfall HTTPS images.
- If an early watchdog review opens without choices, a later review containing catalog candidates replaces it rather than leaving the generic dialog in front. A saved result closes any stale review.

No changes to the camera resolution, collector/set verification thresholds, stack-change gate, or catalog generation. This is a camera UX update, **not a new recognition model**.

## Tests and build

Run `node tools/check_scan_result_ui.js`, `node tools/check_auto_save.js`, and `node tools/check_scanner.js`. Other regression scripts are in `tools/`.

Build the APK through `.github/workflows/build-apk.yml`, including hidden `.github` files when uploading to GitHub. Offline Scryfall assets are generated during the Android build. Android compilation and real-phone UI/audio testing have not been completed in this environment. Back up your collection before installing.
