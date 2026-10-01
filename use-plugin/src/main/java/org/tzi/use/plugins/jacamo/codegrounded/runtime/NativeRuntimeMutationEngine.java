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
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseModelBuilder;
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
    // Ephemeral rollback/baseline images retain exact USE object/link identity, not a history state.
    private record LinkImage(String association, List<String> objectNames, MLink original) { }
    private record ObjectImage(String className, Map<String, Value> attributes, MObject original) { }
    public record StateImage(Map<String, ObjectImage> objects, List<LinkImage> links,
                             Map<String, String> semanticNames, java.util.Set<String> tombstones) { }

    private final MSystem system;
    private final UseSystemApi api;
    private final Map<String, MObject> semanticObjectIndex;
    private final CodeGroundedRuntimeRuleRegistry registry;
    private final StateImage baseline;
    private final java.util.Set<String> tombstones = new java.util.HashSet<>();

    public NativeRuntimeMutationEngine(MSystem system, Map<String, MObject> semanticObjectIndex,
                                       CodeGroundedRuntimeRuleRegistry registry) {
        this.system = Objects.requireNonNull(system, "system");
        this.api = UseSystemApi.create(system, false);
        this.semanticObjectIndex = new LinkedHashMap<>(semanticObjectIndex);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.baseline = capture();
    }

    public MSystem system() { return system; }
    public CodeGroundedRuntimeRuleRegistry registry() { return registry; }
    public synchronized StateImage savepoint() { return capture(); }
    public synchronized void rollback(StateImage image) { restore(image); }

    /** Restores the static native state in the same MSystem before an authoritative resync. */
    public synchronized void resetToBaseline() {
        restore(baseline);
    }

    public synchronized ApplyResult apply(BridgeEntityId runtimeIdentity, BridgeRelationId binding,
                                           RuntimeFactKind factKind, ProjectionStatus projectionStatus,
                                           Completeness completeness, Map<String, Object> payload) {
        return apply(runtimeIdentity, binding, factKind, projectionStatus, completeness, payload, true);
    }

    public synchronized ApplyResult apply(BridgeEntityId runtimeIdentity, BridgeRelationId binding,
            RuntimeFactKind factKind, ProjectionStatus projectionStatus, Completeness completeness,
            Map<String, Object> payload, boolean validateStructure) {
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
            BoundTarget target;
            switch (rule.action()) {
                case UPSERT_CARTAGO_WORKSPACE -> target = upsertWorkspace(payload);
                case UPSERT_CARTAGO_AGENT_IDENTITY -> target = upsertCartagoAgent(payload);
                case UPSERT_CARTAGO_ARTIFACT -> target = upsertArtifact(payload);
                case UPSERT_CARTAGO_PROPERTY_SNAPSHOT -> target = upsertPropertySnapshot(payload);
                case APPLY_CARTAGO_PROPERTY_DELTA, DELETE_CARTAGO_ARTIFACT -> {
                    String semanticId = required(payload, "semanticId");
                    target = new BoundTarget(semanticId, requiredObject(semanticId));
                }
                default -> target = resolveTarget(runtimeIdentity, binding);
            }
            if (!rule.targetClasses().contains("*") && !rule.targetClasses().contains(target.object().cls().name()))
                throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_TARGET_CLASS_MISMATCH:" + target.object().cls().name());
            validateCartagoIdentity(rule.action(), runtimeIdentity, target, payload);
            switch (rule.action()) {
                case ATTRIBUTE_SET -> setAttribute(target.object(), payload, false);
                case ATTRIBUTE_UNSET -> setAttribute(target.object(), payload, true);
                case LINK_INSERT -> link(payload, true);
                case LINK_DELETE -> link(payload, false);
                case APPLY_CARTAGO_PROPERTY_DELTA -> propertyDelta(target.semanticId(), payload);
                case DELETE_CARTAGO_ARTIFACT -> deleteArtifact(target.semanticId());
                case UPSERT_CARTAGO_WORKSPACE, UPSERT_CARTAGO_AGENT_IDENTITY,
                        UPSERT_CARTAGO_ARTIFACT, UPSERT_CARTAGO_PROPERTY_SNAPSHOT -> { }
                case EVIDENCE_ONLY -> throw new IllegalStateException("handled above");
            }
            if (validateStructure && !structureValid()) {
                restore(before);
                return ApplyResult.rejected(rule.id(), "NATIVE_RUNTIME_STRUCTURE_INVALID");
            }
            return new ApplyResult(Status.MATERIALIZED, rule.id(), target.semanticId(), target.object().name(),
                    "NATIVE_RUNTIME_MUTATION_APPLIED");
        } catch (RuntimeException | UseApiException error) {
            try { restore(before); }
            catch (RuntimeException rollback) { error.addSuppressed(rollback); }
            return ApplyResult.rejected(rule.id(), diagnostic(error));
        }
    }

    public synchronized boolean structureValid() {
        return system.state().checkStructure(new java.io.PrintWriter(new java.io.StringWriter()));
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

    private BoundTarget upsertWorkspace(Map<String, Object> payload) throws UseApiException {
        requireStableIdentity(payload, "fullName", "uuid", "environmentSemanticId");
        String environmentSemanticId = required(payload, "environmentSemanticId");
        MObject environment = upsertObject(environmentSemanticId, "Environment",
                required(payload, "environmentId"));
        setText(environment, "name", requiredTextAllowEmpty(payload, "environmentName"));
        setText(environment, "environmentId", required(payload, "environmentId"));
        setText(environment, "version", requiredTextAllowEmpty(payload, "environmentVersion"));
        setText(environment, "defaultInfrastructureLayer",
                requiredTextAllowEmpty(payload, "defaultInfrastructureLayer"));

        String semanticId = required(payload, "semanticId");
        MObject workspace = upsertObject(semanticId, "Workspace", required(payload, "name"));
        setText(workspace, "fullName", required(payload, "fullName"));
        setText(workspace, "name", required(payload, "name"));
        setText(workspace, "uuid", required(payload, "uuid"));
        setText(workspace, "parentSemanticId", requiredTextAllowEmpty(payload, "parentSemanticId"));
        setText(workspace, "environmentSemanticId", environmentSemanticId);
        setBoolean(workspace, "local", requiredBoolean(payload, "local"));
        setText(workspace, "protocol", requiredTextAllowEmpty(payload, "protocol"));
        setText(workspace, "remotePath", requiredTextAllowEmpty(payload, "remotePath"));
        setText(workspace, "address", requiredTextAllowEmpty(payload, "address"));
        ensureLink("C13EnvironmentWorkspace", environment, workspace);
        return new BoundTarget(semanticId, workspace);
    }

    private BoundTarget upsertCartagoAgent(Map<String, Object> payload) throws UseApiException {
        requireStableIdentity(payload, "globalId", "workspaceSemanticId");
        String semanticId = required(payload, "semanticId");
        MObject agent = upsertObject(semanticId, "CartagoAgentIdentity", required(payload, "name"));
        setText(agent, "globalId", required(payload, "globalId"));
        setInteger(agent, "localId", requiredInteger(payload, "localId"));
        setText(agent, "name", required(payload, "name"));
        setText(agent, "role", requiredTextAllowEmpty(payload, "role"));
        String workspaceSemanticId = required(payload, "workspaceSemanticId");
        setText(agent, "workspaceSemanticId", workspaceSemanticId);
        ensureLink("C19WorkspaceAgent", requiredObject(workspaceSemanticId), agent);
        return new BoundTarget(semanticId, agent);
    }

    private BoundTarget upsertArtifact(Map<String, Object> payload) throws UseApiException {
        requireStableIdentity(payload, "name", "uuid", "workspaceSemanticId");
        String typeSemanticId = required(payload, "artifactTypeSemanticId");
        String typeName = required(payload, "artifactTypeJavaClassName");
        MObject type = upsertObject(typeSemanticId, "ArtifactType", typeName);
        setText(type, "javaClassName", typeName);
        setText(type, "classLoaderIdentity",
                requiredTextAllowEmpty(payload, "artifactTypeClassLoaderIdentity"));

        String semanticId = required(payload, "semanticId");
        MObject artifact = upsertObject(semanticId, "Artifact", required(payload, "name"));
        setText(artifact, "name", required(payload, "name"));
        setText(artifact, "uuid", required(payload, "uuid"));
        setText(artifact, "artifactTypeSemanticId", typeSemanticId);
        String workspaceSemanticId = required(payload, "workspaceSemanticId");
        setText(artifact, "workspaceSemanticId", workspaceSemanticId);
        setText(artifact, "creatorAgentSemanticId",
                requiredTextAllowEmpty(payload, "creatorAgentSemanticId"));
        ensureLink("C14WorkspaceArtifact", requiredObject(workspaceSemanticId), artifact);
        ensureLink("C15ArtifactType", artifact, type);
        if (payload.containsKey("properties")) propertyDelta(semanticId,
                Map.of("properties", payload.get("properties"), "removedPropertySemanticIds", List.of()));
        return new BoundTarget(semanticId, artifact);
    }

    private BoundTarget upsertPropertySnapshot(Map<String, Object> payload) throws UseApiException {
        requireStableIdentity(payload, "artifactSemanticId", "propertyId");
        List<String> values = stringList(payload, "values");
        List<String> types = stringList(payload, "valueTypes");
        stringList(payload, "annotations");
        if (values.size() != types.size()) throw new NativeRuntimeProtocolException("C09_VALUE_TYPE_ARITY_MISMATCH");
        String semanticId = required(payload, "semanticId");
        MObject property = upsertObject(semanticId, "ObservablePropertySnapshot", required(payload, "name"));
        String artifactSemanticId = required(payload, "artifactSemanticId");
        setText(property, "artifactSemanticId", artifactSemanticId);
        setText(property, "propertyId", required(payload, "propertyId"));
        setText(property, "name", required(payload, "name"));
        setText(property, "values", NativeUseModelBuilder.canonicalJson(requiredValue(payload, "values")));
        setText(property, "valueTypes", NativeUseModelBuilder.canonicalJson(requiredValue(payload, "valueTypes")));
        setText(property, "annotations", NativeUseModelBuilder.canonicalJson(requiredValue(payload, "annotations")));
        ensureLink("C17ArtifactObservableProperty", requiredObject(artifactSemanticId), property);
        return new BoundTarget(semanticId, property);
    }

    private void propertyDelta(String artifactId, Map<String, Object> payload) throws UseApiException {
        for (String id : stringList(payload, "removedPropertySemanticIds")) {
            MObject property = requiredObject(id);
            if (!property.cls().name().equals("ObservablePropertySnapshot")
                    || !new StringValue(artifactId).equals(property.state(system.state()).attributeValue("artifactSemanticId")))
                throw new NativeRuntimeProtocolException("C09_PROPERTY_OWNER_MISMATCH:" + id);
            delete(id);
        }
        Object raw = requiredValue(payload, "properties");
        if (!(raw instanceof List<?> properties)) throw new NativeRuntimeProtocolException("C09_TYPED_PROPERTIES_REQUIRED");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Object item : properties) {
            if (!(item instanceof Map<?, ?> map) || map.keySet().stream().anyMatch(key -> !(key instanceof String)))
                throw new NativeRuntimeProtocolException("C09_TYPED_PROPERTY_REQUIRED");
            @SuppressWarnings("unchecked") Map<String, Object> property = (Map<String, Object>) map;
            if (!artifactId.equals(required(property, "artifactSemanticId")) || !ids.add(required(property, "semanticId")))
                throw new NativeRuntimeProtocolException("C09_PROPERTY_IDENTITY_MISMATCH");
            upsertPropertySnapshot(property);
        }
    }

    private void requireStableIdentity(Map<String, Object> payload, String... attributes) {
        MObject existing = semanticObjectIndex.get(required(payload, "semanticId"));
        if (existing == null) return;
        for (String attribute : attributes)
            if (!new StringValue(required(payload, attribute)).equals(existing.state(system.state()).attributeValue(attribute)))
                throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_INCARNATION_REDIRECT:" + attribute);
    }

    private String attribute(MObject object, String name) {
        Value value = object.state(system.state()).attributeValue(name);
        if (!(value instanceof StringValue text)) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_IDENTITY_UNDEFINED:" + name);
        return text.value();
    }

    private void validateCartagoIdentity(CodeGroundedRuntimeRuleRegistry.Action action, BridgeEntityId id,
            BoundTarget target, Map<String, Object> payload) {
        String kind, scope, local, incarnation;
        switch (action) {
            case UPSERT_CARTAGO_WORKSPACE -> {
                kind = "workspace"; scope = required(payload, "environmentId");
                local = attribute(target.object(), "fullName"); incarnation = attribute(target.object(), "uuid");
            }
            case UPSERT_CARTAGO_AGENT_IDENTITY -> {
                kind = "agent"; scope = attribute(requiredObject(attribute(target.object(), "workspaceSemanticId")), "fullName");
                local = attribute(target.object(), "globalId"); incarnation = Long.toString(requiredInteger(payload, "localId"));
            }
            case UPSERT_CARTAGO_ARTIFACT, APPLY_CARTAGO_PROPERTY_DELTA, DELETE_CARTAGO_ARTIFACT -> {
                kind = "artifact"; scope = attribute(requiredObject(attribute(target.object(), "workspaceSemanticId")), "fullName");
                local = attribute(target.object(), "name"); incarnation = attribute(target.object(), "uuid");
            }
            case UPSERT_CARTAGO_PROPERTY_SNAPSHOT -> {
                kind = "observable-property-snapshot"; scope = attribute(target.object(), "artifactSemanticId");
                local = attribute(target.object(), "propertyId"); incarnation = "snapshot";
            }
            default -> { return; } // Generic mutations require their explicit runtime-model-binding instead.
        }
        if (id == null || !id.equals(new BridgeEntityId("cartago", "environment", kind, scope, local, incarnation)))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_TYPED_IDENTITY_MISMATCH:" + kind);
    }

    private void deleteArtifact(String semanticId) throws UseApiException {
        List<String> properties = semanticObjectIndex.entrySet().stream()
                .filter(entry -> entry.getValue().cls().name().equals("ObservablePropertySnapshot")
                        && new StringValue(semanticId).equals(entry.getValue().state(system.state()).attributeValue("artifactSemanticId")))
                .map(Map.Entry::getKey).toList();
        for (String property : properties) delete(property);
        delete(semanticId);
    }

    private void delete(String semanticId) throws UseApiException {
        api.deleteObjectEx(requiredObject(semanticId));
        semanticObjectIndex.remove(semanticId);
        tombstones.add(semanticId);
    }

    private static List<String> stringList(Map<String, Object> payload, String key) {
        Object raw = requiredValue(payload, key);
        if (!(raw instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String)))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_STRING_LIST_REQUIRED:" + key);
        return list.stream().map(String.class::cast).toList();
    }

    private MObject upsertObject(String semanticId, String className, String human) throws UseApiException {
        if (tombstones.contains(semanticId)) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_DISPOSED_INCARNATION:" + semanticId);
        MObject existing = semanticObjectIndex.get(semanticId);
        if (existing != null) {
            if (!className.equals(existing.cls().name()))
                throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_OBJECT_CLASS_MISMATCH:" + semanticId);
            return existing;
        }
        var cls = system.model().getClass(className);
        if (cls == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_CLASS_MISSING:" + className);
        String objectName = NativeUseStateBuilder.objectName(className, human, semanticId);
        MObject byName = system.state().objectByName(objectName);
        if (byName != null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_OBJECT_NAME_COLLISION:" + objectName);
        MObject object = api.createObjectEx(cls, objectName);
        setText(object, "semanticId", semanticId);
        semanticObjectIndex.put(semanticId, object);
        return object;
    }

    private MObject requiredObject(String semanticId) {
        MObject object = semanticObjectIndex.get(semanticId);
        if (object == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_OBJECT_UNRESOLVED:" + semanticId);
        return object;
    }

    private void ensureLink(String associationName, MObject first, MObject second) throws UseApiException {
        MAssociation association = system.model().getAssociation(associationName);
        if (association == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ASSOCIATION_MISSING:" + associationName);
        MObject[] connected = {first, second};
        if (!system.state().hasLinkBetweenObjects(association, connected)) api.createLinkEx(association, connected);
    }

    private void setText(MObject object, String name, String value) throws UseApiException {
        api.setAttributeValueEx(object, requiredAttribute(object, name), new StringValue(value));
    }

    private void setInteger(MObject object, String name, int value) throws UseApiException {
        api.setAttributeValueEx(object, requiredAttribute(object, name), IntegerValue.valueOf(value));
    }

    private void setBoolean(MObject object, String name, boolean value) throws UseApiException {
        api.setAttributeValueEx(object, requiredAttribute(object, name), BooleanValue.get(value));
    }

    private MAttribute requiredAttribute(MObject object, String name) {
        MAttribute attribute = object.cls().attribute(name, true);
        if (attribute == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ATTRIBUTE_MISSING:" + name);
        return attribute;
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
        Map<String, ObjectImage> attributes = new LinkedHashMap<>();
        system.state().allObjects().stream().sorted(Comparator.comparing(MObject::name)).forEach(object -> {
            Map<String, Value> values = new LinkedHashMap<>();
            object.cls().allAttributes().forEach(attribute ->
                    values.put(attribute.name(), object.state(system.state()).attributeValue(attribute)));
            attributes.put(object.name(), new ObjectImage(object.cls().name(), Map.copyOf(values), object));
        });
        List<LinkImage> links = system.state().allLinks().stream()
                .sorted(Comparator.comparing((MLink link) -> link.association().name())
                        .thenComparing(link -> link.linkedObjects().stream().map(MObject::name).toList().toString()))
                .map(link -> new LinkImage(link.association().name(),
                        link.linkedObjects().stream().map(MObject::name).toList(), link)).toList();
        Map<String, String> names = new LinkedHashMap<>();
        semanticObjectIndex.forEach((id, object) -> names.put(id, object.name()));
        return new StateImage(Map.copyOf(attributes), links, Map.copyOf(names), java.util.Set.copyOf(tombstones));
    }

    private void restore(StateImage image) {
        try {
            for (MLink link : new ArrayList<>(system.state().allLinks())) api.deleteLinkEx(link);
            for (MObject object : new ArrayList<>(system.state().allObjects()))
                if (!image.objects().containsKey(object.name())) api.deleteObjectEx(object);
            for (var objectEntry : image.objects().entrySet()) {
                MObject object = system.state().objectByName(objectEntry.getKey());
                if (object != objectEntry.getValue().original()) {
                    if (object != null) api.deleteObjectEx(object);
                    object = objectEntry.getValue().original();
                    system.state().restoreObject(new org.tzi.use.uml.sys.MObjectState(object));
                }
                for (var attributeEntry : objectEntry.getValue().attributes().entrySet())
                    api.setAttributeValueEx(object, object.cls().attribute(attributeEntry.getKey(), true), attributeEntry.getValue());
            }
            semanticObjectIndex.clear();
            image.semanticNames().forEach((id, name) -> semanticObjectIndex.put(id, system.state().objectByName(name)));
            tombstones.clear(); tombstones.addAll(image.tombstones());
            for (LinkImage link : image.links()) {
                MAssociation association = system.model().getAssociation(link.association());
                if (association == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BASELINE_ASSOCIATION_MISSING:" + link.association());
                MObject[] objects = link.objectNames().stream().map(name -> system.state().objectByName(name)
                        ).toArray(MObject[]::new);
                if (java.util.Arrays.stream(objects).anyMatch(Objects::isNull))
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BASELINE_LINK_OBJECT_MISSING:" + link.association());
                if (!system.state().hasLinkBetweenObjects(association, objects)) system.state().insertLink(link.original());
            }
        } catch (UseApiException | org.tzi.use.uml.sys.MSystemException error) {
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

    private static String requiredTextAllowEmpty(Map<String, Object> payload, String name) {
        Object value = payload == null ? null : payload.get(name);
        if (!(value instanceof String text))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_PAYLOAD_REQUIRED:" + name);
        return text;
    }

    private static Object requiredValue(Map<String, Object> payload, String name) {
        Object value = payload == null ? null : payload.get(name);
        if (value == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_PAYLOAD_REQUIRED:" + name);
        return value;
    }

    private static int requiredInteger(Map<String, Object> payload, String name) {
        Object value = payload == null ? null : payload.get(name);
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue())
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_INTEGER_VALUE_INVALID:" + name);
        return number.intValue();
    }

    private static boolean requiredBoolean(Map<String, Object> payload, String name) {
        Object value = payload == null ? null : payload.get(name);
        if (!(value instanceof Boolean booleanValue))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BOOLEAN_VALUE_INVALID:" + name);
        return booleanValue;
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
