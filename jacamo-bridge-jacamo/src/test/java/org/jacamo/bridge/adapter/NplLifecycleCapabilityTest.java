package org.jacamo.bridge.adapter;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class NplLifecycleCapabilityTest {
    @TempDir Path temp;
    @Test @Timeout(30) void curatedRegistryOwnsExistingNplSourceWithExplicitEpochAndRetiresListenerWithoutNewEngineOrTransport()throws Exception {
        Path jcm=Files.writeString(temp.resolve("norms.jcm"),"mas norms {}\n");var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var environment=CartagoEnvironment.getInstance();environment.init();var actor=new CartagoBasicContext("normRegistry"+UUID.randomUUID().toString().replace("-",""));ArtifactId artifact=null;
        var request=new ObservationCapabilityRequest("norm-events",NplLifecycleCapability.PROVIDER,true,Map.of("hash",NplRuntimeProfile.evidence().get("artifactSha256")));
        var registry=FrameworkObservationProviders.runtime(new FrameworkObservationProviders.Context(environment,source,temp,"registry-run",4,ignored->{},reason->fail(reason)));var events=new CopyOnWriteArrayList<RuntimeEvent>();NplLifecycleCapability handle=null;
        try(var session=registry.open(List.of(request))) {
            assertTrue(session.requiredUnavailable().isEmpty(),session.capabilities().toString());handle=session.handle(request.id(),NplLifecycleCapability.class).orElseThrow();
            try(var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"registry-run",null,handle.source()::artifactCreated);var snapshots=new SnapshotCoordinator(List.of(cartago,handle.source()),512,events::add)) {
                artifact=actor.makeArtifact(actor.getJoinedWspId("main"),"registryNorms",NplLiveBoardObservationTest.Board.class.getName());
                actor.doAction(artifact,new Op("load","scope registry { norm first: start -> obligation(alice,true,missing,`now`+`10 seconds`). norm second: later -> permission(bob,true,unused,`now`+`10 seconds`). }"),5000);
                actor.doAction(artifact,new Op("addFact","start"),5000);NplLiveBoardObservationTest.await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED));
                var actual=events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED).findFirst().orElseThrow();assertEquals(4,actual.generation());assertEquals(source.modelRevision(),actual.modelRevision());
                var cut=snapshots.capture(source.modelRevision(),3).snapshot();assertEquals(Completeness.PARTIAL,cut.sourceCompleteness().get("npl"));assertFalse(cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.NORM_INSTANCE).toList().isEmpty());
                session.close();assertEquals(CapabilityStatus.UNAVAILABLE,handle.capability().status());long before=events.stream().filter(e->e.sourceId().equals("npl")).count();actor.doAction(artifact,new Op("addFact","later"),5000);
                assertEquals(before,events.stream().filter(e->e.sourceId().equals("npl")).count());
            }
            var wrong=new ObservationCapabilityRequest("wrong",request.provider(),true,Map.of("hash","0".repeat(64)));
            try(var rejected=registry.open(List.of(wrong))){assertEquals(1,rejected.requiredUnavailable().size());}
        }finally {
            if(handle!=null)handle.close();if(artifact!=null){NplLiveBoardObservationTest.Board.instance.getNormativeEngine().stop();NplLiveBoardObservationTest.Board.instance.destroy();environment.getController("/main").removeArtifact(artifact.getName());}
            for(var id:environment.getController("/main").getCurrentAgents())if(id.getAgentName().equals(actor.getName()))environment.resolveWSP("/main").getWorkspace().quitAgent(id);environment.shutdown();
        }
    }
}
