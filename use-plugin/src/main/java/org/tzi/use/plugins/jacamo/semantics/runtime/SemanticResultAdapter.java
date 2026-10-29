package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.semantic.engine.runtime.*;

/** Raw original statuses are portable evidence, never cast to local IntentionalMarking. */
public final class SemanticResultAdapter {
    private SemanticResultAdapter() { }
    static Object context(JaCaMoOccurrenceBindingAdapter.ContextCast cast,Use4GoalRuntimeSessionAdapter session,EngineStepResult step,EngineCheckpoint previous,EngineInputs inputs,Map<String,String> quality,boolean advance) {
        return render(cast,session,step,previous,inputs,quality,advance,null,null,null);
    }
    static Object xmlContext(JaCaMoOccurrenceBindingAdapter.ContextCast cast,Use4GoalRuntimeSessionAdapter session,EngineStepResult step,EngineCheckpoint previous,EngineInputs inputs,Map<String,String> quality,boolean advance,OccurrenceGoalModelExpander.Expansion expansion,org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.OccurrenceProjection.Scope scope,SemanticProgram program) {
        return render(cast,session,step,previous,inputs,quality,advance,program,expansion,scope);
    }
    private static Object render(JaCaMoOccurrenceBindingAdapter.ContextCast cast,Use4GoalRuntimeSessionAdapter session,EngineStepResult step,EngineCheckpoint previous,EngineInputs inputs,Map<String,String> quality,boolean advance,SemanticProgram program,OccurrenceGoalModelExpander.Expansion neutral,org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.OccurrenceProjection.Scope scope) {
        var history=step==null?previous:step.history();var raw=new TreeMap<String,Object>();
        var dependencies=new HashMap<String,Set<String>>();var model=session.topology().instance().model();
        model.getActors().forEach(actor->{
            actor.refinements().forEach(refinement->{var children=refinement instanceof org.vnu.sme.goal.dsl.istar.mm.AndRefinement and?and.children():List.of(((org.vnu.sme.goal.dsl.istar.mm.OrRefinement)refinement).child());dependencies.computeIfAbsent(refinement.parent(),key->new HashSet<>()).addAll(children);});
            actor.contributions().forEach(contribution->dependencies.computeIfAbsent(contribution.quality(),key->new HashSet<>()).add(contribution.element()));
        });
        model.getDependencies().forEach(dependency->{if(dependency.dependerElmt()!=null&&dependency.dependeeElmt()!=null)dependencies.computeIfAbsent(dependency.dependerElmt(),key->new HashSet<>()).add(dependency.dependeeElmt());});
        session.topology().occurrences().forEach((id,binding)->{
            var value=new TreeMap<String,Object>();value.put("sourceElement",binding.sourceElementId());var element=model.findElement(id).orElseThrow();value.put("elementKind",element instanceof org.vnu.sme.goal.dsl.istar.mm.Goal?"GOAL":element instanceof org.vnu.sme.goal.dsl.istar.mm.Task?"TASK":element instanceof org.vnu.sme.goal.dsl.istar.mm.Resource?"RESOURCE":"SUPPORTING_ELEMENT");value.put("stack",SemanticJson.tree(binding.stack()));

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
        var result=new TreeMap<String,Object>();result.put("context",cast.contextId());result.put("castRevision",cast.episode());result.put("boundary",history.boundary());result.put("advance",advance);result.put("occurrences",raw);result.put("inputs",SemanticJson.tree(inputs));result.put("deferred",step==null?List.of():step.deferred().stream().sorted().toList());result.put("originalApiCalls",!advance||step==null?List.of():step.originalApiCalls());

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
    /** Adds execution qualification without overwriting current observation quality.
     * Only admitted original leaf completions may establish historical proofs. */
    static Object execution(Object rendered,Use4GoalRuntimeSessionAdapter session,RuntimeEngineSessionOwner.Cut cut,ExecutionEvidence ledger) {
        var context=CanonicalJson.object(rendered);var occurrences=CanonicalJson.object(context.get("occurrences"));
        var history=session.checkpoint();var proofs=new HashMap<String,Map<String,Object>>();
        var qualities=new HashMap<String,String>();var dependencies=new HashMap<String,Set<String>>();
        var model=session.topology().instance().model();
        model.getActors().forEach(actor->actor.refinements().forEach(refinement->{
            var children=refinement instanceof org.vnu.sme.goal.dsl.istar.mm.AndRefinement and?and.children():List.of(((org.vnu.sme.goal.dsl.istar.mm.OrRefinement)refinement).child());
            dependencies.computeIfAbsent(refinement.parent(),x->new HashSet<>()).addAll(children);
        }));
        model.getDependencies().forEach(d->{if(d.dependerElmt()!=null&&d.dependeeElmt()!=null)dependencies.computeIfAbsent(d.dependerElmt(),x->new HashSet<>()).add(d.dependeeElmt());});
        for(var entry:occurrences.entrySet()) {
            String id=entry.getKey();var row=CanonicalJson.object(entry.getValue());String quality=Objects.toString(row.get("quality"));
            boolean admitted=!cut.lost()&&Set.of("KNOWN","KNOWN_DERIVED").contains(quality)&&Boolean.TRUE.equals(context.get("advance"));
            var proof=ledger.leaf(Objects.toString(context.get("context")),history.keys().get(id),history.goals().get(id),history.tasks().get(id),admitted,cut.version(),cut.stateHash());
            proof.ifPresent(p->proofs.put(id,CanonicalJson.object(SemanticJson.tree(p))));
            // Current quality is authoritative for non-monotone leaves. A stored
            // proof can only qualify the same original fulfilled leaf episode.
            if(proof.isPresent())qualities.put(id,Set.of("KNOWN","KNOWN_DERIVED").contains(quality)?quality:"KNOWN_HISTORY");
            else if(ledger.terminal(Objects.toString(context.get("context")),history.keys().get(id)).isPresent()) {
                var failure=ledger.terminal(Objects.toString(context.get("context")),history.keys().get(id)).orElseThrow();
                proofs.put(id,CanonicalJson.object(SemanticJson.tree(failure)));qualities.put(id,"KNOWN_HISTORY");
            }
            else if(history.goals().containsKey(id)||history.tasks().containsKey(id))qualities.put(id,quality);
        }
        // Reuse the original propagation over a proof-only marking. A missing
        // alternative cannot invalidate a satisfied OR/ANY branch. This copy
        // never updates the session's history or creates another runtime.
        var certified=org.vnu.sme.goal.verify.conformance.semantics.IStarMarking.initial(model);
        for(var entry:history.goals().entrySet())if(qualifiedLeaf(qualities.get(entry.getKey())))certified=certified.withGoalMarking(entry.getKey(),entry.getValue());
        for(var entry:history.tasks().entrySet())if(qualifiedLeaf(qualities.get(entry.getKey())))certified=certified.withTaskMarking(entry.getKey(),entry.getValue());
        certified=org.vnu.sme.goal.verify.conformance.semantics.IStarPropagation.closePending(model,certified);
        if(!certified.goalTaskStatuses().isEmpty()) {
            String anchor=certified.goalTaskStatuses().keySet().iterator().next();
            certified=org.vnu.sme.goal.verify.conformance.semantics.IStarPropagation.assignGoalTask(model,certified,anchor,certified.goalTaskStatus(anchor));
        }
        certified=org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser.GoalModelInstanceTranslator.propagateDependencies(model,certified);
        certified=org.vnu.sme.goal.verify.conformance.semantics.IStarPropagation.closePending(model,certified);
        var updated=new TreeMap<String,Object>();
        for(var entry:occurrences.entrySet()) {
            String id=entry.getKey();var row=new TreeMap<>(CanonicalJson.object(entry.getValue()));
            String quality=executionQuality(id,qualities,dependencies,new HashSet<>());
            var sources=new TreeMap<String,Object>();collectProofs(id,proofs,dependencies,sources,new HashSet<>());
            if(!qualities.containsKey(id)&&"FULFILLED".equals(row.get("aggregateStatus"))
                    &&certified.goalTaskStatus(id)==org.vnu.sme.goal.verify.conformance.semantics.GoalTaskStatus.FULFILLED)
                quality=sources.isEmpty()?"KNOWN_DERIVED":"KNOWN_DERIVED_HISTORY";
            ledger.terminal(Objects.toString(context.get("context")),history.keys().get(id)).ifPresent(failure->row.put("executionStatus","FAILED"));
            row.put("executionQuality",quality);
            row.put("executionEvidence",sources);updated.put(id,row);
        }
        var result=new TreeMap<>(context);result.put("occurrences",updated);result.put("executionQualification","ORIGINAL_PROPAGATION_ON_ADMITTED_LEAVES/1.0");return Collections.unmodifiableMap(result);
    }
    private static boolean qualifiedLeaf(String quality){return quality!=null&&Set.of("KNOWN","KNOWN_DERIVED","KNOWN_HISTORY").contains(quality);}
    /** Missing current scope is not evidence that a past task did not complete.
     * Requalify ONLY stored monotone proofs; no old live predicate is admitted. */
    static Object unavailableExecution(Map<String,Object> previous,Use4GoalRuntimeSessionAdapter session,RuntimeEngineSessionOwner.Cut cut,ExecutionEvidence ledger,List<String> diagnostics) {
        var held=new TreeMap<>(previous);var occurrences=new TreeMap<String,Object>();
        CanonicalJson.object(previous.getOrDefault("occurrences",Map.of())).forEach((id,value)->{
            var row=new TreeMap<>(CanonicalJson.object(value));row.put("quality","UNAVAILABLE:CURRENT_SCOPE");occurrences.put(id,row);
        });
        held.put("occurrences",occurrences);held.put("advance",false);held.put("originalApiCalls",List.of());
        held.put("quality","UNAVAILABLE");held.put("diagnostics",diagnostics);held.put("executionView","HISTORICAL_CERTIFICATES_ONLY_CURRENT_SCOPE_UNAVAILABLE");
        return execution(held,session,cut,ledger);
    }
    private static String executionQuality(String id,Map<String,String> qualities,Map<String,Set<String>> dependencies,Set<String> visited) {
        if(qualities.containsKey(id))return qualities.get(id);
        if(!visited.add(id)||dependencies.getOrDefault(id,Set.of()).isEmpty())return "DERIVED_INPUT_UNAVAILABLE";
        var values=dependencies.get(id).stream().map(child->executionQuality(child,qualities,dependencies,new HashSet<>(visited))).toList();
        if(!values.stream().allMatch(v->Set.of("KNOWN","KNOWN_DERIVED","KNOWN_HISTORY","KNOWN_DERIVED_HISTORY").contains(v)))return "DERIVED_INPUT_UNAVAILABLE";
        return values.stream().anyMatch(v->v.endsWith("HISTORY"))?"KNOWN_DERIVED_HISTORY":"KNOWN_DERIVED";
    }
    private static void collectProofs(String id,Map<String,Map<String,Object>> proofs,Map<String,Set<String>> dependencies,Map<String,Object> result,Set<String> visited) {
        if(!visited.add(id))return;
        if(proofs.containsKey(id)){result.put(id,proofs.get(id));return;}
        dependencies.getOrDefault(id,Set.of()).forEach(child->collectProofs(child,proofs,dependencies,result,visited));
    }
    static RuntimeEngineSessionOwner.Evaluation result(SemanticProgram program,RuntimeEngineSessionOwner.Cut cut,List<SemanticConstraintResult> rows,List<ExternalOclConstraintService.Outcome> nativeOutcomes,List<Object> contexts,Map<String,String> originalClasses,boolean historyGap,Map<String,Object> requirementInventory) {
        if(program.xmlPlan()!=null&&contexts.isEmpty())rows.add(new SemanticConstraintResult("XML:OCCURRENCES","RUNTIME_BINDING","","","","",SemanticVerdict.SKIPPED,"",
                "XML_OCCURRENCE_BINDING_UNAVAILABLE",cut.version(),"",Map.of("mappingPlanHash",program.xmlPlan().hash())));
        var core=new TreeMap<String,Object>();core.put("version","1.0");core.put("engineProfile",program.profile().engine().name());core.put("upstreamCommit",program.profile().engine().upstreamCommit());core.put("classHashes",originalClasses);core.put("programHash",program.hash());core.put("cslHash",program.cslHash());core.put("goalHash",program.goalHash());core.put("identity",SemanticJson.tree(cut.identity()));core.put("stateVersion",cut.version());core.put("stateHash",cut.stateHash());core.put("contexts",contexts);core.put("expectedIntentionalElements",program.goalBindings().elements().size());core.put("historyQuality",historyGap||cut.lost()?"GAP":"OBSERVED_PREFIX");core.put("evaluated","PINNED_NATIVE_CUT");
        var envelope=new SemanticVerificationEnvelope("1.0",program.profile().engine().name(),program.rules().hash(),cut.boundary(),rows,Map.of(),Map.of("history",historyGap||cut.lost()?"GAP":"OBSERVED_PREFIX"),List.of("ORIGINAL_RAW_MARKINGS_ARE_SEPARATE_FROM_OBSERVATION_QUALITY","TASK_INVOCATION_CONFORMANCE_IS_SEPARATE_FROM_HISTORICAL_TASK_MARKING"),Map.of("originalEngine",core,"presentationState",presentation(program,cut.state().after()),"requirementInventory",requirementInventory,"requirementCoveragePolicy",program.xmlPlan()!=null?"SELECTED_REQUIREMENTS/1.0":"ALL_SOURCES/1.0"));
        var outcomes=new ArrayList<>(nativeOutcomes);rows.stream().filter(r->!Set.of("INVARIANT_BODY","TASK_PRE","TASK_POST","GOAL_CONDITION","EXECUTION_OUTCOME").contains(r.kind())&&nativeOutcomes.stream().noneMatch(o->o.constraintId().equals(r.id()))).forEach(r->outcomes.add(r.legacy()));return new RuntimeEngineSessionOwner.Evaluation(envelope,outcomes);
    }
    private static Map<String,Object> presentation(SemanticProgram program,org.tzi.use.uml.sys.MSystemState state) {
        if(program.xmlPlan()==null)return Map.of();
        var selected=new TreeMap<String,Object>();boolean qualified=true;
        for(var section:program.xmlPlan().inputs().specification().root().children("presentation"))for(var selection:section.children("state")) {
            var cls=state.system().model().getClass(selection.attr("owner"));
            if(cls==null)throw selection.location().error("PRESENTATION_CLASS_UNRESOLVED");
            for(var object:state.objectsOfClassAndSubClasses(cls)) {
                var attributes=new TreeMap<String,Object>();
                for(String field:selection.optional("attributes").isBlank()?List.<String>of():Arrays.asList(selection.attr("attributes").trim().split("\\s+"))){var value=object.state(state).attributeValue(field);qualified&=!value.isUndefined();attributes.put(field,value.toString());}
                selected.put(selection.attr("owner")+":"+object.name(),attributes);
            }
        }
        return program.xmlPlan().inputs().specification().root().children("presentation").isEmpty()?Map.of():Map.of("version","DECLARATIVE_PRESENTATION/1.0","objects",selected,"hash",SemanticJson.hash(selected),"qualifiedValues",qualified,"executionMilestones",!program.xmlPlan().inputs().specification().root().child("presentation").optional("milestones").equals("DOMAIN"));
    }
    public static SemanticVerificationEnvelope pending(SemanticProgram program,String boundary,String reason) {
        var observed=new TreeMap<String,Object>();observed.put("originalEngine",Map.of("version","1.0","programHash",program.hash(),"evaluated",reason));
        // A pending evaluation has no business-state evidence, but it must carry
        // the same declared browser policy as its eventual completed cut.
        if(program.xmlPlan()!=null&&!program.xmlPlan().inputs().specification().root().children("presentation").isEmpty())
            observed.put("presentationState",Map.of("version","DECLARATIVE_PRESENTATION/1.0","pending",true,"qualifiedValues",false,"executionMilestones",!program.xmlPlan().inputs().specification().root().child("presentation").optional("milestones").equals("DOMAIN")));
        return new SemanticVerificationEnvelope("1.0",program.profile().engine().name(),program.rules().hash(),boundary,List.of(),Map.of(),Map.of("evaluation",reason),List.of(reason),observed);
    }
}
