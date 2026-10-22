package relief.bench;

import relief.heap.FibNode;
import relief.heap.FibonacciHeap;
import relief.heap.HeapStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Empirical support for the complexity claims. Two experiments:
 *
 *  1. MERGE: merge two heaps of size n. Fibonacci heap = O(1) (constant time for
 *     every n); array binary heap = O(n) (time grows linearly).
 *
 *  2. CONTROL-ROOM WORKLOAD: many raises (decrease-key) and merges, few removals.
 *     We report wall-clock time AND structural work (binary: swaps/moves;
 *     Fibonacci: links + cuts + roots scanned).
 *
 * Wall-clock numbers depend on the machine and on JIT warm-up; the structural
 * work counts do not. Say this honestly in the viva.
 */
public final class Benchmark {

    public static void main(String[] args) {
        System.out.println("=== Experiment 1: merge two heaps of n requests each ===");
        System.out.printf("%10s %18s %18s %16s%n", "n", "Fibonacci (us)", "Binary heap (us)", "binary moves");
        for (int n = 1_000; n <= 1_000_000; n *= 10) mergeOnce(n, true);   // warm-up pass, not printed
        for (int n = 1_000; n <= 1_000_000; n *= 10) mergeOnce(n, false);

        System.out.println();
        System.out.println("=== Experiment 2: work per raise when a request becomes the MOST urgent (worst case) ===");
        System.out.printf("%10s %22s %24s %14s%n", "n", "Fib cuts per raise", "Binary swaps per raise", "log2 n");
        for (int n = 1_000; n <= 1_000_000; n *= 10) raises(n);

        System.out.println();
        System.out.println("=== Experiment 3: mixed control-room workload (wall clock) ===");
        System.out.println("k districts, n inserts, 4n raises, k-1 district merges, n/4 serves");
        System.out.printf("%10s %14s %14s %18s %18s%n", "n", "Fib (ms)", "Binary (ms)", "Fib work", "Binary work");
        for (int n : new int[]{10_000, 100_000, 400_000}) workload(n, true);
        for (int n : new int[]{10_000, 100_000, 400_000}) workload(n, false);
    }

    private static void mergeOnce(int n, boolean warm) {
        Random r = new Random(n);
        FibonacciHeap<Integer> f1 = new FibonacciHeap<>(), f2 = new FibonacciHeap<>();
        BinaryHeapBaseline b1 = new BinaryHeapBaseline(), b2 = new BinaryHeapBaseline();
        for (int i = 0; i < n; i++) {
            int k1 = r.nextInt(1_000_000), k2 = r.nextInt(1_000_000);
            f1.insert(k1, i); f2.insert(k2, i);
            b1.insert(k1); b2.insert(k2);
        }
        b1.work = 0;
        long t0 = System.nanoTime();
        f1.union(f2);
        long t1 = System.nanoTime();
        b1.union(b2);
        long t2 = System.nanoTime();
        if (!warm) System.out.printf("%,10d %18.2f %18.2f %,16d%n", n, (t1 - t0) / 1e3, (t2 - t1) / 1e3, b1.work);
    }

