# Running the Live Demo on a Mac

## 1. Install Java (once)

Open **Terminal** (Cmd + Space → "Terminal") and check:

```bash
java -version
javac -version
```

If both print a version **11 or higher**, skip to step 2. Otherwise install a JDK — either option works:

**Option A — installer (easiest):** go to **adoptium.net**, download **Temurin 21 (LTS)** for macOS
(choose *aarch64* for M1/M2/M3/M4 Macs, *x64* for Intel), open the `.pkg`, click through. Close and
reopen Terminal, then check `javac -version` again.

**Option B — Homebrew:**
```bash
brew install openjdk@21
sudo ln -sfn "$(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk" /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

## 2. Get the project

Either unzip the project zip, or (after the team has pushed) clone it:
```bash
cd ~/Desktop
git clone https://github.com/<owner>/cyclone-control-room.git
cd cyclone-control-room
```

If you unzipped from Downloads:
```bash
cd ~/Downloads/cyclone-control-room
xattr -dr com.apple.quarantine .   # removes macOS "downloaded file" lock (harmless)
chmod +x *.sh
```

## 3. Run

```bash
./run-tests.sh     # should end with: Passed 14 / 14
./run.sh           # opens the Cyclone Control Room window
```

Other modes:
```bash
./run-console.sh                                   # terminal fallback (type: help)
./run-console.sh < tests/sample_faculty_script.txt # scripted run
./run-benchmark.sh                                 # Fibonacci vs binary heap numbers
```

Always run the scripts **from the project folder**, so the app finds `data/cyclone_requests.csv`
(if it can't, it uses the built-in dataset automatically and says so in the log).

## 4. Running from an IDE instead (optional)

- **IntelliJ IDEA:** File → Open → the project folder. Right-click `src` → *Mark Directory as → Sources
  Root*; right-click `test` → *Test Sources Root*. Open `src/relief/ui/MainApp.java` → green ▶ next to
  `main`. Set the run configuration's *Working directory* to the project folder.
- **VS Code:** install "Extension Pack for Java", open the folder, open `MainApp.java`, click *Run*.

## 5. Projector tips

- Maximise the window (green button, or Ctrl + Cmd + F). It is designed for 1440×900 and above; on
  a smaller screen, the panels scroll.
- System Settings → Displays → choose "Mirror" so the faculty sees the same screen.
- Turn on Do Not Disturb so notifications don't cover the demo.

## 6. Troubleshooting

| Message | Fix |
|---|---|
| `permission denied: ./run.sh` | `chmod +x *.sh` |
| `javac not found` / `Unable to locate a Java Runtime` | install a JDK (step 1), reopen Terminal |
| `zsh: command not found: ./run.sh` | you are not in the project folder: `cd` into it (`ls` should show `run.sh`) |
| `release version 11 not supported` | your JDK is older than 11 — install Temurin 21 |
| window opens but is blank / frozen | close it, run `./run-console.sh` (same logic), then try again |
| "unidentified developer" when double-clicking a jar | right-click → Open, or run `java -jar dist/CycloneControlRoom.jar` from Terminal |
