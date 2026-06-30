package org.sleuthkit.autopsy.enhancedgallery.datamodel;

/**
 * Per-file review state persisted to the case SQLite database.
 *
 * Stored as a TEXT column so the db is human-readable and
 * survives future enum reorderings.
 */
public enum ReviewState {

    /** File has never been scrolled into view in this case. */
    UNSEEN("unseen"),

    /** File was displayed in the thumbnail grid but not tagged. */
    SEEN("seen"),

    /** File has at least one Autopsy tag attached. */
    TAGGED("tagged"),

    /** Examiner explicitly chose to skip this file. */
    SKIPPED("skipped");

    private final String dbValue;

    ReviewState(String dbValue) {
        this.dbValue = dbValue;
    }

    /** Value written to / read from the database TEXT column. */
    public String getDbValue() {
        return dbValue;
    }

    /**
     * Parses a string from the database back to enum.
     * Returns UNSEEN for any unknown value (safe default).
     */
    public static ReviewState fromDbValue(String value) {
        if (value == null) return UNSEEN;
        for (ReviewState s : values()) {
            if (s.dbValue.equalsIgnoreCase(value)) return s;
        }
        return UNSEEN;
    }

    /**
     * Returns the next "natural" state when a file is clicked:
     *   UNSEEN → SEEN  (auto-transition when thumbnail appears in viewport)
     *   everything else stays as-is (user must explicitly tag/skip)
     */
    public ReviewState afterView() {
        return this == UNSEEN ? SEEN : this;
    }
}
