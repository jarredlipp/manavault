# ManaVault v5.12.23 — physical printing evidence

This update follows v5.12.22's continuous-scanning and edition-safety fixes. It does **not** modify the 100-card collection save format, Android permissions, camera resolution, or the continuous stacking flow.

## Root causes addressed

1. **Expansion symbols on old/retro frames:** the native matcher previously searched a narrow horizontal/vertical band. It now examines more of the *right-hand type-line neighborhood* at multiple sizes for Tempest, Invasion, Seventh Edition, Return to Ravnica, and retro artifact layouts. It still requires a distinct winner among candidate set icons.
2. **Collector + logo + art had no combined decision:** the live fast path mostly tried either footer set codes, collector numbers, or very strong artwork separately. The new resolver combines a **candidate-scoped, observed collector numerator** with the physical set symbol. Where there is no collector, it requires either an exceptionally strong symbol match with exact title and uniquely identified set, or a solid symbol **plus complete bundled artwork evidence** and exact title.
3. **Reverse-faced transform cards:** the OCR path previously tried to prove both printed names appeared on the *same side* whenever a database name contained ` // `. True split/aftermath layouts still do so, but transform / modal double-faced cards no longer must show their hidden side.
4. **Artwork reference seed omitted reverse faces:** when the Scryfall bulk record contains a reverse-face image and illustration ID, the native catalog and bundled artwork generator now retain both face signatures under the same physical printing ID. The source catalog seed regenerates during the GitHub build.

## Six targeted regression cases

- Soul-Guide Lantern: retro **BRR #54**, Cliff Childs artwork; candidate-scoped short collector + physical symbol.
- Rakdos Shred-Freak: **RTR #221**; 221/274 footer + expansion symbol.
- Tigereye Cameo: **INV #314**; 314/350 footer + Invasion expansion symbol.
- Lone Wolf: **7ED #254**; 254/350 footer + Seventh Edition expansion symbol.
- Light of Day: original **TMP** printing; older frame and set icon where collector OCR is not sufficient.
- Desperate Farmer // Depraved Harvester: **VOW #104**; transformed side no longer requires a face that is physically on the other side; reverse artwork indexed.

These are source-level regression fixtures for evidence combination, **not proof that the phone camera recognizes each image in real conditions**. The matcher will still present immediate printing review when the icon/collector/art is unreadable, has close competing candidates, or conflicts with an observed field. Artwork and symbol similarity scores are not calibrated probabilities. Visual cues do not establish foil finish, condition, or authenticity.

## Changed files

- `app/src/main/java/com/manavault/app/PrintingFingerprintVerifier.java`
- `app/src/main/assets/index.html`
- `app/src/main/assets/card-db.js`
- `tools/build_native_catalog.py`
- `tools/build_artwork_index.py`
- `.github/workflows/build-apk.yml`
- `tools/check_51223_visual_fusion.js` (new)
- `tools/check_51223_reverse_face.py` (new)
- `SOURCE-MANIFEST.sha256` (regenerated in full-source package)

## GitHub installation

1. Scan the ZIP using Microsoft Defender before extraction. Do not override antivirus warnings.
2. Upload the changed files **to their exact paths** in the repository, replacing existing versions. Keep `app/src/main/assets/index.html` (the real Android app), and do not reintroduce the obsolete root `index.html` or the retired Commander test flagged previously.
3. Build from GitHub Actions. The workflow runs the two new regression checks before preparing the catalog and producing the APK. The complete native catalog, symbol index and artwork seed are generated **during that build**, not in the small ZIP.
4. Install the debug APK only after scanning it and backing up existing collection data. Do not uninstall your existing app solely because the signing keys differ.
5. Re-test all six physical cards; capture the `Printing marks`, `Structured printing evidence`, and `Physical printing fusion` logs if any requires manual selection.

**Limits:** Passed JS and Python checks in this environment; no full Android Gradle build and no real-device camera test have been performed here. A successfully downloaded archive is not a malware-free certification.
