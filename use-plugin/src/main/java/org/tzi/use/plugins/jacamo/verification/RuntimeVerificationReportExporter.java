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
                .put("latencyNanos", report.latencyNanos())
                .put("checkpoint", report.checkpoint().name());
        if (report.event() == null) root.putNull("event");
        else root.putObject("event").put("eventId", report.event().eventId())
                .put("kind", report.event().kind().name()).put("sequence", report.event().sequence())
                .put("runtimeSourceId", report.event().runtimeSourceId())
                .put("semanticSourceId", report.event().semanticSourceId())
                .put("v2SemanticId", report.event().semanticSourceId())
                .put("correlationId", report.event().correlationId());
        ArrayNode provenance = root.putArray("provenance");
        for (var record : report.provenance()) {
            var item = provenance.addObject().put("traceId",record.traceId()).put("semanticId",record.sourceSemanticId())
                .put("useId",record.targetUseId()).put("sourceKind", record.sourceKind())
                .put("targetKind", record.targetKind()).put("mappingRuleId", record.mappingRuleId())
                .put("projectionRuleId", record.projectionRuleId()).put("sourceHash", record.sourceHash())
                .put("status",record.status().name());
            if (record.sourceSpan()!=null) item.putObject("sourceSpan").put("path",record.sourceSpan().path().toString())
                .put("startLine",record.sourceSpan().startLine()).put("startColumn",record.sourceSpan().startColumn())
                .put("endLine",record.sourceSpan().endLine()).put("endColumn",record.sourceSpan().endColumn());
        }
        ArrayNode diagnostics = root.putArray("diagnostics");
        report.diagnostics().forEach(diagnostics::add);
        ObjectNode verification = root.putObject("verification")
                .put("runId", report.verification().runId()).put("mode", report.verification().mode());
        verification.set("fingerprints",JSON.valueToTree(report.verification().fingerprints()));
        verification.put("structureValid",report.verification().structureValid());
        ArrayNode results = verification.putArray("results");
        for (VerificationResult result : report.verification().results()) {
            RuntimeVerificationAttribution attribution = report.attribution().stream()
                    .filter(value -> value.constraintId().equals(result.constraintId())).findFirst().orElse(null);
            ObjectNode node = results.addObject().put("constraintId", result.constraintId())
                    .put("outcome", result.outcome().name()).put("explanation", result.explanation())
                    .put("correlationId",result.correlationId());
            if (attribution != null) {
                node.put("constraintName", attribution.constraintName()).put("origin", attribution.origin())
                        .put("checkpoint", report.checkpoint().name()).put("useContext", attribution.context())
                        .put("operation", attribution.operation()).put("sourcePath", attribution.sourcePath())
                        .put("runtimeRuleId", attribution.runtimeRuleId());
                if (attribution.sourceSpan() != null) node.putObject("constraintSourceSpan")
                        .put("path", attribution.sourceSpan().path().toString())
                        .put("startLine", attribution.sourceSpan().startLine())
                        .put("startColumn", attribution.sourceSpan().startColumn())
                        .put("endLine", attribution.sourceSpan().endLine())
                        .put("endColumn", attribution.sourceSpan().endColumn());
                node.set("mappingRuleIds", JSON.valueToTree(attribution.mappingRuleIds()));
                node.set("projectionRuleIds", JSON.valueToTree(attribution.projectionRuleIds()));
            }
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
                .append("- Checkpoint: ").append(report.checkpoint()).append("\n")
                .append("- Connection: ").append(report.connectionState()).append("\n")
                .append("- Snapshot version: ").append(report.snapshotVersion()).append("\n")
                .append("- Event: `").append(event).append("`\n")
                .append("- Latency: ").append(report.latencyNanos()).append(" ns\n\n")
                .append("| Constraint | Origin | Outcome | Context | Runtime rule | Trace |\n|---|---|---|---|---|---|\n");
        for (VerificationResult result : report.verification().results()) {
            RuntimeVerificationAttribution attribution = report.attribution().stream()
                    .filter(value -> value.constraintId().equals(result.constraintId())).findFirst().orElse(null);
            out.append("| ").append(cell(result.constraintId())).append(" | ")
                    .append(attribution == null ? "" : attribution.origin())
                    .append(" | ").append(result.outcome())
                    .append(" | ").append(cell(result.contextObject())).append(" | ")
                    .append(attribution == null ? "" : cell(attribution.runtimeRuleId())).append(" | ")
                    .append(cell(String.join(", ", result.sourceTrace()))).append(" |\n");
        }
        return out.toString();
    }

    private String cell(String value) { return value == null ? "" : value.replace("|", "\\|").replace("\n", " "); }
}
