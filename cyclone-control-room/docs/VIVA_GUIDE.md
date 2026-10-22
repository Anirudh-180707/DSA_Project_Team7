# Viva Guide — what to explain, where it is in the code, and how to answer

This follows the five rubric components (2 marks each, scored 0/1/2). Read section 6 for the
questions each member should be ready for in the individual interview.

---

## 0. Must-explain code map (open these in the viva)

| # | File → method | What to say in one breath | Owner |
|---|---|---|---|
| 1 | `heap/FibNode.java` fields | left/right circular sibling list, single child pointer, parent, degree, mark, seq tie-break | M1 |
| 2 | `FibonacciHeap.concat` | splice two circular lists with 4 pointer updates → O(1) union and child promotion | M1 |
| 3 | `FibonacciHeap.insert` / `union` | lazy: new one-node tree / list splice, update min, no restructuring | M1 |
| 4 | `FibonacciHeap.extractMin` | promote children, remove min, consolidate | M1 |
| 5 | `FibonacciHeap.consolidate` + `link` | degree table of size ⌊log_φ n⌋+2, link equal degrees, rebuild min | M1 |
| 6 | `FibonacciHeap.decreaseKey` + `cut` | lower key; if heap order breaks, cut to root list | M2 |
| 7 | `FibonacciHeap.cascadingCut` | mark on first loss, cut on second, loop upward | M2 |
| 8 | `FibonacciHeap.delete` | cut + force min + extract-min | M2 |
| 9 | `FibonacciHeap.validate` + `HeapTests` | all invariants; 200k random ops vs TreeSet reference | M2 |
| 10 | `core/ControlRoom` handles + `resolve` + `mergeDistricts` | O(1) id→node lookup; merge = union + alias, no relabelling | M3 |
| 11 | `core/OpResult` + checks in `ControlRoom` | every invalid input becomes a message, never an exception | M3 |
| 12 | `sim/ImpactSimulation.step` | same arrivals to both policies; aging = decrease-key; merges = union | M3 |
| 13 | `ui/MainApp.Recorder` + `ForestSnapshot` | heap events → immutable snapshots → Swing Timer animation | M4 |
| 14 | `ui/HeapView.measure/place` | subtree width = leaves; parent centred; compact view above 60 nodes | M4 |
| 15 | `ui/MainApp.perform` | logs actual work and ΔΦ → amortised cost shown live | M4 |

---

## 1. Data Structure Justification (2 marks)

**One-sentence pitch:** our workload is dominated by *urgency raises* and *district merges*, with fewer
removals, and the Fibonacci heap is the only standard priority queue that makes both of those O(1)
(raise is amortised), while still giving O(1) find-min and O(log n) amortised extract-min.

**Operation mix in a cyclone control room**

| What happens | How often | Heap op |
|---|---|---|
| New requests | very often | insert |
| Urgency rises (water rising, a patient worsening, waiting too long) | **most often** — every pending request can rise many times | decrease-key |
| An overwhelmed district handed to a neighbour | several times per cyclone, on large lists | union |
| A team dispatched | limited by number of teams | extract-min |
| Coordinator checks most urgent | constantly | find-min |

**Comparison with the alternatives (say this table out loud)**

| Structure | insert | find-min | decrease-key | merge | extract-min |
|---|---|---|---|---|---|
| Unsorted list | O(1) | O(n) | O(1) | O(1) | O(n) |
| Sorted list | O(n) | O(1) | O(n) | O(n) | O(1) |
| Array binary heap | O(log n) | O(1) | O(log n) | **O(n)** | O(log n) |
| Binomial heap | O(1) am. | O(log n)* | O(log n) | O(log n) | O(log n) |
| Balanced BST / `TreeSet` | O(log n) | O(log n) | O(log n) | O(n) | O(log n) |
| **Fibonacci heap** | **O(1)** | **O(1)** | **O(1) am.** | **O(1)** | O(log n) am. |

(*O(1) if a min pointer is kept.)

With *R* raises, *M* merges, *I* inserts and *S* serves on heaps of size up to *n*:

- Binary heap: O((I + R + S) log n + M·n)
- Fibonacci heap: O(I + R + M + S log n)

