package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.BridgeRelationId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseStateBuilder;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/**
 * The native-only mutation boundary.  It owns no parallel model: every accepted mutation is
 * applied through UseSystemApi to the MSystem supplied by the native pipeline.
 */
public final class NativeRuntimeMutationEngine {
    public enum Status { MATERIALIZED, EVIDENCE_ONLY, REJECTED }

    public record ApplyResult(Status status, String ruleId, String targetSemanticId,
                              String targetUseId, String diagnostic) {
        public ApplyResult {
            Objects.requireNonNull(status, "status");
            ruleId = ruleId == null ? "" : ruleId;
            targetSemanticId = targetSemanticId == null ? "" : targetSemanticId;
            targetUseId = targetUseId == null ? "" : targetUseId;
            diagnostic = diagnostic == null ? "" : diagnostic;
        }
        public static ApplyResult evidence(String ruleId, String identity, String diagnostic) {
            return new ApplyResult(Status.EVIDENCE_ONLY, ruleId, "evidence:" + identity, "evidence", diagnostic);
        }
        public static ApplyResult rejected(String ruleId, String diagnostic) {
            return new ApplyResult(Status.REJECTED, ruleId, "", "", diagnostic);
        }
    }

    public record OclGate(boolean structureValid, boolean invariantsValid, String diagnostic) {
        public boolean passed() { return structureValid && invariantsValid; }
    }

    private record BoundTarget(String semanticId, MObject object) { }
    private record LinkImage(String association, List<String> objectNames) { }
    private record StateImage(Map<String, Map<String, Value>> attributes, List<LinkImage> links) { }

    private final MSystem system;
    private final UseSystemApi api;
    private final Map<String, MObject> semanticObjectIndex;
    private final CodeGroundedRuntimeRuleRegistry registry;
    private final StateImage baseline;

    public NativeRuntimeMutationEngine(MSystem system, Map<String, MObject> semanticObjectIndex,
                                       CodeGroundedRuntimeRuleRegistry registry) {
        this.system = Objects.requireNonNull(system, "system");
        this.api = UseSystemApi.create(system, false);
        this.semanticObjectIndex = Map.copyOf(semanticObjectIndex);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.baseline = capture();
    }

    public MSystem system() { return system; }
    public CodeGroundedRuntimeRuleRegistry registry() { return registry; }

    /** Restores the static native state in the same MSystem before an authoritative resync. */
    public synchronized void resetToBaseline() {
        restore(baseline);
    }

    public synchronized ApplyResult apply(BridgeEntityId runtimeIdentity, BridgeRelationId binding,
                                           RuntimeFactKind factKind, ProjectionStatus projectionStatus,
                                           Completeness completeness, Map<String, Object> payload) {
        String identity = runtimeIdentity == null ? "<missing>" : runtimeIdentity.canonical();
        String normalized = textOrNull(payload, "normalizedEventKind");
        CodeGroundedRuntimeRuleRegistry.Rule rule = normalized == null ? null : registry.select(factKind, normalized);
        if (rule == null) rule = registry.evidenceRule(factKind);
        if (rule != null && rule.action() == CodeGroundedRuntimeRuleRegistry.Action.EVIDENCE_ONLY)
            return ApplyResult.evidence(rule.id(), identity, "RUNTIME_FACT_EVIDENCE_ONLY");
        if (projectionStatus != ProjectionStatus.MATERIALIZED_FAITHFULLY
                || completeness != Completeness.COMPLETE)
            return ApplyResult.evidence("R-NATIVE-EVIDENCE", identity, "NON_AUTHORITATIVE_RUNTIME_FACT");
        if (normalized == null)
            return ApplyResult.evidence("R-NATIVE-EVIDENCE", identity, "NO_NATIVE_MUTATION_KIND");
        if (rule == null)
            return ApplyResult.rejected("R-NATIVE-RULE-MISSING", "NATIVE_RUNTIME_RULE_MISSING:" + factKind + ":" + normalized);

        StateImage before = capture();
        try {
            BoundTarget target = resolveTarget(runtimeIdentity, binding);
            if (!rule.targetClasses().contains("*") && !rule.targetClasses().contains(target.object().cls().name()))
                return ApplyResult.rejected(rule.id(), "NATIVE_RUNTIME_TARGET_CLASS_MISMATCH:" + target.object().cls().name());
            switch (rule.action()) {
                case ATTRIBUTE_SET -> setAttribute(target.object(), payload, false);
                case ATTRIBUTE_UNSET -> setAttribute(target.object(), payload, true);
                case LINK_INSERT -> link(payload, true);
                case LINK_DELETE -> link(payload, false);
                case EVIDENCE_ONLY -> throw new IllegalStateException("handled above");
            }
            OclGate gate = validateOcl();
            if (!gate.passed()) {
                restore(before);
                return ApplyResult.rejected(rule.id(), "NATIVE_RUNTIME_OCL_GATE_FAILED:" + gate.diagnostic());
            }
            return new ApplyResult(Status.MATERIALIZED, rule.id(), target.semanticId(), target.object().name(),
                    "NATIVE_RUNTIME_MUTATION_APPLIED");
        } catch (RuntimeException | UseApiException error) {
            try { restore(before); }
            catch (RuntimeException rollback) { error.addSuppressed(rollback); }
            return ApplyResult.rejected(rule.id(), diagnostic(error));
        }
    }

