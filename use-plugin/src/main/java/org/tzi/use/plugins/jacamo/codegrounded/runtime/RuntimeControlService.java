package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jacamo.bridge.contract.RuntimeControlContract;
import org.jacamo.bridge.contract.RuntimeControlContract.*;
import org.tzi.use.plugins.jacamo.JaCaMoFacade.TraceRow;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.*;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import static org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationViolation.Confirmation.*;

/** Serialized execution commands and post-ACK resync. Producer control survives observer disconnect. */
public final class RuntimeControlService implements AutoCloseable {
    public enum DistinctViolationPolicy { COALESCE_CONFIRM, REPORT_DISTINCT }
    public interface Port {
        Status control(Request request);
        VerificationSnapshot authoritativeResync();
        boolean ownsRuntime(String session,long generation,String revision);
        boolean liveAvailable();
    }
    public record View(State state,boolean capable,boolean connected,boolean liveReady,Set<String> required,Set<String> acknowledged,
                       String diagnostic,String limitation) {
        public View {required=Set.copyOf(required);acknowledged=Set.copyOf(acknowledged);}
    }
    private final RuntimeVerificationCoordinator coordinator;
    private final Port port;
    private final Supplier<List<TraceRow>> traces;
    private final String session,revision;
    private final long generation,timeoutMillis;
    private final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),
            r->{var t=new Thread(r,"use-jason-runtime-control");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final AtomicBoolean closed=new AtomicBoolean();
    private final Map<String,VerificationViolation> violations=new LinkedHashMap<>();
    private final Map<String,String> active=new LinkedHashMap<>();
    private final int historyLimit=64;
    private volatile View view;
    private CompletableFuture<Status> command;
    private String activePauseViolation="";
    private final Set<String> pauseCohort=new LinkedHashSet<>();
    private final DistinctViolationPolicy distinctPolicy;
    private final RuntimePerformanceStats performance=new RuntimePerformanceStats();
    public Map<String,Object> performanceMetrics(){return performance.snapshot();}
    public RuntimeControlService(RuntimeVerificationCoordinator coordinator,Port port,Supplier<List<TraceRow>> traces,
            String session,long generation,String revision,boolean capable,long timeoutMillis) {
        this(coordinator,port,traces,session,generation,revision,capable,timeoutMillis,
                DistinctViolationPolicy.valueOf(System.getProperty("use.jacamo.control.distinctViolations","COALESCE_CONFIRM")));
    }
    public RuntimeControlService(RuntimeVerificationCoordinator coordinator,Port port,Supplier<List<TraceRow>> traces,
            String session,long generation,String revision,boolean capable,long timeoutMillis,DistinctViolationPolicy distinctPolicy) {
        this.coordinator=coordinator;this.port=port;this.traces=traces;this.session=session;this.generation=generation;this.revision=revision;
        this.distinctPolicy=Objects.requireNonNull(distinctPolicy);
        if(timeoutMillis<100 || timeoutMillis>120000)throw new IllegalArgumentException("CONTROL_ACK_DEADLINE_INVALID");this.timeoutMillis=timeoutMillis;
        view=new View(State.RUNNING,capable,true,true,Set.of(),Set.of(),"",RuntimeControlContract.LIMITATION);
        coordinator.snapshotObserver(this::onSnapshot);
    }
    public View state(){return view;}
    public synchronized List<VerificationViolation> violations(){return List.copyOf(violations.values());}
    public List<ExternalOclConstraintService.RuntimeConstraint> constraints(){return coordinator.read(()->coordinator.constraints().runtimeRegistry());}
    public void approve(String id,RuntimeConstraintPolicy policy) {coordinator.configurePolicy(id,policy);}
    private void onSnapshot(VerificationSnapshot cut) {
        try {observeSnapshot(cut);} catch(RuntimeException diagnosticFailure) {
            view=new View(view.state(),view.capable(),view.connected(),false,view.required(),view.acknowledged(),
                    "VIOLATION_CAPTURE_ERROR:"+diagnosticFailure.getMessage(),view.limitation());
        }
    }
    private void observeSnapshot(VerificationSnapshot cut) {
        if(closed.get() || cut.metadata()==null || cut.image()==null || cut.result()==null)return;
        var registry=coordinator.constraints().runtimeRegistry();
        var definitions=new HashMap<String,ExternalOclConstraintService.RuntimeConstraint>();registry.forEach(c->definitions.put(c.id(),c));
        String pause="";var created=new ArrayList<String>();
        synchronized(this) {
            for(var outcome:cut.result().outcomes()) {
                if(outcome.outcome()==VerificationOutcome.PASS) {active.remove(outcome.constraintId());continue;}
                if(outcome.outcome()!=VerificationOutcome.FAIL)continue;
                var definition=definitions.get(outcome.constraintId());
                if(active.containsKey(outcome.constraintId())) {
                    var old=violations.get(active.get(outcome.constraintId()));
                    if(old==null || old.enforcement()!=RuntimeConstraintPolicy.Enforcement.REPORT_ONLY
                            || definition==null || !coordinator.constraints().mayPause(outcome.constraintId()))continue;
                    active.remove(outcome.constraintId());
                }
                var policy=definition==null?RuntimeConstraintPolicy.reportOnly(org.tzi.use.plugins.jacamo.verification.ConstraintOrigin.CORE,"USE structure check"):definition.policy();
                String id="violation:"+UUID.randomUUID();
                var failingContexts=coordinator.constraints().failingContexts(outcome);
                var involved=involved(cut,outcome,definition,failingContexts);
                String context=!outcome.contextObject().isBlank()?outcome.contextObject():failingContexts.stream().sorted().findFirst().orElse("");
                var sourceTrace=new ArrayList<VerificationViolation.SourceTrace>();
                for(var object:involved.values()) {
                    var nativeObject=coordinator.system().state().objectByName(object.name());
                    String specification="";
                    if(nativeObject!=null && nativeObject.cls().attribute("specSemanticId",true)!=null
                            && nativeObject.state(coordinator.system().state()).attributeValue("specSemanticId") instanceof org.tzi.use.uml.ocl.value.StringValue spec)
                        specification=spec.value();
                    String exactSpec=specification;
                    var matches=traces.get().stream().filter(t->t.targetUseId().equals(object.name()) || t.targetUseId().equals(object.className())
                            || exactSpec.isBlank() && t.targetUseId().equals("class:"+object.className()) || t.targetUseId().startsWith("value:"+object.name()+".")
                            || t.semanticId().equals(object.semanticId()) || !exactSpec.isBlank() && t.semanticId().equals(exactSpec)).toList();
                    if(matches.isEmpty())sourceTrace.add(new VerificationViolation.SourceTrace("source unavailable",0,object.semanticId(),object.exactIdentities(),"",object.name()));
                    else matches.forEach(t->sourceTrace.add(new VerificationViolation.SourceTrace(t.sourcePath()==null?"source unavailable":t.sourcePath().toString(),
                            t.sourceLine(),t.semanticId(),object.exactIdentities(),t.mappingRule(),t.targetUseId())));
                }
                if(definition!=null && !definition.sourceFile().isBlank())sourceTrace.add(new VerificationViolation.SourceTrace(definition.sourceFile(),0,
                        outcome.constraintId(),List.of(),"OCL compiler",outcome.constraintId()));
                var values=new TreeMap<String,String>();values.put("conditionTruth","false");
                involved.values().forEach(o->o.attributes().forEach((a,v)->values.put(o.name()+"."+a,v)));
                boolean enforcing=definition!=null && coordinator.constraints().mayPause(outcome.constraintId());
                boolean distinctReport=enforcing && distinctPolicy==DistinctViolationPolicy.REPORT_DISTINCT
                        && (!pause.isBlank() || view.state()!=State.RUNNING);
                var violation=new VerificationViolation(id,outcome.constraintId(),outcome.constraintId(),VerificationOutcome.FAIL,policy.severity(),policy.enforcement(),
                        cut.metadata().checkpoint(),cut.snapshotId(),cut.metadata().boundarySequence(),cut.metadata().generation(),cut.metadata().capturedAt(),context,
                        involved,Map.of("conditionTruth","true"),values,sourceTrace,view.state(),distinctReport?REPORT_ONLY:enforcing?
                                view.capable() && port.liveAvailable()?PENDING:CONFIRMATION_ERROR:REPORT_ONLY,"",
                        distinctReport?"DISTINCT_HARD_REPORT_ONLY_DURING_ACTIVE_PAUSE":enforcing && !view.capable()?"CONTROL_CAPABILITY_UNAVAILABLE":outcome.diagnostic());
                violations.put(id,violation);active.put(outcome.constraintId(),id);created.add(id);
                while(violations.size()>historyLimit){String oldest=violations.keySet().stream().filter(key->!key.equals(activePauseViolation)).findFirst().orElseThrow();
                    violations.remove(oldest);active.values().removeIf(oldest::equals);pauseCohort.remove(oldest);}
                if(enforcing && pause.isBlank() && view.state()==State.RUNNING && view.capable() && port.liveAvailable())pause=id;
            }
            if(!pause.isBlank()) {
                // The committed immutable failing cut and diagnostics exist BEFORE the command is queued.
                coordinator.snapshots().releasePins();coordinator.snapshots().pinFailure(cut.snapshotId());activePauseViolation=pause;pauseCohort.clear();
            } else if(!created.isEmpty() && view.state()==State.RUNNING && activePauseViolation.isBlank()) {
                coordinator.snapshots().releasePins();coordinator.snapshots().pinFailure(cut.snapshotId());
            }
            if(!pause.isBlank() || view.state()==State.PAUSE_REQUESTED)
                created.stream().filter(id->violations.get(id).confirmation()==PENDING).forEach(pauseCohort::add);
        }
        if(!pause.isBlank())try {requestPause("HARD_FAIL:"+pause);}catch(RuntimeException unavailable){failed(unavailable.getMessage());}
    }
    private Map<String,VerificationSnapshot.ObjectState> involved(VerificationSnapshot cut,ExternalOclConstraintService.Outcome outcome,
            ExternalOclConstraintService.RuntimeConstraint definition,Set<String> failingContexts) {
        var result=new LinkedHashMap<String,VerificationSnapshot.ObjectState>();
        if(!outcome.contextObject().isBlank()) {
            var object=cut.image().objects().get(outcome.contextObject());
            if(object!=null)result.put(object.name(),object);
        }
        failingContexts.forEach(name->{var object=cut.image().objects().get(name);if(object!=null)result.put(name,object);});
        if(definition!=null)for(String identity:definition.policy().exactEvidenceTargets().keySet()) {
            var matches=cut.image().objects().values().stream().filter(o->o.exactIdentities().contains(identity)).toList();
            if(matches.size()>1)throw new IllegalArgumentException("VIOLATION_EVIDENCE_TARGET_AMBIGUOUS:"+identity);
            if(matches.size()==1)result.put(matches.getFirst().name(),matches.getFirst());
        }
        // Involved relation endpoints are evidence, not claimed additional failing contexts.
        if(definition!=null)for(var link:cut.image().links())if(link.participants().stream().anyMatch(result::containsKey))
            for(String name:link.participants()) {
                var object=cut.image().objects().get(name);var nativeObject=coordinator.system().state().objectByName(name);
                if(object!=null && nativeObject!=null && (definition.requiredClasses().contains(object.className())
                        || nativeObject.cls().allParents().stream().anyMatch(p->definition.requiredClasses().contains(p.name()))))result.put(name,object);
            }
        return Map.copyOf(result);
    }
    public CompletableFuture<Status> requestPause(String reason) {
        ensureActive();
        synchronized(this) {if(command!=null && !command.isDone())return command;
            if(view.state()==State.PAUSED)return CompletableFuture.completedFuture(null);}
        // Never wait for the EDT while holding the diagnostic/control monitor.
        requireSynchronizedCut();
        synchronized(this) {
            ensureActive();if(command!=null && !command.isDone())return command;
            if(view.state()==State.PAUSED)return CompletableFuture.completedFuture(null);
            view=new View(State.PAUSE_REQUESTED,view.capable(),view.connected(),false,view.required(),view.acknowledged(),"",view.limitation());
            command=CompletableFuture.supplyAsync(()->execute(Action.PAUSE,reason),worker);return command;
        }
    }
    public CompletableFuture<Status> requestResume() {
        ensureActive();synchronized(this) {if(command!=null && !command.isDone())return command;
            if(view.state()==State.RUNNING)return CompletableFuture.completedFuture(null);
            if(view.state()!=State.PAUSED)throw new IllegalStateException("CONTROL_PAUSE_NOT_CONFIRMED");}
        requireSynchronizedCut();
        synchronized(this) {
            ensureActive();if(command!=null && !command.isDone())return command;
            if(view.state()==State.RUNNING)return CompletableFuture.completedFuture(null);
            if(view.state()!=State.PAUSED)throw new IllegalStateException("CONTROL_PAUSE_NOT_CONFIRMED");
            if(!view.diagnostic().isBlank())throw new IllegalStateException("CONTROL_RESYNC_REQUIRED:"+view.diagnostic());
            view=new View(State.RESUME_REQUESTED,view.capable(),view.connected(),false,view.required(),view.acknowledged(),"",view.limitation());
            command=CompletableFuture.supplyAsync(()->execute(Action.RESUME,"USER_RESUME"),worker);return command;
        }
    }
    public CompletableFuture<Status> refresh() {return CompletableFuture.supplyAsync(()->{
        ensureOwnership();var status=port.control(request(Action.STATUS,UUID.randomUUID().toString(),"CONTROL_REFRESH"));
        accept(status,status.state()==State.RUNNING && port.liveAvailable());
        coordinator.controlState(status.state());
        return status;
    },worker);}
    private Status execute(Action action,String reason) {
        String requestId=UUID.randomUUID().toString();
        try {
            ensureOwnership();coordinator.controlState(action==Action.PAUSE?State.PAUSE_REQUESTED:State.RESUME_REQUESTED);
            if(action==Action.RESUME)coordinator.controlledLifecycle(VerificationSnapshot.SynchronizationState.SYNCING);
            long requestStarted=System.nanoTime();var status=port.control(request(action,requestId,reason));
            performance.record(action==Action.PAUSE?RuntimePerformanceStats.Metric.PAUSE_REQUEST:RuntimePerformanceStats.Metric.RESUME_REQUEST,System.nanoTime()-requestStarted);
            accept(status,false);
            long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            State wanted=action==Action.PAUSE?State.PAUSED:State.RUNNING;
            while(status.state()!=wanted) {
                if(System.nanoTime()>=deadline)throw new IllegalStateException("CONTROL_ALL_AGENT_ACK_TIMEOUT");
                Thread.sleep(25);ensureOwnership();status=port.control(request(Action.STATUS,requestId,"ACK_WAIT"));accept(status,false);
                if(!status.capable())throw new IllegalStateException("CONTROL_CAPABILITY_LOST:"+status.diagnostic());
            }
            if(!status.requestId().equals(requestId))throw new IllegalStateException("CONTROL_REQUEST_ACK_IDENTITY_MISMATCH");
            performance.record(action==Action.PAUSE?RuntimePerformanceStats.Metric.PAUSE_ACK:RuntimePerformanceStats.Metric.RESUME_ACK,System.nanoTime()-requestStarted);
            coordinator.controlState(status.state());
            coordinator.controlledLifecycle(VerificationSnapshot.SynchronizationState.SYNCING);
            VerificationSnapshot confirmation;
            long resyncStarted=System.nanoTime();
            try {ensureOwnership();confirmation=port.authoritativeResync();ensureOwnership();
                if(confirmation==null || confirmation.result()==null || confirmation.metadata()==null || confirmation.result().freshness().equals("STALE"))
                    throw new IllegalStateException("CONTROL_RESYNC_UNAVAILABLE");
            } catch(RuntimeException failed) {
                if(action==Action.PAUSE)updateActive(CONFIRMATION_ERROR,"","AUTHORITATIVE_RESYNC_FAILED:"+failed.getMessage());throw failed;
            } finally {performance.record(RuntimePerformanceStats.Metric.AUTHORITATIVE_RESYNC,System.nanoTime()-resyncStarted);}
            if(action==Action.PAUSE) {
                status=port.control(request(Action.STATUS,requestId,"POST_RESYNC_ACK_REVALIDATION"));
                if(status.state()!=State.PAUSED || !status.capable())throw new IllegalStateException("CONTROL_PAUSE_COHORT_CHANGED_AFTER_RESYNC");
                coordinator.controlledLifecycle(VerificationSnapshot.SynchronizationState.LIVE);
                coordinator.manualVerify();confirmation=coordinator.verificationSnapshot();
                if(!activePauseViolation.isBlank()) {
                    coordinator.snapshots().pinConfirmation(confirmation);
                    synchronized(this) {for(String id:pauseCohort) {
                        var violation=violations.get(id);if(violation==null)continue;
                        var outcomes=confirmation.result().outcomes().stream().filter(o->o.constraintId().equals(violation.constraintId())).toList();
                        var confirmed=outcomes.size()!=1?CONFIRMATION_ERROR:outcomes.getFirst().outcome()==VerificationOutcome.FAIL?CONFIRMED:
                                outcomes.getFirst().outcome()==VerificationOutcome.PASS?TRANSIENT_NOT_REPRODUCED:CONFIRMATION_ERROR;
                        violations.put(id,violation.update(State.PAUSED,confirmed,confirmation.snapshotId(),outcomes.isEmpty()?"CONFIRMATION_CONSTRAINT_UNAVAILABLE":outcomes.getFirst().diagnostic()));
                    }}
                }
                accept(status,false);
            } else {
                status=port.control(request(Action.STATUS,requestId,"POST_RESYNC_ACK_REVALIDATION"));
                if(status.state()!=State.RUNNING || !status.capable())throw new IllegalStateException("CONTROL_RESUME_COHORT_CHANGED_AFTER_RESYNC");
                coordinator.controlledLifecycle(VerificationSnapshot.SynchronizationState.LIVE);coordinator.manualVerify();accept(status,true);
            }
            return status;
        } catch(InterruptedException interrupted) {Thread.currentThread().interrupt();failed("CONTROL_INTERRUPTED",action==Action.PAUSE);throw new CompletionException(interrupted);}
        catch(RuntimeException error) {failed(error.getMessage(),action==Action.PAUSE);throw error;}
    }
    private void ensureActive(){ensureOwnership();if(!view.capable() || !port.liveAvailable())throw new IllegalStateException("CONTROL_ACTIVE_RUNTIME_UNAVAILABLE");}
    private void requireSynchronizedCut() {
        var cut=coordinator.verificationSnapshot();
        if(cut.result()==null || cut.metadata()==null || !cut.result().freshness().equals("CURRENT_OBSERVED")
                || cut.metadata().lifecycle()!=VerificationSnapshot.SynchronizationState.LIVE)
            throw new IllegalStateException("CONTROL_AUTHORITATIVE_RESYNC_REQUIRED");
    }
    private void ensureOwnership(){if(closed.get() || !port.ownsRuntime(session,generation,revision))throw new IllegalStateException("CONTROL_WORKSPACE_OR_GENERATION_REPLACED");}
    private Request request(Action action,String id,String reason){return new Request(RuntimeControlContract.VERSION,session,generation,revision,id,action,reason);}
    private void accept(Status status,boolean liveReady){view=new View(status.state(),status.capable(),true,liveReady,status.requiredAgents(),status.acknowledgedAgents(),status.diagnostic(),RuntimeControlContract.LIMITATION);
        synchronized(this){for(String id:pauseCohort){var v=violations.get(id);if(v!=null)violations.put(id,v.update(status.state(),v.confirmation(),v.confirmationSnapshotId(),v.diagnostic()));}}}
    private void updateActive(VerificationViolation.Confirmation result,String snapshot,String diagnostic){synchronized(this){for(String id:pauseCohort){var v=violations.get(id);if(v!=null)violations.put(id,v.update(view.state(),result,snapshot,diagnostic));}}}
    private void failed(String diagnostic){failed(diagnostic,true);}
    private void failed(String diagnostic,boolean confirmationError){view=new View(view.state(),view.capable(),port.liveAvailable(),false,view.required(),view.acknowledged(),diagnostic,view.limitation());
        if(confirmationError)updateActive(CONFIRMATION_ERROR,"",diagnostic);}
    public void disconnected(){view=new View(view.state(),view.capable(),false,false,view.required(),view.acknowledged(),"BRIDGE_DISCONNECTED; producer control state retained",view.limitation());}
    /** A validated authoritative observation reconnect does not imply resume. */
    public void observationConnected() {
        var before=view;
        view=new View(before.state(),before.capable(),true,!before.capable() && before.state()==State.RUNNING,
                before.required(),before.acknowledged(),before.capable()?before.diagnostic():"CONTROL_CAPABILITY_UNAVAILABLE",before.limitation());
    }
    @Override public void close(){if(!closed.compareAndSet(false,true))return;worker.shutdownNow();}
}
