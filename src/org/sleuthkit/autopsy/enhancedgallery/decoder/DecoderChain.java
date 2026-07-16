package org.sleuthkit.autopsy.enhancedgallery.decoder;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.sleuthkit.autopsy.enhancedgallery.datamodel.MediaFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Decodes AbstractFile → thumbnail BufferedImage.
 *
 * Priority chain per type:
 *  IMAGE:  ImageIO → ImageMagick → Batik (SVG) → dcraw (RAW)
 *  VIDEO:  FFmpeg frame → video placeholder
 *  AUDIO:  waveform placeholder (no external tool needed)
 *
 * External tools are located once at startup by ToolFinder.
 */
public class DecoderChain {

    private static final Logger logger =
            Logger.getLogger(DecoderChain.class.getName());

    public static final int THUMB_SIZE = 256;
    private static final String TEMP_DIR = "autopsy_enhanced_gallery";

    /** Lazy Batik classloader — built from Autopsy's JAR files. */
    private static volatile ClassLoader batikLoader;
    private static volatile boolean batikChecked = false;

    // ── Entry point ──────────────────────────────────────────────────────────

    /** Max file size to attempt thumbnail decoding (50 MB). Larger files → "no preview". */
    private static final long MAX_DECODE_SIZE = 50_000_000L;

    /** Decoder timeout — read from settings, default 60s. */
    private static int getTimeoutSec() {
        return org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings
                .getDecodeTimeoutSeconds();
    }

