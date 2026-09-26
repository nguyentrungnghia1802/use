package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jacamo.bridge.adapter.LocalTcpBridgeServer;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;

class LocalTcpBridgeTransportTest {
    private static final byte[] SECRET="0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final String DISTRIBUTION="1".repeat(64);

    @Test void productionTransportRunsAllCanonicalModelsWithBufferedEventAndAck() throws Exception {
        Path examples=Path.of("..","..","JaCaMo","examples").toAbsolutePath().normalize();
        for(Path jcm:List.of(Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().normalize(),
                examples.resolve("auction/auction.jcm"),examples.resolve("house-building/house-building.jcm"))) runCase(jcm);
    }

    @Test void authUnknownResumeAndRemoteConfigurationFailClosed() throws Exception {
        Fixture f=fixture(Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().normalize(),"security-session");
        try(var server=server(f,()->{})){server.start();
            var wrong=new LocalTcpBridgeTransport(InetAddress.getLoopbackAddress(),server.port(),"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx".getBytes(),1024*1024,2000);
            assertThrows(BridgeProtocolException.class,wrong::handshake);wrong.close();
            var transport=new LocalTcpBridgeTransport(InetAddress.getLoopbackAddress(),server.port(),SECRET,1024*1024,2000);
            var latch=new CountDownLatch(1);var received=new AtomicReference<RuntimeEvent>();
            try(var subscription=transport.subscribe("not-retained",bytes->{received.set(ContractPayloads.event(ContractCodec.decode(bytes,1024*1024,64,262144).payload()));latch.countDown();})){
                assertTrue(latch.await(3,TimeUnit.SECONDS));assertEquals(RuntimeEventKind.GAP,received.get().kind());
            }transport.close();
        }
        assertThrows(IllegalArgumentException.class,()->new LocalTcpBridgeTransport(InetAddress.getByName("192.0.2.1"),1234,SECRET,1024*1024,2000));
    }

    @Test void unsupportedSchemaIsRejectedBeforeMaterialization() throws Exception {
        Fixture normal=fixture(Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().normalize(),"schema-session");
        var bad=new ContractEnvelope("2.0.0",normal.handshake.messageType(),normal.handshake.bridgeBuild(),normal.handshake.distribution(),normal.handshake.projectKey(),normal.handshake.modelRevision(),normal.handshake.sessionId(),normal.handshake.generation(),normal.handshake.messageId(),normal.handshake.producedAt(),normal.handshake.capabilities(),normal.handshake.completeness(),normal.handshake.watermarks(),normal.handshake.evidence(),normal.handshake.payloadDigest(),normal.handshake.payload());
        Fixture f=new Fixture(bad,normal.model,normal.runtime,normal.event,normal.gap);
        try(var server=server(f,()->{})){server.start();var transport=new LocalTcpBridgeTransport(InetAddress.getLoopbackAddress(),server.port(),SECRET,1024*1024,2000);var mirror=new BridgeMirrorStateMachine(32);
            try(var client=new BridgeClient(transport,mirror,DISTRIBUTION,Set.of("official.model","runtime.snapshot"),8)){
                assertThrows(BridgeProtocolException.class,client::synchronize);assertEquals(BridgeClientState.RESYNC_REQUIRED,mirror.state());
            }
        }
    }

    @Test void networkPartitionMarksAcceptedMirrorForResync() throws Exception {
        Fixture f=fixture(Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().normalize(),"partition-session");
        var server=server(f,()->{});server.start();var mirror=new BridgeMirrorStateMachine(32);
        try(var client=new BridgeClient(new LocalTcpBridgeTransport(InetAddress.getLoopbackAddress(),server.port(),SECRET,4*1024*1024,2000),mirror,DISTRIBUTION,Set.of("official.model","runtime.snapshot"),8)){
            client.synchronize();assertEquals(BridgeClientState.LIVE,mirror.state());server.close();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(mirror.state()==BridgeClientState.LIVE&&System.nanoTime()<deadline)Thread.onSpinWait();assertEquals(BridgeClientState.RESYNC_REQUIRED,mirror.state());
        }finally{server.close();}
    }

