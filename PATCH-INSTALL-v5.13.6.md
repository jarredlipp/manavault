# v5.13.6 patch installation

Extract the patch ZIP. In your existing `jarredlipp/manavault` repository,
place the **contents** of the ZIP into the repository root, preserving paths.
Do not create a nested `manavault-main` directory. This is a small set of
modified files, not a replacement of your full repository.

Commit, then use Actions > Build ManaVault Android APK. Download the debug APK
from that workflow's artifacts after a successful build; back up your
collection and scan downloads with your antivirus before installing.

The complete source ZIP is a reference/backup; it is not a GitHub-upload
batch under the 100-file UI limit. No existing project files must be deleted.
