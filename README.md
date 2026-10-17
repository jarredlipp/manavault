# ManaVault 5.10.0 — Native automatic collection scanner

Android source for ManaVault, a Magic: The Gathering collection, deck, and price-tracking app. The primary scanner uses **CameraX + OpenCV + Google ML Kit**. The WebView-based camera remains available as a fallback. The app is not affiliated with TCGplayer, Wizards of the Coast, or Card Kingdom, and does not reuse another app's proprietary code or recognition model.

## What's new

- **Automatic saving when evidence is sufficient.** A confidently identified card with exactly one *physical English* Scryfall printing is saved without a confirmation screen.
- **Artwork-based printing comparison.** Where a name has multiple printings, ManaVault compares the rectified scan to candidate Scryfall `art_crop` images. It auto-saves only when *all relevant printing artworks could be checked*, the winning artwork comparison is clearly separated, and that illustration corresponds to just one printing. Reprints using the same art stay in the **exception review queue** rather than being assigned to a random edition.
- **Uncertain results are not silently saved.** Incomplete/absent catalogs, similar artworks, unsupported layouts, ambiguous names, or failed network requests require user review. A name match is not a printing match. Artwork alone cannot prove foil or card condition: auto-added cards default to nonfoil (except foil-only printings) and NM, both editable in Collection.
- **Faster native scan:** recently verified corners guide subsequent OpenCV edge searches; fewer full-frame passes, around 75 ms between frame evaluations, two stable edge observations, and *one* ML Kit title read when the title exactly matches the cached Scryfall name catalog. Weaker OCR evidence still needs confirmation. Timings shown in the scanner are stage times, not guaranteed end-to-end identification latency.
- **Offline title index** is supplied from Scryfall's public name catalog to the native code for rapid exact matching (including Adventure faces). Names are cached on the phone for future sessions. The app does not require an English dictionary, so unusual card names are valid.
- **Continuous capture with batch auto-save.** Place a card, wait for capture, remove it and present the next. Tap **Finish / save (N)** once. When the Android scanner closes, ManaVault automatically resolves and saves the confident printings in the WebView; only exceptions enter the existing printing-review screen. An **Undo last auto-saved card** control is available during that app session.
- Preserves collection, price references, printing IDs, decks, backups, and the old WebView scanner. Existing collection entries are not deleted or migrated.

## Why some cards still require review

There can be dozens or hundreds of printings of one card. Different sets sometimes use identical artwork, which cannot be differentiated by art similarity alone. ManaVault conservatively declines automatic selection when two printings share artwork, there are more than 12 distinct reference artworks, or comparison confidence is insufficient. Full Scryfall metadata does not imply the app has already downloaded all artwork; multi-printing image verification needs network access to retrieve those reference images. The prebuilt artwork-first index is optional and usually small.

This release is not a completed reproduction of TCGplayer's recognition performance. No sub-second end-to-end guarantee is made. Native processing speed, edge accuracy, lighting response, and artwork thresholds require validation on a physical phone.

## Building and installing

1. Export your existing collection using **Export backup**. A debug APK signed with a new GitHub runner key may not update an old build; uninstalling the app can destroy its local data.
2. **Replace** the GitHub source repository with the contents of this ZIP, deleting obsolete root files. Keep `.github/workflows/build-apk.yml`.
3. Open GitHub **Actions → Build ManaVault Android APK → Run workflow**. The workflow runs JavaScript tests, optionally builds a small Scryfall visual reference index, and uses Gradle to compile the APK.
4. Download the `ManaVault-v5.10.0-native-auto-debug` artifact, extract the APK, and install it.
5. The main toolbar should say **ManaVault 5.10.0 · CameraX**. Tap **Open native scanner**, not the legacy camera button. After scanning, select **Finish / save** and watch ManaVault classify and save captured cards.

## Project structure

- `app/src/main/java/com/manavault/app/NativeScannerActivity.java`: CameraX preview, continuous tracking, fast OCR, scene-change detection, capture queue.
- `CardQuadDetector.java`, `CardEdgeOverlay.java`: OpenCV card quadrilateral detection, ROI tracking, rectification, live overlay.
- `NativeNameIndex.java`: offline exact card-title matching.
- `VisualMatcher.java`, `ArtworkVerifier.java`: optional broad artwork matching and conservative candidate-specific printing comparisons.
- `MainActivity.java`: native launch button, native-to-WebView result handoff, artwork and name index bridges.
- `app/src/main/assets/index.html`: collection, printing resolution, auto-save decisions, manual exceptions, pricing, backups, legacy scanner.
- `app/src/main/assets/card-db.js`: Scryfall printing catalog (all printing records and illustration ID for future downloads).
- `app/src/main/assets/visual-index.json`: optional compact bundled artwork index.
- `.github/workflows/build-apk.yml`: APK build workflow.
- `tools/check_scanner.js`, `tools/check_runtime.js`, `tools/check_auto_save.js`: checks for startup, matching, and safe automatic printing selection.

## Local validation

```sh
node tools/check_scanner.js
node tools/check_auto_save.js
python -m py_compile tools/build_seed_visual_index.py tools/build_visual_index.py
```

The tests do **not** compile Android code. The final APK and on-device CameraX behavior must be verified through the GitHub Actions build and a phone test. Android uses a root and app Gradle file; both are required and not redundant.
