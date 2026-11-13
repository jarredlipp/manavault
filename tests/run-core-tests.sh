#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" \
  "$ROOT/app/src/main/java/com/manavault/app/scanner/HandheldScanCoordinator.java" \
  "$ROOT/app/src/main/java/com/manavault/app/scanner/PrintingEvidenceResolver.java" \
  "$ROOT/app/src/main/java/com/manavault/app/scanner/FrameQualityEstimator.java" \
  "$ROOT/app/src/main/java/com/manavault/app/scanner/PerspectiveRectifier.java" \
  "$ROOT/app/src/main/java/com/manavault/app/scanner/ScannerStartupCoordinator.java" \
  "$ROOT/app/src/main/java/com/manavault/app/scanner/SceneFingerprint.java" \
  "$ROOT/tests/ScannerCoreTest.java" "$ROOT/tests/ScannerStartupTest.java" \
  "$ROOT/tests/ScannerSceneGateTest.java"
java -cp "$OUT" ScannerCoreTest
java -cp "$OUT" ScannerStartupTest
java -cp "$OUT" ScannerSceneGateTest
