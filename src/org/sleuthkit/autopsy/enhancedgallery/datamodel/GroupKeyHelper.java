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
                // Conversation cards group by messaging app (SMS/WHATSAPP/EMAIL…)
                String ext = mf.getExtension().toUpperCase();
                yield ext.isEmpty() ? "(no extension)" : ext;
            }
            case "mime", "mime type" -> {
                if (mf.isThread()) yield "message/" + (mf.getDocApp().isBlank() ? "thread" : mf.getDocApp());
                String mime = mf.getMimeType();
                yield (mime != null && !mime.isBlank()) ? mime : "(unknown)";
            }
            case "participants" -> {
                if (!mf.isThread()) yield "(not a conversation)";
                java.util.List<String> p = new java.util.ArrayList<>(mf.getDocParticipants());
                if (p.isEmpty()) yield "(unknown participants)";
                java.util.Collections.sort(p);
                yield String.join(" ↔ ", p);
            }
            case "date", "modified" -> {
                // A thread's own time range beats the source file's mtime
                // (mmssms.db mtime says nothing about the conversation's date).
                if (mf.isThread()) yield threadMonth(mf);
                yield new SimpleDateFormat("yyyy-MM-dd").format(new Date(mf.getMtimeMillis()));
            }
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

    /** Month bucket (yyyy-MM) from the thread's ISO-8601 date_start, or "(unknown)". */
    private static String threadMonth(MediaFile mf) {
        String iso = mf.getDocDateStart();
        return (iso != null && iso.length() >= 7) ? iso.substring(0, 7) : "(unknown date)";
    }

    public static String displayName(String groupBy) {
        if (groupBy == null) return "Paths";
        return switch (groupBy.toLowerCase()) {
            case "format", "extension" -> "Extensions";
            case "mime", "mime type"   -> "MIME types";
            case "participants"        -> "Participants";
            case "date", "modified"    -> "Modified dates";
            case "accessed"            -> "Accessed dates";
            case "created"             -> "Created dates";
            case "changed"             -> "Changed dates";
            case "tag"                 -> "Tags";
            default                    -> "Paths";
        };
    }
}
