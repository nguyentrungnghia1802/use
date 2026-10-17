package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.normative.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;

class NormativeObservationTest {
    static Map<String,Object> payload(String board,String modality,String state,long created,String history) {
        var row=new HashMap<String,Object>();row.put("observationVersion","NPL_OBSERVATION/1.0");row.put("sourceVersion","NPL_0.6.1_PUBLIC_API");row.put("boardArtifactIdentity",board);row.put("normDefinition","n");row.put("norm","raw normative term");row.put("subject","alice");row.put("substitution","[]");row.put("createdEpochMillis",Long.toString(created));row.put("activationKey",SemanticJson.hash(List.of(board,"n","alice","[]",created)));row.put("activationIdentityStatus","BOUND_PUBLIC_CREATED_ANNOTATION");row.put("history",history);row.put("modality",modality);row.put("state",state);row.put("maintenance","true");row.put("aim","done");row.put("deadlineKind","STATE");row.put("deadlineRaw","finished");row.put("deadline","");row.put("clockEvidence","SOURCE_NPL_SYSTEM_TIME_REPORTED_NO_REPLAY_CLOCK");return row;
    }
    static RuntimeEvent event(String revision,long sequence,Map<String,Object> payload){var id=new BridgeEntityId("npl","organisation","norm-instance","/main:norms",(String)payload.get("activationKey"),"norm-run:"+payload.get("boardArtifactIdentity"));var kind=switch((String)payload.get("state")){case "ACTIVE"->RuntimeEventKind.NORM_ACTIVATED;case "FULFILLED"->RuntimeEventKind.NORM_FULFILLED;case "UNFULFILLED"->RuntimeEventKind.NORM_UNFULFILLED;default->RuntimeEventKind.NORM_INACTIVATED;};return new RuntimeEvent("norm-event:"+sequence,"norm-run",1,revision,"npl","npl",sequence,java.time.Instant.EPOCH.plusMillis(sequence),kind,RuntimeFactKind.NORM_INSTANCE,ProjectionStatus.EVIDENCE_ONLY,id,null,"","",Map.of(),payload,new SourceWatermark("npl",sequence),Completeness.COMPLETE,List.of());}
    @Test void reportedLifecycleStaysSeparateFromIndependentDerivationAndIsReplayedWithoutAClock()throws Exception {
        var source=FunctionalObservationTest.source();var config=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var pipeline=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,config);
        try(var projector=new NativeRuntimeProjector(pipeline,"norm-run",1,source.modelRevision())) {
            var now=java.time.Instant.EPOCH;projector.applySnapshot(new RuntimeSnapshot("norm-base",source.modelRevision(),now,now,Map.of("npl",new SourceWatermark("npl",0)),Map.of("npl",new SourceWatermark("npl",0)),1,List.of(),Map.of("npl",Completeness.PARTIAL),"norm-base"));String board=UUID.randomUUID().toString();var active=payload(board,"OBLIGATION","ACTIVE",1,"CREATED_EVENT_OBSERVED");var first=event(source.modelRevision(),1,active);assertTrue(projector.apply(first));var object=projector.mutations().objectForSemanticId(first.entityId().canonical());assertNotNull(object);assertEquals("EVIDENCE_ONLY",projector.mutations().metadata(object,"sourceProjectionStatus"));assertFalse(object.cls().isSubClassifierOf(projector.system().model().getClass("DomainEntity")));
            var terminal=payload(board,"OBLIGATION","UNFULFILLED",1,"CREATED_EVENT_OBSERVED");projector.apply(event(source.modelRevision(),2,terminal));assertSame(object,projector.mutations().objectForSemanticId(first.entityId().canonical()));var semantic=projector.coordinator().latest().semantic();assertTrue(semantic.constraints().stream().anyMatch(r->r.kind().equals("NORM_REPORTED_STATE") && r.verdict()==SemanticVerdict.FAIL));assertTrue(semantic.constraints().stream().anyMatch(r->r.kind().equals("NORM_DERIVATION") && r.verdict()==SemanticVerdict.INCONCLUSIVE));
            var permission=payload(board,"PERMISSION","ACTIVE",2,"CREATED_EVENT_OBSERVED");projector.apply(event(source.modelRevision(),3,permission));assertTrue(projector.coordinator().latest().semantic().constraints().stream().filter(r->r.kind().equals("NORM_REPORTED_STATE") && r.sourceIdentity().equals(event(source.modelRevision(),3,permission).entityId().canonical())).noneMatch(r->r.verdict()==SemanticVerdict.FAIL));
            var path=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"),"norm-replay-");new NativeRuntimeReplay().exportBundle(projector,pipeline.export().useText(),path);var replay=new NativeRuntimeReplay().replay(path);assertTrue(replay.complete(),replay.diagnostics().toString());CslVerticalSliceTest.assertReplayEndpoint(path,replay);
        }
    }
    @Test void resyncRestoresReportedStateButCannotCertifyLifecycleAcrossAMissingInterval()throws Exception {
        var source=FunctionalObservationTest.source();var config=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var pipeline=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,config);
        try(var projector=new NativeRuntimeProjector(pipeline,"norm-run",1,source.modelRevision())) {
            var now=java.time.Instant.EPOCH;var w=Map.of("npl",new SourceWatermark("npl",0));projector.applySnapshot(new RuntimeSnapshot("base",source.modelRevision(),now,now,w,w,1,List.of(),Map.of("npl",Completeness.PARTIAL),"base"));
            String board=UUID.randomUUID().toString();var active=payload(board,"OBLIGATION","ACTIVE",1,"CREATED_EVENT_OBSERVED");var first=event(source.modelRevision(),1,active);projector.apply(first);projector.coordinator().coverageGap("fixture missing interval");
            var terminal=payload(board,"OBLIGATION","FULFILLED",1,"CREATED_EVENT_OBSERVED");var fact=new RuntimeFact(first.entityId(),RuntimeFactKind.NORM_INSTANCE,terminal,List.of(),ProjectionStatus.EVIDENCE_ONLY,Completeness.COMPLETE,List.of());var end=Map.of("npl",new SourceWatermark("npl",2));projector.applySnapshot(new RuntimeSnapshot("recovered",source.modelRevision(),now,now,end,end,1,List.of(fact),Map.of("npl",Completeness.PARTIAL),"recovered"));
            var result=projector.coordinator().latest();assertEquals("CURRENT_OBSERVED",result.freshness());assertTrue(result.semantic().constraints().stream().anyMatch(r->r.kind().equals("NORM_REPORTED_STATE") && r.verdict()==SemanticVerdict.PASS));assertTrue(result.semantic().constraints().stream().anyMatch(r->r.kind().equals("NORM_LIFECYCLE_TRACE_CONSISTENCY") && r.verdict()==SemanticVerdict.INCONCLUSIVE && r.diagnostic().contains("HISTORY_GAP")));
            var path=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"),"norm-gap-replay-");new NativeRuntimeReplay().exportBundle(projector,pipeline.export().useText(),path);var replay=new NativeRuntimeReplay().replay(path);assertFalse(replay.complete(),"A recorded coverage gap cannot become a complete trace proof");assertTrue(replay.diagnostics().contains("REPLAY_PARTIAL_COVERAGE_GAP"));assertEquals(result.resultHash(),replay.finalResultHash());
        }
    }
    @Test void incompatibleTerminalTransitionsAmbiguousKeysAndMissingCreationAreExplicit() {
        String board=UUID.randomUUID().toString();var row=payload(board,"OBLIGATION","ACTIVE",1,"CREATED_EVENT_OBSERVED");var event=event("model",1,row);var active=NormativeObservation.decode(event.entityId(),row);var monitor=new NormLifecycleMonitor();assertEquals(SemanticVerdict.PASS,monitor.observe(null,active,false).verdict());assertEquals(SemanticVerdict.INCONCLUSIVE,monitor.observe(null,active,true).verdict());assertEquals(SemanticVerdict.ERROR,monitor.observe(NormativeObservation.State.FULFILLED,active,false).verdict());row.put("activationIdentityStatus","AMBIGUOUS_NORM_INSTANCE");assertThrows(IllegalArgumentException.class,()->NormativeObservation.decode(event.entityId(),row));row.put("activationIdentityStatus","BOUND_PUBLIC_CREATED_ANNOTATION");row.put("createdEpochMillis","2");assertThrows(IllegalArgumentException.class,()->NormativeObservation.decode(event.entityId(),row));
    }
}
