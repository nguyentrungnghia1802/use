package org.tzi.use.plugins.jacamo.ocl;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tzi.use.plugins.jacamo.constraint.ConstraintSpec;
import org.tzi.use.plugins.jacamo.constraint.Expression;
import org.tzi.use.plugins.jacamo.constraint.TranslationStatus;
import org.tzi.use.plugins.jacamo.mapping.StructuralUseGenerator;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlan;

/** Deterministically adds translated operation contracts and invariant profiles to a USE model. */
public final class OclGenerator {
    public GeneratedOcl generate(String modelName, TransformationPlan plan, List<ConstraintSpec> constraints,
                                 List<OclProfileLoader.LoadedProfile> profiles) {
        List<ConstraintSpec> emitted = constraints.stream().filter(c -> c.status() == TranslationStatus.EXACT
                || c.status() == TranslationStatus.SOUND_SUBSET).sorted(Comparator.comparing(ConstraintSpec::id)).toList();
        String model = new StructuralUseGenerator().generate(modelName, plan);
        Map<String, List<ConstraintSpec>> contracts = emitted.stream()
                .filter(c -> c.kind() != ConstraintSpec.Kind.INVARIANT).collect(java.util.stream.Collectors.groupingBy(
                        c -> c.contextClass() + "\0" + c.operationName(), LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        for (List<ConstraintSpec> group : contracts.values()) {
            ConstraintSpec first = group.getFirst();
            var operation = plan.operations().stream().filter(op -> op.owner().equals(first.contextClass())
                    && op.name().equals(first.operationName())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("operation not found for " + first.id()));
            String signature = "  " + operation.name() + "("
                    + operation.parameters().stream().map(p -> p.name() + " : " + p.type())
                    .collect(java.util.stream.Collectors.joining(", ")) + ")"
                    + (operation.returnType() == null ? "" : " : " + operation.returnType()) + "\n";
            String contractsText = group.stream().map(constraint -> "    "
                    + (constraint.kind() == ConstraintSpec.Kind.PRE ? "pre " : "post ") + safe(constraint.name())
                    + ": " + render(constraint.expression()) + "\n").collect(java.util.stream.Collectors.joining());
            model = insertInClass(model, first.contextClass(), signature, contractsText);
        }
        StringBuilder extra = new StringBuilder();
        for (ConstraintSpec constraint : emitted.stream().filter(c -> c.kind() == ConstraintSpec.Kind.INVARIANT).toList())
            extra.append("context ").append(constraint.contextClass()).append(" inv ").append(safe(constraint.name()))
                    .append(":\n  ").append(render(constraint.expression())).append("\n\n");
        profiles.stream().sorted(Comparator.comparing(p -> p.origin().toString())).forEach(profile ->
                extra.append(profile.content().strip()).append("\n\n"));
        model += extra;
        String manifest = emitted.stream().map(c -> c.id() + "|" + c.sourceKind() + "|" + c.status() + "|"
                + c.provenance().sourceHash() + "|" + String.join(",", c.dependencies()))
                .collect(java.util.stream.Collectors.joining("\n", "", emitted.isEmpty() ? "" : "\n"));
        manifest += profiles.stream().sorted(Comparator.comparing(p -> p.origin().toString()))
                .map(p -> "PROFILE|" + p.origin() + "|" + p.sha256() + "\n")
                .collect(java.util.stream.Collectors.joining());
        return new GeneratedOcl(model, manifest, emitted);
    }

    private String insertInClass(String model, String owner, String signature, String addition) {
        int classStart = model.indexOf("class " + owner);
        if (classStart < 0) throw new IllegalArgumentException("class not found for operation contract: " + owner);
        int classEnd = model.indexOf("\nend\n", classStart);
        int operation = model.indexOf(signature, classStart);
        if (operation < 0 || operation >= classEnd)
            throw new IllegalArgumentException("operation signature not found in class " + owner);
        int insertion = operation + signature.length();
        return model.substring(0, insertion) + addition + model.substring(insertion);
    }

    public String render(Expression expression) {
        if (expression instanceof Expression.Literal value) return value.type() == Expression.ValueType.STRING
                ? "'" + value.text().substring(1, value.text().length() - 1).replace("'", "''") + "'" : value.text();
        if (expression instanceof Expression.VariableRef value) return safe(value.name());
        if (expression instanceof Expression.PropertyRef value) return render(value.receiver()) + "." + safe(value.property())
                + (value.pre() ? "@pre" : "");
        if (expression instanceof Expression.UnaryOp value) return value.operator() + " (" + render(value.operand()) + ")";
        if (expression instanceof Expression.BinaryOp value) return "(" + render(value.left()) + " " + value.operator()
                + " " + render(value.right()) + ")";
        if (expression instanceof Expression.Call value) return safe(value.name()) + "("
                + value.arguments().stream().map(this::render).collect(java.util.stream.Collectors.joining(", ")) + ")";
        if (expression instanceof Expression.CollectionPredicate value) return render(value.source()) + "->"
                + safe(value.operation()) + "(" + safe(value.variable()) + " | " + render(value.body()) + ")";
        throw new IllegalArgumentException("unsupported expression cannot generate OCL: " + expression);
    }
    private String safe(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9_]", "_");
        return safe.matches("[A-Za-z_].*") ? safe : "_" + safe;
    }
    public record GeneratedOcl(String useModel, String provenanceManifest, List<ConstraintSpec> emitted) {
        public GeneratedOcl { emitted = List.copyOf(emitted); }
    }
}
