package org.tzi.use.plugins.jacamo.verify.use75;

import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.sys.MSystemState;
import java.util.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.*;
import org.tzi.use.uml.ocl.value.*;

/** Model/program-scoped bounded compilation/evaluation owner; no live state writer. */
public final class NativeOclBackend implements AutoCloseable {
    public record Compiled(String owner,long epoch,Spec specification,Expression expression,Map<String,Type> variables) {
        public Compiled {variables=Map.copyOf(variables);}
    }
    public record Metrics(long compileNanos,long evaluationNanos,long evaluations,int expressions) { }
    private final MModel model;private final String program,profile,owner=UUID.randomUUID().toString();private final int capacity;
    private final Map<Spec,Compiled> cache=new LinkedHashMap<>();
    private long epoch,compileNanos,evaluationNanos,evaluations;private boolean closed;
    public NativeOclBackend(MModel model,String program,String profile){this(model,program,profile,4096);}
    public NativeOclBackend(MModel model,String program,String profile,int capacity) {
        this.model=Objects.requireNonNull(model);this.program=Objects.requireNonNull(program);this.profile=Objects.requireNonNull(profile);this.capacity=capacity;
        if(program.isBlank()||profile.isBlank()||capacity<1||capacity>4096)throw new IllegalArgumentException("OCL_OWNER_CONFIG_INVALID");
    }
    public synchronized Compiled compile(Spec specification) {
        requireOpen();if(specification.self().model()!=model||model.getClass(specification.self().name())!=specification.self())throw new IllegalArgumentException("OCL_CONTEXT_MODEL_MISMATCH");
        var existing=cache.get(specification);if(existing!=null)return existing;
        if(cache.size()>=capacity)throw new IllegalStateException("OCL_CACHE_CAPACITY_EXCEEDED");
        long start=System.nanoTime();
        try {
            var operation=specification.operation();
            if(operation!=null&&operation.cls().operation(operation.name(),false)!=operation)throw new IllegalArgumentException("OCL_OPERATION_IDENTITY_MISMATCH");
            for(var type:specification.bindings().values())if(!ownedType(type))throw new IllegalArgumentException("OCL_BINDING_TYPE_MODEL_MISMATCH");
            Expression expression=NativeOclCompilation.compile(model,specification);
            var variables=new LinkedHashMap<String,Type>();variables.put("self",specification.self());
            variables.putAll(specification.bindings());
            if(operation!=null) {
                for(int i=0;i<operation.paramList().size();i++){var parameter=operation.paramList().varDecl(i);variables.put(parameter.name(),parameter.type());}
                if(specification.kind()==Spec.Kind.TASK_POST&&operation.hasResultType())variables.put("result",operation.resultType());
            }
            var compiled=new Compiled(owner,epoch,specification,expression,variables);cache.put(specification,compiled);return compiled;
        }finally{compileNanos+=System.nanoTime()-start;}
    }
    public synchronized Outcome evaluate(Compiled compiled,UseStateReadView view,Map<String,Value> values,String unavailable) {
        requireOpen();Objects.requireNonNull(unavailable);
        if(!owner.equals(compiled.owner())||epoch!=compiled.epoch()||cache.get(compiled.specification())!=compiled)return Outcome.error("OCL_COMPILED_OWNER_MISMATCH");
        // Native equality can return true for two undefined values; quality is independent.
        if(!unavailable.isEmpty())return Outcome.skipped(unavailable);
        if(view==null)return Outcome.skipped("OCL_STATE_UNAVAILABLE");
        if(view.after().system().model()!=model)return Outcome.error("OCL_STATE_MODEL_MISMATCH");
        if(compiled.specification().requiresBefore()&&view.before()==null)return Outcome.skipped("POST_MATCHING_PRE_UNAVAILABLE");
        var bindings=new VarBindings();
        for(var variable:compiled.variables().entrySet()) {
            Value value=values.get(variable.getKey());
            if(value==null)return Outcome.skipped("OCL_BINDING_UNAVAILABLE:"+variable.getKey());
            if(!value.isUndefined()&&!value.type().conformsTo(variable.getValue()))return Outcome.error("OCL_BINDING_TYPE_MISMATCH:"+variable.getKey());
            if(!belongs(value,view))return Outcome.error("OCL_BINDING_IDENTITY_MISMATCH:"+variable.getKey());
            bindings.push(variable.getKey(),value);
        }
        long start=System.nanoTime();
        try{return Outcome.evaluate(compiled.expression(),view.before()==null?view.after():view.before(),view.after(),bindings);}
        finally{evaluationNanos+=System.nanoTime()-start;evaluations++;}
    }
    private boolean belongs(Value value,UseStateReadView view) {
        if(!ownedType(value.type()))return false;
        if(value instanceof ObjectValue object) {
            var target=object.value();
            return target.cls().model()==model&&(view.after().objectByName(target.name())==target||view.before()!=null&&view.before().objectByName(target.name())==target);
        }
        if(value instanceof CollectionValue collection)for(var child:collection.collection())if(!belongs(child,view))return false;
        if(value instanceof TupleValue tuple)for(var part:((TupleType)tuple.type()).getParts().values())if(!belongs(tuple.getElementValue(part.name()),view))return false;
        return true;
    }
    private boolean ownedType(Type type) {
        if(type instanceof MClassifier classifier)return classifier.model()==model&&model.getClassifier(classifier.name())==classifier;
        if(type instanceof EnumType enumeration)return model.enumType(enumeration.name())==enumeration;
        if(type instanceof CollectionType collection)return ownedType(collection.elemType());
        if(type instanceof TupleType tuple)return tuple.getParts().values().stream().allMatch(p->ownedType(p.type()));
        return true;
    }
    public String program(){return program;}
    public String profile(){return profile;}
    public synchronized Metrics metrics(){return new Metrics(compileNanos,evaluationNanos,evaluations,cache.size());}
    public synchronized void clear(){requireOpen();cache.clear();epoch++;}
    private void requireOpen(){if(closed)throw new IllegalStateException("OCL_BACKEND_CLOSED");}
    @Override public synchronized void close(){if(!closed){cache.clear();epoch++;closed=true;}}


