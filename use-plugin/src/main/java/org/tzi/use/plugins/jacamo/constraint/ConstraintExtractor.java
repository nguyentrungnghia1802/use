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
        for (SemanticElement context : semantic.elements().stream().filter(e -> e.kind() == MetamodelKind.Plan).toList()) {
            String source = text(context, "context");
            if (source == null) continue;
            result.add(new ConstraintSpec("JASON-" + stable(context.id().value()), ConstraintSpec.SourceKind.JASON_CONTEXT,
                    ConstraintSpec.Kind.INVARIANT, "Plan", null, "JasonContext_" + stable(context.id().value()),
                    new Expression.Unknown(source,"UNSUPPORTED_JASON_APPLICABILITY: plan context is not a global invariant"),
                    Expression.ValueType.BOOLEAN, TranslationStatus.UNSUPPORTED, context.provenance().getFirst(),
                    List.of("UNSUPPORTED_JASON_APPLICABILITY: exact predicate bindings alone do not establish an invariant checkpoint"), List.of(context.id().value())));
        }
    }

    private void extractGuards(JaCaMoSemanticModel semantic, TransformationPlan plan, List<ConstraintSpec> result) {
        for (SemanticElement operation : semantic.elements().stream().filter(e -> e.kind() == MetamodelKind.Operation).toList()) {
            String guardName = fact(operation, "guardedBy");
            if (guardName == null) continue;
            var owners = semantic.elements().stream().filter(e -> e.kind() == MetamodelKind.Artifact
                    && e.references().stream().anyMatch(r -> r.feature().equals("operations") && operation.id().equals(r.targetId()))).toList();
            if (owners.size() != 1) continue;
            SemanticElement artifact = owners.getFirst();
            String prefix = "guard:" + guardName + ":";
            String guardSource = fact(artifact, prefix + "source");
            if (guardSource == null) continue;
            var provenance = artifact.provenance().stream().filter(p -> p.originalSpelling().equals(guardSource)).findFirst();
            if (provenance.isEmpty()) continue;
            String guardId = artifact.id().value() + "#sourceFact:" + prefix;
            String parameters = fact(artifact, prefix + "parameters");
            String source = fact(artifact, prefix + "expression");
            TargetOperationSpec target = plan.operations().stream().filter(candidate ->
                    candidate.sourceIdentity().equals(operation.id().value())).findFirst().orElse(null);
            if (target == null) continue;
            if (source == null) source = "UNSUPPORTED_GUARD_BODY: no pure return expression";
            if (!java.util.Objects.equals(parameters, fact(operation,"parameters"))
                    || !"boolean".equals(fact(artifact, prefix + "returnType")))
                source = "UNSUPPORTED_GUARD_SIGNATURE: exact primitive parameter order/names and boolean return required";
            Map<String, Expression.ValueType> variables = new LinkedHashMap<>();
            for (TargetOperationSpec.Parameter parameter : target.parameters())
                variables.put(parameter.name(), primitiveParameter(parameters, parameter.name())
                        ? type(parameter.type()) : Expression.ValueType.UNKNOWN);
            var parsed = parser.parse(source, new TypeEnvironment(variables, Map.of()));
            // Java integer overflow, truncating division, floating-point and reference/string equality
            // are not equivalent to mathematical OCL. Only the proven scalar condition subset emits.
            if (parsed.status()==TranslationStatus.EXACT && (ConstraintExpressionParser.validate(parsed.expression()) != Expression.ValueType.BOOLEAN || !safeJavaCondition(parsed.expression())))
                parsed=new ConstraintExpressionParser.Result(new Expression.Unknown(source,"UNSUPPORTED_JAVA_VALUE_SEMANTICS"),
                    TranslationStatus.UNSUPPORTED,List.of("UNSUPPORTED_JAVA_VALUE_SEMANTICS: arithmetic/reference equality requires an explicit contract"));
            result.add(new ConstraintSpec("GUARD-" + stable(operation.id().value()) + "-" + stable(guardId), ConstraintSpec.SourceKind.CARTAGO_GUARD,
                    ConstraintSpec.Kind.PRE, target.owner(), target.name(), "Guard_" + guardName, parsed.expression(),
                    Expression.ValueType.BOOLEAN, parsed.status(), provenance.get(),
                    parsed.unsupportedReasons(), List.of(operation.id().value(), guardId)));
        }
    }

    private boolean primitiveParameter(String parameters, String name) {
        return parameters != null && java.util.Arrays.stream(parameters.split(",")).anyMatch(p ->
                p.strip().matches("(?:int|boolean) +" + java.util.regex.Pattern.quote(name)));
    }

    private boolean safeJavaCondition(Expression expression) {
        if (expression instanceof Expression.Literal literal) return literal.type()==Expression.ValueType.BOOLEAN || literal.type()==Expression.ValueType.INTEGER;
        if (expression instanceof Expression.VariableRef variable) return variable.type()==Expression.ValueType.BOOLEAN || variable.type()==Expression.ValueType.INTEGER;
        if (expression instanceof Expression.UnaryOp unary) return unary.operator().equals("not") && safeJavaCondition(unary.operand());
        if (expression instanceof Expression.BinaryOp binary) return List.of("and","or","=","<>",">","<",">=","<=").contains(binary.operator())
            && safeJavaCondition(binary.left()) && safeJavaCondition(binary.right());
        return false;
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
    private String fact(SemanticElement element, String key) {
        return element.sourceFacts().get(key) instanceof AttributeValue.Text value ? value.value() : null;
    }
    private String text(SemanticElement element, String key) {
        return element.attributes().get(key) instanceof AttributeValue.Text value ? value.value() : null;
    }
    private String stable(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
