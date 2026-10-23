package org.tzi.use.plugins.jacamo.semantics;

import java.util.*;
import cartago.Op;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.semantics.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.ConstraintCoverageResolver;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.ocl.value.Value;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalRuntimeAdmissionTest {
    @Test void alreadyObservedGroupCannotInventAPlayerMissingFromTheAcceptedNativeCut()throws Exception {
        try(var f=new CanonicalRuntimeFixture()) {
            f.target.set(null);
            var group=f.projector.evidence().values().stream().filter(v->v.kind()==RuntimeFactKind.GROUP_BOARD).findFirst().orElseThrow();
            f.projector.coordinator().read(()->{
                var method=org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine.class.getDeclaredMethod("cslPort");method.setAccessible(true);
                var port=(SemanticObservationMapper.Port)method.invoke(f.projector.mutations());
                String bobSource=f.model.semanticContract().agentDeclarations().stream().filter(a->a.name().equals("bob")).findFirst().orElseThrow().metadata().semanticId();
                var bob=f.projector.mutations().objectForSemanticId(bobSource);assertNotNull(bob);
                String primary=port.metadata(bob,"semanticId");port.delete(primary);assertNull(port.find(bobSource));
                var mapper=new JaCaMoRuntimeStateMapping(f.program,f.projector.system().model());
                var result=mapper.apply(mapper.plan(port,group.id(),"UPSERT_MOISE_GROUP",Completeness.COMPLETE,group.values()),port);
                assertEquals("INCOMPLETE",port.metadata(result.object(),"rolePopulationQuality"));
                assertTrue(port.metadata(result.object(),"unresolvedPlayerEvidence").contains(bobSource));
                assertNull(port.find(bobSource));assertTrue(port.objects().stream().noneMatch(o->primary.equals(port.metadata(o,"rolePlayer"))));
                var invariant=f.projector.system().model().classInvariants().stream().filter(i->i.name().equals("CslV4RoleCompatibility")).findFirst().orElseThrow();
                assertEquals("CANONICAL_ROLE_POPULATION_BINDING_INCOMPLETE",new ConstraintCoverageResolver().unavailableSelf(invariant.bodyExpression(),f.projector.mutations(),result.object(),Map.of("cartago",Completeness.COMPLETE,"moise:org",Completeness.COMPLETE),false));
                return null;
            });
        }
    }
    @Test void exactCastContainsOriginalActorsAndSelfWithStableThenNewEpisodeOnReenactment() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            var adapter=new JaCaMoOccurrenceBindingAdapter();var first=f.projector.coordinator().read(()->adapter.resolve(f.program,f.projector.mutations())).getFirst();
            assertTrue(first.complete(),first.unavailable().toString());assertEquals(Set.of("Worker","Reviewer"),first.actors().keySet());assertEquals(2,first.instances().size());assertEquals(f.artifactId,first.selves().get("left"));
            f.resync();assertEquals(first,f.projector.coordinator().read(()->adapter.resolve(f.program,f.projector.mutations())).getFirst());
            f.bob.doAction(f.board,new Op("leaveRole","reviewer"));f.resync();assertFalse(f.projector.coordinator().read(()->adapter.resolve(f.program,f.projector.mutations())).getFirst().complete());
            f.bob.doAction(f.board,new Op("adoptRole","reviewer"));f.resync();var next=f.projector.coordinator().read(()->adapter.resolve(f.program,f.projector.mutations())).getFirst();assertTrue(next.complete());assertNotEquals(first.episode(),next.episode());
        }
    }
    @Test void typedPlanIsReadOnlyAndManualEditsCannotCertifyAcceptedWorldState() throws Exception {
        try(var f=new CanonicalRuntimeFixture()) {
            var fact=f.projector.evidence().values().stream().filter(v->v.kind()==RuntimeFactKind.PROPERTY&&f.artifactId.equals(v.values().get("artifactSemanticId"))).findFirst().orElseThrow();
            var state=f.projector.mutations().savepoint();var before=f.projector.mutations().objectMetadata(f.counter());
            var mapper=new JaCaMoRuntimeStateMapping(f.program,f.projector.system().model());var plan=f.projector.coordinator().read(()->mapper.plan(reads(f),fact.id(),"UPSERT_CARTAGO_PROPERTY_SNAPSHOT",Completeness.COMPLETE,fact.values()));
            assertEquals(1,plan.assignments().size());assertEquals("0",plan.assignments().getFirst().value().toString());assertEquals(before,f.projector.mutations().objectMetadata(f.counter()));
            var wrong=new HashMap<>(fact.values());wrong.put("values",List.of("x"));wrong.put("valueTypes",List.of("java.lang.String"));assertThrows(IllegalArgumentException.class,()->mapper.plan(reads(f),fact.id(),"UPSERT_CARTAGO_PROPERTY_SNAPSHOT",Completeness.COMPLETE,wrong));assertEquals("0",f.value("p"));
            f.projector.coordinator().read(()->{var inv=f.projector.system().model().classInvariants().iterator().next();assertEquals("",new ConstraintCoverageResolver().unavailableSelf(inv.bodyExpression(),f.projector.mutations(),f.counter(),Map.of("cartago",Completeness.COMPLETE),false));UseSystemApi.create(f.projector.system(),false).setAttributeValue(f.counter().name(),"p","7");assertTrue(new ConstraintCoverageResolver().unavailableSelf(inv.bodyExpression(),f.projector.mutations(),f.counter(),Map.of("cartago",Completeness.COMPLETE),false).startsWith("NATIVE_VALUE_DIFFERS"));f.projector.mutations().rollback(state);return null;});assertEquals(before,f.projector.mutations().objectMetadata(f.counter()));assertEquals("0",f.value("p"));
        }
    }
    @Test void unknownErrorUndefinedPartialAndGapNeverBecomeFalse() {
        var gate=new ObservationQualityGate();
        for(var status:NativeOclOutcome.Status.values()) {
            var result=new NativeOclOutcome(status,"",status.name());var decision=gate.booleanInput(result,true,false);
            assertEquals(status==NativeOclOutcome.Status.PASS?Boolean.TRUE:status==NativeOclOutcome.Status.FAIL?Boolean.FALSE:null,decision.known());
            assertNull(gate.booleanInput(result,false,false).known());assertNull(gate.booleanInput(result,true,true).known());
        }
    }
    @Test void actualPlayerQuitDisposesIndependentIdentityAndRolesAndCannotRedirectOldIncarnation() throws Exception {
        try(var f=new CanonicalRuntimeFixture()) {
            String source=f.model.semanticContract().agentDeclarations().stream().filter(a->a.name().equals("bob")).findFirst().orElseThrow().metadata().semanticId();var actor=f.projector.mutations().objectForSemanticId(source);assertNotNull(actor);String old=f.projector.mutations().metadata(actor,"semanticId");
            org.jacamo.bridge.adapter.CslRuntimeTestRegistry.quitCartagoActor(f.environment,f.bobName);f.resync();assertNull(f.projector.mutations().objectForSemanticId(old));assertNull(f.projector.mutations().objectForSemanticId(source));assertTrue(f.projector.system().state().allObjects().stream().noneMatch(o->old.equals(f.projector.mutations().metadata(o,"rolePlayer"))));
        }
    }
    private SemanticObservationMapper.Port reads(CanonicalRuntimeFixture f){return new SemanticObservationMapper.Port(){
        public MObject find(String id){return f.projector.mutations().objectForSemanticId(id);}public String metadata(MObject object,String key){return f.projector.mutations().metadata(object,key);}public Collection<MObject> objects(){return f.projector.system().state().allObjects();}
        public MObject upsert(String id,String type,String label){throw new AssertionError("PLAN_MUST_NOT_WRITE");}public void alias(String id,MObject o){throw new AssertionError("PLAN_MUST_NOT_WRITE");}public void metadata(MObject o,String k,String v){throw new AssertionError("PLAN_MUST_NOT_WRITE");}public void value(MObject o,String k,Value v){throw new AssertionError("PLAN_MUST_NOT_WRITE");}public void connect(String a,MObject x,MObject y){throw new AssertionError("PLAN_MUST_NOT_WRITE");}public void delete(String id){throw new AssertionError("PLAN_MUST_NOT_WRITE");}public void disconnect(String a,MObject x,MObject y){throw new AssertionError("PLAN_MUST_NOT_WRITE");}
    };}
}
