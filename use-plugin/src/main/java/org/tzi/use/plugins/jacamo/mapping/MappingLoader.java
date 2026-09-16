package org.tzi.use.plugins.jacamo.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Loads and rejects any mapping that is not compatible with the frozen Ecore baseline. */
public final class MappingLoader {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ByteReader reader;

    public MappingLoader() { this(Files::readAllBytes); }

    MappingLoader(ByteReader reader) { this.reader = reader; }

    @FunctionalInterface
    interface ByteReader {
        byte[] read(Path path) throws IOException;
    }

    public MappingModel loadCanonical(Path checkout) {
        Path module = Files.isDirectory(checkout.resolve("Core")) ? checkout : checkout.resolve("use-plugin");
        return load(module.resolve("Core/Mapping/jacamo-use-mapping-v1.json"),
                module.resolve("Core/Mapping/jacamo-use-mapping.schema.json"),
                module.resolve("Core/Metamodel/JaCaMo-Metamodel.ecore"),
                module.resolve("Core/Mapping/freeze-manifest.json"));
    }

    public MappingModel load(Path mappingPath, Path schemaPath, Path ecorePath, Path freezePath) {
        try {
            // Own each snapshot when its validation stage begins; never reopen an input.
            byte[] mappingBytes = reader.read(mappingPath).clone();
            byte[] schemaBytes = reader.read(schemaPath).clone();
            String mappingText = new String(mappingBytes, StandardCharsets.UTF_8);
            String schemaText = new String(schemaBytes, StandardCharsets.UTF_8);
            var schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schemaText, InputFormat.JSON);
            var errors = schema.validate(mappingText, InputFormat.JSON);
            if (!errors.isEmpty()) {
                throw new MappingException("MAPPING_SCHEMA_INVALID", errors.toString());
            }
            JsonNode root = JSON.readTree(mappingText);
            byte[] freezeBytes = reader.read(freezePath).clone();
            JsonNode manifest = JSON.readTree(freezeBytes);
            byte[] ecoreBytes = reader.read(ecorePath).clone();
            validateFingerprint(root, ecoreBytes, manifest);
            EcoreKeys keys = ecoreKeys(ecoreBytes);
            validateSources(root, keys);
            validateMappingFingerprint(mappingBytes, manifest);
            return parse(root);
        } catch (MappingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MappingException("MAPPING_LOAD_FAILED", "Cannot load mapping: " + mappingPath, exception);
        }
    }

    private void validateMappingFingerprint(byte[] mapping, JsonNode manifest) throws Exception {
        String expected = manifest.path("hashes").path("mapping/jacamo-use-mapping-v1.json").asText();
        String actual = sha256(mapping);
        if (expected.isBlank() || !expected.equals(actual)) {
            throw new MappingException("MAPPING_HASH_MISMATCH",
                    "Frozen mapping fingerprint " + expected + " does not match " + actual
                            + ". Restore the frozen mapping or rerun the full mapping audit before reconciling the manifest.");
        }
    }

    private void validateFingerprint(JsonNode mapping, byte[] ecore, JsonNode manifest) throws Exception {
        String expected = manifest.path("hashes").path("Core/JaCaMo-Metamodel.ecore").asText();
        String actual = sha256(ecore);
        if (expected.isBlank() || !expected.equals(actual)) {
            throw new MappingException("MAPPING_ECORE_MISMATCH",
                    "Frozen Ecore fingerprint " + expected + " does not match " + actual);
        }
        String contractFingerprint = mapping.path("sourceMetamodel").path("sha256").asText();
        if (!contractFingerprint.isBlank() && !contractFingerprint.equals(actual)) {
            throw new MappingException("MAPPING_ECORE_MISMATCH", "Mapping embeds a stale Ecore fingerprint");
        }
    }

    private EcoreKeys ecoreKeys(byte[] ecore) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(ecore));
        Set<String> classes = new LinkedHashSet<>();
        Set<String> attributes = new LinkedHashSet<>();
        Set<String> references = new LinkedHashSet<>();
        Set<String> inheritance = new LinkedHashSet<>();
        var classifiers = document.getElementsByTagName("eClassifiers");
        for (int i = 0; i < classifiers.getLength(); i++) {
            Element classifier = (Element) classifiers.item(i);
            if (!classifier.getAttribute("xsi:type").endsWith("EClass")) continue;
            String owner = classifier.getAttribute("name");
            classes.add("dSML4JaCaMo::" + owner);
            for (String supertype : classifier.getAttribute("eSuperTypes").trim().split("\\s+")) {
                if (!supertype.isBlank()) inheritance.add("dSML4JaCaMo::" + owner + "->super::" + supertype.substring(supertype.lastIndexOf('/') + 1));
            }
            var children = classifier.getElementsByTagName("eStructuralFeatures");
            for (int j = 0; j < children.getLength(); j++) {
                Element feature = (Element) children.item(j);
                String key = "dSML4JaCaMo::" + owner + "#" + feature.getAttribute("name");
                if (feature.getAttribute("xsi:type").endsWith("EAttribute")) attributes.add(key);
                else if (feature.getAttribute("xsi:type").endsWith("EReference")) references.add(key);
            }
        }
        return new EcoreKeys(classes, attributes, references, inheritance);
    }

    private void validateSources(JsonNode root, EcoreKeys keys) {
        validateArray(root, "classMappings", keys.classes);
        validateArray(root, "attributeMappings", keys.attributes);
        validateArray(root, "referenceMappings", keys.references);
        validateArray(root, "inheritanceMappings", keys.inheritance);
        requireUnique(root, "classMappings", "target", "name");
        requireUnique(root, "referenceMappings", "target", "name");
    }

    private void validateArray(JsonNode root, String field, Set<String> expected) {
        Set<String> found = new LinkedHashSet<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode entry : root.withArray(field)) {
            String source = entry.path("source").asText();
            if (!ids.add(entry.path("id").asText()) || !found.add(source)) {
                throw new MappingException("MAPPING_SOURCE_INVALID", "Duplicate ID/source in " + field + ": " + source);
            }
        }
        if (!found.equals(expected)) {
            Set<String> missing = new LinkedHashSet<>(expected); missing.removeAll(found);
            Set<String> extra = new LinkedHashSet<>(found); extra.removeAll(expected);
            throw new MappingException("MAPPING_SOURCE_INVALID", field + " missing=" + missing + " extra=" + extra);
        }
    }

    private void requireUnique(JsonNode root, String array, String object, String field) {
        Set<String> values = new HashSet<>();
        for (JsonNode entry : root.withArray(array)) {
            String value = entry.path(object).path(field).asText();
            if (!values.add(value)) throw new MappingException("MAPPING_TARGET_COLLISION", "Duplicate target: " + value);
        }
    }

    private MappingModel parse(JsonNode root) {
        List<MappingModel.ClassMapping> classes = new ArrayList<>();
        for (JsonNode node : root.withArray("classMappings")) classes.add(new MappingModel.ClassMapping(
                text(node, "id"), text(node, "source"), text(node.path("target"), "name"),
                node.path("target").path("abstract").asBoolean()));
        List<MappingModel.AttributeMapping> attributes = new ArrayList<>();
        for (JsonNode node : root.withArray("attributeMappings")) attributes.add(new MappingModel.AttributeMapping(
                text(node, "id"), text(node, "source"), text(node, "sourceOwner"), text(node, "sourceName"),
                text(node.path("target"), "owner"), text(node.path("target"), "name"), text(node.path("target"), "type")));
        List<MappingModel.ReferenceMapping> references = new ArrayList<>();
        for (JsonNode node : root.withArray("referenceMappings")) {
            JsonNode target = node.path("target");
            references.add(new MappingModel.ReferenceMapping(text(node, "id"), text(node, "source"),
                    text(node, "sourceOwner"), text(node, "sourceName"), text(node, "sourceTarget"),
                    node.path("sourceContainment").asBoolean(), text(target, "kind"), text(target, "name"),
                    end(target.path("firstEnd")), end(target.path("secondEnd"))));
        }
        List<MappingModel.InheritanceMapping> inheritance = new ArrayList<>();
        for (JsonNode node : root.withArray("inheritanceMappings")) inheritance.add(new MappingModel.InheritanceMapping(
                text(node, "id"), text(node, "source"), text(node.path("target"), "subclass"),
                text(node.path("target"), "superclass")));
        List<MappingModel.ProjectionMapping> projections = new ArrayList<>();
        for (JsonNode node : root.withArray("verificationProjections")) projections.add(new MappingModel.ProjectionMapping(
                text(node, "id"), text(node, "name"), text(node, "status")));
        return new MappingModel(text(root, "schemaVersion"), text(root, "mappingId"), text(root, "status"),
                classes, attributes, references, inheritance, projections);
    }

    private MappingModel.AssociationEnd end(JsonNode node) {
        return new MappingModel.AssociationEnd(text(node, "class"), text(node, "multiplicity"),
                text(node, "role"), node.path("ordered").asBoolean());
    }

    private String text(JsonNode node, String field) { return node.path(field).asText(); }

    private String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record EcoreKeys(Set<String> classes, Set<String> attributes, Set<String> references,
                             Set<String> inheritance) { }
}
