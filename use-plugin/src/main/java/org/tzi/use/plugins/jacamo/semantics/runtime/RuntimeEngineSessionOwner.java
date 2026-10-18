package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.ConstraintCoverageResolver;
import org.tzi.use.semantic.engine.runtime.*;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MObject;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser.GoalModelInstanceTopology;
import org.vnu.sme.goal.verify.conformance.semantics.*;

/** One consumer session: immutable definitions, pinned native read views, one
 * ordered evaluation lane. It never creates/writes/subscribes to an MSystem. */
public final class RuntimeEngineSessionOwner implements AutoCloseable {
    public record Identity(String run,long generation,String revision,String program,String profile) { }
    public record GoalRead(String element,NativeOclBackend.Compiled expression,Map<String,Value> variables,String unavailable) {public GoalRead{variables=Map.copyOf(variables);}}
    public record PreparedCast(JaCaMoOccurrenceBindingAdapter.ContextCast cast,Map<String,GoalRead> goals,boolean exact) {public PreparedCast{goals=Map.copyOf(goals);}}
    public record Cut(String owner,Identity identity,long ticket,long version,String boundary,String stateHash,UseStateReadView state,
            List<PreparedCast> casts,boolean advance,boolean lost,List<SemanticConstraintResult> invocation,
            ExternalOclConstraintService.PreparedEvaluation nativeChecks,long bytes,Map<String,Completeness> sources,
            List<org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeInvocationEvaluation.Work> invocationWork) {
        public Cut{casts=List.copyOf(casts);invocation=List.copyOf(invocation);sources=Map.copyOf(sources);invocationWork=List.copyOf(invocationWork);}
    }
    public record Evaluation(SemanticVerificationEnvelope evidence,List<ExternalOclConstraintService.Outcome> outcomes) {public Evaluation{outcomes=List.copyOf(outcomes);}}
    private record ContextSession(String revision,Use4GoalRuntimeSessionAdapter engine,EngineStepResult last) { }
    public record Checkpoint(String owner,long ticket,Identity identity,Map<String,EngineCheckpoint> histories,Map<String,EngineStepResult> results,Set<EngineOccurrenceKey> gaps,boolean discontinuous) {
        public Checkpoint{histories=Map.copyOf(histories);results=Map.copyOf(results);gaps=Set.copyOf(gaps);}
    }
    private final SemanticProgram program;
    private final NativeRuntimeMutationEngine nativeEngine;
    private final NativeOclBackend ocl;
    private final Map<String,NativeOclBackend.Compiled> conditions=new ConcurrentSkipListMap<>();
    private final JaCaMoOccurrenceBindingAdapter bindings=new JaCaMoOccurrenceBindingAdapter();
    private final ConstraintCoverageResolver coverage=new ConstraintCoverageResolver();
    private final ObservationQualityGate quality=new ObservationQualityGate();
    private final Set<String> fresh=new HashSet<>();
    private final ThreadPoolExecutor lane;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final String owner=UUID.randomUUID().toString();
    private final AtomicLong ticket=new AtomicLong(),retainedBytes=new AtomicLong();
    private final AtomicInteger pins=new AtomicInteger();
    private final Set<CompletableFuture<Evaluation>> pending=ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<?>> controls=ConcurrentHashMap.newKeySet();
    // Only the evaluation lane touches these histories.
    private final Map<String,ContextSession> sessions=new TreeMap<>();
    private final Set<EngineOccurrenceKey> gaps=new HashSet<>();
    private Identity evaluatedIdentity;
    private volatile Identity identity;
    private volatile boolean discontinuous;
    private volatile long completed,topologyBuilds;
    private volatile Evaluation latest;
    private volatile java.util.function.Consumer<String> faults=point->{};
    void faultProbe(java.util.function.Consumer<String> probe){faults=Objects.requireNonNull(probe);}
    private final Map<String,String> originalClasses;

