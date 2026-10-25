# ManaVault v5.13.1 — targeted recognition hotfix

The ZIP patch keeps the **same `manavault-main/` root and relative paths** as
ManaVault v5.13.0. Extract it and upload its contained files **to the same
paths** in your GitHub repo; don't create another `manavault-main` directory.

This patch does not remove anything from the v5.13.0 project. It adds the
native `SplitFacePairPolicy.java` and checks, then updates the title scanner,
name index, OCR rules-word guard, review-suggestion filter, and version labels.
A complete-source ZIP is included separately for backup and migration.

1. Scan the ZIP using Defender. Do not bypass a quarantine/detection.
2. Back up your ManaVault collection in the installed app.
3. Merge the patch files into the existing GitHub paths and commit.
4. Run **Build ManaVault Android APK**. Source/JVM checks have passed here,
   but the full Gradle APK build was not available in this container.
5. Install the APK; the UI should say **ManaVault 5.13.1**.
6. Re-scan the four troublesome split cards: Night // Day DMR 214,
   Stand // Deliver DMR 207, Wax // Wane DMR 211, Fire // Ice DMR 215.
   Keep the camera/lighting consistent and inspect any wrong printing result.

## Complete artwork index — separate required GitHub step

A phone download error `Artwork release HTTP 404` means the app cannot find
`manavault-artwork-manifest.json` under the `artwork-db` GitHub release. The
APK build alone does not create that release. In GitHub Actions, manually run
**Build full printing artwork database**, wait for the **publish** job, and
check that its release contains the compressed SQLite and manifest assets.
Then use the existing in-app update button. If the workflow fails, share the
failed build/publish log; retrying the phone download before a release exists
will keep producing 404.
