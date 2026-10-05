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

    @Test void officialRelativeAndHierarchicalFileUrisResolveToTheSameExactOs() {
        Path root=hello().getParent(), source=root.resolve("src/org/o1.xml");
        assertEquals(source,NativeSemanticAdapter.exactOsPath(root,"file:src/org/o1.xml"));
        assertEquals(source,NativeSemanticAdapter.exactOsPath(root,source.toUri().toString()));
        assertEquals(source,NativeSemanticAdapter.exactOsPath(root,"o1.xml"));
    }

    @Test void officialHelloSnapshotBuildsNativeIrAndUseModelWithoutLegacyParser() throws Exception {
        Path jcm=hello(); var snapshot=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var adapted=new NativeSemanticAdapter().adapt(snapshot,jcm.getParent(),"helloworld"); var semantic=adapted.model();
        assertEquals(snapshot.modelRevision(),adapted.modelRevision());
        long rawRoleTuples=snapshot.crossDimensionalRelations().stream().filter(f->f.factKind().equals("role-tuple")).count();
        assertTrue(rawRoleTuples>0);
        assertTrue(snapshot.crossDimensionalRelations().stream().filter(f->f.factKind().equals("role-tuple"))
                .allMatch(f->f.references().isEmpty()),"official J09 contract must remain raw");
        assertEquals(rawRoleTuples,semantic.elements().stream().filter(e->e.kind()==MetamodelKind.Agent)
                .flatMap(e->e.references().stream()).filter(r->r.feature().equals("roles")&&r.targetId()!=null).count(),
                "legacy V2 compatibility must resolve only exact canonical tuple endpoints");
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

    @Test void historicalTestOnlyAdapterStopsAtDtoToSemanticModelBoundary() throws Exception {
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/java/org/tzi/use/plugins/jacamo/bridge/NativeSemanticAdapter.java")));
        String source=java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/test/java/org/tzi/use/plugins/jacamo/bridge/NativeSemanticAdapter.java"));
        assertTrue(source.contains("JaCaMoSemanticModel"));
        assertTrue(source.contains("ModelSnapshot"));
        for(String forbidden:java.util.List.of("org.tzi.use.api.","org.tzi.use.uml.","org.tzi.use.parser.",
                "UseModelApi","UseSystemApi","MModel","MSystem","ActiveBaseline","MappingLoader",
                "TransformationPlanner","StructuralUseGenerator","TextBackend","DirectUseBackend"))
            assertFalse(source.contains(forbidden),forbidden);
    }
}
