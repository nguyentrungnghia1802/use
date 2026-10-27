package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.bridge.BridgeTransportFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Real unmodified JaCaMo MAS, production bootstrap and TCP; not a fixture action dispatcher. */
class GenericProducerTcpTest {
    @Test @Timeout(300) void declarativeAuctionRequestsReachProductionBeforeReasoningAndOriginalEngine() throws Exception {
        Path evidence=Files.createTempDirectory(Files.createDirectories(Path.of("target/generic-production")),"auction-").toAbsolutePath();
        Path plan=evidence.resolve("observation-requests.json"),project=Path.of("examples/auction/auction.jcm");
        // Test classes can contain another compiled copy of the application FQCN. A production
        // source proof correctly rejects shadow resources; the real producer needs no test classes.
        String oldBudget=System.getProperty("use.jacamo.runtime.journal.maxBytes");
        // Full observer history is deliberately retained, not truncated to make a live
        // acceptance fit the small default. The same explicit bounded budget is shipped by the launcher.
        System.setProperty("use.jacamo.runtime.journal.maxBytes","268435456");
        try(var producer=new ManagedProducerTestSupport(project,evidence,false,true,Map.of(ObservationRequestPlan.PROPERTY,plan.toString()),entry->!entry.replace('\\','/').endsWith("/target/test-classes"))) {
            producer.configureWorkflow();System.setProperty(ObservationRequestPlan.PROPERTY,plan.toString());
            var session=new Session();
            try(var facade=new DefaultJaCaMoFacade(Path.of("..").toAbsolutePath(),SemanticAuthority.BRIDGE,()->producer.configuration,BridgeTransportFactory.localTcp(),PipelineMode.CODE_GROUNDED_NATIVE,session)) {
                facade.configureBridge(producer.configuration);
                facade.importProject(producer.jcm,new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",project.getParent().resolve("auction-mapping.xml"),project.getParent().resolve("auction_constraints_final.ocl")));
                var system=session.system();assertNotNull(system);assertFalse(facade.semanticProgramEvidence().containsKey("caseProgram"));
                var requirements=ObservationRequestPlan.read(plan,facade.authorityStatus().sessionId(),facade.authorityStatus().generation(),facade.authorityStatus().modelRevision());assertEquals(8,requirements.requests().size());
                assertFalse(Files.exists(producer.control.resolve("started.json")));facade.startRuntime();
                var tasks=new TreeSet<String>();var calls=new TreeSet<String>();var rows=new LinkedHashMap<String,Object>();
                var cursor=Files.newBufferedReader(journal(facade),java.nio.charset.StandardCharsets.UTF_8);
                long position=0;
                try {
                // Native cuts are verified and journaled in order. The source can finish
                // before that admitted prefix drains; resync must not skip the queued
                // terminal callbacks. Stay below the harness's 240-second producer lease.
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(210);
                while(System.nanoTime()<end) {
                    long persisted=projector(facade).coordinator().journal().persistedEntries();
                    while(position<persisted) {
                        String line=cursor.readLine();assertNotNull(line);position++;
                        var entry=CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        var result=CanonicalJson.object(entry.get("result"));if(!(result.get("semantic") instanceof Map<?,?>))continue;
                        var semantic=CanonicalJson.object(result.get("semantic"));
                        for(Object value:(List<?>)semantic.get("constraints")) {
                            var row=CanonicalJson.object(value);if(!"TASK_POST".equals(row.get("kind")))continue;
                            var witness=CanonicalJson.object(row.get("witness"));if(Set.of("PASS","FAIL").contains(row.get("verdict")))tasks.add(Objects.toString(witness.get("targetTaskId"),""));
                            rows.put(row.get("id")+":"+row.get("invocation"),row);
                        }
                        Object engine=CanonicalJson.object(semantic.get("observation")).get("originalEngine");
                        if(engine instanceof Map<?,?> m&&m.get("contexts") instanceof List<?> contexts)for(Object item:contexts) {
                            var context=CanonicalJson.object(item);if(context.get("originalApiCalls") instanceof List<?> api)api.forEach(a->calls.add(a.toString()));
                        }
                    }
                    assertSame(system,session.system());
                    if(facade.authorityStatus().readiness()!=org.tzi.use.plugins.jacamo.bridge.BridgeClientState.LIVE) {
                        Files.write(evidence.resolve("failure.json"),CanonicalJson.encode(facade.runtimeVerificationResult().toMap()));
                        fail("Live subscription lost: "+facade.authorityStatus()+" evidence="+evidence);
                    }
                    if(tasks.containsAll(Set.of("A_start_t1","A_start_t2","P_bid_t1","P_bid_t2","A_decide_t1","A_decide_t2")))break;
                    Thread.sleep(100);
                }
                facade.pauseRuntime("generic acceptance stable cut").get(25,TimeUnit.SECONDS);
                facade.resyncRuntime();facade.runFullVerification();
                Files.write(evidence.resolve("accepted.json"),CanonicalJson.encode(facade.runtimeVerificationResult().toMap()));
                Files.write(evidence.resolve("tasks.json"),CanonicalJson.encode(rows));
                Files.write(evidence.resolve("program.json"),CanonicalJson.encode(facade.semanticProgramEvidence()));
                Files.write(evidence.resolve("binding-diagnostics.json"),CanonicalJson.encode(
                        org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(Map.of(
                                "scopes",projector(facade).mutations().xmlOccurrences(facade.authorityStatus().sessionId(),facade.authorityStatus().generation(),facade.authorityStatus().modelRevision()),
                                "demands",projector(facade).mutations().xmlTaskDemands(facade.authorityStatus().sessionId(),facade.authorityStatus().generation(),facade.authorityStatus().modelRevision())))));
                Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(Map.of("tasks",tasks,"calls",calls,"sameActiveMSystem",session.system()==system,"consumerPid",ProcessHandle.current().pid(),"producerPid",producer.producerPid())));
                String log=Files.readString(evidence.resolve("producer.log"))+Files.readString(evidence.resolve("producer.err"));
                assertTrue(log.contains("BRIDGE_OBSERVATION_PLAN_ACTIVE"),log);assertTrue(log.contains("requests=8"),log);
                assertFalse(log.contains("requiredUnavailable=[Capability"),log);
                assertEquals(Set.of("A_start_t1","A_start_t2","P_bid_t1","P_bid_t2","A_decide_t1","A_decide_t2"),tasks,"evidence="+evidence+" status="+facade.status());
                assertTrue(calls.contains("TaskMarking.update"),"Actual original calls "+calls+" evidence="+evidence);
                assertEquals(2,session.system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).count());
                assertTrue(producer.sourceUnchanged());System.out.println("GENERIC_PRODUCTION_TCP_PASS evidence="+evidence);
                }finally{cursor.close();Files.copy(journal(facade),evidence.resolve("runtime-journal.jsonl"));Files.write(evidence.resolve("last-result.json"),CanonicalJson.encode(facade.runtimeVerificationResult().toMap()));
                    Files.write(evidence.resolve("frozen-program.json"),new org.tzi.use.plugins.jacamo.semantics.evidence.SemanticProgramManifest().encode(projector(facade).mutations().canonicalProgram()));}
            }finally{System.clearProperty(ObservationRequestPlan.PROPERTY);}
        }finally{if(oldBudget==null)System.clearProperty("use.jacamo.runtime.journal.maxBytes");else System.setProperty("use.jacamo.runtime.journal.maxBytes",oldBudget);}
    }
    private static Path journal(DefaultJaCaMoFacade facade)throws Exception {
        return projector(facade).coordinator().journal().path();
    }
    private static org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector projector(DefaultJaCaMoFacade facade)throws Exception {
        var field=DefaultJaCaMoFacade.class.getDeclaredField("nativeWorkspace");field.setAccessible(true);Object workspace=field.get(facade);
        var p=workspace.getClass().getDeclaredField("runtimeProjector");p.setAccessible(true);
        return (org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector)p.get(workspace);
    }
}
