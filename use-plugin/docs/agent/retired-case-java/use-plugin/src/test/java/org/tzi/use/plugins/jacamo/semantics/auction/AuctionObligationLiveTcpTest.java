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

/** External canonical source/TCP component. Not the E01 USE GUI vertical slice. */
class AuctionObligationLiveTcpTest {
    @Test void externalOriginalAuctionCarriesQualifiedGoalActivationAndTerminalAnnotationsAcrossTheExistingTcp()throws Exception {
        Path evidence=Files.createTempDirectory(Files.createDirectories(Path.of("target/auction-implementation")),"N03-live-").toAbsolutePath();var events=new CopyOnWriteArrayList<RuntimeEvent>();var errors=new CopyOnWriteArrayList<String>();
        try(var producer=new ManagedProducerTestSupport(Path.of("examples/auction/auction.jcm"),evidence,false,true,Map.of(AuctionSchemeObservation.PROPERTY,"true",AuctionClockObservation.PROPERTY,"true"),entry->!entry.replace('\\','/').endsWith("/target/test-classes"))) {
            var config=producer.configuration;var secret=HexFormat.of().parseHex(Files.readString(config.secretFile()).trim());
            try(var client=new BridgeClient(new LocalTcpBridgeTransport(InetAddress.getByName(config.endpoint().getHost()),config.endpoint().getPort(),secret,config.maxFrameBytes(),config.timeoutMillis()),new BridgeMirrorStateMachine(8192),config.distributionSha256(),Set.of("official.model","runtime.observation.npl-lifecycle.v1"),8192,event->{if(event.factKind()==RuntimeFactKind.NORM_INSTANCE||VerificationObservation.present(event)&&VerificationObservation.require(event).kind()==VerificationObservation.Kind.NPL_LIFECYCLE)events.add(event);},diagnostic->errors.add(diagnostic))) {
                var accepted=client.synchronize();var bundle=AuctionCaseBundle.read(Path.of("examples/auction"));var specification=new AuctionMappingXmlLoader().load(bundle);var program=new AuctionStaticMappingAdapter().map(accepted.model(),new AuctionStaticMappingAdapter.Selection(bundle,specification),producer.jcm);var decoder=new AuctionObligationDecoder(program);
                var waiting=ManagedStartupControl.read(producer.control,"waiting.json");ManagedStartupControl.request(producer.control,CanonicalJson.object(waiting.get("owner")));long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
                var observations=new ArrayList<AuctionObligationDecoder.Observed>();
                while(System.nanoTime()<end&&errors.isEmpty()&&producer.producerAlive()) {
                    observations.clear();for(var event:events) {var value=VerificationObservation.present(event)?decoder.decode(VerificationObservation.require(event),accepted.sessionId(),accepted.generation()):decoder.decode(event.entityId(),event.after(),accepted.sessionId(),accepted.generation());if(value!=null)observations.add(value);}
                    if(observations.stream().filter(o->o.goal().equals("bid")).map(AuctionObligationDecoder.Observed::scheme).distinct().count()>=2&&observations.stream().anyMatch(o->o.goal().equals("bid")&&o.ended()!=null)&&observations.stream().anyMatch(o->o.goal().equals("decide")))break;Thread.sleep(25);
                }
                Files.write(evidence.resolve("npl-events.json"),CanonicalJson.encode(events.stream().map(ContractPayloads::event).toList()));Files.write(evidence.resolve("model-snapshot.json"),CanonicalJson.encode(ContractPayloads.model(accepted.model())));
                assertTrue(errors.isEmpty(),errors+"; "+evidence);assertTrue(producer.producerAlive());assertTrue(producer.sourceUnchanged());assertNotEquals(ProcessHandle.current().pid(),producer.producerPid());
                assertEquals(2,observations.stream().filter(o->o.goal().equals("bid")).map(AuctionObligationDecoder.Observed::scheme).distinct().count());assertTrue(observations.stream().anyMatch(o->o.goal().equals("bid")&&o.ended()!=null));assertTrue(observations.stream().anyMatch(o->o.goal().equals("decide")));
                assertTrue(observations.stream().allMatch(o->Boolean.TRUE.equals(CanonicalJson.object(o.norm().raw().get("boardObservationProof")).get("emptyBaselineAfterListener"))),"Runtime SchemeBoard listener must precede first relevant activation: "+evidence);
                assertTrue(observations.stream().allMatch(AuctionObligationDecoder.Observed::timeComplete));
                Files.write(evidence.resolve("source-acceptance.json"),CanonicalJson.encode(Map.of("scope","N03_EXTERNAL_CANONICAL_NPL_TCP_NOT_E01","producerPid",producer.producerPid(),"junitConsumerPid",ProcessHandle.current().pid(),"frames",events.size(),"bidSchemes",observations.stream().filter(o->o.goal().equals("bid")).map(AuctionObligationDecoder.Observed::scheme).distinct().toList(),"terminals",observations.stream().filter(o->o.ended()!=null).count(),"decideObserved",observations.stream().anyMatch(o->o.goal().equals("decide")),"earlySubscriptions",true,"errors",errors)));
            }
        }
    }
}
