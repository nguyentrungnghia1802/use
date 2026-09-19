package org.tzi.use.plugins.jacamo.runtime;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tzi.use.plugins.jacamo.semantic.Dimension;

public final class RuntimeEventCodec {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCHEMA = "/org/tzi/use/plugins/jacamo/runtime/runtime-event-v1.schema.json";

    public String write(RuntimeEvent event) {
        try { return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node(event)) + "\n"; }
        catch (Exception exception) { throw new IllegalArgumentException("RUNTIME_EVENT_WRITE_FAILED", exception); }
    }

    public RuntimeEvent read(String json) {
        try { return event(JSON.readTree(json)); }
        catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("RUNTIME_EVENT_READ_FAILED", exception); }
    }

    public void writeEvents(Path path, List<RuntimeEvent> events) {
        try {
            ObjectNode root = JSON.createObjectNode().put("schemaVersion", "1.0.0");
            ArrayNode array = root.putArray("events");
            events.forEach(value -> array.add(node(value)));
            Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            Files.writeString(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n",
                    StandardCharsets.UTF_8);
        } catch (Exception exception) { throw new IllegalArgumentException("RUNTIME_EVENT_STREAM_WRITE_FAILED", exception); }
    }

    public List<RuntimeEvent> readEvents(Path path) {
        try {
            JsonNode root = JSON.readTree(Files.readString(path, StandardCharsets.UTF_8));
            if (!"1.0.0".equals(root.path("schemaVersion").asText()) || !root.path("events").isArray())
                throw new IllegalArgumentException("RUNTIME_EVENT_STREAM_SCHEMA_INVALID");
            List<RuntimeEvent> result = new ArrayList<>();
            for (JsonNode item : root.path("events")) result.add(event(item));
            return List.copyOf(result);
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("RUNTIME_EVENT_STREAM_READ_FAILED", exception); }
    }

    private ObjectNode node(RuntimeEvent event) {
        ObjectNode node = JSON.createObjectNode().put("schemaVersion", event.schemaVersion())
                .put("eventId", event.eventId()).put("timestamp", event.timestamp().toString())
                .put("sequence", event.sequence()).put("dimension", event.dimension().name())
                .put("kind", event.kind().name()).put("runtimeSourceId", event.runtimeSourceId());
        if (event.semanticSourceId() == null) node.putNull("semanticSourceId");
        else node.put("semanticSourceId", event.semanticSourceId());
        node.set("payload", JSON.valueToTree(event.payload()));
        if (event.correlationId() == null) node.putNull("correlationId");
        else node.put("correlationId", event.correlationId());
        return node;
    }

    private RuntimeEvent event(JsonNode node) {
        if (!node.isObject()) throw new IllegalArgumentException("RUNTIME_EVENT_SCHEMA_INVALID");
        validate(node.toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = JSON.convertValue(node.path("payload"), LinkedHashMap.class);
        return new RuntimeEvent(node.path("schemaVersion").asText(), node.path("eventId").asText(),
                Instant.parse(node.path("timestamp").asText()), node.path("sequence").asLong(),
                Dimension.valueOf(node.path("dimension").asText()), RuntimeEventKind.valueOf(node.path("kind").asText()),
                node.path("runtimeSourceId").asText(), nullable(node, "semanticSourceId"), payload,
                nullable(node, "correlationId"));
    }

    private String nullable(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private void validate(String text) {
        try (InputStream input = RuntimeEventCodec.class.getResourceAsStream(SCHEMA)) {
            if (input == null) throw new IllegalArgumentException("RUNTIME_EVENT_SCHEMA_MISSING");
            String schemaText = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            var errors = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schemaText, InputFormat.JSON).validate(text, InputFormat.JSON);
            if (!errors.isEmpty()) throw new IllegalArgumentException("RUNTIME_EVENT_SCHEMA_INVALID: " + errors);
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("RUNTIME_EVENT_SCHEMA_FAILED", exception); }
    }
}
