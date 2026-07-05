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

    /** Shows the bar with the given query/label and result count. */
    public void showBar(String labelText, int count) {
        label.setText("<html><b>AI:</b> "
                + escape(labelText) + "  &mdash;  " + count + " result"
                + (count == 1 ? "" : "s") + " (by relevance)</html>");
        setVisible(true);
        revalidate();
        repaint();
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
