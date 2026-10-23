package org.tzi.use.plugins.jacamo.semantics.auction;

import java.net.InetAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.jacamo.bridge.adapter.AuctionStepObservation;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ManagedProducerTestSupport;
import org.tzi.use.plugins.jacamo.bridge.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual external Auction + existing authenticated TCP. Consumer is JUnit,
 * not a USE GUI/Session: this is the N01 source gate, not E01/E02. */
class AuctionStepLiveTcpTest {
    @Test void exactPinnedSourceRunsItsMarkersInTheRealExternalJacamoProcess()throws Exception {
        Path evidence=Files.createTempDirectory(Files.createDirectories(Path.of("target/auction-implementation")),"N01-live-").toAbsolutePath();
        var eventCount=new java.util.concurrent.atomic.AtomicLong();var frames=new CopyOnWriteArrayList<VerificationObservation>();var errors=new CopyOnWriteArrayList<String>();
        var tasks=Set.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1","P_bid_t2");
        long producerPid;
        try(var producer=new ManagedProducerTestSupport(Path.of("examples/auction/auction.jcm"),evidence,false,true,Map.of(AuctionStepObservation.PROPERTY,"true"),entry->!entry.replace('\\','/').endsWith("/target/test-classes"))) {
            producerPid=producer.producerPid();assertNotEquals(ProcessHandle.current().pid(),producerPid);
            var config=producer.configuration;var secret=HexFormat.of().parseHex(Files.readString(config.secretFile()).trim());
            try(var journal=Files.newBufferedWriter(evidence.resolve("events.ndjson"));var client=new BridgeClient(new LocalTcpBridgeTransport(InetAddress.getByName(config.endpoint().getHost()),config.endpoint().getPort(),secret,config.maxFrameBytes(),config.timeoutMillis()),
                    new BridgeMirrorStateMachine(8192),config.distributionSha256(),Set.of("official.model",VerificationObservation.CAPABILITY,"runtime.observation.jason-step.v1"),8192,event->{
                        eventCount.incrementAndGet();try{synchronized(journal){journal.write(new String(CanonicalJson.encode(ContractPayloads.event(event)),java.nio.charset.StandardCharsets.UTF_8));journal.newLine();}}catch(java.io.IOException failure){errors.add("TEST_JOURNAL_FAILED:"+failure.getMessage());}
                        if(VerificationObservation.present(event)){var frame=VerificationObservation.require(event);if(frame.kind()==VerificationObservation.Kind.JASON_STEP)frames.add(frame);}},diagnostic->errors.add(diagnostic))) {
                var accepted=client.synchronize();
                Files.write(evidence.resolve("model-snapshot.json"),CanonicalJson.encode(ContractPayloads.model(accepted.model())));
                var bundle=AuctionCaseBundle.read(Path.of("examples/auction"));var specification=new AuctionMappingXmlLoader().load(bundle);
                var program=new AuctionStaticMappingAdapter().map(accepted.model(),new AuctionStaticMappingAdapter.Selection(bundle,specification),producer.jcm);
                var decoder=new AuctionStepObservationDecoder(program);
                assertTrue(accepted.model().semanticContract().jasonPrograms().stream().allMatch(p->p.sourceDigest().equals(org.jacamo.bridge.adapter.AuctionStagingInstrumentation.SOURCE_SHA)));
                var waiting=ManagedStartupControl.read(producer.control,"waiting.json");ManagedStartupControl.request(producer.control,CanonicalJson.object(waiting.get("owner")));
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);
                while(System.nanoTime()<until&&errors.isEmpty()&&producer.producerAlive()) {
                    var observed=frames.stream().filter(f->!f.data().get("phase").equals("ENABLED")).map(f->f.data().get("sourceStepId")).collect(java.util.stream.Collectors.toSet());
                    if(observed.containsAll(tasks)&&frames.stream().filter(f->f.quality()==Completeness.COMPLETE&&!f.data().get("schemeIdentity").toString().isBlank()).map(f->f.data().get("schemeIdentity")).distinct().count()>=2)break;
                    Thread.sleep(25);
                }
                Files.write(evidence.resolve("steps.json"),CanonicalJson.encode(frames.stream().map(f->Map.of("quality",f.quality().name(),"diagnostic",f.diagnostic(),"data",f.data())).toList()));
                assertTrue(errors.isEmpty(),errors+"; "+evidence);assertTrue(producer.producerAlive(),Files.readString(evidence.resolve("producer.err")));
                assertTrue(frames.stream().map(f->f.data().get("sourceStepId")).collect(java.util.stream.Collectors.toSet()).containsAll(tasks),"Missing source steps: "+frames+"; "+evidence);
                assertTrue(frames.stream().filter(f->!f.data().get("phase").equals("ENABLED")).map(f->f.data().get("sourceStepId")).collect(java.util.stream.Collectors.toSet()).containsAll(tasks),"Every site needs an actual terminal source callback: "+evidence);
                assertTrue(frames.stream().anyMatch(f->f.quality()==Completeness.COMPLETE&&f.data().get("phase").equals("SUCCEEDED")),"No independently qualified real scope: "+frames+"; "+evidence);
                assertTrue(frames.stream().filter(f->f.quality()==Completeness.COMPLETE&&!f.data().get("schemeIdentity").toString().isBlank()).map(f->f.data().get("schemeIdentity")).distinct().count()>=2,"Both actual scheme incarnations must be observed: "+evidence);
                assertTrue(frames.stream().map(f->f.data().get("agentIdentity")).distinct().count()>=2);
                for(var frame:frames)assertEquals(frame.quality(),decoder.decode(frame,accepted.sessionId(),accepted.generation(),accepted.modelRevision()).quality());
                var starts=frames.stream().filter(f->f.quality()==Completeness.COMPLETE&&f.data().get("sourceStepId").equals("A_start_t2")&&f.data().get("phase").equals("SUCCEEDED")).toList();
                assertFalse(starts.isEmpty(),"Actual start input capture is required");
                for(var frame:starts) {
                    var bindings=CanonicalJson.object(frame.data().get("bindings"));var service=CanonicalJson.object(bindings.get("serviceInput"));var term=CanonicalJson.object(CanonicalJson.object(bindings.get("values")).get("S"));
                    assertEquals("JACA_PUBLIC_TERM_TO_OBJECT_START_VARIABLE_V1",service.get("authority"));assertEquals("java.lang.String",service.get("javaType"));
                    assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(term),service.get("sourceTermHash"));
                    assertEquals(term.get("raw"),service.get("value"),"The pinned actual services are structured Jason terms; JaCa converts their exact rendering to String");
                    assertTrue(((List<?>)bindings.get("actions")).stream().map(CanonicalJson::object).anyMatch(a->a.get("correlation").equals(frame.data().get("operationInvocation"))&&"start".equals(a.get("operationName"))&&"SUCCEEDED".equals(a.get("result"))));
                }
                assertTrue(producer.sourceUnchanged());
                Files.write(evidence.resolve("source-acceptance.json"),CanonicalJson.encode(Map.of("scope","N01_EXTERNAL_AUCTION_OBSERVATION_ONLY_NOT_E01","producerPid",producerPid,"junitConsumerPid",ProcessHandle.current().pid(),"sourceRevision",accepted.modelRevision(),
                        "frames",frames.size(),"events",eventCount.get(),"tasks",tasks,"schemes",frames.stream().filter(f->f.quality()==Completeness.COMPLETE).map(f->f.data().get("schemeIdentity")).distinct().toList(),"errors",errors)));
            }
        }
    }
}
