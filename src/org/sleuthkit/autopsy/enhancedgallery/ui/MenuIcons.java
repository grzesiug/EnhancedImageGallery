package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.Icon;

/**
 * Small vector icons for the thumbnail context menu, drawn in Java2D so they
 * stay crisp at any size and need no bundled resources (same approach as
 * {@link TagIcon} / {@link SpiscomLogo}). Every menu item gets an icon so the
 * text column aligns and the left gutter doesn't look like a random blank strip.
 */
final class MenuIcons {

    private MenuIcons() {}

    private static abstract class Base implements Icon {
        final int size;
        Base(int size) { this.size = size; }
        @Override public int getIconWidth()  { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);
            double sc = size / 16.0;   // glyphs are designed on a 16x16 grid
            g.scale(sc, sc);
            paint16(g, c.isEnabled());
            g.dispose();
        }
        /** Paints the glyph on a 16x16 unit grid. */
        abstract void paint16(Graphics2D g, boolean enabled);
        static Color col(boolean enabled, int rgb) {
            return enabled ? new Color(rgb) : new Color(0xB0B4C0);
        }
    }

    /** Box with an arrow escaping to the top-right — "open in external app". */
    static Icon openExternal(int size) {
        return new Base(size) {
            @Override void paint16(Graphics2D g, boolean en) {
                g.setColor(col(en, 0x475569));
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // Box (open on the top-right corner)
                g.draw(new Path2D.Double() {{
                    moveTo(9, 3); lineTo(4, 3); quadTo(3, 3, 3, 4);
                    lineTo(3, 12); quadTo(3, 13, 4, 13);
                    lineTo(12, 13); quadTo(13, 13, 13, 12); lineTo(13, 8);
                }});
                // Arrow
                g.draw(new Line2D.Double(8.5, 7.5, 13.5, 2.5));
                g.draw(new Line2D.Double(10, 2.5, 13.5, 2.5));
                g.draw(new Line2D.Double(13.5, 2.5, 13.5, 6));
            }
        };
    }

    /** Arrow into a tray — "save to disk". */
    static Icon save(int size) {
        return new Base(size) {
            @Override void paint16(Graphics2D g, boolean en) {
                g.setColor(col(en, 0x15803D));
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // Down arrow
                g.draw(new Line2D.Double(8, 2.5, 8, 9.5));
                g.draw(new Line2D.Double(5, 6.8, 8, 9.8));
                g.draw(new Line2D.Double(11, 6.8, 8, 9.8));
                // Tray
                g.draw(new Path2D.Double() {{
                    moveTo(3, 10.5); lineTo(3, 12.5); quadTo(3, 13.5, 4, 13.5);
                    lineTo(12, 13.5); quadTo(13, 13.5, 13, 12.5); lineTo(13, 10.5);
                }});
            }
        };
    }

    /** Map pin — "show on map". */
    static Icon mapPin(int size) {
        return new Base(size) {
            @Override void paint16(Graphics2D g, boolean en) {
                g.setColor(col(en, 0xB91C1C));
                // Teardrop pin: circle head + triangle tail
                g.fill(new Ellipse2D.Double(4.5, 2, 7, 7));
                Path2D tail = new Path2D.Double();
                tail.moveTo(5.4, 7.6); tail.lineTo(10.6, 7.6); tail.lineTo(8, 13.5);
                tail.closePath();
                g.fill(tail);
                // Hole
                g.setColor(en ? Color.WHITE : new Color(0xE7E9F0));
                g.fill(new Ellipse2D.Double(6.9, 4.4, 2.2, 2.2));
            }
        };
    }
}
