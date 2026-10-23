package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.semantic.engine.runtime.*;

/** Raw original statuses are portable evidence, never cast to local IntentionalMarking. */
public final class SemanticResultAdapter {
    private SemanticResultAdapter() { }
    static Object context(JaCaMoOccurrenceBindingAdapter.ContextCast cast,Use4GoalRuntimeSessionAdapter session,EngineStepResult step,EngineCheckpoint previous,EngineInputs inputs,Map<String,String> quality,boolean advance) {
        return context(cast,session,step,previous,inputs,quality,advance,null,null);
    }
    static Object context(JaCaMoOccurrenceBindingAdapter.ContextCast cast,Use4GoalRuntimeSessionAdapter session,EngineStepResult step,EngineCheckpoint previous,EngineInputs inputs,Map<String,String> quality,boolean advance,org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionGoalInstanceExpander.Expansion expansion,SemanticProgram program) {
        return render(cast,session,step,previous,inputs,quality,advance,expansion,program,null,null);
    }
    static Object xmlContext(JaCaMoOccurrenceBindingAdapter.ContextCast cast,Use4GoalRuntimeSessionAdapter session,EngineStepResult step,EngineCheckpoint previous,EngineInputs inputs,Map<String,String> quality,boolean advance,OccurrenceGoalModelExpander.Expansion expansion,org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.OccurrenceProjection.Scope scope,SemanticProgram program) {
        return render(cast,session,step,previous,inputs,quality,advance,null,program,expansion,scope);
    }
    private static Object render(JaCaMoOccurrenceBindingAdapter.ContextCast cast,Use4GoalRuntimeSessionAdapter session,EngineStepResult step,EngineCheckpoint previous,EngineInputs inputs,Map<String,String> quality,boolean advance,org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionGoalInstanceExpander.Expansion expansion,SemanticProgram program,OccurrenceGoalModelExpander.Expansion neutral,org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.OccurrenceProjection.Scope scope) {
        var history=step==null?previous:step.history();var raw=new TreeMap<String,Object>();
        var dependencies=new HashMap<String,Set<String>>();var model=session.topology().instance().model();
        model.getActors().forEach(actor->{
            actor.refinements().forEach(refinement->{var children=refinement instanceof org.vnu.sme.goal.dsl.istar.mm.AndRefinement and?and.children():List.of(((org.vnu.sme.goal.dsl.istar.mm.OrRefinement)refinement).child());dependencies.computeIfAbsent(refinement.parent(),key->new HashSet<>()).addAll(children);});
            actor.contributions().forEach(contribution->dependencies.computeIfAbsent(contribution.quality(),key->new HashSet<>()).add(contribution.element()));
        });
        model.getDependencies().forEach(dependency->{if(dependency.dependerElmt()!=null&&dependency.dependeeElmt()!=null)dependencies.computeIfAbsent(dependency.dependerElmt(),key->new HashSet<>()).add(dependency.dependeeElmt());});
        session.topology().occurrences().forEach((id,binding)->{
            var value=new TreeMap<String,Object>();value.put("sourceElement",binding.sourceElementId());value.put("stack",SemanticJson.tree(binding.stack()));
            if(expansion!=null) {
                var trace=expansion.trace().get(binding.sourceElementId());
                value.put("executionElement",binding.sourceElementId());value.put("sourceElement",trace.source());value.put("player",trace.player());value.put("role",trace.role());value.put("expansionKind",trace.kind());
                value.putAll(AuctionEvidenceEncoder.element(program,trace.source(),trace.actor()));
            }
            if(neutral!=null) {
                var trace=neutral.trace().get(binding.sourceElementId());
                value.put("executionElement",binding.sourceElementId());value.put("sourceElement",trace.source());value.put("player",trace.player());value.put("role",trace.role());value.put("sourceActor",trace.actor());value.put("expansionKind",trace.kind());
                value.put("mappingPlanHash",program.xmlPlan().hash());
                program.bindings().bindings().stream().filter(b->b.targetId().equals(trace.source())).findFirst().ifPresent(b->value.put("sourceRule",b.ruleId()));
            }
            value.put("aggregateStatus",step==null?"NOT_ADVANCED":step.aggregate().goalTaskStatus(id).name());
            if(history.goals().containsKey(id)){value.put("goal",SemanticJson.tree(history.goals().get(id)));value.put("leafStatus",history.goals().get(id).status().name());}
            if(history.tasks().containsKey(id)){value.put("task",SemanticJson.tree(history.tasks().get(id)));value.put("leafStatus",history.tasks().get(id).status().name());}
            value.put("quality",derivedQuality(id,quality,dependencies,new HashSet<>()));
            var key=history.keys().get(id);if(key!=null)value.put("key",SemanticJson.tree(key));raw.put(id,value);
        });
        var result=new TreeMap<String,Object>();result.put("context",cast.contextId());result.put("castRevision",cast.episode());result.put("boundary",history.boundary());result.put("advance",advance);result.put("occurrences",raw);result.put("inputs",SemanticJson.tree(inputs));result.put("deferred",step==null?List.of():step.deferred().stream().sorted().toList());result.put("originalApiCalls",!advance||step==null?List.of():step.originalApiCalls());if(expansion!=null)result.put("topologyPolicy",session.topologyPolicy().name());
        if(expansion!=null) {result.put("expansionRecipe",org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionGoalInstanceExpander.VERSION);result.put("executionGoalHash",expansion.owned().hash());result.put("expansionHash",expansion.hash());result.put("scheme",expansion.scope().scheme());result.put("artifact",expansion.scope().artifact());result.put("expansionTrace",SemanticJson.tree(expansion.trace()));}
        if(neutral!=null) {result.put("topologyPolicy",session.topologyPolicy().name());result.put("expansionRecipe",OccurrenceGoalModelExpander.VERSION);result.put("executionGoalHash",neutral.owned().hash());result.put("expansionHash",neutral.hash());result.put("anchor",scope.anchor());result.put("scopeDefinition",scope.definition());result.put("expansionTrace",SemanticJson.tree(neutral.trace()));}
        return Collections.unmodifiableMap(result);
    }
    private static String derivedQuality(String id,Map<String,String> inputs,Map<String,Set<String>> dependencies,Set<String> stack) {
        if(inputs.containsKey(id))return inputs.get(id);
        if(!stack.add(id)||dependencies.getOrDefault(id,Set.of()).isEmpty())return "DERIVED_INPUT_UNAVAILABLE";
        var sources=dependencies.get(id).stream().map(child->derivedQuality(child,inputs,dependencies,new HashSet<>(stack))).toList();
        if(sources.stream().anyMatch("HISTORY_GAP"::equals))return "HISTORY_GAP";
        return sources.stream().allMatch(value->value.equals("KNOWN")||value.equals("KNOWN_DERIVED"))?"KNOWN_DERIVED":"DERIVED_INPUT_UNAVAILABLE";
    }
    static RuntimeEngineSessionOwner.Evaluation result(SemanticProgram program,RuntimeEngineSessionOwner.Cut cut,List<SemanticConstraintResult> rows,List<ExternalOclConstraintService.Outcome> nativeOutcomes,List<Object> contexts,Map<String,String> originalClasses,boolean historyGap) {
        if(program.casePlan()!=null&&contexts.isEmpty())rows.add(new SemanticConstraintResult("AUCTION:OCCURRENCES","RUNTIME_BINDING","","","","",SemanticVerdict.SKIPPED,"",
                "AUCTION_SCHEME_PLAYER_INVOCATION_EVIDENCE_UNAVAILABLE",cut.version(),"",Map.of("casePlanHash",program.casePlan().hash())));
        if(program.xmlPlan()!=null&&contexts.isEmpty())rows.add(new SemanticConstraintResult("XML:OCCURRENCES","RUNTIME_BINDING","","","","",SemanticVerdict.SKIPPED,"",
                "XML_OCCURRENCE_BINDING_UNAVAILABLE",cut.version(),"",Map.of("mappingPlanHash",program.xmlPlan().hash())));
        var core=new TreeMap<String,Object>();core.put("version","1.0");core.put("engineProfile",program.profile().engine().name());core.put("upstreamCommit",program.profile().engine().upstreamCommit());core.put("classHashes",originalClasses);core.put("programHash",program.hash());core.put("cslHash",program.cslHash());core.put("goalHash",program.goalHash());core.put("identity",SemanticJson.tree(cut.identity()));core.put("stateVersion",cut.version());core.put("stateHash",cut.stateHash());core.put("contexts",contexts);core.put("historyQuality",historyGap||cut.lost()?"GAP":"OBSERVED_PREFIX");core.put("evaluated","PINNED_NATIVE_CUT");
        var envelope=new SemanticVerificationEnvelope("1.0",program.profile().engine().name(),program.rules().hash(),cut.boundary(),rows,Map.of(),Map.of("history",historyGap||cut.lost()?"GAP":"OBSERVED_PREFIX"),List.of("ORIGINAL_RAW_MARKINGS_ARE_SEPARATE_FROM_OBSERVATION_QUALITY","TASK_INVOCATION_CONFORMANCE_IS_SEPARATE_FROM_HISTORICAL_TASK_MARKING"),Map.of("originalEngine",core));
        var outcomes=new ArrayList<>(nativeOutcomes);rows.stream().filter(r->!r.kind().equals("INVARIANT_BODY")&&nativeOutcomes.stream().noneMatch(o->o.constraintId().equals(r.id()))).forEach(r->outcomes.add(r.legacy()));return new RuntimeEngineSessionOwner.Evaluation(envelope,outcomes);
    }
    public static SemanticVerificationEnvelope pending(SemanticProgram program,String boundary,String reason) {
        return new SemanticVerificationEnvelope("1.0",program.profile().engine().name(),program.rules().hash(),boundary,List.of(),Map.of(),Map.of("evaluation",reason),List.of(reason),Map.of("originalEngine",Map.of("version","1.0","programHash",program.hash(),"evaluated",reason)));
    }
}
