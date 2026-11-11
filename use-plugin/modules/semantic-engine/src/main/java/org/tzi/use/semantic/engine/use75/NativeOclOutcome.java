package org.tzi.use.semantic.engine.use75;

import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystemState;

/** Exact native classification; errors and unavailable evidence never become false. */
public record NativeOclOutcome(Status status,String backendValue,String diagnostic) {
    public enum Status { PASS, FAIL, UNDEFINED, ERROR, SKIPPED }
    public static NativeOclOutcome skipped(String reason){return new NativeOclOutcome(Status.SKIPPED,"",reason);}
    public static NativeOclOutcome error(String reason){return new NativeOclOutcome(Status.ERROR,"",reason);}
    public Boolean knownBoolean(){return status==Status.PASS?Boolean.TRUE:status==Status.FAIL?Boolean.FALSE:null;}
    public static NativeOclOutcome evaluate(Expression expression,MSystemState before,MSystemState after,VarBindings bindings) {
        try {
            Value value=new Evaluator().eval(expression,before,after,bindings);
            if(value==null)return error("USE_OCL_BACKEND_RETURNED_NULL");
            if(value.isUndefined())return new NativeOclOutcome(Status.UNDEFINED,String.valueOf(value),"USE_OCL_UNDEFINED");
            if(value instanceof BooleanValue truth)return new NativeOclOutcome(truth.isTrue()?Status.PASS:Status.FAIL,value.toString(),"");
            return new NativeOclOutcome(Status.ERROR,value.toString(),"OCL_BOOLEAN_VALUE_REQUIRED");
        }catch(RuntimeException invalid){return error(invalid.getClass().getName()+":"+invalid.getMessage());}
    }
}
