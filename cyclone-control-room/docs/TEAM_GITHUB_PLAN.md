# Team Ownership & GitHub Plan

The rubric checks that **each member commits from their own GitHub account** and **understands their
own part**. So: split the code by ownership below, have each person commit *their* files from *their*
laptop/account, and make sure each person can explain every line they committed. Make real, small,
honest commits — do not backdate or commit on someone else's behalf; the commit history is evidence.

Deadline is **25 October**: aim to finish commits on 23–24 October and book the evaluation slot now
(the faculty will not call you — rule 3 of the instructions).

---

## 1. Who owns what

| Member | Owns (files) | Explains in viva |
|---|---|---|
| **M1 — CB.SC.U4CSE25704** | `heap/FibNode.java`, `heap/HeapStats.java`, `heap/StepListener.java`, `heap/FibonacciHeap.java` **Section A** (insert, findMin, union, extractMin, consolidate, link, concat, maxDegreeBound), `cli/ConsoleApp.java`, `build.sh`, `run*.sh`, `.gitignore`, `README.md` | node design, circular lists, lazy insert/union, consolidation + degree table, D(n) bound |
| **M2 — CB.SC.U4CSE25706** | `heap/FibonacciHeap.java` **Section B** (decreaseKey, cut, cascadingCut, delete, validate), `test/relief/HeapTests.java`, `bench/Benchmark.java`, `bench/BinaryHeapBaseline.java`, `run-tests.sh`, `run-benchmark.sh` | mark bits, cascading cut, amortised analysis with Φ, tests, benchmark results |
| **M3 — CB.SC.U4CSE25747** | `model/*`, `core/ControlRoom.java`, `core/OpResult.java`, `data/*`, `data/*.csv`, `sim/*`, `tests/sample_faculty_script.txt` | problem mapping, handles map, merge aliases, edge-case handling, dataset, replay metrics |
| **M4 — CB.SC.U4CSE25755** | `ui/*` (MainApp, HeapView, ImpactPanel, ForestSnapshot, Theme), `build-jar.sh`, `docs/DEMO_SCRIPT.md` | visualiser, animation via snapshots, layout algorithm, impact panel, demo flow |

Everyone edits `docs/VIVA_GUIDE.md` (each adds/improves their own section).

---

## 2. One-time setup (every member, on their own Mac)

```bash
# 1. git (installs Apple's command-line tools if missing)
git --version

# 2. Tell git who you are — the email MUST be one added to YOUR GitHub account,
#    otherwise your commits will not show your avatar/name on GitHub.
git config --global user.name  "Your Name"
git config --global user.email "you@example.com"

# 3. Sign in to GitHub from the terminal (easiest way):
brew install gh          # if you have Homebrew
gh auth login            # choose GitHub.com → HTTPS → login with browser
# (No Homebrew? When git asks for a password on push, paste a Personal Access Token from
#  GitHub → Settings → Developer settings → Personal access tokens.)
```

## 3. Create the repository (M1)

1. On github.com: **New repository** → name `cyclone-control-room` → *no* README (we have one).
2. **Settings → Collaborators → Add people**: add M2, M3, M4 by GitHub username. They must accept the
   invite email.
3. On M1's Mac, in the unzipped project folder, start with **only M1's files** (see section 4):

```bash
cd cyclone-control-room
git init
git branch -M main
git remote add origin https://github.com/<M1-username>/cyclone-control-room.git
```

## 4. Commit order (so the history shows everyone, and it compiles at each step)

The zip contains the complete project. Each member adds **their** files in this order. Others clone the
repo and copy their files in from the zip.

`TEAM_STAGING/FibonacciHeap_SectionA_only.java` (outside the repo folder, in the zip) is the heap with
Section B as stubs — M1 commits that first, M2 later replaces the stubs with the real Section B.

