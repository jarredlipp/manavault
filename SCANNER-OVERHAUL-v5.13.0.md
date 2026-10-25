# ManaVault v5.13.0 — conservative recognition-engine overhaul

**Purpose:** Fix false OCR identities and wasted retries while retaining every existing
collection, deck, native CameraX, offline SQLite, and scan-time persistence feature.
This is a real source implementation, but the Android APK and real-card throughput
**must still be verified through GitHub Actions and a physical phone scan**.

## Implemented

1. **Safer title identity**: Exact OCR matches from a sideways rotation of an
   upright card are never silently accepted. Two-face split cards become
   provisional until OCR sees *both* printed face-title panels; sideways battle
   titles are preserved as provisional candidates needing independent review.
   Three-or-more-face guesses from ordinary portrait OCR are rejected.
2. **Catalog-constrained repair**: A unique near-title match can correct a
   missing glyph (`Tigereye Camco` → `Tigereye Cameo`) or a stray numeric
   suffix (`Raggadragga, Goreguts Boss 2`), without treating a fuzzy name as
   a verified printing. Small bounded two-character prefix buckets avoid
   scanning the full name list on every frame.
3. **Independent card-text recovery**: When set/collector OCR can't rescue
   a tentative title, the existing offline native SQLite full-text index can
   examine distinctive type/rules terms and suggest another candidate. It is
   **review-only**, never permission to invent an exact printing.
4. **Actionable on-camera corrections**: The actual footer/text suggestion is
   used to populate the printing picker, not discarded in favor of the original
   wrong OCR identity. Same-art printings remain separate; reviewer selection
   chooses a specific printing ID.
5. **Quality evidence**: Each accepted capture records sampled brightness,
   glare percentage, image-edge detail, and a lighting/focus hint in diagnostics.
   The classifier is advisory and never silently drops a foil or dark card.
6. **Collector OCR performance**: Adaptive scaling replaces a fixed forced 4×
   enlargement in the optional footer-first rescue.
7. **Physical-card throughput measurement**: `tools/scanner_benchmark.py` reads
   exported logs and distinguishes saved entries, retries, skips, capture gaps,
   and manual review time. An optional `--physical N` requires an *actual*
   count supplied by the user; true precision requires verified ground truth.
8. **Build guards and release packaging**: v5.13.0's contract preflight includes
   the new pure-Java policy classes. This cumulative patch includes all prior
   scanner policy dependencies missing from some earlier GitHub checkouts.

## Explicitly not claimed

- No competitor's proprietary library, model, or source code was copied.
- No trained TFLite detector/embedding model is included: producing a
  meaningful model requires labeled imagery and measured training/evaluation.
- The complete printing-level artwork DB is **not** embedded in this source ZIP.
  It requires the separate `Build full printing artwork database` GitHub Action
  and the in-app download. The bundled ~1200 starter references still operate
  when the full database is absent.
- Full Android compilation, antivirus clearance, improved phone accuracy and
  cards-per-minute performance cannot be asserted until measured.

## Installation

**Apply the small PATCH** into your repository's existing paths (not inside a
second `manavault-main` folder). The complete source is intended for a clean
replacement via multiple GitHub upload batches or Git, not a one-step upload
of >100 files. Always back up the collection and scan ZIP files with Defender.

Run `Build ManaVault Android APK` after committing. Then run
`Build full printing artwork database` and install that database from the app's
**Automatic printing identification** panel to enable full-index retrieval.

## Test protocol

Use the same physical card sequence each time, record actual physical count,
export the diagnostic log, and verify exact set + collector + card identity.
Priority fixtures: Webstrike Elite DFT 345, Captain Storm LCI 303,
Black Waltz No. 3 FIN 214, Raggadragga CLB 437, Lone Wolf 7ED 254,
Tigereye Cameo INV 314, Fire // Ice DMR 215, Wax // Wane DMR 211,
plus two identical physical copies and one stationary card with changing light.
Do not count an OCR retry as a new physical card.

`python3 tools/scanner_benchmark.py scan-log.txt --physical 30`

This update intentionally focuses on scanner identity and correctness.
Remaining work: a trained image-embedding model after collecting labeled,
permissioned photographs, calibrating confidence, and benchmarking against
held-out cards; then lower scene-to-capture latency without false duplicate
saves. All other ManaVault app features remain in place.
