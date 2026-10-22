package relief.heap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Fibonacci heap (Fredman & Tarjan, JACM 1987) - a MIN-heap on "urgency":
 * the smallest key is the most urgent request.
 *
 * Structure: a collection of heap-ordered trees whose roots are kept in a circular
 * doubly linked ROOT LIST, plus a pointer {@code min} to the most urgent root.
 *
 * Cost summary (n = number of nodes):
 *   insert ............ O(1)  actual
 *   findMin ........... O(1)  actual
 *   union (merge) ..... O(1)  actual   (splice two circular lists)
 *   decreaseKey ....... O(1)  amortised (cut + cascading cut)
 *   extractMin ........ O(log n) amortised (consolidation)
 *   delete ............ O(log n) amortised (= cut to root + extractMin)
 *
 * Potential function used for the amortised analysis:
 *   PHI(H) = t(H) + 2 * m(H)   where t = number of roots, m = number of marked nodes.
 * We maintain t and m incrementally so the UI can show PHI live in O(1).
 *
 * File ownership for the team (see docs/TEAM_GITHUB_PLAN.md):
 *   SECTION A (insert, union, extractMin, consolidate, link)    -> Member 1
 *   SECTION B (decreaseKey, cut, cascadingCut, delete, validate) -> Member 2
 */
public final class FibonacciHeap<T> {

    /** 1 / log(phi), used for the degree bound D(n) <= floor(log_phi n). */
    private static final double INV_LOG_PHI = 1.0 / Math.log((1.0 + Math.sqrt(5.0)) / 2.0);

    /** Global arrival counter so FIFO tie-breaking survives merges between heaps. */
    private static long globalSeq = 0;

    private FibNode<T> min;      // most urgent root (null when empty)
    private int n;               // number of nodes
    private int rootCount;       // t(H)
    private int markedCount;     // m(H)

    private final HeapStats stats;
    private StepListener<T> listener;

    public FibonacciHeap() { this(new HeapStats()); }

    public FibonacciHeap(HeapStats sharedStats) { this.stats = sharedStats; }

    // =====================================================================
    // SECTION A  -  insert, findMin, union, extractMin, consolidate, link
    // =====================================================================

    /** O(1): make a one-node tree and splice it into the root list next to min. */
    public FibNode<T> insert(int key, T value) {
        FibNode<T> x = new FibNode<>(key, globalSeq++, value);
        if (min == null) {
            min = x;                       // x is already a circular list of size 1
        } else {
            concat(min, x);                // put x into the root list
            if (FibNode.less(x, min)) min = x;
        }
        n++;
        rootCount++;
        stats.inserts++;
        fire(StepListener.Step.INSERT, x, null);
        return x;
    }

    /** O(1): the min pointer is always maintained. */
    public FibNode<T> findMin() { return min; }

    /**
     * O(1): destructive merge. All nodes of {@code other} move into this heap and
     * {@code other} becomes empty. We only splice the two circular root lists
     * (4 pointer changes) and compare the two minimums - no tree is touched.
     */
    public void union(FibonacciHeap<T> other) {
        if (other == null || other == this || other.min == null) {
            stats.unions++;
            return;                        // merging an empty heap: nothing to move
        }
        if (min == null) {
            min = other.min;
        } else {
            concat(min, other.min);
            if (FibNode.less(other.min, min)) min = other.min;
        }
        n += other.n;
        rootCount += other.rootCount;
        markedCount += other.markedCount;
        other.min = null;
        other.n = other.rootCount = other.markedCount = 0;
        stats.unions++;
        fire(StepListener.Step.MERGE, min, null);
    }

    /**
     * O(log n) amortised.
     * 1. Move every child of the min to the root list (O(degree) = O(log n)).
     * 2. Remove the min.
     * 3. Consolidate: link roots of equal degree until all root degrees differ.
     */
    public FibNode<T> extractMin() {
        FibNode<T> z = min;
        if (z == null) return null;        // empty heap - caller shows a message

        // 1. promote children
        if (z.child != null) {
            FibNode<T> c = z.child;
            FibNode<T> x = c;
            do {
                x.parent = null;
                if (x.mark) { x.mark = false; markedCount--; }   // roots are never marked
                x = x.right;
            } while (x != c);
            concat(z, c);                  // splice the whole child list in O(1)
            rootCount += z.degree;
            z.child = null;
            z.degree = 0;
            fire(StepListener.Step.PROMOTE_CHILDREN, z, null);
        }

        // 2. remove z from the root list
        boolean wasOnlyRoot = (z.right == z);
        FibNode<T> next = z.right;
        z.left.right = z.right;
        z.right.left = z.left;
        z.left = z.right = z;
        z.inHeap = false;
        n--;
        rootCount--;
        stats.extractMins++;
        fire(StepListener.Step.REMOVE_MIN, z, null);

        // 3. consolidate
        if (wasOnlyRoot) {
            min = null;
        } else {
            min = next;                    // temporary; consolidate() finds the real min
            consolidate();
        }
        return z;
    }

