package org.tzi.use.plugins.jacamo.mapping;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActiveBaselineTest {
    @TempDir Path temporary;
    @Test void filesystemAndPackagedSelectionsHaveIdenticalContractsAndFingerprints() {
        var file = new ActiveBaseline().fromCheckout(Path.of("."));
        var packaged = new ActiveBaseline().packaged();
        assertEquals(file.mapping(), packaged.mapping()); assertEquals(file.hashes(), packaged.hashes());
        assertEquals("WORKING_BASELINE", file.mapping().status());
        assertFalse(file.mapping().orderProjections().isEmpty());
        assertEquals(file.mapping(), new MappingLoader().loadCanonical(Path.of(".")));
        assertThrows(UnsupportedOperationException.class, () -> file.hashes().put("tampered", "value"));
    }
    @Test void missingOrStaleV2NeverFallsBackToHistoricalInputs() throws Exception {
        var error = assertThrows(MappingException.class, () -> new ActiveBaseline().fromCheckout(temporary));
        assertEquals("ACTIVE_BASELINE_LOAD_FAILED", error.code());
        Path meta = Files.createDirectories(temporary.resolve("Core/Metamodel/version-2"));
        Path mapping = Files.createDirectories(temporary.resolve("Core/Mapping/version-2"));
        Files.copy(V2EcoreAuditTest.SOURCE, meta.resolve(ActiveBaseline.ECORE));
        Files.copy(V2MappingAuditTest.MAPPING, mapping.resolve(ActiveBaseline.MAPPING));
        Files.copy(V2MappingAuditTest.SCHEMA, mapping.resolve(ActiveBaseline.SCHEMA));
        var before = new ActiveBaseline().fromCheckout(temporary);
        String source = Files.readString(meta.resolve(ActiveBaseline.ECORE));
        Files.writeString(meta.resolve(ActiveBaseline.ECORE), source.replace("name=\"Agent\"", "name=\"ChangedAgent\""));
        error = assertThrows(MappingException.class, () -> new ActiveBaseline().fromCheckout(temporary));
        assertEquals("MAPPING_ECORE_MISMATCH", error.code()); assertTrue(error.getMessage().contains(temporary.toString()));
        assertEquals(before.mapping(), new ActiveBaseline().fromCheckout(Path.of(".")).mapping());
    }
    @Test void syntheticSubclassChangeRequiresReconciliationThenUsesGenericConsumers() throws Exception {
        String source = Files.readString(V2EcoreAuditTest.SOURCE);
        byte[] changed = source.replace("</ecore:EPackage>", "<eClassifiers xsi:type=\"ecore:EClass\" name=\"SyntheticGroup\" eSuperTypes=\"#//Group\"/></ecore:EPackage>")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertNotEquals(source, new String(changed, java.nio.charset.StandardCharsets.UTF_8));
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) V2MappingAuditTest.mapping();
        ((com.fasterxml.jackson.databind.node.ObjectNode) document.path("sourceMetamodel")).put("sha256",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(changed)));
        var parser = new MappingLoader(path -> path.equals(V2EcoreAuditTest.SOURCE) ? changed :
                path.equals(V2MappingAuditTest.MAPPING) ? V2MappingAuditTest.JSON.writeValueAsBytes(document) : Files.readAllBytes(path));
        assertEquals("MAPPING_SOURCE_COVERAGE_MISMATCH", assertThrows(MappingException.class,
                () -> parser.loadWorking(V2MappingAuditTest.MAPPING, V2MappingAuditTest.SCHEMA, V2EcoreAuditTest.SOURCE)).code());
        var classifier = document.path("classMappings").get(0).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) classifier).put("id", "C999").put("source", "agentmetamodel::SyntheticGroup");
        ((com.fasterxml.jackson.databind.node.ObjectNode) classifier.path("target")).put("name", "SyntheticGroup");
        ((com.fasterxml.jackson.databind.node.ArrayNode) document.path("classMappings")).add(classifier);
        ((com.fasterxml.jackson.databind.node.ArrayNode) document.path("inheritanceMappings")).add(V2MappingAuditTest.JSON.readTree(
                "{\"id\":\"I999\",\"source\":\"agentmetamodel::SyntheticGroup->super::Group\",\"sourceKind\":\"eSuperType\",\"dimension\":\"organisation\",\"target\":{\"kind\":\"USE_GENERALIZATION\",\"subclass\":\"SyntheticGroup\",\"superclass\":\"Group\"},\"policy\":\"EXACT_SYNTHETIC_TEST\"}"));
        var mapping = parser.loadWorking(V2MappingAuditTest.MAPPING, V2MappingAuditTest.SCHEMA, V2EcoreAuditTest.SOURCE);
        var plan = new TransformationPlanner().structuralPlan(mapping);
        var errors = new java.io.StringWriter();
        assertNotNull(V2MappingAuditTest.compile(new StructuralUseGenerator().generate("SyntheticMinor", plan), errors), errors.toString());
        assertTrue(plan.classes().stream().anyMatch(c -> c.name().equals("SyntheticGroup") && c.superclasses().equals(java.util.List.of("Group"))));
    }
}
