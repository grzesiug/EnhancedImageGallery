package org.sleuthkit.autopsy.enhancedgallery.decoder;

import java.io.OutputStream;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings;

/**
 * Locates external tools needed for thumbnail decoding.
 *
 * Priority order:
 *   1. Saved path from GallerySettings (user configured)
 *   2. System PATH
 *   3. Common Windows installation directories
 */
public final class ToolFinder {

    private static final Logger logger = Logger.getLogger(ToolFinder.class.getName());

    private static volatile String ffmpegPath;
    private static volatile String magickPath;
    private static volatile String dcrawPath;
    private static volatile boolean initialized = false;

    private ToolFinder() {}

    // ── Public API ────────────────────────────────────────────────────────────

    public static synchronized void init() {
        if (initialized) return;
        reload();
    }

    /** Re-reads settings and re-detects tools. Called after settings dialog Save. */
    public static synchronized void reload() {
        ffmpegPath = resolveFfmpeg(GallerySettings.getFfmpegPath());
        magickPath = resolveMagick(GallerySettings.getMagickPath());
        dcrawPath  = resolveOrDetect(GallerySettings.getDcrawPath(), "dcraw",
                DCRAW_PATHS, new String[]{""});
        initialized = true;
        logger.log(Level.INFO, statusSummary());
    }

    public static synchronized void clearCache() {
        initialized = false;
        ffmpegPath = null;
        magickPath = null;
        dcrawPath  = null;
    }

    public static String ffmpeg()  { if (!initialized) init(); return ffmpegPath; }
    public static String magick()  { if (!initialized) init(); return magickPath; }
    public static String dcraw()   { if (!initialized) init(); return dcrawPath; }

    public static boolean hasFfmpeg() { return ffmpeg() != null; }
    public static boolean hasMagick() { return magick() != null; }
    public static boolean hasDcraw()  { return dcraw()  != null; }

    /** Auto-detect FFmpeg without saving. Used by settings dialog. */
    public static String detectFfmpeg() {
        return resolveFfmpeg("");
    }
    public static String detectMagick() { return resolveMagick(""); }
    public static String detectDcraw()  {
        return resolveOrDetect("", "dcraw", DCRAW_PATHS, new String[]{""});
    }

    public static String statusSummary() {
        return "ToolFinder:"
                + " ffmpeg=" + (hasFfmpeg() ? ffmpegPath : "not found")
                + " | magick=" + (hasMagick() ? magickPath : "not found")
                + " | dcraw=" + (hasDcraw() ? dcrawPath : "not found");
    }

    // ── Candidate paths ───────────────────────────────────────────────────────

    private static final String[] FFMPEG_PATHS = {
        "C:\\ffmpeg\\bin\\ffmpeg.exe",
        "C:\\ffmpeg\\ffmpeg.exe",
        "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
        "C:\\Program Files (x86)\\ffmpeg\\bin\\ffmpeg.exe",
        "C:\\tools\\ffmpeg\\bin\\ffmpeg.exe",
        System.getProperty("user.home", "") + "\\scoop\\shims\\ffmpeg.exe",
        "C:\\ProgramData\\chocolatey\\bin\\ffmpeg.exe",
    };

    private static final String[] MAGICK7_PATHS = {
        // Standard installer locations (Program Files)
        "C:\\Program Files\\ImageMagick-7.1.2-Q16-HDRI\\magick.exe",
        "C:\\Program Files\\ImageMagick-7.1.2-Q16\\magick.exe",
        "C:\\Program Files\\ImageMagick-7.1.1-Q16-HDRI\\magick.exe",
        "C:\\Program Files\\ImageMagick-7.1.0-Q16-HDRI\\magick.exe",
        "C:\\Program Files\\ImageMagick-7.0.11-Q16-HDRI\\magick.exe",
        // Portable installations (root of C:\ or common portable dirs)
        "C:\\ImageMagick-7.1.2-26-portable-Q16-x64\\magick.exe",
        "C:\\ImageMagick-7.1.2-portable-Q16-x64\\magick.exe",
        "C:\\ImageMagick\\magick.exe",
        "C:\\tools\\ImageMagick\\magick.exe",
        System.getProperty("user.home", "") + "\\scoop\\shims\\magick.exe",
        "C:\\ProgramData\\chocolatey\\bin\\magick.exe",
    };

    private static final String[] MAGICK6_PATHS = {
        "C:\\Program Files\\ImageMagick-6.9.12-Q16\\convert.exe",
        "C:\\Program Files\\ImageMagick-6.9.11-Q16\\convert.exe",
        "C:\\Program Files (x86)\\ImageMagick-6.9.12-Q16\\convert.exe",
    };

    private static final String[] DCRAW_PATHS = {
        "C:\\Program Files\\dcraw\\dcraw.exe",
        "C:\\dcraw\\dcraw.exe",
        "C:\\tools\\dcraw.exe",
        System.getProperty("user.home", "") + "\\scoop\\shims\\dcraw.exe",
    };

    // ── Resolution logic ──────────────────────────────────────────────────────

    /**
     * Returns:
     * 1. savedPath if non-empty and executable
     * 2. commandName if found on PATH
     * 3. First existing candidate path
     * 4. null
     */
    private static String resolveOrDetect(String savedPath, String commandName,
                                           String[] candidates, String[] testArgs) {
        // 1. User-configured path
        if (!savedPath.isEmpty()) {
            if (Files.exists(Path.of(savedPath))) {
                logger.log(Level.INFO, "Using saved path for {0}: {1}",
                        new Object[]{commandName, savedPath});
                return savedPath;
            } else {
                logger.log(Level.WARNING,
                        "Saved path for {0} doesn''t exist: {1}", new Object[]{commandName, savedPath});
            }
        }

        // 2. Try PATH
        for (String arg : testArgs) {
            try {
                java.util.List<String> cmd = new java.util.ArrayList<>();
                cmd.add(commandName);
                if (!arg.isEmpty()) cmd.add(arg);
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                if (p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS))
                    return commandName; // found on PATH
            } catch (Exception ignored) {}
        }

