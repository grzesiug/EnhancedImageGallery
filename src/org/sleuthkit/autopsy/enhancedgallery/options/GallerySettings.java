package org.sleuthkit.autopsy.enhancedgallery.options;

import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * Persists Enhanced Gallery settings via NbPreferences.
 * Written to Autopsy user config dir automatically.
 */
public final class GallerySettings {

    private static final Preferences PREFS =
            NbPreferences.forModule(GallerySettings.class);

    // Keys
    private static final String KEY_FFMPEG  = "tool.ffmpeg.path";
    private static final String KEY_MAGICK  = "tool.magick.path";
    private static final String KEY_DCRAW   = "tool.dcraw.path";
    private static final String KEY_THUMB_THREADS  = "decoder.threads";
    private static final String KEY_DECODE_TIMEOUT  = "decoder.timeout.seconds";
    private static final String KEY_EXCLUDE_KNOWN    = "filter.exclude.known";
    private static final String KEY_SIDEBAR_DEBOUNCE = "sidebar.debounce.seconds";
    private static final String KEY_PROPAGATE_MD5   = "review.propagate.md5";
    private static final String KEY_MD5_MAX_FILES   = "review.propagate.md5.maxfiles";

    private GallerySettings() {}

    // ── FFmpeg ───────────────────────────────────────────────────────────────

    public static String getFfmpegPath() {
        return PREFS.get(KEY_FFMPEG, "").trim();
    }
    public static void setFfmpegPath(String path) {
        PREFS.put(KEY_FFMPEG, path == null ? "" : path.trim());
    }

    // ── ImageMagick (magick or convert) ──────────────────────────────────────

    public static String getMagickPath() {
        return PREFS.get(KEY_MAGICK, "").trim();
    }
    public static void setMagickPath(String path) {
        PREFS.put(KEY_MAGICK, path == null ? "" : path.trim());
    }

    // ── dcraw ────────────────────────────────────────────────────────────────

    public static String getDcrawPath() {
        return PREFS.get(KEY_DCRAW, "").trim();
    }
    public static void setDcrawPath(String path) {
        PREFS.put(KEY_DCRAW, path == null ? "" : path.trim());
    }

    // ── Decoder thread count ──────────────────────────────────────────────────

    public static int getDecoderThreads() {
        return PREFS.getInt(KEY_THUMB_THREADS,
                Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
    }
    public static void setDecoderThreads(int n) {
        PREFS.putInt(KEY_THUMB_THREADS, Math.max(1, Math.min(16, n)));
    }

    /** Timeout in seconds for a single thumbnail decode operation. Default: 60s. */
    public static int getDecodeTimeoutSeconds() {
        return PREFS.getInt(KEY_DECODE_TIMEOUT, 180);
    }
    public static void setDecodeTimeoutSeconds(int s) {
        PREFS.putInt(KEY_DECODE_TIMEOUT, Math.max(10, Math.min(300, s)));
    }

    /**
     * Exclude files marked as "Known" (NSRL hash match) from the gallery.
     * Known files are typically OS/application files with no forensic value. Default: true.
     */
    public static boolean isExcludeKnown() {
        return PREFS.getBoolean(KEY_EXCLUDE_KNOWN, true);
    }
    public static void setExcludeKnown(boolean v) {
        PREFS.putBoolean(KEY_EXCLUDE_KNOWN, v);
    }

    /**
     * Debounce delay in seconds before sidebar group panel refreshes after
     * tagging/seen operations. Prevents UI blocking when quickly tagging multiple
     * files. Default: 2 seconds.
     */
    public static int getSidebarDebounceSeconds() {
        return PREFS.getInt(KEY_SIDEBAR_DEBOUNCE, 2);
    }
    public static void setSidebarDebounceSeconds(int s) {
        PREFS.putInt(KEY_SIDEBAR_DEBOUNCE, Math.max(0, Math.min(30, s)));
    }

    /** Propagate seen/tag changes to all files with the same MD5 hash. Default: true. */
    public static boolean isPropagateMd5() {
        return PREFS.getBoolean(KEY_PROPAGATE_MD5, true);
    }
    public static void setPropagateMd5(boolean v) {
        PREFS.putBoolean(KEY_PROPAGATE_MD5, v);
    }

    /**
     * Max number of files with the same MD5 that will be propagated automatically.
     * If more files share the hash, a confirmation dialog is shown. Default: 50.
     */
    public static int getMd5MaxFiles() {
        return PREFS.getInt(KEY_MD5_MAX_FILES, 50);
    }
    public static void setMd5MaxFiles(int n) {
        PREFS.putInt(KEY_MD5_MAX_FILES, Math.max(1, Math.min(10000, n)));
    }
}
