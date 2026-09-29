package org.tzi.use.plugins.jacamo.codegrounded.trace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jacamo.bridge.contract.CanonicalJson;

/** Exports the exact code-grounded trace without converting it to the V2 trace schema. */
public final class CodeGroundedTraceExporter {
    public static final String SCHEMA_VERSION = "1.0.0";

    public String export(CodeGroundedTraceIndex trace) {
        Objects.requireNonNull(trace, "trace");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("records", trace.records().stream().map(CodeGroundedTraceExporter::record).toList());
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

    private static Map<String, Object> record(CodeGroundedTraceRecord value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", value.ruleId());
        row.put("phase", value.phase().name());
        row.put("sourceKind", value.sourceKind());
        row.put("sourceJavaFqcn", value.sourceJavaFqcn());
        row.put("sourceIdentity", value.sourceIdentity());
        row.put("targetKind", value.targetKind());
        row.put("targetIdentity", value.targetIdentity());
        row.put("evidenceAuthority", value.evidenceAuthority().name());
        row.put("fidelity", value.fidelity().name());
        row.put("capabilityStatus", value.capabilityStatus().name());
        row.put("diagnostics", List.copyOf(value.diagnostics()));
        return row;
    }
}
