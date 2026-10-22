package relief.model;

/** The four kinds of relief requests a cyclone control room handles. */
public enum RequestType {
    RESCUE("Rescue"),
    MEDICINE("Medicine"),
    FOOD("Food"),
    BOAT("Boat");

    private final String label;

    RequestType(String label) { this.label = label; }

    public String label() { return label; }

    /** Accepts "rescue", "R", "Medicine", "boat" ... ; returns null if unknown. */
    public static RequestType parse(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase();
        if (t.isEmpty()) return null;
        for (RequestType r : values()) {
            if (r.name().equals(t) || r.name().charAt(0) == t.charAt(0) && t.length() == 1) return r;
        }
        return null;
    }

    @Override
    public String toString() { return label; }
}
