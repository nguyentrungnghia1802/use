package org.jacamo.bridge.adapter;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SelectiveCartagoCaptureTest {
    @TempDir Path root;
    public static final class Payload {
        static final AtomicInteger reads=new AtomicInteger();
        static volatile String lastRead="";
        @Override public String toString(){reads.incrementAndGet();lastRead=Arrays.toString(Thread.currentThread().getStackTrace());return "private-unselected-data".repeat(1024);}
    }
    @Test @Timeout(30) void selectedProducerDoesNotInspectNoiseAndRetainsNoChangeAndFailedInvocationEvents() throws Exception {
        String type=SelectiveMeterArtifact.class.getName();
        Path jcm=Files.writeString(root.resolve("meter.jcm"),"mas meter { workspace main { artifact sample: "+type+" } }");
        var model=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var env=CartagoEnvironment.getInstance();env.init();
        var actor=new CartagoBasicContext("selective"+UUID.randomUUID().toString().replace("-",""));
        var events=new CopyOnWriteArrayList<RuntimeEvent>();var created=new ArrayList<ArtifactId>();
        String hash=AdapterEvidence.digest(DeclaredArtifactTypeProof.readActualBinary(SelectiveMeterArtifact.class).bytes());
        var request=new ObservationCapabilityRequest("operations",CartagoOperationHistory.PROVIDER,true,
                Map.of("class",type,"hash",hash,"operations",List.of("bump","reset","keepSame","reject")));
        System.setProperty("jacamo.bridge.generation","1");System.setProperty("jacamo.bridge.modelRevision",model.modelRevision());
        try(var source=new CartagoSnapshotSource(env,List.of(),"selective",model,null,false);
            var providers=new ProductionObservationSession(new FrameworkObservationProviders.Context(env,model,root,"selective",1,o->{},reason->fail(reason)),List.of(request))) {
            var scope=new ObservationCaptureScope(false,Set.of(type),false,Set.of("reading"),"a".repeat(64));
            source.deferObservation();source.capabilities(providers);source.attach(events::add);
            var unrelated=actor.makeArtifact(actor.getJoinedWspId("main"),"noise",SelectiveNoiseArtifact.class.getName());
            Payload.reads.set(0);
            var warmup=source.capture();
            assertTrue(warmup.stream().noneMatch(f->f.kind()==RuntimeFactKind.PROPERTY||f.kind()==RuntimeFactKind.OPERATION));
            assertEquals(0,Payload.reads.get());assertEquals(Completeness.PARTIAL,source.completeness());
            assertTrue(events.isEmpty(),"Bootstrap metadata is not a domain observation before plan activation");
            source.observationScope(scope);source.observationsReady();
            source.observationKinds(Set.of(RuntimeFactKind.ARTIFACT,RuntimeFactKind.PROPERTY,RuntimeFactKind.WORKSPACE,RuntimeFactKind.AGENT));
            created.add(actor.makeArtifact(actor.getJoinedWspId("main"),"meter",type));
            created.add(unrelated);
            Payload.reads.set(0);
            var infoReads=new AtomicInteger();
            var adapter=new OfficialCartagoAdapter((controller,artifact)->{
                assertEquals(type,artifact.getArtifactType(),"Class selection must happen before the ArtifactInfo API read");
                infoReads.incrementAndGet();return controller.getArtifactInfo(artifact.getName());
            });
            adapter.capture(env,(artifact,operation)->false,artifact->scope.includesArtifactClass(artifact.getArtifactType()),
                    (artifact,property)->scope.includesProperty(property.getName()));
            assertEquals(1,infoReads.get());
            assertThrows(IllegalStateException.class,()->source.observationScope(scope),"A running capture scope cannot be replaced");
            var initial=source.capture();
            assertEquals(0,Payload.reads.get(),"Unselected values must not be inspected or serialized by the producer");
            assertEquals(1,initial.stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT).count());
            assertEquals(Set.of("reading"),initial.stream().filter(f->f.kind()==RuntimeFactKind.PROPERTY).map(f->f.values().get("name")).collect(java.util.stream.Collectors.toSet()));
            assertEquals(scope.hash(),source.captureMetadata().get("captureScopeHash"));
            int before=events.size();actor.doAction(created.getFirst(),new Op("noise"),5000);
            assertEquals(before,events.size(),"An unselected property/operation must not publish a domain checkpoint");
            assertEquals(0,Payload.reads.get(),()->Payload.lastRead);
            actor.doAction(created.getFirst(),new Op("keepSame"),5000);
            assertThrows(cartago.util.agent.ActionFailedException.class,()->actor.doAction(created.getFirst(),new Op("reject"),5000));
            actor.doAction(created.getFirst(),new Op("bump"),5000);
            NplLiveBoardObservationTest.await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.SUCCEEDED&&"bump".equals(e.after().get("operation"))));
            assertTrue(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.SUCCEEDED&&"keepSame".equals(e.after().get("operation"))));
            assertTrue(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.FAILED&&"reject".equals(e.after().get("operation"))));
            assertTrue(events.stream().allMatch(e->scope.hash().equals(e.after().get("producerCaptureScopeHash"))));
            source.publishObservation(()->{});
            var stable=CartagoSourceCut.require(events.getLast());
            assertEquals(scope.hash(),stable.captureMetadata().get("captureScopeHash"));
            assertEquals(0,Payload.reads.get());
            var selected=source.capture();
            long selectedBytes=bytes(selected);
            try(var full=new CartagoSnapshotSource(env,List.of(),"baseline",model,null,false)) {
                var baseline=full.capture();long fullBytes=bytes(baseline);
                assertTrue(Payload.reads.get()>0,"The same workload's full capture must actually inspect the noise values");
                assertTrue(selectedBytes<fullBytes);
                long selectedFrame=framedBytes(selected,source.captureMetadata()),fullFrame=framedBytes(baseline,full.captureMetadata());
                assertTrue(selectedFrame<fullFrame);
                System.out.println("SELECTIVE_CAPTURE_MEASUREMENTS selectedFacts="+selected.size()+" fullFacts="+baseline.size()+" selectedEncodedFactBytes="+selectedBytes+" fullEncodedFactBytes="+fullBytes+" selectedFramedSnapshotBytes="+selectedFrame+" fullFramedSnapshotBytes="+fullFrame+" events="+events.size());
                full.close();assertThrows(IllegalStateException.class,full::capture,"A retired source must not re-register its loggers");
            }
            Payload.reads.set(0);
            actor.doAction(created.getFirst(),new Op("reset"),5000);
            var values=new ArrayList<String>();
            for(var event:events)if(event.after().get("properties") instanceof List<?> properties)
                for(var raw:properties){var property=CanonicalJson.object(raw);if(property.get("name").equals("reading"))values.add(((List<?>)property.get("values")).getFirst().toString());}
            assertEquals(List.of("0","1","0"),values,"Relevant public state boundaries must survive an A→B→A execution");
            var retired=created.removeFirst();env.getController("/main").removeArtifact(retired.getName());
            var replacement=actor.makeArtifact(actor.getJoinedWspId("main"),"meter",type);created.add(replacement);
            assertNotEquals(retired.getId(),replacement.getId());
            var recreated=source.capture().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT).toList();
            assertEquals(1,recreated.size());assertEquals(replacement.getId().toString(),recreated.getFirst().id().incarnation());
            assertTrue(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.DISPOSED&&e.entityId().incarnation().equals(retired.getId().toString())));
            assertEquals(0,Payload.reads.get(),()->Payload.lastRead);
        }finally {
            for(var id:created)env.getController("/main").removeArtifact(id.getName());
            for(var agent:env.getController("/main").getCurrentAgents())if(agent.getAgentName().equals(actor.getName()))env.resolveWSP("/main").getWorkspace().quitAgent(agent);
            env.shutdown();System.clearProperty("jacamo.bridge.generation");System.clearProperty("jacamo.bridge.modelRevision");
        }
    }
    private static long bytes(List<RuntimeFact> facts){return CanonicalJson.encode(facts.stream().map(f->List.of(f.id().canonical(),f.values())).toList()).length;}
    /** Production contract codec plus the existing TCP server's int32 length
     * prefix. This measures encoded wire size, not network throughput or live MAS acceptance. */
    private static long framedBytes(List<RuntimeFact> facts,Map<String,Object> metadata){
        var now=java.time.Instant.EPOCH;var marks=Map.of("cartago",new SourceWatermark("cartago",0));
        var snapshot=new RuntimeSnapshot("measurement","revision",now,now,marks,marks,1,facts,Map.of("cartago",Completeness.COMPLETE),"measurement",Map.of("sourceEvidence",Map.of("cartago",metadata)));
        var distribution=new DistributionFingerprint("fixture","CAPTURE_SPI_FIXTURE_NOT_DISTRIBUTION_ATTESTATION",Map.of());
        byte[] frame=ContractCodec.encode(ContractEnvelope.create("1.1.0",MessageType.RUNTIME_SNAPSHOT,"capture-spi-fixture",distribution,
                "measurement","revision","run",1,"snapshot",now,List.of(),Completeness.COMPLETE,marks,List.of(),ContractPayloads.runtime(snapshot)));
        assertEquals(MessageType.RUNTIME_SNAPSHOT,ContractCodec.decode(frame).messageType());
        return Integer.BYTES+(long)frame.length;
    }
}
