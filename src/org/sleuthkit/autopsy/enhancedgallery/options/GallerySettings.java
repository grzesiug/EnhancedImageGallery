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
    private static final String KEY_RECENT_SEARCHES = "aisearch.recent";
    private static final String KEY_AISEARCH_TEXTMODE = "aisearch.textmode";

    /** How many recent AI search queries are remembered for autocomplete. */
    public static final int RECENT_SEARCHES_MAX = 5;
    // Separator that cannot appear in a normal query line (queries are single-line).
    private static final String RECENT_SEP = "\n";

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

    // ── AI search mode ────────────────────────────────────────────────────────

    /**
     * Which index the AI search dialog targets: {@code true} = Text + OCR
     * (AI Text Triage / BGE-M3), {@code false} = Images / visual (AI Image Triage /
     * CLIP). Remembers the analyst's last choice. Default: false (visual — back-compat).
     */
    public static boolean isAiSearchTextMode() {
        return PREFS.getBoolean(KEY_AISEARCH_TEXTMODE, false);
    }
    public static void setAiSearchTextMode(boolean textMode) {
        PREFS.putBoolean(KEY_AISEARCH_TEXTMODE, textMode);
    }

    // ── Recent searches (autocomplete history) ────────────────────────────────

    private static final String KEY_RECENT_FILE_SEARCHES  = "filesearch.recent";
    private static final String KEY_RECENT_GROUP_SEARCHES = "groupsearch.recent";
    /** History size for the file-name and group-name search boxes. */
    public static final int RECENT_FILTER_MAX = 10;

    /** Recent AI search queries, most-recent first (up to {@link #RECENT_SEARCHES_MAX}). */
    public static java.util.List<String> getRecentSearches() {
        return getRecentList(KEY_RECENT_SEARCHES);
    }

    /**
     * Records a query at the front of the recent-search history: de-duplicated
     * (case-insensitive), newest-first, capped at {@link #RECENT_SEARCHES_MAX}.
     */
    public static void addRecentSearch(String query) {
        addRecentList(KEY_RECENT_SEARCHES, query, RECENT_SEARCHES_MAX);
    }

    /** Recent file-name search queries, most-recent first (up to {@link #RECENT_FILTER_MAX}). */
    public static java.util.List<String> getRecentFileSearches() {
        return getRecentList(KEY_RECENT_FILE_SEARCHES);
    }
    public static void addRecentFileSearch(String query) {
        addRecentList(KEY_RECENT_FILE_SEARCHES, query, RECENT_FILTER_MAX);
    }

    /** Recent group-name filter queries, most-recent first (up to {@link #RECENT_FILTER_MAX}). */
    public static java.util.List<String> getRecentGroupSearches() {
        return getRecentList(KEY_RECENT_GROUP_SEARCHES);
    }
    public static void addRecentGroupSearch(String query) {
        addRecentList(KEY_RECENT_GROUP_SEARCHES, query, RECENT_FILTER_MAX);
    }

    // ── Shared history impl ────────────────────────────────────────────────────

    private static java.util.List<String> getRecentList(String key) {
        String raw = PREFS.get(key, "");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (raw.isBlank()) return out;
        for (String s : raw.split(RECENT_SEP)) {
            if (!s.isBlank()) out.add(s);
        }
        return out;
    }

    private static void addRecentList(String key, String query, int max) {
        if (query == null) return;
        String q = query.trim();
        if (q.isEmpty() || q.contains(RECENT_SEP)) return; // keep entries single-line
        java.util.List<String> list = getRecentList(key);
        list.removeIf(s -> s.equalsIgnoreCase(q));
        list.add(0, q);
        while (list.size() > max) list.remove(list.size() - 1);
        PREFS.put(key, String.join(RECENT_SEP, list));
    }
}
