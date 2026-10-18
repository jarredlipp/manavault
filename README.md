# ManaVault v5.12.6

Android source project for continuous, one-card-at-a-time stack scanning. Native CameraX performs detection and footer OCR; a local, evidence-aware printing resolver matches scanned marks against Scryfall printing records before a collection save. The scanner confirms saved cards with its success sound, then accepts the next card on the stack.

## Changes in 5.12.6

- **Universal structured footer evidence:** parses collector number, slash-separated set total, candidate printed set code, rarity and language independently. A footer such as `086/254 C` plus `MH1 EN` means **set MH1, collector 086, set total 254, rarity common, English**; `254` is not an alternate collector number.
- **Set-first exact-printing resolution:** narrows the already recognized card's editions to the physically observed set, then matches the collector numerator. Supports code and number across different native OCR lines/regions.
- **No wasted artwork checks on proven printings:** a unique, noncontradictory local set/collector match is returned before expansion-symbol or artwork matching. Strong evidence of conflicting sets, incompatible rarity or multiple identical printing identifiers still requires the immediate visual review.
- **Older cards:** the existing offline expansion-symbol and artwork fallback remains in place for cards without usable collector numbers; set-only lookup cannot guess between multiple same-set printings.
- **Clearer diagnostics:** logs structured set, collector, denominator, rarity, language, and the reason for verification or review.
- Existing low-light Auto/Off, high-resolution CameraX, stack-change detection, immediate on-camera review, translucent success confirmation, search filters, and transactional scan receipts are preserved.

## Tests and build

Run `node tools/check_structured_footer_5126.js` and `node tools/check_auto_save.js` to verify both the parser and the real live-resolution path. Additional scanner, catalog, stack-gate, offline-reference and UI regressions are in `tools/` and the GitHub Actions workflow.

Build through `.github/workflows/build-apk.yml` (include hidden `.github`). Generated Scryfall SQLite and compact reference assets are created at build time. Android compilation, actual phone-image OCR quality, and runtime scanning speed are **not verified in this environment**. Back up your collection before installing.
