package org.tzi.use.plugins.jacamo.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class VerificationReportExporter {
    private static final ObjectMapper JSON = new ObjectMapper();

    public String toJson(VerificationReport report) {
        ObjectNode root = JSON.createObjectNode().put("schemaVersion", report.schemaVersion())
                .put("runId", report.runId()).put("timestamp", report.timestamp().toString())
                .put("mode", report.mode()).put("structureValid", report.structureValid());
        ObjectNode fingerprints = root.putObject("fingerprints");
        report.fingerprints().forEach(fingerprints::put);
        ArrayNode results = root.putArray("results");
        for (VerificationResult result : report.results()) {
            ObjectNode node = results.addObject().put("constraintId", result.constraintId())
                    .put("outcome", result.outcome().name());
            nullable(node, "contextObject", result.contextObject());
            node.put("explanation", result.explanation()).put("oclSource", result.oclSource());
            ArrayNode traces = node.putArray("sourceTrace");
            result.sourceTrace().forEach(traces::add);
            nullable(node, "correlationId", result.correlationId());
            ArrayNode events = node.putArray("runtimeEventIds");
            result.runtimeEventIds().forEach(events::add);
        }
        try { return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n"; }
        catch (Exception exception) { throw new IllegalStateException("REPORT_JSON_FAILED", exception); }
    }

    public String toMarkdown(VerificationReport report) {
        StringBuilder out = new StringBuilder("# Verification Report\n\n")
                .append("- Run: `").append(report.runId()).append("`\n")
                .append("- Mode: ").append(report.mode()).append("\n")
                .append("- Structure valid: ").append(report.structureValid()).append("\n\n")
                .append("| Constraint | Outcome | Context |\n|---|---|---|\n");
        for (VerificationResult result : report.results()) {
            out.append("| ").append(cell(result.constraintId())).append(" | ").append(result.outcome())
                    .append(" | ").append(cell(result.contextObject())).append(" |\n");
            if (!result.sourceTrace().isEmpty())
                out.append("\nTrace: `").append(String.join("`, `", result.sourceTrace())).append("`\n");
        }
        return out.toString();
    }

    private void nullable(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }
    private String cell(String value) { return value == null ? "" : value.replace("|", "\\|").replace("\n", " "); }
}
