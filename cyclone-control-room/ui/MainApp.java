package relief.ui;

import relief.core.ControlRoom;
import relief.core.OpResult;
import relief.data.DatasetGenerator;
import relief.heap.FibNode;
import relief.heap.FibonacciHeap;
import relief.heap.HeapStats;
import relief.heap.StepListener;
import relief.model.ReliefRequest;
import relief.model.RequestType;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/**
 * "Cyclone Control Room" - single-window simulator.
 *   left   : control panel (the four core operations + cancel + demo helpers)
 *   centre : heap-forest visualiser with step-by-step animation + operation log
 *   right  : impact panel (replay of a whole cyclone, priority vs FIFO)
 *
 * The heap never depends on this class: if Swing fails, run the console mode.
 */
public final class MainApp extends JFrame {

    private final ControlRoom room = new ControlRoom();
    private final HeapView view = new HeapView();
    private final JTextPane logPane = new JTextPane();
    private final Ui.Stat stPending = new Ui.Stat("Pending", Theme.ACCENT);
    private final Ui.Stat stDistricts = new Ui.Stat("Districts", Theme.ACCENT_2);
    private final Ui.Stat stRaises = new Ui.Stat("Raises", Theme.MARK);
    private final Ui.Stat stMerges = new Ui.Stat("Merges", Theme.OK);
    private final Ui.Stat stServes = new Ui.Stat("Serves", Theme.MIN_RING);
    private final Ui.Stat stLinks = new Ui.Stat("Links", Theme.HIGHLIGHT);
    private final Ui.Stat stCuts = new Ui.Stat("Cuts", Theme.ERR);
    private final Ui.StatusBar status = new Ui.StatusBar();

    // animation
    private final Deque<ForestSnapshot> frames = new ArrayDeque<>();
    private final Timer animTimer;
    private boolean capturing = false;
    private final JCheckBox animate = new JCheckBox("Animate each step", true);
    private final JSlider animSpeed = new JSlider(1, 10, 5);

    // controls
    private final JComboBox<String> viewDistrict = new JComboBox<>();
    private final JComboBox<String> addDistrict = new JComboBox<>();
    private final JComboBox<RequestType> addType = new JComboBox<>(RequestType.values());
    private final JSpinner addUrgency = new JSpinner(new SpinnerNumberModel(5, 1, 10, 1));
    private final JComboBox<String> requestPick = new JComboBox<>();
    private final JSpinner raiseTo = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
    private final JComboBox<String> mergeFrom = new JComboBox<>();
    private final JComboBox<String> mergeInto = new JComboBox<>();
    private boolean updatingCombos = false;

    public MainApp() {
        super("Cyclone Control Room — Fibonacci Heap Relief Prioritisation");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setBackground(Theme.BG);
        setLayout(new BorderLayout());

        room.setListener(new Recorder());
        animTimer = new Timer(500, e -> nextFrame());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildControls(), BorderLayout.WEST);
        add(buildCentre(), BorderLayout.CENTER);
        add(new ImpactPanel(this::logInfo), BorderLayout.EAST);

        logInfo("Ready. Urgency 1 = critical ... 10 = can wait. 'Raise urgency' means a SMALLER number (decrease-key).");
        logInfo("Tip: click 'Load demo scenario' for a heap that is one step away from a cascading cut.");
        for (JComboBox<?> cb : new JComboBox<?>[]{viewDistrict, addDistrict, addType, requestPick, mergeFrom, mergeInto})
            Ui.styleCombo(cb);
        requestPick.addActionListener(e -> SwingUtilities.invokeLater(this::showRequestStart));
        refreshAll();

