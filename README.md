# ManaVault v5.12.1 — Collector-first printing identification

## Changes

- Preserve CameraX's fast title recognition and one-card-at-a-time stack workflow.
- Extend fast printing OCR to the actual lower card border, including older and shifted printing layouts. The first crop now covers 87.5%–99% of card height; a second thin strip and a bottom-left rescue crop cover additional variants.
- Avoid repeated high-contrast OCR passes when the plain crop already contains plausible footer marks. No additional network requests are introduced.
- Resolve a printing by a **unique collector number within the independently recognized card's known editions**, only when two separate OCR regions/passes agree and observed set codes do not contradict the printing. Shared numbers, copyright years, and inconsistent evidence remain unverified.
- Retain fast exact set-code + collector-number verification, immediate review where unavoidable, collection saving before stacking the next card, and the successful-save sound.
- Expand diagnostics to include footer-deep OCR evidence.
- Preserve the offline native/IndexedDB catalog fallback and artwork reference cache. This release does **not** claim to repair the separate `IndexedDB fallback: printing-catalog.sqlite.gz` initialization issue seen in the phone logs; the native APK asset needs inspection if it persists.

## Build

Upload the entire source tree including the hidden `.github` folder and run the existing GitHub Actions build workflow. The workflow downloads Scryfall data and generates offline catalog and artwork assets. Back up the existing card collection before installing.

## Validation

```bash
node tools/check_collector_first_5121.js
node tools/check_scanner.js
node tools/check_auto_save.js
node tools/check_fast_scan.js
node tools/check_catalog_recovery.js
bash tools/check_stack_gate.sh
```

Local source/fixture tests do not establish Android APK compilation or on-phone OCR accuracy. For a physical check, use a card with a clearly printed collector number plus a previously ambiguous older-frame card. The expected behavior is a unique printing verified without manual review when the physical evidence is sufficient.
