package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/**
 * Exports the state owned by the native USE system as deterministic JSON.
 *
 * <p>This is deliberately separate from the {@code .use} model export. It reads
 * only the supplied {@link MSystem}; no historical mapping or parallel state is
 * consulted.</p>
 */
public final class NativeUseStateExporter {
    public static final String SCHEMA_VERSION = "1.0.0";

    public String export(MSystem system) {
        return export(system, NativeUseStructure.sha256(system.model()));
    }

    public String export(MSystem system, String structuralHash) {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(structuralHash, "structuralHash");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("modelName", system.model().name());
        root.put("structuralHash", structuralHash);
        root.put("objects", objects(system));
        root.put("links", links(system));
        return new String(CanonicalJson.encode(root), StandardCharsets.UTF_8);
    }

    public void write(MSystem system, Path destination) {
        write(system, NativeUseStructure.sha256(system.model()), destination);
    }

    public void write(MSystem system, String structuralHash, Path destination) {
        Path output = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        if (!output.getFileName().toString().toLowerCase().endsWith(".json"))
            throw new IllegalArgumentException("NATIVE_USE_STATE_EXPORT_EXTENSION_REQUIRED");
        try {
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            Files.writeString(output, export(system, structuralHash), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("NATIVE_USE_STATE_EXPORT_IO_FAILED: " + output, error);
        }
    }

    private static List<Map<String, Object>> objects(MSystem system) {
        return system.state().allObjects().stream()
                .sorted(Comparator.comparing(MObject::name))
                .map(object -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", object.name());
                    row.put("class", object.cls().name());
                    Map<String, Object> attributes = new LinkedHashMap<>();
                    object.cls().allAttributes().stream()
                            .sorted(Comparator.comparing(attribute -> attribute.name()))
                            .forEach(attribute -> {
                                var value = object.state(system.state()).attributeValue(attribute);
                                attributes.put(attribute.name(), value == null ? "null" : value.toString());
                            });
                    row.put("attributes", attributes);
                    return row;
                }).toList();
    }

    private static List<Map<String, Object>> links(MSystem system) {
        List<MLink> links = new ArrayList<>(system.state().allLinks());
        links.sort(Comparator.comparing((MLink link) -> link.association().name())
                .thenComparing(link -> link.linkedObjects().stream()
                        .map(MObject::name).sorted().toList().toString()));
        return links.stream().map(link -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("association", link.association().name());
            row.put("objects", link.linkedObjects().stream().map(MObject::name).sorted().toList());
            return row;
        }).toList();
    }
}
