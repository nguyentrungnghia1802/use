package org.tzi.use.plugins.jacamo.trace;

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
import java.util.List;
import org.tzi.use.plugins.jacamo.project.SourceSpan;

public final class TraceStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCHEMA = "/org/tzi/use/plugins/jacamo/trace/trace-v1.schema.json";
    public void write(Path path, TraceIndex index) {
        try {
            ObjectNode root = JSON.createObjectNode().put("schemaVersion", "1.0.0"); ArrayNode records = root.putArray("records");
            for (TraceRecord record : index.records()) {
                ObjectNode node = records.addObject().put("traceId", record.traceId())
                        .put("sourceSemanticId", record.sourceSemanticId()).put("targetUseId", record.targetUseId())
                        .put("sourceKind", record.sourceKind()).put("targetKind", record.targetKind())
                        .put("status", record.status().name());
                nullable(node,"mappingRuleId",record.mappingRuleId()); nullable(node,"projectionRuleId",record.projectionRuleId());
                nullable(node,"sourceHash",record.sourceHash()); nullable(node,"runtimeKey",record.runtimeKey());
                if (record.sourceSpan() != null) node.putObject("sourceSpan").put("path", record.sourceSpan().path().toString())
                        .put("startLine", record.sourceSpan().startLine()).put("startColumn", record.sourceSpan().startColumn())
                        .put("endLine", record.sourceSpan().endLine()).put("endColumn", record.sourceSpan().endColumn());
            }
            String text = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n"; validate(text);
            Files.createDirectories(path.toAbsolutePath().normalize().getParent()); Files.writeString(path,text,StandardCharsets.UTF_8);
        } catch (Exception exception) { throw new IllegalArgumentException("TRACE_WRITE_FAILED: " + exception.getMessage(), exception); }
    }
    public TraceIndex read(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8); validate(text); JsonNode root = JSON.readTree(text);
            List<TraceRecord> result = new ArrayList<>();
            for (JsonNode node : root.withArray("records")) {
                JsonNode span = node.path("sourceSpan"); SourceSpan sourceSpan = span.isMissingNode() ? null : new SourceSpan(
                        Path.of(value(span,"path")), span.path("startLine").asInt(), span.path("startColumn").asInt(),
                        span.path("endLine").asInt(), span.path("endColumn").asInt());
                result.add(new TraceRecord(value(node,"traceId"),value(node,"sourceSemanticId"),value(node,"targetUseId"),
                        value(node,"sourceKind"),value(node,"targetKind"),nullable(node,"mappingRuleId"),
                        nullable(node,"projectionRuleId"),sourceSpan,nullable(node,"sourceHash"),nullable(node,"runtimeKey"),
                        TraceRecord.Status.valueOf(value(node,"status"))));
            }
            return new TraceIndex(result);
        } catch (Exception exception) { throw new IllegalArgumentException("TRACE_READ_FAILED: " + exception.getMessage(), exception); }
    }
    private void validate(String text) throws Exception {
        try (InputStream input = TraceStore.class.getResourceAsStream(SCHEMA)) {
            if (input == null) throw new IllegalArgumentException("TRACE_SCHEMA_MISSING");
            String schemaText = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            var errors = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schemaText, InputFormat.JSON).validate(text, InputFormat.JSON);
            if (!errors.isEmpty()) throw new IllegalArgumentException("TRACE_SCHEMA_INVALID: " + errors);
        }
    }
    private void nullable(ObjectNode node,String name,String value) { if (value == null) node.putNull(name); else node.put(name,value); }
    private String nullable(JsonNode node,String field) { return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).asText(); }
    private String value(JsonNode node,String field) { return node.path(field).asText(); }
}
