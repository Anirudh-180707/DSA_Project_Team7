package relief.ui;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Small custom-painted components that give the app its modern look. */
final class Ui {
    private Ui() {}

    static Graphics2D smooth(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    // =============================================================== button

    enum Kind { PRIMARY, SECONDARY, DANGER, GHOST }

    /** Rounded, flat button with hover and press states. */
    static final class ModernButton extends JButton {
        private static final long serialVersionUID = 1L;
        private final Kind kind;
        private boolean hover, press;

        ModernButton(Action a, Kind kind) {
            super(a);
            this.kind = kind;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(Theme.H2);
            setForeground(kind == Kind.PRIMARY ? Color.WHITE : Theme.INK);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; press = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { press = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e){ press = false; repaint(); }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, Math.max(36, d.height));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            int w = getWidth(), h = getHeight(), arc = 12;
            Color fill, border = null;
            switch (kind) {
                case PRIMARY:
                    Color top = press ? Theme.ACCENT.darker() : hover ? Theme.ACCENT.brighter() : Theme.ACCENT;
                    g.setPaint(new GradientPaint(0, 0, top, w, h, press ? Theme.ACCENT_2.darker() : Theme.ACCENT_2));
                    g.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                    fill = null;
                    break;
                case DANGER:
                    fill = press ? Theme.alpha(Theme.ERR, 70) : hover ? Theme.alpha(Theme.ERR, 45) : Theme.alpha(Theme.ERR, 22);
                    border = Theme.alpha(Theme.ERR, 120);
                    break;
                case GHOST:
                    fill = press ? Theme.CARD_HI : hover ? Theme.CARD : Theme.alpha(Theme.CARD, 0);
                    border = Theme.LINE;
                    break;
                default:
                    fill = press ? Theme.BG : hover ? Theme.CARD_HI : Theme.alpha(Theme.CARD_HI, 200);
                    border = hover ? Theme.alpha(Theme.ACCENT, 160) : Theme.LINE;
            }
            if (fill != null) {
                g.setColor(fill);
                g.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }
            if (border != null) {
                g.setColor(border);
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            String t = getText();
            int tw = fm.stringWidth(t);
            if (tw > w - 12) {                                   // shrink text politely if too narrow
                while (t.length() > 3 && fm.stringWidth(t + "…") > w - 12) t = t.substring(0, t.length() - 1);
                t = t + "…";
                tw = fm.stringWidth(t);
            }
            g.setColor(kind == Kind.DANGER ? Theme.ERR : getForeground());
            g.drawString(t, (w - tw) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    // =============================================================== card

    /** Rounded panel with a subtle border. */
    static class Card extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Color fill;

        Card(LayoutManager lm) { this(lm, Theme.CARD); }

        Card(LayoutManager lm, Color fill) {
            super(lm);
            this.fill = fill;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(Theme.LINE);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.dispose();
        }
    }

    // =============================================================== chip

    /** Rounded label ("pill") used for stats, status and complexity badges. */
    static final class Chip extends JLabel {
        private static final long serialVersionUID = 1L;
        private Color bg, fg;

        Chip(String text, Color fg, Color bg) {
            super(text);
            this.fg = fg;
            this.bg = bg;
            setFont(Theme.TINY);
            setForeground(fg);
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        }

        void set(String text, Color fg, Color bg) {
            this.fg = fg;
            this.bg = bg;
            setForeground(fg);
            setText(text);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (getText() == null || getText().trim().isEmpty()) return;
            Graphics2D g = smooth(g0);
            g.setColor(bg);
            int arc = Math.min(getHeight(), 24);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.setColor(Theme.alpha(fg, 90));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.dispose();
            super.paintComponent(g0);
        }
    }

    /** Header stat: small caption over a bold value, on a translucent pill. */
    static final class Stat extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String caption;
        private String value = "0";
        private final Color accent;

        Stat(String caption, Color accent) {
            this.caption = caption;
            this.accent = accent;
            setPreferredSize(new Dimension(86, 46));
        }

        void setValue(String v) { value = v; repaint(); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            g.setColor(new Color(255, 255, 255, 14));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g.setColor(new Color(255, 255, 255, 28));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g.setColor(accent);
            g.fillRoundRect(10, 10, 3, getHeight() - 20, 3, 3);
            g.setFont(Theme.TINY);
            g.setColor(new Color(0xA9B7D6));
            g.drawString(caption.toUpperCase(), 20, 19);
            g.setFont(Theme.H1);
            g.setColor(Theme.HEADER_INK);
            g.drawString(value, 20, 37);
            g.dispose();
        }
    }

    // =============================================================== layout helpers

    /** A panel inside a scroll pane that always fits the viewport width (no clipped fields). */
    static final class WidthTrackingPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;
        WidthTrackingPanel() { super(); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Paints a vertical gradient background (used for the header). */
    static final class GradientPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        GradientPanel(LayoutManager lm) { super(lm); setOpaque(false); }
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            g.setPaint(new GradientPaint(0, 0, Theme.HEADER, getWidth(), getHeight(), Theme.HEADER_2));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Theme.LINE);
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g.dispose();
        }
    }

