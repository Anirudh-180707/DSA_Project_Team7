package relief.core;

import relief.heap.FibNode;
import relief.heap.FibonacciHeap;
import relief.heap.HeapStats;
import relief.heap.StepListener;
import relief.model.ReliefRequest;
import relief.model.RequestType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The state relief control room: one Fibonacci heap per ACTIVE district.
 *
 * Supporting structures (and why):
 *  - heaps    : district -> heap. Merging districts = heap.union(), O(1).
 *  - handles  : request id -> its heap node. decrease-key needs a direct pointer to
 *               the node; a heap cannot "search" for a key. HashMap gives O(1) lookup.
 *  - mergedInto: union-find style alias table. When Puri is merged into Khordha we do
 *               NOT walk Puri's requests to relabel them (that would be O(n) and would
 *               kill the O(1) merge). Instead "Puri -> Khordha" is recorded and any
 *               lookup follows the chain with path compression (near O(1)).
 */
public final class ControlRoom {

    private final Map<String, FibonacciHeap<ReliefRequest>> heaps = new LinkedHashMap<>();
    private final Map<String, FibNode<ReliefRequest>> handles = new HashMap<>();
    private final Map<String, String> mergedInto = new HashMap<>();
    private final HeapStats stats = new HeapStats();
    private StepListener<ReliefRequest> listener;
    private int nextId = 1;
    private long clock = 0;

    // ------------------------------------------------------------ districts