    public synchronized OclGate validateOcl() {
        java.io.StringWriter output = new java.io.StringWriter();
        java.io.PrintWriter writer = new java.io.PrintWriter(output, true);
        boolean structure = system.state().checkStructure(writer);
        boolean invariants = system.state().check(writer, false, true, true, List.of());
        return new OclGate(structure, invariants, output.toString());
    }

    private BoundTarget resolveTarget(BridgeEntityId runtimeIdentity, BridgeRelationId binding) {
        if (runtimeIdentity == null || binding == null)
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BINDING_REQUIRED");
        if (!"runtime-model-binding".equals(binding.relationKind()))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BINDING_KIND_REQUIRED");
        if (binding.endpoints().size() != 2 || !binding.endpoints().contains(runtimeIdentity))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BINDING_ENDPOINT_INVALID");
        BridgeEntityId staticIdentity = binding.endpoints().stream()
                .filter(value -> !value.equals(runtimeIdentity)).findFirst().orElseThrow();
        MObject target = semanticObjectIndex.get(staticIdentity.canonical());
        if (target == null)
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_TRACE_TARGET_REQUIRED:" + staticIdentity.canonical());
        return new BoundTarget(staticIdentity.canonical(), target);
    }

    private void setAttribute(MObject object, Map<String, Object> payload, boolean unset) throws UseApiException {
        String name = required(payload, "attribute");
        MAttribute attribute = object.cls().attribute(name, true);
        if (attribute == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ATTRIBUTE_MISSING:" + name);
        Value value = unset ? UndefinedValue.instance : value(payload);
        if (!value.isUndefined() && !value.type().conformsTo(attribute.type()))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ATTRIBUTE_TYPE_MISMATCH:" + name);
        api.setAttributeValueEx(object, attribute, value);
    }

    private void link(Map<String, Object> payload, boolean insert) throws UseApiException {
        String associationName = required(payload, "association");
        Object raw = payload.get("participantSemanticIds");
        if (!(raw instanceof List<?> ids) || ids.size() < 2)
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_PARTICIPANT_IDS_REQUIRED");
        List<MObject> objects = new ArrayList<>();
        for (Object id : ids) {
            if (!(id instanceof String semanticId) || semanticId.isBlank())
                throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_PARTICIPANT_ID_INVALID");
            MObject object = semanticObjectIndex.get(semanticId);
            if (object == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_PARTICIPANT_UNRESOLVED:" + semanticId);
            objects.add(object);
        }
        MAssociation association = system.model().getAssociation(associationName);
        if (association == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ASSOCIATION_MISSING:" + associationName);
        MObject[] connected = objects.toArray(MObject[]::new);
        boolean present = system.state().hasLinkBetweenObjects(association, connected);
        if (insert && !present) api.createLinkEx(association, connected);
        if (!insert && present) api.deleteLinkEx(association, connected);
    }

    private StateImage capture() {
        Map<String, Map<String, Value>> attributes = new LinkedHashMap<>();
        system.state().allObjects().stream().sorted(Comparator.comparing(MObject::name)).forEach(object -> {
            Map<String, Value> values = new LinkedHashMap<>();
            object.cls().allAttributes().forEach(attribute ->
                    values.put(attribute.name(), object.state(system.state()).attributeValue(attribute)));
            attributes.put(object.name(), Map.copyOf(values));
        });
        List<LinkImage> links = system.state().allLinks().stream()
                .sorted(Comparator.comparing((MLink link) -> link.association().name())
                        .thenComparing(link -> link.linkedObjects().stream().map(MObject::name).toList().toString()))
                .map(link -> new LinkImage(link.association().name(),
                        link.linkedObjects().stream().map(MObject::name).toList())).toList();
        return new StateImage(Map.copyOf(attributes), links);
    }

    private void restore(StateImage image) {
        try {
            for (MLink link : new ArrayList<>(system.state().allLinks())) api.deleteLinkEx(link);
            for (var objectEntry : image.attributes().entrySet()) {
                MObject object = system.state().objectByName(objectEntry.getKey());
                if (object == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BASELINE_OBJECT_MISSING:" + objectEntry.getKey());
                for (var attributeEntry : objectEntry.getValue().entrySet())
                    api.setAttributeValueEx(object, object.cls().attribute(attributeEntry.getKey(), true), attributeEntry.getValue());
            }
            for (LinkImage link : image.links()) {
                MAssociation association = system.model().getAssociation(link.association());
                if (association == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BASELINE_ASSOCIATION_MISSING:" + link.association());
                MObject[] objects = link.objectNames().stream().map(name -> system.state().objectByName(name)
                        ).toArray(MObject[]::new);
                if (java.util.Arrays.stream(objects).anyMatch(Objects::isNull))
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BASELINE_LINK_OBJECT_MISSING:" + link.association());
                if (!system.state().hasLinkBetweenObjects(association, objects)) api.createLinkEx(association, objects);
            }
        } catch (UseApiException error) {
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BASELINE_RESTORE_FAILED", error);
        }
    }

    private Value value(Map<String, Object> payload) {
        String type = required(payload, "valueType");
        Object raw = payload.get("value");
        if (raw == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_VALUE_REQUIRED");
        return switch (type) {
            case "STRING" -> stringValue(raw);
            case "BOOLEAN" -> booleanValue(raw);
            case "INTEGER" -> integer(raw);
            case "REAL" -> real(raw);
            case "UNDEFINED" -> UndefinedValue.instance;
            default -> throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_VALUE_TYPE_UNSUPPORTED:" + type);
        };
    }

    private Value stringValue(Object raw) {
        if (!(raw instanceof String value))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_STRING_VALUE_REQUIRED");
        return new StringValue(value);
    }

    private Value booleanValue(Object raw) {
        if (!(raw instanceof Boolean value))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BOOLEAN_VALUE_REQUIRED");
        return BooleanValue.get(value);
    }

    private Value integer(Object raw) {
        if (!(raw instanceof Number number) || number.doubleValue() != number.longValue())
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_INTEGER_VALUE_INVALID");
        long value = number.longValue();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_INTEGER_VALUE_OVERFLOW");
        return IntegerValue.valueOf((int) value);
    }

    private Value real(Object raw) {
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue()))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_REAL_VALUE_INVALID");
        return new RealValue(number.doubleValue());
    }

    private static String required(Map<String, Object> payload, String name) {
        String value = textOrNull(payload, name);
        if (value == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_PAYLOAD_REQUIRED:" + name);
        return value;
    }

    private static String textOrNull(Map<String, Object> payload, String name) {
        Object value = payload == null ? null : payload.get(name);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static String diagnostic(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ":" + message);
    }
}
