package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.mapping.ActiveBaseline;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;

class NativeSemanticAdapterTest {
    private static Path hello(){return Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().normalize();}

    @Test void officialHelloSnapshotBuildsNativeIrAndUseModelWithoutLegacyParser() throws Exception {
        Path jcm=hello(); var snapshot=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var adapted=new NativeSemanticAdapter().adapt(snapshot,jcm.getParent(),"helloworld"); var semantic=adapted.model();
        assertEquals(snapshot.modelRevision(),adapted.modelRevision());
        assertTrue(semantic.elements().stream().anyMatch(e->e.kind()==MetamodelKind.Agent));
        assertTrue(semantic.elements().stream().anyMatch(e->e.kind()==MetamodelKind.Plan));
        assertTrue(semantic.elements().stream().anyMatch(e->e.kind()==MetamodelKind.Norm));
        assertFalse(adapted.groupRoleCardinalities().isEmpty());
        var baseline=new ActiveBaseline().packaged(); var structure=new TransformationPlanner().plan(semantic,baseline.mapping());
        var instances=new InstancePlanner().plan(semantic,baseline.mapping(),structure);
        var text=new TextBackend().generate("helloworld_bridge",structure,instances);
        var direct=new DirectUseBackend().materialize(text,instances);
        assertNotNull(direct.system().model().getClass("Agent"));
        assertTrue(direct.structureValid(),direct.validationOutput());
    }

    @Test void useBridgeProductionSourcesDoNotImportPlatformRuntimePackages() throws Exception {
        String sources=java.nio.file.Files.walk(Path.of("src/main/java/org/tzi/use/plugins/jacamo/bridge"))
                .filter(java.nio.file.Files::isRegularFile).map(path->{try{return java.nio.file.Files.readString(path);}catch(Exception e){throw new RuntimeException(e);}}).reduce("",String::concat);
        for(String forbidden:java.util.List.of("import jacamo.","import jason.","import cartago.","import moise.","import npl."))assertFalse(sources.contains(forbidden),forbidden);
    }
}
