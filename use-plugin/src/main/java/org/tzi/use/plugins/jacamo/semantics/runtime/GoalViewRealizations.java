package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;
import org.vnu.sme.goal.dsl.istar.mm.*;

/** Explicit views of one source Goal. Expands its original refinement over the
 * SAME scope/player; does not introduce a social Dependency or evaluate truth. */
public final class GoalViewRealizations {
    public static final String POLICY="SAME_SCOPE_PLAYER_REFINEMENT_VIEW/1.0";
    public record View(String target,String source,String sourceGoal,String sourceRef,String scheme,String reason) { }
    private GoalViewRealizations() { }
    public static Map<String,View> compile(MappingSpecification specification,GoalModel model) {
        var result=new TreeMap<String,View>();
        for(var node:specification.root().child("bindings").children("goalRealization")) {
            if(!POLICY.equals(node.attr("policy"))||node.attr("reason").isBlank()||node.attr("sourceGoal").isBlank())throw node.location().error("GOAL_VIEW_EXPLICIT_IDENTITY_AND_POLICY_REQUIRED");
            var target=model.findElement(node.attr("target")).orElse(null);var source=model.findElement(node.attr("source")).orElse(null);
            if(!(target instanceof Goal left)||!(source instanceof Goal right)||left.goalType()!=right.goalType()||!left.conditions().isEmpty()||!right.conditions().isEmpty())throw node.location().error("GOAL_VIEW_EQUAL_UNCONDITIONED_GOALS_REQUIRED");
            String owner=model.ownerOf(left.id()).orElseThrow(),provider=model.ownerOf(right.id()).orElseThrow();
            if(owner.equals(provider)||model.getActors().stream().flatMap(a->a.refinements().stream()).anyMatch(r->r.parent().equals(left.id()))
                    ||model.getDependencies().stream().anyMatch(d->left.id().equals(d.dependerElmt())))throw node.location().error("GOAL_VIEW_UNREFINED_TARGET_REQUIRED");
            var actor=model.findActor(provider).orElseThrow();
            if(actor.associations().stream().noneMatch(a->a.kind()==AssocKind.PARTICIPATES_IN&&a.target().equals(owner)))throw node.location().error("GOAL_VIEW_DECLARED_PARTICIPATION_REQUIRED");
            if(actor.refinements().stream().noneMatch(r->r.parent().equals(right.id())))throw node.location().error("GOAL_VIEW_SOURCE_REFINEMENT_REQUIRED");
            validateGraph(model,actor,right.id(),new HashSet<>(),new HashSet<>());
            if(result.putIfAbsent(left.id(),new View(left.id(),right.id(),node.attr("sourceGoal"),node.attr("sourceRef"),node.attr("scheme"),node.attr("reason")))!=null)throw node.location().error("GOAL_VIEW_TARGET_DUPLICATE");
        }
        if(result.size()>128)throw new IllegalArgumentException("GOAL_VIEW_CAPACITY");
        for(var view:result.values())if(result.containsKey(view.source()))throw new IllegalArgumentException("GOAL_VIEW_CHAIN_UNSUPPORTED");
        return Collections.unmodifiableMap(result);
    }
    public static Map<String,View> compile(List<org.tzi.use.plugins.jacamo.semantics.mapping.compact.CompactMappingSpecification.GoalRealization> list,GoalModel model) {
        var result=new TreeMap<String,View>();
        for(var node:list) {
            if(!POLICY.equals(node.policy())||node.reason().isBlank()||node.sourceGoal().isBlank())throw node.location().error("GOAL_VIEW_EXPLICIT_IDENTITY_AND_POLICY_REQUIRED");
            var target=model.findElement(node.target()).orElse(null);var source=model.findElement(node.source()).orElse(null);
            if(!(target instanceof Goal left)||!(source instanceof Goal right)||left.goalType()!=right.goalType()||!left.conditions().isEmpty()||!right.conditions().isEmpty())throw node.location().error("GOAL_VIEW_EQUAL_UNCONDITIONED_GOALS_REQUIRED");
            String owner=model.ownerOf(left.id()).orElseThrow(),provider=model.ownerOf(right.id()).orElseThrow();
            if(owner.equals(provider)||model.getActors().stream().flatMap(a->a.refinements().stream()).anyMatch(r->r.parent().equals(left.id()))
                    ||model.getDependencies().stream().anyMatch(d->left.id().equals(d.dependerElmt())))throw node.location().error("GOAL_VIEW_UNREFINED_TARGET_REQUIRED");
            var actor=model.findActor(provider).orElseThrow();
            if(actor.associations().stream().noneMatch(a->a.kind()==AssocKind.PARTICIPATES_IN&&a.target().equals(owner)))throw node.location().error("GOAL_VIEW_DECLARED_PARTICIPATION_REQUIRED");
            if(actor.refinements().stream().noneMatch(r->r.parent().equals(right.id())))throw node.location().error("GOAL_VIEW_SOURCE_REFINEMENT_REQUIRED");
            validateGraph(model,actor,right.id(),new HashSet<>(),new HashSet<>());
            if(result.putIfAbsent(left.id(),new View(left.id(),right.id(),node.sourceGoal(),node.sourceRef(),node.scheme(),node.reason()))!=null)throw node.location().error("GOAL_VIEW_TARGET_DUPLICATE");
        }
        if(result.size()>128)throw new IllegalArgumentException("GOAL_VIEW_CAPACITY");
        for(var view:result.values())if(result.containsKey(view.source()))throw new IllegalArgumentException("GOAL_VIEW_CHAIN_UNSUPPORTED");
        return Collections.unmodifiableMap(result);
    }
    private static void validateGraph(GoalModel model,Actor owner,String id,Set<String> visiting,Set<String> done) {
        if(done.contains(id))return;
        if(!visiting.add(id)||!model.ownerOf(id).orElse("").equals(owner.name()))throw new IllegalArgumentException("GOAL_VIEW_CYCLIC_OR_CROSS_OWNER_REFINEMENT");
        if(model.getDependencies().stream().anyMatch(d->id.equals(d.dependerElmt())||id.equals(d.dependeeElmt())))throw new IllegalArgumentException("GOAL_VIEW_SOCIAL_DEPENDENCY_IN_SOURCE_UNSUPPORTED");
        var element=model.findElement(id).orElseThrow();
        if(!(element instanceof Goal||element instanceof Task))throw new IllegalArgumentException("GOAL_VIEW_REFINEMENT_KIND_UNSUPPORTED");
        for(var refinement:owner.refinements())if(refinement.parent().equals(id))for(String child:children(refinement))validateGraph(model,owner,child,visiting,done);
        visiting.remove(id);done.add(id);
    }
    public static List<String> children(Refinement refinement) {
        return refinement instanceof AndRefinement and?and.children():List.of(((OrRefinement)refinement).child());
    }
    public static boolean includes(GoalModel model,View view,String element) {
        var owner=model.findActor(model.ownerOf(view.source()).orElseThrow()).orElseThrow();var work=new ArrayDeque<String>();var seen=new HashSet<String>();work.add(view.source());
        while(!work.isEmpty()){String id=work.remove();if(!seen.add(id))continue;if(id.equals(element))return true;for(var refinement:owner.refinements())if(refinement.parent().equals(id))work.addAll(children(refinement));}
        return false;
    }
}
