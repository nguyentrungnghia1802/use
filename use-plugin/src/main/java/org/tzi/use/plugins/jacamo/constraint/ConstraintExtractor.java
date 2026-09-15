package org.tzi.use.plugins.jacamo.constraint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tzi.use.plugins.jacamo.mapping.TargetOperationSpec;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlan;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;
import org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticElement;

/** Extracts only evidence-backed Jason/CArtAgO conditions; unsupported semantics remain explicit. */
public final class ConstraintExtractor {
    private final ConstraintExpressionParser parser = new ConstraintExpressionParser();

    public List<ConstraintSpec> extract(JaCaMoSemanticModel semantic, TransformationPlan plan,
                                        Map<String, TypeEnvironment.PropertyBinding> jasonBindings) {
        List<ConstraintSpec> result = new ArrayList<>();
        extractJason(semantic, jasonBindings, result);
        extractGuards(semantic, plan, result);
        return result.stream().sorted(java.util.Comparator.comparing(ConstraintSpec::id)).toList();
    }

    private void extractJason(JaCaMoSemanticModel semantic,
                              Map<String, TypeEnvironment.PropertyBinding> bindings, List<ConstraintSpec> result) {
        for (SemanticElement context : semantic.elements().stream().filter(e -> e.kind() == MetamodelKind.Context).toList()) {
            String source = text(context, "Expression");
            if (source == null) continue;
            var parsed = parser.parse(source, new TypeEnvironment(Map.of(), bindings));
            result.add(new ConstraintSpec("JASON-" + stable(context.id().value()), ConstraintSpec.SourceKind.JASON_CONTEXT,
                    ConstraintSpec.Kind.INVARIANT, "Plan", null, "JasonContext_" + stable(context.id().value()),
                    parsed.expression(), Expression.ValueType.BOOLEAN, parsed.status(), context.provenance().getFirst(),
                    parsed.unsupportedReasons(), List.of(context.id().value())));
        }
    }

    private void extractGuards(JaCaMoSemanticModel semantic, TransformationPlan plan, List<ConstraintSpec> result) {
        Map<String, SemanticElement> byId = semantic.elements().stream().collect(
                java.util.stream.Collectors.toMap(e -> e.id().value(), e -> e));
        for (SemanticElement operation : semantic.elements().stream().filter(e -> e.kind() == MetamodelKind.Operation).toList()) {
            var guardReference = operation.references().stream().filter(r -> r.feature().equals("guardedBy")
                    && r.targetId() != null).findFirst();
            if (guardReference.isEmpty()) continue;
            SemanticElement guard = byId.get(guardReference.get().targetId().value());
            String source = guard == null ? null : text(guard, "guardExpression");
            TargetOperationSpec target = plan.operations().stream().filter(candidate ->
                    candidate.sourceIdentity().equals(operation.id().value())).findFirst().orElse(null);
            if (guard == null || source == null || target == null) continue;
            Map<String, Expression.ValueType> variables = new LinkedHashMap<>();
            for (TargetOperationSpec.Parameter parameter : target.parameters())
                variables.put(parameter.name(), type(parameter.type()));
            var parsed = parser.parse(source, new TypeEnvironment(variables, Map.of()));
            result.add(new ConstraintSpec("GUARD-" + stable(guard.id().value()), ConstraintSpec.SourceKind.CARTAGO_GUARD,
                    ConstraintSpec.Kind.PRE, target.owner(), target.name(), "Guard_" + guard.name(), parsed.expression(),
                    Expression.ValueType.BOOLEAN, parsed.status(), guard.provenance().getFirst(),
                    parsed.unsupportedReasons(), List.of(operation.id().value(), guard.id().value())));
        }
    }

    public ConstraintSpec explicitPostcondition(String id, String owner, String operation, String name,
                                                Expression expression, SemanticElement evidence,
                                                List<String> assumptions, List<String> dependencies) {
        if (evidence == null) throw new IllegalArgumentException("source-backed postcondition evidence required");
        return new ConstraintSpec(id, ConstraintSpec.SourceKind.EXPLICIT_CONTRACT, ConstraintSpec.Kind.POST,
                owner, operation, name, expression, Expression.ValueType.BOOLEAN, TranslationStatus.EXACT,
                evidence.provenance().getFirst(), assumptions, dependencies);
    }

    private Expression.ValueType type(String useType) {
        return switch (useType) {
            case "Boolean" -> Expression.ValueType.BOOLEAN;
            case "Integer" -> Expression.ValueType.INTEGER;
            case "Real" -> Expression.ValueType.REAL;
            case "String" -> Expression.ValueType.STRING;
            default -> Expression.ValueType.UNKNOWN;
        };
    }
    private String text(SemanticElement element, String key) {
        return element.attributes().get(key) instanceof AttributeValue.Text value ? value.value() : null;
    }
    private String stable(String value) { return Integer.toUnsignedString(value.hashCode(), 36); }
}
