package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.semantic.engine.use75.UseStateReadView;
import org.tzi.use.uml.sys.MSystemState;

/** Writer-owned, bounded borrowed pre-cuts. Markers are admission tokens, not
 * truth or engine history; all are part of the coordinator admission savepoint. */
public final class AuctionTaskCheckpoints {
    public record Marker(String scope,String player,String roleInterval,String task,String demand,String goal,String invocation,String phase) { }
    public record Entry(Marker marker,NativeInvocationEvaluation.Condition pre,long bytes) { }
    public record Early(VerificationObservation observation,MSystemState before,String player,String roleInterval,AuctionSchemeObservationDecoder.Cut source,long bytes) { }
    public record Image(Map<String,Entry> active,Map<String,Marker> latest,List<NativeInvocationEvaluation.Work> prepared,Set<String> seen,Map<String,Early> early) {
        public Image{active=Map.copyOf(active);latest=Map.copyOf(latest);prepared=List.copyOf(prepared);seen=Set.copyOf(seen);early=Map.copyOf(early);}
    }
    private final SemanticProgram program;private final NativeRuntimeMutationEngine engine;private final AuctionTaskOclBindings binding;private final AuctionStepObservationDecoder decoder;
    private final AuctionOccurrenceBindingAdapter casts;private final AuctionTaskDemand demands;private final AuctionSchemeObservationDecoder schemeDecoder;
    private final Map<String,Entry> active=new LinkedHashMap<>();private final Map<String,Marker> latest=new LinkedHashMap<>();
    private final Set<String> seen=new LinkedHashSet<>();
    private final Map<String,Early> early=new LinkedHashMap<>();
    private List<NativeInvocationEvaluation.Work> prepared=List.of();
    public AuctionTaskCheckpoints(SemanticProgram program,NativeRuntimeMutationEngine engine,AuctionTaskOclBindings binding){this.program=program;this.engine=engine;this.binding=binding;decoder=new AuctionStepObservationDecoder(program);casts=new AuctionOccurrenceBindingAdapter(program);demands=new AuctionTaskDemand(program);schemeDecoder=new AuctionSchemeObservationDecoder(program);}
    public Image savepoint(){return new Image(active,latest,prepared,seen,early);}
    public void restore(Image image){active.clear();active.putAll(image.active());latest.clear();latest.putAll(image.latest());prepared=image.prepared();seen.clear();seen.addAll(image.seen());early.clear();early.putAll(image.early());}
    public void clear(){active.clear();latest.clear();seen.clear();early.clear();prepared=List.of();}
    public void retireUnavailable() {
        active.values().removeIf(entry->!current(entry.marker()));latest.values().removeIf(marker->!current(marker));
        early.values().removeIf(entry->engine.system().state().allObjects().stream().noneMatch(o->entry.roleInterval().equals(engine.metadata(o,"auctionObservedRoleInterval"))&&entry.player().equals(engine.metadata(o,"rolePlayer"))));
    }
    private boolean current(Marker marker){var monitor=engine.objectForSemanticId(marker.scope());return monitor!=null&&!"ARCHIVED".equals(engine.metadata(monitor,"auctionMonitorLifecycle"))&&engine.system().state().allObjects().stream().anyMatch(o->marker.roleInterval().equals(engine.metadata(o,"auctionObservedRoleInterval"))&&marker.player().equals(engine.metadata(o,"rolePlayer")));}
    public static String key(String scope,String player,String task){return SemanticJson.hash(List.of(scope,player,task));}
    public Marker marker(String scope,String player,String task){return latest.get(key(scope,player,task));}
    public List<NativeInvocationEvaluation.Work> drain(){var result=prepared;prepared=List.of();return result;}
    public void observe(RuntimeEvent event,Map<String,Completeness> sources,boolean lost,RuntimeSnapshot prefix) {
        retireUnavailable();
        if(event.kind()==RuntimeEventKind.SUCCEEDED&&event.after().get("operationSignature") instanceof List<?> signature&&signature.size()==4&&signature.get(0).equals("ora4mas.nopl.SchemeBoard")&&signature.get(1).equals("resetGoal"))
            early.values().removeIf(e->e.source().artifact().equals(event.after().get("artifactSemanticId")));
        if(!VerificationObservation.present(event)||VerificationObservation.require(event).kind()!=VerificationObservation.Kind.JASON_STEP)return;
        var observation=VerificationObservation.require(event);var run=new AuctionArtifactBindingResolver.Run(event.sessionId(),event.generation(),event.modelRevision());var step=decoder.decode(observation,run.id(),run.generation(),run.modelRevision());
        var candidates=new ArrayList<Map.Entry<AuctionOccurrenceBindingAdapter.Scope,AuctionOccurrenceBindingAdapter.Player>>();
        var player=engine.objectForSemanticId(step.agent());
        for(var scope:casts.resolve(engine,run))if(scope.complete()&&scope.scheme().equals(step.scheme()))for(var actor:scope.actors())
            if(player!=null&&engine.objectForSemanticId(actor.player())==player&&program.casePlan().tasks().stream().anyMatch(t->t.task().equals(step.task())&&t.actor().equals(actor.sourceActor())))candidates.add(Map.entry(scope,actor));
        if(candidates.size()!=1){if(candidates.isEmpty()&&step.task().equals("A_start_t1")&&step.phase().equals("ENABLED")&&step.artifact().isBlank()&&captureEarly(event,observation,step,run,prefix,lost))return;unavailable(step,"EXACT_TASK_CAST_UNAVAILABLE");return;}
        var scope=candidates.getFirst().getKey();var actor=candidates.getFirst().getValue();var demand=demands.read(engine,scope,actor,step.task(),run);
        var role=engine.objectForSemanticId(actor.role());String floor=role==null?"":engine.metadata(role,"auctionRoleIntervalObserverFloor");
        if(floor.isBlank()||event.sourceSequence()<=Long.parseLong(floor)){unavailable(step,"STEP_PRECEDES_OBSERVED_ROLE_INTERVAL");return;}
        String slot=key(scope.id(),actor.player(),step.task());
        var marker=new Marker(scope.id(),actor.player(),actor.roleInterval(),step.task(),demand.episode(),step.goalInvocation(),step.invocation(),step.phase());
        if(step.phase().equals("ENABLED")) {
            if(seen.contains(step.invocation())){active.remove(step.invocation());latest.remove(slot);unavailable(step,"DUPLICATE_TASK_ENTRY_INVALIDATED");return;}
            if(seen.size()>=8192)throw new IllegalStateException("AUCTION_TASK_INVOCATION_LEDGER_CAPACITY");seen.add(step.invocation());
            var pre=binding.prepare(step.task(),true,engine,scope,actor,run,observation,UseStateReadView.pin(null,engine.system().state()),sources,lost);
            var witness=new TreeMap<>(pre.witness());witness.putAll(demand.witness());witness.put("required",Objects.toString(demand.required(),"UNAVAILABLE"));
            pre=condition(pre,pre.before(),pre.after(),pre.unavailable().isBlank()&&(!Boolean.TRUE.equals(demand.required())||!"ENABLED".equals(demand.witness().get("demandSourceState")))?"ACTUAL_TASK_DEMAND_UNAVAILABLE":pre.unavailable(),witness);
            long bytes=new NativeUseSoilExporter().exportState(pre.after()).commands().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if(active.size()+early.size()>=128||active.values().stream().mapToLong(Entry::bytes).sum()+early.values().stream().mapToLong(Early::bytes).sum()+bytes>8L*1024*1024||latest.size()>=4096&&!latest.containsKey(slot))throw new IllegalStateException("AUCTION_TASK_PRE_CAPACITY");
            active.put(step.invocation(),new Entry(marker,pre,bytes));latest.put(slot,marker);append(NativeInvocationEvaluation.prepare(List.of(pre),List.of(),List.of()));
        } else {
            if(!seen.contains(step.invocation())){if(seen.size()>=8192)throw new IllegalStateException("AUCTION_TASK_INVOCATION_LEDGER_CAPACITY");seen.add(step.invocation());}
            var entry=active.remove(step.invocation());
            var creating=early.remove(step.invocation());
            if(entry==null&&creating!=null&&creating.player().equals(actor.player())&&creating.roleInterval().equals(actor.roleInterval())
                    &&creating.source().scheme().equals(scope.scheme())&&step.goalInvocation().equals(creating.observation().data().get("goalInvocation"))&&Boolean.TRUE.equals(demand.required())) {
                var pre=binding.prepare(step.task(),true,engine,scope,actor,run,creating.observation(),UseStateReadView.pin(null,creating.before()),sources,lost);
                // This exact authored pre is a source tuple predicate. No
                // attribute/population read or future domain self is permitted.
                var dependencies=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.dependencyVisitor();pre.expression().processWithVisitor(dependencies);
                if(!dependencies.attributeReads().isEmpty()||!dependencies.populationTypes().isEmpty())throw new IllegalStateException("AUCTION_CREATION_PRE_REQUIRES_NONEXISTENT_DOMAIN_STATE");
                var witness=new TreeMap<>(pre.witness());witness.putAll(demand.witness());witness.put("required","true");witness.put("entryDemandSourceHash",creating.source().hash());witness.put("selfAtEntry","ABSENT_NO_DOMAIN_VALUE_READ_OR_SYNTHESIZED");witness.put("entryStateHash",hash(creating.before()));
                var variables=new TreeMap<>(pre.variables());variables.put("self",org.tzi.use.uml.ocl.value.UndefinedValue.instance);
                pre=new NativeInvocationEvaluation.Condition(pre.id(),pre.kind(),pre.context(),pre.object(),pre.source(),pre.text(),pre.expression(),creating.before(),creating.before(),variables,pre.unavailable(),pre.unavailableVerdict(),pre.invocation(),witness);
                entry=new Entry(new Marker(scope.id(),actor.player(),actor.roleInterval(),step.task(),demand.episode(),step.goalInvocation(),step.invocation(),"ENABLED"),pre,creating.bytes());
            }
            // A newer source invocation without its own PRE cannot borrow the
            // earlier invocation's historical preSeen/postSeen certificate.
            if(latest.size()>=4096&&!latest.containsKey(slot))throw new IllegalStateException("AUCTION_TASK_SELECTION_CAPACITY");
            latest.put(slot,marker);
            if(entry==null||!same(entry.marker(),marker)||!Boolean.TRUE.equals(demand.required())){unavailable(step,"TASK_TERMINAL_MATCHING_DEMAND_PRE_UNAVAILABLE");return;}
            var view=UseStateReadView.pin(entry.pre().after(),engine.system().state());
            var post=binding.prepare(step.task(),false,engine,scope,actor,run,observation,view,sources,lost);
            var witness=new TreeMap<>(post.witness());witness.putAll(demand.witness());witness.put("required","true");witness.put("matchingPreStateHash",hash(entry.pre().after()));witness.put("postStateHash",hash(view.after()));
            post=condition(post,entry.pre().after(),view.after(),post.unavailable(),witness);
            latest.put(slot,marker);var fixed=step.phase().equals("FAILED")?List.of(new SemanticConstraintResult("AUCTION:SOURCE_FAILURE:"+step.invocation(),"SOURCE_EXECUTION_FAILURE","","",scope.artifact(),"",SemanticVerdict.FAIL,"","EXPLICIT_JASON_STEP_FAILURE_NOT_TASK_VIOLATED",0,step.invocation(),witness)):List.<SemanticConstraintResult>of();
            append(NativeInvocationEvaluation.prepare(List.of(entry.pre(),post),List.of(),fixed));
        }
    }
    private boolean captureEarly(RuntimeEvent event,VerificationObservation observation,AuctionStepObservationDecoder.Step step,AuctionArtifactBindingResolver.Run run,RuntimeSnapshot prefix,boolean lost) {
        if(lost||prefix==null||step.quality()!=Completeness.COMPLETE||seen.contains(step.invocation()))return false;
        var player=engine.objectForSemanticId(step.agent());if(player==null)return false;
        boolean observed=prefix.facts().stream().anyMatch(f->f.kind()==RuntimeFactKind.AGENT&&f.completeness()==Completeness.COMPLETE&&engine.objectForSemanticId(f.id().canonical())==player);if(!observed)return false;
        var candidates=new ArrayList<AuctionSchemeObservationDecoder.Cut>();
        for(var fact:prefix.facts())if(fact.kind()==RuntimeFactKind.ARTIFACT&&fact.values().get("auctionSchemeObservation") instanceof Map<?,?> raw&&"COMPLETE".equals(raw.get("status"))) {
            var source=schemeDecoder.decode(CanonicalJson.object(raw),run.id(),run.generation(),run.modelRevision());if(source.scheme().equals(step.scheme())&&source.artifact().equals(fact.values().get("semanticId"))&&source.uuid().equals(fact.values().get("uuid")))candidates.add(source);
        }
        if(candidates.isEmpty())return false;var source=candidates.stream().max(Comparator.comparingLong(AuctionSchemeObservationDecoder.Cut::sequence)).orElseThrow();
        if(!source.continuous()||!source.goal("start").state().equals("ENABLED")||!source.goal("start").committed().contains(engine.metadata(player,"name")))return false;
        var boards=prefix.facts().stream().filter(f->f.kind()==RuntimeFactKind.SCHEME_BOARD&&f.id().canonical().equals(step.scheme())).toList();if(boards.size()!=1)return false;
        var functional=org.tzi.use.plugins.jacamo.semantics.observer.FunctionalObservation.decode(boards.getFirst().id(),boards.getFirst().values());if(!functional.artifactUuid().equals(source.uuid()))return false;
        String actorType=program.casePlan().tasks().stream().filter(t->t.task().equals(step.task())).findFirst().orElseThrow().actor();
        var declaration=program.casePlan().actors().stream().filter(a->a.kind().equals("ROLE_OCCURRENCE")&&a.actor().equals(actorType)).findFirst().orElseThrow();
        var roles=engine.system().state().allObjects().stream().filter(o->declaration.roleSourceId().equals(engine.metadata(o,"roleSourceId"))&&"true".equals(engine.metadata(o,"auctionLiveEnactment"))&&engine.metadata(player,"semanticId").equals(engine.metadata(o,"rolePlayer"))&&functional.responsibleContexts().stream().anyMatch(c->engine.objectForSemanticId(c)==engine.objectForSemanticId(engine.metadata(o,"roleContext")))).toList();if(roles.size()!=1)return false;
        var role=roles.getFirst();var group=engine.objectForSemanticId(engine.metadata(role,"roleContext"));
        if(group==null||!"COMPLETE_OBSERVED_BOARD".equals(engine.metadata(group,"auctionPopulationQuality"))||event.sourceSequence()<=Long.parseLong(engine.metadata(role,"auctionRoleIntervalObserverFloor")))return false;
        var before=new MSystemState("auction-creation-step-entry",engine.system().state());long bytes=new NativeUseSoilExporter().exportState(before).commands().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if(active.size()+early.size()>=128||active.values().stream().mapToLong(Entry::bytes).sum()+early.values().stream().mapToLong(Early::bytes).sum()+bytes>8L*1024*1024||seen.size()>=8192)throw new IllegalStateException("AUCTION_TASK_PRE_CAPACITY");
        early.put(step.invocation(),new Early(observation,before,engine.metadata(player,"semanticId"),engine.metadata(role,"auctionObservedRoleInterval"),source,bytes));seen.add(step.invocation());return true;
    }
    private static boolean same(Marker before,Marker after){return before.scope().equals(after.scope())&&before.player().equals(after.player())&&before.roleInterval().equals(after.roleInterval())&&before.task().equals(after.task())&&before.demand().equals(after.demand())&&before.goal().equals(after.goal())&&before.invocation().equals(after.invocation());}
    private static NativeInvocationEvaluation.Condition condition(NativeInvocationEvaluation.Condition c,MSystemState before,MSystemState after,String unavailable,Map<String,String> witness){return new NativeInvocationEvaluation.Condition(c.id(),c.kind(),c.context(),c.object(),c.source(),c.text(),c.expression(),before,after,c.variables(),unavailable,org.tzi.use.plugins.jacamo.semantics.ocl.ConstraintCoverageResolver.unavailableVerdict(unavailable),c.invocation(),witness);}
    private static String hash(MSystemState state){return org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(new NativeUseSoilExporter().exportState(state).commands().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private void unavailable(AuctionStepObservationDecoder.Step step,String reason){append(new NativeInvocationEvaluation.Work(List.of(),List.of(),List.of(new SemanticConstraintResult("AUCTION:STEP:"+step.invocation(),"TASK_INVOCATION","","","","",SemanticVerdict.INCONCLUSIVE,"",reason,0,step.invocation(),Map.of("targetTaskId",step.task(),"sourcePhase",step.phase()))),0));}
    private void append(NativeInvocationEvaluation.Work work){var next=new ArrayList<>(prepared);next.add(work);if(next.size()>128)throw new IllegalStateException("AUCTION_TASK_PREPARED_CAPACITY");prepared=List.copyOf(next);}
}
