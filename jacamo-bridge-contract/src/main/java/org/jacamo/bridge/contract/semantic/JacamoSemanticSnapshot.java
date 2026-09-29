package org.jacamo.bridge.contract.semantic;

import java.util.List;

/** Versioned, immutable, code-grounded semantic contract. */
public record JacamoSemanticSnapshot(
        String contractVersion,
        JcmSemanticContract.ProjectSemantic project,
        List<JcmSemanticContract.AgentDeclarationSemantic> agentDeclarations,
        List<JcmSemanticContract.WorkspaceDeclarationSemantic> workspaceDeclarations,
        List<JcmSemanticContract.ArtifactDeclarationSemantic> artifactDeclarations,
        List<JcmSemanticContract.OrganizationDeploymentSemantic> organizationDeployments,
        List<JcmSemanticContract.GroupDeploymentSemantic> groupDeployments,
        List<JcmSemanticContract.SchemeDeploymentSemantic> schemeDeployments,
        List<JcmSemanticContract.InstitutionDeploymentSemantic> institutionDeployments,
        List<JcmSemanticContract.AgentRoleTupleSemantic> rawRoleTuples,
        List<JcmSemanticContract.AgentFocusTupleSemantic> rawFocusTuples,
        List<JcmSemanticContract.ImportProvenanceSemantic> importProvenance,
        List<JasonSemanticContract.AgentProgramSemantic> jasonPrograms,
        List<CartagoSemanticContract.EnvironmentSemantic> cartagoEnvironments,
        List<MoiseSemanticContract.OrganizationSemantic> moiseOrganizations,
        List<CrossSemanticContract.ExactBindingSemantic> exactBindings,
        List<String> diagnostics) {
    public static final String CURRENT_VERSION = "1.0.0";
    public JacamoSemanticSnapshot {
        contractVersion=SemanticSupport.required(contractVersion,"contractVersion");
        agentDeclarations=List.copyOf(agentDeclarations); workspaceDeclarations=List.copyOf(workspaceDeclarations);
        artifactDeclarations=List.copyOf(artifactDeclarations); organizationDeployments=List.copyOf(organizationDeployments);
        groupDeployments=List.copyOf(groupDeployments); schemeDeployments=List.copyOf(schemeDeployments);
        institutionDeployments=List.copyOf(institutionDeployments); rawRoleTuples=List.copyOf(rawRoleTuples);
        rawFocusTuples=List.copyOf(rawFocusTuples); importProvenance=List.copyOf(importProvenance);
        jasonPrograms=List.copyOf(jasonPrograms); cartagoEnvironments=List.copyOf(cartagoEnvironments);
        moiseOrganizations=List.copyOf(moiseOrganizations); exactBindings=List.copyOf(exactBindings);
        diagnostics=List.copyOf(diagnostics);
    }
    public static JacamoSemanticSnapshot empty() {
        return new JacamoSemanticSnapshot(CURRENT_VERSION,null,List.of(),List.of(),List.of(),List.of(),List.of(),
                List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
    }
}