    public RuntimeEngineSessionOwner(NativeRuntimeMutationEngine engine,String run,long generation,String revision) {
        if(!engine.canonicalMappingConfigured())throw new IllegalArgumentException("ORIGINAL_ENGINE_REQUIRES_CANONICAL_MAPPING");
        nativeEngine=engine;program=engine.canonicalProgram();program.assertUnchanged();
        identity=identity(run,generation,revision);ocl=new NativeOclBackend(engine.system().model(),program.hash(),program.profile().engine().name());
        try {
            for(var element:program.goalBindings().elements().values())if(!element.derived()&&program.goalModel().findElement(element.elementId()).orElse(null) instanceof Goal goal) {
                String id=program.contracts().stream().filter(c->c.kind()==OclContractSpec.Kind.GOAL&&c.targetElementId().equals(goal.id())).map(MappingRuleSet.Contract::id).findFirst().orElse("GOAL:"+goal.id());
                var variables=Map.<String,org.tzi.use.uml.ocl.type.Type>of("player",engine.system().model().getClass("RuntimePlayer"),"orgContext",engine.system().model().getClass("OrgContext"));
                conditions.put(goal.id(),ocl.compile(new OclContractSpec(id,OclContractSpec.Kind.GOAL,engine.system().model().getClass(element.selfDeclaration()),null,goal.oclSource(),variables)));
            }
            var hashes=new TreeMap<String,String>();
            for(Class<?> type:List.of(GoalMarking.class,TaskMarking.class,IStarMarking.class,IStarPropagation.class,org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser.GoalModelInstanceTranslator.class))
                try(var input=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){if(input==null)throw new IllegalStateException("ORIGINAL_ENGINE_CLASS_BYTES_UNAVAILABLE");hashes.put(type.getName(),ExternalOclConstraintService.sha256(input.readAllBytes()));}
            originalClasses=Map.copyOf(hashes);
        }catch(Exception error){ocl.close();throw new IllegalStateException("ORIGINAL_ENGINE_PREPARATION_FAILED",error);}
        lane=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(program.profile().engine().maxPendingInputs()),r->{var thread=new Thread(r,"use-original-engine-"+UUID.randomUUID());thread.setDaemon(true);return thread;}) {
            @Override protected void terminated(){sessions.values().forEach(s->s.engine().close());sessions.clear();gaps.clear();ocl.close();conditions.clear();}
        };
    }
    private Identity identity(String run,long generation,String revision){if(run==null||run.isBlank()||generation<0||revision==null||revision.isBlank())throw new IllegalArgumentException("ENGINE_OWNER_IDENTITY_REQUIRED");return new Identity(run,generation,revision,program.hash(),program.profile().hash());}
    /** Writer-only: same run resync preserves histories; producer epoch/revision replaces them. */
    public void rebind(String run,long generation,String revision) {
        requireOpen();var next=identity(run,generation,revision);
        if(!identity.equals(next)){identity=next;ticket.incrementAndGet();fresh.clear();discontinuous=false;latest=null;}
    }
    public void observed(List<RuntimeFact> facts,boolean replacement) {
        requireOpen();if(replacement)fresh.clear();
        for(var fact:facts)if(fact.kind()==RuntimeFactKind.GROUP_BOARD) {
            String id=fact.id().canonical();fresh.remove(id);var object=nativeEngine.objectForSemanticId(id);
            if(object!=null&&fact.completeness()==Completeness.COMPLETE&&fact.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY
                    &&ExternalOclConstraintService.sha256(CanonicalJson.encode(fact.values())).equals(nativeEngine.metadata(object,"lastGroupCut")))fresh.add(id);
        }
    }
    public void observed(RuntimeEvent event) {
        if(event.entityId()!=null&&event.factKind()==RuntimeFactKind.GROUP_BOARD)observed(List.of(new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),event.evidence())),false);
    }
    public Set<String> freshContexts(){return Set.copyOf(fresh);}
    /** Writer metadata only. Histories advance on the lane after native commit. */
    public void restoreAdmission(Set<String> contexts){requireOpen();fresh.clear();fresh.addAll(contexts);}
    public void commitIdentity(String run,long generation,String revision){var admitted=freshContexts();rebind(run,generation,revision);fresh.addAll(admitted);}
    public void lost(){discontinuous=true;fresh.clear();latest=null;}
    /** Must be called under the native writer/reader barrier. */
    public Cut prepare(long version,String boundary,String stateHash,Map<String,Completeness> sources,boolean lost,boolean advance,
            List<SemanticConstraintResult> invocation,ExternalOclConstraintService.PreparedEvaluation nativeChecks,long estimatedBytes) {
        return prepare(version,boundary,stateHash,sources,lost,advance,invocation,nativeChecks,List.of(),estimatedBytes);
    }
    public Cut prepare(long version,String boundary,String stateHash,Map<String,Completeness> sources,boolean lost,boolean advance,
            List<SemanticConstraintResult> invocation,ExternalOclConstraintService.PreparedEvaluation nativeChecks,
            List<org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeInvocationEvaluation.Work> work,long estimatedBytes) {
        var checked=Collections.newSetFromMap(new IdentityHashMap<org.tzi.use.uml.sys.MSystemState,Boolean>());
        for(var item:work){if(item.bytes()<0)throw new IllegalArgumentException("INVOCATION_PIN_BYTES_INVALID");estimatedBytes=Math.addExact(estimatedBytes,item.bytes());for(var condition:item.conditions()) {
            if(condition.before().system()!=nativeEngine.system()||condition.after().system()!=nativeEngine.system())throw new IllegalArgumentException("INVOCATION_PIN_OWNER_MISMATCH");
            if(condition.kind().equals("POST")&&checked.add(condition.after())&&!stateHash.equals(ExternalOclConstraintService.sha256(new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().exportState(condition.after()).commands().getBytes(StandardCharsets.UTF_8))))throw new IllegalArgumentException("INVOCATION_POST_CUT_HASH_MISMATCH");
        }}
        requireOpen();program.assertUnchanged();if(estimatedBytes<0||estimatedBytes>16L*1024*1024)throw new IllegalStateException("ENGINE_PIN_BYTE_CAPACITY");
        var casts=new ArrayList<PreparedCast>();
        for(var cast:bindings.resolve(program,nativeEngine)) {
            var reads=new TreeMap<String,GoalRead>();
            for(var item:conditions.entrySet()) {
                var template=program.goalBindings().elements().get(item.getKey());var actor=cast.actors().get(template.actorId());
                var self=nativeEngine.objectForSemanticId(cast.selves().getOrDefault(template.elementId(),""));var context=nativeEngine.objectForSemanticId(cast.contextId());
                var player=actor==null?null:nativeEngine.objectForSemanticId(actor.playerId());var variables=new TreeMap<String,Value>();
                String reason=!cast.complete()?String.join(";",cast.unavailable()):!fresh.contains(cast.contextId())?"EXACT_CONTEXT_CUT_UNAVAILABLE":"";
                if(self==null||player==null||context==null)reason="EXACT_OCL_BINDING_UNAVAILABLE";
                else {variables.put("self",self.value());variables.put("player",player.value());variables.put("orgContext",context.value());if(reason.isBlank())reason=coverage.unavailableSelf(item.getValue().expression(),nativeEngine,self,sources,lost,fresh);}
                reads.put(item.getKey(),new GoalRead(item.getKey(),item.getValue(),variables,reason));
            }
            casts.add(new PreparedCast(cast,reads,cast.complete()&&fresh.contains(cast.contextId())));
        }
        return new Cut(owner,identity,ticket.get(),version,boundary,stateHash,UseStateReadView.pin(null,nativeEngine.system().state()),casts,advance,lost,invocation,nativeChecks,Math.max(1024,estimatedBytes),sources,work);
    }
    public CompletableFuture<Evaluation> submit(Cut cut) {
        requireOpen();var future=new CompletableFuture<Evaluation>();
        if(!owner.equals(cut.owner())||cut.ticket()!=ticket.get()||!cut.identity().equals(identity)||cut.state().after().system()!=nativeEngine.system()) {
            future.completeExceptionally(new IllegalArgumentException("ENGINE_CUT_OWNER_MISMATCH"));return future;
        }
        synchronized(pending) {
            if(pins.get()>=8||retainedBytes.get()+cut.bytes()>16L*1024*1024){discontinuous=true;future.completeExceptionally(new IllegalStateException("ENGINE_PIN_CAPACITY_EXHAUSTED"));return future;}
            pins.incrementAndGet();retainedBytes.addAndGet(cut.bytes());pending.add(future);
        }
        Runnable work=()->{
            try {if(closed.get()||cut.ticket()!=ticket.get())throw new CancellationException("ENGINE_OWNER_REVOKED");
                var result=evaluate(cut);if(closed.get()||cut.ticket()!=ticket.get())throw new CancellationException("ENGINE_OWNER_REVOKED");latest=result;completed++;future.complete(result);
            }catch(Throwable error){if(!(error instanceof CancellationException)&&cut.ticket()==ticket.get())discontinuous=true;future.completeExceptionally(error);}
            finally{release(future,cut.bytes());}
        };
        try{lane.execute(work);}catch(RejectedExecutionException rejected){release(future,cut.bytes());future.completeExceptionally(rejected);}
        return future;
    }
    private void release(CompletableFuture<Evaluation> future,long bytes){synchronized(pending){if(pending.remove(future)){pins.decrementAndGet();retainedBytes.addAndGet(-bytes);}}}
    private Evaluation evaluate(Cut cut) {
        if(!cut.identity().equals(evaluatedIdentity)){sessions.values().forEach(s->s.engine().close());sessions.clear();gaps.clear();evaluatedIdentity=cut.identity();}
        var rows=new ArrayList<SemanticConstraintResult>(cut.invocation().stream().map(r->r.atVersion(cut.version())).toList());var contexts=new ArrayList<Object>();var currentContexts=new HashSet<String>();
        var nativeResults=ExternalOclConstraintService.evaluatePrepared(cut.nativeChecks(),cut.state().after());
        cut.invocationWork().forEach(work->rows.addAll(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeInvocationEvaluation.evaluate(work,cut.version())));
        var savedSessions=new TreeMap<>(sessions);var savedGaps=new HashSet<>(gaps);long savedBuilds=topologyBuilds;
        var save=new LinkedHashMap<Use4GoalRuntimeSessionAdapter,EngineCheckpoint>();sessions.values().forEach(s->save.put(s.engine(),s.engine().checkpoint()));
        try {
            int total=0;
            for(var prepared:cut.casts()) {
                var cast=prepared.cast();currentContexts.add(cast.contextId());
                if(!cast.complete()){contexts.add(Map.of("context",cast.contextId(),"quality","UNAVAILABLE","diagnostics",cast.unavailable()));continue;}
                var owned=sessions.get(cast.contextId());
                if(owned==null||!owned.revision().equals(cast.episode())) {
                    var next=new Use4GoalRuntimeSessionAdapter(program.goalModel(),cast.instances(),(a,x,b,y)->cast.actors().values().stream().anyMatch(v->v.actorId().equals(a)&&v.instanceId().equals(x))&&cast.actors().values().stream().anyMatch(v->v.actorId().equals(b)&&v.instanceId().equals(y)),cast.episode());
                    if(owned!=null)next.seedStableHistory(owned.engine().checkpoint(),episodes(next,cast));
                    owned=new ContextSession(cast.episode(),next,null);sessions.put(cast.contextId(),owned);topologyBuilds++;
                    faults.accept("AFTER_TOPOLOGY_BUILD");
                }
                total+=owned.engine().topology().occurrences().size();if(total>program.profile().engine().maxOccurrences())throw new IllegalStateException("ENGINE_TOTAL_OCCURRENCE_CAPACITY");
                var goals=new TreeMap<String,EngineInputs.GoalInput>();var tasks=new TreeMap<String,EngineInputs.TaskInput>();var statusQuality=new TreeMap<String,String>();var episodes=episodes(owned.engine(),cast);
                for(var occurrence:owned.engine().topology().occurrences().values()) {
                    var template=program.goalBindings().elements().get(occurrence.sourceElementId());if(template==null||template.derived())continue;
                    String id=occurrence.occurrenceId(),episode=episodes.get(id);var key=new EngineOccurrenceKey(occurrence.sourceElementId(),occurrence.stack(),episode);
                    boolean exact=!cut.lost()&&prepared.exact();
                    if(cut.lost()||discontinuous)gaps.add(key);
                    var read=prepared.goals().get(template.elementId());
                    if(read!=null) {
                        var value=ocl.evaluate(read.expression(),cut.state(),read.variables(),read.unavailable());var decision=quality.booleanInput(value,exact,cut.lost());
                        goals.put(id,new EngineInputs.GoalInput(episode,activation(owned.engine(),id,exact),decision.known()));
                        statusQuality.put(id,gaps.contains(key)?"HISTORY_GAP":decision.admitted()?"KNOWN":"UNAVAILABLE:"+decision.reason());
                        var self=read.variables().get("self");
                        rows.add(new SemanticConstraintResult("ORIGINAL:CONDITION:"+cast.contextId()+":"+id,"GOAL_CONDITION",read.expression().specification().self().name(),self instanceof ObjectValue object?object.value().name():"",cast.selves().getOrDefault(template.elementId(),""),read.expression().specification().body(),verdict(value),value.backendValue(),decision.reason(),cut.version(),"",Map.of("occurrence",id,"episode",episode)));
                    } else {
                        var tuple=taskInput(cut,cast,template,rows);tasks.put(id,new EngineInputs.TaskInput(episode,exact?Boolean.TRUE:null,tuple[0],tuple[1]));
                        statusQuality.put(id,gaps.contains(key)?"HISTORY_GAP":tuple[0]!=null&&tuple[1]!=null&&exact?"KNOWN":"EXACT_INVOCATION_TUPLE_UNAVAILABLE");
                    }
                }
                var input=new EngineInputs(goals,tasks,Map.of());var before=owned.engine().checkpoint();
                if(cut.advance())faults.accept("BEFORE_ENGINE_STEP");
                EngineStepResult result=cut.advance()?owned.engine().step(input):owned.last();
                if(cut.advance())faults.accept("AFTER_PROPAGATION");
                if(cut.advance())sessions.put(cast.contextId(),new ContextSession(owned.revision(),owned.engine(),result));
                contexts.add(SemanticResultAdapter.context(cast,owned.engine(),result,before,input,statusQuality,cut.advance()));
            }
            // A complete cast withdrawal changes the topology; missing/partial cuts keep
            // last raw history for diagnosis, never certificate it as current.
            if(cut.advance())sessions.keySet().removeIf(id->!currentContexts.contains(id));
            if(sessions.values().stream().mapToInt(s->s.engine().topology().occurrences().size()).sum()>program.profile().engine().maxOccurrences())throw new IllegalStateException("ENGINE_HELD_OCCURRENCE_CAPACITY");
            var heldKeys=new HashSet<EngineOccurrenceKey>();sessions.values().forEach(s->heldKeys.addAll(s.engine().checkpoint().keys().values()));gaps.retainAll(heldKeys);
            var result=SemanticResultAdapter.result(program,cut,rows,nativeResults,contexts,originalClasses,discontinuous);
            savedSessions.values().stream().filter(old->sessions.values().stream().noneMatch(now->now.engine()==old.engine())).forEach(old->old.engine().close());
            return result;
        }catch(RuntimeException failure){
            sessions.values().stream().filter(now->savedSessions.values().stream().noneMatch(old->old.engine()==now.engine())).forEach(now->now.engine().close());
            save.forEach((session,image)->{try{session.restore(image);}catch(RuntimeException rollback){failure.addSuppressed(rollback);}});
            sessions.clear();sessions.putAll(savedSessions);gaps.clear();gaps.addAll(savedGaps);topologyBuilds=savedBuilds;throw failure;
        }
    }
    private static SemanticVerdict verdict(NativeOclOutcome value){return SemanticVerdict.valueOf(value.status().name());}
    private static Boolean activation(Use4GoalRuntimeSessionAdapter session,String id,boolean exact) {
        if(!exact)return null;var graph=GoalActivationGraph.of(session.topology().instance().model());var seen=new HashSet<String>();String current=id;
        while(graph.parentOf(current).isPresent()){if(!seen.add(current))throw new IllegalStateException("ACTIVATION_PARENT_CYCLE");current=graph.parentOf(current).orElseThrow();}
        return graph.roots().contains(current)?Boolean.TRUE:null;
    }
    private Map<String,String> episodes(Use4GoalRuntimeSessionAdapter session,JaCaMoOccurrenceBindingAdapter.ContextCast cast) {
        var result=new TreeMap<String,String>();
        for(var occurrence:session.topology().occurrences().values()) {
            var stack=occurrence.stack().stream().map(b->cast.actors().values().stream().filter(a->a.actorId().equals(b.actorType())&&a.instanceId().equals(b.instanceId())).findFirst().orElseThrow()).toList();
            var template=program.goalBindings().elements().get(occurrence.sourceElementId());String self=template==null?"":cast.selves().getOrDefault(template.elementId(),"");
            result.put(occurrence.occurrenceId(),SemanticJson.hash(List.of(cutIdentity(),cast.contextId(),SemanticJson.tree(stack),self)));
        }
        return result;
    }
    private Object cutIdentity(){return SemanticJson.tree(evaluatedIdentity==null?identity:evaluatedIdentity);}
    private Boolean[] taskInput(Cut cut,JaCaMoOccurrenceBindingAdapter.ContextCast cast,GoalRuntimeBindingPlan.ElementTemplate template,List<SemanticConstraintResult> rows) {
        var actor=cast.actors().get(template.actorId());String token=taskToken(cast,template);
        var matches=rows.stream().filter(r->r.kind().equals("INVOCATION_CONFORMANCE")&&template.elementId().equals(r.witness().get("targetTaskId"))&&cast.selves().get(template.elementId()).equals(r.sourceIdentity())&&actor.playerId().equals(r.witness().get("performerSemanticId")))
                .filter(r->token.equals(CanonicalJson.object(CanonicalJson.decode(r.witness().getOrDefault("taskBindingTokens","{}").getBytes(StandardCharsets.UTF_8))).get(cast.contextId()+"|"+template.elementId()))).toList();
        if(matches.size()!=1||cut.lost())return new Boolean[]{null,null};var witness=matches.getFirst().witness();
        return new Boolean[]{known(witness.get("preVerdict")),known(witness.get("postVerdict"))};
    }
    private static Boolean known(String value){return "PASS".equals(value)?Boolean.TRUE:"FAIL".equals(value)?Boolean.FALSE:null;}
    /** Pinned at PRE by NativeOperationCheckpoints. It is not a goal truth value. */
    public Map<String,String> taskBindings(MObject player) {
        if(player==null||closed.get())return Map.of();var result=new TreeMap<String,String>();String identity=nativeEngine.metadata(player,"semanticId");
        var contextsByTask=new TreeMap<String,List<String>>();
        for(var cast:bindings.resolve(program,nativeEngine))if(cast.complete()&&fresh.contains(cast.contextId()))for(var template:program.goalBindings().elements().values())
            if(!template.derived()&&program.goalModel().findElement(template.elementId()).orElse(null) instanceof Task&&cast.actors().get(template.actorId()).playerId().equals(identity)) {
                String key=cast.contextId()+"|"+template.elementId();result.put(key,taskToken(cast,template));contextsByTask.computeIfAbsent(template.elementId(),ignored->new ArrayList<>()).add(key);
            }
        // OpId proves the performer and artifact, but carries no organizational
        // context. V1 admits a Task only when those bindings identify one cast.
        contextsByTask.values().stream().filter(keys->keys.size()!=1).forEach(keys->keys.forEach(result::remove));
        return Map.copyOf(result);
    }
    private String taskToken(JaCaMoOccurrenceBindingAdapter.ContextCast cast,GoalRuntimeBindingPlan.ElementTemplate template){return SemanticJson.hash(List.of(SemanticJson.tree(identity),cast.episode(),template.elementId(),cast.actors().get(template.actorId()).playerId(),cast.selves().get(template.elementId())));}
    public Map<String,Object> metrics(){return Map.of("pendingPins",pins.get(),"pinnedSerializedBytes",retainedBytes.get(),"queue",lane.getQueue().size(),"completed",completed,"topologyBuilds",topologyBuilds,"closed",closed.get(),"terminated",lane.isTerminated(),"expressions",conditions.size());}
    public Identity identity(){return identity;}public long ticket(){return ticket.get();}public Evaluation latest(){return latest;}
    public CompletableFuture<Checkpoint> checkpoint() {
        return control(()->{var images=new TreeMap<String,EngineCheckpoint>();var results=new TreeMap<String,EngineStepResult>();sessions.forEach((id,s)->{images.put(id,s.engine().checkpoint());if(s.last()!=null)results.put(id,s.last());});return new Checkpoint(owner,ticket.get(),identity,images,results,gaps,discontinuous);});
    }
    public CompletableFuture<Void> restore(Checkpoint image) {
        return control(()->{if(!owner.equals(image.owner())||image.ticket()!=ticket.get()||!image.identity().equals(identity)||!image.histories().keySet().equals(sessions.keySet()))throw new IllegalArgumentException("ENGINE_CHECKPOINT_OWNER_OR_TOPOLOGY_MISMATCH");
            if(!image.histories().keySet().containsAll(image.results().keySet())||image.results().entrySet().stream().anyMatch(e->!e.getValue().history().equals(image.histories().get(e.getKey()))))throw new IllegalArgumentException("ENGINE_CHECKPOINT_RESULT_MISMATCH");
            var before=new TreeMap<String,EngineCheckpoint>();sessions.forEach((id,s)->before.put(id,s.engine().checkpoint()));
            try{image.histories().forEach((id,h)->sessions.get(id).engine().restore(h));}
            catch(RuntimeException failure){before.forEach((id,h)->{try{sessions.get(id).engine().restore(h);}catch(RuntimeException rollback){failure.addSuppressed(rollback);}});throw failure;}
            image.histories().forEach((id,h)->{var session=sessions.get(id);sessions.put(id,new ContextSession(session.revision(),session.engine(),image.results().get(id)));});gaps.clear();gaps.addAll(image.gaps());discontinuous=image.discontinuous();latest=null;
            return null;});
    }
    public CompletableFuture<Void> drained(){if(closed.get())return CompletableFuture.completedFuture(null);return control(()->null);}
    private <T> CompletableFuture<T> control(Callable<T> action) {
        requireOpen();long expected=ticket.get();var future=new CompletableFuture<T>();controls.add(future);
        try{lane.execute(()->{try{if(closed.get()||expected!=ticket.get())throw new CancellationException("ENGINE_OWNER_REVOKED");future.complete(action.call());}catch(Throwable error){future.completeExceptionally(error);}finally{controls.remove(future);}});}
        catch(RejectedExecutionException rejected){controls.remove(future);future.completeExceptionally(rejected);}return future;
    }
    private void requireOpen(){if(closed.get())throw new IllegalStateException("ENGINE_OWNER_CLOSED");}
    @Override public void close(){if(!closed.compareAndSet(false,true))return;ticket.incrementAndGet();lane.shutdownNow();synchronized(pending){pending.forEach(f->f.completeExceptionally(new CancellationException("ENGINE_OWNER_CLOSED")));pending.clear();pins.set(0);retainedBytes.set(0);}controls.forEach(f->f.completeExceptionally(new CancellationException("ENGINE_OWNER_CLOSED")));controls.clear();}
}
