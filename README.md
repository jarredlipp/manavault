# ManaVault v5.7

Android WebView-based Magic: The Gathering collection scanner.

## Build

Use `.github/workflows/build-apk.yml` or build `:app:assembleDebug` with Gradle. The root and app-level `build.gradle` files serve different required roles.

## Scanner

The scanner detects portrait and sideways candidate card boundaries, rectifies the card image, then OCRs the title region. The green polygon represents the detected corners; yellow marks the title area. A weak detection uses the fixed-stand OCR fallback without stopping the scan. The live overlay retains the most recent valid outline briefly to reduce flicker. OCR exact names are matched against the local catalog, while fuzzy candidates require repeated agreement. Edition and foil status require user confirmation.

Camera startup now guards against stale or removed video elements. Low-light title enhancement and scanner diagnostics are retained. Artwork matching is advisory, not a guaranteed verification step.

## Pricing

Scryfall printing-specific price estimates are used when available; Card Kingdom feed support is experimental. Missing prices are not guaranteed to be available.

## Limitations

No phone-camera or APK installation test has been performed for this source package. Automatic corner detection can fail with glare, sleeves, or dark backgrounds. Millisecond card recognition is not guaranteed with WebView and Tesseract OCR.


## v5.6 scanner
Uses a live four-edge card silhouette overlay, rectifies the detected card before OCR, and indexes both sides of multi-face card names. When an OCR title is missing from the catalog, a guarded Scryfall lookup can add it to the current session. Pricing and collection data remain unchanged. Card-edge detection is experimental and requires live-device validation.

## v5.7 scanner changes

- Sideways (90-degree) card outline detection and title rectification for Battle layouts.
- Prioritize larger outside silhouettes instead of artwork rectangles.
- Keep scanning using the fixed-stand OCR crop when edges are uncertain, rather than returning early.
- Require two agreeing OCR reads when an exact title is found without a verified card outline.
- Throttle repeated status entries in persistent diagnostics.
- Keep one canonical Android asset scanner (`app/src/main/assets/index.html`), one card database asset, one workflow, and one README.

**Limitations:** The fallback is not equivalent to reliable four-corner detection. Artwork verification and precise edition identification remain manual/advisory; sub-second card recognition is not guaranteed.
