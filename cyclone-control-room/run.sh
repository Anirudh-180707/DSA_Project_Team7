#!/usr/bin/env bash
# Launches the Cyclone Control Room GUI
set -e
cd "$(dirname "$0")"
./build.sh
java -cp out relief.ui.MainApp
