package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.ui.JaCaMoWorkbenchPanel;

/** Real desktop Swing + external Auction. First failed checkpoint is reviewed
 * through the actual UI button; all native/model/source semantics are unchanged. */
public class VerifiedCheckpointUiAuctionIT {
    @Test void desktopShowsEachCutBeforeRelease()throws Exception {
        Path evidence=Path.of("verification/verified-checkpoints/ui-auction-"+System.currentTimeMillis()).toAbsolutePath();Files.createDirectories(evidence);
        Path agent=evidence.resolve("source-evidence-input.jar");var manifest=new java.util.jar.Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");manifest.getMainAttributes().putValue("Premain-Class",AuctionEvidenceInputAgent.class.getName());
        try(var jar=new java.util.jar.JarOutputStream(Files.newOutputStream(agent),manifest)) {
            for(String type:List.of(AuctionEvidenceInputAgent.class.getName(),AuctionEvidenceInputAgent.class.getName()+"$Input")) {
                String resource=type.replace('.','/')+".class";try(var input=getClass().getClassLoader().getResourceAsStream(resource)){jar.putNextEntry(new java.util.jar.JarEntry(resource));jar.write(input.readAllBytes());jar.closeEntry();}
            }
        }
        var builder=new ProcessBuilder(JavaProcessSupport.executable(),"-Djava.awt.headless=false","-cp",System.getProperty("java.class.path"),getClass().getName(),evidence.toString()).redirectErrorStream(true).redirectOutput(evidence.resolve("consumer.log").toFile());
        builder.environment().put("JAVA_TOOL_OPTIONS",Objects.toString(builder.environment().get("JAVA_TOOL_OPTIONS"),"")+" \"-javaagent:"+agent+"="+evidence+"\"");
        var process=builder.start();try{assertTrue(process.waitFor(240,TimeUnit.SECONDS),evidence.toString());assertEquals(0,process.exitValue(),Files.readString(evidence.resolve("consumer.log")));}finally{if(process.isAlive())process.destroyForcibly();}
    }
    public static void main(String[] args){try{run(Path.of(args[0]));System.out.println("DESKTOP_CHECKPOINT_AUCTION_PASS");System.exit(0);}catch(Throwable failed){failed.printStackTrace();System.exit(1);}}
    static Component find(Container root,String name){for(var child:root.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container nested){var found=find(nested,name);if(found!=null)return found;}}return null;}
    static void run(Path evidence)throws Exception {
        Path checkout=Path.of("..").toRealPath(),source=checkout.getParent().resolve("jacamo/examples/auction/auction.jcm"),models=checkout.resolve("use-plugin/examples/auction");
        org.tzi.use.config.Options.processArgs(new String[]{"-H="+checkout.resolve("use-assembly/target/new"),"-nogui","-nr"});
        var hashes=VerifiedCheckpointsAuctionIT.sourceHashes(source.getParent());var session=new Session();var facade=new DefaultJaCaMoFacade(checkout,session);facade.verifiedCheckpointMode(true);
        var frame=new java.util.concurrent.atomic.AtomicReference<JFrame>();var panel=new java.util.concurrent.atomic.AtomicReference<JaCaMoWorkbenchPanel>();
        var heartbeat=new java.util.concurrent.atomic.AtomicReference<javax.swing.Timer>();var delays=new CopyOnWriteArrayList<Long>();
        try {
            facade.prepareOwnedProducer(source,new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",models.resolve("auction-mapping.xml"),models.resolve("auction.ocl"),models.resolve("auction.csl"),models.resolve("auction.istar"),false),null,false);
            var nativeSystem=session.system();
            SwingUtilities.invokeAndWait(()->{
                var view=JaCaMoWorkbenchPanel.authoredWorkflow(facade);panel.set(view);
                ((JTextField)find(view,"selected-jcm")).setText(source.toString());
                ((JTextField)find(view,"case-folder")).setText(models.toString());
                var owner=new JFrame("Verified checkpoints — live Auction acceptance");frame.set(owner);owner.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);owner.add(view);owner.setSize(1250,440);owner.setLocation(20,20);owner.setVisible(true);
                long[] last={System.nanoTime()};var timer=new javax.swing.Timer(25,e->{long now=System.nanoTime();delays.add(TimeUnit.NANOSECONDS.toMillis(now-last[0]));last[0]=now;});heartbeat.set(timer);timer.start();
                view.startRuntime();
            });
            boolean manual=false;int reviewed=0;var seen=new HashSet<String>();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(165);
            while(System.nanoTime()<end) {
                assertSame(nativeSystem,session.system());var view=facade.verifiedCheckpointState();
                if(!view.token().isBlank()&&seen.add(view.token()))System.out.println("UI_SOURCE_TOKEN "+view.token()+" "+view.state());
                var serviceField=DefaultJaCaMoFacade.class.getDeclaredField("checkpointService");serviceField.setAccessible(true);
                var service=(org.tzi.use.plugins.jacamo.codegrounded.runtime.VerifiedCheckpointService)serviceField.get(facade);
                boolean qualified=service!=null&&service.history().stream().anyMatch(v->v.state().equals("RUNNING")&&v.reason().equals("QUALIFIED_EXACT_CUT_APPROVED"));
                // Wait for an actual native-qualified source cut, rather than
                // counting transient cuts whose entity binding may be unavailable.
                if(qualified&&!Files.exists(evidence.resolve("request-evidence-input")))Files.writeString(evidence.resolve("request-evidence-input"),"Actual existing public source operations after a native-qualified cut, unchanged model");
                if(view.reviewable()) {
                    if(!manual&&view.state().equals("HELD_FAILURE")) {
                        Thread.sleep(350);assertEquals(view.token(),facade.verifiedCheckpointState().token());
                        Files.write(evidence.resolve("manual-review-ready.json"),CanonicalJson.encode(Map.of("token",view.token(),"resultHash",view.resultHash(),"useHash",view.useHash(),"state",view.state(),"request",view.request())));
                        System.out.println("MANUAL_REVIEW_READY "+view.token());
                        long wait=System.nanoTime()+TimeUnit.SECONDS.toNanos(100);
                        while(view.token().equals(facade.verifiedCheckpointState().token())&&!facade.verifiedCheckpointState().state().equals("RUNNING")&&System.nanoTime()<wait)Thread.sleep(80);
                        assertTrue(System.nanoTime()<wait,"Click the actual Acknowledge & Continue button in the results window");manual=true;reviewed++;
                        Files.writeString(evidence.resolve("manual-review-completed.txt"),view.token()+"\n"+view.resultHash());
                    }else {facade.acknowledgeVerifiedCheckpoint(view.token(),view.resultHash()).get(20,TimeUnit.SECONDS);reviewed++;}
                }
                if(manual&&seen.size()>=5)break;Thread.sleep(80);
            }
            assertTrue(manual,"A genuine native failure must be reviewed in the desktop UI");
            var displayed=new HashMap<String,String>();int automatic=0,acks=0;
            Path replay=evidence.resolve("replay");facade.exportRuntimeReplay(replay);
            try(var lines=Files.lines(replay.resolve("runtime.jsonl"))) {for(String line:(Iterable<String>)lines::iterator){
                var entry=CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));if(!"CHECKPOINT_AUDIT".equals(entry.get("kind")))continue;
                var audit=CanonicalJson.object(CanonicalJson.object(entry.get("payload")).get("checkpointAudit"));
                if("DISPLAYED".equals(audit.get("kind"))){var identity=CanonicalJson.object(CanonicalJson.object(audit.get("request")).get("identity"));displayed.put((String)identity.get("token"),(String)audit.get("resultHash"));}
                if(audit.get("receipt") instanceof Map<?,?> raw&&Boolean.TRUE.equals(raw.get("released"))){var command=CanonicalJson.object(audit.get("command"));var identity=CanonicalJson.object(command.get("identity"));assertEquals(command.get("resultHash"),displayed.get(identity.get("token")),"Every released result must already be displayed for this exact source token");if("APPROVE".equals(command.get("action")))automatic++;else acks++;}
            }}
            assertTrue(automatic>0);assertTrue(acks>0);assertTrue(displayed.size()>=3);
            var sorted=delays.stream().sorted().toList();long p95=sorted.get((int)((sorted.size()-1)*.95)),max=sorted.getLast();
            assertTrue(p95<500,"UI heartbeat p95="+p95+"ms, max="+max+"ms");
            Files.write(evidence.resolve("ui-receipt.json"),CanonicalJson.encode(Map.of("displayedCuts",displayed.size(),"automaticReleaseReceipts",automatic,"reviewedReleaseReceipts",acks,"manualDesktopReview",true,"swingHeartbeatP95Millis",p95,"swingHeartbeatMaxMillis",max,"originalAuctionUnchanged",hashes.equals(VerifiedCheckpointsAuctionIT.sourceHashes(source.getParent())),"sameMSystem",session.system()==nativeSystem)));
            assertEquals(hashes,VerifiedCheckpointsAuctionIT.sourceHashes(source.getParent()));
        }finally{facade.stopOwnedProducer();facade.close();SwingUtilities.invokeAndWait(()->{if(heartbeat.get()!=null)heartbeat.get().stop();if(frame.get()!=null)frame.get().dispose();});}
    }
}
