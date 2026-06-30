package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;
import org.sleuthkit.autopsy.enhancedgallery.datamodel.MediaFile;

/**
 * Scrollable grid of thumbnail tiles.
 *
 * Each tile shows:
 *   - decoded thumbnail (or spinner while loading)
 *   - format badge (top-left)
 *   - state icon: 👁 seen / tag label (top-right)
 *   - green dot for GPS (bottom-right inside img)
 *   - 3px colour stripe at bottom for tag colour
 *   - hover overlay with 3 quick-tag buttons + "▾ Tag" dropdown
 *   - filename (below image)
 *
 * Rendering uses a custom paintComponent on a JPanel for performance —
 * no per-cell JLabel/JButton objects, which would cause lag at 1000+ files.
 * Quick-tag buttons are computed hit-box regions checked in MouseListener.
 */
public class ThumbnailGrid extends JScrollPane {

    private static final int    PAD       = 6;
    private static final int    FOOT_H    = 16;
    private static final int    QTBAR_H   = 22;
    private static final Color  BG        = UIManager.getColor("Panel.background");
    private static final Color  SEL_BORDER= new Color(0x378ADD);
    private static final Color  OVERLAY   = new Color(0,0,0,100);

    private final EnhancedGalleryTopComponent parent;
    private final GridPanel gridPanel;

