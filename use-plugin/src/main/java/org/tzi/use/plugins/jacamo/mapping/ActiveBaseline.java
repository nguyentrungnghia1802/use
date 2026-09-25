package org.tzi.use.plugins.jacamo.mapping;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The only active filesystem/classpath selection boundary. Never falls back to historical V1. */
public final class ActiveBaseline {
    public static final String VERSION = "V2";
    public static final String ECORE = "jacamo_v2_complete.ecore";
    public static final String MAPPING = "jacamo-use-mapping-v2.json";
    public static final String SCHEMA = "jacamo-use-mapping-v2.schema.json";
    public static final String FREEZE = "v2-freeze-manifest.json";
    public static final String RESOURCE_ROOT = "/org/tzi/use/plugins/jacamo/canonical/version-2/";
    public static final String RELEASE_ROOT = "/org/tzi/use/plugins/jacamo/release/";

    public Selection fromCheckout(Path checkout) {
        Path module = Files.isDirectory(checkout.resolve("Core")) ? checkout : checkout.resolve("use-plugin");
        Map<String, Path> paths = Map.of(ECORE, module.resolve("Core/Metamodel/version-2/" + ECORE),
                MAPPING, module.resolve("Core/Mapping/version-2/" + MAPPING),
                SCHEMA, module.resolve("Core/Mapping/version-2/" + SCHEMA),
                FREEZE, module.resolve("release/" + FREEZE));
        return select(name -> Files.readAllBytes(paths.get(name)), paths.toString());
    }

    public Selection packaged() {
        return select(name -> {
            String root = FREEZE.equals(name) ? RELEASE_ROOT : RESOURCE_ROOT;
            try (var stream = ActiveBaseline.class.getResourceAsStream(root + name)) {
                if (stream == null) throw new java.io.IOException("Missing " + root + name);
                return stream.readAllBytes();
            }
        }, RESOURCE_ROOT);
    }

    private Selection select(Reader reader, String origin) {
        try {
            Map<String, byte[]> bytes = new HashMap<>();
            Map<String, String> hashes = new TreeMap<>();
            for (String name : List.of(ECORE, MAPPING, SCHEMA)) {
                byte[] value = reader.read(name).clone(); bytes.put(name, value);
                hashes.put(name, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)));
            }
            var loader = new MappingLoader(path -> bytes.get(path.toString()).clone());
            var mapping = loader.loadV2(Path.of(MAPPING), Path.of(SCHEMA), Path.of(ECORE));
            var root = new ObjectMapper().readTree(bytes.get(MAPPING));
            validateFreeze(mapping, hashes, new ObjectMapper().readTree(reader.read(FREEZE)));
            return new Selection(mapping, root.path("sourceMetamodel").path("packageName").asText(),
                    root.path("sourceMetamodel").path("nsURI").asText(), Map.copyOf(hashes), origin,
                    "SOURCE_CONTRACT_VALIDATED_FROZEN", RESOURCE_ROOT);
        } catch (MappingException e) {
            throw new MappingException(e.code(), origin + ": " + e.getMessage(), e);
        } catch (Exception e) { throw new MappingException("ACTIVE_BASELINE_LOAD_FAILED", origin + ": " + e.getMessage(), e); }
    }
    private void validateFreeze(MappingModel mapping, Map<String, String> hashes,
                                com.fasterxml.jackson.databind.JsonNode freeze) {
        if (!"FROZEN".equals(mapping.status()) || !"FROZEN".equals(freeze.path("status").asText())
                || !freeze.path("freeze").asBoolean())
            throw new MappingException("ACTIVE_FREEZE_MANIFEST_INVALID", "V2 baseline is not frozen");
        var resources = freeze.path("resources");
        Map<String, String> expected = Map.of(
                ECORE, resources.path("metamodel").path("sha256").asText(),
                MAPPING, resources.path("structuralMapping").path("sha256").asText(),
                SCHEMA, resources.path("structuralMapping").path("schemaSha256").asText());
        if (!hashes.equals(expected))
            throw new MappingException("ACTIVE_FREEZE_HASH_MISMATCH", "Frozen V2 fingerprints do not match active bytes");
        if (!hashes.get(ECORE).equals(resources.path("structuralMapping").path("sourceMetamodelSha256").asText()))
            throw new MappingException("ACTIVE_FREEZE_HASH_MISMATCH", "Structural Mapping targets a different Metamodel V2");
    }
    @FunctionalInterface private interface Reader { byte[] read(String name) throws Exception; }
    public record Selection(MappingModel mapping, String packageName, String nsURI, Map<String, String> hashes,
                            String origin, String compatibility, String resourceRoot) {
        public Selection { hashes = Map.copyOf(hashes); }
    }
}
