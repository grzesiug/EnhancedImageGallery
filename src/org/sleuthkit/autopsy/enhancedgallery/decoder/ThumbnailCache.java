package org.sleuthkit.autopsy.enhancedgallery.decoder;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.*;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.enhancedgallery.datamodel.ReviewStateStore;

/**
 * Persistent thumbnail cache using the existing review_state.db (ReviewStateStore).
 *
 * Thumbnails are stored as JPEG BLOBs in the 'thumbnails' table alongside
 * review state data — single file, no extra connection management.
 *
 * Location: <case_dir>/enhanced_gallery/review_state.db → table 'thumbnails'
 *
 * Cache invalidation: stored file_size vs current AbstractFile.getSize().
 * If file was modified, thumbnail is regenerated.
 */
public class ThumbnailCache {

    private static final Logger logger = Logger.getLogger(ThumbnailCache.class.getName());
    private static final float JPEG_QUALITY = 0.82f;

    private static volatile ThumbnailCache instance;
    private volatile ReviewStateStore store; // shared connection from ReviewStateStore

    private ThumbnailCache() {}

    // ── Singleton ─────────────────────────────────────────────────────────────

    public static ThumbnailCache getInstance() {
        if (instance == null) {
            synchronized (ThumbnailCache.class) {
                if (instance == null) instance = new ThumbnailCache();
            }
        }
        return instance;
    }

    // ── Case lifecycle ────────────────────────────────────────────────────────

    /** Called after ReviewStateStore is opened — shares the same DB connection. */
    public void openCase(ReviewStateStore reviewStateStore) {
        this.store = reviewStateStore;
        logger.log(Level.INFO,
                "ThumbnailCache active. Stats: {0}", reviewStateStore.thumbStats());
    }

    /** Kept for API compatibility — real open is via openCase(ReviewStateStore). */
    public void openCase(Case autopsyCase) {
        // No-op: connection managed by ReviewStateStore
    }

    public void closeCase() {
        store = null;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public BufferedImage load(long objId, long fileSize) {
        ReviewStateStore s = store;
        if (s == null) return null;
        try {
            byte[] data = s.loadThumb(objId, fileSize);
            if (data == null || data.length == 0) return null;
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img != null)
                logger.log(Level.FINEST, "Cache hit: obj_id={0}", objId);
            return img;
        } catch (Exception ex) {
            logger.log(Level.FINE, "Cache read failed obj_id=" + objId, ex);
            return null;
        }
    }

    public boolean isCached(long objId, long fileSize) {
        ReviewStateStore s = store;
        return s != null && s.hasThumb(objId, fileSize);
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public void save(long objId, long fileSize, BufferedImage img) {
        ReviewStateStore s = store;
        if (s == null || img == null) return;
        try {
            byte[] jpeg = toJpeg(img, JPEG_QUALITY);
            if (jpeg != null) s.saveThumb(objId, fileSize, jpeg);
        } catch (Exception ex) {
            logger.log(Level.FINE, "Cache write failed obj_id=" + objId, ex);
        }
    }

    // ── Stats / management ────────────────────────────────────────────────────

    public String stats() {
        ReviewStateStore s = store;
        return s != null ? s.thumbStats() : "cache not open";
    }

    public void clear() {
        ReviewStateStore s = store;
        if (s != null) {
            s.clearThumbs();
            logger.info("Thumbnail cache cleared");
        }
    }

    // ── JPEG helper ───────────────────────────────────────────────────────────

    private static byte[] toJpeg(BufferedImage img, float quality) {
        try {
            // JPEG has no alpha — convert to RGB with white background
            BufferedImage rgb = new BufferedImage(
                    img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = rgb.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(img, 0, 0, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream(16384);
            var writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (writers.hasNext()) {
                var writer = writers.next();
                var param  = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                try (var out = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(out);
                    writer.write(null, new IIOImage(rgb, null, null), param);
                }
                writer.dispose();
            } else {
                ImageIO.write(rgb, "jpg", baos);
            }
            return baos.toByteArray();
        } catch (Exception ex) {
            logger.log(Level.FINE, "JPEG encode failed", ex);
            return null;
        }
    }
}
