package org.tzi.use.plugins.jacamo.semantics.xml;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.NplLifecycleCut;
import org.tzi.use.plugins.jacamo.semantics.observer.SourceClock;
import static org.junit.jupiter.api.Assertions.*;
/** Actual timed NPL callbacks/public annotations; no Auction names or fake deadline DTOs. */
class NplLifecycleCutTest {
    @Test @Timeout(30) void exactActivationDeadlineTerminalAndModalityStayReportedEvidenceAndNeverReceiverClockDecisions()throws Exception {
        var environment=CartagoEnvironment.getInstance();environment.init();var actor=new CartagoBasicContext("normConsumer"+UUID.randomUUID().toString().replace("-",""));
        var events=new CopyOnWriteArrayList<RuntimeEvent>();var frames=new ArrayList<VerificationObservation>();ArtifactId artifact=null;
        String oldRevision=System.getProperty("jacamo.bridge.modelRevision"),oldGeneration=System.getProperty("jacamo.bridge.generation");System.setProperty("jacamo.bridge.modelRevision","npl-revision");System.setProperty("jacamo.bridge.generation","1");
        try(var norms=new NplBoardSnapshotSource("norm-run",()->NeutralNormBoard.instance==null?List.of():List.of(NeutralNormBoard.instance));
            var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"norm-run",null,norms::artifactCreated);
            var snapshots=new SnapshotCoordinator(List.of(cartago,norms),1024,events::add);
            var clock=new NplSourceClockObservation("norm-run",1,"npl-revision",frames::add)) {
            clock.pulse(false);artifact=actor.makeArtifact(actor.getJoinedWspId("main"),"normEvidence",NeutralNormBoard.class.getName());
            actor.doAction(artifact,new Op("load","scope evidence { norm duty: start -> obligation(alice,true,missing,`now`+`1 seconds`). norm optional: start -> permission(bob,true,unused,`now`+`10 seconds`). }"),5000);
            actor.doAction(artifact,new Op("addFact","start"),5000);await(()->events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED).count()==2);
            var activated=events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED&&e.after().get("normDefinition").equals("duty")).findFirst().orElseThrow();
            String hash=(String)NplRuntimeProfile.evidence().get("artifactSha256");var decoder=new NplLifecycleCut(hash);
            var initial=decoder.decode(activated.entityId(),activated.after(),"norm-run",1);assertEquals("OBLIGATION",initial.norm().modality());assertEquals(Long.parseLong((String)activated.after().get("deadline")),initial.deadlineEpochMillis());
            assertEquals(Long.parseLong((String)activated.after().get("createdEpochMillis")),initial.createdEpochMillis());assertFalse(initial.reportedUnfulfilled());assertNull(initial.terminalEpochMillis());
            var permission=events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED&&e.after().get("normDefinition").equals("optional")).findFirst().orElseThrow();
            assertEquals("PERMISSION",decoder.decode(permission.entityId(),permission.after(),"norm-run",1).norm().modality());
            await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED&&e.entityId().equals(activated.entityId())));
            var terminal=events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED&&e.entityId().equals(activated.entityId())).findFirst().orElseThrow();var ended=decoder.decode(terminal.entityId(),terminal.after(),"norm-run",1);
            assertTrue(ended.reportedUnfulfilled());assertNotNull(ended.terminalEpochMillis());assertEquals(initial.norm().identity(),ended.norm().identity());
            assertEquals(ended,decoder.decode(BridgeEntityId.parse(terminal.entityId().canonical()),CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(terminal.after()))),"norm-run",1));
            assertThrows(IllegalArgumentException.class,()->new NplLifecycleCut("0".repeat(64)).decode(terminal.entityId(),terminal.after(),"norm-run",1));
            assertThrows(IllegalArgumentException.class,()->decoder.decode(terminal.entityId(),terminal.after(),"other-run",1));
            assertEquals(SourceClock.DeadlineStatus.INCONCLUSIVE,SourceClock.unavailable().pending(ended.clockIdentity(),ended.deadlineEpochMillis()).status());
        }finally {
            if(artifact!=null){NeutralNormBoard.instance.getNormativeEngine().stop();environment.getController("/main").removeArtifact(artifact.getName());}NeutralNormBoard.instance=null;
            CslRuntimeTestRegistry.closeCartagoContext(actor);environment.shutdown();restore("jacamo.bridge.modelRevision",oldRevision);restore("jacamo.bridge.generation",oldGeneration);
        }
    }
    static void await(java.util.function.BooleanSupplier condition)throws Exception {long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(6);while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(10);assertTrue(condition.getAsBoolean(),"Actual NPL source callback timed out");}
    static void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
}
