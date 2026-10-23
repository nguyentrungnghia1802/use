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

class CartagoOperationHistoryTest {
    @TempDir Path temp;
    @Test @Timeout(30) void registryCapturesActualSuccessFailureAndPerformerWithoutEquatingSuccessWithValueChange()throws Exception {
        var jcm=Files.writeString(temp.resolve("receipts.jcm"),"mas receipts {}\n");var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var bytes=DeclaredArtifactTypeProof.readActualBinary(OperationReceiptArtifact.class);
        var request=new ObservationCapabilityRequest("operations",CartagoOperationHistory.PROVIDER,true,Map.of("class",OperationReceiptArtifact.class.getName(),"hash",AdapterEvidence.digest(bytes.bytes())));
        var environment=CartagoEnvironment.getInstance();environment.init();var actorA=new CartagoBasicContext("receiptA"+UUID.randomUUID().toString().replace("-",""));var actorB=new CartagoBasicContext("receiptB"+UUID.randomUUID().toString().replace("-",""));ArtifactId artifact=null;
        String oldRevision=System.getProperty("jacamo.bridge.modelRevision"),oldGeneration=System.getProperty("jacamo.bridge.generation");System.setProperty("jacamo.bridge.modelRevision",source.modelRevision());System.setProperty("jacamo.bridge.generation","3");
        var registry=FrameworkObservationProviders.runtime(new FrameworkObservationProviders.Context(environment,source,temp,"receipt-run",3,ignored->{},reason->fail(reason)));
        try(var session=registry.open(List.of(request))) {
            assertTrue(session.requiredUnavailable().isEmpty(),session.capabilities().toString());var provider=session.handle(request.id(),CartagoOperationHistory.class).orElseThrow();var events=new CopyOnWriteArrayList<RuntimeEvent>();
            try(var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"receipt-run");var snapshots=new SnapshotCoordinator(List.of(cartago),2048,e->{provider.observe(e);events.add(e);})) {
                artifact=actorA.makeArtifact(actorA.getJoinedWspId("main"),"receiptTarget",OperationReceiptArtifact.class.getName());
                actorA.doAction(artifact,new Op("offer",5.0),5000);actorB.doAction(artifact,new Op("offer",7.0),5000);
                var selected=artifact;assertThrows(cartago.util.agent.ActionFailedException.class,()->actorB.doAction(selected,new Op("offer",-1.0),5000));
                NplLiveBoardObservationTest.await(()->events.stream().filter(e->Set.of(RuntimeEventKind.SUCCEEDED,RuntimeEventKind.FAILED).contains(e.kind())&&"offer".equals(e.after().get("operation"))).count()==3);
                var cut=provider.capture(artifact);assertEquals("COMPLETE",cut.get("historyQuality"));assertEquals(true,cut.get("fromCreation"));
                var receipts=((List<?>)cut.get("events")).stream().map(CanonicalJson::object).map(ContractPayloads::event).toList();assertEquals(6,receipts.size());
                var successes=receipts.stream().filter(e->e.kind()==RuntimeEventKind.SUCCEEDED).toList();assertEquals(2,successes.size());assertEquals(Set.of(actorA.getName(),actorB.getName()),successes.stream().map(e->e.after().get("performerName")).collect(java.util.stream.Collectors.toSet()));
                assertEquals(1,receipts.stream().filter(e->e.kind()==RuntimeEventKind.FAILED).count());
                assertEquals(5.0,(double)environment.getController("/main").getArtifactInfo(artifact.getName()).getObsProperties().stream().filter(p->p.getName().equals("best")).findFirst().orElseThrow().getValue());
                var unsigned=new LinkedHashMap<>(cut);Object hash=unsigned.remove("hash");assertEquals(AdapterEvidence.digest(CanonicalJson.encode(unsigned)),hash);
                try(var late=new CartagoOperationHistory(request,new FrameworkObservationProviders.Context(environment,source,temp,"receipt-run",3,ignored->{},reason->{}))) {assertEquals(false,late.capture(artifact).get("fromCreation"));assertEquals("INCOMPLETE",late.capture(artifact).get("historyQuality"));}
                session.close();assertEquals(CapabilityStatus.UNAVAILABLE,provider.capability().status());assertEquals("INCOMPLETE",provider.capture(artifact).get("status"));
            }
            try(var wrong=registry.open(List.of(new ObservationCapabilityRequest("wrong",request.provider(),true,Map.of("class",OperationReceiptArtifact.class.getName(),"hash","0".repeat(64)))))){assertEquals(1,wrong.requiredUnavailable().size());}
        }finally {
            if(artifact!=null)environment.getController("/main").removeArtifact(artifact.getName());for(var id:environment.getController("/main").getCurrentAgents())if(Set.of(actorA.getName(),actorB.getName()).contains(id.getAgentName()))environment.resolveWSP("/main").getWorkspace().quitAgent(id);environment.shutdown();
            if(oldRevision==null)System.clearProperty("jacamo.bridge.modelRevision");else System.setProperty("jacamo.bridge.modelRevision",oldRevision);if(oldGeneration==null)System.clearProperty("jacamo.bridge.generation");else System.setProperty("jacamo.bridge.generation",oldGeneration);
        }
    }
}
