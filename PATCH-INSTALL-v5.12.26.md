# Upload ManaVault v5.12.26 targeted patch to GitHub

1. Scan the ZIP with Microsoft Defender. Never override a positive detection.
2. Create a branch/backup and export your current ManaVault collection.
3. Extract and open `manavault-main`, then upload the contained files into the corresponding existing repository paths, **without** nesting an extra `manavault-main` directory.
4. Commit and run GitHub Actions > Build ManaVault Android APK. Regression scripts are integrated into the workflow.
5. Scan the APK with your antivirus before installing and use the same signing key if updating a current installation.
6. On-device, rescan Webstrike Elite (DFT 0345), Lone Wolf (7ED 254/350), Tigereye Cameo (INV 314/350), Light of Day (TMP), Rakdos Shred-Freak (RTR 221/274), and same-copy/different-copy pairs.

NOTE: The patch does not delete repository files. A passing download or green source tests are not malware certifications.
