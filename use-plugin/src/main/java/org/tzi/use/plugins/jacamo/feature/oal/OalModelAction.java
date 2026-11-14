package org.tzi.use.plugins.jacamo.feature.oal;
public final class OalModelAction implements org.tzi.use.runtime.gui.IPluginActionDelegate {
    public boolean shouldBeEnabled(org.tzi.use.runtime.gui.IPluginAction action) { return true; }
    public void performAction(org.tzi.use.runtime.gui.IPluginAction action) {
        javax.swing.SwingUtilities.invokeLater(() -> org.tzi.use.plugins.jacamo.feature.ModelToolWindow.open(action.getParent(),org.tzi.use.plugins.jacamo.feature.ModelToolWindow.Kind.OAL));
    }
}
