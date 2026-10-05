package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.eventbus.Subscribe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.ContractCodec;
import org.jacamo.bridge.contract.ContractPayloads;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.RuntimeSnapshot;
import org.jacamo.bridge.contract.semantic.SemanticContractCodec;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.bridge.BridgeConnectionConfig;
import org.tzi.use.plugins.jacamo.bridge.BridgeTransport;
import org.tzi.use.plugins.jacamo.bridge.BridgeTransportFactory;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.events.AtomicStateChangedEvent;

/** Read-only inventory of an original project in a separate official producer JVM.
 * No fabricated runtime events, source edits, second active system, or name matching.
 * Complete per-object/per-link evidence is persisted, not just a GUI total.
 */
public class HelloWorldSemanticInventoryIT {
    private String oldHeight, oldWidth;
    @org.junit.jupiter.api.BeforeEach void configureUseDiagramDefaults() {
        oldHeight=System.getProperty("use.gui.view.classdiagram.class.minheight");
        oldWidth=System.getProperty("use.gui.view.classdiagram.class.minwidth");
        System.setProperty("use.gui.view.classdiagram.class.minheight","40");
        System.setProperty("use.gui.view.classdiagram.class.minwidth","140");
    }
    @org.junit.jupiter.api.AfterEach void restoreUseDiagramDefaults() {
        restoreProperty("use.gui.view.classdiagram.class.minheight",oldHeight);
        restoreProperty("use.gui.view.classdiagram.class.minwidth",oldWidth);
    }
    private static void restoreProperty(String key,String value) {
        if(value==null)System.clearProperty(key);else System.setProperty(key,value);
    }
    @Test void originalHelloWorldAutoInventory() throws Exception { audit(NativeProjectionMode.AUTO); }
    @Test void originalHelloWorldFullInventory() throws Exception { audit(NativeProjectionMode.FULL); }