Because R is the largest term and merges involve large lists, the Fibonacci heap wins exactly where our
workload is heaviest. Our benchmark shows it (section 4).

**Uniqueness to mention:** laziness. Insert and union do no restructuring at all; all tidying is
postponed to extract-min (consolidation). Cascading cuts with mark bits keep trees "bushy", which is
what bounds the degree by log<sub>φ</sub> n — hence the name *Fibonacci*.

**Honest trade-off (examiners like this):** Fibonacci heaps have bigger constant factors (more pointers
per node, worse cache locality). For small n, or workloads without decrease-key/merge, a binary heap is
faster in wall-clock time. Our benchmark's mixed-workload experiment shows this honestly. Our
justification is the *asymptotic* operation profile, which experiments 1 and 2 confirm.

---

## 2. Implementation Strategy (2 marks)

Be ready to point at the exact lines. File: `src/relief/heap/FibonacciHeap.java` unless stated.

### 2.1 Linked representation, not array-based

- Array heaps (binary heap) rely on "children of i are 2i+1, 2i+2". Fibonacci heap trees have **any
  number of children**, and nodes are constantly **cut out** and **spliced** into other lists. In an
  array, a cut or a merge means copying elements — O(n). With pointers, it is a few pointer updates.
- Node fields (`FibNode.java`): `key, seq, value, parent, child, left, right, degree, mark, inHeap`.

### 2.2 Why *circular doubly* linked lists (root list and every child list)

| Need | Why circular + doubly linked gives it |
|---|---|
| Remove a node in O(1) given only the node (cut, link) | doubly linked: we know both neighbours |
| Concatenate two whole lists in O(1) (union; promoting children in extract-min) | `concat(a, b)`: 4 pointer updates, no walking to the tail |
| No special cases for head/tail/null | circular: every node has non-null left/right |
| Reach all siblings from any one | parent stores only one `child` pointer |

Show `concat()` (around line 306): `a.right = b; b.left = a; bLeft.right = aRight; aRight.left = bLeft;`

### 2.3 The degree table in consolidation is an array (on purpose)

`consolidate()` (around line 154) uses `table[d]` = "the root of degree d seen so far". Indexing by degree
needs random access, so an **array of size D(n)+2** is the right tool here, where
`D(n) = floor(log_φ n)` (`maxDegreeBound`). We also copy the roots into an array first, because linking
changes the root list while we iterate it.

### 2.4 Mark bits and the cascading cut

- `decreaseKey` (around line 222): lower the key; if smaller than the parent, `cut` it to the root list,
  then `cascadingCut(parent)`.
- `cascadingCut` (around line 265): an unmarked non-root gets **marked** (it has lost one child); a
  marked node has now lost a **second** child, so it is cut too, and we continue upward.
- Written **iteratively** (a loop, not recursion) so a long chain cannot overflow the Java stack.
- A node is unmarked whenever it becomes a root or becomes a child via `link` (clean slate).

### 2.5 Supporting structures in `ControlRoom.java`

| Structure | Why |
|---|---|
| `HashMap<String, FibNode>` handles (request id → node) | A heap cannot search by id. Decrease-key needs a direct pointer, so we keep the handle: O(1) lookup. |
| `LinkedHashMap<String, FibonacciHeap>` districts | One heap per active district. |
| `mergedInto` alias map with path compression (union-find idea) | After "Puri → Khordha", we must NOT walk Puri's requests to relabel them (O(n) would destroy the O(1) merge). Lookups follow the alias chain instead. |
| `seq` arrival counter in every node | Ties: two requests with urgency 3 are served in arrival order (FIFO). Global counter, so the order survives merges. |
| `inHeap` flag | A served or cancelled request's handle is rejected (no use-after-free style bugs). |
| `OpResult` | Invalid input returns a message instead of throwing, so the live demo never crashes. |

### 2.6 Separation of heap and UI

- The heap fires a `StepListener` event at each structural step (insert, link, cut, mark, cascading cut…).
  The UI turns each event into an immutable `ForestSnapshot` and animates them. With no listener (replay,
  benchmark) the cost is one null check.
- The UI never touches live pointers. Console mode (`ConsoleApp`) uses the same `ControlRoom`.

