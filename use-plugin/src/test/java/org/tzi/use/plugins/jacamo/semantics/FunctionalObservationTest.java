package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.profile.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.observer.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.api.UseSystemApi;

class FunctionalObservationTest {
    static ModelSnapshot source()throws Exception {var path=Path.of("src/test/resources/semantics/functional/functional.jcm").toAbsolutePath();return new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(path),path);}
    static BridgeEntityId board(String name){return new BridgeEntityId("moise","organisation","scheme-board","org",name,"session:"+UUID.randomUUID());}
    static Map<String,Object> payload(ModelSnapshot source,BridgeEntityId board,String argument,String state) {
        var os=source.semanticContract().moiseOrganizations().getFirst();var spec=os.functionalSpecification().schemes().getFirst();
        var values=new LinkedHashMap<String,Object>();values.put("normalizedEventKind","UPSERT_MOISE_SCHEME");values.put("runtimeIdentity",board.canonical());values.put("semanticId",board.canonical());
        values.put("name",board.localId());values.put("organisationName","org");values.put("organisationSemanticId","org-instance");values.put("organisationDefinition",SemanticContractCodec.organizationToTree(os));values.put("organisationSpecSemanticId",os.metadata().semanticId());values.put("schemeSpecSemanticId",spec.metadata().semanticId());
        values.put("boardArtifactIdentity",board.incarnation().substring("session:".length()));values.put("goalObservationVersion","1.0.0");values.put("responsibleGroupSemanticIds",List.of());values.put("commitments",List.of());
        values.put("schemeArguments",Map.of("g1",Map.of("item",argument)));
        values.put("goalStates",spec.goals().stream().map(g->Map.of("goalSemanticId",g.metadata().semanticId(),"state",state,"committedAgentSemanticIds",List.of(),"achievedAgentSemanticIds",List.of(),"arguments",g.goalId().equals("g1")?Map.of("item",argument):Map.of())).toList());return values;
    }
    @Test void sameSpecTwoBoardsKeepArgumentsGoalsAndAllocationSeparateAndRequireFreshCut()throws Exception {
        var source=source();var os=source.semanticContract().moiseOrganizations().getFirst();var defs=new FunctionalMappingRules().derive(os);assertEquals(1,defs.size());
        assertTrue(defs.getFirst().goals().stream().anyMatch(g->g.source().goalType().equalsIgnoreCase("performance") && g.threshold().all()));
        var cfg=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));
        var pipeline=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,cfg);var system=pipeline.state().system();assertTrue(system.state().allObjects().isEmpty());
        var engine=new NativeRuntimeMutationEngine(system,pipeline.state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry());
        var first=board("s1");var second=board("s2");var firstData=payload(source,first,"alpha","SATISFIED");var secondData=payload(source,second,"beta","WAITING");
        assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,first,firstData).status());assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,second,secondData).status());
        var api=UseSystemApi.create(system,false);assertEquals("2",api.evaluate("FunctionalSchemeObservation.allInstances()->size()").toString());assertEquals("6",api.evaluate("FunctionalGoalObservation.allInstances()->size()").toString());
        assertNull(system.model().getClass("Mission"));assertNull(system.model().getClass("Scheme"));
        var cut=FunctionalObservation.decode(first,firstData);var goal=cut.goals().stream().filter(g->!g.arguments().isEmpty()).findFirst().orElseThrow();
        var id=FunctionalObservation.goalIdentity(first.canonical(),goal.definition(),goal.arguments());var object=engine.objectForSemanticId(id);assertNotNull(object);
        assertEquals("'SATISFIED'",object.state(system.state()).attributeValue("state").toString());
        var expr=SemanticOclCompiler.predicate(system.model(),object.cls(),"boardCondition","self.state = 'SATISFIED'");
        var coverage=new ConstraintCoverageResolver();assertEquals("EXACT_FUNCTIONAL_BOARD_CUT_UNAVAILABLE",coverage.unavailableSelf(expr,engine,object,Map.of("moise:org",Completeness.PARTIAL),false));
        assertEquals("",coverage.unavailableSelf(expr,engine,object,Map.of("moise:org",Completeness.PARTIAL),false,Set.of(first.canonical())));
        var global=SemanticOclCompiler.predicate(system.model(),object.cls(),"global","FunctionalGoalObservation.allInstances()->notEmpty()");
        assertEquals("FUNCTIONAL_GLOBAL_POPULATION_UNPROVEN",coverage.unavailableSelf(global,engine,object,Map.of("moise:org",Completeness.PARTIAL),false,Set.of(first.canonical())));
        var actor=new RequirementModel.Binding("actor","player","role","group","unused","unused","episode");
        var req=new MoiseRequirementBinder().bind("ack","acknowledged",RequirementModel.Temporal.NONE,actor,cut,goal.definition());assertEquals(RequirementModel.Temporal.NONE,req.temporal());assertEquals("observer:FunctionalGoalObservation",req.binding().selfTypeId());
        assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,first,payload(source,first,"gamma","ENABLED")).status());assertNull(engine.objectForSemanticId(id));
        assertEquals("3",api.evaluate("FunctionalGoalObservation.allInstances()->select(g | g.state = 'WAITING')->size()").toString());assertSame(system,engine.system());
        var exported=new NativeUseExporter().export(system.model());assertEquals(exported.originalStructuralHash(),exported.recompiledStructuralHash());
    }
    @Test void commitmentLeaveRejoinProducesNewLifetimeAndAllocationViolationIsRetained()throws Exception {
        var source=source();var cfg=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var p=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,cfg);
        var engine=new NativeRuntimeMutationEngine(p.state().system(),p.state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry());var agent=new BridgeEntityId("jason","agent","runtime-agent",source.semanticContract().project().name(),"a","agent-lifetime");
        assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,engine.apply(agent,null,RuntimeFactKind.AGENT,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,Map.of("normalizedEventKind","UPSERT_JASON_AGENT_STATE","semanticId",agent.canonical(),"agentDeclarationSemanticId",source.semanticContract().agentDeclarations().getFirst().metadata().semanticId(),"name","a")).status());
        var board=board("allocation");var data=payload(source,board,"a","ENABLED");var mission=source.semanticContract().moiseOrganizations().getFirst().functionalSpecification().schemes().getFirst().missions().getFirst().metadata().semanticId();
        var member=Map.of("agentSemanticId",agent.canonical(),"missionSemanticId",mission);data.put("commitments",List.of(member));assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,board,data).status());
        var old=p.state().system().state().objectsOfClass(p.state().system().model().getClass(FunctionalUseBinding.COMMITMENT)).iterator().next();String oldId=engine.metadata(old,"semanticId");
        data.put("commitments",List.of());assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,board,data).status());assertNull(engine.objectForSemanticId(oldId));
        var verifier=new org.tzi.use.plugins.jacamo.semantics.runtime.SemanticRuntimeVerifier(engine);verifier.observed(List.of(new RuntimeFact(board,RuntimeFactKind.SCHEME_BOARD,data,List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of())),true);
        var checked=verifier.evaluate("allocation",1,Map.of("moise:org",Completeness.PARTIAL),false,List.of());assertTrue(checked.constraints().stream().anyMatch(r->r.id().contains("FunctionalMissionAllocation") && r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL));
        data.put("commitments",List.of(member));assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,board,data).status());var fresh=p.state().system().state().objectsOfClass(p.state().system().model().getClass(FunctionalUseBinding.COMMITMENT)).iterator().next();assertNotSame(old,fresh);assertNotEquals(oldId,engine.metadata(fresh,"semanticId"));
    }
    @Test void malformedIncompleteOrWrongUuidCutIsRejectedAtomically()throws Exception {
        var source=source();var cfg=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var p=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,cfg);
        var engine=new NativeRuntimeMutationEngine(p.state().system(),p.state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry());var board=board("s");var data=payload(source,board,"a","ENABLED");assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,board,data).status());var before=engine.savepoint();
        for(String key:List.of("boardArtifactIdentity","goalObservationVersion","goalStates")) {var invalid=new HashMap<>(data);invalid.put(key,key.equals("goalStates")?List.of():"invalid");assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,apply(engine,board,invalid).status());assertEquals(before,engine.savepoint());}
        var unknown=new HashMap<>(data);unknown.put("goalStates",List.of(Map.of("goalSemanticId","foreign","state","SATISFIED")));assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,apply(engine,board,unknown).status());assertEquals(before,engine.savepoint());
    }
    private static NativeRuntimeMutationEngine.ApplyResult apply(NativeRuntimeMutationEngine engine,BridgeEntityId id,Map<String,Object> data){return engine.apply(id,null,RuntimeFactKind.SCHEME_BOARD,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,data);}
}
