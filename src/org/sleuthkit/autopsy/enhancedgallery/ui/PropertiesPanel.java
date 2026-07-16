package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.enhancedgallery.datamodel.GpsCache;
import org.sleuthkit.autopsy.enhancedgallery.datamodel.MediaFile;
import org.sleuthkit.datamodel.*;

/**
 * Right panel: thumbnail + full Autopsy-style file properties + EXIF + GPS.
 */
public class PropertiesPanel extends JPanel {

    private static final Logger logger = Logger.getLogger(PropertiesPanel.class.getName());
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

    private final EnhancedGalleryTopComponent parent;
    private MediaFile currentFile;
    private GpsCache  currentGps;
    private double    artifactLat = Double.NaN;
    private double    artifactLon = Double.NaN;

    // ── UI components ─────────────────────────────────────────────────────────
    private final ThumbnailPreview thumbPreview = new ThumbnailPreview();
    private final DefaultTableModel tableModel  = new DefaultTableModel(
            new Object[]{"Attribute", "Value"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable    table        = new JTable(tableModel);
    private final JButton   removeTagBtn = new JButton("✕ Remove tags");
    private final JButton   mapsBtn      = new JButton("🗺 Open in Google Maps");

    // ── Constructor ───────────────────────────────────────────────────────────

    public PropertiesPanel(EnhancedGalleryTopComponent parent) {
        this.parent = parent;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(290, 0));
        setBackground(UIManager.getColor("Panel.background"));

        // Thumbnail
        thumbPreview.setPreferredSize(new Dimension(290, 200));
        thumbPreview.setBorder(new MatteBorder(0, 0, 1, 0, new Color(200, 200, 210)));

        // Table with wrapping Value column
        table.setFont(table.getFont().deriveFont(11f));
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 11f));
        table.getTableHeader().setBackground(new Color(230, 232, 240));
        table.getTableHeader().setPreferredSize(new Dimension(0, 24));
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(0).setMaxWidth(140);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        table.setDefaultRenderer(Object.class, new WrappingRenderer());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);

        // Ctrl+C — copy value of selected row
        table.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
             .put(javax.swing.KeyStroke.getKeyStroke("control C"), "copyValue");
        table.getActionMap().put("copyValue", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                copySelectedValue();
            }
        });

        // Right-click context menu
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem copyItem = new javax.swing.JMenuItem("Copy value");
        copyItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke("control C"));
        copyItem.addActionListener(e -> copySelectedValue());
        popup.add(copyItem);

        javax.swing.JMenuItem copyRowItem = new javax.swing.JMenuItem("Copy row (key: value)");
        copyRowItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            Object key = tableModel.getValueAt(row, 0);
            Object val = tableModel.getValueAt(row, 1);
            String text = (key != null ? key.toString() : "") + ": "
                        + (val != null ? val.toString() : "");
            copyToClipboard(text);
        });
        popup.add(copyRowItem);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e)  { maybeShowPopup(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeShowPopup(e); }
            private void maybeShowPopup(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) table.setRowSelectionInterval(row, row);
                popup.show(table, e.getX(), e.getY());
            }
        });

        // Adjust row heights when table size changes
        table.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { adjustRowHeights(); }
        });

        // Remove tag button
        // Use a custom painted button to ensure colors are visible on all L&F
        styleBtn(removeTagBtn, new Color(0xDC2626), Color.WHITE);
        removeTagBtn.setVisible(false);
        removeTagBtn.addActionListener(e -> { if (currentFile != null) parent.applyTag(null); });

        styleBtn(mapsBtn, new Color(0x15803D), Color.WHITE);
        mapsBtn.setVisible(false);
        mapsBtn.addActionListener(e -> openGoogleMaps());

        // WrapLayout so that, when the panel is too narrow for both buttons
        // side by side, "Open in Google Maps" wraps to a second row (and the
        // panel grows taller) instead of being clipped out of view.
        JPanel btns = new JPanel(new WrapLayout(FlowLayout.CENTER, 6, 4));
        btns.setOpaque(false);
        btns.add(removeTagBtn);
        btns.add(mapsBtn);

        JScrollPane scroll = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(scroll, BorderLayout.CENTER);
        bottom.add(btns,   BorderLayout.SOUTH);

        add(thumbPreview, BorderLayout.NORTH);
        add(bottom,       BorderLayout.CENTER);

        showEmpty();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void show(MediaFile mf, GpsCache gps) {
        this.currentFile = mf;
        this.currentGps  = gps;
        if (mf == null) { showEmpty(); return; }

        thumbPreview.setImage(mf.getThumbnail());
        tableModel.setRowCount(0);

        AbstractFile af = mf.getAbstractFile();

        // ── Matched text (document text search) ───────────────────────────────
        // Shown at the top so the analyst immediately sees WHY this document matched.
        String snippet = parent.getSemanticSnippet(af.getId());
        if (snippet != null) {
            section("Matched text");
            addRow("Snippet", "…" + snippet + "…");
        }

        // ── Conversation (message-thread card from AITT) ──────────────────────
        if (mf.isThread()) {
            section("Conversation");
            addRow("Thread", mf.getDocLabel());
            addRow("App", mf.getDocApp().isBlank() ? "—" : mf.getDocApp());
            java.util.List<String> parts = mf.getDocParticipants();
            addRow("Participants", parts.isEmpty() ? "—" : String.join(", ", parts));
            if (mf.getDocMsgCount() > 0)
                addRow("Messages", String.valueOf(mf.getDocMsgCount()));
            String ds = mf.getDocDateStart(), de = mf.getDocDateEnd();
            if (!ds.isBlank() || !de.isBlank())
                addRow("Date range", (ds.isBlank() ? "?" : ds) + "  →  " + (de.isBlank() ? "?" : de));
            addRow("Source file", af.getName());
        }

        // ── Basic file info ───────────────────────────────────────────────────
        section("File Info");
        addRow("Name",      af.getName());
        addRow("Type",      af.getType().getName());
        addRow("MIME Type", nvl(af.getMIMEType()));
        addRow("Size",      String.format("%,d bytes  (%s)", af.getSize(), humanSize(af.getSize())));

        // Allocation flags
        try {
            addRow("File Name Alloc.",
                    af.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC)
                            ? "Allocated" : "Unallocated");
            addRow("Metadata Alloc.",
                    af.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC)
                            ? "Allocated" : "Unallocated");
        } catch (Exception ignored) {}

        // ── Timestamps ────────────────────────────────────────────────────────
        section("Timestamps");
        addRow("Modified",  fmtUnixSec(af.getMtime()));
        addRow("Accessed",  fmtUnixSec(af.getAtime()));
        addRow("Created",   fmtUnixSec(af.getCrtime()));
        addRow("Changed",   fmtUnixSec(af.getCtime()));

        // ── Hashes ────────────────────────────────────────────────────────────
        section("Hashes");
        addRow("MD5",    nvl(af.getMd5Hash()));
        addRow("SHA-256", nvl(af.getSha256Hash()));

        // Known status (hash lookup)
        try {
            TskData.FileKnown known = af.getKnown();
            String knownStr;
            if (known == TskData.FileKnown.KNOWN)     knownStr = "KNOWN (safe)";
            else if (known == TskData.FileKnown.BAD)  knownStr = "KNOWN BAD";
            else                                       knownStr = "UNKNOWN";
            addRow("Hash Result", knownStr);
        } catch (Exception ignored) {}

        // ── Location ──────────────────────────────────────────────────────────
        section("Location");
        addRow("Data Source",  mf.getDataSourceName());
        addRow("Path",         mf.getUniquePath());
        addRow("Internal ID",  String.valueOf(af.getId()));

        // ── Review ────────────────────────────────────────────────────────────
        section("Review");
        addRow("Status", mf.getReviewState().name());
        java.util.List<String> tags = mf.getAllTagNames();
        if (tags.isEmpty())       addRow("Tags", "—");
        else if (tags.size() == 1) addRow("Tag",  tags.get(0));
        else                       addRow("Tags", String.join(", ", tags));

        // Tag comments from Autopsy (e.g. AI classifier notes). Show ALL comments,
        // including the gallery's own "Enhanced Gallery" marker, so what the
        // investigator sees here matches exactly what Autopsy shows for the tag.
        // Same EDT DB-query pattern already used below for EXIF; guarded so it
        // never breaks the panel.
        try {
            var tm = org.sleuthkit.autopsy.casemodule.Case.getCurrentCaseThrows()
                    .getServices().getTagsManager();
            java.util.List<org.sleuthkit.datamodel.ContentTag> commented =
                    new java.util.ArrayList<>();
            for (org.sleuthkit.datamodel.ContentTag ct : tm.getContentTagsByContent(af)) {
                String cm = ct.getComment();
                if (cm != null && !cm.isBlank()) commented.add(ct);
            }
            for (org.sleuthkit.datamodel.ContentTag ct : commented) {
                String label = commented.size() > 1
                        ? "Comment [" + ct.getName().getDisplayName() + "]" : "Comment";
                addRow(label, ct.getComment());
            }
        } catch (Exception ignored) { /* tags manager unavailable — skip comments */ }

        // ── GPS from GpsCache + EXIF artifacts (called once below) ──────────────
        artifactLat = Double.NaN;
        artifactLon = Double.NaN;
        boolean hasGps = (gps != null && gps.hasGps(mf.getId()));
        if (hasGps) {
            GpsCache.GpsPoint pt = gps.getGps(mf.getId());
            if (pt != null) {
                section("GPS");
                addRow("Latitude",  String.format("%.6f", pt.lat));
                addRow("Longitude", String.format("%.6f", pt.lng));
                if (pt.label != null && !pt.label.isEmpty())
                    addRow("Location", pt.label);
                artifactLat = pt.lat;
                artifactLon = pt.lng;
            }
        }

        // Load EXIF + GPS artifacts (may update artifactLat/Lon)
        loadExifFromBlackboard(af);

        boolean showMaps = hasGps || (!Double.isNaN(artifactLat) && !Double.isNaN(artifactLon));
        removeTagBtn.setVisible(mf.isTagged());
        mapsBtn.setVisible(showMaps);

        SwingUtilities.invokeLater(this::adjustRowHeights);
    }

    public void refresh() {
        if (currentFile != null) show(currentFile, currentGps);
    }

    public MediaFile getCurrentFile() { return currentFile; }

    // ── EXIF from Blackboard ──────────────────────────────────────────────────

    private void loadExifFromBlackboard(AbstractFile af) {
        try {
            // Get ALL artifacts for this file, then filter EXIF + GPS types
            java.util.List<BlackboardArtifact> allArts = af.getAllArtifacts();
            if (allArts.isEmpty()) return;

            // Collect EXIF attributes (skip empty / zero values)
            java.util.List<String[]> exifRows = new java.util.ArrayList<>();
            java.util.List<String[]> gpsRows  = new java.util.ArrayList<>();

            int exifTypeId = BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID();

            for (BlackboardArtifact art : allArts) {
                int typeId = art.getArtifactTypeID();
                boolean isExif = (typeId == exifTypeId);
                // GPS artifact type IDs
                boolean isGps  = (typeId == 52 || typeId == 53 || typeId == 54);
                if (!isExif && !isGps) continue;

                for (BlackboardAttribute attr : art.getAttributes()) {
                    String name  = attr.getAttributeType().getDisplayName();
                    String value = attrValue(attr);
                    if (value == null || value.isBlank() || "—".equals(value)) continue;
                    if (name == null || name.isBlank()) continue;

                    // Extract lat/lon directly from attribute value (avoid locale issues)
                    int attrTypeId = attr.getAttributeType().getTypeID();
                    BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE vt =
                            attr.getAttributeType().getValueType();
                    if (vt == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DOUBLE) {
                        double d = attr.getValueDouble();
                        // Some EXIF writers store garbage (e.g. 2183275042.13) — only
                        // physically possible values may drive the map button. The raw
                        // value still shows in the EXIF/GPS rows below (honest view of
                        // the artifact), it just can't produce a nonsense maps link.
                        boolean latOk = Double.isFinite(d) && d >= -90.0  && d <= 90.0;
                        boolean lonOk = Double.isFinite(d) && d >= -180.0 && d <= 180.0;
                        // TSK_GEO_LATITUDE=119, TSK_GEO_LONGITUDE=120
                        if (attrTypeId == 119 && latOk) artifactLat = d;
                        if (attrTypeId == 120 && lonOk) artifactLon = d;
                        // Also check display name as fallback
                        String nl = name.toLowerCase();
                        if (Double.isNaN(artifactLat) && latOk && nl.contains("latit"))  artifactLat = d;
                        if (Double.isNaN(artifactLon) && lonOk && nl.contains("longit")) artifactLon = d;
                    }

                    if (isGps) gpsRows.add(new String[]{name, value});
                    else        exifRows.add(new String[]{name, value});
                }
            }

            if (!exifRows.isEmpty()) {
                section("EXIF");
                for (String[] row : exifRows) addRow(row[0], row[1]);
            }
            if (!gpsRows.isEmpty()) {
                section("GPS (Artifact)");
                for (String[] row : gpsRows) addRow(row[0], row[1]);
            }

        } catch (Exception ex) {
            logger.log(Level.WARNING, "EXIF/GPS load failed for " + af.getName(), ex);
        }
    }

    private static String attrValue(BlackboardAttribute attr) {
        return switch (attr.getAttributeType().getValueType()) {
            case STRING   -> attr.getValueString();
            case INTEGER  -> String.valueOf(attr.getValueInt());
            case LONG     -> String.valueOf(attr.getValueLong());
            case DOUBLE   -> String.format(java.util.Locale.US, "%.6f", attr.getValueDouble());
            case DATETIME -> attr.getValueLong() == 0 ? "—"
                    : fmtUnixSec(attr.getValueLong());
            default -> attr.getDisplayString();
        };
    }

    // ── Clipboard helpers ─────────────────────────────────────────────────────

    private void copySelectedValue() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        Object val = tableModel.getValueAt(row, 1);
        copyToClipboard(val != null ? val.toString() : "");
    }

    private static void copyToClipboard(String text) {
        try {
            java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(text);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
        } catch (Exception ignored) {}
    }

    // ── Google Maps ───────────────────────────────────────────────────────────

    private void openGoogleMaps() {
        double lat = Double.NaN, lon = Double.NaN;

        // Prefer GpsCache (more reliable)
        if (currentFile != null && currentGps != null && currentGps.hasGps(currentFile.getId())) {
            GpsCache.GpsPoint pt = currentGps.getGps(currentFile.getId());
            if (pt != null) { lat = pt.lat; lon = pt.lng; }
        }
        // Fallback to artifact GPS
        if (Double.isNaN(lat) && !Double.isNaN(artifactLat)) {
            lat = artifactLat; lon = artifactLon;
        }
        if (Double.isNaN(lat)) return;

        String url = String.format(java.util.Locale.US,
                "https://www.google.com/maps?q=%.6f,%.6f", lat, lon);
        try {
            Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Cannot open Google Maps", ex);
        }
    }

    // ── Row height adjustment for wrapped text ────────────────────────────────

    private void adjustRowHeights() {
        int colWidth = table.getColumnModel().getColumn(1).getWidth();
        if (colWidth <= 0) return;
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            Object val = tableModel.getValueAt(r, 1);
            if (val == null) continue;
            JTextArea ta = new JTextArea(val.toString());
            ta.setFont(table.getFont());
            ta.setLineWrap(true);
            // Character wrap (not word) so long unbroken tokens — file paths,
            // hashes — wrap instead of overflowing and disappearing.
            ta.setWrapStyleWord(false);
            ta.setSize(colWidth - 8, Integer.MAX_VALUE);
            int prefH = Math.max(22, ta.getPreferredSize().height + 6);
            if (table.getRowHeight(r) != prefH) table.setRowHeight(r, prefH);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showEmpty() {
        thumbPreview.setImage(null);
        tableModel.setRowCount(0);
        addRow("", "Select a file to view its properties");
        removeTagBtn.setVisible(false);
        mapsBtn.setVisible(false);
    }

    /** Adds a section header row (grey background, bold). */
    private void addSection(String title) {
        tableModel.addRow(new Object[]{"§" + title, ""});
    }
    private void section(String t) { addSection(t); }
    private void addRow(String attr, String value) {
        tableModel.addRow(new Object[]{attr, value != null ? value : "—"});
    }

    private static String humanSize(long b) {
        if (b < 1024)    return b + " B";
        if (b < 1 << 20) return String.format("%.1f KB", b / 1024.0);
        if (b < 1 << 30) return String.format("%.1f MB", b / (1024.0 * 1024));
        return String.format("%.2f GB", b / (1024.0 * 1024 * 1024));
    }

    private static String fmtUnixSec(long sec) {
        if (sec <= 0) return "0000-00-00 00:00:00";
        return SDF.format(new Date(sec * 1000L));
    }

    private static String nvl(String s) { return (s != null && !s.isEmpty()) ? s : "—"; }

    private void styleBtn(JButton b, final Color bg, final Color fg) {
        b.setOpaque(true);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 12f));
        b.setMargin(new Insets(4, 12, 4, 12));
        b.setFocusPainted(false);
        b.setContentAreaFilled(true);
        b.setBorderPainted(false);
        // Override painting to guarantee color on any L&F (incl. Windows native)
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hover = (c instanceof AbstractButton ab) && ab.getModel().isRollover();
                g2.setColor(hover ? bg.brighter() : bg);
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 6, 6);
                g2.dispose();
                super.paint(g, c);
            }
        });
    }

    // ── Inner: Thumbnail preview ──────────────────────────────────────────────

    private static class ThumbnailPreview extends JPanel {
        private BufferedImage img;
        void setImage(BufferedImage i) { this.img = i; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(new Color(30, 30, 40));
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (img == null) {
                g2.setColor(new Color(90, 90, 110));
                g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                String t = "No preview";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(t, (getWidth()-fm.stringWidth(t))/2,
                        getHeight()/2+fm.getAscent()/2);
                return;
            }
            int iw=img.getWidth(), ih=img.getHeight();
            int pw=getWidth()-8,  ph=getHeight()-8;
            double sc = Math.min((double)pw/iw,(double)ph/ih);
            int dw=(int)(iw*sc), dh=(int)(ih*sc);
            int dx=(getWidth()-dw)/2, dy=(getHeight()-dh)/2;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(img,dx,dy,dw,dh,null);
        }
    }

    // ── Inner: Cell renderer with text wrap and section headers ───────────────

    private class WrappingRenderer extends DefaultTableCellRenderer {
        private static final Color SECTION_BG  = new Color(220, 225, 240);
        private static final Color SECTION_FG  = new Color(40,  60, 130);
        private static final Color ROW_ODD     = Color.WHITE;
        private static final Color ROW_EVEN    = new Color(246, 247, 251);
        private static final Color ATTR_FG     = new Color(55,  75, 145);

        @Override
        public Component getTableCellRendererComponent(JTable t, Object val,
                boolean sel, boolean focus, int row, int col) {

            String str = val != null ? val.toString() : "";
            boolean isSection = col == 0 && str.startsWith("§");

            if (isSection) {
                JLabel lbl = new JLabel(str.substring(1).toUpperCase());
                lbl.setFont(t.getFont().deriveFont(Font.BOLD, 10f));
                lbl.setBackground(SECTION_BG);
                lbl.setForeground(SECTION_FG);
                lbl.setOpaque(true);
                lbl.setBorder(new EmptyBorder(3, 6, 3, 4));
                return lbl;
            }

            // Value column — use JTextArea for wrapping
            if (col == 1) {
                JTextArea ta = new JTextArea(str);
                ta.setFont(t.getFont().deriveFont(11f));
                ta.setLineWrap(true);
                ta.setWrapStyleWord(false); // char-wrap: long paths/hashes wrap, don't clip
                ta.setOpaque(true);
                ta.setBorder(new EmptyBorder(2, 4, 2, 4));
                ta.setBackground(sel ? t.getSelectionBackground()
                        : row % 2 == 0 ? ROW_EVEN : ROW_ODD);
                ta.setForeground(sel ? t.getSelectionForeground() : Color.BLACK);
                return ta;
            }

            // Attribute column
            super.getTableCellRendererComponent(t, str, sel, focus, row, col);
            setFont(t.getFont().deriveFont(Font.BOLD, 11f));
            setBackground(sel ? t.getSelectionBackground()
                    : row % 2 == 0 ? ROW_EVEN : ROW_ODD);
            setForeground(sel ? t.getSelectionForeground() : ATTR_FG);
            setBorder(new EmptyBorder(2, 6, 2, 4));
            setToolTipText(str);
            return this;
        }
    }
}
