package org.tzi.use.plugins.jacamo.constraint;

import java.util.List;
import org.tzi.use.plugins.jacamo.semantic.SourceProvenance;

public record ConstraintSpec(String id, SourceKind sourceKind, Kind kind, String contextClass,
                             String operationName, String name, Expression expression,
                             Expression.ValueType expectedType, TranslationStatus status,
                             SourceProvenance provenance, List<String> assumptions, List<String> dependencies) {
    public ConstraintSpec {
        assumptions = List.copyOf(assumptions);
        dependencies = List.copyOf(dependencies);
        if (id == null || id.isBlank() || contextClass == null || contextClass.isBlank())
            throw new IllegalArgumentException("constraint id and context are required");
        if ((kind == Kind.PRE || kind == Kind.POST) && (operationName == null || operationName.isBlank()))
            throw new IllegalArgumentException("operation constraint requires operationName");
    }
    public enum SourceKind { JASON_CONTEXT, CARTAGO_GUARD, CORE_OCL, CASE_OCL, EXPLICIT_CONTRACT }
    public enum Kind { INVARIANT, PRE, POST }
}
