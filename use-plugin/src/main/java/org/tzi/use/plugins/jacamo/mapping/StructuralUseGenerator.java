package org.tzi.use.plugins.jacamo.mapping;

import java.util.Map;
import java.util.stream.Collectors;

/** Reproducible textual USE backend for a structural transformation plan. */
public final class StructuralUseGenerator {
    public String generate(String requestedModelName, TransformationPlan plan) {
        StringBuilder out = new StringBuilder("model ").append(new UseNameAllocator().allocate(requestedModelName, requestedModelName)).append("\n\n");
        Map<String, java.util.List<TargetAttributeSpec>> attributes = plan.attributes().stream()
                .collect(Collectors.groupingBy(TargetAttributeSpec::owner));
        Map<String, java.util.List<TargetOperationSpec>> operations = plan.operations().stream()
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
            if (!operations.getOrDefault(spec.name(), java.util.List.of()).isEmpty()) {
                out.append("operations\n");
                for (TargetOperationSpec operation : operations.get(spec.name())) {
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
        out.append("constraints\n");
        return out.toString();
    }

    private void appendEnd(StringBuilder out, MappingModel.AssociationEnd end) {
        out.append("  ").append(end.className()).append('[').append(end.multiplicity()).append("] role ")
                .append(end.role());
        if (end.ordered()) out.append(" ordered");
        out.append('\n');
    }
}
