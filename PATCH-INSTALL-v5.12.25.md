# ManaVault v5.12.25 targeted patch: GitHub instructions

1. Scan this ZIP with Microsoft Defender before extracting. Never override a detection.
2. Back up your collection using ManaVault Export backup; create a GitHub branch before uploading.
3. Open the patch's `manavault-main` directory. Upload its files to the SAME paths in your current GitHub repository. Do not upload a second nested project directory.
4. Keep app/src/main/assets/index.html (the active Android app). Do NOT add back obsolete root-level index.html or tools/check_commander_creator_5128.js.
5. Commit to your branch and run Actions > Build ManaVault Android APK. Confirm source tests and asset generation succeed. Download the generated APK artifact, scan it and test.
6. After a save, tap Copy options to choose Same card (no new quantity) versus Another copy (scan +1). For any accidental prior extra saved card, use Scan cards > Undo last auto-saved card or correct the quantity from Collection.
7. Benchmark a mixed 100-card stack, especially Festering Newt, Light of Day, Tigereye Cameo, Rakdos Shred-Freak, and identical physical copies.

The release was source-tested only. Android compilation and live-camera performance were not validated here.
