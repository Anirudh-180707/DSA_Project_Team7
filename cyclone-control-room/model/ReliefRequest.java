package relief.model;

/**
 * A single relief request. Urgency scale: 1 = critical (life in danger) ... 10 = can wait.
 * The urgency is the heap KEY: smaller key = served earlier.
 */
public final class ReliefRequest {

    public static final int MOST_URGENT = 1;
    public static final int LEAST_URGENT = 10;

    private final String id;
    private final String originDistrict;   // where it was raised (never changes)
    private final RequestType type;
    private final int initialUrgency;
    private final long createdAt;          // tick / sequence when it arrived

    public ReliefRequest(String id, String originDistrict, RequestType type,
                         int initialUrgency, long createdAt) {
        this.id = id;
        this.originDistrict = originDistrict;
        this.type = type;
        this.initialUrgency = initialUrgency;
        this.createdAt = createdAt;
    }

    public String id()             { return id; }
    public String originDistrict() { return originDistrict; }
    public RequestType type()      { return type; }
    public int initialUrgency()    { return initialUrgency; }
    public long createdAt()        { return createdAt; }

    public static boolean validUrgency(int u) { return u >= MOST_URGENT && u <= LEAST_URGENT; }

    @Override
    public String toString() { return id; }
}
