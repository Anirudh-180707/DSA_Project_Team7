#!/usr/bin/env bash
# Compiles all sources into ./out  (used by every run-*.sh script)
set -e
cd "$(dirname "$0")"
if ! command -v javac >/dev/null 2>&1; then
  echo "javac not found. On a Mac:  brew install openjdk@21   (or install Temurin 21 from adoptium.net)"
  exit 1
fi
rm -rf out
mkdir -p out
DIRS="src"; [ -d test ] && DIRS="src test"
javac --release 11 -encoding UTF-8 -d out $(find $DIRS -name "*.java")
echo "Build OK -> out/"
