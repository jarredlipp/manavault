# ManaVault 5.13.1 — split-title recovery and conservative identity suggestions

## Why this update

In an October 25, 2026 native-scanning test, *Night // Day* (DMR 214),
*Stand // Deliver* (DMR 207), *Wax // Wane* (DMR 211), and *Fire // Ice*
(DMR 215) were sometimes skipped as an unrecognized title or "player".
The app had a **printing verifier** that could OCR both physical title panels,
but its main **title recognizer** could not identify an unknown split card
before invoking that verifier. The ordinary portrait-title strip sampled
rules text instead of each rotated title panel.

## Changes

- When the normal native OCR title sequence fails, inspect each vertically
  stacked split-card half separately. Rotate each half's left-side title strip
  upright and compare the two independently observed titles to the Scryfall
  name catalog. A unique two-face pair may become a **provisional** title;
  it must still pass the downstream dual-panel and printing verifier before
  any collection save. Successful ordinary scans incur no new OCR passes.
- Reject generic rules words, such as "player", from title candidates.
- Filter unrelated FTS rules-text identity suggestions if readable title
  words contradict them. A rules-text suggestion never authorizes a save.
- Add build preflight and regression coverage for the new split-pair helper.
  All existing features, database formats, and tests remain.

## Full artwork index (separate issue)

The `Full printing artwork: NOT INSTALLED` status and `Artwork release HTTP
404` are **not caused by card scanning**. GitHub has not made the required
`artwork-db` release asset available to the app. Run the **Build full printing
artwork database** GitHub Actions workflow from
`.github/workflows/full-artwork-index.yml`. It downloads and fingerprints
all available printing images and publishes a release with
`manavault-artwork-manifest.json` and `manavault-artwork.sqlite.gz`.
Wait for a successful **publish** job, check the artwork-db release assets,
then use ManaVault's existing Download/update button over Wi-Fi. The phone
cannot generate or publish this large database from the starter APK.

## Validation caveat

Passing source/JVM tests is not proof of higher on-device recognition
accuracy. Build the APK through GitHub Actions and repeat the same four
split cards; compare manual interventions and *correct* saves per minute.
