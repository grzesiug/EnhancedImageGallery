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
 * Thin client + lifecycle manager for the <b>AI Text Triage</b> Python service
 * (semantic DOCUMENT search over a BGE-M3 index), used by Enhanced Evidence Gallery
 * for {@code /search} and {@code /similar} on port 8757.
 *
 * <p>This is the text-modality twin of {@link AiSearchService} (which talks to the
 * AI Image Triage CLIP service on 8756). The two indexes live in <em>different</em>
 * embedding spaces (CLIP image↔text vs. BGE-M3 text↔text) with non-comparable
 * score scales, so a text query is routed here only for documents — never merged
 * with CLIP results by raw score. See the gallery's search dispatch for routing.
 *
 * <p>Design goals are identical to {@link AiSearchService}:
 * <ul>
 *   <li><b>Zero impact when unused.</b> Nothing starts unless the user triggers a
 *       document text search. Auto-discovered under %LOCALAPPDATA%/%ProgramData%;
 *       {@code AITT_SERVICE_DIR} is only a dev override.</li>
 *   <li><b>Own-process only.</b> Started lazily if not already running (e.g. left
 *       by a Text-Triage ingest); {@link #stopIfOwned()} destroys only our process.</li>
 *   <li><b>Classic HTTP + MiniJson.</b> {@link HttpURLConnection}, never
 *       {@code java.net.http.HttpClient} (NIO deadlock under Autopsy's security
 *       manager); no external JSON jars (NetBeans classloader deadlock).</li>
 * </ul>
 */
public final class AiTextSearchService {

    private static final Logger logger = Logger.getLogger(AiTextSearchService.class.getName());
    private static final AiTextSearchService INSTANCE = new AiTextSearchService();

    private static final String SERVICE_DIR_ENV = "AITT_SERVICE_DIR";
    private static final int    PORT            = 8757;
    private static final String BASE            = "http://127.0.0.1:" + PORT;
    private static final int    HEALTH_TIMEOUT_MS       = 1000;
    private static final int    SEARCH_TIMEOUT_MS       = 30000; // first /search loads BGE-M3
    private static final int    STARTUP_HEALTH_RETRIES  = 40;
    private static final long   STARTUP_RETRY_DELAY_MS  = 500;

    /** Non-null only if THIS process launched the service (so we may stop it). */
    private Process ownedProcess;

    private AiTextSearchService() {}

    public static AiTextSearchService getInstance() { return INSTANCE; }

    // ── Result type ───────────────────────────────────────────────────────────

    /**
     * One document search/similar hit: Autopsy obj_id, best matching chunk index,
     * cosine-similarity score, and a short snippet of the matched text (may be
     * empty — always handle defensively).
     */
    public record TextHit(long fileId, int chunkIdx, double score, String snippet) {}

    /** Thrown by {@link #search} when the embedder is a stub / has no weights (HTTP 503). */
    public static final class EmbedderUnavailableException extends IOException {
        public EmbedderUnavailableException(String msg) { super(msg); }
    }

    /**
     * Thrown by {@link #search}/{@link #similar} when the index was built with a
     * different embedding model than the running service (HTTP 409). The case must
     * be re-ingested with the current Text-Triage model before search will work.
     */
    public static final class IndexModelMismatchException extends IOException {
        public IndexModelMismatchException(String msg) { super(msg); }
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
     * Ensures the service is running: no-op if already healthy (e.g. left by a
     * Text-Triage ingest), otherwise launches it and waits until healthy. Only the
     * launched process is tracked for later shutdown.
     *
     * @throws IOException if the service dir/venv is missing or startup times out
     */
    public synchronized void ensureRunning() throws IOException {
        if (isHealthy()) return;

        File serviceDir = findServiceDir();
        ProcessBuilder pb = buildLaunchCommand(serviceDir, PORT);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(logFile()));
        logger.log(Level.INFO, "EIG launching AI text service: {0}", pb.command());
        ownedProcess = pb.start();

        for (int i = 0; i < STARTUP_HEALTH_RETRIES; i++) {
            if (isHealthy()) {
                logger.log(Level.INFO, "AI text service healthy on port {0}", PORT);
                return;
            }
            try { Thread.sleep(STARTUP_RETRY_DELAY_MS); }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while starting AI text service", ie);
            }
        }
        throw new IOException("AI text service did not become healthy within "
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
     * Semantic document text search. Returns hits ordered by descending score
     * (top-N files, best chunk per file). A missing/empty index returns an empty
     * list (HTTP 200 with results:[]), not an error.
     *
     * @throws EmbedderUnavailableException if the service returns 503 (no weights)
     * @throws IndexModelMismatchException  if the service returns 409 (re-ingest needed)
     */
    public List<TextHit> search(String query, String indexDir, int topN) throws IOException {
        String url = BASE + "/search?query=" + enc(query)
                + "&index_dir=" + enc(indexDir) + "&top_n=" + topN;
        HttpURLConnection c = open(url, "GET", SEARCH_TIMEOUT_MS);
        try {
            int code = c.getResponseCode();
            checkErrorCode(code, c);
            if (code != 200) throw new IOException("/search returned HTTP " + code);
            return parseHits(readBody(c));
        } finally { c.disconnect(); }
    }

    /** Similar-document lookup by file obj_id (excludes the file itself). */
    public List<TextHit> similar(long fileId, String indexDir, int topN) throws IOException {
        String url = BASE + "/similar?file_id=" + fileId
                + "&index_dir=" + enc(indexDir) + "&top_n=" + topN;
        HttpURLConnection c = open(url, "GET", SEARCH_TIMEOUT_MS);
        try {
            int code = c.getResponseCode();
            checkErrorCode(code, c);
            if (code != 200) throw new IOException("/similar returned HTTP " + code);
            return parseHits(readBody(c));
        } finally { c.disconnect(); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Maps the Text-Triage error contract (503 stub, 409 model mismatch, 422 empty). */
    private static void checkErrorCode(int code, HttpURLConnection c) throws IOException {
        if (code == 200) return;
        String detail = readErrorDetail(c);
        switch (code) {
            case 503 -> throw new EmbedderUnavailableException(detail != null ? detail
                    : "The AI Text Triage embedder has no model weights (stub).");
            case 409 -> throw new IndexModelMismatchException(detail != null ? detail
                    : "The text index was built with a different model — re-ingest is required.");
            case 422 -> throw new IOException(detail != null ? detail : "Empty query.");
            default  -> { /* fall through to generic handling in caller */ }
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

    @SuppressWarnings("unchecked")
    private static List<TextHit> parseHits(String json) {
        Map<String, Object> root = MiniJson.parseObject(json);
        Object resultsObj = root.get("results");
        List<TextHit> hits = new ArrayList<>();
        if (resultsObj instanceof List<?> results) {
            for (Object o : results) {
                if (o instanceof Map<?, ?> m) {
                    Object fid = m.get("file_id");
                    if (fid instanceof Number n) {
                        Object ci = m.get("chunk_idx");
                        Object sc = m.get("score");
                        Object sn = m.get("snippet");
                        int    chunk   = (ci instanceof Number cn) ? cn.intValue() : 0;
                        double score   = (sc instanceof Number sn2) ? sn2.doubleValue() : 0.0;
                        String snippet = (sn != null) ? sn.toString() : "";
                        hits.add(new TextHit(n.longValue(), chunk, score, snippet));
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

    // ── Service location / launch (same contract as the Text-Triage ingest module) ──

    // Auto-discovery matching the AI Text Triage ingest module's ServiceLocator:
    // the portable installer drops the service in %LOCALAPPDATA%/%ProgramData%,
    // so AITT_SERVICE_DIR is only a dev override. A directory counts as the
    // service only if it contains app/main.py.
    private static File findServiceDir() throws IOException {
        java.util.List<File> candidates = new ArrayList<>();
        String configured = System.getenv(SERVICE_DIR_ENV);
        if (configured != null && !configured.isBlank()) {
            candidates.add(new File(configured));
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            candidates.add(new File(localAppData, "AITextTriage\\service"));
        }
        String programData = System.getenv("ProgramData");
        if (programData != null && !programData.isBlank()) {
            candidates.add(new File(programData, "AITextTriage\\service"));
        }
        for (File dir : candidates) {
            if (dir.isDirectory() && new File(dir, "app/main.py").isFile()) {
                return dir;
            }
        }
        if (configured != null && !configured.isBlank()) {
            throw new IOException("Document search unavailable: " + SERVICE_DIR_ENV + " ('" + configured
                    + "') does not contain the service (no app/main.py).");
        }
        throw new IOException(
                "Document search unavailable: AI Text Triage service not found. Install it "
                + "(%LOCALAPPDATA%\\AITextTriage\\service) or set " + SERVICE_DIR_ENV + " for a dev checkout.");
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
            throw new IOException("Document search: no Python interpreter in " + serviceDir
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
        return new File(System.getProperty("java.io.tmpdir"), "ai-text-triage-service.log");
    }
}
