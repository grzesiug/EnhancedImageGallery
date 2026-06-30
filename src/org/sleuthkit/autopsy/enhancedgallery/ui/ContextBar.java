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

    // Data source button + popup
    private final JButton    dsButton = new JButton("All data sources ▾");
    private final JPopupMenu dsPopup  = new JPopupMenu();
    private String currentDs = null;
    private final java.util.List<String> dsNames = new java.util.ArrayList<>();

    // Row 1 components
    private final JLabel       countLabel = new JLabel("0 / 0 files");
    private final JProgressBar progress   = new JProgressBar(0, 100);

    // Row 2 — group path (wraps naturally in a BorderLayout CENTER)
    private final JLabel groupLabel = new JLabel("All files");

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

        // ── Row 2: group path (full width, wraps) ─────────────────────────────
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row2.setOpaque(false);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel groupPrefixLabel = label("Group:");
        groupLabel.setFont(groupLabel.getFont().deriveFont(Font.BOLD, FS));
        groupLabel.setForeground(new Color(40, 60, 140));

        row2.add(groupPrefixLabel);
        row2.add(groupLabel);

        add(row1);
        add(Box.createVerticalStrut(2));
        add(row2);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void resetGroupBy() {}

    public void setDataSources(java.util.Set<String> sources) {
        dsNames.clear();
        if (sources != null) dsNames.addAll(sources);
        updateButtonLabel();
    }

    public void addDataSources(java.util.Set<String> sources) {
        if (sources == null) return;
        for (String s : sources) if (!dsNames.contains(s)) dsNames.add(s);
        updateButtonLabel();
    }

    public void updateProgress(List<MediaFile> all, List<MediaFile> visible) {
        long total  = all.size();
        long unseen = all.stream()
                .filter(f -> f.getReviewState() == MediaFile.ReviewState.UNSEEN && !f.isTagged())
                .count();
        long done = total - unseen;
        countLabel.setText(String.format("%,d / %,d  •  %,d unseen",
                visible.size(), total, unseen));
        if (total > 0) progress.setValue((int)(done * 100 / total));
    }

    public void setGroupName(String name) {
        String full = (name != null && !name.isEmpty()) ? name : "All files";
        groupLabel.setToolTipText(full);
        // Use <html> so Swing wraps at word/slash boundaries
        groupLabel.setText("<html>" + escapeHtml(full) + "</html>");
    }

    // ── DS popup ──────────────────────────────────────────────────────────────

    private void rebuildDsPopup() {
        dsPopup.removeAll();
        JMenuItem allItem = new JMenuItem("All data sources");
        allItem.setFont(allItem.getFont().deriveFont(FS));
        if (currentDs == null) allItem.setFont(allItem.getFont().deriveFont(Font.BOLD));
        allItem.addActionListener(e -> selectDs(null));
        dsPopup.add(allItem);
        if (!dsNames.isEmpty()) {
            dsPopup.addSeparator();
            for (String ds : dsNames) {
                JMenuItem item = new JMenuItem(ds);
                item.setFont(item.getFont().deriveFont(FS));
                if (ds.equals(currentDs)) item.setFont(item.getFont().deriveFont(Font.BOLD));
                item.addActionListener(e -> selectDs(ds));
                dsPopup.add(item);
            }
        }
    }

    private void selectDs(String ds) {
        currentDs = ds; updateButtonLabel(); parent.setDataSource(ds);
    }

    private void updateButtonLabel() {
        if (currentDs == null) {
            dsButton.setText("All data sources ▾");
            dsButton.setToolTipText("Filter gallery by data source");
        } else {
            String lbl = currentDs.length() > 24
                    ? "…" + currentDs.substring(currentDs.length() - 22) : currentDs;
            dsButton.setText(lbl + " ▾");
            dsButton.setToolTipText(currentDs);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(FS));
        return l;
    }
}
