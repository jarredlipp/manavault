# Latest patch: November 13, 2026 — camera / foil / focus recovery

See **CAMERA-RECOVERY-2026-11-13.md** for added Java camera helpers, actual production wiring requirements and test status. **This is not a deployed APK.** The original README continues below unchanged.

---

# November 12 verified singleton printing update

See **ONE-PRINTING-FIX-2026-11-12.md** for new authoritative catalog uniqueness handling, diagnostics findings, production integration steps, and regression tests. The source patch is not the installed Android APK.

# ManaVault handheld recognition recovery — 2026-11-12

## Status and scope

**A tested scanner-core code patch, not a deployed APK.** The user's currently
installed app identifies itself as `ManaVault 5.13.8`. Its full Android source
archive was indexed in project files, but the archive's actual bytes could not be
materialized in this environment. Therefore it would be unsafe to claim that
`NativeScannerActivity.java`, the existing OCR engine, or the database code was
patched. This release does not replace the app, does not remove any features,
and does not touch decks, collection data, or stored card allocations.

**Do not install the November 8 patch in addition to this patch.** This patch
supersedes its scanner helper files, with a safe installer that upgrades only
exact original November 8 helper hashes. Customized files require manual review.

## Confirmed November 12 diagnostic evidence

Source: `ManaVault-complete-scanner-diagnostics (1).zip`, 20 included sessions
(including a legacy session). The supplied `tools/diagnostics_report.py`
reconstructs these totals from the session text:

- 9 CameraX sessions; 8 measurable startup-to-native-ready times averaging
  **14,911 ms** (p95 **26,413 ms**).
- 5 logged `Unrecognized title` footer failures; 2 `Image too dark` notices.
- Across the archive, 27 saved cards, 4 retry/skip receipts, 11 footer-first
  failures, 6 edition ambiguity messages, 7 slow-cycle warnings.
- The 26 logged inter-capture gaps average **4,109 ms**; the 35 verification
  samples average only **320 ms**. This indicates acquisition/UI/review delays
  dominate many cycles, not the native SQLite query itself.
- The database is **present** (119,504 native printings); the complete artwork
  index reported **112,192 faces**. Missing entire card data is not the cause.
- After a scan, the old app began a full Scryfall download/import while already
  holding an offline catalog. That task must not run during active scanning.
- The diagnostic ZIP contains no camera photos, so the exact autofocus vs crop
  vs glare failure rate cannot be measured from these logs alone.

## Implemented changes in this source patch

1. **No-match doesn't block on manual review.** `RETRY` from missing title,
   footer or candidates now automatically reattempts after short pacing. A real
   ambiguous printing gets up to two more evidence frames before the picker.
2. **Cheaper frame acquisition.** Keep-latest-frame CameraX analysis, 95 ms
   sampler pacing, a 144×202 rectified quality preview before a 540×756 OCR
   crop. Only a frame selected for recognition gets full cropping and color.
3. **Correct camera rotation.** Rotate the camera Y buffer according to
   `ImageInfo.getRotationDegrees()` before geometry and OCR. Focus points and
   YUV chroma samples are mapped back to raw image coordinates correctly.
4. **Handheld boundary fallback.** Gradient-based portrait-card quadrilateral
   detection when the app's primary geometry detector fails. No fixed stand
   coordinates are required; false-positives must still pass the exact-printing
   resolver to be saved. It is a lightweight fallback, not an OpenCV replacement.
5. **Image quality recovery.** Focus and exposure retry with a bounded grace
   interval; best-effort OCR on minimally legible cards instead of waiting
   indefinitely for perfection. Very dark/blank images remain blocked.
6. **Art and symbol support.** Accepted frames contain both grayscale
   (`Capture.rectifiedGray`) and, where chroma is available, packed RGB
   (`Capture.rectifiedRgb`). Wire the RGB field to the **existing** color
   artwork matcher. Keep the current type-bar set-symbol, collector number,
   card-name and paper-printing database paths intact.
7. **Safe visual-only disambiguation.** A uniquely recognized symbol and artwork
   may auto-resolve a printing without collector OCR ONLY when complete artwork
   and symbol coverage and the entire same-name candidate list are explicitly
   verified. Old constructor defaults are conservative; missing coverage still
   requires manual review.