    private void audit(NativeProjectionMode mode) throws Exception {
        Path original=Path.of("../../jacamo/doc/tutorials/hello-world/code/helloworld/helloworld.jcm").toAbsolutePath().normalize();
        Path base=Path.of(System.getProperty("use.jacamo.inventory.directory", "target/hello-world-object-audit"));
        Path evidence=base.resolve(mode.name()+"-"+System.currentTimeMillis()).toAbsolutePath().normalize();
        String oldMode=System.getProperty(NativeProjectionMode.PROPERTY);
        System.setProperty(NativeProjectionMode.PROPERTY,mode.name());
        Map<String,String> originalHashes=allHashes(original.getParent());
        try(var producer=new ManagedProducerTestSupport(original,evidence)) {
            write(evidence.resolve("source-hashes-before.json"),originalHashes);
            var recording=new RecordingTransportFactory(evidence);
            producer.configureWorkflow(); Session session=new Session();
            try(var facade=new DefaultJaCaMoFacade(Path.of("."),SemanticAuthority.BRIDGE,
                    ()->producer.configuration,recording,PipelineMode.CODE_GROUNDED_NATIVE,session)) {
                producer.clearWorkflowProperties(); facade.configureBridge(producer.configuration);
                facade.importProject(producer.jcm);
                assertEquals("MODEL_READY",facade.workflowStatus().state());
                assertFalse(Files.exists(producer.control.resolve("started.json")));
                var system=session.system(); assertSame(system,facade.materializedSystem());
                CodeGroundedNativePipeline.Result pipeline=pipeline(facade);
                var semantic=new TreeMap<String,Map<String,Object>>();
                collectSemantic(SemanticContractCodec.toTree(recording.model.semanticContract()),semantic);
                write(evidence.resolve("semantic-entities.json"),semantic.values().stream().toList());
                var semanticCounts=new TreeMap<String,Long>();
                semantic.values().forEach(value->semanticCounts.merge((String)CanonicalJson.object(value.get("metadata")).get("sourceKind"),1L,Long::sum));
                write(evidence.resolve("semantic-counts.json"),semanticCounts);
                var initialCounts=new TreeMap<String,Long>();
                pipeline.state().semanticObjectIndex().values().forEach(object->initialCounts.merge(object.cls().name(),1L,Long::sum));
                write(evidence.resolve("static-materialization-counts.json"),initialCounts);
                var inventory=new Inventory(facade,pipeline,semantic,evidence,recording);
                try(var diagramAudit=new org.tzi.use.plugins.jacamo.codegrounded.ObjectDiagramLifecycleEvidence(facade,session,evidence)) {
                var phases=new ArrayList<Map<String,Object>>();
                phases.add(inventory.dump("01-model-ready"));
                diagramAudit.capture("01-model-ready");
                var expectedInitial=compareCartago(inventory,recording.runtime);
                write(evidence.resolve("01-authoritative-identity-comparison.json"),expectedInitial);
                assertTrue((Boolean)expectedInitial.get("matches"),expectedInitial.toString());
                system.getEventBus().register(inventory);
                try {
                    // The test profile only checks identities actually exposed by the native model.
                    Path profile=evidence.resolve("identity-audit.ocl");
                    Files.writeString(profile,"context Agent inv AuditUniqueIdentity: Agent.allInstances()->isUnique(semanticId)\n"
                            +"context Workspace inv AuditUniqueWorkspace: Workspace.allInstances()->isUnique(semanticId)\n"
                            +"context Agent inv AuditDomainBeliefs: self.beliefs->isUnique(semanticId)\n");
                    int beforeProfile=system.state().numObjects();
                    facade.loadVerificationProfile(profile);
                    assertEquals("OCL_READY",facade.workflowStatus().state());
                    assertTrue(system.state().numObjects()>=beforeProfile); // Relevant authored seeds may materialize at profile load.
                    phases.add(inventory.dump("02-ocl-ready"));
                    diagramAudit.capture("02-ocl-ready");
                    long beforeStart=facade.verificationSnapshot().currentVersion();
                    facade.startRuntime();
                    assertEquals("LIVE",facade.workflowStatus().state());
                    assertTrue(Files.exists(producer.control.resolve("started.json")));
                    phases.add(inventory.dump("03-start-ack"));
                    diagramAudit.capture("03-start-ack");
                    Thread.sleep(1500); phases.add(inventory.dump("04-live-early"));
                    diagramAudit.capture("04-live-early");
                    Thread.sleep(12000); phases.add(inventory.dump("05-live-mutations"));
                    diagramAudit.capture("05-live-mutations");
                    assertTrue(facade.verificationSnapshot().currentVersion()>beforeStart,"No actual producer mutation observed");
                    facade.resyncRuntime(); assertSame(system,session.system());
                    phases.add(inventory.dump("06-authoritative-resync"));
                    diagramAudit.capture("06-authoritative-resync");
                    diagramAudit.assertSourceRelations(recording.runtime,"06-authoritative-resync");
                    assertTrue(inventory.projector().coordinator().read(()->inventory.projector().mutations().structureValid()),
                            "The original post-start GroupBoard players must restore the required role multiplicities");
                    var current=compareCartago(inventory,recording.runtime);
                    write(evidence.resolve("06-authoritative-identity-comparison.json"),current);
                    assertTrue((Boolean)current.get("matches"),current.toString());
                    Thread.sleep(1500);
                    facade.resyncRuntime(); assertSame(system,session.system());
                    phases.add(inventory.dump("07-repeated-resync"));
                    diagramAudit.capture("07-repeated-resync");
                    var goalView=facade.goalView();
                    assertTrue(goalView.schemes().stream().anyMatch(s->s.runtime() && !s.goals().isEmpty()),"No observed runtime Scheme goals");
                    assertTrue(goalView.schemes().stream().flatMap(s->s.goals().stream()).anyMatch(g->g.operator().equals("sequence")));
                    org.tzi.use.plugins.jacamo.codegrounded.GoalWorkbenchEvidence.capture(facade,session,evidence,false);
                    write(evidence.resolve("runtime-performance.json"),facade.runtimePerformanceMetrics());
                    write(evidence.resolve("verification-snapshot.json"),facade.verificationSnapshot().toMap());
                    Path completeBundle=evidence.resolve("recorded-replay-before-disconnect");
                    facade.exportRuntimeReplay(completeBundle);
                    facade.disconnectRuntime(); assertSame(system,session.system());
                    phases.add(inventory.dump("08-disconnected-stale-coverage"));
                    diagramAudit.capture("08-disconnected-stale-coverage");
                    facade.connectRuntime(); assertSame(system,session.system());
                    phases.add(inventory.dump("09-reconnected"));
                    diagramAudit.capture("09-reconnected");
                    facade.importProject(producer.jcm); assertSame(system,session.system());
                    phases.add(inventory.dump("10-repeated-import"));
                    diagramAudit.capture("10-repeated-import");
                    int beforeReload=system.state().numObjects(); facade.loadVerificationProfile(profile);
                    assertEquals(beforeReload,system.state().numObjects()); assertSame(system,session.system());
                    phases.add(inventory.dump("11-profile-reload"));
                    diagramAudit.capture("11-profile-reload");
                    var finalComparison=compareCartago(inventory,recording.runtime);
                    write(evidence.resolve("11-authoritative-identity-comparison.json"),finalComparison);
                    assertTrue((Boolean)finalComparison.get("matches"),finalComparison.toString());
                    Path bundle=evidence.resolve("recorded-replay"); facade.exportRuntimeReplay(bundle);
                    // Producer activity cannot be confused with replay mutations: disconnect first.
                    facade.disconnectRuntime(); String beforeReplay=facade.formalStateStatus().sha256();
                    var gapReplay=facade.replayRuntime(bundle);
                    write(evidence.resolve("replay-with-intentional-disconnect-gap.json"),recordTree(gapReplay));
                    assertFalse(gapReplay.complete(),"Recorded disconnect must remain a visible coverage gap");
                    assertEquals(List.of("REPLAY_PARTIAL_COVERAGE_GAP"),gapReplay.diagnostics());
                    var replay=facade.replayRuntime(completeBundle);
                    write(evidence.resolve("replay.json"),recordTree(replay));
                    assertTrue(replay.complete(),replay.diagnostics().toString());
                    assertEquals(beforeReplay,facade.formalStateStatus().sha256()); assertSame(system,session.system());
                    var analysis=facade.reanalyzeRuntime(completeBundle,evidence.resolve("reanalysis"));
                    write(evidence.resolve("reanalysis.json"),recordTree(analysis));
                    assertTrue(analysis.complete(),analysis.diagnostics().toString());
                    assertEquals(beforeReplay,facade.formalStateStatus().sha256()); assertSame(system,session.system());
                    phases.add(inventory.dump("12-after-isolated-replay"));
                    diagramAudit.capture("12-after-isolated-replay");
                    write(evidence.resolve("atomic-count-timeline.json"),inventory.timeline);
                    write(evidence.resolve("lifecycle.json"),phases);
                    facade.exportNativeUse(evidence.resolve("helloworld.use")); facade.exportNativeSoil(evidence.resolve("helloworld.cmd"));
                    var report=new LinkedHashMap<String,Object>();
                    report.put("status","PASS"); report.put("projection",mode.name()); report.put("originalJcm",original.toString());
                    report.put("semanticEntityCount",semantic.size()); report.put("semanticCounts",semanticCounts);
                    report.put("staticCounts",initialCounts); report.put("model",inventory.model()); report.put("phases",phases);
                    report.put("sameActiveSystem",true); report.put("identityParity",finalComparison);
                    report.put("selectiveProjection",org.tzi.use.plugins.jacamo.codegrounded.ProjectionExposureEvidence.assertAndReport(facade));
                    report.put("replayComplete",replay.complete()); report.put("reanalysisComplete",analysis.complete());
                    report.put("sourceUnchanged",originalHashes.equals(allHashes(original.getParent())));
                    write(evidence.resolve("summary.json"),report);
                    assertTrue(originalHashes.equals(allHashes(original.getParent())));
                    // Inventory is complete; the old count view must not observe the separately selected replay.
                    diagramAudit.close();
                    inventory.detachCountView();
                    producer.close(); // Recording was frozen before the observation-disconnect marker.
                    org.tzi.use.gui.main.MainWindow.setJavaFxCall(true);
                    var window=org.tzi.use.plugins.jacamo.codegrounded.StepReplayProof.onEdt(()->org.tzi.use.gui.main.MainWindow.create(session,org.tzi.use.runtime.impl.PluginRuntime.getInstance()));
                    try {
                        org.tzi.use.plugins.jacamo.codegrounded.StepReplayProof.run(facade,session,window,completeBundle,evidence);
                        report.put("stepReplayComplete",true);write(evidence.resolve("summary.json"),report);
                    } finally {
                        org.tzi.use.plugins.jacamo.codegrounded.StepReplayProof.onEdt(()->{window.dispose();org.tzi.use.gui.main.MainWindow.setJavaFxCall(false);return null;});
                    }
                    System.out.println("HELLO_WORLD_SEMANTIC_INVENTORY_PASS projection="+mode+" evidence="+evidence);
                } finally {
                    system.getEventBus().unregister(inventory);
                    inventory.detachCountView();
                }
                }
            }
            write(evidence.resolve("source-hashes-after.json"),allHashes(original.getParent()));
        } finally {
            if(oldMode==null) System.clearProperty(NativeProjectionMode.PROPERTY); else System.setProperty(NativeProjectionMode.PROPERTY,oldMode);
        }
    }

