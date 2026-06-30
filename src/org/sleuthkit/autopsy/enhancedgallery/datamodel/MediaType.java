package org.sleuthkit.autopsy.enhancedgallery.datamodel;

import java.util.Set;

/**
 * Classifies a file as image, video, audio, or unknown.
 * Used throughout the module to decide rendering, filtering,
 * metadata extraction and which external viewers to offer.
 */
public enum MediaType {

    IMAGE,
    VIDEO,
    AUDIO,
    UNKNOWN;

    // ── MIME type sets ────────────────────────────────────────────────────────

    private static final Set<String> IMAGE_MIMES = Set.of(
        // Standard
        "image/jpeg", "image/png", "image/gif", "image/bmp",
        "image/tiff", "image/webp", "image/svg+xml",
        // HEIC / HEIF (Apple)
        "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence",
        // AVIF
        "image/avif", "image/avif-sequence",
        // RAW formats — various camera makers
        "image/x-canon-cr2", "image/x-canon-cr3", "image/x-canon-crw",
        "image/x-nikon-nef", "image/x-nikon-nrw",
        "image/x-sony-arw", "image/x-sony-sr2", "image/x-sony-srf",
        "image/x-adobe-dng",
        "image/x-olympus-orf",
        "image/x-panasonic-rw2",
        "image/x-fuji-raf",
        "image/x-pentax-pef",
        "image/x-sigma-x3f",
        "image/x-leica-rwl",
        "image/x-hasselblad-3fr", "image/x-hasselblad-fff",
        "image/x-kodak-dcr", "image/x-kodak-kdc",
        "image/x-minolta-mrw",
        "image/x-samsung-srw",
        // Other common
        "image/x-xcf", "image/x-psd",
        "image/vnd.ms-photo",        // HD Photo / JPEG XR
        "image/jxr", "image/jxl"     // JPEG XL
    );

    private static final Set<String> VIDEO_MIMES = Set.of(
        "video/mp4", "video/mpeg", "video/quicktime", "video/x-msvideo",
        "video/x-matroska", "video/webm", "video/3gpp", "video/3gpp2",
        "video/x-ms-wmv", "video/x-flv", "video/x-m4v",
        "video/ogg", "video/mp2t",
        "video/x-ms-asf"
    );

    private static final Set<String> AUDIO_MIMES = Set.of(
        "audio/mpeg", "audio/mp4", "audio/aac", "audio/ogg",
        "audio/wav", "audio/x-wav", "audio/flac", "audio/x-flac",
        "audio/webm", "audio/3gpp", "audio/3gpp2",
        "audio/x-ms-wma", "audio/x-aiff", "audio/aiff",
        "audio/x-m4a", "audio/mp3",
        "audio/vnd.dlna.adts"
    );

    // ── Extension fallback sets ───────────────────────────────────────────────

    private static final Set<String> IMAGE_EXTS = Set.of(
        "jpg","jpeg","png","gif","bmp","tif","tiff","webp","svg","svgz",
        "heic","heif","avif",
        "cr2","cr3","crw","nef","nrw","arw","sr2","srf","dng",
        "orf","rw2","raf","pef","x3f","rwl","3fr","fff",
        "dcr","kdc","mrw","srw",
        "psd","xcf","jxr","jxl","wdp"
    );

    private static final Set<String> VIDEO_EXTS = Set.of(
        "mp4","m4v","mov","avi","mkv","webm","3gp","3g2",
        "wmv","flv","mpg","mpeg","ts","mts","m2ts","vob"
    );

    private static final Set<String> AUDIO_EXTS = Set.of(
        "mp3","m4a","aac","ogg","oga","wav","flac","wma",
        "aiff","aif","opus","3gp","3g2","amr","mid","midi"
    );

    // ── Public factory ────────────────────────────────────────────────────────

    /**
     * Determines media type from MIME type first, extension as fallback.
     *
     * @param mimeType  MIME type string from Autopsy (may be null or empty)
     * @param fileName  original filename (used for extension fallback)
     * @return          MediaType, never null
     */
    public static MediaType fromFile(String mimeType, String fileName) {
        // 1 — try MIME type (most reliable, set by File Type Identification module)
        if (mimeType != null && !mimeType.isBlank()) {
            String mime = mimeType.toLowerCase().trim();
            if (IMAGE_MIMES.contains(mime)) return IMAGE;
            if (VIDEO_MIMES.contains(mime)) return VIDEO;
            if (AUDIO_MIMES.contains(mime)) return AUDIO;
            // Catch-all for mime types we don't have explicit entries for
            if (mime.startsWith("image/")) return IMAGE;
            if (mime.startsWith("video/")) return VIDEO;
            if (mime.startsWith("audio/")) return AUDIO;
        }

        // 2 — fall back to extension
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0) {
                String ext = fileName.substring(dot + 1).toLowerCase();
                if (IMAGE_EXTS.contains(ext)) return IMAGE;
                if (VIDEO_EXTS.contains(ext)) return VIDEO;
                if (AUDIO_EXTS.contains(ext)) return AUDIO;
            }
        }

        return UNKNOWN;
    }

    /** Convenience: is this a file the gallery should show at all? */
    public boolean isSupported() {
        return this != UNKNOWN;
    }

    /** Should we try to generate a thumbnail image for this type? */
    public boolean hasThumbnail() {
        return this == IMAGE || this == VIDEO;
    }
}
