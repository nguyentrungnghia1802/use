package org.jacamo.bridge.adapter;

import cartago.AgentId;
import cartago.ArtifactId;
import cartago.ArtifactInfo;
import cartago.ArtifactObsProperty;
import cartago.ArtifactOpMethod;
import cartago.CartagoEnvironment;
import cartago.CartagoException;
import cartago.IArtifactGuard;
import cartago.IArtifactOp;
import cartago.ICartagoController;
import cartago.OpDescriptor;
import cartago.WorkspaceDescriptor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.Evidence;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactInfoSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactTypeSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.BackingJavaOperationSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.CartagoAgentIdentitySemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.EnvironmentSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.FocusSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.GuardSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.LiveObservablePropertySemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ObservablePropertySnapshotSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.OperationDescriptorSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.SignalSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.WorkspaceSemantic;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;

/** Read-only CArtAgO adapter. It emits exact runtime identities and never joins by display name. */
public final class OfficialCartagoAdapter {
    public EnvironmentSemantic capture(CartagoEnvironment environment) throws CartagoException {
        if (environment == null) throw new IllegalArgumentException("CARTAGO_ENVIRONMENT_REQUIRED");
        WorkspaceDescriptor root = environment.getRootWSP();
        if (root == null || root.getId() == null) throw new IllegalStateException("CARTAGO_ENVIRONMENT_NOT_INITIALIZED");
        String environmentId = String.valueOf(environment.getId());
        Evidence evidence = evidence(environment, environmentId);
        String environmentSemanticId = "cartago:environment:" + environmentId;
        SemanticMetadata environmentMetadata = metadata(environmentSemanticId, "CARTAGO_ENVIRONMENT",
                CartagoEnvironment.class.getName(), evidence, CapabilityStatus.COMPLETE, Fidelity.EXACT, List.of());

        List<WorkspaceDescriptor> descriptors = new ArrayList<>();
        collect(root, descriptors);
        descriptors.sort(Comparator.comparing(value -> value.getId().getFullName()));
        Map<String, String> workspaceIds = new LinkedHashMap<>();
        for (WorkspaceDescriptor descriptor : descriptors)
            workspaceIds.put(descriptor.getId().getFullName(), workspaceId(environmentId, descriptor.getId().getFullName(), descriptor));

        List<WorkspaceSemantic> workspaces = new ArrayList<>();
        List<CartagoAgentIdentitySemantic> agents = new ArrayList<>();
        List<ArtifactSemantic> artifacts = new ArrayList<>();
        List<OperationDescriptorSemantic> operations = new ArrayList<>();
        List<BackingJavaOperationSemantic> backingOperations = new ArrayList<>();
        List<GuardSemantic> guards = new ArrayList<>();
        List<ObservablePropertySnapshotSemantic> properties = new ArrayList<>();
        List<ArtifactInfoSemantic> infos = new ArrayList<>();
        Map<String, String> agentIds = new LinkedHashMap<>();
        Map<String, String> artifactIds = new LinkedHashMap<>();
        Map<String, ArtifactTypeSemantic> artifactTypes = new LinkedHashMap<>();

        for (WorkspaceDescriptor descriptor : descriptors) {
            String fullName = descriptor.getId().getFullName();
            String semanticId = workspaceIds.get(fullName);
            // In the audited Cartago 3.1 API, isRoot() is the historical name for
            // "has a parent"; the root descriptor has an empty Optional internally.
            String parentId = descriptor.isRoot()
                    ? workspaceIds.getOrDefault(descriptor.getParentInfo().getId().getFullName(), "") : "";
            workspaces.add(new WorkspaceSemantic(metadata(semanticId, "CARTAGO_WORKSPACE", WorkspaceDescriptor.class.getName(),
                    evidence, CapabilityStatus.COMPLETE, Fidelity.EXACT, List.of()), fullName,
                    descriptor.getId().getName(), String.valueOf(descriptor.getId().getUUID()), parentId,
                    environmentSemanticId, descriptor.isLocal(), text(descriptor.getProtocol()),
                    text(descriptor.getRemotePath()), text(descriptor.getAddress())));
            ICartagoController controller = environment.getController(fullName);
            for (AgentId agent : controller.getCurrentAgents()) {
                String agentSemanticId = agentId(environmentId, agent);
                agentIds.put(agentKey(agent), agentSemanticId);
                agents.add(new CartagoAgentIdentitySemantic(metadata(agentSemanticId, "CARTAGO_AGENT_IDENTITY",
                        AgentId.class.getName(), evidence, CapabilityStatus.COMPLETE, Fidelity.EXACT, List.of()),
                        agent.getGlobalId(), agent.getLocalId(), text(agent.getAgentName()), text(agent.getAgentRole()), semanticId));
            }
            for (ArtifactId artifact : controller.getCurrentArtifacts()) {
                ArtifactInfo info = controller.getArtifactInfo(artifact.getName());
                if (info == null || !artifact.equals(info.getId()))
                    throw new IllegalStateException("CARTAGO_ARTIFACT_ID_REVALIDATION_FAILED: " + artifact.getName());
                String artifactSemanticId = artifactId(environmentId, artifact);
                artifactIds.put(artifactKey(artifact), artifactSemanticId);
                String typeId = artifactTypeId(environmentId, artifact.getArtifactType());
                artifactTypes.putIfAbsent(typeId, artifactType(environmentId, artifact.getArtifactType(), evidence));
                String creator = artifact.getCreatorId() == null ? "" : agentIds.getOrDefault(agentKey(artifact.getCreatorId()), "");
                artifacts.add(new ArtifactSemantic(metadata(artifactSemanticId, "CARTAGO_ARTIFACT", ArtifactId.class.getName(),
                        evidence, CapabilityStatus.COMPLETE, Fidelity.EXACT, List.of()), artifact.getName(),
                        String.valueOf(artifact.getId()), typeId, semanticId, creator));
                List<String> operationIds = new ArrayList<>();
                List<String> propertyIds = new ArrayList<>();
                for (OpDescriptor descriptorValue : safe(info.getOperations())) {
                    IArtifactOp operation = descriptorValue.getOp();
                    if (operation == null || operation.getName() == null || operation.getName().isBlank())
                        throw new IllegalStateException("CARTAGO_OPERATION_NAME_UNAVAILABLE: " + descriptorValue.getKeyId());
                    String operationId = operationId(artifactSemanticId, descriptorValue.getKeyId());
                    operationIds.add(operationId);
                    operations.add(new OperationDescriptorSemantic(metadata(operationId, "CARTAGO_OPERATION_DESCRIPTOR",
                            OpDescriptor.class.getName(), evidence, CapabilityStatus.COMPLETE, Fidelity.EXACT, List.of()),
                            artifactSemanticId, descriptorValue.getKeyId(), operation.getName(), operation.getNumParameters(),
                            descriptorValue.isDynamic(), descriptorValue.isLinkOperation(), descriptorValue.isUI(), descriptorValue.isInternalOp()));
                    if (descriptorValue.getGuard() != null) {
                        IArtifactGuard guard = descriptorValue.getGuard();
                        String guardId = guardId(operationId);
                        guards.add(new GuardSemantic(metadata(guardId, "CARTAGO_GUARD", guard.getClass().getName(),
                                evidence, CapabilityStatus.COMPLETE, Fidelity.EXACT, List.of()), operationId,
                                guard.getName(), guard.getNumParameters(), guard.getClass().getName()));
                    }
                    if (operation instanceof ArtifactOpMethod method) backingOperations.add(backing(operationId, method, evidence));
                }
                for (ArtifactObsProperty property : safe(info.getObsProperties())) {
                    String propertyId = propertyId(artifactSemanticId, property.getFullId());
                    propertyIds.add(propertyId);
                    List<String> values = java.util.Arrays.stream(property.getValues()).map(OfficialCartagoAdapter::text).toList();
                    List<String> valueTypes = java.util.Arrays.stream(property.getValues())
                            .map(value -> value == null ? "NULL" : value.getClass().getName()).toList();
                    List<String> annotations = safe(property.getAnnots()).stream().map(OfficialCartagoAdapter::text).toList();
                    properties.add(new ObservablePropertySnapshotSemantic(metadata(propertyId,
                            "CARTAGO_OBSERVABLE_PROPERTY_SNAPSHOT", ArtifactObsProperty.class.getName(), evidence,
                            CapabilityStatus.COMPLETE, Fidelity.EXACT, List.of()), artifactSemanticId,
                            property.getFullId(), property.getName(), values, valueTypes, annotations));
                }
                infos.add(new ArtifactInfoSemantic(metadata("cartago:artifact-info:" + artifactSemanticId,
                        "CARTAGO_ARTIFACT_INFO", ArtifactInfo.class.getName(), evidence, CapabilityStatus.COMPLETE,
                        Fidelity.EXACT, List.of()), artifactSemanticId, creator, List.copyOf(operationIds),
                        List.copyOf(propertyIds), safe(info.getLinkedArtifacts()).stream().map(value -> artifactKey(value)).toList()));
            }
        }
        workspaces.sort(Comparator.comparing(value -> value.metadata().semanticId()));
        agents.sort(Comparator.comparing(value -> value.metadata().semanticId()));
        artifacts.sort(Comparator.comparing(value -> value.metadata().semanticId()));
        operations.sort(Comparator.comparing(value -> value.metadata().semanticId()));
        backingOperations.sort(Comparator.comparing(value -> value.metadata().semanticId()));
        guards.sort(Comparator.comparing(value -> value.metadata().semanticId()));
        properties.sort(Comparator.comparing(value -> value.metadata().semanticId()));
        infos.sort(Comparator.comparing(value -> value.metadata().semanticId()));
        List<FocusSemantic> focuses = List.of();
        List<LiveObservablePropertySemantic> liveProperties = List.of();
        List<SignalSemantic> signals = List.of();
        return new EnvironmentSemantic(environmentMetadata, text(environment.getName()), environmentId,
                text(environment.getVersion()), text(environment.getDefaultInfrastructureLayer()), workspaces,
                artifactTypes.values().stream().sorted(Comparator.comparing(value -> value.metadata().semanticId())).toList(),
                artifacts, operations, backingOperations, guards, liveProperties, properties, infos, signals, agents, focuses);
    }

