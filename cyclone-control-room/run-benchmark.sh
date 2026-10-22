#!/usr/bin/env bash
# Fibonacci heap vs array binary heap: merge cost, work per raise, mixed workload
set -e
cd "$(dirname "$0")"
./build.sh >/dev/null
java -Xmx2g -cp out relief.bench.Benchmark
