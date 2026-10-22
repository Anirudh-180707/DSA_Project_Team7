package relief.cli;

import relief.core.ControlRoom;
import relief.core.OpResult;
import relief.data.Dataset;
import relief.data.DatasetGenerator;
import relief.data.DatasetLoader;
import relief.heap.FibNode;
import relief.heap.FibonacciHeap;
import relief.heap.HeapStats;
import relief.model.ReliefRequest;
import relief.model.RequestType;
import relief.sim.ImpactSimulation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

/**
 * Plain-terminal fallback. Same ControlRoom and heap as the GUI, so if the display
 * misbehaves during the evaluation we switch to this and nothing is lost.
 * Faculty test cases can be typed in directly, or piped from a file:
 *     ./run-console.sh < tests/faculty_script.txt
 */
public final class ConsoleApp {

    private final ControlRoom room = new ControlRoom();

    public static void main(String[] args) throws Exception {
        new ConsoleApp().run();
    }

    private void run() throws Exception {
        banner();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        System.out.print("\n> ");
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                if (System.console() == null) System.out.println(line);   // echo when input is piped
                if (!handle(line)) break;
            }
            System.out.print("\n> ");
        }
        System.out.println("\nBye.");
    }

    /** Returns false when the user wants to quit. Never throws. */
    private boolean handle(String line) {
        String[] p = line.split("\\s+");
        String cmd = p[0].toLowerCase();
        try {
            switch (cmd) {
                case "add": {
                    if (p.length < 4) { usage("add <district> <type> <urgency>"); break; }
                    String district = String.join(" ", java.util.Arrays.copyOfRange(p, 1, p.length - 2));
                    RequestType t = RequestType.parse(p[p.length - 2]);
                    Integer u = parseInt(p[p.length - 1]);
                    if (u == null) { System.out.println("  REJECTED: urgency must be a whole number 1..10"); break; }
                    print(room.addRequest(district, t, u));
                    break;
                }
                case "raise": {
                    if (p.length != 3) { usage("raise <requestId> <newUrgency>"); break; }
                    Integer u = parseInt(p[2]);
                    if (u == null) { System.out.println("  REJECTED: urgency must be a whole number 1..10"); break; }
                    work(() -> room.raiseUrgency(p[1], u));
                    break;
                }
                case "merge": {
                    if (p.length != 3) { usage("merge <fromDistrict> <intoDistrict>   (single-word names)"); break; }
                    print(room.mergeDistricts(p[1], p[2]));
                    break;
                }
                case "serve": {
                    if (p.length < 2) { usage("serve <district>   or   serveall"); break; }
                    work(() -> room.serve(rest(p, 1)));
                    break;
                }
                case "serveall": work(room::serveGlobal); break;
                case "cancel": {
                    if (p.length != 2) { usage("cancel <requestId>"); break; }
                    work(() -> room.cancel(p[1]));
                    break;
                }
                case "open": {
                    if (p.length < 2) { usage("open <district>"); break; }
                    print(room.addDistrict(rest(p, 1)));
                    break;
                }
                case "show": {
                    if (p.length < 2) { for (String d : room.activeDistricts()) show(d); break; }
                    show(rest(p, 1));
                    break;
                }
                case "peek": {
                    if (p.length < 2) { usage("peek <district>"); break; }
                    FibNode<ReliefRequest> m = room.peek(rest(p, 1));
                    System.out.println(m == null ? "  (empty)" : "  most urgent: " + describe(m));
                    break;
                }
                case "stats": stats(); break;
                case "demo": demo(); break;
                case "replay": replay(p.length > 1 ? rest(p, 1) : null); break;
                case "check": {
                    for (String d : room.activeDistricts()) {
                        String e = room.heapOf(d).validate();
                        System.out.println("  " + d + ": " + (e == null ? "all heap invariants hold" : "BROKEN: " + e));
                    }
                    break;
                }
                case "reset": room.reset(); System.out.println("  reset."); break;
                case "help": case "?": help(); break;
                case "quit": case "exit": case "q": return false;
                default: System.out.println("  Unknown command '" + cmd + "'. Type help.");
            }
        } catch (RuntimeException ex) {
            System.out.println("  Handled safely: " + ex.getMessage());
        }
        return true;
    }

    // ------------------------------------------------------------ commands

    private void work(java.util.function.Supplier<OpResult> op) {
        HeapStats b = room.stats().copy();
        OpResult r = op.get();
        print(r);
        if (!r.ok) return;
        HeapStats a = room.stats();
        System.out.printf("  work: links %d, cuts %d (cascading %d), roots scanned %d%n",
                a.links - b.links, a.cuts - b.cuts, a.cascadingCuts - b.cascadingCuts, a.rootsScanned - b.rootsScanned);
        if (r.district != null && room.heapOf(r.district) != null && room.heapOf(r.district).size() <= 40) show(r.district);
    }

    private void show(String district) {
        FibonacciHeap<ReliefRequest> h = room.heapOf(district);
        if (h == null) { System.out.println("  Unknown district: " + district); return; }
        String d = room.resolve(district);
        System.out.printf("  %s : n=%d  roots t=%d  marked m=%d  PHI=t+2m=%d  maxDegreeBound=%d%n",
                d, h.size(), h.rootCount(), h.markedCount(), h.potential(), FibonacciHeap.maxDegreeBound(h.size()));
        if (h.isEmpty()) { System.out.println("  (empty heap, min = null)"); return; }
        if (h.size() > 60) { System.out.println("  (" + h.size() + " nodes - tree print skipped)"); return; }
        System.out.println("  root list (circular, starting at min):");
        for (FibNode<ReliefRequest> r : h.roots()) printTree(r, "    ", true, true);
    }

    private void printTree(FibNode<ReliefRequest> x, String indent, boolean last, boolean root) {
        String branch = root ? "* " : (last ? "`-- " : "|-- ");
        System.out.println(indent + branch + describe(x));
        List<FibNode<ReliefRequest>> kids = x.children();
        String next = indent + (root ? "  " : (last ? "    " : "|   "));
        for (int i = 0; i < kids.size(); i++) printTree(kids.get(i), next, i == kids.size() - 1, false);
    }

    private String describe(FibNode<ReliefRequest> x) {
        ReliefRequest r = x.getValue();
        return r.id() + " [u" + x.getKey() + "] " + r.type() + " (" + r.originDistrict() + ")"
                + (x.isMarked() ? "  MARKED" : "") + (x.getParent() == null && room.peek(r.originDistrict()) == x ? "  <- min" : "");
    }

    private void stats() {
        HeapStats s = room.stats();
        System.out.printf("  pending %d in %d districts | inserts %d, raises %d, merges %d, serves %d, deletes %d%n",
                room.totalPending(), room.activeDistricts().size(), s.inserts, s.decreaseKeys, s.unions, s.extractMins, s.deletes);
        System.out.printf("  links %d, cuts %d (cascading %d), roots scanned %d%n",
                s.links, s.cuts, s.cascadingCuts, s.rootsScanned);
        if (!room.mergeAliases().isEmpty()) System.out.println("  merged: " + room.mergeAliases());
    }

    private void demo() {
        room.reset();
        int[] puri = {3, 8, 6, 9, 7, 10, 5, 8, 9};
        for (int i = 0; i < puri.length; i++) room.addRequest("Puri", RequestType.values()[i % 4], puri[i]);
        room.addRequest("Khordha", RequestType.MEDICINE, 4);
        room.addRequest("Khordha", RequestType.RESCUE, 2);
        room.addRequest("Khordha", RequestType.FOOD, 7);
        room.addDistrict("Ganjam");
        room.serve("Puri");
        room.stats().reset();
        System.out.println("  Demo loaded: Puri is one 8-node tree, Khordha has 3 requests, Ganjam is empty.");
        show("Puri");
        FibNode<ReliefRequest> root = room.peek("Puri");
        for (FibNode<ReliefRequest> c : root.children()) {
            if (c.getDegree() == 2) {
                List<FibNode<ReliefRequest>> k = c.children();
                System.out.println("  Try: raise " + k.get(0).getValue().id() + " 1   (cut + mark "
                        + c.getValue().id() + ")   then   raise " + k.get(1).getValue().id() + " 1   (cascading cut)");
            }
        }
    }

    private void replay(String file) {
        Dataset d;
        try {
            d = file == null ? DatasetLoader.load(Paths.get("data", "cyclone_requests.csv")) : DatasetLoader.load(Paths.get(file));
        } catch (Exception ex) {
            System.out.println("  Could not load dataset (" + ex.getMessage() + "); using the built-in scenario.");
            d = DatasetGenerator.fullScenario();
        }
        ImpactSimulation s = new ImpactSimulation(d);
        long t0 = System.nanoTime();
        s.runAll();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("  Replayed %d requests, %d merges in %d ms (%d ticks)%n", d.arrivals.size(), d.merges.size(), ms, s.tick());
        System.out.printf("  %-34s %12s %12s%n", "", "Fib-heap", "FIFO");
        System.out.printf("  %-34s %12.1f %12.1f%n", "critical avg wait (ticks)", s.priority.avgCriticalWait(), s.fifo.avgCriticalWait());
        System.out.printf("  %-34s %11.0f%% %11.0f%%%n", "critical within deadline", s.priority.criticalOnTimePct(), s.fifo.criticalOnTimePct());
        System.out.printf("  %-34s %12d %12d%n", "worst critical wait (ticks)", s.priority.worstCriticalWait, s.fifo.worstCriticalWait);
        System.out.printf("  %-34s %12.1f %12.1f%n", "all requests avg wait (ticks)", s.priority.avgWait(), s.fifo.avgWait());
        System.out.printf("  %-34s %12d %12d%n", "worst wait, any request (ticks)", s.priority.worstWait, s.fifo.worstWait);
        System.out.printf("  %-34s %12d %12d%n", "merge work (pointer ops / moves)", s.priority.mergeWork, s.fifo.mergeWork);
        HeapStats h = s.heapStats();
        System.out.printf("  heap: %d raises -> %d cuts, %d of them cascading (%.2f cuts per raise)%n",
                h.decreaseKeys, h.cuts, h.cascadingCuts, h.decreaseKeys == 0 ? 0.0 : (double) h.cuts / h.decreaseKeys);
    }

    // ------------------------------------------------------------ helpers

    private static void print(OpResult r) { System.out.println("  " + r); }

    private static void usage(String u) { System.out.println("  Usage: " + u); }

    private static Integer parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static String rest(String[] p, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(p, from, p.length));
    }

    private static void banner() {
        System.out.println("==============================================================");
        System.out.println(" Cyclone Control Room - console mode (Fibonacci heap)");
        System.out.println(" Urgency: 1 = critical ... 10 = can wait.  Type 'help'.");
        System.out.println("==============================================================");
    }

    private static void help() {
        System.out.println("  add <district> <type> <urgency>   insert          e.g. add Puri rescue 2");
        System.out.println("  raise <id> <newUrgency>           decrease-key    e.g. raise R-4 1");
        System.out.println("  merge <from> <into>               union           e.g. merge Puri Khordha");
        System.out.println("  serve <district>                  extract-min     e.g. serve Khordha");
        System.out.println("  serveall                          most urgent across all districts");
        System.out.println("  cancel <id>                       delete");
        System.out.println("  open <district>                   open an empty district");
        System.out.println("  show [district] | peek <district> | stats | check (validate invariants)");
        System.out.println("  demo                              load the cascading-cut demo");
        System.out.println("  replay [csvFile]                  priority vs FIFO on the dataset");
        System.out.println("  reset | help | quit");
        System.out.println("  types: rescue, medicine, food, boat (or r, m, f, b)");
    }
}
