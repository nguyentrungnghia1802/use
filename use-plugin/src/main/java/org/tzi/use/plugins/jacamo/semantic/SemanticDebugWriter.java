package org.tzi.use.plugins.jacamo.semantic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.List;
import java.util.Map;

/** Versioned audit export; source reference list order is never sorted or inferred. */
public final class SemanticDebugWriter {
    private static final ObjectMapper JSON = new ObjectMapper();
    public String write(JaCaMoSemanticModel model) {
        if (model.registry() == null) throw new IllegalArgumentException("V2 registry required for V2 export");
        ObjectNode root = JSON.createObjectNode();
        root.put("formatVersion", "2.0.0").put("metamodelSha256", model.registry().ecoreHash())
                .put("mappingId", model.registry().mapping().mappingId()).put("projectId", model.projectId());
        var project = root.putObject("project");
        project.put("name", model.declaration().name());
        provenance(project.putArray("provenance"), model.declaration().provenance());
        values(project.putObject("sourceFacts"), model.declaration().sourceFacts());
        var elements = root.putArray("elements");
        for (var element : model.elements()) {
            var node = elements.addObject();
            node.put("id", element.id().value()).put("kind", element.kind().name())
                    .put("dimension", element.kind().dimension().key()).put("name", element.name());
            provenance(node.putArray("provenance"), element.provenance());
            values(node.putObject("attributes"), element.attributes());
            values(node.putObject("sourceFacts"), element.sourceFacts());
            var refs = node.putArray("references");
            for (var ref : element.references()) {
                var r = refs.addObject().put("feature", ref.feature()).put("sourceSpelling", ref.originalSpelling());
                if (ref.targetId() == null) r.putNull("targetId").put("status", "UNRESOLVED");
                else r.put("targetId", ref.targetId().value()).put("status", "RESOLVED");
            }
        }
        var diagnostics = root.putArray("diagnostics");
        model.diagnostics().stream().sorted(java.util.Comparator.comparing(Object::toString)).forEach(d -> {
            var node = diagnostics.addObject().put("code", d.code()).put("severity", d.severity().name())
                    .put("phase", d.phase().name()).put("message", d.message()).put("evidence", d.evidence())
                    .put("remediation", d.remediation()).put("semanticId", d.semanticId()).put("mappingRuleId", d.mappingRuleId());
            if (d.sourceLocation() != null) span(node.putObject("sourceLocation"), d.sourceLocation());
        });
        try { return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n"; }
        catch (java.io.IOException error) { throw new IllegalStateException("semantic debug export failed", error); }
    }
    private void values(ObjectNode node, Map<String, AttributeValue> values) {
        new java.util.TreeMap<>(values).forEach((key, value) -> {
            var target = node.putObject(key);
            switch (value) {
                case AttributeValue.Text v -> target.put("type", "String").put("value", v.value());
                case AttributeValue.IntegerNumber v -> target.put("type", "Integer").put("value", v.value());
                case AttributeValue.Bool v -> target.put("type", "Boolean").put("value", v.value());
                case AttributeValue.EnumLiteral v -> target.put("type", v.enumeration()).put("value", v.literal());
            }
        });
    }
    private void provenance(ArrayNode node, List<SourceProvenance> origins) {
        origins.stream().sorted(java.util.Comparator.comparing(Object::toString)).forEach(origin -> {
            var p = node.addObject().put("parser", origin.parser()).put("sourceHash", origin.sourceHash())
                    .put("originalSpelling", origin.originalSpelling());
            span(p.putObject("span"), origin.span());
        });
    }
    private void span(ObjectNode node, org.tzi.use.plugins.jacamo.project.SourceSpan span) {
        node.put("path", span.path().toString()).put("startLine", span.startLine()).put("startColumn", span.startColumn())
                .put("endLine", span.endLine()).put("endColumn", span.endColumn());
    }
}
