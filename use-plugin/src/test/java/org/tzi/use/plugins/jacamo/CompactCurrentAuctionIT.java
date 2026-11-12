package org.tzi.use.plugins.jacamo;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.main.Session;
import static org.junit.jupiter.api.Assertions.*;
/** Current v3 service/TCP acceptance; no desktop acceptance or legacy input. */
class CompactCurrentAuctionIT {
 @Test @Timeout(180) void currentCompactSourceCheckpoints()throws Exception {
  Path checkout=Path.of("..").toAbsolutePath().normalize();
  Path project=Path.of("D:/_CODE_BANK/Project_/vnu-sme-lab/jacamo/examples/auction/auction.jcm");
  Path inputs=checkout.resolve("use-plugin/examples/auction"),evidence=Files.createDirectories(checkout.resolve("use-plugin/verification/compact-completion-20261112/live"));
  var session=new Session();
  try(var facade=new DefaultJaCaMoFacade(checkout,session)) {
   facade.verifiedCheckpointMode(true);
   try {
    facade.prepareOwnedProducer(project,new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",inputs.resolve("auction-compact-mapping.xml"),inputs.resolve("auction.ocl"),inputs.resolve("auction.csl"),inputs.resolve("auction.istar"),false),null,false);
    assertEquals("MODEL_READY",facade.workflowStatus().state());
    assertDoesNotThrow(()->CanonicalJson.encode(facade.semanticProgramEvidence()),"Workbench program evidence must be JSON-compatible before Start");facade.startRuntime();
    var projector=OwnedProducerRuntimeIT.projector(facade);long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(100);boolean finished=false;
    while(System.nanoTime()<until) {
     var view=facade.verifiedCheckpointState();
     Files.write(evidence.resolve("checkpoint.json"),CanonicalJson.encode(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(view)));
     if(view.state().equals("HELD_UNVERIFIED")||view.state().equals("HELD_FAILURE")) {
      Files.writeString(evidence.resolve("status.txt"),facade.status().toString());
      var result=facade.runtimeVerificationResult();if(result!=null)Files.write(evidence.resolve("held-result.json"),CanonicalJson.encode(result.toMap()));
      fail("Actual checkpoint retained: "+view.state()+"; "+view.checkpoint()+"; "+view.reason());
     }
     finished=projector.coordinator().read(()->{var state=session.system().state();var auctions=state.allObjects().stream().filter(o->o.cls().name().equals("Auction")).toList();return auctions.size()==2&&auctions.stream().allMatch(o->o.state(state).attributeValue("running").toString().equals("false")&&!o.state(state).attributeValue("winner").toString().equals("'no_winner'"));});
     if(finished&&terminalApprovals(facade)==2)break;Thread.sleep(250);
    }
    assertTrue(finished,"Current source auctions did not finish; "+facade.status());
    assertEquals(2,terminalApprovals(facade),"Both exact terminal scope cuts must be automatically approved before shutdown");
    var result=projector.coordinator().awaitEvaluation(projector.coordinator().manualVerify(),30000);
    Files.write(evidence.resolve("result.json"),CanonicalJson.encode(result.toMap()));
    assertFalse(projector.coordinator().journal().hasGap());
   } finally {facade.stopOwnedProducer();}
  }
 }
 private static long terminalApprovals(DefaultJaCaMoFacade facade)throws Exception {
  var field=DefaultJaCaMoFacade.class.getDeclaredField("checkpointService");field.setAccessible(true);
  var service=(org.tzi.use.plugins.jacamo.codegrounded.runtime.VerifiedCheckpointService)field.get(facade);
  return service.history().stream().filter(v->v.state().equals("RUNNING")&&v.checkpoint().startsWith("scheme-post:")&&v.checkpoint().endsWith(":terminal")).count();
 }
}
