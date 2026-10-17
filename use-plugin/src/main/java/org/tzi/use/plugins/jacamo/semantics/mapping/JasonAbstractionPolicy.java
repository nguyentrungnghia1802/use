package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.*;
import org.tzi.use.plugins.jacamo.semantics.istar.TaskBinding;
import org.tzi.use.plugins.jacamo.semantics.profile.CslMonitorConfiguration;
import org.tzi.use.plugins.jacamo.semantics.ocl.JasonConditionFragment;

/** Selected ground action-step abstraction, never an implicit translation of the complete Jason interpreter. */
public final class JasonAbstractionPolicy {
    public record Decision(String source,String disposition,String reason){}
    public record Approved(TaskBinding binding,String contextOcl,List<Decision> decisions) {public Approved{decisions=List.copyOf(decisions);}}
    public Approved admit(AgentProgramSemantic program,TaskBinding binding,Map<String,Object> parsedContext,CslMonitorConfiguration configuration) {
        if(!program.metadata().semanticId().equals(binding.sourceAgentProgram()))throw new IllegalArgumentException("JASON_TASK_PROGRAM_ID_MISMATCH");
        var plan=program.planLibrary().plans().stream().filter(p->p.metadata().semanticId().equals(binding.sourcePlan())).findFirst().orElseThrow(()->new IllegalArgumentException("JASON_TASK_PLAN_ID_UNRESOLVED"));
        var selected=plan.body().stream().filter(b->b.metadata().semanticId().equals(binding.sourceBody())).findFirst().orElseThrow(()->new IllegalArgumentException("JASON_TASK_BODY_ID_UNRESOLVED"));
        if(configuration.schema().contracts().stream().noneMatch(c->c.id().equals(binding.targetContract())))throw new IllegalArgumentException("JASON_TASK_INVOCATION_CONTRACT_UNRESOLVED");
        if(!selected.bodyType().equals("action"))throw new IllegalArgumentException("UNSUPPORTED:JASON_TASK_INTERNAL_CONTROL_OR_BELIEF_STEP:"+selected.bodyType());
        if(!plan.context().equals(parsedContext.get("source")))throw new IllegalArgumentException("JASON_CONTEXT_SOURCE_MISMATCH");
        String context=new JasonConditionFragment().compile(parsedContext);var decisions=new ArrayList<Decision>();
        for(var body:plan.body())decisions.add(new Decision(body.metadata().semanticId(),body==selected?"APPROVED_ACTION_STEP":"TRACE_ONLY",body==selected?"AUTHORED_TARGET_INVOCATION_CONTRACT":"JASON_ORDER_CONTROL_AND_EFFECTS_RETAINED_IN_SOURCE"));
        decisions.add(new Decision(plan.metadata().semanticId(),"PARTIAL","SELECTED_STEP_ONLY; plan selection/unification and complete body execution are not Task semantics"));
        decisions.add(new Decision(plan.trigger().metadata().semanticId(),"TRACE_ONLY",plan.trigger().type().equals("test")?"TEST_GOAL_QUERY_IS_NOT_ACHIEVE":"TRIGGER_OBSERVATION_IS_NOT_SATISFACTION"));
        return new Approved(binding,context,decisions);
    }
}
