# ManaVault v2.0 — Continuous scanner upgrade

Changes: default 2× zoom with adjustable slider (hardware zoom where supported, software crop otherwise); continuous autofocus request where supported; center-card framing guide; enlarged, contrast-enhanced OCR title crop and live preview; exact Scryfall matches auto-save after two consistent OCR reads, fuzzy matches require manual confirmation; persistent camera diagnostics.

## Upload to GitHub
Extract the ZIP, then upload **the contents of this folder** to your GitHub repository root (not the ZIP and not the enclosing folder). Preserve `.github/workflows/build-apk.yml` and `app/src/main/assets/index.html`. GitHub Actions builds `:app:assembleDebug` and produces `ManaVault-Android-debug`.

Back up existing collection/decks before uninstalling the old app. Camera zoom and focus support vary by device/WebView. Hardware zoom may not be exposed, in which case ManaVault uses a software crop. OCR remains experimental.
