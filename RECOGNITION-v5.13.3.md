# ManaVault v5.13.3 — accuracy-and-throughput refinement

Builds on v5.13.2 without removing existing features or scanner paths. The
111,301-face full artwork index remains a separately installed asset and does
not need to be downloaded again.

## Changes

1. **Review-only collector OCR near-miss recovery.** When a recognized card's
   photographed footer contains exactly one set code but its collector token
   disagrees, a narrowly defined, one-character OCR confusion (B→8, G→6,
   O→0, S→5, Z→2, I/L→1) may prioritize ONE matching edition in the native
   printing review. An observed full collector fraction, contradictory set
   evidence, or competing possible matches blocks the hint. It does **not**
   auto-save or relax any existing verification threshold. For example a
   letter misread in a Radiant Fountain collector number can lead to a faster,
   better ordered manual choice.

2. **More useful slow-capture timing.** Each native capture includes the prior
   receipt latency and elapsed time since the prior receipt. Captures with
   >4 s gaps are classified as a possible previous review/receipt delay,
   scene-to-capture delay, or time before the scene was detected. This is
   diagnostic; it cannot prove how long a person physically handled cards.
   Existing edge, warp, OCR and verification timers remain in place. No
   camera threshold, scene gate or duplicate policy is loosened.

3. **Physical-versus-saved audit.** In **Scan cards → Physical printing accuracy
   audit**, examine the latest 24 saved entries and mark **Matches** or
   **Wrong printing** after comparing the actual paper card with the saved
   set and collector number. An export through Android's Share interface
   contains up to 300 recent scan outcomes with printing IDs, identity, set,
   collector, verification milliseconds, manual-review time, and human audit
   status. It never includes photographs. An audit mark does **not** change
   collection quantity, the selected printing, or deck allocation. To fix a
   wrong printing, separately edit the collection/undo the most recent save.
   Audit results are stored locally under a dedicated localStorage key and
   remain untouched by closing the scanner. This log is not automatically
   included in the collection backup; export it separately.

4. **New deterministic regression test** `tools/check_5133_accuracy_audit.js`
   exercises review-only OCR corrections, conflict rejection, receipt audit
   idempotency, exported printing data, and slow-gap classifications. GitHub
   Actions runs it before APK compilation.

## Constraints and known limitations

- A high save rate is NOT an independent measure of exact-printing accuracy.
  Users must review physical copies to estimate actual error rates.
- Evidence found in the physical footer does not override contradictory
  physical title or a uniquely competing printing. No new art-only auto-save
  path has been added.
- The scanner still uses its existing native CameraX path and verifies/saves
  cards while scanning. Scene gate and physical-copy confirmation are retained.
- Manual choices and delays may still reduce observed cards per minute.
- A full Android Gradle build and device-level timing are required after
  applying this patch to confirm APK compilation and performance.