    private static final java.util.concurrent.ExecutorService decodeHelper =
            java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "EG-DecodeHelper");
                t.setDaemon(true);
                return t;
            });

    public static BufferedImage decodeThumbnail(MediaFile mf) {
        AbstractFile f = mf.getAbstractFile();
        long objId    = f.getId();
        long fileSize = f.getSize();
        // Audio placeholder needs no file extraction — skip size limit and cache checks
        if (mf.getMediaType() == MediaFile.MediaType.AUDIO) {
            return renderAudioPlaceholder(f.getName());
        }
        // Conversation cards (message threads) get a chat-bubble tile — no file
        // content is involved at all (the "file" is the thread's source db/eml).
        if (mf.isThread()) {
            return renderConversationPlaceholder(mf.getDocApp(), mf.getDocMsgCount());
        }
        // Documents show an icon+extension placeholder, never a rendered preview —
        // no extraction, no external tool, no size limit (Phase 2 may add real text thumbs).
        if (mf.getMediaType() == MediaFile.MediaType.DOCUMENT) {
            return renderDocumentPlaceholder(mf.getExtension());
        }

        // ── 0. Skip very large non-audio files ───────────────────────────────
        if (fileSize > MAX_DECODE_SIZE) {
            logger.log(Level.FINE, "Skipping large file: {0} ({1} MB)",
                    new Object[]{f.getName(), fileSize / 1_000_000});
            mf.markThumbnailFailed();
            return null;
        }

        // ── 1. Check persistent cache ────────────────────────────────────────
        ThumbnailCache cache = ThumbnailCache.getInstance();
        BufferedImage cached = cache.load(objId, fileSize);
        if (cached != null) return cached;

        java.util.concurrent.Future<BufferedImage> future = decodeHelper.submit(() -> {
            try {
                Path extracted = extractToTemp(f);

                String ext  = mf.getExtension().toLowerCase();
                String mime = mf.getMimeType();

                return switch (mf.getMediaType()) {
                    case IMAGE    -> decodeImage(extracted, ext, mime);
                    case VIDEO    -> decodeVideoFrame(extracted);
                    case AUDIO    -> renderAudioPlaceholder(mf.getName());     // fallback (shouldn't reach)
                    case DOCUMENT -> renderDocumentPlaceholder(mf.getExtension()); // fallback (shouldn't reach)
                };
            } catch (Exception ex) {
                logger.log(Level.FINE, "Decode error for {0}: {1}",
                        new Object[]{f.getName(), ex.getMessage()});
                return null;
            }
        });

        try {
            BufferedImage img = future.get(getTimeoutSec(),
                    java.util.concurrent.TimeUnit.SECONDS);
            if (img != null) {
                BufferedImage thumb = scaleThumbnail(img, THUMB_SIZE);
                if (mf.getMediaType() != MediaFile.MediaType.AUDIO) {
                    cache.save(objId, fileSize, thumb);
                }
                return thumb;
            }
        } catch (java.util.concurrent.TimeoutException ex) {
            future.cancel(true);
            logger.log(Level.WARNING, "Decode timeout ({0}s): {1}",
                    new Object[]{getTimeoutSec(), f.getName()});
        } catch (Exception ex) {
            future.cancel(true);
            logger.log(Level.FINE, "Decode failed for {0}: {1}",
                    new Object[]{f.getName(), ex.getMessage()});
        }

        mf.markThumbnailFailed();
        return null;
    }

    // ── IMAGE ────────────────────────────────────────────────────────────────

    private static BufferedImage decodeImage(Path file, String ext, String mime)
            throws IOException {

        // Formats where Java ImageIO is fast and reliable
        boolean imageIoFormat = mime != null && (
                mime.startsWith("image/jpeg") || mime.startsWith("image/png")  ||
                mime.startsWith("image/gif")  || mime.startsWith("image/bmp")  ||
                mime.startsWith("image/tiff") || mime.startsWith("image/x-tiff"));

        // Formats where ImageMagick is faster than the pure-Java fallback (TwelveMonkeys WebP etc.)
        boolean preferMagick = "webp".equals(ext) || "image/webp".equals(mime)
                || "avif".equals(ext) || "image/avif".equals(mime)
                || "heic".equals(ext) || "heif".equals(ext)
                || "image/heic".equals(mime) || "image/heif".equals(mime);

        if (preferMagick) {
            // Go directly to ImageMagick — faster than pure-Java TwelveMonkeys for these formats
            BufferedImage img = tryImageMagick(file);
            if (img != null) return img;
            // Fallback to ImageIO (e.g. if ImageMagick not configured)
            img = tryImageIO(file);
            return img;
        }

        // 1. Java ImageIO — fast for JPEG/PNG/GIF/BMP/TIFF
        BufferedImage img = tryImageIO(file);
        if (img != null) return img;

        // 2. SVG via Batik (before ImageMagick — lightweight)
        if ("svg".equals(ext) || "svgz".equals(ext) || "image/svg+xml".equals(mime)) {
            img = tryBatik(file);
            if (img != null) return img;
        }

        // 3. RAW via dcraw (before ImageMagick — dedicated tool)
        if (ext.matches("cr2|cr3|nef|arw|dng|orf|rw2|raf|pef|srw|x3f") ||
                (mime != null && mime.startsWith("image/x-raw"))) {
            img = tryDcraw(file);
            if (img != null) return img;
        }

        // 4. ImageMagick — universal fallback (HEIC, AVIF, WebP, TIFF, PSD, …)
        //    Skip for formats ImageIO already handles (they would have succeeded above)
        if (!imageIoFormat) {
            img = tryImageMagick(file);
            if (img != null) return img;
        }

        // 5. ImageMagick last resort even for "ImageIO formats" (in case file is corrupt/unusual)
        if (imageIoFormat) {
            img = tryImageMagick(file);
        }

        return img;
    }

    // ── Decoder 1: Java ImageIO ──────────────────────────────────────────────

    private static BufferedImage tryImageIO(Path file) {
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            if (img != null) logger.log(Level.FINEST, "ImageIO OK: {0}", file.getFileName());
            return img;
        } catch (Exception ex) { return null; }
    }

    // ── Decoder 2: ImageMagick ───────────────────────────────────────────────

    private static BufferedImage tryImageMagick(Path file) {
        String magick = ToolFinder.magick();
        if (magick == null) return null;

        Path out = null;
        try {
            out = tempPath(file.getFileName().toString() + "_im.jpg");
            ProcessBuilder pb = new ProcessBuilder(
                    magick,
                    file.toAbsolutePath() + "[0]",
                    "-flatten", "-background", "white",
                    "-resize", THUMB_SIZE + "x" + THUMB_SIZE + ">",
                    "-quality", "85",
                    "jpg:" + out.toAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.getInputStream().transferTo(OutputStream.nullOutputStream());
            proc.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);

            boolean exists = Files.exists(out);
            long size = exists ? Files.size(out) : 0;
            if (exists && size > 0) {
                BufferedImage img = ImageIO.read(out.toFile());
                if (img != null) {
                    logger.log(Level.FINE, "ImageMagick OK: {0}", file.getFileName());
                    return img;
                }
            }
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        catch (Exception ex) {
            logger.log(Level.FINE, "ImageMagick error: {0}", ex.getMessage());
        } finally {
            if (out != null) try { Files.deleteIfExists(out); } catch (Exception ignored) {}
        }
        return null;
    }

    // ── Decoder 3: Apache Batik (SVG, via URLClassLoader) ────────────────────

    private static BufferedImage tryBatik(Path svgFile) {
        ClassLoader cl = getBatikLoader();
        if (cl == null) return null;
        try {
            Class<?> tcClass  = cl.loadClass("org.apache.batik.transcoder.image.PNGTranscoder");
            Class<?> inClass  = cl.loadClass("org.apache.batik.transcoder.TranscoderInput");
            Class<?> outClass = cl.loadClass("org.apache.batik.transcoder.TranscoderOutput");
            Class<?> itClass  = cl.loadClass("org.apache.batik.transcoder.image.ImageTranscoder");

            Object tc = tcClass.getDeclaredConstructor().newInstance();
            Object kw = itClass.getField("KEY_WIDTH").get(null);
            Object kh = itClass.getField("KEY_HEIGHT").get(null);
            tcClass.getMethod("addTranscodingHint", kw.getClass(), Object.class)
                    .invoke(tc, kw, (float) THUMB_SIZE);
            tcClass.getMethod("addTranscodingHint", kh.getClass(), Object.class)
                    .invoke(tc, kh, (float) THUMB_SIZE);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // Prefer feeding sanitized SVG text (strips invalid width/height="auto",
            // which makes Batik throw and yield "no preview"). Fall back to the raw
            // file URI if reading fails, so normal SVGs behave exactly as before.
            String cleaned = readSvgSanitized(svgFile);
            Object input = (cleaned != null)
                    ? inClass.getConstructor(java.io.Reader.class)
                          .newInstance(new java.io.StringReader(cleaned))
                    : inClass.getConstructor(java.net.URI.class).newInstance(svgFile.toUri());
            Object output = outClass.getConstructor(OutputStream.class)
                    .newInstance((OutputStream) baos);
            tcClass.getMethod("transcode", inClass, outClass).invoke(tc, input, output);

            byte[] png = baos.toByteArray();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img != null) {
                logger.log(Level.FINE, "Batik OK: {0}", svgFile.getFileName());
                return img;
            }
        } catch (Exception ex) {
            logger.log(Level.FINE, "Batik failed: {0}", ex.getMessage());
        }
        return null;
    }

    /**
     * Reads an SVG (or gzipped .svgz) as text and strips {@code width="auto"} /
     * {@code height="auto"} attributes on the root element. "auto" is not a valid
     * SVG length, so Batik throws on it and produces no preview — removing the
     * attributes lets Batik size the image from the viewBox plus our width/height
     * transcoding hints. Returns null if the file can't be read (caller then falls
     * back to the raw file URI). Only the first ~1&nbsp;MB is inspected — enough for
     * any real SVG header, and avoids slurping a pathologically large file.
     */
    private static String readSvgSanitized(Path svgFile) {
        try {
            byte[] raw = Files.readAllBytes(svgFile);
            // Gunzip .svgz (gzip magic 1F 8B).
            if (raw.length > 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B) {
                try (var gis = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(raw))) {
                    raw = gis.readAllBytes();
                }
            }
            if (raw.length == 0 || raw.length > 8_000_000) return null; // too big → use URI path
            String svg = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            // Remove width/height="auto" (single or double quoted). Case-insensitive.
            String cleaned = svg.replaceAll("(?i)\\s(?:width|height)\\s*=\\s*([\"'])\\s*auto\\s*\\1", "");
            return cleaned.equals(svg) ? svg : cleaned; // return original if nothing changed
        } catch (Exception ex) {
            logger.log(Level.FINE, "SVG sanitize read failed: {0}", ex.getMessage());
            return null;
        }
    }

    /** Builds a URLClassLoader pointing at Autopsy's Batik JARs. Called once. */
    private static synchronized ClassLoader getBatikLoader() {
        if (batikChecked) return batikLoader;
        batikChecked = true;
        try {
            // Find Autopsy's platform/modules/ext directory
            String[] autopsyRoots = {
                "C:\\Program Files\\Autopsy-4.23.1\\platform\\modules\\ext",
                "C:\\Program Files\\Autopsy-4.23.1\\autopsy\\modules\\ext",
            };
            java.util.List<URL> urls = new java.util.ArrayList<>();
            for (String root : autopsyRoots) {
                Path dir = Path.of(root);
                if (!Files.isDirectory(dir)) continue;
                try (var stream = Files.list(dir)) {
                    stream.filter(p -> {
                        String fn = p.getFileName().toString().toLowerCase();
                        return fn.startsWith("batik-") && fn.endsWith(".jar");
                    }).forEach(p -> {
                        try { urls.add(p.toUri().toURL()); } catch (Exception ignored) {}
                    });
                }
            }
            if (urls.isEmpty()) {
                logger.log(Level.INFO, "Batik JARs not found — SVG preview unavailable");
                return null;
            }
            logger.log(Level.INFO, "Batik loader: {0} JARs", urls.size());
            // Parent = our classloader so XML/IO classes are visible
            batikLoader = new URLClassLoader(urls.toArray(new URL[0]),
                    DecoderChain.class.getClassLoader());
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to create Batik classloader", ex);
        }
        return batikLoader;
    }

    // ── Decoder 4: dcraw (RAW) ───────────────────────────────────────────────

    private static BufferedImage tryDcraw(Path rawFile) {
        String dcraw = ToolFinder.dcraw();
        if (dcraw == null) return null;

        Path outPpm = null;
        try {
            outPpm = tempPath(rawFile.getFileName() + ".ppm");
            ProcessBuilder pb = new ProcessBuilder(dcraw, "-c", "-h",
                    rawFile.toAbsolutePath().toString());
            pb.redirectOutput(outPpm.toFile());
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process proc = pb.start();
            proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (Files.exists(outPpm) && Files.size(outPpm) > 0) {
                BufferedImage img = ImageIO.read(outPpm.toFile());
                if (img != null) {
                    logger.log(Level.FINE, "dcraw OK: {0}", rawFile.getFileName());
                    return img;
                }
            }
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        catch (Exception ex) {
            logger.log(Level.FINE, "dcraw error: {0}", ex.getMessage());
        } finally {
            if (outPpm != null) try { Files.deleteIfExists(outPpm); } catch (Exception ignored) {}
        }
        return null;
    }

    // ── VIDEO ────────────────────────────────────────────────────────────────

    private static BufferedImage decodeVideoFrame(Path videoFile) {
        if (!ToolFinder.hasFfmpeg()) return renderVideoPlaceholder();

        String filePath = videoFile.toAbsolutePath().toString();

        // Strategy order:
        // 1. Fast seek to 3s (-ss BEFORE -i): near-instant for MP4/H.264, sometimes wrong frame
        // 2. Fast seek to 1s: for videos shorter than 3s
        // 3. Accurate seek to 3s (-ss AFTER -i): slow but works for VP9/AV1/WebM/AVI
        // 4. First frame accurate: always works if file is valid
        String[][] seekConfigs = {
            {"-ss", "3", "-i", filePath},           // fast seek 3s
            {"-ss", "1", "-i", filePath},           // fast seek 1s
            {"-i",  filePath, "-ss", "3"},           // accurate seek 3s
            {"-i",  filePath},                       // first frame
        };

        BufferedImage bestSoFar = null; // keep darkest-but-valid frame as fallback

        for (String[] seekArgs : seekConfigs) {
            Path out = null;
            try {
                out = tempPath(videoFile.getFileName() + "_v.jpg");
                java.util.List<String> cmd2 = new java.util.ArrayList<>();
                cmd2.add(ToolFinder.ffmpeg());
                for (String a : seekArgs) cmd2.add(a);
                cmd2.addAll(java.util.List.of(
                        "-vframes", "1",
                        "-vf", "scale=" + THUMB_SIZE + ":-1",
                        "-q:v", "3", "-y", out.toAbsolutePath().toString()));
                ProcessBuilder pb = new ProcessBuilder(cmd2);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                proc.getInputStream().transferTo(OutputStream.nullOutputStream());
                proc.waitFor(25, java.util.concurrent.TimeUnit.SECONDS);

                if (Files.exists(out) && Files.size(out) > 100) {
                    BufferedImage img = ImageIO.read(out.toFile());
                    if (img != null) {
                        if (!isBlackFrame(img)) {
                            logger.log(Level.FINE, "FFmpeg OK: {0}", videoFile.getFileName());
                            return img; // good frame — use it
                        }
                        if (bestSoFar == null) bestSoFar = img; // dark but valid — keep as fallback
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); break;
            } catch (Exception ex) {
                logger.log(Level.FINE, "FFmpeg strategy failed: {0}", ex.getMessage());
            } finally {
                final Path f = out;
                if (f != null) try { Files.deleteIfExists(f); } catch (Exception ignored) {}
            }
        }

        // All frames were dark (night recording) — return the dark frame rather than placeholder
        if (bestSoFar != null) {
            logger.log(Level.FINE, "FFmpeg: dark frame used for {0}", videoFile.getFileName());
            return bestSoFar;
        }

        // No frame extracted at all — likely audio-only container (e.g. M4A disguised as MP4)
        logger.log(Level.FINE, "FFmpeg: no frame for {0} — rendering audio placeholder", videoFile.getFileName());
        return renderAudioPlaceholder(videoFile.getFileName().toString());
    }

    /** Returns true if frame is nearly all black — useless thumbnail, try next seek point. */
    private static boolean isBlackFrame(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        if (w == 0 || h == 0) return true;
        long brightness = 0;
        int step = Math.max(1, (w * h) / 200); // sample ~200 pixels
        int samples = 0;
        for (int i = 0; i < w * h; i += step) {
            int rgb = img.getRGB(i % w, i / w);
            brightness += ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
            samples++;
        }
        return samples > 0 && (brightness / (3.0 * samples)) < 10.0;
    }

    // ── Placeholders ─────────────────────────────────────────────────────────

    private static BufferedImage renderVideoPlaceholder() {
        BufferedImage img = new BufferedImage(THUMB_SIZE, THUMB_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(18, 18, 32));
        g.fillRect(0, 0, THUMB_SIZE, THUMB_SIZE);
        // Play button circle
        g.setColor(new java.awt.Color(80, 80, 100));
        int r = THUMB_SIZE / 3;
        int cx = THUMB_SIZE / 2, cy = THUMB_SIZE / 2;
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        // Triangle
        g.setColor(new java.awt.Color(200, 200, 220));
        int tr = r / 2;
        int[] xs = {cx - tr/2, cx + tr, cx - tr/2};
        int[] ys = {cy - tr,   cy,      cy + tr};
        g.fillPolygon(xs, ys, 3);
        g.dispose();
        return img;
    }

    private static BufferedImage renderAudioPlaceholder(String name) {
        BufferedImage img = new BufferedImage(THUMB_SIZE, THUMB_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(12, 42, 28));
        g.fillRect(0, 0, THUMB_SIZE, THUMB_SIZE);
        java.util.Random rng = new java.util.Random(name.hashCode());
        int bars = 24;
        int gap  = 2;
        int barW = Math.max(2, (THUMB_SIZE - gap * (bars + 1)) / bars);
        int startX = (THUMB_SIZE - (bars * (barW + gap))) / 2;
        for (int i = 0; i < bars; i++) {
            int h = 12 + rng.nextInt(THUMB_SIZE / 2 - 12);
            int x = startX + i * (barW + gap);
            int green = 120 + rng.nextInt(100);
            g.setColor(new java.awt.Color(20, green, 70));
            g.fillRoundRect(x, THUMB_SIZE/2 - h/2, barW, h, 2, 2);
        }
        g.dispose();
        return img;
    }

    /**
     * A document tile: a sheet-of-paper glyph with a folded corner and the file's
     * extension stamped across it (e.g. "PDF", "DOCX"). No preview is rendered —
     * documents have no visual thumbnail. Modelled on the audio placeholder.
     */
    private static BufferedImage renderDocumentPlaceholder(String ext) {
        BufferedImage img = new BufferedImage(THUMB_SIZE, THUMB_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Slate background
        g.setColor(new java.awt.Color(30, 34, 44));
        g.fillRect(0, 0, THUMB_SIZE, THUMB_SIZE);

        // Paper sheet
        int margin = THUMB_SIZE / 4;
        int pw = THUMB_SIZE - 2 * margin;
        int ph = (int) (pw * 1.3);
        int px = (THUMB_SIZE - pw) / 2;
        int py = (THUMB_SIZE - ph) / 2;
        int fold = pw / 3;

        java.awt.Polygon sheet = new java.awt.Polygon();
        sheet.addPoint(px, py);
        sheet.addPoint(px + pw - fold, py);
        sheet.addPoint(px + pw, py + fold);
        sheet.addPoint(px + pw, py + ph);
        sheet.addPoint(px, py + ph);
        g.setColor(new java.awt.Color(226, 230, 238));
        g.fillPolygon(sheet);

        // Folded corner
        g.setColor(new java.awt.Color(186, 192, 205));
        java.awt.Polygon corner = new java.awt.Polygon();
        corner.addPoint(px + pw - fold, py);
        corner.addPoint(px + pw, py + fold);
        corner.addPoint(px + pw - fold, py + fold);
        g.fillPolygon(corner);

        // Extension label
        String label = (ext == null || ext.isBlank()) ? "TXT" : ext.toUpperCase();
        if (label.length() > 5) label = label.substring(0, 5);
        g.setColor(new java.awt.Color(70, 80, 96));
        int fontSize = label.length() <= 3 ? 34 : (label.length() == 4 ? 28 : 24);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, fontSize));
        java.awt.FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(label);
        g.drawString(label, px + (pw - tw) / 2, py + ph / 2 + fm.getAscent() / 2);

        g.dispose();
        return img;
    }

    /**
     * A conversation-card tile for a message thread: two chat bubbles on a slate
     * background, the app name (SMS/WHATSAPP/EMAIL) and the message count.
     */
    private static BufferedImage renderConversationPlaceholder(String app, int msgCount) {
        BufferedImage img = new BufferedImage(THUMB_SIZE, THUMB_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new java.awt.Color(24, 32, 44));
        g.fillRect(0, 0, THUMB_SIZE, THUMB_SIZE);

        // Incoming bubble (left, grey) and outgoing bubble (right, blue)
        int bw = THUMB_SIZE * 5 / 9, bh = THUMB_SIZE / 5, r = 14;
        int lx = THUMB_SIZE / 9,      ly = THUMB_SIZE / 4;
        int rx = THUMB_SIZE - bw - THUMB_SIZE / 9, ry = ly + bh + THUMB_SIZE / 12;
        g.setColor(new java.awt.Color(78, 88, 104));
        g.fillRoundRect(lx, ly, bw, bh, r, r);
        g.fillPolygon(new int[]{lx + 8, lx + 22, lx + 8},
                      new int[]{ly + bh, ly + bh, ly + bh + 9}, 3);
        g.setColor(new java.awt.Color(37, 99, 235));
        g.fillRoundRect(rx, ry, bw, bh, r, r);
        g.fillPolygon(new int[]{rx + bw - 8, rx + bw - 22, rx + bw - 8},
                      new int[]{ry + bh, ry + bh, ry + bh + 9}, 3);

        // App label + message count
        String label = (app == null || app.isBlank()) ? "MESSAGES" : app.toUpperCase();
        g.setColor(new java.awt.Color(203, 213, 225));
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 22));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(label, (THUMB_SIZE - fm.stringWidth(label)) / 2, THUMB_SIZE - 48);
        if (msgCount > 0) {
            String cnt = msgCount + (msgCount == 1 ? " message" : " messages");
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 16));
            fm = g.getFontMetrics();
            g.setColor(new java.awt.Color(148, 163, 184));
            g.drawString(cnt, (THUMB_SIZE - fm.stringWidth(cnt)) / 2, THUMB_SIZE - 24);
        }

        g.dispose();
        return img;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static BufferedImage scaleThumbnail(BufferedImage src, int size) {
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        if (w <= 0 || h <= 0) return null;
        double scale = Math.min((double) size / w, (double) size / h);
        if (scale >= 1.0) return src;
        int nw = Math.max(1, (int)(w * scale));
        int nh = Math.max(1, (int)(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    static Path extractToTemp(AbstractFile f) throws IOException, TskCoreException {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), TEMP_DIR);
        Files.createDirectories(dir);
        String safe = f.getId() + "_" + f.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path out = dir.resolve(safe);
        if (Files.exists(out) && Files.size(out) == f.getSize()) return out;
        byte[] buf = new byte[65536];
        long offset = 0, remaining = f.getSize();
        try (OutputStream os = Files.newOutputStream(out)) {
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int read   = f.read(buf, offset, toRead);
                if (read <= 0) break;
                os.write(buf, 0, read);
                offset    += read;
                remaining -= read;
            }
        }
        out.toFile().deleteOnExit();
        return out;
    }

    private static Path tempPath(String name) throws IOException {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), TEMP_DIR);
        Files.createDirectories(dir);
        return dir.resolve(name.replaceAll("[^a-zA-Z0-9._-]", "_"));
    }
}
