package org.sleuthkit.autopsy.enhancedgallery.options;

import java.awt.*;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

/**
 * Modal settings dialog — external tool paths + decoder options.
 * All external process calls run on background threads (never blocks EDT).
 */
public class ToolSettingsDialog extends JDialog {

    private static final Logger logger =
            Logger.getLogger(ToolSettingsDialog.class.getName());

    // Tool path fields
    private final JTextField ffmpegField = new JTextField();
    private final JTextField magickField = new JTextField();
    private final JTextField dcrawField  = new JTextField();

    // Status labels
    private final JLabel ffmpegStatus = new JLabel("—");
    private final JLabel magickStatus = new JLabel("—");
    private final JLabel dcrawStatus  = new JLabel("—");

    // Spinners & checkboxes
    private final JSpinner  threadsSpinner;
    private final JSpinner  timeoutSpinner;
    private final JSpinner  md5MaxSpinner;
    private final JSpinner  debounceSpinner;
    private final JCheckBox md5PropagateCheck;
    private final JCheckBox excludeKnownCheck;

    public ToolSettingsDialog(Window owner) {
        super(owner, "Enhanced Gallery — Settings", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        int threads = Math.max(1, Math.min(16, GallerySettings.getDecoderThreads()));
        threadsSpinner = new JSpinner(new SpinnerNumberModel(threads, 1, 16, 1));
        int timeout = Math.max(10, Math.min(300, GallerySettings.getDecodeTimeoutSeconds()));
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(timeout, 10, 300, 10));
        int md5Max = Math.max(1, Math.min(10000, GallerySettings.getMd5MaxFiles()));
        md5MaxSpinner = new JSpinner(new SpinnerNumberModel(md5Max, 1, 10000, 10));
        int debounce = Math.max(0, Math.min(30, GallerySettings.getSidebarDebounceSeconds()));
        debounceSpinner = new JSpinner(new SpinnerNumberModel(debounce, 0, 30, 1));
        md5PropagateCheck = new JCheckBox(
                "Propagate Seen/Tag to all files with same MD5 hash",
                GallerySettings.isPropagateMd5());
        excludeKnownCheck = new JCheckBox(
                "Exclude NSRL-known files (hash = Known) from gallery",
                GallerySettings.isExcludeKnown());

        // Load saved values
        ffmpegField.setText(GallerySettings.getFfmpegPath());
        magickField.setText(GallerySettings.getMagickPath());
        dcrawField.setText(GallerySettings.getDcrawPath());

        initUI();
        // Set window icon
        try {
            java.net.URL iconUrl = getClass().getResource(
                    "/org/sleuthkit/autopsy/enhancedgallery/resources/gallery.png");
            if (iconUrl != null)
                setIconImage(javax.imageio.ImageIO.read(iconUrl));
        } catch (Exception ignored) {}

        pack();
        setMinimumSize(new Dimension(720, 700));
        setPreferredSize(new Dimension(760, 740));
        setLocationRelativeTo(owner);

        // Validate saved paths in background (doesn't block EDT)
        new Thread(this::validateAll, "EG-SettingsCheck").start();
    }

