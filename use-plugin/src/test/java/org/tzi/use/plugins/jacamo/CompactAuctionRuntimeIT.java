package org.tzi.use.plugins.jacamo;
import java.nio.file.*;import java.util.*;import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;import org.tzi.use.main.Session;import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import static org.junit.jupiter.api.Assertions.*;
class CompactAuctionRuntimeIT {
 @Test @Timeout(300) void externalAuctionUsesCompactReadSetAndOriginalGoalEngineWithShortBusinessTimeline()throws Exception {
  Path checkout=Path.of("..").toAbsolutePath().normalize(),project=checkout.getParent().resolve("jacamo/examples/auction/auction.jcm"),inputs=checkout.resolve("use-plugin/examples/auction/compact-v1");
  Path evidence=Files.createDirectories(checkout.resolve("use-plugin/verification/compact-auction"));var host=new Session();boolean accepted=false;
  Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(Map.of("status","RUNNING","startedAt",java.time.Instant.now().toString())));
  var selection=new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",inputs.resolve("auction-mapping.xml"),inputs.resolve("auction.ocl"),inputs.resolve("auction.csl"),inputs.resolve("auction.istar"),false);
  try(var facade=new DefaultJaCaMoFacade(checkout,host)) {
   try {
    facade.prepareOwnedProducer(project,selection,null,false);assertEquals("MODEL_READY",facade.workflowStatus().state());long pid=facade.ownedProducerStatus().pid();String run=facade.ownedProducerStatus().runId();
    assertNotEquals(ProcessHandle.current().pid(),pid);var system=host.system();var projector=OwnedProducerRuntimeIT.projector(facade);assertNotNull(projector);facade.startRuntime();
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(100);boolean complete=false;
    while(System.nanoTime()<deadline) {
     assertSame(system,host.system());assertEquals(org.tzi.use.plugins.jacamo.bridge.BridgeClientState.LIVE,facade.authorityStatus().readiness(),facade.status());
     complete=projector.coordinator().read(()->{var state=system.state();var auctions=state.allObjects().stream().filter(o->o.cls().name().equals("Auction")).toList();return auctions.size()==2&&auctions.stream().allMatch(o->!o.state(state).attributeValue("winner").isUndefined()&&!o.state(state).attributeValue("winner").toString().equals("'no_winner'")&&o.state(state).attributeValue("running").toString().equals("false"));});
     if(complete)break;Thread.sleep(300);
    }
    assertTrue(complete,"Actual two auctions must finish");
    var result=projector.coordinator().awaitEvaluation(projector.coordinator().manualVerify(),60000);Files.write(evidence.resolve("result.json"),CanonicalJson.encode(result.toMap()));
    var core=CanonicalJson.object(result.semantic().observation().get("originalEngine"));assertEquals("PINNED_NATIVE_CUT",core.get("evaluated"));assertEquals("OBSERVED_PREFIX",core.get("historyQuality"));
    int roots=0;for(Object raw:(List<?>)core.get("contexts")){var context=CanonicalJson.object(raw);for(Object item:CanonicalJson.object(context.getOrDefault("occurrences",Map.of())).values()){var occurrence=CanonicalJson.object(item);if("AuctionFinished".equals(occurrence.get("sourceElement"))){roots++;assertEquals("FULFILLED",occurrence.get("aggregateStatus"));assertEquals("KNOWN_DERIVED",occurrence.get("quality"));}}}assertEquals(2,roots);
    var audit=new ObservedConformancePresenter(facade).capture();var summary=BusinessTimeline.summarize(audit);
    Files.write(evidence.resolve("timeline-summary.json"),CanonicalJson.encode(summary.stream().map(c->Map.of("id",c.id(),"businessKey",c.businessKey(),"state",c.businessState(),"requirements",c.requirements(),"freshness",c.freshness(),"evaluations",c.evaluations().stream().filter(r->r.status().equals("FAIL")||r.status().equals("ERROR")).map(r->r.element()+":"+r.status()+":"+r.evidence()).toList())).toList()));
    assertTrue(summary.size()<=30,"Expected compact example milestones; actual="+summary.size()+" / audit="+audit.size());assertEquals(audit.getLast().id(),summary.getLast().id());
    assertTrue(summary.getLast().requirements().startsWith("Requirements: KNOWN"),summary.getLast().requirements());assertFalse(projector.coordinator().journal().hasGap());
    var program=facade.semanticProgramEvidence();Files.write(evidence.resolve("program.json"),CanonicalJson.encode(program));
    var operationChecks=new ArrayList<Map<String,Object>>();for(long offset=0;offset<projector.coordinator().journal().persistedEntries();) {var page=projector.coordinator().journal().page(offset,128);assertTrue(page.nextOrdinal()>offset);offset=page.nextOrdinal();for(var entry:page.entries())if(entry.result().semantic()!=null)for(var row:entry.result().semantic().constraints())if(Set.of("PRE","POST","INVOCATION").contains(row.kind()))operationChecks.add(Map.of("id",row.id(),"kind",row.kind(),"verdict",row.verdict().name(),"invocation",row.invocation(),"diagnostic",row.diagnostic()));}
    Files.write(evidence.resolve("operations.json"),CanonicalJson.encode(operationChecks));
    assertTrue(operationChecks.stream().anyMatch(r->r.get("kind").equals("POST")&&r.get("verdict").equals("PASS")&&!r.get("invocation").toString().isBlank()));Files.write(evidence.resolve("operations.json"),CanonicalJson.encode(operationChecks));
    Path replayDirectory=evidence.resolve("replay-"+UUID.randomUUID());facade.exportRuntimeReplay(replayDirectory);facade.stopOwnedProducer();assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    var replay=facade.replayRuntime(replayDirectory);assertTrue(replay.complete(),replay.toString());
    facade.restartOwnedProducer();long restart=facade.ownedProducerStatus().pid();assertNotEquals(pid,restart);assertNotEquals(run,facade.ownedProducerStatus().runId());assertEquals("MODEL_READY",facade.workflowStatus().state());facade.stopOwnedProducer();assertFalse(ProcessHandle.of(restart).map(ProcessHandle::isAlive).orElse(false));
    Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(Map.of("status","PASS","project",project.toString(),"producerPid",pid,"restartPid",restart,"sameMSystem",true,"auditBoundaries",audit.size(),"businessMilestones",summary.size(),"replayCheckedEntries",replay.checkedEntries(),"stopRestartCleanup",true,"replayDirectory",replayDirectory.toString())));accepted=true;
   } finally {
    if(!accepted)Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(Map.of("status","FAILED","detail","See corresponding Maven log; no prior receipt is reused")));
    var last=facade.runtimeVerificationResult();if(last!=null)Files.write(evidence.resolve("last-result.json"),CanonicalJson.encode(last.toMap()));
    if(host.hasSystem()){var p=OwnedProducerRuntimeIT.projector(facade);if(p!=null)Files.write(evidence.resolve("last-state.json"),CanonicalJson.encode(p.coordinator().read(()->host.system().state().allObjects().stream().map(o->Map.of("class",o.cls().name(),"name",o.name(),"attributes",o.state(host.system().state()).attributeValueMap().toString())).toList())));}
    facade.stopOwnedProducer();}
  }
 }
}
