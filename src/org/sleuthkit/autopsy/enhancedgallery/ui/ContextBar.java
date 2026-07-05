package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import org.sleuthkit.autopsy.enhancedgallery.datamodel.MediaFile;

/**
 * Top bar — two rows:
 *   Row 1: [Source ▾]  [count • unseen]  [progress bar]
 *   Row 2: Group: [full path — wraps as needed]
 */
public class ContextBar extends JPanel {

    private final EnhancedGalleryTopComponent parent;

    /** One entry in the data source list: (Content.getId(), display name). */
    private record DsEntry(long id, String name) {}

    // Data source button + popup
    private final JButton    dsButton = new JButton("All data sources ▾");
    private final JPopupMenu dsPopup  = new JPopupMenu();
    private Long currentDsId = null; // null = "All data sources"
    private final java.util.List<DsEntry> dsEntries = new java.util.ArrayList<>();

    // Row 1 components
    private final JLabel       countLabel = new JLabel("0 / 0 files");
    private final JProgressBar progress   = new JProgressBar(0, 100);

    // Row 2 — group path: a wrapping, read-only text area so long paths are
    // fully visible (a JLabel with HTML doesn't wrap without a width hint).
    private final JTextArea groupLabel = new JTextArea("All files");

    private static final float FS = 13f;

    public ContextBar(EnhancedGalleryTopComponent parent) {
        this.parent = parent;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(new Color(232, 233, 245));
        setBorder(new EmptyBorder(2, 10, 2, 10));

        // ── Row 1: source | count + progress ─────────────────────────────────
        JPanel row1 = new JPanel(new BorderLayout(8, 0));
        row1.setOpaque(false);

        dsButton.setFont(dsButton.getFont().deriveFont(FS));
        dsButton.setMargin(new Insets(2, 8, 2, 8));
        dsButton.setFocusPainted(false);
        dsButton.setToolTipText("Filter gallery by data source");
        dsButton.addActionListener(e -> { rebuildDsPopup(); dsPopup.show(dsButton, 0, dsButton.getHeight()); });

        JPanel sourcePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sourcePanel.setOpaque(false);
        sourcePanel.add(label("Source:")); sourcePanel.add(dsButton);

        countLabel.setFont(countLabel.getFont().deriveFont(FS));
        progress.setPreferredSize(new Dimension(110, 8));
        progress.setBorderPainted(false);
        progress.setForeground(new Color(0x2563EB));

        JPanel countPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        countPanel.setOpaque(false);
        countPanel.add(countLabel); countPanel.add(progress);

        row1.add(sourcePanel, BorderLayout.WEST);
        row1.add(countPanel,  BorderLayout.EAST);
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);

        // ── Row 2: group path (full width, wraps to as many lines as needed) ──
        JPanel row2 = new JPanel(new BorderLayout(4, 0));
        row2.setOpaque(false);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Let BoxLayout stretch row2 to the full bar width so the text area
        // receives a real width and can wrap.
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel groupPrefixLabel = label("Group:");
        groupPrefixLabel.setVerticalAlignment(SwingConstants.TOP);

        groupLabel.setFont(groupLabel.getFont().deriveFont(Font.BOLD, FS));
        groupLabel.setForeground(new Color(40, 60, 140));
        groupLabel.setEditable(false);
        groupLabel.setOpaque(false);
        groupLabel.setLineWrap(true);
        groupLabel.setWrapStyleWord(false); // wrap long slash-separated paths anywhere
        groupLabel.setBorder(null);

        JPanel prefixHolder = new JPanel(new BorderLayout());
        prefixHolder.setOpaque(false);
        prefixHolder.add(groupPrefixLabel, BorderLayout.NORTH);

        row2.add(prefixHolder, BorderLayout.WEST);
        row2.add(groupLabel,   BorderLayout.CENTER);

        add(row1);
        add(Box.createVerticalStrut(2));
        add(row2);

