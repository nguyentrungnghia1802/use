package org.tzi.use.plugins.jacamo.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class RuntimeVerificationReportExporter {
    private static final ObjectMapper JSON = new ObjectMapper();

    public String toJson(RuntimeVerificationReport report) {
        ObjectNode root = JSON.createObjectNode().put("schemaVersion", report.schemaVersion())
                .put("reportId", report.reportId()).put("timestamp", report.timestamp().toString())
                .put("connectionState", report.connectionState().name())
                .put("snapshotVersion", report.snapshotVersion())
                .put("snapshotFingerprint", report.snapshotFingerprint())
                .put("latencyNanos", report.latencyNanos());
        if (report.event() == null) root.putNull("event");
        else root.putObject("event").put("eventId", report.event().eventId())
                .put("kind", report.event().kind().name()).put("sequence", report.event().sequence())
                .put("runtimeSourceId", report.event().runtimeSourceId());
        ArrayNode diagnostics = root.putArray("diagnostics");
        report.diagnostics().forEach(diagnostics::add);
        ObjectNode verification = root.putObject("verification")
                .put("runId", report.verification().runId()).put("mode", report.verification().mode());
        ArrayNode results = verification.putArray("results");
        for (VerificationResult result : report.verification().results()) {
            ObjectNode node = results.addObject().put("constraintId", result.constraintId())
                    .put("outcome", result.outcome().name()).put("explanation", result.explanation());
            if (result.contextObject() == null) node.putNull("contextObject");
            else node.put("contextObject", result.contextObject());
            node.put("oclSource", result.oclSource());
            ArrayNode traces = node.putArray("sourceTrace");
            result.sourceTrace().forEach(traces::add);
            ArrayNode events = node.putArray("runtimeEventIds");
            result.runtimeEventIds().forEach(events::add);
        }
        try { return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n"; }
        catch (Exception exception) { throw new IllegalStateException("RUNTIME_REPORT_JSON_FAILED", exception); }
    }

    public String toMarkdown(RuntimeVerificationReport report) {
        String event = report.event() == null ? "snapshot" : report.event().eventId() + " / " + report.event().kind();
        StringBuilder out = new StringBuilder("# Runtime Verification Report\n\n")
                .append("- Connection: ").append(report.connectionState()).append("\n")
                .append("- Snapshot version: ").append(report.snapshotVersion()).append("\n")
                .append("- Event: `").append(event).append("`\n")
                .append("- Latency: ").append(report.latencyNanos()).append(" ns\n\n")
                .append("| Constraint | Outcome | Context | Trace |\n|---|---|---|---|\n");
        for (VerificationResult result : report.verification().results())
            out.append("| ").append(cell(result.constraintId())).append(" | ").append(result.outcome())
                    .append(" | ").append(cell(result.contextObject())).append(" | ")
                    .append(cell(String.join(", ", result.sourceTrace()))).append(" |\n");
        return out.toString();
    }

    private String cell(String value) { return value == null ? "" : value.replace("|", "\\|").replace("\n", " "); }
}
