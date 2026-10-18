package org.tzi.use.plugins.jacamo.semantics.ocl;

import org.tzi.use.api.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.semantic.engine.use75.NativeOclCompilation;

/** Uses the host compiler, including actual parameter/result and @pre contexts. */
public final class SemanticOclCompiler {
    private SemanticOclCompiler() { }
    public static Expression predicate(MModel model, MClass context, String id, String body) {
        return NativeOclCompilation.predicate(model,context,id,body);
    }
    /** Pair-state predicate without adding a synthetic operation to the borrowed model. */
    public static Expression transitionPredicate(MModel model,MClass self,String id,String body) {
        return NativeOclCompilation.transitionPredicate(model,self,id,body);
    }
    public static MPrePostCondition condition(UseModelApi api, MOperation operation, String id, String body, boolean pre) {
        return NativeOclCompilation.condition(api,operation,id,body,pre);
    }
}
