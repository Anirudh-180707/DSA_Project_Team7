package relief;

import relief.core.ControlRoom;
import relief.core.OpResult;
import relief.data.Dataset;
import relief.data.DatasetGenerator;
import relief.heap.FibNode;
import relief.heap.FibonacciHeap;
import relief.model.RequestType;
import relief.sim.ImpactSimulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/**
 * Self-contained test-suite (no JUnit needed, so it runs on any lab Mac with just a JDK).
 * Run with:  ./run-tests.sh
 */
public final class HeapTests {

    private static int passed = 0, failed = 0;

    public static void main(String[] args) {
        test("empty heap: findMin/extractMin return null", HeapTests::emptyHeap);
        test("single node insert + extract", HeapTests::singleNode);
        test("extract order is sorted (heap-sort 10k)", HeapTests::heapSort);
        test("equal urgencies are served FIFO", HeapTests::fifoTies);
        test("decreaseKey rejects non-decrease and stale nodes", HeapTests::decreaseKeyRejects);
        test("cascading cut actually happens and marks are correct", HeapTests::cascadingCut);
        test("union with empty heaps on either side", HeapTests::unionEmpty);
        test("union of two populated heaps keeps all nodes", HeapTests::unionFull);
        test("delete arbitrary nodes", HeapTests::deleteNodes);
        test("degree bound D(n) <= floor(log_phi n) after many ops", HeapTests::degreeBound);
        test("randomised 200k ops vs reference TreeSet (+ invariants)", HeapTests::randomised);
        test("control room: edge cases return messages, never crash", HeapTests::controlRoomEdges);
        test("control room: merge aliases route later operations", HeapTests::controlRoomMerge);
        test("replay: priority beats FIFO for critical requests", HeapTests::replay);

        System.out.println();
        System.out.println("Passed " + passed + " / " + (passed + failed));
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------ tests

    static void emptyHeap() {
        FibonacciHeap<String> h = new FibonacciHeap<>();
        check(h.findMin() == null, "findMin on empty");
        check(h.extractMin() == null, "extractMin on empty");
        check(h.isEmpty() && h.size() == 0, "size 0");
        valid(h);
    }

    static void singleNode() {
        FibonacciHeap<String> h = new FibonacciHeap<>();
        FibNode<String> a = h.insert(5, "a");
        check(h.findMin() == a, "min is the only node");
        check(h.extractMin() == a, "extract returns it");
        check(h.isEmpty(), "now empty");
        check(!a.isInHeap(), "handle flagged as removed");
        valid(h);
    }

    static void heapSort() {
        FibonacciHeap<Integer> h = new FibonacciHeap<>();
        Random r = new Random(1);
        for (int i = 0; i < 10_000; i++) { int k = r.nextInt(1000); h.insert(k, k); }
        int prev = Integer.MIN_VALUE;
        while (!h.isEmpty()) {
            int k = h.extractMin().getKey();
            check(k >= prev, "non-decreasing output");
            prev = k;
        }
    }

    static void fifoTies() {
        FibonacciHeap<String> h = new FibonacciHeap<>();
        for (int i = 0; i < 50; i++) h.insert(3, "x" + i);
        h.extractMin();                    // forces consolidation of equal keys
        for (int i = 1; i < 50; i++) check(h.extractMin().getValue().equals("x" + i), "FIFO for ties at " + i);
    }

    static void decreaseKeyRejects() {
        FibonacciHeap<String> h = new FibonacciHeap<>();
        FibNode<String> a = h.insert(5, "a");
        expect(IllegalArgumentException.class, () -> h.decreaseKey(a, 5));
        expect(IllegalArgumentException.class, () -> h.decreaseKey(a, 9));
        h.extractMin();
        expect(IllegalStateException.class, () -> h.decreaseKey(a, 1));
        expect(IllegalStateException.class, () -> h.delete(a));
    }

    static void cascadingCut() {
        FibonacciHeap<String> h = new FibonacciHeap<>();
        List<FibNode<String>> nodes = new ArrayList<>();
        for (int i = 0; i <= 8; i++) nodes.add(h.insert(10 + i, "n" + i)); // 9 nodes
        h.extractMin();                     // 8 nodes -> one binomial tree of degree 3
        check(h.rootCount() == 1, "one tree after consolidation, got " + h.rootCount());
        FibNode<String> root = h.findMin();
        // the child of the root with degree 2 has two children of its own
        FibNode<String> parent = null;
        for (FibNode<String> c : root.children()) if (c.getDegree() == 2) parent = c;
        check(parent != null, "degree-2 child exists");
        FibNode<String> first = parent.children().get(0);
        FibNode<String> second = parent.children().get(1);
        long before = h.stats().cascadingCuts;
        h.decreaseKey(first, 1);            // cut; parent loses its 1st child -> marked
        check(first.getParent() == null, "first child cut to root list");
        check(parent.isMarked(), "parent marked after first child lost");
        valid(h);
        h.decreaseKey(second, 0);           // 2nd loss -> marked parent is cut too (cascading)
        check(h.stats().cascadingCuts == before + 1, "exactly one cascading cut");
        check(parent.getParent() == null, "marked parent moved to root list");
        check(!parent.isMarked(), "roots are unmarked");
        check(h.findMin() == second, "min updated");
        valid(h);
    }

    static void unionEmpty() {
        FibonacciHeap<String> a = new FibonacciHeap<>(), b = new FibonacciHeap<>();
        a.union(b);
        check(a.isEmpty(), "empty+empty");
        b.insert(4, "x");
        a.union(b);
        check(a.size() == 1 && b.isEmpty(), "empty+one");
        a.union(new FibonacciHeap<>());
        check(a.size() == 1, "one+empty");
        a.union(a);
        check(a.size() == 1, "self union ignored");
        valid(a);
    }

    static void unionFull() {
        FibonacciHeap<Integer> a = new FibonacciHeap<>(), b = new FibonacciHeap<>();
        for (int i = 0; i < 100; i++) a.insert(i * 2, i);
        for (int i = 0; i < 100; i++) b.insert(i * 2 + 1, i);
        a.extractMin(); b.extractMin();
        a.union(b);
        check(a.size() == 198 && b.isEmpty() && b.findMin() == null, "sizes after union");
        check(a.findMin().getKey() == 2, "min after union");
        valid(a);
        int prev = -1;
        while (!a.isEmpty()) { int k = a.extractMin().getKey(); check(k > prev, "order"); prev = k; }
    }

    static void deleteNodes() {
        FibonacciHeap<Integer> h = new FibonacciHeap<>();
        List<FibNode<Integer>> nodes = new ArrayList<>();
        for (int i = 0; i < 500; i++) nodes.add(h.insert(i, i));
        h.extractMin();
        for (int i = 1; i < 500; i += 3) { h.delete(nodes.get(i)); valid(h); }
        int prev = -1;
        while (!h.isEmpty()) {
            int k = h.extractMin().getKey();
            check(k % 3 != 1, "deleted key reappeared: " + k);
            check(k > prev, "order");
            prev = k;
        }
    }

    static void degreeBound() {
        FibonacciHeap<Integer> h = new FibonacciHeap<>();
        Random r = new Random(9);
        List<FibNode<Integer>> live = new ArrayList<>();
        for (int i = 0; i < 50_000; i++) live.add(h.insert(r.nextInt(1_000_000), i));
        for (int round = 0; round < 20_000; round++) {
            if (r.nextInt(4) == 0) {
                FibNode<Integer> x = h.extractMin();
                live.remove(x);
            } else {
                FibNode<Integer> x = live.get(r.nextInt(live.size()));
                if (x.getKey() > 0) h.decreaseKey(x, r.nextInt(x.getKey()));
            }
        }
        check(h.maxDegreePresent() <= FibonacciHeap.maxDegreeBound(h.size()) + 1,
                "max degree " + h.maxDegreePresent() + " vs bound " + FibonacciHeap.maxDegreeBound(h.size()));
        valid(h);
    }

    /** Compare against a TreeSet of (key, seq) - the reference answer. */
    static void randomised() {
        Random r = new Random(42);
        FibonacciHeap<Long> h = new FibonacciHeap<>();
        FibonacciHeap<Long> other = new FibonacciHeap<>();
        TreeSet<long[]> ref = new TreeSet<>((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        List<FibNode<Long>> handles = new ArrayList<>();
        List<FibNode<Long>> otherHandles = new ArrayList<>();
        for (int op = 0; op < 200_000; op++) {
            int c = r.nextInt(100);
            if (c < 35) {
                FibNode<Long> x = h.insert(r.nextInt(10_000), (long) op);
                handles.add(x);
                ref.add(new long[]{x.getKey(), x.getSeq()});
            } else if (c < 40) {
                FibNode<Long> x = other.insert(r.nextInt(10_000), (long) op);
                otherHandles.add(x);
            } else if (c < 42) {
                for (FibNode<Long> x : otherHandles) ref.add(new long[]{x.getKey(), x.getSeq()});
                handles.addAll(otherHandles);
                otherHandles.clear();
                h.union(other);
            } else if (c < 60) {
                FibNode<Long> x = h.extractMin();
                long[] e = ref.pollFirst();
                if (x == null) check(e == null, "both empty");
                else check(e != null && e[0] == x.getKey() && e[1] == x.getSeq(), "extract matches reference");
            } else if (c < 95) {
                if (handles.isEmpty()) continue;
                FibNode<Long> x = handles.get(r.nextInt(handles.size()));
                if (!x.isInHeap() || x.getKey() == 0) continue;
                ref.remove(new long[]{x.getKey(), x.getSeq()});
                h.decreaseKey(x, r.nextInt(x.getKey()));
                ref.add(new long[]{x.getKey(), x.getSeq()});
            } else {
                if (handles.isEmpty()) continue;
                FibNode<Long> x = handles.get(r.nextInt(handles.size()));
                if (!x.isInHeap()) continue;
                ref.remove(new long[]{x.getKey(), x.getSeq()});
                h.delete(x);
            }
            if (op % 5_000 == 0) { handles.removeIf(n -> !n.isInHeap()); valid(h); }
            check(h.size() == ref.size(), "size matches reference");
            if (!ref.isEmpty()) check(h.findMin().getKey() == ref.first()[0] && h.findMin().getSeq() == ref.first()[1], "min matches");
        }
        valid(h);
    }

    static void controlRoomEdges() {
        ControlRoom cr = new ControlRoom();
        check(!cr.serve("Puri").ok, "serve unknown district");
        check(!cr.serveGlobal().ok, "serve global when empty");
        cr.addDistrict("Puri");
        check(!cr.serve("Puri").ok, "serve empty district");
        check(!cr.addRequest("", RequestType.FOOD, 3).ok, "blank district");
        check(!cr.addRequest("Puri", null, 3).ok, "null type");
        check(!cr.addRequest("Puri", RequestType.FOOD, 0).ok, "urgency 0");
        check(!cr.addRequest("Puri", RequestType.FOOD, 11).ok, "urgency 11");
        OpResult a = cr.addRequest("puri", RequestType.RESCUE, 4);
        check(a.ok && a.district.equals("Puri"), "case-insensitive district");
        String id = a.request.id();
        check(!cr.raiseUrgency(id, 4).ok, "raise to same value rejected");
        check(!cr.raiseUrgency(id, 7).ok, "raise to larger value rejected");
        check(!cr.raiseUrgency("R-999", 1).ok, "unknown id rejected");
        check(!cr.raiseUrgency(id, 0).ok, "out-of-range urgency rejected");
        check(cr.raiseUrgency(id.substring(2), 2).ok, "numeric id accepted");
        check(!cr.mergeDistricts("Puri", "Puri").ok, "self merge rejected");
        check(!cr.mergeDistricts("Puri", "Nowhere").ok, "unknown merge target");
        check(cr.serve("Puri").ok, "serve single-node heap");
        check(!cr.serve("Puri").ok, "then empty again");
        check(!cr.cancel(id).ok, "cancel after serve rejected");
        check(!cr.raiseUrgency(id, 1).ok, "raise after serve rejected");
    }

    static void controlRoomMerge() {
        ControlRoom cr = new ControlRoom();
        cr.addDistrict("Khordha");
        cr.addDistrict("Ganjam");
        String p1 = cr.addRequest("Puri", RequestType.RESCUE, 6).request.id();
        cr.addRequest("Puri", RequestType.FOOD, 9);
        cr.addRequest("Khordha", RequestType.MEDICINE, 3);
        check(cr.mergeDistricts("Ganjam", "Khordha").ok, "merge empty district");
        check(cr.mergeDistricts("Puri", "Khordha").ok, "merge populated district");
        check(!cr.mergeDistricts("Puri", "Khordha").ok, "repeat merge rejected");
        check(cr.heapOf("Khordha").size() == 3, "all moved");
        check(cr.resolve("Puri").equals("Khordha"), "alias resolves");
        check(cr.raiseUrgency(p1, 1).ok, "raise a moved request");
        check(cr.peek("Khordha").getValue().id().equals(p1), "raised request is now most urgent");
        OpResult late = cr.addRequest("Puri", RequestType.BOAT, 2);
        check(late.ok && late.district.equals("Khordha"), "new Puri request routed to Khordha");
        check(!cr.addDistrict("Puri").ok, "merged district cannot be re-opened");
        check(cr.cancel(late.request.id()).ok, "cancel works after merge");
        check(cr.serveGlobal().request.id().equals(p1), "global serve finds most urgent");
        check(cr.heapOf("Khordha").validate() == null, "heap still valid");
    }

    static void replay() {
        Dataset d = DatasetGenerator.fullScenario();
        ImpactSimulation s = new ImpactSimulation(d);
        s.runAll();
        check(s.priority.served == d.arrivals.size() && s.fifo.served == d.arrivals.size(), "everything served");
        check(s.priority.avgCriticalWait() < s.fifo.avgCriticalWait(), "critical wait improves");
        check(s.priority.criticalOnTimePct() > s.fifo.criticalOnTimePct(), "on-time improves");
        System.out.printf("      replay: %d requests | critical avg wait %.1f vs %.1f ticks | on time %.0f%% vs %.0f%%%n",
                d.arrivals.size(), s.priority.avgCriticalWait(), s.fifo.avgCriticalWait(),
                s.priority.criticalOnTimePct(), s.fifo.criticalOnTimePct());
    }

    // ------------------------------------------------------------ tiny harness

    interface Body { void run(); }

    static void test(String name, Body b) {
        try {
            b.run();
            passed++;
            System.out.println("  PASS  " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("  FAIL  " + name + "  ->  " + t);
        }
    }

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
    }

    static void valid(FibonacciHeap<?> h) {
        String err = h.validate();
        if (err != null) throw new AssertionError("invariant broken: " + err);
    }

    static void expect(Class<? extends Throwable> type, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return;
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }
}
