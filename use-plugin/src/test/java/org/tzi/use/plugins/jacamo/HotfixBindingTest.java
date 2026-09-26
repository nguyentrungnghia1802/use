package org.tzi.use.plugins.jacamo;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.binding.*;
import org.tzi.use.plugins.jacamo.extraction.*;
import org.tzi.use.plugins.jacamo.semantic.*;
class HotfixBindingTest {
    @TempDir Path temporary;
    @Test void productionImportUsesValidBindingAndRejectsInvalidOrStaleBindings() throws Exception {
        Files.createDirectories(temporary.resolve("src/env/demo"));
        Files.createDirectories(temporary.resolve("src/agt"));
        Path entry = temporary.resolve("app.jcm");
        Files.writeString(entry, "mas app { agent a:a.asl workspace w { artifact x:demo.X() artifact y:demo.Y() } asl-path:src/agt java-path:src/env }");
        Files.writeString(temporary.resolve("src/agt/a.asl"), "+!g <- bid(1).");
        for (String name : List.of("X", "Y")) Files.writeString(temporary.resolve("src/env/demo/" + name + ".java"),
                "package demo; class " + name + " { @OPERATION void bid(int n){} }");
        var importer = new StaticProjectImporter();
        var unresolved = importer.importProject(entry);
        assertFalse(unresolved.success());
        var source = unresolved.model().elements().stream().filter(e -> e.kind() == MetamodelKind.Action).findFirst().orElseThrow();
        var target = unresolved.model().elements().stream().filter(e -> e.kind() == MetamodelKind.Operation).findFirst().orElseThrow();
        {
            assertFalse(importer.importProject(entry).success());
            write(source, target.id().value(), source.provenance().getFirst().sourceHash());
            var resolved = importer.importProject(entry);
            assertTrue(resolved.success(), resolved.diagnostics().toString());
            assertEquals(target.id(), resolved.model().elements().stream().filter(e -> e.id().equals(source.id()))
                    .findFirst().orElseThrow().references().getFirst().targetId());
            write(source, target.id().value() + "-missing", source.provenance().getFirst().sourceHash());
            assertTrue(importer.importProject(entry).diagnostics().stream().anyMatch(d -> d.code().equals("BINDING_STALE")));
            write(source, source.id().value(), source.provenance().getFirst().sourceHash());
            assertTrue(importer.importProject(entry).diagnostics().stream().anyMatch(d -> d.code().equals("BINDING_INVALID")));
            Files.writeString(temporary.resolve("binding.json"), "{broken");
            assertTrue(importer.importProject(entry).diagnostics().stream().anyMatch(d -> d.code().equals("BINDING_INVALID")));
            write(source, target.id().value(), "0".repeat(64));
            assertTrue(importer.importProject(entry).diagnostics().stream().anyMatch(d -> d.code().equals("BINDING_STALE")));
        }
    }
    private void write(SemanticElement source, String target, String hash) {
        new BindingStore().write(temporary.resolve("binding.json"), new BindingFile("1.0.0", List.of(
                BindingEntry.active(source.id().value(), target, "EXPLICIT_BINDING", "test", hash, "test"))));
    }
}
