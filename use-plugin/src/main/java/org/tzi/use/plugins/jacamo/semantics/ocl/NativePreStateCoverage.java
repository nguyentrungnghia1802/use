package org.tzi.use.plugins.jacamo.semantics.ocl;

import java.io.*;
import java.util.*;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystemState;

/** Availability of actual @pre attribute receivers/values. Equality with native
 * undefined can be false; that value must not certify an unobserved pre-state.
 * Evaluation remains the native USE evaluator, without expression rewriting. */
public final class NativePreStateCoverage {
    private NativePreStateCoverage() { }
    public static List<ExpAttrOp> attributeReads(Expression expression) {
        var reads=new ArrayList<ExpAttrOp>();expression.processWithVisitor(new ExpressionPrintVisitor(new PrintWriter(Writer.nullWriter())) {
            @Override public void visitAttrOp(ExpAttrOp expression){if(expression.isPre())reads.add(expression);super.visitAttrOp(expression);}
        });return List.copyOf(reads);
    }
    public static String unavailable(Expression expression,MSystemState before,MSystemState after,Map<String,Value> values) {
        var reads=attributeReads(expression);if(reads.isEmpty())return "";if(before==null)return "PRE_STATE_UNAVAILABLE";
        var variables=new VarBindings(after);values.forEach(variables::push);
        for(var read:reads)try {
            var value=new Evaluator().eval(read.objExp(),before,after,variables);
            if(!(value instanceof ObjectValue receiver)||before.objectByName(receiver.value().name())!=receiver.value())return "PRE_STATE_ATTRIBUTE_RECEIVER_UNAVAILABLE:"+read.attr().qualifiedName();
            var state=receiver.value().state(before);if(state==null||state.attributeValue(read.attr()).isUndefined())return "PRE_STATE_ATTRIBUTE_VALUE_UNAVAILABLE:"+read.attr().qualifiedName();
        }catch(RuntimeException unknown){return "PRE_STATE_ATTRIBUTE_RECEIVER_UNAVAILABLE:"+read.attr().qualifiedName();}
        return "";
    }
}
