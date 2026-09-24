package org.tzi.use.plugins.jacamo.mapping;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.uml.mm.ModelFactory;

/** Historical frozen-input authorization controls; never changes active selection. */
class HistoricalMappingLoaderTest {
    @TempDir Path temporary;

    @Test
    void historicalMappingPassesSchemaFingerprintAndEcoreIdentityValidation() {
        MappingModel mapping = historical(new MappingLoader());
        assertEquals("1.1.0", mapping.schemaVersion());
        assertEquals(37, mapping.classes().size());
        assertEquals(67, mapping.attributes().size());
        assertEquals(63, mapping.associations().size());
        assertEquals(14, mapping.inheritance().size());
        assertEquals(7, mapping.projections().size());
    }

    @Test
    void invalidSchemaAndFingerprintBlockLoading() throws Exception {
        Path invalid = temporary.resolve("invalid.json");
        Files.writeString(invalid, "{}");
        MappingException schema = assertThrows(MappingException.class, () -> new MappingLoader().load(
                invalid, Path.of("Core/Mapping/version-1/jacamo-use-mapping.schema.json"),
                Path.of("Core/Metamodel/version-1/JaCaMo-Metamodel.ecore"), Path.of("Core/Mapping/version-1/freeze-manifest.json")));
        assertEquals("MAPPING_SCHEMA_INVALID", schema.code());

        Path changedEcore = temporary.resolve("changed.ecore");
        Files.writeString(changedEcore, Files.readString(Path.of("Core/Metamodel/version-1/JaCaMo-Metamodel.ecore")) + "\n");
        MappingException fingerprint = assertThrows(MappingException.class, () -> new MappingLoader().load(
                Path.of("Core/Mapping/version-1/jacamo-use-mapping-v1.json"),
                Path.of("Core/Mapping/version-1/jacamo-use-mapping.schema.json"), changedEcore,
                Path.of("Core/Mapping/version-1/freeze-manifest.json")));
        assertEquals("MAPPING_ECORE_MISMATCH", fingerprint.code());

        Path invalidSource = temporary.resolve("invalid-source.json");
        Files.writeString(invalidSource, Files.readString(Path.of("Core/Mapping/version-1/jacamo-use-mapping-v1.json"))
                .replaceFirst("dSML4JaCaMo::MAS#Name", "dSML4JaCaMo::MAS#NotDeclared"));
        MappingException source = assertThrows(MappingException.class, () -> new MappingLoader().load(
                invalidSource, Path.of("Core/Mapping/version-1/jacamo-use-mapping.schema.json"),
                Path.of("Core/Metamodel/version-1/JaCaMo-Metamodel.ecore"), Path.of("Core/Mapping/version-1/freeze-manifest.json")));
        assertEquals("MAPPING_SOURCE_INVALID", source.code());
    }

    @Test
    void invalidMappingSchemaTakesPrecedenceOverMissingDownstreamInputs() throws Exception {
        Path invalid = temporary.resolve("invalid.json");
        Files.writeString(invalid, "{}");

        MappingException schema = assertThrows(MappingException.class, () -> new MappingLoader().load(
                invalid, Path.of("Core/Mapping/version-1/jacamo-use-mapping.schema.json"),
                temporary.resolve("missing.ecore"), temporary.resolve("missing-freeze-manifest.json")));

        assertEquals("MAPPING_SCHEMA_INVALID", schema.code(),
                "schema validation must fail before Ecore or manifest inputs are required");
    }

    @Test
    void alteredMappingTargetsAreBlockedByTheFrozenMappingFingerprint() throws Exception {
        Path changed = temporary.resolve("changed.json");
        String canonical = Files.readString(Path.of("Core/Mapping/version-1/jacamo-use-mapping-v1.json"));
        // Preserve every source identity and a schema-valid target, but change semantics.
        Files.writeString(changed, canonical.replaceFirst("\"multiplicity\": \"0\\.\\.1\"", "\"multiplicity\": \"*\""));
        assertNotEquals(canonical, Files.readString(changed));
        MappingException mismatch = assertThrows(MappingException.class, () -> new MappingLoader().load(
                changed, Path.of("Core/Mapping/version-1/jacamo-use-mapping.schema.json"),
                Path.of("Core/Metamodel/version-1/JaCaMo-Metamodel.ecore"), Path.of("Core/Mapping/version-1/freeze-manifest.json")));
        assertEquals("MAPPING_HASH_MISMATCH", mismatch.code());
        assertTrue(mismatch.getMessage().contains("Restore the frozen mapping"));
    }

