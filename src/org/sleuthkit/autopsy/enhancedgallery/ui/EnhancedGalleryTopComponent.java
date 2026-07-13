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
 * Main window (TopComponent) of the Enhanced Evidence Gallery.
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
    // Set by view-change actions (source/group/filter) so the next applyFilters
    // scrolls the grid to the top and clears the now-stale index-based selection
    // + properties panel. Not set by tag/seen updates (those keep the view).
    private boolean resetViewOnNextFilter = false;
    // Semantic (AI Image Triage) search — null when inactive (normal gallery use).
    // semanticMatchIds: obj_ids returned by /search or /similar; semanticOrder:
    // same ids in relevance order so the grid can be ranked by score.
    private volatile java.util.Set<Long>  semanticMatchIds = null;
    private volatile java.util.List<Long> semanticOrder    = null;
    private volatile String semanticLabel = null; // e.g. 'osoba z dokumentem' or 'IMG_1234.jpg'
    // Matched-text snippets from a document (BGE-M3) search, keyed by obj_id — shown
    // in the tile tooltip and PropertiesPanel. Null for visual (CLIP) searches.
    private volatile java.util.Map<Long, String> semanticSnippets = null;
    // Documents are loaded lazily (option b): only the first time the user enables
    // the "Documents" type filter. Pure-image analysts never pay the load cost.
    private volatile boolean documentsLoaded  = false;
    private volatile boolean documentsLoading = false;
    // Last type-filter set the sidebar was built for — when it changes, the sidebar
    // groups are rebuilt so they track the visible types (see rebuildSidebar).
    private Set<String> lastSidebarTypes = null;

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
        // allFiles was just cleared, so any previously-loaded documents are gone too:
        // reset the lazy-load flags, otherwise ensureDocumentsLoaded() would treat the
        // stale "already loaded" state as current and never re-query documents when the
        // gallery is reopened on the same TopComponent instance (no Autopsy restart).
        documentsLoaded  = false;
        documentsLoading = false;
        lastSidebarTypes = null;
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
    /**
     * Lazily loads document files the first time the "Documents" filter is enabled
     * (option b). Runs the document-only query on the loader pool, appends results
     * to {@link #allFiles}, then re-applies filters so the new tiles appear. No-op
     * once the documents are loaded or a load is already in progress.
     */
    private void ensureDocumentsLoaded() {
        if (documentsLoaded || documentsLoading) return;
        documentsLoading = true;
        logger.log(Level.INFO, "Documents filter enabled — loading document files on demand");
        if (statusBar != null) statusBar.startIndeterminate("Loading documents…");

        final var loader = new org.sleuthkit.autopsy.enhancedgallery.datamodel
                .GalleryFileLoader(stateStore);
        loaderPool.submit(() -> loader.loadDocuments(
            batch -> allFiles.addAll(batch),
            () -> SwingUtilities.invokeLater(() -> {
                documentsLoaded  = true;
                documentsLoading = false;
                if (statusBar != null) statusBar.hideSpinner();
                applyFilters();          // re-run so freshly loaded documents are shown
                rebuildSidebarDebounced(); // document counts now appear in groups
                updateStatusBar();
            })
        ));
    }

    public void applyFilters() {
        // Lazy document load (option b): the first time "Documents" is enabled, pull
        // document files in the background and re-apply once they're in allFiles.
        if (typeFilters.contains("document")) ensureDocumentsLoaded();

        // When the visible type set changes, rebuild the sidebar so its groups
        // (MIME, extension, …) match the types now shown in the grid.
        if (!typeFilters.equals(lastSidebarTypes)) {
            lastSidebarTypes = new HashSet<>(typeFilters);
            rebuildSidebarDebounced();
        }

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
        // File-name search: pipe-separated terms, OR-matched (e.g. img1|img2).
        final java.util.List<String> searchTerms = SearchTerms.parse(searchText);
        final java.util.Set<Long>  semIds   = semanticMatchIds; // null = no AI filter
        final java.util.List<Long> semOrder = semanticOrder;
        // Consume the view-reset flag (set by source/group/filter changes).
        final boolean resetView = resetViewOnNextFilter;
        resetViewOnNextFilter = false;

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
                .filter(mf -> SearchTerms.matchesAny(mf.getName().toLowerCase(), searchTerms))
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
                // On a source/group/filter change: the old index-based selection
                // is now stale — clear it and the properties panel, and scroll the
                // grid back to the top (done here, after setFiles, so it's reliable).
                if (resetView) {
                    selected.clear();
                    selFile = null;
                    propsPanel.show(null, gpsCache);
                    if (actionBar != null) actionBar.setSelectionCount(0);
                    thumbnailGrid.scrollToTop();
                }
                updateStatusBar();
                ctxBar.updateProgress(allFiles, visible);
                // Update the AI bar's "N hidden by filters" counter (semIds != null
                // means an AI search is active; total is what the bar already stores).
                if (semanticBar != null && semIds != null) {
                    semanticBar.updateVisible(visible.size());
                }
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

    // Prefixes used by the automated triage modules for their tag names. These get
    // their OWN group in the tag menus and TAKE PRECEDENCE over every other category
    // (so e.g. "TxtAI: Child exploid" is grouped as an AI tag, not lumped in with the
    // child-exploitation heuristic below).
    private static final String[] AI_TAG_PREFIXES = { "ai:", "clipai:", "txtai:" };

    /** True for automated triage tags ("AI:", "ClipAI:", "TxtAI:" — case-insensitive). */
    public static boolean isAiTag(String t) {
        if (t == null) return false;
        String lc = t.trim().toLowerCase();
        for (String p : AI_TAG_PREFIXES) if (lc.startsWith(p)) return true;
        return false;
    }

    /**
     * Child-exploitation category tags (e.g. "Child Abuse Material - (CAM)",
     * "CGI/Animation - Child Exploitive"). Grouped separately at the very end.
     * AI-prefixed tags are excluded — their own group wins.
     */
    public static boolean isChildExploitationTag(String t) {
        return t != null && !isAiTag(t) && t.toLowerCase().contains("child");
    }

    /** Automated triage tag names (AI:/ClipAI:/TxtAI: prefixed), alphabetical. */
    public java.util.List<String> aiTagsSorted() {
        return autopsyTagNames.stream()
                .filter(EnhancedGalleryTopComponent::isAiTag)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Custom tag names (not AI, not standard, not child-exploitation), alphabetical. */
    public java.util.List<String> customTagsSorted() {
        return autopsyTagNames.stream()
                .filter(t -> !isAiTag(t) && !isPredefinedTag(t) && !isChildExploitationTag(t))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Built-in standard tag names, alphabetical. */
    public java.util.List<String> predefinedTagsSorted() {
        return autopsyTagNames.stream()
                .filter(t -> !isAiTag(t) && isPredefinedTag(t))
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

    /** Colour used to mark automated (AI) tags in the menus. */
    public static final java.awt.Color AI_TAG_COLOR = new java.awt.Color(0x1D4ED8);

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
        java.util.List<String> ai     = aiTagsSorted();
        java.util.List<String> custom = customTagsSorted();
        java.util.List<String> predef = predefinedTagsSorted();
        java.util.List<String> ce     = childExploitationTagsSorted();
        for (String t : ai) {
            javax.swing.JMenuItem mi = new javax.swing.JMenuItem(t);
            mi.setForeground(AI_TAG_COLOR);
            mi.addActionListener(e -> onPick.accept(t));
            menu.add(mi);
        }
        if (!ai.isEmpty() && !custom.isEmpty()) menu.addSeparator();
        for (String t : custom) {
            javax.swing.JMenuItem mi = new javax.swing.JMenuItem(t);
            mi.addActionListener(e -> onPick.accept(t));
            menu.add(mi);
        }
        if ((!ai.isEmpty() || !custom.isEmpty()) && !predef.isEmpty()) menu.addSeparator();
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
        java.util.List<String> ordered = new ArrayList<>(aiTagsSorted());
        ordered.addAll(customTagsSorted());
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
        resetViewOnNextFilter = true;
        applyFilters();
    }
    public void toggleTypeFilter(String key) {
        if (!typeFilters.remove(key)) typeFilters.add(key);
        resetViewOnNextFilter = true;
        applyFilters();
    }
    public void toggleGeoOnly() {
        geoOnly = !geoOnly;
        resetViewOnNextFilter = true;
        applyFilters();
    }
    /** Sets the filename search text (case-insensitive substring match) and re-filters. */
    public void setSearchText(String text) {
        searchText = text;
        resetViewOnNextFilter = true;
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

    /** Per-case BGE-M3 document index directory (AI Text Triage), or null if no case. */
    private String currentTextIndexDir() {
        try {
            String moduleOutput = Case.getCurrentCaseThrows().getModuleDirectory();
            return new java.io.File(moduleOutput, "AITextTriage").getAbsolutePath();
        } catch (Exception ex) {
            return null;
        }
    }

    /** Matched-text snippet for a document hit (from the last text search), or null. */
    public String getSemanticSnippet(long objId) {
        java.util.Map<Long, String> s = semanticSnippets;
        if (s == null) return null;
        String snip = s.get(objId);
        return (snip == null || snip.isBlank()) ? null : snip;
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
        semanticSnippets = null;
        if (semanticBar != null) semanticBar.hideBar();
        applyFilters();
    }

    private void applySemanticHits(java.util.List<org.sleuthkit.autopsy.enhancedgallery.search.AiSearchService.Hit> hits,
                                   String label) {
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        java.util.List<Long> order = new java.util.ArrayList<>();
        for (var h : hits) { if (ids.add(h.fileId())) order.add(h.fileId()); }
        setSemanticResult(ids, order, null, label); // visual search — no text snippets
    }

    /**
     * Installs a ranked AI result set (visual and/or document) as the active grid
     * filter. {@code snippets} is non-null only for document (text) searches.
     */
    private void setSemanticResult(java.util.Set<Long> ids, java.util.List<Long> order,
                                   java.util.Map<Long, String> snippets, String label) {
        semanticMatchIds = ids;
        semanticOrder    = order;
        semanticSnippets = snippets;
        semanticLabel    = label;
        if (semanticBar != null) semanticBar.showBar(label, ids.size());
        applyFilters();
    }

    /**
     * Runs a semantic text search via the AI service (off EDT), then filters the
     * grid to the ranked results. Safe no-op path when the service/index is
     * unavailable — shows a message and leaves the normal view untouched.
     */
    /**
     * Runs an AI search against ONE index, chosen explicitly in the dialog:
     * {@code textMode=false} → AI Image Triage (CLIP, visual phrasing);
     * {@code textMode=true}  → AI Text Triage (BGE-M3, document text + OCR).
     *
     * The two indexes are phrased differently and (with OCR) a text search may
     * return both document and image files, so the analyst picks the index rather
     * than us inferring it from the type filter. The active filters and selected
     * group still narrow the results (shown as "N hidden by filters" in the bar).
     */
    public void runSemanticSearch(String query, int topN, boolean textMode) {
        if (query == null || query.isBlank()) return;
        final String q = query.trim();

        final String idxDir = textMode ? currentTextIndexDir() : currentIndexDir();
        if (idxDir == null) {
            JOptionPane.showMessageDialog(this, "No case is open.",
                    "AI search", JOptionPane.WARNING_MESSAGE);
            return;
        }

        statusBar.startIndeterminate("Starting AI service / searching...");
        loaderPool.submit(() -> {
            java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
            java.util.List<Long> order = new java.util.ArrayList<>();
            java.util.Map<Long, String> snippets = new java.util.HashMap<>();
            Exception[] fatal = { null };
            boolean[]   handled = { false }; // a specific dialog was already chosen

            try {
                if (textMode) {
                    var tsvc = org.sleuthkit.autopsy.enhancedgallery.search.AiTextSearchService.getInstance();
                    tsvc.ensureRunning();
                    for (var h : tsvc.search(q, idxDir, topN)) {
                        if (ids.add(h.fileId())) order.add(h.fileId());
                        if (h.snippet() != null && !h.snippet().isBlank())
                            snippets.putIfAbsent(h.fileId(), h.snippet());
                    }
                } else {
                    var svc = org.sleuthkit.autopsy.enhancedgallery.search.AiSearchService.getInstance();
                    svc.ensureRunning();
                    for (var h : svc.search(q, idxDir, topN)) {
                        if (ids.add(h.fileId())) order.add(h.fileId());
                    }
                }
            } catch (org.sleuthkit.autopsy.enhancedgallery.search.AiSearchService.ClipDisabledException ce) {
                handled[0] = true;
                SwingUtilities.invokeLater(() -> {
                    statusBar.hideSpinner();
                    JOptionPane.showMessageDialog(this,
                            "Visual search requires CLIP enabled in the AI Image Triage service config\n"
                            + "(config/clip_categories.json → \"enabled\": true) and the model downloaded.",
                            "CLIP not enabled", JOptionPane.WARNING_MESSAGE);
                });
            } catch (org.sleuthkit.autopsy.enhancedgallery.search.AiTextSearchService.EmbedderUnavailableException ee) {
                handled[0] = true;
                SwingUtilities.invokeLater(() -> {
                    statusBar.hideSpinner();
                    JOptionPane.showMessageDialog(this,
                            "The AI Text Triage embedder has no model weights installed (stub).",
                            "Text search unavailable", JOptionPane.WARNING_MESSAGE);
                });
            } catch (org.sleuthkit.autopsy.enhancedgallery.search.AiTextSearchService.IndexModelMismatchException me) {
                handled[0] = true;
                SwingUtilities.invokeLater(() -> {
                    statusBar.hideSpinner();
                    JOptionPane.showMessageDialog(this,
                            "The text index was built with a different model — re-run "
                            + "AI Text Triage ingest on this case.",
                            "Text index mismatch", JOptionPane.WARNING_MESSAGE);
                });
            } catch (Exception ex) {
                logger.log(Level.WARNING, "AI search failed (textMode=" + textMode + ")", ex);
                fatal[0] = ex;
            }

            if (handled[0]) return;

            final boolean hasResults = !order.isEmpty();
            final String label = q;
            final java.util.Map<Long, String> snipFinal = snippets.isEmpty() ? null : snippets;
            SwingUtilities.invokeLater(() -> {
                statusBar.hideSpinner();
                if (hasResults) {
                    setSemanticResult(ids, order, snipFinal, label);
                } else if (fatal[0] != null) {
                    if (textMode) showAiTextUnavailableDialog("Text search", fatal[0]);
                    else          showAiUnavailableDialog("AI search", fatal[0]);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "No matches for: " + q,
                            "AI search", JOptionPane.INFORMATION_MESSAGE);
                }
            });
        });
    }

    /**
     * Shows the "AI Image Triage required" message when a visual AI feature can't
     * reach a working service (module not installed / ingest not run / service down).
     */
    private void showAiUnavailableDialog(String feature, Exception ex) {
        showAiModuleUnavailableDialog(feature, "AI Image Triage",
                "classifies images and builds a searchable CLIP index during ingest", ex);
    }

    /** Same as above but for the document (AI Text Triage / BGE-M3) modality. */
    private void showAiTextUnavailableDialog(String feature, Exception ex) {
        showAiModuleUnavailableDialog(feature, "AI Text Triage",
                "extracts document text and builds a searchable BGE-M3 index during ingest", ex);
    }

    private void showAiModuleUnavailableDialog(String feature, String module, String whatItDoes, Exception ex) {
        String detail = (ex != null && ex.getMessage() != null) ? ex.getMessage() : "service not reachable";
        JOptionPane.showMessageDialog(this,
            "<html><body style='width:420px'>"
            + "<b>" + feature + " is powered by the " + module + " module.</b><br><br>"
            + "This feature needs the <b>" + module + "</b> companion module for Autopsy, "
            + "which " + whatItDoes + ".<br><br>"
            + "To enable it:<br>"
            + "&nbsp;&nbsp;1. Install the <b>" + module + "</b> module (.nbm) in Autopsy.<br>"
            + "&nbsp;&nbsp;2. Run ingest with it on this case (it builds the AI index).<br><br>"
            + "Then this feature will work here.<br><br>"
            + "<font color='gray' size='2'>Details: " + escapeHtml(detail) + "</font>"
            + "</body></html>",
            module + " required", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Runs a similarity lookup for the file at the given visible index, routed by
     * the file's type: DOCUMENT → AI Text Triage (BGE-M3, /similar on 8757, with
     * snippets); everything else → AI Image Triage (CLIP, /similar on 8756).
     */
    public void runFindSimilar(int visibleIdx) {
        if (visibleIdx < 0 || visibleIdx >= visible.size()) return;
        final MediaFile mf = visible.get(visibleIdx);
        final long fileId  = mf.getId();
        final String label = mf.getName();
        final boolean isDoc = mf.getMediaType() == MediaFile.MediaType.DOCUMENT;

        if (isDoc) {
            runFindSimilarDocuments(fileId, label);
            return;
        }

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
                    showAiUnavailableDialog("Find similar", ex);
                });
            }
        });
    }

    /** Document similarity via AI Text Triage (/similar on 8757), keeping snippets. */
    private void runFindSimilarDocuments(long fileId, String label) {
        final String txtIdx = currentTextIndexDir();
        if (txtIdx == null) return;
        statusBar.startIndeterminate("Starting AI text service / finding similar documents...");
        loaderPool.submit(() -> {
            try {
                var tsvc = org.sleuthkit.autopsy.enhancedgallery.search.AiTextSearchService.getInstance();
                tsvc.ensureRunning();
                var hits = tsvc.similar(fileId, txtIdx, 50);
                java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
                java.util.List<Long> order = new java.util.ArrayList<>();
                java.util.Map<Long, String> snippets = new java.util.HashMap<>();
                for (var h : hits) {
                    if (ids.add(h.fileId())) order.add(h.fileId());
                    if (h.snippet() != null && !h.snippet().isBlank())
                        snippets.putIfAbsent(h.fileId(), h.snippet());
                }
                final java.util.Map<Long, String> snipFinal = snippets.isEmpty() ? null : snippets;
                SwingUtilities.invokeLater(() -> {
                    statusBar.hideSpinner();
                    if (order.isEmpty()) {
                        JOptionPane.showMessageDialog(this,
                                "No similar documents found (is this file in the text index?).",
                                "Find similar", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    setSemanticResult(ids, order, snipFinal, "similar to " + label);
                });
            } catch (org.sleuthkit.autopsy.enhancedgallery.search.AiTextSearchService.IndexModelMismatchException me) {
                SwingUtilities.invokeLater(() -> {
                    statusBar.hideSpinner();
                    JOptionPane.showMessageDialog(this,
                            "The text index was built with a different model — re-run "
                            + "AI Text Triage ingest on this case.",
                            "Find similar", JOptionPane.WARNING_MESSAGE);
                });
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Document find-similar failed", ex);
                SwingUtilities.invokeLater(() -> {
                    statusBar.hideSpinner();
                    showAiTextUnavailableDialog("Find similar documents", ex);
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
        resetViewOnNextFilter = true;
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
        // Snapshot the active type filter so the sidebar groups (MIME, extension, …)
        // only reflect the types currently shown in the grid — otherwise document
        // MIME/extension groups would clutter the panel even for image-only analysts.
        final Set<String> ty = new HashSet<>(typeFilters);
        if (rebuildOverlay != null) rebuildOverlay.showOverlay();
        groupSidebar.captureScrollPosition();
        rebuildPool.submit(() -> {
            List<MediaFile> snap;
            synchronized (allFiles) { snap = new ArrayList<>(allFiles); }
            List<MediaFile> forSidebar = snap.stream()
                    .filter(mf -> dsId == null
                               || mf.getAbstractFile().getDataSourceObjectId() == dsId)
                    .filter(mf -> ty.contains(mf.getMediaType().name().toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());
            groupSidebar.rebuild(forSidebar, grpBy, () -> {
                if (rebuildOverlay != null) SwingUtilities.invokeLater(rebuildOverlay::hideOverlay);
            });
        });
    }

    public void onGroupSelected(String groupKey) {
        activeGroupKey = groupKey;
        ctxBar.setGroupName(groupKey != null ? groupKey : "All files");
        resetViewOnNextFilter = true; // clears stale selection/panel + scrolls to top
        applyFilters();
    }

    public void setDataSource(Long dsId) {
        activeDataSourceId = dsId;  // null = all; value = Content.getId()
        activeGroupKey      = null;
        ctxBar.setGroupName("All files");
        groupSidebar.resetSelectionToAll(); // view resets to "All files" — keep sidebar in sync
        resetViewOnNextFilter = true;
        if (actionBar != null) actionBar.onFilteringStart();
        applyFilters();
        rebuildSidebar();  // sidebar rebuild uses same ID-based filter via applyFilters snapshot
    }

    public void setShowBroken(boolean broken) {
        showBrokenOnly = broken;
        applyFilters();
    }

    /** Re-filters AND resets the view (scroll top + clear stale selection/panel). */
    public void applyFiltersResettingView() {
        resetViewOnNextFilter = true;
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

    /**
     * Extracts the selected files (or the right-clicked one if nothing is selected)
     * to a folder chosen by the analyst — the Enhanced Gallery equivalent of
     * Autopsy's "Extract File(s)". Writes the ORIGINAL bytes (not thumbnails) via
     * AbstractFile.read, so it works for every source type without an extra module
     * dependency. Runs the copy off the EDT and reports a summary when done.
     *
     * @param rightClickedIdx visible index of the file under the cursor (used as the
     *                        target when the selection is empty)
     */
    public void exportFiles(int rightClickedIdx) {
        // Resolve the target files on the EDT (indices → MediaFile), snapshotting now.
        java.util.List<MediaFile> targets = new ArrayList<>();
        if (!selected.isEmpty()) {
            java.util.List<Integer> idxs = new ArrayList<>(selected);
            java.util.Collections.sort(idxs);
            for (int i : idxs) if (i >= 0 && i < visible.size()) targets.add(visible.get(i));
        } else if (rightClickedIdx >= 0 && rightClickedIdx < visible.size()) {
            targets.add(visible.get(rightClickedIdx));
        }
        if (targets.isEmpty()) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Save " + targets.size()
                + (targets.size() == 1 ? " file to folder" : " files to folder"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        final java.io.File destDir = chooser.getSelectedFile();
        if (destDir == null) return;

        final java.util.List<MediaFile> toExport = targets;
        statusBar.startIndeterminate("Exporting " + toExport.size() + " file(s)…");
        loaderPool.submit(() -> {
            int ok = 0, fail = 0;
            java.util.List<String> errors = new ArrayList<>();
            try { java.nio.file.Files.createDirectories(destDir.toPath()); } catch (Exception ignored) {}

            for (MediaFile mf : toExport) {
                try {
                    java.io.File out = uniqueDestFile(destDir, mf.getId(), mf.getName());
                    writeFileBytes(mf.getAbstractFile(), out);
                    ok++;
                } catch (Exception ex) {
                    fail++;
                    errors.add(mf.getName() + " — " + (ex.getMessage() != null ? ex.getMessage() : ex));
                    logger.log(Level.WARNING, "Export failed for " + mf.getName(), ex);
                }
            }

            final int okF = ok, failF = fail;
            final java.util.List<String> errF = errors;
            SwingUtilities.invokeLater(() -> {
                statusBar.hideSpinner();
                StringBuilder msg = new StringBuilder();
                msg.append("Exported ").append(okF).append(" file(s) to:\n")
                   .append(destDir.getAbsolutePath());
                if (failF > 0) {
                    msg.append("\n\nFailed: ").append(failF).append(" file(s).");
                    int show = Math.min(errF.size(), 8);
                    for (int i = 0; i < show; i++) msg.append("\n  • ").append(errF.get(i));
                    if (errF.size() > show) msg.append("\n  … and ").append(errF.size() - show).append(" more");
                }
                JOptionPane.showMessageDialog(this, msg.toString(),
                        "Export " + (failF == 0 ? "complete" : "finished with errors"),
                        failF == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                // Best-effort: reveal the folder in the OS file manager.
                if (okF > 0) {
                    try { java.awt.Desktop.getDesktop().open(destDir); } catch (Exception ignored) {}
                }
            });
        });
    }

    /** Streams an AbstractFile's raw content to {@code out} (same read loop as thumbnail extraction). */
    private static void writeFileBytes(org.sleuthkit.datamodel.AbstractFile f, java.io.File out)
            throws java.io.IOException {
        byte[] buf = new byte[65536];
        long offset = 0, remaining = f.getSize();
        try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(out.toPath())) {
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int read;
                try { read = f.read(buf, offset, toRead); }
                catch (org.sleuthkit.datamodel.TskCoreException te) { throw new java.io.IOException(te); }
                if (read <= 0) break;
                os.write(buf, 0, read);
                offset    += read;
                remaining -= read;
            }
        }
    }

    /**
     * Returns a destination file in {@code dir} named {@code <objId>_<name>}: the
     * Autopsy obj_id is unique per file across the whole case, so this guarantees no
     * two exported files ever collide (even when they share the same evidence name
     * from different folders/data sources) AND keeps each file traceable back to its
     * source. Characters invalid on the OS are sanitised; a numeric suffix is only a
     * defensive last resort (should never trigger in practice).
     */
    private static java.io.File uniqueDestFile(java.io.File dir, long objId, String rawName) {
        String name = (rawName == null || rawName.isBlank()) ? "file" : rawName.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_");
        String prefixed = objId + "_" + name;
        java.io.File candidate = new java.io.File(dir, prefixed);
        if (!candidate.exists()) return candidate;

        String base = prefixed, ext = "";
        int dot = prefixed.lastIndexOf('.');
        if (dot > 0) { base = prefixed.substring(0, dot); ext = prefixed.substring(dot); }
        for (int i = 1; i < 100000; i++) {
            java.io.File c = new java.io.File(dir, base + " (" + i + ")" + ext);
            if (!c.exists()) return c;
        }
        return new java.io.File(dir, base + "_" + System.nanoTime() + ext);
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

