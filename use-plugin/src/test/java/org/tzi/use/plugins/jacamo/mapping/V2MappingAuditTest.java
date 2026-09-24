package org.tzi.use.plugins.jacamo.mapping;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.eclipse.emf.ecore.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.*;

/** Independent contract audit fixtures, not the production V2 transformation. */
public class V2MappingAuditTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Path MAPPING = Path.of("Core/Mapping/version-2/jacamo-use-mapping-v2.json");
    static final Path SCHEMA = Path.of("Core/Mapping/version-2/jacamo-use-mapping-v2.schema.json");

    static JsonNode mapping() throws Exception { return JSON.readTree(MAPPING.toFile()); }
    static String hash(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }

    static void validate(JsonNode root) throws Exception {
        var schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(Files.readString(SCHEMA), InputFormat.JSON);
        assertTrue(schema.validate(root.toString(), InputFormat.JSON).isEmpty(), "MAPPING_SCHEMA_INVALID");
        assertEquals(hash(V2EcoreAuditTest.SOURCE), root.path("sourceMetamodel").path("sha256").asText(), "MAPPING_ECORE_MISMATCH");
        var pkg = V2EcoreAuditTest.load(Files.readString(V2EcoreAuditTest.SOURCE));
        assertEquals(pkg.getName(), root.path("sourceMetamodel").path("packageName").asText());
        assertEquals(pkg.getNsURI(), root.path("sourceMetamodel").path("nsURI").asText());
        var expected = new TreeMap<String, EObject>();
        for (var type : pkg.getEClassifiers()) {
            expected.put(pkg.getName() + "::" + type.getName(), type);
            if (type instanceof EClass cls)
                for (var feature : cls.getEStructuralFeatures()) expected.put(pkg.getName() + "::" + type.getName() + "#" + feature.getName(), feature);
        }
        var actual = new TreeSet<String>();
        var ids = new HashMap<String, JsonNode>();
        for (String section : List.of("enumMappings", "classMappings", "attributeMappings", "referenceMappings", "inheritanceMappings")) {
            for (var rule : root.path(section)) {
                String source = rule.path("source").asText();
                assertTrue(actual.add(source), "Duplicate source " + source);
                assertNull(ids.put(rule.path("id").asText(), rule), "Duplicate rule ID");
                EObject element = expected.get(source);
                assertNotNull(element, "Orphan source " + source);
                var target = rule.path("target");
                if (element instanceof EClass cls) {
                    assertEquals(cls.isAbstract(), rule.path("sourceAbstract").asBoolean());
                    assertEquals(cls.isAbstract(), target.path("abstract").asBoolean());
                } else if (element instanceof EEnum en) {
                    assertEquals(en.getELiterals().size(), rule.path("sourceLiterals").size());
                    for (int i = 0; i < en.getELiterals().size(); i++) {
                        var literal = en.getELiterals().get(i); var supplied = rule.path("sourceLiterals").get(i);
                        assertEquals(literal.getName(), supplied.path("name").asText());
                        assertEquals(literal.getLiteral(), supplied.path("literal").asText());
                        assertEquals(literal.getValue(), supplied.path("value").asInt());
                        assertEquals(literal.getName(), target.path("literals").get(i).asText());
                    }
                } else if (element instanceof EStructuralFeature feature) {
                    assertEquals(feature.getEContainingClass().getName(), rule.path("sourceOwner").asText());
                    assertEquals(feature.getName(), rule.path("sourceName").asText());
                    assertEquals(feature.getLowerBound(), rule.path("sourceMultiplicity").path("lower").asInt());
                    assertEquals(feature.getUpperBound(), rule.path("sourceMultiplicity").path("upper").asInt());
                    if (feature instanceof EAttribute attribute) {
                        assertEquals((attribute.getEType() instanceof EEnum ? "EEnum:" : "") + attribute.getEType().getName(), rule.path("sourceType").asText());
                        assertEquals(attribute.getLowerBound() > 0, rule.path("sourceRequired").asBoolean());
                        assertEquals(attribute.getDefaultValueLiteral(), rule.has("sourceExplicitDefaultLiteral") ? rule.path("sourceExplicitDefaultLiteral").asText() : null);
                        String type = attribute.getEType() instanceof EEnum ? attribute.getEType().getName() :
                                Map.of("EString", "String", "EInt", "Integer", "EBoolean", "Boolean").get(attribute.getEType().getName());
                        assertNotNull(type, "Unsupported datatype must be explicit");
                        assertEquals(type, target.path("type").asText(), "DATATYPE_MISMATCH");
                        String absence = attribute.getDefaultValueLiteral() != null ? "MATERIALIZE_EXPLICIT_ECORE_DEFAULT_WHEN_SOURCE_UNSET" :
                                attribute.getLowerBound() > 0 ? "REQUIRE_RESOLVED_SOURCE_VALUE" : "LEAVE_UNDEFINED_IF_SOURCE_UNSET";
                        assertEquals(absence, rule.path("absencePolicy").asText());
                        if (attribute.getDefaultValueLiteral() != null) {
                            String literal = attribute.getDefaultValueLiteral();
                            String expression = attribute.getEType() instanceof EEnum en ? en.getName() + "::" + en.getEEnumLiteralByLiteral(literal).getName() :
                                    type.equals("String") ? "'" + literal.replace("\\", "\\\\").replace("'", "\\'") + "'" : literal;
                            assertEquals(expression, rule.path("targetDefaultExpression").asText(), "DEFAULT_EXPRESSION_MISMATCH");
                        }
                    } else if (feature instanceof EReference ref) {
                        assertEquals(ref.getEType().getName(), rule.path("sourceTarget").asText());
                        assertEquals(ref.isContainment(), rule.path("sourceContainment").asBoolean());
                        assertEquals(ref.isOrdered(), rule.path("sourceOrdered").asBoolean());
                        assertEquals(ref.isUnique(), rule.path("sourceUnique").asBoolean());
                        if (ref.getEOpposite() != null) assertEquals(pkg.getName() + "::" + ref.getEOpposite().getEContainingClass().getName() + "#" + ref.getEOpposite().getName(), rule.path("sourceEOpposite").asText());
                        if (!target.path("kind").asText().endsWith("ALIAS")) {
                            assertEquals(ref.isContainment() ? "USE_COMPOSITION" : "USE_ASSOCIATION", target.path("kind").asText());
                            assertEquals(ref.getEContainingClass().getName(), target.path("firstEnd").path("class").asText());
                            assertEquals(ref.getEType().getName(), target.path("secondEnd").path("class").asText());
                            assertEquals(bounds(ref), target.path("secondEnd").path("multiplicity").asText());
                            assertEquals(ref.isMany() && ref.isOrdered(), target.path("secondEnd").path("ordered").asBoolean());
                            if (ref.isContainment()) assertEquals("firstEnd", target.path("diamondEnd").asText());
                            if (ref.getEOpposite() != null) {
                                assertEquals(bounds(ref.getEOpposite()), target.path("firstEnd").path("multiplicity").asText());
                                assertEquals(ref.getEOpposite().isMany() && ref.getEOpposite().isOrdered(), target.path("firstEnd").path("ordered").asBoolean());
                            }
                        }
                    }
                }
            }
        }
        assertEquals(expected.keySet(), actual, "Exact source coverage");
        for (var rule : root.path("referenceMappings")) if (rule.path("target").path("kind").asText().endsWith("ALIAS")) {
            var target = rule.path("target"); var canonical = ids.get(target.path("aliasOf").asText());
            assertNotNull(canonical); assertFalse(target.path("emit").asBoolean(true));
            assertEquals(canonical.path("target").path("name").asText(), target.path("associationName").asText(), "ALIAS_TARGET_MISMATCH");
            assertEquals(canonical.path("sourceEOpposite").asText(), rule.path("source").asText());
        }
        for (var projection : root.path("verificationProjections")) {
            assertNull(ids.put(projection.path("id").asText(), projection), "Duplicate projection ID");
            for (var source : projection.path("metamodelContract").path("sourceElements")) assertTrue(expected.containsKey(source.asText()), source.asText());
            for (var binding : projection.path("metamodelContract").path("structuralBindings")) assertTrue(ids.containsKey(binding.asText()), binding.asText());
        }
    }

    static String bounds(ETypedElement f) {
        if (f.getUpperBound() == -1) return f.getLowerBound() == 0 ? "*" : f.getLowerBound() + "..*";
        return f.getLowerBound() == f.getUpperBound() ? "" + f.getLowerBound() : f.getLowerBound() + ".." + f.getUpperBound();
    }

    static String fixture(JsonNode root) {
        var out = new StringBuilder("model V2ContractAudit\n\n");
        for (var entry : root.path("enumMappings")) {
            var names = new ArrayList<String>(); entry.path("target").path("literals").forEach(l -> names.add(l.asText()));
            out.append("enum ").append(entry.path("target").path("name").asText()).append(" { ").append(String.join(", ", names)).append(" }\n");
        }
        for (var entry : root.path("classMappings")) {
            var target = entry.path("target"); String name = target.path("name").asText();
            if (target.path("abstract").asBoolean()) out.append("abstract ");
            out.append("class ").append(name).append("\nattributes\n");
            for (var attribute : root.path("attributeMappings")) if (attribute.path("target").path("owner").asText().equals(name))
                out.append("  ").append(attribute.path("target").path("name").asText()).append(" : ").append(attribute.path("target").path("type").asText()).append('\n');
            out.append("end\n\n");
        }
        for (var entry : root.path("referenceMappings")) {
            var target = entry.path("target"); if (target.path("kind").asText().endsWith("ALIAS")) continue;
            out.append(target.path("kind").asText().equals("USE_COMPOSITION") ? "composition " : "association ")
                    .append(target.path("name").asText()).append(" between\n");
            for (String end : List.of("firstEnd", "secondEnd")) {
                var e = target.path(end); out.append("  ").append(e.path("class").asText()).append('[').append(e.path("multiplicity").asText())
                        .append("] role ").append(e.path("role").asText());
                if (e.path("ordered").asBoolean()) out.append(" ordered");
                out.append('\n');
            }
            out.append("end\n\n");
        }
        return out.toString().stripTrailing() + "\n";
    }

    static MModel compile(String text, StringWriter errors) {
        return USECompiler.compileSpecification(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                "mapping-v2.use", URI.create("memory:/mapping-v2.use"), new PrintWriter(errors), new ModelFactory());
    }

    @Test void suppliedMappingHasExactSourcesAndCompilesInUse() throws Exception {
        var root = mapping(); validate(root); String text = fixture(root);
        var errors = new StringWriter(); var model = compile(text, errors);
        assertNotNull(model, errors.toString()); assertEquals(root.path("classMappings").size(), model.classes().size());
        assertEquals(root.path("enumMappings").size(), model.enumTypes().size());
        long emitted = java.util.stream.StreamSupport.stream(root.path("referenceMappings").spliterator(), false)
                .filter(r -> !r.path("target").path("kind").asText().endsWith("ALIAS")).count();
        assertEquals(emitted, model.associations().size()); assertEquals(text, fixture(root));
        Path output = Files.createDirectories(Path.of("target/phase31-mapping-audit"));
        Files.writeString(output.resolve("mapping-v2.use"), text);
        Files.writeString(output.resolve("mapping-v2-use-compile.log"), "USECompiler: PASS\n" + errors);
        Files.writeString(output.resolve("mapping-v2-validation.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "schemaAndSourceCoverage", "PASS", "structuralCompile", "PASS", "mappingSha256", hash(MAPPING),
                "schemaSha256", hash(SCHEMA), "ecoreSha256", hash(V2EcoreAuditTest.SOURCE),
                "status", "WORKING_BASELINE", "instanceFidelity", "SEPARATE_GATE_NOT_PROVEN_BY_COMPILATION")) + "\n");
    }

    @Test void malformedSchemaFingerprintSourceAndTypeMutationsAreRejected() throws Exception {
        validate(mapping());
        var expected = Map.of("unknownField", "MAPPING_SCHEMA_INVALID", "unknownNested", "MAPPING_SCHEMA_INVALID", "fingerprint", "MAPPING_ECORE_MISMATCH",
                "source", "Orphan source", "duplicateId", "Duplicate rule ID", "type", "DATATYPE_MISMATCH", "oppositeAlias", "ALIAS_TARGET_MISMATCH", "default", "DEFAULT_EXPRESSION_MISMATCH");
        for (String mutation : List.of("unknownField", "unknownNested", "fingerprint", "source", "duplicateId", "type", "oppositeAlias", "default")) {
            var root = (ObjectNode) mapping();
            switch (mutation) {
                case "unknownField" -> root.put("surprise", true);
                case "unknownNested" -> ((ObjectNode) root.path("classMappings").get(0).path("target")).put("surprise", true);
                case "fingerprint" -> ((ObjectNode) root.path("sourceMetamodel")).put("sha256", hash(Path.of("Core/Metamodel/version-1/JaCaMo-Metamodel.ecore")));
                case "source" -> ((ObjectNode) root.path("classMappings").get(0)).put("source", "agentmetamodel::Missing");
                case "duplicateId" -> ((ObjectNode) root.path("classMappings").get(1)).put("id", root.path("classMappings").get(0).path("id").asText());
                case "type" -> ((ObjectNode) root.path("attributeMappings").get(0).path("target")).put("type", "Boolean");
                case "default" -> {
                    for (var attribute : root.path("attributeMappings")) if (attribute.has("targetDefaultExpression")) {
                        ((ObjectNode) attribute).put("targetDefaultExpression", "'invented'"); break;
                    }
                }
                case "oppositeAlias" -> {
                    for (var r : root.path("referenceMappings")) if (r.path("target").has("aliasOf")) { ((ObjectNode) r.path("target")).put("aliasOf", "R001"); break; }
                }
            }
            var failure = assertThrows(AssertionError.class, () -> validate(root), mutation);
            assertTrue(failure.getMessage().contains(expected.get(mutation)), mutation + ": " + failure.getMessage());
        }
    }

    @Test void compileRejectsUnknownTypesAndReservedRoles() throws Exception {
        String text = fixture(mapping());
        assertNull(compile(text.replace("name : String", "name : MissingType"), new StringWriter()));
        assertNull(compile(text.replace("role ecore_Artifact_operations", "role operations"), new StringWriter()));
    }

    @Test void conditionalProjectionFixtureCompilesOnlyWithExplicitTypedFacts() throws Exception {
        // Explicit test-owned signature/type evidence, not inferred from name/arity.
        String projection = "\nclass AuditArtifact < Artifact\nattributes\n  sampleValue : Integer\noperations\n  sampleOp(value : Integer) : Boolean\nend\n";
        var errors = new StringWriter(); assertNotNull(compile(fixture(mapping()) + projection, errors), errors.toString());
    }
}
