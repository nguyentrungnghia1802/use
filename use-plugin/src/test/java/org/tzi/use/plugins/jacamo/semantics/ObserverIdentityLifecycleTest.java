package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import jason.asSemantics.Agent;
import jason.asSyntax.ASSyntax;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.observer.*;

class ObserverIdentityLifecycleTest {
    static RuntimeSnapshot snapshot(ModelSnapshot model,String id,List<RuntimeFact> facts,Completeness complete) {var now=java.time.Instant.EPOCH;return new RuntimeSnapshot(id,model.modelRevision(),now,now,Map.of(),Map.of(),1,facts,Map.of("jason",complete),id);}
    static RuntimeFact fact(ModelSnapshot model,BridgeEntityId owner,Agent agent,int cycle){return new RuntimeFact(owner,RuntimeFactKind.AGENT,MentalStateProjectionTest.payload(model,owner,agent,cycle),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());}
    @Test void sameRunDiffBeliefReappearanceRestartPartialOmissionAndCompleteDeletionRespectLifetimes()throws Exception {
        var source=FunctionalObservationTest.source();var cfg=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var pipeline=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,cfg);var agent=new Agent();agent.initAg();agent.getBB().add(ASSyntax.parseLiteral("ready"));var owner=new BridgeEntityId("jason","agent","runtime-agent",source.semanticContract().project().name(),"a","first-incarnation");
        try(var projector=new NativeRuntimeProjector(pipeline,"life",1,source.modelRevision())) {
            projector.applySnapshot(snapshot(source,"one",List.of(fact(source,owner,agent,1)),Completeness.COMPLETE));var object=projector.mutations().objectForSemanticId(owner.canonical());var belief=projector.system().state().objectsOfClass(projector.system().model().getClass(MentalUseBinding.BELIEF)).iterator().next();String oldBelief=projector.mutations().metadata(belief,"semanticId");
            projector.applySnapshot(snapshot(source,"same",List.of(fact(source,owner,agent,1)),Completeness.COMPLETE));assertSame(object,projector.mutations().objectForSemanticId(owner.canonical()));
            agent.getBB().remove(ASSyntax.parseLiteral("ready"));projector.applySnapshot(snapshot(source,"removed",List.of(fact(source,owner,agent,2)),Completeness.COMPLETE));assertNull(projector.mutations().objectForSemanticId(oldBelief));agent.getBB().add(ASSyntax.parseLiteral("ready"));projector.applySnapshot(snapshot(source,"readded",List.of(fact(source,owner,agent,3)),Completeness.COMPLETE));var newBelief=projector.system().state().objectsOfClass(projector.system().model().getClass(MentalUseBinding.BELIEF)).iterator().next();assertNotSame(belief,newBelief);assertNotEquals(oldBelief,projector.mutations().metadata(newBelief,"semanticId"));
            var before=projector.mutations().savepoint();assertThrows(NativeRuntimeProtocolException.class,()->projector.applySnapshot(snapshot(source,"regressed",List.of(fact(source,owner,agent,1)),Completeness.COMPLETE)));assertEquals(before,projector.mutations().savepoint());
            var restarted=new BridgeEntityId("jason","agent","runtime-agent",owner.scope(),"a","new-incarnation");projector.applySnapshot(snapshot(source,"restart",List.of(fact(source,restarted,agent,0)),Completeness.COMPLETE));var fresh=projector.mutations().objectForSemanticId(restarted.canonical());assertNotNull(fresh);assertNotSame(object,fresh);assertNotEquals(object.name(),fresh.name());assertNull(projector.mutations().objectForSemanticId(owner.canonical()));assertNull(projector.mutations().objectForSemanticId(MentalObservationMapper.identity(owner.canonical())));
            assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,projector.mutations().apply(owner,null,RuntimeFactKind.AGENT,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,MentalStateProjectionTest.payload(source,owner,agent,4)).status());
            projector.applySnapshot(snapshot(source,"partial",List.of(),Completeness.PARTIAL));assertSame(fresh,projector.mutations().objectForSemanticId(restarted.canonical()));projector.applySnapshot(snapshot(source,"deleted",List.of(),Completeness.COMPLETE));assertNull(projector.mutations().objectForSemanticId(restarted.canonical()));assertTrue(projector.system().state().allObjects().isEmpty());
        }
    }
    @Test void multipleConfiguredInstancesKeepDeclarationAsProvenanceInsteadOfAliasingBothAgents()throws Exception {
        var directory=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"),"multi-agent-");var path=directory.resolve("multi.jcm");java.nio.file.Files.writeString(path,"mas multiple {\nagent a {\ninstances: 2\n}\n}\n");var model=new org.jacamo.bridge.adapter.OfficialProjectAdapter().adapt(new org.jacamo.bridge.adapter.OfficialProjectLoader().load(path),path);assertEquals(2,model.semanticContract().agentDeclarations().getFirst().instances());
        var cfg=new OrganizationalMappingRules().derive(model,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var built=new CodeGroundedNativePipeline().build(model,NativeProjectionMode.AUTO,cfg);var ag=new Agent();ag.initAg();String scope=model.semanticContract().project().name();var a=new BridgeEntityId("jason","agent","runtime-agent",scope,"a1","inc-a1");var b=new BridgeEntityId("jason","agent","runtime-agent",scope,"a2","inc-a2");var af=new java.util.HashMap<>(MentalStateProjectionTest.payload(model,a,ag,1));af.put("name","a1");var bf=new java.util.HashMap<>(MentalStateProjectionTest.payload(model,b,ag,1));bf.put("name","a2");
        try(var projector=new NativeRuntimeProjector(built,"multi",1,model.modelRevision())) {projector.applySnapshot(snapshot(model,"two",List.of(new RuntimeFact(a,RuntimeFactKind.AGENT,af,List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()),new RuntimeFact(b,RuntimeFactKind.AGENT,bf,List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of())),Completeness.COMPLETE));var one=projector.mutations().objectForSemanticId(a.canonical());var two=projector.mutations().objectForSemanticId(b.canonical());assertNotNull(one);assertNotNull(two);assertNotSame(one,two);String declaration=model.semanticContract().agentDeclarations().getFirst().metadata().semanticId();assertNull(projector.mutations().objectForSemanticId(declaration));assertEquals(declaration,projector.mutations().metadata(one,"declarationBinding"));assertEquals(declaration,projector.mutations().metadata(two,"declarationBinding"));}
    }
}