    private static Map<String,Object> compareCartago(Inventory inventory,RuntimeSnapshot snapshot) {
        return inventory.projector().coordinator().read(()->{
            var expected=new TreeMap<String,Set<String>>();
            expected.put("workspace",new java.util.TreeSet<>());expected.put("artifact",new java.util.TreeSet<>());
            for(var fact:snapshot.facts()) {
                for(String declarationKey:List.of("workspaceDeclarationSemanticId","artifactDeclarationSemanticId"))
                    if(fact.values().get(declarationKey) instanceof String declaration)
                        assertSame(inventory.projector().mutations().objectForSemanticId(declaration),
                            inventory.projector().mutations().objectForSemanticId(fact.values().get("semanticId").toString()),
                            "An officially proved declaration/runtime alias must reuse one native object");
                if("UPSERT_CARTAGO_WORKSPACE".equals(fact.values().get("normalizedEventKind"))) expected.get("workspace").add(fact.values().get("semanticId").toString());
                if("UPSERT_CARTAGO_ARTIFACT".equals(fact.values().get("normalizedEventKind")) && org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.domainArtifact(
                        fact.values().get("artifactTypeJavaClassName").toString(),fact.values().getOrDefault("artifactTypeOrigin","UNAVAILABLE").toString()))
                    expected.get("artifact").add(fact.values().get("semanticId").toString());
            }
            var rows=new ArrayList<Map<String,Object>>(); boolean matches=true;
            for(var entry:expected.entrySet()) {
                var actual=new java.util.TreeSet<String>();
                inventory.facade.materializedSystem().state().allObjects().forEach(object->{
                    // Subtypes, if exact C06 reflection is supported, are still Artifact instances.
                    if(org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.kind(object.cls()).equals(entry.getKey())
                            && !object.state(inventory.facade.materializedSystem().state()).attributeValue("uuid").isUndefined()) {
                        var bound=entry.getValue().stream().filter(id->inventory.projector().mutations().objectForSemanticId(id)==object).toList();
                        assertEquals(1,bound.size(),"Each observed instance requires exactly one identity from this official cut: "+object.name());
                        actual.add(bound.getFirst());
                    }
                });
                var missing=new java.util.TreeSet<>(entry.getValue()); missing.removeAll(actual);
                var stale=new java.util.TreeSet<>(actual); stale.removeAll(entry.getValue());
                matches&=missing.isEmpty()&&stale.isEmpty();
                rows.add(Map.of("class",entry.getKey(),"expected",entry.getValue().size(),"actual",actual.size(),"missing",missing,"stale",stale));
            }
            // Mutable literal/role/functional occurrences have their own exact source proof in dump().
            for(var cls:inventory.facade.materializedSystem().model().classes()) {
                if(expected.containsKey(org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.kind(cls)))continue;
                if(!org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.kind(cls).equals("agent-program")) continue;
                var staticIds=new java.util.TreeSet<String>();inventory.pipeline.state().semanticObjectIndex().forEach((id,object)->{
                    if(object.cls()==cls)staticIds.add(id);
                });
                var actualIds=new java.util.TreeSet<String>();inventory.facade.materializedSystem().state().objectsOfClass(cls)
                        .forEach(object->actualIds.add(inventory.semanticId(object)));
                var missing=new java.util.TreeSet<>(staticIds);missing.removeAll(actualIds);
                var unexpected=new java.util.TreeSet<>(actualIds);unexpected.removeAll(staticIds);
                matches&=missing.isEmpty()&&unexpected.isEmpty();
                rows.add(Map.of("class",cls.name(),"expected",staticIds.size(),"actual",actualIds.size(),"missing",missing,"stale",unexpected));
            }
            return Map.of("snapshot",snapshot.snapshotId(),"matches",matches,"rows",rows);
        });
    }

