package relief.data;

import relief.core.ControlRoom;
import relief.model.ReliefRequest;
import relief.model.RequestType;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a scenario CSV. Defensive by design: bad lines are skipped and counted,
 * and the caller falls back to the built-in sample if the file cannot be read.
 */
public final class DatasetLoader {

    private DatasetLoader() {}

    public static Dataset load(Path file) throws IOException {
        Dataset d = new Dataset(file.getFileName().toString());
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.toLowerCase().startsWith("tick,")) continue;
                String[] p = line.split(",");
                try {
                    if (p[0].trim().equalsIgnoreCase("MERGE")) {
                        if (p.length < 4) { d.skippedLines++; continue; }
                        d.merges.add(new Dataset.MergeEvent(Integer.parseInt(p[1].trim()),
                                ControlRoom.normalise(p[2]), ControlRoom.normalise(p[3])));
                        continue;
                    }
                    if (p.length < 4) { d.skippedLines++; continue; }
                    int tick = Integer.parseInt(p[0].trim());
                    String district = ControlRoom.normalise(p[1]);
                    RequestType type = RequestType.parse(p[2]);
                    int urgency = Integer.parseInt(p[3].trim());
                    if (tick < 0 || district.isEmpty() || type == null || !ReliefRequest.validUrgency(urgency)) {
                        d.skippedLines++;
                        continue;
                    }
                    d.arrivals.add(new Dataset.Arrival(tick, district, type, urgency));
                } catch (NumberFormatException ex) {
                    d.skippedLines++;
                }
            }
        }
        d.arrivals.sort((a, b) -> Integer.compare(a.tick, b.tick));   // stable: keeps file order per tick
        d.merges.sort((a, b) -> Integer.compare(a.tick, b.tick));
        if (d.arrivals.isEmpty()) throw new IOException("no valid request rows in " + file);
        return d;
    }
}
