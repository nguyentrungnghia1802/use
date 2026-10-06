package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.gui.main.ModelBrowserSorting;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter;
import org.tzi.use.plugins.jacamo.ui.JaCaMoWorkbenchPanel;
import org.tzi.use.uml.sys.MSystem;

/** The same acceptance driver for synthetic, Hello and Auction. No domain names or fabricated live events. */
public final class StepReplayProof {
    private record Expected(long version, String soil, RuntimeVerificationResult result) { }

    public static void run(JaCaMoFacade facade, Session session, MainWindow window, Path recording, Path evidence) throws Exception {
        var original=session.system();
        String originalSoil=new NativeUseSoilExporter().export(original).commands();
        Map<String,String> hashes=hashes(recording);
        var expected=new ArrayList<Expected>();
        List<NativeRuntimeReplay.Step> steps;
        int captureStep;
        try(var bundle=new NativeRuntimeReplay().openBundle(recording);var cursor=bundle.atStep(0)) {
            steps=bundle.steps();
            for(var step:steps) {
                cursor.forward(step.endOrdinal()); var snapshot=cursor.projector().coordinator().verificationSnapshot();
                expected.add(new Expected(snapshot.currentVersion(),new NativeUseSoilExporter().export(cursor.projector().system()).commands(),snapshot.result()));
            }
        }
        assertTrue(steps.size()>2,"Acceptance requires at least two real semantic transitions");
        // The final recorded cut includes the real authoritative resync of all
        // dimensions. A transient maximum of Jason goal observations could
        // precede actual role adoption and completed artifact state.
        captureStep=steps.getLast().stepIndex();
        var errors=new CopyOnWriteArrayList<String>();
        var panel=onEdt(()->panel(facade,errors));
        var frame=onEdt(()->{var value=new JFrame("Generic Step Replay acceptance");value.setContentPane(panel);value.setSize(1500,1000);value.setVisible(true);return value;});
        JDialog[] dialog={null};
        int[] stableListeners={-1,-1,-1,-1};
        var visited=new ArrayList<Map<String,Object>>();
        try {
            facade.openStepReplay(recording);
            awaitReady(panel,facade,errors);
            assertTrue(errors.isEmpty(),errors.toString());
            assertNotNull(facade.stepReplayStatus(),"Open control released without a selected replay: "+onEdt(()->component(panel,"workbench-status",JLabel.class).getText()));
            assertNotSame(original,session.system(),"Open status="+facade.stepReplayStatus()+" errors="+errors);assertNotSame(original.model(),session.system().model());
            NativeObjectDiagramEvidence.capture(window,evidence,"object-diagram-before","after declarations / before behavior; recorded baseline step 0");
            onEdt(()->{menu(window.getJMenuBar(),"Evaluate OCL expression...").doClick();return null;});
            dialog[0]=onEdt(()->Arrays.stream(Window.getWindows()).filter(JDialog.class::isInstance).map(JDialog.class::cast)
                    .filter(value->value.isDisplayable()&&value.getOwner()==window&&value.getTitle().equals("Evaluate OCL expression")).findFirst().orElseThrow());
            for(int cycle=0;cycle<3;cycle++) {
                navigate(panel,facade,"reset",errors);check(panel,facade,session,window,dialog[0],expected,stableListeners,visited);
                navigate(panel,facade,"next",errors);check(panel,facade,session,window,dialog[0],expected,stableListeners,visited);
                MSystem forward=session.system();
                navigate(panel,facade,"next",errors);assertSame(forward,session.system());
                check(panel,facade,session,window,dialog[0],expected,stableListeners,visited);
                navigate(panel,facade,"previous",errors);assertNotSame(forward,session.system());
                assertFalse(hasGuiSubscribers(forward),"Old system must not retain USE GUI callbacks");
                check(panel,facade,session,window,dialog[0],expected,stableListeners,visited);
                navigate(panel,facade,"next",errors);check(panel,facade,session,window,dialog[0],expected,stableListeners,visited);
            }
            if(captureStep==2) NativeObjectDiagramEvidence.capture(window,evidence,"object-diagram-after","after runtime transition step 2; "+steps.get(2).event());
            while(facade.stepReplayStatus().step()<facade.stepReplayStatus().total()) {
                navigate(panel,facade,"next",errors);
                check(panel,facade,session,window,dialog[0],expected,stableListeners,visited);
                if(facade.stepReplayStatus().step()==captureStep) NativeObjectDiagramEvidence.capture(window,evidence,"object-diagram-after",
                        "after runtime transition step "+captureStep+"; "+steps.get(captureStep).event());
            }
            assertFalse(Files.readString(evidence.resolve("object-diagram-before.cmd")).equals(Files.readString(evidence.resolve("object-diagram-after.cmd"))),"Two diagrams must represent distinct actual states");
            onEdt(()->{panel.refreshRuntime();
                assertFalse(button(panel,"import-project").isEnabled());
                assertFalse(button(panel,"start-runtime").isEnabled());
                assertFalse(button(panel,"load-profile").isEnabled());return null;});
            assertEquals(hashes,hashes(recording));
            assertEquals(originalSoil,new NativeUseSoilExporter().export(original).commands());
            assertFalse(hasGuiSubscribers(original));
            // Reopen uses a new isolated context, with no accumulated listener or object count.
            MSystem last=session.system();
            facade.openStepReplay(recording);awaitReady(panel,facade,errors);
            assertNotSame(last,session.system());assertFalse(hasGuiSubscribers(last));
            check(panel,facade,session,window,dialog[0],expected,stableListeners,visited);
            assertTrue(errors.isEmpty(),errors.toString());
            Files.createDirectories(evidence);
            Files.write(evidence.resolve("step-replay-proof.json"),CanonicalJson.encode(Map.of(
                    "status","PASS","scope","OBSERVED_SUPPORTED_PROJECTION_ONLY","semanticSteps",steps.size()-1,
                    "recordedEntries",Files.readAllLines(recording.resolve("runtime.jsonl")).size(),"visited",visited,
                    "originalStateUnchanged",true,"recordingUnchanged",true,"duplicateIdentities",0,"orphanLinks",0,
                    "listenerCounts",Arrays.stream(stableListeners).boxed().toList())));
        } finally {
            onEdt(()->{if(dialog[0]!=null)dialog[0].dispose();frame.dispose();return null;});
        }
    }

