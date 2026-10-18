# ManaVault 5.11.8 — Offline Catalog Build Fix

The prior GitHub build failed in `:app:prepareOfflineAssets` with `ValueError: read of closed file` while `gzip` processed the Scryfall bulk download. The offline catalog builder now **downloads the response to a temporary file and closes the HTTP stream before parsing**. It detects gzip based on the saved file contents, parses using `ijson`, and builds an atomic SQLite output. The temporary download is automatically cleaned up.

No camera, collection, or scanner state behavior was changed from v5.11.7. Stack scanning, immediate review, live saving, success sound, and advanced filters are preserved.

Build using `.github/workflows/build-apk.yml` or Gradle; both call `tools/ensure_offline_assets.sh`. The Gradle build validates a populated SQLite catalog, artwork seed, and set-symbol index before compiling. An internet connection is needed to generate these assets on the first build. Generated assets are intentionally excluded from the source ZIP.

Tests: `python tools/check_offline_catalog.py`, `python tools/check_offline_build.py`, JavaScript checks under `tools/check_*.js`, and `bash tools/check_stack_gate.sh` / `bash tools/check_reference_cache.sh`.

Note: tests without access to Scryfall or the Android SDK do not verify remote downloads or APK compilation. Do not install an unverified build over your only copy of your collection without exporting a backup first.
