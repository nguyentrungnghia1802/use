package org.tzi.use.plugins.jacamo.semantics.auction;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.bridge.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real authenticated TCP with the official case model and observer producer;
 * frameworks share the test JVM. E01/E02 still require external Auction processes. */
class AuctionObservationTcpTest {
    private static final byte[] SECRET="0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    @Test void existingTransportPreservesTypedEvidenceAndNegotiatesSchemaWithoutPromotingPopulation()throws Exception {
        var publication=new AtomicReference<java.util.function.Consumer<RuntimeEvent>>(event->{});
        try(var fixture=new AuctionLiveFixture();var source=new VerificationObservationSource("auction-import-run",1,fixture.source.modelRevision());
                var coordinator=new SnapshotCoordinator(List.of(source),8,event->publication.get().accept(event))) {
            var base=ContractCodec.decode(AuctionImportTest.frames(fixture.source).getFirst());var advertised=new AtomicBoolean(true);
            java.util.function.Supplier<List<Capability>> capabilities=()->{var values=new ArrayList<>(base.capabilities());if(advertised.get())values.add(VerificationObservation.schemaCapability());return List.copyOf(values);};
            java.util.function.BiFunction<MessageType,Map<String,Object>,byte[]> encode=(type,payload)->ContractCodec.encode(ContractEnvelope.create("1.1.0",type,"verification-protocol-test",base.distribution(),base.projectKey(),base.modelRevision(),base.sessionId(),base.generation(),type.name(),java.time.Instant.EPOCH,capabilities.get(),Completeness.PARTIAL,Map.of(),List.of(),payload));
            try(var server=new LocalTcpBridgeServer(InetAddress.getLoopbackAddress(),0,SECRET,4*1024*1024,8,16L*1024*1024,
                    ()->encode.apply(MessageType.HANDSHAKE,Map.of("readOnly",true)),()->encode.apply(MessageType.MODEL_SNAPSHOT,ContractPayloads.model(fixture.source)),
                    ()->{try{return encode.apply(MessageType.RUNTIME_SNAPSHOT,ContractPayloads.runtime(coordinator.capture(base.modelRevision(),2).snapshot()));}catch(Exception error){throw new IllegalStateException(error);}},
                    ()->{throw new IllegalStateException("UNEXPECTED_TEST_TRANSPORT_GAP");})) {
                server.start();var received=new CopyOnWriteArrayList<RuntimeEvent>();var latch=new CountDownLatch(1);
                publication.set(event->server.publish(event.sourceId()+":"+event.sourceSequence(),encode.apply(MessageType.RUNTIME_EVENT,ContractPayloads.event(event))));
                var frame=new VerificationObservation(VerificationObservation.Kind.CLOCK,"clock",Completeness.PARTIAL,"CLOCK_UNQUALIFIED",VerificationObservation.SourceTime.unavailable("RECORDED_TEST_NO_CLOCK"),Map.of("clockIdentity","clock","sourceProof",Map.of("fixture","T01")));
                var mirror=new BridgeMirrorStateMachine(32);
                try(var client=new BridgeClient(new LocalTcpBridgeTransport(InetAddress.getLoopbackAddress(),server.port(),SECRET,4*1024*1024,3000),mirror,"1".repeat(64),
                        Set.of("official.model",VerificationObservation.CAPABILITY),8,event->{received.add(event);latch.countDown();})) {
                    var accepted=client.synchronize();assertEquals(Completeness.PARTIAL,accepted.runtime().sourceCompleteness().get(source.sourceId()));
                    source.publish(frame,"","");
                    assertTrue(latch.await(5,TimeUnit.SECONDS));assertEquals(frame,VerificationObservation.require(received.getFirst()));
                    assertEquals(source.capture().getFirst().values(),received.getFirst().after());
                    assertEquals(ProjectionStatus.EVIDENCE_ONLY,received.getFirst().projectionStatus());assertEquals(Completeness.PARTIAL,received.getFirst().completeness());
                }
                // The pre-existing reader can retain opaque evidence; only an
                // explicitly required new capability grants new schema support.
                try(var legacy=new BridgeClient(new LocalTcpBridgeTransport(InetAddress.getLoopbackAddress(),server.port(),SECRET,4*1024*1024,3000),new BridgeMirrorStateMachine(32),"1".repeat(64),Set.of("official.model"),8)) {
                    assertEquals(Completeness.PARTIAL,legacy.synchronize().runtime().facts().getFirst().completeness());
                }
                advertised.set(false);
                try(var unsupported=new BridgeClient(new LocalTcpBridgeTransport(InetAddress.getLoopbackAddress(),server.port(),SECRET,4*1024*1024,3000),new BridgeMirrorStateMachine(32),"1".repeat(64),Set.of(VerificationObservation.CAPABILITY),8)) {
                    assertThrows(BridgeProtocolException.class,unsupported::synchronize);
                }
            }
        }
    }
}
