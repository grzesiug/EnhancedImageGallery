package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import org.sleuthkit.autopsy.enhancedgallery.datamodel.GroupKeyHelper;
import org.sleuthkit.autopsy.enhancedgallery.datamodel.MediaFile;
import org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings;

/**
 * Left sidebar: list of groups with file counts + sort controls.
 * rebuild() is safe to call from any thread — all Swing updates go via invokeLater.
 */
public class GroupSidebar extends JPanel {

    // ── Sort state ────────────────────────────────────────────────────────────
    private enum SortMode { NAME, COUNT, UNSEEN }
    private SortMode sortMode = SortMode.COUNT;
    private boolean  sortAsc  = false; // default: count descending

    // ── Components ────────────────────────────────────────────────────────────
    private final EnhancedGalleryTopComponent parent;
    private final DefaultListModel<GroupEntry> model = new DefaultListModel<>();
    private final JList<GroupEntry>            list  = new JList<>(model);
    private final JLabel headerLabel                 = new JLabel("Folders");
    private JScrollPane scroll; // set in constructor — used to preserve scroll position across rebuilds
    private volatile Point savedScrollPos = null;

    /** Captures current scroll position. Must be called on EDT before triggering a background rebuild. */
    public void captureScrollPosition() {
        if (scroll != null) savedScrollPos = scroll.getViewport().getViewPosition();
    }

    // Sort buttons (toggle ascend/descend on each click)
    private final JToggleButton btnName   = sortBtn("Name");
    private final JToggleButton btnCount  = sortBtn("Count");
    private final JToggleButton btnUnseen = sortBtn("Unseen");
    private final ButtonGroup   sortGroup = new ButtonGroup();

    // Cached entries for re-sorting without re-building
    private List<GroupEntry> lastEntries = new ArrayList<>();
    private volatile String groupFilterText = null; // case-insensitive substring filter on group name
    private final JComboBox<String> groupSearchBox = new JComboBox<>();
    private final JTextField groupSearchField = (JTextField) groupSearchBox.getEditor().getEditorComponent();
    private final java.util.List<String> groupSearchHistory = new java.util.ArrayList<>();
    private final javax.swing.Timer groupSearchDebounce = new javax.swing.Timer(250, null);

    record GroupEntry(String key, String display, int total, int unseen) {}

    // ── Active sort indicator ─────────────────────────────────────────────────
    private static final Color SORT_ACTIVE = new Color(0x2563EB);

    public GroupSidebar(EnhancedGalleryTopComponent parent) {
        this.parent = parent;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(290, 0));
        setMinimumSize(new Dimension(200, 0));