        // The wrapping group-path text area computes its height from its width;
        // once the bar has a real width, re-validate so the bar grows to fit the
        // wrapped path (BoxLayout queries preferred height before width is known).
        addComponentListener(new java.awt.event.ComponentAdapter() {
            private int lastW = -1;
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if (getWidth() != lastW) {
                    lastW = getWidth();
                    revalidate();
                }
            }
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void resetGroupBy() {}

    /**
     * Sets the list of available data sources, keyed by ID (order preserved).
     * Two entries MAY have the same display name (e.g. the same image added
     * twice to the case) — they remain separate, selectable entries; the
     * popup and button label disambiguate duplicate names by appending the ID.
     */
    public void setDataSources(java.util.Map<Long, String> sources) {
        // Called on each case (re)load, which resets the view to all files —
        // reset the selection too so the button doesn't keep a stale data source
        // from a previous open while the gallery actually shows all sources.
        currentDsId = null;
        dsEntries.clear();
        if (sources != null) {
            for (java.util.Map.Entry<Long, String> e : sources.entrySet())
                dsEntries.add(new DsEntry(e.getKey(), e.getValue()));
        }
        updateButtonLabel();
    }

    public void updateProgress(List<MediaFile> all, List<MediaFile> visible) {
        long total  = all.size();
        long unseen = all.stream()
                .filter(f -> f.getReviewState() == MediaFile.ReviewState.UNSEEN)
                .count();
        long done = total - unseen;
        countLabel.setText(String.format("%,d / %,d  •  %,d unseen",
                visible.size(), total, unseen));
        if (total > 0) progress.setValue((int)(done * 100 / total));
    }

    public void setGroupName(String name) {
        String full = (name != null && !name.isEmpty()) ? name : "All files";
        groupLabel.setToolTipText(full);
        groupLabel.setText(full);   // JTextArea wraps this to the available width
        groupLabel.revalidate();
    }

    // ── DS popup ──────────────────────────────────────────────────────────────

    /**
     * Returns the label to show for an entry: the plain display name, unless
     * another entry shares the same name — then the ID is appended so the
     * two (e.g. same .dd image added twice) can be told apart.
     */
    private String disambiguatedLabel(DsEntry e) {
        long sameName = dsEntries.stream().filter(o -> o.name().equals(e.name())).count();
        return sameName > 1 ? e.name() + "  (ID " + e.id() + ")" : e.name();
    }

    private void rebuildDsPopup() {
        dsPopup.removeAll();
        JMenuItem allItem = new JMenuItem("All data sources");
        allItem.setFont(allItem.getFont().deriveFont(FS));
        if (currentDsId == null) allItem.setFont(allItem.getFont().deriveFont(Font.BOLD));
        allItem.addActionListener(e -> selectDs(null));
        dsPopup.add(allItem);
        if (!dsEntries.isEmpty()) {
            dsPopup.addSeparator();
            for (DsEntry entry : dsEntries) {
                JMenuItem item = new JMenuItem(disambiguatedLabel(entry));
                item.setFont(item.getFont().deriveFont(FS));
                if (entry.id() == (currentDsId != null ? currentDsId : Long.MIN_VALUE))
                    item.setFont(item.getFont().deriveFont(Font.BOLD));
                item.addActionListener(e -> selectDs(entry.id()));
                dsPopup.add(item);
            }
        }
    }

    private void selectDs(Long dsId) {
        currentDsId = dsId; updateButtonLabel(); parent.setDataSource(dsId);
    }

    private void updateButtonLabel() {
        if (currentDsId == null) {
            dsButton.setText("All data sources ▾");
            dsButton.setToolTipText("Filter gallery by data source");
            return;
        }
        DsEntry match = dsEntries.stream()
                .filter(e -> e.id() == currentDsId).findFirst().orElse(null);
        if (match == null) {
            // Selected data source no longer present (e.g. new case loaded) — reset.
            currentDsId = null;
            dsButton.setText("All data sources ▾");
            dsButton.setToolTipText("Filter gallery by data source");
            return;
        }
        String full = disambiguatedLabel(match);
        // Show the full data-source name (only clip pathologically long ones).
        String lbl = full.length() > 60 ? "…" + full.substring(full.length() - 58) : full;
        dsButton.setText(lbl + " ▾");
        dsButton.setToolTipText(full);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(FS));
        return l;
    }
}
