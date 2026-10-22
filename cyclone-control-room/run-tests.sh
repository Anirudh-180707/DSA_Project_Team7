#!/usr/bin/env bash
# Runs the full self-checking test-suite (randomised + edge cases + invariants)
set -e
cd "$(dirname "$0")"
./build.sh >/dev/null
java -cp out relief.HeapTests
