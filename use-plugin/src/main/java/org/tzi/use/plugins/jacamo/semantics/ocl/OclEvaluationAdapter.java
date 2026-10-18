package org.tzi.use.plugins.jacamo.semantics.ocl;

import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MSystemState;
import org.tzi.use.semantic.engine.use75.NativeOclOutcome;

/** One classification boundary for native USE values; undefined is not an ordinary false. */
public final class OclEvaluationAdapter {
    public record Result(SemanticVerdict verdict, String backendValue, String diagnostic) {
        public static Result skipped(String diagnostic) { return new Result(SemanticVerdict.SKIPPED, "", diagnostic); }
    }
    public Result evaluate(Expression expression, MSystemState before, MSystemState after, VarBindings bindings) {
        var outcome=NativeOclOutcome.evaluate(expression,before,after,bindings);
        return new Result(SemanticVerdict.valueOf(outcome.status().name()),outcome.backendValue(),outcome.diagnostic());
    }
}
