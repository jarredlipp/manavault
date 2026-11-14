# ManaVault native scanner — real CameraX integration bridge and release guard (Nov 14, 2026)

## Status / scope

This archive **implements a compile-tested native camera-session bridge**, preserves the earlier camera/focus/foil/recognition helper sources, and **upgrades the APK verification check to inspect actual DEX method instructions**. The target production Android project was **not accessible as editable Java source in this session**. The newest supplied archive, `ManaVault-Android-debug(1).zip`, contains only `app-debug.apk`.

**No production APK was rebuilt or signed. No modifications were made to the user's installed app or collection. This is not a release.** The existing APK fails the new release guard: the live `NativeScannerActivity` does not call the new scanner pipeline.

## New executable integration layer

`app/src/main/java/com/manavault/app/scanner/NativeScanCameraSession.java` is a real AndroidX CameraX binding component, not example pseudocode. It:

1. Binds the existing `Preview` and new `ImageAnalysis` together through `ProcessCameraProvider.bindToLifecycle()` without blocking on catalog downloads.
2. Builds the new `HandheldCameraXAnalyzer` with the *bound* `CameraControl` and `CameraInfo`, enabling autofocus/auto-exposure and Off/Auto/On lighting modes.
3. Connects the application's existing `RecognitionEngine` and `ResultSink` callbacks (the real OCR, name/edition lookup, review UI, collection save, and success sound must be supplied from the application source).
4. Forwards `setTorchMode()` and `completeManualReview()` to the analyzer on the camera executor.
5. Uses a **shared `ScannerStartupCoordinator`** begin/end scan gate so catalog refreshes can wait until scanning is over.
6. Unbinds owned use cases on close and releases the scan gate, including bind failures and executor shutdown cases. It will not recreate the user's camera preview or delete existing app data.
7. Emits a `camera_bound` telemetry event, distinguishing camera initialization from printing-engine initialization.

The earlier helper code remains in `app/src/main/java/com/manavault/app/scanner/` and includes rapid frame analysis, focused metering, bounded exposure/torch logic, dark-card edge fallback, independent title/footer OCR crops, frame retry/scene transitions, complete-catalog physical singleton selection, and exact printing verification.

## Known integration locations from the uploaded 5.13.8 APK

The *compiled method names*, not editable code, show the following likely source-level locations. Verify actual Java source before editing:

| Production method | Required integration |
|---|---|
| `NativeScannerActivity.bindCamera()` / `startCamera()` | Invoke `NativeScanCameraSession.start(...)` **once**, passing the existing preview and real OCR/catalog/save adapters. Do not bind the old `ImageAnalysis` analyzer concurrently. |
| `NativeScannerActivity.analyzeFrame()` | Replace or route through the new analyzer's `Capture` instead of performing a second competing OCR pass. OCR must consume `capture.textRegions` and preserve `capture.rectifiedRgb` for artwork and set-symbol recognition. |
| `NativeScannerActivity.toggleTorch()` / `adjustLowLight()` | Use `session.setTorchMode(OFF/AUTO/ON)`; do not use torch cycling as a focus workaround. |
| `NativeScannerActivity.receiveLiveResolution()` / `showImmediateReview()` | Map new `PrintingEvidenceResolver` auto-save vs review vs retry decisions to existing durable inventory/deck updates, UI, and sounds. Explicitly close the review with `session.completeManualReview(...)`. |
| `NativeScannerActivity.onDestroy()` | Call `session.close()` exactly once, while preserving existing lifecycle cleanup. |
| Existing `NativePrintingIndex` and artwork loader | Provide complete eligible **paper** printing counts and real observed set/collector/symbol/artwork evidence. Never infer singleton from filtered visual candidates. Avoid full Scryfall download during active scanning; use the shared startup gate. |

The source ZIP is needed to implement these bindings safely. The method table above is grounded in the packaged 5.13.8 class definitions but **is not a claim that exact source is available**.

## Practical source-level use (illustrative host adapters)

The following is a **call shape**, not a complete compile-ready activity modification; `existing*` variables must refer to the app's actual existing objects and callbacks.

```java
// From the real NativeScannerActivity, inside its camera-start/binding method.
// Reuse existingProcessCameraProvider, existingPreview, camera executor,
// and application-process singleton startup coordinator.
NativeScanCameraSession scanSession = NativeScanCameraSession.start(
    this, existingProcessCameraProvider, existingCameraSelector,
    existingPreview, existingGeometryDetector,
    existingAsyncOcrPrintingMatcher,
    existingDurableSaveReviewAndMetricsSink,
    existingCameraExecutor, existingSharedStartupCoordinator);

// From existing torch mode UI:
scanSession.setTorchMode(AdaptiveLightingPolicy.Mode.AUTO);
// From the existing manual edition picker after confirming/canceling:
scanSession.completeManualReview(wasSaved);
// At scanner shutdown:
scanSession.close();
```

The `existingAsyncOcrPrintingMatcher` must asynchronously build `PrintingEvidenceResolver.Evidence` and the matching candidate list from the **authoritative complete offline catalog**. The `existingDurableSaveReviewAndMetricsSink` must call the current app's proven persistence functions. Do **not** write duplicate collection entries using a new storage scheme.

## Automated checks performed

- 40 original scanner core assertions passed
- 11 startup coordination assertions passed
- 17 scene transition and duplicate-safety assertions passed
- 49 recovery/printing assertions passed
- 28 lighting/focus/dark-edge/OCR assertions passed
- **12 new NativeScanCameraSession lifecycle/integration assertions passed**
- **6 new DEX instruction-level parser regression checks passed**
- Android-adapter compilation against local CameraX stubs passed (not a real Android compile)
- Old / newer uploaded 5.13.8 APK inspection: **fails release guard**, since `NativeScannerActivity` makes no calls to the improved analyzer or the new session bridge
- Additive installer dry-run and mock Android source-tree test passed; existing files preserved

Total: **163 individually counted standalone assertions**, plus camera-adapter tests.

## Applying in a real Android source checkout

Start from a clean branch and an inventory backup. Copy this archive into the existing project, preserving your app and repository files:

```bash
python3 tools/install_additive.py /path/to/your/current/manavault --dry-run
python3 tools/install_additive.py /path/to/your/current/manavault
```

`install_additive.py` refuses to overwrite unknown customized files and creates backups for known earlier scanner helpers. **It does not automatically rewrite the production activity.** After wiring the entry points above in the real source, run:

```bash
bash tools/validate_scanner_release.sh
```

That command performs core tests, adapter tests, source-level call-site checks, `:app:assembleDebug`, then a **DEX instruction-level invocation check** against the built APK. This is designed to fail on the user's 5.13.8 APK instead of distributing another inactive helper patch. Its static checks are necessary but not sufficient; a real-device test must follow.

## On-device acceptance criteria (not yet met)

- Startup: preview without waiting for Scryfall/index load; card-centered AF and AE within the first usable frames.
- Repeated dark, foil and black-bordered cards under daylight and artificial lighting; torch Off/Auto/On without flicker or focus-hunting loops.
- OCR title, set symbol, collector number and artwork evidence all attempted where applicable.
- Fully verified single eligible physical printing saved automatically; ambiguous reprints still require review; no wrong automatic printing saves.
- Consecutive handheld and fixed-stand test sequences with p50/p95 scan latency and no duplicate physical saves.
- Full Android APK build, signing, same application ID, collection backup and upgrade/install preservation confirmed before distribution.
