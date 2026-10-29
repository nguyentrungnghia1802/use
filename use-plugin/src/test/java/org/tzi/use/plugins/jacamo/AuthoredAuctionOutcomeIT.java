package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.bridge.BridgeClientState;

/** Full authored domain/engine slice. Temporal authority is explicitly inspected,
 * never certified from this test's process exit or Task pass count. */
class AuthoredAuctionOutcomeIT {
    @Test @Timeout(480) void fiveAuthoredTasksReachNativeOclAndTheOriginalEngineOnLiveTcpCuts()throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize(),jcm=Path.of("src/test/resources/auction-reference/auction.jcm").toAbsolutePath(),input=jcm.getParent().resolve("authored-v1");
        Path evidence=Files.createTempDirectory(Files.createDirectories(root.resolve("use-plugin/verification/authored-models/P05-domain-live")),"run-");
        var selection=new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",input.resolve("auction-mapping.xml"),input.resolve("auction_constraints_final_fixed.ocl"),input.resolve("auction_csl.csl"),input.resolve("auction_istar_final.istar"));
        String before=System.getProperty("use.jacamo.runtime.journal.maxBytes");System.setProperty("use.jacamo.runtime.journal.maxBytes","268435456");
        var session=new Session();
        try(var facade=new DefaultJaCaMoFacade(root,session)) {
            facade.prepareOwnedProducer(jcm,selection,null);var system=session.system();
            assertTrue(facade.semanticModels().goals().getActors().stream().flatMap(a->a.elements().stream()).filter(org.vnu.sme.goal.dsl.istar.mm.Goal.class::isInstance).map(org.vnu.sme.goal.dsl.istar.mm.Goal.class::cast).allMatch(g->g.conditions().isEmpty()),"This unchanged authored graph derives its Goals from Tasks/dependencies; do not invent leaf conditions to manufacture GoalMarking calls");
            var projector=OwnedProducerRuntimeIT.projector(facade);
            var tasks=new TreeSet<String>();var calls=new TreeSet<String>();var constraints=new LinkedHashMap<String,Object>();
            try(var cursor=Files.newBufferedReader(projector.coordinator().journal().path(),java.nio.charset.StandardCharsets.UTF_8)) {
                facade.startRuntime();long position=0,end=System.nanoTime()+TimeUnit.SECONDS.toNanos(360);
                while(System.nanoTime()<end) {
                    long entries=projector.coordinator().journal().persistedEntries();
                    while(position<entries) {
                        String line=cursor.readLine();assertNotNull(line);position++;
                        var record=CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        var result=CanonicalJson.object(record.get("result"));if(!(result.get("semantic") instanceof Map<?,?>))continue;
                        var semantic=CanonicalJson.object(result.get("semantic"));
                        for(Object item:(List<?>)semantic.get("constraints")) {
                            var row=CanonicalJson.object(item);constraints.put(row.get("id")+":"+row.get("invocation"),row);
                            if("TASK_POST".equals(row.get("kind"))&&Set.of("PASS","FAIL").contains(row.get("verdict")))tasks.add(Objects.toString(CanonicalJson.object(row.get("witness")).get("targetTaskId"),""));
                        }
                        var observed=CanonicalJson.object(semantic.get("observation"));
                        if(observed.get("originalEngine") instanceof Map<?,?> core&&core.get("contexts") instanceof List<?> contexts)
                            for(Object item:contexts)if(CanonicalJson.object(item).get("originalApiCalls") instanceof List<?> api)api.forEach(call->calls.add(call.toString()));
                    }
                    assertSame(system,session.system());
                    if(facade.authorityStatus().readiness()!=BridgeClientState.LIVE)fail("Live coverage lost: "+facade.status());
                    if(tasks.containsAll(Set.of("A_start_t1","A_start_t2","A_decide_t2","P_bid_t1","P_bid_t2"))&&calls.containsAll(Set.of("TaskMarking.update","GoalModelInstanceTranslator.propagateDependencies","IStarPropagation.assignGoalTask:aggregate-only")))break;
                    Thread.sleep(100);
                }
                Files.write(evidence.resolve("observed-constraints.json"),CanonicalJson.encode(constraints));
                Files.write(evidence.resolve("progress.json"),CanonicalJson.encode(Map.of("tasks",tasks,"originalCalls",calls,"entries",position,"status",facade.status())));
                assertEquals(Set.of("A_start_t1","A_start_t2","A_decide_t2","P_bid_t1","P_bid_t2"),tasks,"evidence="+evidence);
                assertTrue(calls.containsAll(Set.of("TaskMarking.update","GoalModelInstanceTranslator.propagateDependencies","IStarPropagation.assignGoalTask:aggregate-only")),calls.toString());
                assertFalse(tasks.contains("A_decide_t1"),"The source-only prefix must never become a sixth Task");
                facade.pauseRuntime("authored Auction accepted domain cut").get(25,TimeUnit.SECONDS);facade.resyncRuntime();facade.runFullVerification();
                Files.write(evidence.resolve("accepted.json"),CanonicalJson.encode(facade.runtimeVerificationResult().toMap()));
                var observerRows=projector.coordinator().read(()->system.state().allObjects().stream().filter(o->o.cls().name().equals("AuctionObligation")).map(o->Map.of("object",o.name(),"metadata",projector.mutations().objectMetadata(o),"attributes",o.state(system.state()).attributeValueMap().toString())).toList());
                Files.write(evidence.resolve("observers.json"),CanonicalJson.encode(observerRows));
                var accepted=CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(facade.runtimeVerificationResult().toMap())));
                long knownDeadlines=((List<?>)CanonicalJson.object(accepted.get("semantic")).get("constraints")).stream().map(CanonicalJson::object).filter(c->"INVARIANT_BODY".equals(c.get("kind"))&&"NATIVE:AuctionObligation::WithinDeadline".equals(c.get("id"))&&Set.of("PASS","FAIL").contains(c.get("verdict"))).count();
                assertTrue(knownDeadlines>0,"A qualified final source cut must recover NPL deadline evidence after transient mapping unavailability; evidence="+evidence);
                Files.write(evidence.resolve("program.json"),CanonicalJson.encode(facade.semanticProgramEvidence()));
                Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(Map.of("scope","AUTHORED_DOMAIN_NPL_ENGINE_SLICE_NOT_FINAL_MOISE_TEMPORAL_ACCEPTANCE","usePid",ProcessHandle.current().pid(),"producerPid",facade.ownedProducerStatus().pid(),"sameMSystem",session.system()==system,"tasks",tasks,"originalCalls",calls,"qualifiedDeadlineBodies",knownDeadlines)));
            }finally {
                Files.copy(projector.coordinator().journal().path(),evidence.resolve("journal.jsonl"));
                var result=facade.runtimeVerificationResult();if(result!=null)Files.write(evidence.resolve("last-result.json"),CanonicalJson.encode(result.toMap()));
                System.out.println("AUTHORED_AUCTION_DOMAIN_EVIDENCE="+evidence);
            }
        }finally{if(before==null)System.clearProperty("use.jacamo.runtime.journal.maxBytes");else System.setProperty("use.jacamo.runtime.journal.maxBytes",before);}
    }
}
