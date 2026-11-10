package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceContextPopulationTest {
    @TempDir java.nio.file.Path temp;
    static final String PLAN="""
        <runtime><datasets><facts id="workspaces" authority="cartago" kind="WORKSPACE"/></datasets><tables>
        <table id="contexts" dataset="workspaces"><column name="workspace" path="identity" required="true"/><identity mode="PHYSICAL" path="identity"/></table>
        </tables><inputs><input id="context-input" tableRef="contexts" identityKind="PHYSICAL" identityFields="workspace"><field name="workspace" type="String" required="true"/></input></inputs>
        <converters/><rule id="workspace-context" inputRef="context-input"><requireEvidence proof="source-cut"/><upsertInstance targetType="Team" keyFields="workspace"/></rule></runtime>
        """;
    static RuntimeFact workspace(String id){return new RuntimeFact(new BridgeEntityId("cartago","environment","workspace","/main",id,"uuid-"+id),RuntimeFactKind.WORKSPACE,
            Map.of("name",id,"uuid","uuid-"+id,"fullName","/main/"+id,"semanticId","workspace:"+id),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());}
    static RuntimeSnapshot cut(List<RuntimeFact> facts,int sequence,Completeness quality,boolean receipt,boolean stable,boolean workspaceKind) {
        var base=RuntimeFactSourcesTest.cut(facts,Map.of("cartago",quality,"moise:organization",Completeness.PARTIAL));
        var start=new TreeMap<>(base.startWatermarks());start.put("cartago",new SourceWatermark("cartago",sequence));
        var end=new TreeMap<>(start);if(!stable)end.put("cartago",new SourceWatermark("cartago",sequence+1));
        var metadata=Map.<String,Object>of("cutPolicy","LOCAL_SOURCE_STABLE_INTERVAL","topologyStart","a".repeat(64),"topologyEnd","a".repeat(64),
                "capturedFactKinds",workspaceKind?List.of("WORKSPACE"):List.of("ARTIFACT"));
        return new RuntimeSnapshot("workspace-cut-"+sequence,base.modelRevision(),base.captureStartedAt(),base.captureEndedAt(),start,end,1,facts,base.sourceCompleteness(),"state-"+sequence,
                receipt?Map.of("sourceEvidence",Map.of("cartago",metadata)):Map.of());
    }
    static RuntimeSnapshot changedTopology(RuntimeSnapshot cut) {
        var metadata=new TreeMap<>(CanonicalJson.object(CanonicalJson.object(cut.captureMetadata().get("sourceEvidence")).get("cartago")));
        metadata.put("topologyEnd","b".repeat(64));
        return new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),cut.validationAttempts(),cut.facts(),cut.sourceCompleteness(),cut.stateFingerprint(),Map.of("sourceEvidence",Map.of("cartago",metadata)));
    }
    static RuntimeSnapshot coordinated(RuntimeSnapshot cut,boolean validHash,boolean stableTopology) {
        var scope=new ObservationCaptureScope(false,Set.of(),false,Set.of(),"c".repeat(64));
        var metadata=Map.<String,Object>of("cutPolicy","PER_SOURCE_WATERMARK_AND_TOPOLOGY_VALIDATED",
                "topologyStart",Map.of("cartago","a".repeat(64)),"topologyEnd",Map.of("cartago",(stableTopology?"a":"b").repeat(64)),
                "sourceEvidence",Map.of("cartago",Map.of("captureScope",scope.evidence(),"captureScopeHash",validHash?scope.hash():"d".repeat(64),
                        "inventoryAcquisition","OFFICIAL_CONTROLLER_ENUMERATION_THEN_SELECTED_ARTIFACT_INFO_AND_VALUES")));
        return new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),cut.validationAttempts(),cut.facts(),cut.sourceCompleteness(),cut.stateFingerprint(),metadata);
    }
    @Test void coordinatedSnapshotUsesItsOwnTopologyAndValidatedCaptureProfile()throws Exception {
        var helper=new M0RuleExecutorTest();helper.temp=temp;
        var f=helper.fixture(PLAN,true,text->text.replace("<runtime/>",PLAN));var engine=f.engine();
        var batch=new XmlRuntimeBatch(engine.canonicalProgram().xmlPlan().inputs().specification(),engine.system().model(),engine.canonicalProgramHash());
        var type=engine.system().model().getClass("OrgContext");
        batch.apply(f.port(),"run",0,coordinated(cut(List.of(workspace("neutral")),1,Completeness.COMPLETE,false,true,true),true,true));
        assertTrue(MappedPopulationInventory.complete(type,engine));
        batch.apply(f.port(),"run",0,coordinated(cut(List.of(workspace("neutral")),2,Completeness.COMPLETE,false,true,true),false,true));
        assertFalse(MappedPopulationInventory.complete(type,engine),"A mismatching capture scope hash cannot certify population");
        batch.apply(f.port(),"run",0,coordinated(cut(List.of(workspace("neutral")),3,Completeness.COMPLETE,false,true,true),true,false));
        assertFalse(MappedPopulationInventory.complete(type,engine),"A changed source topology cannot certify population");
    }
    @Test void independentWorkspaceInventoryProvesNativeCompatibilityAndKeepsLossStaleAndRollbackGuards()throws Exception {
        var helper=new M0RuleExecutorTest();helper.temp=temp;
        var f=helper.fixture(PLAN,true,text->text.replace("<runtime/>",PLAN));var engine=f.engine();
        var batch=new XmlRuntimeBatch(engine.canonicalProgram().xmlPlan().inputs().specification(),engine.system().model(),engine.canonicalProgramHash());
        var one=workspace("first");var two=workspace("second");
        batch.apply(f.port(),"run",0,cut(List.of(one,two),1,Completeness.COMPLETE,true,true,true));
        var type=engine.system().model().getClass("OrgContext");
        assertTrue(MappedPopulationInventory.complete(type,engine),"Workspace proof must not require an unrelated board mapping policy");
        var invariant=engine.system().model().getClassInvariant("OrgContext::CslV4RoleCompatibility");
        var prepared=new SemanticInvariantEvaluator().prepareScoped("compatibility",invariant,engine,Map.of("cartago",Completeness.COMPLETE),false,Set.of(),"");
        assertEquals(SemanticVerdict.PASS,SemanticInvariantEvaluator.evaluateScoped(prepared,engine.system().state()).getLast().verdict());
        var savepoint=engine.savepoint();
        var context=engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("Team")).findFirst().orElseThrow();
        f.port().delete(f.port().metadata(context,"semanticId"));assertFalse(MappedPopulationInventory.complete(type,engine));
        engine.rollback(savepoint);assertTrue(MappedPopulationInventory.complete(type,engine));
        for(var next:List.of(cut(List.of(one,two),2,Completeness.COMPLETE,false,true,true),
                cut(List.of(one,two),3,Completeness.PARTIAL,true,true,true),
                cut(List.of(one,two),4,Completeness.COMPLETE,true,false,true),
                cut(List.of(one,two),5,Completeness.COMPLETE,true,true,false),
                changedTopology(cut(List.of(one,two),6,Completeness.COMPLETE,true,true,true)))) {
            batch.apply(f.port(),"run",0,next);assertFalse(MappedPopulationInventory.complete(type,engine),"Absent, partial, unstable or excluded inventory cannot borrow the previous proof");
        }
        batch.apply(f.port(),"run",0,cut(List.of(one,two),7,Completeness.COMPLETE,true,true,true));assertTrue(MappedPopulationInventory.complete(type,engine));
        batch.apply(f.port(),"run",0,cut(List.of(one),8,Completeness.COMPLETE,true,true,true));assertFalse(MappedPopulationInventory.complete(type,engine),"Retained unmatched context must not silently disappear from coverage");
    }
}
