package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseExporter;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

class NativeUseSoilExporterTest {
    @Test
    void helloNativeStateExportsAndReplaysWithoutEvidenceObjects() throws Exception {
        var pipeline = CodeGroundedTestFixtures.helloPipeline();
        NativeUseSoilExporter exporter = new NativeUseSoilExporter();
        NativeUseSoilExporter.Result exported = exporter.export(pipeline.state().system());

        assertEquals(pipeline.state().system().state().numObjects(), exported.objectCount());
        assertTrue(exported.attributeAssignmentCount() > 0);
        assertTrue(exported.linkCount() > 0);
        assertTrue(exported.commands().contains("!new "));
        assertTrue(exported.commands().contains("!insert ("));
        assertFalse(exported.commands().contains("ExactBindingEvidence"));
        assertFalse(exported.commands().contains("ArtifactInfo"));

        MSystem replayed = exporter.replay(pipeline.model().model(), exported.commands());
        assertEquivalentState(stateSignature(pipeline.state().system()), stateSignature(replayed));
    }

    @Test
    void separateUseAndCmdArtifactsRoundTripWithOfficialCompilers(@TempDir Path directory) throws Exception {
        var pipeline = CodeGroundedTestFixtures.helloPipeline();
        Path useFile = directory.resolve("hello.use");
        Path commandFile = directory.resolve("hello.cmd");
        NativeUseExporter.Result model = new NativeUseExporter().export(pipeline.model().model(), useFile);
        NativeUseSoilExporter.Result state = new NativeUseSoilExporter().export(
                pipeline.state().system(), commandFile);

        assertTrue(Files.isRegularFile(useFile));
        assertTrue(Files.isRegularFile(commandFile));
        assertEquals(model.useText(), Files.readString(useFile));
        assertEquals(state.commands(), Files.readString(commandFile));
        MSystem replayed = new NativeUseSoilExporter().replay(model.recompiledModel(), state.commands());
        assertEquivalentState(stateSignature(pipeline.state().system()), stateSignature(replayed));
    }

    private static List<String> stateSignature(MSystem system) {
        List<String> result = new ArrayList<>();
        system.state().allObjects().stream().sorted(Comparator.comparing(MObject::name)).forEach(object -> {
            object.cls().allAttributes().stream().sorted(Comparator.comparing(attribute -> attribute.name()))
                    .forEach(attribute -> result.add("A|" + object.name() + "|" + attribute.name() + "|"
                            + object.state(system.state()).attributeValue(attribute)));
            result.add(0, "O|" + object.cls().name() + "|" + object.name());
        });
        system.state().allLinks().stream()
                .sorted(Comparator.comparing((MLink link) -> link.association().name())
                        .thenComparing(link -> link.linkedObjects().stream().map(MObject::name).toList().toString()))
                .forEach(link -> result.add("L|" + link.association().name() + "|"
                        + link.linkedObjects().stream().map(MObject::name).toList()
                        + "|" + link.getQualifier().stream().flatMap(List::stream).toList()));
        return result;
    }

    private static void assertEquivalentState(List<String> expected, List<String> actual) {
        assertEquals(expected.size(), actual.size(), "state row count");
        for (int index = 0; index < expected.size(); index++)
            assertEquals(expected.get(index), actual.get(index), "state row " + index);
    }
}
