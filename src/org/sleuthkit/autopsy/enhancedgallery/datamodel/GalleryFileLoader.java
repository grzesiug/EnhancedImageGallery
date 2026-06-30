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
     * Queries SleuthkitCase for all files whose MIME type or extension
     * indicates image / video / audio content, then calls {@code onBatch}
     * with successive batches and {@code onDone} when complete.
     *
     * @param onBatch  receives each batch (called on the loader thread — wrap with SwingUtilities)
     * @param onDone   called once when loading is finished (or cancelled)
     */
    public void load(Consumer<List<MediaFile>> onBatch, Runnable onDone) {
        try {
            SleuthkitCase db = Case.getCurrentCaseThrows().getSleuthkitCase();
            List<AbstractFile> rawFiles = queryAllMediaFiles(db);
            logger.log(Level.INFO,
                    "GalleryFileLoader: found {0} candidate files",
                    rawFiles.size());

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
                    case IMAGE -> MediaFile.MediaType.IMAGE;
                    case VIDEO -> MediaFile.MediaType.VIDEO;
                    default    -> MediaFile.MediaType.AUDIO;
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
    // SQL query
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all files whose MIME type starts with image/, video/, or audio/,
     * PLUS files whose extension matches known media types even if MIME is null
     * (happens when File Type Identification hasn't run yet).
     *
     * We use a single WHERE clause with OR to keep it one round-trip.
     */
    private List<AbstractFile> queryAllMediaFiles(SleuthkitCase db)
            throws TskCoreException {

        // Build MIME prefix conditions
        String mimeWhere = """
            (mime_type LIKE 'image/%'
             OR mime_type LIKE 'video/%'
             OR mime_type LIKE 'audio/%')
            """;

        // Extension fallback (for files not yet identified)
        String extWhere = """
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

        int regDir = TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue();

        // No filter on 'type' — include files from ALL Autopsy sources:
        //   FS (disk images), CARVED (recovered), DERIVED (from archives),
        //   LOCAL (added files), cloud sources, etc.
        // dir_type = REG ensures only actual files, not directories/virtual nodes.
        // Each file has a unique obj_id regardless of duplicate content (MD5).
        String where = "(" + mimeWhere + " OR " + extWhere + ")"
                + " AND dir_type = " + regDir
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
}