    private void runCase(Path jcm)throws Exception{
        Fixture f=fixture(jcm,"tcp-"+jcm.getParent().getFileName());AtomicReference<LocalTcpBridgeServer> serverRef=new AtomicReference<>();
        try(var server=server(f,()->serverRef.get().publish(f.event.sourceId()+":"+f.event.sourceSequence(),ContractCodec.encode(f.eventEnvelope())))){serverRef.set(server);server.start();
            var transport=new LocalTcpBridgeTransport(InetAddress.getLoopbackAddress(),server.port(),SECRET,4*1024*1024,3000);var mirror=new BridgeMirrorStateMachine(64);
            try(var client=new BridgeClient(transport,mirror,DISTRIBUTION,Set.of("official.model","runtime.snapshot"),8)){
                var accepted=client.synchronize();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(mirror.facts().values().stream().anyMatch(v->Boolean.TRUE.equals(v.values().get("active")))&&System.nanoTime()<deadline)Thread.onSpinWait();
                assertEquals(BridgeClientState.LIVE,mirror.state());assertTrue(mirror.facts().values().stream().allMatch(v->Boolean.FALSE.equals(v.values().get("active"))));
                assertEquals(f.model.modelRevision(),accepted.model().modelRevision());assertFalse(new NativeSemanticAdapter().adapt(accepted.model(),jcm.getParent(),jcm.getFileName().toString()).model().elements().isEmpty());
            }
        }
    }

    private LocalTcpBridgeServer server(Fixture f,Runnable duringRuntime){return new LocalTcpBridgeServer(InetAddress.getLoopbackAddress(),0,SECRET,4*1024*1024,8,16L*1024*1024,
            ()->ContractCodec.encode(f.handshake),()->ContractCodec.encode(f.modelEnvelope()),()->{duringRuntime.run();return ContractCodec.encode(f.runtimeEnvelope());},()->ContractCodec.encode(f.gapEnvelope()));}

    private Fixture fixture(Path jcm,String session)throws Exception{
        ModelSnapshot model=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);long generation=1;String source="jason:test";
        var distribution=new DistributionFingerprint("1.3.1",DISTRIBUTION,Map.of("jason","3.3.2","cartago","3.1","moise","1.1","npl","0.6.1"));
        var caps=List.of(new Capability("official.model",CapabilityStatus.COMPLETE,List.of(),""),new Capability("runtime.snapshot",CapabilityStatus.COMPLETE,List.of(),""));
        var id=new BridgeEntityId("jason","agent","runtime-agent",model.sources().getFirst().id().scope(),"agent",session+":"+generation);
        var fact=new RuntimeFact(id,RuntimeFactKind.AGENT,Map.of("active",true),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var runtime=new RuntimeSnapshot("snapshot",model.modelRevision(),Instant.EPOCH,Instant.EPOCH,Map.of(source,new SourceWatermark(source,0)),Map.of(source,new SourceWatermark(source,0)),1,List.of(fact),Map.of(source,Completeness.COMPLETE),"f".repeat(64));
        var event=new RuntimeEvent("event",session,generation,model.modelRevision(),"jason",source,1,Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.AGENT,ProjectionStatus.MATERIALIZED_FAITHFULLY,id,null,"corr","",Map.of("active",true),Map.of("active",false),new SourceWatermark(source,1),Completeness.COMPLETE,List.of());
        var gap=new RuntimeEvent("gap",session,generation,model.modelRevision(),"transport",source,2,Instant.EPOCH,RuntimeEventKind.GAP,null,null,null,null,"","",Map.of(),Map.of(),new SourceWatermark(source,2),Completeness.PARTIAL,List.of());
        return new Fixture(envelope(MessageType.HANDSHAKE,distribution,model.modelRevision(),session,generation,caps,Map.of("readOnly",true)),model,runtime,event,gap);
    }
    private static ContractEnvelope envelope(MessageType type,DistributionFingerprint distribution,String revision,String session,long generation,List<Capability> caps,Map<String,Object> payload){return ContractEnvelope.create("1.0.0",type,"phase8",distribution,"canonical",revision,session,generation,type.name(),Instant.EPOCH,caps,Completeness.COMPLETE,Map.of(),List.of(),payload);}
    private record Fixture(ContractEnvelope handshake,ModelSnapshot model,RuntimeSnapshot runtime,RuntimeEvent event,RuntimeEvent gap){
        ContractEnvelope modelEnvelope(){return envelope(MessageType.MODEL_SNAPSHOT,handshake.distribution(),model.modelRevision(),handshake.sessionId(),handshake.generation(),handshake.capabilities(),ContractPayloads.model(model));}
        ContractEnvelope runtimeEnvelope(){return envelope(MessageType.RUNTIME_SNAPSHOT,handshake.distribution(),model.modelRevision(),handshake.sessionId(),handshake.generation(),handshake.capabilities(),ContractPayloads.runtime(runtime));}
        ContractEnvelope eventEnvelope(){return envelope(MessageType.RUNTIME_EVENT,handshake.distribution(),model.modelRevision(),handshake.sessionId(),handshake.generation(),handshake.capabilities(),ContractPayloads.event(event));}
        ContractEnvelope gapEnvelope(){return envelope(MessageType.RUNTIME_EVENT,handshake.distribution(),model.modelRevision(),handshake.sessionId(),handshake.generation(),handshake.capabilities(),ContractPayloads.event(gap));}
    }
}
