package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared parsing/matching for the multi-phrase, pipe-separated search boxes
 * (file-name search and group-name filter). A query like {@code img1|img2}
 * matches anything containing <em>either</em> term (OR). Terms are trimmed and
 * lower-cased; empty terms (from {@code a||b}, {@code img|}, or a lone {@code |})
 * are dropped so they can't turn the filter into a match-everything no-op.
 */
final class SearchTerms {

    private SearchTerms() {}

    /** Splits on '|', trims, lower-cases, drops empties. Null/blank → empty list (= no filter). */
    static List<String> parse(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String part : raw.split("\\|")) {
            String t = part.trim().toLowerCase();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /**
     * True if {@code haystackLower} (already lower-cased) contains ANY of the terms.
     * An empty term list means "no filter" and matches everything.
     */
    static boolean matchesAny(String haystackLower, List<String> termsLower) {
        if (termsLower.isEmpty()) return true;
        for (String t : termsLower) {
            if (haystackLower.contains(t)) return true;
        }
        return false;
    }
}
