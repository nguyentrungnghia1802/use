package org.tzi.use.plugins.jacamo.resolution;

import java.util.List;
import java.util.Set;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;

public record ResolutionRequest(String sourceSemanticId, String spelling, Set<MetamodelKind> targetKinds,
                                String explicitTargetId, List<String> typedScopeOwners) {
    public ResolutionRequest { targetKinds = Set.copyOf(targetKinds); typedScopeOwners = List.copyOf(typedScopeOwners); }
}
