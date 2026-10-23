# ManaVault v5.12.26 — targeted corrections from the October 23 scanner trial

## Supported findings from the submitted log

- At the 26-handled checkpoint, 23 were saved, and capture cadence was 14.0 handled captures/minute. This is not 14 saved cards/minute.
- Recognition/printing verification averaged 144 ms. Capture gaps were often 2–4 seconds, sometimes 8–13 seconds. Scene-to-capture was typically under 250 ms, but Exava took ~7.5 seconds.
- The bundled artwork count was 1,240–1,259, but legacy printings still required selection, and Webstrike Elite was not recognized confidently.
- The displayed camera crop for Webstrike Elite shows printed DFT collector 0345 and a stylized title; attempted matching confused it with unrelated split-card names. No direct photographic comparison tests were run here.

## Changes

1. If a printed collector numerator is independently observed but symbol shape matching is weak, try fully offline, candidate-complete artwork comparison. Require unique collector+artwork agreement, a clear score/margin, and an exact title; missing/ambiguous artwork **never** auto-saves.
2. Preserve a complete terminal collector fraction like `314/350` or `254/350` when OCR merges it into copyright or illustrator text. Reject year-like numbers and require collector match within the identified card's printing catalog; collisions still require review.
3. For an OCR partial title's long, distinctive first word, allow **one substitution only** when the matched prefix is uniquely associated with one card, e.g. `WEPSTRIKE` suggesting `Webstrike Elite`. This is a *candidate*, not a validated name or printing. Multiple readings and independent physical printing evidence are still required for saving.
4. Log why a weak-symbol/collector candidate's offline artwork fallback was unable to verify the edition. Add targeted regression tests to the GitHub build workflow.

## Limitations and safety

- This is NOT a general fix for every old-frame printing. A high apparent symbol score is not enough to resolve indistinguishable set symbols. A small artwork library cannot identify missing or shared artwork, and a collector numeral alone cannot resolve collisions.
- Software capture gaps include physical card handling and review time. This patch does not claim a measured throughput increase.
- Shining Armor and other genuinely separate identical physical cards must continue to count as distinct only through the existing Copy options/scene-change protocol.
- Full APK compilation, real camera recognition, and Microsoft Defender scanning are **not verified** in this environment. Back up your collection, scan the archive, and test the compiled APK before replacing a production installation.
