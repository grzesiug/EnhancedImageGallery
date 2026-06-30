package org.sleuthkit.autopsy.enhancedgallery.options;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Opens an AbstractFile in an external viewer using Autopsy's own
 * ExternalViewerAction — exactly as Autopsy's "Open in External Viewer" menu.
 *
 * Priority:
 *   1. ExternalViewerAction from org.sleuthkit.autopsy.directorytree
 *      → handles temp export, original filename, External Viewer rules,
 *        exactly like Autopsy's built-in menu item.
 *   2. Windows default (Desktop.open / ShellExecute) as fallback.
 */
public class ExternalViewerService {

    private static final Logger logger =
            Logger.getLogger(ExternalViewerService.class.getName());

    private static final String TEMP_SUBDIR = "autopsy_enhanced_gallery";

    // ── Public entry point ────────────────────────────────────────────────────

    public static void openDefault(AbstractFile file) {
        Thread t = new Thread(() -> {
            try {
                // Strategy 1: Autopsy's ExternalViewerAction (best)
                if (tryExternalViewerAction(file)) return;

                // Strategy 2: Windows default
                Path tempFile = extractToTemp(file);
                openWithWindowsDefault(tempFile);

            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Failed to open file: " + file.getName(), ex);
                showError("Cannot open file: " + file.getName()
                        + "\n" + ex.getMessage());
            }
        }, "EG-OpenFile");
        t.setDaemon(true);
        t.start();
    }

    // ── Strategy 1: ExternalViewerAction ─────────────────────────────────────

    /**
     * Invokes Autopsy's ExternalViewerAction which:
     * - exports AbstractFile to the case temp dir
     * - keeps original filename
     * - handles disk image files
     * - applies External Viewer rules (Tools → Options → External Viewer)
     * - opens exactly like Autopsy's "Open in External Viewer" menu item
     */
    private static boolean tryExternalViewerAction(AbstractFile file) {
        try {
            ClassLoader cl = org.sleuthkit.autopsy.casemodule.Case.class.getClassLoader();
            Class<?> actionClass = cl.loadClass(
                    "org.sleuthkit.autopsy.directorytree.ExternalViewerAction");

            // Constructor: ExternalViewerAction(String displayName, Node node)
            // Create a minimal Node with the AbstractFile in its Lookup
            org.openide.nodes.AbstractNode node = new org.openide.nodes.AbstractNode(
                    org.openide.nodes.Children.LEAF,
                    org.openide.util.lookup.Lookups.singleton(file));

            java.lang.reflect.Constructor<?> ctor = null;
            for (java.lang.reflect.Constructor<?> c : actionClass.getConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 2 && params[0] == String.class) {
                    ctor = c;
                    break;
                }
            }

            if (ctor == null) {
                logger.log(Level.WARNING, "EG: ExternalViewerAction has no 2-arg constructor");
                return false;
            }

            // new ExternalViewerAction("Open", node)
            Object action = ctor.newInstance("Open in External Viewer", node);

            // Fire: action.actionPerformed(new ActionEvent(...))
            ActionEvent event = new ActionEvent(
                    ExternalViewerService.class,
                    ActionEvent.ACTION_PERFORMED,
                    "open");

            actionClass.getMethod("actionPerformed", ActionEvent.class)
                    .invoke(action, event);

            logger.log(Level.INFO, "EG: Opened via ExternalViewerAction: {0}",
                    file.getName());
            return true;

        } catch (ClassNotFoundException ex) {
            logger.log(Level.WARNING,
                    "EG: ExternalViewerAction not found — falling back to Windows default");
            return false;
        } catch (Exception ex) {
            logger.log(Level.WARNING,
                    "EG: ExternalViewerAction failed for " + file.getName()
                    + ": " + ex.getMessage(), ex);
            return false;
        }
    }

    // ── Strategy 2: Windows default ───────────────────────────────────────────

    private static void openWithWindowsDefault(Path tempFile) throws IOException {
        if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            logger.log(Level.INFO, "EG: Opening with Windows default: {0}",
                    tempFile.getFileName());
            Desktop.getDesktop().open(tempFile.toFile());
        } else {
            logger.log(Level.INFO, "EG: Using ShellExecute for: {0}",
                    tempFile.getFileName());
            new ProcessBuilder("cmd", "/c", "start", "",
                    tempFile.toAbsolutePath().toString()).start();
        }
    }

    // ── File extraction (fallback only) ───────────────────────────────────────

    static Path extractToTemp(AbstractFile file) throws IOException, TskCoreException {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), TEMP_SUBDIR);
        Files.createDirectories(tempDir);
        String safeName = file.getId() + "_" + sanitize(file.getName());
        Path outPath    = tempDir.resolve(safeName);
        if (Files.exists(outPath) && Files.size(outPath) == file.getSize()) return outPath;

        byte[] buf = new byte[65536];
        long offset = 0, remaining = file.getSize();
        try (var out = Files.newOutputStream(outPath)) {
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int read   = file.read(buf, offset, toRead);
                if (read <= 0) break;
                out.write(buf, 0, read);
                offset    += read;
                remaining -= read;
            }
        }
        outPath.toFile().deleteOnExit();
        return outPath;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static void showError(String message) {
        javax.swing.SwingUtilities.invokeLater(() ->
            javax.swing.JOptionPane.showMessageDialog(null, message,
                    "Open Error", javax.swing.JOptionPane.ERROR_MESSAGE));
    }

    // ── Kept for API compatibility ─────────────────────────────────────────────

    public static class ViewerEntry {
        private final String displayName, exePath;
        private final java.util.List<String> extraArgs, mimeTypes, extensions;
        private final boolean isDefault;

        public ViewerEntry(String displayName, String exePath,
                           java.util.List<String> extraArgs,
                           java.util.List<String> mimeTypes,
                           java.util.List<String> extensions,
                           boolean isDefault) {
            this.displayName = displayName; this.exePath = exePath;
            this.extraArgs   = extraArgs;   this.mimeTypes = mimeTypes;
            this.extensions  = extensions;  this.isDefault = isDefault;
        }
        public String getDisplayName()                { return displayName; }
        public String getExePath()                    { return exePath; }
        public java.util.List<String> getExtraArgs()  { return extraArgs; }
        public java.util.List<String> getMimeTypes()  { return mimeTypes; }
        public java.util.List<String> getExtensions() { return extensions; }
        public boolean isDefault()                    { return isDefault; }
    }
}
