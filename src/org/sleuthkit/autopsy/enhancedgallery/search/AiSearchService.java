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
 * used by Enhanced Image Gallery for semantic search ({@code /search}) and
 * visual-similarity lookup ({@code /similar}).
 *
 * <p>Design goals:
 * <ul>
 *   <li><b>Zero impact when unused.</b> Nothing here runs unless the user
 *       explicitly triggers an AI search. If {@code AIT_SERVICE_DIR} is unset,
 *       the venv/model is missing, or no FAISS index exists, the calls fail
 *       with a clear message and the rest of the gallery is unaffected.</li>
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

    /** One search/similar hit: Autopsy obj_id + cosine-similarity score. */
    public record Hit(long fileId, double score) {}

    /** Thrown by {@link #search} when the service has CLIP disabled (HTTP 503). */
    public static final class ClipDisabledException extends IOException {
        public ClipDisabledException(String msg) { super(msg); }
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
            if (code == 503) {
                throw new ClipDisabledException(
                        "Text search needs CLIP enabled in the AI service config.");
            }
            if (code != 200) throw new IOException("/search returned HTTP " + code);
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
            if (code != 200) throw new IOException("/similar returned HTTP " + code);
            return parseHits(readBody(c));
        } finally { c.disconnect(); }
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
                        hits.add(new Hit(n.longValue(), score));
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

    private static File findServiceDir() throws IOException {
        String configured = System.getenv(SERVICE_DIR_ENV);
        if (configured == null || configured.isBlank()) {
            throw new IOException(
                    "AI search unavailable: environment variable " + SERVICE_DIR_ENV
                    + " is not set. Point it at the AIImageTriage 'service' directory "
                    + "(the one containing .venv) to enable semantic search.");
        }
        File dir = new File(configured);
        if (!dir.isDirectory()) {
            throw new IOException(SERVICE_DIR_ENV + " does not point to a directory: " + configured);
        }
        return dir;
    }

    private static ProcessBuilder buildLaunchCommand(File serviceDir, int port) throws IOException {
        File venvPython = new File(serviceDir, ".venv/Scripts/python.exe");
        if (!venvPython.isFile()) venvPython = new File(serviceDir, ".venv/bin/python");
        if (!venvPython.isFile()) {
            throw new IOException("AI search: no Python interpreter in " + serviceDir
                    + "/.venv — create the venv and install requirements first.");
        }
        return new ProcessBuilder(
                venvPython.getAbsolutePath(),
                "-m", "uvicorn", "app.main:app",
                "--host", "127.0.0.1",
                "--port", String.valueOf(port)
        ).directory(serviceDir);
    }

    private static File logFile() {
        return new File(System.getProperty("java.io.tmpdir"), "ai-image-triage-service.log");
    }
}
