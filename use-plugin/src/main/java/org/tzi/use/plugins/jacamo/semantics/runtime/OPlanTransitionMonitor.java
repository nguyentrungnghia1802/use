package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.model.ExecutionDescriptor;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.observer.FunctionalObservation;

/** Checks declared enabling/observed invocation contracts. No callback-arrival order is treated as causality. */
public final class OPlanTransitionMonitor {
    public record Result(SemanticVerdict verdict,String diagnostic,Map<String,String> witness) {public Result{witness=Map.copyOf(witness);}}
    public enum Phase { ENABLED, PERFORMED }
    public record Transition(String invocation,String goal,Phase phase,Map<String,FunctionalObservation.State> before,
            boolean completePreState,boolean exactInvocationBoundary,boolean historyGap) {public Transition{before=Map.copyOf(before);}}
    public Result check(ExecutionDescriptor descriptor,Transition transition) {
        if(descriptor.plans().stream().anyMatch(p->p.operator()==ExecutionDescriptor.Operator.UNKNOWN))return result(SemanticVerdict.UNSUPPORTED,"CUSTOM_OPLAN_OPERATOR",Map.of());
        if(transition.historyGap() || !transition.completePreState() || !transition.exactInvocationBoundary())return result(SemanticVerdict.INCONCLUSIVE,"CAUSAL_INVOCATION_PRE_STATE_UNAVAILABLE",Map.of("invocation",transition.invocation()));
        var enabling=descriptor.enabling().stream().filter(e->e.goal().equals(transition.goal())).findFirst();
        if(enabling.isEmpty())return result(SemanticVerdict.ERROR,"OPLAN_GOAL_SCOPE_MISMATCH",Map.of());
        var witness=new TreeMap<String,String>();transition.before().forEach((k,v)->witness.put(k,v.name()));
        if(transition.phase()==Phase.PERFORMED) {
            var state=transition.before().get(transition.goal());if(state==null)return result(SemanticVerdict.INCONCLUSIVE,"GOAL_PRE_STATE_MISSING",witness);
            return result(state==FunctionalObservation.State.ENABLED?SemanticVerdict.PASS:SemanticVerdict.FAIL,"PERFORMED_REQUIRES_OBSERVED_ENABLED_PRE_STATE",witness);
        }
        for(String id:enabling.get().preconditions())if(!transition.before().containsKey(id))return result(SemanticVerdict.INCONCLUSIVE,"PRECONDITION_GOAL_STATE_MISSING",witness);
        boolean values=enabling.get().aggregation()==ExecutionDescriptor.Aggregation.ALL
            ?enabling.get().preconditions().stream().allMatch(g->transition.before().get(g)==FunctionalObservation.State.SATISFIED)
            :enabling.get().preconditions().stream().anyMatch(g->transition.before().get(g)==FunctionalObservation.State.SATISFIED);
        return result(enabling.get().minAgents()!=0 && values?SemanticVerdict.PASS:SemanticVerdict.FAIL,"MOISE_DEP_ENABLING_FRAGMENT",witness);
    }
    public Map<String,Result> checkCut(ExecutionDescriptor descriptor,FunctionalObservation cut) {
        if(!descriptor.scheme().equals(cut.definition().metadata().semanticId()))throw new IllegalArgumentException("OPLAN_SCHEME_SCOPE_MISMATCH");
        var states=new TreeMap<String,FunctionalObservation.State>();cut.goals().forEach(g->states.put(g.definition(),g.state()));var result=new TreeMap<String,Result>();
        for(var goal:cut.goals())if(goal.state()==FunctionalObservation.State.ENABLED)result.put(goal.definition(),check(descriptor,new Transition("PUBLIC_BOARD_STATE:"+cut.cutHash(),goal.definition(),Phase.ENABLED,states,true,true,false)));
        result.put("execution-history",result(SemanticVerdict.INCONCLUSIVE,"POLLING_IS_NOT_A_COMPLETE_EXECUTION_TRACE",Map.of("scheme",cut.schemeIdentity())));
        return Map.copyOf(result);
    }
    private static Result result(SemanticVerdict verdict,String diagnostic,Map<String,String> witness){return new Result(verdict,diagnostic,witness);}
}
