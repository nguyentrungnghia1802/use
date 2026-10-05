package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointType;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.RuntimeConstraintPolicy;
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;
import org.jacamo.bridge.contract.*;
import java.util.*;
import java.time.Instant;

class NativeRuntimeReplayTest {
    @TempDir Path directory;
    @Test void exportedUseSoilOclAndJournalReplaySameVersionsStatesAndResults() throws Exception {
        var pipeline = CodeGroundedTestFixtures.helloPipeline();
        var projector = new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector(pipeline, SESSION, 1, REVISION);
        projector.applySnapshot(snapshot("initial", 0));
        var coordinator = projector.coordinator();
        coordinator.loadProfileSource("constraints.ocl", RuntimeVerificationCoordinatorTest.PROFILE);
        projector.apply(delta("B", 1, "B")); projector.apply(delta("A", 2, "A")); coordinator.manualVerify();
        projector.applySnapshot(snapshot("resync", 2), SESSION, 2);
        var replay = new NativeRuntimeReplay(); replay.exportBundle(projector, pipeline.export().useText(), directory);
        var report = replay.replay(directory);
        assertTrue(report.complete(), report.diagnostics().toString());
        assertEquals(coordinator.journal().persistedEntries(), report.checkedEntries());
        assertEquals(coordinator.latest().stateHash(), report.finalStateHash());
        assertEquals(coordinator.latest().resultHash(), report.finalResultHash());
        assertEquals(5, Files.list(directory).count());
    }
    @Test void corruptionMissingFileAndGapFailClosed() throws Exception {
        var pipeline = CodeGroundedTestFixtures.helloPipeline();
        var projector = new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector(pipeline, SESSION, 1, REVISION);
        projector.applySnapshot(snapshot("initial", 0)); projector.coordinator().coverageGap("SOURCE_OVERFLOW");
        var replay = new NativeRuntimeReplay(); replay.exportBundle(projector, pipeline.export().useText(), directory);
        // A marked incomplete observed timeline is replayable but must retain SKIPPED, never manufacture PASS.
        assertFalse(replay.replay(directory).complete());
        assertEquals("REPLAY_PARTIAL_COVERAGE_GAP", replay.replay(directory).diagnostics().getFirst());
        Files.writeString(directory.resolve("runtime.jsonl"), "{}\n", java.nio.file.StandardOpenOption.APPEND);
        var corrupt = replay.replay(directory);
        assertFalse(corrupt.complete()); assertTrue(corrupt.diagnostics().getFirst().contains("HASH_MISMATCH"));
        Files.delete(directory.resolve("constraints.ocl"));
        assertFalse(replay.replay(directory).complete());
    }
    @Test void capabilityApprovalAndExactBoundaryTimelineReplayWithoutControlCalls() throws Exception {
        try(var p=projector()) {
            var c=p.coordinator();String owner=p.mutations().objectForSemanticId(ARTIFACT).cls().name();
            c.loadProfileSource("case.ocl","context "+owner+" inv State: self.status = 'A'");
            c.runtimeCapabilities(Map.of("fixture.domain","COMPLETE"),Map.of("cartago","fixture-version"));
            var policy=new RuntimeConstraintPolicy(ConstraintOrigin.CASE,RuntimeConstraintPolicy.Severity.HARD,
                    RuntimeConstraintPolicy.Enforcement.PAUSE_ON_FAIL,Set.of(CheckpointType.SNAPSHOT,CheckpointType.STREAM_BOUNDARY),
                    Set.of("fixture.domain"),true,"Explicit fixture approval",Map.of(ARTIFACT,"Exact UUID of the artifact used by this fixture condition"));
            String id="EXTERNAL:"+owner+"::State";
            c.configurePolicy(id,policy);
            var before=c.verificationSnapshot();
            assertThrows(IllegalArgumentException.class,()->c.configurePolicy(id,new RuntimeConstraintPolicy(ConstraintOrigin.CASE,RuntimeConstraintPolicy.Severity.HARD,
                    RuntimeConstraintPolicy.Enforcement.PAUSE_ON_FAIL,Set.of(CheckpointType.SNAPSHOT),Set.of(),true,"Unknown target",Map.of("bare-name","No authoritative binding"))));
            assertEquals(before,c.verificationSnapshot());
            assertThrows(IllegalArgumentException.class,()->c.configurePolicy(id,policy,"foreign-condition"));
            assertEquals(before,c.verificationSnapshot(),"Changed compiled condition cannot gain recorded approval");
            p.apply(delta("parent",1,"B"));
            var entity=new BridgeEntityId("bridge","runtime","verification-boundary",SESSION,"batch","parent");
            p.apply(new RuntimeEvent("boundary",SESSION,1,REVISION,"bridge","bridge:platform",1,Instant.EPOCH,
                    RuntimeEventKind.STREAM_BOUNDARY,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,entity,null,
                    "operation-1","parent",Map.of(),Map.of("boundarySource","cartago","boundaryEventId","parent","boundarySourceSequence",1),
                    new SourceWatermark("bridge:platform",1),Completeness.COMPLETE,List.of()));
            var cut=c.verificationSnapshot();assertEquals(CheckpointType.STREAM_BOUNDARY,cut.metadata().checkpoint());
            var replay=new NativeRuntimeReplay();replay.exportBundle(p,"",directory);
            var report=replay.replay(directory);
            assertTrue(report.complete(),report.diagnostics().toString());
            assertEquals(c.latest().stateHash(),report.finalStateHash());
            assertEquals(c.latest().resultHash(),report.finalResultHash());
            try(var bundle=replay.openBundle(directory);var cursor=bundle.reconstruct()) {
                cursor.forward(bundle.steps().getLast().endOrdinal());
                var recorded=cursor.projector().coordinator();
                assertEquals(policy,recorded.read(()->recorded.constraints().runtimeRegistry().stream().filter(row->row.id().equals(id)).findFirst().orElseThrow().policy()));
                assertEquals(cut.metadata().capabilities(),recorded.verificationSnapshot().metadata().capabilities());
            }
        }
    }
    @Test void actualNativeOperationPrePostAndAtPreReplayOnTheProductionProjector() throws Exception {
        // The operation contract must exist before the recording baseline. Adding
        // unrecorded contracts midway through a timeline is intentionally rejected.
        var fixture=new NativeOperationCheckpointTest();
        org.tzi.use.uml.sys.MSystem system;
        var index=new LinkedHashMap<String,org.tzi.use.uml.sys.MObject>();
        try(var seed=projector()) {
            fixture.contracts(seed);system=seed.system();
            for(var object:system.state().allObjects()) {
                var identity=object.state(system.state()).attributeValue("semanticId");
                if(identity instanceof org.tzi.use.uml.ocl.value.StringValue value)index.put(value.value(),object);
            }
        }
        try(var p=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector(system,index,SESSION,1,REVISION,
                new org.tzi.use.plugins.jacamo.codegrounded.runtime.CodeGroundedRuntimeRuleRegistry())) {
            p.applySnapshot(snapshot("initial-with-operation-contract",0));
            p.apply(fixture.op("enter",1,RuntimeEventKind.STARTED,"B","op-one"));
            p.apply(delta("mutation",2,"B"));
            p.apply(fixture.op("exit",3,RuntimeEventKind.SUCCEEDED,"B","op-one"));
            var replay=new NativeRuntimeReplay();replay.exportBundle(p,"",directory);
            var report=replay.replay(directory);
            assertTrue(report.complete(),report.diagnostics().toString());
            assertEquals(p.coordinator().latest().resultHash(),report.finalResultHash());
        }
    }
    @Test void unrecordedOperationContractsAddedAfterBaselineFailClosedAtExport() throws Exception {
        try(var p=projector()) {
            new NativeOperationCheckpointTest().contracts(p);
            var error=assertThrows(IllegalStateException.class,()->new NativeRuntimeReplay().exportBundle(p,"",directory));
            assertTrue(error.getMessage().startsWith("REPLAY_UNRECORDED_OPERATION_CONTRACT:"));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void runtimeOperationDiscoveryDoesNotBecomeAvailableBeforeItsRecordedDescriptor(boolean snapshotDiscovery) throws Exception {
        var fixture=new NativeOperationCheckpointTest();
        try(var p=projector()) {
            p.apply(fixture.op("early-pre",1,RuntimeEventKind.STARTED,"B","early-operation"));
            assertTrue(p.coordinator().latest().outcomes().stream().anyMatch(o->o.diagnostic().equals("PRE_EXACT_OPERATION_UNAVAILABLE")));
            p.apply(fixture.op("early-post",2,RuntimeEventKind.SUCCEEDED,"B","early-operation"));
            assertTrue(p.coordinator().latest().outcomes().stream().anyMatch(o->o.diagnostic().equals("POST_MATCHING_PRE_UNAVAILABLE")));
            String descriptorId="cartago:operation:"+ARTIFACT+":set/1";
            var metadata=new org.jacamo.bridge.contract.semantic.SemanticMetadata(descriptorId,"CARTAGO_OPERATION_DESCRIPTOR",
                    "official Method fixture",org.jacamo.bridge.contract.semantic.EvidenceAuthority.OFFICIAL_CARTAGO_API,
                    org.jacamo.bridge.contract.semantic.Fidelity.EXACT,CapabilityStatus.COMPLETE,List.of(),List.of());
            var descriptor=new org.jacamo.bridge.contract.semantic.CartagoSemanticContract.OperationDescriptorSemantic(
                    metadata,ARTIFACT,"set/1","set",1,false,false,false,false);
            var backing=new org.jacamo.bridge.contract.semantic.CartagoSemanticContract.BackingJavaOperationSemantic(
                    new org.jacamo.bridge.contract.semantic.SemanticMetadata("backing:set","CARTAGO_BACKING_JAVA_OPERATION",
                        "official Method fixture",org.jacamo.bridge.contract.semantic.EvidenceAuthority.OFFICIAL_CARTAGO_API,
                        org.jacamo.bridge.contract.semantic.Fidelity.EXACT,CapabilityStatus.COMPLETE,List.of(),List.of()),
                    descriptorId,LiveRuntimePropertyArtifact.class.getName(),"set",List.of("java.lang.String"),"void",false,"actual fixture Method");
            var descriptorIdentity=new BridgeEntityId("cartago","environment","operation-descriptor",ARTIFACT,"set/1","snapshot");
            var descriptorPayload=Map.<String,Object>of("normalizedEventKind","UPSERT_CARTAGO_OPERATION","semanticId",descriptorId,
                    "operationDescriptor",org.jacamo.bridge.contract.semantic.SemanticContractCodec.operationToTree(descriptor),
                    "backingOperation",org.jacamo.bridge.contract.semantic.SemanticContractCodec.backingOperationToTree(backing));
            if(snapshotDiscovery) {
                var cut=snapshot("late-operation-discovery",3);
                var facts=new ArrayList<>(cut.facts());
                facts.add(new RuntimeFact(descriptorIdentity,RuntimeFactKind.OPERATION,descriptorPayload,List.of(),
                        ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
                p.applySnapshot(new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),
                        cut.startWatermarks(),cut.endWatermarks(),cut.validationAttempts(),facts,cut.sourceCompleteness(),cut.stateFingerprint()));
            } else p.apply(event("descriptor",3,RuntimeEventKind.CHANGED,RuntimeFactKind.OPERATION,descriptorIdentity,descriptorPayload));
            p.apply(fixture.op("admitted-pre",4,RuntimeEventKind.STARTED,"B","admitted-operation"));
            assertFalse(p.coordinator().latest().outcomes().stream().anyMatch(o->o.constraintId().startsWith("OPERATION:")));
            p.apply(fixture.op("admitted-post",5,RuntimeEventKind.SUCCEEDED,"B","admitted-operation"));
            assertFalse(p.coordinator().latest().outcomes().stream().anyMatch(o->o.diagnostic().equals("POST_MATCHING_PRE_UNAVAILABLE")));
            var replay=new NativeRuntimeReplay();replay.exportBundle(p,"",directory);
            var report=replay.replay(directory);
            assertTrue(report.complete(),report.diagnostics().toString());
            assertEquals(p.coordinator().latest().stateHash(),report.finalStateHash());
            assertEquals(p.coordinator().latest().resultHash(),report.finalResultHash());
        }
    }
}
