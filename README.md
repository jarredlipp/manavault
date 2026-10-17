# ManaVault v5.9.3 — Native fullscreen scanner

The native CameraX screen now requests immersive edge-to-edge camera preview, hides Android status/navigation bars until a swipe reveals them, and keeps its top diagnostics and bottom controls inside display-cutout safe insets. This is a fullscreen camera UI fix, not a new artwork-recognition engine. Android may show system bars momentarily after a swipe or permission dialog. PreviewView uses `FILL_CENTER` to fill the display, which necessarily crops some camera field of view on tall phone displays; the detector and overlay retain their prior behavior.

## Testing on phone

1. Confirm the app toolbar says **ManaVault 5.9.3 · CameraX**.
2. Open **native scanner**. The live image should extend to all screen edges; the top diagnostics must be readable and bottom controls fully tappable.
3. Scan a card, enable/disable flashlight, temporarily swipe to reveal system bars, and return. Verify the camera restores fullscreen.
4. Scan a second card and tap Review; ensure the printing-confirmation queue still appears.
5. If the camera appears zoomed, note that fullscreen `FILL_CENTER` crops a 4:3 camera stream to fill a tall 9:20-ish display. This is expected and distinct from the legacy browser zoom control.

# ManaVault 5.9.2 — Native scanner runtime & artwork-index fix

ManaVault is an independent Android MTG collection, deck, and card-scanning app. Its primary scanner uses **CameraX, OpenCV, and ML Kit**; the older WebView/OCR camera remains available as a fallback. Printing confirmation and collection management run in the WebView. This release preserves collection valuation using Scryfall prices and optional Card Kingdom comparison.

## What changed since 5.9.1

- **WebView startup crash fixed.** The pricing extension can now wrap `info()` without throwing `Assignment to constant variable` (`index.html` former line 841). That exception previously stopped the rest of the JS scanner-review wiring from initializing.
- **Artwork index is a real Android asset.** `app/src/main/assets/visual-index.json` is present even in offline builds, eliminating a misleading 404. A blank asset deliberately leaves artwork-first detection unavailable and uses ML Kit OCR.
- **Optional Scryfall starter artwork references at build time.** GitHub Actions tries to download a small set of about 40 commonly used/tested cards and generate 8×8 RGB fingerprints before compiling. If the network request fails, the build may continue with the included empty index, and the native scanner reports the actual number of references. The starter index **does not cover all MTG cards**; expanding the reference database is a separate undertaking.
- **Better Android navigation-bar/status-bar spacing**, both for the always-visible native launch toolbar and for the scanner's top status/bottom controls on Android edge-to-edge displays.
- **Per-card scan timing** (`card_ms`) added to the native review results alongside total time since the camera opened (`elapsed`). Individual processing timings are also included.
- App/build markers moved to **5.9.2**, and the regression checker now guards against the `const info` startup crash and missing artwork asset.

These are source fixes. Android compilation and live camera tests still need to run in GitHub Actions/on a phone; the JavaScript syntax checker cannot guarantee an Android build.

## Build on GitHub Actions

1. Export **Backup** in ManaVault if the existing collection matters. Uninstalling or signing with a new debug key can erase local app data.
2. Replace your repository project files with these ZIP contents, including the `.github` folder. Delete old duplicate root files when replacing. The root of the Git repository should contain `settings.gradle`, `build.gradle`, `README.md`, `app/`, and `tools/`.
3. Go to **Actions → Build ManaVault Android APK → Run workflow**. The workflow checks JavaScript and project structure, attempts the **optional artwork seed**, and builds the APK.
4. Download artifact `ManaVault-v5.9.2-native-debug`, extract it, and install the included APK.
5. Open the app: the native Android header should say `ManaVault 5.9.2 · CameraX` and include **Open native scanner**. The old `Start legacy OCR camera` button is not the native scanner.

**Debug signing warning:** GitHub Actions may generate a new debug certificate on each fresh runner. APKs with different signatures cannot update each other. Do not uninstall an app with collection data until you have exported and verified a backup.

## Native scanner usage

1. Tap **Open native scanner** at the top of the app or on the **Scan cards** page.
2. Put one complete card in frame. The green four-corner outline turns solid after stable detection. The yellow title region derives from the detected geometry.
3. When artwork references are available, a candidate artwork fingerprint may identify the card; otherwise ML Kit tries OCR. A match is only a candidate name, **not a confirmed printing**.
4. Remove the card before placing the next card. Tap **Review (N)** to return to the collection screen.
5. Choose the physical set and collector number for each queued card, and confirm foil and condition as relevant. No cards are saved automatically based solely on the camera image.

The status line reports `Artwork references: N`. When N is zero, artwork-first matching has no index to compare against and OCR fallback is expected; it is not an ML Kit error. The first build's optional index-generation step is visible in GitHub Actions logs. Identified printings can be learned into the native artwork cache after confirmation when their Scryfall art is reachable.

## Developer layout

- `app/src/main/assets/index.html` — collection UI, printing review, JS/native bridge, pricing, legacy camera
- `app/src/main/assets/card-db.js` — offline Scryfall catalog
- `app/src/main/assets/visual-index.json` — bundled artwork fingerprints (valid empty baseline; CI can populate)
- `app/src/main/java/com/manavault/app/MainActivity.java` — WebView, native launch toolbar, scan batch bridge
- `NativeScannerActivity.java` — CameraX analyzer, ML Kit OCR, scan queue, stage timing (same Java folder)
- `CardQuadDetector.java` — OpenCV quadrilateral detector, rotation/perspective correction
- `CardEdgeOverlay.java` — live green outline and yellow title overlay
- `VisualMatcher.java` — bundled/learned artwork features and candidate matching
- `ArtworkVerifier.java` — advisory artwork comparison during printing review
- `tools/build_seed_visual_index.py` — optional small Scryfall-derived startup index for GitHub Actions
- `tools/build_visual_index.py` and `tools/download_and_build_index.py` — optional *larger* reference-index generation utilities
- `tools/check_scanner.js` — JavaScript syntax, recognition smoke tests, project/regression checks

A more comprehensive index can be built on a machine with internet access:

```bash
python -m pip install requests pillow
python tools/build_visual_index.py downloaded-scryfall-bulk.json app/src/main/assets/visual-index.json --limit 3000
```

This process downloads reference artwork and may require substantial time. Respect Scryfall's rate limits and usage terms. Existing `VisualMatcher` loads a compact 8×8 RGB feature index with a 28 MB parser size limit. Reference-art comparison is approximate and can fail under glare, low light, or different artwork layouts. The app is not affiliated with TCGplayer, Wizards of the Coast, or Card Kingdom, and includes no proprietary code or extracted model from another app.

## Running local checks

```bash
node tools/check_scanner.js  # includes the full no-network JS startup smoke test
python -m py_compile tools/build_seed_visual_index.py tools/build_visual_index.py
```

These tests do not compile the Android application. If GitHub Actions fails, use the **first Gradle error**, not later cascading errors, for troubleshooting.
