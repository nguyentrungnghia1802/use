package org.tzi.use.plugins.jacamo.mapping;

import java.util.Comparator;
import java.util.List;

public record TransformationPlan(List<TargetClassSpec> classes, List<TargetAttributeSpec> attributes,
                                 List<TargetAssociationSpec> associations, List<TargetOperationSpec> operations,
                                 List<ProjectionDiagnostic> diagnostics) {
    public TransformationPlan {
        classes = classes.stream().sorted(Comparator.comparing(TargetClassSpec::name)).toList();
        attributes = attributes.stream().sorted(Comparator.comparing(TargetAttributeSpec::owner)
                .thenComparing(TargetAttributeSpec::name)).toList();
        associations = associations.stream().sorted(Comparator.comparing(TargetAssociationSpec::name)).toList();
        operations = operations.stream().sorted(Comparator.comparing(TargetOperationSpec::owner)
                .thenComparing(TargetOperationSpec::name)).toList();
        diagnostics = diagnostics.stream().sorted(Comparator.comparing(ProjectionDiagnostic::sourceIdentity)
                .thenComparing(ProjectionDiagnostic::ruleId)).toList();
    }
}
