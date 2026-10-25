# Installing the v5.13.2 scanner patch

1. Back up the existing ManaVault collection in the app first.
2. Scan the ZIP with your antivirus. Do not bypass a security warning.
3. Extract `ManaVault-5.13.2-recognition-patch.zip` locally.
4. Upload its CONTENTS to the root of the `main` branch of your ManaVault
   GitHub repository, keeping the folders exactly as supplied. Do not create a
   new `manavault-main/` parent folder in GitHub.
5. Commit and run **Build ManaVault Android APK** in GitHub Actions. The build
   will run regression checks before producing the installable APK.
6. Install the built APK after confirming the build succeeds, then check it
   displays `5.13.2` and that the separate 111,301-face artwork index is still
   installed.
7. Retest Endless Sands (EOS #0015), Captain Storm (LCI #0303), Webstrike
   Elite (DFT #0345), Black Waltz No. 3 (FIN #0214), and split cards in the
   same stand/lighting arrangement. Record exact-printing accuracy, retries,
   manual review time, and indexed-lookup phase/latency.

This patch is intended for a repository already at **v5.13.1**. The full-source
archive is provided separately and includes all previously existing files.
