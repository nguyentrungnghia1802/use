package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.runtime.impl.PluginRuntime;

class NativeStepReplayGuiIT {
    @TempDir Path root;
    @Test void duplicateWorkbenchMenuOpenReusesItsOwnerAndCanReopenAfterClose() throws Exception {
        var session=new Session();MainWindow.setJavaFxCall(true);
        var window=StepReplayProof.onEdt(()->MainWindow.create(session,PluginRuntime.getInstance()));
        class OfflineFacade implements JaCaMoFacade,AutoCloseable {
            int closes;
            @Override public String status(){return "synthetic/offline UI lifecycle";}
            @Override public void close(){closes++;}
        }
        var first=new OfflineFacade();var second=new OfflineFacade();
        var action=org.tzi.use.plugins.jacamo.JaCaMoWorkbenchAction.class;
        var open=action.getDeclaredMethod("openDialog",MainWindow.class,JaCaMoFacade.class);open.setAccessible(true);
        var registry=action.getDeclaredField("dialogs");registry.setAccessible(true);
        try {
            var dialog=StepReplayProof.onEdt(()->{
                open.invoke(null,window,first);
                var value=java.util.Arrays.stream(java.awt.Window.getWindows()).filter(javax.swing.JDialog.class::isInstance)
                        .map(javax.swing.JDialog.class::cast).filter(candidate->candidate.getOwner()==window&&candidate.isDisplayable()
                                &&candidate.getTitle().equals("USE JaCaMo Workbench")).findFirst().orElseThrow();
                open.invoke(null,window,second);
                assertEquals(1,java.util.Arrays.stream(java.awt.Window.getWindows()).filter(candidate->candidate.getOwner()==window
                        &&candidate.isDisplayable()&&candidate instanceof javax.swing.JDialog).count());
                assertSame(first,StepReplayProof.field(StepReplayProof.components(value,org.tzi.use.plugins.jacamo.ui.JaCaMoWorkbenchPanel.class).getFirst(),"facade"));
                assertEquals(1,((java.util.Map<?,?>)registry.get(null)).size());value.dispose();return value;
            });
            StepReplayProof.onEdt(()->{assertEquals(1,first.closes);assertEquals(0,second.closes);
                assertTrue(((java.util.Map<?,?>)registry.get(null)).isEmpty());open.invoke(null,window,second);
                var reopened=(javax.swing.JDialog)((java.util.Map<?,?>)registry.get(null)).get(window);
                assertNotSame(dialog,reopened);assertTrue(reopened.isDisplayable());reopened.dispose();return null;});
            StepReplayProof.onEdt(()->{assertEquals(1,second.closes);assertTrue(((java.util.Map<?,?>)registry.get(null)).isEmpty());return null;});
        } finally {StepReplayProof.onEdt(()->{window.dispose();MainWindow.setJavaFxCall(false);return null;});}
        assertEquals(0,((java.util.List<?>)StepReplayProof.field(session,"fListenerStateChange")).size());
    }
    @Test void failedDiagramActivationDoesNotLeakSortingListenersOrViewCounts() throws Exception {
        String height=System.getProperty("use.gui.view.classdiagram.class.minheight"),width=System.getProperty("use.gui.view.classdiagram.class.minwidth");
        System.setProperty("use.gui.view.classdiagram.class.minheight","40");System.setProperty("use.gui.view.classdiagram.class.minwidth","140");
        var pipeline=CodeGroundedTestFixtures.guiPipeline();var session=new Session();session.setSystem(pipeline.state().system());
        MainWindow.setJavaFxCall(true);
        var window=StepReplayProof.onEdt(()->MainWindow.create(session,PluginRuntime.getInstance()));
        try(var original=new NativeRuntimeProjector(pipeline,SESSION,1,REVISION);var controller=new NativeReplayStepController(session)) {
            Path recording=root.resolve("failed-activation");new NativeRuntimeReplay().exportBundle(original,pipeline.export().useText(),recording);
            int listeners=StepReplayProof.onEdt(()->((javax.swing.event.EventListenerList)StepReplayProof.field(
                    org.tzi.use.gui.main.ModelBrowserSorting.getInstance(),"fListenerList")).getListenerCount());
            int views=org.tzi.use.gui.views.diagrams.objectdiagram.NewObjectDiagramView.viewcount;
            System.clearProperty("use.gui.view.classdiagram.class.minwidth");
            var failure=assertThrows(NumberFormatException.class,()->controller.open(recording));
            assertTrue(failure.getMessage().contains("null"));assertSame(original.system(),session.system());assertFalse(controller.active());
            assertEquals(views,org.tzi.use.gui.views.diagrams.objectdiagram.NewObjectDiagramView.viewcount);
            assertEquals(listeners,StepReplayProof.onEdt(()->((javax.swing.event.EventListenerList)StepReplayProof.field(
                    org.tzi.use.gui.main.ModelBrowserSorting.getInstance(),"fListenerList")).getListenerCount()));
            System.setProperty("use.gui.view.classdiagram.class.minwidth","140");
            controller.open(recording);assertNotSame(original.system(),session.system());
        } finally {
            StepReplayProof.onEdt(()->{window.dispose();MainWindow.setJavaFxCall(false);return null;});
            restore("use.gui.view.classdiagram.class.minheight",height);restore("use.gui.view.classdiagram.class.minwidth",width);
        }
    }
    @Test void normalViewsAndOclDialogFollowReadOnlyReplayWithoutListenerOrObjectDrift() throws Exception {
        assertFalse(java.awt.GraphicsEnvironment.isHeadless());
        String height=System.getProperty("use.gui.view.classdiagram.class.minheight"),width=System.getProperty("use.gui.view.classdiagram.class.minwidth");
        System.setProperty("use.gui.view.classdiagram.class.minheight","40");System.setProperty("use.gui.view.classdiagram.class.minwidth","140");
        var pipeline=CodeGroundedTestFixtures.guiPipeline();var session=new Session();session.setSystem(pipeline.state().system());
        MainWindow.setJavaFxCall(true);
        var window=StepReplayProof.onEdt(()->MainWindow.create(session,PluginRuntime.getInstance()));
        try(var original=new NativeRuntimeProjector(pipeline,SESSION,1,REVISION);var controller=new NativeReplayStepController(session)) {
            original.coordinator().loadProfileSource("native.ocl",RuntimeVerificationCoordinatorTest.PROFILE);
            original.applySnapshot(snapshot("initial",0));original.apply(delta("B",1,"B"));original.apply(delta("A",2,"A"));
            original.apply(event("dispose",3,org.jacamo.bridge.contract.RuntimeEventKind.DISPOSED,ID,
                    java.util.Map.of("normalizedEventKind","DELETE_CARTAGO_ARTIFACT","semanticId",ARTIFACT)));
            Path bundle=root.resolve("bundle");new NativeRuntimeReplay().exportBundle(original,pipeline.export().useText(),bundle);
            var facade=new JaCaMoFacade() {
                @Override public String status(){return "synthetic/offline";}
                @Override public void openStepReplay(Path recording){controller.open(recording);}
                @Override public void resetStepReplay(){controller.reset();}
                @Override public void previousStepReplay(){controller.previous();}
                @Override public void nextStepReplay(){controller.next();}
                @Override public NativeReplayStepController.Status stepReplayStatus(){return controller.status();}
                @Override public boolean stepReplayBusy(){return controller.busy();}
                @Override public VerificationSnapshot verificationSnapshot(){return controller.active()?controller.verificationSnapshot():original.coordinator().verificationSnapshot();}
                @Override public RuntimeVerificationResult runtimeVerificationResult(){return verificationSnapshot().result();}
                @Override public RuntimeHistoryPage runtimeHistoryTail(){return controller.active()?controller.historyTail():RuntimeHistoryPage.empty();}
            };
            StepReplayProof.run(facade,session,window,bundle,Path.of("target/step-replay-gui-proof"));
            StepReplayProof.onEdt(()->{
                assertFalse(StepReplayProof.menu(window.getJMenuBar(),"Create object...").isEnabled());
                assertFalse(StepReplayProof.menu(window.getJMenuBar(),"Reset").isEnabled());return null;
            });
        } finally {
            StepReplayProof.onEdt(()->{window.dispose();MainWindow.setJavaFxCall(false);return null;});
            restore("use.gui.view.classdiagram.class.minheight",height);restore("use.gui.view.classdiagram.class.minwidth",width);
        }
        assertEquals(0,((java.util.List<?>)StepReplayProof.field(session,"fListenerStateChange")).size());
        assertEquals(0,((java.util.List<?>)StepReplayProof.field(session,"fListenerEvaluatedStatement")).size());
    }
    private static void restore(String name,String value){if(value==null)System.clearProperty(name);else System.setProperty(name,value);}
}
