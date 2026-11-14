# ManaVault — Verified singleton printing fast path

**Source patch only.** This archive is not a rebuilt Android APK, and it does not by itself change the installed ManaVault 5.13.8 scanner. The production `NativeScannerActivity.java` was not available to edit; earlier standalone scanner helpers are included to keep this additive patch self-contained.

## Problem demonstrated by 2026-11-12 diagnostics

The uploaded archive contains 11 scan sessions (10 CameraX sessions and one recovery preload). A parse of the logs found **237 SCAN RECEIPT records** (218 verified, 16 retry, 3 skipped). **44 cards** were saved with `Edition selected during scanning`. One card, `Sanctuary Cat`, used `one catalog printing + physical identifier` without review. The logs report **eight** already-verified `structured set + collector numerator` receipts with nonzero `manual review` time, indicating manual-confirmation latency can occur even when identity was already established. These require review of the actual activity/UI path. **Manual time isn't sufficient proof that an edition picker appeared in each case.**

At least three ambiguous scans had text similar to `Only one plausible printing; no competing artwork to rank`, yet the same `Unresolved edition` records state that many catalog printings exist (`Highway Robber`: 5; `Sangromancer`: 8; `Rishkar's Expertise`: 11). This is **not** proof of catalog uniqueness. No record in the uploaded log explicitly reports an `Unresolved edition` with exactly one authoritative printing; the detailed catalog candidate lists and actual picker UI were not exported. Thus the specific singleton-prompt cases cannot be conclusively reconstructed solely from these text logs.

## Code change

`PrintingEvidenceResolver.Evidence` has two *new optional* fields:

- `authoritativePrintingCount`: count from an **independent, exhaustive, exact-name query for eligible physical/paper printings** (the database's identity policy must include legal alternate arts/promos, front/back faces, and collector variants as relevant); defaults to `-1` (unknown).
- `completeCatalog`: true only if the full catalog has finished loading and completeness checks have passed; defaults false.

The previous `allNamePrintingsConsidered` flag must also be true, and the `candidates` array must contain exactly one identity. Only then does a confirmed catalog-singleton bypass review if `titleConfidence >= 0.96`, candidate `titleScore >= 0.96`, the normalized observed name equals the catalog name, and no high-confidence observed set/collector mark contradicts the candidate.

**Do not use** `1` for `authoritativePrintingCount` just because the artwork top-K, OCR query, local cache, search suggestions, narrowed set filter, or printing picker lists only one item. Do not assert `completeCatalog` if the bundled 1,200 artwork reference fallback is all that is available; the printing catalog and artwork index are different datasets. The printing catalog may be complete even if the complete artwork index is not, but the count must come from the full catalog.

If title evidence is weak, re-scan automatically; if there is strong contradictory printed evidence, reject the result and retry. If multiple genuine eligible printings exist and no exact-printing evidence is available, manual selection is still required. Scanner duplicate suppression remains intact.

## Production integration (not completed)

1. In the production printing matcher, query the full offline database by **exact normalized card name, no set/image filters**, and enumerate all eligible paper printing IDs. Confirm catalog import is complete and not using a partial asset. Deduplicate physical printing IDs, not merely card-name or art groups.
2. Pass the full candidate list to `PrintingEvidenceResolver.resolve`. For an actual single printing, instantiate `Evidence` using the new 12-argument constructor, e.g.:

   ```java
   new PrintingEvidenceResolver.Evidence(
       recognizedTitle, printedSet, printedNumber,
       titleConfidence, setConfidence, collectorConfidence,
       observedSymbol, observedArt, completeVisualCoverage,
       /* allNamePrintingsConsidered= */ true,
       /* authoritativePrintingCount= */ 1,
       /* completeCatalog= */ true);
   ```

   Do **not** hardcode the last three values for every scan; set each from real, independently checked catalog-query results. Do not use fallback title or guessing as exact-title evidence. If `authoritativePrintingCount > 1`, preserve the normal multi-printing verification flow.
3. Ensure an `AUTO_SAVE` result directly calls the existing persistent save and success-sound path, **before any generic manual confirmation/picker branch**. Maintain generation/scene guards and physical-copy duplicate protection. Log `unique eligible printing in verified complete catalog + exact title`. A UI review should only appear on `REVIEW`, not a previously `AUTO_SAVE` result.
4. Review why already-verified set/number matches still accumulated user-facing `manual review` time (e.g., Cloak and Dagger, Entwined; Bold Biochemist; Wonder Man, Hollywood Hero), and make sure the native UI doesn't insist on a redundant confirmation.
5. Test both a truly unique catalog printing and a multi-printing card where only one *visual* candidate is available. The second must **not** save a guessed printing. Check cards with alternate art, tokens, promos, DFCs, and foreign-language variants.
6. Run `bash tests/run-core-tests.sh` and `bash tests/run-android-adapter-compile.sh`, then the original app's Gradle verification and `:app:assembleDebug` tasks. Run on-device tests with the diagnostics enabled. The local Java tests use stubbed CameraX APIs, **not** the full Android SDK.

## Tests added

- True complete catalog singleton with exact title auto-saves, with an explicit diagnostic reason.
- Truncated/not fully loaded catalog, unenumerated candidate list, eight existing printings with one plausible artwork, weak title, conflicting title, conflicting set/number, and inconsistent count versus candidate-list size **never** get a singleton auto-save.
- Camera adapter emits a real save callback and **never** opens the printing review picker for an authoritative singleton with unreadable footer.

## Validation performed

- Scanner core: **40 assertions passed**.
- Startup lifecycle: **11 assertions passed**.
- Scene transition: **17 assertions passed**.
- Camera adapter compiles against local stubs and its regression suites pass, including **47 handheld/retry/singleton assertions**.

**No production APK build, integration, or phone benchmark has been performed.** The installed app remains unchanged unless these changes are correctly merged into the real Android source and a new APK is installed.
