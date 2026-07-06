package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.enhancedgallery.datamodel.*;
import org.sleuthkit.autopsy.enhancedgallery.decoder.DecoderChain;

/**
 * Main window (TopComponent) of the Enhanced Image Gallery.
 *
 * Layout (matches the final mockup):
 *
 *  â”Śâ”€ ctx-bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
 *  â”‚  /DCIM/Camera  [5 unseen] [8 files] â•â•â•â• progress â•â•â•â• [Group: â–Ľ]   â”‚
 *  â”śâ”€ action-bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
 *  â”‚  [Bookmark] [Tag â–Ľ] [All][âś•]  |  [Open] [â–Ľ]  |  Image Video Audio  â”‚
 *  â”‚  Show: Unseen Seen Tagged GPS    Size â”€â”€â—Źâ”€â”€  100px                   â”‚
 *  â”śâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
 *  â”‚  Group      â”‚  Thumbnail grid                       â”‚  Properties    â”‚
 *  â”‚  sidebar    â”‚  (virtual, lazy-decoded)              â”‚  panel         â”‚
 *  â”‚             â”‚                                       â”‚                â”‚
 *  â”‚             â”śâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤                â”‚
 *  â”‚             â”‚  status bar           [â†“ First unseen]â”‚                â”‚
 *  â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
 *
 * Threading model:
 *   - MediaFileLoader runs on a background Executor (loads DB)
 *   - DecoderChain runs on a fixed thread pool (decodes thumbnails)
 *   - All Swing updates run on EDT via SwingUtilities.invokeLater
 */
@TopComponent.Description(
    preferredID = "EnhancedGalleryTopComponent",
    iconBase    = "org/sleuthkit/autopsy/enhancedgallery/resources/gallery.png",
    persistenceType = TopComponent.PERSISTENCE_NEVER
)
@TopComponent.Registration(
    mode     = "editor",
    openAtStartup = false
)
@NbBundle.Messages({
    "CTL_GalleryTopComponent=Enhanced Gallery",
    "HINT_GalleryTopComponent=Enhanced Image/Video/Audio Gallery"
})
public class EnhancedGalleryTopComponent extends TopComponent {

    private static final Logger logger =
            Logger.getLogger(EnhancedGalleryTopComponent.class.getName());

    // â”€â”€ Singleton â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static EnhancedGalleryTopComponent instance;
    public static synchronized EnhancedGalleryTopComponent findInstance() {
        if (instance == null) instance = new EnhancedGalleryTopComponent();
        return instance;
    }

    // â”€â”€ State â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private List<MediaFile> allFiles = java.util.Collections.synchronizedList(new ArrayList<>());
    private List<MediaFile> visible  = new ArrayList<>();
    private GpsCache                 gpsCache   = new GpsCache();
    private ReviewStateStore         stateStore;

    // Filter state
    // Default: show only Unseen and Tagged â€” Seen hidden (user can enable in ActionBar)
    private final Set<String> statusFilters = new HashSet<>(
            Set.of("unseen","tagged"));
    private final Set<String> typeFilters   = new HashSet<>(
            Set.of("image","video","audio"));
    private boolean geoOnly         = false;
    private int     thumbSize       = 110;
    private String  groupBy         = "path";
    private String  activeGroupKey     = null;   // null = all groups
    private Long    activeDataSourceId = null;   // null = all; value = Content.getId() of selected data source
    // ID-based data source map (populated at case load).
    // Keyed by ID, not name — two data sources CAN share the same display name
    // (e.g. the same .dd image added to the case twice), so name must never be
    // used as a lookup key for filtering.
    private final java.util.Map<Long, String>  dsIdToName = new java.util.LinkedHashMap<>();
    private boolean showBrokenOnly    = false;  // show only files with no thumbnail
    private volatile String searchText = null;  // case-insensitive filename substring filter
    // Semantic (AI Image Triage) search — null when inactive (normal gallery use).
    // semanticMatchIds: obj_ids returned by /search or /similar; semanticOrder:
    // same ids in relevance order so the grid can be ranked by score.
    private volatile java.util.Set<Long>  semanticMatchIds = null;
    private volatile java.util.List<Long> semanticOrder    = null;
    private volatile String semanticLabel = null; // e.g. 'osoba z dokumentem' or 'IMG_1234.jpg'

    // Selection
    private final Set<Integer>  selected  = new LinkedHashSet<>();
    private Integer             selFile   = null;