    private ArtifactTypeSemantic artifactType(String environmentId, String javaClassName, Evidence evidence) {
        String id = artifactTypeId(environmentId, javaClassName);
        try {
            Class<?> type = Class.forName(javaClassName, false, Thread.currentThread().getContextClassLoader());
            String loader = type.getProtectionDomain().getCodeSource() == null ? type.getClassLoader().toString()
                    : String.valueOf(type.getProtectionDomain().getCodeSource().getLocation());
            return new ArtifactTypeSemantic(metadata(id, "CARTAGO_ARTIFACT_TYPE", javaClassName, evidence,
                    CapabilityStatus.COMPLETE, Fidelity.EXACT, List.of()), javaClassName, loader);
        } catch (Exception error) {
            return new ArtifactTypeSemantic(metadata(id, "CARTAGO_ARTIFACT_TYPE", javaClassName, evidence,
                    CapabilityStatus.PARTIAL, Fidelity.EXACT, List.of("ARTIFACT_TYPE_CLASS_NOT_LOADABLE")), javaClassName, "");
        }
    }

    private BackingJavaOperationSemantic backing(String operationId, ArtifactOpMethod operation, Evidence evidence) {
        Method method = operation.getMethod();
        String declaringClass = method.getDeclaringClass().getName();
        String id = "cartago:backing-operation:" + AdapterEvidence.digest((operationId + "|" + method).getBytes(StandardCharsets.UTF_8));
        String loader = method.getDeclaringClass().getProtectionDomain().getCodeSource() == null
                ? String.valueOf(method.getDeclaringClass().getClassLoader())
                : String.valueOf(method.getDeclaringClass().getProtectionDomain().getCodeSource().getLocation());
        return new BackingJavaOperationSemantic(metadata(id, "CARTAGO_BACKING_JAVA_OPERATION", ArtifactOpMethod.class.getName(),
                evidence, CapabilityStatus.COMPLETE, Fidelity.EXACT, List.of()), operationId, declaringClass,
                method.getName(), java.util.Arrays.stream(method.getParameterTypes()).map(Class::getName).toList(),
                method.getReturnType().getName(), method.isVarArgs(), loader);
    }

