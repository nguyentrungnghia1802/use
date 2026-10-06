package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import javax.swing.*;
import org.tzi.use.main.Session;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.ui.JaCaMoWorkbenchPanel;

/** Real native Swing views on the active Session; controls still go through the facade. */
public final class WorkbenchEvidence {
    public static void capture(JaCaMoFacade facade,Session session,Path evidence,boolean paused) throws Exception {
        String oldHeight=System.getProperty("use.gui.view.classdiagram.class.minheight");
        String oldWidth=System.getProperty("use.gui.view.classdiagram.class.minwidth");
        System.setProperty("use.gui.view.classdiagram.class.minheight","40");
        System.setProperty("use.gui.view.classdiagram.class.minwidth","140");
        MainWindow.setJavaFxCall(true);
        var window=StepReplayProof.onEdt(()->MainWindow.create(session,org.tzi.use.runtime.impl.PluginRuntime.getInstance()));
        var frame=StepReplayProof.onEdt(()->new JFrame("Simplified Workbench acceptance"));
        try {
            StepReplayProof.onEdt(()->{
                var panel=new JaCaMoWorkbenchPanel(facade);frame.setContentPane(panel);frame.setSize(1550,1100);frame.setVisible(true);panel.refreshRuntime();
                var tabs=StepReplayProof.component(panel,"workbench-tabs",JTabbedPane.class);
                assertEquals(1,tabs.getTabCount());assertEquals("Projection Rules",tabs.getTitleAt(0));
                var rules=StepReplayProof.component(panel,"mapping-rules-table",JTable.class);assertEquals(3,rules.getColumnCount());
                if(paused)assertEquals(org.jacamo.bridge.contract.RuntimeControlContract.State.PAUSED,facade.runtimeControlState().state());
                image(frame,evidence.resolve("workbench.png"));
                facade.showObjectDiagram();assertSame(session.system(),window.getObjectDiagrams().getFirst().system());
                assertTrue(window.getObjectDiagrams().getFirst().getDiagram().getVisibleData().fObjectToNodeMap.keySet().containsAll(session.system().state().allObjects()));
                return null;
            });
            NativeObjectDiagramEvidence.capture(window,evidence,"native-object-diagram",paused?"Jason agents paused; active native USE state":"Observed domain state on the active native USE system");
        } finally {StepReplayProof.onEdt(()->{window.getObjectDiagrams().forEach(view->view.detachModel());frame.dispose();window.dispose();MainWindow.setJavaFxCall(false);return null;});
            restore("use.gui.view.classdiagram.class.minheight",oldHeight);restore("use.gui.view.classdiagram.class.minwidth",oldWidth);}
    }
    private static void restore(String key,String value) {if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
    private static void image(JFrame frame,Path output) throws Exception {
        var image=new java.awt.image.BufferedImage(frame.getWidth(),frame.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();frame.printAll(graphics);graphics.dispose();assertTrue(javax.imageio.ImageIO.write(image,"png",output.toFile()));
    }
}
