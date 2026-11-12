package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.ui.JaCaMoWorkbenchPanel;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;

/** Unchanged external Auction, with real source barriers and the Swing presentation path. */
public class CheckpointMilestoneAuctionIT {
    @Test void unchangedAuctionKeepsExactAuditAndIndependentCompactTimelines() throws Exception {
        Path evidence=Path.of("verification/milestone-isolation/live-"+System.currentTimeMillis()).toAbsolutePath();Files.createDirectories(evidence);
        var process=new ProcessBuilder(JavaProcessSupport.executable(),"-Djava.awt.headless=false","-cp",System.getProperty("java.class.path"),getClass().getName(),evidence.toString())
                .redirectErrorStream(true).redirectOutput(evidence.resolve("consumer.log").toFile()).start();
        try{assertTrue(process.waitFor(260,TimeUnit.SECONDS),evidence.toString());assertEquals(0,process.exitValue(),Files.readString(evidence.resolve("consumer.log")));}
        finally{if(process.isAlive())process.destroy();}
    }
    public static void main(String[] args){try{run(Path.of(args[0]));System.out.println("LIVE_MILESTONE_ISOLATION_PASS");System.exit(0);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
    static void run(Path evidence) throws Exception {
        Path checkout=Path.of("..").toRealPath(),project=checkout.getParent().resolve("jacamo/examples/auction/auction.jcm"),models=checkout.resolve("use-plugin/examples/auction");
        org.tzi.use.config.Options.processArgs(new String[]{"-H="+checkout.resolve("use-assembly/target/new"),"-nogui","-nr"});
        var original=VerifiedCheckpointsAuctionIT.sourceHashes(project.getParent());var session=new Session();var facade=new DefaultJaCaMoFacade(checkout,session);facade.verifiedCheckpointMode(true);facade.nextJournalBudgetBytes(536870912L);
        var frame=new java.util.concurrent.atomic.AtomicReference<JFrame>();var panel=new java.util.concurrent.atomic.AtomicReference<JaCaMoWorkbenchPanel>();
        try {
            facade.prepareOwnedProducer(project,new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",models.resolve("auction-compact-mapping.xml"),models.resolve("auction.ocl"),models.resolve("auction.csl"),models.resolve("auction.istar"),false),null,false);
            var system=session.system();
            SwingUtilities.invokeAndWait(()->{
                var view=JaCaMoWorkbenchPanel.authoredWorkflow(facade);panel.set(view);var window=new JFrame("Checkpoint milestone isolation — Auction regression");frame.set(window);
                window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);window.add(view);window.setSize(1250,440);window.setVisible(true);view.startRuntime();
            });
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(200);
            while(!CheckpointStabilityAuctionIT.complete(facade)&&System.nanoTime()<end){
                assertSame(system,session.system());assertFalse(facade.verifiedCheckpointState().reviewable(),facade.verifiedCheckpointState().reason());Thread.sleep(100);
            }
            assertTrue(CheckpointStabilityAuctionIT.complete(facade),"Both unchanged native executions must complete automatically");
            var navigation=new LinkedHashMap<String,Object>();boolean complete=false;end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(!complete&&System.nanoTime()<end){Thread.sleep(250);var ok=new java.util.concurrent.atomic.AtomicBoolean(true);
                SwingUtilities.invokeAndWait(()->{
                    var tabs=CheckpointStabilityAuctionIT.tabs(panel.get());var results=CheckpointStabilityAuctionIT.find(tabs,"use4goal-live-results",ObservedConformancePanel.class);
                    var selector=CheckpointStabilityAuctionIT.find(tabs,"execution-scope-selector",JComboBox.class);if(selector.getItemCount()!=5){ok.set(false);return;}
                    for(int i=0;i<selector.getItemCount();i++){
                        selector.setSelectedIndex(i);var states=CheckpointStabilityAuctionIT.find(tabs,"observed-state-selector",JComboBox.class);
                        String scope=results.executionScope();var cut=results.selectedCut();if(cut==null||!ExecutionScopeViews.goalVerdict(cut).equals("COMPLETE_OBSERVED")){ok.set(false);continue;}
                        int count=states.getItemCount();String id=cut.id();
                        for(int j=0;j<3;j++){tabs.setSelectedIndex(1);tabs.setSelectedIndex(2);tabs.setSelectedIndex(0);assertEquals(count,states.getItemCount());assertEquals(id,results.selectedCut().id());}
                        CheckpointStabilityAuctionIT.find(tabs,"observed-audit-toggle",JToggleButton.class).doClick();int boundaries=states.getItemCount();assertTrue(boundaries>=count);
                        CheckpointStabilityAuctionIT.find(tabs,"observed-milestones",JButton.class).doClick();assertEquals(count,states.getItemCount());
                        navigation.put(scope,Map.of("milestones",count,"auditBoundaries",boundaries,"verdict",ExecutionScopeViews.goalVerdict(results.selectedCut()),"selectedCut",results.selectedCut().id()));
                    }
                });complete=ok.get();
            }
            assertTrue(complete,"Both compact timelines must show their exact final native completion");
            facade.exportRuntimeReplay(evidence.resolve("replay"));assertEquals(original,VerifiedCheckpointsAuctionIT.sourceHashes(project.getParent()));
            int releases=0;var displayed=new HashMap<String,String>();
            try(var lines=Files.lines(evidence.resolve("replay/runtime.jsonl"))){for(String line:(Iterable<String>)lines::iterator){
                if(!line.contains("\"CHECKPOINT_AUDIT\""))continue;
                var row=CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                if(!row.get("kind").equals("CHECKPOINT_AUDIT"))continue;
                var audit=CanonicalJson.object(CanonicalJson.object(row.get("payload")).get("checkpointAudit"));
                if("DISPLAYED".equals(audit.get("kind"))){var identity=CanonicalJson.object(CanonicalJson.object(audit.get("request")).get("identity"));displayed.put(identity.get("token").toString(),audit.get("resultHash").toString());}
                if(audit.get("receipt") instanceof Map<?,?> receipt&&Boolean.TRUE.equals(receipt.get("released"))){
                    var command=CanonicalJson.object(audit.get("command"));var identity=CanonicalJson.object(command.get("identity"));
                    assertEquals("APPROVE",command.get("action"));assertEquals(command.get("resultHash"),displayed.get(identity.get("token")));releases++;
                }
            }}
            assertTrue(releases>0,"This must exercise verified source checkpoints, not passive observation");
            Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(Map.of("nativeRootsFulfilled",2,"sameMSystem",true,"originalAuctionUnchanged",true,"manualAcknowledgement",false,"automaticReleases",releases,"displayedSourceCuts",displayed.size(),"navigation",navigation)));
        } finally {facade.stopOwnedProducer();facade.close();SwingUtilities.invokeAndWait(()->{if(frame.get()!=null)frame.get().dispose();});}
    }
}
