package org.sleuthkit.autopsy.enhancedgallery.datamodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings;
import org.sleuthkit.autopsy.enhancedgallery.datamodel.MediaType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Loads all media files from the current Autopsy case and converts them
 * to {@link MediaFile} objects.
 *
 * Design:
 *  - Runs entirely on the thread that calls {@link #load} (must NOT be EDT).
 *  - Calls {@code onBatch} for each batch of files as they are loaded so the
 *    UI can start rendering before the full load is complete.
 *  - Respects a {@code cancelled} flag so the caller can abort early.
 *
 * Usage:
 * <pre>
 *   GalleryFileLoader loader = new GalleryFileLoader(reviewStateStore);
 *   loader.load(
 *       batch -> SwingUtilities.invokeLater(() -> grid.addFiles(batch)),
 *       () -> SwingUtilities.invokeLater(() -> spinner.setVisible(false))
 *   );
 * </pre>
 */
public class GalleryFileLoader {

    private static final Logger logger =
            Logger.getLogger(GalleryFileLoader.class.getName());

    /** Files are delivered to the UI in batches of this size */
    private static final int BATCH_SIZE = 5000;

    private final ReviewStateStore reviewStore;
    private volatile boolean cancelled = false;

    public GalleryFileLoader(ReviewStateStore reviewStore) {
        this.reviewStore = reviewStore;
    }

    public void cancel() { cancelled = true; }

    // ─────────────────────────────────────────────────────────────────────────
    // Main entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Queries SleuthkitCase for all files whose MIME type indicates
     * image / video / audio content, then calls {@code onBatch}
     * with successive batches and {@code onDone} when complete.
     *
     * @param onBatch  receives each batch (called on the loader thread — wrap with SwingUtilities)
     * @param onDone   called once when loading is finished (or cancelled)
     */
    public void load(Consumer<List<MediaFile>> onBatch, Runnable onDone) {
        load(onBatch, onDone, null);
    }

    /**
     * Same as {@link #load(Consumer, Runnable)}, plus an optional callback that
     * receives a diagnostic warning: data sources where files with a known media
     * extension (.jpg, .mp4, etc.) have no MIME type set — meaning File Type
     * Identification likely did not run on that data source, so those files were
     * NOT included in the gallery (loading is MIME-type based only).
     *
     * @param onTypeIdWarning receives a map of dataSourceName -> count of
     *                        unidentified media-like files. Only called if
     *                        at least one such file was found. Called on the
     *                        loader thread (not EDT).
     */
    public void load(Consumer<List<MediaFile>> onBatch, Runnable onDone,
                      Consumer<Map<String, Integer>> onTypeIdWarning) {
        try {
            SleuthkitCase db = Case.getCurrentCaseThrows().getSleuthkitCase();
            List<AbstractFile> rawFiles = queryAllMediaFiles(db);
            logger.log(Level.INFO,
                    "GalleryFileLoader: found {0} candidate files",
                    rawFiles.size());

            if (onTypeIdWarning != null && !cancelled) {
                try {
                    Map<String, Integer> warning = findUnidentifiedMediaFiles(db);
                    if (!warning.isEmpty()) onTypeIdWarning.accept(warning);
                } catch (Exception ex) {
                    logger.log(Level.FINE, "Type-ID diagnostic query failed", ex);
                }
            }

            // Pre-load all saved review states
            Map<Long, ReviewStateStore.SavedState> savedStates;
            try { savedStates = reviewStore.loadAll(); }
            catch (Exception e) { savedStates = new java.util.HashMap<>(); }

            List<MediaFile> batch = new ArrayList<>(BATCH_SIZE);

            for (AbstractFile af : rawFiles) {
                if (cancelled) break;

                // Skip directories, unallocated meta files, etc.
                if (af.isDir() || af.isVirtual()) continue;
                if (af.getSize() == 0) continue;

                MediaType mt = MediaType.fromFile(af.getMIMEType(), af.getName());
                if (!mt.isSupported()) continue;

                MediaFile.MediaType mfType = switch (mt) {
                    case IMAGE    -> MediaFile.MediaType.IMAGE;
                    case VIDEO    -> MediaFile.MediaType.VIDEO;
                    case DOCUMENT -> MediaFile.MediaType.DOCUMENT;
                    default       -> MediaFile.MediaType.AUDIO;
                };
                MediaFile mf = new MediaFile(af, mfType);

                // Attach persisted review state
                ReviewStateStore.SavedState saved = savedStates.get(af.getId());
                if (saved != null) {
                    mf.setReviewState(saved.state);
                    if (saved.tagName != null) mf.setTagName(saved.tagName);
                }

                batch.add(mf);
                if (batch.size() >= BATCH_SIZE) {
                    onBatch.accept(new ArrayList<>(batch));
                    batch.clear();
                }
            }

            // Flush remaining files
            if (!batch.isEmpty()) {
                onBatch.accept(batch);
            }

        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "No case open during file load", ex);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error querying files from case", ex);
        } finally {
            onDone.run();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // On-demand document loading (option b)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads DOCUMENT files only. Called lazily the first time the user enables the
     * "Documents" filter, so pure-image analysts never pay the cost of pulling
     * (potentially very many) document files into memory. Mirrors {@link #load}
     * but restricted to the document MIME set, with no type-ID diagnostic.
     *
     * @param onBatch receives each batch (called on the loader thread — wrap with SwingUtilities)
     * @param onDone  called once when loading is finished (or cancelled)
     */
    public void loadDocuments(Consumer<List<MediaFile>> onBatch, Runnable onDone) {
        try {
            SleuthkitCase db = Case.getCurrentCaseThrows().getSleuthkitCase();
            List<AbstractFile> rawFiles = queryDocumentFiles(db);
            logger.log(Level.INFO,
                    "GalleryFileLoader: found {0} candidate document files",
                    rawFiles.size());

            Map<Long, ReviewStateStore.SavedState> savedStates;
            try { savedStates = reviewStore.loadAll(); }
            catch (Exception e) { savedStates = new java.util.HashMap<>(); }

            List<MediaFile> batch = new ArrayList<>(BATCH_SIZE);
            for (AbstractFile af : rawFiles) {
                if (cancelled) break;
                if (af.isDir() || af.isVirtual()) continue;
                if (af.getSize() == 0) continue;

                MediaType mt = MediaType.fromFile(af.getMIMEType(), af.getName());
                if (mt != MediaType.DOCUMENT) continue;

                MediaFile mf = new MediaFile(af, MediaFile.MediaType.DOCUMENT);
                ReviewStateStore.SavedState saved = savedStates.get(af.getId());
                if (saved != null) {
                    mf.setReviewState(saved.state);
                    if (saved.tagName != null) mf.setTagName(saved.tagName);
                }

                batch.add(mf);
                if (batch.size() >= BATCH_SIZE) {
                    onBatch.accept(new ArrayList<>(batch));
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) onBatch.accept(batch);

        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "No case open during document load", ex);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error querying document files from case", ex);
        } finally {
            onDone.run();
        }
    }

    /** Files whose MIME type is in the recognised document set (MIME-only, like media). */
    private List<AbstractFile> queryDocumentFiles(SleuthkitCase db)
            throws TskCoreException {

        StringBuilder in = new StringBuilder();
        for (String mime : MediaType.documentMimes()) {
            if (in.length() > 0) in.append(", ");
            in.append('\'').append(mime.replace("'", "''")).append('\'');
        }

        int regMeta = TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue();
        int regName = TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue();

        String where = "mime_type IN (" + in + ")"
                + " AND (meta_type = " + regMeta + " OR dir_type = " + regName + ")"
                + " AND size > 0";

        if (org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings.isExcludeKnown()) {
            where += " AND (known IS NULL OR known != 1)";
        }

        logger.log(Level.INFO, "GalleryFileLoader document query (excludeKnown={0}): {1}",
                new Object[]{GallerySettings.isExcludeKnown(), where});
        return db.findAllFilesWhere(where);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SQL query
    // ─────────────────────────────────────────────────────────────────────────

    // Extension list used ONLY for the type-identification diagnostic — not for
    // inclusion. Extensions are unreliable for forensic content matching (a file
    // can be renamed to evade detection), so actual loading is MIME-type only.
    private static final String MEDIA_EXT_WHERE = """
        LOWER(name) LIKE '%.jpg'  OR LOWER(name) LIKE '%.jpeg'
        OR LOWER(name) LIKE '%.png'  OR LOWER(name) LIKE '%.gif'
        OR LOWER(name) LIKE '%.bmp'  OR LOWER(name) LIKE '%.tif'
        OR LOWER(name) LIKE '%.tiff' OR LOWER(name) LIKE '%.webp'
        OR LOWER(name) LIKE '%.heic' OR LOWER(name) LIKE '%.heif'
        OR LOWER(name) LIKE '%.avif' OR LOWER(name) LIKE '%.svg'
        OR LOWER(name) LIKE '%.cr2'  OR LOWER(name) LIKE '%.cr3'
        OR LOWER(name) LIKE '%.nef'  OR LOWER(name) LIKE '%.arw'
        OR LOWER(name) LIKE '%.dng'  OR LOWER(name) LIKE '%.orf'
        OR LOWER(name) LIKE '%.rw2'  OR LOWER(name) LIKE '%.raf'
        OR LOWER(name) LIKE '%.mp4'  OR LOWER(name) LIKE '%.mov'
        OR LOWER(name) LIKE '%.avi'  OR LOWER(name) LIKE '%.mkv'
        OR LOWER(name) LIKE '%.mp3'  OR LOWER(name) LIKE '%.m4a'
        OR LOWER(name) LIKE '%.aac'  OR LOWER(name) LIKE '%.wav'
        OR LOWER(name) LIKE '%.flac' OR LOWER(name) LIKE '%.ogg'
        """;

    /**
     * Returns all files whose MIME type starts with image/, video/, or audio/.
     * MIME-type only — extension is NOT used for inclusion, since a file's
     * extension says nothing about its actual content (and can be deliberately
     * wrong). This means File Type Identification MUST have run on a data
     * source for its media files to appear here; see {@link #findUnidentifiedMediaFiles}.
     */
    private List<AbstractFile> queryAllMediaFiles(SleuthkitCase db)
            throws TskCoreException {

        // MIME-based inclusion (image/video/audio). SVG is the one deliberate
        // exception matched by extension: File Type Identification sometimes reports
        // it as text/xml (it's XML under the hood), which would otherwise route it to
        // the document path — but SVG is a graphic and should render a Batik preview.
        // See MediaType.fromFile, which classifies .svg/.svgz as IMAGE regardless of MIME.
        String mimeWhere = """
            (mime_type LIKE 'image/%'
             OR mime_type LIKE 'video/%'
             OR mime_type LIKE 'audio/%'
             OR LOWER(name) LIKE '%.svg'
             OR LOWER(name) LIKE '%.svgz')
            """;

        int regMeta = TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue();
        int regName = TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue();

        // No filter on 'type' — include files from ALL Autopsy sources:
        //   FS (disk images), CARVED (recovered), DERIVED (from archives),
        //   LOCAL (added files), cloud sources, etc.
        // Check meta_type OR dir_type (not just one): for files recovered from
        // deleted/orphaned MFT entries, dir_type can be inconsistent but meta_type
        // is reliable. For CARVED/DERIVED/LOCAL files (no filesystem metadata),
        // meta_type may be NULL/unset but dir_type is reliable. Checking both
        // with OR covers every source without excluding either case.
        // Each file has a unique obj_id regardless of duplicate content (MD5).
        String where = mimeWhere
                + " AND (meta_type = " + regMeta + " OR dir_type = " + regName + ")"
                + " AND size > 0";

        // Exclude NSRL "Known" files when option is enabled (known=1 in tsk_files).
        // These are OS/application files verified safe — no forensic value.
        if (org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings.isExcludeKnown()) {
            where += " AND (known IS NULL OR known != 1)";
        }

        logger.log(Level.INFO, "GalleryFileLoader query (excludeKnown={0}): {1}",
                new Object[]{GallerySettings.isExcludeKnown(), where});
        return db.findAllFilesWhere(where);
    }

    /**
     * Diagnostic: finds files with a known media extension but no MIME type set —
     * a strong signal that File Type Identification did not run on their data
     * source. These files are NOT included in the gallery (see queryAllMediaFiles).
     *
     * @return map of data source display name -> count of such files
     *         (empty map if none found)
     */
    private Map<String, Integer> findUnidentifiedMediaFiles(SleuthkitCase db) {
        Map<String, Integer> result = new java.util.LinkedHashMap<>();
        try {
            int regMeta = TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue();
            int regName = TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue();
            String where = "(" + MEDIA_EXT_WHERE + ")"
                    + " AND (mime_type IS NULL OR mime_type = '')"
                    + " AND (meta_type = " + regMeta + " OR dir_type = " + regName + ")"
                    + " AND size > 0";
            List<AbstractFile> unidentified = db.findAllFilesWhere(where);
            for (AbstractFile af : unidentified) {
                if (af.isDir() || af.isVirtual()) continue;
                String dsName;
                try { dsName = af.getDataSource().getName(); }
                catch (Exception ex) { dsName = "(unknown data source)"; }
                result.merge(dsName, 1, Integer::sum);
            }
        } catch (Exception ex) {
            logger.log(Level.FINE, "findUnidentifiedMediaFiles failed", ex);
        }
        return result;
    }
}
