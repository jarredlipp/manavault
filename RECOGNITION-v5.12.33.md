# ManaVault 5.12.33 — Footer-first identity and orientation-aware split recognition

## What this addresses

Actual scanner sessions showed `Who // What // When // Where // Why` offered for **Webstrike Elite** (DFT #0345) and **Captain Storm, Cosmium Raider** (LCI #0303), repeated manual retries and edition selections on rotated split cards (`Fire // Ice`, `Wax // Wane`, `Night // Day`), and uncertainty about whether the complete artwork index had ever been installed. Some screenshots showed only 1,200–1,249 starter artwork references. The cards' real collector codes are physical evidence; mere similarity to a catalog name is not.

## Recognition changes

1. **Footer-first on tentative names.** `mv133FooterFirstRequired` causes `ocr-candidate`, `ocr-two-line-suggested`, and `art-index-suggestion` to invoke `readPrintingMarks` with `footerFirst` before pursuing name-specific printing candidates. Printings identified by independently parsed set code **and** collector number are checked against the physically observed title. Title conflict, rules text, illustrator credits, and implausible multi-face guesses are routed to immediate review. This path does not download artwork or save items after the scan session.
2. **One extra bounded footer OCR rescue.** Native `PrintingFingerprintVerifier.readMarks` can perform a targeted 4× enlarged, high-contrast lower-left read when a speculative title needs physical corroboration. It does not invent set codes or collector numbers.
3. **True split layout.** `SplitCardLayoutPolicy` identifies portrait upper/lower sideways title panels versus already rotated landscape left/right panels. Native verifier rotates sideways sub-panels before reading the top of each half. The JavaScript resolver checks **both** distinct face titles and refuses verification if they are not corroborated. Transform/modal double-faced cards retain their existing one-face behavior.
4. **Clear artwork index metrics.** The live camera shows “Full printing artwork: NOT INSTALLED — using starter references” when absent, otherwise installed face count, version, and last indexed-lookup result. This fixes the ambiguity between roughly 1,200 bundled/learned reference images and the separate full-printing SQLite database. The complete artwork index still requires its separate GitHub Actions build and installation.

## Accuracy rules

* A set/collector pair can propose a printing, but cannot replace contradictory or unreadable physical **title** evidence with an automatic save.
* Shared illustration, identical visual layout, and unresolvable set symbols remain review cases, not random edition assignments.
* Manual duplicate-copy confirmation, during-scanning persistence, catalog data, deck inventory and the CameraX-only UI are unchanged.
* This update is not a throughput guarantee: targeted footer rescue adds OCR only for uncertain titles and can increase those captures' verification time in exchange for fewer false candidates and repeated retries.

## Validation

Regression commands: `node tools/check_51233_footer_first.js`; `javac -d /tmp/mv33-split-test app/src/main/java/com/manavault/app/SplitCardLayoutPolicy.java tools/SplitCardLayoutPolicyTest.java`; `java -cp /tmp/mv33-split-test com.manavault.app.SplitCardLayoutPolicyTest`. Both are called in the APK GitHub workflow; the source contract includes the new helper so missing source fails *before* Scryfall downloads. The broader regression suite and static source-hygiene checks also run in CI.

**Unverified until GitHub and phone test:** complete Android compilation, OCR detection of DFT #0345 and LCI #0303 in every real lighting condition, installed complete artwork-index coverage, and real cards-per-minute. Nothing in this patch removes legacy features, files, or tests.

## Installation

Apply the patch ZIP to its existing repo-relative paths; retain all other files. The added `SplitCardLayoutPolicy.java` is **required** for compilation. Avoid nesting `manavault-main/` inside the repository. Scan the ZIP with Defender; do not override detections. Commit and run GitHub Actions APK build; back up collection before installation.
