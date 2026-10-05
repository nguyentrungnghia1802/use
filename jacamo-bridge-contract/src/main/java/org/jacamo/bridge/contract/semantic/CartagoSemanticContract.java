package org.jacamo.bridge.contract.semantic;

import java.util.List;

/** Typed CArtAgO contract surface. Values are snapshots, never live CArtAgO objects. */
public final class CartagoSemanticContract {
    private CartagoSemanticContract() { }

    /** One initialized environment snapshot and all CArtAgO facts scoped to it. */
    public record EnvironmentSemantic(SemanticMetadata metadata, String name, String environmentId,
            String version, String defaultInfrastructureLayer, List<WorkspaceSemantic> workspaces,
            List<ArtifactTypeSemantic> artifactTypes, List<ArtifactSemantic> artifacts,
            List<OperationDescriptorSemantic> operations, List<BackingJavaOperationSemantic> backingOperations,
            List<GuardSemantic> guards, List<LiveObservablePropertySemantic> liveProperties,
            List<ObservablePropertySnapshotSemantic> propertySnapshots, List<ArtifactInfoSemantic> artifactInfos,
            List<SignalSemantic> signals, List<CartagoAgentIdentitySemantic> agents, List<FocusSemantic> focuses) {
        public EnvironmentSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            name = SemanticSupport.text(name); environmentId = SemanticSupport.text(environmentId);
            version = SemanticSupport.text(version); defaultInfrastructureLayer = SemanticSupport.text(defaultInfrastructureLayer);
            workspaces = List.copyOf(workspaces == null ? List.of() : workspaces);
            artifactTypes = List.copyOf(artifactTypes == null ? List.of() : artifactTypes);
            artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
            operations = List.copyOf(operations == null ? List.of() : operations);
            backingOperations = List.copyOf(backingOperations == null ? List.of() : backingOperations);
            guards = List.copyOf(guards == null ? List.of() : guards);
            liveProperties = List.copyOf(liveProperties == null ? List.of() : liveProperties);
            propertySnapshots = List.copyOf(propertySnapshots == null ? List.of() : propertySnapshots);
            artifactInfos = List.copyOf(artifactInfos == null ? List.of() : artifactInfos);
            signals = List.copyOf(signals == null ? List.of() : signals);
            agents = List.copyOf(agents == null ? List.of() : agents);
            focuses = List.copyOf(focuses == null ? List.of() : focuses);
        }
    }

    public record WorkspaceSemantic(SemanticMetadata metadata, String fullName, String name, String uuid,
            String parentSemanticId, String environmentSemanticId, boolean local, String protocol,
            String remotePath, String address) {
        public WorkspaceSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            fullName = SemanticSupport.required(fullName, "fullName"); name = SemanticSupport.text(name);
            uuid = SemanticSupport.text(uuid); parentSemanticId = SemanticSupport.text(parentSemanticId);
            environmentSemanticId = SemanticSupport.required(environmentSemanticId, "environmentSemanticId");
            protocol = SemanticSupport.text(protocol); remotePath = SemanticSupport.text(remotePath);
            address = SemanticSupport.text(address);
        }
    }

    public record ArtifactTypeSemantic(SemanticMetadata metadata, String javaClassName,
            String classLoaderIdentity) {
        public ArtifactTypeSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            javaClassName = SemanticSupport.required(javaClassName, "javaClassName");
            classLoaderIdentity = SemanticSupport.text(classLoaderIdentity);
        }
    }

    public record ArtifactSemantic(SemanticMetadata metadata, String name, String uuid,
            String artifactTypeSemanticId, String workspaceSemanticId, String creatorAgentSemanticId) {
        public ArtifactSemantic {
            metadata = SemanticSupport.required(metadata, "metadata"); name = SemanticSupport.required(name, "name");
            uuid = SemanticSupport.required(uuid, "uuid");
            artifactTypeSemanticId = SemanticSupport.required(artifactTypeSemanticId, "artifactTypeSemanticId");
            workspaceSemanticId = SemanticSupport.required(workspaceSemanticId, "workspaceSemanticId");
            creatorAgentSemanticId = SemanticSupport.text(creatorAgentSemanticId);
        }
    }

    public record OperationDescriptorSemantic(SemanticMetadata metadata, String artifactSemanticId, String keyId,
            String name, int arity, boolean dynamic, boolean linkOperation, boolean ui, boolean internal) {
        public OperationDescriptorSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            artifactSemanticId = SemanticSupport.required(artifactSemanticId, "artifactSemanticId");
            keyId = SemanticSupport.required(keyId, "keyId"); name = SemanticSupport.required(name, "name");
            if (arity < -1) throw new IllegalArgumentException("arity");
        }
    }

    public record BackingJavaOperationSemantic(SemanticMetadata metadata, String operationDescriptorId,
            String declaringClass, String methodName, List<String> parameterTypes, String returnType,
            boolean varArgs, String classLoaderIdentity) {
        public BackingJavaOperationSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            operationDescriptorId = SemanticSupport.required(operationDescriptorId, "operationDescriptorId");
            declaringClass = SemanticSupport.required(declaringClass, "declaringClass");
            methodName = SemanticSupport.required(methodName, "methodName"); parameterTypes = List.copyOf(parameterTypes);
            returnType = SemanticSupport.required(returnType, "returnType"); classLoaderIdentity = SemanticSupport.text(classLoaderIdentity);
        }
    }

    public record GuardSemantic(SemanticMetadata metadata, String operationDescriptorId, String name, int arity,
            String implementationClass) {
        public GuardSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            operationDescriptorId = SemanticSupport.required(operationDescriptorId, "operationDescriptorId");
            name = SemanticSupport.required(name, "name"); if (arity < 0) throw new IllegalArgumentException("arity");
            implementationClass = SemanticSupport.text(implementationClass);
        }
    }

    /** The audited controller API does not expose a live ObsProperty object. */
    public record LiveObservablePropertySemantic(SemanticMetadata metadata, String artifactSemanticId,
            String propertyId, String name, List<String> values, List<String> annotations) {
        public LiveObservablePropertySemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            artifactSemanticId = SemanticSupport.required(artifactSemanticId, "artifactSemanticId");
            propertyId = SemanticSupport.required(propertyId, "propertyId"); name = SemanticSupport.required(name, "name");
            values = List.copyOf(values); annotations = List.copyOf(annotations);
        }
    }

    public record ObservablePropertySnapshotSemantic(SemanticMetadata metadata, String artifactSemanticId,
            String propertyId, String name, List<String> values, List<String> valueTypes,
            List<String> annotations) {
        public ObservablePropertySnapshotSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            artifactSemanticId = SemanticSupport.required(artifactSemanticId, "artifactSemanticId");
            propertyId = SemanticSupport.required(propertyId, "propertyId"); name = SemanticSupport.required(name, "name");
            values = List.copyOf(values); valueTypes = List.copyOf(valueTypes); annotations = List.copyOf(annotations);
        }
    }

    public record ArtifactInfoSemantic(SemanticMetadata metadata, String artifactSemanticId,
            String creatorAgentSemanticId, List<String> operationSemanticIds,
            List<String> observablePropertySemanticIds, List<String> linkedArtifactSemanticIds) {
        public ArtifactInfoSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            artifactSemanticId = SemanticSupport.required(artifactSemanticId, "artifactSemanticId");
            creatorAgentSemanticId = SemanticSupport.text(creatorAgentSemanticId);
            operationSemanticIds = List.copyOf(operationSemanticIds);
            observablePropertySemanticIds = List.copyOf(observablePropertySemanticIds);
            linkedArtifactSemanticIds = List.copyOf(linkedArtifactSemanticIds);
        }
    }

    public record SignalSemantic(SemanticMetadata metadata, String artifactSemanticId, String name,
            List<String> values) {
        public SignalSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            artifactSemanticId = SemanticSupport.required(artifactSemanticId, "artifactSemanticId");
            name = SemanticSupport.required(name, "name"); values = List.copyOf(values);
        }
    }

    public record CartagoAgentIdentitySemantic(SemanticMetadata metadata, String globalId, int localId,
            String name, String role, String workspaceSemanticId) {
        public CartagoAgentIdentitySemantic {
            metadata = SemanticSupport.required(metadata, "metadata"); globalId = SemanticSupport.required(globalId, "globalId");
            if (localId < 0) throw new IllegalArgumentException("localId"); name = SemanticSupport.text(name);
            role = SemanticSupport.text(role); workspaceSemanticId = SemanticSupport.required(workspaceSemanticId, "workspaceSemanticId");
        }
    }

    /** C20 is an official observer cut or focus/unfocus event, with exact endpoint identities. */
    public record FocusSemantic(SemanticMetadata metadata, String agentSemanticId, String artifactSemanticId,
            boolean focused, long sequence) {
        public FocusSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            agentSemanticId = SemanticSupport.required(agentSemanticId, "agentSemanticId");
            artifactSemanticId = SemanticSupport.required(artifactSemanticId, "artifactSemanticId");
            if (sequence < 0) throw new IllegalArgumentException("sequence");
        }
    }
}
