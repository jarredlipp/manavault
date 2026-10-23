# ManaVault 5.12.24 — Live scan throughput overhaul

## What changed

1. **No post-save blind cooldown.** `StackChangeGate` changed from a fixed 850ms capture cooldown + 550ms changed-scene stability to a minimum 180ms + 210ms with >=3 consistent, independent artwork/whole-card/title observations. These are minimum criteria; actual latency depends on the frame rate, CPU, focus and physical card stacking.
2. **New scene / final receipt handshake.** `ScanCycleCoordinator` allows the camera to *detect and latch* a new top card while the previous card is being resolved by the WebView. Only the matching `scan_id` final receipt unlocks the next recognition. A prearmed scene is not an unsaved card queue. No guessing editions, no delayed Finish processing.
3. **Independently advancing camera pipeline.** CameraX continues live frame/scene analysis (65ms minimum interval while awaiting a new scene). It stops expensive repeated warping once a new scene has been latched but persistence has not finished. JavaScript verification and collection writes continue in the existing WebView path. The camera never starts OCR for the next card until the previous capture's final receipt.
4. **Physical duplicate support.** Removed the blanket 20-second same-name block. A genuine changed-stack event can admit a second card with the same name. Identical art/copies still need a clear cover/reveal or an intentional Same copy confirmation; a stationary card or momentary glare must not duplicate inventory.
5. **Candidate printing evidence diagnostics.** Ambiguous offline edition decisions now log the best competing set-symbol scores, score separation, missing artwork coverage/reason, and observed collector/set evidence. Previously added collector/set/symbol/art matching and conservative conflict gates remain intact; the update does not claim to solve all older-edition photographs.
6. **Local artwork learning after manual selection.** A confirmed edition teaches the artwork matcher a local feature vector extracted from the existing saved JPEG on a separate executor. Only bounded feature vectors are persisted; no user card photos are sent to Scryfall or another server. It helps future scans, not the in-progress review, and artwork still cannot distinguish variants sharing the same illustration.
7. **Full-cycle metrics.** Native logcat now includes capture-to-capture gap, scene-to-capture, capture-to-receipt, and prearmed-vs-post-save scene timing. WebView diagnostics log verification duration, manual-selection wait, total finalization, slow gaps over four seconds, and an every-ten-card/final session summary.
8. **Regression checks integrated into GitHub Actions.** `tools/check_stack_gate.sh` compiles and runs plain-JVM gate/coordinator tests. `node tools/check_51224_scan_throughput.js` tests source integration and throughput aggregation. Prior card-recognition, physical-printing fusion and WebView security checks remain.

## GitHub installation

Preferred: apply the **v5.12.24 patch ZIP** to the repository root, preserving each exact path, then commit. To replace the entire project, use the **complete-source ZIP** and copy the *contents* of its `manavault-main/` folder into your checkout. Keep the existing `.github/workflows/build-apk.yml` path. Do not add another root `index.html`, `MainActivity.java`, or duplicate build.gradle.

A safe command-line path after reviewing and scanning the ZIP:

```bash
# From a local clone of your GitHub repository, on a new branch:
unzip ManaVault-5.12.24-throughput-patch.zip -d .
git status --short
# Review changes, then commit and push the branch.
```

GitHub Actions: **Build ManaVault Android APK → Run workflow**, or push to `main` after validating the branch. Back up your local collection before installing a new APK. Do not uninstall your existing app if Android reports a signing mismatch.

## Test on the phone

Use a 100-card mixed-era stack, fixed phone stand, and the existing one-card-on-top method. Verify:

- A new top card appears while the prior save is still finishing: new-scene recognition is detected, but *no* second capture is persisted before the first receipt.
- Modern cards with readable set codes complete automatically, with one audible tone after each saved receipt.
- Two identical physical copies both add one copy each **only** after a real cover/reveal or deliberate Same copy confirmation. Leaving the same card stationary must not add a copy.
- Older RTR, THS, M14 and 7ED cards report symbol/artwork evidence and provide an immediate manual choice if evidence conflicts.
- Unrecognized or sideways cards do not repeat forever; the retry and Next card controls remain.
- Compare true *cards per minute*, `Scan gap` averages and `manual review` wait with the previous 45-save trial (about 7m24s; ~6.1 saves/minute). A test result of 20–30/minute or 95%+ auto-ID is a **target**, not an established result.

## What remains

- The existing 450-reference *bundled* artwork seed is not a complete printing image database. Manual learning improves future local coverage, but does not supply every possible alternate artwork upfront.
- Auto-edition accuracy on photographed older cards, camera performance, and APK compilation **have not been validated on a physical device in this environment**.
- JavaScript still depends on the protected WebView for exact printing selection and database writes; the camera may prearm one next scene, but cannot process arbitrarily many unsaved captures in parallel, by design.
- This is a source-review/logic-regression release, not an antivirus certification. Scan archives with Defender; do not bypass a detection.

## Local checks

```bash
bash tools/check_stack_gate.sh
node tools/check_51224_scan_throughput.js
python3 tools/check_source_hygiene.py
```
