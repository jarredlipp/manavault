#!/usr/bin/env bash
# Run from an actual ManaVault Gradle checkout AFTER merging source and wiring live activity.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
python3 tests/test_wiring_gate.py
python3 tools/check_camera_wiring.py .
bash tests/run-core-tests.sh
bash tests/run-android-adapter-compile.sh
if command -v ./gradlew >/dev/null 2>&1; then
  ./gradlew --no-daemon :app:assembleDebug
elif [ -x ./gradlew ]; then
  ./gradlew --no-daemon :app:assembleDebug
else
  gradle --no-daemon :app:assembleDebug
fi
python3 tools/verify_scanner_wiring.py app/build/outputs/apk/debug/app-debug.apk
