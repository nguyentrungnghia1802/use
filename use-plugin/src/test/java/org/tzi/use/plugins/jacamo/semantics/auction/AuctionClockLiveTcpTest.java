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
        var received=new CopyOnWriteArrayList<RuntimeEvent>();var errors=new CopyOnWriteArrayList<String>();
        try(var producer=new ManagedProducerTestSupport(Path.of("examples/auction/auction.jcm"),evidence,false,true,Map.of(AuctionClockObservation.PROPERTY,"true"),entry->!entry.replace('\\','/').endsWith("/target/test-classes"))) {
            var config=producer.configuration;var secret=HexFormat.of().parseHex(Files.readString(config.secretFile()).trim());
            try(var client=new BridgeClient(new LocalTcpBridgeTransport(InetAddress.getByName(config.endpoint().getHost()),config.endpoint().getPort(),secret,config.maxFrameBytes(),config.timeoutMillis()),
                    new BridgeMirrorStateMachine(8192),config.distributionSha256(),Set.of("official.model","runtime.observation.clock.v1"),8192,received::add,diagnostic->errors.add(diagnostic))) {
                var accepted=client.synchronize();String nplHash=AuctionCaseBundle.read(Path.of("examples/auction")).runtimeRequirements().get("sha256:npl-0.6.1.jar");
                var initial=accepted.runtime().facts().stream().filter(f->f.values().containsKey(VerificationObservation.KEY)).map(f->VerificationObservation.require(f,accepted.runtime(),accepted.sessionId(),accepted.generation())).filter(o->o.kind()==VerificationObservation.Kind.CLOCK).findFirst().orElseThrow();
                var anchor=AuctionSourceClock.decode(initial,accepted.sessionId(),accepted.generation(),accepted.modelRevision(),nplHash);
                var waiting=ManagedStartupControl.read(producer.control,"waiting.json");ManagedStartupControl.request(producer.control,CanonicalJson.object(waiting.get("owner")));
                long started=System.nanoTime(),end=started+TimeUnit.SECONDS.toNanos(18);
                while(System.nanoTime()<end&&errors.isEmpty()&&producer.producerAlive()) {
                    var clocks=received.stream().filter(e->VerificationObservation.present(e)&&VerificationObservation.require(e).kind()==VerificationObservation.Kind.CLOCK).toList();var ids=clocks.stream().map(RuntimeEvent::eventId).collect(java.util.stream.Collectors.toSet());
                    // Canonical random runs can discharge every obligation in
                    // under three seconds. Require a real advancing clock and
                    // its boundary, not an invented minimum pending duration.
                    if(System.nanoTime()-started>=TimeUnit.SECONDS.toNanos(3)&&!clocks.isEmpty()&&coveredClockBatch(received,ids,accepted.sessionId(),accepted.generation(),accepted.modelRevision()))break;Thread.sleep(25);
                }
                var prefix=List.copyOf(received);var norms=prefix.stream().filter(e->e.subsystem().equals("npl")).toList();
                writeEvents(evidence.resolve("clock-events.ndjson"),prefix);
                writeEvents(evidence.resolve("npl-events.ndjson"),norms);
                assertTrue(errors.isEmpty(),errors+"; "+evidence);assertTrue(producer.producerAlive());assertTrue(producer.sourceUnchanged());
                var clocks=prefix.stream().filter(e->VerificationObservation.present(e)&&VerificationObservation.require(e).kind()==VerificationObservation.Kind.CLOCK).toList();assertFalse(clocks.isEmpty(),"Source needs pending normative obligations: "+evidence);assertFalse(norms.isEmpty(),"The actual OrgArt engines must be observed");
                long last=anchor.nowEpochMillis();for(var event:clocks){var cut=AuctionSourceClock.decode(VerificationObservation.require(event),accepted.sessionId(),accepted.generation(),accepted.modelRevision(),nplHash);assertEquals(anchor.anchorEpochMillis(),cut.anchorEpochMillis());assertEquals(Completeness.COMPLETE,cut.quality());assertTrue(cut.nowEpochMillis()>=last);last=cut.nowEpochMillis();}
                var ids=clocks.stream().map(RuntimeEvent::eventId).collect(java.util.stream.Collectors.toSet());assertTrue(coveredClockBatch(prefix,ids,accepted.sessionId(),accepted.generation(),accepted.modelRevision()),"Qualified clock must precede a validated source callback batch boundary: "+evidence);
                Files.write(evidence.resolve("source-acceptance.json"),CanonicalJson.encode(Map.of("scope","N04_SOURCE_CLOCK_TCP_NOT_E01","producerPid",producer.producerPid(),"junitConsumerPid",ProcessHandle.current().pid(),"clockFrames",clocks.size(),"sourceClock",anchor.proof(),"errors",errors)));
                assertNotEquals(ProcessHandle.current().pid(),producer.producerPid());
            }
        }
    }
    static void writeEvents(Path path,List<RuntimeEvent> events)throws java.io.IOException {
        // A diagnostic prefix is many individually bounded wire records, not one wire frame.
        try(var output=Files.newOutputStream(path)) {
            for(var event:events){output.write(CanonicalJson.encode(ContractPayloads.event(event)));output.write('\n');}
        }
    }
    /** The publisher preserves all callbacks and closes a bounded batch with its last
     * trigger. A clock need not be that last trigger; its accepted prefix must still
     * be closed by an actual, same-epoch boundary with an observed exact cause. */
    static boolean coveredClockBatch(List<RuntimeEvent> events,Set<String> clockIds,String run,long generation,String revision) {
        var seen=new HashMap<String,RuntimeEvent>();var sequences=new HashMap<String,Long>();boolean pendingClock=false;
        for(var event:events) {
            if(!run.equals(event.sessionId())||generation!=event.generation()||!revision.equals(event.modelRevision()))return false;
            if(event.kind()==RuntimeEventKind.STREAM_BOUNDARY) {
                if(pendingClock) {
                    var cause=seen.get(event.causationId());if(cause==null)return false;
                    var boundary=VerificationBoundary.require(event,sequences);
                    return boundary.eventId().equals(cause.eventId())&&boundary.sourceId().equals(cause.sourceId())&&boundary.sequence()==cause.sourceSequence();
                }
            } else {
                seen.put(event.eventId(),event);sequences.merge(event.sourceId(),event.sourceSequence(),Math::max);
                if(clockIds.contains(event.eventId()))pendingClock=true;
            }
        }
        return false;
    }
}
