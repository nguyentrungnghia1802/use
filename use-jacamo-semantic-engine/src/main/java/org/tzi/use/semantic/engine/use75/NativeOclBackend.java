package org.tzi.use.semantic.engine.use75;

import java.util.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.*;
import org.tzi.use.uml.ocl.value.*;

/** Model/program-scoped bounded compilation/evaluation owner; no live state writer. */
public final class NativeOclBackend implements AutoCloseable {
    public record Compiled(String owner,long epoch,OclContractSpec specification,Expression expression,Map<String,Type> variables) {
        public Compiled {variables=Map.copyOf(variables);}
    }
    public record Metrics(long compileNanos,long evaluationNanos,long evaluations,int expressions) { }
    private final MModel model;private final String program,profile,owner=UUID.randomUUID().toString();private final int capacity;
    private final Map<OclContractSpec,Compiled> cache=new LinkedHashMap<>();
    private long epoch,compileNanos,evaluationNanos,evaluations;private boolean closed;
    public NativeOclBackend(MModel model,String program,String profile){this(model,program,profile,4096);}
    public NativeOclBackend(MModel model,String program,String profile,int capacity) {
        this.model=Objects.requireNonNull(model);this.program=Objects.requireNonNull(program);this.profile=Objects.requireNonNull(profile);this.capacity=capacity;
        if(program.isBlank()||profile.isBlank()||capacity<1||capacity>4096)throw new IllegalArgumentException("OCL_OWNER_CONFIG_INVALID");
    }
    public synchronized Compiled compile(OclContractSpec specification) {
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
                if(specification.kind()==OclContractSpec.Kind.TASK_POST&&operation.hasResultType())variables.put("result",operation.resultType());
            }
            var compiled=new Compiled(owner,epoch,specification,expression,variables);cache.put(specification,compiled);return compiled;
        }finally{compileNanos+=System.nanoTime()-start;}
    }
    public synchronized NativeOclOutcome evaluate(Compiled compiled,UseStateReadView view,Map<String,Value> values,String unavailable) {
        requireOpen();Objects.requireNonNull(unavailable);
        if(!owner.equals(compiled.owner())||epoch!=compiled.epoch()||cache.get(compiled.specification())!=compiled)return NativeOclOutcome.error("OCL_COMPILED_OWNER_MISMATCH");
        // Native equality can return true for two undefined values; quality is independent.
        if(!unavailable.isEmpty())return NativeOclOutcome.skipped(unavailable);
        if(view==null)return NativeOclOutcome.skipped("OCL_STATE_UNAVAILABLE");
        if(view.after().system().model()!=model)return NativeOclOutcome.error("OCL_STATE_MODEL_MISMATCH");
        if(compiled.specification().requiresBefore()&&view.before()==null)return NativeOclOutcome.skipped("POST_MATCHING_PRE_UNAVAILABLE");
        var bindings=new VarBindings();
        for(var variable:compiled.variables().entrySet()) {
            Value value=values.get(variable.getKey());
            if(value==null)return NativeOclOutcome.skipped("OCL_BINDING_UNAVAILABLE:"+variable.getKey());
            if(!value.isUndefined()&&!value.type().conformsTo(variable.getValue()))return NativeOclOutcome.error("OCL_BINDING_TYPE_MISMATCH:"+variable.getKey());
            if(!belongs(value,view))return NativeOclOutcome.error("OCL_BINDING_IDENTITY_MISMATCH:"+variable.getKey());
            bindings.push(variable.getKey(),value);
        }
        long start=System.nanoTime();
        try{return NativeOclOutcome.evaluate(compiled.expression(),view.before()==null?view.after():view.before(),view.after(),bindings);}
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
}
