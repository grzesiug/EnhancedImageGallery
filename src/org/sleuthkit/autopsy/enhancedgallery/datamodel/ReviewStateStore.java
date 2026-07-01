package org.sleuthkit.autopsy.enhancedgallery.datamodel;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * Persists review state (UNSEEN/SEEN/TAGGED/SKIPPED) and tag names to a
 * SQLite database stored inside the Autopsy case directory:
 *
 *   <case_dir>/enhanced_gallery/review_state.db
 *
 * Schema:
 *   review_state(
 *     obj_id       INTEGER PRIMARY KEY,
 *     status       TEXT    NOT NULL DEFAULT 'unseen',
 *     tag_name     TEXT,
 *     reviewed_at  TEXT,
 *     reviewer     TEXT
 *   )
 *
 * All writes are batched and committed together for performance.
 * The DB is opened once per case and closed when the case closes.
 */
public class ReviewStateStore implements AutoCloseable {

    private static final Logger logger =
            Logger.getLogger(ReviewStateStore.class.getName());

    private static final String DB_FILENAME = "gallery.db";
    // All gallery files under ModuleOutput\enhanced_gallery (Autopsy convention)
    private static final String SUBDIR = "ModuleOutput" + File.separator + "enhanced_gallery";

    private Connection conn;
    private String     caseDir;

    // ── Open / Init ─────────────────────────────────────────────────────────

    public ReviewStateStore(Case autopsyCase) throws SQLException {
        this.caseDir = autopsyCase.getCaseDirectory();
        File dir = new File(caseDir, SUBDIR);
        if (!dir.exists()) dir.mkdirs();
        File dbFile = new File(dir, DB_FILENAME);

        logger.log(Level.INFO, "ReviewStateStore: opening DB at {0}", dbFile.getAbsolutePath());
        try {
            conn = openSqliteConnection(dbFile.getAbsolutePath());
        } catch (Exception ex) {
            throw new SQLException("Cannot open gallery.db: " + ex.getMessage(), ex);
        }
        initSchema();
        setPragmas();
        logger.log(Level.INFO, "ReviewStateStore opened OK: {0}", dbFile.getAbsolutePath());
    }

    /**
     * Ensures the SQLite JDBC driver is registered with DriverManager.
     * Uses Autopsy's SleuthkitCase classloader which has sqlite-jdbc bundled.
     */
    /**
     * Returns a SQLite connection by trying every available mechanism.
     * Works around NetBeans Platform classloader isolation for JDBC drivers.
     */
    private static Connection openSqliteConnection(String path) throws Exception {
        String url = "jdbc:sqlite:" + path;
        Exception last = null;

        // Strategy 1: standard DriverManager (works if driver already registered)
        try {
            return DriverManager.getConnection(url);
        } catch (Exception ex) { last = ex; }

        // Strategy 2: load driver via Autopsy/SleuthKit classloaders then retry
        ClassLoader[] loaders = {
            org.sleuthkit.datamodel.SleuthkitCase.class.getClassLoader(),
            Thread.currentThread().getContextClassLoader(),
            ReviewStateStore.class.getClassLoader(),
            ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader cl : loaders) {
            if (cl == null) continue;
            try {
                Class<?> drvClass = Class.forName("org.sqlite.JDBC", true, cl);
                java.sql.Driver drv = (java.sql.Driver) drvClass
                        .getDeclaredConstructor().newInstance();
                java.util.Properties props = new java.util.Properties();
                Connection conn = drv.connect(url, props);
                if (conn != null) {
                    logger.log(Level.INFO, "SQLite connected via classloader: {0}", cl);
                    return conn;
                }
            } catch (Exception ex) { last = ex; }
        }

        // Strategy 3: direct SQLiteConnection via full class name
        try {
            ClassLoader cl = org.sleuthkit.datamodel.SleuthkitCase.class.getClassLoader();
            Class<?> connClass = Class.forName("org.sqlite.SQLiteConnection", true, cl);
            Object conn = connClass.getConstructor(String.class, String.class)
                    .newInstance(path, path);
            logger.log(Level.INFO, "SQLite connected via SQLiteConnection directly");
            return (Connection) conn;
        } catch (Exception ex) { last = ex; }

        throw new SQLException("Could not connect to SQLite: " + path
                + " — all strategies failed. Last error: " + last);
    }

    // keep this for unused import cleanup
    @SuppressWarnings("unused")
    private static void ensureSqliteDriver() {}

