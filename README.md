# ManaVault v5.9.1 — native-scanner entry fix

**The visible Android toolbar is the authoritative way to open CameraX.**
When running v5.9.1, the toolbar displays **ManaVault 5.9.1 · CameraX** and
**Open native scanner**. The Scan Cards page repeats the button at its top and
beside the labeled **Start legacy OCR camera** fallback.

If that Android toolbar is absent, the APK running on the phone was not built
from this v5.9.1 source. Do not diagnose performance using the legacy camera
in that case. The build artifact in GitHub Actions is named
`ManaVault-v5.9.1-native-debug` and includes the `ManaVault-v5.9.1-native-debug.apk`.

**Data precaution:** Local collection data can be erased by uninstalling the app.
Export a backup before changing APK signing certificates or uninstalling.

---

# ManaVault v5.9 — Native Continuous Scanner Beta

Android Magic: The Gathering collection and deck app. Version 5.9 extends v5.8's native CameraX + OpenCV + Google ML Kit scanner with **continuous capture**, **artwork-first candidates when known reference art is available**, **OCR fallback**, **stable scene-change gating**, and **a queue for manual printing confirmation**. The WebView collection, deck functions, printing catalog, Scryfall-based prices, and legacy scanner remain.

This is an independently implemented scanner informed by publicly identifiable Android camera/image-processing techniques. **No TCGplayer proprietary source, models, assets, credentials, or private endpoints are embedded.**

## Build the APK

1. Back up/export your current ManaVault collection (JSON backup in the app). Removing the Android app can erase local collection data.
2. Replace your GitHub repository files with the **contents of this project folder**; delete outdated root-level README and duplicate files. Keep `.github/workflows/build-apk.yml`.
3. Check root paths: `README.md`, `settings.gradle`, `build.gradle`, `app/`, `tools/`, `.github/`.
4. In GitHub, run **Actions → Build ManaVault Android APK**.
5. Download and extract `ManaVault-Android-debug`, then install `app-debug.apk`.

**Signing warning:** The workflow generates a debug-signed build. On temporary GitHub Actions runners, debug signing keys can change between builds. A differently signed APK cannot update the installed app, even when the package ID matches. To preserve collection data, **export the collection before any uninstall**. A persistent release signing key is recommended before routine updates.

The source has been checked with `node tools/check_scanner.js` and ZIP integrity verification, but the native Android project has **not** been compiled or run on a phone in the author's environment. GitHub Actions is the first full Android build test. Performance targets are not verified measurements.

## Continuous native scanning

From **Scan cards → Open native scanner**:

1. CameraX supplies live frames; OpenCV detects credible outer card quadrilaterals. The polygon overlay tracks the corners and yellow title area, with smoothing and rejection of sudden smaller artwork-box outlines.
2. On stable geometry, a perspective-corrected frame is analyzed. The **artwork index is tried first** and uses a cheap cached 8×8 RGB fingerprint comparison. A match is accepted as a *name candidate* only with a conservative similarity and lead over competing names.
3. If a credible artwork candidate is unavailable, ML Kit reads the title strip; up to four orientations are considered. Two agreeing OCR observations are queued, or **Keep title** can be used for an imperfect read.
4. The camera stays open and a detected card is queued **once**. Physically remove/replace it to scan the next. Detection of a different artwork across stable frames also allows continuation. The camera does not stop for individual OCR failures.
5. **Review (N)** ends capture and opens the queued results in the collection interface. Confirm the physical **set, collector number, artwork and foil status** for each card, or skip it. **No card is added merely because OCR or artwork matched a name.** The review process runs after the continuous camera session; it is not yet an in-camera instant-save interface.
6. Once you confirm a printing, ManaVault asynchronously caches its Scryfall art crop for future artwork-first recognition. It will also optionally compare the selected printing's art against the scanned photo (advisory only).

The live metrics show approximate milliseconds spent on edge detection, perspective correction, artwork matching, and OCR. Scanner launch speed and card recognition speed are separate metrics. Neither is promised to be sub-second on all hardware.

### Artwork-index coverage (important)

**v5.9 does not include a complete precomputed fingerprint database for every MTG printing.** Fingerprint comparison can identify a card from artwork only if the card's art appears in the reference index. Without references, ML Kit OCR remains the identification path. The native cache grows as you confirm printings and survives ordinary upgrades that preserve Android app data.

You may optionally build a broader reference index from authorized Scryfall bulk metadata and art crops:

```bash
python -m pip install pillow requests
python tools/build_visual_index.py default_cards.json app/src/main/assets/visual-index.json --limit 3000
```

`default_cards.json` is a downloaded Scryfall default-cards bulk file. The script fetches art crops, so building a large index takes time, bandwidth and storage; comply with Scryfall usage policies. **Indexes beyond 28 MB are not supported by the current on-device parser.** `visual-index.json` is optional and is intentionally not included in the source package; keep it as a single file within Android assets if you build it. The compact index format is `manavault-visual-v1` (same reference layout the legacy scanner accepts). Artwork similarity is a heuristic, not definitive evidence of the exact edition.

## Implementation map

- `MainActivity.java` — WebView collection interface; batches and review bridge; asynchronous Scryfall artwork-cache updates.
- `NativeScannerActivity.java` — CameraX live session, scene gating, ML Kit OCR, multi-card queue and frame-stage timings.
- `CardQuadDetector.java` — OpenCV outer-edge detection, perspective correction, localized OCR contrast.
- `CardEdgeOverlay.java` — stabilized polygon and title region.
- `VisualMatcher.java` — locally bundled/learned artwork index, bounded fingerprint cache, conservative matching.
- `ArtworkVerifier.java` — optional selected-printing art comparison; never definitive.
- `app/src/main/assets/index.html`, `card-db.js` — existing WebView UI, offline card catalog, pricing and printing confirmation.
- `tools/build_visual_index.py` — optional creation of reference-art index.
- `tools/check_scanner.js` — JS syntax, project and matching smoke checks.
- `app/build.gradle`, `build.gradle` — two distinct Gradle files required for the Android build.

There is one root `README.md` and one authoritative source per component. Obsolete copies should be deleted from GitHub when replacing the repository.

## Limitations and troubleshooting

- **No reference art / unexpected name:** Limited visual index coverage or dark/glared photos can produce a miss. OCR is a fallback, not a guarantee.
- **Hard-to-detect borders:** Put the entire physical card in view with a contrasting mat. Internal rectangles and rotated Battle cards can still confuse early builds.
- **Different printing of same artwork:** A visual match only proposes the name. Confirm the actual edition; the scanner never silently selects a different set.
- **Duplicate detection:** Remove the first card fully before inserting another. Two copies with identical art may require visible removal, which resets the scene gate.
- **Price unavailable:** Scryfall USD is a TCGplayer-referenced estimate. Card Kingdom feed access remains experimental; missing prices are not necessarily $0 value.
- **Build fails:** Share the *first* Gradle compiler error from GitHub Actions. Browser JavaScript checks alone do not validate Android APIs.

ManaVault is an unofficial personal project; it is not affiliated with Wizards of the Coast, TCGplayer or Card Kingdom.
