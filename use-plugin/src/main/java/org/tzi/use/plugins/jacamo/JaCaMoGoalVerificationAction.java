package org.tzi.use.plugins.jacamo;

import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

/** Main authored-input workflow; shares the existing per-window dialog/facade
 * owner with the advanced Workbench entry, never a second live session. */
public final class JaCaMoGoalVerificationAction implements IPluginActionDelegate {
    @Override public boolean shouldBeEnabled(IPluginAction action){return true;}
    @Override public void performAction(IPluginAction action){
        var parent=action==null?null:action.getParent();
        var facade=action!=null&&action.getSession()!=null?DefaultJaCaMoFacade.forSession(action.getSession()):DefaultJaCaMoFacade.INSTANCE;
        javax.swing.SwingUtilities.invokeLater(()->JaCaMoWorkbenchAction.openObservedDialog(parent,facade));
    }
}
