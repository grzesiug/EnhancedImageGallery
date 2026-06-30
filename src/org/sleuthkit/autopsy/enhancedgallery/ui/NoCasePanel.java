package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import javax.swing.*;

/**
 * Simple placeholder shown inside the TopComponent when no case is open.
 */
class NoCasePanel extends JPanel {

    NoCasePanel() {
        setLayout(new GridBagLayout());
        setBackground(UIManager.getColor("Panel.background"));

        JLabel icon = new JLabel("🖼️");
        icon.setFont(icon.getFont().deriveFont(48f));
        icon.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel msg = new JLabel(
                "<html><center>No case is open.<br>"
                + "Please open or create a case first,<br>"
                + "then reopen Enhanced Gallery from the Tools menu.</center></html>");
        msg.setHorizontalAlignment(SwingConstants.CENTER);
        msg.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(icon);
        inner.add(Box.createVerticalStrut(12));
        inner.add(msg);

        add(inner);
    }
}
