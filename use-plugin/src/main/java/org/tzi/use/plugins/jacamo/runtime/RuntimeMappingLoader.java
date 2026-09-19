package org.tzi.use.plugins.jacamo.runtime;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Owns a single byte snapshot per input and never falls back after invalid input. */
public final class RuntimeMappingLoader {
    static final String ROOT = "/org/tzi/use/plugins/jacamo/runtime/";
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    public RuntimeMapping loadDefault() {
        RuntimeMapping mapping = loadBytes(resource("jacamo-use-runtime-mapping-v1.json"));
        if (!"FROZEN".equals(mapping.status()))
            throw new RuntimeMappingException("RUNTIME_MAPPING_FREEZE_INVALID", "document", "Default must be FROZEN");
        return mapping;
    }
    public RuntimeMapping load(Path path) {
        try { return loadBytes(Files.readAllBytes(path)); }
        catch (RuntimeMappingException exception) { throw exception; }
        catch (Exception exception) { throw new RuntimeMappingException("RUNTIME_MAPPING_LOAD_FAILED", "document", exception.getMessage()); }
    }
    public RuntimeMapping loadBytes(byte[] input) {
        byte[] bytes = input.clone();
        try {
            String text = new String(bytes, StandardCharsets.UTF_8);
            var document = JSON.readTree(bytes); // Reject duplicate JSON keys before schema validation.
            if (!"2.0.0".equals(document.path("schemaVersion").asText()))
                throw new RuntimeMappingException("RUNTIME_MAPPING_VERSION_UNSUPPORTED", "document",
                    "Use runtime schema 2.0.0 and explicitly reconcile legacy draft metadata; no automatic semantic migration");
            var schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(new String(resource("runtime-mapping.schema.json"), StandardCharsets.UTF_8), InputFormat.JSON);
            var errors = schema.validate(text, InputFormat.JSON);
            if (!errors.isEmpty()) throw new RuntimeMappingException("RUNTIME_MAPPING_SCHEMA_INVALID", "document", errors.toString());
            RuntimeMapping result = JSON.readValue(bytes, RuntimeMapping.class);
            new RuntimeMappingValidator().validate(result);
            if ("FROZEN".equals(result.status())) verifyFrozen(bytes);
            return result;
        } catch (RuntimeMappingException exception) { throw exception; }
        catch (Exception exception) { throw new RuntimeMappingException("RUNTIME_MAPPING_LOAD_FAILED", "document", exception.getMessage()); }
    }
    private void verifyFrozen(byte[] mappingBytes) throws Exception {
        var manifest = JSON.readTree(resource("runtime-mapping-freeze.json"));
        if (!"FROZEN".equals(manifest.path("status").asText()))
            throw new RuntimeMappingException("RUNTIME_MAPPING_FREEZE_INVALID", "document", "status");
        for (String name : java.util.List.of("runtime/jacamo-use-runtime-mapping-v1.json",
                "runtime/runtime-mapping.schema.json", "canonical/JaCaMo-Metamodel.ecore",
                "canonical/jacamo-use-mapping-v1.json")) {
            byte[] bytes;
            if (name.endsWith("jacamo-use-runtime-mapping-v1.json")) bytes = mappingBytes;
            else try (var input = getClass().getResourceAsStream("/org/tzi/use/plugins/jacamo/" + name)) {
                if (input == null) throw new RuntimeMappingException("RUNTIME_MAPPING_RESOURCE_FAILED", "document", name);
                bytes = input.readAllBytes();
            }
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!hash.equals(manifest.path("hashes").path(name).asText()))
                throw new RuntimeMappingException("RUNTIME_MAPPING_HASH_MISMATCH", "document", name);
        }
    }
    static byte[] resource(String name) {
        try (var input = RuntimeMappingLoader.class.getResourceAsStream(ROOT + name)) {
            if (input == null) throw new IllegalArgumentException("resource missing: " + name);
            return input.readAllBytes();
        } catch (Exception exception) { throw new RuntimeMappingException("RUNTIME_MAPPING_RESOURCE_FAILED", "document", name); }
    }
}
