package org.tzi.use.semantic.engine.use75;

import java.util.Objects;
import java.util.Map;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.Type;

/** Source-neutral specification. Correlation and observation quality belong to the caller. */
public record OclContractSpec(String id,Kind kind,MClass self,MOperation operation,String body,Map<String,Type> bindings) {
    public enum Kind { INVARIANT, GOAL, TASK_PRE, TASK_POST, TRANSITION }
    public OclContractSpec(String id,Kind kind,MClass self,MOperation operation,String body){this(id,kind,self,operation,body,Map.of());}
    public OclContractSpec {
        Objects.requireNonNull(id);Objects.requireNonNull(kind);Objects.requireNonNull(self);Objects.requireNonNull(body);
        bindings=Map.copyOf(bindings);
        if(bindings.containsKey("self")||bindings.containsKey("result"))throw new IllegalArgumentException("OCL_RESERVED_BINDING");
        if(id.isBlank()||body.isBlank())throw new IllegalArgumentException("OCL_CONTRACT_EMPTY");
        if(operation!=null&&operation.cls()!=self)throw new IllegalArgumentException("OCL_OPERATION_CONTEXT_MISMATCH");
        if(operation!=null&&kind!=Kind.TASK_PRE&&kind!=Kind.TASK_POST)throw new IllegalArgumentException("OCL_OPERATION_KIND_MISMATCH");
    }
    public boolean requiresBefore(){return kind==Kind.TASK_POST||kind==Kind.TRANSITION;}
}
