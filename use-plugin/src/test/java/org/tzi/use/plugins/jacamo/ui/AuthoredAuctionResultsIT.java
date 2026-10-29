package org.tzi.use.plugins.jacamo.ui;
import java.nio.file.*;import java.util.*;import java.util.concurrent.TimeUnit;
import javax.swing.*;import org.junit.jupiter.api.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.main.Session;import org.tzi.use.plugins.jacamo.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import static org.junit.jupiter.api.Assertions.*;
class AuthoredAuctionResultsIT {
 @Test @Timeout(420) void fullAuthoredAuctionKeepsFixedModelRowsAndReportsRealConstraintsEvenAfterWinner()throws Exception {
  Path checkout=Path.of("..").toAbsolutePath().normalize(),project=checkout.getParent().resolve("jacamo/examples/auction/auction.jcm"),inputs=checkout.resolve("use-plugin/src/test/resources/auction-reference/authored-v1");
  Path evidence=Files.createTempDirectory(Files.createDirectories(checkout.resolve("use-plugin/verification/live-results-ui")),"auction-");
  byte[] source=Files.readAllBytes(project);var session=new Session();long pid=0;
  try(var facade=new DefaultJaCaMoFacade(checkout,session)){
   try{
    var selection=new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",inputs.resolve("auction-mapping.xml"),inputs.resolve("auction_constraints_final_fixed.ocl"),inputs.resolve("auction_csl.csl"),inputs.resolve("auction_istar_final.istar"),false);
    facade.prepareOwnedProducer(project,selection,null,false);pid=facade.ownedProducerStatus().pid();var system=session.system();assertNotEquals(ProcessHandle.current().pid(),pid);
    var definitions=new LiveGoalSummary(facade.semanticModels().goals());int expected=definitions.rows(List.of()).size();assertTrue(expected>0);
    var view=new java.util.concurrent.atomic.AtomicReference<ObservedConformancePanel>();SwingUtilities.invokeAndWait(()->view.set(ObservedConformancePanel.liveResults(facade.semanticModels().goals())));
    var presenter=new ObservedConformancePresenter(facade);facade.startRuntime();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(180);boolean finished=false;int samples=0;
    while(System.nanoTime()<deadline){assertSame(system,session.system());assertEquals(org.tzi.use.plugins.jacamo.bridge.BridgeClientState.LIVE,facade.authorityStatus().readiness(),facade.status());assertFalse(coordinator(facade).journal().hasGap(),coordinator(facade).journal().diagnostic());var cuts=presenter.captureCurrent();SwingUtilities.invokeAndWait(()->{view.get().setCuts(cuts);assertEquals(expected,DetachedAuthoredWorkflowTest.find(view.get(),"live-goal-status",JTable.class).getRowCount());});samples++;if(samples%10==0)diagnostics(facade,evidence);
     finished=systemRead(facade,()->{var state=system.state();var auctions=state.allObjects().stream().filter(o->o.cls().name().equals("Auction")).toList();return auctions.size()==2&&auctions.stream().allMatch(o->o.state(state).attributeValue("running").toString().equals("false")&&!o.state(state).attributeValue("winner").isUndefined()&&!o.state(state).attributeValue("winner").toString().equals("'no_winner'"));});if(finished)break;Thread.sleep(700);
    }assertTrue(finished,"Two actual source auctions must finish");
    // The observable winner precedes the Jason terminal marker. Wait for the
    // actual two certified stop receipts; console/domain completion is not a
    // verification barrier and must not create a premature final UI receipt.
    long stopDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
    while(!stopReceiptsComplete(facade)&&System.nanoTime()<stopDeadline)Thread.sleep(200);
    assertTrue(stopReceiptsComplete(facade),"Both source stop terminal receipts must be processed before freezing final verification");
    var coordinator=coordinator(facade);var result=completedObserverCut(facade,coordinator);assertNotNull(result.semantic());
    Files.write(evidence.resolve("result.json"),CanonicalJson.encode(result.toMap()));
    var objects=coordinator.read(()->system.state().allObjects().stream().filter(o->Set.of("Auction","AuctionMonitor","AuctionObligation").contains(o.cls().name())).map(o->{var fields=new TreeMap<String,String>();o.state(system.state()).attributeValueMap().forEach((a,v)->fields.put(a.name(),v.toString()));return Map.of("class",o.cls().name(),"object",o.name(),"fields",fields);}).toList());Files.write(evidence.resolve("domain-and-observer-state.json"),CanonicalJson.encode(objects));
    var browsable=presenter.capture();assertTrue(browsable.size()>2);
    // Pin result.json and UI summary to the SAME completed native cut. A new
    // live pending boundary must not be substituted for this verified receipt.
    var pinned=browsable.stream().filter(c->c.trace().contains("Result hash: "+result.resultHash())).findFirst().orElseThrow();
    var rows=definitions.rows(pinned.evaluations());assertEquals(expected,rows.size());
    assertTrue(pinned.selectedRequirements(),"Selected-requirement policy must come from the production runtime result");
    var declaredIds=new HashSet<String>();definitions.rows(List.of()).forEach(r->declaredIds.add(r.id()));
    assertTrue(pinned.evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")).allMatch(r->declaredIds.contains(r.element())),"Resources cannot become hidden Goal/Task UNKNOWN requirements");
    for(String clause:List.of("ObservedBidEvidence","ConfirmedSchemeOrder"))assertTrue(result.semantic().constraints().stream().anyMatch(c->c.id().endsWith("::"+clause)&&c.kind().equals("INVARIANT")&&Set.of("PASS","FAIL").contains(c.verdict().name())),"Actual source observer contract must execute, not stay permanently SKIPPED: "+clause);
    var finalVerdict=new java.util.concurrent.atomic.AtomicReference<String>();SwingUtilities.invokeAndWait(()->{view.get().setCuts(List.of(pinned));finalVerdict.set(DetachedAuthoredWorkflowTest.find(view.get(),"observed-verdict",JLabel.class).getText());});
    Files.write(evidence.resolve("selected-requirements.json"),CanonicalJson.encode(Map.of("verdict",finalVerdict.get(),"requirements",pinned.requirements(),"overallCoverage",pinned.coverage(),"selectedRequirements",pinned.selectedRequirements(),"goalStatuses",rows.stream().map(r->Map.of("id",r.id(),"status",r.status())).toList())));

    assertEquals("FULFILLED",rows.stream().filter(r->r.id().equals("O_bid")).findFirst().orElseThrow().status(),"Declared ANY must certify the organization using a real successful participant, not require all participants");
    var core=CanonicalJson.object(result.semantic().observation().get("originalEngine"));
    var originalRoots=((List<?>)core.get("contexts")).stream().map(CanonicalJson::object).flatMap(c->CanonicalJson.object(c.getOrDefault("occurrences",Map.of())).values().stream()).map(CanonicalJson::object).filter(o->"O_root".equals(o.get("sourceElement"))&&"SOURCE_ELEMENT".equals(o.get("expansionKind"))).toList();
    assertFalse(originalRoots.isEmpty());var rootStatus=rows.stream().filter(r->r.id().equals("O_root")).findFirst().orElseThrow().status();
    if(originalRoots.stream().allMatch(o->"FULFILLED".equals(o.get("aggregateStatus"))))assertEquals("FULFILLED",rootStatus,"All certified branches of the real source root must qualify");
    else assertNotEquals("FULFILLED",rootStatus,"Winner cannot fabricate an original root completion when a required stop checkpoint is missing");
    var taskDiagnostics=facade.runtimeVerificationHistory().stream().filter(r->r.semantic()!=null).flatMap(r->r.semantic().constraints().stream()).filter(c->c.kind().startsWith("TASK_")).map(c->Map.of("id",c.id(),"kind",c.kind(),"verdict",c.verdict().name(),"diagnostic",c.diagnostic(),"invocation",c.invocation(),"witness",c.witness())).distinct().toList();
    Files.write(evidence.resolve("task-checkpoint-diagnostics.json"),CanonicalJson.encode(taskDiagnostics));
    var milestones=BusinessTimeline.summarize(browsable);
    assertTrue(milestones.size()<=30,"This bounded 2-auction/4-participant source must not generate hundreds of domain milestones: "+milestones.size());
    assertTrue(milestones.size()*4<browsable.size(),"Full audit must remain available independently of the small business projection");
    assertFalse(pinned.executionMilestones());
    assertTrue(milestones.stream().allMatch(c->!c.businessKey().isBlank()&&!c.businessState().isBlank()),"DOMAIN milestones must have admitted selected CSL projections");
    assertTrue(milestones.stream().noneMatch(c->c.businessState().contains("!new ")||c.businessState().contains("!insert ")),"Default state preview must not be raw infrastructure SOIL");
    var terminalRows=pinned.evaluations().stream().filter(c->c.kind().equals("GOAL_TASK")&&c.status().equals("FAILED")).toList();
    assertTrue(terminalRows.stream().allMatch(c->c.quality().equals("KNOWN_HISTORY")&&c.evidence().contains("CERTIFIED_TERMINAL_EXECUTION/1.0")&&!c.originalStatus().equals("FULFILLED")),"FAILED must reference a terminal receipt, never raw history or a success certificate");
    assertTrue(terminalRows.isEmpty(),"Source deliberate decline is an explicitly accepted attempt; unexpected failures must be investigated, not auto-violated");
    assertTrue(result.semantic().constraints().stream().noneMatch(c->c.kind().equals("EXECUTION_OUTCOME")&&c.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL));
    assertEquals("FULFILLED",rows.stream().filter(r->r.id().equals("O_bid_accepted")).findFirst().orElseThrow().status(),"Actual Artifact acceptance is required independently of attempt completion");
    assertEquals("FULFILLED",rootStatus,"Both completed source auctions must have certified original-engine roots");
    boolean nativeViolation=result.semantic().constraints().stream().anyMatch(c->Set.of("INVARIANT","PRE","POST","INVOCATION_CONFORMANCE").contains(c.kind())&&c.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL);
    assertEquals(nativeViolation?"VIOLATION":"COMPLETE_OBSERVED",finalVerdict.get(),"No failures may be hidden; only selected original root plus mandatory native contracts define completion");
    Files.write(evidence.resolve("terminal-outcomes.json"),CanonicalJson.encode(Map.of("failedOccurrences",terminalRows.size(),"rows",terminalRows.stream().map(c->Map.of("element",c.element(),"status",c.status(),"original",c.originalStatus(),"evidence",c.evidence())).toList(),"note","Declared attempt semantics v3. Actual simulated .fail is recorded separately, never fabricated as accepted Artifact bid. Deterministic XmlTaskCheckpointsTest covers both accepted and unrelated failures.")));

    var delays=facade.runtimeVerificationHistory().stream().filter(r->r.semantic()!=null).mapToLong(r->Math.max(0,java.time.Duration.between(r.observedAt(),r.verifiedAt()).toMillis())).sorted().toArray();
    Files.write(evidence.resolve("essential-states.json"),CanonicalJson.encode(Map.of("milestones",milestones.size(),"auditBoundaries",browsable.size(),"completionDelayMaxMs",delays.length==0?0:delays[delays.length-1],"completionDelayLatestVerifiedMs",Math.max(0,java.time.Duration.between(result.observedAt(),result.verifiedAt()).toMillis()),"pinnedResultHash",result.resultHash(),"presentationMode","DOMAIN","optionalBeliefsAcquired",false,"latestVerifiedEventSource",result.sourceId(),"latestVerifiedEventId",result.eventId())));

    assertTrue(result.semantic().constraints().stream().anyMatch(c->c.kind().startsWith("INVARIANT")&&Set.of("PASS","FAIL","SKIPPED").contains(c.verdict().name())),"Native authored requirements must actually be classified");
    Files.write(evidence.resolve("goal-summary.json"),CanonicalJson.encode(rows.stream().map(r->Map.of("id",r.id(),"label",r.label(),"status",r.status(),"detail",r.detail())).toList()));
    var checks=result.semantic().constraints().stream().filter(c->c.contextClass().equals("AuctionObligation")||c.contextClass().equals("AuctionMonitor")).map(c->Map.of("id",c.id(),"object",c.contextObject(),"expression",c.expression(),"verdict",c.verdict().name(),"diagnostic",c.diagnostic(),"witness",c.witness())).toList();Files.write(evidence.resolve("observer-checks.json"),CanonicalJson.encode(checks));
    assertTrue(browsable.stream().anyMatch(c->c.evaluations().stream().anyMatch(r->r.originalStatus().equals("FULFILLED"))),"Original engine history must be visible, separately from current marker availability");
    var historicalProofs=browsable.stream().flatMap(c->c.evaluations().stream()).filter(r->r.kind().equals("GOAL_TASK")&&r.status().equals("FULFILLED")&&Set.of("KNOWN_HISTORY","KNOWN_DERIVED_HISTORY").contains(r.quality())).toList();
    assertFalse(historicalProofs.isEmpty(),"Real completed Auction steps must retain certified execution after the current marker disappears");
    assertTrue(historicalProofs.stream().allMatch(r->r.evidence().contains("CERTIFIED_EXECUTION/1.0")),"Held fulfillment must reference actual persisted runtime certificates");
    Files.write(evidence.resolve("certified-execution.json"),CanonicalJson.encode(Map.of("policy","CERTIFIED_EXECUTION/1.0","qualifiedHistoricalRows",historicalProofs.size(),"samples",historicalProofs.stream().limit(12).map(r->Map.of("element",r.element(),"quality",r.quality(),"status",r.status(),"evidence",r.evidence())).toList())));
    assertTrue(facade.runtimeHistoryPage(0,128).entries().stream().anyMatch(e->!e.stateCommands().isBlank()),"Actual frozen state commands must be persisted for browsing");
    SwingUtilities.invokeAndWait(()->{view.get().setCuts(browsable);var previous=DetachedAuthoredWorkflowTest.find(view.get(),"observed-previous",JButton.class);assertNotNull(previous);assertTrue(previous.isEnabled());previous.doClick();assertNotNull(view.get().selectedCut());assertEquals(expected,DetachedAuthoredWorkflowTest.find(view.get(),"live-goal-status",JTable.class).getRowCount());});
    facade.exportRuntimeReplay(evidence.resolve("replay"));
    facade.stopOwnedProducer();assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));assertSame(system,session.system());assertArrayEquals(source,Files.readAllBytes(project));
    var replay=facade.replayRuntime(evidence.resolve("replay"));assertTrue(replay.complete(),replay.toString());Files.write(evidence.resolve("replay-receipt.json"),CanonicalJson.encode(Map.of("status","PASS","entries",replay.checkedEntries())));
    Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(Map.of("status","PASS","pid",pid,"sameMSystem",true,"fixedGoalTaskRows",expected,"samples",samples,"winnerObservedForTwoArtifacts",true,"coverage",result.coverage(),"domainFailures",result.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL),"scope","Audit, not assertion that full normative profile is satisfied")));
   }finally{diagnostics(facade,evidence);
    var owned=facade.ownedProducerStatus();
    Path preparedRoot=checkout.resolve("use-plugin/target/owned-runtime");
    if(Files.isDirectory(preparedRoot))try(var dirs=Files.list(preparedRoot)){for(var dir:dirs.toList()){var manifest=dir.resolve("prepared.json");if(!Files.isRegularFile(manifest)||!Files.readString(manifest).contains(owned.runId()))continue;for(String name:List.of("producer.stdout.log","producer.stderr.log"))if(Files.isRegularFile(dir.resolve(name)))Files.copy(dir.resolve(name),evidence.resolve(name),StandardCopyOption.REPLACE_EXISTING);}}
    facade.stopOwnedProducer();if(pid!=0)assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));System.out.println("AUTHORED_AUCTION_RESULTS_EVIDENCE="+evidence);}
  }
 }
 private static boolean stopReceiptsComplete(DefaultJaCaMoFacade facade){
  var history=facade.runtimeVerificationHistory();
  for(int i=history.size()-1;i>=0;i--){var r=history.get(i);if(r.semantic()==null||!(r.semantic().observation().get("originalEngine") instanceof Map<?,?> value))continue;var core=CanonicalJson.object(value);if(!"PINNED_NATIVE_CUT".equals(core.get("evaluated")))continue;
   var stops=((List<?>)core.getOrDefault("contexts",List.of())).stream().map(CanonicalJson::object).flatMap(c->CanonicalJson.object(c.getOrDefault("occurrences",Map.of())).values().stream()).map(CanonicalJson::object).filter(o->"A_decide_t2".equals(o.get("sourceElement"))&&"SOURCE_ELEMENT".equals(o.get("expansionKind"))).toList();
   if(stops.size()==2&&stops.stream().allMatch(o->"FULFILLED".equals(o.get("aggregateStatus"))&&Set.of("KNOWN","KNOWN_HISTORY").contains(o.get("executionQuality"))))return true;
  }return false;
 }
 private static void diagnostics(DefaultJaCaMoFacade facade,Path evidence)throws Exception{
  var c=coordinatorOrNull(facade);if(c==null)return;var latest=facade.runtimeVerificationResult();if(latest!=null)Files.write(evidence.resolve("last-result.json"),CanonicalJson.encode(latest.toMap()));
  var state=c.read(()->c.system().state().allObjects().stream().filter(o->Set.of("Auction","AuctionMonitor","AuctionObligation").contains(o.cls().name())).map(o->{var fields=new TreeMap<String,String>();o.state(c.system().state()).attributeValueMap().forEach((a,v)->fields.put(a.name(),v.toString()));return Map.of("class",o.cls().name(),"object",o.name(),"fields",fields);}).toList());Files.write(evidence.resolve("last-state.json"),CanonicalJson.encode(state));
  Files.write(evidence.resolve("transport.json"),CanonicalJson.encode(Map.of("status",facade.status(),"authority",facade.authorityStatus().toString(),"journalGap",c.journal().hasGap(),"journalDiagnostic",c.journal().diagnostic(),"journalBytes",c.journal().persistedBytes(),"journalQuota",c.journal().maxBytes(),"performance",c.performanceMetrics())));
 }
 private static RuntimeVerificationCoordinator coordinator(DefaultJaCaMoFacade facade)throws Exception {return Objects.requireNonNull(coordinatorOrNull(facade),"An active coordinator is required");}
 private static RuntimeVerificationCoordinator coordinatorOrNull(DefaultJaCaMoFacade facade)throws Exception {var field=DefaultJaCaMoFacade.class.getDeclaredField("nativeWorkspace");field.setAccessible(true);var workspace=field.get(facade);if(workspace==null)return null;var projector=workspace.getClass().getDeclaredField("runtimeProjector");projector.setAccessible(true);var active=(NativeRuntimeProjector)projector.get(workspace);return active==null?null:active.coordinator();}
 private static <T>T systemRead(DefaultJaCaMoFacade facade,java.util.concurrent.Callable<T> read)throws Exception{return coordinator(facade).read(read);}

 private static RuntimeVerificationResult completedObserverCut(DefaultJaCaMoFacade facade,RuntimeVerificationCoordinator coordinator)throws Exception {
  var result=coordinator.awaitEvaluation(coordinator.manualVerify(),60000);
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
  // Manual snapshot may be partial while already admitted source events are still
  // being verified. Wait for a completed current cut, never specifically for PASS.
  while(System.nanoTime()<deadline){
   var snapshot=facade.verificationSnapshot();
   if(result!=null&&result.semantic()!=null&&snapshot.currentVersion()==result.stateVersion()
      &&observerEvaluated(result,"ObservedBidEvidence")&&observerEvaluated(result,"ConfirmedSchemeOrder"))return result;
   Thread.sleep(200);result=facade.runtimeVerificationResult();
  }
  throw new AssertionError("No completed current cut evaluated both source observer contracts within deadline");
 }
 private static boolean observerEvaluated(RuntimeVerificationResult result,String name){return result.semantic().constraints().stream().anyMatch(c->c.id().endsWith("::"+name)&&c.kind().equals("INVARIANT")&&Set.of("PASS","FAIL").contains(c.verdict().name()));}
}
