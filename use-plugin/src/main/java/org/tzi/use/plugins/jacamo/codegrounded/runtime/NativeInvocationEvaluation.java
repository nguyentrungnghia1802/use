package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.OclEvaluationAdapter;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MSystemState;

/** Prepared by the native writer; evaluated on its owner's ordered lane. No live
 * engine/index/registry access and no second MSystem. */
public final class NativeInvocationEvaluation {
    private NativeInvocationEvaluation() { }
    public record Condition(String id,String kind,String context,String object,String source,String text,Expression expression,
            MSystemState before,MSystemState after,Map<String,Value> variables,String unavailable,SemanticVerdict unavailableVerdict,
            String invocation,Map<String,String> witness) {
        public Condition {variables=Map.copyOf(variables);witness=Map.copyOf(witness);if(before.system()!=after.system())throw new IllegalArgumentException("INVOCATION_STATE_OWNER_MISMATCH");}
    }
    public record Contract(String id,String task,String context,String object,String source,String expression,String invocation,
            Map<String,String> witness,boolean failed) {public Contract{witness=Map.copyOf(witness);}}
    public record Work(List<Condition> conditions,List<Contract> contracts,List<SemanticConstraintResult> fixed,long bytes) {
        public Work {conditions=List.copyOf(conditions);contracts=List.copyOf(contracts);fixed=List.copyOf(fixed);}
    }
    public static Work prepare(List<Condition> conditions,List<Contract> contracts,List<SemanticConstraintResult> fixed) {
        var states=Collections.newSetFromMap(new IdentityHashMap<MSystemState,Boolean>());conditions.forEach(c->{states.add(c.before());states.add(c.after());});long bytes=0;
        for(var state:states)bytes+=new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().exportState(state).commands().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        return new Work(conditions,contracts,fixed,bytes);
    }
    public static List<SemanticConstraintResult> evaluate(Work work,long version) {
        var rows=new ArrayList<SemanticConstraintResult>(work.fixed().stream().map(r->r.atVersion(version)).toList());
        for(var condition:work.conditions()) {
            SemanticVerdict verdict;String backend="",diagnostic=condition.unavailable();
            if(!diagnostic.isBlank())verdict=condition.unavailableVerdict();
            else {var variables=new VarBindings(condition.after());condition.variables().forEach(variables::push);var result=new OclEvaluationAdapter().evaluate(condition.expression(),condition.before(),condition.after(),variables);verdict=result.verdict();backend=result.backendValue();diagnostic=result.diagnostic();}
            rows.add(new SemanticConstraintResult(condition.id(),condition.kind(),condition.context(),condition.object(),condition.source(),condition.text(),verdict,backend,diagnostic,version,condition.invocation(),condition.witness()));
        }
        for(var contract:work.contracts()) {
            var pre=rows.stream().filter(r->r.id().equals(contract.id()+":pre")).findFirst();var post=rows.stream().filter(r->r.id().equals(contract.id()+":post")).findFirst();if(post.isEmpty())continue;
            boolean enabled=pre.isPresent()&&pre.get().verdict()==SemanticVerdict.PASS;var witness=new TreeMap<>(post.get().witness());witness.putAll(contract.witness());witness.put("targetTaskId",contract.task());witness.put("historySemantics","ORIGINAL_TASK_MARKING_IS_SEPARATE");witness.put("preVerdict",pre.map(r->r.verdict().name()).orElse("SKIPPED"));witness.put("postVerdict",contract.failed()?"SKIPPED":post.get().verdict().name());
            rows.add(new SemanticConstraintResult(contract.id(),"INVOCATION_CONFORMANCE",contract.context(),contract.object(),contract.source(),contract.expression(),!enabled?SemanticVerdict.SKIPPED:contract.failed()?SemanticVerdict.FAIL:post.get().verdict(),post.get().backendValue(),!enabled?"PRECONDITION_NOT_TRUE":contract.failed()?"SOURCE_REPORTED_OPERATION_FAILURE":post.get().diagnostic(),version,contract.invocation(),witness));
        }
        return List.copyOf(rows);
    }
}
