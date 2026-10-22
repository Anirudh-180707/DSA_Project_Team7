package relief.data;

import relief.model.RequestType;

import java.util.ArrayList;
import java.util.List;

/** A replayable cyclone scenario: timed request arrivals plus district-merge events. */
public final class Dataset {

    public static final class Arrival {
        public final int tick;
        public final String district;
        public final RequestType type;
        public final int urgency;

        public Arrival(int tick, String district, RequestType type, int urgency) {
            this.tick = tick;
            this.district = district;
            this.type = type;
            this.urgency = urgency;
        }
    }

    public static final class MergeEvent {
        public final int tick;
        public final String from;
        public final String into;

        public MergeEvent(int tick, String from, String into) {
            this.tick = tick;
            this.from = from;
            this.into = into;
        }
    }

    public final String name;
    public final List<Arrival> arrivals = new ArrayList<>();
    public final List<MergeEvent> merges = new ArrayList<>();
    public int skippedLines;   // malformed CSV lines that were ignored

    public Dataset(String name) { this.name = name; }

    public int lastTick() {
        int t = 0;
        for (Arrival a : arrivals) t = Math.max(t, a.tick);
        for (MergeEvent m : merges) t = Math.max(t, m.tick);
        return t;
    }
}