        setMinimumSize(new Dimension(1200, 740));
        setSize(1440, 860);
        setLocationRelativeTo(null);
    }

    // ================================================================= layout

    private JComponent buildHeader() {
        Ui.GradientPanel p = new Ui.GradientPanel(new BorderLayout(16, 0));
        p.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

        JPanel brand = new JPanel(new BorderLayout(12, 0));
        brand.setOpaque(false);
        brand.add(new Ui.Logo(), BorderLayout.WEST);
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        JLabel t = new JLabel("Cyclone Control Room");
        t.setFont(Theme.TITLE);
        t.setForeground(Theme.HEADER_INK);
        JLabel sub = new JLabel("Disaster-relief prioritisation \u00B7 Fibonacci heap (Fredman & Tarjan, 1987)");
        sub.setFont(Theme.SMALL);
        sub.setForeground(new Color(0xA9B7D6));
        titles.add(t);
        titles.add(sub);
        brand.add(titles, BorderLayout.CENTER);

        JPanel stats = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        stats.setOpaque(false);
        for (Ui.Stat st : new Ui.Stat[]{stPending, stDistricts, stRaises, stMerges, stServes, stLinks, stCuts}) stats.add(st);
        stCuts.setPreferredSize(new Dimension(110, 46));

        p.add(brand, BorderLayout.WEST);
        p.add(stats, BorderLayout.EAST);
        return p;
    }

    private JComponent buildControls() {
        Ui.WidthTrackingPanel p = new Ui.WidthTrackingPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Theme.PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 12));

        addDistrict.setEditable(true);
        requestPick.setEditable(true);
        requestPick.setToolTipText("Pick a pending request or type its id, e.g. R-7 or 7");
        for (JComponent c : new JComponent[]{addDistrict, addType, addUrgency, requestPick, raiseTo, mergeFrom, mergeInto})
            c.setPreferredSize(new Dimension(60, 32));        // let the card decide the width

        // 1. add
        Ui.Card c1 = card("1", "Add request", "insert \u00B7 O(1)", Theme.ACCENT);
        c1.add(row("District", addDistrict));
        c1.add(row("Type", addType));
        c1.add(row("Urgency", addUrgency));
        c1.add(Box.createVerticalStrut(6));
        c1.add(button("Add request", this::doAdd, Ui.Kind.PRIMARY));
        p.add(c1);
        p.add(gap());

        // 2. raise
        Ui.Card c2 = card("2", "Raise urgency", "decrease-key \u00B7 O(1) am.", Theme.MARK);
        c2.add(row("Request", requestPick));
        c2.add(row("New urgency", raiseTo));
        c2.add(Box.createVerticalStrut(6));
        JPanel rr = new JPanel(new GridLayout(1, 2, 8, 0));
        rr.setOpaque(false);
        rr.add(new Ui.ModernButton(new Act("Raise urgency", this::doRaise), Ui.Kind.PRIMARY));
        rr.add(new Ui.ModernButton(new Act("Cancel", this::doCancel), Ui.Kind.DANGER));
        c2.add(fit(rr));
        p.add(c2);
        p.add(gap());

        // 3. merge
        Ui.Card c3 = card("3", "Merge districts", "union \u00B7 O(1)", Theme.OK);
        c3.add(row("Merge", mergeFrom));
        c3.add(row("into", mergeInto));
        c3.add(Box.createVerticalStrut(6));
        c3.add(button("Merge districts", this::doMerge, Ui.Kind.PRIMARY));
        p.add(c3);
        p.add(gap());

        // 4. serve
        Ui.Card c4 = card("4", "Serve most urgent", "extract-min \u00B7 O(log n) am.", Theme.MIN_RING);
        c4.add(button("Serve in viewed district", this::doServe, Ui.Kind.PRIMARY));
        c4.add(Box.createVerticalStrut(8));
        c4.add(button("Serve most urgent overall", this::doServeGlobal, Ui.Kind.SECONDARY));
        p.add(c4);
        p.add(gap());

        // helpers
        Ui.Card c5 = card("5", "Demo helpers", "", Theme.ACCENT_2);
        c5.add(button("Load demo scenario", this::loadScenario, Ui.Kind.SECONDARY));
        c5.add(Box.createVerticalStrut(8));
        c5.add(button("Run edge-case checklist", this::runEdgeCases, Ui.Kind.SECONDARY));
        c5.add(Box.createVerticalStrut(8));
        JPanel hr = new JPanel(new GridLayout(1, 2, 8, 0));
        hr.setOpaque(false);
        hr.add(new Ui.ModernButton(new Act("+20 random", this::addRandom), Ui.Kind.GHOST));
        hr.add(new Ui.ModernButton(new Act("Reset all", this::resetAll), Ui.Kind.DANGER));
        c5.add(fit(hr));
        c5.add(Box.createVerticalStrut(10));
        animate.setOpaque(false);
        animate.setForeground(Theme.INK);
        animate.setFont(Theme.BODY);
        c5.add(fit(animate));
        JPanel sp = new JPanel(new BorderLayout(8, 0));
        sp.setOpaque(false);
        JLabel sl = new JLabel("Speed");
        sl.setFont(Theme.SMALL);
        sl.setForeground(Theme.MUTED);
        animSpeed.setOpaque(false);
        sp.add(sl, BorderLayout.WEST);
        sp.add(animSpeed, BorderLayout.CENTER);
        c5.add(fit(sp));
        p.add(c5);
        p.add(Box.createVerticalGlue());

        JScrollPane sc = new JScrollPane(p);
        sc.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.LINE));
        sc.setPreferredSize(new Dimension(340, 600));
        sc.getViewport().setBackground(Theme.PANEL);
        sc.getVerticalScrollBar().setUnitIncrement(16);
        sc.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        Ui.styleScroll(sc);
        return sc;
    }

    private JComponent buildCentre() {
        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setBackground(Theme.BG);
        top.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftTop.setOpaque(false);
        JLabel vl = new JLabel("Viewing district");
        vl.setFont(Theme.H2);
        vl.setForeground(Theme.MUTED);
        leftTop.add(vl);
        viewDistrict.setPreferredSize(new Dimension(190, 32));
        viewDistrict.addActionListener(e -> {
            if (updatingCombos) return;
            Object v = viewDistrict.getSelectedItem();
            if (v != null) { stopAnimation(); showDistrict(v.toString(), "Viewing " + v); }
        });
        leftTop.add(viewDistrict);
        top.add(leftTop, BorderLayout.NORTH);
        // status message gets its own full-width line so it is never cut off
        JPanel st = new JPanel(new BorderLayout());
        st.setOpaque(false);
        st.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        st.add(status, BorderLayout.CENTER);
        top.add(st, BorderLayout.CENTER);

        JScrollPane heapScroll = new JScrollPane(view);
        heapScroll.setBorder(BorderFactory.createEmptyBorder());
        heapScroll.getViewport().setBackground(Theme.CANVAS);
        heapScroll.getHorizontalScrollBar().setUnitIncrement(20);
        heapScroll.getVerticalScrollBar().setUnitIncrement(20);
        Ui.styleScroll(heapScroll);
        Ui.Card heapCard = new Ui.Card(new BorderLayout(), Theme.CANVAS);
        heapCard.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        heapCard.add(heapScroll);

        logPane.setEditable(false);
        logPane.setFont(Theme.MONO);
        logPane.setBackground(new Color(0x090E1B));
        logPane.setForeground(Theme.INK);
        logPane.setCaretColor(Theme.INK);
        logPane.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JScrollPane logScroll = new JScrollPane(logPane);
        logScroll.setBorder(BorderFactory.createEmptyBorder());
        Ui.styleScroll(logScroll);
        Ui.Card logCard = new Ui.Card(new BorderLayout(0, 6), new Color(0x090E1B));
        logCard.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));
        JLabel lt = new JLabel("  OPERATION LOG");
        lt.setFont(Theme.TINY);
        lt.setForeground(Theme.MUTED);
        logCard.add(lt, BorderLayout.NORTH);
        logCard.add(logScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, heapCard, logCard);
        split.setResizeWeight(0.72);
        split.setDividerSize(10);
        split.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        split.setOpaque(false);
        split.setBackground(Theme.BG);

        JPanel c = new JPanel(new BorderLayout());
        c.setBackground(Theme.BG);
        c.add(top, BorderLayout.NORTH);
        c.add(split, BorderLayout.CENTER);
        return c;
    }

    // ================================================================= operations

    private void doAdd() {
        String d = text(addDistrict);
        RequestType t = (RequestType) addType.getSelectedItem();
        int u = (Integer) addUrgency.getValue();
        perform(() -> room.addRequest(d, t, u), d);
    }

    private void doRaise() {
        String id = selectedId();
        int u = (Integer) raiseTo.getValue();
        FibNode<ReliefRequest> h = room.handle(normaliseId(id));
        String d = h == null ? null : h.getValue().originDistrict();
        perform(() -> room.raiseUrgency(id, u), d);
    }

    private void doCancel() {
        String id = selectedId();
        FibNode<ReliefRequest> h = room.handle(normaliseId(id));
        String d = h == null ? null : h.getValue().originDistrict();
        perform(() -> room.cancel(id), d);
    }

    private void doMerge() {
        String a = text(mergeFrom), b = text(mergeInto);
        openIfListed(a);
        openIfListed(b);
        perform(() -> room.mergeDistricts(a, b), b, a);
    }

    /** Scroll the Request box back to the start so the id (R-2 ...) is always visible. */
    private void showRequestStart() {
        java.awt.Component ed = requestPick.getEditor().getEditorComponent();
        if (ed instanceof javax.swing.JTextField) ((javax.swing.JTextField) ed).setCaretPosition(0);
    }

    /** Districts shown in the drop-downs exist even before their first request. */
    private void openIfListed(String d) {
        String n = ControlRoom.normalise(d);
        if (!n.isEmpty() && !room.mergeAliases().containsKey(n) && room.heapOf(n) == null) room.addDistrict(n);
    }

    private void doServe() {
        String d = text(viewDistrict);
        if (d.isEmpty()) { logError("Choose a district to serve."); setStatus("Choose a district to serve.", Theme.ERR); return; }
        if (room.heapOf(d) == null) {
            String m = "No pending requests in " + d + " \u2014 nothing to serve.";
            logError(m); setStatus(m, Theme.ERR); return;
        }
        perform(() -> room.serve(d), d);
    }

    private void doServeGlobal() {
        String d = room.globalMostUrgentDistrict();
        perform(room::serveGlobal, d);
    }

    /**
     * Runs one control-room operation, records the animation frames, then logs the
     * actual work done and the change in potential so the amortised cost is visible.
     */
    private void perform(Supplier<OpResult> op, String district, String... alsoMerged) {
        stopAnimation();
        HeapStats before = room.stats().copy();
        int phiBefore = potentialOf(district);
        for (String m : alsoMerged) phiBefore += potentialOf(m);

        frames.clear();
        FibonacciHeap<ReliefRequest> target = district == null ? null : room.heapOf(district);
        capturing = animate.isSelected();
        if (capturing && target != null && target.size() <= ForestSnapshot.FULL_VIEW_LIMIT) {
            frames.add(ForestSnapshot.of(room.resolve(district), target, "Before", ForestSnapshot.Highlight.NONE));
        }
        OpResult r;
        try {
            r = op.get();
        } catch (RuntimeException ex) {                    // should never happen: belt and braces
            r = OpResult.fail("Internal error handled safely: " + ex);
        } finally {
            capturing = false;
        }

        if (!r.ok) {
            frames.clear();
            logError(r.message);
            setStatus(r.message, Theme.ERR);
            return;
        }
        logOk(r.message);
        setStatus(r.message, Theme.OK);

        HeapStats after = room.stats();
        int phiAfter = potentialOf(r.district);
        long actual = (after.links - before.links) + (after.cuts - before.cuts)
                + (after.rootsScanned - before.rootsScanned);
        logCost(String.format("   work: links %d, cuts %d (of which cascading %d), roots scanned %d  |  ΔΦ = %+d  (Φ = t + 2m now %d)",
                after.links - before.links, after.cuts - before.cuts, after.cascadingCuts - before.cascadingCuts,
                after.rootsScanned - before.rootsScanned, phiAfter - phiBefore, phiAfter));
        if (actual > 0)
            logCost(String.format("   amortised cost = actual (%d + 1) + ΔΦ (%+d) = %d", actual,
                    phiAfter - phiBefore, actual + 1 + (phiAfter - phiBefore)));

        refreshCombos(r.district);
        String id = r.request == null ? null : r.request.id();
        frames.add(ForestSnapshot.of(r.district, room.heapOf(r.district),
                "Done: " + shorten(r.message), ForestSnapshot.Highlight.NEW, id));
        if (animate.isSelected() && frames.size() > 1) {
            animTimer.setDelay(1400 - animSpeed.getValue() * 125);
            nextFrame();
            animTimer.start();
        } else {
            view.show(frames.peekLast());
            frames.clear();
        }
        refreshHeader();
    }

    private int potentialOf(String district) {
        if (district == null) return 0;
        FibonacciHeap<ReliefRequest> h = room.heapOf(district);
        return h == null ? 0 : h.potential();
    }

    // ================================================================= animation

    /** Heap observer: takes a snapshot at every structural step while capturing. */
    private final class Recorder implements StepListener<ReliefRequest> {
        @Override
        public void onStep(FibonacciHeap<ReliefRequest> heap, Step step, FibNode<ReliefRequest> a, FibNode<ReliefRequest> b) {
            if (!capturing || heap.size() > ForestSnapshot.FULL_VIEW_LIMIT) return;
            String district = districtOf(heap);
            String ida = a == null ? null : a.getValue().id();
            String idb = b == null ? null : b.getValue().id();
            String cap;
            ForestSnapshot.Highlight k;
            switch (step) {
                case INSERT:
                    cap = "Insert " + ida + " (urgency " + a.getKey() + ") as a one-node tree in the root list — O(1)";
                    k = ForestSnapshot.Highlight.NEW; break;
                case MERGE:
                    cap = "Union: splice the two circular root lists (4 pointer updates) and compare mins — O(1)";
                    k = ForestSnapshot.Highlight.MERGE; break;
                case PROMOTE_CHILDREN:
                    cap = "Extract-min: move the children of " + ida + " into the root list";
                    k = ForestSnapshot.Highlight.REMOVED; break;
                case REMOVE_MIN:
                    cap = "Remove " + ida + " (urgency " + a.getKey() + ") — now consolidate roots of equal degree";
                    k = ForestSnapshot.Highlight.REMOVED; break;
                case LINK:
                    cap = "Link: " + ida + " becomes a child of " + idb + " (both had degree " + (b.getDegree() - 1) + ")";
                    k = ForestSnapshot.Highlight.LINK; break;
                case CONSOLIDATED:
                    cap = "Consolidation done: every root degree is distinct; new min = " + (ida == null ? "none" : ida);
                    k = ForestSnapshot.Highlight.NONE; break;
                case DECREASE:
                    cap = ida + " urgency lowered to " + a.getKey() + (idb == null ? " (it is a root: just update min)"
                            : (a.getKey() < b.getKey() || (a.getKey() == b.getKey() && a.getSeq() < b.getSeq())
                               ? " — now more urgent than parent " + idb + ": heap order broken"
                               : " — still not more urgent than parent " + idb + ": no cut needed"));
                    k = ForestSnapshot.Highlight.NEW; break;
                case CUT:
                    cap = "Cut " + ida + " from parent " + idb + " and add it to the root list (unmarked)";
                    k = ForestSnapshot.Highlight.CUT; break;
                case MARK:
                    cap = ida + " lost its first child → marked (one more loss and it will be cut)";
                    k = ForestSnapshot.Highlight.MARK; break;
                case CASCADING_CUT:
                    cap = "Cascading cut: " + ida + " was already marked, so it is cut from " + idb + " too";
                    k = ForestSnapshot.Highlight.CASCADE; break;
                case DELETE:
                    cap = "Cancel " + ida + ": cut it to the root list, make it min, then extract-min";
                    k = ForestSnapshot.Highlight.REMOVED; break;
                default:
                    cap = step.name();
                    k = ForestSnapshot.Highlight.NONE;
            }
            frames.add(ForestSnapshot.of(district, heap, cap, k, ida, idb));
        }
    }

    private String districtOf(FibonacciHeap<ReliefRequest> h) {
        for (String d : room.activeDistricts()) if (room.heapOf(d) == h) return d;
        return "?";
    }

    private void nextFrame() {
        ForestSnapshot f = frames.pollFirst();
        if (f == null) { animTimer.stop(); return; }
        view.show(f);
        if (frames.isEmpty()) animTimer.stop();
    }

    private void stopAnimation() {
        animTimer.stop();
        if (!frames.isEmpty()) view.show(frames.peekLast());
        frames.clear();
    }

    private void showDistrict(String d, String caption) {
        view.show(ForestSnapshot.of(room.resolve(d), room.heapOf(d), caption, ForestSnapshot.Highlight.NONE));
    }

    // ================================================================= demo helpers

    /**
     * Builds Puri as ONE binomial-shaped tree of 8 nodes (insert 9, extract 1),
     * so a raise on a grandchild cuts + marks, and a raise on its sibling triggers
     * a cascading cut. Khordha gets 3 requests, Ganjam is opened empty (merge edge case).
     */
    private void loadScenario() {
        stopAnimation();
        room.reset();
        int[] puri = {3, 8, 6, 9, 7, 10, 5, 8, 9};
        RequestType[] types = RequestType.values();
        for (int i = 0; i < puri.length; i++) room.addRequest("Puri", types[i % 4], puri[i]);
        room.addRequest("Khordha", RequestType.MEDICINE, 4);
        room.addRequest("Khordha", RequestType.RESCUE, 2);
        room.addRequest("Khordha", RequestType.FOOD, 7);
        room.addDistrict("Ganjam");
        OpResult r = room.serve("Puri");
        logInfo("Demo scenario loaded. Puri: 9 requests inserted, then " + r.request.id()
                + " served, so consolidation built ONE tree of 8 nodes. Khordha: 3 requests. Ganjam: empty.");

        FibNode<ReliefRequest> root = room.peek("Puri");
        FibNode<ReliefRequest> mid = null;
        for (FibNode<ReliefRequest> c : root.children()) if (c.getDegree() == 2) mid = c;
        if (mid != null) {
            List<FibNode<ReliefRequest>> kids = mid.children();
            logInfo("Try this:  (a) raise " + kids.get(0).getValue().id() + " to 1  → cut, parent "
                    + mid.getValue().id() + " gets MARKED.");
            logInfo("           (b) raise " + kids.get(1).getValue().id() + " to 1  → cut, and "
                    + mid.getValue().id() + " is CASCADING-CUT to the root list.");
            logInfo("           (c) merge Puri into Khordha (O(1)),  (d) merge Ganjam into Khordha (empty merge),  (e) serve.");
            requestPickHint = kids.get(0).getValue().id();
        }
        room.stats().reset();
        refreshCombos("Puri");
        showDistrict("Puri", "Demo scenario: Puri after one extract-min (one tree, degree 3)");
        refreshHeader();
    }

    private String requestPickHint = null;

    private void runEdgeCases() {
        ControlRoom t = new ControlRoom();
        logInfo("Edge-case checklist (runs on a scratch control room, your data is untouched):");
        t.addDistrict("Puri");
        edge("Serve from an EMPTY district", t.serve("Puri"));
        edge("Serve overall when nothing is pending", t.serveGlobal());
        edge("Unknown district", t.serve("Atlantis"));
        edge("Urgency out of range (0)", t.addRequest("Puri", RequestType.FOOD, 0));
        edge("Urgency out of range (11)", t.addRequest("Puri", RequestType.FOOD, 11));
        edge("Blank district name", t.addRequest("   ", RequestType.FOOD, 4));
        OpResult a = t.addRequest("Puri", RequestType.RESCUE, 5);
        edge("Single-node heap: add one request", a);
        edge("Raise to the SAME urgency (not lower)", t.raiseUrgency(a.request.id(), 5));
        edge("Raise to a LARGER number (less urgent)", t.raiseUrgency(a.request.id(), 8));
        edge("Raise an id that does not exist", t.raiseUrgency("R-999", 1));
        edge("Merge a district with itself", t.mergeDistricts("Puri", "Puri"));
        t.addDistrict("Ganjam");
        edge("Merge an EMPTY district into another", t.mergeDistricts("Ganjam", "Puri"));
        edge("Single-node heap: serve it", t.serve("Puri"));
        edge("Raise a request that was already served", t.raiseUrgency(a.request.id(), 1));
        String x = t.addRequest("Puri", RequestType.FOOD, 3).request.id();
        String y = t.addRequest("Puri", RequestType.BOAT, 3).request.id();
        String z = t.addRequest("Puri", RequestType.MEDICINE, 3).request.id();
        StringBuilder order = new StringBuilder();
        for (int i = 0; i < 3; i++) order.append(t.serve("Puri").request.id()).append(i < 2 ? ", " : "");
        boolean fifo = order.toString().equals(x + ", " + y + ", " + z);
        logLine((fifo ? "  ✔ " : "  ✖ ") + "Duplicate urgencies (3,3,3) served in arrival order: " + order, fifo ? Theme.OK : Theme.ERR);
        edge("Cancel a request that no longer exists", t.cancel(x));
        logInfo("All edge cases handled without exceptions.");
    }

    private void edge(String what, OpResult r) {
        logLine("  ✔ " + what + " → " + (r.ok ? "OK: " : "rejected: ") + r.message, r.ok ? Theme.OK : Theme.WARN);
    }

    private void addRandom() {
        Random rnd = new Random();
        String d = text(viewDistrict);
        if (d.isEmpty()) d = "Cuttack";
        stopAnimation();
        for (int i = 0; i < 20; i++)
            room.addRequest(d, RequestType.values()[rnd.nextInt(4)], 1 + rnd.nextInt(10));
        logOk("Added 20 random requests to " + room.resolve(d) + " (20 × O(1) inserts: the root list just grows).");
        refreshCombos(room.resolve(d));
        showDistrict(d, "20 inserts: 20 new single-node trees, nothing consolidated yet (lazy)");
        refreshHeader();
    }

    private void resetAll() {
        stopAnimation();
        room.reset();
        logInfo("Everything reset.");
        refreshAll();
        view.show(ForestSnapshot.empty("Reset. Add a request or load the demo scenario."));
    }

    // ================================================================= refresh

    private void refreshAll() {
        refreshCombos(null);
        refreshHeader();
    }

    private void refreshCombos(String focusDistrict) {
        updatingCombos = true;
        try {
            List<String> active = room.activeDistricts();
            String keepAdd = text(addDistrict);
            Set<String> all = new LinkedHashSet<>(active);
            for (String d : DatasetGenerator.DISTRICTS) all.add(d);
            addDistrict.setModel(new DefaultComboBoxModel<>(all.toArray(new String[0])));
            addDistrict.setSelectedItem(keepAdd.isEmpty() ? (focusDistrict != null ? focusDistrict : "Puri") : keepAdd);

            String[] act = active.toArray(new String[0]);
            Set<String> listed = new LinkedHashSet<>(active);
            for (String d : DatasetGenerator.DISTRICTS)
                if (!room.mergeAliases().containsKey(d)) listed.add(d);
            String[] lst = listed.toArray(new String[0]);
            String oldFrom = text(mergeFrom), oldInto = text(mergeInto);
            String oldView = text(viewDistrict);
            mergeFrom.setModel(new DefaultComboBoxModel<>(lst));
            mergeInto.setModel(new DefaultComboBoxModel<>(lst));
            if (listed.contains(oldFrom)) mergeFrom.setSelectedItem(oldFrom);
            if (listed.contains(oldInto)) mergeInto.setSelectedItem(oldInto);
            else if (lst.length > 1) mergeInto.setSelectedIndex(1);
            if (lst.length > 1 && text(mergeFrom).equals(text(mergeInto)))
                mergeInto.setSelectedItem(lst[0].equals(text(mergeFrom)) ? lst[1] : lst[0]);

            viewDistrict.setModel(new DefaultComboBoxModel<>(lst));
            if (focusDistrict != null && listed.contains(room.resolve(focusDistrict)))
                viewDistrict.setSelectedItem(room.resolve(focusDistrict));
            else if (listed.contains(oldView))
                viewDistrict.setSelectedItem(oldView);

            DefaultComboBoxModel<String> reqs = new DefaultComboBoxModel<>();
            for (String d : active) {
                for (String id : room.pendingIds(d)) {
                    FibNode<ReliefRequest> n = room.handle(id);
                    reqs.addElement(id + "   u" + n.getKey() + "  " + n.getValue().type() + "  (" + d + ")");
                }
            }
            requestPick.setModel(reqs);
            SwingUtilities.invokeLater(this::showRequestStart);
            if (requestPickHint != null) {
                for (int i = 0; i < reqs.getSize(); i++)
                    if (reqs.getElementAt(i).startsWith(requestPickHint + " ")) requestPick.setSelectedIndex(i);
                requestPickHint = null;
            }
        } finally {
            updatingCombos = false;
        }
    }

    private void refreshHeader() {
        HeapStats s = room.stats();
        stPending.setValue(String.valueOf(room.totalPending()));
        stDistricts.setValue(String.valueOf(room.activeDistricts().size()));
        stRaises.setValue(String.valueOf(s.decreaseKeys));
        stMerges.setValue(String.valueOf(s.unions));
        stServes.setValue(String.valueOf(s.extractMins));
        stLinks.setValue(String.valueOf(s.links));
        stCuts.setValue(s.cuts + (s.cascadingCuts > 0 ? "  (" + s.cascadingCuts + " casc.)" : ""));
    }

    // ================================================================= log

    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private void logInfo(String m)  { logLine(m, Theme.INK); }
    private void logOk(String m)    { logLine("✔ " + m, Theme.OK); }
    private void logError(String m) { logLine("✖ " + m, Theme.ERR); }
    private void logCost(String m)  { logLine(m, Theme.MUTED); }

    private void logLine(String m, Color c) {
        StyledDocument doc = logPane.getStyledDocument();
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, c);
        try {
            doc.insertString(doc.getLength(), LocalTime.now().format(HMS) + "  " + m + "\n", a);
        } catch (BadLocationException ignored) { }
        logPane.setCaretPosition(doc.getLength());
    }

    private void setStatus(String m, Color c) {
        status.show((c == Theme.ERR ? "Error:  " : "Done:  ") + m, c);
    }

    // ================================================================= small helpers

    private static String shorten(String s) { return s.length() > 95 ? s.substring(0, 92) + "…" : s; }

    private static String text(JComboBox<?> c) {
        Object o = c.isEditable() ? c.getEditor().getItem() : c.getSelectedItem();
        if (o == null) o = c.getSelectedItem();
        return o == null ? "" : o.toString().trim();
    }

    private String selectedId() {
        String s = text(requestPick);
        return s.isEmpty() ? "" : s.split("\\s+")[0];
    }

    private static String normaliseId(String id) {
        String s = id == null ? "" : id.trim().toUpperCase();
        return s.matches("\\d+") ? "R-" + s : s;
    }

    /** A rounded card with a coloured number badge, a title and a complexity chip. */
    private static Ui.Card card(String badge, String title, String complexity, Color accent) {
        Ui.Card c = new Ui.Card(null);
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        Ui.Chip b = new Ui.Chip(badge, Color.WHITE, accent);
        b.setFont(Theme.H2);
        b.setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
        head.add(b, BorderLayout.WEST);
        JLabel t = new JLabel(title);
        t.setFont(Theme.H1);
        t.setForeground(Theme.INK);
        head.add(t, BorderLayout.CENTER);
        if (!complexity.isEmpty()) {
            Ui.Chip cx = new Ui.Chip(complexity, accent, Theme.alpha(accent, 28));
            head.add(cx, BorderLayout.SOUTH);
            cx.setHorizontalAlignment(JLabel.LEFT);
            JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
            wrap.setOpaque(false);
            head.remove(cx);
            wrap.add(cx);
            head.add(wrap, BorderLayout.SOUTH);
        }
        c.add(fit(head));
        c.add(Box.createVerticalStrut(8));
        return c;
    }

    private static Component row(String label, JComponent field) {
        JPanel r = new JPanel(new BorderLayout(10, 0));
        r.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(Theme.BODY);
        l.setForeground(Theme.MUTED);
        l.setPreferredSize(new Dimension(88, 32));
        r.add(l, BorderLayout.WEST);
        r.add(field, BorderLayout.CENTER);
        r.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        return fit(r);
    }

    private Component button(String label, Runnable action, Ui.Kind kind) {
        return fit(new Ui.ModernButton(new Act(label, action), kind));
    }

    private static Component fit(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 4));
        return c;
    }

    private static Component gap() { return Box.createVerticalStrut(12); }

    /** Every button goes through here: no exception can escape to Swing. */
    private final class Act extends javax.swing.AbstractAction {
        private final Runnable r;
        Act(String name, Runnable r) { super(name); this.r = r; }
        @Override
        public void actionPerformed(java.awt.event.ActionEvent e) {
            try { r.run(); }
            catch (RuntimeException ex) { logError("Unexpected problem handled safely: " + ex); }
        }
    }

    // ================================================================= main

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "Cyclone Control Room");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        Theme.install();
        SwingUtilities.invokeLater(() -> new MainApp().setVisible(true));
    }
}
