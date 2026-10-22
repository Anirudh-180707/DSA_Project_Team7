package relief.ui;

import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Modern dark theme: one place for every colour and font, plus {@link #install()}
 * which configures Swing's built-in Nimbus look-and-feel with matching dark colours.
 * No external libraries are needed.
 */
final class Theme {
    private Theme() {}

    // ---------- surfaces
    static final Color BG          = new Color(0x0B1020);   // window background
    static final Color PANEL       = new Color(0x0F1628);   // side panels
    static final Color CARD        = new Color(0x161F35);   // cards
    static final Color CARD_HI     = new Color(0x1C2742);   // hovered / raised
    static final Color FIELD       = new Color(0x0C1324);   // text-field background
    static final Color CANVAS      = new Color(0x0D1426);   // heap drawing area
    static final Color LINE        = new Color(0x26324D);   // borders, dividers
    static final Color GRID        = new Color(0x1A2440);   // canvas dot grid

    // ---------- text
    static final Color INK         = new Color(0xE6EBF5);
    static final Color MUTED       = new Color(0x8B97B0);
    static final Color FAINT       = new Color(0x5D6A86);
    static final Color NODE_INK    = new Color(0x0B1020);   // text inside bright nodes

    // ---------- header
    static final Color HEADER      = new Color(0x0E1730);
    static final Color HEADER_2    = new Color(0x182A55);
    static final Color HEADER_INK  = new Color(0xF4F7FD);

    // ---------- accents
    static final Color ACCENT      = new Color(0x5B8CFF);
    static final Color ACCENT_2    = new Color(0x8B5CF6);
    static final Color PRIORITY    = new Color(0x5B8CFF);   // Fibonacci-heap policy
    static final Color FIFO        = new Color(0x56607A);   // arrival-order policy
    static final Color MIN_RING    = new Color(0xFFC24B);
    static final Color MARK        = new Color(0xFF4D8D);
    static final Color HIGHLIGHT   = new Color(0xFF9F43);
    static final Color OK          = new Color(0x34D399);
    static final Color WARN        = new Color(0xFBBF24);
    static final Color ERR         = new Color(0xF87171);

    /** Urgency 1 (critical, red) ... 10 (can wait, green): bright fills that read on a dark canvas. */
    static Color urgency(int u) {
        int k = Math.max(1, Math.min(10, u));
        float t = (k - 1) / 9f;
        float hue = t * 0.36f;                       // red -> green
        return Color.getHSBColor(hue, 0.62f, 0.98f);
    }

    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    // ---------- fonts (pick the best installed UI font)
    private static final String FAMILY = pickFamily("Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter", "Roboto", "Ubuntu", "SansSerif");
    private static final String MONO_FAMILY = pickFamily("Cascadia Mono", "Consolas", "SF Mono", "Menlo", "JetBrains Mono", "DejaVu Sans Mono", Font.MONOSPACED);

    static final Font TITLE  = new Font(FAMILY, Font.BOLD, 20);
    static final Font H1     = new Font(FAMILY, Font.BOLD, 16);
    static final Font H2     = new Font(FAMILY, Font.BOLD, 13);
    static final Font BODY   = new Font(FAMILY, Font.PLAIN, 13);
    static final Font SMALL  = new Font(FAMILY, Font.PLAIN, 11);
    static final Font TINY   = new Font(FAMILY, Font.BOLD, 10);
    static final Font NODE   = new Font(FAMILY, Font.BOLD, 15);
    static final Font KPI    = new Font(FAMILY, Font.BOLD, 26);
    static final Font MONO   = new Font(MONO_FAMILY, Font.PLAIN, 12);

    private static String pickFamily(String... wanted) {
        Set<String> have = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String w : wanted) if (have.contains(w)) return w;
        return wanted[wanted.length - 1];
    }

    /** Dark Nimbus: built into every JDK, so this works on any lab machine. */
    static void install() {
        try {
            put("control", PANEL);
            put("info", CARD);
            put("nimbusBase", new Color(0x1A2440));
            put("nimbusBlueGrey", new Color(0x2A3654));
            put("nimbusLightBackground", FIELD);
            put("nimbusFocus", ACCENT);
            put("nimbusSelectionBackground", ACCENT);
            put("nimbusSelectedText", Color.WHITE);
            put("nimbusDisabledText", FAINT);
            put("nimbusInfoBlue", ACCENT);
            put("nimbusGreen", OK);
            put("nimbusOrange", HIGHLIGHT);
            put("nimbusRed", ERR);
            put("nimbusAlertYellow", WARN);
            put("text", INK);
            put("textForeground", INK);
            put("textBackground", FIELD);
            put("background", PANEL);
            put("menu", PANEL);
            put("menuText", INK);
            put("ToolTip.background", CARD_HI);
            put("ToolTip.foreground", INK);
            UIManager.put("defaultFont", new FontUIResource(BODY));
            for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(i.getName())) { UIManager.setLookAndFeel(i.getClassName()); break; }
            }
            put("ComboBox.background", FIELD);
            put("ComboBox.foreground", INK);
            put("TextField.background", FIELD);
            put("FormattedTextField.background", FIELD);
            put("List.background", CARD);
            put("List.foreground", INK);
            put("ProgressBar.foreground", ACCENT);
        } catch (Exception ignored) {
            // fall back to whatever look-and-feel is available; the app still works
        }
    }

    private static void put(String key, Color c) { UIManager.put(key, new ColorUIResource(c)); }
}
