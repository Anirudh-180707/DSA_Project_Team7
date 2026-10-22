package relief.bench;

import java.util.Arrays;

/**
 * The alternative we compare against: an ARRAY-based binary min-heap with a
 * position index in every handle so decrease-key can find the element in O(1)
 * and then sift it up in O(log n). Merging two binary heaps has no shortcut:
 * copy both arrays and rebuild (bottom-up heapify), which is O(n + m).
 */
public final class BinaryHeapBaseline {

    public static final class Handle {
        int key;
        final long seq;
        int pos;               // index in the array, -1 once removed
        Handle(int key, long seq) { this.key = key; this.seq = seq; }
        public int key() { return key; }
    }

    private static long globalSeq = 0;
    private Handle[] a = new Handle[16];
    private int n = 0;
    public long work = 0;       // element moves / swaps, to compare with the Fibonacci heap

    public Handle insert(int key) {
        if (n == a.length) a = Arrays.copyOf(a, n * 2);
        Handle h = new Handle(key, globalSeq++);
        a[n] = h;
        h.pos = n++;
        siftUp(h.pos);
        return h;
    }

    public Handle extractMin() {
        if (n == 0) return null;
        Handle top = a[0];
        a[0] = a[--n];
        a[0].pos = 0;
        a[n] = null;
        if (n > 0) siftDown(0);
        top.pos = -1;
        return top;
    }

    public void decreaseKey(Handle h, int k) {
        if (h.pos < 0 || k >= h.key) throw new IllegalArgumentException("bad decrease-key");
        h.key = k;
        siftUp(h.pos);
    }

    /**
     * O(n + m) for two large heaps: there is no structural shortcut for array heaps.
     * To be fair to the baseline, a SMALL heap is merged by re-inserting its elements
     * (O(m log(n+m))) whenever that is cheaper than rebuilding.
     */
    public void union(BinaryHeapBaseline o) {
        double log = Math.log(Math.max(2, n + o.n)) / Math.log(2);
        if (o.n * log < n + o.n) {
            for (int i = 0; i < o.n; i++) {
                Handle h = o.a[i];
                if (n == a.length) a = Arrays.copyOf(a, n * 2);
                a[n] = h;
                h.pos = n++;
                work++;
                siftUp(h.pos);
            }
            o.a = new Handle[16];
            o.n = 0;
            return;
        }
        Handle[] b = Arrays.copyOf(a, Math.max(16, n + o.n));
        System.arraycopy(o.a, 0, b, n, o.n);
        work += o.n;                                     // every element of o is copied
        a = b;
        n += o.n;
        for (int i = 0; i < n; i++) a[i].pos = i;
        work += n;
        for (int i = n / 2 - 1; i >= 0; i--) siftDown(i);   // bottom-up heapify, O(n)
        o.a = new Handle[16];
        o.n = 0;
    }

    public int size() { return n; }

    private boolean less(Handle x, Handle y) { return x.key < y.key || (x.key == y.key && x.seq < y.seq); }

    private void siftUp(int i) {
        while (i > 0) {
            int p = (i - 1) / 2;
            if (!less(a[i], a[p])) break;
            swap(i, p);
            i = p;
        }
    }

    private void siftDown(int i) {
        while (true) {
            int l = 2 * i + 1, r = l + 1, m = i;
            if (l < n && less(a[l], a[m])) m = l;
            if (r < n && less(a[r], a[m])) m = r;
            if (m == i) return;
            swap(i, m);
            i = m;
        }
    }

    private void swap(int i, int j) {
        Handle t = a[i]; a[i] = a[j]; a[j] = t;
        a[i].pos = i; a[j].pos = j;
        work++;
    }
}
