package org.tzi.use.plugins.jacamo.runtime;

import java.util.HashSet;
import java.util.Set;

/** Semantic validation against the embedded structural contract. */
public final class RuntimeMappingValidator {
    private final RuntimeBindingContract binding;
    public RuntimeMappingValidator() { this(new V2RuntimeBindingAdapter()); }
    public RuntimeMappingValidator(RuntimeBindingContract binding) { this.binding = java.util.Objects.requireNonNull(binding); }
    public void validate(RuntimeMapping mapping) {
        Set<String> ids = new HashSet<>(), selectors = new HashSet<>();
        for (var rule : mapping.rules()) {
            if (!ids.add(rule.id())) fail(rule, "DUPLICATE_ID", "id");
            if (!selectors.add(rule.eventKind().name())) fail(rule, "CONFLICTING_SELECTOR", "eventKind");
        }
        for (var rule : mapping.rules()) {
            var authority = RuntimeEventValidator.authority(rule.eventKind());
            if (authority != null && !authority.name().equals(rule.dimension())) fail(rule, "AUTHORITY_CONFLICT", "eventKind/dimension");
            var action = rule.action();
            if (!action.mutation().equals(rule.mutation()) || !action.target().equals(rule.targetKind())
                    || !action.checkpoint().equals(rule.checkpoint())) fail(rule, "ACTION_CONFLICT", "action/mutation/target/checkpoint");
            if (action.mutates() && (!rule.traceRequired() || !rule.authoritative()
                    || !rule.support().equals("SUPPORTED"))) fail(rule, "UNSAFE_MUTATION", "trace/authority/support");
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
            if (action.mutates()) {
                boolean sourceCompatible = switch (rule.eventKind()) {
                    case CREATE_OBJECT, DESTROY_OBJECT, INSERT_LINK, DELETE_LINK,
                         REPLACE_ORDER, OP_ENTER, OP_EXIT, OP_FAIL -> rule.eventKind().name().equals(action.mutation());
                    case SET_ATTRIBUTE, OBS_PROPERTY_ADDED, OBS_PROPERTY_CHANGED -> action == RuntimeSemanticAction.ATTRIBUTE_STATE_SET;
                    case OBS_PROPERTY_REMOVED -> action == RuntimeSemanticAction.ATTRIBUTE_STATE_UNSET;
                    default -> false;
                };
                if (!sourceCompatible) fail(rule, "SOURCE_SEMANTICS_CONFLICT", "eventKind/action");
            }
            binding.validate(rule);
            if (action.mutates()) {
                Set<String> required = switch (action) {
                    case ATTRIBUTE_STATE_SET -> Set.of("attribute", "valueType", "value");
                    case ATTRIBUTE_STATE_UNSET -> Set.of("attribute");
                    case OBJECT_AVAILABLE -> Set.of("useClass", "useObject");
                    case RELATION_INSERT, RELATION_DELETE -> Set.of("association", "participants");
                    case RELATION_REORDER -> Set.of("orders");
                    case OPERATION_ENTER -> Set.of("operation", "arguments");
                    case OPERATION_FAIL -> Set.of("error");
                    default -> Set.of();
                };
                if (!Set.copyOf(rule.payload()).equals(required)) fail(rule, "PAYLOAD_CONFLICT", "payload");
            }
        }
    }
    private void fail(RuntimeMapping.Rule rule, String code, String field) {
        throw new RuntimeMappingException("RUNTIME_MAPPING_" + code, rule.id(), field);
    }
}
