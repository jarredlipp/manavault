# ManaVault v5.10.3 — Android source

Magic: The Gathering collection and deck manager with native CameraX card scanning. The Android project uses Java, Gradle, OpenCV, ML Kit, and a WebView frontend.

## Build

Keep the folder structure, including `.github/workflows/build-apk.yml` (the GitHub Actions workflow). Upload **the contents** of this folder to the GitHub repository root. In GitHub, run **Actions → Build ManaVault Android APK → Run workflow** and download the resulting APK artifact. An APK is not included here.

**Before installing:** Export a collection backup from the existing app. Debug APKs signed with different keys may require uninstalling an earlier build, which can erase local collection data.

## Scanning

Open the native CameraX scanner, scan/remove cards, then tap **Finish / save** once. Exact printings supported by sufficient evidence are auto-added. If the name is confidently identified but the edition remains ambiguous, the card is saved in the collection as **Printing unverified**, with no guessed Scryfall ID or edition price. Use **Resolve edition** from Collection when you want to assign the exact printing. Unreadable names and token/type conflicts still require review. Printed collector numbers, set codes, artwork, and expansion-symbol evidence are used when available. The GitHub Actions build attempts to bundle a small Scryfall artwork fingerprint seed; the app can also learn references from verified printings and compare candidates on demand. Some variants remain uncertain because reference artwork or readable printed identifiers are unavailable. Card condition and foil treatment are not reliably inferred from artwork.

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
```

The source ZIP is not a compiled APK and physical-camera scanning has not been verified by these JavaScript tests.
