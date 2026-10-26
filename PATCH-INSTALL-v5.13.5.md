# Install ManaVault 5.13.5 — Saga OCR candidate fix

This patch is for an existing **5.13.4** repository checkout. It changes only
source and tests; it does not contain an APK or the large external artwork index.

1. Export a collection backup from your installed ManaVault app.
2. Scan the source ZIP with Windows Defender and inspect the files if desired.
3. Extract the **patch ZIP**, then upload its contents with their folder paths
   preserved to your existing GitHub repository root; replace matching paths.
   Do not upload a new nested `manavault-main/` folder.
4. Commit, then run Actions > Build ManaVault Android APK. Review all checks
   and install the APK as an **update**, not an uninstall/reinstall.
5. Confirm that the app header reads `ManaVault 5.13.5 · CameraX`.

The patch adds narrow recognition for `O The Long Reach of Night3` and
`O The Long Reach of Night 3` after exact catalog lookup. The correction
remains provisional and needs independently corroborating set/collector evidence
or a manual edition selection. Normal exact-title scanning, copy policy,
collection storage, decks, diagnostic session ZIP export and the separately
installed complete artwork index are unchanged.

The GitHub Actions Android build and real camera behavior were NOT run locally;
source regression checks and Java compilation contracts were run locally.
