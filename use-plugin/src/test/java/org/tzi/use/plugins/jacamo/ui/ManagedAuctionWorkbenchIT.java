package org.tzi.use.plugins.jacamo.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.impl.PluginRuntime;
import org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade;
import org.tzi.use.plugins.jacamo.ManagedProducerTestSupport;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeHistoryPage;

/** Separate official producer JVM + real USE GUI/Session + unmodified maintained Auction profile. */
class ManagedAuctionWorkbenchIT {
    private String oldHeight, oldWidth, oldProjection;
    @org.junit.jupiter.api.BeforeEach void configureUseDiagramDefaults() {
        oldHeight=System.getProperty("use.gui.view.classdiagram.class.minheight");
        oldWidth=System.getProperty("use.gui.view.classdiagram.class.minwidth");
        oldProjection=System.getProperty(org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.PROPERTY);
        // This unchanged profile queries static Moise definition objects, not domain enactments.
        System.setProperty(org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.PROPERTY,"FULL");
        System.setProperty("use.gui.view.classdiagram.class.minheight","40");
        System.setProperty("use.gui.view.classdiagram.class.minwidth","140");
    }
    @org.junit.jupiter.api.AfterEach void restoreUseDiagramDefaults() {
        restoreProperty("use.gui.view.classdiagram.class.minheight",oldHeight);
        restoreProperty("use.gui.view.classdiagram.class.minwidth",oldWidth);
        restoreProperty(org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.PROPERTY,oldProjection);
    }
    private static void restoreProperty(String key,String value) {
        if(value==null) System.clearProperty(key); else System.setProperty(key,value);
    }
    @Test void managedOriginalAuctionLoadsOclBeforeOfficialStartAndShowsRealTransientViolation() throws Exception {
        assertFalse(java.awt.GraphicsEnvironment.isHeadless());
        Path evidence=Path.of("target/workbench-acceptance", "auction-"+System.currentTimeMillis()).toAbsolutePath();
        Path original=Path.of("../../jacamo/examples/auction/auction.jcm");
        Path profile=Path.of("src/test/resources/jacamo/ocl/auction/auction_demo.ocl").toAbsolutePath();
        String profileHash=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(Files.readAllBytes(profile));
        var errors=new java.util.concurrent.CopyOnWriteArrayList<String>();
        try(var producer=new ManagedProducerTestSupport(original,evidence)) {
            producer.configureWorkflow(); Session session=new Session();
            try(var facade=new DefaultJaCaMoFacade(Path.of("."),session)) {
                producer.clearWorkflowProperties(); facade.configureBridge(producer.configuration);
                MainWindow.setJavaFxCall(true);
                MainWindow window=onEdt(()->MainWindow.create(session,PluginRuntime.getInstance()));
                JFrame workbench=onEdt(()->new JFrame("Managed Auction Workbench acceptance"));
                JaCaMoWorkbenchPanel panel=onEdt(()->new JaCaMoWorkbenchPanel(facade,errors::add));
                try {
                    onEdt(()->{workbench.setContentPane(panel);workbench.setSize(1500,1000);workbench.setVisible(true);panel.importProject(producer.jcm);return null;});
                    await(()->facade.workflowStatus().state().equals("MODEL_READY"),60,errors);
                    // Facade readiness precedes SwingWorker.done() and the UI busy-guard release.
                    // A real user cannot Load OCL until this control is enabled; do not race that boundary.
                    awaitEnabled(panel,"load-profile",30,errors);
                    var system=session.system(); assertNotNull(system); assertFalse(Files.exists(producer.control.resolve("started.json")));
                    var baseline=facade.verificationSnapshot();
                    onEdt(()->{panel.loadVerificationProfile(profile);return null;});
                    await(()->facade.workflowStatus().state().equals("OCL_READY"),30,errors);
                    awaitEnabled(panel,"start-runtime",30,errors);
                    var installed=facade.verificationSnapshot(); assertSame(system,session.system());
                    assertEquals(profileHash,installed.profile().profile().sourceHash());
                    assertTrue(installed.currentVersion()>baseline.currentVersion());
                    onEdt(()->{
                        // USE asks about hiding >100 instances. Choose No through the normal dialog,
                        // so this acceptance actually verifies every displayed object/link.
                        var confirmation=new javax.swing.Timer(100,event->{
                            for(var dialog:java.awt.Window.getWindows())
                                if(dialog instanceof javax.swing.JDialog prompt && prompt.isShowing()
                                        && "Large system state".equals(prompt.getTitle())) {
                                    findOptionPane(prompt).setValue(javax.swing.JOptionPane.NO_OPTION);
                                    ((javax.swing.Timer)event.getSource()).stop();
                                }
                        });
                        confirmation.start();
                        try { menuItem(window.getJMenuBar(),"Object diagram").doClick(); }
                        finally { confirmation.stop(); }
                        return null;
                    });
                    var diagram=onEdt(()->window.getObjectDiagrams().getFirst());
                    assertSame(system,diagram.system());
                    onEdt(()->{component(panel,"start-runtime",javax.swing.JButton.class).doClick();return null;});
                    await(()->facade.workflowStatus().state().equals("LIVE"),30,errors);
                    assertTrue(Files.exists(producer.control.resolve("started.json")));
                    List<RuntimeHistoryPage.Entry> recorded=List.of();
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(180);
                    while(System.nanoTime()<deadline) {
                        Thread.sleep(500); recorded=allPages(facade);
                        var transition=outcomes(recorded);
                        if(transition.contains(VerificationOutcome.FAIL) && transition.getLast()==VerificationOutcome.PASS) break;
                    }
                    var transition=outcomes(recorded);
                    assertTrue(transition.contains(VerificationOutcome.FAIL),"No actual live Auction running-property violation: "+transition+" "+errors);
                    assertEquals(VerificationOutcome.PASS,transition.getFirst()); assertEquals(VerificationOutcome.PASS,transition.getLast());
                    assertSame(system,session.system());
                    assertTrue(facade.verificationSnapshot().currentVersion()>installed.currentVersion());
                    onEdt(()->{
                        panel.refreshRuntime(); JTable current=component(panel,"verification-table",JTable.class);
                        assertEquals(facade.verificationSnapshot().result().outcomes().size(),current.getRowCount());
                        assertSame(system,diagram.system());
                        assertTrue(system.state().allObjects().stream().allMatch(diagram.getDiagram().getVisibleData().fObjectToNodeMap::containsKey));
                        assertTrue(system.state().allLinks().stream().allMatch(diagram.getDiagram().getVisibleData()::containsLink));
                        component(panel,"workbench-tabs",javax.swing.JTabbedPane.class).setSelectedIndex(4);
                        var image=new java.awt.image.BufferedImage(workbench.getWidth(),workbench.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
                        var graphics=image.createGraphics();workbench.printAll(graphics);graphics.dispose();
                        javax.imageio.ImageIO.write(image,"png",evidence.resolve("workbench.png").toFile());return null;
                    });
                    assertTrue(producer.sourceUnchanged()); assertTrue(errors.isEmpty(),errors.toString());
                    Path bundle=evidence.resolve("recorded-replay"); facade.exportRuntimeReplay(bundle);
                    var propertyEvidence=runningPropertyEvidence(bundle);
                    assertTrue(propertyEvidence.stream().anyMatch(item->((Map<?,?>)item.get("property")).get("values").equals(List.of("true"))));
                    assertTrue(propertyEvidence.stream().anyMatch(item->((Map<?,?>)item.get("property")).get("values").equals(List.of("false"))));
                    var replay=facade.replayRuntime(bundle); assertTrue(replay.complete(),replay.diagnostics().toString());
                    var before=facade.verificationSnapshot();
                    // Stop the producer before an explicit late-load/re-analysis check; preserve the recorded observation evidence.
                    producer.close();
                    facade.disconnectRuntime();
                    facade.loadVerificationProfile(profile); var late=facade.verificationSnapshot();
                    var analysis=facade.reanalyzeRuntime(bundle,evidence.resolve("reanalysis"));
                    assertTrue(analysis.complete(),analysis.diagnostics().toString()); assertEquals("REPLAY_REANALYSIS",analysis.origin());
                    assertEquals(late,facade.verificationSnapshot()); assertSame(system,session.system());
                    var report=new java.util.LinkedHashMap<String,Object>();
                    report.put("status","PASS");report.put("scope","OBSERVED_SUPPORTED_PROJECTION_ONLY");report.put("profileHash",profileHash);
                    report.put("originalSourceUnchanged",true); report.put("baseline",installed.result().toMap());
                    report.put("workflow",Map.of("bootstrapAt",facade.workflowStatus().bootstrapAt(),"startedAt",Files.readString(producer.control.resolve("started.json"))));
                    report.put("transitions",transition.stream().map(Enum::name).toList()); report.put("current",before.result().toMap());
                    report.put("transitionRecords",transitionRecords(recorded));report.put("runningPropertyEvidence",propertyEvidence);
                    report.put("lateLoadedVersion",late.profile().loadedVersion());
                    report.put("liveUnchangedByReanalysis",true);report.put("sameActiveSystem",true);
                    report.put("journalEntries",recorded.size());report.put("recordedReplay",replay.complete());report.put("reanalysis",analysis.complete());
                    Files.write(evidence.resolve("gui-acceptance.json"),CanonicalJson.encode(report));
                } finally {
                    onEdt(()->{window.getObjectDiagrams().forEach(view->view.detachModel());workbench.dispose();window.dispose();MainWindow.setJavaFxCall(false);return null;});
                }
            }
        }
    }
    private static List<RuntimeHistoryPage.Entry> allPages(DefaultJaCaMoFacade facade) {
        var records=new java.util.ArrayList<RuntimeHistoryPage.Entry>();long offset=0,limit=facade.runtimeHistoryTail().persistedEntries();
        while(offset<limit){var page=facade.runtimeHistoryPage(offset,128);records.addAll(page.entries());if(page.nextOrdinal()<=offset)break;offset=page.nextOrdinal();}
        return records;
    }
    private static List<VerificationOutcome> outcomes(List<RuntimeHistoryPage.Entry> entries) {
        var values=new java.util.ArrayList<VerificationOutcome>();
        entries.forEach(entry->entry.result().outcomes().stream().filter(value->value.constraintId().equals("EXTERNAL:Artifact::DEMO_RUNTIME_NoRunningAuction"))
                .forEach(value->{if(values.isEmpty()||values.getLast()!=value.outcome())values.add(value.outcome());}));
        return values;
    }
    private static List<Map<String,Object>> transitionRecords(List<RuntimeHistoryPage.Entry> entries) {
        var records=new java.util.ArrayList<Map<String,Object>>(); VerificationOutcome previous=null;
        for(var entry:entries) for(var outcome:entry.result().outcomes())
            if(outcome.constraintId().equals("EXTERNAL:Artifact::DEMO_RUNTIME_NoRunningAuction")&&outcome.outcome()!=previous) {
                records.add(Map.of("ordinal",entry.ordinal(),"interval",entry.intervalId(),"outcome",outcome.outcome().name(),"result",entry.result().toMap()));
                previous=outcome.outcome();
            }
        return records;
    }
    private static List<Map<String,Object>> runningPropertyEvidence(Path bundle) throws Exception {
        var records=new java.util.ArrayList<Map<String,Object>>();
        try(var lines=Files.lines(bundle.resolve("runtime.jsonl"))) {
            for(String line:(Iterable<String>)lines::iterator) {
                var entry=CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                if(!"EVENT".equals(entry.get("kind"))) continue;
                var properties=new java.util.ArrayList<Map<String,Object>>(); collectRunningProperties(entry.get("payload"),properties);
                var result=CanonicalJson.object(entry.get("result"));
                for(var property:properties) records.add(Map.of("ordinal",entry.get("ordinal"),"stateVersion",result.get("stateVersion"),
                        "eventId",result.get("eventId"),"sourceSequence",result.get("sourceSequence"),"property",property));
            }
        } return List.copyOf(records);
    }
    private static void collectRunningProperties(Object value,List<Map<String,Object>> properties) {
        if(value instanceof Map<?,?> map) {
            if("running".equals(map.get("name"))&&List.of("java.lang.Boolean").equals(map.get("valueTypes")))
                properties.add(CanonicalJson.object(value));
            map.values().forEach(child->collectRunningProperties(child,properties));
        } else if(value instanceof List<?> list) list.forEach(child->collectRunningProperties(child,properties));
    }
    private static void await(java.util.function.BooleanSupplier ready,int seconds,List<String> errors) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        while(!ready.getAsBoolean()&&errors.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(50);
        assertTrue(ready.getAsBoolean(),"GUI workflow timeout: "+errors);
    }
    private static void awaitEnabled(JaCaMoWorkbenchPanel panel,String name,int seconds,List<String> errors) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        while(!onEdt(()->component(panel,name,javax.swing.JButton.class).isEnabled())
                &&errors.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(50);
        assertTrue(onEdt(()->component(panel,name,javax.swing.JButton.class).isEnabled()),"GUI control not ready: "+name+" "+errors);
    }
    private static <T>T onEdt(java.util.concurrent.Callable<T> action)throws Exception{
        var task=new FutureTask<>(action);SwingUtilities.invokeLater(task);return task.get(30,TimeUnit.SECONDS);
    }
    private static javax.swing.JOptionPane findOptionPane(Container root) {
        for(Component child:root.getComponents()) {
            if(child instanceof javax.swing.JOptionPane pane) return pane;
            if(child instanceof Container nested) { var pane=findOptionPane(nested); if(pane!=null)return pane; }
        } return null;
    }
    private static <T extends Component>T component(Container root,String name,Class<T> type){
        for(Component child:root.getComponents()){if(name.equals(child.getName())&&type.isInstance(child))return type.cast(child);
            if(child instanceof Container container){T found=find(container,name,type);if(found!=null)return found;}}
        throw new AssertionError("Component missing: "+name);
    }
    private static <T extends Component>T find(Container root,String name,Class<T> type){
        for(Component child:root.getComponents()){if(name.equals(child.getName())&&type.isInstance(child))return type.cast(child);
            if(child instanceof Container container){T found=find(container,name,type);if(found!=null)return found;}}return null;
    }
    private static javax.swing.JMenuItem menuItem(javax.swing.JMenuBar bar,String text){
        for(int index=0;index<bar.getMenuCount();index++){var item=menuItem(bar.getMenu(index),text);if(item!=null)return item;}
        throw new AssertionError("Menu missing: "+text);
    }
    private static javax.swing.JMenuItem menuItem(javax.swing.JMenu menu,String text){
        if(menu==null)return null;
        for(Component item:menu.getMenuComponents()){
            if(item instanceof javax.swing.JMenu submenu){var found=menuItem(submenu,text);if(found!=null)return found;}
            else if(item instanceof javax.swing.JMenuItem action&&text.equals(action.getText()))return action;
        }return null;
    }
}
