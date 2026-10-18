package org.tzi.use.plugins.jacamo.semantics;

import java.time.Instant;
import java.util.*;
import cartago.Op;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalRuntimeProjectionTest {
    @Test void actualCartagoCounterAndMoiseRolesUseCanonicalBindingsOneStateAndStableIncarnations() throws Exception {
        try(var f=new CanonicalRuntimeFixture()) {
            var system=f.projector.system();var counter=f.counter();assertEquals("Counter",counter.cls().name());assertEquals("0",f.value("p"));assertTrue(f.projector.mutations().canonicalMappingConfigured());
            assertSame(f.built.binding().model(),system.model());assertEquals(f.program.hash(),f.projector.mutations().canonicalProgramHash());
            assertEquals(2,roles(f).size());assertEquals(4,system.state().allLinks().stream().filter(l->Set.of("Plays","InContext").contains(l.association().name())).count());
            f.alice.doAction(f.artifact,new Op("increment"));f.await(()->f.value("p").equals("1"));assertSame(counter,f.counter());
            f.resync();assertSame(counter,f.counter());assertEquals(2,roles(f).size());
            String reviewer=f.model.semanticContract().moiseOrganizations().getFirst().structuralSpecification().roles().stream().filter(r->r.roleId().equals("reviewer")).findFirst().orElseThrow().metadata().semanticId();
            String oldReviewer=roles(f).stream().filter(o->reviewer.equals(f.projector.mutations().metadata(o,"roleSourceId"))).map(o->f.projector.mutations().metadata(o,"semanticId")).findFirst().orElseThrow();
            f.bob.doAction(f.board,new Op("leaveRole","reviewer"));f.resync();assertEquals(1,roles(f).size());assertNull(f.projector.mutations().objectForSemanticId(oldReviewer));
            f.bob.doAction(f.board,new Op("adoptRole","reviewer"));f.resync();assertEquals(2,roles(f).size());assertTrue(roles(f).stream().noneMatch(o->oldReviewer.equals(f.projector.mutations().metadata(o,"semanticId"))));
            assertSame(system,f.projector.system());assertFalse(f.projector.projectionBindings().isEmpty());
        }
    }
    @Test void partialAndInvalidReplacementPreserveObjectsWhileFullMoiseAbsenceRetiresOnlyItsScope() throws Exception {
        try(var f=new CanonicalRuntimeFixture()) {
            f.target.set(null);var full=f.captures.capture(f.model.modelRevision(),3).snapshot();var counter=f.counter();var context=f.projector.mutations().objectForSemanticId(f.groupId);
            var partial=new RuntimeSnapshot("partial",full.modelRevision(),full.captureStartedAt(),full.captureEndedAt(),full.startWatermarks(),full.endWatermarks(),1,List.of(),Map.of("cartago",Completeness.PARTIAL,"moise:org",Completeness.PARTIAL),"partial");
            f.projector.applySnapshot(partial);assertSame(counter,f.counter());assertSame(context,f.projector.mutations().objectForSemanticId(f.groupId));assertEquals(2,roles(f).size());
            f.projector.applySnapshot(full);var hash=f.projector.coordinator().latest().stateHash();var before=f.projector.mutations().objectMetadata(counter);
            var invalidFacts=full.facts().stream().map(fact->{if(fact.kind()!=RuntimeFactKind.PROPERTY||!f.artifactId.equals(fact.values().get("artifactSemanticId")))return fact;var data=new HashMap<>(fact.values());data.put("values",List.of("wrong"));data.put("valueTypes",List.of("java.lang.String"));return new RuntimeFact(fact.id(),fact.kind(),data,fact.relations(),fact.projectionStatus(),fact.completeness(),fact.evidence());}).toList();
            var invalid=new RuntimeSnapshot("invalid",full.modelRevision(),full.captureStartedAt(),full.captureEndedAt(),full.startWatermarks(),full.endWatermarks(),1,invalidFacts,full.sourceCompleteness(),"bad-type");
            assertThrows(NativeRuntimeProtocolException.class,()->f.projector.applySnapshot(invalid));assertSame(counter,f.counter());assertEquals(hash,f.projector.coordinator().latest().stateHash());assertEquals(before,f.projector.mutations().objectMetadata(counter));
            var noGroups=full.facts().stream().filter(fact->fact.kind()!=RuntimeFactKind.GROUP_BOARD).toList();var sources=new HashMap<>(full.sourceCompleteness());sources.put("moise:org",Completeness.COMPLETE);
            var removed=new RuntimeSnapshot("removed-board",full.modelRevision(),full.captureStartedAt(),full.captureEndedAt(),full.startWatermarks(),full.endWatermarks(),1,noGroups,sources,"authoritative-board-absence");
            f.projector.applySnapshot(removed);assertSame(counter,f.counter());assertNull(f.projector.mutations().objectForSemanticId(f.groupId));assertEquals(0,roles(f).size());
        }
    }
    @Test void actualTypedEnumObservationsUseExactNamedEnumAndNativeMutation() throws Exception {
        try(var f=new CanonicalRuntimeFixture(EnumCounterArtifact.class,model->{
            var base=CanonicalCslMappingTest.rules(model,"csl v4.0 EnumDomain { enum Color { RED, BLUE } entity Counter { color : Color mutable; } }");
            var bindings=new ArrayList<>(base.bindings());bindings.replaceAll(b->b.kind()==SemanticBindingIndex.Kind.ENTITY?new SemanticBindingIndex.Binding(DomainProjection.artifactId(EnumCounterArtifact.class.getName()),b.kind(),b.targetId(),b.nativeSymbol(),b.ruleId(),b.abstractionReason(),Map.of("property:color","color","javaType:color",EnumCounterArtifact.Color.class.getName(),"enumJavaType:Color",EnumCounterArtifact.Color.class.getName())):b);
            return copy(base,bindings,base.options());
        })) {
            assertEquals("Color",f.counter().cls().attribute("color",true).type().toString());assertTrue(f.value("color").contains("RED"));
            f.alice.doAction(f.artifact,new Op("blue"));f.await(()->f.value("color").contains("BLUE"));assertFalse(f.projector.mutations().metadata(f.counter(),"observedValueHash:color").isBlank());
        }
    }
    @Test void propertyRemovalAndArtifactReplacementRetireOnlyTheirExactIdentity() throws Exception {
        try(var f=new CanonicalRuntimeFixture()) {
            var oldObject=f.counter();String oldId=f.artifactId;var old=f.projector.evidence().values().stream().filter(fact->fact.kind()==RuntimeFactKind.ARTIFACT&&oldId.equals(fact.values().get("semanticId"))).findFirst().orElseThrow();
            f.alice.doAction(f.artifact,new Op("remove"));f.await(()->f.projector.coordinator().read(()->f.counter().state(f.projector.system().state()).attributeValue("p").isUndefined()));assertEquals("true",f.projector.mutations().metadata(oldObject,"propertyAbsent:p"));
            f.alice.doAction(f.artifact,new Op("add",5));f.await(()->f.value("p").equals("5"));assertSame(oldObject,f.counter());
            f.environment.getController(f.artifact.getWorkspaceId().getFullName()).removeArtifact("counter");f.resync();assertNull(f.projector.mutations().objectForSemanticId(oldId));
            f.artifact=f.alice.makeArtifact(f.alice.getJoinedWspId("main"),"counter",CslCounterArtifact.class.getName());
            var official=new org.jacamo.bridge.adapter.OfficialCartagoAdapter().capture(f.environment);f.artifactId=official.artifacts().stream().filter(a->a.uuid().equals(f.artifact.getId().toString())).findFirst().orElseThrow().metadata().semanticId();f.resync();
            assertNotEquals(oldId,f.artifactId);assertNotSame(oldObject,f.counter());assertEquals("0",f.value("p"));
            var rejected=f.projector.coordinator().read(()->f.projector.mutations().apply(old.id(),null,old.kind(),old.projectionStatus(),old.completeness(),old.values(),true));
            assertNotEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,rejected.status());assertEquals("0",f.value("p"));assertNull(f.projector.mutations().objectForSemanticId(oldId));
        }
    }
    @Test void selectedRoleSubsetKeepsOtherSourceRolesAsEvidenceAndImmutablePolicyPreservesObservedFacts() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,model->{
            var base=CanonicalCslMappingTest.rules(model,CanonicalCslMappingTest.csl().replace("p : Integer mutable","p : Integer"));
            String omitted=model.semanticContract().moiseOrganizations().getFirst().structuralSpecification().roles().stream().filter(r->r.roleId().equals("reviewer")).findFirst().orElseThrow().metadata().semanticId();
            return copy(base,base.bindings().stream().filter(b->!(b.kind()==SemanticBindingIndex.Kind.ROLE&&b.sourceId().equals(omitted))).toList(),base.options());
        })) {
            assertEquals(1,roles(f).size());assertFalse(f.projector.mutations().objectMetadata(f.projector.mutations().objectForSemanticId(f.groupId)).keySet().stream().noneMatch(k->k.startsWith("unmappedRoleEvidence:")));
            f.alice.doAction(f.artifact,new Op("increment"));f.await(()->f.value("p").equals("1"));assertEquals("OBSERVED_FACT_CHANGED_IMMUTABLE_ATTRIBUTE",f.projector.mutations().metadata(f.counter(),"immutableChange:p"));
        }
    }
    @Test void existingGenericWriterHandlesAuthoritativeNaryTupleCreateDeleteRollbackAndExport() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,model->{
            String text=CanonicalCslMappingTest.csl().replace("context Counter inv", "association Triple { Counter [*] role first; Counter [*] role second; Counter [*] role third; } context Counter inv");var base=CanonicalCslMappingTest.rules(model,text);
            var options=new HashMap<>(base.options());options.put("associationTupleAuthority","EXPLICIT_RUNTIME_MODEL_BINDING");options.put("associationTupleSourceId","custom-links");return copy(base,base.bindings(),options);
        })) {
            var runtime=new BridgeEntityId("custom-links","domain","relation","scope","triple","1");var anchor=f.projector.evidence().values().stream().filter(fact->fact.kind()==RuntimeFactKind.ARTIFACT&&f.artifactId.equals(fact.values().get("semanticId"))).findFirst().orElseThrow().id();var binding=new BridgeRelationId("runtime-model-binding",List.of(runtime,anchor),"explicit tuple binding","1");
            var payload=Map.<String,Object>of("normalizedEventKind","INSERT_LINK","association","Triple","participantSemanticIds",List.of(f.artifactId,f.artifactId,f.artifactId));
            assertTrue(f.projector.apply(tuple(f,1,runtime,binding,payload)));assertEquals(1,triples(f));
            var savepoint=f.projector.mutations().savepoint();var remove=new HashMap<>(payload);remove.put("normalizedEventKind","DELETE_LINK");assertTrue(f.projector.apply(tuple(f,2,runtime,binding,remove)));assertEquals(0,triples(f));
            f.projector.coordinator().read(()->{f.projector.mutations().rollback(savepoint);return null;});assertEquals(1,triples(f));
            var export=new NativeUseExporter().export(f.projector.system().model());assertEquals(export.originalStructuralHash(),export.recompiledStructuralHash());
            String soil=new NativeUseSoilExporter().export(f.projector.system()).commands();assertTrue(soil.contains("Triple"));
        }
    }
    private RuntimeEvent tuple(CanonicalRuntimeFixture f,long sequence,BridgeEntityId runtime,BridgeRelationId binding,Map<String,Object> payload){return new RuntimeEvent("tuple:"+sequence,"canonical-run",1,f.model.modelRevision(),"custom-links","custom-links",sequence,Instant.ofEpochMilli(sequence),RuntimeEventKind.CHANGED,RuntimeFactKind.RELATION_STATE,ProjectionStatus.MATERIALIZED_FAITHFULLY,runtime,binding,"","",Map.of(),payload,new SourceWatermark("custom-links",sequence),Completeness.COMPLETE,List.of());}
    private int triples(CanonicalRuntimeFixture f){return f.projector.coordinator().read(()->(int)f.projector.system().state().allLinks().stream().filter(l->l.association().name().equals("Triple")).count());}
    private List<org.tzi.use.uml.sys.MObject> roles(CanonicalRuntimeFixture f){return f.projector.coordinator().read(()->f.projector.system().state().allObjects().stream().filter(o->f.groupId.equals(f.projector.mutations().metadata(o,"roleContext")))).toList();}
    private static MappingRuleSet copy(MappingRuleSet base,List<SemanticBindingIndex.Binding> bindings,Map<String,String> options){return new MappingRuleSet(base.version(),base.id(),base.profileId(),base.profileVersion(),base.expectedSourceHash(),base.cslSource(),base.iStarSource(),bindings,base.contracts(),base.decisions(),options);}
}
