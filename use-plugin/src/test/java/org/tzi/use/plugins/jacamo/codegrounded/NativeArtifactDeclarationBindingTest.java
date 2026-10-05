package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;

class NativeArtifactDeclarationBindingTest {
    @TempDir Path directory;

    @Test void unavailableColdTypeBindsLateWithoutDuplicateObjectsAndRecompilesExactly() throws Exception {
        var fixture=fixture(); var engine=fixture.engine(); var system=engine.system();
        assertNull(system.model().getClass("LateCounter"));
        assertNull(engine.objectForSemanticId(fixture.declaration()));
        assertTrue(fixture.pipeline().trace().records().stream().anyMatch(r->r.diagnostics().stream().anyMatch(d->d.contains("ARTIFACT_TYPE_UNRESOLVED"))));
        var payload=artifact(fixture.declaration(),fixture.workspace(),"counter","demo.LateCounter","artifact-one");
        assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,payload).status());
        var object=engine.objectForSemanticId(fixture.declaration());
        assertNotNull(object); assertSame(object,engine.objectForSemanticId(payload.get("semanticId").toString()));
        assertEquals(1,system.state().objectsOfClass(system.model().getClass("LateCounter")).size());
        assertEquals(fixture.pipeline().source().snapshot().workspaceDeclarations().size(),system.state().objectsOfClass(system.model().getClass("Workspace")).size());
        assertTrue(object.cls().parents().contains(system.model().getClass("Artifact")));
        assertTrue(system.state().hasLinkBetweenObjects(system.model().getAssociation(DomainProjection.relation("locatedIn","Workspace","Artifact")),
            new org.tzi.use.uml.sys.MObject[]{engine.objectForSemanticId(fixture.workspace()),object}));
        String before=new NativeUseSoilExporter().export(system).commands();
        assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,payload).status());
        assertEquals(before,new NativeUseSoilExporter().export(system).commands());
        assertSame(system,engine.system()); GenericFunctionalRuntimeProjectionTest.assertBindingsComplete(engine);
        var exported=new NativeUseExporter().export(system.model());
        assertEquals(exported.originalStructuralHash(),exported.recompiledStructuralHash());
        var replay=new NativeUseSoilExporter().replay(exported.recompiledModel(),before);
        assertEquals(system.state().numObjects(),replay.state().numObjects());
        assertEquals(system.state().allLinks().size(),replay.state().allLinks().size());
        var saved=engine.savepoint();
        assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,apply(engine,artifact(fixture.declaration(),fixture.workspace(),"counter","demo.LateCounter","artifact-two")).status());
        assertEquals(saved,engine.savepoint()); assertSame(object,engine.objectForSemanticId(fixture.declaration()));
    }

    @Test void unprovedOrMismatchedDeclarationsFailClosedBeforeCreatingClasses() throws Exception {
        var fixture=fixture(); var engine=fixture.engine(); var saved=engine.savepoint(); int classes=engine.system().model().classes().size();
        for(var payload:List.of(artifact("missing-declaration",fixture.workspace(),"counter","demo.LateCounter","artifact-one"),
                artifact(fixture.declaration(),fixture.workspace(),"other-name","demo.LateCounter","artifact-one"),
                artifact(fixture.declaration(),fixture.workspace(),"counter","demo.WrongCounter","artifact-one"))) {
            assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,apply(engine,payload).status());
            assertEquals(saved,engine.savepoint()); assertEquals(classes,engine.system().model().classes().size());
        }
    }

    private Fixture fixture() throws Exception {
        Path source=directory.resolve("late.jcm");
        Files.writeString(source,"""
            mas late_binding {
                workspace declaredSpace {
                    artifact counter: demo.LateCounter
                }
            }
            """);
        var snapshot=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(source),source);
        var pipeline=new CodeGroundedNativePipeline().build(snapshot);
        var engine=new NativeRuntimeMutationEngine(pipeline.state().system(),pipeline.state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry());
        String declaration=snapshot.semanticContract().artifactDeclarations().getFirst().metadata().semanticId();
        String workspace="cartago:workspace:demo:/test-root/declaredSpace:workspace-one";
        var values=Map.<String,Object>ofEntries(Map.entry("normalizedEventKind","UPSERT_CARTAGO_WORKSPACE"),Map.entry("semanticId",workspace),
            Map.entry("environmentSemanticId","cartago:environment:demo"),Map.entry("environmentId","demo"),Map.entry("name","declaredSpace"),
            Map.entry("fullName","/test-root/declaredSpace"),Map.entry("uuid","workspace-one"),
            Map.entry("workspaceDeclarationSemanticId",snapshot.semanticContract().workspaceDeclarations().stream()
                .filter(w->w.name().equals(snapshot.semanticContract().artifactDeclarations().getFirst().workspace())).findFirst().orElseThrow().metadata().semanticId()));
        var applied=engine.apply(new BridgeEntityId("cartago","environment","workspace","demo","/test-root/declaredSpace","workspace-one"),null,
            RuntimeFactKind.WORKSPACE,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,values);
        assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,applied.status(),applied.diagnostic());
        return new Fixture(pipeline,engine,declaration,workspace);
    }

    private Map<String,Object> artifact(String declaration,String workspace,String name,String type,String uuid) {
        return Map.of("normalizedEventKind","UPSERT_CARTAGO_ARTIFACT","semanticId","cartago:artifact:demo:/test-root/declaredSpace:"+uuid,
            "name",name,"uuid",uuid,"artifactTypeSemanticId",DomainProjection.artifactId(type),"artifactTypeJavaClassName",type,
            "workspaceSemanticId",workspace,"creatorAgentSemanticId","","artifactDeclarationSemanticId",declaration,"artifactTypeOrigin","APPLICATION");
    }
    private NativeRuntimeMutationEngine.ApplyResult apply(NativeRuntimeMutationEngine engine,Map<String,Object> values) {
        return engine.apply(new BridgeEntityId("cartago","environment","artifact","/test-root/declaredSpace",values.get("name").toString(),values.get("uuid").toString()),null,
            RuntimeFactKind.ARTIFACT,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,values);
    }
    private record Fixture(CodeGroundedNativePipeline.Result pipeline,NativeRuntimeMutationEngine engine,String declaration,String workspace) { }
}