8. **Stale-callback and duplicate prevention.** Generation tokens, one-shot
   callbacks, physical card removal detection and stricter new-card thresholds
   after a save. Changing phone angle alone should not rescan a saved card.
9. **Warm-start/refresh gate.** `ScannerStartupCoordinator` adds idempotent
   process-scoped offline catalog preloading and queues/coalesces background
   refreshes until scanning is finished. **It must be connected to the
   application's actual loader**; the standalone class does not magically
   eliminate the measured 15 s startup delay.
10. **Diagnostics.** New script reports startup time, capture gaps, failed
   titles, image-quality warnings, manual review waits and download starts.

## Installation into the ACTUAL current project

Make a safety branch and back up collections before modifying the Android app.
Extract this ZIP elsewhere (do not commit the ZIP as an application file).
Run against the **current** complete source checkout, not an older 5.12.x app:

```bash
python3 tools/install_additive.py /path/to/current/manavault --dry-run
python3 tools/install_additive.py /path/to/current/manavault
```

The script confirms the destination has `NativeScannerActivity.java` and
`settings.gradle[.kts]`, refuses to overwrite customized files, and backs up
old November 8 helpers under `tools/scanner_patch_backups/2026-11-12/`.
It copies helper sources, tests, workflow, and diagnostic script **only**.

**Required native integration before any APK can use this patch:**

1. In existing `NativeScannerActivity.java`, keep the current Preview,
   permission handling, camera/deck lifecycle, zoom controls, save logic,
   sounds, edition sheet and app screens. Replace the old `ImageAnalysis`
   analyzer (do not add a second one) with:

   ```java
   ImageAnalysis analysis=HandheldCameraXAnalyzer.latestFrameAnalysis();
   HandheldCameraXAnalyzer newAnalyzer=new HandheldCameraXAnalyzer(
       existingGeometryDetector, existingAsynchronousOCRAndPrintingMatcher,
       existingDurableSaveAndReviewSink, sameSerialCameraExecutor,
       camera.getCameraControl());
   analysis.setAnalyzer(sameSerialCameraExecutor,newAnalyzer);
   ```

   `existingGeometryDetector` must return actual observed TL/TR/BR/BL corners
   in **upright Y-plane coordinates**. The fallback is automatic on null/weak
   geometry. The recognition callback must use the installed offline printing
   catalog, the actual set/collector OCR, observed type-bar symbol and full
   artwork matcher. Never fabricate symbol evidence based on catalog identity.

2. The supplied `Capture` is already upright: do not apply its camera rotation
   again. Use `Capture.rectifiedGray` for title/footer OCR and
   `Capture.rectifiedRgb` for artwork and colored printing marks. Build candidate
   arrays with printing IDs, real observed symbol/art confidence, and verified
   set/collector evidence. Use the extended `Evidence` constructor with
   `fullVisualCoverage` and `allNamePrintingsConsidered` set to true ONLY when
   verified; otherwise image-only identification remains review-only. Keep
   the same edition-selection and verified collection/deck persistence methods.

3. Wire `ResultSink.notice` to the scanner status label without pausing capture.
   Wire `ResultSink.telemetry` into diagnostic timestamps. On edition-picker
   completion, call `newAnalyzer.completeManualReview(saved)` on the **same**
   serial analyzer executor. On camera stop, unbind then `newAnalyzer.close()`.

4. In the app-level lifecycle, initialize ONE shared
   `ScannerStartupCoordinator` before CameraX starts. Call
   `prewarm(actualExistingCatalogAndIndexLoader)` once per process, not once
   per Activity. Call `beginScan()` / `endScan()` on camera session start/end;
   route background bulk refresh requests through `refreshWhenIdle(...)`.
   Do not block the camera preview or UI while waiting for offline indexes.
   Reuse the existing warmed index objects rather than reconstructing them
   for each CameraX session.

5. Run the existing offline-catalog / artwork-index / set-symbol preflight.
   Confirm the index used at runtime is truly the installed full index, not
   merely the 1,200 bundled artwork references used as a fallback.