### 2.7 Things you might be asked to justify

- **Generic `FibonacciHeap<T>`**: the same class is reused by the replay simulation and the benchmark.
- **`int` key + `long seq`** instead of a comparator: keeps comparisons cheap; tie-break inside `FibNode.less`.
- **Delete without −∞**: `delete` cuts the node to the root list, forces `min = x`, then extract-min.
  Avoids reserving a sentinel key.
- **`validate()`**: checks every invariant (heap order, circular links, parent pointers, degree counts,
  root count, marked count, min pointer, degree bound). The tests call it repeatedly.

---

## 3. Live Demo (2 marks)

See `DEMO_SCRIPT.md` for the minute-by-minute flow. What the screen shows:

- **Centre**: every tree in the root list; the dashed line + arc is the **circular root list**; gold
  ring + "min" arrow = min pointer; magenta ring + "M" = **marked**; orange halo = changed in this step.
  The grey line above shows `n, t (roots), m (marked), Φ = t + 2m, max degree, bound`.
- **Log**: every operation prints the actual work (links, cuts, roots scanned) and **ΔΦ**, then
  "amortised = actual + ΔΦ" — you can use this to *prove the complexity live*.
- **Right**: replay of ~1,300 synthetic requests through both policies.

**Edge cases handled (button "Run edge-case checklist" shows all of them in 1 click):**

| Faculty input | What happens |
|---|---|
| Serve from an empty district | "No pending requests in X – nothing to serve" |
| Serve overall when everything is empty | message, no crash |
| Raise to the same or a larger number | rejected with explanation (decrease-key must decrease) |
| Raise / cancel an unknown, served or cancelled id | rejected |
| Urgency 0, 11, non-number | rejected (1–10 only) |
| Blank district, unknown type | rejected |
| Merge a district with itself / already merged | rejected |
| Merge an empty district | allowed, "nothing to move" |
| Duplicate urgencies | served in arrival order (FIFO tie-break) |
| Single-node heap: serve it | heap becomes empty, min = null |
| New request for a merged district | routed to the district that absorbed it |
| Dataset file missing/corrupt | built-in scenario / small sample used instead; bad CSV lines skipped and counted |

---

## 4. Time Complexity Analysis (2 marks)

### 4.1 Exact bounds of our solution

| Operation (our method) | Bound |
|---|---|
| Add request (`ControlRoom.addRequest` → `insert`) | O(1) (+ expected O(1) hash, near-O(1) alias lookup) |
| Most urgent in a district (`peek` → `findMin`) | O(1) |
| Raise urgency (`raiseUrgency` → `decreaseKey`) | **O(1) amortised** |
| Merge districts (`mergeDistricts` → `union`) | **O(1)** worst case |
| Serve (`serve` → `extractMin`) | **O(log n) amortised** |
| Serve most urgent overall (`serveGlobal`) | O(k + log n), k = number of active districts |
| Cancel (`cancel` → `delete`) | O(log n) amortised |
| Space | O(n) |

UI-only helpers (filling the drop-down, `validate`, `allNodes`) are O(n) and are *not* part of the
algorithm; the console mode does not need them.

### 4.2 Potential function

Φ(H) = t(H) + 2·m(H), where t = number of roots, m = number of marked nodes. Φ ≥ 0 and Φ = 0 for an empty
heap, so total amortised cost ≥ total actual cost. Amortised cost = actual cost + ΔΦ.

