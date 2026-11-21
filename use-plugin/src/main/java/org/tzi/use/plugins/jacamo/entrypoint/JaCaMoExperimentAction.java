package org.tzi.use.plugins.jacamo.entrypoint;

import javax.swing.SwingUtilities;
import org.tzi.use.plugins.jacamo.gui.main.ExperimentWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

/** Binds the USE menu item to the experiment window. */
public final class JaCaMoExperimentAction implements IPluginActionDelegate {
    @Override public boolean shouldBeEnabled(IPluginAction action) { return true; }

    @Override public void performAction(IPluginAction action) {
        SwingUtilities.invokeLater(() -> ExperimentWindow.open(action.getParent()));
    }
}
