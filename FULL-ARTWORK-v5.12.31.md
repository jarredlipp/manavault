# ManaVault v5.12.31 — Complete printing-level artwork index

## What changed

This version keeps the **native CameraX/ML Kit scanner** and its existing collection storage. It adds an optional, separately downloadable **complete English paper-printing visual fingerprint database**, built from the existing Scryfall catalog, with no arbitrary 2,400-reference cap.

The new database stores a separate row per **printing ID and card face**, even when multiple entries share the same artwork or Scryfall illustration ID. Each row includes six normalized image descriptors (art, wide-frame, title, type line, rules/text box, footer), an additional RGB color descriptor for the art region, a perceptual artwork hash and its eight indexed hash bands, plus set code, collector number, and treatment metadata. It is an offline SQLite database compressed with gzip for distribution.

### Accuracy rules

- Artwork, color, text-box appearance and frame can **suggest** which rendering is present. They do not override a contradictory collector number or set code.
- Printings sharing the same illustration are **kept separate**. The full-index matcher returns a family of plausible printing IDs; exact selection still requires **independently photographed** collector/set/symbol evidence when art is shared.
- Visually indistinguishable printings (same art, text, frame and symbol but a different small printed collector number) **cannot be reliably separated by visual fingerprint matching alone**. The scanner must use a readable footer or request on-camera review.
- A title inferred from the global perceptual-hash index remains an **unverified suggestion**. It cannot bypass printing verification or collection-save safeguards.
- Missing/damaged references are recorded as unavailable. Incomplete candidate coverage triggers review, not an incorrect best guess.
- Scans are **resolved and saved during scanning**. No backlog or post-session processing is introduced.

## How the full database is generated

The normal APK workflow continues to build the small fallback artwork seed. It does **not** download all artwork images on every APK build.

1. Upload the v5.12.31 patch preserving `.github/workflows/` and `tools/` paths.
2. Open GitHub **Actions → Build full printing artwork database → Run workflow**. The new workflow is also scheduled weekly. It runs eight parallel image-fetching shards and can take substantial time and GitHub Actions quota on its first run.
3. A successful run publishes two GitHub Release assets under the **`artwork-db`** tag: `manavault-artwork.sqlite.gz` and `manavault-artwork-manifest.json`. Later runs also publish `manavault-artwork-delta.sqlite.gz` for additions, edits and removals.
4. Build/install the v5.12.31 APK from the existing **Build ManaVault Android APK** workflow. Back up your collection first, and do not uninstall your existing app if an APK signature mismatch occurs.
5. In ManaVault's **Automatic printing identification** panel, tap **Download / update complete offline artwork index** once while connected to Wi-Fi. It is installed after SHA-256 verification and SQLite integrity checking. Scanning may continue while it downloads. The fallback index remains active until the full database is ready.
6. Subsequent app launches check once a day for updated releases **only on unmetered connections** after you opted in; when a compatible delta exists, the updater uses it instead of the complete snapshot.

The release URL is intentionally fixed to this repository:
`https://github.com/jarredlipp/manavault/releases/download/artwork-db/`

**There is no precomputed full database in this source ZIP.** We have not run a live 120,000-printing image download or published the GitHub release from this environment. Until that workflow has successfully run, the phone will use its bundled artwork fallback. The full-index downloader will report HTTP 404 if a release has not yet been published.

## Updating and storage

- First install downloads the compressed index, not every original artwork image. The expanded on-device index is a SQLite file in **private app storage**; it is not included in your collection export or deleted by normal scanning.
- The build workflow reuses unchanged descriptors from the previous release, fetching new or changed Scryfall images only. On the phone, a version-compatible delta changes SQLite rows transactionally in a copied database, then swaps it into place only after row-count and integrity checks.
- The full index may use tens or hundreds of MB of storage. Keep sufficient space for the downloaded gzip file, the extracted database and an update-time temporary copy. Actual size depends on Scryfall's catalog and compression.
- Never use artwork similarity to infer foil, condition, language, or special manufacturing properties that do not appear in the photograph.

## Validation and known limits

`python tools/check_51231_full_artwork.py` tests duplicate illustration IDs across printings, text-only changes, visually identical printings with different collector IDs, reverse faces, indexed searches, checksum manifests, seeded rebuilds and delta generation. `node tools/check_51231_printing_accuracy.js` tests that same-illustration alternatives cannot be automatically selected as a specific printing on art alone. Existing stack, copy, OCR, symbol and security-hygiene tests continue to run in GitHub Actions.

These are **source and small-fixture tests**, not proof of accuracy on a phone. No full Android APK build, complete Scryfall artwork job, GitHub release publication, independent antivirus scan, or live camera benchmark was performed here. If the Scryfall image host changes its layout or a card's cropped regions do not align, full-index comparisons may remain inconclusive. Unknown/missing references stay review-only. The earlier Defender alerts were not established as false positives; scan all downloads and do not bypass Windows Security alerts.