    private void collect(WorkspaceDescriptor descriptor, List<WorkspaceDescriptor> result) {
        result.add(descriptor);
        if (descriptor.getWorkspace() != null)
            for (WorkspaceDescriptor child : descriptor.getWorkspace().getChildWSPs()) collect(child, result);
    }

    private Evidence evidence(CartagoEnvironment environment, String environmentId) {
        String payload = environment.getName() + "|" + environmentId + "|" + environment.getVersion();
        String digest = AdapterEvidence.digest(payload.getBytes(StandardCharsets.UTF_8));
        return new Evidence("cartago-runtime:" + digest.substring(0, 16), "cartago.CartagoEnvironment",
                "runtime:/cartago/" + environmentId, digest, "initialized official CArtAgO environment snapshot");
    }

    private SemanticMetadata metadata(String id, String kind, String fqcn, Evidence evidence,
                                      CapabilityStatus capability, Fidelity fidelity, List<String> diagnostics) {
        return SemanticEvidence.metadata(id, kind, fqcn, EvidenceAuthority.OFFICIAL_CARTAGO_API,
                fidelity, capability, evidence, 0, 0, diagnostics);
    }

    private static String workspaceId(String environmentId, String fullName, WorkspaceDescriptor descriptor) {
        return "cartago:workspace:" + environmentId + ":" + fullName + ":" + descriptor.getId().getUUID();
    }
    private static String artifactId(String environmentId, ArtifactId id) {
        return "cartago:artifact:" + environmentId + ":" + id.getWorkspaceId().getFullName() + ":" + id.getId();
    }
    private static String operationId(String artifactId, String keyId) { return "cartago:operation:" + artifactId + ":" + keyId; }
    private static String guardId(String operationId) { return "cartago:guard:" + operationId; }
    private static String propertyId(String artifactId, String propertyId) { return "cartago:property:" + artifactId + ":" + propertyId; }
    private static String artifactTypeId(String environmentId, String type) { return "cartago:artifact-type:" + environmentId + ":" + type; }
    private static String artifactKey(ArtifactId id) { return id.getWorkspaceId().getFullName() + ":" + id.getId(); }
    private static String agentId(String environmentId, AgentId id) { return "cartago:agent:" + environmentId + ":" + id.getGlobalId() + ":" + id.getLocalId(); }
    private static String agentKey(AgentId id) { return id.getGlobalId() + ":" + id.getLocalId(); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static <T> List<T> safe(List<T> value) { return value == null ? List.of() : value; }
}
