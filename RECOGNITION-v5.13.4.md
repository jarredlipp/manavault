# ManaVault v5.13.4 — Complete scan-session diagnostics and selective recognition refinements

This is a **targeted** change on top of v5.13.3. It does not remove any existing
collection, deck, CameraX, printing, copy-safety or full-artwork index feature.
The Android app/package and stored collection JSON schema remain unchanged.

## Why 186 cards produced only a small diagnostic excerpt

In v5.13.3 `mvLogCamera` saved only the newest **150 diagnostic messages**, not
150 captures. A single card often produces multiple messages, so older scan
records were silently dropped before using Copy/Share. Separately, the native
CameraX activity refused captures after **150** in one camera session.

v5.13.4 keeps the 150-message **on-screen preview** for performance but now
appends each new message to an Android-private log file using a dedicated I/O
executor. Opening CameraX creates a separate file. The `Export COMPLETE session
logs (.zip)` button uses the Android system file picker and writes all retained
session logs as separate `.txt` files in a ZIP. The file is written via Android's
Storage Access Framework, not the share-sheet text field, to avoid clipboard or
share truncation. It includes `SCAN RECEIPT <id>` for each handled card, including
its final disposition and the saved printing where available. The archive keeps
up to 20 recent sessions or 32 MiB total, with an 8 MiB per-session limit;
those safeguards prevent unlimited storage use while covering 186 cards and
many substantially longer runs. The native session cap is now 300 captures.
When that limit is reached, the app asks the user to finish and open a new
session rather than silently accepting more captures.

**IMPORTANT:** Messages evicted by v5.13.3 and earlier cannot be reconstructed.
The update imports the last 150 messages retained by the prior version as a
clearly identified **partial historic** session. This import is one-time and
must not be confused with complete earlier run histories. The existing
v5.13.3 audit can still export previously retained scan outcomes separately.

## Faster, more useful decisions without guessing editions

- Adds a candidate-scoped lower-right footer OCR pass only after a known
  card's collector candidates remain unreadable in the normal footer and
  left-side rescue. This targets traditional 254/350 and 314/350 printing
  marks at the end of artist/copyright text. Already verified fast scans do
  **not** pay for the additional pass.
- Older frame copyright-year and artist clues may change the order of manual
  **review suggestions only**. They never independently verify a printing.
  Artwork and set-symbol similarities remain advisory where printings share
  visual treatment. Existing conflict and duplicate guards are unchanged.
- Adds native timing from first sufficiently changed candidate frame through
  confirmed stable scene and capture. Slow-gap events identify `capture→candidate`
  and `candidate→scene` separately. Physical handling and scene-gate delays
  are still combined in the first metric; these values are *diagnostics*, not
  proof of a slow user or slow camera.
- Increases physical printing audit retention from 300 to **1,200 handled
  captures**. Marking a wrong printing does not modify the collection.

## Deployment & limitations

Install the patch onto the corresponding v5.13.3 project paths. Run GitHub
Actions **Build ManaVault Android APK**. A separate full artwork database
rebuild is **not required**. Do not uninstall an installation with collection
data; make a backup first. Scan the archive/APK with a trusted antivirus.

Source-level JS regression tests, ordinary JVM scanner contracts, the new
186-card archive test, and source hygiene are run before the APK build.
**No Android SDK, full `assembleDebug` APK compilation, or on-device throughput
validation was available in this environment.** Exact-printing accuracy must
still be measured against a human-verified physical sample. Optimizations here
are guarded and have not been proven faster on device.
