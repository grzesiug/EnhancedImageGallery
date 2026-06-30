package org.sleuthkit.autopsy.enhancedgallery.options;

import javax.swing.*;
import java.awt.*;
import org.sleuthkit.autopsy.enhancedgallery.options.ExternalViewerSettingsPanel;

/**
 * Main options panel — currently shows the External Viewers tab.
 * Additional tabs (decoder paths, thumbnail cache size) will be added later.
 */
public class GalleryOptionsPanel extends JPanel {

    private final GalleryOptionsPanelController controller;
    private final ExternalViewerSettingsPanel viewersPanel;

    public GalleryOptionsPanel(GalleryOptionsPanelController controller) {
        this.controller   = controller;
        this.viewersPanel = new ExternalViewerSettingsPanel();
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("External viewers", viewersPanel);
        // Future tabs: tabs.addTab("Decoder paths", decoderPathsPanel);
        add(tabs, BorderLayout.CENTER);
    }

    void load()  { /* load from prefs → done inside ExternalViewerSettingsPanel */ }
    void store() { viewersPanel.save(); }
    boolean valid() { return true; }
}
