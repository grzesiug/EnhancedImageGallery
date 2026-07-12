package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/**
 * Thin bar shown between the action bar and the thumbnail grid while an AI
 * (semantic / find-similar) search is active. Hidden by default, so it has no
 * effect on normal gallery use.
 *
 *   🧠 AI: "osoba z dokumentem"  —  37 results        [✕ Clear]
 */
public class SemanticBar extends JPanel {

    private final JLabel  label    = new JLabel();
    private final JButton clearBtn = new JButton("✕ Clear");

    // Remembered so updateVisible() can re-render the "N hidden by filters" suffix
    // without the caller re-supplying the query text and total each time.
    private String labelText = "";
    private int    total     = 0;

    public SemanticBar(EnhancedGalleryTopComponent parent) {
        setLayout(new BorderLayout(8, 0));
        setBackground(new Color(0xEAF1FF));
        setBorder(new javax.swing.border.CompoundBorder(
                new MatteBorder(0, 0, 1, 0, new Color(0xB6C9EA)),
                new EmptyBorder(3, 10, 3, 10)));

        label.setFont(label.getFont().deriveFont(12f));
        label.setForeground(new Color(0x1E3A8A));
        label.setIcon(new AiSearchIcon(16));
        label.setIconTextGap(6);

        clearBtn.setFont(clearBtn.getFont().deriveFont(11f));
        clearBtn.setMargin(new Insets(1, 8, 1, 8));
        clearBtn.setFocusPainted(false);
        clearBtn.setToolTipText("Clear the AI search and return to the normal view");
        clearBtn.addActionListener(e -> parent.clearSemanticSearch());

        add(label,    BorderLayout.WEST);
        add(clearBtn, BorderLayout.EAST);

        setVisible(false); // hidden until an AI search is active
    }

    /** Shows the bar with the given query/label and total hit count (before filters). */
    public void showBar(String labelText, int count) {
        this.labelText = labelText;
        this.total     = count;
        render(count); // no filtering applied yet — shown == total
        setVisible(true);
        revalidate();
        repaint();
    }

    /**
     * Updates the bar after filtering: {@code shown} hits remain visible; the rest
     * of the {@code total} are hidden by the active filters / selected group.
     */
    public void updateVisible(int shown) {
        render(shown);
        revalidate();
        repaint();
    }

    private void render(int shown) {
        int hidden = Math.max(0, total - shown);
        StringBuilder sb = new StringBuilder("<html><b>AI:</b> ");
        sb.append(escape(labelText)).append("  &mdash;  ")
          .append(shown).append(" result").append(shown == 1 ? "" : "s")
          .append(" (by relevance)");
        if (hidden > 0) {
            sb.append("  <span style='color:#9A3412'>&mdash; ").append(hidden)
              .append(" hidden by filters</span>");
        }
        sb.append("</html>");
        label.setText(sb.toString());
    }

    public void hideBar() {
        setVisible(false);
        revalidate();
        repaint();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
