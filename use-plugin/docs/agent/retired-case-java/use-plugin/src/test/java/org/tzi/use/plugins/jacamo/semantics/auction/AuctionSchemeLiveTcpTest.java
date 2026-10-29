package org.tzi.use.plugins.jacamo.semantics.auction;

import java.net.InetAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ManagedProducerTestSupport;
import org.tzi.use.plugins.jacamo.bridge.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real canonical Auction/source/TCP capture, not E01/E02 USE/UI acceptance. */
class AuctionSchemeLiveTcpTest {
    @Test void realExternalAuctionPublishesBothSchemeHistoriesWithActualDoneAndExplicitPublicationTime()throws Exception {
        Path evidence=Files.createTempDirectory(Files.createDirectories(Path.of("target/auction-implementation")),"N02-live-").toAbsolutePath();
        var frames=new CopyOnWriteArrayList<RuntimeEvent>();var errors=new CopyOnWriteArrayList<String>();
        try(var producer=new ManagedProducerTestSupport(Path.of("examples/auction/auction.jcm"),evidence,false,true,Map.of(AuctionSchemeObservation.PROPERTY,"true",AuctionClockObservation.PROPERTY,"true"),entry->!entry.replace('\\','/').endsWith("/target/test-classes"))) {
            var config=producer.configuration;var secret=HexFormat.of().parseHex(Files.readString(config.secretFile()).trim());
            try(var journal=Files.newBufferedWriter(evidence.resolve("source-events.ndjson"));var client=new BridgeClient(new LocalTcpBridgeTransport(InetAddress.getByName(config.endpoint().getHost()),config.endpoint().getPort(),secret,config.maxFrameBytes(),config.timeoutMillis()),
                    new BridgeMirrorStateMachine(8192),config.distributionSha256(),Set.of("official.model","runtime.observation.goal-transition.v1"),8192,event->{
                        try{synchronized(journal){journal.write(new String(CanonicalJson.encode(ContractPayloads.event(event)),java.nio.charset.StandardCharsets.UTF_8));journal.newLine();}}catch(java.io.IOException failure){errors.add("JOURNAL:"+failure.getMessage());}
                        if(VerificationObservation.present(event)&&VerificationObservation.require(event).kind()==VerificationObservation.Kind.GOAL_TRANSITION)frames.add(event);},diagnostic->errors.add(diagnostic))) {
                var accepted=client.synchronize();var bundle=AuctionCaseBundle.read(Path.of("examples/auction"));var specification=new AuctionMappingXmlLoader().load(bundle);
                var program=new AuctionStaticMappingAdapter().map(accepted.model(),new AuctionStaticMappingAdapter.Selection(bundle,specification),producer.jcm);var decoder=new AuctionSchemeObservationDecoder(program);
                var waiting=ManagedStartupControl.read(producer.control,"waiting.json");ManagedStartupControl.request(producer.control,CanonicalJson.object(waiting.get("owner")));
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
                while(System.nanoTime()<end&&errors.isEmpty()&&producer.producerAlive()) {
                    var cuts=frames.stream().map(VerificationObservation::require).map(o->CanonicalJson.object(o.data().get("goalEvidence"))).toList();
                    if(cuts.stream().filter(c->!((List<?>)c.get("done")).isEmpty()).map(c->c.get("schemeIdentity")).distinct().count()>=2&&cuts.stream().anyMatch(c->((List<?>)c.get("done")).size()>=2))break;Thread.sleep(25);
                }
                Files.write(evidence.resolve("model-snapshot.json"),CanonicalJson.encode(ContractPayloads.model(accepted.model())));
                Files.write(evidence.resolve("scheme-events.json"),CanonicalJson.encode(frames.stream().map(ContractPayloads::event).toList()));
                assertTrue(errors.isEmpty(),errors+"; "+evidence);assertTrue(producer.producerAlive());assertTrue(producer.sourceUnchanged());assertFalse(frames.isEmpty());
                var cuts=new ArrayList<AuctionSchemeObservationDecoder.Cut>();for(var event:frames)cuts.add(decoder.decode(VerificationObservation.require(event),accepted.sessionId(),accepted.generation(),accepted.modelRevision()));
                assertEquals(2,cuts.stream().map(AuctionSchemeObservationDecoder.Cut::scheme).distinct().count());assertTrue(cuts.stream().anyMatch(c->!c.doneBy().isEmpty()));
                assertTrue(cuts.stream().anyMatch(AuctionSchemeObservationDecoder.Cut::continuous));assertTrue(cuts.stream().flatMap(c->c.goals().values().stream()).anyMatch(g->!g.publicationTimes().isEmpty()));
                assertTrue(frames.stream().map(VerificationObservation::require).filter(o->o.sourceTime().status().equals("OBSERVED")).allMatch(o->o.sourceTime().authority().contains("PUBLICATION_NOT_MOISE_INTERNAL")));
                Files.write(evidence.resolve("source-acceptance.json"),CanonicalJson.encode(Map.of("scope","N02_CANONICAL_SOURCE_TCP_NOT_E01","producerPid",producer.producerPid(),"junitConsumerPid",ProcessHandle.current().pid(),"frames",frames.size(),"schemes",cuts.stream().map(AuctionSchemeObservationDecoder.Cut::scheme).distinct().toList(),"doneObserved",cuts.stream().anyMatch(c->!c.doneBy().isEmpty()),"errors",errors)));
            }
        }
    }
}