    public ThumbnailGrid(EnhancedGalleryTopComponent parent) {
        this.parent = parent;
        gridPanel = new GridPanel();
        setViewportView(gridPanel);
        setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        getVerticalScrollBar().setUnitIncrement(40);
        setBorder(BorderFactory.createEmptyBorder());

        // Reflow columns whenever the viewport width changes (divider drag, window resize)
        getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                gridPanel.revalidate();
                gridPanel.repaint();
                parent.requestThumbsForViewport();
            }
        });

        // Mouse wheel scrolling
        java.awt.event.MouseWheelListener mwl = e -> {
            int units = e.getWheelRotation() * 3;
            javax.swing.JScrollBar bar = getVerticalScrollBar();
            bar.setValue(bar.getValue() + units * bar.getUnitIncrement());
        };
        addMouseWheelListener(mwl);
        gridPanel.addMouseWheelListener(mwl);

        // Request thumbnails for newly visible tiles as the user scrolls
        getViewport().addChangeListener(e ->
            parent.requestThumbsForViewport());
    }

    /** Returns files currently visible in the scroll viewport (+ 1 row buffer). */
    public List<MediaFile> getViewportFiles() {
        if (gridPanel.files.isEmpty()) return List.of();

        java.awt.Rectangle view = getViewport().getViewRect();
        int cellH = gridPanel.cellH();
        int cellW = gridPanel.cellW();
        int total = gridPanel.files.size();

        // Viewport not yet laid out — load first 2 rows worth as fallback
        if (view.width <= 0 || view.height <= 0 || cellW <= 0 || cellH <= 0) {
            return new java.util.ArrayList<>(gridPanel.files.subList(0, Math.min(50, total)));
        }

        int cols     = Math.max(1, view.width / cellW);
        int firstRow = Math.max(0, view.y / cellH - 1);
        int lastRow  = (view.y + view.height) / cellH + 2;
        int firstIdx = firstRow * cols;
        int lastIdx  = Math.min(total - 1, (lastRow + 1) * cols - 1);

        // Extra safety: if firstIdx==0 and lastIdx<0 (grid too small), show first 50
        if (firstIdx > lastIdx) {
            return new java.util.ArrayList<>(gridPanel.files.subList(0, Math.min(50, total)));
        }
        return new java.util.ArrayList<>(gridPanel.files.subList(firstIdx, lastIdx + 1));
    }

    public void setFiles(List<MediaFile> files) {
        gridPanel.files = new java.util.ArrayList<>(files);
        gridPanel.hoveredIdx = -1;
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    /** Appends a batch of files without resetting the full list. */
    public void addFiles(List<MediaFile> batch) {
        if (!(gridPanel.files instanceof java.util.ArrayList)) {
            gridPanel.files = new java.util.ArrayList<>(gridPanel.files);
        }
        gridPanel.files.addAll(batch);
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    public void setThumbSize(int px) {
        gridPanel.thumbSize = px;
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    public void repaintFile(MediaFile mf) {
        // find index and repaint only that cell
        List<MediaFile> files = gridPanel.files;
        if (files == null) return;
        int idx = files.indexOf(mf);
        if (idx >= 0) {
            Rectangle r = gridPanel.cellRect(idx);
            gridPanel.repaint(r);
        }
    }

    public void clear() {
        gridPanel.files = new java.util.ArrayList<>();
        gridPanel.hoveredIdx = -1;
        gridPanel.repaint();
    }

    public void scrollToTop() {
        getViewport().setViewPosition(new java.awt.Point(0, 0));
    }

    public void scrollToIndex(int idx) {
        Rectangle r = gridPanel.cellRect(idx);
        gridPanel.scrollRectToVisible(r);
    }

    // ── Inner grid panel ─────────────────────────────────────────────────────

    private class GridPanel extends JPanel {

        List<MediaFile> files    = new java.util.ArrayList<>();
        int             thumbSize = 110;
        int             hoveredIdx = -1;

        GridPanel() {
            setBackground(BG);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e)    { onMove(e); }
                @Override public void mouseExited(MouseEvent e)   { hoveredIdx=-1; repaint(); }
                // mouseReleased always fires regardless of mouse movement between press/release.
                // No drag check — scrolling is via mouse wheel only, not drag.
                @Override public void mouseReleased(MouseEvent e) { onClick(e); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private int cols() {
            // Use viewport width so columns reflow when divider is dragged
            int vw = ThumbnailGrid.this.getViewport().getWidth();
            int w = Math.max(vw > 0 ? vw : getWidth(), cellW());
            return Math.max(1, w / cellW());
        }
        int cellW() { return thumbSize + PAD; }
        int cellH() { return thumbSize + FOOT_H + PAD; }

        Rectangle cellRect(int idx) {
            int c = cols();
            int col = idx % c, row = idx / c;
            return new Rectangle(col * cellW(), row * cellH(), cellW(), cellH());
        }

        private int indexAt(int x, int y) {
            int c = cols();
            int col = x / cellW(), row = y / cellH();
            int idx = row * c + col;
            return (idx >= 0 && idx < files.size()) ? idx : -1;
        }

        @Override
        public Dimension getPreferredSize() {
            int vw = ThumbnailGrid.this.getViewport().getWidth();
            int w  = vw > 0 ? vw : Math.max(cellW(), getWidth());
            int c  = Math.max(1, w / cellW());
            int rows = files.isEmpty() ? 0 : (files.size() + c - 1) / c;
            return new Dimension(w, rows * cellH() + PAD);
        }

        private void onMove(MouseEvent e) {
            int idx = indexAt(e.getX(), e.getY());
            if (idx != hoveredIdx) {
                hoveredIdx = idx;
                repaint();
            }
        }

        private void onClick(MouseEvent e) {
            int idx = indexAt(e.getX(), e.getY());
            if (idx < 0) return;

            if (e.getClickCount() == 2) {
                parent.openFileExternally(idx);
                return;
            }

            // Check if click is in the "Follow up" button zone (bottom strip)
            if (isInFollowUpZone(idx, e.getX(), e.getY())) {
                if (idx < files.size()) {
                    MediaFile mf = files.get(idx);
                    // Toggle Follow Up: if already in tags → remove, else → add
                    // Toggle "Follow Up": always pass the tag name
                    // applyTag() will toggle it off if already present
                    parent.onFileClicked(idx, false, false);
                    parent.applyTag("Follow Up");
                }
                return;
            }

            boolean ctrl  = e.isControlDown() || e.isMetaDown();
            boolean shift = e.isShiftDown();
            parent.onFileClicked(idx, ctrl, shift);
        }

        private boolean isInFollowUpZone(int idx, int x, int y) {
            if (idx < 0) return false;
            Rectangle r = cellRect(idx);
            // Button zone: bottom 24px of image area (when hovering)
            return idx == hoveredIdx
                    && y >= r.y + thumbSize - QTBAR_H
                    && y <= r.y + thumbSize;
        }

        private void showTagMenu(int idx, Point p) {
            // Select the file first
            parent.onFileClicked(idx, false);
            JPopupMenu menu = buildTagMenu();
            menu.show(this, p.x, p.y);
        }

        private JPopupMenu buildTagMenu() {
            JPopupMenu m = new JPopupMenu();
            String[][] tags = {
                {"Bookmark","#378ADD"},{"Notable item","#E24B4A"},
                {"Follow up","#EF9F27"},{"Evidence","#0f6e56"},
                {"OK / Irrelevant","#888780"},{"Needs review","#854f0b"},
                {"Child exploitation","#A32D2D"}
            };
            for (String[] t : tags) {
                JMenuItem item = new JMenuItem(t[0]);
                item.addActionListener(e -> parent.applyTag(t[0]));
                m.add(item);
            }
            m.addSeparator();
            JMenuItem rm = new JMenuItem("Remove tag");
            rm.addActionListener(e -> parent.applyTag(null));
            m.add(rm);
            return m;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                               RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Rectangle clip = g.getClipBounds();
            int c = cols();

            for (int i = 0; i < files.size(); i++) {
                Rectangle r = cellRect(i);
                if (!r.intersects(clip)) continue;
                paintCell(g, i, files.get(i), r);
            }
        }

        private void paintCell(Graphics2D g, int idx, MediaFile mf, Rectangle r) {
            boolean isSel     = parent.getSelected().contains(idx);
            boolean isHovered = idx == hoveredIdx;
            boolean isSeen    = mf.getReviewState() == MediaFile.ReviewState.SEEN
                                && !mf.isTagged();
            boolean isLastSel = Integer.valueOf(idx).equals(parent.getSelFile());

            int x = r.x, y = r.y;
            int imgW = thumbSize, imgH = thumbSize;

            // ── Thumbnail image ──────────────────────────────────────────────
            BufferedImage thumb = mf.getThumbnail();
            if (thumb != null) {
                int tw = thumb.getWidth(), th = thumb.getHeight();
                double scale = Math.min((double)imgW/tw, (double)imgH/th);
                int dw=(int)(tw*scale), dh=(int)(th*scale);
                int dx=x+(imgW-dw)/2, dy=y+(imgH-dh)/2;
                g.drawImage(thumb, dx, dy, dw, dh, null);
            } else if (mf.isThumbnailFailed()) {
                // FAILED placeholder — dark red tint, ✕ icon, "no preview" text
                g.setColor(new Color(50, 28, 28));
                g.fillRoundRect(x, y, imgW, imgH, 6, 6);
                int fs2 = Math.max(10, Math.min(16, thumbSize / 8));
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, fs2 * 2));
                g.setColor(new Color(140, 70, 70));
                drawCenteredString(g, "✕", x + imgW/2, y + imgH/2 - fs2/2);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(9, fs2 - 2)));
                g.setColor(new Color(160, 100, 100));
                drawCenteredString(g, "no preview", x + imgW/2, y + imgH*3/4);
            } else {
                // LOADING placeholder — dark blue tint, spinner ring, "loading..." text
                g.setColor(new Color(30, 35, 55));
                g.fillRoundRect(x, y, imgW, imgH, 6, 6);
                g.setColor(new Color(70, 80, 120));
                g.setStroke(new BasicStroke(2));
                int rr = imgW / 5;
                g.drawOval(x + imgW/2 - rr, y + imgH/2 - rr, rr*2, rr*2);
                g.setStroke(new BasicStroke(1));
                g.setColor(new Color(110, 120, 170));
                int fs2 = Math.max(9, Math.min(12, thumbSize / 12));
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, fs2));
                drawCenteredString(g, "loading...", x + imgW/2, y + imgH*3/4);
            }

            // ── Selected: orange border for all selected files ───────────────
            if (isSel) {
                Color borderColor = new Color(0xFF8C00); // orange for all
                int borderW = 4;
                g.setColor(borderColor);
                g.setStroke(new BasicStroke(borderW));
                g.drawRect(x + borderW/2, y + borderW/2,
                        imgW - borderW, imgH - borderW);
                g.setStroke(new BasicStroke(1));
                g.setColor(new Color(255, 140, 0, 30));
                g.fillRect(x, y, imgW, imgH);
            }

            // Hover overlay removed — was causing confusion with click detection

            // ── Format badge (top-left) ──────────────────────────────────────
            paintBadge(g, mf.getExtension().toUpperCase(),
                    formatColor(mf.getMediaType()), x+3, y+3);

            // ── Video indicator (below extension badge, top-left) ────────────
            if (mf.getMediaType() == MediaFile.MediaType.VIDEO) {
                int fontSize = Math.max(8, Math.min(14, thumbSize / 10));
                int badgeH   = fontSize + 6; // same as paintBadge height
                paintVideoIndicator(g, x + 3, y + 3 + badgeH + 2);
            }

            // ── Tag badges (top-right, stacked downward, one per tag) ──────────
            java.util.List<String> tags = mf.getAllTagNames();
            if (!tags.isEmpty()) {
                // Calculate badge height dynamically from font size
                int fontSize = Math.max(8, Math.min(14, thumbSize / 10));
                int badgeH   = fontSize + 6;           // badge height with padding
                int gap      = 3;                       // gap between badges
                int step     = badgeH + gap;
                int maxBadges = Math.max(1, (imgH - 20) / step);

                int badgeY = y + 4;
                for (int ti = 0; ti < Math.min(tags.size(), maxBadges); ti++) {
                    String t = tags.get(ti);
                    String label = t.split("[ /]")[0]; // first word
                    paintBadge(g, label, tagColor(t), x + imgW - 3, badgeY, true);
                    badgeY += step;
                }
                if (tags.size() > maxBadges) {
                    paintBadge(g, "+" + (tags.size() - maxBadges),
                            new Color(80, 80, 80, 200), x + imgW - 3, badgeY, true);
                }
            } else if (isSeen) {
                paintBadge(g, "👁", new Color(60,60,60,180), x+imgW-3, y+3, true);
            }

            // ── GPS dot (bottom-right inside img) ──────────────────────────
            if (parent.getGpsCache().hasGps(mf.getId())) {
                g.setColor(new Color(0x1D9E75));
                g.fillOval(x+imgW-11, y+imgH-11, 8, 8);
            }

            // ── Tag stripes at bottom — one stripe per tag ───────────────────
            if (!tags.isEmpty()) {
                int stripeH = Math.max(3, Math.min(6, 3 * tags.size()));
                int stripeW = imgW / tags.size();
                for (int ti = 0; ti < tags.size(); ti++) {
                    g.setColor(tagColor(tags.get(ti)));
                    g.fillRect(x + ti * stripeW, y + imgH - stripeH,
                            ti == tags.size()-1 ? imgW - ti*stripeW : stripeW,
                            stripeH);
                }
            }

            // ── Quick-tag bar (shown on hover) ───────────────────────────────
            if (isHovered) {
                paintQtBar(g, idx, x, y+imgH-QTBAR_H, imgW);
            }

            // ── Filename footer ──────────────────────────────────────────────
            g.setColor(UIManager.getColor("Label.foreground"));
            int nameFontSize = Math.max(9, Math.min(13, thumbSize / 11));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, nameFontSize));
            String name = mf.getName().replaceAll("\\.[^.]+$","");
            FontMetrics fm = g.getFontMetrics();
            String clipped = clipText(name, fm, imgW - 4);
            g.drawString(clipped, x+2, y+imgH+fm.getAscent()+2);
        }

        private void paintQtBar(Graphics2D g, int idx, int x, int y, int w) {
            if (idx >= files.size()) return;
            MediaFile mf  = files.get(idx);
            // "Follow up" is active if ANY of the file's tags is "Follow up"
            boolean isFu  = mf.getAllTagNames().stream()
                    .anyMatch(t -> "Follow up".equalsIgnoreCase(t));

            // Semi-transparent background
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRoundRect(x, y, w, QTBAR_H, 4, 4);

            // Button — full width
            Color btnColor = isFu
                    ? new Color(0xEF9F27)   // orange = active
                    : new Color(50, 55, 75); // dark = inactive
            g.setColor(btnColor);
            g.fillRoundRect(x + 2, y + 2, w - 4, QTBAR_H - 4, 3, 3);

            // Label
            g.setColor(Color.WHITE);
            int fs = Math.max(9, Math.min(12, thumbSize / 11));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fs));
            String label = isFu ? "★ Follow up" : "☆ Follow up";
            drawCenteredString(g, label, x + w/2, y + QTBAR_H/2);
        }

        private void paintBadge(Graphics2D g, String text, Color bg, int x, int y) {
            paintBadge(g, text, bg, x, y, false);
        }

        private void paintBadge(Graphics2D g, String text, Color bg,
                                 int x, int y, boolean rightAlign) {
            // Badge font scales with thumb size: 8pt at 60px, ~14pt at 190px
            int fontSize = Math.max(8, Math.min(14, thumbSize / 10));
            int padH = fontSize / 2;
            int padV = 2;
            int badgeH = fontSize + padV * 2 + 2;

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(text) + padH * 2;
            int bx = rightAlign ? x - tw : x;
            g.setColor(bg);
            g.fillRoundRect(bx, y, tw, badgeH, 4, 4);
            g.setColor(Color.WHITE);
            g.drawString(text, bx + padH, y + fontSize + padV);
        }

        /** Draws a small film-strip icon. ix/iy = top-left corner of the icon. */
        private void paintVideoIndicator(Graphics2D g, int ix, int iy) {
            int sz   = Math.max(14, Math.min(22, thumbSize / 7));
            int hole = Math.max(2, sz / 5);
            int gap  = Math.max(1, sz / 10);

            // Semi-transparent dark background strip
            g.setColor(new Color(0, 0, 0, 155));
            g.fillRoundRect(ix, iy, sz, sz, 4, 4);

            // Film strip: white holes on left and right edges, 3 each
            g.setColor(new Color(255, 255, 255, 220));
            int holeSpacing = (sz - gap * 2) / 3;
            for (int i = 0; i < 3; i++) {
                int hy = iy + gap + i * holeSpacing + (holeSpacing - hole) / 2;
                g.fillRect(ix + gap,          hy, hole, hole); // left column
                g.fillRect(ix + sz - gap * 2, hy, hole, hole); // right column
            }

            // Center play triangle
            int cx = ix + sz / 2, cy = iy + sz / 2;
            int ts = Math.max(3, sz / 4);
            int[] px = {cx - ts + 1, cx + ts, cx - ts + 1};
            int[] py = {cy - ts,     cy,       cy + ts};
            g.setColor(new Color(255, 220, 50, 230));
            g.fillPolygon(px, py, 3);
        }

        private void drawCenteredString(Graphics2D g, String s, int cx, int cy) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, cx - fm.stringWidth(s)/2,
                         cy + fm.getAscent()/2 - fm.getDescent()/2);
        }

        private String clipText(String s, FontMetrics fm, int maxW) {
            if (fm.stringWidth(s) <= maxW) return s;
            while (s.length()>1 && fm.stringWidth(s+"…")>maxW) s=s.substring(0,s.length()-1);
            return s+"…";
        }

        private Color formatColor(MediaFile.MediaType t) {
            return switch(t) {
                case IMAGE -> new Color(0x534AB7);
                case VIDEO -> new Color(0x7F77DD);
                case AUDIO -> new Color(0x0f6e56);
            };
        }

        private Color tagColor(String tag) {
            if (tag == null) return Color.GRAY;
            return switch(tag.toLowerCase().replace(" ","_").split("/")[0].trim()) {
                case "bookmark"          -> new Color(0x378ADD);
                case "notable_item"      -> new Color(0xE24B4A);
                case "notable"           -> new Color(0xE24B4A);
                case "follow_up"         -> new Color(0xEF9F27);
                case "evidence"          -> new Color(0x0f6e56);
                case "ok"                -> new Color(0x888780);
                case "needs_review"      -> new Color(0x854f0b);
                case "child_exploitation"-> new Color(0xA32D2D);
                case "child"             -> new Color(0xA32D2D);
                default                  -> new Color(0x534AB7);
            };
        }
    }
}
