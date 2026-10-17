package org.tzi.use.plugins.jacamo.semantics.model;

import java.util.*;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.*;

/** CSL/iStar outcome projection plus the original MOISE enabling contract, versioned separately. */
public record ExecutionDescriptor(String version,String scheme,List<Plan> plans,List<Enabling> enabling,List<String> assumptions) {
    public enum Operator { SEQUENCE, PARALLEL, CHOICE, UNKNOWN;
        static Operator of(String raw){try{return valueOf(raw.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){return UNKNOWN;}}
    }
    public enum Aggregation { ALL, ANY }
    public record Child(String goal,int ordinal) { }
    public record Plan(String id,String target,String rawOperator,Operator operator,double successRate,List<Child> children) {
        public Plan {children=List.copyOf(children);for(int i=0;i<children.size();i++)if(children.get(i).ordinal()!=i)throw new IllegalArgumentException("OPLAN_ORDINAL_MISMATCH");if(new HashSet<>(children.stream().map(Child::goal).toList()).size()!=children.size())throw new IllegalArgumentException("OPLAN_DUPLICATE_CHILD");}
    }
    public record Enabling(String goal,Aggregation aggregation,int minAgents,List<String> preconditions) {public Enabling {preconditions=List.copyOf(preconditions);}}
    public ExecutionDescriptor {if(!"1.0".equals(version))throw new IllegalArgumentException("OPLAN_DESCRIPTOR_VERSION");plans=List.copyOf(plans);enabling=List.copyOf(enabling);assumptions=List.copyOf(assumptions);}
    public static ExecutionDescriptor from(SchemeSemantic scheme) {
        var goals=new LinkedHashMap<String,OrganizationalGoalSemantic>();for(var goal:scheme.goals())if(goals.put(goal.metadata().semanticId(),goal)!=null)throw new IllegalArgumentException("OPLAN_DUPLICATE_GOAL");
        var byId=new LinkedHashMap<String,OrganizationalPlanSemantic>();var targets=new HashSet<String>();var parents=new HashSet<String>();var plans=new ArrayList<Plan>();
        for(var plan:scheme.plans()) {
            if(byId.put(plan.metadata().semanticId(),plan)!=null || !targets.add(plan.targetGoalSemanticId()) || !goals.containsKey(plan.targetGoalSemanticId()) || !plan.schemeSemanticId().equals(scheme.metadata().semanticId()))throw new IllegalArgumentException("OPLAN_REFERENCE_OR_ID_MISMATCH");
            var children=new ArrayList<Child>();for(int i=0;i<plan.orderedSubGoalSemanticIds().size();i++){String child=plan.orderedSubGoalSemanticIds().get(i);if(!goals.containsKey(child) || !parents.add(child))throw new IllegalArgumentException("OPLAN_CHILD_REFERENCE_OR_MULTIPLE_PARENTS");children.add(new Child(child,i));}
            plans.add(new Plan(plan.metadata().semanticId(),plan.targetGoalSemanticId(),plan.operator(),Operator.of(plan.operator()),plan.successRate(),children));
        }
        for(var plan:scheme.plans()) {
            if(!goals.get(plan.targetGoalSemanticId()).planSemanticId().equals(plan.metadata().semanticId()))throw new IllegalArgumentException("OPLAN_TARGET_BACK_REFERENCE_MISMATCH");
            for(String child:plan.orderedSubGoalSemanticIds())if(!goals.get(child).inPlanSemanticId().equals(plan.metadata().semanticId()))throw new IllegalArgumentException("OPLAN_PARENT_BACK_REFERENCE_MISMATCH");
        }
        var enabling=new ArrayList<Enabling>();
        for(var goal:scheme.goals()) {
            var pre=new ArrayList<>(goal.dependencySemanticIds());Aggregation aggregation=Aggregation.ALL;
            if(!goal.planSemanticId().isBlank()) {
                var plan=required(byId,goal.planSemanticId());
                switch(Operator.of(plan.operator())) {
                    case SEQUENCE -> {if(plan.orderedSubGoalSemanticIds().isEmpty())throw new IllegalArgumentException("OPLAN_EMPTY_SEQUENCE");pre.add(plan.orderedSubGoalSemanticIds().getLast());}
                    case PARALLEL -> pre.addAll(plan.orderedSubGoalSemanticIds());
                    case CHOICE -> {pre.addAll(plan.orderedSubGoalSemanticIds());aggregation=Aggregation.ANY;}
                    case UNKNOWN -> { }
                }
            } else {
                var at=goal;var seen=new HashSet<String>();
                while(!at.inPlanSemanticId().isBlank()) {
                    if(!seen.add(at.metadata().semanticId()))throw new IllegalArgumentException("OPLAN_DECOMPOSITION_CYCLE");
                    var plan=required(byId,at.inPlanSemanticId());int ordinal=plan.orderedSubGoalSemanticIds().indexOf(at.metadata().semanticId());if(ordinal<0)throw new IllegalArgumentException("OPLAN_PARENT_ORDINAL_MISMATCH");
                    if(Operator.of(plan.operator())==Operator.SEQUENCE && ordinal>0){pre.add(plan.orderedSubGoalSemanticIds().get(ordinal-1));break;}
                    at=goals.get(plan.targetGoalSemanticId());
                }
            }
            for(String id:pre)if(!goals.containsKey(id))throw new IllegalArgumentException("OPLAN_PRECONDITION_REFERENCE_MISMATCH");
            enabling.add(new Enabling(goal.metadata().semanticId(),aggregation,goal.minAgentsToSatisfy(),pre));
        }
        var graph=new HashMap<String,List<String>>();enabling.forEach(e->graph.put(e.goal(),e.preconditions()));for(String goal:graph.keySet())acyclic(goal,graph,new HashSet<>(),new HashSet<>());
        return new ExecutionDescriptor("1.0",scheme.metadata().semanticId(),plans,enabling,List.of("Goal.getPreConditionGoals + os2nopl dep(and/or) fragment","Outcome refinement does not execute MOISE scheduling","Public goalState polling does not establish a complete causal trace"));
    }
    private static <T>T required(Map<String,T> map,String id){var value=map.get(id);if(value==null)throw new IllegalArgumentException("OPLAN_REFERENCE_MISSING:"+id);return value;}
    private static void acyclic(String id,Map<String,List<String>> graph,Set<String> path,Set<String> done){if(done.contains(id))return;if(!path.add(id))throw new IllegalArgumentException("OPLAN_PRECONDITION_CYCLE");for(String next:graph.get(id))acyclic(next,graph,path,done);path.remove(id);done.add(id);}
}
