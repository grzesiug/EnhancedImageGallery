package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import java.util.Set;
import javax.swing.*;
import javax.swing.border.*;

/**
 * Second toolbar row.
 * Order (left→right):
 *   Group by  |  Status filters  Type filters  More  [Apply ✓]  |  Size  |  Tag▾  All  ✕  Mark selected seen  Mark group seen
 */
public class ActionBar extends JPanel {

    private final EnhancedGalleryTopComponent parent;

    // Group by
    private final JComboBox<String> groupByBox = new JComboBox<>(
            new String[]{"Path","Extension","MIME type","Modified","Accessed","Created","Changed","Tag"});

    // Filter checkboxes
    private final JCheckBox cbUnseen = checkTip("Unseen", true,  "Show files not yet reviewed");
    private final JCheckBox cbSeen   = checkTip("Seen",   false, "Show files already reviewed (hidden by default)");
    private final JCheckBox cbTagged = checkTip("Tagged", true,  "Show files with tags applied");
    private final JCheckBox cbImage  = checkTip("Image",  true,  "Show image files (JPG, PNG, HEIC, WebP, RAW...)");
    private final JCheckBox cbVideo  = checkTip("Video",  true,  "Show video files (MP4, MOV, AVI, MKV...)");
    private final JCheckBox cbAudio  = checkTip("Audio",  true,  "Show audio files (MP3, M4A, WAV, FLAC...)");
    private final JCheckBox cbGps    = checkTip("GPS only",        false, "Show only files with GPS coordinates in EXIF");
    private final JCheckBox cbBroken = checkTip("No preview only", false, "Show only files for which thumbnail could not be generated");

    // Apply + status indicator
    private final JButton applyBtn     = new JButton("▶ Apply");
    private final JLabel  filterStatus = new JLabel("✓");

    // Size
    private final JSlider sizeSlider = new JSlider(60, 320, 110);
    private final JLabel  sizeLabel  = new JLabel("110px");

    // Action buttons
    private final JButton markSelSeenBtn   = new JButton("Mark selected seen");
    private final JButton markGroupSeenBtn = new JButton("Mark group seen");

    // File name search (within current group/filter)
    private final JTextField searchField = new JTextField(14);
    private final javax.swing.Timer searchDebounce = new javax.swing.Timer(250, null);

