package relief.ui;

import relief.heap.FibNode;
import relief.heap.FibonacciHeap;
import relief.model.ReliefRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable picture of a heap at one instant. The visualiser animates a list of
 * these, so the drawing never touches live heap pointers (heap logic and display
 * stay separate: if the UI breaks, the heap is unaffected).
 */
final class ForestSnapshot {

    /** Above this many nodes we draw a compact summary instead of every tree. */
    static final int FULL_VIEW_LIMIT = 60;

    enum Highlight { NONE, NEW, LINK, CUT, CASCADE, MARK, REMOVED, MERGE }

    static final class Node {
        final String id;
        final int key;
        final boolean marked;
        final int degree;
        final String type;
        final List<Node> kids = new ArrayList<>();

        Node(FibNode<ReliefRequest> x) {
            id = x.getValue().id();
            key = x.getKey();
            marked = x.isMarked();
            degree = x.getDegree();
            type = x.getValue().type().label();
        }
    }

    final String district;
    final String caption;
    final List<Node> roots;          // empty when compact
    final List<Integer> rootDegrees; // always filled
    final String minId;
    final int size, rootCount, markedCount, maxDegree, degreeBound;
    final boolean compact;
    final Set<String> highlighted;
    final Highlight kind;

    private ForestSnapshot(String district, String caption, List<Node> roots, List<Integer> rootDegrees,
                           String minId, int size, int rootCount, int markedCount, int maxDegree,
                           boolean compact, Set<String> hl, Highlight kind) {
        this.district = district;
        this.caption = caption;
        this.roots = roots;
        this.rootDegrees = rootDegrees;
        this.minId = minId;
        this.size = size;
        this.rootCount = rootCount;
        this.markedCount = markedCount;
        this.maxDegree = maxDegree;
        this.degreeBound = FibonacciHeap.maxDegreeBound(size);
        this.compact = compact;
        this.highlighted = hl;
        this.kind = kind;
    }

    static ForestSnapshot empty(String caption) {
        return new ForestSnapshot("-", caption, Collections.emptyList(), Collections.emptyList(),
                null, 0, 0, 0, 0, false, Collections.emptySet(), Highlight.NONE);
    }

    static ForestSnapshot of(String district, FibonacciHeap<ReliefRequest> h, String caption,
                             Highlight kind, String... hlIds) {
        Set<String> hl = new HashSet<>();
        for (String s : hlIds) if (s != null) hl.add(s);
        if (h == null) {
            return new ForestSnapshot(district, caption, Collections.emptyList(), Collections.emptyList(),
                    null, 0, 0, 0, 0, false, hl, kind);
        }
        List<FibNode<ReliefRequest>> liveRoots = h.roots();
        List<Integer> degs = new ArrayList<>();
        for (FibNode<ReliefRequest> r : liveRoots) degs.add(r.getDegree());
        boolean compact = h.size() > FULL_VIEW_LIMIT;
        List<Node> roots = new ArrayList<>();
        int maxDeg = 0;
        if (!compact) {
            for (FibNode<ReliefRequest> r : liveRoots) roots.add(copy(r));
            for (FibNode<ReliefRequest> x : h.allNodes()) maxDeg = Math.max(maxDeg, x.getDegree());
        } else {
            for (int d : degs) maxDeg = Math.max(maxDeg, d);
        }
        FibNode<ReliefRequest> m = h.findMin();
        return new ForestSnapshot(district, caption, roots, degs, m == null ? null : m.getValue().id(),
                h.size(), h.rootCount(), h.markedCount(), maxDeg, compact, hl, kind);
    }

    /** Depth is at most FULL_VIEW_LIMIT here, so recursion is safe. */
    private static Node copy(FibNode<ReliefRequest> x) {
        Node n = new Node(x);
        for (FibNode<ReliefRequest> c : x.children()) n.kids.add(copy(c));
        return n;
    }

    int potential() { return rootCount + 2 * markedCount; }
}