    private static void raises(int n) {
        Random r = new Random(3);
        FibonacciHeap<Integer> f = new FibonacciHeap<>();
        BinaryHeapBaseline b = new BinaryHeapBaseline();
        List<FibNode<Integer>> fn = new ArrayList<>(n);
        List<BinaryHeapBaseline.Handle> bn = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int k = 1_000_000_000 - r.nextInt(1_000_000);
            fn.add(f.insert(k, i));
            bn.add(b.insert(k));
        }
        f.extractMin();                    // consolidate into real trees
        b.extractMin();
        b.work = 0;
        long cuts0 = f.stats().cuts;
        int raises = 0;
        for (int i = 0; i < n; i++) {
            FibNode<Integer> x = fn.get(i);
            BinaryHeapBaseline.Handle h = bn.get(i);
            if (!x.isInHeap() || h.pos < 0) continue;
            int nk = -(i + 1);                       // becomes more urgent than everything else
            f.decreaseKey(x, nk);
            b.decreaseKey(h, nk);
            raises++;
        }
        System.out.printf("%,10d %22.2f %24.2f %14.1f%n", n, (double) (f.stats().cuts - cuts0) / raises,
                (double) b.work / raises, Math.log(n) / Math.log(2));
    }

    private static void workload(int n, boolean warm) {
        int k = 8;
        Random r = new Random(7);
        // ---- Fibonacci
        HeapStats st = new HeapStats();
        List<FibonacciHeap<Integer>> fh = new ArrayList<>();
        List<List<FibNode<Integer>>> fn = new ArrayList<>();
        for (int i = 0; i < k; i++) { fh.add(new FibonacciHeap<>(st)); fn.add(new ArrayList<>()); }
        int[] keys = new int[n], dist = new int[n];
        for (int i = 0; i < n; i++) { keys[i] = 1_000_000 + r.nextInt(1_000_000); dist[i] = r.nextInt(k); }
        int[] raiseIdx = new int[4 * n];
        for (int i = 0; i < raiseIdx.length; i++) raiseIdx[i] = r.nextInt(n);

        long t0 = System.nanoTime();
        List<FibNode<Integer>> all = new ArrayList<>(n);
        for (int i = 0; i < n; i++) all.add(fh.get(dist[i]).insert(keys[i], i));
        for (int i = 0; i < raiseIdx.length / 2; i++) {
            FibNode<Integer> x = all.get(raiseIdx[i]);
            if (x.isInHeap() && x.getKey() > 0) fh.get(dist[raiseIdx[i]]).decreaseKey(x, x.getKey() - 1);
        }
        for (int i = 0; i < n / 8; i++) fh.get(i % k).extractMin();
        // merge every district into district 0
        for (int i = 1; i < k; i++) fh.get(0).union(fh.get(i));
        for (int i = raiseIdx.length / 2; i < raiseIdx.length; i++) {
            FibNode<Integer> x = all.get(raiseIdx[i]);
            if (x.isInHeap() && x.getKey() > 0) fh.get(0).decreaseKey(x, x.getKey() - 1);
        }
        for (int i = 0; i < n / 8; i++) fh.get(0).extractMin();
        long fibNs = System.nanoTime() - t0;
        long fibWork = st.links + st.cuts + st.rootsScanned + st.inserts + st.unions;

        // ---- binary
        r = new Random(7);
        List<BinaryHeapBaseline> bh = new ArrayList<>();
        for (int i = 0; i < k; i++) bh.add(new BinaryHeapBaseline());
        long t1 = System.nanoTime();
        List<BinaryHeapBaseline.Handle> hs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) hs.add(bh.get(dist[i]).insert(keys[i]));
        for (int i = 0; i < raiseIdx.length / 2; i++) {
            BinaryHeapBaseline.Handle h = hs.get(raiseIdx[i]);
            if (h.pos >= 0 && h.key > 0) bh.get(dist[raiseIdx[i]]).decreaseKey(h, h.key - 1);
        }
        for (int i = 0; i < n / 8; i++) bh.get(i % k).extractMin();
        for (int i = 1; i < k; i++) bh.get(0).union(bh.get(i));
        for (int i = raiseIdx.length / 2; i < raiseIdx.length; i++) {
            BinaryHeapBaseline.Handle h = hs.get(raiseIdx[i]);
            if (h.pos >= 0 && h.key > 0) bh.get(0).decreaseKey(h, h.key - 1);
        }
        for (int i = 0; i < n / 8; i++) bh.get(0).extractMin();
        long binNs = System.nanoTime() - t1;
        long binWork = 0;
        for (BinaryHeapBaseline b : bh) binWork += b.work;

        if (!warm)
            System.out.printf("%,10d %14.1f %14.1f %,18d %,18d%n", n, fibNs / 1e6, binNs / 1e6, fibWork, binWork);
    }
}
