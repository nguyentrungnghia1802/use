package org.tzi.use.plugins.jacamo.mapping;

import java.util.Map;
import java.util.stream.Collectors;

/** Reproducible textual USE backend for a structural transformation plan. */
public final class StructuralUseGenerator {
    public String generate(String requestedModelName, TransformationPlan plan) {
        StringBuilder out = new StringBuilder("model ").append(new UseNameAllocator().allocate(requestedModelName, requestedModelName)).append("\n\n");
        for (var enumeration : plan.enums()) out.append("enum ").append(enumeration.name()).append(" { ")
                .append(String.join(", ", enumeration.literals())).append(" }\n");
        Map<String, java.util.List<TargetAttributeSpec>> attributes = uniqueAttributes(plan).stream()
                .collect(Collectors.groupingBy(TargetAttributeSpec::owner));
        Map<String, java.util.List<TargetOperationSpec>> operations = uniqueOperations(plan).stream()
                .collect(Collectors.groupingBy(TargetOperationSpec::owner));
        for (TargetClassSpec spec : plan.classes()) {
            if (spec.abstractClass()) out.append("abstract ");
            out.append("class ").append(spec.name());
            if (!spec.superclasses().isEmpty()) out.append(" < ").append(String.join(",", spec.superclasses()));
            out.append('\n');
            if (!attributes.getOrDefault(spec.name(), java.util.List.of()).isEmpty()) {
                out.append("attributes\n");
                for (TargetAttributeSpec attribute : attributes.get(spec.name()))
                    out.append("  ").append(attribute.name()).append(" : ").append(attribute.type()).append('\n');
            }
            var orders = plan.orderProjections().stream().filter(p -> p.owner().equals(spec.name())).toList();
            if (!operations.getOrDefault(spec.name(), java.util.List.of()).isEmpty() || !orders.isEmpty()) {
                out.append("operations\n");
                for (var order : orders) out.append("  ").append(order.query()).append("() : Sequence(")
                    .append(order.target()).append(") = ").append(order.expression("self")).append('\n');
                for (TargetOperationSpec operation : operations.getOrDefault(spec.name(), java.util.List.of())) {
                    out.append("  ").append(operation.name()).append('(')
                            .append(operation.parameters().stream().map(p -> p.name() + " : " + p.type()).collect(Collectors.joining(", ")))
                            .append(')');
                    if (operation.returnType() != null) out.append(" : ").append(operation.returnType());
                    out.append('\n');
                }
            }
            out.append("end\n\n");
        }
        for (TargetAssociationSpec association : plan.associations()) {
            out.append(association.kind().equals("USE_COMPOSITION") ? "composition " : "association ")
                    .append(association.name()).append(" between\n");
            appendEnd(out, association.firstEnd()); appendEnd(out, association.secondEnd());
            out.append("end\n\n");
        }
        for (var order : plan.orderProjections()) {
            out.append("class ").append(order.entryClass()).append("\nattributes\n  rank : Integer\nend\n\n");
            out.append("association ").append(order.ownerAssociation()).append(" between\n  ")
                .append(order.owner()).append("[1] role owner\n  ").append(order.entryClass())
                .append("[*] role ").append(order.entriesRole()).append("\nend\n\n");
            out.append("association ").append(order.targetAssociation()).append(" between\n  ")
                .append(order.entryClass()).append("[*] role incoming_").append(order.ruleId()).append("\n  ")
                .append(order.target()).append("[1] role value\nend\n\n");
        }
        out.append("constraints\n");
        for (var order : plan.orderProjections()) {
            String rows = "self." + order.entriesRole();
            out.append("context ").append(order.owner()).append(" inv order_").append(order.ruleId()).append(":\n  ")
                .append(rows).append("->isUnique(rank) and ").append(rows).append("->isUnique(value) and ")
                .append(rows).append("->forAll(e | e.rank >= 0 and e.rank < ").append(rows).append("->size()) and ")
                .append(rows).append("->collect(value)->asSet() = self.").append(order.membershipRole()).append("->asSet()\n");
        }
        return out.toString();
    }

    private java.util.Collection<TargetAttributeSpec> uniqueAttributes(TransformationPlan plan) {
        Map<String, TargetAttributeSpec> declarations = new java.util.LinkedHashMap<>();
        for (var attribute : plan.attributes()) {
            var previous = declarations.putIfAbsent(attribute.owner() + "." + attribute.name(), attribute);
            if (previous != null && !previous.type().equals(attribute.type()))
                throw new IllegalArgumentException("PROJECTION_ATTRIBUTE_CONFLICT: " + attribute.owner() + "." + attribute.name());
        }
        return declarations.values();
    }
    private java.util.Collection<TargetOperationSpec> uniqueOperations(TransformationPlan plan) {
        Map<String, TargetOperationSpec> declarations = new java.util.LinkedHashMap<>();
        for (var operation : plan.operations()) {
            var previous = declarations.putIfAbsent(operation.owner() + "." + operation.name(), operation);
            if (previous != null && (!previous.parameters().equals(operation.parameters())
                    || !java.util.Objects.equals(previous.returnType(), operation.returnType())))
                throw new IllegalArgumentException("PROJECTION_OPERATION_CONFLICT: " + operation.owner() + "." + operation.name());
        }
        return declarations.values();
    }

    private void appendEnd(StringBuilder out, MappingModel.AssociationEnd end) {
        out.append("  ").append(end.className()).append('[').append(end.multiplicity()).append("] role ")
                .append(end.role());
        if (end.ordered()) out.append(" ordered");
        out.append('\n');
    }
}
