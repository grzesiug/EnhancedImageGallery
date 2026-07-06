package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/**
 * Small vector "price tag / label" icon for the Tag button. Painted with Java2D
 * so it needs no binary asset and stays crisp at any size.
 */
public class TagIcon implements Icon {

    private final int   size;
    private final Color fill;

    public TagIcon(int size) { this(size, new Color(0x2563EB)); }
    public TagIcon(int size, Color fill) { this.size = size; this.fill = fill; }

    @Override public int getIconWidth()  { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(x, y);

        float s = size;
        float m = s * 0.10f;          // margin
        float w = s - 2 * m;
        float notch = w * 0.42f;      // depth of the pointed left end

        // Tag body: pointed on the left, squared on the right (label shape)
        Path2D tag = new Path2D.Float();
        tag.moveTo(m + notch, m);
        tag.lineTo(m + w,     m);
        tag.lineTo(m + w,     m + w);
        tag.lineTo(m + notch, m + w);
        tag.lineTo(m,         m + w / 2f); // point
        tag.closePath();

        g.setColor(fill);
        g.fill(tag);

        // Hole (grommet) near the point
        g.setColor(new Color(255, 255, 255, 235));
        float hr = w * 0.13f;
        float hx = m + notch * 0.62f, hy = m + w / 2f;
        g.fillOval(Math.round(hx - hr), Math.round(hy - hr),
                   Math.round(hr * 2), Math.round(hr * 2));

        g.dispose();
    }
}
