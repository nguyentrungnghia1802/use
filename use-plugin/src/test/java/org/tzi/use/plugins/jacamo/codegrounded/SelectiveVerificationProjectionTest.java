package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;

class SelectiveVerificationProjectionTest {
    private static final String PROFILE="context Agent inv DomainBeliefs: self.beliefs->forAll(b | not b.literal.oclIsUndefined())";

    @Test void onlyDemandedAuthoredNonduplicatedFactsExposeAndRemovalDoesNotResurrectOnReload() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try(var projector=new NativeRuntimeProjector(pipeline,"selection-session",1,pipeline.source().revision())) {
            var engine=projector.mutations(); var coordinator=projector.coordinator(); var system=engine.system();
            var declaration=pipeline.source().snapshot().agentDeclarations().getFirst();
            var id=new BridgeEntityId("jason","agent","runtime-agent",pipeline.source().project().name(),declaration.name(),"selection-incarnation");
            var values=new LinkedHashMap<String,Object>();
            values.put("normalizedEventKind","UPSERT_JASON_AGENT_STATE"); values.put("semanticId",id.canonical());
            values.put("agentDeclarationSemanticId",declaration.metadata().semanticId()); values.put("name",declaration.name()); values.put("goals",List.of());
            var domain=belief(id,"ready(a)",true,false);
            var internal=belief(id,"platform_state(x)",false,false);
            var duplicated=belief(id,"ready(b)[source(percept)]",true,true);
            values.put("beliefs",List.of(domain,internal,duplicated));
            assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,id,values).status());
            assertEquals(0,beliefCount(system));
            coordinator.loadProfileSource("domain.ocl",PROFILE);
            assertEquals(1,system.state().allObjects().stream().filter(o->o.cls().name().equals("Belief")
                    && engine.metadata(o,"sourceLayer").equals("RUNTIME")).count());
            assertNotNull(engine.objectForSemanticId(domain.get("semanticId").toString()));
            assertNull(engine.objectForSemanticId(internal.get("semanticId").toString()));
            assertNull(engine.objectForSemanticId(duplicated.get("semanticId").toString()));
            int classes=system.model().classes().size();
            values.put("beliefs",List.of(internal)); apply(engine,id,values);
            assertNull(engine.objectForSemanticId(domain.get("semanticId").toString()));
            coordinator.loadProfileSource("domain.ocl",PROFILE);
            assertNull(engine.objectForSemanticId(domain.get("semanticId").toString()));
            values.put("beliefs",List.of(domain,internal)); apply(engine,id,values);
            assertNotNull(engine.objectForSemanticId(domain.get("semanticId").toString()));
            coordinator.loadProfileSource("identity.ocl","context Agent inv Identity: Agent.allInstances()->forAll(a | not a.name.oclIsUndefined())");
            assertEquals(0,beliefCount(system));
            coordinator.loadProfileSource("domain.ocl",PROFILE);
            assertNotNull(engine.objectForSemanticId(domain.get("semanticId").toString()));
            var nextId=new BridgeEntityId("jason","agent","runtime-agent",id.scope(),id.localId(),"next-incarnation");
            var nextDomain=belief(nextId,"ready(c)",true,false);
            values.put("semanticId",nextId.canonical()); values.put("beliefs",List.of(nextDomain));
            apply(engine,nextId,values);
            assertEquals(Set.of(nextId.canonical()),engine.savepoint().jasonCuts().keySet());
            assertNull(engine.objectForSemanticId(domain.get("semanticId").toString()));
            coordinator.loadProfileSource("domain.ocl",PROFILE);
            assertNull(engine.objectForSemanticId(domain.get("semanticId").toString()));
            assertNotNull(engine.objectForSemanticId(nextDomain.get("semanticId").toString()));
            var saved=engine.savepoint();
            assertThrows(IllegalArgumentException.class,()->coordinator.loadProfileSource("invalid.ocl","context Missing inv Bad: true"));
            assertEquals(saved,engine.savepoint()); assertSame(system,coordinator.constraints().system());
            var invariant=system.model().classInvariants().stream().filter(i->i.name().equals("DomainBeliefs")).findFirst().orElseThrow();
            invariant.setActive(false); coordinator.manualVerify(); assertEquals(0,beliefCount(system));
            invariant.setActive(true); coordinator.manualVerify(); assertNotNull(engine.objectForSemanticId(nextDomain.get("semanticId").toString()));
            assertEquals(classes,system.model().classes().size()); GenericFunctionalRuntimeProjectionTest.assertBindingsComplete(engine);
            var soil=new NativeUseSoilExporter(); var exported=new NativeUseExporter().export(system.model());
            var replay=soil.replay(exported.recompiledModel(),soil.export(system).commands());
            assertEquals(system.state().numObjects(),replay.state().numObjects());
        }
    }

    @Test void missingProvenanceFailsClosedAndProfileInstallRollsBack() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try(var projector=new NativeRuntimeProjector(pipeline,"missing-proof",1,pipeline.source().revision())) {
            var engine=projector.mutations(); var declaration=pipeline.source().snapshot().agentDeclarations().getFirst();
            var id=new BridgeEntityId("jason","agent","runtime-agent",pipeline.source().project().name(),declaration.name(),"missing-proof");
            var literal=Map.<String,Object>of("semanticId",DomainProjection.occurrenceId("runtime-belief",id.canonical(),"unproved"),
                    "sourceIdentity","unproved","literal","ready(a)");
            var values=Map.<String,Object>of("normalizedEventKind","UPSERT_JASON_AGENT_STATE","semanticId",id.canonical(),
                    "agentDeclarationSemanticId",declaration.metadata().semanticId(),"name",declaration.name(),"beliefs",List.of(literal),"goals",List.of());
            assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,id,values).status());
            var saved=engine.savepoint();
            var listener=new ProfileNotifications(); engine.system().getEventBus().register(listener);
            assertThrows(NativeRuntimeProtocolException.class,()->projector.coordinator().loadProfileSource("proof.ocl",PROFILE));
            assertEquals(saved,engine.savepoint()); assertNull(projector.coordinator().constraints().profile());
            assertEquals(0,listener.loaded,"Rejected profile must not leak a loaded-invariants notification");
        }
    }

    @Test void sameProviderInfrastructureIsExcludedWhileApplicationTypesWithIdenticalSimpleNamesRemainEligible() {
        assertFalse(DomainProjection.domainArtifact("cartago.tools.Console"));
        assertFalse(DomainProjection.domainArtifact("cartago.tools.TupleSpace"));
        assertTrue(DomainProjection.domainArtifact(Console.class.getName()));
        assertTrue(DomainProjection.domainArtifact(TupleSpace.class.getName()));
        assertFalse(DomainProjection.domainArtifact("application.Unavailable"));
        assertTrue(DomainProjection.domainArtifact("application.LateType","APPLICATION"));
    }
    public static class Console extends cartago.Artifact { }
    public static class TupleSpace extends cartago.Artifact { }
    @Test void demandedAuthoredBeliefWithInfrastructureLikeNameIsNotBlacklisted() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try(var projector=new NativeRuntimeProjector(pipeline,"similar-name",1,pipeline.source().revision())) {
            var declaration=pipeline.source().snapshot().agentDeclarations().getFirst();
            var id=new BridgeEntityId("jason","agent","runtime-agent",pipeline.source().project().name(),declaration.name(),"similar-name");
            var authored=new LinkedHashMap<>(belief(id,"focused(domain)",true,false));authored.put("predicateIndicator","focused/1");
            var duplicated=new LinkedHashMap<>(belief(id,"focused(platform)[source(percept)]",true,true));duplicated.put("predicateIndicator","focused/1");
            var values=Map.<String,Object>of("normalizedEventKind","UPSERT_JASON_AGENT_STATE","semanticId",id.canonical(),
                    "agentDeclarationSemanticId",declaration.metadata().semanticId(),"name",declaration.name(),
                    "beliefs",List.of(authored,duplicated),"goals",List.of());
            apply(projector.mutations(),id,values);projector.coordinator().loadProfileSource("similar.ocl",PROFILE);
            assertNotNull(projector.mutations().objectForSemanticId(authored.get("semanticId").toString()));
            assertNull(projector.mutations().objectForSemanticId(duplicated.get("semanticId").toString()));
            var removed=new LinkedHashMap<>(values);removed.put("beliefs",List.of());apply(projector.mutations(),id,removed);
            assertNull(projector.mutations().objectForSemanticId(authored.get("semanticId").toString()));
        }
    }
    @Test void recordedProviderProofKeepsKnownArtifactsAvailableWithoutProducerClasses() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try(var projector=new NativeRuntimeProjector(pipeline,RuntimeVerificationFixtures.SESSION,1,RuntimeVerificationFixtures.REVISION)) {
            projector.applySnapshot(RuntimeVerificationFixtures.snapshot("provider",0));
            var engine=projector.mutations(); var artifact=engine.objectForSemanticId(RuntimeVerificationFixtures.ARTIFACT);
            var previous=Thread.currentThread().getContextClassLoader();
            String fqcn=LiveRuntimePropertyArtifact.class.getName();
            try {
                Thread.currentThread().setContextClassLoader(new ClassLoader(previous) {
                    @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                        if(name.equals(fqcn)) throw new ClassNotFoundException(name);
                        return super.loadClass(name,resolve);
                    }
                });
                assertEquals("UNAVAILABLE",DomainProjection.artifactOrigin(fqcn));
                var result=engine.apply(RuntimeVerificationFixtures.ID,null,RuntimeFactKind.ARTIFACT,
                        ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,
                        RuntimeVerificationFixtures.artifact(RuntimeVerificationFixtures.ARTIFACT,"uuid-1"));
                assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,result.status());
                assertSame(artifact,engine.objectForSemanticId(RuntimeVerificationFixtures.ARTIFACT));
            } finally { Thread.currentThread().setContextClassLoader(previous); }
        }
    }
    public static class ProfileNotifications {
        int loaded;
        @com.google.common.eventbus.Subscribe public void loaded(org.tzi.use.uml.sys.events.ClassInvariantsLoadedEvent event) { loaded++; }
    }
    private static int beliefCount(org.tzi.use.uml.sys.MSystem system) { return system.state().objectsOfClass(system.model().getClass("Belief")).size(); }
    private static Map<String,Object> belief(BridgeEntityId id,String text,boolean authored,boolean duplicated) {
        String source="literal:"+text;
        return Map.of("semanticId",DomainProjection.occurrenceId("runtime-belief",id.canonical(),source),"sourceIdentity",source,
                "literal",text,"predicateIndicator","ready/1","domainAuthored",authored,"authoritativeElsewhere",duplicated);
    }
    private static NativeRuntimeMutationEngine.ApplyResult apply(NativeRuntimeMutationEngine engine,BridgeEntityId id,Map<String,Object> values) {
        return engine.apply(id,null,RuntimeFactKind.AGENT,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,values);
    }
}
