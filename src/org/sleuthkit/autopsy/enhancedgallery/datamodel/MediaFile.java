package org.sleuthkit.autopsy.enhancedgallery.datamodel;

import java.time.Instant;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Wraps an Autopsy AbstractFile with pre-computed fields used by the gallery:
 *   - media type (IMAGE / VIDEO / AUDIO)
 *   - review state (UNSEEN / SEEN / TAGGED / SKIPPED)
 *   - applied tag name
 *   - cached thumbnail (set later by DecoderChain on a background thread)
 */
public class MediaFile {

    // ── Media type ──────────────────────────────────────────────────────────
    public enum MediaType { IMAGE, VIDEO, AUDIO, DOCUMENT }

    // ── Review state ────────────────────────────────────────────────────────
    // Review state and tags are INDEPENDENT dimensions: a file can be SEEN and
    // tagged, UNSEEN and tagged, SEEN and untagged, etc. Tagging never changes
    // the review state (and vice-versa). "TAGGED" is intentionally NOT a review
    // state — tag presence is tracked separately via isTagged().
    public enum ReviewState { UNSEEN, SEEN, SKIPPED }

    // ── Core fields ─────────────────────────────────────────────────────────
    private final AbstractFile abstractFile;
    private final MediaType    mediaType;
    private final String       dataSourceName; // first path segment, e.g. "img_laptop.E01"
    private final String       uniquePathCache; // cached to avoid repeated DB calls

    // mutable review state — written on EDT, read on EDT + background
    private volatile ReviewState    reviewState = ReviewState.UNSEEN;
    private volatile String         tagName     = null;  // primary tag (for filtering/grouping)
    private volatile java.util.List<String> allTagNames = java.util.List.of(); // all tags

    // thumbnail — null until decoded
    private volatile java.awt.image.BufferedImage thumbnail = null;
    private volatile boolean thumbnailRequested = false;
    private volatile boolean thumbnailFailed    = false;  // true if decode was attempted and failed

    // ── Message-thread fields (AITT Etap J) ─────────────────────────────────
    // A "thread card" wraps the artifact's SOURCE FILE (e.g. mmssms.db, .eml) but
    // its identity is the ARTIFACT obj_id — many threads can share one source
    // file, so the file id must never be used as the card's key. Null for
    // ordinary files.
    private final Long   artifactObjId;
    private final String docKind;   // "thread-chat" | "thread-email" | null
    private final String docLabel;  // display label from AITT ("Chat (SMS): … (12 msgs)")
    private final String docApp;    // "sms" | "whatsapp" | "email" | "" (normalized)
    private final java.util.List<String> docParticipants; // normalized, may be empty
    private final int    docMsgCount;
    private final String docDateStart, docDateEnd; // ISO-8601 UTC or ""

    // ── Constructor ─────────────────────────────────────────────────────────
    public MediaFile(AbstractFile abstractFile, MediaType mediaType) {
        this(abstractFile, mediaType, null, null, null, null, null, 0, null, null);
    }

    private MediaFile(AbstractFile abstractFile, MediaType mediaType,
                      Long artifactObjId, String docKind, String docLabel, String docApp,
                      java.util.List<String> docParticipants, int docMsgCount,
                      String docDateStart, String docDateEnd) {
        this.abstractFile   = abstractFile;
        this.mediaType      = mediaType;
        this.artifactObjId  = artifactObjId;
        this.docKind        = docKind;
        this.docLabel       = docLabel;
        this.docApp         = docApp;
        this.docParticipants = docParticipants == null
                ? java.util.List.of() : java.util.List.copyOf(docParticipants);
        this.docMsgCount    = docMsgCount;
        this.docDateStart   = docDateStart;
        this.docDateEnd     = docDateEnd;
        // Cache path at construction time — avoids repeated DB calls during grouping/filtering
        String path = abstractFile.getName();
        try { path = abstractFile.getUniquePath(); } catch (Exception ignored) {}
        this.uniquePathCache = path;
        this.dataSourceName  = extractDataSource(abstractFile, path);
    }

    /**
     * Creates a "conversation card" for a message thread indexed by AI Text Triage:
     * wraps the artifact's source file for display/plumbing, but identifies as the
     * artifact obj_id (see {@link #getId}).
     */
    public static MediaFile forThread(AbstractFile sourceFile, long artifactObjId,
                                      String docKind, String docLabel, String docApp,
                                      java.util.List<String> participants, int msgCount,
                                      String dateStart, String dateEnd) {
        return new MediaFile(sourceFile, MediaType.DOCUMENT, artifactObjId,
                docKind, docLabel, docApp, participants, msgCount, dateStart, dateEnd);
    }

    /** True when this is a conversation card (message thread), not a real file. */
    public boolean isThread()          { return artifactObjId != null; }
    public String  getDocKind()        { return docKind  == null ? "" : docKind; }
    public String  getDocLabel()       { return docLabel == null ? "" : docLabel; }
    public String  getDocApp()         { return docApp   == null ? "" : docApp; }
    public java.util.List<String> getDocParticipants() { return docParticipants; }
    public int     getDocMsgCount()    { return docMsgCount; }
    public String  getDocDateStart()   { return docDateStart == null ? "" : docDateStart; }
    public String  getDocDateEnd()     { return docDateEnd   == null ? "" : docDateEnd; }

