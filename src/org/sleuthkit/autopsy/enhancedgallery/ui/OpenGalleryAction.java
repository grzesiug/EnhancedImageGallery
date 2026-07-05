package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Opens the Enhanced Gallery in a separate JFrame window (not docked).
 */
@ActionID(
    category = "Tools",
    id       = "org.sleuthkit.autopsy.enhancedgallery.ui.OpenGalleryAction"
)
@ActionRegistration(
    displayName = "#CTL_OpenGalleryAction",
    iconBase    = "org/sleuthkit/autopsy/enhancedgallery/resources/gallery.png"
)
@ActionReference(
    path     = "Menu/Tools",
    position = 999
)
@Messages("CTL_OpenGalleryAction=Otwórz Rozszerzoną Galerię")
public class OpenGalleryAction implements ActionListener {

    private static JFrame galleryFrame;

    @Override
    public void actionPerformed(ActionEvent e) {
        // If already open, bring to front
        if (galleryFrame != null && galleryFrame.isDisplayable()) {
            galleryFrame.toFront();
            galleryFrame.requestFocus();
            return;
        }

        TopComponent tc = EnhancedGalleryTopComponent.findInstance();

        // Build a standalone JFrame
        galleryFrame = new JFrame("Enhanced Image Gallery");
        galleryFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        galleryFrame.setLayout(new BorderLayout());
        galleryFrame.add(tc, BorderLayout.CENTER);

        // Window icon
        try {
            java.net.URL iconUrl = OpenGalleryAction.class.getResource(
                    "/org/sleuthkit/autopsy/enhancedgallery/resources/gallery.png");
            if (iconUrl != null)
                galleryFrame.setIconImage(javax.imageio.ImageIO.read(iconUrl));
        } catch (Exception ignored) {}

        // Set restore size BEFORE maximizing — otherwise Restore button gives tiny strip
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        galleryFrame.setSize((int)(screen.width * 0.82), (int)(screen.height * 0.82));
        galleryFrame.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        // Now maximize
        galleryFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        galleryFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent we) {
                // Stop the AI search service if THIS gallery launched it (no-op
                // if it was never used or is owned by an active ingest)
                try {
                    org.sleuthkit.autopsy.enhancedgallery.search.AiSearchService
                            .getInstance().stopIfOwned();
                } catch (Exception ignored) {}
                // Return TC to NB so it can be opened again later
                galleryFrame = null;
            }
        });

        // Install overlay as glass pane
        RebuildOverlay overlay = new RebuildOverlay();
        galleryFrame.setGlassPane(overlay);
        ((EnhancedGalleryTopComponent) tc).setRebuildOverlay(overlay);

        galleryFrame.setVisible(true);

        // Trigger case load after the frame is visible
        SwingUtilities.invokeLater(() ->
            ((EnhancedGalleryTopComponent) tc).loadCase());
    }
}
