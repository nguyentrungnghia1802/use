package org.tzi.use.plugins.jacamo.semantics.auction;

import java.net.InetAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.jacamo.bridge.adapter.AuctionClockObservation;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ManagedProducerTestSupport;
import org.tzi.use.plugins.jacamo.bridge.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;

/** N04 source/TCP clock acceptance; not a USE GUI/observer/marking verdict. */
class AuctionClockLiveTcpTest {
    @Test void canonicalExternalAuctionPublishesQualifiedSourceClockAndExistingBoundaryCallbacks()throws Exception {
        Path evidence=Files.createTempDirectory(Files.createDirectories(Path.of("target/auction-implementation")),"N04-live-").toAbsolutePath();
        var received=new CopyOnWriteArrayList<RuntimeEvent>();var norms=new CopyOnWriteArrayList<RuntimeEvent>();var errors=new CopyOnWriteArrayList<String>();
        try(var producer=new ManagedProducerTestSupport(Path.of("examples/auction/auction.jcm"),evidence,false,true,Map.of(AuctionClockObservation.PROPERTY,"true"),entry->!entry.replace('\\','/').endsWith("/target/test-classes"))) {
            var config=producer.configuration;var secret=HexFormat.of().parseHex(Files.readString(config.secretFile()).trim());
            try(var client=new BridgeClient(new LocalTcpBridgeTransport(InetAddress.getByName(config.endpoint().getHost()),config.endpoint().getPort(),secret,config.maxFrameBytes(),config.timeoutMillis()),
                    new BridgeMirrorStateMachine(8192),config.distributionSha256(),Set.of("official.model","runtime.observation.clock.v1"),8192,event->{
                        if(event.subsystem().equals("npl"))norms.add(event);
                        if(event.kind()==RuntimeEventKind.STREAM_BOUNDARY||VerificationObservation.present(event)&&VerificationObservation.require(event).kind()==VerificationObservation.Kind.CLOCK)received.add(event);},diagnostic->errors.add(diagnostic))) {
                var accepted=client.synchronize();String nplHash=AuctionCaseBundle.read(Path.of("examples/auction")).runtimeRequirements().get("sha256:npl-0.6.1.jar");
                var initial=accepted.runtime().facts().stream().filter(f->f.values().containsKey(VerificationObservation.KEY)).map(f->VerificationObservation.require(f,accepted.runtime(),accepted.sessionId(),accepted.generation())).filter(o->o.kind()==VerificationObservation.Kind.CLOCK).findFirst().orElseThrow();
                var anchor=AuctionSourceClock.decode(initial,accepted.sessionId(),accepted.generation(),accepted.modelRevision(),nplHash);
                var waiting=ManagedStartupControl.read(producer.control,"waiting.json");ManagedStartupControl.request(producer.control,CanonicalJson.object(waiting.get("owner")));
                long started=System.nanoTime(),end=started+TimeUnit.SECONDS.toNanos(18);
                while(System.nanoTime()<end&&errors.isEmpty()&&producer.producerAlive()) {
                    var clocks=received.stream().filter(VerificationObservation::present).toList();var ids=clocks.stream().map(RuntimeEvent::eventId).toList();
                    // Canonical random runs can discharge every obligation in
                    // under three seconds. Require a real advancing clock and
                    // its boundary, not an invented minimum pending duration.
                    if(System.nanoTime()-started>=TimeUnit.SECONDS.toNanos(3)&&!clocks.isEmpty()&&received.stream().anyMatch(e->e.kind()==RuntimeEventKind.STREAM_BOUNDARY&&ids.contains(e.after().get("boundaryEventId"))))break;Thread.sleep(25);
                }
                Files.write(evidence.resolve("clock-events.json"),CanonicalJson.encode(received.stream().map(ContractPayloads::event).toList()));
                Files.write(evidence.resolve("npl-events.json"),CanonicalJson.encode(norms.stream().map(ContractPayloads::event).toList()));
                assertTrue(errors.isEmpty(),errors+"; "+evidence);assertTrue(producer.producerAlive());assertTrue(producer.sourceUnchanged());
                var clocks=received.stream().filter(VerificationObservation::present).toList();assertFalse(clocks.isEmpty(),"Source needs pending normative obligations: "+evidence);assertFalse(norms.isEmpty(),"The actual OrgArt engines must be observed");
                long last=anchor.nowEpochMillis();for(var event:clocks){var cut=AuctionSourceClock.decode(VerificationObservation.require(event),accepted.sessionId(),accepted.generation(),accepted.modelRevision(),nplHash);assertEquals(anchor.anchorEpochMillis(),cut.anchorEpochMillis());assertEquals(Completeness.COMPLETE,cut.quality());assertTrue(cut.nowEpochMillis()>=last);last=cut.nowEpochMillis();}
                var ids=clocks.stream().map(RuntimeEvent::eventId).toList();assertTrue(received.stream().anyMatch(e->e.kind()==RuntimeEventKind.STREAM_BOUNDARY&&ids.contains(e.after().get("boundaryEventId"))));
                Files.write(evidence.resolve("clock-events.json"),CanonicalJson.encode(received.stream().map(ContractPayloads::event).toList()));
                Files.write(evidence.resolve("source-acceptance.json"),CanonicalJson.encode(Map.of("scope","N04_SOURCE_CLOCK_TCP_NOT_E01","producerPid",producer.producerPid(),"junitConsumerPid",ProcessHandle.current().pid(),"clockFrames",clocks.size(),"sourceClock",anchor.proof(),"errors",errors)));
                assertNotEquals(ProcessHandle.current().pid(),producer.producerPid());
            }
        }
    }
}