    private void initUI() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(10, 12, 6, 12));

        // ── Tool rows ──────────────────────────────────────────────────────
        content.add(toolRow("FFmpeg",
                "video thumbnails (MP4, AVI, MOV, MKV…)",
                "ffmpeg.exe — e.g. C:\\ffmpeg\\bin\\ffmpeg.exe",
                ffmpegField, ffmpegStatus,
                () -> browse(ffmpegField, "ffmpeg.exe"),
                () -> testInBackground(ffmpegField, ffmpegStatus)));

        content.add(Box.createVerticalStrut(8));

        content.add(toolRow("ImageMagick",
                "HEIC, WebP, AVIF, SVG, RAW and more",
                "magick.exe (v7) or convert.exe (v6) — e.g. C:\\Program Files\\ImageMagick-7.x\\magick.exe",
                magickField, magickStatus,
                () -> browse(magickField, "magick.exe"),
                () -> testInBackground(magickField, magickStatus)));

        content.add(Box.createVerticalStrut(8));

        content.add(toolRow("dcraw",
                "RAW camera files (CR2, NEF, ARW, DNG…)",
                "dcraw.exe — e.g. C:\\tools\\dcraw.exe",
                dcrawField, dcrawStatus,
                () -> browse(dcrawField, "dcraw.exe"),
                () -> testInBackground(dcrawField, dcrawStatus)));

        content.add(Box.createVerticalStrut(8));

        // ── Threads ────────────────────────────────────────────────────────
        JPanel perfRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        perfRow.setBorder(titled("Decoder performance"));

        int cpu = Runtime.getRuntime().availableProcessors();
        JPanel debounceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        debounceRow.setBorder(titled("Group panel refresh delay  (after tag / mark seen)"));
        debounceRow.add(new JLabel("Refresh delay:"));
        debounceRow.add(debounceSpinner);
        JLabel debounceHint = new JLabel("seconds  (0 = immediate,  recommended: 2–5 s)");
        debounceHint.setForeground(Color.GRAY);
        debounceHint.setFont(debounceHint.getFont().deriveFont(11f));
        debounceRow.add(debounceHint);
        content.add(debounceRow);
        content.add(Box.createVerticalStrut(8));

        perfRow.add(new JLabel("Parallel threads:"));
        perfRow.add(threadsSpinner);
        JLabel cpuHint = new JLabel("(CPU cores: " + cpu + ")");
        cpuHint.setForeground(Color.GRAY);
        cpuHint.setFont(cpuHint.getFont().deriveFont(11f));
        perfRow.add(cpuHint);

        perfRow.add(Box.createHorizontalStrut(20));
        perfRow.add(new JLabel("Timeout per file:"));
        perfRow.add(timeoutSpinner);
        JLabel secLabel = new JLabel("seconds  (10–300, default 60)");
        secLabel.setForeground(Color.GRAY);
        secLabel.setFont(secLabel.getFont().deriveFont(11f));
        perfRow.add(secLabel);

        content.add(perfRow);

        content.add(Box.createVerticalStrut(8));

        // ── NSRL / Known files filter ──────────────────────────────────────
        JPanel filterPanel = new JPanel(new BorderLayout(8, 2));
        filterPanel.setBorder(titled("File filtering  (applied at gallery load)"));
        filterPanel.setOpaque(false);

        excludeKnownCheck.setFont(excludeKnownCheck.getFont().deriveFont(12f));
        excludeKnownCheck.setOpaque(false);
        JLabel excludeDesc = new JLabel(
                "<html><font color='gray' size='2'>" +
                "Skips files verified by NSRL hash sets as known-safe (Windows OS, standard apps). " +
                "Recommended — can eliminate tens of thousands of irrelevant files. " +
                "Takes effect on next gallery open." +
                "</font></html>");
        excludeDesc.setBorder(new EmptyBorder(0, 4, 0, 0));

        filterPanel.add(excludeKnownCheck, BorderLayout.NORTH);
        filterPanel.add(excludeDesc,        BorderLayout.CENTER);
        content.add(filterPanel);

        content.add(Box.createVerticalStrut(8));

        // ── MD5 propagation ────────────────────────────────────────────────
        JPanel md5Panel = new JPanel();
        md5Panel.setLayout(new BoxLayout(md5Panel, BoxLayout.Y_AXIS));
        md5Panel.setBorder(titled("MD5 duplicate propagation"));
        md5Panel.setOpaque(false);

        md5PropagateCheck.setFont(md5PropagateCheck.getFont().deriveFont(12f));
        md5PropagateCheck.setOpaque(false);
        JLabel md5Desc = new JLabel(
                "<html><font color='gray' size='2'>When enabled: marking a file as Seen or tagging it will automatically apply<br>"
                + "the same change to all other files in the case with the identical MD5 hash.<br>"
                + "Useful when the same image exists in multiple locations on disk.</font></html>");

        JPanel md5MaxRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        md5MaxRow.setOpaque(false);
        md5MaxRow.add(new JLabel("Ask confirmation if more than"));
        md5MaxRow.add(md5MaxSpinner);
        md5MaxRow.add(new JLabel("copies share the same MD5"));

        md5Panel.add(md5PropagateCheck);
        md5Panel.add(md5Desc);
        md5Panel.add(Box.createVerticalStrut(4));
        md5Panel.add(md5MaxRow);
        content.add(md5Panel);

        content.add(Box.createVerticalStrut(8));

        // ── Thumbnail cache ────────────────────────────────────────────────
        JPanel cachePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        cachePanel.setBorder(titled("Thumbnail cache"));
        String stats = org.sleuthkit.autopsy.enhancedgallery.decoder.ThumbnailCache
                .getInstance().stats();
        JLabel cacheStats = new JLabel("Cache: " + stats);
        cacheStats.setFont(cacheStats.getFont().deriveFont(12f));
        JButton clearBtn = new JButton("Clear cache");
        styleBtn(clearBtn);
        clearBtn.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Delete all cached thumbnails?\nThey will be regenerated on next view.",
                    "Clear cache", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                org.sleuthkit.autopsy.enhancedgallery.decoder.ThumbnailCache
                        .getInstance().clear();
                cacheStats.setText("Cache: cleared");
            }
        });
        cachePanel.add(cacheStats);
        cachePanel.add(clearBtn);
        content.add(cachePanel);

        content.add(Box.createVerticalStrut(8));

        // ── About ──────────────────────────────────────────────────────────
        JPanel aboutPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        aboutPanel.setBorder(titled("About Enhanced Image Gallery"));
        JLabel aboutLabel = new JLabel(
            "<html><b>Enhanced Image Gallery</b> — Autopsy forensic module<br>"
            + "Extends Autopsy's built-in gallery with support for HEIC, WebP, AVIF, SVG, RAW,<br>"
            + "video thumbnails, EXIF metadata, GPS mapping, and bulk review workflow.<br><br>"
            + "<b>Author:</b> Grzegorz Ginalski &nbsp; "
            + "<a href='https://github.com/grzesiug'>github.com/grzesiug</a></html>");
        aboutLabel.setFont(aboutLabel.getFont().deriveFont(11f));
        aboutPanel.add(aboutLabel);
        content.add(aboutPanel);

        content.add(Box.createVerticalStrut(8));

        // ── Hint ───────────────────────────────────────────────────────────
        JLabel hint = new JLabel(
            "<html><font color='gray' size='2'>Paths saved in Autopsy user settings. " +
            "Leave empty to auto-detect from system PATH. " +
            "Click <b>Auto-detect</b> to search common install locations.</font></html>");
        content.add(hint);

        // ── Buttons ────────────────────────────────────────────────────────
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        btns.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));

        JButton autoBtn   = new JButton("Auto-detect all");
        JButton cancelBtn = new JButton("Cancel");
        JButton saveBtn   = new JButton("Save");

        styleBtn(autoBtn);   styleBtn(cancelBtn);
        saveBtn.setFont(saveBtn.getFont().deriveFont(Font.BOLD, 12f));

        autoBtn.addActionListener(e -> autoDetectAll());
        cancelBtn.addActionListener(e -> dispose());
        saveBtn.addActionListener(e -> save());
        getRootPane().setDefaultButton(saveBtn);

        btns.add(autoBtn);
        btns.add(Box.createHorizontalStrut(30));
        btns.add(cancelBtn);
        btns.add(saveBtn);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        add(btns,    BorderLayout.SOUTH);
    }

    // ── Tool row builder ──────────────────────────────────────────────────────

    private JPanel toolRow(String name, String purpose, String placeholder,
                           JTextField field, JLabel status,
                           Runnable browseAction, Runnable testAction) {

        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.setFont(field.getFont().deriveFont(12f));
        // Fix height — prevent BorderLayout from stretching the text field
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        field.setPreferredSize(new Dimension(400, 28));

        JButton browseBtn = new JButton("Browse…");
        JButton testBtn   = new JButton("Test");
        styleBtn(browseBtn); styleBtn(testBtn);
        browseBtn.addActionListener(e -> browseAction.run());
        testBtn.addActionListener(e -> testAction.run());

        status.setFont(status.getFont().deriveFont(11f));
        status.setForeground(Color.GRAY);

        // Field + Browse in a row
        JPanel fieldRow = new JPanel(new BorderLayout(4, 0));
        fieldRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        fieldRow.add(field,     BorderLayout.CENTER);
        fieldRow.add(browseBtn, BorderLayout.EAST);

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        statusRow.add(testBtn);
        statusRow.add(status);

        // Use BoxLayout so the panel respects the field's max height
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(titled(name + " — " + purpose));
        panel.add(fieldRow);
        panel.add(statusRow);
        return panel;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void browse(JTextField field, String name) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select " + name);
        String cur = field.getText().trim();
        if (!cur.isEmpty()) {
            File f = new File(cur);
            fc.setCurrentDirectory(f.isFile() ? f.getParentFile() : f);
        }
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Executables (*.exe)", "exe"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            field.setText(fc.getSelectedFile().getAbsolutePath());
    }

    private void testInBackground(JTextField field, JLabel status) {
        String path = field.getText().trim();
        setStatus(status, "Testing…", Color.GRAY);
        new Thread(() -> {
            boolean ok = testPath(path);
            SwingUtilities.invokeLater(() ->
                setStatus(status,
                        ok ? "✓ Working" : (path.isEmpty() ? "empty" : "✗ Not working"),
                        ok ? new Color(0, 140, 0) : (path.isEmpty() ? Color.GRAY : Color.RED)));
        }, "EG-ToolTest").start();
    }

    private boolean testPath(String path) {
        if (path == null || path.isEmpty()) return false;
        try {
            Path p = Path.of(path);
            if (!Files.exists(p)) {
                // Try as command name on PATH
                Process proc = new ProcessBuilder(path, "-version")
                        .redirectErrorStream(true).start();
                proc.getInputStream().transferTo(OutputStream.nullOutputStream());
                return proc.waitFor(4, java.util.concurrent.TimeUnit.SECONDS);
            }
            return Files.isExecutable(p) || path.toLowerCase().endsWith(".exe");
        } catch (Exception ex) {
            logger.log(Level.FINE, "Test failed for: " + path, ex);
            return false;
        }
    }

    private void validateAll() {
        validateOne(ffmpegField, ffmpegStatus);
        validateOne(magickField, magickStatus);
        validateOne(dcrawField,  dcrawStatus);
    }

    private void validateOne(JTextField field, JLabel status) {
        String path = field.getText().trim();
        if (path.isEmpty()) {
            SwingUtilities.invokeLater(() ->
                    setStatus(status, "(empty — will auto-detect)", Color.GRAY));
            return;
        }
        boolean ok = testPath(path);
        SwingUtilities.invokeLater(() ->
            setStatus(status,
                    ok ? "✓ Found" : "✗ Not found",
                    ok ? new Color(0, 140, 0) : Color.RED));
    }

    private void autoDetectAll() {
        setStatus(ffmpegStatus, "Searching…", Color.GRAY);
        setStatus(magickStatus, "Searching…", Color.GRAY);
        setStatus(dcrawStatus,  "Searching…", Color.GRAY);

        new Thread(() -> {
            org.sleuthkit.autopsy.enhancedgallery.decoder.ToolFinder.clearCache();
            String ff = org.sleuthkit.autopsy.enhancedgallery.decoder.ToolFinder.detectFfmpeg();
            String mg = org.sleuthkit.autopsy.enhancedgallery.decoder.ToolFinder.detectMagick();
            String dc = org.sleuthkit.autopsy.enhancedgallery.decoder.ToolFinder.detectDcraw();
            SwingUtilities.invokeLater(() -> {
                if (ff != null) { ffmpegField.setText(ff); setStatus(ffmpegStatus, "✓ Found", new Color(0,140,0)); }
                else setStatus(ffmpegStatus, "✗ Not found — install FFmpeg", Color.RED);
                if (mg != null) { magickField.setText(mg); setStatus(magickStatus, "✓ Found", new Color(0,140,0)); }
                else setStatus(magickStatus, "✗ Not found — install ImageMagick", Color.RED);
                if (dc != null) { dcrawField.setText(dc); setStatus(dcrawStatus, "✓ Found", new Color(0,140,0)); }
                else setStatus(dcrawStatus, "not found (optional)", Color.GRAY);
            });
        }, "EG-AutoDetect").start();
    }

    private void save() {
        GallerySettings.setFfmpegPath(ffmpegField.getText().trim());
        GallerySettings.setMagickPath(magickField.getText().trim());
        GallerySettings.setDcrawPath(dcrawField.getText().trim());
        GallerySettings.setDecoderThreads((int) threadsSpinner.getValue());
        GallerySettings.setDecodeTimeoutSeconds((int) timeoutSpinner.getValue());
        GallerySettings.setSidebarDebounceSeconds((int) debounceSpinner.getValue());
        GallerySettings.setExcludeKnown(excludeKnownCheck.isSelected());
        GallerySettings.setPropagateMd5(md5PropagateCheck.isSelected());
        GallerySettings.setMd5MaxFiles((int) md5MaxSpinner.getValue());
        org.sleuthkit.autopsy.enhancedgallery.decoder.ToolFinder.reload();

        // Retry thumbnails that previously failed (e.g. HEIC before ImageMagick was set)
        org.sleuthkit.autopsy.enhancedgallery.ui.EnhancedGalleryTopComponent tc =
                org.sleuthkit.autopsy.enhancedgallery.ui.EnhancedGalleryTopComponent.findInstance();
        if (tc != null) tc.retryFailedThumbnails();

        dispose();
        JOptionPane.showMessageDialog(getOwner(),
                "Settings saved.\nThumbnails that previously failed will be retried now.",
                "Settings saved", JOptionPane.INFORMATION_MESSAGE);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setStatus(JLabel label, String text, Color color) {
        label.setText(text);
        label.setForeground(color);
    }

    private void styleBtn(JButton b) {
        b.setFont(b.getFont().deriveFont(12f));
        b.setMargin(new Insets(2, 8, 2, 8));
        b.setFocusPainted(false);
    }

    private TitledBorder titled(String text) {
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), text);
        tb.setTitleFont(tb.getTitleFont().deriveFont(Font.BOLD, 11f));
        return tb;
    }
}
