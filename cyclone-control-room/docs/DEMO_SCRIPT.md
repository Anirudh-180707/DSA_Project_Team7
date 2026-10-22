# Live Demo Script (about 5–6 minutes)

Before the evaluation: run `./run-tests.sh` (should end with `Passed 14 / 14`), then start `./run.sh`
and maximise the window. Keep a second Terminal tab open in the project folder for the fallback.

Who speaks: each member presents the part they own (see `TEAM_GITHUB_PLAN.md`).

---

## 0. Opening line (Member 3, 20 s)

"During a cyclone, a control room gets thousands of requests whose urgency keeps rising, and overwhelmed
districts are handed to neighbours. We keep each district's requests in a Fibonacci heap: raises and
merges are O(1), and the most urgent request is always at the min pointer."

## 1. Manual mode (Member 1 + Member 2, about 2 min)

1. Click **Load demo scenario**. *(Member 1)*
   "We inserted 9 requests into Puri — each insert is just a new one-node tree in the root list. Then we
   served the most urgent one, so consolidation linked trees of equal degree into one tree of 8 nodes."
   Point at: the gold **min** arrow, `t=1`, max degree 3 ≤ bound 4.
2. The request box already shows **R-2**. Set **New urgency = 1**, click **Raise urgency**. *(Member 2)*
   Animation: R-2 is cut to the root list, its parent **R-3 turns magenta (marked)**.
   Log: `cuts 1`, `ΔΦ = +3` (one new root +1, one new mark +2).
3. Pick **R-5**, urgency **1**, **Raise urgency**. *(Member 2)*
   Animation: R-5 cut, R-3 was already marked → **cascading cut**, R-3 moves to the root list unmarked.
   Log: `cuts 2 (of which cascading 1)`, `ΔΦ = +0`, `amortised = 3`. "That is the O(1) amortised bound
   working: the cascading cut was prepaid by the mark."
4. Merge: **Merge Puri into Khordha**. *(Member 3)*
   "Two circular lists spliced with four pointer updates; no tree touched; Puri's requests were not
   relabelled — Puri is now an alias of Khordha."
5. **Serve most urgent overall**. *(Member 1)* Watch the links during consolidation in the animation;
   the log shows `roots scanned` and `links`.
6. Invite faculty: "Please give us any inputs." Use the left panel. Type an id directly (e.g. `7`
   or `R-7`) in the Request box if needed.

## 2. Edge cases (Member 2 or 3, about 1 min)

Click **Run edge-case checklist** — all cases print in the log in one go (empty heap, invalid raise,
unknown id, merge with itself, empty merge, duplicates served FIFO, single-node heap…).
Then do two by hand if they ask: serve from **Ganjam** (empty) and raise a request to a larger number.

## 3. Replay + impact (Member 4, about 2 min)

1. Right panel: dataset label shows ~1,296 requests, 2 merges. Click **▶ Replay**; drag the speed slider.
2. Point out live: critical avg wait (blue vs grey), % on time, merge events appearing in the log,
   the counters ("1,978 raises → 577 cuts: about 0.3 cuts per raise — constant, amortised O(1)").
3. Final numbers to say: critical average wait **0.7 vs 14.4 ticks**, on time **95% vs 43%**.
   Mention the honest trade-off: worst wait of *any* request is higher (90 vs 54), which is why
   urgency ages so nothing starves.
4. If short of time, click **Instant** instead of Replay.

## 4. Complexity close (whoever owns that part, 30 s)

"Insert, find-min and merge are O(1); raise is O(1) amortised using Φ = t + 2m; serve is O(log n)
amortised because the Fibonacci bound keeps every degree ≤ log_φ n." Optionally run
`./run-benchmark.sh` in the second terminal: merge time flat vs linear; cuts per raise 1.0 vs log₂ n swaps.

---

## Fallbacks (practise these once)

| Problem | Do this |
|---|---|
| GUI window does not open / looks broken | `./run-console.sh`, then type `demo`, `raise R-2 1`, `raise R-5 1`, `merge Puri Khordha`, `serveall`, `replay` |
| Faculty wants many inputs fast | `./run-console.sh < tests/sample_faculty_script.txt` (or type their commands) |
| Dataset file won't load | Right panel → **Built-in** (or **Small**). Console: `replay` falls back automatically |
| Animation too fast/slow | "anim speed" slider (left), or untick **Animate each step** |
| Heap too big to draw | above 60 nodes it switches to a compact view automatically (root-degree histogram) |
| Need to prove correctness | `./run-tests.sh` → 14 tests incl. 200,000 random operations checked against a reference |
| Laptop dies | another member's laptop: clone from GitHub → `./run.sh` |

Reset anytime with **Reset everything** or **Load demo scenario**.