    private static void check(JaCaMoWorkbenchPanel panel,JaCaMoFacade facade,Session session,MainWindow window,JDialog dialog,
                              List<Expected> expected,int[] stable,List<Map<String,Object>> visited)throws Exception {
        onEdt(()->{
            var status=facade.stepReplayStatus();var target=expected.get(status.step());var system=session.system();var state=system.state();
            assertTrue(dialog.isDisplayable(),"Owned OCL dialog was closed while navigating step "+status.step());
            var result=facade.verificationSnapshot();
            assertEquals(target.version(),result.currentVersion());
            assertEquals(target.soil(),new NativeUseSoilExporter().export(system).commands());
            assertEquals(target.result().semanticEvidence(),result.result().semanticEvidence());
            assertEquals(target.result().resultHash(),result.result().resultHash());
            assertEquals(state.numObjects(),result.image().objects().values().stream().map(org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot.ObjectState::semanticId).distinct().count());
            assertEquals(state.allLinks().size(),state.allLinks().stream().map(Object::toString).distinct().count());
            assertTrue(state.allLinks().stream().allMatch(link->state.allObjects().containsAll(link.linkedObjects())));
            assertEquals(1,window.getObjectDiagrams().size());var diagram=window.getObjectDiagrams().getFirst();assertSame(system,diagram.system());
            assertEquals(state.allObjects(),diagram.getDiagram().getVisibleData().fObjectToNodeMap.keySet());
            assertTrue(state.allLinks().stream().allMatch(diagram.getDiagram().getVisibleData()::containsLink));
            var tree=components(window.getModelBrowser(),JTree.class).getFirst();
            var root=(javax.swing.tree.DefaultMutableTreeNode)tree.getModel().getRoot();
            var browser=Collections.list(root.depthFirstEnumeration()).stream().map(node->((javax.swing.tree.DefaultMutableTreeNode)node).getUserObject()).toList();
            assertTrue(system.model().classes().stream().allMatch(browser::contains));
            var invariants=components(window,org.tzi.use.gui.views.ClassInvariantView.class);
            assertEquals(1,invariants.size());
            var invariantTable=components(invariants.getFirst(),JTable.class).getFirst();
            for(int row=0;row<invariantTable.getRowCount();row++) {
                String name=invariantTable.getValueAt(row,0).toString();
                var outcome=result.result().outcomes().stream().filter(value->name.contains(value.constraintId().substring(value.constraintId().indexOf(':')+1))).findFirst().orElseThrow();
                String cell=String.valueOf(invariantTable.getValueAt(row,1));
                switch(outcome.outcome()) { case PASS -> assertTrue(cell.contains("true"),name+" "+cell);
                    case FAIL -> assertTrue(cell.contains("false"),name+" "+cell); default -> assertFalse(cell.contains("true"),name+" "+cell); }
                for(int column=0;column<invariantTable.getColumnCount();column++)assertFalse(invariantTable.isCellEditable(row,column));
            }
            var input=components(dialog,JTextArea.class).stream().filter(JTextArea::isEditable).findFirst().orElseThrow();
            var output=components(dialog,JTextArea.class).stream().filter(area->!area.isEditable()).findFirst().orElseThrow();
            // Normal dialog compiles/evaluates a query over all current native classes, including zero-count classes.
            String query=system.model().classes().stream().map(cls->cls.name()+".allInstances()->select(o | o.oclIsTypeOf("+cls.name()+"))->size()").reduce((a,b)->a+" + "+b).orElse("0");
            input.setText(query);components(dialog,JButton.class).stream().filter(button->button.getText().equals("Evaluate")).findFirst().orElseThrow().doClick();
            assertTrue(output.getText().startsWith(Integer.toString(state.numObjects())+" : Integer"),output.getText());
            int[] listeners={((List<?>)field(session,"fListenerStateChange")).size(),((List<?>)field(session,"fListenerEvaluatedStatement")).size(),
                    ((javax.swing.event.EventListenerList)field(window.getModelBrowser(),"fListenerList")).getListenerCount(),
                    ((javax.swing.event.EventListenerList)field(ModelBrowserSorting.getInstance(),"fListenerList")).getListenerCount()};
            if(stable[0]<0)System.arraycopy(listeners,0,stable,0,listeners.length);else assertArrayEquals(stable,listeners,"Navigation listener counts changed at step "+status.step()+" "+field(session,"fListenerStateChange"));
            visited.add(Map.of("step",status.step(),"stateVersion",result.currentVersion(),"objects",state.numObjects(),"links",state.allLinks().size(),
                    "stateHash",result.result().stateHash(),"resultHash",result.result().resultHash()));return null;
        });
    }
    private static void navigate(JaCaMoWorkbenchPanel panel,JaCaMoFacade facade,String action,List<String> errors)throws Exception {
        // Replay remains a core API regression. Its retired Workbench controls are not recreated here.
        switch(action) {
            case "reset" -> facade.resetStepReplay();
            case "previous" -> facade.previousStepReplay();
            case "next" -> facade.nextStepReplay();
            default -> throw new IllegalArgumentException(action);
        }
        awaitReady(panel,facade,errors);
    }
    public static void awaitReady(JaCaMoWorkbenchPanel panel,JaCaMoFacade facade,List<String> errors)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
        while(System.nanoTime()<deadline&&errors.isEmpty()) {
            if(!facade.stepReplayBusy()){onEdt(()->{panel.refreshRuntime();return null;});return;}
            Thread.sleep(20);
        }
        fail("Step operation did not complete: "+errors);
    }
    public static JaCaMoWorkbenchPanel panel(JaCaMoFacade facade,List<String> errors)throws Exception {
        var ctor=JaCaMoWorkbenchPanel.class.getDeclaredConstructor(JaCaMoFacade.class,java.util.function.Consumer.class);ctor.setAccessible(true);
        return ctor.newInstance(facade,(java.util.function.Consumer<String>)errors::add);
    }
    public static boolean hasGuiSubscribers(MSystem system)throws Exception {
        var registry=field(system.getEventBus(),"subscribers");
        for(var values:((Map<?,?>)field(registry,"subscribers")).values())
            for(var subscriber:(Set<?>)values)if(field(subscriber,"target").getClass().getName().startsWith("org.tzi.use.gui."))return true;
        return false;
    }
    public static Object field(Object object,String name)throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static Map<String,String> hashes(Path root)throws Exception {
        var values=new TreeMap<String,String>();try(var paths=Files.list(root)){for(var path:paths.filter(Files::isRegularFile).toList())values.put(path.getFileName().toString(),ExternalOclConstraintService.sha256(Files.readAllBytes(path)));}return values;
    }
    public static <T>T onEdt(Callable<T> action)throws Exception {
        if(SwingUtilities.isEventDispatchThread())return action.call();var task=new FutureTask<T>(action);SwingUtilities.invokeLater(task);return task.get(120,TimeUnit.SECONDS);
    }
    public static JButton button(Container root,String name){return component(root,name,JButton.class);}
    public static <T extends Component>T component(Component root,String name,Class<T> type){return components(root,type).stream().filter(value->name.equals(value.getName())).findFirst().orElseThrow(()->new AssertionError("Missing "+name));}
    public static <T extends Component>List<T> components(Component root,Class<T> type){var values=new ArrayList<T>();if(type.isInstance(root))values.add(type.cast(root));if(root instanceof Container container)for(var child:container.getComponents())values.addAll(components(child,type));return values;}
    public static JMenuItem menu(JMenuBar bar,String text){for(int index=0;index<bar.getMenuCount();index++){var found=findMenu(bar.getMenu(index),text);if(found!=null)return found;}throw new AssertionError("Missing menu "+text);}
    private static JMenuItem findMenu(JMenu menu,String text){if(menu==null)return null;for(var child:menu.getMenuComponents()){if(child instanceof JMenu nested){var found=findMenu(nested,text);if(found!=null)return found;}else if(child instanceof JMenuItem item&&text.equals(item.getText()))return item;}return null;}
}