    /**
     * Link roots of equal degree. Uses a "degree table" A[0..D(n)] where
     * D(n) = floor(log_phi n) is the maximum possible degree (Fibonacci bound).
     * Every link removes one root, so the work is O(t + D(n)) actual and O(log n)
     * amortised (the t part is paid for by the drop in potential).
     */
    private void consolidate() {
        FibNode<T>[] table = newTable(maxDegreeBound(n) + 2);

        // Snapshot the current roots: linking changes the root list while we walk it.
        FibNode<T>[] roots = newTable(rootCount);
        int k = 0;
        FibNode<T> w = min;
        do { roots[k++] = w; w = w.right; } while (w != min && k < roots.length);
        stats.rootsScanned += k;

        for (int i = 0; i < k; i++) {
            FibNode<T> x = roots[i];
            int d = x.degree;
            while (true) {
                if (d >= table.length) table = Arrays.copyOf(table, d + 4); // defensive only
                FibNode<T> y = table[d];
                if (y == null) break;
                if (FibNode.less(y, x)) { FibNode<T> tmp = x; x = y; y = tmp; }
                link(y, x);                // y becomes a child of x
                table[d] = null;
                d++;
            }
            table[d] = x;
        }

        // Rebuild min from the table (at most D(n)+1 entries).
        min = null;
        for (FibNode<T> r : table) {
            if (r != null && (min == null || FibNode.less(r, min))) min = r;
        }
        fire(StepListener.Step.CONSOLIDATED, min, null);
    }

    /** O(1): remove root y from the root list and make it a child of root x. */
    private void link(FibNode<T> y, FibNode<T> x) {
        y.left.right = y.right;            // unlink y from the root list
        y.right.left = y.left;
        rootCount--;

        y.parent = x;
        if (x.child == null) {
            x.child = y;
            y.left = y.right = y;
        } else {                           // insert y into x's child list
            FibNode<T> c = x.child;
            y.right = c;
            y.left = c.left;
            c.left.right = y;
            c.left = y;
        }
        x.degree++;
        if (y.mark) { y.mark = false; markedCount--; }  // fresh child starts unmarked
        stats.links++;
        fire(StepListener.Step.LINK, y, x);
    }

    // =====================================================================
    // SECTION B  -  decreaseKey ("raise urgency"), cut, cascadingCut, delete
    // =====================================================================

    /**
     * O(1) amortised. "Raise urgency" = lower the key.
     * If heap order with the parent breaks, cut x to the root list and run a
     * cascading cut up the ancestors.
     *
     * @throws IllegalArgumentException if newKey is not strictly smaller (more urgent)
     * @throws IllegalStateException    if the node was already served / removed
     */
    public void decreaseKey(FibNode<T> x, int newKey) {
        if (x == null || !x.inHeap)
            throw new IllegalStateException("request is no longer pending");
        if (newKey >= x.key)
            throw new IllegalArgumentException(
                "new urgency " + newKey + " is not more urgent than current " + x.key);

        x.key = newKey;
        stats.decreaseKeys++;
        fire(StepListener.Step.DECREASE, x, x.parent);

        FibNode<T> y = x.parent;
        if (y != null && FibNode.less(x, y)) {
            cut(x, y);
            cascadingCut(y);
        }
        if (FibNode.less(x, min)) min = x;
    }

    /** O(1): detach x from its parent y and add it to the root list (unmarked). */
    private void cut(FibNode<T> x, FibNode<T> y) {
        if (x.right == x) {
            y.child = null;
        } else {
            x.left.right = x.right;
            x.right.left = x.left;
            if (y.child == x) y.child = x.right;
        }
        y.degree--;
        x.left = x.right = x;
        concat(min, x);
        rootCount++;
        x.parent = null;
        if (x.mark) { x.mark = false; markedCount--; }
        stats.cuts++;
        fire(StepListener.Step.CUT, x, y);
    }

    /**
     * Walk up from y: an unmarked non-root gets marked and we stop; a marked node
     * (it has now lost a SECOND child) is cut too and we continue with its parent.
     * Written iteratively so very deep chains cannot overflow the Java stack.
     */
    private void cascadingCut(FibNode<T> y) {
        FibNode<T> z = y.parent;
        while (z != null) {
            if (!y.mark) {
                y.mark = true;
                markedCount++;
                fire(StepListener.Step.MARK, y, null);
                return;
            }
            stats.cascadingCuts++;
            fire(StepListener.Step.CASCADING_CUT, y, z);
            cut(y, z);
            y = z;
            z = y.parent;
        }
    }

    /**
     * O(log n) amortised: cancel a request. Equivalent to "decrease to minus
     * infinity, then extractMin", implemented without a sentinel key.
     */
    public void delete(FibNode<T> x) {
        if (x == null || !x.inHeap)
            throw new IllegalStateException("request is no longer pending");
        fire(StepListener.Step.DELETE, x, x.parent);
        FibNode<T> y = x.parent;
        if (y != null) {
            cut(x, y);
            cascadingCut(y);
        }
        min = x;                           // force x to be extracted next
        stats.deletes++;
        stats.extractMins--;               // count it as a delete, not a serve
        extractMin();
    }

