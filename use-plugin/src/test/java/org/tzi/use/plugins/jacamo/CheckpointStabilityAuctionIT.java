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
import org.tzi.use.plugins.jacamo.ui.*;
/** Full unchanged Auction completion, independent UI histories, then repeated
 * real invalid operations to exercise the same desktop review button twice. */
public class CheckpointStabilityAuctionIT {
 @Test void bothExecutionsCompleteAndDesktopReviewRemainsReusable()throws Exception {
  Path evidence=Path.of("verification/checkpoint-stability/live-"+System.currentTimeMillis()).toAbsolutePath();Files.createDirectories(evidence);
  Path agent=evidence.resolve("source-evidence-input.jar");var manifest=new java.util.jar.Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");manifest.getMainAttributes().putValue("Premain-Class",AuctionEvidenceInputAgent.class.getName());
  try(var jar=new java.util.jar.JarOutputStream(Files.newOutputStream(agent),manifest)){for(String type:List.of(AuctionEvidenceInputAgent.class.getName(),AuctionEvidenceInputAgent.class.getName()+"$Input")){String resource=type.replace('.','/')+".class";try(var input=getClass().getClassLoader().getResourceAsStream(resource)){jar.putNextEntry(new java.util.jar.JarEntry(resource));jar.write(input.readAllBytes());jar.closeEntry();}}}
  var builder=new ProcessBuilder(JavaProcessSupport.executable(),"-Djava.awt.headless=false","-cp",System.getProperty("java.class.path"),getClass().getName(),evidence.toString()).redirectErrorStream(true).redirectOutput(evidence.resolve("consumer.log").toFile());
  builder.environment().put("JAVA_TOOL_OPTIONS",Objects.toString(builder.environment().get("JAVA_TOOL_OPTIONS"),"")+" \"-javaagent:"+agent+"="+evidence+"\"");
  var process=builder.start();try{assertTrue(process.waitFor(600,TimeUnit.SECONDS),evidence.toString());assertEquals(0,process.exitValue(),Files.readString(evidence.resolve("consumer.log")));}finally{if(process.isAlive())process.destroyForcibly();}
 }
 public static void main(String[] args){try{run(Path.of(args[0]));System.out.println("CHECKPOINT_STABILITY_AUCTION_PASS");System.exit(0);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
 static <T extends Component>T find(Container root,String name,Class<T> type){var value=VerifiedCheckpointUiAuctionIT.find(root,name);assertNotNull(value,name);return type.cast(value);}
 static JTabbedPane tabs(JaCaMoWorkbenchPanel panel){try{var field=JaCaMoWorkbenchPanel.class.getDeclaredField("tabs");field.setAccessible(true);return (JTabbedPane)field.get(panel);}catch(Exception failure){throw new AssertionError(failure);}}
 static List<Map<String,Object>> contexts(DefaultJaCaMoFacade facade){var result=facade.verificationSnapshot().result();if(result==null||result.semantic()==null)return List.of();return ((List<?>)CanonicalJson.object(result.semantic().observation().getOrDefault("originalEngine",Map.of())).getOrDefault("contexts",List.of())).stream().map(CanonicalJson::object).toList();}
 static boolean complete(DefaultJaCaMoFacade facade){var values=contexts(facade);return values.size()==2&&values.stream().allMatch(c->CanonicalJson.object(c.getOrDefault("occurrences",Map.of())).values().stream().map(CanonicalJson::object).anyMatch(o->"O_root".equals(o.get("sourceElement"))&&"FULFILLED".equals(o.get("aggregateStatus"))&&Set.of("KNOWN_DERIVED","KNOWN_DERIVED_HISTORY").contains(Objects.toString(o.get("executionQuality"),""))));}
 static void run(Path evidence)throws Exception {
  Path checkout=Path.of("..").toRealPath(),project=checkout.getParent().resolve("jacamo/examples/auction/auction.jcm"),models=checkout.resolve("use-plugin/examples/auction");
  org.tzi.use.config.Options.processArgs(new String[]{"-H="+checkout.resolve("use-assembly/target/new"),"-nogui","-nr"});
  var original=VerifiedCheckpointsAuctionIT.sourceHashes(project.getParent());var session=new Session();var facade=new DefaultJaCaMoFacade(checkout,session);facade.verifiedCheckpointMode(true);facade.nextJournalBudgetBytes(768L*1024*1024);
  var owner=new java.util.concurrent.atomic.AtomicReference<JFrame>();var panel=new java.util.concurrent.atomic.AtomicReference<JaCaMoWorkbenchPanel>();
  var timer=new java.util.concurrent.atomic.AtomicReference<javax.swing.Timer>();var heartbeat=new CopyOnWriteArrayList<Long>();
  try {
   facade.prepareOwnedProducer(project,new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",models.resolve("auction-mapping.xml"),models.resolve("auction.ocl"),models.resolve("auction.csl"),models.resolve("auction.istar"),false),checkout.resolve("jacamo-bridge-jacamo/target/jacamo-bridge-classpath.txt"),false);
   var system=session.system();
   SwingUtilities.invokeAndWait(()->{var view=JaCaMoWorkbenchPanel.authoredWorkflow(facade);panel.set(view);var window=new JFrame("Checkpoint stability — full Auction");owner.set(window);window.add(view);window.setSize(1250,440);window.setLocation(20,20);window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);window.setVisible(true);long[] last={System.nanoTime()};var beat=new javax.swing.Timer(25,e->{long now=System.nanoTime();heartbeat.add(TimeUnit.NANOSECONDS.toMillis(now-last[0]));last[0]=now;});timer.set(beat);beat.start();view.startRuntime();});
   // Per-Task verification deliberately adds intermediate cuts. Keep the exact
   // native completion assertion, with a bounded budget for their evaluation.
   long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(300);String token="";
   while(System.nanoTime()<end&&!complete(facade)){
    assertSame(system,session.system());var state=facade.verifiedCheckpointState();if(!token.equals(state.token())){token=state.token();System.out.println("SOURCE_TOKEN "+token+" "+state.checkpoint());}
    if(state.reviewable()){Files.write(evidence.resolve("unexpected-hold.json"),CanonicalJson.encode(Map.of("view",Map.of("token",state.token(),"reason",state.reason(),"request",state.request()),"native",facade.verificationSnapshot().toMap())));throw new AssertionError("Unmodified initialization/domain run held: "+state.reason());}
    Thread.sleep(100);
   }
   if(!complete(facade)){var held=facade.verifiedCheckpointState();Files.write(evidence.resolve("completion-timeout.json"),CanonicalJson.encode(Map.of("state",held.state(),"reason",held.reason(),"request",held.request(),"native",facade.verificationSnapshot().toMap())));}
   assertTrue(complete(facade),"Both exact native roots must finish");System.out.println("BOTH_NATIVE_ROOTS_FULFILLED");
   Files.write(evidence.resolve("completed-native.json"),CanonicalJson.encode(facade.verificationSnapshot().toMap()));
   // Allow the current accepted native completion to reach the view, without
   // manufacturing a new source transition or altering its verdict.
   var navigation=new LinkedHashMap<String,Object>();long uiEnd=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);boolean uiComplete=false;
   while(!uiComplete&&System.nanoTime()<uiEnd){Thread.sleep(250);var ok=new java.util.concurrent.atomic.AtomicBoolean(true);
    SwingUtilities.invokeAndWait(()->{var tabs=tabs(panel.get());var results=find(tabs,"use4goal-live-results",org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.class);var selector=find(tabs,"execution-scope-selector",JComboBox.class);int checked=0;for(int i=0;i<selector.getItemCount();i++){if(org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ExecutionScopeViews.ALL.equals(selector.getItemAt(i)))continue;checked++;selector.setSelectedIndex(i);String scope=results.executionScope();int count=find(tabs,"observed-state-selector",JComboBox.class).getItemCount();String id=results.selectedCut()==null?"":results.selectedCut().id();String verdict=find(tabs,"observed-verdict",JLabel.class).getText();if(!verdict.equals("COMPLETE_OBSERVED")){ok.set(false);continue;}for(int j=0;j<3;j++){tabs.setSelectedIndex(1);tabs.setSelectedIndex(2);tabs.setSelectedIndex(0);assertEquals(count,find(tabs,"observed-state-selector",JComboBox.class).getItemCount());assertEquals(scope,results.executionScope());assertEquals(id,results.selectedCut().id());}navigation.put(scope,Map.of("states",count,"selectedCut",id,"verdict",verdict,"state",results.selectedCut().businessState()));}if(checked!=2)ok.set(false);});uiComplete=ok.get();
   }
   assertTrue(uiComplete,"Both executions must display their own final native result");Files.write(evidence.resolve("navigation.json"),CanonicalJson.encode(navigation));
   Files.writeString(evidence.resolve("request-evidence-input"),"Late invalid public stop operations after both native roots fulfilled");
   var reviewed=new ArrayList<String>();end=System.nanoTime()+TimeUnit.SECONDS.toNanos(150);
   while(reviewed.size()<2&&System.nanoTime()<end){var state=facade.verifiedCheckpointState();if(state.reviewable()&&!reviewed.contains(state.token())){
    assertEquals("HELD_FAILURE",state.state());String hash=state.resultHash();
    Files.write(evidence.resolve("review-ready-"+reviewed.size()+".json"),CanonicalJson.encode(Map.of("token",state.token(),"resultHash",hash,"request",state.request())));
    System.out.println("DESKTOP_REVIEW_READY "+reviewed.size()+" "+state.token());
    long wait=System.nanoTime()+TimeUnit.SECONDS.toNanos(75);while(state.token().equals(facade.verifiedCheckpointState().token())&&!facade.verifiedCheckpointState().state().equals("RUNNING")&&System.nanoTime()<wait)Thread.sleep(80);
    assertTrue(System.nanoTime()<wait,"Click Acknowledge & Continue again for each failed token");reviewed.add(state.token());
   }Thread.sleep(100);}
   assertEquals(2,reviewed.size());Path replay=evidence.resolve("replay");facade.exportRuntimeReplay(replay);
   int approvals=0,acks=0;var displayed=new HashMap<String,String>();try(var lines=Files.lines(replay.resolve("runtime.jsonl"))){for(String line:(Iterable<String>)lines::iterator){var row=CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));if(!row.get("kind").equals("CHECKPOINT_AUDIT"))continue;var audit=CanonicalJson.object(CanonicalJson.object(row.get("payload")).get("checkpointAudit"));if("DISPLAYED".equals(audit.get("kind"))){var identity=CanonicalJson.object(CanonicalJson.object(audit.get("request")).get("identity"));displayed.put((String)identity.get("token"),(String)audit.get("resultHash"));}if(audit.get("receipt") instanceof Map<?,?> receipt&&Boolean.TRUE.equals(receipt.get("released"))){var command=CanonicalJson.object(audit.get("command"));var identity=CanonicalJson.object(command.get("identity"));assertEquals(command.get("resultHash"),displayed.get(identity.get("token")));if("APPROVE".equals(command.get("action")))approvals++;else acks++;}}}
   assertTrue(approvals>0);assertEquals(2,acks);assertEquals(original,VerifiedCheckpointsAuctionIT.sourceHashes(project.getParent()));var sorted=heartbeat.stream().sorted().toList();long p95=sorted.get((int)((sorted.size()-1)*.95));assertTrue(p95<500,"UI heartbeat p95 "+p95);
   Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(Map.of("nativeRootsFulfilled",2,"uiExecutionsComplete",2,"automaticReleases",approvals,"desktopReviewedReleases",acks,"distinctReviewedTokens",reviewed,"displayedCuts",displayed.size(),"swingP95Millis",p95,"swingMaxMillis",sorted.getLast(),"originalAuctionUnchanged",true,"sameMSystem",true)));
  }finally{facade.stopOwnedProducer();facade.close();SwingUtilities.invokeAndWait(()->{if(timer.get()!=null)timer.get().stop();if(owner.get()!=null)owner.get().dispose();});}
 }
}
