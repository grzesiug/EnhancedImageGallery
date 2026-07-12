import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Generates gallery.png icon (32x32) for Enhanced Evidence Gallery module.
 * Run: java generate_icon.java
 * Output: src/org/sleuthkit/autopsy/enhancedgallery/resources/gallery.png
 */
public class generate_icon {
    public static void main(String[] args) throws Exception {
        for (int size : new int[]{16, 32, 64, 128, 256}) {
            BufferedImage img = draw(size);
            String path = "src/org/sleuthkit/autopsy/enhancedgallery/resources/gallery"
                    + (size == 32 ? "" : size) + ".png";
            ImageIO.write(img, "png", new File(path));
            System.out.println("Written: " + path);
        }
    }

    static BufferedImage draw(int S) {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,    RenderingHints.VALUE_RENDER_QUALITY);

        float s = S / 32f; // scale factor

        // Rounded background
        int r = Math.max(2, (int)(5 * s));
        g.setColor(new Color(0x1e3a5f));
        g.fill(new RoundRectangle2D.Float(0, 0, S, S, r*2, r*2));

        // Photo grid (3 cols x 2 rows of thumbnails)
        Color[] thumbColors = {
            new Color(0x2d6a4f), new Color(0x1a4b7a), new Color(0x6b2d2d),
            new Color(0x4a3728), new Color(0x2d6a4f), new Color(0x1a4b7a)
        };
        int cols = 3, rows = 2;
        float margin  = 2 * s;
        float gap     = 1.5f * s;
        float topH    = (S * 0.5f); // top half for grid
        float tw = (S - 2*margin - (cols-1)*gap) / cols;
        float th = (topH - margin - (rows-1)*gap) / rows;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                float tx = margin + col * (tw + gap);
                float ty = margin + row * (th + gap);
                g.setColor(thumbColors[row * cols + col]);
                g.fill(new RoundRectangle2D.Float(tx, ty, tw, th, 1.5f*s, 1.5f*s));
                // Small mountain icon inside thumb
                if (S >= 32) {
                    g.setColor(new Color(255, 255, 255, 60));
                    float cx = tx + tw/2, cy = ty + th/2;
                    float mw = tw*0.5f, mh = th*0.45f;
                    g.fill(new java.awt.geom.Path2D.Float() {{
                        moveTo(cx - mw/2, cy + mh/2);
                        lineTo(cx, cy - mh/2);
                        lineTo(cx + mw/2, cy + mh/2);
                        closePath();
                    }});
                }
            }
        }

        // Magnifying glass
        float glassY  = topH + 1.5f * s;
        float glassH  = S - glassY - margin;
        float glassR  = glassH * 0.45f;
        float glassCX = S * 0.42f;
        float glassCY = glassY + glassH * 0.46f;

        // Lens circle
        g.setColor(new Color(0xf0a500));
        g.setStroke(new BasicStroke(Math.max(1.5f, 2.5f * s),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Float(glassCX - glassR, glassCY - glassR, glassR*2, glassR*2));

        // Lens fill tint
        g.setColor(new Color(240, 165, 0, 35));
        g.fill(new Ellipse2D.Float(glassCX - glassR, glassCY - glassR, glassR*2, glassR*2));

        // Handle
        float handleAngle = (float)(Math.PI * 0.75);
        float hx1 = glassCX + (float)Math.cos(handleAngle) * glassR;
        float hy1 = glassCY + (float)Math.sin(handleAngle) * glassR;
        float hx2 = glassCX + (float)Math.cos(handleAngle) * (glassR + glassH * 0.55f);
        float hy2 = glassCY + (float)Math.sin(handleAngle) * (glassH * 0.55f + glassR);
        g.setColor(new Color(0xf0a500));
        g.setStroke(new BasicStroke(Math.max(1.5f, 2.5f * s),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int)hx1, (int)hy1, (int)hx2, (int)hy2);

        g.dispose();
        return img;
    }
}
