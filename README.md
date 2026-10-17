# ManaVault — Android card scanner

## Build
Use `.github/workflows/build-apk.yml` (GitHub Actions) to build `app-debug.apk`. Debug signing certificates can differ between GitHub runners; a permanent signing key is needed for reliable in-place updates.

## v5.4 scanner changes
- Prefer a card silhouette detector in a bright enclosure; retain v5.2 edge-quad fallback and perspective warp.
- Crop the title from the top of the rectified card, excluding the mana cost where possible.
- Enhance dark title regions before OCR.
- Retain credible candidates when later OCR frames fail; use database-aware nonsense filtering.
- Limit fallback OCR to plausible name regions rather than rules text.
- Avoid restarting a functioning camera merely because OCR is slow or no title matches.
- Preserve v5.3.1 Scryfall pricing, optional Card Kingdom comparison, collection and diagnostics.

## Performance expectations
App shell rendering can take milliseconds, but camera permission, camera startup, OCR worker initialization, and card recognition are separate asynchronous tasks and cannot be guaranteed to finish in milliseconds. This build is untested on a physical device.
