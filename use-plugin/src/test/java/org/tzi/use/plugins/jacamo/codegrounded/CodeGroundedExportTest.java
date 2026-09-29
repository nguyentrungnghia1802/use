package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceExporter;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseStateExporter;

class CodeGroundedExportTest {
    @Test
    void nativeStateAndTraceExportsAreDeterministicAndSelfDescribing() throws Exception {
        var first = new CodeGroundedNativePipeline().build(CodeGroundedTestFixtures.helloSnapshot());
        var second = new CodeGroundedNativePipeline().build(CodeGroundedTestFixtures.helloSnapshot());

        NativeUseStateExporter stateExporter = new NativeUseStateExporter();
        String firstState = stateExporter.export(first.state().system(), first.export().originalStructuralHash());
        String secondState = stateExporter.export(second.state().system(), second.export().originalStructuralHash());
        assertEquals(firstState, secondState);
        Map<String, Object> stateTree = CanonicalJson.object(CanonicalJson.decode(firstState.getBytes(StandardCharsets.UTF_8)));
        assertEquals(NativeUseStateExporter.SCHEMA_VERSION, stateTree.get("schemaVersion"));
        assertEquals(first.export().originalStructuralHash(), stateTree.get("structuralHash"));
        assertTrue(((List<?>) stateTree.get("objects")).size() > 0);

        CodeGroundedTraceExporter traceExporter = new CodeGroundedTraceExporter();
        String firstTrace = traceExporter.export(first.trace());
        String secondTrace = traceExporter.export(second.trace());
        assertEquals(firstTrace, secondTrace);
        Map<String, Object> traceTree = CanonicalJson.object(CanonicalJson.decode(firstTrace.getBytes(StandardCharsets.UTF_8)));
        assertEquals(CodeGroundedTraceExporter.SCHEMA_VERSION, traceTree.get("schemaVersion"));
        List<?> records = (List<?>) traceTree.get("records");
        assertTrue(records.stream().map(value -> ((Map<?, ?>) value).get("ruleId")).anyMatch("J01"::equals));
        assertTrue(records.stream().map(value -> ((Map<?, ?>) value).get("evidenceAuthority"))
                .anyMatch("OFFICIAL_JACAMO_API"::equals));
    }

    @Test
    void exportersWriteOnlyTheirDeclaredJsonArtifacts() throws Exception {
        var result = new CodeGroundedNativePipeline().build(CodeGroundedTestFixtures.helloSnapshot());
        Path directory = Path.of("target", "code-grounded-export-test");
        Path state = directory.resolve("state.json");
        Path trace = directory.resolve("trace.json");
        new NativeUseStateExporter().write(result.state().system(), result.export().originalStructuralHash(), state);
        new CodeGroundedTraceExporter().write(result.trace(), trace);
        assertEquals(Files.readString(state), new NativeUseStateExporter().export(result.state().system(),
                result.export().originalStructuralHash()));
        assertEquals(Files.readString(trace), new CodeGroundedTraceExporter().export(result.trace()));
        assertThrows(IllegalArgumentException.class,
                () -> new NativeUseStateExporter().write(result.state().system(), directory.resolve("state.use")));
        assertThrows(IllegalArgumentException.class,
                () -> new CodeGroundedTraceExporter().write(result.trace(), directory.resolve("trace.use")));
    }
}
