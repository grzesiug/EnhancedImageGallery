package org.sleuthkit.autopsy.enhancedgallery.options;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

/**
 * Registers the Enhanced Gallery options panel in
 * Tools → Options → Advanced → Enhanced Gallery.
 */
@OptionsPanelController.SubRegistration(
    location    = "Advanced",
    displayName = "#CTL_OptionsDisplayName",
    keywords    = "#KW_OptionsKeywords",
    keywordsCategory = "#CTL_OptionsDisplayName",
    position    = 1100
)
@org.openide.util.NbBundle.Messages({
    "CTL_OptionsDisplayName=Enhanced Gallery",
    "KW_OptionsKeywords=gallery image video audio heic webp svg raw viewer"
})
public class GalleryOptionsPanelController extends OptionsPanelController {

    private GalleryOptionsPanel panel;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    @Override public void update()  { getPanel().load();  }
    @Override public void applyChanges() { getPanel().store(); }
    @Override public void cancel()  {}
    @Override public boolean isValid() { return getPanel().valid(); }
    @Override public boolean isChanged() { return false; }
    @Override public HelpCtx getHelpCtx() { return HelpCtx.DEFAULT_HELP; }

    @Override
    public JComponent getComponent(Lookup masterLookup) { return getPanel(); }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) { pcs.addPropertyChangeListener(l); }
    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) { pcs.removePropertyChangeListener(l); }

    private GalleryOptionsPanel getPanel() {
        if (panel == null) panel = new GalleryOptionsPanel(this);
        return panel;
    }

    void changed() {
        pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, false, true);
    }
}