    private static CodeGroundedNativePipeline.Result pipeline(DefaultJaCaMoFacade facade) {
        return (CodeGroundedNativePipeline.Result)field(field(facade,"nativeWorkspace"),"pipeline");
    }
    private static Object field(Object object,String name) {
        try { var field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static Map<String,String> allHashes(Path root) throws Exception {
        var hashes=new TreeMap<String,String>(); try(var paths=Files.walk(root)) {
            for(Path path:paths.filter(Files::isRegularFile).toList()) hashes.put(root.relativize(path).toString(),
                    org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(Files.readAllBytes(path)));
        } return hashes;
    }
    // Local audit files can exceed the bounded Bridge message size; never weaken the wire codec.
    private static void write(Path file,Object value) throws Exception {
        Files.write(file,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(value));
    }
    private static void collectSemantic(Object value,Map<String,Map<String,Object>> entities) {
        if(value instanceof Map<?,?> map) {
            if(map.get("metadata") instanceof Map<?,?> metadata && metadata.get("semanticId") instanceof String id) {
                assertNull(entities.put(id,CanonicalJson.object(map)),"Duplicate extracted semantic identity: "+id);
            }
            map.values().forEach(child->collectSemantic(child,entities));
        } else if(value instanceof List<?> list) list.forEach(child->collectSemantic(child,entities));
    }
    private static Object recordTree(Object value) {
        if(value==null||value instanceof String||value instanceof Number||value instanceof Boolean)return value;
        if(value instanceof Enum<?> item)return item.name(); if(value instanceof Path||value instanceof Instant)return value.toString();
        if(value instanceof Map<?,?> map) { var result=new LinkedHashMap<String,Object>();map.forEach((key,item)->result.put(String.valueOf(key),recordTree(item)));return result; }
        if(value instanceof Iterable<?> items){var result=new ArrayList<>();items.forEach(item->result.add(recordTree(item)));return result;}
        if(value.getClass().isRecord()) {var result=new LinkedHashMap<String,Object>();
            for(var component:value.getClass().getRecordComponents())try{result.put(component.getName(),recordTree(component.getAccessor().invoke(value)));}
            catch(ReflectiveOperationException error){throw new IllegalStateException(error);} return result;}
        return value.toString();
    }

    public static final class RecordingTransportFactory implements BridgeTransportFactory {
        final Path directory; final AtomicInteger connection=new AtomicInteger();
        volatile ModelSnapshot model; volatile RuntimeSnapshot runtime;
        final Map<String,Map<String,Object>> runtimeEntities=new java.util.concurrent.ConcurrentHashMap<>();
        public RecordingTransportFactory(Path directory){this.directory=directory;}
        public RuntimeSnapshot lastRuntimeSnapshot(){return runtime;}
        @Override public BridgeTransport open(BridgeConnectionConfig configuration) {
            BridgeTransport delegate=BridgeTransportFactory.localTcp().open(configuration); int ordinal=connection.incrementAndGet();
            return new BridgeTransport() {
                private byte[] persist(String kind,byte[] bytes) {
                    try { Files.write(directory.resolve(String.format("bridge-%02d-%s.json",ordinal,kind)),bytes); }
                    catch(java.io.IOException error){throw new IllegalStateException(error);} return bytes;
                }
                @Override public byte[] handshake(){return persist("handshake",delegate.handshake());}
                @Override public byte[] modelSnapshot(){byte[] bytes=persist("model",delegate.modelSnapshot());model=ContractPayloads.model(ContractCodec.decode(bytes).payload());return bytes;}
                @Override public byte[] runtimeSnapshot(){byte[] bytes=persist("runtime",delegate.runtimeSnapshot());runtime=ContractPayloads.runtime(ContractCodec.decode(bytes).payload());
                    runtime.facts().forEach(fact->remember(fact.values()));return bytes;}
                private Consumer<byte[]> observed(Consumer<byte[]> receiver){return bytes->{
                    var envelope=ContractCodec.decode(bytes);if(envelope.messageType()==org.jacamo.bridge.contract.MessageType.RUNTIME_EVENT)
                        remember(CanonicalJson.object(envelope.payload().get("after")));receiver.accept(bytes);};}
                @Override public Subscription subscribe(String token,Consumer<byte[]> receiver){return delegate.subscribe(token,observed(receiver));}
                @Override public Subscription subscribe(String token,Consumer<byte[]> receiver,Consumer<RuntimeException> failure){return delegate.subscribe(token,observed(receiver),failure);}
                @Override public void acknowledge(String token){delegate.acknowledge(token);}
                @Override public byte[] control(org.jacamo.bridge.contract.RuntimeControlContract.Request request){return persist("control",delegate.control(request));}
                @Override public void close(){delegate.close();}
            };
        }
        private void remember(Map<String,Object> payload) {
            if(payload.get("semanticId") instanceof String id)runtimeEntities.put(id,payload);
            if(payload.get("environmentSemanticId") instanceof String id)runtimeEntities.put(id,payload);
            if(payload.get("artifactTypeSemanticId") instanceof String id)runtimeEntities.put(id,payload);
            for(Object value:payload.values()) if(value instanceof List<?> list) for(Object item:list)
                if(item instanceof Map<?,?> map) remember(CanonicalJson.object(map));
            if(payload.get("properties") instanceof List<?> properties)properties.forEach(value->remember(CanonicalJson.object(value)));
        }
    }

    private static final class Inventory {
        final DefaultJaCaMoFacade facade; final CodeGroundedNativePipeline.Result pipeline; final Map<String,Map<String,Object>> semantic;
        final Path directory; final Map<String,Long> firstVersions=new HashMap<>(); final Map<String,String> namesToIds=new HashMap<>();
        final RecordingTransportFactory recording;
        final List<Map<String,Object>> timeline=new ArrayList<>();
        final org.tzi.use.gui.views.ObjectCountView countView;
        boolean countDetached;
        void detachCountView(){projector().coordinator().read(()->{if(!countDetached){countDetached=true;countView.detachModel();}return null;});}
        Inventory(DefaultJaCaMoFacade facade,CodeGroundedNativePipeline.Result pipeline,Map<String,Map<String,Object>> semantic,Path directory,RecordingTransportFactory recording) {
            this.facade=facade;this.pipeline=pipeline;this.semantic=semantic;this.directory=directory;this.recording=recording;
            pipeline.state().semanticObjectIndex().forEach((id,object)->{firstVersions.put(id,0L);namesToIds.put(object.name(),id);});
            countView=projector().coordinator().read(()->new org.tzi.use.gui.views.ObjectCountView(facade.materializedSystem()));
        }
        NativeRuntimeProjector projector(){return (NativeRuntimeProjector)field(field(facade,"nativeWorkspace"),"runtimeProjector");}
        String semanticId(MObject object) {
            var attribute=object.cls().attribute("semanticId",true);
            if(attribute==null)return namesToIds.getOrDefault(object.name(),"");
            var value=object.state(facade.materializedSystem().state()).attributeValue(attribute);
            return value instanceof StringValue string?string.value():namesToIds.getOrDefault(object.name(),"");
        }
        @Subscribe public void atomic(AtomicStateChangedEvent event) {
            var result=facade.runtimeVerificationResult(); if(result==null)return;
            var added=new ArrayList<Map<String,Object>>(); var deleted=new ArrayList<Map<String,Object>>();
            event.getNewObjects().forEach(object->{String id=semanticId(object);firstVersions.putIfAbsent(id,result.stateVersion());namesToIds.put(object.name(),id);
                added.add(Map.of("object",object.name(),"class",object.cls().name(),"semanticId",id));});
            event.getDeletedObjects().forEach(object->deleted.add(Map.of("object",object.name(),"class",object.cls().name(),"semanticId",namesToIds.getOrDefault(object.name(),""))));
            timeline.add(Map.of("stateVersion",result.stateVersion(),"eventId",result.eventId(),"session",result.sessionId(),"generation",result.generation(),
                    "counts",counts(),"added",added,"deleted",deleted,"newLinks",event.getNewLinks().size(),"deletedLinks",event.getDeletedLinks().size()));
        }
        Map<String,Long> counts(){var counts=new TreeMap<String,Long>();facade.materializedSystem().model().classes().forEach(cls->counts.put(cls.name(),(long)facade.materializedSystem().state().objectsOfClass(cls).size()));return counts;}
        Map<String,Object> model() {
            var model=facade.materializedSystem().model();var classes=new ArrayList<Map<String,Object>>();
            model.classes().stream().sorted(Comparator.comparing(cls->cls.name())).forEach(cls->{
                var traces=pipeline.trace().sourcesForTarget("class:"+cls.name());
                if(traces.isEmpty()) {
                    assertEquals("artifact",org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.kind(cls));
                    String fqcn=org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.decode(cls.getAnnotationValue("DomainProjection","javaClass64"));
                    assertTrue(recording.runtimeEntities.values().stream().anyMatch(f->fqcn.equals(f.get("artifactTypeJavaClassName"))),"Runtime classifier without exact producer type evidence: "+fqcn);
                }
                classes.add(Map.of("class",cls.name(),"classification",org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.kind(cls),"rules",traces.isEmpty()?List.of("C03"):traces.stream().map(trace->trace.ruleId()).distinct().toList(),
                        "sourceConcept",traces.isEmpty()?List.of("OFFICIAL_RUNTIME_ARTIFACT_TYPE"):traces.stream().map(trace->trace.sourceKind()).distinct().toList(),"projectionStatus","MATERIALIZED",
                        "operations",cls.operations().stream().map(operation->operation.toString()).sorted().toList()));
            });
            return Map.of("classCount",model.classes().size(),"associationCount",model.associations().size(),
                    "compositionCount",model.associations().stream().filter(association->association.aggregationKind()==2).count(),
                    "operationCount",model.classes().stream().mapToInt(cls->cls.operations().size()).sum(),"classes",classes,
                    "associations",model.associations().stream().sorted(Comparator.comparing(association->association.name()))
                            .map(association->Map.of("name",association.name(),"aggregation",association.aggregationKind(),"ends",association.associationEnds().stream()
                                    .map(end->Map.of("class",end.cls().name(),"role",end.name(),"multiplicity",end.multiplicity().toString(),"ordered",end.isOrdered())).toList())).toList());
        }
        Map<String,Object> dump(String phase) {
            return projector().coordinator().read(()->{
                var system=facade.materializedSystem();var state=system.state();var result=facade.runtimeVerificationResult();
                var objects=new ArrayList<Map<String,Object>>(); var identityCounts=new HashMap<String,Integer>();
                var runtimeTraces=projector().trace();
                for(MObject object:state.allObjects().stream().sorted(Comparator.comparing(MObject::name)).toList()) {
                    String id=semanticId(object);assertFalse(id.isBlank(),"Object without semantic identity: "+object.name());
                    identityCounts.merge(id,1,Integer::sum); firstVersions.putIfAbsent(id,result.stateVersion());namesToIds.put(object.name(),id);
                    var attributes=new TreeMap<String,Object>(); object.state(state).attributeValueMap().forEach((attribute,value)->attributes.put(attribute.name(),value instanceof StringValue string?string.value():value.toString()));
                    var traces=pipeline.trace().sourcesForTarget(object.name());var aliases=projector().runtimeAliases().values().stream().filter(alias->alias.targetSemanticId().equals(id)).toList();
                    var row=new LinkedHashMap<String,Object>();row.put("name",object.name());row.put("class",object.cls().name());row.put("semanticId",id);
                    row.put("classification",org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.kind(object.cls())); row.put("attributes",attributes);row.put("status","ACTIVE_IN_CURRENT_STATE");
                    row.put("sourceSemanticEntity",semantic.getOrDefault(id,Map.of())); row.put("sourceTraces",traces.stream().map(HelloWorldSemanticInventoryIT::recordTree).toList());
                    row.put("officialRuntimePayload",recording.runtimeEntities.getOrDefault(id,Map.of()));
                    assertTrue(semantic.containsKey(id)||recording.runtimeEntities.containsKey(id)
                            ||traces.stream().anyMatch(trace->semantic.containsKey(trace.sourceIdentity()))
                            ||exactInitialBeliefOccurrence(id)
                            ||exactFunctionalOccurrence(id),
                            "Object without an exact static/runtime/helper source: "+object.name()+" "+id);
                    row.put("runtimeAliases",aliases.stream().map(HelloWorldSemanticInventoryIT::recordTree).toList());
                    row.put("runtimeTraces",runtimeTraces.stream().filter(trace->trace.targetUseId().equals(object.name())).map(HelloWorldSemanticInventoryIT::recordTree).toList());
                    row.put("firstMaterializedVersion",firstVersions.get(id)); row.put("session",result.sessionId());row.put("generation",result.generation());objects.add(row);
                }
                assertTrue(identityCounts.values().stream().allMatch(count->count==1),"Duplicate object source identity: "+identityCounts);
                var links=state.allLinks().stream().map(link->Map.of("association",link.association().name(),"participants",link.linkedObjects().stream()
                        .map(object->Map.of("name",object.name(),"semanticId",semanticId(object))).toList())).sorted(Comparator.comparing(Object::toString)).toList();
                assertEquals(links.size(),links.stream().map(Object::toString).distinct().count(),"Double materialized links");
                assertTrue(state.allLinks().stream().allMatch(link->state.allObjects().containsAll(link.linkedObjects())),"Orphan link");
                var validation=new java.io.StringWriter();boolean structureValid=state.checkStructure(new java.io.PrintWriter(validation),true);
                assertEquals(!structureValid,result.outcomes().stream().anyMatch(o->o.diagnostic().equals("NATIVE_MULTIPLICITY_VIOLATION")),
                        "Observed native cardinality failures must be retained as verification FAIL: "+validation);
                for(var association:system.model().associations()) if(association.getAnnotation(org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.ROLE_ASSOCIATION)==null)
                    assertTrue(state.checkStructure(association,new java.io.PrintWriter(validation),true),validation.toString());
                var guiCounts=new TreeMap<String,Long>();
                var guiClasses=(org.tzi.use.uml.mm.MClass[])field(countView,"fClasses");var guiValues=(int[])field(countView,"fValues");
                for(int index=0;index<guiClasses.length;index++)guiCounts.put(guiClasses[index].name(),(long)guiValues[index]);
                assertEquals(counts(),guiCounts,"Object Count view must match the current native system at "+phase);
                var summary=new LinkedHashMap<String,Object>();summary.put("phase",phase);summary.put("stateVersion",result.stateVersion()); summary.put("session",result.sessionId());
                summary.put("generation",result.generation());summary.put("counts",counts());summary.put("objectCount",objects.size());summary.put("linkCount",links.size());
                summary.put("stateHash",result.stateHash());summary.put("coverage",result.coverage());summary.put("freshness",result.freshness());summary.put("structureValid",structureValid);
                summary.put("duplicateIdentities",0);summary.put("duplicateLinks",0);summary.put("orphanLinks",0);
                summary.put("guiObjectCountsMatch",true);
                try {write(directory.resolve(phase+"-objects.json"),objects);write(directory.resolve(phase+"-links.json"),links);write(directory.resolve(phase+"-model.json"),model());
                    write(directory.resolve(phase+"-summary.json"),summary);
                    write(directory.resolve(phase+"-bindings.json"),projector().projectionBindings().stream().map(HelloWorldSemanticInventoryIT::recordTree).toList());}
                catch(Exception error){throw new IllegalStateException(error);} return summary;
            });
        }
        private boolean exactFunctionalOccurrence(String id) {
            if(!id.startsWith("organisational-goal:") && !id.startsWith("mission:")) return false;
            try {
                Object tree=CanonicalJson.decode(id.substring(id.indexOf(':')+1).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if(!(tree instanceof List<?> pair) || pair.size()!=2) return false;
                return (semantic.containsKey(pair.getFirst()) || recording.runtimeEntities.containsKey(pair.getFirst())) && semantic.containsKey(pair.get(1));
            } catch(RuntimeException invalid) { return false; }
        }
        private boolean exactInitialBeliefOccurrence(String id) {
            if(!id.startsWith("initial-belief:")) return false;
            try {
                Object tree=CanonicalJson.decode(id.substring(id.indexOf(':')+1).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if(!(tree instanceof List<?> pair) || pair.size()!=2) return false;
                return semantic.containsKey(pair.getFirst()) && semantic.containsKey(pair.get(1))
                        && facade.materializedSystem().model().getClass("Belief").getAllAnnotations().values().stream()
                            .anyMatch(a->a.getName().startsWith("InitialBelief_")
                                && a.getAnnotationValue("id64").equals(org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.encode(id))
                                && a.getAnnotationValue("sourceId64").equals(org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.encode(pair.get(1).toString())));
            } catch(RuntimeException invalid) { return false; }
        }
    }
}
