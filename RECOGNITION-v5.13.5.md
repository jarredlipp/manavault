# v5.13.5 — Saga decorative-prefix and joined-digit OCR recovery

## Source evidence
The v5.13.4 complete diagnostic archive recorded `O The Long Reach of Night 3`
and `O The Long Reach of Night3` as unmatched names. The photo shows the
physical card `The Long Reach of Night`, with `109/302` and `NEO` in the footer.
The isolated leading O is most likely the saga-frame glyph interpreted as text,
and the terminal 3 is consistent with the printed mana cost. The prior
first-two-character name-correction bucket begins with `o ` rather than `th`.

## Change
`TitleRecoveryPolicy.strayEmblemAndJoinedDigit` now strips that two-part OCR
pattern only to form a **candidate index lookup key**. It requires isolated
leading O, a one-digit suffix joined or separated after a >=4-letter last word, a title with at
least four words, and an **exact indexed name match**. Only this combined
pattern is repaired. It does not alter automatically accepted literal titles,
standalone numbered titles, manual set/collector choices, or camera lifecycle.

`NativeNameIndex.suggestTitle` returns the candidate to the existing
`ocr-candidate` path. No automatic collection save may be based on this
string alone: subsequent physical set/collector evidence or explicit user
choice remains mandatory.

This is a narrow fix, not a general solution to OCR failures on glare, sideways
cards, or unusual title frames. The archive log proves retries/skips rather than
unintended saves for the shown Saga capture. The deck deletion confirmation
seen in the screenshot is the app's existing `confirm()` prompt for a saved
deck, not a collection deletion or evidence of a remote command.

Validation: pure-Java TitleRecoveryPolicy tests and scanner regression suite.
A GitHub Actions Android Gradle build and real-phone test are still required.
