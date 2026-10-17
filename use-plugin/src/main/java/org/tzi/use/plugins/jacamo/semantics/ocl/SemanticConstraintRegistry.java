package org.tzi.use.plugins.jacamo.semantics.ocl;

import java.util.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Per-model cache. A different model/revision/profile cannot reuse an old AST. */
public final class SemanticConstraintRegistry {
    private final MModel model;private final String revision,profile;
    private final Map<String,CompiledSemanticConstraint> compiled=new LinkedHashMap<>();
    public SemanticConstraintRegistry(MModel model,String revision,String profile){this.model=Objects.requireNonNull(model);this.revision=Objects.requireNonNull(revision);this.profile=Objects.requireNonNull(profile);}
    public synchronized CompiledSemanticConstraint predicate(String id,String kind,MClass self,String body) {
        if(self.model()!=model)throw new IllegalArgumentException("CONSTRAINT_CONTEXT_MODEL_MISMATCH");
        String key=SemanticJson.hash(List.of(id,kind,self.name(),body,revision,profile));var existing=compiled.get(key);if(existing!=null)return existing;
        var expression=SemanticOclCompiler.predicate(model,self,id,body);var result=CompiledSemanticConstraint.of(id,kind,self.name(),body,revision,profile,expression);
        if(compiled.size()>=4096)compiled.remove(compiled.keySet().iterator().next());compiled.put(key,result);return result;
    }
    public synchronized CompiledSemanticConstraint nativeExpression(String id,String kind,MClassifier self,org.tzi.use.uml.ocl.expr.Expression expression) {
        if(self.model()!=model)throw new IllegalArgumentException("CONSTRAINT_CONTEXT_MODEL_MISMATCH");
        String body=expression.toString(),key=SemanticJson.hash(List.of(id,kind,self.name(),body,revision,profile));var existing=compiled.get(key);if(existing!=null)return existing;
        var result=CompiledSemanticConstraint.of(id,kind,self.name(),body,revision,profile,expression);if(compiled.size()>=4096)compiled.remove(compiled.keySet().iterator().next());compiled.put(key,result);return result;
    }
    public synchronized List<CompiledSemanticConstraint> inventory(){return List.copyOf(compiled.values());}
    public synchronized void clear(){compiled.clear();}
}
