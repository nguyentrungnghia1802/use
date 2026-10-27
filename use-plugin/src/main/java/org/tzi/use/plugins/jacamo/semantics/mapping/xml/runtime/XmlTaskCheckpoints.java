package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.XmlTaskOclBindings;
import org.tzi.use.semantic.engine.use75.UseStateReadView;

/** Native writer-owned bounded PRE journal for XML-selected source callbacks. No
 * recurrence, model, live state, source listener, receiver clock or transport is owned here. */
public final class XmlTaskCheckpoints {
    public record Marker(String scope,String actor,String player,String role,String interval,String task,String demand,String goal,String invocation,String phase,long entrySequence,String references,String certificate) { }
    public record Entry(Marker marker,NativeInvocationEvaluation.Condition pre,long bytes) { }
    public record Epoch(String run,long generation,String revision) { }
    public record Image(Map<String,Entry> active,Map<String,Marker> latest,Map<String,Marker> historical,Set<String> seen,List<NativeInvocationEvaluation.Work> prepared,Epoch epoch) {
        public Image {active=Map.copyOf(active);latest=Map.copyOf(latest);historical=Map.copyOf(historical);seen=Set.copyOf(seen);prepared=List.copyOf(prepared);}
    }
    private record Candidate(OccurrenceProjection.Scope scope,OccurrenceProjection.Member member,TaskDemandProjection.Demand demand) { }
    private final SemanticProgram program;
    private final NativeRuntimeMutationEngine engine;
    private final XmlTaskOclBindings bindings;
    private final List<JasonStepCutDecoder> decoders;
    private final Map<String,Entry> active=new LinkedHashMap<>();
    private final Map<String,Marker> latest=new LinkedHashMap<>();
    private final Map<String,Marker> historical=new LinkedHashMap<>();
    private final Set<String> seen=new LinkedHashSet<>();
    private List<NativeInvocationEvaluation.Work> prepared=List.of();
    private Epoch epoch;
    public XmlTaskCheckpoints(SemanticProgram program,NativeRuntimeMutationEngine engine,XmlTaskOclBindings bindings) {
        this.program=program;this.engine=engine;this.bindings=bindings;
        if(program.xmlPlan()==null||engine.canonicalProgram()!=program)throw new IllegalArgumentException("XML_TASK_CHECKPOINT_OWNER_MISMATCH");
        var source=ContractPayloads.model(CanonicalJson.object(CanonicalJson.decode(program.complement().assets().stream().filter(a->a.id().equals("official-model")).findFirst().orElseThrow().bytes())));
        program.requireSourceHash(new SourceModelNormalizer().normalize(source).sourceHash());
        decoders=program.xmlPlan().requirements().stream().filter(r->r.provider().equals("jason.plan-boundaries.v1")).map(r->new JasonStepCutDecoder(source,r)).toList();
    }
    public Image savepoint(){return new Image(active,latest,historical,seen,prepared,epoch);}
    public void restore(Image image){active.clear();active.putAll(image.active());latest.clear();latest.putAll(image.latest());historical.clear();historical.putAll(image.historical());seen.clear();seen.addAll(image.seen());prepared=image.prepared();epoch=image.epoch();}
    public void clear(){active.clear();latest.clear();historical.clear();seen.clear();prepared=List.of();epoch=null;}
    public void retireUnavailable(){if(epoch==null)return;var scopes=engine.xmlOccurrences(epoch.run(),epoch.generation(),epoch.revision());var demands=engine.xmlTaskDemands(epoch.run(),epoch.generation(),epoch.revision());active.values().removeIf(e->!retainPending(e.marker(),scopes,demands));historical.values().removeIf(m->!retainPending(m,scopes,demands));latest.values().removeIf(m->!currentRole(m,scopes));}
    public List<NativeInvocationEvaluation.Work> drain(){var result=prepared;prepared=List.of();return result;}
    public static String key(String scope,String player,String role,String interval,String task){return SemanticJson.hash(List.of(scope,player,role,interval,task));}
    private static String key(Marker marker){return key(marker.scope(),marker.player(),marker.role(),marker.interval(),marker.task());}
    public Marker marker(String scope,String player,String role,String interval,String task){return latest.get(key(scope,player,role,interval,task));}
    /** Historical source identity only. It must never supply a current PRE/POST
     * tuple; current demand quality and marker() remain the authority gates. */
    public Marker historicalMarker(String scope,String player,String role,String interval,String task){return historical.get(key(scope,player,role,interval,task));}
    public void observe(RuntimeEvent event,Map<String,Completeness> sources,boolean lost) {
        program.assertUnchanged();if(!VerificationObservation.present(event))return;var observation=VerificationObservation.require(event);if(observation.kind()!=VerificationObservation.Kind.JASON_STEP)return;
        var selected=decoders.stream().filter(d->d.accepts(observation)).toList();if(selected.isEmpty())return;if(selected.size()!=1)throw new IllegalArgumentException("XML_TASK_SOURCE_REQUEST_AMBIGUOUS");
        var step=selected.getFirst().decode(observation,event.sessionId(),event.generation(),event.modelRevision());
        if(program.xmlPlan().inputs().specification().root().child("omittedConcepts").children("omit").stream().anyMatch(n->n.optional("sourceStep").equals(step.task())&&n.optional("handledBy").equals("TRACE_ONLY"))) {
            append(new NativeInvocationEvaluation.Work(List.of(),List.of(),List.of(new SemanticConstraintResult("XML:TRACE:"+step.invocation(),"SOURCE_TRACE","","","","",SemanticVerdict.SKIPPED,"","DECLARED_SOURCE_OBSERVATION_ONLY",0,step.invocation(),Map.of("sourceStepId",step.task(),"sourcePhase",step.phase()))),0));return;
        }
        if(!bindings.slots().containsKey(step.task())){unavailable(step,"UNSUPPORTED:XML_TASK_OBSERVATION_BINDING_REQUIRED");return;}
        var next=new Epoch(event.sessionId(),event.generation(),event.modelRevision());if(epoch!=null&&!epoch.equals(next))throw new IllegalArgumentException("XML_TASK_EPOCH_REQUIRES_CLEAR");epoch=next;
        if(lost){clear();unavailable(step,"COVERAGE_LOST");return;}
        var scopes=engine.xmlOccurrences(next.run(),next.generation(),next.revision());var demands=engine.xmlTaskDemands(next.run(),next.generation(),next.revision());
        if(step.phase().equals("ENABLED")&&seen.contains(step.invocation())){active.remove(step.invocation());latest.values().removeIf(m->m.invocation().equals(step.invocation()));unavailable(step,"DUPLICATE_TASK_ENTRY_INVALIDATED");return;}
        active.values().removeIf(e->!retainPending(e.marker(),scopes,demands));historical.values().removeIf(m->!retainPending(m,scopes,demands));latest.values().removeIf(m->!currentRole(m,scopes));
        var candidates=new ArrayList<Candidate>();
        for(var scope:scopes)if(scope.complete()&&bindings.references(step.task(),scope,step,engine,step.phase().equals("ENABLED")))for(var member:scope.members())
            if(member.actor().equals(program.goalBindings().elements().get(step.task()).actorId())&&engine.objectForSemanticId(step.agent())==engine.objectForSemanticId(member.player())) {
                var matches=demands.stream().filter(d->d.target().equals(step.task())&&d.scope().equals(scope.id())&&d.player().equals(member.player())&&d.role().equals(member.role())&&d.roleInterval().equals(member.interval())&&d.quality()==Completeness.COMPLETE&&Boolean.TRUE.equals(d.required())&&!d.episode().isBlank()).toList();
                if(matches.size()==1)candidates.add(new Candidate(scope,member,matches.getFirst()));
            }
        if(candidates.size()!=1){if(!step.phase().equals("ENABLED"))active.remove(step.invocation());unavailable(step,"EXACT_TASK_CAST_OR_DEMAND_UNAVAILABLE");return;}
        var candidate=candidates.getFirst();var member=candidate.member();var scope=candidate.scope();var demand=candidate.demand();
        var role=engine.objectForSemanticId(member.role());String floor=role==null?"":engine.metadata(role,"xml:m0:observer-floor");
        try{if(floor.isBlank()||event.sourceSequence()<=Long.parseLong(floor)){unavailable(step,"STEP_PRECEDES_OBSERVED_ROLE_INTERVAL");return;}}catch(NumberFormatException invalid){unavailable(step,"OBSERVED_ROLE_INTERVAL_FLOOR_UNAVAILABLE");return;}
        String references=referenceHash(bindings.slots().get(step.task()).definition(),scope,member,step.task());
        var marker=new Marker(scope.id(),member.actor(),member.player(),member.role(),member.interval(),step.task(),demand.episode(),step.goalInvocation(),step.invocation(),step.phase(),event.sourceSequence(),references,"");String slot=key(marker);
        if(step.phase().equals("ENABLED")) {
            if(seen.contains(step.invocation())){active.remove(step.invocation());latest.remove(slot);unavailable(step,"DUPLICATE_TASK_ENTRY_INVALIDATED");return;}
            remember(step.invocation());
            var overlaps=active.values().stream().filter(e->key(e.marker()).equals(slot)).map(e->e.marker().invocation()).toList();
            if(!overlaps.isEmpty()){overlaps.forEach(active::remove);latest.remove(slot);unavailable(step,"UNSUPPORTED:OVERLAPPING_TASK_INVOCATIONS_IN_ONE_SLOT");return;}
            var condition=bindings.prepare(step.task(),true,engine,scope,member,demand,step,UseStateReadView.pin(null,engine.system().state()),sources,false);
            marker=certify(marker,SemanticJson.hash(List.of(program.hash(),SemanticJson.tree(epoch),key(marker),marker.demand(),marker.goal(),marker.invocation(),marker.entrySequence(),marker.references())));
            condition=certificate(condition,marker.certificate());
            long bytes=stateBytes(condition.after());
            if(active.size()>=128||active.values().stream().mapToLong(Entry::bytes).sum()+bytes>8L*1024*1024)throw new IllegalStateException("XML_TASK_PRE_CAPACITY");
            active.put(step.invocation(),new Entry(marker,condition,bytes));select(slot,marker);append(NativeInvocationEvaluation.prepare(List.of(condition),List.of(),List.of()));
        }else {
            if(!seen.contains(step.invocation()))remember(step.invocation());var entry=active.remove(step.invocation());select(slot,marker);
            if(entry==null||!same(entry,marker)||event.sourceSequence()<=entry.marker().entrySequence()){unavailable(step,"TASK_TERMINAL_MATCHING_PRE_UNAVAILABLE");return;}
            marker=certify(marker,entry.marker().certificate());select(slot,marker);
            var view=UseStateReadView.pin(entry.pre().after(),engine.system().state());var post=bindings.prepare(step.task(),false,engine,scope,member,demand,step,view,sources,false);
            var witness=new TreeMap<>(post.witness());witness.put("matchingPreStateHash",hash(entry.pre().after()));witness.put("postStateHash",hash(view.after()));
            String priorCoverage=entry.pre().witness().getOrDefault("postPreCoverage","POST_PRE_COVERAGE_NOT_CAPTURED");witness.put("matchingPreCoverage",priorCoverage);
            String reason=post.unavailable().isBlank()&&!priorCoverage.isBlank()?"PRE_STATE_"+priorCoverage:post.unavailable();
            post=new NativeInvocationEvaluation.Condition(post.id(),post.kind(),post.context(),post.object(),post.source(),post.text(),post.expression(),entry.pre().after(),post.after(),post.variables(),reason,org.tzi.use.plugins.jacamo.semantics.ocl.ConstraintCoverageResolver.unavailableVerdict(reason),post.invocation(),witness);
            post=certificate(post,marker.certificate());
            append(NativeInvocationEvaluation.prepare(List.of(entry.pre(),post),List.of(),List.of()));
        }
    }
    private static String referenceHash(org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node definition,OccurrenceProjection.Scope scope,OccurrenceProjection.Member member,String task) {
        var refs=new TreeMap<String,Object>();for(String attribute:List.of("stepSchemeColumn","stepArtifactColumn"))if(!definition.optional(attribute).isBlank())refs.put(attribute,scope.bindings().getOrDefault(definition.attr(attribute),""));refs.put("self",member.selves().getOrDefault(task,""));refs.put("resources",scope.resources());return new String(CanonicalJson.encode(refs),java.nio.charset.StandardCharsets.UTF_8);
    }
    private boolean same(Entry entry,Marker second){var first=entry.marker();return key(first).equals(key(second))&&first.actor().equals(second.actor())&&first.demand().equals(second.demand())&&first.goal().equals(second.goal())&&first.invocation().equals(second.invocation())&&compatibleReferences(entry,second);}
    private static Marker certify(Marker m,String certificate){return new Marker(m.scope(),m.actor(),m.player(),m.role(),m.interval(),m.task(),m.demand(),m.goal(),m.invocation(),m.phase(),m.entrySequence(),m.references(),certificate);}
    private static NativeInvocationEvaluation.Condition certificate(NativeInvocationEvaluation.Condition c,String certificate){var witness=new TreeMap<>(c.witness());witness.put("checkpointBinding",certificate);return new NativeInvocationEvaluation.Condition(c.id(),c.kind(),c.context(),c.object(),c.source(),c.text(),c.expression(),c.before(),c.after(),c.variables(),c.unavailable(),c.unavailableVerdict(),c.invocation(),witness);}
    private boolean compatibleReferences(Entry entry,Marker second) {
        var first=entry.marker();
        if(first.references().equals(second.references()))return true;
        if(!bindings.permitsLateSelf(first.task(),entry.pre()))return false;
        var old=CanonicalJson.object(CanonicalJson.decode(first.references().getBytes(java.nio.charset.StandardCharsets.UTF_8)));var current=CanonicalJson.object(CanonicalJson.decode(second.references().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // Only a previously absent self may become observed. Source scheme/other pinned
        // references must remain exact; existing resource identities cannot be replaced.
        if(!"".equals(old.get("self"))||!(current.get("self") instanceof String self)||self.isBlank())return false;
        for(var field:old.entrySet())if(!Set.of("self","resources","stepArtifactColumn").contains(field.getKey())&&!Objects.equals(field.getValue(),current.get(field.getKey())))return false;
        Object oldArtifact=old.get("stepArtifactColumn");if(oldArtifact!=null&&!oldArtifact.equals("")&&!Objects.equals(oldArtifact,current.get("stepArtifactColumn")))return false;
        var oldResources=CanonicalJson.object(old.get("resources"));var nextResources=CanonicalJson.object(current.get("resources"));return oldResources.entrySet().stream().allMatch(e->Objects.equals(nextResources.get(e.getKey()),e.getValue()));
    }
    private static boolean currentRole(Marker marker,List<OccurrenceProjection.Scope> scopes){return scopes.stream().anyMatch(s->s.complete()&&s.id().equals(marker.scope())&&s.members().stream().anyMatch(m->m.player().equals(marker.player())&&m.role().equals(marker.role())&&m.interval().equals(marker.interval())));}
    /** Retention is not verification authority. A pending exact PRE survives temporary
     * unavailability, but terminal evaluation still requires a fresh complete cast/demand
     * and same() checks all invocation, actor, interval, episode and reference identities. */
    private boolean retainPending(Marker marker,List<OccurrenceProjection.Scope> scopes,List<TaskDemandProjection.Demand> demands) {
        if(engine.objectForSemanticId(marker.player())==null)return false;
        if(!marker.role().isBlank()) {
            var role=engine.objectForSemanticId(marker.role());
            if(role==null||!marker.interval().equals(engine.metadata(role,"xml:m0:lifetime"))||!marker.player().equals(engine.metadata(role,"rolePlayer")))return false;
        }
        var selected=scopes.stream().filter(s->s.id().equals(marker.scope())).toList();
        if(selected.size()!=1)return false;
        if(!selected.getFirst().complete())return true;
        if(!currentRole(marker,scopes))return false;
        var known=demands.stream().filter(d->d.scope().equals(marker.scope())&&d.target().equals(marker.task())&&d.player().equals(marker.player())&&d.role().equals(marker.role())&&d.roleInterval().equals(marker.interval())&&d.quality()==Completeness.COMPLETE&&d.required()!=null).toList();
        return known.isEmpty()||known.size()==1&&Boolean.TRUE.equals(known.getFirst().required())&&known.getFirst().episode().equals(marker.demand());
    }
    private void remember(String invocation){if(seen.size()>=8192)throw new IllegalStateException("XML_TASK_INVOCATION_CAPACITY");seen.add(invocation);}
    private void select(String slot,Marker marker){if(latest.size()>=4096&&!latest.containsKey(slot)||historical.size()>=4096&&!historical.containsKey(slot))throw new IllegalStateException("XML_TASK_SELECTION_CAPACITY");latest.put(slot,marker);historical.put(slot,marker);}
    private void append(NativeInvocationEvaluation.Work work){var next=new ArrayList<>(prepared);next.add(work);if(next.size()>128)throw new IllegalStateException("XML_TASK_PREPARED_CAPACITY");prepared=List.copyOf(next);}
    private void unavailable(JasonStepCutDecoder.Step step,String reason){append(new NativeInvocationEvaluation.Work(List.of(),List.of(),List.of(new SemanticConstraintResult("XML:STEP:"+step.invocation(),"TASK_INVOCATION","","","","",SemanticVerdict.INCONCLUSIVE,"",reason,0,step.invocation(),Map.of("targetTaskId",step.task(),"sourcePhase",step.phase()))),0));}
    private static long stateBytes(org.tzi.use.uml.sys.MSystemState state){return new NativeUseSoilExporter().exportState(state).commands().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}
    private static String hash(org.tzi.use.uml.sys.MSystemState state){return org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(new NativeUseSoilExporter().exportState(state).commands().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
