package org.tzi.use.plugins.jacamo.semantics.ocl;

import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystemState;

/** One classification boundary for native USE values; undefined is not an ordinary false. */
public final class OclEvaluationAdapter {
    public record Result(SemanticVerdict verdict, String backendValue, String diagnostic) {
        public static Result skipped(String diagnostic) { return new Result(SemanticVerdict.SKIPPED, "", diagnostic); }
    }
    public Result evaluate(Expression expression, MSystemState before, MSystemState after, VarBindings bindings) {
        try {
            Value value = new Evaluator().eval(expression, before, after, bindings);
            if(value==null)return new Result(SemanticVerdict.ERROR,"","USE_OCL_BACKEND_RETURNED_NULL");
            if (value.isUndefined())
                return new Result(SemanticVerdict.UNDEFINED, String.valueOf(value), "USE_OCL_UNDEFINED");
            if (value instanceof BooleanValue truth)
                return new Result(truth.isTrue() ? SemanticVerdict.PASS : SemanticVerdict.FAIL, value.toString(), "");
            return new Result(SemanticVerdict.ERROR, value.toString(), "OCL_BOOLEAN_VALUE_REQUIRED");
        } catch (RuntimeException invalid) {
            return new Result(SemanticVerdict.ERROR, "", invalid.getClass().getName() + ":" + invalid.getMessage());
        }
    }
}
