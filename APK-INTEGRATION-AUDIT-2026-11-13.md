# ManaVault APK integration audit — November 13, 2026

## Input (the uploaded compiled app)

`ManaVault-Android-debug.zip` contains exactly one file: `app-debug.apk`. It does **not** contain the Android Gradle project, Java source, signing key, or any source repository metadata.

- APK SHA-256: `9db11a0ed2c4d20982edc02634678503736956b449dc01c97b676e54ca89b3d9`
- Reported app version: `CameraX · ManaVault 5.13.8` (in `classes3.dex` strings)
- Offline SQLite catalog: 120,143 printings, generated 2026-11-12T22:06:24.370+00:00
- `NativeScannerActivity` is defined in `classes3.dex`.
- `HandheldCameraXAnalyzer`, `PrintingEvidenceResolver`, and `ScannerStartupCoordinator` are packaged in `classes4.dex`.
- **None of those three helper classes appears in the method-reference table of `classes3.dex`.** The live native activity cannot directly invoke them; the present classes are not integrated into its recognition path.
- The packaged `PrintingEvidenceResolver.Evidence` type lacks the fields `authoritativePrintingCount` and `completeCatalog` from the November 12 singleton-printing correction. This binary contains an earlier compiled helper, not the complete singleton fix.

The APK therefore contains dormant scanner helper code. This **explains why the successive source-only patches have not changed behavior in the live native activity**. It does not, by itself, prove every underlying camera/OCR defect.

## Physical versus digital catalog entries

The embedded catalog's JSON contains `digital` flags. Counting all rows can overstate physically eligible printings.

| Card | Total catalog rows | `digital=false` rows | `digital=true` rows |
|---|---:|---:|---:|
| Gift Horse | 1 | 1 | 0 |
| Sanctuary Cat | 3 | 1 | 2 |
| Highway Robber | 5 | 5 | 0 |
| Sangromancer | 8 | 8 | 0 |
| Arbor Elf | 7 | 7 | 0 |

The `digital=false` filter is a necessary starting point, not a complete definition of every physical card category. Any authoritative eligibility policy must also handle promo, language, collector variants, double-faced layouts, tokens, and nonstandard printing categories deliberately rather than collapsing them by artwork.

## Integration required in the actual Android source

1. In `NativeScannerActivity.java` replace or delegate the **active** CameraX frame analyzer with the tested `HandheldCameraXAnalyzer`, including `ImageProxy` lifecycle, correct orientation, framing, retry callbacks, and cancellation of stale scenes. Do not merely add/import its source class.
2. In the real native printing lookup (`NativePrintingIndex`, or adapter wrapping it), query all printings by exact normalized card name from the complete offline SQLite catalog, independently of visual top-K ranking and set/collector OCR filters. Exclude digital-only rows from physical printings. Compute the exact, deduplicated eligible-printing count and completeness status from the actual catalog.
3. Wire that authoritative count and `completeCatalog` into the new `PrintingEvidenceResolver.Evidence`. Only true catalog singletons with strong title recognition, matching candidate identity and no contradictory markings should bypass review. A sole **visual** match among several catalog printings must not auto-save.
4. Route `Verdict.AUTO_SAVE` directly into the current persistent scan-save and success-sound handler **before** the generic `ScanResultUi.review`/immediate-review path. For `Verdict.RETRY`, capture another frame; only `Verdict.REVIEW` should eventually open the picker. Maintain duplicate-copy and stale-generation checks.
5. Connect `ScannerStartupCoordinator` to the actual activity lifecycle. Preload the offline catalog and visual indexes at app startup / reuse their single in-memory instance. Avoid launching a bulk download while the camera is active.
6. Preserve all application features, stored collection/deck data, Android package ID, existing tests, and source file layout. Do not uninstall the app to install a new key-signed APK without first exporting data; a differently signed APK cannot update the current installation in place.
7. Run the source patch's Java tests; then run `gradle --no-daemon :app:assembleDebug` in the actual repository; then run the binary wiring audit below on the resulting `app-debug.apk`. Only then do on-device test runs against problem cards and 100+ consecutive handheld scans.

## Automated release guard included

The new `tools/verify_scanner_wiring.py` (Python stdlib only) checks the built APK for:
- the three helper classes compiled into the binary,
- **method references to them in the same DEX that contains NativeScannerActivity** (necessary condition, not proof of actual invocation), and
- the new singleton-evidence fields compiled into the resolver.

```
python3 tools/verify_scanner_wiring.py app/build/outputs/apk/debug/app-debug.apk
```

Current uploaded APK **fails** with exit status 2. Add this command to the existing Gradle build workflow **after APK assembly**. It prevents publishing the same accidentally-unwired helper classes, but does not replace a full source integration test or phone verification. Note: if the app is refactored to invoke helpers through another class, update the guard with a more specific activity→adapter call-graph test rather than disabling it.

Optional embedded-catalog eligibility check:

```
python3 tools/check_catalog_eligibility.py app/build/outputs/apk/debug/app-debug.apk 'Sanctuary Cat' 'Gift Horse'
```

## Test status and limitation

The bundled source patch (from November 12) still passes:
- 40 core scanner assertions
- 11 startup assertions
- 17 scene-transition assertions
- 47 handheld/retry/singleton assertions
- CameraX adapter compilation against local stubs

**Not done:** Patching and rebuilding `NativeScannerActivity`, running a production Gradle build, signing a new APK with the original key, or validating the scanner on a real phone. These operations require the real Android Gradle source project (including `app/src/main/java/com/manavault/app/NativeScannerActivity.java` and `NativePrintingIndex.java`), not just an APK. This document is an audit and an additive source integration kit—not a fixed installed app.
