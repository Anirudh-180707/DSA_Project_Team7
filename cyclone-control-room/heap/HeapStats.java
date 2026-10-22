package relief.heap;

/**
 * Operation counters shared by all heaps of one control room.
 * They let us show the complexity claims with real numbers during the demo:
 * e.g. "1,000 raises caused only 612 cuts in total" (amortised O(1)).
 */
public final class HeapStats {
    public long inserts;
    public long unions;
    public long extractMins;
    public long decreaseKeys;
    public long deletes;
    public long links;            // tree-linking steps inside consolidation
    public long cuts;             // ALL cuts (direct + cascading)
    public long cascadingCuts;    // how many of those cuts were cascading (marked ancestors)
    public long rootsScanned;     // roots walked by consolidation (actual work of extract-min)

    public void reset() {
        inserts = unions = extractMins = decreaseKeys = deletes = 0;
        links = cuts = cascadingCuts = rootsScanned = 0;
    }

    public HeapStats copy() {
        HeapStats s = new HeapStats();
        s.inserts = inserts; s.unions = unions; s.extractMins = extractMins;
        s.decreaseKeys = decreaseKeys; s.deletes = deletes; s.links = links;
        s.cuts = cuts; s.cascadingCuts = cascadingCuts; s.rootsScanned = rootsScanned;
        return s;
    }
}