    /** Small cyclone logo drawn with arcs. */
    static final class Logo extends JComponent {
        private static final long serialVersionUID = 1L;
        Logo() { setPreferredSize(new Dimension(44, 44)); }
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            g.setPaint(new GradientPaint(0, 0, Theme.ACCENT, 44, 44, Theme.ACCENT_2));
            g.fillRoundRect(0, 0, 44, 44, 14, 14);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(9, 9, 26, 26, 20, 250);
            g.drawArc(15, 15, 14, 14, 200, 250);
            g.fillOval(20, 20, 4, 4);
            g.dispose();
        }
    }

    static Insets pad(int v, int h) { return new Insets(v, h, v, h); }

    // =============================================================== status bar

    /** Rounded message bar whose text wraps onto as many lines as it needs. */
    static final class StatusBar extends JPanel {
        private static final long serialVersionUID = 1L;
        private final javax.swing.JTextArea text = new javax.swing.JTextArea();
        private Color tone = Theme.MUTED;

        StatusBar() {
            super(new java.awt.BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
            text.setEditable(false);
            text.setFocusable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setOpaque(false);
            text.setFont(Theme.H2);
            text.setBorder(BorderFactory.createEmptyBorder());
            add(text, java.awt.BorderLayout.CENTER);
            setVisible(false);
        }

        void show(String message, Color c) {
            tone = c;
            text.setForeground(c);
            text.setText(message);
            setVisible(true);
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            g.setColor(Theme.alpha(tone, 32));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g.setColor(Theme.alpha(tone, 140));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g.setColor(tone);
            g.fillRoundRect(0, 0, 5, getHeight() - 1, 5, 5);
            g.dispose();
        }
    }

    // =============================================================== combo boxes

    /** Dark, readable combo box (Nimbus draws light text on a light button otherwise). */
    static void styleCombo(javax.swing.JComboBox<?> cb) {
        cb.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override protected javax.swing.JButton createArrowButton() {
                javax.swing.JButton b = new javax.swing.JButton() {
                    private static final long serialVersionUID = 1L;
                    @Override protected void paintComponent(Graphics g0) {
                        Graphics2D g = smooth(g0);
                        g.setColor(Theme.FIELD);
                        g.fillRect(0, 0, getWidth(), getHeight());
                        g.setColor(Theme.MUTED);
                        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        int cx = getWidth() / 2, cy = getHeight() / 2;
                        g.drawLine(cx - 4, cy - 2, cx, cy + 2);
                        g.drawLine(cx, cy + 2, cx + 4, cy - 2);
                        g.dispose();
                    }
                };
                b.setBorder(BorderFactory.createEmptyBorder());
                b.setContentAreaFilled(false);
                b.setFocusPainted(false);
                b.setPreferredSize(new Dimension(28, 28));
                return b;
            }
            @Override public void paintCurrentValueBackground(Graphics g, Rectangle r, boolean hasFocus) {
                g.setColor(Theme.FIELD);
                g.fillRect(r.x, r.y, r.width, r.height);
            }
        });
        cb.setBackground(Theme.FIELD);
        cb.setForeground(Theme.INK);
        cb.setFont(Theme.BODY);
        cb.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE, 1, true),
                BorderFactory.createEmptyBorder(0, 6, 0, 0)));
        cb.setRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                          boolean sel, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
                l.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
                l.setFont(Theme.BODY);
                l.setOpaque(true);
                l.setBackground(sel && index >= 0 ? Theme.ACCENT : (index < 0 ? Theme.FIELD : Theme.CARD));
                l.setForeground(sel && index >= 0 ? Color.WHITE : Theme.INK);
                if (value == null || value.toString().isEmpty()) l.setText(" ");
                return l;
            }
        });
        if (cb.isEditable() && cb.getEditor().getEditorComponent() instanceof javax.swing.JTextField) {
            javax.swing.JTextField tf = (javax.swing.JTextField) cb.getEditor().getEditorComponent();
            tf.setBackground(Theme.FIELD);
            tf.setForeground(Theme.INK);
            tf.setCaretColor(Theme.INK);
            tf.setFont(Theme.BODY);
            tf.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        }
    }

    // =============================================================== scrollbars

    /** Slim dark scrollbars for every scroll pane. */
    static void styleScroll(javax.swing.JScrollPane sp) {
        for (javax.swing.JScrollBar b : new javax.swing.JScrollBar[]{sp.getVerticalScrollBar(), sp.getHorizontalScrollBar()}) {
            b.setUI(new SlimScrollBarUI());
            b.setOpaque(false);
            if (b.getOrientation() == javax.swing.JScrollBar.VERTICAL) b.setPreferredSize(new Dimension(10, 0));
            else b.setPreferredSize(new Dimension(0, 10));
        }
        sp.setCorner(javax.swing.JScrollPane.LOWER_RIGHT_CORNER, null);
    }

    static final class SlimScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override protected javax.swing.JButton createDecreaseButton(int o) { return zero(); }
        @Override protected javax.swing.JButton createIncreaseButton(int o) { return zero(); }
        private javax.swing.JButton zero() {
            javax.swing.JButton b = new javax.swing.JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
        @Override protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g = smooth(g0);
            g.setColor(isThumbRollover() ? new Color(0x4A5A7E) : new Color(0x2E3B58));
            g.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g.dispose();
        }
    }
}
