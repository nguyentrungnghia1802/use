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
        @SuppressWarnings("unchecked") Map<String, Object> versions = (Map<String, Object>) traceTree.get("componentVersions");
        assertEquals(Map.of("plugin", "1.0.1", "use", "7.5.0", "jacamo", "1.3.1", "jason", "3.3.2",
                "cartago", "3.1", "moise", "1.1"), versions);
        List<?> records = (List<?>) traceTree.get("records");
        List<?> sources = (List<?>) traceTree.get("sources");
        @SuppressWarnings("unchecked") Map<String, Object> metrics = (Map<String, Object>) traceTree.get("metrics");
        assertEquals(records.size(), ((Number) metrics.get("recordCount")).intValue());
        assertEquals(false, metrics.get("warningThresholdExceeded"));
        assertTrue(records.stream().map(value -> ((Map<?, ?>) value).get("ruleId")).anyMatch("J01"::equals));
        assertTrue(sources.stream().map(value -> ((Map<?, ?>) value).get("evidenceAuthority"))
                .anyMatch("OFFICIAL_JACAMO_API"::equals));
        assertTrue(sources.stream().map(value -> (List<?>) ((Map<?, ?>) value).get("sourceEvidence"))
                .flatMap(List::stream).anyMatch(value -> ((Map<?, ?>) value).get("sourceUri").equals("project:/helloworld.jcm")));
        first.source().programs().forEach(program -> program.planLibrary().plans().forEach(plan -> {
            var traceRows = first.trace().targetsForSource(plan.metadata().semanticId());
            assertTrue(traceRows.stream().anyMatch(row -> row.targetKind().equals("MObject")));
            assertTrue(traceRows.stream().filter(row -> row.targetKind().equals("MObject") || row.targetKind().equals("MValue"))
                    .allMatch(row -> row.sourceEvidence().equals(plan.metadata().evidence())));
        }));
    }

    @Test
    void traceCoversEveryNativeDeclarationAndMaterializedAttributeValue() throws Exception {
        var result = new CodeGroundedNativePipeline().build(CodeGroundedTestFixtures.helloSnapshot());
        var records = result.trace().records();
        result.model().model().classes().forEach(cls -> {
            assertTrue(records.stream().anyMatch(record -> record.targetKind().equals("MClass")
                    && record.targetIdentity().equals("class:" + cls.name())), cls.name());
            cls.attributes().forEach(attribute -> assertTrue(records.stream().anyMatch(record ->
                    record.targetKind().equals("MAttribute") && record.targetIdentity()
                            .equals("attribute:" + cls.name() + "." + attribute.name())),
                    cls.name() + "." + attribute.name()));
        });
        result.model().model().enumTypes().forEach(type -> assertTrue(records.stream().anyMatch(record ->
                record.targetKind().equals("EnumType") && record.targetIdentity().equals("enum:" + type.name())),
                type.name()));
        result.state().system().state().allObjects().forEach(object -> object.state(result.state().system().state())
                .attributeValueMap().keySet().forEach(attribute -> assertTrue(records.stream().anyMatch(record ->
                        record.targetKind().equals("MValue") && record.targetIdentity()
                                .equals("value:" + object.name() + "." + attribute.name())),
                        object.name() + "." + attribute.name())));
        assertEquals(records.size(), result.trace().metrics().recordCount());
        assertTrue(result.trace().metrics(1).warningThresholdExceeded());
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
