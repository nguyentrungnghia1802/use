package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.tzi.use.main.Session;
import static org.junit.jupiter.api.Assertions.*;

/** Real external runtimes; all case semantics are authored inputs, never a production Java mapper. */
class AdditionalAuthoredCasesIT {
    @Test @Timeout(360) void helloWorldFiveInputsReachNativeStateAndOriginalGoals()throws Exception {run("hello-world","hello","helloworld.jcm");}
    @Test @Timeout(360) void houseBuildingFiveInputsReachNativeStateAndOriginalGoals()throws Exception {run("house-building","house","house-building.jcm");}
    private void run(String name,String prefix,String jcm)throws Exception {
        Path checkout=Path.of("..").toAbsolutePath().normalize(),source=checkout.resolve("use-plugin/examples/"+name);
        Path copiedBase=Files.createDirectories(checkout.resolve("use-plugin/target/additional-case-inputs"));
        Path root=Files.createTempDirectory(copiedBase,name+"-");
        try(var files=Files.walk(source)){for(var file:files.toList()){var target=root.resolve(source.relativize(file));if(Files.isDirectory(file))Files.createDirectories(target);else Files.copy(file,target);}}
        Path evidence=Files.createTempDirectory(Files.createDirectories(checkout.resolve("use-plugin/verification/additional-cases")),name+"-");
        var input=new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",root.resolve(prefix+"-mapping.xml"),root.resolve(prefix+".ocl"),root.resolve(prefix+".csl"),root.resolve(prefix+".istar"));
        var hashes=new TreeMap<String,String>();
        try(var files=Files.walk(source)){for(var file:files.filter(Files::isRegularFile).toList())hashes.put(source.relativize(file).toString(),sha(file));}
        var host=new Session();long child=0;String run="";
        var wire=new java.util.concurrent.CopyOnWriteArrayList<Map<String,Object>>();
        org.tzi.use.plugins.jacamo.bridge.BridgeTransportFactory recording=config->{
            var delegate=org.tzi.use.plugins.jacamo.bridge.BridgeTransportFactory.localTcp().open(config);
            return new org.tzi.use.plugins.jacamo.bridge.BridgeTransport(){
                public byte[] handshake(){return delegate.handshake();}public byte[] modelSnapshot(){return delegate.modelSnapshot();}public byte[] runtimeSnapshot(){return delegate.runtimeSnapshot();}
                private java.util.function.Consumer<byte[]> observed(java.util.function.Consumer<byte[]> receiver){return bytes->{var envelope=org.jacamo.bridge.contract.ContractCodec.decode(bytes);if(envelope.messageType()==org.jacamo.bridge.contract.MessageType.RUNTIME_EVENT&&"cartago".equals(envelope.payload().get("sourceId")))wire.add(envelope.payload());receiver.accept(bytes);};}
                public Subscription subscribe(String token,java.util.function.Consumer<byte[]> receiver){return delegate.subscribe(token,observed(receiver));}
                public Subscription subscribe(String token,java.util.function.Consumer<byte[]> receiver,java.util.function.Consumer<RuntimeException> error){return delegate.subscribe(token,observed(receiver),error);}
                public void acknowledge(String token){delegate.acknowledge(token);}public byte[] control(org.jacamo.bridge.contract.RuntimeControlContract.Request request){return delegate.control(request);}public void close(){delegate.close();}
            };};
        try(var facade=new DefaultJaCaMoFacade(checkout,SemanticAuthority.BRIDGE,org.tzi.use.plugins.jacamo.bridge.BridgeConnectionConfig::fromSystemProperties,recording,PipelineMode.CODE_GROUNDED_NATIVE,host)) {
            try {
                facade.prepareOwnedProducer(root.resolve(jcm),input,null,false);
                child=facade.ownedProducerStatus().pid();run=facade.ownedProducerStatus().runId();assertNotEquals(ProcessHandle.current().pid(),child);
                assertEquals("MODEL_READY",facade.workflowStatus().state());
                var system=host.system();assertSame(system.model(),facade.semanticModels().nativeModel());
                var projector=OwnedProducerRuntimeIT.projector(facade);assertNotNull(projector);
                assertFalse(java.util.Arrays.stream(org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram.class.getDeclaredFields()).anyMatch(field->field.getName().equals("casePlan")));
                Files.write(evidence.resolve("program.json"),CanonicalJson.encode(facade.semanticProgramEvidence()));
                facade.startRuntime();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(170);boolean complete=false;
                while(System.nanoTime()<end) {
                    assertSame(system,host.system());
                    assertEquals(org.tzi.use.plugins.jacamo.bridge.BridgeClientState.LIVE,facade.authorityStatus().readiness(),()->facade.status()+" "+evidence);
                    complete=projector.coordinator().read(()->{
                        var state=system.state();
                        if(name.equals("hello-world"))return state.allObjects().stream().anyMatch(o->o.cls().name().equals("Console")&&!o.state(state).attributeValue("numMsg").isUndefined()&&Integer.parseInt(o.state(state).attributeValue("numMsg").toString())>=12);
                        var auctions=state.allObjects().stream().filter(o->o.cls().name().equals("ProcurementAuction")).toList();
                        var built=state.allObjects().stream().filter(o->o.cls().name().equals("BuildProgress")).toList();
                        return auctions.size()==8&&auctions.stream().allMatch(o->!o.state(state).attributeValue("currentWinner").isUndefined()&&!o.state(state).attributeValue("currentWinner").toString().equals("'no_winner'"))
                            &&built.size()==1&&List.of("prepareSite","layFloors","buildWalls","buildRoof","fitWindows","fitDoors","installPlumbing","installElectricalSystem","paintExterior","paintInterior").stream().allMatch(op->!built.getFirst().state(state).attributeValue(op+"At").isUndefined()&&Integer.parseInt(built.getFirst().state(state).attributeValue(op+"At").toString())>=0);
                    });
                    if(complete)break;Thread.sleep(250);
                }
                assertTrue(complete,"Actual source state did not reach the case outcome; see "+evidence);
                var evaluated=projector.coordinator().awaitEvaluation(projector.coordinator().manualVerify(),60000);
                // Operation completion and the next source cut are distinct
                // boundaries. Wait for a qualified accepted cut, never promote
                // an intermediate missing receipt or partial population.
                while(System.nanoTime()<end&&!"FULFILLED".equals(goalStatuses(evaluated).get(name.equals("hello-world")?"TwelveMessagesObserved":"HouseRealized"))) {
                    assertEquals(org.tzi.use.plugins.jacamo.bridge.BridgeClientState.LIVE,facade.authorityStatus().readiness(),facade.status());
                    assertFalse(projector.coordinator().journal().hasGap());Thread.sleep(500);
                    evaluated=projector.coordinator().awaitEvaluation(projector.coordinator().manualVerify(),60000);
                }
                assertNotNull(evaluated.semantic());var core=CanonicalJson.object(evaluated.semantic().observation().get("originalEngine"));
                assertEquals("PINNED_NATIVE_CUT",core.get("evaluated"));assertFalse(projector.coordinator().journal().hasGap());
                assertNotEquals("STALE",evaluated.freshness());
                assertTrue(evaluated.semantic().constraints().stream().anyMatch(c->Set.of("PASS","FAIL").contains(c.verdict().name())),"Native OCL must actually execute");
                assertFalse(((List<?>)core.getOrDefault("contexts",List.of())).isEmpty(),"Original goal engine needs actual context occurrences");
                var statuses=goalStatuses(evaluated);
                assertEquals("FULFILLED",statuses.get(name.equals("hello-world")?"TwelveMessagesObserved":"HouseRealized"),"Original goal marking must reflect the verified authored outcome");
                if(name.equals("hello-world"))assertEquals("FULFILLED",statuses.get("NonnegativeCounts"),"Maintenance over the selected workspace must use original marking with qualified values");
                String preName=name.equals("hello-world")?"CanPrint":"NonnegativeOffer",postName=name.equals("hello-world")?"CountIncremented":"BestOfferRetained";
                var operationChecks=new ArrayList<Map<String,Object>>();boolean prePassed=false,postPassed=false,preCheckpoint=false,postCheckpoint=false;
                long entries=projector.coordinator().journal().persistedEntries();
                for(long offset=0;offset<entries;) {
                    var page=projector.coordinator().journal().page(offset,128);assertFalse(page.entries().isEmpty());offset=page.nextOrdinal();
                    for(var entry:page.entries()) {
                        preCheckpoint|=entry.kind().equals("OPERATION_PRE");postCheckpoint|=entry.kind().equals("OPERATION_POST");
                        // Original-engine evaluation is asynchronous: the
                        // completed native contracts are persisted as ENGINE_RESULT.
                        if(entry.result().semantic()==null)continue;
                        for(var outcome:entry.result().semantic().constraints())if(outcome.id().contains(preName)||outcome.id().contains(postName)) {
                            operationChecks.add(Map.of("checkpoint",entry.kind(),"id",outcome.id(),"kind",outcome.kind(),"verdict",outcome.verdict().name(),"diagnostic",outcome.diagnostic(),"invocation",outcome.invocation(),"witness",outcome.witness()));
                            boolean exact=!outcome.invocation().isBlank()&&outcome.witness().keySet().stream().anyMatch(k->k.startsWith("before."))&&outcome.witness().keySet().stream().anyMatch(k->k.startsWith("after."));
                            prePassed|=exact&&outcome.kind().equals("PRE")&&outcome.id().contains(preName)&&outcome.verdict().name().equals("PASS");
                            postPassed|=exact&&outcome.kind().equals("POST")&&outcome.id().contains(postName)&&outcome.verdict().name().equals("PASS");
                        }
                    }
                }
                Files.write(evidence.resolve("operation-checks.json"),CanonicalJson.encode(operationChecks));
                assertTrue(preCheckpoint&&prePassed,"Authored precondition must execute on a real operation");
                assertTrue(postCheckpoint&&postPassed,"Authored postcondition must execute with a matching native @pre state");
                var cuts=new org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePresenter(facade).capture();assertFalse(cuts.isEmpty());
                var state=projector.coordinator().read(()->host.system().state().allObjects().stream().map(o->Map.of("name",o.name(),"class",o.cls().name(),"attributes",o.state(host.system().state()).attributeValueMap().toString())).toList());
                Files.write(evidence.resolve("state.json"),CanonicalJson.encode(state));
                facade.exportRuntimeReplay(evidence.resolve("replay"));
                Files.write(evidence.resolve("result.json"),CanonicalJson.encode(evaluated.toMap()));
                var receipt=new TreeMap<String,Object>();receipt.put("case",name);receipt.put("run",run);receipt.put("producerPid",child);receipt.put("usePid",ProcessHandle.current().pid());receipt.put("sameMSystem",system==host.system());receipt.put("timelineCuts",cuts.size());receipt.put("journalBytes",projector.coordinator().journal().persistedBytes());receipt.put("freshness",evaluated.freshness());receipt.put("coverage",evaluated.coverage());receipt.put("originalEngine",core);receipt.put("goalStatuses",statuses);
                facade.stopOwnedProducer();assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false));assertNotNull(host.system());
                facade.restartOwnedProducer();long restarted=facade.ownedProducerStatus().pid();String newRun=facade.ownedProducerStatus().runId();
                assertNotEquals(child,restarted);assertNotEquals(run,newRun);assertEquals("MODEL_READY",facade.workflowStatus().state());
                var next=OwnedProducerRuntimeIT.projector(facade);assertNotNull(next);assertNotSame(projector,next);
                assertTrue(next.coordinator().journal().persistedEntries()<projector.coordinator().journal().persistedEntries(),"New owner starts a fresh journal");
                facade.startRuntime();assertEquals("RUNNING",facade.ownedProducerStatus().state());facade.stopOwnedProducer();assertFalse(ProcessHandle.of(restarted).map(ProcessHandle::isAlive).orElse(false));
                receipt.put("restartRun",newRun);receipt.put("restartPid",restarted);receipt.put("restartAndCleanup",true);
                // Delete only this test's verified temporary copy. The production examples and reference repository remain untouched.
                assertTrue(root.toRealPath().startsWith(copiedBase.toRealPath()));
                try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}assertFalse(Files.exists(root));
                var replay=facade.replayRuntime(evidence.resolve("replay"));assertTrue(replay.complete(),replay.toString());
                var replayManifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(evidence.resolve("replay/manifest.json"))));
                assertEquals(replayManifest.get("finalStateHash"),replay.finalStateHash());assertEquals(replayManifest.get("finalResultHash"),replay.finalResultHash());
                // Validate the whole recording once. Canonical UI Next rebuilds
                // every prefix; walking every button here repeats O(n²) work,
                // whereas the full report already checks every recorded entry.
                facade.openStepReplay(evidence.resolve("replay"));assertNotNull(facade.runtimeVerificationResult());facade.resetStepReplay();
                if(facade.stepReplayStatus().total()>0){facade.nextStepReplay();facade.previousStepReplay();assertEquals(0,facade.stepReplayStatus().step());}
                assertNotNull(facade.runtimeVerificationResult());receipt.put("replayCheckedEntries",replay.checkedEntries());receipt.put("replayFinalStateHash",replay.finalStateHash());receipt.put("replayFinalResultHash",replay.finalResultHash());receipt.put("replayAfterAllCopiedInputsDeleted",true);receipt.put("status","PASS");
                Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(receipt));
            } finally {
                var result=facade.runtimeVerificationResult();if(result!=null)Files.write(evidence.resolve("last-result.json"),CanonicalJson.encode(result.toMap()));
                var p=OwnedProducerRuntimeIT.projector(facade);
                if(p!=null) {
                    var field=p.getClass().getDeclaredField("evidence");field.setAccessible(true);
                    Files.write(evidence.resolve("accepted-runtime-facts.json"),CanonicalJson.encode(p.coordinator().read(()->((Map<?,?>)read(field,p)).values().stream().map(f->{var fact=(org.jacamo.bridge.contract.RuntimeFact)f;return Map.of("identity",fact.id().canonical(),"kind",fact.kind().name(),"values",fact.values(),"quality",fact.completeness().name());}).toList())));
                }
                if(p!=null&&!Files.exists(evidence.resolve("state.json")))Files.write(evidence.resolve("state.json"),CanonicalJson.encode(p.coordinator().read(()->host.system().state().allObjects().stream().map(o->Map.of("name",o.name(),"class",o.cls().name(),"attributes",o.state(host.system().state()).attributeValueMap().toString())).toList())));
                facade.stopOwnedProducer();
                try(var output=Files.newBufferedWriter(evidence.resolve("cartago-events.jsonl"),java.nio.charset.StandardCharsets.UTF_8)) {
                    for(var frame:wire){output.write(new String(CanonicalJson.encode(frame),java.nio.charset.StandardCharsets.UTF_8));output.newLine();}
                }
                for(Path base:List.of(checkout.resolve("use-plugin/target/owned-runtime"),checkout.resolve("work/owned-runtime")))if(Files.isDirectory(base))try(var dirs=Files.list(base)){for(Path dir:dirs.toList())if(!run.isBlank()&&Files.isRegularFile(dir.resolve("prepared.json"))&&Files.readString(dir.resolve("prepared.json")).contains(run))for(String log:List.of("prepare.stdout.log","prepare.stderr.log","producer.stdout.log","producer.stderr.log"))if(Files.isRegularFile(dir.resolve(log)))Files.copy(dir.resolve(log),evidence.resolve(log));}
                for(var entry:hashes.entrySet())assertEquals(entry.getValue(),sha(source.resolve(entry.getKey())),"Never mutate case inputs while running");
                System.out.println("ADDITIONAL_CASE_EVIDENCE="+evidence);
            }
        }
    }
    private static String sha(Path p)throws Exception{return org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement.Asset.of("hash","bytes",Files.readAllBytes(p)).sha256();}
    private static Object read(java.lang.reflect.Field f,Object owner){try{return f.get(owner);}catch(IllegalAccessException e){throw new RuntimeException(e);}}
    private static Map<String,String> goalStatuses(org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult result) {
        var statuses=new TreeMap<String,String>();if(result.semantic()==null)return statuses;
        var core=CanonicalJson.object(result.semantic().observation().get("originalEngine"));
        for(Object context:(List<?>)core.getOrDefault("contexts",List.of()))for(Object occurrence:CanonicalJson.object(CanonicalJson.object(context).get("occurrences")).values()) {
            var row=CanonicalJson.object(occurrence);statuses.put(Objects.toString(row.get("sourceElement")),Objects.toString(row.get("aggregateStatus")));
        }
        return statuses;
    }
}
