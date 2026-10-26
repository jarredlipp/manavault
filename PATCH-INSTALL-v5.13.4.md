# Install ManaVault 5.13.4

1. In your existing app, **Export backup**. Export the v5.13.3 physical printing
   audit separately if it matters. Never uninstall the data-bearing app to fix
   a signature mismatch.
2. Download and scan `ManaVault-5.13.4-complete-diagnostics-patch.zip` with
   Windows Defender. If the file is flagged, do not bypass the warning.
3. Extract it. Upload the extracted files to the GitHub repository using the
   **exact included paths**; do not add an extra `manavault-main/` directory.
   This patch targets the existing **v5.13.3** repo; it is not standalone.
4. Commit and run **Actions → Build ManaVault Android APK** on `main`. The
   workflow runs original and new regression checks, then Gradle build.
5. Install the APK as an **update**, not as a reinstall, and confirm v5.13.4.
6. From the Scan cards page select **Export COMPLETE session logs (.zip)**.
   Android will ask where to save `ManaVault-complete-scanner-diagnostics.zip`.
   The ZIP contains a text file per native CameraX session, plus README.
   **Copy last 150 messages** and **Share recent preview** are intentionally
   short previews and do not export the complete archive.
7. Run 186+ cards if desired; there is now a **300-capture ceiling per camera
   session**. Compare saved exact printings against physical cards; export
   the physical-printing audit separately to measure accuracy.

This release will **not restore diagnostic messages that older builds already
deleted**. The app will try to preserve the previous build's most recent
150 messages as a partial historical record when you update.

The existing separately downloaded 111,301-face artwork database is untouched.
