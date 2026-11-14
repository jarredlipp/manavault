# ManaVault scanner integration deliverable — November 14, 2026

**Work completed:** A real `NativeScanCameraSession` CameraX binder, previous scanner focus/lighting/OCR/printing helpers, safe additive installer, improved source-level wiring guard, and instruction-level APK release guard with tests. The APK audit proves the latest uploaded 5.13.8 build remains disconnected from the new helpers.

**Not completed:** Direct edits to `NativeScannerActivity.java`, the production `NativePrintingIndex`/OCR/collection-save adapters, the original Gradle project build, APK signing, or hardware testing. The only new Android upload was an APK archive, not editable source. Shipping a new APK without that source would require unsafe reconstruction and signing changes.

Read `README-INTEGRATION-2026-11-14.md` for the precise integration points, test results, and release procedure.

Do not install any ZIP from this package as an APK. Don't delete your original repository, other app files, or saved collection. Apply the patch only on a copy/branch of the complete Android Gradle source tree.
