package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.tzi.use.plugins.jacamo.semantics.model.ExecutionDescriptor;
import org.tzi.use.semantic.engine.frontend.IStarFrontend;
import org.tzi.use.semantic.engine.runtime.RuntimeModelAdmission;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.dsl.istar.mm.*;
import static org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex.Kind.*;

/** Authored intentional abstractions with exact source/context selectors. No recurrence or world-state rules in the engine. */
public final class JaCaMoGoalMappingRules {
    public record Result(GoalRuntimeBindingPlan bindings,List<MappingDiagnostic> diagnostics){public Result{diagnostics=List.copyOf(diagnostics);}}
    public Result map(ModelSnapshot input,NormalizedSourceModel source,MappingRuleSet rules,CslModel csl,GoalModel goals) {
        new IStarFrontend().validate(goals,"<mapped-goal-model>").requireModel();var index=new SemanticBindingIndex(rules.bindings());
        var actors=new LinkedHashMap<String,GoalRuntimeBindingPlan.ActorTemplate>();var elements=new LinkedHashMap<String,GoalRuntimeBindingPlan.ElementTemplate>();var diagnostics=new ArrayList<MappingDiagnostic>();
        for(var actor:goals.getActors()) {
            var binding=index.find(ACTOR,actor.name()).orElseThrow(()->missing("ACTOR_BINDING_REQUIRED",actor.name()));
            var original=source.require(binding.sourceId());String role=binding.metadata().getOrDefault("roleSourceId",actor instanceof Role?binding.sourceId():"");
            String context=binding.metadata().getOrDefault("contextSourceId","");String player=binding.metadata().getOrDefault("playerSourceId",actor instanceof Agent?binding.sourceId():"");
            if(actor instanceof Role&&!original.sourceKind().equals("MOISE_ROLE")||actor instanceof Agent&&!original.sourceKind().equals("JACAMO_AGENT_DECLARATION"))reject(binding,"INTENTIONAL_ACTOR_SOURCE_KIND_MISMATCH",original.sourceKind());
            if(role.isBlank()||index.source(role).stream().noneMatch(b->b.kind()==ROLE))reject(binding,"ACTOR_CSL_ROLE_BINDING_REQUIRED",role);
            if(context.isBlank()||index.source(context).stream().noneMatch(b->b.kind()==ORG_CONTEXT))reject(binding,"ACTOR_CONTEXT_DEFINITION_REQUIRED",context);
            if(!player.isBlank()&&!source.require(player).sourceKind().equals("JACAMO_AGENT_DECLARATION"))reject(binding,"EXACT_PLAYER_DECLARATION_REQUIRED",player);
            String activation=binding.metadata().getOrDefault("activationPolicy","");String episode=binding.metadata().getOrDefault("episodePolicy","");
            if(!activation.equals("ENACTMENT_INTERVAL"))reject(binding,"EXPLICIT_ROOT_DEMAND_POLICY_REQUIRED",activation);
            if(!episode.equals("ENACTMENT_INCARNATION"))reject(binding,"EXPLICIT_EPISODE_POLICY_REQUIRED",episode);
            actors.put(actor.name(),new GoalRuntimeBindingPlan.ActorTemplate(actor.name(),binding.sourceId(),role,context,player,activation,episode));
        }
        // Only a definition-shape admission. These names are not an accepted live cast,
        // and no session/history/M0 objects are created by this check.
        var shape=RuntimeModelAdmission.admit(goals,actors.keySet().stream().map(a->new ScenarioInstance("definition:"+a,a)).toList(),(a,x,b,y)->true);
        for(var element:goals.allElements().values()) {
            if(!(element instanceof GoalTaskElement))continue;
            var kind=element instanceof Goal?GOAL:TASK;
            var binding=index.find(kind,element.id()).orElseThrow(()->missing("INTENTIONAL_ELEMENT_BINDING_REQUIRED",element.id()));
            String actor=goals.ownerOf(element.id()).orElseThrow();boolean derived=shape.derivedSourceIds().contains(element.id());var original=source.require(binding.sourceId());
            if(binding.abstractionReason().isBlank())reject(binding,"EXPLICIT_INTENTIONAL_ABSTRACTION_REQUIRED",original.concept());
            String temporal=binding.metadata().getOrDefault("temporalType","");
            if(!temporal.isBlank()&&(!(element instanceof Goal goal)||!temporal.equals(goal.goalType().name())))reject(binding,"TEMPORAL_TYPE_MUST_MATCH_AUTHORED_ISTAR",temporal);
            if(original.concept().equals("AGoal")&&!binding.metadata().getOrDefault("evidencePolicy","").equals("AUTHORED_WORLD_OCL"))reject(binding,"ACTIVE_JASON_GOAL_IS_NOT_SATISFACTION",original.id());
            if(original.concept().equals("Norm"))reject(binding,"NORM_IS_NOT_INTENTIONAL_STATE_CONDITION",original.id());
            if(original.concept().equals("OGoal"))diagnostics.add(note(binding,"MOISE_TYPE_NOT_CAST_TO_TEMPORAL","Temporal type and condition are authored independently; source acknowledgement is not world truth"));
            if(derived) {elements.put(element.id(),new GoalRuntimeBindingPlan.ElementTemplate(element.id(),actor,true,"","","DERIVED",""));continue;}
            if(element instanceof Goal goal&&goal.conditions().isEmpty())reject(binding,"OBSERVED_GOAL_CONDITION_REQUIRED",element.id());
            if(element instanceof Task task&&(task.preconditions().isEmpty()||task.postconditions().isEmpty()))reject(binding,"TASK_PRE_POST_ABSTRACTION_REQUIRED",element.id());
            String declaration=binding.metadata().getOrDefault("selfDeclaration","");String self=binding.metadata().getOrDefault("selfSourceId","");String selfKind=binding.metadata().getOrDefault("selfKind","ARTIFACT_DECLARATION");
            if(selfKind.equals("ARTIFACT_DECLARATION")) {
                if(csl.findEntity(declaration).isEmpty()||self.isBlank()||!source.require(self).sourceKind().equals("JACAMO_ARTIFACT_DECLARATION"))reject(binding,"EXACT_WORLD_SELF_BINDING_REQUIRED",declaration+":"+self);
                var type=index.find(ENTITY,declaration).orElseThrow(()->missing("ENTITY_TYPE_BINDING_REQUIRED",declaration));
                String expected=JaCaMoCslMappingRules.javaClass(source,type);Object actual=source.require(self).features().get("javaClass").value();
                if(!expected.equals(actual))reject(binding,"SELF_DECLARATION_JAVA_TYPE_MISMATCH",self);
            } else if(selfKind.equals("ROLE_OCCURRENCE")) {
                var template=actors.get(actor);
                if(index.source(template.roleSourceId()).stream().noneMatch(b->b.kind()==ROLE&&b.targetId().equals(declaration))||!self.isBlank())reject(binding,"ROLE_OCCURRENCE_SELF_BINDING_MISMATCH",declaration);
            } else reject(binding,"SELF_BINDING_KIND_UNSUPPORTED",selfKind);
            String contract=binding.metadata().getOrDefault("contractId","");
            if(element instanceof Task) {
                var mapped=rules.contracts().stream().filter(c->c.targetElementId().equals(element.id())).toList();
                if(contract.isBlank()||mapped.stream().noneMatch(c->c.id().equals(contract+":pre"))||mapped.stream().noneMatch(c->c.id().equals(contract+":post")))reject(binding,"TASK_OPERATION_CONTRACT_BINDING_REQUIRED",contract);
            }
            elements.put(element.id(),new GoalRuntimeBindingPlan.ElementTemplate(element.id(),actor,false,declaration,self,selfKind,contract));
        }
        var descriptors=new ArrayList<ExecutionDescriptor>();
        for(var organization:input.semanticContract().moiseOrganizations())for(var scheme:new FunctionalMappingRules().derive(organization))descriptors.add(ExecutionDescriptor.from(scheme.source()));
        for(var binding:index.bindings())if(binding.kind()==REFINEMENT)validateOutcome(binding,index,goals,descriptors,diagnostics);
        for(var dependency:goals.getDependencies()) {
            var first=actors.get(dependency.depender());var second=actors.get(dependency.dependee());
            if(!first.contextSourceId().equals(second.contextSourceId()))throw missing("DEPENDENCY_CONTEXT_DEFINITION_MISMATCH",dependency.dependum().id());
        }
        return new Result(new GoalRuntimeBindingPlan(actors,elements,descriptors),diagnostics);
    }
    private void validateOutcome(SemanticBindingIndex.Binding binding,SemanticBindingIndex index,GoalModel model,List<ExecutionDescriptor> descriptors,List<MappingDiagnostic> diagnostics) {
        var plan=descriptors.stream().flatMap(d->d.plans().stream()).filter(p->p.id().equals(binding.sourceId())).findFirst().orElseThrow(()->missing("OPLAN_SOURCE_PLAN_REQUIRED",binding.sourceId()));
        if(!binding.metadata().getOrDefault("abstraction","").equals("OUTCOME_ONLY")||binding.abstractionReason().isBlank())reject(binding,"OPLAN_ORDER_NOT_PLAIN_REFINEMENT",plan.rawOperator());
        if(plan.operator()==ExecutionDescriptor.Operator.UNKNOWN)reject(binding,"CUSTOM_OPLAN_OPERATOR_UNSUPPORTED",plan.rawOperator());
        var expected=new LinkedHashSet<String>();for(var child:plan.children()) {
            var candidates=index.source(child.goal()).stream().filter(b->b.kind()==GOAL).toList();
            if(candidates.size()!=1)reject(binding,"OPLAN_CHILD_ABSTRACTION_REQUIRED",child.goal());expected.add(candidates.getFirst().targetId());
        }
        var refinements=model.getActors().stream().flatMap(a->a.refinements().stream()).filter(r->r.parent().equals(binding.targetId())).toList();
        var actual=new LinkedHashSet<String>();refinements.forEach(r->actual.addAll(GoalActivationGraph.children(r)));
        if(!expected.equals(actual)||refinements.isEmpty()||plan.operator()==ExecutionDescriptor.Operator.CHOICE&&refinements.stream().anyMatch(r->!(r instanceof OrRefinement))
                ||plan.operator()!=ExecutionDescriptor.Operator.CHOICE&&refinements.stream().anyMatch(r->!(r instanceof AndRefinement)))reject(binding,"OPLAN_OUTCOME_GRAPH_MISMATCH",actual.toString());
        diagnostics.add(note(binding,"OUTCOME_ONLY_ORDER_RETAINED",plan.operator()+" execution/enabling retained separately; refinement does not prove scheduling or causal conformance"));
    }
    private static MappingAdmissionException missing(String code,String target){return new MappingAdmissionException(List.of(new MappingDiagnostic(MappingDiagnostic.Kind.UNSUPPORTED,code,"",target,"AUTHORED_ISTAR_BINDING","AUTHORED_MAPPING",0,"Explicit source/binding/abstraction is required")));}
    private static MappingDiagnostic note(SemanticBindingIndex.Binding binding,String code,String reason){return new MappingDiagnostic(MappingDiagnostic.Kind.TRACE_ONLY,code,binding.sourceId(),binding.targetId(),binding.ruleId(),"AUTHORED_MAPPING_AND_OFFICIAL_SOURCE",0,reason);}
    private static void reject(SemanticBindingIndex.Binding binding,String code,String reason){throw new MappingAdmissionException(List.of(new MappingDiagnostic(MappingDiagnostic.Kind.UNSUPPORTED,code,binding.sourceId(),binding.targetId(),binding.ruleId(),"AUTHORED_MAPPING_AND_OFFICIAL_SOURCE",0,reason)));}
}
