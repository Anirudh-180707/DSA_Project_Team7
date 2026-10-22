package relief.sim;

/** Running outcome numbers for one dispatch policy during a replay. */
public final class PolicyMetrics {
    public final String name;
    public int arrived;
    public int served;
    public int criticalArrived;
    public int criticalServed;
    public long criticalWaitSum;
    public int criticalWithinDeadline;
    public int worstCriticalWait;
    public long allWaitSum;
    public int worstWait;        // any request (shows aging prevents starvation)
    public long mergeWork;      // elements/pointers touched while merging districts

    public PolicyMetrics(String name) { this.name = name; }

    public double avgCriticalWait() { return criticalServed == 0 ? 0 : (double) criticalWaitSum / criticalServed; }

    public double avgWait() { return served == 0 ? 0 : (double) allWaitSum / served; }

    public double criticalOnTimePct() {
        return criticalServed == 0 ? 0 : 100.0 * criticalWithinDeadline / criticalServed;
    }

    public int pending() { return arrived - served; }
}