**Step 1 — M1**
```bash
# put TEAM_STAGING/FibonacciHeap_SectionA_only.java in place as the heap for now
cp ../TEAM_STAGING/FibonacciHeap_SectionA_only.java src/relief/heap/FibonacciHeap.java
git add .gitignore README.md build.sh
git commit -m "Project skeleton: README, build script, gitignore"
git add src/relief/heap/FibNode.java src/relief/heap/HeapStats.java src/relief/heap/StepListener.java
git commit -m "FibNode with circular doubly linked sibling pointers, degree and mark"
git add src/relief/heap/FibonacciHeap.java
git commit -m "Fibonacci heap: O(1) insert, findMin and union via root-list splice"
git push -u origin main
```
(Optionally split the heap into two commits: insert/union first, extractMin + consolidate + link next.)

**Step 2 — M2** (after accepting the invite)
```bash
git clone https://github.com/<M1-username>/cyclone-control-room.git
cd cyclone-control-room
git checkout -b decrease-key
cp <zip>/cyclone-control-room/src/relief/heap/FibonacciHeap.java src/relief/heap/FibonacciHeap.java
git add src/relief/heap/FibonacciHeap.java
git commit -m "decreaseKey with cut and iterative cascading cut; delete; invariant checker"
git push -u origin decrease-key
```
Open a **Pull Request** on GitHub; M1 reviews and clicks *Merge*. (PRs show collaboration nicely.)

**Step 3 — M3**
```bash
git pull   # or clone
git checkout -b control-room
mkdir -p src/relief/model src/relief/core src/relief/data src/relief/sim data tests
# copy model/, core/, data/, sim/ sources, data/*.csv, tests/sample_faculty_script.txt from the zip
git add src/relief/model src/relief/core
git commit -m "ControlRoom: per-district heaps, id->node handles, O(1) merge with alias table"
git add src/relief/data data
git commit -m "Synthetic cyclone dataset generator, CSV loader with fallback"
git add src/relief/sim tests
git commit -m "Replay simulation: Fibonacci-heap priority vs FIFO with urgency aging"
git push -u origin control-room   # then open a PR
```

**Step 4 — M1 again:** `cli/ConsoleApp.java` + `run.sh`, `run-console.sh` → commit "Console fallback mode".

**Step 5 — M4**
```bash
git checkout -b ui
# copy src/relief/ui/* and build-jar.sh, docs/DEMO_SCRIPT.md
git add src/relief/ui/Theme.java src/relief/ui/ForestSnapshot.java
git commit -m "Immutable forest snapshots for animation"
git add src/relief/ui/HeapView.java
git commit -m "Heap forest visualiser: root list, min pointer, marks, compact view"
git add src/relief/ui/ImpactPanel.java src/relief/ui/MainApp.java build-jar.sh docs/DEMO_SCRIPT.md
git commit -m "Control room window: control panel, step animation, impact panel"
git push -u origin ui   # then PR
```

**Step 6 — M2 again:** `test/relief/HeapTests.java`, `bench/*`, `run-tests.sh`, `run-benchmark.sh`
→ commits "Test-suite: 200k random ops vs reference + edge cases", "Benchmark vs array binary heap".

**Step 7 — everyone:** each person improves their own section of `docs/VIVA_GUIDE.md` in their own
words and commits it. Strongly recommended: each member also makes **one real change of their own**
(a new test case, a clearer message, a colour, a comment) — it gives you something genuine to talk
about when the faculty asks "show me a commit you made".

After all merges, everyone runs:
```bash
git pull
./run-tests.sh      # must print Passed 14 / 14
./run.sh
```

## 5. Showing the commit history in the evaluation

- On GitHub: repository → **Insights → Contributors** (graph per person) and **Commits** (list with avatars).
- In Terminal: `git log --pretty=format:"%h  %an  %ad  %s" --date=short`
- Per person: `git shortlog -sn`
- If someone's commits show without an avatar: their `user.email` isn't on their GitHub account. Add that
  email in GitHub → Settings → Emails (it then links retroactively).

## 6. Checklist before you walk in

- [ ] Every member has at least 2–3 commits from their own account.
- [ ] `./run-tests.sh` passes on the demo laptop.
- [ ] The demo laptop has a JDK (see `README.md` / Mac steps) and the repo cloned fresh from GitHub.
- [ ] Each member can explain every file they own (use `VIVA_GUIDE.md` section 6).
- [ ] Practised the fallback (`./run-console.sh`) once.
- [ ] Slot booked with the lab faculty before 25 October.
