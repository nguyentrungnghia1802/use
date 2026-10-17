package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import jason.asSemantics.Agent;
import jason.asSyntax.ASSyntax;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.plugins.jacamo.semantics.observer.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.api.UseSystemApi;

class MentalStateProjectionTest {
    @Test void changingStoredKnowledgeKeepsWorldStateAndOclQueriesSeparateWithClosedScopeAdmission()throws Exception {
        var source=FunctionalObservationTest.source();var cfg=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var pipeline=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,cfg);
        // Official Jason AST observations are injected at the adapter contract boundary.
        var engine=new NativeRuntimeMutationEngine(pipeline.state().system(),pipeline.state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry());var owner=new BridgeEntityId("jason","agent","runtime-agent",source.semanticContract().project().name(),"a","actual-agent-lifetime");var agent=new Agent();agent.initAg();
        for(String literal:List.of("price(10)[source(percept)]","~price(9)[source(self)]","shop::price(12)"))agent.getBB().add(ASSyntax.parseLiteral(literal));var query=new JasonGroundQueryCompiler();var service=new MentalPredicateBinding();
        apply(engine,source,owner,agent,1);assertEquals(SemanticVerdict.PASS,service.evaluate(engine,owner.canonical(),query.compile("price(10)"),MentalPredicateBinding.Mode.GROUND_RULE_FREE_ENTAILMENT,Set.of(owner.canonical()),false).verdict());
        assertEquals(SemanticVerdict.FAIL,service.evaluate(engine,owner.canonical(),query.compile("price(12)"),MentalPredicateBinding.Mode.STORED_MEMBERSHIP,Set.of(owner.canonical()),false).verdict());assertEquals(SemanticVerdict.PASS,service.evaluate(engine,owner.canonical(),query.compile("shop::price(12) & not price(12)"),MentalPredicateBinding.Mode.STORED_MEMBERSHIP,Set.of(owner.canonical()),false).verdict());
        assertEquals(SemanticVerdict.UNSUPPORTED,service.evaluate(engine,owner.canonical(),query.compile("price(X)"),MentalPredicateBinding.Mode.STORED_MEMBERSHIP,Set.of(owner.canonical()),false).verdict());assertEquals(SemanticVerdict.SKIPPED,service.evaluate(engine,owner.canonical(),query.compile("price(10)"),MentalPredicateBinding.Mode.STORED_MEMBERSHIP,Set.of(),false).verdict());
        agent.getBB().add(ASSyntax.parseRule("price(X) :- external(X)."));apply(engine,source,owner,agent,2);assertEquals(SemanticVerdict.UNSUPPORTED,service.evaluate(engine,owner.canonical(),query.compile("price(10)"),MentalPredicateBinding.Mode.GROUND_RULE_FREE_ENTAILMENT,Set.of(owner.canonical()),false).verdict());assertEquals(SemanticVerdict.PASS,service.evaluate(engine,owner.canonical(),query.compile("price(10)"),MentalPredicateBinding.Mode.STORED_MEMBERSHIP,Set.of(owner.canonical()),false).verdict());
        agent.getBB().remove(ASSyntax.parseLiteral("price(10)[source(percept)]"));apply(engine,source,owner,agent,3);assertEquals(SemanticVerdict.FAIL,service.evaluate(engine,owner.canonical(),query.compile("price(10)"),MentalPredicateBinding.Mode.STORED_MEMBERSHIP,Set.of(owner.canonical()),false).verdict());
        assertEquals("1",UseSystemApi.create(engine.system(),false).evaluate("StoredBeliefObservation.allInstances()->select(b | b.rule)->size()").toString());assertNull(engine.system().model().getClass("Belief"));assertSame(pipeline.state().system(),engine.system());
        var ownerObject=engine.objectForSemanticId(owner.canonical());var mental=engine.objectForSemanticId(MentalObservationMapper.identity(owner.canonical()));assertTrue(engine.system().state().hasLinkBetweenObjects(engine.system().model().getAssociation("AgentMentalState"),new org.tzi.use.uml.sys.MObject[]{ownerObject,mental}));
    }
    @Test void liveArtifactValueAndLaggingMentalPerceptRemainIndependentlyQueryableAfterBeliefRemoval()throws Exception {
        var source=SemanticTransformationTest.source();var env=cartago.CartagoEnvironment.getInstance();env.init();String name="epistemic"+UUID.randomUUID().toString().replace("-","");var context=new cartago.util.agent.CartagoBasicContext(name);var artifact=context.makeArtifact(context.getJoinedWspId("main"),name,CslCounterArtifact.class.getName());context.doAction(artifact,new cartago.Op("set",12));
        try(var registration=CslRuntimeTestRegistry.bind(source.semanticContract(),name,"a")) {
            var type=new org.tzi.use.plugins.jacamo.semantics.csl.CslSchema.Classifier("domain",DomainProjection.artifactId(CslCounterArtifact.class.getName()),"Product",org.tzi.use.plugins.jacamo.semantics.csl.CslSchema.Kind.ENTITY,"",false,List.of(new org.tzi.use.plugins.jacamo.semantics.csl.CslSchema.Attribute("price","price","Integer","p",false)));
            var cfg=new OrganizationalMappingRules().derive(source,List.of(type),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var pipeline=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,cfg);
            var cartago=new CartagoSnapshotSource(env,List.of("/main"),"mental-session");var agent=new Agent();agent.initAg();agent.getBB().add(ASSyntax.parseLiteral("price(10)[source(percept)]"));
            var owner=new BridgeEntityId("jason","agent","runtime-agent",source.semanticContract().project().metadata().semanticId(),name,"test-boundary-instance");
            try(var projector=new NativeRuntimeProjector(pipeline,"mental-session",1,source.modelRevision())) {
                for(int step=0;step<2;step++) {
                    var facts=new ArrayList<RuntimeFact>(cartago.capture());var data=payload(source,owner,agent,step);data.put("name",name);facts.add(new RuntimeFact(owner,RuntimeFactKind.AGENT,data,List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));var now=java.time.Instant.now();projector.applySnapshot(new RuntimeSnapshot("mental-world-"+step,source.modelRevision(),now,now,Map.of(),Map.of(),1,facts,Map.of("cartago",Completeness.COMPLETE,"jason",Completeness.COMPLETE),"mental-world-"+step));
                    assertEquals("12",UseSystemApi.create(projector.system(),false).evaluate("Product.allInstances()->any(p | true).price").toString());assertEquals(step==0?SemanticVerdict.PASS:SemanticVerdict.FAIL,new MentalPredicateBinding().evaluate(projector.mutations(),owner.canonical(),new JasonGroundQueryCompiler().compile("price(10)"),MentalPredicateBinding.Mode.STORED_MEMBERSHIP,Set.of(owner.canonical()),false).verdict());
                    agent.getBB().remove(ASSyntax.parseLiteral("price(10)[source(percept)]"));
                }
                var folder=Files.createTempDirectory(Path.of("target"),"mental-replay-");new NativeRuntimeReplay().exportBundle(projector,pipeline.export().useText(),folder);var replay=new NativeRuntimeReplay().replay(folder);assertTrue(replay.complete(),replay.diagnostics().toString());CslVerticalSliceTest.assertReplayEndpoint(folder,replay);
            }
        } finally {env.getController("/main").removeArtifact(name);CslRuntimeTestRegistry.quitCartagoActor(env,name);env.shutdown();}
    }
    @Test void malformedOwnerOrCycleObservationCannotPartiallyReplaceExistingMentalState()throws Exception {
        var source=FunctionalObservationTest.source();var cfg=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var p=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,cfg);var engine=new NativeRuntimeMutationEngine(p.state().system(),p.state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry());var owner=new BridgeEntityId("jason","agent","runtime-agent",source.semanticContract().project().name(),"a","agent");var agent=new Agent();agent.initAg();agent.getBB().add(ASSyntax.parseLiteral("ready"));apply(engine,source,owner,agent,1);var before=engine.savepoint();
        var data=payload(source,owner,agent,2);var mental=new HashMap<>(CanonicalJson.object(data.get("mentalObservation")));mental.put("owner","other-agent");data.put("mentalObservation",mental);assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,engine.apply(owner,null,RuntimeFactKind.AGENT,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,data).status());assertEquals(before,engine.savepoint());
    }
    static Map<String,Object> payload(ModelSnapshot source,BridgeEntityId owner,Agent agent,long cycle){var data=new HashMap<String,Object>();data.put("normalizedEventKind","UPSERT_JASON_AGENT_STATE");data.put("semanticId",owner.canonical());data.put("agentDeclarationSemanticId",source.semanticContract().agentDeclarations().getFirst().metadata().semanticId());data.put("name","a");data.put("mentalObservation",JasonCycleObservation.mental(agent,owner.canonical(),cycle));return data;}
    static void apply(NativeRuntimeMutationEngine engine,ModelSnapshot source,BridgeEntityId owner,Agent agent,long cycle){var result=engine.apply(owner,null,RuntimeFactKind.AGENT,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,payload(source,owner,agent,cycle));assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,result.status(),result.diagnostic());}
}
