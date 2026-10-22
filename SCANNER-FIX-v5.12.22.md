# ManaVault v5.12.22 — continuous scanner safety patch

This source update is based on ManaVault v5.12.21 build-fixed source, not a verified copy of your current GitHub branch. Review changes before merging. No full Android APK compilation or Windows Defender scan was performed here.

## What changed

- `app/src/main/java/com/manavault/app/NativeScannerActivity.java`
  - Retry counts for the same unresolved title are no longer erased merely because the camera's image hash changes. After two unsuccessful retries, the scanner blocks repeats until another card arrives or the user explicitly confirms starting the next physical card.
  - The recovery control says **Next card** for unresolved scans; for two same-named cards it continues to say **Same copy** and requires an explicit confirmation.
  - If the scanner erroneously thinks a just-saved card was replaced, it will not automatically save a second copy with the same name within 20 seconds unless the user explicitly confirms a newly stacked copy. Other names continue automatically.
  - Confirmed saves reset the unresolved retry budget. Collection writes remain DURING scanning, never deferred until Finish.
- `app/src/main/assets/index.html`
  - Removed the unsafe shortcut that marked a card's sole catalog printing as physically verified without any readable set or collector number.
  - A lone catalog printing without a matching physical identifier now opens the immediate printing review; a positive collector or set match can still verify and save immediately.
- `tools/check_auto_save.js`
  - Updated the regression scenarios to enforce the new rule and check that matching physical marks still auto-verify.

## Installation

For the small patch archive, upload the three affected files to the **same relative paths** in your GitHub repository, replacing their current versions. Preserve the `.github/workflows/build-apk.yml` workflow and all other files.

Run GitHub Actions > **Build ManaVault Android APK** on a test branch. Back up the collection before installing a test APK. Verify that the current phone app's data is preserved (do not uninstall it if Android refuses an APK signed with a different key).

## What to retest

1. Scan a common known printing (e.g. Foundation set code plus collector). It should save during scanning with an audible confirmation.
2. Keep one saved card stationary for 20 seconds. It must not save a second copy. Add a different card on top: the scanner should resume automatically.
3. Add a second *identical-title* card. If it does not automatically proceed, tap **Same copy** and confirm a second physical card was added.
4. For Who // What // When // Where // Why or another ambiguous edition, press Retry twice. It must not repeatedly capture the same unresolved card. Put a different card on top or use **Next card** if automatic stack detection stalls.
5. For older unique-catalog cards with unreadable footer, verify that printing review is offered instead of claiming a verified edition without physical evidence.
6. Retest sideways/split cards and the Webstrike Elite DFT #345 card. Title/printing recognition accuracy is not fully addressed by this state-safety patch.

## Validation performed

- 42 JavaScript and Python checks completed successfully, plus JVM stack-change tests and title reading tests.
- Android APK compilation and real-device camera tests were unavailable in this environment.
- The two old Defender flagged paths (`index.html` at repository root and `tools/check_commander_creator_5128.js`) are excluded from the complete-source archive. Antivirus software should check all ZIP/APK outputs independently.