    /** Source-neutral specification. Correlation and observation quality belong to the caller. */
    public record Spec(String id,Kind kind,MClass self,MOperation operation,String body,Map<String,Type> bindings) {
        public enum Kind { INVARIANT, GOAL, TASK_PRE, TASK_POST, TRANSITION }
        public Spec(String id,Kind kind,MClass self,MOperation operation,String body){this(id,kind,self,operation,body,Map.of());}
        public Spec {
            Objects.requireNonNull(id);Objects.requireNonNull(kind);Objects.requireNonNull(self);Objects.requireNonNull(body);
            bindings=Map.copyOf(bindings);
            if(bindings.containsKey("self")||bindings.containsKey("result"))throw new IllegalArgumentException("OCL_RESERVED_BINDING");
            if(id.isBlank()||body.isBlank())throw new IllegalArgumentException("OCL_CONTRACT_EMPTY");
            if(operation!=null&&operation.cls()!=self)throw new IllegalArgumentException("OCL_OPERATION_CONTEXT_MISMATCH");
            if(operation!=null&&kind!=Kind.TASK_PRE&&kind!=Kind.TASK_POST)throw new IllegalArgumentException("OCL_OPERATION_KIND_MISMATCH");
        }
        public boolean requiresBefore(){return kind==Kind.TASK_POST||kind==Kind.TRANSITION;}
    }


    /** Exact native classification; errors and unavailable evidence never become false. */
    public record Outcome(Status status,String backendValue,String diagnostic) {
        public enum Status { PASS, FAIL, UNDEFINED, ERROR, SKIPPED }
        public static Outcome skipped(String reason){return new Outcome(Status.SKIPPED,"",reason);}
        public static Outcome error(String reason){return new Outcome(Status.ERROR,"",reason);}
        public Boolean knownBoolean(){return status==Status.PASS?Boolean.TRUE:status==Status.FAIL?Boolean.FALSE:null;}
        public static Outcome evaluate(Expression expression,MSystemState before,MSystemState after,VarBindings bindings) {
            try {
                Value value=new Evaluator().eval(expression,before,after,bindings);
                if(value==null)return error("USE_OCL_BACKEND_RETURNED_NULL");
                if(value.isUndefined())return new Outcome(Status.UNDEFINED,String.valueOf(value),"USE_OCL_UNDEFINED");
                if(value instanceof BooleanValue truth)return new Outcome(truth.isTrue()?Status.PASS:Status.FAIL,value.toString(),"");
                return new Outcome(Status.ERROR,value.toString(),"OCL_BOOLEAN_VALUE_REQUIRED");
            }catch(RuntimeException invalid){return error(invalid.getClass().getName()+":"+invalid.getMessage());}
        }
    }
}