    /** Canonical form: trimmed, first letter of each word upper-case. */
    public static String normalise(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) {
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
        }
        return b.toString();
    }

    /** Follow merge aliases to the district that now owns the requests (path compression). */
    public String resolve(String district) {
        String d = normalise(district);
        String root = d;
        while (mergedInto.containsKey(root)) root = mergedInto.get(root);
        while (!d.equals(root)) {                // compress the path
            String next = mergedInto.get(d);
            mergedInto.put(d, root);
            d = next;
        }
        return root;
    }

    public OpResult addDistrict(String name) {
        String d = normalise(name);
        if (d.isEmpty()) return OpResult.fail("District name is empty.");
        if (mergedInto.containsKey(d))
            return OpResult.fail(d + " was merged into " + resolve(d) + "; it cannot be re-opened.");
        if (heaps.containsKey(d)) return OpResult.ok(d + " already exists.", null, d);
        heaps.put(d, newHeap());
        return OpResult.ok("District " + d + " opened.", null, d);
    }

    private FibonacciHeap<ReliefRequest> newHeap() {
        FibonacciHeap<ReliefRequest> h = new FibonacciHeap<>(stats);
        h.setListener(listener);
        return h;
    }

    // ------------------------------------------------------------ operations

    /** INSERT, O(1). Unknown districts are opened automatically. */
    public OpResult addRequest(String district, RequestType type, int urgency) {
        String d = normalise(district);
        if (d.isEmpty()) return OpResult.fail("Please enter a district name.");
        if (type == null) return OpResult.fail("Unknown request type. Use Rescue, Medicine, Food or Boat.");
        if (!ReliefRequest.validUrgency(urgency))
            return OpResult.fail("Urgency must be between 1 (critical) and 10 (can wait); got " + urgency + ".");

        String owner = resolve(d);
        if (!heaps.containsKey(owner)) heaps.put(owner, newHeap());

        String id = "R-" + (nextId++);
        ReliefRequest r = new ReliefRequest(id, d, type, urgency, clock++);
        FibNode<ReliefRequest> node = heaps.get(owner).insert(urgency, r);
        handles.put(id, node);

        String note = owner.equals(d) ? "" : " (" + d + " is now handled by " + owner + ")";
        return OpResult.ok("Added " + id + " - " + type + " in " + d + ", urgency " + urgency + note + ".", r, owner);
    }

    /** DECREASE-KEY, O(1) amortised. */
    public OpResult raiseUrgency(String requestId, int newUrgency) {
        String id = requestId == null ? "" : requestId.trim().toUpperCase();
        if (id.matches("\\d+")) id = "R-" + id;
        FibNode<ReliefRequest> node = handles.get(id);
        if (node == null) return OpResult.fail("No pending request with id '" + requestId + "' (already served, cancelled or never created).");
        if (!ReliefRequest.validUrgency(newUrgency))
            return OpResult.fail("Urgency must be between 1 and 10; got " + newUrgency + ".");
        int old = node.getKey();
        if (newUrgency >= old)
            return OpResult.fail("Rejected: " + id + " is already at urgency " + old
                    + ". Raising urgency means a SMALLER number (" + newUrgency + " is not more urgent).");
        String owner = resolve(node.getValue().originDistrict());
        heaps.get(owner).decreaseKey(node, newUrgency);
        return OpResult.ok("Raised " + id + " from " + old + " to " + newUrgency + ".", node.getValue(), owner);
    }

    /** UNION, O(1): every pending request of 'from' moves to 'into'. */
    public OpResult mergeDistricts(String from, String into) {
        String a = normalise(from), b = normalise(into);
        if (a.isEmpty() || b.isEmpty()) return OpResult.fail("Choose both districts to merge.");
        String ra = resolve(a), rb = resolve(b);
        if (!heaps.containsKey(ra)) return OpResult.fail("Unknown district: " + a + ".");
        if (!heaps.containsKey(rb)) return OpResult.fail("Unknown district: " + b + ".");
        if (ra.equals(rb))
            return OpResult.fail(a.equals(b) ? "Cannot merge a district with itself."
                    : a + " and " + b + " are already handled together by " + ra + ".");

        FibonacciHeap<ReliefRequest> src = heaps.remove(ra);
        int moved = src.size();
        heaps.get(rb).union(src);              // O(1) splice
        mergedInto.put(ra, rb);                // O(1) alias, no relabelling
        String msg = moved == 0
                ? ra + " had no pending requests; merged into " + rb + " (nothing to move)."
                : "Merged " + ra + " into " + rb + ": " + moved + " pending request(s) moved in O(1).";
        return OpResult.ok(msg, null, rb);
    }

    /** EXTRACT-MIN for one district, O(log n) amortised. */
    public OpResult serve(String district) {
        String d = normalise(district);
        if (d.isEmpty()) return OpResult.fail("Choose a district to serve.");
        String owner = resolve(d);
        FibonacciHeap<ReliefRequest> h = heaps.get(owner);
        if (h == null) return OpResult.fail("Unknown district: " + d + ".");
        if (h.isEmpty()) return OpResult.fail("No pending requests in " + owner + " - nothing to serve.");
        FibNode<ReliefRequest> node = h.extractMin();
        handles.remove(node.getValue().id());
        ReliefRequest r = node.getValue();
        return OpResult.ok("Dispatched " + r.id() + " (" + r.type() + ", " + r.originDistrict()
                + ", urgency " + node.getKey() + ") from " + owner + ".", r, owner);
    }

    /** Most urgent request across ALL districts: compare k district minimums, O(k). */
    public OpResult serveGlobal() {
        String best = globalMostUrgentDistrict();
        if (best == null) return OpResult.fail("No pending requests anywhere - nothing to serve.");
        return serve(best);
    }

    /** DELETE, O(log n) amortised: a request that is no longer needed. */
    public OpResult cancel(String requestId) {
        String id = requestId == null ? "" : requestId.trim().toUpperCase();
        if (id.matches("\\d+")) id = "R-" + id;
        FibNode<ReliefRequest> node = handles.remove(id);
        if (node == null) return OpResult.fail("No pending request with id '" + requestId + "'.");
        String owner = resolve(node.getValue().originDistrict());
        heaps.get(owner).delete(node);
        return OpResult.ok("Cancelled " + id + ".", node.getValue(), owner);
    }

    // ------------------------------------------------------------ queries

    public String globalMostUrgentDistrict() {
        String best = null;
        FibNode<ReliefRequest> bestNode = null;
        for (Map.Entry<String, FibonacciHeap<ReliefRequest>> e : heaps.entrySet()) {
            FibNode<ReliefRequest> m = e.getValue().findMin();
            if (m == null) continue;
            if (bestNode == null || m.getKey() < bestNode.getKey()
                    || (m.getKey() == bestNode.getKey() && m.getSeq() < bestNode.getSeq())) {
                bestNode = m;
                best = e.getKey();
            }
        }
        return best;
    }

    public FibNode<ReliefRequest> peek(String district) {
        FibonacciHeap<ReliefRequest> h = heaps.get(resolve(district));
        return h == null ? null : h.findMin();
    }

    public FibonacciHeap<ReliefRequest> heapOf(String district) { return heaps.get(resolve(district)); }

    public List<String> activeDistricts() { return new ArrayList<>(heaps.keySet()); }

    public Map<String, String> mergeAliases() { return Collections.unmodifiableMap(mergedInto); }

    /** Pending request ids for a district, most urgent first (UI helper, O(n log n)). */
    public List<String> pendingIds(String district) {
        FibonacciHeap<ReliefRequest> h = heapOf(district);
        List<String> ids = new ArrayList<>();
        if (h == null) return ids;
        List<FibNode<ReliefRequest>> nodes = h.allNodes();
        nodes.sort((x, y) -> x.getKey() != y.getKey() ? Integer.compare(x.getKey(), y.getKey())
                : Long.compare(x.getSeq(), y.getSeq()));
        for (FibNode<ReliefRequest> x : nodes) ids.add(x.getValue().id());
        return ids;
    }

    public FibNode<ReliefRequest> handle(String id) { return handles.get(id); }

    public int totalPending() { return handles.size(); }

    public HeapStats stats() { return stats; }

    /** Attach the visualiser to every current and future district heap. */
    public void setListener(StepListener<ReliefRequest> l) {
        this.listener = l;
        for (FibonacciHeap<ReliefRequest> h : heaps.values()) h.setListener(l);
    }

    public void reset() {
        heaps.clear();
        handles.clear();
        mergedInto.clear();
        stats.reset();
        nextId = 1;
        clock = 0;
    }
}
