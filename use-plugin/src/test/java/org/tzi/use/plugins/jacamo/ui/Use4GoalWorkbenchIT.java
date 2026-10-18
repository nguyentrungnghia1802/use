package org.tzi.use.plugins.jacamo.ui;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.impl.PluginRuntime;
import org.tzi.use.plugins.jacamo.*;
import org.tzi.use.plugins.jacamo.bridge.*;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import static org.junit.jupiter.api.Assertions.*;

/** Real external MAS, real Swing Workbench/native diagrams and one Session. */
class Use4GoalWorkbenchIT {
    @Test void selectedOriginalEngineAndMonitorControlsKeepNativeViewsResponsive() throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless());
        var evidence=Files.createTempDirectory(Path.of("target"),"use4goal-workbench-").toAbsolutePath();
        var ready=evidence.resolve("ready");var release=evidence.resolve("release");
        var errors=new CopyOnWriteArrayList<String>();var delays=new CopyOnWriteArrayList<Long>();
        String height=System.getProperty("use.gui.view.classdiagram.class.minheight"),width=System.getProperty("use.gui.view.classdiagram.class.minwidth");
        System.setProperty("use.gui.view.classdiagram.class.minheight","40");System.setProperty("use.gui.view.classdiagram.class.minwidth","140");
        try(var producer=new ManagedProducerTestSupport(Path.of("src/test/resources/semantics/original-engine-live/monitor.jcm"),evidence,false,true,
                Map.of("use.fix.bootReady",ready.toString(),"use.fix.release",release.toString(),"use.fix.stopMas",evidence.resolve("stop-mas").toString()));
            var bootstrap=BridgeTransportFactory.localTcp().open(producer.configuration)) {
            new ContractValidator().validate(ContractCodec.decode(bootstrap.handshake(),CanonicalJson.DEFAULT_MAX_BYTES,64,262144));
            var model=ContractPayloads.model(ContractCodec.decode(bootstrap.modelSnapshot(),CanonicalJson.DEFAULT_MAX_BYTES,64,262144).payload());
            var rules=evidence.resolve("rules.json");Files.write(rules,Use4GoalExternalBridgeIT.rules(model).bytes());
            var host=new Session();producer.configureWorkflow();
            try(var facade=new DefaultJaCaMoFacade(Path.of("."),SemanticAuthority.BRIDGE,()->producer.configuration,BridgeTransportFactory.localTcp(),PipelineMode.CODE_GROUNDED_NATIVE,host)) {
                producer.clearWorkflowProperties();MainWindow.setJavaFxCall(true);
                var window=edt(()->MainWindow.create(host,PluginRuntime.getInstance()));
                var frame=edt(()->new JFrame("Original use4Goal engine acceptance"));
                var panel=edt(()->new JaCaMoWorkbenchPanel(facade,errors::add));
                var previous=new long[]{System.nanoTime()};
                var heartbeat=edt(()->new javax.swing.Timer(50,event->{long now=System.nanoTime();delays.add(Math.max(0,now-previous[0]-TimeUnit.MILLISECONDS.toNanos(50)));previous[0]=now;}));
                try {
                    edt(()->{frame.setContentPane(panel);frame.setSize(1500,1000);frame.setVisible(true);heartbeat.start();
                        component(panel,"engine-profile",JComboBox.class).setSelectedItem("USE4GOAL_OBSERVED_V1");component(panel,"mapping-specification",JTextField.class).setText(rules.toString());panel.importProject(producer.jcm);return null;});
                    enabled(panel,"start-runtime",errors);var system=host.system();
                    assertEquals("USE4GOAL_OBSERVED_V1",facade.runtimeVerificationResult().semantic().profile());assertFalse(Files.exists(producer.control.resolve("started.json")));
                    edt(()->{component(panel,"start-runtime",JButton.class).doClick();return null;});
                    await(()->Files.exists(ready),errors);enabled(panel,"pause-runtime",errors);click(panel,"pause-runtime");enabled(panel,"resume-runtime",errors);
                    assertSame(system,host.system());edt(()->{facade.showObjectDiagram();assertSame(system,window.getObjectDiagrams().getLast().system());return null;});
                    Files.writeString(release,"workload");click(panel,"resume-runtime");
                    await(()->facade.verificationSnapshot().image()!=null&&facade.verificationSnapshot().image().objects().values().stream().anyMatch(o->o.className().equals("Counter")&&"-1".equals(o.attributes().get("p"))),errors);
                    enabled(panel,"pause-runtime",errors);click(panel,"pause-runtime");enabled(panel,"resume-runtime",errors);
                    var result=facade.runtimeVerificationResult();assertEquals("CURRENT_OBSERVED",result.freshness());assertSame(system,host.system());
                    assertTrue(result.outcomes().stream().anyMatch(o->o.constraintId().startsWith("NATIVE:")&&o.outcome()==VerificationOutcome.FAIL));
                    assertTrue(facade.runtimeVerificationHistory().stream().flatMap(r->r.outcomes().stream()).anyMatch(o->o.constraintId().equals("increment:post")&&o.outcome()==VerificationOutcome.PASS));
                    edt(()->{
                        panel.refreshRuntime();var table=component(panel,"semantic-original-history",JTable.class);assertTrue(table.getRowCount()>0);
                        var core=CanonicalJson.object(result.semantic().observation().get("originalEngine"));
                        for(int row=0;row<table.getRowCount();row++) {
                            String context=(String)table.getValueAt(row,0),key=(String)table.getValueAt(row,1);
                            var source=((List<?>)core.get("contexts")).stream().map(CanonicalJson::object).filter(c->context.equals(c.get("context"))).findFirst().orElseThrow();
                            var raw=CanonicalJson.object(CanonicalJson.object(source.get("occurrences")).get(key));assertEquals(raw.getOrDefault("leafStatus","UNAVAILABLE"),table.getValueAt(row,4));assertEquals(raw.get("aggregateStatus"),table.getValueAt(row,5));assertEquals(raw.get("quality"),table.getValueAt(row,6));
                        }
                        assertEquals("true",org.tzi.use.api.UseSystemApi.create(system,false).evaluate("Counter.allInstances()->forAll(c | c.p = -1)").toString());
                        assertSame(system,window.getObjectDiagrams().getLast().system());
                        var image=new java.awt.image.BufferedImage(frame.getWidth(),frame.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();frame.printAll(graphics);graphics.dispose();javax.imageio.ImageIO.write(image,"png",evidence.resolve("workbench.png").toFile());return null;
                    });
                    enabled(panel,"stop-monitor",errors);click(panel,"stop-monitor");await(()->facade.workflowStatus().state().equals("MONITOR_STOPPED"),errors);enabled(panel,"restart-monitor",errors);
                    assertSame(system,host.system());assertTrue(producer.producerAlive());assertEquals("STALE",facade.runtimeVerificationResult().freshness());
                    edt(()->{assertEquals("true",org.tzi.use.api.UseSystemApi.create(host.system(),false).evaluate("Counter.allInstances()->size() = 1").toString());return null;});
                    click(panel,"restart-monitor");enabled(panel,"stop-monitor",errors);assertNotSame(system,host.system());assertTrue(producer.producerAlive());
                    edt(()->{facade.showObjectDiagram();assertSame(host.system(),window.getObjectDiagrams().getLast().system());return null;});
                    click(panel,"stop-monitor");enabled(panel,"restart-monitor",errors);var stopped=host.system();
                    edt(()->{panel.importProject(producer.jcm);return null;});enabled(panel,"stop-monitor",errors);assertNotSame(stopped,host.system());assertEquals("USE4GOAL_OBSERVED_V1",facade.runtimeVerificationResult().semantic().profile());
                    click(panel,"stop-monitor");enabled(panel,"restart-monitor",errors);assertTrue(errors.isEmpty(),errors.toString());
                    var sorted=delays.stream().sorted().toList();assertTrue(sorted.size()>=20);double p95=sorted.get((int)Math.ceil(sorted.size()*.95)-1)/1e6;assertTrue(p95<=250,"50ms EDT heartbeat p95 delay="+p95);
                    Files.write(Path.of("target/use4goal-workbench.json"),CanonicalJson.encode(Map.of("status","PASS","usePid",ProcessHandle.current().pid(),"jacamoPid",producer.producerPid(),"program",facade.semanticProgramEvidence(),"heartbeatSamples",sorted.size(),"heartbeatP95DelayMillis",p95,"sameSystemBeforeReimport",true,"rawEngineViewParity",true,"sourceUnchanged",producer.sourceUnchanged(),"screenshot",evidence.resolve("workbench.png").toString())));
                } finally {edt(()->{heartbeat.stop();frame.dispose();window.getObjectDiagrams().forEach(v->v.detachModel());window.dispose();MainWindow.setJavaFxCall(false);return null;});}
            }
        } finally {restore("use.gui.view.classdiagram.class.minheight",height);restore("use.gui.view.classdiagram.class.minwidth",width);}
    }
    static void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
    static void click(JaCaMoWorkbenchPanel panel,String name)throws Exception{edt(()->{component(panel,name,JButton.class).doClick();return null;});}
    static void enabled(JaCaMoWorkbenchPanel panel,String name,List<String> errors)throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);while(!edt(()->component(panel,name,JButton.class).isEnabled())&&errors.isEmpty()&&System.nanoTime()<until)Thread.sleep(25);assertTrue(edt(()->component(panel,name,JButton.class).isEnabled()),name+" "+errors);}
    static void await(java.util.function.BooleanSupplier ready,List<String> errors)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);while(!ready.getAsBoolean()&&errors.isEmpty()&&System.nanoTime()<until)Thread.sleep(25);assertTrue(ready.getAsBoolean(),errors.toString());}
    static <T>T edt(Callable<T> action)throws Exception{var task=new FutureTask<T>(action);SwingUtilities.invokeLater(task);return task.get(30,TimeUnit.SECONDS);}
    static <T extends Component>T component(Container root,String name,Class<T> type){for(var child:root.getComponents()){if(type.isInstance(child)&&name.equals(child.getName()))return type.cast(child);if(child instanceof Container nested){var found=component(nested,name,type);if(found!=null)return found;}}return null;}
}
