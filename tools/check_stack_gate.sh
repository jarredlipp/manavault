#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
javac -d "$tmp" "$root/app/src/main/java/com/manavault/app/StackChangeGate.java" "$root/tools/StackChangeGateTest.java"
java -cp "$tmp" com.manavault.app.StackChangeGateTest
