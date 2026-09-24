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
        classes.forEach(c -> classNames.allocate(c.name(), c.sourceIdentity()));
        structural.orderProjections().forEach(p -> classNames.allocate(p.entryClass(), p.sourceIdentity()));
        Map<String, UseNameAllocator> membersByType = new TreeMap<>();

        for (SemanticElement artifact : semantic.elements().stream().filter(e -> e.kind() == MetamodelKind.Artifact).toList()) {
            String type = text(artifact, "type");
            boolean confirmed = artifact.sourceFacts().get("artifactTypeConfirmed") instanceof AttributeValue.Bool value && value.value();
            if (type == null || !confirmed) {
                diagnostics.add(new ProjectionDiagnostic("VP001_UNRESOLVED", "VP001", artifact.id().value(),
                        "Concrete Artifact subtype is not confirmed; structural Artifact is retained"));
                continue;
            }
            String concrete = concreteByType.computeIfAbsent(type, value -> classNames.allocate(simpleName(value), value));
            if (classes.stream().noneMatch(spec -> spec.name().equals(concrete)))
                classes.add(new TargetClassSpec(concrete, false, List.of("Artifact"), type, "VP001"));
            UseNameAllocator members = membersByType.computeIfAbsent(type, ignored -> {
                var allocator = new UseNameAllocator();
                structural.attributes().stream().filter(a -> a.owner().equals("Artifact"))
                        .forEach(a -> allocator.allocate(a.name(), a.sourceIdentity()));
                structural.associations().forEach(a -> {
                    if (a.firstEnd().className().equals("Artifact")) allocator.allocate(a.secondEnd().role(), a.sourceIdentity());
                    if (a.secondEnd().className().equals("Artifact")) allocator.allocate(a.firstEnd().role(), a.sourceIdentity());
                });
                structural.orderProjections().stream().filter(p -> p.owner().equals("Artifact"))
                        .forEach(p -> { allocator.allocate(p.query(), p.sourceIdentity()); allocator.allocate(p.entriesRole(), p.sourceIdentity()); });
                return allocator;
            });
            projectMembers(semantic, artifact, type, concrete, members, attributes, operations, diagnostics);
        }
        return new TransformationPlan(classes, attributes, associations, operations, diagnostics, mapping.orderProjections(), mapping.enums());
    }

    private void projectMembers(JaCaMoSemanticModel semantic, SemanticElement artifact, String type, String concrete, UseNameAllocator memberNames,
                                List<TargetAttributeSpec> attributes, List<TargetOperationSpec> operations,
                                List<ProjectionDiagnostic> diagnostics) {
        for (var reference : artifact.references()) {
            if (reference.targetId() == null) continue;
            SemanticElement target = semantic.elements().stream().filter(e -> e.id().equals(reference.targetId())).findFirst().orElse(null);
            if (target == null) continue;
            if (target.kind() == MetamodelKind.Property && reference.feature().equals("properties")) {
                String name = text(target, "name"); String sourceType = fact(target, "resolvedType");
                String useType = useType(sourceType);
                if (name != null && useType != null && arity(target) == 1) attributes.add(new TargetAttributeSpec(concrete,
                        memberNames.allocate(name, type + "#property:" + name), useType, target.id().value(), "VP002"));
                else diagnostics.add(new ProjectionDiagnostic("VP002_UNSUPPORTED", "VP002", target.id().value(),
                        "Observable property name/type is unresolved; structural representation retained"));
            } else if (target.kind() == MetamodelKind.Operation && reference.feature().equals("operations")) {
                String rawParameters = fact(target, "parameters");
                List<TargetOperationSpec.Parameter> parameters = parseParameters(rawParameters);
                if (rawParameters != null && parameters != null && parameters.size() == arity(target)
                        && ("void".equals(fact(target, "returnType")) || useType(fact(target, "returnType")) != null)) operations.add(new TargetOperationSpec(concrete,
                        memberNames.allocate(target.name(), type + "#operation:" + target.name() + "(" + rawParameters + ")"), parameters, useType(fact(target, "returnType")),
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

    private long arity(SemanticElement element) {
        return element.attributes().get("arity") instanceof AttributeValue.IntegerNumber number ? number.value() : -1;
    }

    private String fact(SemanticElement element, String name) {
        return element.sourceFacts().get(name) instanceof AttributeValue.Text value ? value.value() : null;
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
