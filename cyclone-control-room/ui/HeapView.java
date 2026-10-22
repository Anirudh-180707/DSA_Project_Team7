package relief.ui;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.QuadCurve2D;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Centre panel: draws the root list and every heap-ordered tree of one district. */
final class HeapView extends JPanel {

    private static final int UNIT = 70;      // horizontal space per leaf
    private static final int LEVEL = 86;     // vertical distance between depths
    private static final int R = 22;         // node radius
    private static final int TOP = 160;      // y of the root list
    private static final int LEFT = 40;

    private ForestSnapshot snap = ForestSnapshot.empty("Add a request or load the demo scenario to begin.");
    private final Map<ForestSnapshot.Node, Point> pos = new IdentityHashMap<>();
    private final Map<ForestSnapshot.Node, Integer> width = new IdentityHashMap<>();

    HeapView() {
        setBackground(Theme.CANVAS);
        setPreferredSize(new Dimension(800, 480));
    }

    void show(ForestSnapshot s) {
        this.snap = s;
        layoutForest();
        revalidate();
        repaint();
    }

    ForestSnapshot current() { return snap; }

    // ------------------------------------------------------------ layout

    private void layoutForest() {
        pos.clear();
        width.clear();
        int x = LEFT, maxDepth = 0;
        for (ForestSnapshot.Node r : snap.roots) {
            int w = measure(r);
            place(r, x, TOP);
            x += w * UNIT;
            maxDepth = Math.max(maxDepth, depth(r));
        }
        int w = Math.max(800, x + LEFT + 140);
        int h = Math.max(480, TOP + (maxDepth + 1) * LEVEL + 70);
        if (snap.compact) { w = 800; h = 480; }
        setPreferredSize(new Dimension(w, h));
    }

    private int measure(ForestSnapshot.Node n) {
        int w = 0;
        for (ForestSnapshot.Node k : n.kids) w += measure(k);
        w = Math.max(1, w);
        width.put(n, w);
        return w;
    }

    private void place(ForestSnapshot.Node n, int x0, int y) {
        int w = width.get(n);
        pos.put(n, new Point(x0 + w * UNIT / 2, y));
        int x = x0;
        for (ForestSnapshot.Node k : n.kids) {
            place(k, x, y + LEVEL);
            x += width.get(k) * UNIT;
        }
    }

    private int depth(ForestSnapshot.Node n) {
        int d = 0;
        for (ForestSnapshot.Node k : n.kids) d = Math.max(d, 1 + depth(k));
        return d;
    }

