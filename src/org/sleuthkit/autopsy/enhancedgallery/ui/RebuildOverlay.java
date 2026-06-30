package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import java.awt.geom.Arc2D;
import javax.swing.*;

/**
 * Semi-transparent overlay shown during sidebar group rebuild.
 * Displays a spinning arc + elapsed seconds counter in the center of the gallery.
 * Installed as the glass pane of the gallery JFrame.
 */
public class RebuildOverlay extends JComponent {

    private static final int  SPINNER_R   = 28;
    private static final int  SPINNER_W   = 6;
    private static final int  BOX_W       = 160;
    private static final int  BOX_H       = 90;
    private static final Color BG         = new Color(20, 20, 30, 195);
    private static final Color TRACK      = new Color(80, 80, 100);
    private static final Color ARC        = new Color(99, 149, 255);
    private static final Color TEXT_COLOR = new Color(220, 220, 240);

    private volatile boolean  active    = false;
    private volatile long     startedAt = 0;
    private          float    angle     = 0f;
    private          Timer    timer;

    public RebuildOverlay() {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    /** Show the overlay and start the spinner. */
    public void showOverlay() {
        if (active) return;
        active    = true;
        startedAt = System.currentTimeMillis();
        setVisible(true);
        timer = new Timer(40, e -> {
            angle = (angle + 8f) % 360f;
            repaint();
        });
        timer.start();
    }

    /** Hide the overlay and stop the spinner. */
    public void hideOverlay() {
        active = false;
        if (timer != null) { timer.stop(); timer = null; }
        setVisible(false);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (!active) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cx = getWidth()  / 2;
        int cy = getHeight() / 2;

        // Rounded background box
        int bx = cx - BOX_W / 2;
        int by = cy - BOX_H / 2;
        g.setColor(BG);
        g.fillRoundRect(bx, by, BOX_W, BOX_H, 18, 18);

        // Spinner track
        int sx = cx - SPINNER_R;
        int sy = cy - SPINNER_R - 10;
        int sd = SPINNER_R * 2;
        g.setStroke(new BasicStroke(SPINNER_W, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(TRACK);
        g.drawOval(sx, sy, sd, sd);

        // Spinner arc
        g.setColor(ARC);
        g.draw(new Arc2D.Float(sx, sy, sd, sd, angle, 90, Arc2D.OPEN));

        // Elapsed time label
        long elapsed = (System.currentTimeMillis() - startedAt) / 1000;
        String label = elapsed < 1 ? "Refreshing…" : "Refreshing… " + elapsed + "s";
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 12.5f));
        FontMetrics fm = g.getFontMetrics();
        int tx = cx - fm.stringWidth(label) / 2;
        int ty = cy + SPINNER_R + 6;
        g.setColor(TEXT_COLOR);
        g.drawString(label, tx, ty);

        g.dispose();
    }

    @Override
    public boolean contains(int x, int y) {
        // Only consume mouse events when active so clicks pass through when hidden
        return active && super.contains(x, y);
    }
}
