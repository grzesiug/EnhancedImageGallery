package org.sleuthkit.autopsy.enhancedgallery.datamodel;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.*;

/**
 * Queries the Autopsy SleuthkitCase database and returns a list of
 * MediaFile objects representing all image, video and audio files
 * in the current case.
 *
 * Detection strategy (in order):
 *   1. MIME type prefix  ("image/", "video/", "audio/")
 *   2. File extension fallback (for files that weren't typed by ingest)
 *
 * GPS coordinates are loaded from TSK_GPS_BOOKMARKS / TSK_METADATA_EXIF
 * BlackboardArtifacts and stored in GpsCache for fast lookup.
 */
public class MediaFileLoader {

    private static final Logger logger =
            Logger.getLogger(MediaFileLoader.class.getName());

    // Extensions recognised when MIME type is missing
    private static final Set<String> IMAGE_EXT = Set.of(
        "jpg","jpeg","png","gif","bmp","tif","tiff",
        "heic","heif","webp","avif","svg","svgz",
        "cr2","cr3","nef","arw","dng","orf","rw2","raf","psd"
    );
    private static final Set<String> VIDEO_EXT = Set.of(
        "mp4","mov","avi","mkv","wmv","flv","m4v",
        "3gp","3g2","mts","m2ts","mpg","mpeg","webm"
    );
    private static final Set<String> AUDIO_EXT = Set.of(
        "mp3","m4a","aac","wav","flac","ogg","wma",
        "aiff","aif","opus","amr","3gp"
    );

    // ── Public API ────────────────────────────────────────────────────────────

    public static class LoadResult {
        public final List<MediaFile> files;
        public final GpsCache        gpsCache;
        LoadResult(List<MediaFile> files, GpsCache gpsCache) {
            this.files    = files;
            this.gpsCache = gpsCache;
        }
    }

    /**
     * Loads all media files from the current Autopsy case.
     * Must be called on a background thread — this queries the DB.
     *
     * @param progressCallback receives (loaded, total) updates for a progress bar
     */
    public static LoadResult load(Case autopsyCase,
                                  java.util.function.BiConsumer<Integer,Integer> progressCallback)
            throws TskCoreException {

        SleuthkitCase db = autopsyCase.getSleuthkitCase();
        List<MediaFile> result = new ArrayList<>();
        GpsCache gpsCache = new GpsCache();

        // ── Step 1: find files by MIME type ──────────────────────────────────
        List<String> mimePatterns = List.of("image/%", "video/%", "audio/%");
        List<AbstractFile> all = new ArrayList<>();

        for (String pat : mimePatterns) {
            // findAllFilesWhere uses a raw WHERE clause — efficient, single query
            all.addAll(db.findAllFilesWhere(
                "mime_type LIKE '" + pat + "' AND size > 0"
            ));
        }

        // ── Step 2: extension fallback for untyped files ──────────────────────
        // Collect extensions we haven't already covered
        List<AbstractFile> untyped = db.findAllFilesWhere(
            "(mime_type IS NULL OR mime_type = '') AND size > 0"
        );
        for (AbstractFile f : untyped) {
            String ext = getExtension(f.getName());
            if (IMAGE_EXT.contains(ext) || VIDEO_EXT.contains(ext)
                    || AUDIO_EXT.contains(ext)) {
                all.add(f);
            }
        }

        // ── Step 3: deduplicate by obj_id ────────────────────────────────────
        Map<Long, AbstractFile> unique = new LinkedHashMap<>();
        for (AbstractFile f : all) unique.put(f.getId(), f);

        // ── Step 4: wrap in MediaFile ─────────────────────────────────────────
        int total = unique.size();
        int i = 0;
        for (AbstractFile f : unique.values()) {
            result.add(new MediaFile(f, detectType(f)));
            i++;
            if (progressCallback != null && i % 100 == 0) {
                progressCallback.accept(i, total);
            }
        }

        logger.log(Level.INFO,
                "MediaFileLoader: found {0} media files", result.size());

        // ── Step 5: load GPS data from Blackboard ────────────────────────────
        loadGpsData(db, gpsCache);

        return new LoadResult(result, gpsCache);
    }

    // ── GPS loading ──────────────────────────────────────────────────────────

    private static void loadGpsData(SleuthkitCase db, GpsCache gpsCache) {
        try {
            // TSK_METADATA_EXIF contains lat/long extracted by PictureAnalyzer
            List<BlackboardArtifact> exifArtifacts =
                db.getBlackboardArtifacts(
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF);

            for (BlackboardArtifact art : exifArtifacts) {
                try {
                    BlackboardAttribute latAttr = art.getAttribute(
                        new BlackboardAttribute.Type(
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE));
                    BlackboardAttribute lngAttr = art.getAttribute(
                        new BlackboardAttribute.Type(
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE));

                    if (latAttr != null && lngAttr != null) {
                        double lat = latAttr.getValueDouble();
                        double lng = lngAttr.getValueDouble();
                        // Skip (0,0) — likely missing data
                        if (lat != 0.0 || lng != 0.0) {
                            gpsCache.put(art.getObjectID(), lat, lng, null);
                        }
                    }
                } catch (TskCoreException ex) {
                    // skip individual artifact errors
                }
            }
            logger.log(Level.INFO,
                    "GPS cache loaded: {0} entries", gpsCache.size());
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Could not load GPS data", ex);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static MediaFile.MediaType detectType(AbstractFile f) {
        String mime = f.getMIMEType();
        if (mime != null) {
            if (mime.startsWith("image/")) return MediaFile.MediaType.IMAGE;
            if (mime.startsWith("video/")) return MediaFile.MediaType.VIDEO;
            if (mime.startsWith("audio/")) return MediaFile.MediaType.AUDIO;
        }
        // fall back to extension
        String ext = getExtension(f.getName());
        if (VIDEO_EXT.contains(ext)) return MediaFile.MediaType.VIDEO;
        if (AUDIO_EXT.contains(ext)) return MediaFile.MediaType.AUDIO;
        return MediaFile.MediaType.IMAGE;
    }

    private static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }
}
