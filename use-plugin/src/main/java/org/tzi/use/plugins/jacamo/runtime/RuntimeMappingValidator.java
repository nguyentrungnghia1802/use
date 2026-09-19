package org.tzi.use.plugins.jacamo.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;

/** Semantic validation against the embedded structural contract. */
public final class RuntimeMappingValidator {
    public void validate(RuntimeMapping mapping) {
        Set<String> ids = new HashSet<>(), selectors = new HashSet<>();
        JsonNode structural;
        try (var input = getClass().getResourceAsStream("/org/tzi/use/plugins/jacamo/canonical/jacamo-use-mapping-v1.json")) {
            structural = new ObjectMapper().readTree(input);
        } catch (Exception error) { throw new RuntimeMappingException("RUNTIME_MAPPING_ANCHOR_RESOURCE", "document", error.getMessage()); }
        for (var rule : mapping.rules()) {
            if (!ids.add(rule.id())) fail(rule, "DUPLICATE_ID", "id");
            if (!selectors.add(rule.eventKind().name())) fail(rule, "CONFLICTING_SELECTOR", "eventKind");
            var authority = RuntimeEventValidator.authority(rule.eventKind());
            if (authority != null && !authority.name().equals(rule.dimension())) fail(rule, "AUTHORITY_CONFLICT", "eventKind/dimension");
            var action = rule.action();
            if (!action.mutation().equals(rule.mutation()) || !action.target().equals(rule.targetKind())
                    || !action.checkpoint().equals(rule.checkpoint())) fail(rule, "ACTION_CONFLICT", "action/mutation/target/checkpoint");
            if (action.mutates() && (!rule.traceRequired() || !rule.authoritative()
                    || !rule.support().equals("READY_V1_TEMPORARY"))) fail(rule, "UNSAFE_MUTATION", "trace/authority/support");
            if (action.operation() && (!rule.correlationRequired() || !rule.identity().equals("INVOCATION")))
                fail(rule, "CORRELATION_REQUIRED", "correlationRequired/identity");
            String runtime = switch (rule.dimension()) {
                case "ANY" -> "NORMALIZED";
                case "AGENT" -> "JASON";
                case "ENVIRONMENT" -> "CARTAGO";
                case "ORGANISATION" -> "MOISE";
                default -> "INVALID";
            };
            if (!runtime.equals(rule.runtime())) fail(rule, "AUTHORITY_CONFLICT", "runtime/dimension");
            if (!rule.runtime().equals("NORMALIZED") && action.mutates() && !rule.runtime().equals("CARTAGO"))
                fail(rule, "UNPROVEN_AUTHORITY", "action");
            if (action.mutates() && !anchorExists(structural, rule.anchor())) fail(rule, "ANCHOR_MISSING", "anchor");
            String expectedAnchor = switch (action.target()) {
                case "ATTRIBUTE" -> "VP002";
                case "OPERATION" -> "VP003";
                case "OBJECT" -> "classMappings";
                case "ASSOCIATION" -> "referenceMappings";
                default -> "NONE";
            };
            if (!expectedAnchor.equals(rule.anchor())) fail(rule, "ANCHOR_INCOMPATIBLE", "anchor/targetKind");
            if (action.mutates()) {
                Set<String> required = switch (action) {
                    case ATTRIBUTE_STATE_SET -> Set.of("attribute", "valueType", "value");
                    case ATTRIBUTE_STATE_UNSET -> Set.of("attribute");
                    case OBJECT_AVAILABLE -> Set.of("useClass", "useObject");
                    case RELATION_INSERT, RELATION_DELETE -> Set.of("association", "participants");
                    case OPERATION_ENTER -> Set.of("operation", "arguments");
                    case OPERATION_FAIL -> Set.of("error");
                    default -> Set.of();
                };
                if (!Set.copyOf(rule.payload()).equals(required)) fail(rule, "PAYLOAD_CONFLICT", "payload");
            }
        }
    }
    private boolean anchorExists(JsonNode node, String anchor) {
        if ((anchor.equals("classMappings") || anchor.equals("referenceMappings")) && node.has(anchor)) return true;
        if (node.isObject() && anchor.equals(node.path("id").asText())) return true;
        for (JsonNode child : node) if (anchorExists(child, anchor)) return true;
        return false;
    }
    private void fail(RuntimeMapping.Rule rule, String code, String field) {
        throw new RuntimeMappingException("RUNTIME_MAPPING_" + code, rule.id(), field);
    }
}
