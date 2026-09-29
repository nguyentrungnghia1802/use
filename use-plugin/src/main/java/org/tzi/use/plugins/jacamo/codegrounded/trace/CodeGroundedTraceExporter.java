package org.tzi.use.plugins.jacamo.codegrounded.trace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import org.jacamo.bridge.contract.CanonicalJson;

/** Exports the exact code-grounded trace without converting it to the V2 trace schema. */
public final class CodeGroundedTraceExporter {
    public static final String SCHEMA_VERSION = "1.1.0";

    public String export(CodeGroundedTraceIndex trace) {
        Objects.requireNonNull(trace, "trace");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("componentVersions", NativeComponentVersionManifest.load());
        root.put("metrics", metrics(trace.metrics()));
        List<SourceDescriptor> sources = trace.records().stream().map(SourceDescriptor::from).distinct()
                .sorted(Comparator.comparing(SourceDescriptor::sortKey)).toList();
        Map<SourceDescriptor, Integer> sourceRefs = new LinkedHashMap<>();
        for (int index = 0; index < sources.size(); index++) sourceRefs.put(sources.get(index), index);
        root.put("sources", sources.stream().map(CodeGroundedTraceExporter::source).toList());
        root.put("records", trace.records().stream().map(value -> record(value, sourceRefs.get(SourceDescriptor.from(value))))
                .toList());
        return new String(CanonicalJson.encode(root), StandardCharsets.UTF_8);
    }

    public void write(CodeGroundedTraceIndex trace, Path destination) {
        Path output = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        if (!output.getFileName().toString().toLowerCase().endsWith(".json"))
            throw new IllegalArgumentException("CODE_GROUNDED_TRACE_EXPORT_EXTENSION_REQUIRED");
        try {
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            Files.writeString(output, export(trace), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("CODE_GROUNDED_TRACE_EXPORT_IO_FAILED: " + output, error);
        }
    }

    private static Map<String, Object> record(CodeGroundedTraceRecord value, int sourceRef) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", value.ruleId());
        row.put("phase", value.phase().name());
        row.put("sourceRef", sourceRef);
        row.put("targetKind", value.targetKind());
        row.put("targetIdentity", value.targetIdentity());
        row.put("diagnostics", List.copyOf(value.diagnostics()));
        return row;
    }

    private static Map<String, Object> source(SourceDescriptor value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceKind", value.sourceKind());
        row.put("sourceJavaFqcn", value.sourceJavaFqcn());
        row.put("sourceIdentity", value.sourceIdentity());
        row.put("evidenceAuthority", value.evidenceAuthority());
        row.put("fidelity", value.fidelity());
        row.put("capabilityStatus", value.capabilityStatus());
        return row;
    }

    private static Map<String, Object> metrics(CodeGroundedTraceIndex.Metrics value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("recordCount", value.recordCount());
        row.put("byPhase", value.byPhase());
        row.put("byTargetKind", value.byTargetKind());
        row.put("warningThreshold", value.warningThreshold());
        row.put("warningThresholdExceeded", value.warningThresholdExceeded());
        return row;
    }

    private record SourceDescriptor(String sourceKind, String sourceJavaFqcn, String sourceIdentity,
                                    String evidenceAuthority, String fidelity, String capabilityStatus) {
        private static SourceDescriptor from(CodeGroundedTraceRecord value) {
            return new SourceDescriptor(value.sourceKind(), value.sourceJavaFqcn(), value.sourceIdentity(),
                    value.evidenceAuthority().name(), value.fidelity().name(), value.capabilityStatus().name());
        }

        private String sortKey() {
            return String.join("\u0000", sourceKind, sourceJavaFqcn, sourceIdentity,
                    evidenceAuthority, fidelity, capabilityStatus);
        }
    }
}
