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
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real source/TCP into the production native owner. Installed distribution
 * and headful Workbench acceptance are separate E01/E02 gates. */
class AuctionTaskLiveTcpTest {
    @Test void realCanonicalSourceStepsReachNativePredicatesAndOriginalMarkingOnTheSameSystem()throws Exception {
        run("I04",false);
    }
    static void run(String gate,boolean verifyReplay)throws Exception {
        String budgetKey="use.jacamo.runtime.journal.maxBytes",previousBudget=System.getProperty(budgetKey);
        // Full source events plus per-cut evidence exceed the default 64 MiB.
        // Exercise the existing bounded deployment setting, not a larger frame,
        // queue or history truth limit. Legacy/default and overflow tests remain.
        System.setProperty(budgetKey,Long.toString(256L*1024*1024));
        try {
        Path evidence=Files.createTempDirectory(Files.createDirectories(Path.of("target/auction-implementation")),gate+"-live-").toAbsolutePath();
        var queue=new LinkedBlockingQueue<RuntimeEvent>(32768);var errors=new CopyOnWriteArrayList<String>();var kinds=new TreeSet<String>();var snapshots=new ArrayList<Object>();
        // Use the existing interactive stop-file lifetime, as installed live
        // tests do. A 240s headless lifetime can expire while this consumer is
        // draining/replaying its accepted prefix; source shutdown belongs to
        // the harness owner, not to the source-acquisition deadline.
        try(var producer=new ManagedProducerTestSupport(Path.of("examples/auction/auction.jcm"),evidence,false,false,Map.of(AuctionStepObservation.PROPERTY,"true",AuctionSchemeObservation.PROPERTY,"true",AuctionClockObservation.PROPERTY,"true"),entry->!entry.replace('\\','/').endsWith("/target/test-classes"))) {
            var config=producer.configuration;var secret=HexFormat.of().parseHex(Files.readString(config.secretFile()).trim());
            try(var client=new BridgeClient(new LocalTcpBridgeTransport(InetAddress.getByName(config.endpoint().getHost()),config.endpoint().getPort(),secret,config.maxFrameBytes(),config.timeoutMillis()),new BridgeMirrorStateMachine(8192),config.distributionSha256(),Set.of("official.model",VerificationObservation.CAPABILITY,"runtime.observation.jason-step.v1"),8192,event->{if(!queue.offer(event))errors.add("TEST_NATIVE_QUEUE_CAPACITY");},(String error)->errors.add(error))) {
                var accepted=client.synchronize();var bundle=AuctionCaseBundle.read(Path.of("examples/auction"));var rules=new AuctionMappingXmlLoader().load(bundle);
                var program=new AuctionStaticMappingAdapter().map(accepted.model(),new AuctionStaticMappingAdapter.Selection(bundle,rules),producer.jcm);
                var built=new CodeGroundedNativePipeline().buildCanonicalProgram(accepted.model(),NativeProjectionMode.AUTO,program);
                try(var projector=new NativeRuntimeProjector(built,accepted.sessionId(),accepted.generation(),accepted.modelRevision());var journal=Files.newBufferedWriter(evidence.resolve("accepted-events.ndjson"))) {
                    projector.applySnapshot(accepted.runtime(),accepted.sessionId(),accepted.generation());var system=projector.system();
                    var waiting=ManagedStartupControl.read(producer.control,"waiting.json");ManagedStartupControl.request(producer.control,CanonicalJson.object(waiting.get("owner")));
                    // Every source event is admitted, including the substantial
                    // pre-Auction organizational/bootstrap prefix. Draining it
                    // through native verification is slower than source execution.
                    long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(180);int received=0,retainedAtDeadline=0,remainingPrefix=-1;Throwable failure=null;
                    try {
                        while(errors.isEmpty()) {
                            // Source acquisition has a deadline; processing an
                            // already-received FIFO prefix does not erase it.
                            // Freeze its finite size once, never chase later
                            // arrivals or retry a missing source terminal.
                            if(remainingPrefix<0&&(System.nanoTime()>=until||!producer.producerAlive()))remainingPrefix=retainedAtDeadline=queue.size();
                            if(remainingPrefix==0)break;
                            var event=queue.poll(100,TimeUnit.MILLISECONDS);if(event==null)continue;received++;
                            if(remainingPrefix>0)remainingPrefix--;
                            journal.write(new String(CanonicalJson.encode(ContractPayloads.event(event)),java.nio.charset.StandardCharsets.UTF_8));journal.newLine();
                            projector.apply(event);assertSame(system,projector.system());
                            assertFalse(projector.coordinator().journal().hasGap(),projector.coordinator().journal().diagnostic());
                            if(VerificationObservation.present(event)&&VerificationObservation.require(event).kind()==VerificationObservation.Kind.JASON_STEP) {
                                var result=projector.coordinator().awaitEvaluation(projector.coordinator().latest(),15000);
                                var frame=VerificationObservation.require(event);if(!frame.data().get("phase").equals("ENABLED"))kinds.add((String)frame.data().get("sourceStepId"));
                                snapshots.add(Map.of("event",ContractPayloads.event(event),"semantic",result.semantic().toMap()));
                            }
                            if(kinds.containsAll(Set.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1","P_bid_t2")))break;
                        }
                    }catch(Throwable error){failure=error;}
                    journal.flush();Files.write(evidence.resolve("task-results.json"),CanonicalJson.encode(snapshots));
                    Files.write(evidence.resolve("diagnostic.json"),CanonicalJson.encode(Map.of("received",received,"sourceTerminalKinds",kinds,"errors",errors,"failure",failure==null?"":failure.toString(),"nativeResync",projector.resyncRequired(),"queued",queue.size(),"retainedAtAcquisitionDeadline",retainedAtDeadline,"remainingPrefix",remainingPrefix)));
                    assertNull(failure,"Native source ingress failed: "+failure+"; "+evidence);assertTrue(errors.isEmpty(),errors+"; "+evidence);assertFalse(projector.resyncRequired(),evidence.toString());
                    assertTrue(kinds.containsAll(Set.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1","P_bid_t2")),"All six actual source sites required: "+kinds+"; "+evidence);
                    var admitted=new HashSet<String>();var scopes=new HashSet<String>();var fulfilled=new HashSet<String>();boolean original=false;
                    for(Object raw:snapshots) {
                        var semantic=CanonicalJson.object(CanonicalJson.object(raw).get("semantic"));
                        for(Object value:(List<?>)semantic.get("constraints")){var row=CanonicalJson.object(value);if(row.get("kind").equals("TASK_POST")&&row.get("verdict").equals("PASS")){var witness=CanonicalJson.object(row.get("witness"));admitted.add((String)witness.get("targetTaskId"));scopes.add((String)witness.get("scheme"));}}
                        var core=CanonicalJson.object(CanonicalJson.object(semantic.get("observation")).get("originalEngine"));
                        for(Object value:(List<?>)core.getOrDefault("contexts",List.of())) {
                            var context=CanonicalJson.object(value);
                            if(context.getOrDefault("originalApiCalls",List.of()) instanceof List<?> calls&&calls.contains("TaskMarking.update"))original=true;
                            for(Object occurrence:CanonicalJson.object(context.getOrDefault("occurrences",Map.of())).values()) {
                                var marking=CanonicalJson.object(occurrence);if("FULFILLED".equals(marking.get("leafStatus")))fulfilled.add((String)marking.get("sourceElement"));
                            }
                        }
                    }
                    assertTrue(admitted.containsAll(Set.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1")),"Actual success callbacks must reach authored native predicates: "+admitted+"; "+evidence);
                    assertTrue(admitted.contains("P_bid_t2")||snapshots.stream().map(CanonicalJson::object).map(r->CanonicalJson.object(r.get("semantic"))).flatMap(s->((List<?>)s.get("constraints")).stream()).map(CanonicalJson::object).anyMatch(r->r.get("kind").equals("TASK_POST")&&r.get("verdict").equals("FAIL")&&CanonicalJson.object(r.get("witness")).get("targetTaskId").equals("P_bid_t2")),"The actual random bid branch must have its known success/failure, never an invented outcome: "+evidence);
                    assertTrue(original,"Original TaskMarking.update must be called: "+evidence);assertEquals(2,scopes.size());assertNotEquals(ProcessHandle.current().pid(),producer.producerPid());assertTrue(producer.sourceUnchanged());
                    assertTrue(fulfilled.containsAll(Set.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1")),"Native truth must actually reach the original raw recurrence: "+fulfilled+"; "+evidence);
                    var receipt=new TreeMap<String,Object>();receipt.put("scope","I04_REAL_SOURCE_TCP_NATIVE_OWNER_NOT_INSTALLED_USE_GUI");receipt.put("producerPid",producer.producerPid());receipt.put("nativeConsumerPid",ProcessHandle.current().pid());receipt.put("sameNativeSystem",true);receipt.put("nativeTaskPostPass",admitted);receipt.put("sourceTerminalKinds",kinds);receipt.put("schemes",scopes);receipt.put("received",received);receipt.put("originalTaskMarking",original);receipt.put("originalFulfilledLeaves",fulfilled);receipt.put("errors",errors);receipt.put("journalBudgetBytes",org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationCoordinator.defaultJournalMaxBytes());receipt.put("journalPersistedBytes",projector.coordinator().journal().persistedBytes());receipt.put("journalGap",projector.coordinator().journal().hasGap());
                    Files.write(evidence.resolve("acceptance.json"),CanonicalJson.encode(receipt));
                    if(verifyReplay) {
                        var expected=projector.coordinator().awaitEvaluation(projector.coordinator().lastObservation(),30000);
                        var recording=evidence.resolve("recording");var replay=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay();
                        replay.exportBundle(projector,built.nativeResult().export().useText(),recording);
                        try(var archived=replay.openBundle(recording)) {
                            var report=archived.report();Files.write(evidence.resolve("replay.json"),CanonicalJson.encode(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(report)));
                            assertTrue(report.complete(),report.diagnostics()+"; "+evidence);assertEquals(expected.stateHash(),report.finalStateHash());assertEquals(expected.resultHash(),report.finalResultHash());
                            var taskEvents=snapshots.stream().map(CanonicalJson::object).map(row->CanonicalJson.object(row.get("event"))).map(event->(String)event.get("eventId")).collect(java.util.stream.Collectors.toSet());
                            var stepEvents=archived.steps().stream().map(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay.Step::event).collect(java.util.stream.Collectors.toSet());
                            assertTrue(stepEvents.containsAll(taskEvents),"Every Task callback is a selectable semantic boundary, including unchanged world state");
                            Files.write(evidence.resolve("replay-steps.json"),CanonicalJson.encode(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(archived.steps())));
                        }
                        var metadata=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(recording.resolve("manifest.json"))));assertEquals("3.1.0",metadata.get("schemaVersion"));
                    }
                }
            }
        }
        }finally{if(previousBudget==null)System.clearProperty(budgetKey);else System.setProperty(budgetKey,previousBudget);}
    }
}
