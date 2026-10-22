#!/usr/bin/env bash
# Fallback console mode. Pipe a script with:  ./run-console.sh < tests/sample_faculty_script.txt
set -e
cd "$(dirname "$0")"
./build.sh >/dev/null
java -cp out relief.cli.ConsoleApp
