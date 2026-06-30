package org.sleuthkit.autopsy.enhancedgallery.options;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.enhancedgallery.options.ExternalViewerService.ViewerEntry;

/**
 * Persists the list of configured external viewers to NbPreferences
 * (written to %APPDATA%\Autopsy\... automatically by NetBeans).
 *
 * Storage format (all under key prefix "viewer.N."):
 *   viewer.count          = 4
 *   viewer.0.name         = IrfanView
 *   viewer.0.exe          = C:\Program Files\IrfanView\i_view64.exe
 *   viewer.0.args         =                       (empty = none)
 *   viewer.0.mimes        = image/jpeg,image/png,image/webp,image/heic
 *   viewer.0.exts         = jpg,jpeg,png,webp,heic,bmp,tif,tiff
 *   viewer.0.default      = true
 *   viewer.1.name         = RawTherapee
 *   viewer.1.exe          = C:\Program Files\RawTherapee\rawtherapee.exe
 *   viewer.1.mimes        = image/x-canon-cr2,image/x-nikon-nef
 *   viewer.1.exts         = cr2,nef,arw,dng,orf,rw2
 *   viewer.1.default      = true
 *   ...
 */
public class ExternalViewerSettings {

    private static ExternalViewerSettings instance;
    private final Preferences prefs;
    private static final String COUNT_KEY   = "viewer.count";
    private static final String PREFIX      = "viewer.";

    private ExternalViewerSettings() {
        prefs = NbPreferences.forModule(ExternalViewerSettings.class);
    }

    public static synchronized ExternalViewerSettings getInstance() {
        if (instance == null) instance = new ExternalViewerSettings();
        return instance;
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Finds the first viewer marked as default that handles this MIME type
     * or extension.  Returns null if none configured → caller falls back to
     * Desktop.open().
     */
    public ViewerEntry findViewer(String mimeType, String extension) {
        for (ViewerEntry v : loadAll()) {
            if (!v.isDefault()) continue;
            String mime = mimeType != null ? mimeType.toLowerCase() : "";
            String ext  = extension  != null ? extension.toLowerCase()  : "";
            if (v.getMimeTypes().contains(mime)) return v;
            if (v.getExtensions().contains(ext)) return v;
        }
        return null;
    }

    /**
     * Returns all configured viewers (default + non-default) for the
     * "Open with…" menu.  Viewers that match the given MIME/extension
     * are listed first.
     */
    public List<ViewerEntry> getViewersFor(String mimeType, String extension) {
        List<ViewerEntry> matching    = new ArrayList<>();
        List<ViewerEntry> notMatching = new ArrayList<>();
        String mime = mimeType != null ? mimeType.toLowerCase() : "";
        String ext  = extension  != null ? extension.toLowerCase()  : "";
        for (ViewerEntry v : loadAll()) {
            if (v.getMimeTypes().contains(mime) ||
                    v.getExtensions().contains(ext)) {
                matching.add(v);
            } else {
                notMatching.add(v);
            }
        }
        matching.addAll(notMatching);
        return Collections.unmodifiableList(matching);
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public List<ViewerEntry> loadAll() {
        int count = prefs.getInt(COUNT_KEY, 0);
        if (count == 0) return defaultViewers();   // first run
        List<ViewerEntry> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String p = PREFIX + i + ".";
            String name     = prefs.get(p + "name", "");
            String exe      = prefs.get(p + "exe",  "");
            String argsRaw  = prefs.get(p + "args", "");
            String mimesRaw = prefs.get(p + "mimes","");
            String extsRaw  = prefs.get(p + "exts", "");
            boolean isDef   = prefs.getBoolean(p + "default", false);
            if (name.isBlank() || exe.isBlank()) continue;
            List<String> args  = argsRaw.isBlank()  ? List.of() : Arrays.asList(argsRaw.split(","));
            List<String> mimes = mimesRaw.isBlank() ? List.of() : Arrays.asList(mimesRaw.split(","));
            List<String> exts  = extsRaw.isBlank()  ? List.of() : Arrays.asList(extsRaw.split(","));
            result.add(new ViewerEntry(name, exe, args, mimes, exts, isDef));
        }
        return Collections.unmodifiableList(result);
    }

    public void saveAll(List<ViewerEntry> viewers) {
        prefs.putInt(COUNT_KEY, viewers.size());
        for (int i = 0; i < viewers.size(); i++) {
            ViewerEntry v = viewers.get(i);
            String p = PREFIX + i + ".";
            prefs.put(p + "name",    v.getDisplayName());
            prefs.put(p + "exe",     v.getExePath());
            prefs.put(p + "args",    String.join(",", v.getExtraArgs()));
            prefs.put(p + "mimes",   String.join(",", v.getMimeTypes()));
            prefs.put(p + "exts",    String.join(",", v.getExtensions()));
            prefs.putBoolean(p + "default", v.isDefault());
        }
    }

    // -------------------------------------------------------------------------
    // Sensible defaults for first run
    // -------------------------------------------------------------------------

    private static List<ViewerEntry> defaultViewers() {
        return List.of(
            // IrfanView — handles most common formats
            new ViewerEntry(
                "IrfanView",
                "C:\\Program Files\\IrfanView\\i_view64.exe",
                List.of(),
                List.of("image/jpeg","image/png","image/gif",
                        "image/bmp","image/tiff","image/webp",
                        "image/heic","image/heif","image/avif"),
                List.of("jpg","jpeg","png","gif","bmp",
                        "tif","tiff","webp","heic","heif","avif"),
                false   // not default — user must opt in
            ),
            // RawTherapee — RAW formats
            new ViewerEntry(
                "RawTherapee",
                "C:\\Program Files\\RawTherapee\\rawtherapee.exe",
                List.of(),
                List.of("image/x-canon-cr2","image/x-canon-cr3",
                        "image/x-nikon-nef","image/x-sony-arw",
                        "image/x-adobe-dng","image/x-olympus-orf"),
                List.of("cr2","cr3","nef","arw","dng","orf","rw2","raf"),
                false
            ),
            // Inkscape — SVG
            new ViewerEntry(
                "Inkscape",
                "C:\\Program Files\\Inkscape\\bin\\inkscape.exe",
                List.of(),
                List.of("image/svg+xml"),
                List.of("svg","svgz"),
                false
            )
        );
    }
}
