package relief.heap;

import java.util.ArrayList;
import java.util.List;

/**
 * One node of the Fibonacci heap.
 *
 * Every node sits in a CIRCULAR DOUBLY LINKED LIST of its siblings (left/right).
 * The roots form the "root list"; each node's children form a child list that
 * the parent reaches through the single {@code child} pointer.
 *
 * Why these fields (implementation-strategy talking points):
 *  - left/right : O(1) unlink and O(1) splice of whole lists (merge, promote children).
 *  - parent     : needed by decrease-key to know whether heap order broke, and by cut.
 *  - child      : one pointer is enough because siblings are reachable via left/right.
 *  - degree     : number of children; consolidation links roots of EQUAL degree.
 *  - mark       : "has this node already lost one child since it became a child?"
 *                 Drives the cascading cut that keeps trees bushy (degree stays O(log n)).
 *  - seq        : arrival sequence number, breaks ties between equal urgencies so that
 *                 two requests with the same urgency are served in arrival (FIFO) order.
 */
public final class FibNode<T> {

    int key;              // urgency: SMALLER = MORE URGENT (1 = critical)
    final long seq;       // global arrival order, tie-breaker
    final T value;        // payload (the relief request)

    FibNode<T> parent;
    FibNode<T> child;
    FibNode<T> left;
    FibNode<T> right;

    int degree;
    boolean mark;
    boolean inHeap;       // false once extracted/deleted (stale handles are rejected)

    FibNode(int key, long seq, T value) {
        this.key = key;
        this.seq = seq;
        this.value = value;
        this.left = this;   // a single node is a circular list of length 1
        this.right = this;
        this.inHeap = true;
    }

    /** Heap order: smaller key first; equal keys -> earlier arrival first. */
    static <T> boolean less(FibNode<T> a, FibNode<T> b) {
        return a.key < b.key || (a.key == b.key && a.seq < b.seq);
    }

    // ---------- read-only accessors (used by the UI, CLI and tests) ----------
    public int getKey()             { return key; }
    public long getSeq()            { return seq; }
    public T getValue()             { return value; }
    public int getDegree()          { return degree; }
    public boolean isMarked()       { return mark; }
    public boolean isInHeap()       { return inHeap; }
    public FibNode<T> getParent()   { return parent; }

    /** Children in list order (O(degree)); only used for drawing / printing. */
    public List<FibNode<T>> children() {
        List<FibNode<T>> out = new ArrayList<>(degree);
        if (child == null) return out;
        FibNode<T> x = child;
        do { out.add(x); x = x.right; } while (x != child);
        return out;
    }

    @Override
    public String toString() {
        return value + "(" + key + (mark ? ",marked" : "") + ")";
    }
}