    private static String extractDataSource(AbstractFile af, String cachedPath) {
        // First segment of unique path is always the data source name
        if (cachedPath != null && !cachedPath.isBlank()) {
            String trimmed = cachedPath.startsWith("/") ? cachedPath.substring(1) : cachedPath;
            int slash = trimmed.indexOf('/');
            if (slash > 0) return trimmed.substring(0, slash);
        }
        // Fallback: ask the content object
        try {
            org.sleuthkit.datamodel.Content ds = af.getDataSource();
            if (ds != null && ds.getName() != null && !ds.getName().isBlank())
                return ds.getName();
        } catch (Exception ignored) {}
        return "Unknown";
    }

    public String  getDataSourceName()  { return dataSourceName; }
    public boolean isThumbnailFailed()  { return thumbnailFailed; }
    public void    markThumbnailFailed(){ thumbnailFailed = true; }

    /** Resets thumbnail state so decoding will be retried (e.g. after tool config change). */
    public void resetThumbnailState() {
        thumbnail          = null;
        thumbnailRequested = false;
        thumbnailFailed    = false;
    }

    // ── Accessors ───────────────────────────────────────────────────────────
    public AbstractFile getAbstractFile()   { return abstractFile; }
    /** Card identity: the artifact obj_id for threads, the file obj_id otherwise. */
    public long         getId()             { return artifactObjId != null ? artifactObjId
                                                                            : abstractFile.getId(); }
    public String       getName()           { return isThread() && docLabel != null && !docLabel.isBlank()
                                                     ? docLabel : abstractFile.getName(); }
    public long         getSize()           { return abstractFile.getSize(); }
    public String       getMimeType()       { return abstractFile.getMIMEType(); }
    public MediaType    getMediaType()      { return mediaType; }
    public String       getUniquePath()      { return uniquePathCache; }
    public String       getExtension() {
        if (isThread()) return docApp != null && !docApp.isBlank() ? docApp : "msg";
        String n = abstractFile.getName();
        int dot = n.lastIndexOf('.');
        return dot >= 0 ? n.substring(dot + 1).toLowerCase() : "";
    }
    public long         getMtimeMillis() {
        return abstractFile.getMtime() * 1000L;
    }
    public String       getMd5Hash()        { return abstractFile.getMd5Hash(); }

    // Review state
    public ReviewState  getReviewState()    { return reviewState; }
    public void         setReviewState(ReviewState s) { this.reviewState = s; }

    // Tag
    public String       getTagName()         { return tagName; }
    public java.util.List<String> getAllTagNames() { return allTagNames; }

    /** Sets the primary tag. Does NOT change review state — the two are independent. */
    public void setTagName(String t) {
        this.tagName = t;
        // Keep allTagNames in sync
        if (t == null) {
            this.allTagNames = java.util.List.of();
        } else if (!allTagNames.contains(t)) {
            java.util.List<String> updated = new java.util.ArrayList<>(allTagNames);
            if (!updated.contains(t)) updated.add(0, t); // primary first
            this.allTagNames = java.util.Collections.unmodifiableList(updated);
        }
    }

    /**
     * Sets all tags from Autopsy (first = primary for display).
     * Does NOT change review state — tags and seen/unseen are independent.
     */
    public void setAllTagNames(java.util.List<String> tags) {
        this.allTagNames = tags == null || tags.isEmpty()
                ? java.util.List.of()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(tags));
        this.tagName     = allTagNames.isEmpty() ? null : allTagNames.get(0);
    }

    public boolean isTagged() { return tagName != null; }

    // Thumbnail
    public java.awt.image.BufferedImage getThumbnail()   { return thumbnail; }
    public void setThumbnail(java.awt.image.BufferedImage img) { this.thumbnail = img; }
    public boolean isThumbnailRequested()  { return thumbnailRequested; }
    public void    markThumbnailRequested(){ this.thumbnailRequested = true; }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Status buckets this file belongs to. Since review state and tags are
     * independent, a file can be in MULTIPLE buckets at once — e.g. a seen,
     * tagged file is in both "seen" and "tagged". The status filter matches
     * if ANY selected bucket overlaps these.
     */
    public java.util.Set<String> getFilterBuckets() {
        java.util.Set<String> buckets = new java.util.HashSet<>();
        buckets.add(reviewState.name().toLowerCase()); // unseen | seen | skipped
        if (tagName != null) buckets.add("tagged");
        return buckets;
    }

    /** True if this file matches the given filter set and geo-only flag. */
    public boolean matchesFilters(java.util.Set<String> statusBuckets,
                                  java.util.Set<String> typeSet,
                                  boolean geoOnly,
                                  GpsCache gpsCache) {
        // Conversation cards have their own type key ("message") so the Messages
        // checkbox — not Documents — controls their visibility.
        String typeKey = isThread() ? "message" : mediaType.name().toLowerCase();
        if (!typeSet.contains(typeKey)) return false;
        // Overlapping buckets: show the file if any of its buckets is selected
        boolean statusMatch = getFilterBuckets().stream().anyMatch(statusBuckets::contains);
        if (!statusMatch)                                      return false;
        if (geoOnly && !gpsCache.hasGps(getId()))              return false;
        return true;
    }

    @Override
    public String toString() {
        return "MediaFile{id=" + getId() + ", name=" + getName()
               + ", type=" + mediaType + ", state=" + reviewState + "}";
    }
}
