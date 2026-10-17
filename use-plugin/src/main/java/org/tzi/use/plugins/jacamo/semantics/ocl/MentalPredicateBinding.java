package org.tzi.use.plugins.jacamo.semantics.ocl;

import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.semantics.observer.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.uml.ocl.value.*;

/** Explicit STORED membership versus the much narrower rule-free/default-BB entailment fragment. */
public final class MentalPredicateBinding {
    public enum Mode { STORED_MEMBERSHIP, GROUND_RULE_FREE_ENTAILMENT }
    public OclEvaluationAdapter.Result evaluate(NativeRuntimeMutationEngine engine,String owner,Map<String,Object> query,Mode mode,Set<String> freshAgents,boolean lost) {
        String expression;try{expression=new JasonConditionFragment().compile(query);}catch(IllegalArgumentException unsupported){return new OclEvaluationAdapter.Result(SemanticVerdict.UNSUPPORTED,"",unsupported.getMessage());}
        if(lost || !freshAgents.contains(owner))return OclEvaluationAdapter.Result.skipped("EXACT_AGENT_CYCLE_CUT_UNAVAILABLE");
        var self=engine.objectForSemanticId(MentalObservationMapper.identity(owner));if(self==null)return OclEvaluationAdapter.Result.skipped("MENTAL_OBSERVER_UNAVAILABLE");var state=engine.system().state();
        if(!BooleanValue.TRUE.equals(self.state(state).attributeValue("complete")))return OclEvaluationAdapter.Result.skipped("MENTAL_BB_CUT_INCOMPLETE");
        if(mode==Mode.GROUND_RULE_FREE_ENTAILMENT && (!new StringValue("jason.bb.DefaultBeliefBase").equals(self.state(state).attributeValue("beliefBaseClass")) || !BooleanValue.TRUE.equals(self.state(state).attributeValue("groundFactsOnly"))))return new OclEvaluationAdapter.Result(SemanticVerdict.UNSUPPORTED,"","ENTAILMENT_REQUIRES_DEFAULT_BB_AND_COMPLETE_GROUND_RULE_FREE_FACTS");
        var ast=SemanticOclCompiler.predicate(engine.system().model(),self.cls(),"mental-query",expression);var bindings=new VarBindings(state);bindings.push("self",self.value());return new OclEvaluationAdapter().evaluate(ast,state,state,bindings);
    }
}
