# Cyclone Control Room — Disaster-Relief Prioritisation with a Fibonacci Heap

DSA Lab Evaluation 3 · Team: CB.SC.U4CSE25704, CB.SC.U4CSE25706, CB.SC.U4CSE25747, CB.SC.U4CSE25755

During a cyclone, a state relief control room must rank thousands of rescue, medicine, food and boat
requests whose urgency keeps rising, merge an overwhelmed district's list into a neighbour's, and always
show the coordinator the most urgent request. Each district's pending requests live in a **Fibonacci heap**
(Fredman & Tarjan, JACM 1987).

| Control-room action | Heap operation | Cost |
|---|---|---|
| New request arrives | `insert` | O(1) |
| Show most urgent | `findMin` | O(1) |
| Urgency rises (e.g. water level rising) | `decreaseKey` (cut + cascading cut) | O(1) amortised |
| Overwhelmed district handed to neighbour | `union` (splice root lists) | O(1) |
| Dispatch a team to the most urgent | `extractMin` (consolidate) | O(log n) amortised |
| Request no longer needed | `delete` | O(log n) amortised |

Urgency scale: **1 = critical … 10 = can wait**. Smaller key = more urgent, so "raise urgency" = decrease-key.

## Run it (Mac)

You need a JDK (11 or newer; 21 recommended). Check with `java -version` and `javac -version`.

```bash
cd cyclone-control-room
chmod +x *.sh          # first time only
./run.sh               # GUI simulator (the live demo)
./run-console.sh       # fallback: same logic in the terminal
./run-tests.sh         # 14 self-checking tests (200k random ops vs a reference)
./run-benchmark.sh     # Fibonacci heap vs array binary heap
./build-jar.sh         # optional: dist/CycloneControlRoom.jar
```

**Windows:** double-click `run.bat` (GUI) or `run-tests.bat`. They find the JDK, compile and run.
Manual alternative in Command Prompt:

```
dir /s /b src\*.java test\*.java > sources.txt
javac --release 11 -encoding UTF-8 -d out @sources.txt
java -cp out relief.ui.MainApp
```

No Maven, Gradle or external libraries: plain `javac`, so it runs on any lab machine.

## Layout

```
src/relief/heap   FibNode, FibonacciHeap, HeapStats, StepListener   <- the data structure
src/relief/core   ControlRoom (districts, id->node handles, merge aliases), OpResult
src/relief/model  ReliefRequest, RequestType
src/relief/data   Dataset, DatasetLoader (CSV), DatasetGenerator (built-in fallback)
src/relief/sim    ImpactSimulation, PolicyMetrics (priority vs FIFO replay)
src/relief/ui     MainApp (window), HeapView (forest drawing), ImpactPanel, ForestSnapshot, Theme, Ui (modern components)
src/relief/cli    ConsoleApp (fallback mode)
src/relief/bench  Benchmark, BinaryHeapBaseline
test/relief       HeapTests
data/             cyclone_requests.csv (synthetic, ~1,300 requests), small_sample.csv
tests/            sample_faculty_script.txt (pipe into console mode)
docs/             VIVA_GUIDE.md, DEMO_SCRIPT.md, TEAM_GITHUB_PLAN.md
```

The heap never depends on the UI. The UI only reads immutable snapshots, so a display problem cannot
corrupt the data structure, and console mode runs the exact same `ControlRoom`.

## Reference

M. L. Fredman and R. E. Tarjan, "Fibonacci Heaps and Their Uses in Improved Network Optimization
Algorithms," *Journal of the ACM*, 34(3), 596–615, 1987.

The dataset is synthetic (generated with a fixed seed in `DatasetGenerator`), modelled on an
Odisha-coast landfall; it is not real incident data.
