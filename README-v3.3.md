# ManaVault v3.3 — automatic Scryfall database updates

Changes from v3.2:
- Card database checks for updates on every app launch (network metadata checks throttled to once per 24 hours when cache exists).
- Downloads Oracle Cards bulk export automatically when there is no cache or Scryfall reports a newer bulk release.
- Retries on network reconnection; retains existing local IndexedDB records when offline or download fails.
- Correctly streams Scryfall JSON array format rather than incorrectly treating it as JSONL.
- Manual force-refresh remains available in Scan cards.

Important limitations:
- This updates Oracle card identities and metadata, not the separate artwork fingerprint database or all individual printings.
- The first download is large and requires internet access. Keep the app running until it reports Ready.
- Incremental import writes into the existing store; if a download is interrupted, the existing database is preserved but may include some newly imported records. The next successful sync completes it.
- Build-time bundled visual-index generation is unchanged and must succeed independently.
- JavaScript syntax validated; APK and end-to-end Android network download not tested here.