    // =====================================================================
    // Helpers, queries and invariant checker
    // =====================================================================

    /** O(1): splice circular list starting at b into the circular list containing a. */
    private static <T> void concat(FibNode<T> a, FibNode<T> b) {
        FibNode<T> aRight = a.right;
        FibNode<T> bLeft = b.left;
        a.right = b;
        b.left = a;
        bLeft.right = aRight;
        aRight.left = bLeft;
    }

    /** D(n) = floor(log_phi n): no node in an n-node Fibonacci heap has larger degree. */
    public static int maxDegreeBound(int n) {
        if (n <= 1) return 1;
        return (int) Math.floor(Math.log(n) * INV_LOG_PHI);
    }

    @SuppressWarnings("unchecked")
    private static <T> FibNode<T>[] newTable(int size) {
        return (FibNode<T>[]) new FibNode[Math.max(size, 1)];
    }

    private void fire(StepListener.Step s, FibNode<T> a, FibNode<T> b) {
        if (listener != null) listener.onStep(this, s, a, b);
    }

    public void setListener(StepListener<T> l) { this.listener = l; }
    public StepListener<T> getListener()      { return listener; }
    public boolean isEmpty()                  { return n == 0; }
    public int size()                         { return n; }
    public int rootCount()                    { return rootCount; }
    public int markedCount()                  { return markedCount; }
    /** PHI(H) = t(H) + 2 m(H) */
    public int potential()                    { return rootCount + 2 * markedCount; }
    public HeapStats stats()                  { return stats; }

    /** Roots in root-list order starting from min (O(t)); for UI / printing. */
    public List<FibNode<T>> roots() {
        List<FibNode<T>> out = new ArrayList<>();
        if (min == null) return out;
        FibNode<T> x = min;
        do { out.add(x); x = x.right; } while (x != min);
        return out;
    }

    /** Largest degree currently present (O(n)); UI only. */
    public int maxDegreePresent() {
        int best = 0;
        for (FibNode<T> x : allNodes()) best = Math.max(best, x.degree);
        return best;
    }

    /** Every node, iteratively (no recursion, so deep trees are safe). */
    public List<FibNode<T>> allNodes() {
        List<FibNode<T>> out = new ArrayList<>(n);
        Deque<FibNode<T>> stack = new ArrayDeque<>();
        for (FibNode<T> r : roots()) stack.push(r);
        while (!stack.isEmpty()) {
            FibNode<T> x = stack.pop();
            out.add(x);
            if (x.child != null) {
                FibNode<T> c = x.child;
                do { stack.push(c); c = c.right; } while (c != x.child);
            }
        }
        return out;
    }

    /**
     * Full structural check used by the test-suite. Returns null if the heap is
     * valid, otherwise a description of the first violated invariant.
     */
    public String validate() {
        if (min == null) {
            return (n == 0 && rootCount == 0 && markedCount == 0) ? null
                    : "empty min but n=" + n + " t=" + rootCount + " m=" + markedCount;
        }
        int count = 0, roots = 0, marked = 0;
        Deque<FibNode<T>> stack = new ArrayDeque<>();
        FibNode<T> r = min;
        do {
            if (r.right.left != r) return "root list broken at " + r;
            if (r.parent != null) return "root has parent: " + r;
            if (r.mark) return "root is marked: " + r;
            if (FibNode.less(r, min)) return "min pointer wrong: " + r + " < " + min;
            roots++;
            stack.push(r);
            r = r.right;
            if (roots > n) return "root list does not cycle back to min";
        } while (r != min);

        int bound = maxDegreeBound(Math.max(n, 1)) + 1;
        while (!stack.isEmpty()) {
            FibNode<T> x = stack.pop();
            count++;
            if (!x.inHeap) return "node flagged as removed but still linked: " + x;
            if (x.mark) marked++;
            if (x.degree > bound) return "degree bound violated at " + x;
            int kids = 0;
            if (x.child != null) {
                FibNode<T> c = x.child;
                do {
                    if (c.parent != x) return "bad parent pointer at " + c;
                    if (c.right.left != c) return "child list broken at " + c;
                    if (FibNode.less(c, x)) return "heap order violated: " + c + " under " + x;
                    kids++;
                    stack.push(c);
                    c = c.right;
                    if (kids > n) return "child list does not cycle";
                } while (c != x.child);
            }
            if (kids != x.degree) return "degree mismatch at " + x + ": " + kids + " vs " + x.degree;
        }
        if (count != n) return "size mismatch: counted " + count + " but n=" + n;
        if (roots != rootCount) return "root count mismatch: " + roots + " vs " + rootCount;
        if (marked != markedCount) return "marked count mismatch: " + marked + " vs " + markedCount;
        return null;
    }
}
