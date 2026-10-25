# ManaVault v5.13.2 — Candidate retrieval, unusual titles, scan diagnostics

This is an incremental native scanner update to v5.13.1. It preserves existing
app features, source files, collections, native CameraX scanning, immediate
on-camera saves, and independent exact-printing verification.

## Changed

- Global **name candidate** retrieval against the already installed complete
  printing artwork SQLite index now compares artwork, color, and wide visual
  context, rather than allowing differing text boxes, rules, footer and title
  placement to dominate the first-stage *name* search. Printing-level ranking
  still uses its original separate descriptors for text, title, and footer.
- Failures of indexed hash-band retrieval may expand to Hamming-distance-two
  **byte-band** probes (296 bounded indexed keys) and candidate alignment
  shifts. This is a bounded fallback, not a full database table scan, not a
  trained neural network, and not a guarantee of recall. The final result
  remains advisory; physical title and set/collector checks are unchanged.
- Expanded artwork candidate SQL is moved onto the existing reference worker,
  rather than blocking the ML Kit callback / camera UI thread.
- A lower-position OCR region runs **only after ordinary top-title OCR and
  sideways split-card panel OCR fail**. For cards such as Endless Sands it can
  produce a provisional title. Text in the lower rules box might coincidentally
  be a real card name: the candidate must still be confirmed/verified.
- When title OCR is unavailable, high-similarity artwork name candidates are
  shown only in the native review picker, instead of unrelated fuzzy matches to
  `Unrecognized title`. No image-only automatic collection save was introduced.
- Set-code/collector-number lookup on ambiguous scans prefers the installed
  native SQLite catalog, independent of the guessed OCR title and IndexedDB.
  Existing corroboration and collision checks still decide save eligibility.
- Diagnostics now include the artwork search phase, indexed name candidate
  count, lookup time, and approximate footer-lookup pair count/time. Existing
  persistence, scene-change, and manual-review counters remain intact.

## Safety boundaries

- A valid full-art image match or lower title suggestion is **not** sufficient
  to assert an exact physical printing. Identical-art variants are separated by
  printed collector/set and other physical evidence; ambiguous cards still
  require review.
- Scanner still does not automatically count duplicate copies without physical
  copy confirmation. Repeated unresolved same-card retries retain their
  existing pause/Next-card protection.
- No new permissions, network services, downloaded executable models, or
  removal of features or tests.

## Tests and limitations

Run `node tools/check_5132_recognition.js`, compile/run
`tools/ArtworkProbePolicyTest.java`, execute the Android build workflow's
scanner regression step, and build the APK via GitHub Actions.

The local source and Java API-stub compilation checks **cannot** establish
correctness or speed on a real device. Hamming-two fallback might be slower on
large databases; review `Full artwork candidate retrieval` timings after
installing the APK. Use an identical physical-card benchmark for comparisons.

Installed full artwork DB version/face count remains independent from the APK
version, and the full database does **not** need rebuilding for this change.
