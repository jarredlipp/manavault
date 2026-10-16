# ManaVault v4.0 — database and scanner foundation (experimental)

Implemented: automatic Scryfall **Default Cards** bulk sync (100,000+ printing records when the Scryfall dataset has that many), unique printing IDs in IndexedDB, alternate-store atomic catalog replacement, 24-hour update check, offline printing selection, stable-frame gating, more conservative fuzzy candidates, v3.4 torch and diagnostics retained.

**Not implemented:** CollectorVision/Milo model integration, native Android neural inference, perspective correction, batch scanning pipeline, ANN embedding index, Raspberry Pi control, incremental visual-model updates. Those require separate engineering, model files, licensing review and device benchmarking. This is not a complete implementation of the previously proposed v4.0 architecture.

The separate GitHub visual-index build remains from v3.4 and may require many hours/network requests. There is no prepackaged 100k-record database in this source ZIP; the app downloads the current bulk export at first launch. This can be a large download, and IndexedDB quota limits may apply. The first full import needs a Wi-Fi connection and ample free storage. The update process stages the replacement before switching the active database, though app termination may leave staging data behind.

Build: upload extracted contents to GitHub repository root, run Actions. The APK build and on-device functionality have not been verified in this environment.