    public ActionBar(EnhancedGalleryTopComponent parent) {
        this.parent = parent;
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 3));
        setBackground(new Color(244, 245, 252));
        setBorder(new MatteBorder(0, 0, 1, 0, new Color(210, 212, 228)));
        // No fixed height — auto-adjust when components wrap to next line
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                adjustHeight();
            }
        });

        // ── Group by ─────────────────────────────────────────────────────────
        groupByBox.setFont(groupByBox.getFont().deriveFont(12f));
        groupByBox.setPreferredSize(new Dimension(110, 26));
        groupByBox.setToolTipText("<html><b>Group by</b> — organizes the left panel.<br>"
                + "Path: by folder location<br>"
                + "Extension: by file type (JPG, PNG...)<br>"
                + "MIME type: by content type<br>"
                + "Modified/Accessed/Created/Changed: by date<br>"
                + "Tag: by applied tag</html>");
        groupByBox.addActionListener(e -> {
            String sel = (String) groupByBox.getSelectedItem();
            if (sel != null) parent.setGroupBy(sel.toLowerCase());
        });

        JPanel gbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        gbPanel.setOpaque(false);
        gbPanel.add(lbl("Group by:")); gbPanel.add(groupByBox);

        // ── Status filters ────────────────────────────────────────────────────
        JPanel stPanel = filterGroup("Status");
        stPanel.add(cbUnseen); stPanel.add(cbSeen); stPanel.add(cbTagged);

        // ── Type filters ──────────────────────────────────────────────────────
        JPanel tyPanel = filterGroup("Type");
        tyPanel.add(cbImage); tyPanel.add(cbVideo); tyPanel.add(cbAudio);

        // ── More ──────────────────────────────────────────────────────────────
        JPanel moPanel = filterGroup("More");
        moPanel.add(cbGps); moPanel.add(cbBroken);

        // ── Apply button ──────────────────────────────────────────────────────
        styleApply(applyBtn);
        applyBtn.setToolTipText("<html><b>Apply filters</b><br>"
                + "Applies the current Status/Type/More filter selection.<br>"
                + "Indicator shows: ✓ = applied, ⏳ = filtering in progress.</html>");
        applyBtn.addActionListener(e -> applyNow());
        filterStatus.setFont(filterStatus.getFont().deriveFont(12f));
        filterStatus.setForeground(new Color(0x15803D));

        // ── Size ──────────────────────────────────────────────────────────────
        sizeSlider.setPreferredSize(new Dimension(100, 20));
        sizeSlider.setOpaque(false);
        sizeSlider.addChangeListener(e -> {
            sizeLabel.setText(sizeSlider.getValue() + "px");
            if (!sizeSlider.getValueIsAdjusting())
                parent.setThumbSize(sizeSlider.getValue());
        });
        sizeLabel.setFont(sizeLabel.getFont().deriveFont(11f));
        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        sizePanel.setOpaque(false);
        sizePanel.add(lbl("Size:")); sizePanel.add(sizeSlider); sizePanel.add(sizeLabel);

        // ── File name search ─────────────────────────────────────────────────
        searchField.setFont(searchField.getFont().deriveFont(12f));
        searchField.setToolTipText("<html><b>Search files</b> — filters the currently visible "
                + "group/filter by file name (case-insensitive, substring match).</html>");
        searchDebounce.setRepeats(false);
        searchDebounce.addActionListener(e -> parent.setSearchText(searchField.getText()));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { searchDebounce.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { searchDebounce.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { searchDebounce.restart(); }
        });
        JButton searchClearBtn = new JButton("✕");
        searchClearBtn.setMargin(new Insets(0, 4, 0, 4));
        searchClearBtn.setFont(searchClearBtn.getFont().deriveFont(10f));
        searchClearBtn.setToolTipText("Clear search");
        searchClearBtn.addActionListener(e -> { searchField.setText(""); parent.setSearchText(null); });

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(lbl("🔍")); searchPanel.add(searchField); searchPanel.add(searchClearBtn);

        // ── Tag dropdown ──────────────────────────────────────────────────────
        JPopupMenu tagMenu = buildTagMenu();
        JButton tagBtn = toolBtn("Tag ▾");
        tagBtn.setToolTipText("<html>Apply a tag to selected files.<br>"
                + "Tags are also written to Autopsy's Blackboard<br>"
                + "and visible in Tags section of the case.</html>");
        tagBtn.addActionListener(e -> {
            JPopupMenu fresh = buildTagMenu();
            fresh.show(tagBtn, 0, tagBtn.getHeight());
        });

        // ── All / Clear ───────────────────────────────────────────────────────
        JButton allBtn   = toolBtn("All");
        JButton clearBtn = toolBtn("✕");
        allBtn.setToolTipText("<html><b>Select all</b> — selects all files in the current group/filter.<br>"
                + "Use before Tag or Mark seen to apply the action to all of them.</html>");
        clearBtn.setToolTipText("<html><b>Clear selection</b> — deselects all selected files.<br>"
                + "Properties panel will be cleared.</html>");
        allBtn.addActionListener(e   -> parent.selectAll());
        clearBtn.addActionListener(e -> parent.clearSelection());

        // ── Mark selected seen ────────────────────────────────────────────────
        styleSecondary(markSelSeenBtn);
        markSelSeenBtn.setToolTipText("<html><b>Mark selected seen</b><br>"
                + "Marks the selected files as Seen (reviewed).<br>"
                + "With MD5 propagation enabled, also marks duplicate files.<br>"
                + "Seen files are hidden when the 'Seen' filter is unchecked.</html>");
        markSelSeenBtn.addActionListener(e -> parent.markSelectionAsSeen());

        // ── Mark group seen ───────────────────────────────────────────────────
        styleSecondary(markGroupSeenBtn);
        markGroupSeenBtn.setToolTipText("<html><b>Mark group seen</b><br>"
                + "Marks ALL files in the current group/filter as Seen,<br>"
                + "including those scrolled out of view.<br>"
                + "Use to confirm you have reviewed the entire group or folder.</html>");
        markGroupSeenBtn.addActionListener(e -> parent.markAllVisibleSeen());

        // ── Settings ──────────────────────────────────────────────────────────
        JButton settingsBtn = toolBtn("⚙ Settings");
        settingsBtn.setToolTipText("<html><b>Settings</b><br>"
                + "Configure: FFmpeg (video thumbnails), ImageMagick (HEIC/WebP),<br>"
                + "dcraw (RAW), decoder timeout, MD5 propagation, thumbnail cache.</html>");
        settingsBtn.addActionListener(e -> openSettings());

        // ── Filters group: Status + Type + More + Apply ───────────────────────
        JPanel filtersGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        filtersGroup.setOpaque(false);
        // Flat, modern border — thin line + subtle label
        filtersGroup.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(0xC5C8DC), 1, true),
                        "Filters",
                        javax.swing.border.TitledBorder.LEFT,
                        javax.swing.border.TitledBorder.TOP,
                        new Font(Font.SANS_SERIF, Font.PLAIN, 9),
                        new Color(0x6B7280)),
                new EmptyBorder(0, 2, 0, 2)));
        filtersGroup.add(stPanel); filtersGroup.add(tyPanel); filtersGroup.add(moPanel);
        filtersGroup.add(applyBtn); filtersGroup.add(filterStatus);

        // ── Layout (left→right) ───────────────────────────────────────────────
        add(gbPanel);
        add(vsep());
        add(filtersGroup);
        add(vsep());
        add(sizePanel);
        add(vsep());
        add(searchPanel);
        add(vsep());
        add(tagBtn); add(allBtn); add(clearBtn);
        add(markSelSeenBtn); add(markGroupSeenBtn);
        add(vsep());
        add(settingsBtn);
    }

    // ── Public callbacks ─────────────────────────────────────────────────────

    public void onFilteringStart() {
        filterStatus.setText("⏳");
        filterStatus.setForeground(new Color(0xD97706));
    }
    public void onFilteringDone() {
        filterStatus.setText("✓");
        filterStatus.setForeground(new Color(0x15803D));
    }

    public void updateTagNames(java.util.List<String> names) {
        if (names != null && !names.isEmpty())
            autopsyTagNames = new java.util.ArrayList<>(names);
    }

    public void resetGroupBy() {
        groupByBox.setSelectedItem("Path");
    }

    public void updateOpenButton(boolean e) {}
    public void onSelectionChanged(boolean e) {}
    public void setSelectionCount(int n) {}

    public Set<String> getActiveTypeFilters() {
        Set<String> s = new java.util.HashSet<>();
        if (cbImage.isSelected()) s.add("image");
        if (cbVideo.isSelected()) s.add("video");
        if (cbAudio.isSelected()) s.add("audio");
        return s;
    }
    public Set<String> getActiveStatusFilters() {
        Set<String> s = new java.util.HashSet<>();
        if (cbUnseen.isSelected()) s.add("unseen");
        if (cbSeen.isSelected())   s.add("seen");
        if (cbTagged.isSelected()) s.add("tagged");
        return s;
    }
    public boolean isGpsOnly()    { return cbGps.isSelected(); }
    public boolean isBrokenOnly() { return cbBroken.isSelected(); }

    // ── Private ──────────────────────────────────────────────────────────────

    private void applyNow() {
        Set<String> st = parent.getStatusFilters();
        st.clear(); if (cbUnseen.isSelected()) st.add("unseen");
        if (cbSeen.isSelected()) st.add("seen"); if (cbTagged.isSelected()) st.add("tagged");
        Set<String> ty = parent.getTypeFilters();
        ty.clear(); if (cbImage.isSelected()) ty.add("image");
        if (cbVideo.isSelected()) ty.add("video"); if (cbAudio.isSelected()) ty.add("audio");
        parent.setGeoOnly(cbGps.isSelected());
        parent.setShowBroken(cbBroken.isSelected());
        onFilteringStart();
        parent.applyFilters();
    }

    /**
     * Recalculates and sets preferred height based on how FlowLayout
     * wraps components at the current panel width.
     */
    private void adjustHeight() {
        int panelW = getWidth();
        if (panelW <= 0) return;

        FlowLayout fl = (FlowLayout) getLayout();
        int hgap = fl.getHgap();
        int vgap = fl.getVgap();

        int x = hgap;
        int rowH = 0;
        int totalH = vgap;

        for (Component c : getComponents()) {
            if (!c.isVisible()) continue;
            Dimension d = c.getPreferredSize();
            if (x + d.width + hgap > panelW && x > hgap) {
                // Wrap to next row
                totalH += rowH + vgap;
                x = hgap;
                rowH = 0;
            }
            x    += d.width + hgap;
            rowH  = Math.max(rowH, d.height);
        }
        totalH += rowH + vgap;
        totalH += getInsets().top + getInsets().bottom;

        int minH = 46; // never go below single-row height
        int newH = Math.max(minH, totalH);

        if (getPreferredSize().height != newH) {
            setPreferredSize(new Dimension(0, newH));
            Container parent = getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        }
    }

    private void openSettings() {
        try {
            Window win = SwingUtilities.getWindowAncestor(this);
            new org.sleuthkit.autopsy.enhancedgallery.options.ToolSettingsDialog(win)
                    .setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open settings:\n" + ex.getMessage(),
                    "Settings Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private java.util.List<String> autopsyTagNames = new java.util.ArrayList<>(
            java.util.List.of("Bookmark","Notable item","Follow up",
                    "Evidence","OK / Irrelevant","Needs review"));

    private JPopupMenu buildTagMenu() {
        JPopupMenu m = new JPopupMenu();
        for (String t : autopsyTagNames) {
            JMenuItem mi = new JMenuItem(t);
            mi.addActionListener(e -> parent.applyTag(t));
            m.add(mi);
        }
        m.addSeparator();
        JMenuItem rm = new JMenuItem("✕ Remove all tags");
        rm.setForeground(new Color(0xB91C1C));
        rm.addActionListener(e -> parent.applyTag(null));
        m.add(rm);
        return m;
    }

    // ── Style helpers ─────────────────────────────────────────────────────────

    private static JButton toolBtn(String label) {
        JButton b = new JButton(label);
        b.setFont(b.getFont().deriveFont(12f));
        b.setMargin(new Insets(2, 7, 2, 7));
        b.setFocusPainted(false);
        return b;
    }

    private static void styleApply(JButton b) {
        b.setFont(b.getFont().deriveFont(Font.BOLD, 12f));
        b.setMargin(new Insets(2, 10, 2, 10));
        b.setFocusPainted(false);
        b.setForeground(new Color(0x1D4ED8));
        b.setBackground(new Color(0xDBEAFE));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x93C5FD), 1, true),
                new EmptyBorder(2, 8, 2, 8)));
    }

    private static void styleSecondary(JButton b) {
        b.setFont(b.getFont().deriveFont(11f));
        b.setMargin(new Insets(2, 8, 2, 8));
        b.setFocusPainted(false);
    }

    private static JCheckBox check(String label, boolean sel) {
        return checkTip(label, sel, null);
    }

    private static JCheckBox checkTip(String label, boolean sel, String tip) {
        JCheckBox cb = new JCheckBox(label, sel);
        cb.setFont(cb.getFont().deriveFont(11f));
        cb.setOpaque(false);
        if (tip != null) cb.setToolTipText(tip);
        return cb;
    }

    private static JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(12f));
        return l;
    }

    private static JPanel filterGroup(String title) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        p.setOpaque(false);
        // Flat colored label prefix instead of etched border
        JLabel lbl = new JLabel(title + ":");
        lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        lbl.setForeground(new Color(0x5B6B8A));
        p.add(lbl);
        return p;
    }

    private static JSeparator vsep() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(1, 22));
        return s;
    }
}
