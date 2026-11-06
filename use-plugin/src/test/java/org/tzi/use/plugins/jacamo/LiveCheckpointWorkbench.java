package org.tzi.use.plugins.jacamo;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.ui.JaCaMoWorkbenchPanel;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel;
import static org.junit.jupiter.api.Assertions.*;

/** Actual workbench Prepare/Start/presentation callbacks, offscreen Verification
 * widgets. Native diagram windows are deliberately outside this headless test. */
final class LiveCheckpointWorkbench implements AutoCloseable {
    final DefaultJaCaMoFacade facade;final Path evidence;
    final AtomicReference<JaCaMoWorkbenchPanel> workbench=new AtomicReference<>();
    final AtomicReference<ObservedConformancePanel> results=new AtomicReference<>();
    final CompletableFuture<Void> prepared=new CompletableFuture<>();
    final List<Long> heartbeat=new CopyOnWriteArrayList<>();
    final Map<String,String> displayed=new ConcurrentHashMap<>();
    final AtomicInteger rawHistoryReads=new AtomicInteger();
    final Map<String,BufferedImage> images=new HashMap<>();
    javax.swing.Timer clock;
    static Component find(Container root,String name){for(var c:root.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container child){var found=find(child,name);if(found!=null)return found;}}return null;}
    static void layout(Container root){root.doLayout();for(var c:root.getComponents())if(c instanceof Container child)layout(child);}
    LiveCheckpointWorkbench(DefaultJaCaMoFacade facade,JaCaMoFacade.ImportSelection selection,Path classpath,JaCaMoFacade.CheckpointPresenter recorder,Path evidence)throws Exception {
        this.facade=facade;this.evidence=evidence;
        var proxy=(JaCaMoFacade)Proxy.newProxyInstance(JaCaMoFacade.class.getClassLoader(),new Class<?>[]{JaCaMoFacade.class},(owner,method,args)->{
            if(method.getName().equals("semanticModels"))return null;
            if(method.getName().equals("importSelection"))return selection;
            if(method.getName().equals("defaultRuntimeClasspathFile"))return classpath;
            if(method.getName().equals("runtimeHistoryPage"))rawHistoryReads.incrementAndGet();
            if(method.getName().equals("registerCheckpointPresenter")) {
                var render=(JaCaMoFacade.CheckpointPresenter)args[0];
                return facade.registerCheckpointPresenter((view,cut,checks)->recorder.present(view,cut,checks).thenCompose(ignored->render.present(view,cut,checks)).thenRun(()->{
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    var selected=results.get().selectedCut();assertNotNull(selected);
                    assertEquals(view.token(),selected.visualEvidence().get("verifiedCheckpointToken"));
                    paint();
                    displayed.put(view.token(),cut.result().resultHash());
                }));
            }
            try{var answer=method.invoke(facade,args);if(method.getName().equals("prepareOwnedProducer"))prepared.complete(null);return answer;}
            catch(InvocationTargetException error){if(method.getName().equals("prepareOwnedProducer"))prepared.completeExceptionally(error.getCause());throw error.getCause();}
        });
        SwingUtilities.invokeAndWait(()->{
            workbench.set(JaCaMoWorkbenchPanel.authoredWorkflow(proxy));
            ((JCheckBox)find(workbench.get(),"show-producer-gui")).setSelected(false);
            long[] previous={System.nanoTime()};clock=new javax.swing.Timer(25,event->{long now=System.nanoTime();heartbeat.add(TimeUnit.NANOSECONDS.toMillis(now-previous[0]));previous[0]=now;});clock.start();
            workbench.get().addNotify();
        });
    }
    void prepare(Path project)throws Exception {
        SwingUtilities.invokeAndWait(()->workbench.get().prepareOwnedProject(project));prepared.get(150,TimeUnit.SECONDS);
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);var ready=new AtomicBoolean();
        do{SwingUtilities.invokeAndWait(()->ready.set(((JButton)find(workbench.get(),"start-runtime")).isEnabled()));if(!ready.get())Thread.sleep(25);}while(!ready.get()&&System.nanoTime()<limit);
        assertTrue(ready.get());
        SwingUtilities.invokeAndWait(()->{
            results.set(ObservedConformancePanel.liveResults(facade.semanticModels().goals()));
            try{var field=JaCaMoWorkbenchPanel.class.getDeclaredField("observedConformance");field.setAccessible(true);field.set(workbench.get(),results.get());}
            catch(ReflectiveOperationException error){throw new AssertionError(error);}
            assertEquals(0,((JComboBox<?>)find(results.get(),"observed-state-selector")).getItemCount());
        });
    }
    void start()throws Exception {SwingUtilities.invokeAndWait(()->workbench.get().startRuntime());}
    void paint(){
        for(String facet:List.of("csl","istar")){
            var component=facet.equals("csl")?results.get().cslVerificationPanel():results.get().istarVerificationPanel();component.setSize(1400,820);layout(component);
            var image=images.computeIfAbsent(facet,key->new BufferedImage(1400,820,BufferedImage.TYPE_INT_RGB));var graphics=image.createGraphics();component.printAll(graphics);graphics.dispose();
        }
    }
    void receipt()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            assertEquals(0,rawHistoryReads.get());assertFalse(displayed.isEmpty());
            paint();
            for(String facet:List.of("csl","istar")){
                try{javax.imageio.ImageIO.write(images.get(facet),"png",evidence.resolve("live-"+facet+"-verification.png").toFile());}catch(Exception error){throw new AssertionError(error);}
            }
        });
        var sorted=heartbeat.stream().sorted().toList();assertTrue(sorted.size()>20);
        long p95=sorted.get((int)((sorted.size()-1)*.95)),max=sorted.getLast();assertTrue(p95<250,"EDT heartbeat p95="+p95+" max="+max);
        Files.write(evidence.resolve("live-workbench.json"),CanonicalJson.encode(Map.of("method","LIVE_OFFSCREEN_WORKBENCH_PREPARE_START_AND_VERIFICATION_CALLBACKS","manualDesktopAcceptance",false,"nativeDiagramAcceptance",false,
                "displayedNativeResults",displayed,"rawHistoryPagesRead",rawHistoryReads.get(),"heartbeatSamples",sorted.size(),"heartbeatP95Millis",p95,"heartbeatMaxMillis",max)));
    }
    public void close()throws Exception {SwingUtilities.invokeAndWait(()->{clock.stop();workbench.get().removeNotify();});}
}