- **insert**: actual O(1); one new root → ΔΦ = +1. Amortised **O(1)**.
- **union**: actual O(1) (splice + one comparison); Φ(H₁ ∪ H₂) = Φ(H₁) + Φ(H₂), so ΔΦ = 0. **O(1)**.
- **extract-min**: let t be roots before. Moving ≤ D(n) children and consolidating ≤ t + D(n) − 1 roots
  costs O(t + D(n)). Afterwards at most D(n) + 1 roots (all degrees distinct) and no new marks. So
  ΔΦ ≤ (D(n) + 1) − t. Amortised ≤ O(t + D(n)) + D(n) + 1 − t = **O(D(n)) = O(log n)**
  (scale the potential's unit to cover the constant in O(t)).
- **decrease-key**: suppose it makes c cuts in total (1 direct + c − 1 cascading). Actual O(c).
  Roots increase by c. Each cascading cut un-marks a node (c − 1 of them) and at most one node is
  newly marked, so Δm ≤ −(c − 1) + 1 = 2 − c. ΔΦ ≤ c + 2(2 − c) = 4 − c.
  Amortised ≤ c + 4 − c = **O(1)**. *This is why the factor 2 on m is needed.*
- **delete** = cut (O(1) amortised) + extract-min = **O(log n)**.

### 4.3 Why D(n) = O(log n) — the "Fibonacci" part

Lemma: let x have degree k, with children y₁…y_k in the order they were linked. When y_i was linked,
x already had ≥ i − 1 children, and linking only joins equal degrees, so y_i had degree ≥ i − 1. Since
then y_i lost at most one child (a second loss would have cut it — the mark rule), so
degree(y_i) ≥ i − 2.

Let s_k be the minimum size of a subtree whose root has degree k. Then
s_k ≥ 2 + Σ_{i=2..k} s_{i−2}, which gives s_k ≥ F_{k+2} ≥ φ^k, where φ = (1 + √5)/2 ≈ 1.618.
So n ≥ φ^k, i.e. **k ≤ log_φ n ≈ 1.44 log₂ n**. That is `maxDegreeBound()` and the size of the
consolidation table.

### 4.4 Evidence from our own runs (`./run-benchmark.sh`, numbers from our test machine)

**Experiment 1 — merging two heaps of n each:**

| n | Fibonacci (µs) | Binary heap (µs) | binary element moves |
|---|---|---|---|
| 1,000 | ~6 | ~42 | 3,771 |
| 10,000 | ~2 | ~383 | 38,035 |
| 100,000 | ~35 | ~12,085 | 381,659 |
| 1,000,000 | ~56 | ~180,305 | 3,815,943 |

Fibonacci stays flat (noise only); binary grows linearly — O(1) vs O(n).

**Experiment 2 — work per raise when a request becomes the most urgent:**

| n | Fibonacci cuts per raise | binary swaps per raise | log₂ n |
|---|---|---|---|
| 1,000 | 0.99 | 8.74 | 10.0 |
| 10,000 | 1.00 | 12.12 | 13.3 |
| 100,000 | 1.00 | 15.45 | 16.6 |
| 1,000,000 | 1.00 | 18.71 | 19.9 |

Fibonacci is constant; binary tracks log₂ n — O(1) vs O(log n).

**Replay (the GUI's right panel and `replay` in console):** 1,296 requests, 1,978 raises caused 577
cuts — about **0.3 cuts per raise**, constant on average, exactly what "amortised O(1)" predicts.

**Experiment 3 — mixed workload, wall clock:** the binary heap is often *faster* here. Say why: pointer
chasing and cache misses (constant factors). The asymptotic advantage shows up in experiments 1 and 2.
Admitting this honestly is better than overclaiming.

### 4.5 Showing complexity live

After each operation the log prints, for example (cascading-cut demo):

```
✔ Raised R-5 from 7 to 1.
   work: links 0, cuts 2 (of which cascading 1), roots scanned 0  |  ΔΦ = +0
   amortised cost = actual (2 + 1) + ΔΦ (+0) = 3
```

2 cuts → t went up by 2 and one marked node was unmarked (m down by 1): ΔΦ = 2 − 2 = 0. Amortised 3 ≤ 4.

---

## 5. Impact (the "why it matters" part)

From the replay of the synthetic cyclone (1,296 requests, 2 district merges, 2 dispatches per district per
tick, urgency rises by 1 every 10 ticks of waiting):

| Metric | Fibonacci-heap priority | Arrival order (FIFO) |
|---|---|---|
| Critical requests: average wait | 0.7 ticks | 14.4 ticks |
| Critical served within deadline | 95% | 43% |
| Worst wait of a critical request | 11 ticks | 53 ticks |
| Average wait, all requests | 16.3 | 16.3 |
| Worst wait, any request | 90 | 54 |

Talking points:
- Same total throughput (same average wait overall) — prioritisation **reorders** work; it doesn't
  create teams.
- The price is that low-urgency requests can wait longer (worst wait 90). That is why urgency **ages**
  (decrease-key every 10 ticks): nothing starves. This is also where decrease-key frequency comes from.
- Merges: the heap moved 2 districts with 8 pointer updates; FIFO queues had to move 139 elements.
- The data is **synthetic** (fixed seed), modelled on a coastal landfall — say so if asked.

---

## 6. Individual Contribution & GitHub (2 marks) — questions per member

Ownership is in `TEAM_GITHUB_PLAN.md`. Everyone must also be able to answer the *common* questions.

### Common (anyone may be asked)
1. Why a Fibonacci heap and not a binary heap? (operation profile table)
2. What is a mark bit and why do we need it? (bounds degree; without cascading cuts trees get skinny and D(n) is no longer O(log n))
3. What is the potential function and why "2m"? (each cascading cut releases 2 units: 1 pays for the cut, 1 for the new root)
4. Why is insert O(1) but extract-min O(log n)? (laziness; consolidation pays later)
5. Show your commits and explain one of them.

### Member 1 — core heap (Section A: insert, union, extractMin, consolidate, link, FibNode)
- Walk through `concat()`; draw the 4 pointer changes.
- In `extractMin`, why check `z.right == z` *before* removing z?
- Why copy the roots into an array before consolidating?
- What is the size of the degree table and why?
- Why does `link` unmark the child?
- What happens if two roots have equal key? (seq tie-break in `FibNode.less`)
- Console mode: how is the ASCII tree printed?

### Member 2 — decrease-key family (Section B) + tests + benchmark
- Trace `decreaseKey` on the demo: raise the first grandchild, then its sibling. Where does the mark appear and when does the cascading cut happen?
- Why is `cascadingCut` a loop and not recursion?
- Why do roots never stay marked?
- How does `delete` work without −∞?
- How do the tests know the heap is correct? (TreeSet reference over 200k random ops + `validate()`)
- Explain the benchmark: why does binary merge cost O(n)? why binary swaps ≈ log₂ n per raise?
- Why is the binary heap sometimes faster in wall-clock time?

### Member 3 — control room, data, simulation
- Why does a decrease-key need the `handles` HashMap?
- How does merging districts stay O(1) even though requests still say "Puri"? (alias + path compression)
- What does `serveGlobal` cost and why? (O(k) over district minimums)
- How are invalid inputs handled? (`OpResult`, never throws)
- How is the dataset generated? What if the CSV is missing or has bad lines?
- How does the replay compare policies fairly? (same arrivals, same capacity, same merges)
- Why does urgency age, and what would happen without it? (starvation of low-urgency requests)

### Member 4 — user interface and visualiser
- How does the animation get every intermediate step without touching heap internals? (StepListener → immutable ForestSnapshot → Swing Timer)
- How is the tree laid out? (width of a subtree = number of leaves; parent centred over children)
- Why switch to a compact view above 60 nodes?
- Why can't a UI error corrupt the heap? (snapshots; every button goes through `Act`, which catches exceptions)
- What do Φ, t, m on screen mean and how do you use them to show amortised cost?

### Tough questions (prepare together)
- *"Urgency is only 1–10. Wouldn't 10 queues (a bucket queue) be O(1) for everything?"* —
  True for a fixed small integer scale, and we'd say so. We chose a comparison-based heap because real
  triage scores are finer-grained and unbounded (people at risk, water level, time waiting, medical
  severity combined into one score) and the Fibonacci heap works for any ordered key without
  re-engineering. With our 1–10 demo scale, the heap is still correct and the complexity claims hold.
- *"Pairing heap?"* — Simpler and fast in practice, but its decrease-key is not proven O(1) amortised
  (known lower bound Ω(log log n)); we wanted the guaranteed bounds from the reference paper.
- *"Is the replay escalation loop O(n) per tick?"* — Yes, that is the simulation driver deciding *which*
  requests age; each actual raise is O(1) amortised. A real system would schedule aging events with a timer.
- *"Can the heap shrink a district again (split)?"* — No: union is destructive by design; a split is not a
  Fibonacci-heap operation and wasn't needed.
