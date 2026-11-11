package org.jacamo.bridge.adapter;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.BridgeEntityId;
import static org.junit.jupiter.api.Assertions.*;

class ExplicitMainWorkspaceBindingTest {
    @Test void explicitMainIsAChildDistinctFromTheImplicitPlatformRoot(@TempDir Path directory) throws Exception {
        var jcm=directory.resolve("explicit.jcm");Files.writeString(jcm,"mas explicit_main {\n workspace main {\n artifact console: cartago.tools.Console\n }\n}\n");
        var model=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var declarations=model.semanticContract().workspaceDeclarations().stream().filter(w->w.name().equals("main")).toList();
        assertEquals(2,declarations.size(),"JaCaMo Cartago.start creates /main/main even when the CArtAgO root is /main");
        String rootId=declarations.stream().map(w->w.metadata().semanticId()).filter(id->BridgeEntityId.parse(id).authority().equals("cartago")).findFirst().orElseThrow();
        String childId=declarations.stream().map(w->w.metadata().semanticId()).filter(id->BridgeEntityId.parse(id).authority().equals("jacamo")).findFirst().orElseThrow();
        assertNotEquals(rootId,childId);
        var environment=cartago.CartagoEnvironment.getInstance();environment.init();var root=environment.getRootWSP();var child=root.getWorkspace().createWorkspace("main");
        BridgeRuntimeRegistry.configureDeclarations(model.semanticContract());
        try(var adapter=new CartagoSnapshotSource(environment,List.of(),"explicit-main-test")) {
            adapter.attach(e->{});var facts=adapter.capture();
            assertEquals(rootId,facts.stream().filter(f->root.getId().getUUID().toString().equals(f.values().get("uuid"))).findFirst().orElseThrow().values().get("workspaceDeclarationSemanticId"));
            assertEquals(childId,facts.stream().filter(f->child.getId().getUUID().toString().equals(f.values().get("uuid"))).findFirst().orElseThrow().values().get("workspaceDeclarationSemanticId"));
            String artifactId=model.semanticContract().artifactDeclarations().getFirst().metadata().semanticId();
            var artifact=facts.stream().filter(f->artifactId.equals(f.values().get("artifactDeclarationSemanticId"))).findFirst().orElseThrow();
            assertEquals(child.getWorkspace().getArtifact("console").getId().toString(),artifact.values().get("uuid"));
            assertFalse(facts.stream().anyMatch(f->artifactId.equals(f.values().get("artifactDeclarationSemanticId"))&&root.getWorkspace().getArtifact("console").getId().toString().equals(f.values().get("uuid"))));
        }finally{BridgeRuntimeRegistry.clearAgents();environment.shutdown();}
    }
}