6. Verify real wiring, run all old regression tests and build:

   ```bash
   python3 tools/verify_integration.py /path/to/current/manavault
   bash tests/run-core-tests.sh
   bash tests/run-android-adapter-compile.sh
   gradle --no-daemon :app:verifyScannerSource :app:assembleDebug
   ```

   If the actual Gradle source has a different verification task, use its
   established equivalent. The local tests compile against Java and CameraX
   stubs—not a real Android SDK—and cannot replace APK/hardware validation.

## Local tests performed for this patch

- `tests/run-core-tests.sh`: **30 core resolver/coordinator assertions + 11
  startup lifecycle assertions** passed.
- `tests/run-android-adapter-compile.sh`: CameraX adapter compiled with
  stubs; base release/save/duplicate tests passed; **43 handheld/low-light/
  rotation/retry/reprint assertions** passed.
- An Android SDK and the unmaterialized current production source were not
  available. **Real Gradle build and handheld phone testing are not yet done**.

## Hardware acceptance gate (not yet achieved)

Run same-device, same-lighting tests handheld and in the phone stand: titles,
set symbols, collector numbers, identical-art reprints, alternate/showcase
art, DFC, repeated identical card copies, low light, glare, and fast card
changes. Require no wrong automatic printing saves. Measure time from card
presentation to saved receipt separately from initialization and manual waits.
Target under 1 second for common good-light cards, p95 under 2 seconds, and
manual printing selections under 5% in good conditions; these are **targets,
not verified performance results**.

This patch is a meaningful source-level correction but does **not** fix the
installed 5.13.8 APK until the true native activity is wired, built and tested.

---

## Additional November 12 evening diagnostics: scene-gate fix

The newer ZIP `ManaVault-complete-scanner-diagnostics.zip` contains seven session logs
(six CameraX sessions) from 17:49–18:03 EST. **They still identify the running
app as 5.13.8, not this helper update.** They show 118 saved of 131 handled
attempts, but that is a save rate, not proof every exact printing was correct.

- 142 logged capture gaps: mean **4,136 ms**, p95 **9,146 ms**.
- 148 logged verification samples: mean **287 ms**. The primary bottleneck
  remains outside the actual printing-verification step.
- 35 slow-cycle warnings: **25 before new scene recognition/gating**, six after
  scene recognition but before OCR capture, four linked to previous reviews.
- 35 manual-review waits, including **12 retry outcomes with unnecessary manual
  waiting**; 22 verified outcomes also had nonzero review waiting.
- 19 footer-first failures, six unresolved-edition messages, three low-light
  warnings, two `symbol not attempted` messages, one artwork-comparison cap.
- One session explicitly logged full artwork coverage of 112,192 faces, but
  that index loaded after initial scanning had already started. The packaged
  1,200-reference fallback is not proof of full index availability.

**New standalone code in this iteration:** `SceneFingerprint.java` adds distinct
low-resolution fingerprints of the card's title and artwork, derived from the
already rectified quality preview (no extra OCR or network operation).
`HandheldScanCoordinator` now uses stable, confirmed title+art changes alongside
the whole-card signature, so changes between visually similar cards need not
wait for complete removal or a very large global-hash difference. At least
three consecutive stable observations are required, and high-motion frames
cannot commit a transition. Identical images remain suppressed until physical
card removal to prevent a duplicate auto-save. The previous API remains intact.

`ScannerSceneGateTest.java` adds 17 assertions for stable card transitions,
handheld angle changes, same-image duplicate prevention, and independent
regional fingerprints. Both supplied Java test scripts pass. These are only
**simulations**: do not assume they prove the 4-second average capture gap is
fixed on a phone. The full source, which contains the actual legacy scene-gate
logic, is still needed to wire and validate this path in `NativeScannerActivity`.
The installer can now safely upgrade exact unmodified November 8 or November 12
helper versions; customized files are never overwritten automatically.

**Build gate:** `python3 tools/verify_integration.py /path/to/current/manavault`
will still fail until the real production CameraX adapter is wired. Do not
ship an APK solely because stub compilation passes.
