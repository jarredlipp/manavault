# ManaVault v5.12.0 — Offline reference-matching update

## What is implemented

- Retains CameraX fixed-stand stack scanning, one-card-at-a-time save receipts, immediate uncertain-printing review, sound after successful saving, and collection/search filters (including creature types).
- Generates a **native SQLite FTS4 reference-text index** alongside the offline Scryfall printing catalog. It indexes known names, types, rules text, flavor text, and artists for offline candidate lookup. It does **not** transcribe every individual printing image or determine printings by text alone.
- On uncertain title OCR, makes **one bounded rules-text OCR pass** and compares candidates against that index. A sufficiently corroborated name can proceed to normal collector/set/symbol verification. Contradictory identities remain subject to immediate review.
- Adds a **128-value contrast-normalized perceptual artwork descriptor** alongside the older RGB thumbnail in newly built Scryfall visual references. It is not a trained neural model. Artwork remains advisory; it cannot distinguish printing variants that share art without additional evidence.
- Stores each successful scan ID in the **same collection-state write** as the card being saved, reducing replay duplicates after interruption.
- Uses a new installed-catalog filename to guarantee the richer reference schema is extracted on upgrade. Build verification rejects an old catalog missing the full-text index.

## Build and test

Upload all project files, **including the hidden `.github/workflows/` directory**, into the repository and run **Build ManaVault Android APK**. GitHub downloads Scryfall bulk data and generates the offline database and visual seed. The source ZIP intentionally omits these large generated assets. The new FTS4 catalog may be larger than v5.11.9.

Local fixture/runtime tests:

```sh
python3 tools/check_offline_catalog.py
node tools/check_reference_match_5120.js
node tools/check_runtime.js
node tools/check_catalog_recovery.js
node tools/check_auto_save.js
node tools/check_fast_scan.js
bash tools/check_stack_gate.sh
bash tools/check_reference_cache.sh
```

**Validation limits:** Source and fixture tests cannot establish Android APK compilation, physical camera recognition rate, phone latency, or trained-model performance. This release does not bundle a third-party app's proprietary classifier or replace the current pipeline with a trained neural embedding network. Do not assume every reprint can be uniquely verified without readable physical evidence. Export your collection backup before installing.
