package relief.sim;

import relief.data.Dataset;
import relief.heap.FibNode;
import relief.heap.FibonacciHeap;
import relief.heap.HeapStats;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays the SAME arrivals through two dispatch policies side by side:
 *
 *   PRIORITY : one Fibonacci heap per district; urgency escalates while waiting
 *              (decrease-key); overwhelmed districts merged with heap.union().
 *   FIFO     : one queue per district, served strictly in arrival order;
 *              merges append one queue onto another (moves every element).
 *
 * Each district relief team can dispatch CAPACITY requests per tick. A request is
 * CRITICAL if it arrived with urgency 1 or 2; its deadline is 2 + 2*urgency ticks.
 */
public final class ImpactSimulation {

    public static final int CAPACITY_PER_DISTRICT = 2;
    public static final int ESCALATE_EVERY = 10;     // aging: waiting 10 ticks -> urgency - 1, so nothing starves

    /** One request as tracked by the simulation (shared by both policies). */
    private static final class Job {
        final int arrivedTick;
        final String district;
        final int initialUrgency;
        final boolean critical;
        final int deadline;
        int urgency;
        int lastEscalation;
        FibNode<Job> node;
        boolean servedPriority;

        Job(Dataset.Arrival a) {
            arrivedTick = a.tick;
            district = a.district;
            initialUrgency = a.urgency;
            urgency = a.urgency;
            critical = a.urgency <= 2;
            deadline = 2 + 2 * a.urgency;
            lastEscalation = a.tick;
        }
    }

    private final Dataset data;
    private final HeapStats heapStats = new HeapStats();
    private final Map<String, FibonacciHeap<Job>> heaps = new LinkedHashMap<>();
    private final Map<String, ArrayDeque<Job>> queues = new LinkedHashMap<>();
    private final Map<String, String> alias = new HashMap<>();
    private final List<Job> waitingPriority = new ArrayList<>();

    public final PolicyMetrics priority = new PolicyMetrics("Fibonacci-heap priority");
    public final PolicyMetrics fifo = new PolicyMetrics("Arrival order (FIFO)");

    private int tick = 0;
    private int arrivalIdx = 0;
    private int mergeIdx = 0;
    private final List<String> events = new ArrayList<>();

    public ImpactSimulation(Dataset data) { this.data = data; }

    public boolean finished() {
        return arrivalIdx >= data.arrivals.size() && mergeIdx >= data.merges.size()
                && priority.pending() == 0 && fifo.pending() == 0;
    }

    /** Advance one time step. Returns false when the replay is over. */
    public boolean step() {
        if (finished()) return false;

        // 1. district merges scheduled for this tick
        while (mergeIdx < data.merges.size() && data.merges.get(mergeIdx).tick <= tick) {
            Dataset.MergeEvent m = data.merges.get(mergeIdx++);
            String from = resolve(m.from), into = resolve(m.into);
            if (from.equals(into)) continue;
            ensure(from);
            ensure(into);
            FibonacciHeap<Job> src = heaps.remove(from);
            heaps.get(into).union(src);                    // O(1)
            priority.mergeWork += 4;                       // four pointer updates
            ArrayDeque<Job> q = queues.remove(from);
            fifo.mergeWork += q.size();                    // every element moved
            queues.get(into).addAll(q);
            alias.put(from, into);
            events.add("t=" + tick + ": " + from + " merged into " + into);
        }

        // 2. arrivals for this tick go to both policies
        while (arrivalIdx < data.arrivals.size() && data.arrivals.get(arrivalIdx).tick <= tick) {
            Dataset.Arrival a = data.arrivals.get(arrivalIdx++);
            String d = resolve(a.district);
            ensure(d);
            Job pj = new Job(a);
            pj.node = heaps.get(d).insert(pj.urgency, pj);
            waitingPriority.add(pj);
            Job fj = new Job(a);
            queues.get(d).addLast(fj);
            priority.arrived++;
            fifo.arrived++;
            if (pj.critical) { priority.criticalArrived++; fifo.criticalArrived++; }
        }

        // 3. urgency rises while a request waits (decrease-key)
        waitingPriority.removeIf(j -> j.servedPriority);
        for (Job j : waitingPriority) {
            if (j.urgency > 1 && tick - j.lastEscalation >= ESCALATE_EVERY) {
                j.urgency--;
                j.lastEscalation = tick;
                String d = currentOwner(j);
                heaps.get(d).decreaseKey(j.node, j.urgency);
            }
        }

        // 4. each active district team dispatches CAPACITY requests
        for (FibonacciHeap<Job> h : heaps.values()) {
            for (int c = 0; c < CAPACITY_PER_DISTRICT && !h.isEmpty(); c++) {
                Job j = h.extractMin().getValue();
                j.servedPriority = true;
                record(priority, j);
            }
        }
        for (ArrayDeque<Job> q : queues.values()) {
            for (int c = 0; c < CAPACITY_PER_DISTRICT && !q.isEmpty(); c++) record(fifo, q.pollFirst());
        }

        tick++;
        return true;
    }

    /** Run to completion (used by the console mode and the tests). */
    public void runAll() {
        int guard = 0;
        while (step() && guard++ < 1_000_000) { /* keep going */ }
    }

    private void record(PolicyMetrics m, Job j) {
        int wait = tick - j.arrivedTick;
        m.served++;
        m.allWaitSum += wait;
        m.worstWait = Math.max(m.worstWait, wait);
        if (j.critical) {
            m.criticalServed++;
            m.criticalWaitSum += wait;
            if (wait <= j.deadline) m.criticalWithinDeadline++;
            m.worstCriticalWait = Math.max(m.worstCriticalWait, wait);
        }
    }

    /** The heap that now owns a job = its origin district after following merges. */
    private String currentOwner(Job j) { return resolve(j.district); }

    private String resolve(String d) {
        while (alias.containsKey(d)) d = alias.get(d);
        return d;
    }

    private void ensure(String d) {
        if (!heaps.containsKey(d)) {
            heaps.put(d, new FibonacciHeap<>(heapStats));
            queues.put(d, new ArrayDeque<>());
        }
    }

    // ------------------------------------------------------------ getters for the UI
    public int tick() { return tick; }
    public Dataset data() { return data; }
    public HeapStats heapStats() { return heapStats; }
    public List<String> events() { return events; }
    public double progress() {
        int total = data.arrivals.size();
        return total == 0 ? 1 : (double) arrivalIdx / total;
    }

    /** district -> pending count under the priority policy */
    public Map<String, Integer> pendingByDistrict() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Map.Entry<String, FibonacciHeap<Job>> e : heaps.entrySet()) m.put(e.getKey(), e.getValue().size());
        return m;
    }

    public int totalRoots() {
        int t = 0;
        for (FibonacciHeap<Job> h : heaps.values()) t += h.rootCount();
        return t;
    }

    public int totalMarked() {
        int m = 0;
        for (FibonacciHeap<Job> h : heaps.values()) m += h.markedCount();
        return m;
    }
}
