package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.bridge.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.profile.SemanticProjectionProfile;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import static org.junit.jupiter.api.Assertions.*;

/** Real launcher/agents/artifact/MOISE/TCP. Recorded transports cannot satisfy this gate. */
public class Use4GoalExternalBridgeIT {
    @Test void originalEngineUsesRealExternalMasAndMonitorLifecycleDoesNotOwnProducer() throws Exception {
        Path evidence=Files.createTempDirectory(Path.of("target"),"use4goal-live-").toAbsolutePath();
        String oldProfile=System.getProperty(SemanticProjectionProfile.PROPERTY),oldRules=System.getProperty(MappingRuleSet.PROPERTY);
        Path ready=evidence.resolve("boot-ready"),release=evidence.resolve("release"),stopMas=evidence.resolve("stop-mas");
        var properties=Map.of("use.fix.bootReady",ready.toString(),"use.fix.release",release.toString(),"use.fix.stopMas",stopMas.toString());
        try(var producer=new ManagedProducerTestSupport(Path.of("src/test/resources/semantics/original-engine-live/monitor.jcm"),evidence,false,true,properties)) {
            long usePid=ProcessHandle.current().pid();assertNotEquals(usePid,producer.producerPid());assertTrue(producer.producerAlive());
            var host=new Session();
            // Pause at official Jason cycle boundaries before releasing managed startAgs.
            try(var bootstrap=BridgeTransportFactory.localTcp().open(producer.configuration)) {
                var identity=ContractCodec.decode(bootstrap.handshake(),CanonicalJson.DEFAULT_MAX_BYTES,64,262144);new ContractValidator().validate(identity);
                directControl(bootstrap,identity,RuntimeControlContract.Action.PAUSE);
                var model=ContractPayloads.model(ContractCodec.decode(bootstrap.modelSnapshot(),CanonicalJson.DEFAULT_MAX_BYTES,64,262144).payload());
                var rules=rules(model);
                Path file=evidence.resolve("rules.json");Files.write(file,rules.bytes());System.setProperty(SemanticProjectionProfile.PROPERTY,"USE4GOAL_OBSERVED_V1");System.setProperty(MappingRuleSet.PROPERTY,file.toString());
                producer.configureWorkflow();
                try(var facade=new DefaultJaCaMoFacade(Path.of("."),SemanticAuthority.BRIDGE,()->producer.configuration,BridgeTransportFactory.localTcp(),PipelineMode.CODE_GROUNDED_NATIVE,host)) {
                    producer.clearWorkflowProperties();facade.importProject(producer.jcm);var system=host.system();
                    assertEquals("MODEL_READY",facade.workflowStatus().state());assertTrue(facade.workflowStatus().startAvailable());assertTrue(Files.notExists(producer.control.resolve("started.json")));
                    facade.startRuntime();assertTrue(Files.isRegularFile(producer.control.resolve("started.json")));assertEquals("LIVE",facade.workflowStatus().state());
                    // liveReady means RUNNING in the existing control contract. PAUSED
                    // is certified by the actual all-agent ACK and current native cut.
                    waitFor(()->facade.runtimeControlState().state()==RuntimeControlContract.State.PAUSED,facade,evidence);
                    assertEquals(facade.runtimeControlState().required(),facade.runtimeControlState().acknowledged());
                    facade.resumeRuntime().get(30,TimeUnit.SECONDS);waitFor(()->Files.exists(ready),facade,evidence);
                    facade.pauseRuntime("fixture initialized").get(30,TimeUnit.SECONDS);assertSame(system,host.system());
                    var projector=CanonicalMonitorLifecycleTest.projector(facade);settle(projector);
                    Files.write(evidence.resolve("initialized-cut.json"),CanonicalJson.encode(facade.verificationSnapshot().toMap()));
                    var configuredAgents=model.semanticContract().agentDeclarations().stream().map(a->projector.mutations().objectForSemanticId(a.metadata().semanticId())).toList();
                    assertEquals(2,configuredAgents.size());assertTrue(configuredAgents.stream().allMatch(Objects::nonNull));assertEquals(2,new HashSet<>(configuredAgents).size(),"Configured agents have exact declaration aliases; launcher bookkeeping agents are separate observations");
                    assertFalse(contexts(facade.runtimeVerificationResult()).isEmpty(),"Actual GroupBoard/role/player binding was unavailable");
                    Files.writeString(release,"run actual CArtAgO operations");facade.resumeRuntime().get(30,TimeUnit.SECONDS);
                    waitFor(()->facade.verificationSnapshot().image()!=null&&facade.verificationSnapshot().image().objects().values().stream().anyMatch(o->o.className().equals("Counter")&&"-1".equals(o.attributes().get("p"))),facade,evidence);
                    facade.pauseRuntime("completed workload").get(30,TimeUnit.SECONDS);settle(projector);
                    assertEquals("CURRENT_OBSERVED",facade.runtimeVerificationResult().freshness(),facade.status());
                    var history=facade.runtimeVerificationHistory();
                    assertTrue(history.stream().flatMap(r->r.outcomes().stream()).anyMatch(o->o.constraintId().equals("increment:post")&&o.outcome()==VerificationOutcome.PASS),"Native @pre did not produce PASS");
                    assertTrue(history.stream().flatMap(r->r.outcomes().stream()).anyMatch(o->o.constraintId().startsWith("NATIVE:")&&o.outcome()==VerificationOutcome.FAIL),"Observed negative state was hidden");
                    var allCalls=history.stream().flatMap(r->contexts(r).stream()).flatMap(c->((List<?>)c.getOrDefault("originalApiCalls",List.of())).stream()).toList();
                    assertTrue(allCalls.containsAll(List.of("GoalMarking.update","TaskMarking.update","GoalModelInstanceTranslator.propagateDependencies")),allCalls.toString());
                    assertStatus(facade.runtimeVerificationResult(),"left","FULFILLED");assertStatus(facade.runtimeVerificationResult(),"all","FULFILLED");assertStatus(facade.runtimeVerificationResult(),"any","FULFILLED");assertStatus(facade.runtimeVerificationResult(),"increment","FULFILLED");
                    var owner=projector.coordinator().originalEngine();var before=owner.checkpoint().get(5,TimeUnit.SECONDS);facade.resyncRuntime();settle(projector);assertSame(system,host.system());assertEquals(before.histories(),owner.checkpoint().get(5,TimeUnit.SECONDS).histories());
                    var accepted=facade.runtimeVerificationResult();
                    var replayDirectory=evidence.resolve("canonical-replay");facade.exportRuntimeReplay(replayDirectory);
                    // The real producer is PAUSED and its accepted engine lane is drained.
                    // Keep the test-owned recording before monitor cleanup removes its files.
                    var recording=Files.readAllLines(projector.coordinator().journal().path()).stream()
                            .map(line->CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).toList();
                    var program=projector.mutations().canonicalProgram();
                    facade.stopMonitorSession();assertEquals("MONITOR_STOPPED",facade.workflowStatus().state());assertTrue(producer.producerAlive());assertSame(system,host.system());assertEquals("STALE",facade.runtimeVerificationResult().freshness());
                    CanonicalMonitorLifecycleTest.await(()->Boolean.TRUE.equals(owner.metrics().get("terminated")));assertEquals(0,owner.metrics().get("expressions"));assertEquals(0,owner.metrics().get("pendingPins"));
                    directParity(model,program,recording,accepted);
                    var replayReport=new NativeRuntimeReplay().replay(replayDirectory);assertTrue(replayReport.complete(),replayReport.diagnostics().toString());assertEquals(accepted.resultHash(),replayReport.finalResultHash());assertEquals(accepted.stateHash(),replayReport.finalStateHash());
                    var manual=org.tzi.use.parser.ocl.OCLCompiler.compileExpression(system.model(),system.state(),"Counter.allInstances()->size() = 1","manual-after-stop",new java.io.PrintWriter(new java.io.StringWriter()),system.varBindings());assertNotNull(manual);
                    assertEquals("true",new org.tzi.use.uml.ocl.expr.Evaluator().eval(manual,system.state(),system.state(),system.varBindings()).toString());
                    facade.restartMonitorSession();var next=CanonicalMonitorLifecycleTest.projector(facade);settle(next);assertNotSame(system,host.system());assertNotSame(owner,next.coordinator().originalEngine());assertTrue(producer.producerAlive());assertStatus(facade.runtimeVerificationResult(),"left","PENDING");
                    var cycles=new ArrayList<Map<String,Object>>();
                    for(int cycle=0;cycle<2;cycle++) {
                        var old=CanonicalMonitorLifecycleTest.projector(facade);var oldOwner=old.coordinator().originalEngine();var oldSystem=host.system();
                        facade.stopMonitorSession();CanonicalMonitorLifecycleTest.await(()->Boolean.TRUE.equals(oldOwner.metrics().get("terminated")));
                        assertEquals(0,oldOwner.metrics().get("expressions"));assertEquals(0,oldOwner.metrics().get("pendingPins"));assertTrue(producer.producerAlive());assertSame(oldSystem,host.system());
                        CanonicalMonitorLifecycleTest.await(()->ownedThreads("use-original-engine-")==0&&ownedThreads("jacamo-bridge-tcp-subscription")==0);
                        facade.restartMonitorSession();var active=CanonicalMonitorLifecycleTest.projector(facade);settle(active);assertNotSame(oldSystem,host.system());assertStatus(facade.runtimeVerificationResult(),"left","PENDING");
                        assertEquals(1,ownedThreads("use-original-engine-"));assertEquals(1,ownedThreads("jacamo-bridge-tcp-subscription"));
                        cycles.add(Map.of("cycle",cycle+2,"oldOwner",oldOwner.metrics(),"currentOwner",active.coordinator().originalEngine().metrics(),"engineThreads",ownedThreads("use-original-engine-"),"subscriptionThreads",ownedThreads("jacamo-bridge-tcp-subscription")));
                    }
                    next=CanonicalMonitorLifecycleTest.projector(facade);
                    facade.disconnectRuntime();assertTrue(producer.producerAlive());assertEquals("STALE",facade.runtimeVerificationResult().freshness());facade.connectRuntime();settle(next);assertEquals("GAP",core(facade.runtimeVerificationResult()).get("historyQuality"));
                    facade.resumeRuntime().get(30,TimeUnit.SECONDS);Files.writeString(stopMas,"allow Jason .stopMAS");assertTrue(producer.awaitProducerExit(30));assertEquals(0,producer.producerExitCode());
                    waitFor(()->facade.runtimeVerificationResult().freshness().equals("STALE"),facade,evidence);assertTrue(host.hasSystem());assertTrue(ProcessHandle.current().isAlive());
                    var result=new LinkedHashMap<String,Object>();result.put("usePid",usePid);result.put("jacamoPid",producer.producerPid());result.put("transport","authenticated TCP");result.put("engine","original Java core in USE JVM");result.put("managedAck",true);result.put("stopMasExitCode",producer.producerExitCode());result.put("nativeStateBeforeStop",accepted.toMap());result.put("originalCalls",allCalls.stream().distinct().toList());result.put("sourceUnchanged",producer.sourceUnchanged());result.put("directory",evidence.toString());result.put("monitorCycles",3);result.put("resourceCycles",cycles);result.put("directFeedParity",true);
                    Files.write(Path.of("target/use4goal-external-bridge.json"),CanonicalJson.encode(result));
                }
            }
        }finally {restore(SemanticProjectionProfile.PROPERTY,oldProfile);restore(MappingRuleSet.PROPERTY,oldRules);}
    }
    @Test void actualProducerRestartResetsRunBindingsAndHistoryWhileUnownedConnectKeepsStartUnavailable() throws Exception {
        Path evidence=Files.createTempDirectory(Path.of("target"),"use4goal-restart-").toAbsolutePath();var ready=evidence.resolve("boot-ready");
        String oldProfile=System.getProperty(SemanticProjectionProfile.PROPERTY),oldRules=System.getProperty(MappingRuleSet.PROPERTY);
        var properties=Map.of("use.fix.bootReady",ready.toString(),"use.fix.release",evidence.resolve("release").toString(),"use.fix.stopMas",evidence.resolve("stop-mas").toString());
        try(var producer=new ManagedProducerTestSupport(Path.of("src/test/resources/semantics/original-engine-live/monitor.jcm"),evidence,false,true,properties);
            var bootstrap=BridgeTransportFactory.localTcp().open(producer.configuration)) {
            var first=handshake(bootstrap);directControl(bootstrap,first,RuntimeControlContract.Action.PAUSE);
            var model=ContractPayloads.model(ContractCodec.decode(bootstrap.modelSnapshot(),CanonicalJson.DEFAULT_MAX_BYTES,64,262144).payload());
            var file=evidence.resolve("rules.json");Files.write(file,rules(model).bytes());System.setProperty(SemanticProjectionProfile.PROPERTY,"USE4GOAL_OBSERVED_V1");System.setProperty(MappingRuleSet.PROPERTY,file.toString());
            producer.configureWorkflow();var host=new Session();
            try(var facade=new DefaultJaCaMoFacade(Path.of("."),SemanticAuthority.BRIDGE,()->producer.configuration,BridgeTransportFactory.localTcp(),PipelineMode.CODE_GROUNDED_NATIVE,host)) {
                producer.clearWorkflowProperties();facade.importProject(producer.jcm);facade.startRuntime();waitFor(()->facade.runtimeControlState().state()==RuntimeControlContract.State.PAUSED,facade,evidence);
                facade.resumeRuntime().get(30,TimeUnit.SECONDS);waitFor(()->Files.exists(ready),facade,evidence);facade.pauseRuntime("restart baseline").get(30,TimeUnit.SECONDS);
                var original=CanonicalMonitorLifecycleTest.projector(facade);settle(original);var oldOwner=original.coordinator().originalEngine();var saved=oldOwner.checkpoint().get(5,TimeUnit.SECONDS);assertFalse(saved.histories().isEmpty());
                long oldPid=producer.producerPid();var oldSystem=host.system();Files.delete(ready);producer.restartProducer();assertNotEquals(oldPid,producer.producerPid());
                waitFor(()->facade.runtimeVerificationResult().freshness().equals("STALE"),facade,evidence);
                var replacement=handshake(bootstrap);assertNotEquals(first.sessionId(),replacement.sessionId());directControl(bootstrap,replacement,RuntimeControlContract.Action.PAUSE);
                facade.importProject(producer.jcm);assertTrue(facade.workflowStatus().startAvailable(),facade.workflowStatus().toString());facade.startRuntime();
                waitFor(()->facade.runtimeControlState().state()==RuntimeControlContract.State.PAUSED,facade,evidence);facade.resumeRuntime().get(30,TimeUnit.SECONDS);waitFor(()->Files.exists(ready),facade,evidence);facade.pauseRuntime("replacement ready").get(30,TimeUnit.SECONDS);
                var current=CanonicalMonitorLifecycleTest.projector(facade);settle(current);var owner=current.coordinator().originalEngine();var after=owner.checkpoint().get(5,TimeUnit.SECONDS);
                assertEquals(replacement.sessionId(),owner.identity().run());assertTrue(Collections.disjoint(saved.histories().keySet(),after.histories().keySet()));assertFalse(after.histories().isEmpty());assertStatus(facade.runtimeVerificationResult(),"left","PENDING");
                assertThrows(Exception.class,()->oldOwner.restore(saved).get(5,TimeUnit.SECONDS));
                if(first.modelRevision().equals(replacement.modelRevision()))assertSame(oldSystem,host.system(),"Same source/program reuses exactly one host state while replacing run history");
                boolean reusedHostSystem=oldSystem==host.system();facade.stopMonitorSession();assertTrue(producer.producerAlive());
                // A second facade is an unowned external observer; the old one is fully stopped.
                try(var external=new DefaultJaCaMoFacade(Path.of("."),SemanticAuthority.BRIDGE,()->producer.configuration,BridgeTransportFactory.localTcp(),PipelineMode.CODE_GROUNDED_NATIVE,host)) {
                    external.importProject(producer.jcm);assertFalse(external.workflowStatus().managed());assertFalse(external.workflowStatus().startAvailable());assertThrows(IllegalStateException.class,external::startRuntime);
                    external.resyncRuntime();assertEquals("CURRENT_OBSERVED",external.runtimeVerificationResult().freshness());external.stopMonitorSession();assertTrue(producer.producerAlive());
                }
                Files.write(Path.of("target/use4goal-producer-restart.json"),CanonicalJson.encode(Map.of("usePid",ProcessHandle.current().pid(),"firstProducerPid",oldPid,"replacementProducerPid",producer.producerPid(),"firstSession",first.sessionId(),"replacementSession",replacement.sessionId(),"oldContexts",saved.histories().keySet().stream().sorted().toList(),"newContexts",after.histories().keySet().stream().sorted().toList(),"sameHostSystem",reusedHostSystem,"unownedConnect",true,"sourceUnchanged",producer.sourceUnchanged())));
            }
        }finally{restore(SemanticProjectionProfile.PROPERTY,oldProfile);restore(MappingRuleSet.PROPERTY,oldRules);}
    }
    static long ownedThreads(String prefix){return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive).filter(t->t.getName().startsWith(prefix)).count();}
    /** Offline test oracle after the live monitor has closed. It never becomes
     * a host Session or live writer and opens no Bridge connection. */
    static void directParity(ModelSnapshot model,SemanticProgram program,List<Map<String,Object>> recording,RuntimeVerificationResult expected) throws Exception {
        var built=new org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline().buildCanonicalProgram(model,org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.AUTO,program);
        var oracle=new NativeRuntimeProjector(built,expected.sessionId(),expected.generation(),expected.modelRevision());var owner=oracle.coordinator().originalEngine();
        try(oracle) {
            for(var entry:recording) {
                var payload=CanonicalJson.object(entry.get("payload"));var result=CanonicalJson.object(entry.get("result"));String kind=(String)entry.get("kind");
                switch(kind) {
                    case "SNAPSHOT"->oracle.applySnapshot(ContractPayloads.runtime(payload),(String)result.get("sessionId"),((Number)result.get("generation")).longValue());
                    case "EVENT","OPERATION_PRE","OPERATION_POST","STREAM_BOUNDARY"->oracle.apply(ContractPayloads.event(payload));
                    case "CAPABILITIES"->{var capabilities=new TreeMap<String,String>();var versions=new TreeMap<String,String>();CanonicalJson.object(payload.get("capabilities")).forEach((k,v)->capabilities.put(k,(String)v));CanonicalJson.object(payload.get("sourceVersions")).forEach((k,v)->versions.put(k,(String)v));oracle.coordinator().runtimeCapabilities(capabilities,versions);}
                    case "BASELINE","MANUAL","ENGINE_RESULT"->{/* No extra source observation/temporal input. */}
                    default->fail("Unaccounted live parity record: "+kind);
                }
                settle(oracle);
            }
            var actual=oracle.coordinator().awaitEvaluation(oracle.coordinator().manualVerify(),10000);
            assertEquals(expected.stateHash(),actual.stateHash(),"TCP and direct admission produced different native state");
            assertEquals(rawHistory(expected),rawHistory(actual),"TCP and direct admission produced different original markings or occurrence keys");
            var expectedNative=expected.outcomes().stream().filter(o->o.constraintId().startsWith("NATIVE:")).map(o->List.of(o.constraintId(),o.outcome().name())).sorted(Comparator.comparing(Object::toString)).toList();
            var actualNative=actual.outcomes().stream().filter(o->o.constraintId().startsWith("NATIVE:")).map(o->List.of(o.constraintId(),o.outcome().name())).sorted(Comparator.comparing(Object::toString)).toList();assertEquals(expectedNative,actualNative);
        }
        CanonicalMonitorLifecycleTest.await(()->Boolean.TRUE.equals(owner.metrics().get("terminated")));
    }
    static Map<String,Object> rawHistory(RuntimeVerificationResult result){var history=new TreeMap<String,Object>();for(var context:contexts(result))history.put((String)context.get("context"),Map.of("boundary",context.get("boundary"),"occurrences",context.getOrDefault("occurrences",Map.of())));return history;}
    public static MappingRuleSet rules(ModelSnapshot model){var base=CanonicalGoalMappingTest.rules(model);var contracts=base.contracts().stream().map(c->new MappingRuleSet.Contract(c.id(),c.kind(),c.contextDeclaration(),c.targetElementId(),c.body(),List.of(LiveRegressionCounterArtifact.class.getName(),"increment",List.of(),"void"),c.variableTypes(),c.origin())).toList();return new MappingRuleSet(base.version(),base.id(),base.profileId(),base.profileVersion(),base.expectedSourceHash(),base.cslSource(),base.iStarSource(),base.bindings(),contracts,base.decisions(),base.options());}
    static ContractEnvelope handshake(BridgeTransport transport){var result=ContractCodec.decode(transport.handshake(),CanonicalJson.DEFAULT_MAX_BYTES,64,262144);new ContractValidator().validate(result);return result;}
    static void settle(NativeRuntimeProjector projector) throws Exception {projector.coordinator().originalEngineDrained().get(30,TimeUnit.SECONDS);projector.coordinator().read(()->null);}
    static void waitFor(java.util.function.BooleanSupplier ready,DefaultJaCaMoFacade facade,Path evidence) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);while(!ready.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(25);if(!ready.getAsBoolean()){Files.write(evidence.resolve("consumer-failure.json"),CanonicalJson.encode(facade.verificationSnapshot().toMap()));fail(facade.status()+" control="+facade.runtimeControlState()+" evidence="+evidence);}}
    static Map<String,Object> core(RuntimeVerificationResult result){return result==null||result.semantic()==null?Map.of():CanonicalJson.object(result.semantic().observation().getOrDefault("originalEngine",Map.of()));}
    static List<Map<String,Object>> contexts(RuntimeVerificationResult result){return ((List<?>)core(result).getOrDefault("contexts",List.of())).stream().map(CanonicalJson::object).toList();}
    static void assertStatus(RuntimeVerificationResult result,String source,String expected){assertTrue(contexts(result).stream().flatMap(c->CanonicalJson.object(c.getOrDefault("occurrences",Map.of())).values().stream()).map(CanonicalJson::object).anyMatch(o->source.equals(o.get("sourceElement"))&&expected.equals(o.getOrDefault("leafStatus",o.get("aggregateStatus")))),source+" "+expected+" "+core(result));}
    static RuntimeControlContract.Status directControl(BridgeTransport transport,ContractEnvelope identity,RuntimeControlContract.Action action){var request=new RuntimeControlContract.Request(RuntimeControlContract.VERSION,identity.sessionId(),identity.generation(),identity.modelRevision(),UUID.randomUUID().toString(),action,"bounded startup");return RuntimeControlContract.Status.decode(ContractCodec.decode(transport.control(request),CanonicalJson.DEFAULT_MAX_BYTES,64,262144).payload());}
    static void restore(String name,String value){if(value==null)System.clearProperty(name);else System.setProperty(name,value);}
}