    // Background workers
    private final ExecutorService loaderPool =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "EG-Loader"); t.setDaemon(true); return t;});
    private final java.util.concurrent.ScheduledExecutorService timeoutPool =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "EG-Timeout"); t.setDaemon(true); return t;});
    // Debounce for sidebar rebuild â€” cancels previous scheduled rebuild if new one arrives
    private volatile java.util.concurrent.ScheduledFuture<?> pendingSidebarRebuild = null;
    // Use 2 threads so one long filter doesn't block the next request completely
    private final ExecutorService filterPool =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "EG-Filter"); t.setDaemon(true); return t;});
    private final java.util.concurrent.atomic.AtomicInteger filterGen =
            new java.util.concurrent.atomic.AtomicInteger(0);
    public ExecutorService getRebuildPool() { return rebuildPool; }
    private final ExecutorService rebuildPool =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "EG-Rebuild"); t.setDaemon(true); return t;});
    private final ExecutorService decoderPool =
            Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() - 1),
                r -> { Thread t = new Thread(r,"EG-Decoder"); t.setDaemon(true); return t;});

    // â”€â”€ UI Components â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private ContextBar    ctxBar;
    private ActionBar     actionBar;
    private GroupSidebar  groupSidebar;
    private ThumbnailGrid thumbnailGrid;
    private PropertiesPanel propsPanel;
    private StatusBar       statusBar;
    private SemanticBar     semanticBar;    // thin AI-search chip bar (hidden unless active)
    private RebuildOverlay  rebuildOverlay; // null until JFrame sets it

    public void setRebuildOverlay(RebuildOverlay overlay) { this.rebuildOverlay = overlay; }

    // â”€â”€ Constructor â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public EnhancedGalleryTopComponent() {
        setName("Enhanced Gallery");
        setToolTipText("Enhanced Image/Video/Audio Gallery");
        // Locate external tools in background â€” avoids first-decode delay
        loaderPool.submit(() -> {
            org.sleuthkit.autopsy.enhancedgallery.decoder.ToolFinder.init();
            logger.info(org.sleuthkit.autopsy.enhancedgallery.decoder.ToolFinder.statusSummary());
        });
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // Build sub-panels
        ctxBar        = new ContextBar(this);
        actionBar     = new ActionBar(this);
        groupSidebar  = new GroupSidebar(this);
        thumbnailGrid = new ThumbnailGrid(this);
        propsPanel    = new PropertiesPanel(this);
        statusBar     = new StatusBar(this);
        semanticBar   = new SemanticBar(this);

        // Top area: ctxBar on top; below it the action bar with the (hidden)
        // semantic chip bar directly under it. Nested BorderLayouts keep the
        // action bar full-width with its dynamic height intact.
        JPanel actionArea = new JPanel(new BorderLayout());
        actionArea.add(actionBar,   BorderLayout.NORTH);
        actionArea.add(semanticBar, BorderLayout.SOUTH);

        JPanel topArea = new JPanel(new BorderLayout());
        topArea.add(ctxBar,     BorderLayout.NORTH);
        topArea.add(actionArea, BorderLayout.SOUTH);

        // Center: sidebar + grid + props
        JPanel centerArea = new JPanel(new BorderLayout());
        JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                groupSidebar, thumbnailGrid);
        leftSplit.setDividerLocation(290);
        leftSplit.setDividerSize(4);
        leftSplit.setContinuousLayout(true);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftSplit, propsPanel);
        mainSplit.setResizeWeight(1.0);
        mainSplit.setDividerSize(4);
        mainSplit.setContinuousLayout(true);
        // Set divider after layout so negative offset works correctly
        mainSplit.addComponentListener(new java.awt.event.ComponentAdapter() {
            boolean initialized = false;
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if (!initialized && mainSplit.getWidth() > 0) {
                    initialized = true;
                    mainSplit.setDividerLocation(mainSplit.getWidth() - 290 - mainSplit.getDividerSize());
                }
            }
        });

        centerArea.add(mainSplit, BorderLayout.CENTER);
        centerArea.add(statusBar,  BorderLayout.SOUTH);

        add(topArea,    BorderLayout.NORTH);
        add(centerArea, BorderLayout.CENTER);

        // Show "open a case first" message initially
        showNoCaseMessage();
    }

    // â”€â”€ TopComponent lifecycle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public void componentOpened() {
        loadCase();
    }

    @Override
    public void componentClosed() {
        filterPool.shutdownNow();
        rebuildPool.shutdownNow();
        timeoutPool.shutdownNow();
        if (stateStore != null) {
            try { stateStore.close(); } catch (Exception ignored) {}
            stateStore = null;
        }
        org.sleuthkit.autopsy.enhancedgallery.decoder.ThumbnailCache.getInstance()
                .closeCase();
    }

    // â”€â”€ Case loading â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public void loadCase() {
        Case currentCase;
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            showNoCaseMessage();
            return;
        }

        // Open gallery database (review state + thumbnail cache)
        try {
            if (stateStore != null) stateStore.close();
            stateStore = new ReviewStateStore(currentCase);
            logger.log(Level.INFO, "Gallery DB opened successfully");
            org.sleuthkit.autopsy.enhancedgallery.decoder.ThumbnailCache.getInstance()
                    .openCase(stateStore);
            logger.log(Level.INFO, "Thumbnail cache: {0}",
                    org.sleuthkit.autopsy.enhancedgallery.decoder.ThumbnailCache
                            .getInstance().stats());
        } catch (Exception ex) {
            logger.log(Level.SEVERE,
                    "ENHANCED GALLERY: Could not open gallery database! " +
                    "Path: " + currentCase.getCaseDirectory() +
                    "\\ModuleOutput\\enhanced_gallery\\gallery.db", ex);
            // Show user-visible warning
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this,
                        "Could not open gallery database:\n" + ex.getMessage() +
                        "\n\nReview state and thumbnail cache will not be saved.",
                        "Enhanced Gallery - DB Error",
                        JOptionPane.WARNING_MESSAGE));
        }

        statusBar.startIndeterminate("Loading files...");
        thumbnailGrid.clear();
        allFiles.clear();
        visible.clear();
        // Full reset on each case load â€” clean slate
        activeDataSourceId = null;
        activeGroupKey     = null;
        groupBy            = "path";
        selected.clear();
        selFile          = null;
        SwingUtilities.invokeLater(() -> { if (actionBar != null) actionBar.resetGroupBy(); });

        // Build ID -> name map from all data sources (authoritative, ID-based).
        // Two data sources CAN share the same display name (e.g. the same .dd
        // image added to the case twice) â€” dsIdToName keeps both as separate
        // entries since it's keyed by ID; ContextBar disambiguates duplicate
        // names in its dropdown using the ID.
        dsIdToName.clear();
        try {
            for (org.sleuthkit.datamodel.Content ds :
                    currentCase.getSleuthkitCase().getDataSources()) {
                String name = ds.getName();
                if (name != null && !name.isBlank()) {
                    dsIdToName.put(ds.getId(), name);
                }
            }
            logger.log(Level.INFO, "Data sources: {0}", dsIdToName);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Could not load data sources by ID", ex);
        }
        final java.util.Map<Long, String> dsSnapshot = new java.util.LinkedHashMap<>(dsIdToName);
        SwingUtilities.invokeLater(() -> ctxBar.setDataSources(dsSnapshot));

        final org.sleuthkit.autopsy.enhancedgallery.datamodel.GalleryFileLoader loader =
                new org.sleuthkit.autopsy.enhancedgallery.datamodel.GalleryFileLoader(stateStore);
        final long[] lastFlush = {System.currentTimeMillis()};

        loaderPool.submit(() -> loader.load(
            batch -> {
                // Only accumulate into allFiles â€” visible is managed by applyFilters
                allFiles.addAll(batch);

                long now = System.currentTimeMillis();
                if (now - lastFlush[0] >= 1500) {
                    lastFlush[0] = now;
                    final int loaded = allFiles.size();
                    // Show unfiltered progress tiles during load
                    final List<MediaFile> snap = new ArrayList<>(batch);
                    SwingUtilities.invokeLater(() -> {
                        thumbnailGrid.addFiles(snap);
                        statusBar.setLoadingProgress(loaded, loaded + 1);
                        updateStatusBar();
                        requestThumbsForViewport();
                    });
                }
            },
            () -> {
                // Loading done â€” collect data sources found in files
                // (additive â€” don't remove what was already in the combo from getDataSources())
                java.util.LinkedHashSet<String> fromFiles = new java.util.LinkedHashSet<>();
                synchronized (allFiles) {
                    for (MediaFile mf : allFiles) fromFiles.add(mf.getDataSourceName());
                }
                logger.log(Level.INFO, "Data sources in loaded files: {0}", fromFiles);
                SwingUtilities.invokeLater(() -> {
                    // Combo already has all data sources by ID; no update needed here.
                    statusBar.hideSpinner();
                    applyFilters();
                    rebuildSidebarDebounced();
                    // Build MD5 index in background for propagation feature
                    buildMd5Index();
                    // Load GPS coordinates from EXIF artifacts (enables GPS-only filter + map)
                    loadGpsDataAsync();
                    // Sync tags from Autopsy + load tag names for dropdown
                    loaderPool.submit(EnhancedGalleryTopComponent.this::syncTagsFromAutopsy);
                    loadTagNamesFromAutopsy();
                });
            },
            typeIdWarning -> SwingUtilities.invokeLater(() -> showTypeIdWarning(typeIdWarning))
        ));
    }

    /** Per-session storage of the last Type-ID warning, shown via the status bar icon. */
    private Map<String, Integer> lastTypeIdWarning = null;

    /**
     * Called once after loading if some data sources have media-extension files
     * with no MIME type (File Type Identification likely didn't run on them).
     * Shows a one-time dialog and leaves a persistent clickable warning icon
     * in the status bar so the user can review it again later.
     */
    private void showTypeIdWarning(Map<String, Integer> warning) {
        lastTypeIdWarning = warning;
        if (statusBar != null) statusBar.setTypeIdWarning(warning, this::showTypeIdWarningDialog);
        showTypeIdWarningDialog();
    }

    private void showTypeIdWarningDialog() {
        if (lastTypeIdWarning == null || lastTypeIdWarning.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append("Some data sources have files with a media extension (.jpg, .mp4, ...) ")
          .append("but no detected file type.\n")
          .append("File Type Identification likely did not run on them — ")
          .append("these files are NOT shown in the gallery (loading is by file type, not extension).\n\n");
        int total = 0;
        for (Map.Entry<String, Integer> e : lastTypeIdWarning.entrySet()) {
            sb.append("  • ").append(e.getKey()).append(":  ").append(e.getValue()).append(" file(s)\n");
            total += e.getValue();
        }
        sb.append("\nTotal: ").append(total).append(" file(s) not shown.\n")
          .append("To fix: re-run ingest on these data sources with \"File Type Identification\" enabled.");
        JOptionPane.showMessageDialog(this, sb.toString(),
                "File Type Identification not run", JOptionPane.WARNING_MESSAGE);
    }

    // â”€â”€ Filter + render pipeline â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Returns true if the file belongs to the given group key under the given groupBy mode. */
    private static boolean matchesGroupKey(MediaFile mf, String grpKey, String grpBy) {
        if ("tag".equalsIgnoreCase(grpBy)) {
            // For tag grouping: file is in "Bookmark" group if ANY of its tags is Bookmark
            java.util.List<String> tags = mf.getAllTagNames();
            if ("(untagged)".equals(grpKey)) return tags.isEmpty();
            return tags.stream().anyMatch(t -> t.equalsIgnoreCase(grpKey));
        }
        return org.sleuthkit.autopsy.enhancedgallery.datamodel
                .GroupKeyHelper.keyOf(mf, grpBy).equals(grpKey);
    }

    /**
     * Populates {@link #gpsCache} from EXIF blackboard artifacts on a background
     * thread. Without this the GPS-only filter and map badges never light up
     * (the active loader doesn't read GPS). Reads TSK_METADATA_EXIF artifacts and
     * their TSK_GEO_LATITUDE / TSK_GEO_LONGITUDE attributes, keyed by the tagged
     * file's obj_id.
     */
    private void loadGpsDataAsync() {
        loaderPool.submit(() -> {
            try {
                org.sleuthkit.datamodel.SleuthkitCase db =
                        Case.getCurrentCaseThrows().getSleuthkitCase();
                int latId = org.sleuthkit.datamodel.BlackboardAttribute
                        .ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID();
                int lonId = org.sleuthkit.datamodel.BlackboardAttribute
                        .ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID();
                int count = 0;
                for (org.sleuthkit.datamodel.BlackboardArtifact art :
                        db.getBlackboardArtifacts(
                            org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF)) {
                    try {
                        Double lat = null, lon = null;
                        for (org.sleuthkit.datamodel.BlackboardAttribute a : art.getAttributes()) {
                            int id = a.getAttributeType().getTypeID();
                            if (id == latId) lat = a.getValueDouble();
                            else if (id == lonId) lon = a.getValueDouble();
                            else {
                                // Name-based fallback (mirrors PropertiesPanel) for
                                // cases where GPS is stored under non-standard attrs
                                String nl = a.getAttributeType().getTypeName().toLowerCase();
                                if (lat == null && nl.contains("latit")) lat = a.getValueDouble();
                                else if (lon == null && nl.contains("longit")) lon = a.getValueDouble();
                            }
                        }
                        if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) {
                            gpsCache.put(art.getObjectID(), lat, lon, null);
                            count++;
                        }
                    } catch (Exception ignored) { /* skip individual artifact */ }
                }
                final int total = count;
                logger.log(Level.INFO, "GPS cache loaded: {0} entries", total);
                SwingUtilities.invokeLater(() -> {
                    thumbnailGrid.repaint();          // GPS badges
                    if (geoOnly) applyFilters();      // refresh if GPS filter already on
                });
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Could not load GPS data", ex);
            }
        });
    }

    /** Builds MD5 â†’ files index asynchronously in filterPool. */
    private void buildMd5Index() {
        filterPool.submit(() -> {
            java.util.Map<String, java.util.List<MediaFile>> idx = new java.util.HashMap<>();
            synchronized (allFiles) {
                for (MediaFile mf : allFiles) {
                    String md5 = mf.getMd5Hash();
                    if (md5 != null && !md5.isBlank()
                            && !md5.equals("0000000000000000000000000000000000")) {
                        idx.computeIfAbsent(md5, k -> new java.util.ArrayList<>()).add(mf);
                    }
                }
            }
            md5Index = idx;
            logger.log(Level.INFO, "MD5 index built: {0} unique hashes", idx.size());
        });
    }

    /**
     * Expands a list of files to include all files with the same MD5 hash.
     * Shows confirmation if count exceeds the configured limit.
     * Returns the expanded list, or the original list if propagation is off/skipped.
     */
    private java.util.List<MediaFile> expandByMd5(java.util.List<MediaFile> originals) {
        if (!org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings.isPropagateMd5())
            return originals;
        // If called from background thread and index not ready â€” build inline
        if (md5Index == null && !SwingUtilities.isEventDispatchThread()) {
            java.util.Map<String, java.util.List<MediaFile>> idx = new java.util.HashMap<>();
            synchronized (allFiles) {
                for (MediaFile mf : allFiles) {
                    String md5 = mf.getMd5Hash();
                    if (md5 != null && !md5.isBlank()
                            && !md5.equals("0000000000000000000000000000000000")) {
                        idx.computeIfAbsent(md5, k -> new java.util.ArrayList<>()).add(mf);
                    }
                }
            }
            md5Index = idx;
        }
        if (md5Index == null) return originals; // EDT + not built yet â†’ skip

        java.util.Set<MediaFile> expanded = new java.util.LinkedHashSet<>(originals);
        int maxFiles = org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings.getMd5MaxFiles();

        // Track which MD5s were already processed â€” ask at most ONCE per unique hash
        java.util.Set<String> processedMd5s  = new java.util.HashSet<>();
        java.util.Set<String> approvedMd5s   = new java.util.HashSet<>();

        for (MediaFile mf : originals) {
            String md5 = mf.getMd5Hash();
            if (md5 == null || md5.isBlank()) continue;
            java.util.List<MediaFile> duplicates = md5Index.get(md5);
            if (duplicates == null) continue;

            if (processedMd5s.contains(md5)) {
                // Already decided for this MD5 â€” apply same decision
                if (approvedMd5s.contains(md5)) expanded.addAll(duplicates);
                continue;
            }
            processedMd5s.add(md5);

            if (duplicates.size() > maxFiles) {
                // Ask user ONCE per unique MD5 hash
                final int count = duplicates.size();
                final MediaFile previewFile = mf;
                boolean[] confirm = {false};
                try {
                    javax.swing.SwingUtilities.invokeAndWait(() -> {
                        confirm[0] = showMd5PropagationDialog(previewFile, count);
                    });
                } catch (Exception ignored) {}
                if (confirm[0]) { approvedMd5s.add(md5); expanded.addAll(duplicates); }
            } else {
                // Auto-approve small groups
                approvedMd5s.add(md5);
                expanded.addAll(duplicates);
            }
        }

        if (expanded.size() > originals.size())
            logger.log(Level.INFO, "MD5 propagation: {0} â†’ {1} files",
                    new Object[]{originals.size(), expanded.size()});

        return new java.util.ArrayList<>(expanded);
    }

    /** Rebuilds visible list via filterPool. Newer calls cancel older pending ones. */
    public void applyFilters() {
        final int myGen = filterGen.incrementAndGet();

        // Snapshot filter state on EDT
        final Long    dsId   = activeDataSourceId;
        final String  grpKey = activeGroupKey;
        final Set<String> st = new HashSet<>(statusFilters);
        final Set<String> ty = new HashSet<>(typeFilters);
        final boolean geo    = geoOnly;
        final boolean broken = showBrokenOnly;
        final String  grpBy  = groupBy;
        final GpsCache gps   = gpsCache;
        final String  search = (searchText == null || searchText.isBlank())
                ? null : searchText.trim().toLowerCase();
        final java.util.Set<Long>  semIds   = semanticMatchIds; // null = no AI filter
        final java.util.List<Long> semOrder = semanticOrder;

        filterPool.submit(() -> {
            // If a newer request came in, skip this one
            if (filterGen.get() != myGen) return;

            List<MediaFile> snapshot;
            synchronized (allFiles) { snapshot = new ArrayList<>(allFiles); }

            // Filter by data source using ID directly (reliable even when two
            // data sources share the same display name)
            List<MediaFile> result = snapshot.stream()
                .filter(mf -> dsId == null
                           || mf.getAbstractFile().getDataSourceObjectId() == dsId)
                .filter(mf -> grpKey == null || matchesGroupKey(mf, grpKey, grpBy))
                .filter(mf -> mf.matchesFilters(st, ty, geo, gps))
                .filter(mf -> !broken || mf.isThumbnailFailed())
                .filter(mf -> search == null || mf.getName().toLowerCase().contains(search))
                // Semantic filter — no-op unless an AI search is active (semIds != null)
                .filter(mf -> semIds == null || semIds.contains(mf.getId()))
                .collect(java.util.stream.Collectors.toList());

            // When an AI search is active, order the grid by relevance (score rank)
            // instead of the default allFiles order.
            if (semOrder != null) {
                java.util.Map<Long, Integer> rank = new java.util.HashMap<>();
                for (int i = 0; i < semOrder.size(); i++) rank.put(semOrder.get(i), i);
                result.sort(java.util.Comparator.comparingInt(
                        mf -> rank.getOrDefault(mf.getId(), Integer.MAX_VALUE)));
            }

            // Check again â€” might have been superseded during collect
            if (filterGen.get() != myGen) return;

            SwingUtilities.invokeLater(() -> {
                visible = result;
                // Reset thumbnail progress counters for new view
                thumbPending.set(0);
                thumbDone.set(0);
                thumbnailGrid.setFiles(visible);
                updateStatusBar();
                ctxBar.updateProgress(allFiles, visible);
                if (actionBar != null) actionBar.onFilteringDone();
                SwingUtilities.invokeLater(this::requestThumbsForViewport);
            });
        });
    }

    /** Decodes thumbnails only for files currently visible in the scroll viewport. */
    // MD5 â†’ list of files (built lazily, used for MD5-propagation feature)
    private volatile java.util.Map<String, java.util.List<MediaFile>> md5Index = null;

    // Counters for thumbnail decode progress
    private final java.util.concurrent.atomic.AtomicInteger thumbPending = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger thumbDone    = new java.util.concurrent.atomic.AtomicInteger(0);

    public void requestThumbsForViewport() {
        List<MediaFile> toLoad = thumbnailGrid.getViewportFiles();
        int queued = 0;
        for (MediaFile mf : toLoad) {
            if (!mf.isThumbnailRequested()) {
                mf.markThumbnailRequested();
                queued++;
                thumbPending.incrementAndGet();
                final MediaFile capture = mf;

                // Timeout is now handled inside DecoderChain.decodeThumbnail()
                decoderPool.submit(() -> {
                    java.awt.image.BufferedImage thumb =
                            org.sleuthkit.autopsy.enhancedgallery.decoder.DecoderChain
                                    .decodeThumbnail(capture);
                    if (thumb != null) {
                        capture.setThumbnail(thumb);
                        SwingUtilities.invokeLater(() ->
                            thumbnailGrid.repaintFile(capture));
                    } else {
                        capture.markThumbnailFailed(); // prevent endless retry
                    }
                    int done    = thumbDone.incrementAndGet();
                    int pending = thumbPending.get();
                    SwingUtilities.invokeLater(() ->
                        statusBar.setThumbProgress(done, pending));
                });
            }
        }
        if (queued > 0) {
            SwingUtilities.invokeLater(() ->
                statusBar.setThumbProgress(thumbDone.get(), thumbPending.get()));
        }
    }

    // â”€â”€ Tag operations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public void applyTag(String tagName) {
        List<Integer> targets = selected.isEmpty()
                ? (selFile != null ? List.of(selFile) : List.of())
                : new ArrayList<>(selected);

        List<MediaFile> toSave = new ArrayList<>();
        for (int idx : targets) {
            if (idx < 0 || idx >= visible.size()) continue;
            MediaFile mf = visible.get(idx);
            java.util.List<String> existing =
                    new java.util.ArrayList<>(mf.getAllTagNames());

            if (tagName == null) {
                // Remove all gallery tags
                mf.setAllTagNames(java.util.List.of());
            } else {
                boolean alreadyHas = existing.stream()
                        .anyMatch(t -> t.equalsIgnoreCase(tagName));
                if (alreadyHas) {
                    // Toggle off
                    existing.removeIf(t -> t.equalsIgnoreCase(tagName));
                } else {
                    // Add (primary = first in list)
                    existing.add(0, tagName);
                }
                mf.setAllTagNames(existing);
            }
            // Adding or removing a tag counts as reviewing the file → mark Seen.
            if (mf.getReviewState() == MediaFile.ReviewState.UNSEEN)
                mf.setReviewState(MediaFile.ReviewState.SEEN);
            toSave.add(mf);
        }

        if (toSave.isEmpty()) return;

        // Primary files already modified on EDT â€” immediate visual feedback
        thumbnailGrid.repaint();
        selected.clear();
        selFile = null;

        final boolean tagWasAdded = tagName != null
                && toSave.get(0).getAllTagNames().stream()
                   .anyMatch(t -> t.equalsIgnoreCase(tagName));
        final String finalTagName = tagName;
        final List<MediaFile> finalPrimary = toSave;

        // ALL heavy work (MD5 expansion, Autopsy sync, DB save) on loaderPool â€” never blocks EDT
        loaderPool.submit(() -> {
            // Expand to MD5 duplicates (safe on background thread â€” invokeAndWait OK here)
            List<MediaFile> expanded = expandByMd5(finalPrimary);
            for (MediaFile dup : expanded) {
                if (finalPrimary.contains(dup)) continue;
                java.util.List<String> existing = new java.util.ArrayList<>(dup.getAllTagNames());
                if (finalTagName == null) {
                    dup.setAllTagNames(java.util.List.of());
                } else if (tagWasAdded) {
                    if (existing.stream().noneMatch(t -> t.equalsIgnoreCase(finalTagName))) {
                        existing.add(0, finalTagName);
                        dup.setAllTagNames(existing);
                    }
                } else {
                    existing.removeIf(t -> t.equalsIgnoreCase(finalTagName));
                    dup.setAllTagNames(existing);
                }
            }
            // Tagging/untagging marks the file (and its MD5 duplicates) as Seen.
            for (MediaFile mf : expanded) {
                if (mf.getReviewState() == MediaFile.ReviewState.UNSEEN)
                    mf.setReviewState(MediaFile.ReviewState.SEEN);
            }

            // Save + Autopsy sync (single batched pass — see applyTagBatchToAutopsy)
            if (stateStore != null) stateStore.saveBatch(expanded);
            applyTagBatchToAutopsy(expanded, finalTagName);

            SwingUtilities.invokeLater(() -> {
                thumbnailGrid.repaint();
                applyFilters();
                rebuildSidebarDebounced();
            });
        });

        logger.log(Level.INFO, tagName != null
                ? "Tagging (async, MD5 propagation): " + tagName
                : "Removing tag (async, MD5 propagation)");
    }

    /**
     * Resolves an existing Autopsy TagName by display name (case-insensitive),
     * creating it if absent. Returns null if it could not be found or created.
     * Must be called off EDT.
     */
    private org.sleuthkit.datamodel.TagName resolveOrCreateTagName(
            org.sleuthkit.autopsy.casemodule.services.TagsManager tm, String tagName) throws Exception {
        for (org.sleuthkit.datamodel.TagName t : tm.getAllTagNames()) {
            if (t.getDisplayName().equalsIgnoreCase(tagName)) return t;
        }
        // Not found — create it (API signature varies across Autopsy versions)
        for (java.lang.reflect.Method m : tm.getClass().getMethods()) {
            String mn = m.getName();
            Class<?>[] pt = m.getParameterTypes();
            if ((mn.equals("addOrUpdateTagName") || mn.equals("addTagName"))
                    && pt.length >= 1 && pt[0] == String.class) {
                Object[] args = new Object[pt.length];
                args[0] = tagName;
                if (pt.length > 1) args[1] = "";
                if (pt.length > 2 && pt[2].isEnum()) {
                    for (Object c : pt[2].getEnumConstants())
                        if ("NONE".equals(c.toString())) { args[2] = c; break; }
                }
                try {
                    return (org.sleuthkit.datamodel.TagName) m.invoke(tm, args);
                } catch (Exception ex2) {
                    logger.log(Level.FINE, "Could not create tag name: " + tagName, ex2);
                }
                break;
            }
        }
        return null;
    }

    /**
     * Applies a tag change to a whole batch of files in Autopsy in one pass.
     *
     * Performance: the previous per-file implementation issued one
     * {@code getContentTagsByContent} query for EVERY file — thousands of DB
     * round-trips when operating on a large group. Here we fetch ALL content
     * tags once via {@code getAllContentTags()} and index them by obj_id, so
     * the find side is a single query. (Autopsy's TagsManager has no bulk
     * add/delete, so the individual add/delete calls — and their per-item
     * tree-refresh events — remain one at a time; that part is an API limit.)
     *
     * @param files    files whose in-memory model tag state has ALREADY been updated
     * @param tagName  the tag being toggled, or {@code null} to remove all tags
     */
    private void applyTagBatchToAutopsy(List<MediaFile> files, String tagName) {
        try {
            Case currentCase = Case.getCurrentCaseThrows();
            var tm = currentCase.getServices().getTagsManager();

            // Index every existing content tag by content obj_id — ONE query total
            java.util.Map<Long, java.util.List<org.sleuthkit.datamodel.ContentTag>> byObjId =
                    new java.util.HashMap<>();
            for (org.sleuthkit.datamodel.ContentTag ct : tm.getAllContentTags()) {
                byObjId.computeIfAbsent(ct.getContent().getId(),
                        k -> new java.util.ArrayList<>()).add(ct);
            }

            // Resolve/create the TagName once (only needed on the add path)
            org.sleuthkit.datamodel.TagName tn =
                    (tagName != null) ? resolveOrCreateTagName(tm, tagName) : null;
            if (tagName != null && tn == null) {
                logger.log(Level.WARNING, "Tag ''{0}'' not found and could not be created", tagName);
            }

            int added = 0, removed = 0;
            for (MediaFile mf : files) {
                java.util.List<org.sleuthkit.datamodel.ContentTag> existing =
                        byObjId.getOrDefault(mf.getId(), java.util.List.of());
                if (tagName == null) {
                    // Remove ALL tags from this file
                    for (org.sleuthkit.datamodel.ContentTag ct : existing) {
                        tm.deleteContentTag(ct); removed++;
                    }
                } else {
                    boolean nowHasTag = mf.getAllTagNames().stream()
                            .anyMatch(t -> t.equalsIgnoreCase(tagName));
                    boolean inAutopsy = existing.stream()
                            .anyMatch(ct -> ct.getName().getDisplayName().equalsIgnoreCase(tagName));
                    if (nowHasTag && !inAutopsy && tn != null) {
                        tm.addContentTag(mf.getAbstractFile(), tn, "Enhanced Gallery"); added++;
                    } else if (!nowHasTag && inAutopsy) {
                        for (org.sleuthkit.datamodel.ContentTag ct : existing) {
                            if (ct.getName().getDisplayName().equalsIgnoreCase(tagName)) {
                                tm.deleteContentTag(ct); removed++;
                            }
                        }
                    }
                }
            }
            logger.log(Level.INFO, "Autopsy tag sync: {0} added, {1} removed ({2} files)",
                    new Object[]{added, removed, files.size()});
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Batch tag sync to Autopsy failed", ex);
        }
    }

    /**
     * Makes Autopsy's content tags match each file's in-memory model tag set,
     * in one batched pass (single getAllContentTags query). For every file:
     * deletes Autopsy tags not in the model, adds model tags missing in Autopsy.
     * Handles add / remove / replace uniformly. Must be called off EDT.
     */
    private void reconcileTagsToAutopsy(List<MediaFile> files) {
        try {
            Case currentCase = Case.getCurrentCaseThrows();
            var tm = currentCase.getServices().getTagsManager();

            java.util.Map<Long, java.util.List<org.sleuthkit.datamodel.ContentTag>> byObjId =
                    new java.util.HashMap<>();
            for (org.sleuthkit.datamodel.ContentTag ct : tm.getAllContentTags()) {
                byObjId.computeIfAbsent(ct.getContent().getId(),
                        k -> new java.util.ArrayList<>()).add(ct);
            }

            java.util.Map<String, org.sleuthkit.datamodel.TagName> tagNameCache =
                    new java.util.HashMap<>(); // lowercase name -> TagName
            int added = 0, removed = 0;
            for (MediaFile mf : files) {
                java.util.Set<String> desired = new java.util.HashSet<>();
                for (String t : mf.getAllTagNames()) desired.add(t.toLowerCase());

                java.util.List<org.sleuthkit.datamodel.ContentTag> actual =
                        byObjId.getOrDefault(mf.getId(), java.util.List.of());
                java.util.Set<String> actualNames = new java.util.HashSet<>();
                for (org.sleuthkit.datamodel.ContentTag ct : actual) {
                    String dn = ct.getName().getDisplayName();
                    actualNames.add(dn.toLowerCase());
                    if (!desired.contains(dn.toLowerCase())) { tm.deleteContentTag(ct); removed++; }
                }
                for (String t : mf.getAllTagNames()) {
                    if (!actualNames.contains(t.toLowerCase())) {
                        org.sleuthkit.datamodel.TagName tn = tagNameCache.get(t.toLowerCase());
                        if (tn == null) {
                            tn = resolveOrCreateTagName(tm, t);
                            if (tn != null) tagNameCache.put(t.toLowerCase(), tn);
                        }
                        if (tn != null) {
                            tm.addContentTag(mf.getAbstractFile(), tn, "Enhanced Gallery"); added++;
                        }
                    }
                }
            }
            logger.log(Level.INFO, "Autopsy tag reconcile: {0} added, {1} removed ({2} files)",
                    new Object[]{added, removed, files.size()});
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Reconcile tags to Autopsy failed", ex);
        }
    }

    /**
     * Replaces the tag(s) on the selected files with a single new tag.
     * Files with no tag are skipped (this is a replace, not an add). Propagates
     * to MD5 duplicates, persists, and reconciles Autopsy tags.
     */
    public void replaceSelectedTags(String newTag) {
        if (newTag == null || newTag.isBlank()) return;
        final String finalNew = newTag.trim();

        List<Integer> targets = selected.isEmpty()
                ? (selFile != null ? List.of(selFile) : List.of())
                : new ArrayList<>(selected);

        List<MediaFile> primary = new ArrayList<>();
        for (int idx : targets) {
            if (idx < 0 || idx >= visible.size()) continue;
            MediaFile mf = visible.get(idx);
            if (!mf.isTagged()) continue; // replace only affects files that already have a tag
            mf.setAllTagNames(List.of(finalNew));
            if (mf.getReviewState() == MediaFile.ReviewState.UNSEEN)
                mf.setReviewState(MediaFile.ReviewState.SEEN);
            primary.add(mf);
        }

        if (primary.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "None of the selected files have a tag to replace.",
                    "Replace tag", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        thumbnailGrid.repaint();
        selected.clear();
        selFile = null;
        statusBar.startIndeterminate("Replacing tags + propagating MD5 duplicates...");
        if (actionBar != null) actionBar.onFilteringStart();

        final List<MediaFile> finalPrimary = primary;
        loaderPool.submit(() -> {
            List<MediaFile> expanded = expandByMd5(finalPrimary);
            for (MediaFile dup : expanded) {
                if (!finalPrimary.contains(dup)) dup.setAllTagNames(List.of(finalNew));
                if (dup.getReviewState() == MediaFile.ReviewState.UNSEEN)
                    dup.setReviewState(MediaFile.ReviewState.SEEN);
            }
            if (stateStore != null) stateStore.saveBatch(expanded);
            reconcileTagsToAutopsy(expanded);

            final int count = expanded.size();
            SwingUtilities.invokeLater(() -> {
                statusBar.hideSpinner();
                thumbnailGrid.repaint();
                applyFilters();
                rebuildSidebarDebounced();
                logger.log(Level.INFO, "Replaced tag on {0} file(s) with ''{1}'' (incl. MD5 duplicates)",
                        new Object[]{count, finalNew});
            });
        });
    }

    /**
     * Loads all existing Autopsy content tags and applies them to MediaFiles.
     * Called in background after file loading completes.
     */
    private void syncTagsFromAutopsy() {
        try {
            Case currentCase = Case.getCurrentCaseThrows();
            var tm = currentCase.getServices().getTagsManager();

            // Build map: obj_id â†’ ALL tag names (ordered by importance)
            java.util.Map<Long, java.util.List<String>> autopsyTags =
                    new java.util.HashMap<>();
            for (org.sleuthkit.datamodel.ContentTag ct : tm.getAllContentTags()) {
                long id = ct.getContent().getId();
                autopsyTags.computeIfAbsent(id, k -> new java.util.ArrayList<>())
                        .add(ct.getName().getDisplayName());
            }

            if (autopsyTags.isEmpty()) return;

            List<MediaFile> toUpdate = new ArrayList<>();
            synchronized (allFiles) {
                for (MediaFile mf : allFiles) {
                    java.util.List<String> tags = autopsyTags.get(mf.getId());
                    if (tags != null && !tags.equals(mf.getAllTagNames())) {
                        mf.setAllTagNames(tags);
                        toUpdate.add(mf);
                    }
                }
            }

            if (!toUpdate.isEmpty()) {
                logger.log(Level.INFO, "Synced {0} file tags from Autopsy", toUpdate.size());
                if (stateStore != null) stateStore.saveBatch(toUpdate);
                SwingUtilities.invokeLater(() -> {
                    thumbnailGrid.repaint();
                    // Rebuild sidebar so tag counts are correct
                    rebuildSidebar();
                    ctxBar.updateProgress(allFiles, visible);
                });
            }

        } catch (Exception ex) {
            logger.log(Level.WARNING, "Could not sync tags from Autopsy", ex);
        }
    }

    // Live list of Autopsy tag display names — shared by the Tag ▾ dropdown and
    // the thumbnail right-click menu so both show the same, current tags.
    private volatile java.util.List<String> autopsyTagNames = java.util.List.of(
            "Bookmark", "Notable item", "Follow up", "Evidence", "OK / Irrelevant", "Needs review");

    /** Current Autopsy tag names (defaults until loaded). Never null. */
    public java.util.List<String> getTagNames() { return autopsyTagNames; }

    // The built-in Autopsy standard tags — grouped separately (at the end) in
    // the tag menus so custom/AI tags come first.
    private static final java.util.Set<String> PREDEFINED_TAGS_LC = java.util.Set.of(
            "bookmark", "notable item", "follow up", "evidence", "ok / irrelevant", "needs review");

    public static boolean isPredefinedTag(String t) {
        return t != null && PREDEFINED_TAGS_LC.contains(t.trim().toLowerCase());
    }

    /**
     * Child-exploitation category tags (e.g. "Child Abuse Material - (CAM)",
     * "CGI/Animation - Child Exploitive"). Grouped separately at the very end.
     */
    public static boolean isChildExploitationTag(String t) {
        return t != null && t.toLowerCase().contains("child");
    }

    /** Custom / AI tag names (not standard, not child-exploitation), alphabetical. */
    public java.util.List<String> customTagsSorted() {
        return autopsyTagNames.stream()
                .filter(t -> !isPredefinedTag(t) && !isChildExploitationTag(t))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Built-in standard tag names, alphabetical. */
    public java.util.List<String> predefinedTagsSorted() {
        return autopsyTagNames.stream()
                .filter(EnhancedGalleryTopComponent::isPredefinedTag)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Child-exploitation category tag names, alphabetical. */
    public java.util.List<String> childExploitationTagsSorted() {
        return autopsyTagNames.stream()
                .filter(EnhancedGalleryTopComponent::isChildExploitationTag)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(java.util.stream.Collectors.toList());
    }

    /** The gallery window, used to center dialogs regardless of which control invoked them. */
    private java.awt.Component dialogParent() {
        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
        return w != null ? w : this;
    }

    /** Prompts for a new tag name and applies it to the selection. */
    public void promptAndCreateTag(java.awt.Component invoker) {
        String input = JOptionPane.showInputDialog(dialogParent(), "Tag name:", "New Tag",
                JOptionPane.PLAIN_MESSAGE);
        if (input == null) return;
        String name = input.trim();
        if (name.isEmpty()) return;
        String existing = autopsyTagNames.stream()
                .filter(t -> t.equalsIgnoreCase(name)).findFirst().orElse(null);
        applyTag(existing != null ? existing : name);
        loadTagNamesFromAutopsy();
    }

    /**
     * Adds grouped, sorted tag items (custom → standard → child-exploitation,
     * with separators) to a menu, each firing {@code onPick} with the tag name.
     * Child-exploitation items are coloured red.
     */
    public void addGroupedTagItems(javax.swing.JMenu menu, java.util.function.Consumer<String> onPick) {
        java.util.List<String> custom = customTagsSorted();
        java.util.List<String> predef = predefinedTagsSorted();
        java.util.List<String> ce     = childExploitationTagsSorted();
        for (String t : custom) {
            javax.swing.JMenuItem mi = new javax.swing.JMenuItem(t);
            mi.addActionListener(e -> onPick.accept(t));
            menu.add(mi);
        }
        if (!custom.isEmpty() && !predef.isEmpty()) menu.addSeparator();
        for (String t : predef) {
            javax.swing.JMenuItem mi = new javax.swing.JMenuItem(t);
            mi.addActionListener(e -> onPick.accept(t));
            menu.add(mi);
        }
        if (!predef.isEmpty() && !ce.isEmpty()) menu.addSeparator();
        for (String t : ce) {
            javax.swing.JMenuItem mi = new javax.swing.JMenuItem(t);
            mi.setForeground(new java.awt.Color(0xA32D2D));
            mi.addActionListener(e -> onPick.accept(t));
            menu.add(mi);
        }
    }

    /**
     * Builds the "Replace selected tag(s) with ▸" submenu: pick a target tag
     * directly (no dialog), or "Other / new tag…" to type a new one.
     */
    public javax.swing.JMenu buildReplaceTagSubmenu() {
        javax.swing.JMenu sub = new javax.swing.JMenu("⇄ Replace selected tag(s) with");
        addGroupedTagItems(sub, this::replaceSelectedTags);
        sub.addSeparator();
        javax.swing.JMenuItem other = new javax.swing.JMenuItem("Other / new tag…");
        other.addActionListener(e -> promptAndReplaceTags(sub));
        sub.add(other);
        return sub;
    }

    /** Prompts for a target tag and replaces the selection's tag(s) with it. */
    public void promptAndReplaceTags(java.awt.Component invoker) {
        java.util.List<String> ordered = new ArrayList<>(customTagsSorted());
        ordered.addAll(predefinedTagsSorted());
        ordered.addAll(childExploitationTagsSorted());
        javax.swing.JComboBox<String> combo =
                new javax.swing.JComboBox<>(ordered.toArray(new String[0]));
        combo.setEditable(true);
        int res = JOptionPane.showConfirmDialog(dialogParent(), combo, "Replace selected tag(s) with:",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;
        Object item = combo.getEditor().getItem();
        String name = item == null ? "" : item.toString().trim();
        if (name.isEmpty()) return;
        String existing = autopsyTagNames.stream()
                .filter(t -> t.equalsIgnoreCase(name)).findFirst().orElse(null);
        replaceSelectedTags(existing != null ? existing : name);
        loadTagNamesFromAutopsy();
    }

    /** Loads tag names from Autopsy and updates the ActionBar dropdown + context menu. */
    public void loadTagNamesFromAutopsy() {
        loaderPool.submit(() -> {
            try {
                Case currentCase = Case.getCurrentCaseThrows();
                var tm = currentCase.getServices().getTagsManager();
                List<String> names = new ArrayList<>();
                for (org.sleuthkit.datamodel.TagName t : tm.getAllTagNames()) {
                    names.add(t.getDisplayName());
                }
                if (!names.isEmpty()) {
                    autopsyTagNames = java.util.List.copyOf(names);
                    SwingUtilities.invokeLater(() -> actionBar.updateTagNames(names));
                }
            } catch (Exception ex) {
                logger.log(Level.FINE, "Could not load tag names from Autopsy", ex);
            }
        });
    }

    // â”€â”€ Selection â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * File explorer-style selection:
     *  - Click         â†’ select only this file (deselect others)
     *  - Ctrl+Click    â†’ add/remove from selection (multi-select)
     *  - Shift+Click   â†’ select range from anchor to this file
     */
    public void onFileClicked(int visibleIdx, boolean ctrl, boolean shift) {
        if (visibleIdx < 0 || visibleIdx >= visible.size()) return;

        if (shift && selFile != null) {
            // Range select â€” from anchor (selFile) to visibleIdx
            int from = Math.min(selFile, visibleIdx);
            int to   = Math.max(selFile, visibleIdx);
            selected.clear();
            for (int i = from; i <= to; i++) selected.add(i);
            // Anchor stays at selFile, don't update it for shift-click
        } else if (ctrl) {
            // Toggle individual without clearing others
            if (selected.contains(visibleIdx)) {
                selected.remove(visibleIdx);
                if (Integer.valueOf(visibleIdx).equals(selFile))
                    selFile = selected.isEmpty() ? null
                            : selected.stream().reduce((a, b) -> b).orElse(null);
            } else {
                selected.add(visibleIdx);
                selFile = visibleIdx;
            }
        } else {
            // Plain click â€” select only this file
            selected.clear();
            selected.add(visibleIdx);
            selFile = visibleIdx;
        }

        thumbnailGrid.repaint();

        // Show anchor file in properties (no auto-SEEN marking)
        MediaFile shown = (selFile != null && selFile < visible.size())
                ? visible.get(selFile) : null;
        propsPanel.show(shown, gpsCache);
        actionBar.updateOpenButton(!selected.isEmpty());
        actionBar.setSelectionCount(selected.size());
        updateStatusBar();
        statusBar.setSelectionCount(selected.size());
    }

    // Keep old signature for backward compat (called by some places without shift)
    public void onFileClicked(int visibleIdx, boolean ctrl) {
        onFileClicked(visibleIdx, ctrl, false);
    }

    public void selectAll() {
        for (int i = 0; i < visible.size(); i++) selected.add(i);
        if (!visible.isEmpty()) selFile = visible.size() - 1;
        thumbnailGrid.repaint();
        actionBar.setSelectionCount(selected.size());
        statusBar.updateCount(visible.size(), allFiles.size());
        statusBar.setSelectionCount(selected.size());
    }

    public void clearSelection() {
        selected.clear();
        selFile = null;
        thumbnailGrid.repaint();
        propsPanel.show(null, gpsCache);
        actionBar.updateOpenButton(false);
        actionBar.setSelectionCount(0);
        updateStatusBar();
    }

    // â”€â”€ Filter mutators (called by ActionBar) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public void toggleStatusFilter(String key) {
        if (!statusFilters.remove(key)) statusFilters.add(key);
        applyFilters();
    }
    public void toggleTypeFilter(String key) {
        if (!typeFilters.remove(key)) typeFilters.add(key);
        applyFilters();
    }
    public void toggleGeoOnly() {
        geoOnly = !geoOnly;
        applyFilters();
    }
    /** Sets the filename search text (case-insensitive substring match) and re-filters. */
    public void setSearchText(String text) {
        searchText = text;
        applyFilters();
    }

    // ── Semantic search (AI Image Triage) ──────────────────────────────────────

    /**
     * Returns the AIImageTriage FAISS index dir for the current case, or null.
     * Must match exactly the path the ingest module writes to:
     * {@code new File(Case.getModuleDirectory(), "AIImageTriage")}.
     */
    private String currentIndexDir() {
        try {
            String moduleOutput = Case.getCurrentCaseThrows().getModuleDirectory();
            return new java.io.File(moduleOutput, "AIImageTriage").getAbsolutePath();
        } catch (Exception ex) {
            return null;
        }
    }

    /** True while an AI (semantic/similar) result set is filtering the grid. */
    public boolean isSemanticActive() { return semanticMatchIds != null; }
    public String  getSemanticLabel() { return semanticLabel; }
    public int     getSemanticCount() {
        return semanticMatchIds == null ? 0 : semanticMatchIds.size();
    }

    /** Clears the semantic filter and restores the normal (non-AI) view. */
    public void clearSemanticSearch() {
        semanticMatchIds = null;
        semanticOrder    = null;
        semanticLabel    = null;
        if (semanticBar != null) semanticBar.hideBar();
        applyFilters();
    }

    private void applySemanticHits(java.util.List<org.sleuthkit.autopsy.enhancedgallery.search.AiSearchService.Hit> hits,
                                   String label) {
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        java.util.List<Long> order = new java.util.ArrayList<>();
        for (var h : hits) { if (ids.add(h.fileId())) order.add(h.fileId()); }
        semanticMatchIds = ids;
        semanticOrder    = order;
        semanticLabel    = label;
        if (semanticBar != null) semanticBar.showBar(label, ids.size());
        applyFilters();
    }

    /**
     * Runs a semantic text search via the AI service (off EDT), then filters the
     * grid to the ranked results. Safe no-op path when the service/index is
     * unavailable — shows a message and leaves the normal view untouched.
     */
    public void runSemanticSearch(String query, int topN) {
        if (query == null || query.isBlank()) return;
        final String idxDir = currentIndexDir();
        if (idxDir == null) {
            JOptionPane.showMessageDialog(this, "No case is open.",
                    "AI search", JOptionPane.WARNING_MESSAGE);
            return;
        }
        statusBar.startIndeterminate("Starting AI service / searching...");
        loaderPool.submit(() -> {
            try {
                var svc = org.sleuthkit.autopsy.enhancedgallery.search.AiSearchService.getInstance();
                svc.ensureRunning();
                var hits = svc.search(query.trim(), idxDir, topN);
                SwingUtilities.invokeLater(() -> {
                    statusBar.hideSpinner();
                    if (hits.isEmpty()) {
                        JOptionPane.showMessageDialog(this,
                                "No matches for: " + query,
                                "AI search", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    applySemanticHits(hits, query.trim());
                });
            } catch (org.sleuthkit.autopsy.enhancedgallery.search.AiSearchService.ClipDisabledException ce) {
                SwingUtilities.invokeLater(() -> {
                    statusBar.hideSpinner();
                    JOptionPane.showMessageDialog(this,
                            "Text search requires CLIP enabled in the AI service config\n"
                            + "(config/clip_categories.json → \"enabled\": true) and the model downloaded.\n\n"
                            + "\"Find similar\" (right-click a thumbnail) works without CLIP.",
                            "CLIP not enabled", JOptionPane.WARNING_MESSAGE);
                });
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Semantic search failed", ex);
                SwingUtilities.invokeLater(() -> {
                    statusBar.hideSpinner();
                    JOptionPane.showMessageDialog(this,
                            "AI search unavailable:\n" + ex.getMessage(),
                            "AI search", JOptionPane.WARNING_MESSAGE);
                });
            }
        });
    }

    /** Runs a visual-similarity lookup for the file at the given visible index. */
    public void runFindSimilar(int visibleIdx) {
        if (visibleIdx < 0 || visibleIdx >= visible.size()) return;
        final MediaFile mf = visible.get(visibleIdx);
        final long fileId  = mf.getId();
        final String label = mf.getName();
        final String idxDir = currentIndexDir();
        if (idxDir == null) return;

        statusBar.startIndeterminate("Starting AI service / finding similar...");
        loaderPool.submit(() -> {
            try {
                var svc = org.sleuthkit.autopsy.enhancedgallery.search.AiSearchService.getInstance();
                svc.ensureRunning();
                var hits = svc.similar(fileId, idxDir, 50);
                SwingUtilities.invokeLater(() -> {
                    statusBar.hideSpinner();
                    if (hits.isEmpty()) {
                        JOptionPane.showMessageDialog(this,
                                "No similar images found (is this file in the AI index?).",
                                "Find similar", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    applySemanticHits(hits, "similar to " + label);
                });
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Find-similar failed", ex);
                SwingUtilities.invokeLater(() -> {
                    statusBar.hideSpinner();
                    JOptionPane.showMessageDialog(this,
                            "Find similar unavailable:\n" + ex.getMessage(),
                            "Find similar", JOptionPane.WARNING_MESSAGE);
                });
            }
        });
    }
    public void setThumbSize(int px) {
        thumbSize = px;
        thumbnailGrid.setThumbSize(px);
    }
    public void setGroupBy(String mode) {
        groupBy = mode;
        activeGroupKey = null;
        ctxBar.setGroupName("All files");
        rebuildSidebar();
        applyFilters();
    }

    /** Rebuilds sidebar on rebuildPool â€” never blocks EDT or filterPool. */
    /**
     * Debounced sidebar rebuild â€” cancels any pending rebuild and schedules a new
     * one after the configured delay. Allows rapid tagging without repeated rebuilds.
     */
    private void rebuildSidebarDebounced() {
        java.util.concurrent.ScheduledFuture<?> prev = pendingSidebarRebuild;
        if (prev != null) prev.cancel(false);
        int delaySec = org.sleuthkit.autopsy.enhancedgallery.options.GallerySettings
                .getSidebarDebounceSeconds();
        if (delaySec <= 0) {
            rebuildSidebar(); // immediate if delay=0
            return;
        }
        pendingSidebarRebuild = timeoutPool.schedule(
                () -> SwingUtilities.invokeLater(this::rebuildSidebar),
                delaySec, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Shows a confirmation dialog for MD5 propagation with a thumbnail preview. Must be called on EDT. */
    private boolean showMd5PropagationDialog(MediaFile mf, int count) {
        // Build preview panel
        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(12, 0));

        // Thumbnail on the left (if available)
        java.awt.image.BufferedImage thumb = mf.getThumbnail();
        if (thumb != null) {
            int size = 96;
            java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = Math.min((double) size / thumb.getWidth(), (double) size / thumb.getHeight());
            int w = (int)(thumb.getWidth() * scale), h = (int)(thumb.getHeight() * scale);
            g.drawImage(thumb, (size - w) / 2, (size - h) / 2, w, h, null);
            g.dispose();
            javax.swing.JLabel imgLabel = new javax.swing.JLabel(new javax.swing.ImageIcon(scaled));
            imgLabel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(100, 100, 120), 1));
            panel.add(imgLabel, java.awt.BorderLayout.WEST);
        }

        // Message on the right
        String html = "<html><b>" + escapeHtml(mf.getName()) + "</b><br><br>"
                + "This file has <b>" + count + " copies</b> with the same MD5 hash.<br><br>"
                + "Apply the same change to <b>ALL " + count + " copies</b>?</html>";
        javax.swing.JLabel msg = new javax.swing.JLabel(html);
        msg.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, thumb != null ? 0 : 0, 0, 8));
        panel.add(msg, java.awt.BorderLayout.CENTER);
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 4, 4, 4));

        String[] options = {
            "Yes - apply to all " + count + " copies",
            "No - apply only to selected"
        };
        int res = javax.swing.JOptionPane.showOptionDialog(
                this,
                panel,
                "MD5 Duplicate Propagation",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        return res == 0;
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void rebuildSidebar() {
        final Long   dsId  = activeDataSourceId;
        final String grpBy = groupBy;
        if (rebuildOverlay != null) rebuildOverlay.showOverlay();
        groupSidebar.captureScrollPosition();
        rebuildPool.submit(() -> {
            List<MediaFile> snap;
            synchronized (allFiles) { snap = new ArrayList<>(allFiles); }
            List<MediaFile> forSidebar = dsId == null ? snap
                    : snap.stream()
                        .filter(mf -> mf.getAbstractFile().getDataSourceObjectId() == dsId)
                        .collect(java.util.stream.Collectors.toList());
            groupSidebar.rebuild(forSidebar, grpBy, () -> {
                if (rebuildOverlay != null) SwingUtilities.invokeLater(rebuildOverlay::hideOverlay);
            });
        });
    }

    public void onGroupSelected(String groupKey) {
        activeGroupKey = groupKey;
        ctxBar.setGroupName(groupKey != null ? groupKey : "All files");
        selected.clear();
        selFile = null;
        applyFilters();
        // Scroll to top only on explicit group change, not on filter/tag updates
        SwingUtilities.invokeLater(() -> thumbnailGrid.scrollToTop());
    }

    public void setDataSource(Long dsId) {
        activeDataSourceId = dsId;  // null = all; value = Content.getId()
        activeGroupKey      = null;
        ctxBar.setGroupName("All files");
        groupSidebar.resetSelectionToAll(); // view resets to "All files" — keep sidebar in sync
        if (actionBar != null) actionBar.onFilteringStart();
        applyFilters();
        rebuildSidebar();  // sidebar rebuild uses same ID-based filter via applyFilters snapshot
    }

    public void setShowBroken(boolean broken) {
        showBrokenOnly = broken;
        applyFilters();
    }

    /**
     * Resets all failed thumbnails and retries decoding for visible files.
     * Called after external tool paths are configured in Settings.
     */
    public void retryFailedThumbnails() {
        synchronized (allFiles) {
            for (MediaFile mf : allFiles) {
                // Reset if failed OR if requested but thumbnail never arrived (decode returned null)
                if (mf.isThumbnailFailed() || (mf.isThumbnailRequested() && mf.getThumbnail() == null))
                    mf.resetThumbnailState();
            }
        }
        thumbPending.set(0);
        thumbDone.set(0);
        thumbnailGrid.repaint();
        SwingUtilities.invokeLater(this::requestThumbsForViewport);
    }

    /** Marks selected files (or current file) as SEEN. Updates sidebar + applies filters. */
    public void markSelectionAsSeen() {
        List<Integer> targets = selected.isEmpty()
                ? (selFile != null ? List.of(selFile) : List.of())
                : new ArrayList<>(selected);

        // Collect primary files on EDT â€” mark them immediately for visual feedback
        List<MediaFile> primary = new ArrayList<>();
        for (int idx : targets) {
            if (idx < 0 || idx >= visible.size()) continue;
            MediaFile mf = visible.get(idx);
            if (mf.getReviewState() == MediaFile.ReviewState.UNSEEN) {
                mf.setReviewState(MediaFile.ReviewState.SEEN);
                primary.add(mf);
            }
        }
        if (primary.isEmpty()) return;

        thumbnailGrid.repaint(); // immediate visual feedback
        selected.clear();
        selFile = null;

        statusBar.startIndeterminate("Marking seen + propagating MD5 duplicates...");
        if (actionBar != null) actionBar.onFilteringStart();

        final List<MediaFile> finalPrimary = primary;
        final long startMs = System.currentTimeMillis();

        // All heavy work (MD5 expansion + DB save) on background thread
        loaderPool.submit(() -> {
            // expandByMd5 builds index inline if needed (safe on background thread)
            List<MediaFile> expanded = expandByMd5(finalPrimary);
            for (MediaFile dup : expanded) {
                if (!finalPrimary.contains(dup)
                        && dup.getReviewState() == MediaFile.ReviewState.UNSEEN) {
                    dup.setReviewState(MediaFile.ReviewState.SEEN);
                }
            }
            if (stateStore != null) stateStore.saveBatch(expanded);

            long delay = Math.max(0, 600 - (System.currentTimeMillis() - startMs));
            if (delay > 0) try { Thread.sleep(delay); } catch (InterruptedException ignored) {}

            final int count = expanded.size();
            SwingUtilities.invokeLater(() -> {
                statusBar.hideSpinner();
                applyFilters();
                rebuildSidebarDebounced();
                ctxBar.updateProgress(allFiles, visible);
                logger.log(Level.INFO, "Marked {0} file(s) as seen (incl. MD5 duplicates)", count);
            });
        });
    }

    /** Marks all files in current group/filter as SEEN, including MD5 duplicates in other groups. */
    public void markAllVisibleSeen() {
        // Collect primary files on EDT â€” immediate visual feedback
        List<MediaFile> primary = new ArrayList<>();
        for (MediaFile mf : visible) {
            if (mf.getReviewState() == MediaFile.ReviewState.UNSEEN) {
                mf.setReviewState(MediaFile.ReviewState.SEEN);
                primary.add(mf);
            }
        }
        if (primary.isEmpty()) return;

        thumbnailGrid.repaint();
        statusBar.startIndeterminate("Marking seen + propagating MD5 duplicates...");
        if (actionBar != null) actionBar.onFilteringStart();

        final List<MediaFile> finalPrimary = primary;
        final long startMs = System.currentTimeMillis();

        // MD5 expansion + DB save on background thread (never blocks EDT)
        loaderPool.submit(() -> {
            List<MediaFile> expanded = expandByMd5(finalPrimary);
            for (MediaFile dup : expanded) {
                if (!finalPrimary.contains(dup)
                        && dup.getReviewState() == MediaFile.ReviewState.UNSEEN) {
                    dup.setReviewState(MediaFile.ReviewState.SEEN);
                }
            }
            if (stateStore != null) stateStore.saveBatch(expanded);

            long delay = Math.max(0, 600 - (System.currentTimeMillis() - startMs));
            if (delay > 0) try { Thread.sleep(delay); } catch (InterruptedException ignored) {}

            final int count = expanded.size();
            SwingUtilities.invokeLater(() -> {
                statusBar.hideSpinner();
                applyFilters();
                rebuildSidebarDebounced();
                ctxBar.updateProgress(allFiles, visible);
                logger.log(Level.INFO,
                        "Mark group seen: {0} files (incl. MD5 duplicates outside group)", count);
            });
        });
    }
    /** Opens file at visibleIdx in default external application (double-click). */
    public void openFileExternally(int visibleIdx) {
        if (visibleIdx < 0 || visibleIdx >= visible.size()) return;
        MediaFile mf = visible.get(visibleIdx);
        loaderPool.submit(() -> {
            try {
                org.sleuthkit.autopsy.enhancedgallery.options.ExternalViewerService
                        .openDefault(mf.getAbstractFile());
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Could not open file: " + mf.getName(), ex);
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this,
                            "Cannot open file: " + ex.getMessage(),
                            "Open Error", JOptionPane.ERROR_MESSAGE));
            }
        });
    }

    public void jumpToFirstUnseen() {
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).getReviewState() == MediaFile.ReviewState.UNSEEN) {
                thumbnailGrid.scrollToIndex(i);
                onFileClicked(i, false);
                return;
            }
        }
        JOptionPane.showMessageDialog(this, "All files have been reviewed.");
    }

    // â”€â”€ Getters used by sub-components â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public List<MediaFile>  getVisible()       { return visible; }
    public Set<Integer>     getSelected()      { return selected; }
    public Integer          getSelFile()       { return selFile; }
    public Set<String>      getStatusFilters() { return statusFilters; }
    public Set<String>      getTypeFilters()   { return typeFilters; }
    public boolean          isGeoOnly()        { return geoOnly; }
    public int              getThumbSize()     { return thumbSize; }
    public GpsCache         getGpsCache()      { return gpsCache; }
    public ReviewStateStore getStateStore()    { return stateStore; }
    public void             setGeoOnly(boolean v) { geoOnly = v; }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void showNoCaseMessage() {
        statusBar.updateCount(0, 0);
        statusBar.startIndeterminate("Open a case to use the Enhanced Gallery");
    }

    private void updateStatusBar() {
        statusBar.updateCount(visible.size(), allFiles.size());
    }
}

