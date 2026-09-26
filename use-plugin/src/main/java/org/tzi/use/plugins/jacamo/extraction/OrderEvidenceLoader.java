package org.tzi.use.plugins.jacamo.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.plugins.jacamo.project.SourceKind;
import org.tzi.use.plugins.jacamo.project.SourceSpan;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;
import org.tzi.use.plugins.jacamo.semantic.SemanticReference;

/** Applies explicit project-side evidence for independently ordered source navigations. */
final class OrderEvidenceLoader {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCHEMA = "/org/tzi/use/plugins/jacamo/order/order-evidence-v1.schema.json";

    void apply(ExtractionContext context) {
        List<Path> files = context.graph.sources().stream()
                .filter(source -> source.kind() == SourceKind.OTHER
                        && source.path().getFileName().toString().equals("order-evidence.json"))
                .map(source -> source.path()).toList();
        if (files.isEmpty()) return;
        if (files.size() != 1) {
            context.diagnostic("ORDER_EVIDENCE_AMBIGUOUS", Severity.ERROR, Phase.RESOLUTION,
                    null, null, "Multiple order evidence files were discovered", files.toString(),
                    "Keep one order-evidence.json at the project root");
            return;
        }
        Path path = files.getFirst();
        try {
            String text = context.read(path);
            validate(text);
            JsonNode root = JSON.readTree(text);
            Map<String, ElementDraft> elements = new HashMap<>();
            context.elements.forEach(element -> elements.put(element.id.value(), element));
            Set<String> supplied = new HashSet<>();
            for (JsonNode node : root.withArray("orders")) {
                String ownerId = node.path("owner").asText();
                String feature = node.path("feature").asText();
                String key = ownerId + "#" + feature;
                if (!supplied.add(key)) throw new IllegalArgumentException("duplicate owner/feature: " + key);
                ElementDraft owner = elements.get(ownerId);
                if (owner == null) throw new IllegalArgumentException("owner is absent: " + ownerId);
                String sourceHash = node.path("ownerSourceHash").asText();
                if (owner.provenance.stream().noneMatch(provenance -> provenance.sourceHash().equals(sourceHash)))
                    throw new IllegalArgumentException("owner source hash is stale: " + ownerId);
                boolean projected = context.graph.root() != null && context.elements != null
                        && org.tzi.use.plugins.jacamo.semantic.MetamodelKind.registry().mapping().orderProjections().stream()
                        .anyMatch(spec -> spec.sourceIdentity().equals("agentmetamodel::"
                                + owner.kind.name() + "#" + feature));
                if (!projected) throw new IllegalArgumentException("feature has no frozen order projection: " + key);

                List<SemanticReference> membership = owner.references.stream()
                        .filter(reference -> reference.feature().equals(feature)).toList();
                if (membership.stream().anyMatch(reference -> reference.targetId() == null))
                    throw new IllegalArgumentException("feature contains unresolved membership: " + key);
                List<String> requested = new ArrayList<>();
                node.withArray("targets").forEach(target -> requested.add(target.asText()));
                List<String> actual = membership.stream().map(reference -> reference.targetId().value()).toList();
                if (requested.size() != new HashSet<>(requested).size())
                    throw new IllegalArgumentException("target order contains duplicates: " + key);
                if (requested.size() != actual.size() || !new HashSet<>(requested).equals(new HashSet<>(actual)))
                    throw new IllegalArgumentException("target order differs from exact membership: " + key);

                Map<String, SemanticReference> byTarget = new LinkedHashMap<>();
                membership.forEach(reference -> byTarget.put(reference.targetId().value(), reference));
                owner.references.removeIf(reference -> reference.feature().equals(feature));
                requested.forEach(target -> owner.references.add(byTarget.get(target)));
                owner.sourceFacts.remove("orderUnresolved:" + feature);
                owner.sourceFacts.put("orderEvidence:" + feature,
                        new AttributeValue.Text(path.getFileName() + "#" + node.path("evidenceId").asText()));
                context.diagnostics.removeIf(diagnostic -> diagnostic.code().equals("ORDER_SOURCE_UNRESOLVED")
                        && Objects.equals(diagnostic.semanticId(), ownerId)
                        && diagnostic.evidence().equals(feature));
            }
        } catch (Exception error) {
            context.diagnostic("ORDER_EVIDENCE_INVALID", Severity.ERROR, Phase.RESOLUTION,
                    new SourceSpan(path, 1, 1, 1, 1), null,
                    "Project order evidence is invalid or stale", error.getMessage(),
                    "Regenerate exact order evidence from current semantic identities and source hashes");
        }
    }

    private void validate(String text) throws Exception {
        try (InputStream input = OrderEvidenceLoader.class.getResourceAsStream(SCHEMA)) {
            if (input == null) throw new IllegalArgumentException("ORDER_EVIDENCE_SCHEMA_MISSING");
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            var errors = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schema, InputFormat.JSON).validate(text, InputFormat.JSON);
            if (!errors.isEmpty()) throw new IllegalArgumentException("ORDER_EVIDENCE_SCHEMA_INVALID: " + errors);
        }
    }
}
