# Replace the GitHub project without keeping the flagged files

**Do not upload the ZIP itself into the repository as an application file.** Extract it after your local antivirus accepts it, then commit the *contents*. Preserve your existing repository and data before replacing anything.

## Browser workflow (no command line needed)

1. On GitHub, first create a safety branch from `main`, such as `security-cleanup-51221`. You can review the changes before merging to `main`.
2. **Delete these OLD paths from the branch** using GitHub's file view (open the file, use `...` > **Delete file**, and commit). They are absent from the new source ZIP:
   - `index.html` **(flagged)**
   - `tools/check_commander_creator_5128.js` **(flagged)**
   - `AndroidManifest.xml`
   - `MainActivity.java`
   - `build (1).gradle`
   - `build-apk.yml`
   - `build_visual_index.py`
   - `download_and_build_index.py`
   - `card-db.js`
   - `BUILD-FIX-INSTRUCTIONS.txt`
   - `UPLOAD-INSTRUCTIONS.txt`
3. Extract the accepted **ManaVault v5.12.21 cleaned source ZIP** on your computer. In GitHub use **Add file > Upload files**; drag the **contents of the extracted `manavault-main` folder**, including `.github/`, not the enclosing folder and not the ZIP itself.
4. Commit the replacement files on the cleanup branch. Inspect the file list: the root should contain `app/`, `tools/`, `.github/workflows/`, `README.md`, `SECURITY-REVIEW.md`, `GITHUB-REPLACEMENT.md`, `settings.gradle`, `build.gradle`, `gradle.properties`, and `RECOGNITION-RELIABILITY-UPDATE.md`.
5. Confirm the **two flagged paths are truly gone in the branch file tree**. Uploading new files by itself does **not** delete them. They will remain visible in prior Git commit history; deleting from the latest tree does not erase history.
6. Open **Actions > Build ManaVault Android APK**. Confirm `Verify release source layout and WebView security settings` and scanner regressions pass. The first build may take time because Scryfall assets must be downloaded. Review logs before installing any artifact.
7. When satisfied, merge the branch into `main` and back up your collection before updating the app.

## Git workflow (if you already use Git)

After creating a safety branch and inspecting the package, extract its folder contents over the repository working tree, then delete the old paths (for example with `git rm -- 'index.html' 'tools/check_commander_creator_5128.js'` and the other nine listed paths). Check `git status` and `git diff` carefully before committing. Avoid `git push --force`.

## What stays

- `app/src/main/assets/index.html` — the **real Android application HTML** remains.
- `app/src/main/assets/commander-creator.js` — Commander features remain.
- `app/src/main/java/com/manavault/app/NativeScannerActivity.java` — native continuous scanning remains.
- `tools/ensure_offline_assets.sh` — offline database and visual-asset build pipeline remains.
- Collection storage key `manavault-v1` and app identifier `com.manavault.app` remain unchanged.
