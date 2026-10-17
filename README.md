# ManaVault v5.10.6 — Android source

Magic: The Gathering collection and deck manager with native CameraX card scanning. The Android project uses Java, Gradle, OpenCV, ML Kit, and a WebView frontend.

## Build

Keep the folder structure, including `.github/workflows/build-apk.yml` (the GitHub Actions workflow). Upload **the contents** of this folder to the GitHub repository root. In GitHub, run **Actions → Build ManaVault Android APK → Run workflow** and download the resulting APK artifact. An APK is not included here.

**Before installing:** Export a collection backup from the existing app. Debug APKs signed with different keys may require uninstalling an earlier build, which can erase local collection data.

## Scanning

Open the native CameraX scanner, scan/remove cards, then tap **Finish / save** once. Exact printings supported by sufficient evidence are auto-added. If the name is confidently identified but the edition remains ambiguous, the card is saved in the collection as **Printing unverified**, with no guessed Scryfall ID or edition price. Use **Resolve edition** from Collection when you want to assign the exact printing. Unreadable names and token/type conflicts still require review. Printed collector numbers (including fraction formats such as 015/280), set codes, and expansion symbols are used before artwork comparison when sufficient independent evidence is available. Scryfall variant metadata (layout, frame, border, promo, finishes, and frame effects) is cached for follow-up validation; these metadata fields alone do not prove what the camera photographed. Expansion-symbol comparisons use the collector-number-filtered candidate set where possible. The GitHub Actions build generates a small starter Scryfall fingerprint index and fails instead of silently shipping an empty index. When an exact card name is known, the native edition comparison fetches candidate Scryfall images and stores compact art and expansion-symbol signatures in a bounded, persistent on-device cache. This cache grows as different editions are scanned and survives app restarts; it does not contain all Scryfall images or guarantee automatic identification when editions are visually identical. Native scanning also learns limited, on-device **name-only** fingerprints from exact catalog-name OCR; these are never treated as edition proof. References for a verified printing may be learned separately, and individual printing candidates can be compared on demand. The scanner displays bundled/learned name artwork references separately from persistent edition-fingerprint counts. The initial count may be zero until the first online comparison. Some variants remain uncertain because reference artwork or readable printed identifiers are unavailable. Card condition and foil treatment are not reliably inferred from artwork.

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
