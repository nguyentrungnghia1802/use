package org.tzi.use.plugins.jacamo.semantics.ocl;

import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.DependencyVisitor.*;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.*;

/** Exact receiver bindings for compiler-owned derived expressions. Native USE
 * evaluates the expressions; missing/unbound receivers never mean empty. */
public final class OclReadReceivers {
    private OclReadReceivers() { }
    public static List<MObject> resolve(Expression receiver,List<IteratorRange> iterators,List<DerivedFrame> frames,MSystemState state,MObject self) {
        try {
            List<Map<String,Value>> contexts=List.of(Map.of("self",self.value()));
            for(var frame:frames) {
                var next=new ArrayList<Map<String,Value>>();
                for(var variables:contexts)for(var bound:bind(frame.iterators(),variables,state)) {
                    var value=evaluate(frame.receiver(),bound,state);
                    if(!(value instanceof ObjectValue object)||state.objectByName(object.value().name())!=object.value())return null;
                    next.add(Map.of("self",value));
                    if(next.size()>4096)return null;
                }
                contexts=next;
            }
            var objects=new LinkedHashSet<MObject>();
            for(var variables:contexts)for(var bound:bind(iterators,variables,state)) {
                var value=evaluate(receiver,bound,state);
                if(!(value instanceof ObjectValue object)||state.objectByName(object.value().name())!=object.value())return null;
                objects.add(object.value());
            }
            return List.copyOf(objects);
        }catch(RuntimeException unavailable){return null;}
    }
    private static List<Map<String,Value>> bind(List<IteratorRange> iterators,Map<String,Value> initial,MSystemState state) {
        List<Map<String,Value>> bindings=List.of(initial);
        for(var iterator:iterators) {
            var next=new ArrayList<Map<String,Value>>();
            for(var previous:bindings) {
                var value=evaluate(iterator.range(),previous,state);
                if(!(value instanceof CollectionValue collection))throw new IllegalArgumentException("ITERATOR_RANGE_UNAVAILABLE");
                for(var member:collection.collection()) {
                    var bound=new HashMap<>(previous);bound.put(iterator.variable(),member);next.add(Map.copyOf(bound));
                    if(next.size()>4096)throw new IllegalArgumentException("RECEIVER_BINDING_CAPACITY");
                }
            }
            bindings=next;
        }
        return bindings;
    }
    private static Value evaluate(Expression expression,Map<String,Value> values,MSystemState state) {
        var bindings=new VarBindings(state);values.forEach(bindings::push);
        return new Evaluator().eval(expression,state,bindings);
    }
}
