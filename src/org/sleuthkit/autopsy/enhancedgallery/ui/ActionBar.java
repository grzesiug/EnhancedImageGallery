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
    private final JCheckBox cbDocument = checkTip("Documents", false,
            "Show document files (PDF, DOCX, XLSX, TXT, EML...). Off by default — "
            + "documents are loaded on demand only when this is enabled.");
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
    private final JComboBox<String> searchBox = new JComboBox<>();
    private final JTextField searchField = (JTextField) searchBox.getEditor().getEditorComponent();
    private final java.util.List<String> fileSearchHistory = new java.util.ArrayList<>();
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
        tyPanel.add(cbImage); tyPanel.add(cbVideo); tyPanel.add(cbAudio); tyPanel.add(cbDocument);

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
        searchBox.setEditable(true);
        searchField.setColumns(14);
        searchField.setFont(searchField.getFont().deriveFont(12f));
        String searchTip = "<html><b>Search files</b> — filters the currently visible "
                + "group/filter by file name.<br>"
                + "Case-insensitive substring. Multiple phrases with <b>|</b> match ANY "
                + "(e.g. <tt>img1|img2</tt>).<br>"
                + "Enter remembers the query (last " + org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings.RECENT_FILTER_MAX
                + "); ▾ picks a recent one.</html>";
        searchBox.setToolTipText(searchTip);
        searchField.setToolTipText(searchTip);
        reloadFileSearchHistory();
        installPrefixAutocomplete(searchField, fileSearchHistory);
        searchDebounce.setRepeats(false);
        searchDebounce.addActionListener(e -> parent.setSearchText(searchField.getText()));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { searchDebounce.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { searchDebounce.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { searchDebounce.restart(); }
        });
        // Enter → remember the query in history (live filtering already happens via debounce).
        searchField.addActionListener(e -> {
            String t = searchField.getText().trim();
            if (!t.isEmpty()) {
                org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings.addRecentFileSearch(t);
                reloadFileSearchHistory();
            }
            parent.setSearchText(searchField.getText());
        });
        JButton searchClearBtn = new JButton("✕");
        searchClearBtn.setMargin(new Insets(0, 4, 0, 4));
        searchClearBtn.setFont(searchClearBtn.getFont().deriveFont(10f));
        searchClearBtn.setToolTipText("Clear search");
        searchClearBtn.addActionListener(e -> { searchField.setText(""); parent.setSearchText(null); });

        JButton aiSearchBtn = new JButton("AI search…", new AiSearchIcon(16));
        aiSearchBtn.setFont(aiSearchBtn.getFont().deriveFont(12f));
        aiSearchBtn.setIconTextGap(5);
        aiSearchBtn.setMargin(new Insets(2, 8, 2, 8));
        aiSearchBtn.setFocusPainted(false);
        aiSearchBtn.setToolTipText("<html><b>Semantic search</b> — pick the index in the dialog:<br>"
                + "• <b>Images (visual)</b> → AI Image Triage / CLIP (describe how it looks).<br>"
                + "• <b>Text + OCR</b> → AI Text Triage / BGE-M3 (document text + text in images), with snippets.<br>"
                + "Requires the matching AI service + a completed ingest.<br>"
                + "Active filters and the selected group still narrow the results.</html>");
        aiSearchBtn.addActionListener(e -> promptSemanticSearch());

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(lbl("🔍")); searchPanel.add(searchBox); searchPanel.add(searchClearBtn);
        searchPanel.add(aiSearchBtn);

        // ── Tag dropdown ──────────────────────────────────────────────────────
        JButton tagBtn = toolBtn("Tag ▾");
        tagBtn.setIcon(new TagIcon(14));
        tagBtn.setIconTextGap(5);
        tagBtn.setToolTipText("<html>Apply a tag to selected files.<br>"
                + "Tags are also written to Autopsy's Blackboard<br>"
                + "and visible in Tags section of the case.</html>");
        // Widen the button by ~100% (double its natural width).
        Dimension tagPref = tagBtn.getPreferredSize();
        tagBtn.setPreferredSize(new Dimension(tagPref.width * 2, tagPref.height));
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
        if (cbDocument.isSelected()) s.add("document");
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
        if (cbDocument.isSelected()) ty.add("document");
        parent.setGeoOnly(cbGps.isSelected());
        parent.setShowBroken(cbBroken.isSelected());
        onFilteringStart();
        // Applying filters is a view change → scroll to top + clear stale selection
        parent.applyFiltersResettingView();
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
        // Automated AI tags first (blue), then custom, built-in standard tags, and
        // finally the child-exploitation group.
        java.util.List<String> aiTags    = parent.aiTagsSorted();
        java.util.List<String> custom     = parent.customTagsSorted();
        java.util.List<String> predefined = parent.predefinedTagsSorted();
        java.util.List<String> childExpl  = parent.childExploitationTagsSorted();
        for (String t : aiTags) {
            JMenuItem mi = new JMenuItem(t);
            mi.setForeground(EnhancedGalleryTopComponent.AI_TAG_COLOR);
            mi.addActionListener(e -> parent.applyTag(t));
            m.add(mi);
        }
        if (!aiTags.isEmpty() && !custom.isEmpty()) m.addSeparator();
        for (String t : custom) {
            JMenuItem mi = new JMenuItem(t);
            mi.addActionListener(e -> parent.applyTag(t));
            m.add(mi);
        }
        if ((!aiTags.isEmpty() || !custom.isEmpty()) && !predefined.isEmpty()) m.addSeparator();
        for (String t : predefined) {
            JMenuItem mi = new JMenuItem(t);
            mi.addActionListener(e -> parent.applyTag(t));
            m.add(mi);
        }
        if (!predefined.isEmpty() && !childExpl.isEmpty()) m.addSeparator();
        for (String t : childExpl) {
            JMenuItem mi = new JMenuItem(t);
            mi.setForeground(new Color(0xA32D2D)); // child-exploitation group — red
            mi.addActionListener(e -> parent.applyTag(t));
            m.add(mi);
        }
        m.addSeparator();
        JMenuItem newTag = new JMenuItem("+ New tag...");
        newTag.setForeground(new Color(0x15803D));
        newTag.addActionListener(e -> parent.promptAndCreateTag(this));
        m.add(newTag);
        // Replace: submenu of tags to pick directly (no dialog) — less clicking
        m.add(parent.buildReplaceTagSubmenu());
        m.addSeparator();
        JMenuItem rm = new JMenuItem("✕ Remove all tags");
        rm.setForeground(new Color(0xB91C1C));
        rm.addActionListener(e -> parent.applyTag(null));
        m.add(rm);
        return m;
    }

    /**
     * Opens the semantic-search dialog: a free-text query the AI service embeds
     * (CLIP) and matches against image embeddings. The ranked result set becomes
     * a grid filter; the filename box can further narrow it.
     */
    private void promptSemanticSearch() {
        // Editable combo pre-filled with recent queries (newest first) + prefix
        // autocomplete, so the analyst can re-run or refine a previous search.
        final java.util.List<String> recent =
                org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings.getRecentSearches();
        JComboBox<String> queryBox = new JComboBox<>(recent.toArray(new String[0]));
        queryBox.setEditable(true);
        queryBox.setSelectedItem("");                 // start empty, not on the newest item
        final JTextField editor = (JTextField) queryBox.getEditor().getEditorComponent();
        editor.setColumns(28);
        installPrefixAutocomplete(editor, recent);

        // ── Index selector (explicit — the two indexes are phrased differently) ──
        boolean textMode = org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings.isAiSearchTextMode();
        JRadioButton rbImages = new JRadioButton("Images (visual)");
        JRadioButton rbText   = new JRadioButton("Text + OCR");
        rbImages.setToolTipText("Visual search over the AI Image Triage index (CLIP). "
                + "Describe what an image looks like.");
        rbText.setToolTipText("Text search over the AI Text Triage index (BGE-M3): document "
                + "text and text recognised inside images (OCR). Describe the wording/meaning.");
        ButtonGroup grp = new ButtonGroup(); grp.add(rbImages); grp.add(rbText);
        rbText.setSelected(textMode);
        rbImages.setSelected(!textMode);
        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        modeRow.add(new JLabel("Search:")); modeRow.add(rbImages); modeRow.add(rbText);

        JLabel info = new JLabel("<html><span style='color:#555'><i>Active filters and the "
                + "selected group still apply to these results — enable the matching Type "
                + "filter (e.g. Documents) to see all hits.</i></span></html>");
        info.setFont(info.getFont().deriveFont(11f));

        JSpinner topN = new JSpinner(new SpinnerNumberModel(50, 1, 50_000, 50));
        JCheckBox allBox = new JCheckBox("All");
        allBox.setToolTipText("Return every match (capped to the index size), ranked from "
                + "most to least confident. Ignores the limit.");
        JLabel maxLbl = new JLabel("Max results:");
        // "All" makes the numeric limit irrelevant → grey it out.
        allBox.addActionListener(e -> {
            boolean all = allBox.isSelected();
            topN.setEnabled(!all);
            maxLbl.setEnabled(!all);
        });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bottom.add(maxLbl); bottom.add(topN); bottom.add(allBox);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        modeRow.setAlignmentX(LEFT_ALIGNMENT);
        JLabel prompt = new JLabel("Describe what to find:");
        prompt.setAlignmentX(LEFT_ALIGNMENT);
        info.setAlignmentX(LEFT_ALIGNMENT);
        north.add(modeRow);
        north.add(prompt);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(north, BorderLayout.NORTH);
        panel.add(queryBox, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.add(bottom, BorderLayout.NORTH);
        south.add(info,   BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);

        // Build the option pane manually so Enter in the query editor submits (the
        // editable combo consumes Enter, so it never reaches an OK default button).
        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = pane.createDialog(this, "AI semantic search");
        editor.addActionListener(e -> pane.setValue(JOptionPane.OK_OPTION)); // Enter → OK
        queryBox.addAncestorListener(new javax.swing.event.AncestorListener() {
            @Override public void ancestorAdded(javax.swing.event.AncestorEvent e) {
                editor.requestFocusInWindow();
            }
            @Override public void ancestorMoved(javax.swing.event.AncestorEvent e) {}
            @Override public void ancestorRemoved(javax.swing.event.AncestorEvent e) {}
        });
        dialog.setVisible(true);
        dialog.dispose();

        Object val = pane.getValue();
        int res = (val instanceof Integer) ? (Integer) val : JOptionPane.CLOSED_OPTION;
        if (res != JOptionPane.OK_OPTION) return;

        Object sel = queryBox.getEditor().getItem();
        String query = sel == null ? "" : sel.toString().trim();
        if (query.isEmpty()) return;

        boolean chosenTextMode = rbText.isSelected();
        // "All" → very large top_n; the service caps it to the actual index size and
        // returns hits already sorted from most to least confident (by score).
        int resultLimit = allBox.isSelected() ? 1_000_000 : (int) topN.getValue();
        org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings.setAiSearchTextMode(chosenTextMode);
        org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings.addRecentSearch(query);
        parent.runSemanticSearch(query, resultLimit, chosenTextMode);
    }

    /**
     * Inline prefix autocomplete for the combo editor: after each edit, if a recent
     * query starts with what was typed, the remainder is appended and selected so
     * the next keystroke overwrites it (Backspace/Delete never re-complete).
     */
    /** Refreshes the file-search dropdown model + backing history list, keeping editor text. */
    private void reloadFileSearchHistory() {
        String cur = searchField.getText();
        fileSearchHistory.clear();
        fileSearchHistory.addAll(
                org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings.getRecentFileSearches());
        searchBox.setModel(new DefaultComboBoxModel<>(fileSearchHistory.toArray(new String[0])));
        searchBox.setSelectedIndex(-1);
        searchField.setText(cur); // setModel may reset the editor — restore what the user had
    }

    static void installPrefixAutocomplete(JTextField editor, java.util.List<String> recent) {
        // 'recent' may be a live list that grows during the session — check it per
        // keystroke rather than bailing out when it happens to be empty right now.
        editor.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyReleased(java.awt.event.KeyEvent e) {
                int k = e.getKeyCode();
                if (k == java.awt.event.KeyEvent.VK_BACK_SPACE || k == java.awt.event.KeyEvent.VK_DELETE
                        || k == java.awt.event.KeyEvent.VK_LEFT || k == java.awt.event.KeyEvent.VK_RIGHT
                        || k == java.awt.event.KeyEvent.VK_ENTER || k == java.awt.event.KeyEvent.VK_HOME
                        || k == java.awt.event.KeyEvent.VK_END || e.isActionKey()) {
                    return;
                }
                String typed = editor.getText();
                if (typed.isEmpty()) return;
                String lower = typed.toLowerCase();
                for (String item : recent) {
                    if (item.length() > typed.length() && item.toLowerCase().startsWith(lower)) {
                        editor.setText(item);
                        editor.setCaretPosition(item.length());
                        editor.moveCaretPosition(typed.length()); // select the completed suffix
                        break;
                    }
                }
            }
        });
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
