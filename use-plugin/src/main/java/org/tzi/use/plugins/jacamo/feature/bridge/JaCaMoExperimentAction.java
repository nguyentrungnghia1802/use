package org.tzi.use.plugins.jacamo.feature.bridge;

import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import org.tzi.use.gui.main.*;
import org.tzi.use.plugins.jacamo.feature.bridge.view.*;
import org.tzi.use.runtime.gui.*;

/** Main bridge entry point. The entire run is owned and displayed by USE. */
public final class JaCaMoExperimentAction implements IPluginActionDelegate {
    private static final Map<MainWindow,ViewFrame> windows=new WeakHashMap<>();
    public boolean shouldBeEnabled(IPluginAction action){return true;}
    public void performAction(IPluginAction action){SwingUtilities.invokeLater(()->open(action.getParent()));}
    private static void open(MainWindow owner) {
        var existing=windows.get(owner);
        if(existing!=null&&!existing.isClosed()){existing.toFront();try{existing.setSelected(true);}catch(java.beans.PropertyVetoException ignored){}return;}
        BridgeRunDialog.show(owner).ifPresent(configuration->{
            var view=new JaCaMoEvaluationView(configuration);var frame=new ViewFrame("JaCaMo — "+configuration.project().getFileName(),view,"Diagram.gif");frame.setContentPane(view);frame.setSize(1400,820);
            windows.put(owner,frame);frame.addInternalFrameListener(new InternalFrameAdapter(){public void internalFrameClosed(InternalFrameEvent e){windows.remove(owner);view.detachModel();}});
            owner.addWorkspaceViewFrame(frame);view.startRun();
        });
    }
}