        // 3. Candidate paths
        for (String c : candidates) {
            if (c != null && !c.isEmpty() && Files.exists(Path.of(c))) {
                logger.log(Level.INFO, "Found {0} at: {1}", new Object[]{commandName, c});
                return c;
            }
        }

        // 4. Scan Program Files (max depth 2) — only folder names containing the tool name
        for (String root : new String[]{"C:\\Program Files", "C:\\Program Files (x86)"}) {
            Path rootPath = Path.of(root);
            if (!Files.isDirectory(rootPath)) continue;
            try (var topLevel = Files.list(rootPath)) {
                java.util.Optional<Path> found = topLevel
                    .filter(dir -> Files.isDirectory(dir) &&
                            dir.getFileName().toString().toLowerCase().contains(commandName))
                    .flatMap(dir -> {
                        try (var sub = Files.walk(dir, 2)) {
                            return sub.filter(p -> {
                                String fn = p.getFileName() != null
                                        ? p.getFileName().toString().toLowerCase() : "";
                                return fn.equals(commandName + ".exe");
                            }).findFirst().stream();
                        } catch (Exception e) { return java.util.stream.Stream.empty(); }
                    })
                    .findFirst();
                if (found.isPresent()) {
                    logger.log(Level.INFO, "Found {0} via scan: {1}",
                            new Object[]{commandName, found.get()});
                    return found.get().toString();
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    /** Scans C:\ root for portable ImageMagick-* or FFmpeg-* installations. */
    private static String scanRootFor(String prefix, String exeName) {
        for (String root : new String[]{"C:\\", "D:\\"}) {
            try {
                java.io.File[] dirs = new java.io.File(root).listFiles(
                        f -> f.isDirectory() && f.getName().toLowerCase().startsWith(prefix));
                if (dirs == null) continue;
                for (java.io.File dir : dirs) {
                    // Check dir itself and common subdirs (bin/)
                    for (String sub : new String[]{"", "bin"}) {
                        java.io.File exe = sub.isEmpty()
                                ? new java.io.File(dir, exeName)
                                : new java.io.File(new java.io.File(dir, sub), exeName);
                        if (exe.exists()) return exe.getAbsolutePath();
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Scans WinGet packages directory for FFmpeg installations. */
    private static String scanWinGetForFfmpeg() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null) return null;
        java.io.File pkgs = new java.io.File(localAppData,
                "Microsoft\\WinGet\\Packages");
        if (!pkgs.isDirectory()) return null;
        try {
            java.io.File[] candidates = pkgs.listFiles(
                    f -> f.isDirectory() && f.getName().toLowerCase().contains("ffmpeg"));
            if (candidates == null) return null;
            for (java.io.File pkg : candidates) {
                // WinGet structure: <PackageDir>/<version>/bin/ffmpeg.exe
                java.io.File[] versions = pkg.listFiles(java.io.File::isDirectory);
                if (versions == null) continue;
                for (java.io.File ver : versions) {
                    java.io.File exe = new java.io.File(ver, "bin\\ffmpeg.exe");
                    if (exe.exists()) return exe.getAbsolutePath();
                    exe = new java.io.File(ver, "ffmpeg.exe");
                    if (exe.exists()) return exe.getAbsolutePath();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Wraps resolveOrDetect for FFmpeg, adding root-scan and WinGet fallbacks. */
    private static String resolveFfmpeg(String savedPath) {
        String found = resolveOrDetect(savedPath, "ffmpeg", FFMPEG_PATHS,
                new String[]{"--version", "-version"});
        if (found != null) return found;
        // WinGet packages
        found = scanWinGetForFfmpeg();
        if (found != null) { logger.log(Level.INFO, "Found ffmpeg via WinGet: {0}", found); return found; }
        // Portable builds at C:\ or D:\ root (e.g. ffmpeg-2026-xx-xx-full_build)
        found = scanRootFor("ffmpeg", "ffmpeg.exe");
        if (found != null) { logger.log(Level.INFO, "Found ffmpeg via root scan: {0}", found); return found; }
        return null;
    }

    private static String resolveMagick(String savedPath) {
        // Try 'magick' (IM7) first, then 'convert' (IM6)
        if (!savedPath.isEmpty()) {
            if (Files.exists(Path.of(savedPath))) return savedPath;
        }
        // PATH check: magick
        for (String cmd : new String[]{"magick", "convert"}) {
            try {
                Process p = new ProcessBuilder(cmd, "-version")
                        .redirectErrorStream(true).start();
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                if (p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) return cmd;
            } catch (Exception ignored) {}
        }
        // Candidate paths IM7
        for (String c : MAGICK7_PATHS)
            if (c != null && !c.isEmpty() && Files.exists(Path.of(c))) return c;
        // Candidate paths IM6
        for (String c : MAGICK6_PATHS)
            if (c != null && !c.isEmpty() && Files.exists(Path.of(c))) return c;
        // Scan C:\ / D:\ root for portable ImageMagick-* folders
        String scanned = scanRootFor("imagemagick", "magick.exe");
        if (scanned != null) return scanned;
        scanned = scanRootFor("imagemagick", "convert.exe");
        if (scanned != null) return scanned;
        // Last resort: PATH scan
        return resolveOrDetect("", "magick", new String[0], new String[]{"-version"});
    }
}
