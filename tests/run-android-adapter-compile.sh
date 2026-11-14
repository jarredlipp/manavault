#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
mapfile -t SOURCES < <(find "$ROOT/tests/stubs" "$ROOT/app/src/main/java/com/manavault/app/scanner" -name '*.java' -print)
javac -Xlint:all -d "$OUT" "${SOURCES[@]}" "$ROOT/tests/CameraAdapterTest.java" "$ROOT/tests/ScannerRecoveryTest.java" "$ROOT/tests/CameraFocusLightingTest.java"
echo 'PASS: CameraX adapter compiles against local API stubs (NOT an Android APK build)'
java -cp "$OUT" CameraAdapterTest
java -cp "$OUT" ScannerRecoveryTest
java -cp "$OUT" CameraFocusLightingTest
