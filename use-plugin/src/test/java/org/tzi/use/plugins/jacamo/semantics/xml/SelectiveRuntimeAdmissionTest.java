package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.uml.ocl.value.IntegerValue;
import static org.junit.jupiter.api.Assertions.*;

/** Native admission/rollback fixture, not external-process/live acceptance. */
class SelectiveRuntimeAdmissionTest {
    @TempDir Path temporary;
    @Test void completeSelectedSourceCutRecoversBootstrapPopulationWithoutTreatingOrdinaryDeltasAsPopulationProof() throws Exception {
        Path root=Path.of("examples/csl-counter"),inputs=root.resolve("authored-v1");
        var source=GenericMappingEngineTest.official(root.resolve("counter.jcm"));
        var program=new GenericMappingEngine().map(source,MappingInputs.read(root.resolve("counter.jcm"),inputs.resolve("stock-mapping.xml"),inputs.resolve("stock.ocl"),inputs.resolve("stock.csl"),inputs.resolve("stock.istar")));
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        var scope=ObservationCaptureScope.fromMap(CanonicalJson.object(built.xmlPreparation().preflight().get("captureScope")));
        var id=new BridgeEntityId("cartago","environment","artifact","/main","sample","bootstrap-incarnation");
        var artifact=new RuntimeFact(id,RuntimeFactKind.ARTIFACT,Map.of("uuid",id.incarnation(),"name","sample","semanticId","physical:sample","artifactTypeJavaClassName","demo.counter.Counter"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var property=new RuntimeFact(new BridgeEntityId("cartago","environment","observable-property-snapshot","physical:sample","p","snapshot"),RuntimeFactKind.PROPERTY,
                Map.of("artifactSemanticId","physical:sample","name","p","values",List.of("3"),"valueTypes",List.of("java.lang.Integer")),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var facts=List.of(artifact,property);
        try(var projector=new NativeRuntimeProjector(built,"run",1,source.modelRevision(),temporary.resolve("bootstrap"))) {
            var complete=cut(source.modelRevision(),1,facts,scope);
            var bootstrap=new RuntimeSnapshot(complete.snapshotId(),complete.modelRevision(),complete.captureStartedAt(),complete.captureEndedAt(),complete.startWatermarks(),complete.endWatermarks(),1,facts,Map.of("cartago",Completeness.PARTIAL),complete.stateFingerprint(),complete.captureMetadata());
            projector.applySnapshot(bootstrap,"run",1);
            assertEquals("SKIPPED",stockInvariant(projector));
            projector.apply(delta(source.modelRevision(),2,id,scope.hash(),4));
            assertEquals("SKIPPED",stockInvariant(projector),"An individual update cannot establish the whole selected population");
            var kinds=org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources.producerCartagoKinds(program.xmlPlan().inputs().specification());
            projector.apply(sourceCut(source.modelRevision(),3,facts,scope,Set.of(RuntimeFactKind.ARTIFACT)));
            assertEquals("SKIPPED",stockInvariant(projector),"A narrower receipt cannot certify unobserved PROPERTY/identity populations");
            var system=projector.system();
            projector.apply(sourceCut(source.modelRevision(),4,facts,scope,kinds));
            assertEquals("PASS",stockInvariant(projector),"The validated complete local cut establishes this program's selected population");
            assertSame(system,projector.system());
            projector.apply(delta(source.modelRevision(),5,id,scope.hash(),-1));
            assertEquals("FAIL",stockInvariant(projector),"Ordered selected deltas preserve the admitted population proof");
            projector.applySnapshot(new RuntimeSnapshot("partial-resync",complete.modelRevision(),Instant.EPOCH,Instant.EPOCH,Map.of("cartago",new SourceWatermark("cartago",6)),Map.of("cartago",new SourceWatermark("cartago",6)),1,facts,Map.of("cartago",Completeness.PARTIAL),"partial-resync",complete.captureMetadata()),"run",1);
            assertEquals("SKIPPED",stockInvariant(projector),"A later partial resync must not inherit the complete population claim");
            projector.apply(sourceCut(source.modelRevision(),7,facts,scope,kinds));
            assertEquals("PASS",stockInvariant(projector),"A replacement population proof must be applied even when every captured value is unchanged");
            projector.coordinator().coverageGap("REQUIRED_EVENT_MISSING");
            projector.apply(sourceCut(source.modelRevision(),8,facts,scope,kinds));
            assertFalse(Set.of("PASS","FAIL").contains(stockInvariant(projector)),"A current population receipt cannot recover a lost execution history");
            assertEquals("INCOMPLETE",projector.coordinator().latest().coverage());
        }
    }
    private static String stockInvariant(NativeRuntimeProjector projector) throws Exception {
        projector.coordinator().originalEngineDrained().get(10,java.util.concurrent.TimeUnit.SECONDS);projector.coordinator().read(()->null);
        return projector.coordinator().latest().semantic().constraints().stream().filter(r->r.id().equals("NATIVE:Pallet::NonnegativeWeight")&&r.kind().equals("INVARIANT")).findFirst().orElseThrow().verdict().name();
    }
    private static RuntimeEvent sourceCut(String revision,long sequence,List<RuntimeFact> facts,ObservationCaptureScope scope,Set<RuntimeFactKind> kinds) {
        var selected=facts.stream().filter(f->kinds.contains(f.kind())).toList();
        var marks=Map.of("cartago",new SourceWatermark("cartago",sequence));
        var capture=new RuntimeSnapshot("local-cut-"+sequence,revision,Instant.EPOCH,Instant.EPOCH,marks,marks,1,selected,Map.of("cartago",Completeness.COMPLETE),"local-fingerprint-"+sequence,
                Map.of("capturedFactKinds",kinds.stream().map(Enum::name).sorted().toList(),"captureScope",scope.evidence(),"captureScopeHash",scope.hash()));
        return new RuntimeEvent("local-cut-"+sequence,"run",1,revision,"cartago","cartago",sequence,Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,
                new BridgeEntityId("cartago","environment","source-cut","/main","capture","run"),null,"","",Map.of(),CartagoSourceCut.payload(capture),new SourceWatermark("cartago",sequence),Completeness.COMPLETE,List.of());
    }
    @Test void scopedRecordingReplaysExactStateAndResultAfterAllFiveCopiedInputsHaveBeenRemoved() throws Exception {
        Path original=Path.of("examples/csl-counter").toAbsolutePath().normalize();
        Path copied=java.nio.file.Files.createDirectory(temporary.resolve("inputs")).toAbsolutePath().normalize();
        try(var files=java.nio.file.Files.walk(original)){
            for(Path file:files.toList()){
                Path target=copied.resolve(original.relativize(file));
                if(java.nio.file.Files.isDirectory(file))java.nio.file.Files.createDirectories(target);
                else java.nio.file.Files.copy(file,target);
            }
        }
        var source=GenericMappingEngineTest.official(copied.resolve("counter.jcm"));var inputs=copied.resolve("authored-v1");
        var selected=MappingInputs.read(copied.resolve("counter.jcm"),inputs.resolve("stock-mapping.xml"),inputs.resolve("stock.ocl"),inputs.resolve("stock.csl"),inputs.resolve("stock.istar"));
        var program=new GenericMappingEngine().map(source,selected);
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        var scope=ObservationCaptureScope.fromMap(CanonicalJson.object(built.xmlPreparation().preflight().get("captureScope")));
        var id=new BridgeEntityId("cartago","environment","artifact","/main","sample","portable-incarnation");
        var artifact=new RuntimeFact(id,RuntimeFactKind.ARTIFACT,Map.of("uuid",id.incarnation(),"name","sample","semanticId","physical:sample","artifactTypeJavaClassName","demo.counter.Counter"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var property=new RuntimeFact(new BridgeEntityId("cartago","environment","observable-property-snapshot","physical:sample","p","snapshot"),RuntimeFactKind.PROPERTY,
                Map.of("artifactSemanticId","physical:sample","name","p","values",List.of("3"),"valueTypes",List.of("java.lang.Integer")),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var recording=temporary.resolve("recording");RuntimeVerificationResult expected;
        try(var projector=new NativeRuntimeProjector(built,"run",1,source.modelRevision(),temporary.resolve("live"))) {
            projector.applySnapshot(cut(source.modelRevision(),1,List.of(artifact,property),scope),"run",1);
            projector.apply(delta(source.modelRevision(),2,id,scope.hash(),4));
            projector.coordinator().originalEngineDrained().get(10,java.util.concurrent.TimeUnit.SECONDS);projector.coordinator().read(()->null);
            expected=projector.coordinator().lastObservation();
            new NativeRuntimeReplay().exportBundle(projector,built.nativeResult().export().useText(),recording);
        }
        assertTrue(copied.startsWith(temporary.toAbsolutePath().normalize()));assertNotEquals(original,copied);
        try(var files=java.nio.file.Files.walk(copied)){
            for(Path file:files.sorted(Comparator.reverseOrder()).toList()){
                assertTrue(file.toAbsolutePath().normalize().startsWith(copied));java.nio.file.Files.delete(file);
            }
        }
        assertFalse(java.nio.file.Files.exists(copied));
        var replay=new NativeRuntimeReplay().replay(recording);
        assertTrue(replay.complete(),replay.diagnostics().toString());
        assertEquals(expected.stateHash(),replay.finalStateHash());assertEquals(expected.resultHash(),replay.finalResultHash());
    }
    @Test void relevantNativeVerdictsMatchTheProjectionOfOneRecordedExecutionWithUnrelatedNoiseOmitted() throws Exception {
        Path root=Path.of("examples/csl-counter"),inputs=root.resolve("authored-v1");
        var source=GenericMappingEngineTest.official(root.resolve("counter.jcm"));
        var selected=MappingInputs.read(root.resolve("counter.jcm"),inputs.resolve("stock-mapping.xml"),inputs.resolve("stock.ocl"),inputs.resolve("stock.csl"),inputs.resolve("stock.istar"));
        var program=new GenericMappingEngine().map(source,selected);
        var recorded=new ArrayList<List<RuntimeFact>>();
        var id=new BridgeEntityId("cartago","environment","artifact","/main","sample","same-execution-incarnation");
        var noiseId=new BridgeEntityId("cartago","environment","artifact","/main","noise","noise-incarnation");
        for(int value:List.of(1,-1,1)) recorded.add(List.of(
                new RuntimeFact(id,RuntimeFactKind.ARTIFACT,Map.of("uuid",id.incarnation(),"name","sample","semanticId","physical:sample","artifactTypeJavaClassName","demo.counter.Counter"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()),
                new RuntimeFact(new BridgeEntityId("cartago","environment","observable-property-snapshot","physical:sample","p","snapshot"),RuntimeFactKind.PROPERTY,
                        Map.of("artifactSemanticId","physical:sample","name","p","values",List.of(Integer.toString(value)),"valueTypes",List.of("java.lang.Integer")),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()),
                new RuntimeFact(noiseId,RuntimeFactKind.ARTIFACT,Map.of("uuid",noiseId.incarnation(),"name","noise","semanticId","physical:noise","artifactTypeJavaClassName","example.Unrelated"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()),
                new RuntimeFact(new BridgeEntityId("cartago","environment","observable-property-snapshot","physical:noise","noise","snapshot"),RuntimeFactKind.PROPERTY,
                        Map.of("artifactSemanticId","physical:noise","name","noise","values",List.of("opaque-noise-"+value),"valueTypes",List.of("java.lang.String")),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of())));
        var verdicts=new ArrayList<List<String>>();
        for(boolean reduced:List.of(false,true)) {
            var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
            var scope=ObservationCaptureScope.fromMap(CanonicalJson.object(built.xmlPreparation().preflight().get("captureScope")));
            var actual=new ArrayList<String>();
            try(var projector=new NativeRuntimeProjector(built,"recorded",1,source.modelRevision(),temporary.resolve(reduced?"reduced":"full"))) {
                int sequence=0;
                for(var captured:recorded) {
                    var facts=reduced?captured.stream().filter(f->f.kind()!=RuntimeFactKind.PROPERTY||scope.includesProperty((String)f.values().get("name"))).toList():captured;
                    projector.applySnapshot(cut(source.modelRevision(),++sequence,facts,reduced?scope:null),"recorded",1);
                    projector.coordinator().originalEngineDrained().get(10,java.util.concurrent.TimeUnit.SECONDS);projector.coordinator().read(()->null);
                    var result=projector.coordinator().latest();
                    var rows=result.semantic().constraints().stream().filter(r->r.id().equals("NATIVE:Pallet::NonnegativeWeight")&&r.kind().equals("INVARIANT")).toList();
                    assertEquals(1,rows.size(),result.semantic().constraints().toString());
                    var bodies=result.semantic().constraints().stream().filter(r->r.id().equals("NATIVE:Pallet::NonnegativeWeight")&&r.kind().equals("INVARIANT_BODY")).toList();
                    assertEquals(1,bodies.size());assertEquals(rows.getFirst().verdict(),bodies.getFirst().verdict());
                    actual.add(rows.getFirst().verdict().name());
                }
            }
            verdicts.add(List.copyOf(actual));
        }
        assertEquals(List.of("PASS","FAIL","PASS"),verdicts.getFirst());
        assertEquals(verdicts.getFirst(),verdicts.getLast(),"Reduced evidence preserves each relevant native verdict of the same recorded execution");
    }
    @Test void wrongScopeCannotEraseAcceptedObjectsOrTurnAnEmptySelectedPopulationIntoVacuousPass() throws Exception {
        Path root=Path.of("examples/csl-counter"),inputs=root.resolve("authored-v1");
        var source=GenericMappingEngineTest.official(root.resolve("counter.jcm"));
        var selected=MappingInputs.read(root.resolve("counter.jcm"),inputs.resolve("stock-mapping.xml"),inputs.resolve("stock.ocl"),inputs.resolve("stock.csl"),inputs.resolve("stock.istar"));
        var program=new GenericMappingEngine().map(source,selected);
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        var scope=ObservationCaptureScope.fromMap(CanonicalJson.object(built.xmlPreparation().preflight().get("captureScope")));
        var id=new BridgeEntityId("cartago","environment","artifact","/main","sample","incarnation-one");
        var artifact=new RuntimeFact(id,RuntimeFactKind.ARTIFACT,Map.of("uuid",id.incarnation(),"name","sample","semanticId","physical:sample","artifactTypeJavaClassName","demo.counter.Counter"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var property=new RuntimeFact(new BridgeEntityId("cartago","environment","observable-property-snapshot","physical:sample","p","snapshot"),RuntimeFactKind.PROPERTY,
                Map.of("artifactSemanticId","physical:sample","name","p","values",List.of("3"),"valueTypes",List.of("java.lang.Integer")),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        try(var projector=new NativeRuntimeProjector(built,"run",1,source.modelRevision(),temporary.resolve("runtime"))) {
            var system=projector.system();
            projector.applySnapshot(cut(source.modelRevision(),1,List.of(artifact,property),scope),"run",1);
            var object=projector.mutations().objectForSemanticId(id.canonical());assertNotNull(object);
            assertEquals(IntegerValue.valueOf(3),object.state(system.state()).attributeValue("weight"));
            var wrong=new ObservationCaptureScope(scope.allArtifactClasses(),scope.artifactClasses(),false,Set.of("other"),scope.moiseKinds(),scope.nplObservation(),scope.readSetHash());
            var failure=assertThrows(NativeRuntimeProtocolException.class,()->projector.applySnapshot(cut(source.modelRevision(),2,List.of(),wrong),"run",1));
            assertTrue(failure.getMessage().contains("CAPTURE_SCOPE"),failure.toString());
            assertSame(system,projector.system());assertSame(object,projector.mutations().objectForSemanticId(id.canonical()));
            assertEquals(IntegerValue.valueOf(3),object.state(system.state()).attributeValue("weight"));
            assertTrue(projector.resyncRequired());
            projector.applySnapshot(cut(source.modelRevision(),2,List.of(artifact,property),scope),"run",1);
            assertFalse(projector.resyncRequired());assertSame(system,projector.system());
            assertTrue(projector.apply(delta(source.modelRevision(),3,id,scope.hash(),4)));
            assertEquals(IntegerValue.valueOf(4),object.state(system.state()).attributeValue("weight"));
            assertThrows(NativeRuntimeProtocolException.class,()->projector.apply(delta(source.modelRevision(),4,id,wrong.hash(),999)));
            assertEquals(IntegerValue.valueOf(4),object.state(system.state()).attributeValue("weight"));
            projector.applySnapshot(cut(source.modelRevision(),4,List.of(artifact,property),scope),"run",1);
            var downgrade=cut(source.modelRevision(),5,List.of(artifact,property),null);
            assertThrows(NativeRuntimeProtocolException.class,()->projector.applySnapshot(downgrade,"run",1),
                    "A selective run cannot silently resync through an unscoped legacy snapshot");
        }
    }
    private static RuntimeEvent delta(String revision,long sequence,BridgeEntityId id,String scope,int value){
        var properties=List.of(Map.of("semanticId","property:sample","artifactSemanticId","physical:sample","propertyId","p","name","p","values",List.of(Integer.toString(value)),"valueTypes",List.of("java.lang.Integer")));
        return new RuntimeEvent("delta-"+sequence,"run",1,revision,"cartago","cartago",sequence,Instant.EPOCH,RuntimeEventKind.CHANGED,
                RuntimeFactKind.ARTIFACT,ProjectionStatus.MATERIALIZED_FAITHFULLY,id,null,"","",Map.of(),
                Map.of("normalizedEventKind","APPLY_CARTAGO_PROPERTY_DELTA","semanticId","physical:sample","properties",properties,
                        "removedPropertySemanticIds",List.of(),"producerCaptureScopeHash",scope),new SourceWatermark("cartago",sequence),Completeness.COMPLETE,List.of());
    }
    private static RuntimeSnapshot cut(String revision,int sequence,List<RuntimeFact> facts,ObservationCaptureScope scope){
        var mark=Map.of("cartago",new SourceWatermark("cartago",sequence));
        Map<String,Object> metadata=scope==null?Map.of():Map.of("sourceEvidence",Map.of("cartago",Map.of("captureScope",scope.evidence(),"captureScopeHash",scope.hash())));
        return new RuntimeSnapshot("cut-"+sequence,revision,Instant.EPOCH,Instant.EPOCH,mark,mark,1,facts,Map.of("cartago",Completeness.COMPLETE),"fingerprint-"+sequence,metadata);
    }
}
