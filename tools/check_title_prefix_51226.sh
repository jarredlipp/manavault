#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)";trap 'rm -rf "$out"' EXIT
javac -d "$out" app/src/main/java/com/manavault/app/TitlePrefixMatcher.java tools/TitlePrefixMatcherTest.java
java -cp "$out" com.manavault.app.TitlePrefixMatcherTest
