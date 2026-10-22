# ManaVault GitHub Actions compilation repair (October 22, 2026)

This is a **build repair for v5.12.21**, not a new scanner feature version.

The GitHub Actions job failed at `:app:compileDebugJavaWithJavac`:
`NativeScannerActivity.java:972: local variables referenced from a lambda expression must be final or effectively final`.

## Fix 1: Java lambda capture
`app/src/main/java/com/manavault/app/NativeScannerActivity.java` now captures the validated name into `final String observedName=canonical;` before passing it to the asynchronous visual-learning lambda. The same `canonical` value is still passed to `enqueue`, so scan semantics are unchanged.

## Fix 2: Scryfall catalog verification
`tools/ensure_offline_assets.sh` contained an invalid embedded Python `try:assert` block with a second unindented `assert` before `finally`. The new block uses valid indentation, so the offline catalog is checked without throwing a `SyntaxError` and redundantly downloading the entire catalog.

## How to apply
If your GitHub repository is already updated to v5.12.21, **use the smaller `ManaVault-5.12.21-two-file-build-patch.zip`**: replace exactly the two file paths above. Keep their directories intact. Commit and re-run **Actions > Build ManaVault Android APK**. Do not upload the ZIP as a code file.

If your repository is still missing parts of the v5.12.21 security-cleanup project, use the complete `ManaVault-5.12.21-build-fixed-source.zip` on a branch and remove legacy files from the current tree as instructed in `GITHUB-REPLACEMENT.md`.

## Verification scope
- Shell script syntax: `bash -n` PASSED.
- All three embedded Python snippets: Python `ast.parse` PASSED.
- Native title/layout, JS recognition, catalog and source-hygiene checks PASSED.
- Full Android Gradle compile, device behavior, antivirus scanning: **NOT RUN** in this environment. GitHub Actions must confirm the build.

These changes do not modify the collection data format, offline database schema, card-recognition policy, or GitHub signing configuration.
