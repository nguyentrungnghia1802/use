package org.tzi.use.plugins.jacamo.mapping;

import java.util.Comparator;
import java.util.List;

public record TransformationPlan(List<TargetClassSpec> classes, List<TargetAttributeSpec> attributes,
                                 List<TargetAssociationSpec> associations, List<TargetOperationSpec> operations,
                                 List<ProjectionDiagnostic> diagnostics, List<OrderProjectionSpec> orderProjections,
                                 List<MappingModel.EnumMapping> enums) {
    public TransformationPlan(List<TargetClassSpec> classes, List<TargetAttributeSpec> attributes,
            List<TargetAssociationSpec> associations, List<TargetOperationSpec> operations,
            List<ProjectionDiagnostic> diagnostics) {
        this(classes, attributes, associations, operations, diagnostics, List.of(), List.of());
    }
    public TransformationPlan(List<TargetClassSpec> classes, List<TargetAttributeSpec> attributes,
            List<TargetAssociationSpec> associations, List<TargetOperationSpec> operations,
            List<ProjectionDiagnostic> diagnostics, List<OrderProjectionSpec> orderProjections) {
        this(classes, attributes, associations, operations, diagnostics, orderProjections, List.of());
    }
    public TransformationPlan {
        enums = enums.stream().sorted(Comparator.comparing(MappingModel.EnumMapping::name)).toList();
        orderProjections = orderProjections.stream().sorted(Comparator.comparing(OrderProjectionSpec::sourceIdentity)).toList();
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
