package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/**
 * SPISCOM logo, painted directly in Java2D (no bundled raster, no SVG runtime —
 * Autopsy ships Batik but not its transcoder, so we can't rasterise an SVG at
 * runtime). Geometry is transcribed 1:1 from the source SVG (viewBox 0 0 660 200):
 * an orbit ring centred at (100,100) r=56 with a purple major arc and an amber
 * accent arc, a purple core dot, an amber satellite dot, and the "SPIS"+"COM"
 * wordmark. Everything is drawn in viewBox units and scaled to the requested
 * pixel height, so it stays crisp at any size.
 */
public class SpiscomLogo extends JComponent {

    // Palette (from the SVG)
    private static final Color PURPLE      = new Color(0x5B21B6); // orbit + core dot
    private static final Color AMBER       = new Color(0xF59E0B); // accent arc, satellite, "COM"
    private static final Color TEXT_PURPLE = new Color(0x4C1D95); // "SPIS"

    private static final double VIEW_H = 200.0;
    private static final int    FONT_SIZE = 58;
    private static final int    LETTER_SPACING = 5;
    private static final double TEXT_X = 196, TEXT_BASELINE = 119;
    private static final String PART1 = "SPIS", PART2 = "COM";

    private final double scale;
    private final int    pxHeight;
    private final int    pxWidth;
    private final Font   logoFont;

    /** @param height desired pixel height; width follows the logo's aspect ratio. */
    public SpiscomLogo(int height) {
        this.pxHeight = height;
        this.scale    = height / VIEW_H;
        this.logoFont = new Font("Arial", Font.BOLD, FONT_SIZE);
        // Measure the wordmark so the component is only as wide as the content
        // (the SVG viewBox has a lot of empty space on the right).
        double contentRight = measureContentRight();
        this.pxWidth = (int) Math.ceil((contentRight + 6) * scale);
        setOpaque(false);
        Dimension d = new Dimension(pxWidth, pxHeight);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
    }

    private double measureContentRight() {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        FontMetrics fm = g.getFontMetrics(logoFont);
        double x = TEXT_X;
        for (char ch : (PART1 + PART2).toCharArray()) x += fm.charWidth(ch) + LETTER_SPACING;
        g.dispose();
        return x;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(scale, scale);

        // Orbit ring, centred (100,100) r=56, stroke 13 round-cap.
        g.setStroke(new BasicStroke(13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        java.awt.geom.Rectangle2D.Double ring =
                new java.awt.geom.Rectangle2D.Double(44, 44, 112, 112);
        g.setColor(PURPLE);
        g.draw(new Arc2D.Double(ring.x, ring.y, ring.width, ring.height, 90, -288.6, Arc2D.OPEN));
        g.setColor(AMBER);
        g.draw(new Arc2D.Double(ring.x, ring.y, ring.width, ring.height, 146.6, -26.6, Arc2D.OPEN));

        // Core dot (purple, r=24) and satellite dot (amber, r=10).
        g.setColor(PURPLE);
        g.fill(new Ellipse2D.Double(100 - 24, 100 - 24, 48, 48));
        g.setColor(AMBER);
        g.fill(new Ellipse2D.Double(156 - 10, 100 - 10, 20, 20));

        // Wordmark "SPIS" (purple) + "COM" (amber), with letter spacing.
        g.setFont(logoFont);
        FontMetrics fm = g.getFontMetrics();
        double x = TEXT_X;
        g.setColor(TEXT_PURPLE);
        x = drawSpaced(g, fm, PART1, x);
        g.setColor(AMBER);
        drawSpaced(g, fm, PART2, x);

        g.dispose();
    }

    private static double drawSpaced(Graphics2D g, FontMetrics fm, String s, double x) {
        for (char ch : s.toCharArray()) {
            g.drawString(String.valueOf(ch), (float) x, (float) TEXT_BASELINE);
            x += fm.charWidth(ch) + LETTER_SPACING;
        }
        return x;
    }
}
