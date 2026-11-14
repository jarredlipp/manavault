# ManaVault — camera focus, foil lighting, low-contrast card edges, and text-region patch

**Status:** Added and tested scanner helper code. **NOT** integrated into the production `NativeScannerActivity.java`; **NOT** a rebuilt or signed APK. The supplied diagnostic ZIP only contained logs, and the source archive previously indexed in Library could not be materialized. This package preserves all previous app features and source files by design. It must be merged into the actual current project before the phone app changes.

## What changed (source code)

- `HandheldCameraXAnalyzer.java`: Requests focus/AE promptly on the first received frame, even if a dim/blurred card has no detectable border; requests card-centered focus after geometry is found; throttles AF calls; keeps latest-frame and rapid proxy release; hands the recognition engine individual card-relative text regions and color artwork data. Provides `setTorchMode(OFF/AUTO/ON)` when bound with `CameraInfo` and `CameraControl`. Emits `camera_first_frame`, `first_card_geometry`, `focus_request`, `lighting`, `capture`, and `verification` telemetry.
- `CameraXLightingController.java`, `AdaptiveLightingPolicy.java`, `FrameIlluminationMetrics.java`: Implements sustained-low-light torch automation and glare-triggered torch-off with hysteresis/cooldown; supports direct manual Off/Auto/On override. Applies bounded CameraX AE compensation **only when** supported. Gradually returns exposure compensation to neutral as lighting recovers. A flashlight transition cannot indefinitely flash on/off every video frame.
- `AdaptiveCardGeometryDetector.java`: Retains the existing gradient-based detector and adds an inexpensive bounded-contrast fallback for dark card borders against dark backgrounds. Does not treat card geometry as evidence of printing identity.
- `CardTextRegions.java`: Provides raw and conservatively contrast-adjusted title, footer (set/collector), type-bar, and alternate-title OCR regions extracted after perspective correction. It does not secretly replace or delete the existing OCR engine; the production `RecognitionEngine` must use these regions and compare their evidence.
- `HandheldScanCoordinator.java`: A brief (340 ms) post-lighting stabilization window prevents torch changes from being mistaken for new physical cards. Absence/removal detection still runs throughout the window.
- Existing `PrintingEvidenceResolver` and its verified-singleton / independent printing-evidence safeguards are unchanged, as are existing deck and collection features.
- New `CameraFocusLightingTest.java` covers dark/foil intensity, torch debounce, manual overrides, black-on-dark card geometry, crop dimensions, first-frame AF with no geometry, and duplicate prevention. Prior tests continue to run.

## Must-wire integration (production Android source)

1. Reuse the already-bound CameraX `Camera` instead of opening another camera for scanning. Bind the preview immediately, before waiting for Scryfall catalog or artwork initialization. Prewarm the existing offline database in the application process; **do not launch bulk refreshes while CameraX is active**. Existing `ScannerStartupCoordinator.beginScan()` and `endScan()` must bracket camera start/stop.
2. Replace the actual live `ImageAnalysis` analyzer with the provided `HandheldCameraXAnalyzer`. It already returns correctly oriented, perspective-corrected captures. Keep the same existing OCR/visual match/save/success sound callbacks and the real `NativePrintingIndex` adapter.
3. Construct with **both** camera controls and camera info:

   ```java
   ImageAnalysis analysis = HandheldCameraXAnalyzer.latestFrameAnalysis();
   Camera bound = cameraProvider.bindToLifecycle(owner, selector, preview, analysis);
   HandheldCameraXAnalyzer analyzer = new HandheldCameraXAnalyzer(
       existingGeometryDetector, existingAsyncOcrAndPrintingMatcher,
       existingDurableSaveAndReviewSink, cameraExecutor,
       bound.getCameraControl(), bound.getCameraInfo());
   analysis.setAnalyzer(cameraExecutor, analyzer);
   analyzer.setTorchMode(AdaptiveLightingPolicy.Mode.AUTO);
   ```

   Replace `existing...` variables with real app adapters; these names are deliberately illustrative, **not compilable project code**. Do not attach two analyzers to the same `ImageAnalysis` use case.
4. In the existing scanner UI, connect the flashlight control to `analyzer.setTorchMode(Mode.OFF / Mode.AUTO / Mode.ON)` on the **camera executor**, and reflect the user's choice in the label. Keep previous zoom and camera controls. The production UI must not toggle the torch on every poor frame.
5. The recognition adapter should feed `capture.textRegions.title` (raw/enhanced) into title OCR, `footer` into collector/set OCR, `typeBar` into symbol verification, and `alternateTitle` when title location is uncertain. Retain full `capture.rectifiedGray` for nonstandard layouts and `capture.rectifiedRgb` for artwork/color/symbol matching. Set-symbol and collector evidence must be **observed**, not reconstructed from a guessed printing.
6. Only auto-save when `PrintingEvidenceResolver` confirms a unique paper printing from a complete catalog or independent set/collector evidence. Keep true ambiguities in manual review; retry unrecognized titles automatically; do not save an unverified printing just because one visual candidate survived.
7. On manual picker close call `analyzer.completeManualReview(saved)` on the camera executor. Close the analyzer and unbind on lifecycle stop. Retain app identifier and original signing certificate so collection data is not lost.
8. Add to the existing release workflow AFTER APK assembly: `python3 tools/verify_scanner_wiring.py app/build/outputs/apk/debug/app-debug.apk`. `tools/check_camera_wiring.py` can be run before assembly. **Both must pass** before release; static checks alone do not validate the camera.

## Tests performed here

```text
PASS: 40 scanner core assertions
PASS: 11 startup reuse / deferred-refresh assertions
PASS: 17 fast scene-transition / duplicate-safety assertions
PASS: CameraX adapter compiles against local API stubs (NOT an Android APK build)
PASS: camera adapter release / save / duplicate assertions
PASS: 49 handheld recovery / exact-save assertions
PASS: 28 focus / lighting / foil / dark-edge / OCR-region assertions
```

Total **145 individually counted Java assertions**, plus separate camera-adapter assertions. These are synthetic/unit tests, **not** actual 5.13.8 on-device accuracy or startup measurements.

## Apply safely

From a clean branch of the current Android Gradle project:

```bash
python3 tools/install_additive.py /path/to/manavault --dry-run
python3 tools/install_additive.py /path/to/manavault
python3 tools/check_camera_wiring.py /path/to/manavault
```

The installer only installs known scanner helper files and tests. It refuses customized conflicts and won't delete or overwrite completed decks or main app features. It does **not** modify the production activity automatically; manually connect it as above. A full source tree is required to run `tools/validate_scanner_release.sh` and produce an APK.

## Acceptance tests for the next real build (NOT yet passed)

- Camera preview and first active AF/AE request timing recorded from user action.
- 100+ consecutive handheld cards plus a fixed-stand comparison, including black/black mat, foil glare, set symbols, modern and older frames, collector numbers, physical singleton printings, and repeated identical copies.
- Zero false automatic exact-printing saves; no manual picker on empty OCR; no repeated catalog imports while scanning; actual manual selection and p50/p95 capture-to-save latency measured.
- Real target device CameraX torch/AE compatibility, autofocus recovery, resource warm-start, lifecycle cleanup, app release signing and data preservation verified.
