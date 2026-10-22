package relief.data;

import relief.model.RequestType;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Builds a SYNTHETIC but realistic cyclone scenario (fixed seed = same data every run).
 * Modelled on an Odisha-coast landfall: requests trickle in, surge around landfall,
 * then taper; two overwhelmed coastal districts are merged into inland neighbours.
 *
 * Because it is generated in code, it doubles as the built-in fallback when the CSV
 * file is missing or unreadable during the evaluation.
 */
public final class DatasetGenerator {

    public static final String[] DISTRICTS = {
        "Puri", "Ganjam", "Khordha", "Cuttack", "Jagatsinghpur", "Kendrapara", "Balasore"
    };

    private DatasetGenerator() {}

    /** Full scenario, about 1,500 requests over 120 ticks. */
    public static Dataset fullScenario() {
        return generate("Synthetic cyclone landfall (built-in)", 2026L, 120, 1.0);
    }

    /** Tiny scenario for the fallback and quick demos (about 80 requests). */
    public static Dataset smallSample() {
        Dataset d = generate("Small built-in sample", 7L, 30, 0.22);
        return d;
    }

    private static Dataset generate(String name, long seed, int ticks, double scale) {
        Random rnd = new Random(seed);
        Dataset d = new Dataset(name);
        int landfall = ticks / 3;
        for (int t = 0; t < ticks; t++) {
            // arrivals per tick: base load + a bell-shaped surge around landfall
            double surge = Math.exp(-Math.pow((t - landfall) / (ticks / 8.0), 2));
            double mean = scale * (6 + 22 * surge);
            int count = poisson(rnd, mean);
            for (int i = 0; i < count; i++) {
                String district = DISTRICTS[pickDistrict(rnd)];
                RequestType type = pickType(rnd, surge);
                int urgency = pickUrgency(rnd, type);
                d.arrivals.add(new Dataset.Arrival(t, district, type, urgency));
            }
        }
        // overwhelmed coastal districts handed to inland neighbours
        d.merges.add(new Dataset.MergeEvent(landfall + ticks / 12, "Jagatsinghpur", "Cuttack"));
        d.merges.add(new Dataset.MergeEvent(landfall + ticks / 6, "Puri", "Khordha"));
        return d;
    }

    /** Coastal districts are hit harder. */
    private static int pickDistrict(Random r) {
        double[] w = {0.22, 0.14, 0.10, 0.10, 0.18, 0.14, 0.12};
        double x = r.nextDouble(), acc = 0;
        for (int i = 0; i < w.length; i++) { acc += w[i]; if (x < acc) return i; }
        return w.length - 1;
    }

    private static RequestType pickType(Random r, double surge) {
        double x = r.nextDouble();
        double rescue = 0.15 + 0.20 * surge;           // more rescues near landfall
        if (x < rescue) return RequestType.RESCUE;
        if (x < rescue + 0.20) return RequestType.MEDICINE;
        if (x < rescue + 0.30) return RequestType.BOAT;
        return RequestType.FOOD;
    }

    /** Rescue and medicine skew critical; food skews low urgency. */
    private static int pickUrgency(Random r, RequestType t) {
        int base;
        switch (t) {
            case RESCUE:   base = 1 + r.nextInt(4); break;   // 1..4
            case MEDICINE: base = 2 + r.nextInt(5); break;   // 2..6
            case BOAT:     base = 2 + r.nextInt(6); break;   // 2..7
            default:       base = 4 + r.nextInt(7); break;   // 4..10
        }
        return Math.min(10, base);
    }

    private static int poisson(Random r, double mean) {
        double l = Math.exp(-mean), p = 1.0;
        int k = 0;
        do { k++; p *= r.nextDouble(); } while (p > l);
        return k - 1;
    }

    /** Writes a scenario to CSV (used once to create data/*.csv). */
    public static void writeCsv(Dataset d, Path out) throws IOException {
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println("# " + d.name);
            w.println("# Request rows : tick,district,type,urgency   (urgency 1 = critical ... 10 = can wait)");
            w.println("# Merge rows   : MERGE,tick,fromDistrict,intoDistrict");
            w.println("tick,district,type,urgency");
            for (Dataset.MergeEvent m : d.merges) w.println("MERGE," + m.tick + "," + m.from + "," + m.into);
            for (Dataset.Arrival a : d.arrivals)
                w.println(a.tick + "," + a.district + "," + a.type.name() + "," + a.urgency);
        }
    }

    /** java relief.data.DatasetGenerator  -> regenerates the CSV files in data/ */
    public static void main(String[] args) throws IOException {
        writeCsv(fullScenario(), Paths.get("data", "cyclone_requests.csv"));
        writeCsv(smallSample(), Paths.get("data", "small_sample.csv"));
        System.out.println("Wrote data/cyclone_requests.csv and data/small_sample.csv");
    }
}
