# ManaVault — full artwork publishing repair

This repair fixes the final verification error after the SQLite merge successfully generates the full artwork index:

`ModuleNotFoundError: No module named 'PIL'` from `tools/check_51231_full_artwork.py`.

## Files

- `tools/check_51231_full_artwork.py`: Release verification uses Python standard-library imports only. Image fixture test imports are delayed until those tests execute.
- `tools/full_artwork_index.py`: Merging and updating databases do not import Pillow; shard image processing imports it only when needed. This is included as a dependency-safe cumulative fix.
- `.github/workflows/full-artwork-index.yml`: Complete build workflow that does not need third-party image packages in the publish job.
- `.github/workflows/publish-existing-artwork.yml`: Manual publish-only workflow, which downloads the catalog and eight existing shard artifacts from a failed full-artwork run. Avoids repeating large Scryfall image downloads.

## Installation

1. Scan this ZIP with antivirus. Do not disable protections to open it.
2. Unzip and upload all four files into the *matching paths* in your GitHub repo (no extra enclosing directory). Commit to `main`.
3. Open the GitHub Actions page of the **most recent failed full-artwork run**. Copy the digits at the end of its URL `/actions/runs/123456789`.
4. On Actions, select **Publish previously built artwork shards** > Run workflow. Paste those digits in `source_run_id` and run on `main`.
5. Wait for the single publish job, then verify Releases contains `artwork-db` with `manavault-artwork.sqlite.gz` and `manavault-artwork-manifest.json`.
6. In the ManaVault app select **Download / update complete offline artwork index** and verify it says INSTALLED.

The previous workflow retained shard artifacts for **3 days**. If its catalog or any shard artifact has expired, the publish-only job will stop with a clear error. In that case, rerun **Build full printing artwork database** using the updated source.

## Tested

- End-to-end 8-shard SQLite merge to `.sqlite` and `.sqlite.gz`, generating matching manifest, with Python `-S` excluding third-party packages.
- `--verify-published` and `--verify-catalog` both succeed against the merged database with Python `-S`.
- Original artwork fixture/unit tests pass with Pillow and requests installed.
- Both GitHub workflow YAML files parse and have expected steps.

The actual user's 111,301-face index, GitHub publish permissions, and phone installation have not been tested in this environment.
