#!/usr/bin/env bash
# Builds dist/CycloneControlRoom.jar (double-clickable). Run it from the project folder
# so it can find data/cyclone_requests.csv:   java -jar dist/CycloneControlRoom.jar
set -e
cd "$(dirname "$0")"
./build.sh >/dev/null
mkdir -p dist
printf "Main-Class: relief.ui.MainApp\n" > out/manifest.txt
jar cfm dist/CycloneControlRoom.jar out/manifest.txt -C out relief
echo "Built dist/CycloneControlRoom.jar"