    // ------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawGrid(g);
        drawCaption(g);
        if (snap.compact) { drawCompact(g); return; }
        if (snap.roots.isEmpty()) {
            String msg = snap.size == 0 && !"-".equals(snap.district)
                    ? "No pending requests in " + snap.district + "  \u00B7  empty heap, min = null"
                    : "Heap is empty";
            g.setFont(Theme.H1);
            FontMetrics fm = g.getFontMetrics();
            int cx = Math.max(getWidth(), 600) / 2, cy = Math.max(getHeight(), 400) / 2;
            g.setColor(Theme.alpha(Theme.ACCENT, 40));
            g.fillOval(cx - 34, cy - 80, 68, 68);
            g.setColor(Theme.ACCENT);
            g.setStroke(new BasicStroke(2.5f));
            g.drawArc(cx - 18, cy - 64, 36, 36, 20, 250);
            g.drawArc(cx - 9, cy - 55, 18, 18, 200, 250);
            g.setColor(Theme.INK);
            g.drawString(msg, cx - fm.stringWidth(msg) / 2, cy + 14);
            g.setFont(Theme.BODY);
            g.setColor(Theme.MUTED);
            String hint = "Use \u201CLoad demo scenario\u201D or add a request on the left";
            g.drawString(hint, cx - g.getFontMetrics().stringWidth(hint) / 2, cy + 38);
            return;
        }
        drawRootList(g);
        for (ForestSnapshot.Node r : snap.roots) drawEdges(g, r);
        for (ForestSnapshot.Node r : snap.roots) drawNodes(g, r, true);
        drawLegend(g);
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(Theme.GRID);
        for (int y = 12; y < getHeight(); y += 24)
            for (int x = 12; x < getWidth(); x += 24) g.fillRect(x, y, 2, 2);
    }

    private void drawCaption(Graphics2D g) {
        // step caption as a pill
        g.setFont(Theme.H2);
        FontMetrics fm = g.getFontMetrics();
        Color c = colorFor(snap.kind);
        int w = fm.stringWidth(snap.caption) + 28;
        g.setColor(Theme.alpha(c, 30));
        g.fillRoundRect(16, 14, w, 30, 30, 30);
        g.setColor(Theme.alpha(c, 120));
        g.drawRoundRect(16, 14, w, 30, 30, 30);
        g.setColor(c);
        g.drawString(snap.caption, 30, 34);

        // metric chips
        String[] chips = {
            "district  " + snap.district, "n = " + snap.size, "roots t = " + snap.rootCount,
            "marked m = " + snap.markedCount, "\u03A6 = t + 2m = " + snap.potential(),
            "max degree " + snap.maxDegree + "  \u2264  \u230Alog\u03C6 n\u230B = " + snap.degreeBound
        };
        g.setFont(Theme.SMALL);
        fm = g.getFontMetrics();
        int x = 16;
        for (String t : chips) {
            int cw = fm.stringWidth(t) + 18;
            g.setColor(Theme.CARD);
            g.fillRoundRect(x, 54, cw, 22, 22, 22);
            g.setColor(Theme.LINE);
            g.drawRoundRect(x, 54, cw, 22, 22, 22);
            g.setColor(Theme.MUTED);
            g.drawString(t, x + 9, 69);
            x += cw + 6;
        }
    }

    private Color colorFor(ForestSnapshot.Highlight k) {
        switch (k) {
            case CUT: case CASCADE: return Theme.HIGHLIGHT;
            case MARK: return Theme.MARK;
            case REMOVED: return Theme.ERR;
            case LINK: case MERGE: return Theme.ACCENT;
            case NEW: return Theme.OK;
            default: return Theme.INK;
        }
    }

    private void drawRootList(Graphics2D g) {
        List<ForestSnapshot.Node> roots = snap.roots;
        Point first = pos.get(roots.get(0));
        Point last = pos.get(roots.get(roots.size() - 1));
        Stroke old = g.getStroke();
        g.setColor(new Color(0x3E4D6E));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 5f}, 0f));
        g.drawLine(first.x, first.y, last.x, last.y);
        // wrap-around arc showing the list is circular
        if (roots.size() > 1) {
            g.draw(new QuadCurve2D.Float(last.x, last.y - R, (first.x + last.x) / 2f, TOP - 55, first.x, first.y - R));
        } else {
            g.drawOval(first.x - R - 8, first.y - R - 22, 2 * R + 16, 2 * R + 10);
        }
        g.setStroke(old);
        g.setFont(Theme.SMALL);
        g.setColor(Theme.MUTED);
        g.setColor(Theme.FAINT);
        g.drawString("\u21BB circular root list", last.x + R + 14, TOP + 4);
    }

    private void drawEdges(Graphics2D g, ForestSnapshot.Node n) {
        Point p = pos.get(n);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (ForestSnapshot.Node k : n.kids) {
            Point c = pos.get(k);
            g.setColor(new Color(0x34425F));
            int my = (p.y + R + c.y - R) / 2;
            g.draw(new java.awt.geom.CubicCurve2D.Float(p.x, p.y + R, p.x, my, c.x, my, c.x, c.y - R));
            drawEdges(g, k);
        }
    }

    private void drawNodes(Graphics2D g, ForestSnapshot.Node n, boolean isRoot) {
        Point p = pos.get(n);
        boolean hl = snap.highlighted.contains(n.id);
        boolean isMin = n.id.equals(snap.minId);

        Color fill = Theme.urgency(n.key);
        // soft glow
        for (int i = 3; i >= 1; i--) {
            g.setColor(Theme.alpha(hl ? Theme.HIGHLIGHT : fill, hl ? 26 * i : 10 * i));
            int e = (hl ? 8 : 5) * (4 - i);
            g.fillOval(p.x - R - e, p.y - R - e, 2 * (R + e), 2 * (R + e));
        }
        if (hl) {
            g.setColor(Theme.HIGHLIGHT);
            g.setStroke(new BasicStroke(2.5f));
            g.drawOval(p.x - R - 7, p.y - R - 7, 2 * R + 14, 2 * R + 14);
        }
        g.setPaint(new java.awt.GradientPaint(p.x, p.y - R, fill.brighter(), p.x, p.y + R, fill));
        g.fillOval(p.x - R, p.y - R, 2 * R, 2 * R);

        if (n.marked) {
            g.setColor(Theme.MARK);
            g.setStroke(new BasicStroke(4f));
        } else if (isMin) {
            g.setColor(Theme.MIN_RING);
            g.setStroke(new BasicStroke(4f));
        } else {
            g.setColor(Theme.alpha(Color.BLACK, 70));
            g.setStroke(new BasicStroke(1.2f));
        }
        g.drawOval(p.x - R, p.y - R, 2 * R, 2 * R);

        g.setFont(Theme.NODE);
        g.setColor(Theme.NODE_INK);
        String k = String.valueOf(n.key);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(k, p.x - fm.stringWidth(k) / 2, p.y + fm.getAscent() / 2 - 2);

        g.setFont(Theme.TINY);
        fm = g.getFontMetrics();
        String label = n.id + " \u00B7 " + n.type.substring(0, Math.min(3, n.type.length())).toUpperCase();
        int lw = fm.stringWidth(label);
        g.setColor(Theme.alpha(Theme.CARD, 235));
        g.fillRoundRect(p.x - lw / 2 - 7, p.y + R + 6, lw + 14, 17, 17, 17);
        g.setColor(Theme.LINE);
        g.drawRoundRect(p.x - lw / 2 - 7, p.y + R + 6, lw + 14, 17, 17, 17);
        g.setColor(Theme.INK);
        g.drawString(label, p.x - lw / 2, p.y + R + 18);

        if (n.marked) {
            g.setColor(Theme.MARK);
            g.fillOval(p.x + R - 8, p.y - R - 8, 18, 18);
            g.setColor(Color.WHITE);
            g.setFont(Theme.TINY);
            g.drawString("M", p.x + R - 3, p.y - R + 5);
        }
        if (isMin && isRoot) drawMinPointer(g, p);

        for (ForestSnapshot.Node c : n.kids) drawNodes(g, c, false);
    }

    private void drawMinPointer(Graphics2D g, Point p) {
        int y = p.y - R - 14;
        g.setColor(Theme.MIN_RING);
        Polygon tri = new Polygon(new int[]{p.x - 7, p.x + 7, p.x}, new int[]{y - 10, y - 10, y}, 3);
        g.fillPolygon(tri);
        g.setFont(Theme.TINY);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth("MIN") + 14;
        g.fillRoundRect(p.x - w / 2, y - 30, w, 17, 17, 17);
        g.setColor(Theme.NODE_INK);
        g.drawString("MIN", p.x - w / 2 + 7, y - 18);
    }

    private void drawLegend(Graphics2D g) {
        int y = getHeight() - 16, x = 14;
        g.setFont(Theme.SMALL);
        x = legendDot(g, x, y, Theme.urgency(1), null, "urgency 1 (critical)");
        x = legendDot(g, x, y, Theme.urgency(10), null, "urgency 10");
        x = legendDot(g, x, y, Theme.CARD, Theme.MIN_RING, "min pointer");
        x = legendDot(g, x, y, Theme.CARD, Theme.MARK, "marked (lost a child)");
        legendDot(g, x, y, new Color(255, 122, 0, 90), Theme.HIGHLIGHT, "changed in this step");
    }

    private int legendDot(Graphics2D g, int x, int y, Color fill, Color ring, String text) {
        g.setColor(fill);
        g.fillOval(x, y - 9, 11, 11);
        g.setColor(ring == null ? Theme.alpha(Color.BLACK, 80) : ring);
        g.setStroke(new BasicStroke(ring == null ? 1f : 2.5f));
        g.drawOval(x, y - 9, 11, 11);
        g.setColor(Theme.MUTED);
        g.drawString(text, x + 16, y);
        return x + 26 + g.getFontMetrics().stringWidth(text);
    }

    private void drawCompact(Graphics2D g) {
        int x = LEFT, y = TOP - 20;
        g.setFont(Theme.H2);
        g.setColor(Theme.INK);
        g.drawString("Compact view: " + snap.size + " pending requests (full drawing above "
                + ForestSnapshot.FULL_VIEW_LIMIT + " nodes would be unreadable)", x, y);
        g.setFont(Theme.BODY);
        g.drawString("Root list holds " + snap.rootCount + " trees. Histogram of root degrees:", x, y + 26);

        Map<Integer, Integer> hist = new TreeMap<>();
        for (int d : snap.rootDegrees) hist.merge(d, 1, Integer::sum);
        int max = 1;
        for (int v : hist.values()) max = Math.max(max, v);
        int barY = y + 50, bw = 44;
        int i = 0;
        for (Map.Entry<Integer, Integer> e : hist.entrySet()) {
            int h = (int) (180.0 * e.getValue() / max);
            int bx = x + i * (bw + 14);
            g.setColor(Theme.PRIORITY);
            g.fillRoundRect(bx, barY + 180 - h, bw, h, 10, 10);
            g.setColor(Theme.INK);
            g.drawString(String.valueOf(e.getValue()), bx + 4, barY + 175 - h);
            g.setColor(Theme.MUTED);
            g.drawString("deg " + e.getKey(), bx + 2, barY + 196);
            i++;
        }
        g.setColor(Theme.MUTED);
        g.drawString("After an extract-min every root has a DIFFERENT degree, so each bar is 1 and there are at most "
                + (snap.degreeBound + 1) + " bars.", x, barY + 226);
        g.drawString("Use 'Serve most urgent' to watch consolidation collapse the root list.", x, barY + 244);
    }
}
