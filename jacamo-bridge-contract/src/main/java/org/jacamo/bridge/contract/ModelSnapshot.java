package org.jacamo.bridge.contract;

import java.util.List;
import java.util.Map;

/** Deterministic model snapshot produced from official JaCaMo-side authorities. */
public record ModelSnapshot(
        String modelRevision,
        List<ModelFact> sources,
        List<ModelFact> agentDeclarations,
        List<ModelFact> workspaces,
        List<ModelFact> configuredArtifacts,
        List<ModelFact> organisationFacts,
        List<RelationCardinality> groupRoleCardinalities,
        List<RelationCardinality> parentSubGroupCardinalities,
        List<ModelFact> crossDimensionalRelations,
        List<UnresolvedFact> unresolvedFacts,
        Map<String, String> projectionProvenance) {
    public ModelSnapshot {
        modelRevision = ContractSupport.required(modelRevision, "modelRevision");
        sources = ContractSupport.list(sources);
        agentDeclarations = ContractSupport.list(agentDeclarations);
        workspaces = ContractSupport.list(workspaces);
        configuredArtifacts = ContractSupport.list(configuredArtifacts);
        organisationFacts = ContractSupport.list(organisationFacts);
        groupRoleCardinalities = ContractSupport.list(groupRoleCardinalities);
        parentSubGroupCardinalities = ContractSupport.list(parentSubGroupCardinalities);
        crossDimensionalRelations = ContractSupport.list(crossDimensionalRelations);
        unresolvedFacts = ContractSupport.list(unresolvedFacts);
        projectionProvenance = ContractSupport.map(projectionProvenance);
    }
}
