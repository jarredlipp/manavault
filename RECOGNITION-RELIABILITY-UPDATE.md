# ManaVault recognition reliability patch — 2026-10-20

## Implemented
- Native OCR rejects common rules/keyword fragments (including "Cycling" and Exava's "Each other creature...") as card titles.
- Native title selection uses the actual height of the title crop and geometric placement to limit stray artwork/rules OCR; the expanded two-line title crop remains intact.
- Native OCR maintains rotated-card and split/alternate-name support already present in the project.
- JavaScript rejects the same non-title fragments before name matching, name-only fallback, and printing verification.
- Catalog-unavailable and verification-exception paths no longer silently create name-only collection entries; explicit user approval is required.
- Native retry receipts track repeated same-name failures and stop automatic OCR retry cycles after the second retry. Stacking a different card re-arms the scanner.
- New `tools/check_recognition_reliability.js` regression checks the observed failures and safety gates.

## Not yet fully implemented / limitations
- Enlarging the offline bundled artwork index requires a Scryfall source catalog and downloaded reference data. This ZIP retains the preexisting index and its existing on-device caching implementation; no additional 1000+ images or fingerprints are claimed.
- Existing layout-aware rotation and printing-evidence paths were preserved, not replaced by a newly trained classifier.
- No APK was compiled here: Android SDK/Gradle were not installed in the current environment. Run the GitHub Actions `build-apk.yml` workflow.
- Existing `tools/check_catalog_recovery.js` expects silent name-only saves during catalog warmup; that assertion intentionally conflicts with the revised explicit-consent safety policy.
- Existing `tools/check_scanner.js` expects a GitHub workflow step referencing `ensure_offline_assets.sh`; the uploaded workflow did not contain it, unrelated to this recognition patch.

## Tests run
- `javac TitleReadingPolicy.java`: passed
- `node tools/check_recognition_reliability.js`: passed
- `node tools/check_title_footer_51213.js`: passed
- `node tools/check_scan_recovery.js`: passed
- `node tools/check_5127_recognition.js`: passed
- `python tools/check_name_layout_51211.py`: passed

Test on a real device with Exava, Web-Slinger Elite, sideways battles, two-line showcase titles, and same-name reprints before relying on unattended bulk collection scans.


## October 20 follow-up — v5.12.18 (Teysa/Captain America trial)

Additional changes applied after observing the four-card trial:

- **Critical fix:** Both native Java and JavaScript footer filters wrongly treated an
  arbitrary word beginning with `en` as the printed English-language marker.
  In particular, **Teysa, Envoy of Ghosts** was classified as footer noise.
  Language markers now require a complete `EN` token (or a recognizably
  uppercase fused printing+artist footer) rather than a lowercased prefix.
- **Focused title OCR:** first pass reads the top 14.5% of the captured card;
  a wider 23.5% color fallback remains for showcase and multi-line titles.
  Detected line coordinates must be in the true title band.
- **Rotation guard:** for upright physical portrait cards, only the first
  orientation may generate an unverified name candidate. Rescue orientations
  can return exact indexed names but cannot promote the reversed card's artist
  credit or flavor text to an unverified title. Sideways card input prioritizes
  the 90/270-degree orientations with its own supported tentative band.
- **Type-line disambiguation:** "Land Tax" and "Artifact Mutation" are real
  title candidates; strings such as "Legendary Creature — Human Advisor" are
  not. This also prevents broad type keyword suppression of actual names.
- **Safe footer suggestion:** if all attempted reads are non-title text, the
  scanner may look for a unique physical set+collector combination and show the
  possible printing for immediate confirmation; it cannot silently save it.
- **New regression scripts:** `tools/check_title_rescue_guard.py` exercises the
  production Java policy on the JVM and checks scanner wiring;
  `tools/check_non_title_footer_review.js` checks JS review-only handling.
- **Existing test alignment:** the catalog-warmup test now exercises explicit
  approval of an unverified entry; the offline-assets test accepts the actual
  Gradle pre-build asset generation dependency.

Important: A successful test here proves policy logic and source wiring,
**not** OCR quality from Android's ML Kit camera implementation. Re-scan
Teysa and Captain America after installing the new APK; cards skipped in the
previous trial were not added to the collection. The physical printer catalog
and built-in reference fingerprints were not expanded in this release.


## October 21 follow-up — v5.12.19 (collector/footer OCR)

A new bounded rescue pass in `mv126FooterEvidence` recognizes OCR corruption of
**the printed rarity or leading zero**, including `O054`, `RO054`, `CO100`,
`U0232`, and `L0273`. For this path the OCR token must be a complete token
in a printing-footer region, have at least three numeric digits, and match a
collector number of the **already identified name** in the local catalog.

The rescue has lower evidence weight than a clean printed fraction or a clear
rarity+collector field; it cannot choose between two catalog variants with the
same number, cannot override an independently observed set code, and cannot
read a collector from the type-bar, illustrator credit, or copyright row.

`tools/check_51219_ocr_collector_rescue.js` adds physical-log-derived positive
and negative regression cases. No additional artwork references were bundled:
450 remain available until the asset pipeline is run with the source catalog.

Known limits: older border cards (e.g. Melek, Izzet Paragon) may still need
manual edition review when the physical set symbol and collector are unreadable.
Likewise, photo-based OCR is not verified on a real Android device by these
static tests. User-confirmed copies of the same card are not silently deduped.