    @Test
    void mappingDigestMustCoverTheSnapshotUsedForReturnedSemantics() throws Exception {
        Path mapping = Path.of("Core/Mapping/version-1/jacamo-use-mapping-v1.json");
        String canonical = Files.readString(mapping);
        byte[] modified = canonical.replaceFirst("\"multiplicity\": \"0\\.\\.1\"",
                "\"multiplicity\": \"*\"").getBytes(StandardCharsets.UTF_8);
        assertNotEquals(canonical, new String(modified, StandardCharsets.UTF_8));
        var reads = new java.util.HashMap<Path, Integer>();
        MappingLoader loader = new MappingLoader(path -> {
            path = path.normalize();
            int count = reads.merge(path, 1, Integer::sum);
            return path.equals(mapping) && count == 1 ? modified : Files.readAllBytes(path);
        });

        MappingException mismatch = assertThrows(MappingException.class, () -> historical(loader),
                "hashing a later canonical snapshot must not authorize modified parsed semantics");
        assertEquals("MAPPING_HASH_MISMATCH", mismatch.code());
    }

    @Test
    void ecoreIdentityValidationUsesTheSameSnapshotAsItsDigest() throws Exception {
        Path ecore = Path.of("Core/Metamodel/version-1/JaCaMo-Metamodel.ecore");
        byte[] replaced = Files.readString(ecore).replace("name=\"MAS\"", "name=\"ReplacedMAS\"")
                .getBytes(StandardCharsets.UTF_8);
        assertFalse(java.util.Arrays.equals(Files.readAllBytes(ecore), replaced));
        var reads = new java.util.HashMap<Path, Integer>();
        MappingLoader loader = new MappingLoader(path -> {
            path = path.normalize();
            int count = reads.merge(path, 1, Integer::sum);
            return path.equals(ecore) && count > 1 ? replaced : Files.readAllBytes(path);
        });

        MappingModel result = assertDoesNotThrow(() -> historical(loader),
                "identity validation must parse the canonical Ecore snapshot whose hash was accepted");
        assertEquals(37, result.classes().size());
        assertEquals(1, reads.get(ecore));
    }

    @Test
    void oneManifestSnapshotAuthorizesBothMappingAndEcore() throws Exception {
        Path freeze = Path.of("Core/Mapping/version-1/freeze-manifest.json");
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var first = json.readTree(Files.readAllBytes(freeze));
        ((com.fasterxml.jackson.databind.node.ObjectNode) first.path("hashes"))
                .put("mapping/jacamo-use-mapping-v1.json", "0".repeat(64));
        byte[] firstBytes = json.writeValueAsBytes(first);
        var reads = new java.util.HashMap<Path, Integer>();
        MappingLoader loader = new MappingLoader(path -> {
            path = path.normalize();
            int count = reads.merge(path, 1, Integer::sum);
            return path.equals(freeze) && count == 1 ? firstBytes : Files.readAllBytes(path);
        });

        MappingException mismatch = assertThrows(MappingException.class, () -> historical(loader),
                "a later manifest must not replace the first snapshot's mapping authorization");
        assertEquals("MAPPING_HASH_MISMATCH", mismatch.code());
        assertEquals(4, reads.size());
        assertTrue(reads.values().stream().allMatch(count -> count == 1),
                "all four authoritative inputs must be read once per load");
    }

    private MappingModel historical(MappingLoader loader) {
        return loader.load(Path.of("Core/Mapping/version-1/jacamo-use-mapping-v1.json"),
                Path.of("Core/Mapping/version-1/jacamo-use-mapping.schema.json"),
                Path.of("Core/Metamodel/version-1/JaCaMo-Metamodel.ecore"), Path.of("Core/Mapping/version-1/freeze-manifest.json"));
    }
}
