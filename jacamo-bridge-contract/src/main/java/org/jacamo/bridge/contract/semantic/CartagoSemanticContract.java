package org.jacamo.bridge.contract.semantic;

/** Typed CArtAgO contract surface. Population remains capability-gated in later phases. */
public final class CartagoSemanticContract {
    private CartagoSemanticContract() { }
    public record EnvironmentSemantic(SemanticNode value) { }
    public record WorkspaceSemantic(SemanticNode value) { }
    public record ArtifactTypeSemantic(SemanticNode value) { }
    public record ArtifactSemantic(SemanticNode value) { }
    public record OperationDescriptorSemantic(SemanticNode value) { }
    public record BackingJavaOperationSemantic(SemanticNode value) { }
    public record GuardSemantic(SemanticNode value) { }
    public record LiveObservablePropertySemantic(SemanticNode value) { }
    public record ObservablePropertySnapshotSemantic(SemanticNode value) { }
    public record ArtifactInfoSemantic(SemanticNode value) { }
    public record SignalSemantic(SemanticNode value) { }
    public record CartagoAgentIdentitySemantic(SemanticNode value) { }
}
