package org.sleuthkit.autopsy.enhancedgallery.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Action that opens (or focuses) the Enhanced Gallery TopComponent.
 * Registered in:
 *   • Tools menu (position 850, after standard Autopsy items)
 *   • Main toolbar
 */
@ActionID(
    category = "Tools",
    id       = "org.sleuthkit.autopsy.enhancedgallery.ui.OpenEnhancedGalleryAction"
)
@ActionRegistration(
    displayName = "#OpenEnhancedGalleryAction.name",
    lazy        = true
)
@ActionReferences({
    @ActionReference(
        path     = "Menu/Tools",
        position = 850,
        separatorBefore = 849
    ),
    @ActionReference(
        path     = "Toolbars/File",
        position = 350
    )
})
@NbBundle.Messages({
    "OpenEnhancedGalleryAction.name=Enhanced Evidence Gallery",
    "OpenEnhancedGalleryAction.tooltip=Open Enhanced Image / Video / Audio Gallery"
})
public final class OpenEnhancedGalleryAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        // Find existing instance or create new one
        TopComponent tc = WindowManager.getDefault()
                .findTopComponent("EnhancedGalleryTopComponent");

        if (tc == null) {
            tc = new EnhancedGalleryTopComponent();
        }

        if (!tc.isOpened()) {
            tc.open();
        }
        tc.requestActive();
    }
}
