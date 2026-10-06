package org.tzi.use.plugins.jacamo.codegrounded;

import org.tzi.use.plugins.jacamo.codegrounded.trace.NativeObjectBindings;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.*;

class GenericFunctionalRuntimeProjectionTest {
    @Test void sourceGoalsKeepOperatorsOrdinalsMissionReferencesAndNoExecutionStatus() throws Exception {
        var org=MoiseDomainProjectionTest.organization();
        var result=new CodeGroundedNativePipeline().build(MoiseDomainProjectionTest.withOrganizations(CodeGroundedTestFixtures.helloSnapshot(),List.of(org)));
        var system=result.state().system();
        for(var scheme:org.functionalSpecification().schemes()) {
            for(var goal:scheme.goals()) {
                var object=result.state().semanticObjectIndex().get(goal.metadata().semanticId()); assertNotNull(object);
                assertEquals("OrganizationalGoal",object.cls().name()); assertTrue(object.state(system.state()).attributeValue("runtimeState").isUndefined());
            }
            for(var plan:scheme.plans()) {
                var parent=result.state().semanticObjectIndex().get(plan.targetGoalSemanticId());
                assertEquals(new StringValue(plan.operator()),parent.state(system.state()).attributeValue("decompositionOperator"));
                for(int i=0;i<plan.orderedSubGoalSemanticIds().size();i++) {
                    var child=result.state().semanticObjectIndex().get(plan.orderedSubGoalSemanticIds().get(i));
                    assertEquals(IntegerValue.valueOf(i),child.state(system.state()).attributeValue("orderInParent"));
                    assertTrue(link(system,"subGoals","OrganizationalGoal","OrganizationalGoal",parent,child));
                }
            }
            for(var mission:scheme.missions()) for(String id:mission.goalSemanticIds()) assertTrue(link(system,"missionGoals","Mission","OrganizationalGoal",
                result.state().semanticObjectIndex().get(mission.metadata().semanticId()),result.state().semanticObjectIndex().get(id)));
        }
        assertNull(system.model().getClass("OPlan")); assertNull(system.model().getClass("OrganizationalPlan"));
        assertEquals(result.model().structuralHash(),result.export().recompiledStructuralHash());
    }
    @Test void runtimeLiteralCutsSupportMultipleGoalsRemovalReappearanceAndAtomicForeignBindingRejection() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline(); var system=pipeline.state().system();
        var engine=new NativeRuntimeMutationEngine(system,pipeline.state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry());
        var declaration=pipeline.source().snapshot().agentDeclarations().getFirst();
        var identity=new BridgeEntityId("jason","agent","runtime-agent",pipeline.source().project().name(),declaration.name(),"real-incarnation");
        var payload=new LinkedHashMap<String,Object>(); payload.put("normalizedEventKind","UPSERT_JASON_AGENT_STATE"); payload.put("semanticId",identity.canonical());
        payload.put("name",declaration.name()); payload.put("agentDeclarationSemanticId",declaration.metadata().semanticId());
        payload.put("beliefs",List.of(literal("runtime-belief",identity,"source-a","ready(a)"),literal("runtime-belief",identity,"source-b","ready(b)")));
        payload.put("goals",List.of(literal("runtime-goal",identity,"observed-a","deliver(a)"),literal("runtime-goal",identity,"observed-b","deliver(b)")));
        assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,identity,RuntimeFactKind.AGENT,payload).status());
        var agent=pipeline.state().semanticObjectIndex().get(declaration.metadata().semanticId());
        var goalAssociation=system.model().getAssociation(DomainProjection.relation("hasGoal","Agent","AgentGoal"));
        assertEquals(2,system.state().allLinks().stream().filter(l->l.association()==goalAssociation && l.linkedObjects().getFirst()==agent).count());
        var soil=new NativeUseSoilExporter(); var replay=soil.replay(new NativeUseExporter().export(system.model()).recompiledModel(),soil.export(system).commands());
        assertEquals(system.state().numObjects(),replay.state().numObjects());
        payload.put("goals",List.of()); assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,identity,RuntimeFactKind.AGENT,payload).status());
        assertEquals(0,system.state().allLinks().stream().filter(l->l.association()==goalAssociation && l.linkedObjects().getFirst()==agent).count());
        payload.put("goals",List.of(literal("runtime-goal",identity,"observed-a","deliver(a)")));
        assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,identity,RuntimeFactKind.AGENT,payload).status());
        assertEquals("true",UseSystemApi.create(system,false).evaluate("AgentGoal.allInstances()->exists(g | g.literal = 'deliver(a)')").toString());
        var before=engine.savepoint(); payload.put("name","foreign-agent");
        assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,apply(engine,identity,RuntimeFactKind.AGENT,payload).status());
        assertEquals(before,engine.savepoint()); assertSame(system,engine.system());
    }
    @Test void dynamicSchemesKeepResponsibilityCommitmentsAndAuthoritativeGoalStateOnSameSystem() throws Exception {
        var org=MoiseDomainProjectionTest.organization();
        var pipeline=new CodeGroundedNativePipeline().build(MoiseDomainProjectionTest.withOrganizations(CodeGroundedTestFixtures.helloSnapshot(),List.of(org)));
        var system=pipeline.state().system(); var api=UseSystemApi.create(system,false);
        var groupSpec=org.structuralSpecification().groups().getFirst();
        var owner=api.createObject(DomainProjection.classFor(system.model(),"organisation",org.metadata().semanticId()),"genericOrg");
        var group=api.createObject(DomainProjection.classFor(system.model(),"group",groupSpec.metadata().semanticId()),"genericGroup");
        api.createLinkEx(system.model().getAssociation(DomainProjection.relation("containsGroup","Organization","Group")),new MObject[]{owner,group});
        var index=new NativeObjectBindings(pipeline.state().semanticObjectIndex()); index.put("actual:org",owner); index.put("actual:group",group);
        var agent=pipeline.source().snapshot().agentDeclarations().getFirst();
        var role=org.structuralSpecification().groupRoleCardinalities().stream().filter(c->c.groupId().equals(groupSpec.metadata().semanticId()) && c.min()>0).findFirst().orElseThrow();
        MoiseDomainProjection.linkObject(api,index,MoiseDomainProjection.roleAssociation(system.model(),org.metadata().semanticId(),groupSpec.metadata().semanticId(),role.roleId()),index.get(agent.metadata().semanticId()),group);
        var engine=new NativeRuntimeMutationEngine(system,index,new CodeGroundedRuntimeRuleRegistry());
        var source=org.functionalSpecification().schemes().getFirst(); var mission=source.missions().getFirst();
        var identity=new BridgeEntityId("moise","organisation","scheme-board","actual-org","actual-scheme","real-board");
        var values=new LinkedHashMap<String,Object>(); values.put("normalizedEventKind","UPSERT_MOISE_SCHEME"); values.put("runtimeIdentity",identity.canonical()); values.put("semanticId",identity.canonical());
        values.put("organisationDefinition",SemanticContractCodec.organizationToTree(org)); values.put("organisationSpecSemanticId",org.metadata().semanticId()); values.put("schemeSpecSemanticId",source.metadata().semanticId());
        values.put("organisationSemanticId","actual:org"); values.put("organisationName","actual-org"); values.put("name","actual-scheme"); values.put("responsibleGroupSemanticIds",List.of("actual:group"));
        values.put("commitments",List.of(Map.of("agentSemanticId",agent.metadata().semanticId(),"missionSemanticId",mission.metadata().semanticId())));
        values.put("goalStates",source.goals().stream().map(g->Map.of("goalSemanticId",g.metadata().semanticId(),"state","NOT_SATISFIED")).toList());
        assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,identity,RuntimeFactKind.SCHEME_BOARD,values).status());
        var instance=engine.objectForSemanticId(identity.canonical());
        assertTrue(instance.cls().parents().contains(system.model().getClass("Scheme"))); assertTrue(link(system,"responsibleFor","Group","Scheme",group,instance));
        var commitment=engine.objectForSemanticId(MoiseDomainProjection.functionalObjectId("mission",identity.canonical(),mission.metadata().semanticId()));
        assertTrue(link(system,"committedTo","Agent","Mission",index.get(agent.metadata().semanticId()),commitment));
        int count=system.state().numObjects(); values.put("commitments",List.of()); values.put("responsibleGroupSemanticIds",List.of());
        values.put("goalStates",source.goals().stream().map(g->Map.of("goalSemanticId",g.metadata().semanticId(),"state","SATISFIED")).toList());
        assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,identity,RuntimeFactKind.SCHEME_BOARD,values).status());
        assertEquals(count,system.state().numObjects()); assertSame(instance,engine.objectForSemanticId(identity.canonical()));
        assertFalse(link(system,"responsibleFor","Group","Scheme",group,instance)); assertFalse(link(system,"committedTo","Agent","Mission",index.get(agent.metadata().semanticId()),commitment));
        assertEquals(new StringValue("SATISFIED"),engine.objectForSemanticId(MoiseDomainProjection.functionalObjectId("organisational-goal",identity.canonical(),source.goals().getFirst().metadata().semanticId())).state(system.state()).attributeValue("runtimeState"));
        values.put("goalObservationVersion","1.0.0");values.put("schemeArguments",Map.of("g",Map.of("amount","23")));
        values.put("goalStates",source.goals().stream().map(g->Map.of("goalSemanticId",g.metadata().semanticId(),"state","ENABLED",
                "committedAgentSemanticIds",List.of(agent.metadata().semanticId()),"achievedAgentSemanticIds",List.of(agent.metadata().semanticId()),
                "arguments",Map.of("amount","23"))).toList());
        assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,identity,RuntimeFactKind.SCHEME_BOARD,values).status());
        var observedGoal=engine.objectForSemanticId(MoiseDomainProjection.functionalObjectId("organisational-goal",identity.canonical(),source.goals().getFirst().metadata().semanticId()));
        assertEquals(new StringValue("ENABLED"),observedGoal.state(system.state()).attributeValue("runtimeState"));
        assertTrue(link(system,"goalCommitment","Agent","OrganizationalGoal",index.get(agent.metadata().semanticId()),observedGoal));
        assertTrue(link(system,"goalAchievement","Agent","OrganizationalGoal",index.get(agent.metadata().semanticId()),observedGoal));
        assertEquals(new StringValue("{\"amount\":\"23\"}"),observedGoal.state(system.state()).attributeValue("arguments"));
        try(var coordinator=new RuntimeVerificationCoordinator(engine,"actual-session",1,"actual-model")) {
            coordinator.acceptedSnapshot("actual-session",1,"actual-model",Map.of("moise",Completeness.COMPLETE));coordinator.manualVerify();
            var cut=coordinator.verificationSnapshot();
            assertEquals("'ENABLED'",cut.image().objects().get(observedGoal.name()).attributes().get("runtimeState"));
            assertTrue(cut.image().links().stream().anyMatch(l->l.association().equals(DomainProjection.relation("goalAchievement","Agent","OrganizationalGoal"))));
            assertSame(system,coordinator.system());
            System.out.println("GOAL_SNAPSHOT_BYTES="+cut.estimatedBytes());
        }
        var before=engine.savepoint(); values.put("responsibleGroupSemanticIds",List.of("foreign:group"));
        assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,apply(engine,identity,RuntimeFactKind.SCHEME_BOARD,values).status()); assertEquals(before,engine.savepoint());
        var soil=new NativeUseSoilExporter(); var replay=soil.replay(new NativeUseExporter().export(system.model()).recompiledModel(),soil.export(system).commands());
        assertEquals(count,replay.state().numObjects()); assertEquals(system.state().allLinks().size(),replay.state().allLinks().size());
        assertBindingsComplete(engine);
    }
    static void assertBindingsComplete(NativeRuntimeMutationEngine engine) {
        var system=engine.system(); var bindings=engine.targetBindings();
        assertTrue(bindings.stream().allMatch(b->!b.sourceIdentity().isBlank() && !b.targetIdentity().isBlank()));
        var targets=bindings.stream().map(NativeRuntimeMutationEngine.TargetBinding::targetIdentity).collect(java.util.stream.Collectors.toSet());
        for(var cls:system.model().classes()) {
            assertTrue(targets.contains("class:"+cls.name()),cls.name());
            for(var attribute:cls.attributes()) assertTrue(targets.contains("attribute:"+cls.name()+"."+attribute.name()));
            for(var operation:cls.operations()) assertTrue(targets.contains("operation:"+cls.name()+"."+operation.name()));
        }
        for(var association:system.model().associations()) assertTrue(targets.contains("association:"+association.name()));
        for(var object:system.state().allObjects()) {
            assertTrue(targets.contains(object.name()),object.name());
            for(var attribute:object.cls().allAttributes()) assertTrue(targets.contains("value:"+object.name()+"."+attribute.name()));
        }
        for(var link:system.state().allLinks()) assertTrue(targets.contains("link:"+link.association().name()+":"+link.linkedObjects().stream().map(MObject::name).toList()));
    }
    private static NativeRuntimeMutationEngine.ApplyResult apply(NativeRuntimeMutationEngine engine,BridgeEntityId id,RuntimeFactKind kind,Map<String,Object> values) {
        return engine.apply(id,null,kind,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,values);
    }
    private static Map<String,Object> literal(String kind,BridgeEntityId agent,String source,String text) {
        return Map.of("semanticId",DomainProjection.occurrenceId(kind,agent.canonical(),source),"sourceIdentity",source,"literal",text);
    }
    private static boolean link(MSystem system,String kind,String first,String second,MObject one,MObject two) {
        return system.state().hasLinkBetweenObjects(system.model().getAssociation(DomainProjection.relation(kind,first,second)),new MObject[]{one,two});
    }
}
