package org.sleuthkit.autopsy.enhancedgallery.datamodel;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Single source of truth for grouping logic.
 * Used by both GroupSidebar (to build groups) and applyFilters (to filter).
 */
public final class GroupKeyHelper {

    public static final String ALL = "__ALL__";

    private GroupKeyHelper() {}

    public static String keyOf(MediaFile mf, String groupBy) {
        if (groupBy == null) groupBy = "path";
        return switch (groupBy.toLowerCase()) {
            case "format", "extension" -> {
                String ext = mf.getExtension().toUpperCase();
                yield ext.isEmpty() ? "(no extension)" : ext;
            }
            case "mime", "mime type" -> {
                String mime = mf.getMimeType();
                yield (mime != null && !mime.isBlank()) ? mime : "(unknown)";
            }
            case "date", "modified" ->
                new SimpleDateFormat("yyyy-MM-dd").format(new Date(mf.getMtimeMillis()));
            case "accessed" -> {
                long t = mf.getAbstractFile().getAtime();
                yield t > 0 ? new SimpleDateFormat("yyyy-MM-dd").format(new Date(t * 1000L))
                            : "(unknown)";
            }
            case "created" -> {
                long t = mf.getAbstractFile().getCrtime();
                yield t > 0 ? new SimpleDateFormat("yyyy-MM-dd").format(new Date(t * 1000L))
                            : "(unknown)";
            }
            case "changed" -> {
                long t = mf.getAbstractFile().getCtime();
                yield t > 0 ? new SimpleDateFormat("yyyy-MM-dd").format(new Date(t * 1000L))
                            : "(unknown)";
            }
            case "tag" -> mf.isTagged() ? mf.getTagName() : "(untagged)";
            default -> {                          // path / folder
                String path = mf.getUniquePath();
                int last = path.lastIndexOf('/');
                yield last > 0 ? path.substring(0, last) : "/";
            }
        };
    }

    public static String displayName(String groupBy) {
        if (groupBy == null) return "Paths";
        return switch (groupBy.toLowerCase()) {
            case "format", "extension" -> "Extensions";
            case "mime", "mime type"   -> "MIME types";
            case "date", "modified"    -> "Modified dates";
            case "accessed"            -> "Accessed dates";
            case "created"             -> "Created dates";
            case "changed"             -> "Changed dates";
            case "tag"                 -> "Tags";
            default                    -> "Paths";
        };
    }
}
