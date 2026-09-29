package org.jacamo.bridge.contract.semantic;

import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.CapabilityStatus;

/** Typed JaCaMo project/deployment facts. They are not live Jason/CArtAgO/Moise objects. */
public final class JcmSemanticContract {
    private JcmSemanticContract() { }

    public record ProjectSemantic(SemanticMetadata metadata, String name, String sourceDigest) {
        public ProjectSemantic { metadata=SemanticSupport.required(metadata,"metadata"); name=SemanticSupport.required(name,"name"); sourceDigest=SemanticSupport.required(sourceDigest,"sourceDigest"); }
    }
    public record AgentDeclarationSemantic(SemanticMetadata metadata, String name, String sourceUri,
            Map<String,String> options, List<String> architectureClasses, String agentClass,
            String beliefBaseClass, String host, int instances) {
        public AgentDeclarationSemantic { metadata=SemanticSupport.required(metadata,"metadata"); name=SemanticSupport.required(name,"name"); sourceUri=SemanticSupport.text(sourceUri); options=SemanticSupport.stringMap(options); architectureClasses=List.copyOf(architectureClasses); agentClass=SemanticSupport.text(agentClass); beliefBaseClass=SemanticSupport.text(beliefBaseClass); host=SemanticSupport.text(host); if(instances<0)throw new IllegalArgumentException("instances"); }
    }
    public record WorkspaceDeclarationSemantic(SemanticMetadata metadata, String name, String host, boolean debug) { }
    public record ArtifactDeclarationSemantic(SemanticMetadata metadata, String name, String workspace,
            String javaClass, List<String> parameters) { public ArtifactDeclarationSemantic { parameters=List.copyOf(parameters); } }
    public record OrganizationDeploymentSemantic(SemanticMetadata metadata, String name, String source,
            String institution, String debug) { }
    public record GroupDeploymentSemantic(SemanticMetadata metadata, String organization, String name,
            String type, List<String> responsibleFor) { public GroupDeploymentSemantic { responsibleFor=List.copyOf(responsibleFor); } }
    public record SchemeDeploymentSemantic(SemanticMetadata metadata, String organization, String name, String type) { }
    public record InstitutionDeploymentSemantic(SemanticMetadata metadata, String name,
            List<String> workspaces, Map<String,String> opaqueParameters) {
        public InstitutionDeploymentSemantic { workspaces=List.copyOf(workspaces); opaqueParameters=SemanticSupport.stringMap(opaqueParameters); }
    }
    /** J09: raw tuple only. X04 owns any later Agent-Role resolution. */
    public record AgentRoleTupleSemantic(SemanticMetadata metadata, String agentDeclarationId,
            String organization, String group, String role, int ordinal) { }
    /** J10: raw tuple only. X06 owns any later Agent-Artifact resolution. */
    public record AgentFocusTupleSemantic(SemanticMetadata metadata, String agentDeclarationId,
            String artifact, String workspace, String namespace, int ordinal) { }
    /** J11: official-token provenance only; it never creates semantic declarations. */
    public record ImportProvenanceSemantic(SemanticMetadata metadata, String importerUri,
            String requestedPath, String canonicalPath, String sourceDigest, int ordinal,
            CapabilityStatus status) { }
}
