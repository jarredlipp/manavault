# ManaVault v5.12.25 — scanner copy control and printing recognition

## Changes

- The camera control following a confirmed save now reads **Copy options**.
  - **Same card · no save**: retains the current physical card, adds nothing, and keeps the scene gated.
  - **Another copy · scan +1**: explicit confirmation that a second physical card was placed on top; scans again and adds a separate copy only after exact-printing verification and the collection save receipt.
- A same-looking image is checked again before persistence even if a scene-change event was fired; a near-identical photograph is not automatically saved twice. An occlusion/reveal showing an identical-looking card no longer authorizes an automatic repeat save. The user must explicitly select Another copy.
- Collector verification may accept a unique, strong, 3+ digit physically printed collector number when weaker stray 1–2 digit OCR fragments appear, but only when the card name was positively read and no competing variant or strong collector reading exists.
- Expanded targeted Scryfall offline artwork references for old frames and the recent RTR, Theros, M14 and VOW examples. GitHub Actions now generates up to 1,200 bundled artwork fingerprints (minimum 900 required). The exact result depends on successful reference downloads during the build. **This does not independently verify a printing.**
- New GitHub Actions regression checks for physical-copy control and legacy collector rules.

## Expected workflow

Keep stacking physical cards, one new card on top of the previous card. New distinct artwork is eligible for automatic scene detection and save. For identical-looking cards or uncertain scene changes, tap Copy options and select whether it is the *same physical card* or a *different physical copy*.

## Limitations

- Live camera timing, reprint recognition and success-rate improvement have **not** been demonstrated until a new APK is compiled and tested on-device.
- The artwork library is a bounded advisory reference collection, not all Scryfall illustrations.
- Strong ambiguous or conflicting identifiers still require manual printing selection; barcode-free older cards may remain difficult.
- Static source review and green unit tests are not a malware clearance. Scan ZIPs and APKs with Microsoft Defender, and do not override detections.

## Installation

Upload patch files to their identical paths in the GitHub repository. Do not place the top-level `manavault-main` directory inside another such directory. Commit and run `Build ManaVault Android APK`. Export/back up the app collection before installing a new APK.
