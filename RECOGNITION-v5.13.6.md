# ManaVault v5.13.6 — Exact printing recognition and scanner pacing

## Basis and scope
The October 26 complete diagnostics reported long gaps between captures and
repeated on-camera edition selection despite the full offline artwork index.
This patch is based on the v5.13.5 complete source. No storage schema, core
feature, test, or existing scanner mode is removed.

## Implemented changes
1. **Candidate-specific footer OCR.** `PrintingFingerprintVerifier.readMarks`
   now checks the actual catalog collector numbers of the named card. When
   they are absent from the quick reads, a single high-contrast, near-full-width
   collector-footer crop is attempted. The new `footer-wide` zone is accepted
   by the structured evidence parser. It cannot, by itself, guess a card title;
   printed marks still have to satisfy the existing unique-printing and
   contradiction checks. This is an alternate pass on the *captured frame*,
   NOT a second live-frame camera capture.
2. **Expansion-symbol recognition.** In addition to the existing grayscale
   silhouette, rarity-colored glyphs receive a color-difference mask, which
   rejects flat backgrounds and fine textures. Sampling includes lower type
   bars on Saga/alternate-frame cards. Candidate sets still compete at the
   same sampled photo position; close scores cannot auto-verify a set.
3. **Artwork/alternate printings.** The full printing-level artwork index is
   retained. When a near tie or incomplete reference set prevents automatic
   verification, its per-printing artwork, text, frame, and footer ordering
   is preserved as an advisory ranking in the on-camera edition picker.
   Identical artwork and shared-illustration versions are not treated as
   uniquely determined printings. The user must still approve ambiguous IDs.
4. **Stack cadence.** The scene gate retains the ordinary multi-frame and
   copy-safety checks. A second HIGH-evidence path accepts a clearly different
   new card only after at least five stable observations spanning 480 ms, with
   strong differences in artwork, full card, and title. This helps with
   frame-to-frame glare/jitter that previously restarted the ordinary gate.
   Persistent scene-gate stalls now log Hamming evidence/counters (no photos)
   so the next 30-second stall can be diagnosed.

## Limitations and safe expectations
- The full artwork database still needs to be installed separately, as before.
- Exact editions whose artwork, text/frame treatment, and readable printing
  marks are indistinguishable still require confirmation; no safe threshold
  can invent missing information.
- Added chromatic recognition incurs limited additional CPU work only when
  ambiguous symbol matching runs; measured phone performance is unknown.
- Timing changes are based on evidence thresholds but may not fix all gaps
  caused by autofocus, card placement, camera frame delivery, or review time.
- No second live camera photo is taken during footer rescue; attempting to
  grab a later frame while the user is stacking the next card would risk
  attaching the wrong photo to a save. A synchronized multi-frame transaction
  is a separate, larger camera-lifecycle feature.
- Java/JS regression tests are not an Android APK compilation or field trial.

## Build and installation
Apply `ManaVault-5.13.6-scanner-accuracy-patch.zip` over the project root,
so `app/src/main/...` and `.github/workflows/...` align with existing paths.
No removal of other files. Commit the modified files and run
`Build ManaVault Android APK` in GitHub Actions. Export a collection backup
before APK updates, and scan downloads with Defender as usual.

## Verification
`tools/check_5136_scan_refinements.js`, `tools/check_5136_symbol_color.py`,
`tools/check_stack_gate.sh`, `tools/check_scanner_compile_contract.py`,
and the repository's existing recognition regression suite.
