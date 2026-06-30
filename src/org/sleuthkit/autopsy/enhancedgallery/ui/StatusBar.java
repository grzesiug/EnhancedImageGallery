package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Bottom status bar:
 *   "X / Y files"  |  hint  |  [progress bar + loaded count]  |  [↓ First unseen]
 */
class StatusBar extends JPanel {

    private final JLabel      countLabel   = new JLabel("0 / 0 files");
    private final JLabel      hintLabel    = new JLabel("Click · Ctrl+click · Shift+click range · Dbl-click open");
    private final JProgressBar loadProgress = new JProgressBar(0, 100);
    private final JLabel      loadLabel    = new JLabel("");
    private final JLabel      typeIdWarningLabel = new JLabel("⚠ Type ID");

    StatusBar(GalleryPanel mediator) {
        this(mediator, null);
    }

    StatusBar(GalleryPanel mediator, EnhancedGalleryTopComponent tc) {
        init(tc);
    }

    // Constructor used by EnhancedGalleryTopComponent
    StatusBar(EnhancedGalleryTopComponent tc) {
        init(tc);
    }

    private EnhancedGalleryTopComponent topComponent;

    private void init(EnhancedGalleryTopComponent tc) {
        this.topComponent = tc;
        setLayout(new BorderLayout(8, 0));
        setBorder(new EmptyBorder(3, 10, 3, 10));
        setBackground(new Color(235, 236, 245));
        setPreferredSize(new Dimension(0, 28));

        countLabel.setFont(countLabel.getFont().deriveFont(11f));

        hintLabel.setFont(hintLabel.getFont().deriveFont(10f));
        hintLabel.setForeground(new Color(130, 130, 150));

        // Loading progress (hidden when not loading)
        loadProgress.setPreferredSize(new Dimension(120, 12));
        loadProgress.setBorderPainted(false);
        loadProgress.setStringPainted(false);
        loadProgress.setVisible(false);
        loadLabel.setFont(loadLabel.getFont().deriveFont(10f));
        loadLabel.setForeground(new Color(100, 100, 120));
        loadLabel.setVisible(false);

        typeIdWarningLabel.setFont(typeIdWarningLabel.getFont().deriveFont(Font.BOLD, 11f));
        typeIdWarningLabel.setForeground(new Color(0xB45309));
        typeIdWarningLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        typeIdWarningLabel.setVisible(false);
        typeIdWarningLabel.setToolTipText("Some files were not loaded — File Type Identification did not run on their data source. Click for details.");

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(countLabel);
        left.add(makeSep());
        left.add(hintLabel);
        left.add(typeIdWarningLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(loadLabel);
        right.add(loadProgress);

        add(left,  BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    void updateCount(int visible, int total) {
        countLabel.setText(visible + " / " + total + " files");
    }

    void setSelectionCount(int count) {
        if (count > 0) {
            countLabel.setText(countLabel.getText() + "  •  " + count + " selected");
        }
    }

    /** Show loading progress. Call with loaded=total to hide. */
    void setLoadingProgress(int loaded, int total) {
        if (loaded >= total) {
            loadProgress.setVisible(false);
            loadLabel.setVisible(false);
        } else {
            loadProgress.setMaximum(Math.max(1, total));
            loadProgress.setValue(loaded);
            loadLabel.setText("Loading… " + loaded + " / " + total);
            loadProgress.setVisible(true);
            loadLabel.setVisible(true);
        }
        revalidate(); repaint();
    }

    /** Indeterminate spinner while total is unknown. */
    void startIndeterminate(String msg) {
        loadProgress.setIndeterminate(true);
        loadLabel.setText(msg);
        loadProgress.setVisible(true);
        loadLabel.setVisible(true);
        revalidate(); repaint();
    }

    void hideSpinner() {
        loadProgress.setIndeterminate(false);
        loadProgress.setVisible(false);
        loadLabel.setVisible(false);
        revalidate(); repaint();
    }

    /** Shows thumbnail decode progress. Hides when done=pending. */
    void setThumbProgress(int done, int pending) {
        if (pending == 0 || done >= pending) {
            loadProgress.setVisible(false);
            loadLabel.setVisible(false);
        } else {
            loadProgress.setIndeterminate(false);
            loadProgress.setMaximum(pending);
            loadProgress.setValue(done);
            loadLabel.setText("Previews: " + done + " / " + pending);
            loadProgress.setVisible(true);
            loadLabel.setVisible(true);
        }
        revalidate(); repaint();
    }

    /** Shows (or hides, if warning is empty) the persistent Type-ID warning icon. */
    void setTypeIdWarning(java.util.Map<String, Integer> warning, Runnable onClick) {
        for (java.awt.event.MouseListener ml : typeIdWarningLabel.getMouseListeners())
            typeIdWarningLabel.removeMouseListener(ml);
        if (warning == null || warning.isEmpty()) {
            typeIdWarningLabel.setVisible(false);
            return;
        }
        int total = warning.values().stream().mapToInt(Integer::intValue).sum();
        typeIdWarningLabel.setText("⚠ " + total + " file(s) not loaded (Type ID)");
        typeIdWarningLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { onClick.run(); }
        });
        typeIdWarningLabel.setVisible(true);
        revalidate(); repaint();
    }

    private JSeparator makeSep() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(1, 14));
        return s;
    }
}