    private void initSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS review_state (" +
                "  obj_id      INTEGER PRIMARY KEY," +
                "  status      TEXT    NOT NULL DEFAULT 'unseen'," +
                "  tag_name    TEXT," +
                "  reviewed_at TEXT," +
                "  reviewer    TEXT" +
                ")"
            );
            // Thumbnail cache table — stored in same DB for simplicity
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS thumbnails (" +
                "  obj_id     INTEGER PRIMARY KEY," +
                "  file_size  INTEGER NOT NULL," +
                "  jpeg_data  BLOB    NOT NULL," +
                "  created_at TEXT    DEFAULT (datetime('now'))" +
                ")"
            );
        }
    }

    private void setPragmas() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA cache_size=4000");
            st.execute("PRAGMA page_size=8192");
        }
    }

    // ── Thumbnail cache methods ───────────────────────────────────────────────

    public byte[] loadThumb(long objId, long fileSize) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT jpeg_data, file_size FROM thumbnails WHERE obj_id=?")) {
            ps.setLong(1, objId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                if (rs.getLong("file_size") != fileSize) {
                    deleteThumb(objId);
                    return null;
                }
                return rs.getBytes("jpeg_data");
            }
        } catch (SQLException ex) {
            logger.log(Level.FINE, "loadThumb failed obj_id=" + objId, ex);
            return null;
        }
    }

    public void saveThumb(long objId, long fileSize, byte[] jpeg) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO thumbnails(obj_id,file_size,jpeg_data) VALUES(?,?,?)")) {
            ps.setLong(1, objId);
            ps.setLong(2, fileSize);
            ps.setBytes(3, jpeg);
            ps.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.FINE, "saveThumb failed obj_id=" + objId, ex);
        }
    }

    public boolean hasThumb(long objId, long fileSize) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT file_size FROM thumbnails WHERE obj_id=?")) {
            ps.setLong(1, objId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) == fileSize;
            }
        } catch (SQLException ex) { return false; }
    }

    public void deleteThumb(long objId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM thumbnails WHERE obj_id=?")) {
            ps.setLong(1, objId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public void clearThumbs() {
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM thumbnails");
            st.execute("VACUUM");
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "clearThumbs failed", ex);
        }
    }

    public String thumbStats() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(*) cnt, SUM(LENGTH(jpeg_data)) tot FROM thumbnails")) {
            if (rs.next()) {
                return String.format("%,d cached, %.1f MB",
                        rs.getLong("cnt"), rs.getLong("tot") / (1024.0 * 1024));
            }
        } catch (SQLException ignored) {}
        return "n/a";
    }

    // ── Load all saved states ────────────────────────────────────────────────

    /**
     * Returns map of obj_id → MediaFile.ReviewState + tag_name.
     * Called once when a case is opened.
     */
    public Map<Long, SavedState> loadAll() throws SQLException {
        Map<Long, SavedState> result = new HashMap<>();
        String sql = "SELECT obj_id, status, tag_name FROM review_state";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                long   id      = rs.getLong("obj_id");
                String status  = rs.getString("status");
                String tagName = rs.getString("tag_name");
                MediaFile.ReviewState state;
                try {
                    state = MediaFile.ReviewState.valueOf(status.toUpperCase());
                } catch (IllegalArgumentException e) {
                    state = MediaFile.ReviewState.SEEN;
                }
                result.put(id, new SavedState(state, tagName));
            }
        }
        logger.log(Level.INFO, "Loaded {0} saved review states", result.size());
        return result;
    }

    // ── Save one file's state ────────────────────────────────────────────────

    public void save(MediaFile mf) {
        save(mf.getId(),
             mf.getReviewState().name().toLowerCase(),
             mf.getTagName());
    }

    public void save(long objId, String status, String tagName) {
        String sql =
            "INSERT INTO review_state(obj_id,status,tag_name,reviewed_at,reviewer) " +
            "VALUES(?,?,?,?,?) " +
            "ON CONFLICT(obj_id) DO UPDATE SET " +
            "  status=excluded.status," +
            "  tag_name=excluded.tag_name," +
            "  reviewed_at=excluded.reviewed_at," +
            "  reviewer=excluded.reviewer";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, objId);
            ps.setString(2, status);
            ps.setString(3, tagName);
            ps.setString(4, Instant.now().toString());
            ps.setString(5, System.getProperty("user.name"));
            ps.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING,
                    "Failed to save review state for obj_id=" + objId, ex);
        }
    }

    // ── Batch save ───────────────────────────────────────────────────────────

    /**
     * Saves multiple MediaFiles in a single transaction — use after bulk
     * tagging operations to avoid hammering the DB.
     */
    public void saveBatch(Iterable<MediaFile> files) {
        String sql =
            "INSERT INTO review_state(obj_id,status,tag_name,reviewed_at,reviewer) " +
            "VALUES(?,?,?,?,?) " +
            "ON CONFLICT(obj_id) DO UPDATE SET " +
            "  status=excluded.status," +
            "  tag_name=excluded.tag_name," +
            "  reviewed_at=excluded.reviewed_at," +
            "  reviewer=excluded.reviewer";
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                String now = Instant.now().toString();
                String user = System.getProperty("user.name");
                for (MediaFile mf : files) {
                    ps.setLong(1, mf.getId());
                    ps.setString(2, mf.getReviewState().name().toLowerCase());
                    ps.setString(3, mf.getTagName());
                    ps.setString(4, now);
                    ps.setString(5, user);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Batch save failed", ex);
            try { conn.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // ── Statistics ───────────────────────────────────────────────────────────

    public SessionStats getStats() throws SQLException {
        // Review state (unseen/seen/skipped) is independent of tags — count them
        // separately. Tagged = any row with a non-empty tag_name, regardless of
        // its review state.
        SessionStats stats = new SessionStats();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT status, COUNT(*) as cnt FROM review_state GROUP BY status")) {
            while (rs.next()) {
                String s   = rs.getString("status");
                int    cnt = rs.getInt("cnt");
                switch (s) {
                    case "seen"    -> stats.seen    = cnt;
                    case "skipped" -> stats.skipped = cnt;
                    default        -> { /* unseen or legacy 'tagged' — not tracked here */ }
                }
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(*) as cnt FROM review_state WHERE tag_name IS NOT NULL AND tag_name <> ''")) {
            if (rs.next()) stats.tagged = rs.getInt("cnt");
        }
        return stats;
    }

    @Override
    public void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
            conn = null;
        }
    }

    // ── Inner value types ────────────────────────────────────────────────────

    public static class SavedState {
        public final MediaFile.ReviewState state;
        public final String tagName;
        SavedState(MediaFile.ReviewState state, String tagName) {
            this.state   = state;
            this.tagName = tagName;
        }
    }

    public static class SessionStats {
        public int seen    = 0;
        public int tagged  = 0;
        public int skipped = 0;
    }
}