        // Header label
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 13f));
        headerLabel.setBorder(new EmptyBorder(4, 8, 2, 4));

        // Sort bar
        sortGroup.add(btnName); sortGroup.add(btnCount); sortGroup.add(btnUnseen);
        btnCount.setSelected(true); // default sort by count

        btnName.addActionListener(e   -> applySort(SortMode.NAME));
        btnCount.addActionListener(e  -> applySort(SortMode.COUNT));
        btnUnseen.addActionListener(e -> applySort(SortMode.UNSEEN));

        JPanel sortBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        sortBar.setOpaque(false);
        sortBar.setBorder(new EmptyBorder(0, 4, 2, 4));
        sortBar.add(new JLabel("Sort:"));
        sortBar.add(btnName); sortBar.add(btnCount); sortBar.add(btnUnseen);

        // Group filter (search box) — editable combo with history + multi-phrase OR
        groupSearchBox.setEditable(true);
        groupSearchField.setFont(groupSearchField.getFont().deriveFont(12f));
        String groupTip = "<html><b>Filter groups</b> by name (case-insensitive).<br>"
                + "Multiple phrases with <b>|</b> match ANY (e.g. <tt>2023|2024</tt>).<br>"
                + "Enter remembers the query (last " + GallerySettings.RECENT_FILTER_MAX
                + "); ▾ picks a recent one.</html>";
        groupSearchBox.setToolTipText(groupTip);
        groupSearchField.setToolTipText(groupTip);
        groupSearchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC5C8DC)),
                new EmptyBorder(2, 4, 2, 4)));
        reloadGroupSearchHistory();
        ActionBar.installPrefixAutocomplete(groupSearchField, groupSearchHistory);
        groupSearchDebounce.setRepeats(false);
        groupSearchDebounce.addActionListener(e -> {
            groupFilterText = groupSearchField.getText();
            reapplyFilterAndSort();
        });
        groupSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { groupSearchDebounce.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { groupSearchDebounce.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { groupSearchDebounce.restart(); }
        });
        // Enter → remember the query (live filtering already happens via debounce).
        groupSearchField.addActionListener(e -> {
            String t = groupSearchField.getText().trim();
            if (!t.isEmpty()) {
                GallerySettings.addRecentGroupSearch(t);
                reloadGroupSearchHistory();
            }
            groupFilterText = groupSearchField.getText();
            reapplyFilterAndSort();
        });
        JButton groupSearchClear = new JButton("✕");
        groupSearchClear.setMargin(new Insets(0, 3, 0, 3));
        groupSearchClear.setFont(groupSearchClear.getFont().deriveFont(9f));
        groupSearchClear.setToolTipText("Clear filter");
        groupSearchClear.addActionListener(e -> {
            groupSearchField.setText("");
            groupFilterText = null;
            reapplyFilterAndSort();
        });
        JPanel searchBar = new JPanel(new BorderLayout(3, 0));
        searchBar.setOpaque(false);
        searchBar.setBorder(new EmptyBorder(2, 6, 2, 6));
        searchBar.add(groupSearchBox, BorderLayout.CENTER);
        searchBar.add(groupSearchClear, BorderLayout.EAST);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setOpaque(false);
        northPanel.add(headerLabel, BorderLayout.NORTH);
        northPanel.add(searchBar,   BorderLayout.CENTER);
        northPanel.add(sortBar,     BorderLayout.SOUTH);

        // File list
        list.setFont(list.getFont().deriveFont(12f));
        list.setFixedCellHeight(26);
        list.setFixedCellWidth(1); // prevents JList iterating all 18k cells to find max width
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new GroupCellRenderer());
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            GroupEntry entry = list.getSelectedValue();
            if (entry != null)
                parent.onGroupSelected(GroupKeyHelper.ALL.equals(entry.key())
                        ? null : entry.key());
        });

        scroll = new JScrollPane(list);
        scroll.setBorder(null);

        add(northPanel, BorderLayout.NORTH);
        add(scroll,     BorderLayout.CENTER);
    }

    /** Refreshes the group-search dropdown model + backing history list, keeping editor text. */
    private void reloadGroupSearchHistory() {
        String cur = groupSearchField.getText();
        groupSearchHistory.clear();
        groupSearchHistory.addAll(GallerySettings.getRecentGroupSearches());
        groupSearchBox.setModel(new DefaultComboBoxModel<>(groupSearchHistory.toArray(new String[0])));
        groupSearchBox.setSelectedIndex(-1);
        groupSearchField.setText(cur); // setModel may reset the editor — restore what the user had
    }

    // ── Group filter ──────────────────────────────────────────────────────────

    /** Keeps "All files" entry always visible; filters the rest by display name substring. */
    private List<GroupEntry> filtered(List<GroupEntry> entries) {
        List<String> terms = SearchTerms.parse(groupFilterText); // pipe-separated, OR-matched
        if (terms.isEmpty()) return entries;
        List<GroupEntry> out = new ArrayList<>();
        for (GroupEntry e : entries) {
            if (GroupKeyHelper.ALL.equals(e.key()) || SearchTerms.matchesAny(e.display().toLowerCase(), terms))
                out.add(e);
        }
        return out;
    }

    /** Re-applies filter + current sort on the cached (unfiltered) entries — used when filter text changes. */
    private void reapplyFilterAndSort() {
        if (!lastEntries.isEmpty()) resortAndDisplay();
    }

    // ── Sort logic ────────────────────────────────────────────────────────────

    private void applySort(SortMode mode) {
        if (sortMode == mode) {
            sortAsc = !sortAsc; // same mode → toggle direction
        } else {
            sortMode = mode;
            sortAsc  = (mode == SortMode.NAME); // name defaults asc, others desc
        }
        updateSortButtonLabels();
        resortAndDisplay();
    }

    /** Sorts + filters lastEntries with the current sortMode/groupFilterText and updates the list. */
    private void resortAndDisplay() {
        if (!lastEntries.isEmpty()) {
            // Capture state on EDT, do heavy work on rebuildPool, one invokeLater
            final List<GroupEntry> snap   = new ArrayList<>(lastEntries);
            final String selKey           = lastSelKey;
            final String currentHeader    = headerLabel.getText();
            parent.getRebuildPool().submit(() -> {
                List<GroupEntry> sorted = sorted(filtered(snap));
                DefaultListModel<GroupEntry> newModel = new DefaultListModel<>();
                int restoreIdx = 0;
                for (int i = 0; i < sorted.size(); i++) {
                    newModel.addElement(sorted.get(i));
                    if (sorted.get(i).key().equals(selKey)) restoreIdx = i;
                }
                final DefaultListModel<GroupEntry> finalModel = newModel;
                final int finalIdx = restoreIdx;
                SwingUtilities.invokeLater(() -> {
                    for (javax.swing.event.ListSelectionListener l : list.getListSelectionListeners())
                        list.removeListSelectionListener(l);
                    list.setModel(finalModel);
                    list.setSelectedIndex(finalIdx);
                    headerLabel.setText(currentHeader);
                    updateSortButtonLabels();
                    list.addListSelectionListener(e -> {
                        if (e.getValueIsAdjusting()) return;
                        GroupEntry entry = list.getSelectedValue();
                        if (entry != null) {
                            lastSelKey = entry.key();
                            parent.onGroupSelected(GroupKeyHelper.ALL.equals(entry.key())
                                    ? null : entry.key());
                        }
                    });
                });
            });
        }
    }

    private void updateSortButtonLabels() {
        String asc = "↑", desc = "↓";
        btnName  .setText("Name"   + (sortMode == SortMode.NAME   ? (sortAsc ? asc : desc) : ""));
        btnCount .setText("Count"  + (sortMode == SortMode.COUNT  ? (sortAsc ? asc : desc) : ""));
        btnUnseen.setText("Unseen" + (sortMode == SortMode.UNSEEN ? (sortAsc ? asc : desc) : ""));
    }

    private List<GroupEntry> sorted(List<GroupEntry> entries) {
        Comparator<GroupEntry> base = switch (sortMode) {
            case NAME   -> Comparator.comparing(e -> e.display().toLowerCase());
            case UNSEEN -> Comparator.comparingInt(GroupEntry::unseen);
            default     -> Comparator.comparingInt(GroupEntry::total);
        };
        final Comparator<GroupEntry> cmp = sortAsc ? base : base.reversed();

        List<GroupEntry> result = new ArrayList<>(entries);
        result.sort((a, b) -> {
            if (GroupKeyHelper.ALL.equals(a.key())) return -1;
            if (GroupKeyHelper.ALL.equals(b.key())) return  1;
            return cmp.compare(a, b);
        });
        return result;
    }

    // ── Public rebuild API ────────────────────────────────────────────────────

    public void rebuild(List<MediaFile> files, String groupBy) {
        rebuild(files, groupBy, null);
    }

    public void rebuild(List<MediaFile> files, String groupBy, Runnable onDone) {
        headerLabel.setText(GroupKeyHelper.displayName(groupBy));
        boolean isTagGrouping = "tag".equalsIgnoreCase(groupBy);

        // When the grouping MODE changes, the previously selected group key no
        // longer applies — reset the highlighted entry to "All files" so the
        // sidebar matches the reset view (TopComponent shows "All files" too).
        // A rebuild with the SAME groupBy (e.g. after tagging) keeps the selection.
        if (lastGroupBy != null && !lastGroupBy.equalsIgnoreCase(groupBy)) {
            lastSelKey = GroupKeyHelper.ALL;
        }
        lastGroupBy = groupBy;

        Map<String, int[]> counts = new LinkedHashMap<>();
        int[] allEntry = new int[]{0, 0};
        counts.put(GroupKeyHelper.ALL, allEntry);

        for (MediaFile mf : files) {
            allEntry[0]++;
            // Unseen is independent of tags: a tagged-but-unreviewed file still
            // counts as unseen.
            boolean isUnseen = mf.getReviewState() == MediaFile.ReviewState.UNSEEN;
            if (isUnseen) allEntry[1]++;

            if (isTagGrouping) {
                List<String> tags = mf.getAllTagNames();
                if (tags.isEmpty()) {
                    int[] c = counts.computeIfAbsent("(untagged)", k -> new int[2]);
                    c[0]++;
                    if (isUnseen) c[1]++;
                } else {
                    for (String tag : tags) {
                        String key = counts.keySet().stream()
                                .filter(k -> k.equalsIgnoreCase(tag))
                                .findFirst().orElse(tag);
                        int[] c = counts.computeIfAbsent(key, k -> new int[2]);
                        c[0]++;
                    }
                }
            } else {
                String key = GroupKeyHelper.keyOf(mf, groupBy);
                int[] c = counts.computeIfAbsent(key, k -> new int[2]);
                c[0]++;
                if (isUnseen) c[1]++;
            }
        }

        List<GroupEntry> entries = new ArrayList<>(counts.size());
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            String display = GroupKeyHelper.ALL.equals(e.getKey())
                    ? "All files" : e.getKey();
            entries.add(new GroupEntry(e.getKey(), display,
                    e.getValue()[0], e.getValue()[1]));
        }

        // rebuild() is already called from rebuildPool — do ALL work here,
        // then ONE invokeLater to update Swing. No nested submit to same pool.
        lastEntries = entries;
        final String header = GroupKeyHelper.displayName(groupBy) + " (" + (entries.size() - 1) + ")";
        final String selKey = lastSelKey;

        // Sort + build model on current thread (rebuildPool) — never blocks EDT
        // lastEntries keeps the UNFILTERED list (used for header count + re-filter);
        // filtered() is applied only when building what's actually displayed.
        List<GroupEntry> sorted = sorted(filtered(entries));
        DefaultListModel<GroupEntry> newModel = new DefaultListModel<>();
        int restoreIdx = 0;
        for (int i = 0; i < sorted.size(); i++) {
            newModel.addElement(sorted.get(i));
            if (sorted.get(i).key().equals(selKey)) restoreIdx = i;
        }
        final DefaultListModel<GroupEntry> finalModel = newModel;
        final int finalIdx = restoreIdx;

        // ONE invokeLater — only fast Swing operations
        SwingUtilities.invokeLater(() -> {
            for (javax.swing.event.ListSelectionListener l : list.getListSelectionListeners())
                list.removeListSelectionListener(l);
            list.setModel(finalModel);
            list.setSelectedIndex(finalIdx);
            // Restore scroll position instead of jumping to the selected group —
            // a group's position can shift (e.g. after sort-by-unseen) without the
            // user wanting the view to follow it.
            if (savedScrollPos != null) {
                scroll.getViewport().setViewPosition(savedScrollPos);
            } else if (finalIdx >= 0) {
                list.ensureIndexIsVisible(finalIdx); // first load — no saved position yet
            }
            headerLabel.setText(header);
            updateSortButtonLabels();
            list.addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                GroupEntry entry = list.getSelectedValue();
                if (entry != null) {
                    lastSelKey = entry.key();
                    parent.onGroupSelected(GroupKeyHelper.ALL.equals(entry.key())
                            ? null : entry.key());
                }
            });
            if (onDone != null) onDone.run();
        });
    }

    private volatile String lastSelKey  = GroupKeyHelper.ALL;
    private volatile String lastGroupBy = null; // tracks grouping mode to reset selection on change

    /**
     * Resets the highlighted group to "All files". Called when the main view
     * resets its group context (e.g. data-source switch) so the sidebar
     * selection doesn't stay on a stale group. Takes effect on the next rebuild.
     */
    public void resetSelectionToAll() { lastSelKey = GroupKeyHelper.ALL; }

    // ── Cell renderer ─────────────────────────────────────────────────────────

    private static class GroupCellRenderer extends DefaultListCellRenderer {
        private static final Color ACTIVE = new Color(0x2563EB);
        private static final Color UNSEEN_BG = new Color(0xE8F0FE);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean hasFocus) {
            GroupEntry e = (GroupEntry) value;
            JPanel p = new JPanel(new BorderLayout(4, 0));
            p.setBorder(new EmptyBorder(2, 6, 2, 4));

            if (isSelected) {
                p.setBackground(new Color(0xA9C8FF)); // stronger, more saturated than before
                p.setBorder(new javax.swing.border.CompoundBorder(
                        new MatteBorder(0, 5, 0, 0, ACTIVE), // thicker accent bar
                        new EmptyBorder(2, 4, 2, 4)));
            } else if (e.unseen() > 0) {
                p.setBackground(UNSEEN_BG);
            } else {
                p.setBackground(UIManager.getColor("List.background"));
            }

            String name = e.display();
            if (name.length() > 28) name = "…" + name.substring(name.length() - 27);

            JLabel lbl = new JLabel(
                    "<html>" + name + " <font color='gray'>(" + e.total() + ")</font></html>");
            lbl.setFont(lbl.getFont().deriveFont(isSelected ? Font.BOLD : Font.PLAIN, 12f));
            lbl.setToolTipText(e.display());
            p.add(lbl, BorderLayout.CENTER);

            if (e.unseen() > 0) {
                JLabel badge = new JLabel(String.valueOf(e.unseen()));
                badge.setFont(badge.getFont().deriveFont(Font.BOLD, 11f));
                badge.setForeground(Color.WHITE);
                badge.setBackground(ACTIVE);
                badge.setOpaque(true);
                badge.setBorder(new EmptyBorder(1, 4, 1, 4));
                p.add(badge, BorderLayout.EAST);
            }
            return p;
        }
    }

    // ── Sort button factory ───────────────────────────────────────────────────

    private static JToggleButton sortBtn(String label) {
        JToggleButton b = new JToggleButton(label);
        b.setFont(b.getFont().deriveFont(12f));
        b.setMargin(new Insets(3, 8, 3, 8));
        b.setFocusPainted(false);
        return b;
    }
}
