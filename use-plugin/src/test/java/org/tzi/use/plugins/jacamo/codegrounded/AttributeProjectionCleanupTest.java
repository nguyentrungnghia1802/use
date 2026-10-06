package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.NativeObjectBindings;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;

class AttributeProjectionCleanupTest {
    @TempDir Path directory;
    @Test void basesAndAssociationClassesExposeOnlyDomainStateAndRetainExactBindings() throws Exception {
        var org=MoiseDomainProjectionTest.organization();
        var pipeline=new CodeGroundedNativePipeline().build(MoiseDomainProjectionTest.withOrganizations(CodeGroundedTestFixtures.helloSnapshot(),List.of(org)));
        var expected=Map.of("Agent",Set.of("name"),"AgentGoal",Set.of("literal"),"Belief",Set.of("literal"),
                "Organization",Set.of("name"),"Group",Set.of("name"),"Scheme",Set.of("name","arguments"),
                "Workspace",Set.of("name"),"Artifact",Set.of("name"),"Mission",Set.of("id","name","min","max"),
                "OrganizationalGoal",Set.of("id","name","description","arguments","goalType","runtimeState","decompositionOperator","orderInParent","minAgentsToSatisfy","ttf"));
        var inventory=new TreeMap<String,Object>();
        for(var cls:pipeline.model().model().classes()) {
            var attributes=cls.allAttributes().stream().map(a->a.name()).collect(java.util.stream.Collectors.toSet());
            inventory.put(cls.name(),attributes.stream().sorted().toList());
            if(expected.containsKey(cls.name()))assertEquals(expected.get(cls.name()),attributes,cls.name());
            if(cls instanceof org.tzi.use.uml.mm.MAssociationClass)assertTrue(attributes.isEmpty());
            assertTrue(cls.allAttributes().stream().allMatch(a->a.getAnnotation("ProjectionAttribute")!=null));
        }
        var engine=new NativeRuntimeMutationEngine(pipeline.state().system(),pipeline.state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry());
        GenericFunctionalRuntimeProjectionTest.assertBindingsComplete(engine);
        assertEquals(engine.system().state().numObjects(),engine.system().state().allObjects().stream().map(o->engine.metadata(o,"semanticId")).distinct().count());
        assertEquals(pipeline.export().originalStructuralHash(),pipeline.export().recompiledStructuralHash());
        Files.write(Path.of("target/attribute-cleanup-inventory.json"),CanonicalJson.encode(inventory));
    }
    @Test void observableNamesCannotShadowBindingMetadataAndSurviveSnapshotRollbackAndResync() throws Exception {
        try(var p=projector()) {
            var engine=p.mutations();var system=p.system();var state=system.state();var artifact=engine.objectForSemanticId(ARTIFACT);
            var metadata=engine.objectMetadata(artifact);long sequence=0;
            for(String name:List.of("semanticId","uuid","sourceLayer","runtimeIdentity")) {
                var values=new LinkedHashMap<>(property("domain-value"));values.put("name",name);values.put("propertyId",name);
                values.put("semanticId","cartago:property:"+ARTIFACT+":"+name);
                assertTrue(p.apply(event("domain-"+name,++sequence,RuntimeEventKind.CHANGED,RuntimeFactKind.PROPERTY,
                        new BridgeEntityId("cartago","environment","observable-property-snapshot",ARTIFACT,name,"snapshot"),values)));
                assertEquals("'domain-value'",artifact.state(state).attributeValue(name).toString());
                assertEquals(metadata,engine.objectMetadata(artifact));
            }
            var cut=p.coordinator().verificationSnapshot();var frozen=cut.image().objects().get(artifact.name());
            assertEquals(ARTIFACT,frozen.semanticId());assertEquals("uuid-1",frozen.bindingMetadata().get("uuid"));
            assertEquals("'domain-value'",frozen.attributes().get("semanticId"));
            var before=engine.savepoint();var invalid=new LinkedHashMap<>(artifact(ARTIFACT,"uuid-1"));invalid.put("workspaceSemanticId","foreign:workspace");
            var rejected=engine.apply(ID,null,RuntimeFactKind.ARTIFACT,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,invalid);
            assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,rejected.status());assertEquals(before.bindings().toMap(),engine.savepoint().bindings().toMap());
            assertEquals(metadata,engine.objectMetadata(artifact));
            p.applySnapshot(snapshot("authoritative",sequence));
            assertSame(system,p.system());assertSame(state,system.state());
            artifact=engine.objectForSemanticId(ARTIFACT);assertNotNull(artifact);
            assertEquals(metadata,engine.objectMetadata(artifact));
            assertTrue(artifact.state(state).attributeValue("uuid").isUndefined());assertEquals("uuid-1",engine.metadata(artifact,"uuid"));
            assertEquals("'domain-value'",frozen.attributes().get("uuid"));assertEquals("uuid-1",frozen.bindingMetadata().get("uuid"));
            var removed=event("remove",++sequence,RuntimeEventKind.DISPOSED,ID,Map.of("normalizedEventKind","DELETE_CARTAGO_ARTIFACT","semanticId",ARTIFACT));
            assertTrue(p.apply(removed));assertNull(engine.objectForSemanticId(ARTIFACT));assertTrue(engine.objectMetadata(artifact).isEmpty());
        }
    }
    @Test void replayUsesHashedBindingSidecarAndRejectsMissingOrRedirectedMetadata() throws Exception {
        try(var p=projector()) {
            var bindings=p.mutations().savepoint().bindings();var roundtrip=NativeObjectBindings.fromMap(p.system(),bindings.toMap());
            assertEquals(bindings.toMap(),roundtrip.toMap());
            var changed=new LinkedHashMap<>(bindings.toMap());changed.put("aliases",Map.of("wrong:id",p.mutations().objectForSemanticId(ARTIFACT).name()));
            assertThrows(IllegalArgumentException.class,()->NativeObjectBindings.fromMap(p.system(),changed));
            var path=directory.resolve("bundle");var replay=new NativeRuntimeReplay();replay.exportBundle(p,"",path);
            assertTrue(replay.replay(path).complete());
            Files.writeString(path.resolve("bindings.json"),"{}");assertFalse(replay.replay(path).complete());
        }
    }
    @Test void foreignFunctionalContextIsRejectedUsingInternalBindingsWithoutTechnicalOcl() throws Exception {
        var org=MoiseDomainProjectionTest.organization();var schemes=org.functionalSpecification().schemes();
        var pipeline=new CodeGroundedNativePipeline().build(MoiseDomainProjectionTest.withOrganizations(CodeGroundedTestFixtures.helloSnapshot(),List.of(org)));
        var index=new NativeObjectBindings(pipeline.state().semanticObjectIndex());
        var mission=schemes.getFirst().missions().getFirst();var foreignGoal=schemes.get(1).goals().getFirst();
        var staticId=new BridgeEntityId("moise","organisation","test-binding","fixture",mission.missionId(),"spec");
        var runtime=new BridgeEntityId("moise","organisation","test-runtime","fixture",mission.missionId(),"1");
        index.put(staticId.canonical(),index.get(mission.metadata().semanticId()));
        var engine=new NativeRuntimeMutationEngine(pipeline.state().system(),index,new CodeGroundedRuntimeRuleRegistry());
        var before=engine.savepoint();var state=engine.system().state();
        var result=engine.apply(runtime,new BridgeRelationId("runtime-model-binding",List.of(runtime,staticId),"exact-fixture","1"),
                RuntimeFactKind.RELATION_STATE,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,
                Map.of("normalizedEventKind","INSERT_LINK","association",DomainProjection.relation("missionGoals","Mission","OrganizationalGoal"),
                        "participantSemanticIds",List.of(mission.metadata().semanticId(),foreignGoal.metadata().semanticId())));
        assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,result.status());assertTrue(result.diagnostic().contains("MOISE_FUNCTIONAL_BINDING_CONTEXT_MISMATCH"));
        assertEquals(before.bindings().toMap(),engine.savepoint().bindings().toMap());assertSame(state,engine.system().state());
    }
    @Test void anExistingArtifactIncarnationCannotBeRedirectedToAnotherName() throws Exception {
        try(var p=projector()) {
            var engine=p.mutations();var object=engine.objectForSemanticId(ARTIFACT);var before=engine.savepoint();
            var payload=new LinkedHashMap<>(artifact(ARTIFACT,"uuid-1"));payload.put("name","anotherArtifact");
            var claimed=new BridgeEntityId("cartago","environment","artifact","/main","anotherArtifact","uuid-1");
            var result=engine.apply(claimed,null,RuntimeFactKind.ARTIFACT,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,payload);
            assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,result.status());assertTrue(result.diagnostic().contains("INCARNATION_REDIRECT:name"));
            assertSame(object,engine.objectForSemanticId(ARTIFACT));assertEquals(before.bindings().toMap(),engine.savepoint().bindings().toMap());
        }
    }
}
