package relief.ui;

import relief.data.Dataset;
import relief.data.DatasetGenerator;
import relief.data.DatasetLoader;
import relief.heap.HeapStats;
import relief.sim.ImpactSimulation;
import relief.sim.PolicyMetrics;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Right panel: replays a whole cyclone scenario through BOTH policies and shows,
 * live, why prioritisation matters (critical waits, deadlines) and what the heap
 * did (decrease-keys, links, cuts, O(1) merges).
 */
final class ImpactPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private Dataset dataset;
    private ImpactSimulation sim;
    private final Timer timer;
    private final Consumer<String> log;

    private final JLabel datasetLabel = new JLabel();
    private final JSlider speed = new JSlider(1, 60, 20);
    private final Progress progress = new Progress();
    private final Chart chart = new Chart();

    ImpactPanel(Consumer<String> log) {
        this.log = log;
        setLayout(new BorderLayout());
        setBackground(Theme.PANEL);
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.LINE));
        setPreferredSize(new Dimension(400, 600));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        JLabel title = new JLabel("Impact");
        title.setFont(Theme.H1);
        title.setForeground(Theme.INK);
        top.add(left(title));
        JLabel sub = new JLabel("Fibonacci-heap priority vs arrival order (FIFO)");
        sub.setFont(Theme.SMALL);
        sub.setForeground(Theme.MUTED);
        top.add(left(sub));
        top.add(javax.swing.Box.createVerticalStrut(10));

        Ui.Card data = new Ui.Card(null);
        data.setLayout(new BoxLayout(data, BoxLayout.Y_AXIS));
        data.setAlignmentX(Component.LEFT_ALIGNMENT);
        datasetLabel.setFont(Theme.SMALL);
        datasetLabel.setForeground(Theme.MUTED);
        datasetLabel.setText("<html>&nbsp;<br>&nbsp;</html>");
        data.add(left(datasetLabel));
        data.add(javax.swing.Box.createVerticalStrut(8));
        JPanel row1 = new JPanel(new GridLayout(1, 3, 6, 0));
        row1.setOpaque(false);
        row1.add(btn("Load CSV…", this::chooseFile, Ui.Kind.GHOST));
        row1.add(btn("Built-in", () -> setDataset(DatasetGenerator.fullScenario(), "built-in scenario"), Ui.Kind.GHOST));
        row1.add(btn("Small", () -> setDataset(DatasetGenerator.smallSample(), "small sample"), Ui.Kind.GHOST));
        data.add(left(row1));
        data.add(javax.swing.Box.createVerticalStrut(8));
        JPanel row2 = new JPanel(new GridLayout(1, 3, 6, 0));
        row2.setOpaque(false);
        row2.add(btn("Replay", this::play, Ui.Kind.PRIMARY));
        row2.add(btn("Pause", () -> timer().stop(), Ui.Kind.SECONDARY));
        row2.add(btn("Instant", this::instant, Ui.Kind.SECONDARY));
        data.add(left(row2));
        data.add(javax.swing.Box.createVerticalStrut(10));
        JPanel row3 = new JPanel(new BorderLayout(8, 0));
        row3.setOpaque(false);
        JLabel sp = new JLabel("Speed");
        sp.setFont(Theme.SMALL);
        sp.setForeground(Theme.MUTED);
        speed.setOpaque(false);
        row3.add(sp, BorderLayout.WEST);
        row3.add(speed, BorderLayout.CENTER);
        data.add(left(row3));
        data.add(javax.swing.Box.createVerticalStrut(6));
        data.add(left(progress));
        top.add(data);
        add(top, BorderLayout.NORTH);

        JScrollPane sc = new JScrollPane(chart);
        sc.setBorder(BorderFactory.createEmptyBorder());
        sc.getViewport().setBackground(Theme.PANEL);
        sc.getVerticalScrollBar().setUnitIncrement(16);
        sc.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        Ui.styleScroll(sc);
        add(sc, BorderLayout.CENTER);

        timer = new Timer(50, e -> tickBatch());
        loadDefault();
    }

    private Timer timer() { return timer; }

    private Ui.ModernButton btn(String label, Runnable r, Ui.Kind kind) {
        return new Ui.ModernButton(new AbstractAction(label) {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(ActionEvent e) {
                try { r.run(); } catch (RuntimeException ex) { log.accept("Replay problem handled safely: " + ex); }
            }
        }, kind);
    }

    private static Component left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 4));
        return c;
    }

    // ================================================================= actions

    private void play() {
        if (sim == null || sim.finished()) restart();
        timer.start();
    }

    private void instant() {
        timer.stop();
        if (sim == null || sim.finished()) restart();
        sim.runAll();
        refresh();
        log.accept("Replay finished instantly: " + summary());
    }

    /** Try data/cyclone_requests.csv first; fall back to the built-in scenario. */
    private void loadDefault() {
        Path p = Paths.get("data", "cyclone_requests.csv");
        try {
            setDataset(DatasetLoader.load(p), p.toString());
        } catch (Exception ex) {
            setDataset(DatasetGenerator.fullScenario(), "built-in scenario");
            log.accept("Dataset file not found (" + p + "); using the built-in scenario instead.");
        }
    }

    private void chooseFile() {
        JFileChooser fc = new JFileChooser(new File("data"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            setDataset(DatasetLoader.load(fc.getSelectedFile().toPath()), fc.getSelectedFile().getName());
        } catch (Exception ex) {
            log.accept("Could not load that file (" + ex.getMessage() + "). Falling back to the small built-in sample.");
            setDataset(DatasetGenerator.smallSample(), "small sample (fallback)");
        }
    }

    private void setDataset(Dataset d, String source) {
        timer.stop();
        dataset = d;
        datasetLabel.setText("<html>" + d.arrivals.size() + " requests &middot; " + d.merges.size() + " merges &middot; "
                + (d.lastTick() + 1) + " ticks<br>" + source
                + (d.skippedLines > 0 ? " &middot; skipped " + d.skippedLines + " bad lines" : "") + "</html>");
        restart();
        log.accept("Replay data ready: " + d.arrivals.size() + " requests from " + source
                + (d.skippedLines > 0 ? " (" + d.skippedLines + " malformed lines skipped)" : "") + ".");
    }

    private void restart() {
        sim = new ImpactSimulation(dataset);
        refresh();
    }

    private void tickBatch() {
        int steps = Math.max(1, speed.getValue() / 10);
        timer.setDelay(Math.max(15, 320 - speed.getValue() * 5));
        for (int i = 0; i < steps; i++) {
            int before = sim.events().size();
            if (!sim.step()) {
                timer.stop();
                log.accept("Replay finished: " + summary());
                break;
            }
            for (int k = before; k < sim.events().size(); k++)
                log.accept("Replay " + sim.events().get(k) + " (heap.union: 4 pointer updates)");
        }
        refresh();
    }

    private String summary() {
        return String.format("critical avg wait %.1f vs %.1f ticks, on time %.0f%% vs %.0f%% (Fibonacci heap vs FIFO)",
                sim.priority.avgCriticalWait(), sim.fifo.avgCriticalWait(),
                sim.priority.criticalOnTimePct(), sim.fifo.criticalOnTimePct());
    }

    private void refresh() {
        progress.repaint();
        chart.repaint();
    }

    // ================================================================= progress bar

    private final class Progress extends JComponent {
        private static final long serialVersionUID = 1L;
        Progress() { setPreferredSize(new Dimension(300, 30)); }
        @Override
        protected void paintComponent(Graphics g0) {
            if (sim == null) return;
            Graphics2D g = Ui.smooth(g0);
            int w = getWidth();
            double f = sim.progress();
            g.setColor(Theme.FIELD);
            g.fillRoundRect(0, 4, w, 8, 8, 8);
            g.setPaint(new GradientPaint(0, 0, Theme.ACCENT, w, 0, Theme.ACCENT_2));
            g.fillRoundRect(0, 4, (int) (w * f), 8, 8, 8);
            g.setFont(Theme.SMALL);
            g.setColor(Theme.MUTED);
            g.drawString("tick " + sim.tick() + "  ·  arrivals " + Math.round(100 * f) + "%  ·  pending "
                    + sim.priority.pending(), 0, 27);
            g.dispose();
        }
    }

    // ================================================================= chart

    private final class Chart extends JComponent {
        private static final long serialVersionUID = 1L;
        private int lastHeight = 700;

        @Override
        public Dimension getPreferredSize() { return new Dimension(360, lastHeight); }

        @Override
        protected void paintComponent(Graphics g0) {
            if (sim == null) return;
            Graphics2D g = Ui.smooth(g0);
            PolicyMetrics p = sim.priority, f = sim.fifo;
            int pad = 16, w = getWidth() - 2 * pad, x = pad, y = 6;

            // --- KPI tiles
            int tw = (w - 10) / 2;
            kpi(g, x, y, tw, "CRITICAL AVG WAIT", String.format("%.1f", p.avgCriticalWait()),
                    String.format("vs %.1f ticks FIFO", f.avgCriticalWait()), Theme.ACCENT);
            kpi(g, x + tw + 10, y, tw, "ON TIME (CRITICAL)", String.format("%.0f%%", p.criticalOnTimePct()),
                    String.format("vs %.0f%% FIFO", f.criticalOnTimePct()), Theme.OK);
            y += 96;

            // --- legend
            g.setFont(Theme.SMALL);
            dot(g, x, y, Theme.PRIORITY); g.setColor(Theme.INK); g.drawString("Fibonacci heap", x + 14, y + 9);
            dot(g, x + 120, y, Theme.FIFO); g.setColor(Theme.INK); g.drawString("Arrival order (FIFO)", x + 134, y + 9);
            y += 24;

            // --- comparison card
            int cardTop = y;
            y += 16;
            y = metric(g, x + 14, y, w - 28, "Critical avg wait (ticks, lower is better)", p.avgCriticalWait(), f.avgCriticalWait(), "%.1f");
            y = metric(g, x + 14, y, w - 28, "Critical within deadline (%, higher is better)", p.criticalOnTimePct(), f.criticalOnTimePct(), "%.0f%%");
            y = metric(g, x + 14, y, w - 28, "Worst wait, critical request (ticks)", p.worstCriticalWait, f.worstCriticalWait, "%.0f");
            y = metric(g, x + 14, y, w - 28, "Avg wait, all requests (ticks)", p.avgWait(), f.avgWait(), "%.1f");
            y = metric(g, x + 14, y, w - 28, "Worst wait, any request (the price; aging bounds it)", p.worstWait, f.worstWait, "%.0f");
            cardBehind(g, x, cardTop, w, y - cardTop);
            y += 14;

            // --- heap work
            HeapStats s = sim.heapStats();
            g.setFont(Theme.H2);
            g.setColor(Theme.INK);
            g.drawString("What the Fibonacci heap did", x, y + 12);
            y += 24;
            String[][] cells = {
                {"Inserts", String.format("%,d", s.inserts)}, {"Extract-min", String.format("%,d", s.extractMins)},
                {"Raises (O(1) am.)", String.format("%,d", s.decreaseKeys)}, {"Cuts", String.format("%,d", s.cuts)},
                {"Cascading cuts", String.format("%,d", s.cascadingCuts)}, {"Links", String.format("%,d", s.links)},
                {"Merge: heap ptr ops", String.format("%,d", p.mergeWork)}, {"Merge: FIFO moves", String.format("%,d", f.mergeWork)},
            };
            int cw = (w - 8) / 2;
            for (int i = 0; i < cells.length; i++) {
                int cx = x + (i % 2) * (cw + 8), cy = y + (i / 2) * 50;
                g.setColor(Theme.CARD);
                g.fillRoundRect(cx, cy, cw, 42, 12, 12);
                g.setColor(Theme.LINE);
                g.drawRoundRect(cx, cy, cw, 42, 12, 12);
                g.setFont(Theme.TINY);
                g.setColor(Theme.MUTED);
                g.drawString(cells[i][0].toUpperCase(), cx + 10, cy + 15);
                g.setFont(Theme.H1);
                g.setColor(Theme.INK);
                g.drawString(cells[i][1], cx + 10, cy + 34);
            }
            y += 4 * 50;
            if (s.decreaseKeys > 0) {
                g.setFont(Theme.SMALL);
                g.setColor(Theme.OK);
                g.drawString(String.format("✔ %.2f cuts per raise — constant on average: amortised O(1)",
                        (double) s.cuts / s.decreaseKeys), x, y + 6);
                y += 22;
            }

            // --- pending per district
            y += 8;
            g.setFont(Theme.H2);
            g.setColor(Theme.INK);
            g.drawString("Pending per active district", x, y + 12);
            y += 24;
            Map<String, Integer> pend = sim.pendingByDistrict();
            int max = 1;
            for (int v : pend.values()) max = Math.max(max, v);
            g.setFont(Theme.SMALL);
            for (Map.Entry<String, Integer> e : pend.entrySet()) {
                g.setColor(Theme.MUTED);
                g.drawString(e.getKey(), x, y + 10);
                int bx = x + 100, bw = w - 140;
                g.setColor(Theme.FIELD);
                g.fillRoundRect(bx, y + 2, bw, 9, 9, 9);
                int len = (int) (bw * (e.getValue() / (double) max));
                if (e.getValue() > 0) {
                    g.setPaint(new GradientPaint(bx, 0, Theme.ACCENT, bx + bw, 0, Theme.ACCENT_2));
                    g.fillRoundRect(bx, y + 2, Math.max(9, len), 9, 9, 9);
                }
                g.setColor(Theme.INK);
                g.drawString(String.valueOf(e.getValue()), bx + bw + 8, y + 10);
                y += 20;
            }
            List<String> ev = sim.events();
            if (!ev.isEmpty()) {
                y += 8;
                for (String e : ev) {
                    g.setColor(Theme.alpha(Theme.OK, 30));
                    g.setFont(Theme.SMALL);
                    int ew = g.getFontMetrics().stringWidth(e) + 20;
                    g.fillRoundRect(x, y, Math.min(ew, w), 22, 22, 22);
                    g.setColor(Theme.OK);
                    g.drawString(e, x + 10, y + 15);
                    y += 28;
                }
            }
            y += 16;
            if (Math.abs(y - lastHeight) > 4) { lastHeight = y; revalidate(); }
            g.dispose();
        }

        private void kpi(Graphics2D g, int x, int y, int w, String cap, String big, String vs, Color accent) {
            g.setPaint(new GradientPaint(x, y, Theme.alpha(accent, 50), x + w, y + 86, Theme.CARD));
            g.fillRoundRect(x, y, w, 86, 16, 16);
            g.setColor(Theme.alpha(accent, 110));
            g.drawRoundRect(x, y, w, 86, 16, 16);
            g.setFont(Theme.TINY);
            g.setColor(Theme.MUTED);
            g.drawString(cap, x + 12, y + 20);
            g.setFont(Theme.KPI);
            g.setColor(Theme.INK);
            g.drawString(big, x + 12, y + 54);
            g.setFont(Theme.SMALL);
            g.setColor(Theme.MUTED);
            g.drawString(vs, x + 12, y + 74);
        }

        private void cardBehind(Graphics2D g, int x, int y, int w, int h) {
            // drawn after the bars with a transparent fill so the bars stay crisp
            g.setColor(Theme.LINE);
            g.drawRoundRect(x, y, w, h, 16, 16);
        }

        private void dot(Graphics2D g, int x, int y, Color c) {
            g.setColor(c);
            g.fillRoundRect(x, y, 10, 10, 4, 4);
        }

        private int metric(Graphics2D g, int x, int y, int w, String label, double a, double b, String fmt) {
            g.setFont(Theme.SMALL);
            g.setColor(Theme.MUTED);
            g.drawString(label, x, y + 4);
            y += 10;
            double max = Math.max(1e-9, Math.max(a, b));
            FontMetrics fm = g.getFontMetrics();
            int barMax = w - 56;
            bar(g, x, y, barMax, (int) (barMax * a / max), Theme.PRIORITY, String.format(fmt, a), fm);
            bar(g, x, y + 15, barMax, (int) (barMax * b / max), Theme.FIFO, String.format(fmt, b), fm);
            return y + 44;
        }

        private void bar(Graphics2D g, int x, int y, int barMax, int len, Color c, String text, FontMetrics fm) {
            g.setColor(Theme.FIELD);
            g.fillRoundRect(x, y, barMax, 10, 10, 10);
            g.setColor(c);
            g.fillRoundRect(x, y, Math.max(10, len), 10, 10, 10);
            g.setColor(Theme.INK);
            g.drawString(text, x + barMax + 8, y + 10);
        }
    }
}
