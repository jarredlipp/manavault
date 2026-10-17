# ManaVault v5.5

Android WebView-based Magic: The Gathering collection scanner.

## Build

Use `.github/workflows/build-apk.yml` or build `:app:assembleDebug` with Gradle. The root and app-level `build.gradle` files serve different required roles.

## Scanner

The scanner detects candidate card boundaries, rectifies the card image, then OCRs the title region. The green polygon represents the detected corners; yellow marks the title area. A weak detection uses the fixed-stand fallback. OCR exact names are matched against the local catalog, while fuzzy candidates require repeated agreement. Edition and foil status require user confirmation.

Camera startup now guards against stale or removed video elements. Low-light title enhancement and scanner diagnostics are retained. Artwork matching is advisory, not a guaranteed verification step.

## Pricing

Scryfall printing-specific price estimates are used when available; Card Kingdom feed support is experimental. Missing prices are not guaranteed to be available.

## Limitations

No phone-camera or APK installation test has been performed for this source package. Automatic corner detection can fail with glare, sleeves, or dark backgrounds. Millisecond card recognition is not guaranteed with WebView and Tesseract OCR.
