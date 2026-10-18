# ManaVault v5.11.2 — Android source

Magic: The Gathering collection and deck manager with native CameraX card scanning. The Android project uses Java, Gradle, OpenCV, ML Kit, and a WebView frontend.

## Build

Keep the folder structure, including `.github/workflows/build-apk.yml` (the GitHub Actions workflow). Upload **the contents** of this folder to the GitHub repository root. In GitHub, run **Actions → Build ManaVault Android APK → Run workflow** and download the resulting APK artifact. An APK is not included here.

**Before installing:** Export a collection backup from the existing app. Debug APKs signed with different keys may require uninstalling an earlier build, which can erase local collection data.

## Camera resolution (v5.11.2)

The native scanner defaults to **High 2.8MP**, requesting a **1920 × 1440** (4:3) CameraX analysis stream. Use the **High 2.8MP** button at the top of the scanner to cycle between **High 2.8MP → Balanced (1280 × 960) → Auto**; the selection is retained between launches. CameraX negotiates the actual supported resolution with the hardware and preview pipeline, which may be different from the requested size; unsupported settings fall back to Auto. The live diagnostic shows the **actual CameraX analysis frame**, its upright dimensions, and the **perspective-corrected card crop** dimensions. These are not the phone camera sensor's maximum photo resolution.

Card rectification now preserves up to **1260 × 1760 pixels** based on the detected card's actual pixel dimensions rather than always shrinking to 630 × 880. The captured JPEG uses higher quality to protect small printed collector numbers. Footer OCR uses bounded, adaptive enlargement and a contrast-enhanced pass; enlarging a small crop cannot recover detail that the camera did not capture. Higher analysis resolution may increase processing time and power use. Choose Balanced or Auto if the viewfinder slows, the device overheats, or resolution negotiation fails. OCR accuracy on physical devices has not yet been verified.

## Fixed-stand detail and printing evidence (v5.11.2)

When **High 2.8MP** is selected, the scanner waits for four stable frames and evaluates the card width in the actual CameraX analysis bitmap. If it is below roughly 850 pixels and the entire card can remain inside the frame with a safety margin, it requests a conservative, **one-time CameraX sensor zoom (up to 1.65×, subject to hardware support)** before scanning. This is not equivalent to enlarging a 630px crop after capture: it asks the camera to use a tighter image region. Some devices still digitally interpolate instead of gaining optical/sensor detail. The diagnostics show the applied zoom and the resulting actual card-crop dimensions. If the card is off-center, already large enough, or zoom is unavailable, no zoom is applied. Balanced and Auto modes do not apply fixed-stand zoom.

The Java footer reader now prioritizes two tight bottom-left crops, in color and contrast-enhanced OCR. A new paired-evidence resolver accepts **collector number and exact Scryfall set code read together in the same physical footer line**, with title corroboration. It handles a common OCR confusion such as `RO345 DFT` for `R 0345 DFT`. Generic numbers from rules text, power/toughness, copyright years, and contradictory candidates are not trusted for automatic printing assignment. This improves the evidence priority; it cannot guarantee all footers are legible.

## Live, finalized stack scanning (v5.11.2)

Place the first card on the fixed stand. CameraX identifies the card, resolves its exact printing using the cached Scryfall catalog, collector number, printed set code, set symbol and available artwork evidence, and writes the resulting entry to local collection storage. **The scanner does not accept a different top card until that save finishes.** A short native system acknowledgement sound is played only after a successful collection save receipt. Then place the next physical card directly on the previous card. The stack-change detector rearms automatically; identical-looking physical copies may require the explicit Same copy control and its confirmation dialog.

**Immediate printing review:** When the set, collector number or identity cannot be reliably verified, a native review dialog appears over the camera instead of postponing the decision until Finish. If the name is known, the dialog lists candidate physical editions with a live filter (type set name, set code, or number), and you can select the correct Scryfall printing, deliberately save the name as Printing unverified, retry this card, or skip it. When the name is not known, you can type the correct name, retry, or skip. No candidate printing is guessed silently. Nothing is written to the collection until the identity has been verified or a user explicitly elects to keep an unverified entry. The camera pauses until the choice has been processed. Finish waits for the current scan; it does not begin a delayed manual-review batch. A Close anyway escape hatch remains for unrecoverable stalls.

The collection search supports combined offline **name / set / collector number, mana color or identity, and card type** filters, with sorting by name/value. The Search cards section can search the online Scryfall catalog by **name, color identity, card type, set, and rarity**. Color and type filters depend on saved metadata; older unverified entries may lack those fields until they are resolved. Exact-printing accuracy still depends on camera focus, OCR and reference-image availability. Foil and condition are not reliably determined by camera images.

## Project layout

- `app/src/main/java/com/manavault/app/`: native CameraX, OCR, framing, and printing verification.
- `app/src/main/assets/`: application UI, local Scryfall printing catalog logic, and artwork-index asset.
- `tools/`: source utilities and offline JavaScript regression checks.
- `.github/workflows/build-apk.yml`: build and APK artifact workflow.
- `build.gradle`, `settings.gradle`, `gradle.properties`, and `app/build.gradle`: required Gradle configuration.

## Local checks

```sh
node tools/check_auto_save.js
node tools/check_runtime.js
node tools/check_scanner.js
bash tools/check_reference_cache.sh
```

The source ZIP deliberately contains an empty bundled seed placeholder: the APK build generates its actual artwork references on GitHub. The build fails if fewer than 12 Scryfall references can be fetched, so retry the workflow when network access is available. The source ZIP is not a compiled APK; Android compilation and physical-camera behavior have not been verified by these JavaScript tests.

## Live scan receipts (v5.11.2)
Each capture has a unique scan ID and its own photographed image. The WebView returns one final receipt after saving the verified printing, saving an explicitly accepted unverified name, retrying, or skipping. Already-processed scan IDs are not replayed when Finish closes the camera. Export your collection before updating.

## Stack-and-scan change detection (v5.11.2)

The native scanner no longer requires the previous card to be removed. It waits for a stable change in artwork **and** whole-card perceptual appearance, then automatically identifies and saves the newly visible top card while the camera remains open. A stationary card, transient glare, or contour jitter must not be counted as another card. A new physical copy with identical art can only be inferred after an intervening substantial covering event and a stable reveal; a camera cannot prove that two visually identical cards are separate without such motion evidence. **Manual next** remains an optional fallback when a copy cannot be distinguished automatically; do not use it on a stationary, already-saved card.

`Finish` waits for pending collection writes; uncertain identities trigger review immediately, not at the end. The on-device printing cache remains incomplete and collector-number OCR may still misread some editions. An Android device camera test remains necessary to validate practical capture and duplicate rates.

Run `sh tools/check_stack_gate.sh` for the standalone Java state-machine regression suite, plus `node tools/check_scanner.js` and `node tools/check_auto_save.js`.
