package org.sleuthkit.autopsy.enhancedgallery.search;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin client + lifecycle manager for the AI Image Triage Python service,
 * used by Enhanced Evidence Gallery for semantic search ({@code /search}) and
 * visual-similarity lookup ({@code /similar}).
 *
 * <p>Design goals:
 * <ul>
 *   <li><b>Zero impact when unused.</b> Nothing here runs unless the user
 *       explicitly triggers an AI search. If the service can't be located/started,
 *       the model is missing, or no FAISS index exists, the calls fail with a
 *       clear message and the rest of the gallery is unaffected. The service is
 *       auto-discovered (%LOCALAPPDATA%/%ProgramData%); {@code AIT_SERVICE_DIR}
 *       is only a dev override.</li>
 *   <li><b>Own-process only.</b> The service is shared with the ingest module
 *       on port 8756. We start it lazily only if it isn't already running, and
 *       {@link #stopIfOwned()} destroys it only if WE launched it — never a
 *       service owned by an active ingest.</li>
 *   <li><b>Classic HTTP.</b> Uses {@link HttpURLConnection}, not
 *       {@code java.net.http.HttpClient}, whose NIO selector thread deadlocks
 *       under Autopsy's security-manager JVM. Same reason MiniJson replaces
 *       Gson — no external jars via Class-Path (NetBeans classloader deadlock).</li>
 * </ul>
 */
public final class AiSearchService {

    private static final Logger logger = Logger.getLogger(AiSearchService.class.getName());
    private static final AiSearchService INSTANCE = new AiSearchService();

    private static final String SERVICE_DIR_ENV = "AIT_SERVICE_DIR";
    private static final int    PORT            = 8756;
    private static final String BASE            = "http://127.0.0.1:" + PORT;
    private static final int    HEALTH_TIMEOUT_MS       = 1000;
    private static final int    SEARCH_TIMEOUT_MS       = 30000; // first /search loads CLIP
    private static final int    STARTUP_HEALTH_RETRIES  = 40;
    private static final long   STARTUP_RETRY_DELAY_MS  = 500;

    /** Non-null only if THIS process launched the service (so we may stop it). */
    private Process ownedProcess;

    private AiSearchService() {}

    public static AiSearchService getInstance() { return INSTANCE; }

    // ── Result type ───────────────────────────────────────────────────────────

    /**
     * One search/similar hit. Since AI Image Triage 1.1 the CLIP index holds one
     * vector per VIDEO FRAME; results are deduplicated per file (best frame wins)
     * and carry the matched frame: images have {@code frameIdx=0, timestampSeconds=0}.
     */
    public record Hit(long fileId, double score, int frameIdx, double timestampSeconds) {}

    /** Thrown by {@link #search} when the service has CLIP disabled (HTTP 503). */
    public static final class ClipDisabledException extends IOException {
        public ClipDisabledException(String msg) { super(msg); }
    }

    /**
     * Thrown by {@link #search}/{@link #similar} when the CLIP index was built with
     * an older ID scheme than the running service (HTTP 409, e.g. v1 file ids vs
     * packed_frame_v2). The message carries the service's own instruction — delete
     * the index files and re-run ingest — so show it to the user verbatim.
     */
    public static final class IndexOutdatedException extends IOException {
        public IndexOutdatedException(String msg) { super(msg); }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public boolean isHealthy() {
        try {
            HttpURLConnection c = open(BASE + "/health", "GET", HEALTH_TIMEOUT_MS);
            try { return c.getResponseCode() == 200; }
            finally { c.disconnect(); }
        } catch (IOException e) { return false; }
    }

    /**
     * Ensures the service is running: no-op if already healthy (e.g. left by
     * ingest), otherwise launches it and waits until healthy. Only the launched
     * process is tracked for later shutdown.
     *
     * @throws IOException if the service dir/venv is missing or startup times out
     */
    public synchronized void ensureRunning() throws IOException {
        if (isHealthy()) return;

        File serviceDir = findServiceDir();
        ProcessBuilder pb = buildLaunchCommand(serviceDir, PORT);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(logFile()));
        logger.log(Level.INFO, "EIG launching AI service: {0}", pb.command());
        ownedProcess = pb.start();

        for (int i = 0; i < STARTUP_HEALTH_RETRIES; i++) {
            if (isHealthy()) {
                logger.log(Level.INFO, "AI service healthy on port {0}", PORT);
                return;
            }
            try { Thread.sleep(STARTUP_RETRY_DELAY_MS); }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while starting AI service", ie);
            }
        }
        throw new IOException("AI service did not become healthy within "
                + (STARTUP_HEALTH_RETRIES * STARTUP_RETRY_DELAY_MS / 1000) + "s");
    }

    /** Stops the service ONLY if this instance launched it (never one owned by ingest). */
    public synchronized void stopIfOwned() {
        if (ownedProcess == null) return;
        try {
            HttpURLConnection c = open(BASE + "/shutdown", "POST", HEALTH_TIMEOUT_MS);
            c.getResponseCode(); c.disconnect();
        } catch (IOException e) {
            logger.log(Level.FINE, "Graceful /shutdown failed; destroying process", e);
        }
        ownedProcess.destroy();
        ownedProcess = null;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Semantic text search. Returns hits ordered by descending score.
     * @throws ClipDisabledException if the service returns 503 (CLIP not enabled)
     */
    public List<Hit> search(String query, String indexDir, int topN) throws IOException {
        String url = BASE + "/search?query=" + enc(query)
                + "&index_dir=" + enc(indexDir) + "&top_n=" + topN;
        HttpURLConnection c = open(url, "GET", SEARCH_TIMEOUT_MS);
        try {
            int code = c.getResponseCode();
            checkErrorCode(code, c, "/search");
            return parseHits(readBody(c));
        } finally { c.disconnect(); }
    }

    /** Visual-similarity lookup by file obj_id. Does not require CLIP in memory. */
    public List<Hit> similar(long fileId, String indexDir, int topN) throws IOException {
        String url = BASE + "/similar?file_id=" + fileId
                + "&index_dir=" + enc(indexDir) + "&top_n=" + topN;
        HttpURLConnection c = open(url, "GET", SEARCH_TIMEOUT_MS);
        try {
            int code = c.getResponseCode();
            checkErrorCode(code, c, "/similar");
            return parseHits(readBody(c));
        } finally { c.disconnect(); }
    }

    /**
     * Maps the service's error contract, PRESERVING the body's {@code detail}
     * (it carries actionable instructions — e.g. the 409 message tells the user to
     * delete the index and re-run ingest; swallowing it leaves them with a bare
     * "HTTP 409").
     */
    private static void checkErrorCode(int code, HttpURLConnection c, String endpoint)
            throws IOException {
        if (code == 200) return;
        String detail = readErrorDetail(c);
        switch (code) {
            case 503 -> throw new ClipDisabledException(detail != null ? detail
                    : "Search needs CLIP enabled in the AI service config.");
            case 409 -> throw new IndexOutdatedException(detail != null ? detail
                    : "The CLIP index was built with an older/incompatible scheme — "
                    + "delete clip_embeddings.faiss and meta.json, then re-run ingest.");
            default  -> throw new IOException(endpoint + " returned HTTP " + code
                    + (detail != null ? ": " + detail : ""));
        }
    }

    /** Reads FastAPI's {"detail": "..."} error body (best effort, may be null). */
    private static String readErrorDetail(HttpURLConnection c) {
        try {
            java.io.InputStream es = c.getErrorStream();
            if (es == null) return null;
            String body = new String(es.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> root = MiniJson.parseObject(body);
            Object d = root.get("detail");
            return d != null ? d.toString() : null;
        } catch (Exception e) { return null; }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<Hit> parseHits(String json) {
        Map<String, Object> root = MiniJson.parseObject(json);
        Object resultsObj = root.get("results");
        List<Hit> hits = new ArrayList<>();
        if (resultsObj instanceof List<?> results) {
            for (Object o : results) {
                if (o instanceof Map<?, ?> m) {
                    Object fid = m.get("file_id");
                    Object sc  = m.get("score");
                    if (fid instanceof Number n) {
                        double score = (sc instanceof Number sn) ? sn.doubleValue() : 0.0;
                        Object fi = m.get("frame_idx");
                        Object ts = m.get("timestamp_seconds");
                        hits.add(new Hit(n.longValue(), score,
                                (fi instanceof Number fn) ? fn.intValue() : 0,
                                (ts instanceof Number tn) ? tn.doubleValue() : 0.0));
                    }
                }
            }
        }
        return hits;
    }

    private static HttpURLConnection open(String url, String method, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(Math.min(timeoutMs, 5000));
        c.setReadTimeout(timeoutMs);
        return c;
    }

    private static String readBody(HttpURLConnection c) throws IOException {
        return new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ── Service location / launch (same contract as the ingest module) ─────────

    // Auto-discovery matching the AI Image Triage ingest module's ServiceLocator:
    // the portable installer drops the service in %LOCALAPPDATA%/%ProgramData%,
    // so AIT_SERVICE_DIR is only a dev override, not a requirement. A directory
    // counts as the service only if it contains app/main.py.
    private static File findServiceDir() throws IOException {
        java.util.List<File> candidates = new ArrayList<>();
        String configured = System.getenv(SERVICE_DIR_ENV);
        if (configured != null && !configured.isBlank()) {
            candidates.add(new File(configured));
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            candidates.add(new File(localAppData, "AIImageTriage\\service"));
        }
        String programData = System.getenv("ProgramData");
        if (programData != null && !programData.isBlank()) {
            candidates.add(new File(programData, "AIImageTriage\\service"));
        }
        for (File dir : candidates) {
            if (dir.isDirectory() && new File(dir, "app/main.py").isFile()) {
                return dir;
            }
        }
        if (configured != null && !configured.isBlank()) {
            throw new IOException("AI search unavailable: " + SERVICE_DIR_ENV + " ('" + configured
                    + "') does not contain the service (no app/main.py).");
        }
        throw new IOException(
                "AI search unavailable: AI Image Triage service not found. Install it "
                + "(%LOCALAPPDATA%\\AIImageTriage\\service) or set " + SERVICE_DIR_ENV + " for a dev checkout.");
    }

    // Finds the interpreter in either layout: portable bundle (python/python.exe)
    // or a dev virtualenv (.venv). Mirrors ServiceLocator.findVenvPython.
    private static ProcessBuilder buildLaunchCommand(File serviceDir, int port) throws IOException {
        File[] pythons = {
            new File(serviceDir, "python/python.exe"),        // portable bundle (Windows)
            new File(serviceDir, ".venv/Scripts/python.exe"),  // dev venv (Windows)
            new File(serviceDir, ".venv/bin/python"),          // dev venv (Linux/macOS)
        };
        File python = null;
        for (File p : pythons) {
            if (p.isFile()) { python = p; break; }
        }
        if (python == null) {
            throw new IOException("AI search: no Python interpreter in " + serviceDir
                    + " (neither portable 'python/' nor '.venv/').");
        }
        return new ProcessBuilder(
                python.getAbsolutePath(),
                "-m", "uvicorn", "app.main:app",
                "--host", "127.0.0.1",
                "--port", String.valueOf(port)
        ).directory(serviceDir);
    }

    private static File logFile() {
        return new File(System.getProperty("java.io.tmpdir"), "ai-image-triage-service.log");
    }
}
