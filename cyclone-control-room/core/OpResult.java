package relief.core;

import relief.model.ReliefRequest;

/**
 * Outcome of a control-room operation. Invalid input never throws to the UI:
 * it comes back as ok=false with a human-readable message (edge-case handling).
 */
public final class OpResult {
    public final boolean ok;
    public final String message;
    public final ReliefRequest request;   // may be null
    public final String district;         // district the operation acted on (may be null)

    private OpResult(boolean ok, String message, ReliefRequest request, String district) {
        this.ok = ok;
        this.message = message;
        this.request = request;
        this.district = district;
    }

    public static OpResult ok(String msg, ReliefRequest r, String district) {
        return new OpResult(true, msg, r, district);
    }

    public static OpResult fail(String msg) {
        return new OpResult(false, msg, null, null);
    }

    @Override
    public String toString() { return (ok ? "OK: " : "REJECTED: ") + message; }
}
