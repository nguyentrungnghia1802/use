package org.tzi.use.plugins.jacamo.binding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class BindingStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCHEMA = "/org/tzi/use/plugins/jacamo/binding/binding-v1.schema.json";
    public void write(Path path, BindingFile file) {
        try {
            ObjectNode root = JSON.createObjectNode().put("schemaVersion", file.schemaVersion());
            ArrayNode entries = root.putArray("entries");
            file.entries().stream().sorted(Comparator.comparing(BindingEntry::source)).forEach(entry -> entries.addObject()
                    .put("source", entry.source()).put("target", entry.target()).put("kind", entry.kind())
                    .put("reason", entry.reason()).put("sourceHash", entry.sourceHash())
                    .put("provenance", entry.provenance()).put("status", entry.status().name()));
            String text = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n";
            validate(text); Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (Exception exception) { throw new IllegalArgumentException("BINDING_WRITE_FAILED: " + exception.getMessage(), exception); }
    }
    public BindingFile read(Path path, Map<String, String> currentSourceHashes) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8); validate(text);
            JsonNode root = JSON.readTree(text); List<BindingEntry> entries = new ArrayList<>();
            for (JsonNode node : root.withArray("entries")) {
                BindingEntry entry = new BindingEntry(value(node,"source"), value(node,"target"), value(node,"kind"),
                        value(node,"reason"), value(node,"sourceHash"), value(node,"provenance"),
                        BindingEntry.Status.valueOf(value(node,"status")));
                String current = currentSourceHashes.get(entry.source());
                if (current == null || !current.equals(entry.sourceHash())) entry = entry.stale();
                entries.add(entry);
            }
            return new BindingFile(value(root,"schemaVersion"), entries);
        } catch (Exception exception) { throw new IllegalArgumentException("BINDING_READ_FAILED: " + exception.getMessage(), exception); }
    }
    private void validate(String text) throws Exception {
        try (InputStream input = BindingStore.class.getResourceAsStream(SCHEMA)) {
            if (input == null) throw new IllegalArgumentException("BINDING_SCHEMA_MISSING");
            String schemaText = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            var errors = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schemaText, InputFormat.JSON).validate(text, InputFormat.JSON);
            if (!errors.isEmpty()) throw new IllegalArgumentException("BINDING_SCHEMA_INVALID: " + errors);
        }
    }
    private String value(JsonNode node, String field) { return node.path(field).asText(); }
}
