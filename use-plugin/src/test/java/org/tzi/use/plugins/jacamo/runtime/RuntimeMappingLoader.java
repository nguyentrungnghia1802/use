package org.tzi.use.plugins.jacamo.runtime;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Owns a single byte snapshot per input and never falls back after invalid input. */
public final class RuntimeMappingLoader {
    static final String ROOT = "/org/tzi/use/plugins/jacamo/runtime/";
    static final String HISTORICAL_ROOT = "/org/tzi/use/plugins/jacamo/historical/version-1/";
    static final String RELEASE_ROOT = "/org/tzi/use/plugins/jacamo/release/";
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    public RuntimeMapping loadDefault() {
        byte[] mappingBytes = resource("jacamo-use-runtime-mapping-v2.json");
        byte[] schemaBytes = resource("runtime-mapping-v2.schema.json");
        RuntimeMapping mapping = loadBytes(mappingBytes);
        if (!"FROZEN".equals(mapping.status()))
            throw new RuntimeMappingException("RUNTIME_MAPPING_STATUS_INVALID", "document", "Default must be FROZEN");
        validateFreeze(mappingBytes, schemaBytes, mapping);
        return mapping;
    }
    private void validateFreeze(byte[] mappingBytes, byte[] schemaBytes, RuntimeMapping mapping) {
        try {
            var freeze = JSON.readTree(resource(RELEASE_ROOT, "v2-freeze-manifest.json"));
            var runtime = freeze.path("resources").path("runtimeMapping");
            if (!freeze.path("freeze").asBoolean() || !"FROZEN".equals(freeze.path("status").asText())
                    || !mapping.status().equals(runtime.path("status").asText())
                    || !sha256(mappingBytes).equals(runtime.path("sha256").asText())
                    || !sha256(schemaBytes).equals(runtime.path("schemaSha256").asText())
                    || !mapping.targetContract().get("mappingSha256")
                            .equals(runtime.path("targetMappingSha256").asText()))
                throw new RuntimeMappingException("RUNTIME_MAPPING_FREEZE_MISMATCH", "document",
                        "Frozen Runtime Mapping fingerprints do not match active bytes");
        } catch (RuntimeMappingException exception) { throw exception; }
        catch (Exception exception) {
            throw new RuntimeMappingException("RUNTIME_MAPPING_FREEZE_MISMATCH", "document", exception.getMessage());
        }
    }
    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
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
            if (!"3.0.0".equals(document.path("schemaVersion").asText()))
                throw new RuntimeMappingException("RUNTIME_MAPPING_VERSION_UNSUPPORTED", "document",
                    "Use runtime schema 3.0.0 and explicitly reconcile legacy draft metadata; no automatic semantic migration");
            var schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(new String(resource("runtime-mapping-v2.schema.json"), StandardCharsets.UTF_8), InputFormat.JSON);
            var errors = schema.validate(text, InputFormat.JSON);
            if (!errors.isEmpty()) throw new RuntimeMappingException("RUNTIME_MAPPING_SCHEMA_INVALID", "document", errors.toString());
            RuntimeMapping result = JSON.readValue(bytes, RuntimeMapping.class);
            var baseline = new org.tzi.use.plugins.jacamo.mapping.ActiveBaseline().packaged();
            var expected = java.util.Map.of("mappingId", baseline.mapping().mappingId(),
                    "ecoreSha256", baseline.hashes().get(org.tzi.use.plugins.jacamo.mapping.ActiveBaseline.ECORE),
                    "mappingSha256", baseline.hashes().get(org.tzi.use.plugins.jacamo.mapping.ActiveBaseline.MAPPING));
            if (!expected.equals(result.targetContract()))
                throw new RuntimeMappingException("RUNTIME_MAPPING_BASELINE_MISMATCH", "document", "targetContract");
            new RuntimeMappingValidator(new V2RuntimeBindingAdapter(baseline.mapping())).validate(result);
            return result;
        } catch (RuntimeMappingException exception) { throw exception; }
        catch (Exception exception) { throw new RuntimeMappingException("RUNTIME_MAPPING_LOAD_FAILED", "document", exception.getMessage()); }
    }
    static byte[] resource(String name) {
        return resource(ROOT, name);
    }
    static byte[] historicalResource(String name) {
        return resource(HISTORICAL_ROOT, name);
    }
    private static byte[] resource(String root, String name) {
        try (var input = RuntimeMappingLoader.class.getResourceAsStream(root + name)) {
            if (input == null) throw new IllegalArgumentException("resource missing: " + name);
            return input.readAllBytes();
        } catch (Exception exception) { throw new RuntimeMappingException("RUNTIME_MAPPING_RESOURCE_FAILED", "document", name); }
    }
}
