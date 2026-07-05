package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.Icon;

/**
 * Brain icon for the AI semantic-search UI, loaded from the bundled
 * {@code resources/brain_icon.png} and scaled to the requested size.
 */
public class AiSearchIcon implements Icon {

    private static final BufferedImage SRC = load();

    private static BufferedImage load() {
        try {
            BufferedImage raw = ImageIO.read(AiSearchIcon.class.getResource(
                    "/org/sleuthkit/autopsy/enhancedgallery/resources/brain_icon.png"));
            if (raw == null) return null;
            // The source PNG has a solid white background (no alpha). Key it out so
            // only the brain shows on the button / toolbar. Near-white → transparent.
            int w = raw.getWidth(), h = raw.getHeight();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int yy = 0; yy < h; yy++) {
                for (int xx = 0; xx < w; xx++) {
                    int argb = raw.getRGB(xx, yy);
                    int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                    if (r >= 244 && g >= 244 && b >= 244) {
                        out.setRGB(xx, yy, 0x00000000); // transparent
                    } else {
                        out.setRGB(xx, yy, 0xFF000000 | (argb & 0xFFFFFF));
                    }
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private final int size;

    public AiSearchIcon(int size) { this.size = size; }

    @Override public int getIconWidth()  { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        if (SRC == null) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(SRC, x, y, size, size, null);
        g.dispose();
    }
}
