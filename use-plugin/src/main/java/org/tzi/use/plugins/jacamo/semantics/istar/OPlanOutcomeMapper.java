package org.tzi.use.plugins.jacamo.semantics.istar;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.model.ExecutionDescriptor;

/** Explicit outcome-only abstraction. CHOICE means at least one, with no inferred mutual exclusion. */
public final class OPlanOutcomeMapper {
    public RequirementModel.Node bind(ExecutionDescriptor.Plan plan,String id,String requirement,
            RequirementModel.Temporal temporal,RequirementModel.Binding binding,Map<String,String> childOccurrences) {
        if(plan.operator()==ExecutionDescriptor.Operator.UNKNOWN)throw new IllegalArgumentException("UNSUPPORTED:CUSTOM_OPLAN_OPERATOR:"+plan.rawOperator());
        var children=plan.children().stream().map(c->{var occurrence=childOccurrences.get(c.goal());if(occurrence==null)throw new IllegalArgumentException("OPLAN_CHILD_OCCURRENCE_UNBOUND:"+c.goal());return occurrence;}).toList();
        if(children.isEmpty())throw new IllegalArgumentException("OPLAN_OUTCOME_EMPTY_CHILDREN");
        return new RequirementModel.Node(id,requirement,RequirementModel.Kind.GOAL,temporal,binding,"","true",
            plan.operator()==ExecutionDescriptor.Operator.CHOICE?RequirementModel.Refinement.OR:RequirementModel.Refinement.AND,children,"","");
    }
}
