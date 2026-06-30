package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.util.*;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.enhancedgallery.datamodel.*;

/**
 * Placeholder mediator panel — full implementation pending migration from
 * EnhancedGalleryTopComponent. Current logic lives in EnhancedGalleryTopComponent.
 */
public class GalleryPanel extends JPanel {

    private final GpsCache gpsCache = new GpsCache();
    private final Set<Integer> selected = new HashSet<>();

    public GalleryPanel(ReviewStateStore reviewStore) {
        // stub — UI is built by EnhancedGalleryTopComponent
    }

    public void addFiles(List<MediaFile> files) {}
    public void onLoadComplete() {}
    public void showError() {}
    public void jumpToFirstUnseen() {}
    public void applyTagToSelection(String tagName) {}
    public void removeTagFromFile(MediaFile file) {}
    public void onGroupSelected(String groupKey) {}
    public void onFiltersChanged() {}
    public void onThumbnailSizeChanged(int sizePx) {}
    public void onFileSelected(MediaFile file) {}
    public void onSelectionChanged(int count) {}
    public void applyTag(String tagName) {}
    public void onFileClicked(int idx, boolean ctrl) {}
    public Set<Integer> getSelected() { return selected; }
    public GpsCache getGpsCache() { return gpsCache; }
}
