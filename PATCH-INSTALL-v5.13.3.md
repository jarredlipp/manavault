# Installing ManaVault v5.13.3

1. **Export a collection backup** using ManaVault before updating. If you
   already have a scan accuracy audit, export it separately; it is not part
   of the collection JSON backup.
2. Scan the update ZIP with Windows Defender or another trusted antivirus.
   Do not bypass a threat alert.
3. Extract the `ManaVault-5.13.3-accuracy-refinement-patch.zip` contents.
4. Upload its files into the existing GitHub repository `main` branch with
   the supplied paths (do NOT add an extra `manavault-main/` directory).
   You must be updating an existing v5.13.2 source tree; the small patch is
   not a standalone repository.
5. Commit. Run **Actions → Build ManaVault Android APK** and confirm all
   regression checks, Java compilation and `assembleDebug` are green.
6. Install the resulting APK. Verify **ManaVault 5.13.3** appears and the
   installed complete artwork database is still available.
7. Compare a sample of 30–50 PHYSICAL cards against the saved printing in
   **Scan cards → Physical printing accuracy audit**. Report correct,
   incorrect, unchecked, and any new `Scan cycle SLOW` category breakdown.

The full-source ZIP preserves all v5.13.2 files and adds tests and
release documentation. Android SDK/Gradle and device validation may only be
available in the user's GitHub Actions and phone environment.
