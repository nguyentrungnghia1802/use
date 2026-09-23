package org.tzi.use.plugins.jacamo.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;
import org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticElement;

/** Builds structural declarations and evidence-gated VP001-003 projections. */
public final class TransformationPlanner {
    public TransformationPlan structuralPlan(MappingModel mapping) {
        List<TargetClassSpec> classes = new ArrayList<>();
        Map<String, List<String>> supers = new HashMap<>();
        mapping.inheritance().forEach(edge -> supers.computeIfAbsent(edge.subclass(), ignored -> new ArrayList<>())
                .add(edge.superclass()));
        for (MappingModel.ClassMapping entry : mapping.classes()) classes.add(new TargetClassSpec(entry.name(),
                entry.abstractClass(), supers.getOrDefault(entry.name(), List.of()), entry.source(), entry.id()));
        List<TargetAttributeSpec> attributes = mapping.attributes().stream().map(entry -> new TargetAttributeSpec(
                entry.owner(), entry.name(), entry.type(), entry.source(), entry.id())).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<TargetAssociationSpec> associations = mapping.associations().stream().filter(entry -> !entry.reverse()).map(entry -> new TargetAssociationSpec(
                entry.name(), entry.kind(), entry.firstEnd(), entry.secondEnd(), entry.source(), entry.id())).toList();
        return new TransformationPlan(classes, attributes, associations, List.of(), List.of(), mapping.orderProjections(), mapping.enums());
    }

    public TransformationPlan plan(JaCaMoSemanticModel semantic, MappingModel mapping) {
        var structural = structuralPlan(mapping);
        List<TargetClassSpec> classes = new ArrayList<>(structural.classes());
        List<TargetAttributeSpec> attributes = new ArrayList<>(structural.attributes());
        List<TargetAssociationSpec> associations = structural.associations();
        List<TargetOperationSpec> operations = new ArrayList<>();
        List<ProjectionDiagnostic> diagnostics = new ArrayList<>();
        UseNameAllocator classNames = new UseNameAllocator();
        Map<String, String> concreteByType = new TreeMap<>();

        for (SemanticElement artifact : semantic.elements().stream().filter(e -> e.kind() == MetamodelKind.Artifact).toList()) {
            String type = text(artifact, "className");
            boolean confirmed = artifact.attributes().get("artifactTypeConfirmed") instanceof AttributeValue.Bool value && value.value();
            if (type == null || !confirmed) {
                diagnostics.add(new ProjectionDiagnostic("VP001_UNRESOLVED", "VP001", artifact.id().value(),
                        "Concrete Artifact subtype is not confirmed; structural Artifact is retained"));
                continue;
            }
            String concrete = concreteByType.computeIfAbsent(type, value -> classNames.allocate(simpleName(value), value));
            if (classes.stream().noneMatch(spec -> spec.name().equals(concrete)))
                classes.add(new TargetClassSpec(concrete, false, List.of("Artifact"), type, "VP001"));
            projectMembers(semantic, artifact, type, concrete, attributes, operations, diagnostics);
        }
        return new TransformationPlan(classes, attributes, associations, operations, diagnostics, mapping.orderProjections(), mapping.enums());
    }

    private void projectMembers(JaCaMoSemanticModel semantic, SemanticElement artifact, String type, String concrete,
                                List<TargetAttributeSpec> attributes, List<TargetOperationSpec> operations,
                                List<ProjectionDiagnostic> diagnostics) {
        UseNameAllocator memberNames = new UseNameAllocator();
        for (var reference : artifact.references()) {
            if (reference.targetId() == null) continue;
            SemanticElement target = semantic.elements().stream().filter(e -> e.id().equals(reference.targetId())).findFirst().orElse(null);
            if (target == null) continue;
            if (target.kind() == MetamodelKind.ObsProperty) {
                String name = text(target, "Name"); String sourceType = text(target, "resolvedType");
                String useType = useType(sourceType);
                if (name != null && useType != null) attributes.add(new TargetAttributeSpec(concrete,
                        memberNames.allocate(name, target.id().value()), useType, target.id().value(), "VP002"));
                else diagnostics.add(new ProjectionDiagnostic("VP002_UNSUPPORTED", "VP002", target.id().value(),
                        "Observable property name/type is unresolved; structural representation retained"));
            } else if (isOperation(target.kind())) {
                String rawParameters = text(target, "parameters");
                List<TargetOperationSpec.Parameter> parameters = parseParameters(rawParameters);
                if (rawParameters != null && parameters != null) operations.add(new TargetOperationSpec(concrete,
                        memberNames.allocate(target.name(), target.id().value()), parameters, useType(text(target, "returnType")),
                        target.id().value(), "VP003"));
                else diagnostics.add(new ProjectionDiagnostic("VP003_UNSUPPORTED", "VP003", target.id().value(),
                        "Operation signature is unresolved; structural representation retained"));
            }
        }
    }

    private List<TargetOperationSpec.Parameter> parseParameters(String raw) {
        if (raw == null) return null;
        if (raw.isBlank()) return List.of();
        List<TargetOperationSpec.Parameter> result = new ArrayList<>();
        for (String parameter : raw.split(",")) {
            String[] parts = parameter.trim().split("\\s+");
            if (parts.length != 2 || useType(parts[0]) == null) return null;
            result.add(new TargetOperationSpec.Parameter(parts[1], useType(parts[0])));
        }
        return result;
    }

    private boolean isOperation(MetamodelKind kind) {
        return kind == MetamodelKind.Operation || kind == MetamodelKind.GuardOperation
                || kind == MetamodelKind.InternalOperation || kind == MetamodelKind.LinkedOperation;
    }

    private String text(SemanticElement element, String name) {
        return element.attributes().get(name) instanceof AttributeValue.Text value ? value.value() : null;
    }

    private String simpleName(String name) { return name.substring(name.lastIndexOf('.') + 1); }

    private String useType(String javaType) {
        if (javaType == null || javaType.equals("void")) return null;
        return switch (javaType) {
            case "String", "java.lang.String", "Character", "char" -> "String";
            case "Integer", "int", "Long", "long", "Short", "short", "Byte", "byte" -> "Integer";
            case "Boolean", "boolean" -> "Boolean";
            case "Float", "float", "Double", "double" -> "Real";
            default -> null;
        };
    }
}
