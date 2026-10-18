package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.events.AtomicStateChangedEvent;
import org.tzi.use.util.soil.StateDifference;

/** Single writer and reader barrier: commit, full verification, journaling and notification run on USE's EDT. */
public final class RuntimeVerificationCoordinator implements AutoCloseable {
    public record Mutation<T>(T value, boolean changed, boolean preserveObservedViolations, boolean repeatedSourceCut) {
        public Mutation(T value,boolean changed) { this(value,changed,false,false); }
        public Mutation(T value,boolean changed,boolean preserveObservedViolations) { this(value,changed,preserveObservedViolations,false); }
    }
    private final NativeRuntimeMutationEngine engine;
    private final ExternalOclConstraintService constraints;
    private final RuntimeEventJournal journal;
    private final RuntimeCheckpointStore checkpoints;
    private final Set<String> baselineOperations;
    private final Path directory;
    private final boolean cleanupOnClose;
    private final Thread shutdownHook;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final java.util.concurrent.CompletableFuture<Void> fileCleanup = new java.util.concurrent.CompletableFuture<>();
    private final AtomicInteger backlog = new AtomicInteger();
    private volatile int highWatermark;
    private volatile RuntimeVerificationResult latest;
    private volatile String observerDiagnostic="";
    public String observerDiagnostic(){return observerDiagnostic;}
    private volatile RuntimeVerificationResult lastObservation;
    private String sessionId, modelRevision;
    private long generation, stateVersion, verificationCount;
    private long acceptedBoundaries;
    private long originalBoundarySequence;
    private java.util.function.Supplier<Instant> appliedClock=Instant::now;
    private boolean quarantined;
    private volatile java.util.function.Consumer<String> faults=point->{};
    void faultProbe(java.util.function.Consumer<String> probe){faults=java.util.Objects.requireNonNull(probe);}
    long acceptedBoundaries(){return acceptedBoundaries;}
    private Map<String, Completeness> sources = Map.of();
    private boolean coverageLost;
    private VerificationSnapshot.ProfileInterval profileInterval;
    private final SnapshotRetention snapshots=new SnapshotRetention(Integer.getInteger("use.jacamo.snapshot.history",8),
            Long.getLong("use.jacamo.snapshot.history.bytes",16L*1024*1024));
    private VerificationSnapshot latestSnapshot;
    private final RuntimePerformanceStats performance=new RuntimePerformanceStats();
    private final java.util.EnumMap<CheckpointType,Long> snapshotCounts=new java.util.EnumMap<>(CheckpointType.class);
    private long boundarySequence;
    private Map<String,String> runtimeCapabilities=Map.of(),sourceVersions=Map.of();
    private record MoiseObservation(boolean complete,boolean scheme,boolean goalState) { }
    private final Map<String,MoiseObservation> moiseObservations=new java.util.LinkedHashMap<>();
    void observedSemanticSnapshot(org.jacamo.bridge.contract.RuntimeSnapshot snapshot,String run,long generation) {if(semanticVerifier!=null)semanticVerifier.observedSnapshot(snapshot,run,generation);}
    void observedSources(List<org.jacamo.bridge.contract.RuntimeFact> facts,boolean replacement) {
        if(semanticVerifier!=null)semanticVerifier.observed(facts,replacement);
        if(originalEngine!=null)originalEngine.observed(facts,replacement);
        if(replacement)moiseObservations.clear();
        for(var fact:facts)if(fact.kind()==org.jacamo.bridge.contract.RuntimeFactKind.GROUP_BOARD || fact.kind()==org.jacamo.bridge.contract.RuntimeFactKind.SCHEME_BOARD)
            moiseObservations.put(fact.id().canonical(),new MoiseObservation(fact.projectionStatus()==org.jacamo.bridge.contract.ProjectionStatus.MATERIALIZED_FAITHFULLY
                    && fact.completeness()==Completeness.COMPLETE,fact.kind()==org.jacamo.bridge.contract.RuntimeFactKind.SCHEME_BOARD,
                    "1.0.0".equals(fact.values().get("goalObservationVersion"))));
        var next=new java.util.TreeMap<>(sources);
        boolean complete=!moiseObservations.isEmpty() && moiseObservations.values().stream().allMatch(MoiseObservation::complete);
        if(!moiseObservations.isEmpty() || sources.keySet().stream().anyMatch(s->s.startsWith("moise")))next.put("moise.domain",complete?Completeness.COMPLETE:Completeness.UNAVAILABLE);
        else next.remove("moise.domain");sources=Map.copyOf(next);
        var capabilities=new java.util.TreeMap<>(runtimeCapabilities);
        var schemes=moiseObservations.values().stream().filter(MoiseObservation::scheme).toList();
        capabilities.put("runtime.moise.goalState.v1",complete && !schemes.isEmpty() && schemes.stream().allMatch(MoiseObservation::goalState)?"COMPLETE":"UNAVAILABLE");
        runtimeCapabilities=Map.copyOf(capabilities);
    }
    void observedSource(org.jacamo.bridge.contract.RuntimeEvent event) {
        // A source-local lifecycle notice can change the population before its first full data cut.
        // A later complete object update does not prove that the whole source population recovered.
        if (semanticVerifier!=null && event.factKind()==org.jacamo.bridge.contract.RuntimeFactKind.AGENT
                && (event.subsystem().equals("jason") || event.subsystem().equals("cartago"))
                && (event.projectionStatus()!=org.jacamo.bridge.contract.ProjectionStatus.MATERIALIZED_FAITHFULLY
                    || event.completeness()!=Completeness.COMPLETE || event.entityId()==null
                    || engine.objectForSemanticId(event.entityId().canonical())==null)) {
            var downgraded=new java.util.TreeMap<>(sources);downgraded.put(event.subsystem(),Completeness.PARTIAL);sources=Map.copyOf(downgraded);
        }
        if(semanticVerifier!=null)semanticVerifier.observedEvent(event);
        if(originalEngine!=null)originalEngine.observed(event);
        if(event.entityId()!=null && (event.factKind()==org.jacamo.bridge.contract.RuntimeFactKind.GROUP_BOARD || event.factKind()==org.jacamo.bridge.contract.RuntimeFactKind.SCHEME_BOARD || event.factKind()==org.jacamo.bridge.contract.RuntimeFactKind.AGENT || event.factKind()==org.jacamo.bridge.contract.RuntimeFactKind.NORM_INSTANCE))
            observedSources(List.of(new org.jacamo.bridge.contract.RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),event.evidence())),false);
    }
    private Set<String> changedFeatures=Set.of();
    private final NativeOperationCheckpoints operations;
    private final org.tzi.use.plugins.jacamo.semantics.runtime.SemanticRuntimeVerifier semanticVerifier;
    private final org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner originalEngine;
    public org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner originalEngine(){return originalEngine;}
    private List<ExternalOclConstraintService.Outcome> operationOutcomes=List.of();
    private java.util.function.Consumer<VerificationSnapshot> snapshotObserver=s->{};
    private VerificationSnapshot.SynchronizationState controlledLifecycle;
    private org.jacamo.bridge.contract.RuntimeControlContract.State controlState;
    public void snapshotObserver(java.util.function.Consumer<VerificationSnapshot> observer) {read(()->{snapshotObserver=java.util.Objects.requireNonNull(observer);return null;});}
    public void controlledLifecycle(VerificationSnapshot.SynchronizationState state) {read(()->{controlledLifecycle=state;return null;});}
    public void controlState(org.jacamo.bridge.contract.RuntimeControlContract.State state) {read(()->{controlState=state;return null;});}
    void operationCheckpoint(org.jacamo.bridge.contract.RuntimeEvent event) {operationOutcomes=operations.observe(event,sources,coverageLost,semanticVerifier!=null?semanticVerifier.freshContexts():originalEngine!=null?originalEngine.freshContexts():Set.of());}
    String operationKind(org.jacamo.bridge.contract.RuntimeEvent event){return operations.eligibleKind(event);}
    private java.util.ArrayList<Object> deferredNotifications;
    public VerificationSnapshot verificationSnapshot() {
        return read(() -> {
            var profile = constraints.profile();
            var interval = profileInterval == null ? null : new VerificationSnapshot.ProfileInterval(
                    profileInterval.loadedVersion(), profileInterval.intervalId(), profileInterval.installedAt(),
                    profileInterval.sessionId(), profileInterval.generation(), profileInterval.installedModelRevision(), profile);
            var current = latest != null && latest.constraintSetHash().equals(constraints.constraintSetHash()) ? latest : null;
            return new VerificationSnapshot(stateVersion, current, interval,
                    latestSnapshot==null?null:latestSnapshot.metadata(),latestSnapshot==null?null:latestSnapshot.image());
        });
    }
    public SnapshotRetention snapshots() {return snapshots;}
    public Map<String,Object> performanceMetrics() {
        return read(()->Map.of("statistics",performance.snapshot(),"snapshotsByCheckpoint",snapshotCounts.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(e->e.getKey().name(),Map.Entry::getValue)),"retainedSerializedBytes",snapshots.retainedBytes(),
                "tailSnapshots",snapshots.snapshots().size(),"lastSnapshotSerializedBytes",latestSnapshot==null?0:latestSnapshot.estimatedBytes(),
                "verificationCount",verificationCount,"writerHighWatermark",highWatermark));
    }
    /** Availability is scoped to the exact SchemeBoard incarnation, never its bare name. */
    public boolean goalStateAvailable(String runtimeIdentity) {
        return read(()->{var observed=moiseObservations.get(runtimeIdentity);
            return !coverageLost && observed!=null && observed.scheme() && observed.complete() && observed.goalState();});
    }
    public void runtimeCapabilities(Map<String,String> capabilities,Map<String,String> versions) {
        read(()->{
            if(runtimeCapabilities.equals(capabilities) && sourceVersions.equals(versions))return null;
            runtimeCapabilities=Map.copyOf(capabilities);sourceVersions=Map.copyOf(versions);stateVersion++;
            finish("CAPABILITIES","capabilities:"+stateVersion,"bridge",0,Instant.now(),
                    Map.of("capabilities",runtimeCapabilities,"sourceVersions",sourceVersions),true,false,"");
            publishCurrent();return null;
        });
    }
    /** Policy changes are recorded against the exact compiled condition, for faithful replay. */
    public RuntimeVerificationResult configurePolicy(String id,
            org.tzi.use.plugins.jacamo.codegrounded.constraint.RuntimeConstraintPolicy policy) {
        return configurePolicy(id,policy,null);
    }
    public RuntimeVerificationResult configurePolicy(String id,
            org.tzi.use.plugins.jacamo.codegrounded.constraint.RuntimeConstraintPolicy policy,String recordedFingerprint) {
        return read(()->{
            String fingerprint=constraints.conditionFingerprint(id);
            if(recordedFingerprint!=null && !fingerprint.equals(recordedFingerprint))throw new IllegalArgumentException("RUNTIME_POLICY_CONDITION_CHANGED");
            var aliases=engine.objectIdentityAliases();
            for(String identity:policy.exactEvidenceTargets().keySet()) {
                long matches=aliases.values().stream().filter(ids->ids.contains(identity)).count();
                if(matches!=1)throw new IllegalArgumentException(matches==0?"RUNTIME_POLICY_EVIDENCE_TARGET_UNAVAILABLE:"+identity:"RUNTIME_POLICY_EVIDENCE_TARGET_AMBIGUOUS:"+identity);
            }
            var before=constraints.savepoint();
            try {constraints.configurePolicy(id,policy);}catch(RuntimeException invalid){constraints.restore(before);throw invalid;}
            stateVersion++;
            var result=finish("POLICY","policy:"+stateVersion,"ocl",0,Instant.now(),
                    Map.of("constraintId",id,"conditionFingerprint",fingerprint,"policy",policy.toMap()),true,false,"");
            publishCurrent();return result;
        });
    }
    private void recordProfileInterval(ExternalOclConstraintService.Profile profile) {
        profileInterval = new VerificationSnapshot.ProfileInterval(stateVersion,
                sessionId + ":" + generation + ":" + modelRevision + ":profile:" + stateVersion + ":" + profile.sourceHash(),
                Instant.now(), sessionId, generation, modelRevision, profile);
    }
    public RuntimeVerificationCoordinator(NativeRuntimeMutationEngine engine, String sessionId, long generation,
            String revision) { this(engine, sessionId, generation, revision,
                    createEphemeralDirectory(defaultRuntimeRoot()), 512, defaultJournalMaxBytes(), true); }
    public RuntimeVerificationCoordinator(NativeRuntimeMutationEngine engine, String sessionId, long generation,
            String revision, Path directory, int tail, long bytes) {
        this(engine, sessionId, generation, revision, directory, tail, bytes, false);
    }
    public RuntimeVerificationCoordinator(NativeRuntimeMutationEngine engine, String sessionId, long generation,
            String revision, Path directory, int tail, long bytes, boolean cleanupOnClose) {
        this.engine = engine; this.sessionId = sessionId; this.generation = generation; this.modelRevision = revision;
        operations=new NativeOperationCheckpoints(engine);
        semanticVerifier=engine.cslProfile()?new org.tzi.use.plugins.jacamo.semantics.runtime.SemanticRuntimeVerifier(engine):null;
        originalEngine=engine.canonicalMappingConfigured()?new org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner(engine,sessionId,generation,revision):null;
        if(originalEngine!=null){operations.canonicalBindings(originalEngine::taskBindings);operations.deferCanonical();}
        this.directory = directory.toAbsolutePath().normalize();
        this.cleanupOnClose = cleanupOnClose;
        constraints = new ExternalOclConstraintService(engine.system());
        constraints.notifications(event -> {
            if (deferredNotifications != null) deferredNotifications.add(event);
            else system().getEventBus().post(event);
        });
        journal = new RuntimeEventJournal(this.directory, tail, bytes);
        checkpoints = new RuntimeCheckpointStore(this.directory.resolve("checkpoints"));
        Thread hook = null;
        if (cleanupOnClose) {
            hook = new Thread(this::cleanupDirectory, "jacamo-runtime-cleanup");
            try { Runtime.getRuntime().addShutdownHook(hook); }
            catch (IllegalStateException ignored) { hook = null; }
        }
        shutdownHook = hook;
        baselineOperations=read(()->system().model().classes().stream().flatMap(cls->cls.operations().stream())
                .map(operation->operation.cls().qualifiedName()+"::"+operation.name())
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        read(() -> { finish("BASELINE", "baseline", "native", 0, Instant.now(), Map.of(), true, false, ""); return null; });
    }
    boolean operationAtBaseline(org.tzi.use.uml.mm.MOperation operation) {
        return baselineOperations.contains(operation.cls().qualifiedName()+"::"+operation.name());
    }
    public Path directory() { return directory; }
    /** Disk budget, captured per run; overflow remains a coverage failure. */
    public static long defaultJournalMaxBytes() {
        String configured = System.getProperty("use.jacamo.runtime.journal.maxBytes", "67108864");
        try {
            long bytes = Long.parseLong(configured);
            if (bytes < 32768 || bytes > 1024L * 1024 * 1024) throw new NumberFormatException();
            return bytes;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("RUNTIME_JOURNAL_BUDGET_INVALID", invalid);
        }
    }
    public static Path defaultRuntimeRoot() {
        String configured = System.getProperty("use.jacamo.runtime.root", "").trim();
        if (!configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        Path workingDirectory = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path checkout = Files.isDirectory(workingDirectory.resolve("use-plugin"))
                ? workingDirectory : workingDirectory.resolve("..").toAbsolutePath().normalize();
        return checkout.resolve("use-plugin").resolve("target").resolve("jacamo-runtime");
    }
    public static Path createEphemeralDirectory(Path root) {
        try {
            Path normalized = root.toAbsolutePath().normalize();
            Files.createDirectories(normalized);
            return Files.createTempDirectory(normalized, "run-");
        } catch (java.io.IOException error) {
            throw new IllegalStateException("RUNTIME_STORE_UNAVAILABLE", error);
        }
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if(originalEngine!=null)originalEngine.close();
        if(SwingUtilities.isEventDispatchThread())operations.clear();else SwingUtilities.invokeLater(operations::clear);
        if (shutdownHook != null) {
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
            catch (IllegalStateException ignored) { }
        }
        Runnable cleanup = () -> {
            try {
                if (cleanupOnClose) cleanupDirectory();
                fileCleanup.complete(null);
            } catch (Throwable failure) { fileCleanup.completeExceptionally(failure); }
        };
        if (cleanupOnClose && SwingUtilities.isEventDispatchThread())
            Thread.ofPlatform().daemon().name("use-monitor-file-cleanup").start(cleanup);
        else cleanup.run();
    }
    /** Settles the owned filesystem cleanup attempt; callers wait outside the EDT. */
    public java.util.concurrent.CompletionStage<Void> fileCleanupCompletion() { return fileCleanup.minimalCompletionStage(); }
    private void cleanupDirectory() {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (java.io.IOException ignored) { }
            });
        } catch (java.io.IOException ignored) { }
    }
    public MSystem system() { return engine.system(); }
    public String objectMetadata(MObject object,String key){return read(()->engine.metadata(object,key));}
    public Map<String,List<String>> objectIdentityAliases(){return read(engine::objectIdentityAliases);}
    public ExternalOclConstraintService constraints() { return constraints; }
    public RuntimeEventJournal journal() { return journal; }
    public RuntimeCheckpointStore checkpoints() { return checkpoints; }
    public RuntimeVerificationResult latest() { return latest; }
    public RuntimeVerificationResult lastObservation() { return lastObservation; }
    public int backlog() { return backlog.get(); }
    public int highWatermark() { return highWatermark; }
    public long verificationCount() { return read(() -> verificationCount); }
    public List<RuntimeVerificationResult> history() { return journal.history(); }

    public <T> T read(Callable<T> action) {
        if (SwingUtilities.isEventDispatchThread()) {
            if(closed.get())throw new IllegalStateException("RUNTIME_COORDINATOR_CLOSED");
            return call(action);
        }
        int depth = backlog.incrementAndGet(); highWatermark = Math.max(highWatermark, depth);
        long queuedAt=System.nanoTime();
        FutureTask<T> future = new FutureTask<>(() -> {
            performance.record(RuntimePerformanceStats.Metric.WRITER_WAIT,System.nanoTime()-queuedAt);
            if(closed.get())throw new IllegalStateException("RUNTIME_COORDINATOR_CLOSED");
            return action.call();
        });
        try {
            SwingUtilities.invokeAndWait(future);
            return future.get();
        } catch (java.util.concurrent.ExecutionException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(error.getCause());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("RUNTIME_WRITER_INTERRUPTED", error);
        } catch (java.lang.reflect.InvocationTargetException error) { throw new IllegalStateException(error.getCause()); }
        finally { backlog.decrementAndGet(); }
    }
    private static <T> T call(Callable<T> action) {
        try { return action.call(); } catch (RuntimeException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    /** A whole cursor range is one visible update. Savepoints are transient failure rollback only. */
    public <T> T replayBatch(Callable<T> action) {
        return read(() -> system().authorizedMutation(() -> {
            if (deferredNotifications != null) throw new IllegalStateException("REPLAY_NAVIGATION_NESTED");
            var image=engine.savepoint(); var profile=constraints.savepoint();
            var semanticImage=semanticVerifier==null?null:semanticVerifier.savepoint();var operationImage=operations.savepoint();
            var beforeObjects=Set.copyOf(system().state().allObjects());
            var beforeLinks=Set.copyOf(system().state().allLinks());
            var oldLatest=latest; var oldObservation=lastObservation; var oldInterval=profileInterval;
            var oldSnapshot=latestSnapshot;var oldRetention=snapshots.savepoint();long oldBoundary=boundarySequence;
            long oldVersion=stateVersion, oldCount=verificationCount, oldGeneration=generation;
            String oldSession=sessionId, oldRevision=modelRevision;
            var oldSources=sources; boolean oldLost=coverageLost;
            var oldMoise=Map.copyOf(moiseObservations);var oldCapabilities=runtimeCapabilities;var oldVersions=sourceVersions;
            deferredNotifications=new java.util.ArrayList<>();
            try {
                T result=action.call();
                var notifications=deferredNotifications; deferredNotifications=null;
                notifications.forEach(system().getEventBus()::post);
                publish(beforeObjects,beforeLinks);
                return result;
            } catch (Exception error) {
                engine.rollback(image); constraints.restore(profile);operations.restore(operationImage);
                if(semanticVerifier!=null)semanticVerifier.restore(semanticImage);
                latest=oldLatest; lastObservation=oldObservation; profileInterval=oldInterval;
                latestSnapshot=oldSnapshot;snapshots.restore(oldRetention);boundarySequence=oldBoundary;
                stateVersion=oldVersion; verificationCount=oldCount; generation=oldGeneration;
                sessionId=oldSession; modelRevision=oldRevision; sources=oldSources; coverageLost=oldLost;
                moiseObservations.clear();moiseObservations.putAll(oldMoise);runtimeCapabilities=oldCapabilities;sourceVersions=oldVersions;
                // The failed cursor must be reconstructed; never reuse its advanced protocol/journal.
                throw error;
            } finally { deferredNotifications=null; }
        }));
    }

    public <T> T transaction(String kind, String eventId, String sourceId, long sequence, Instant observed,
            Map<String, Object> payload, boolean checkpoint, Callable<Mutation<T>> action) {
        return read(() -> {
            if(quarantined)throw new IllegalStateException("NATIVE_RUNTIME_OWNER_QUARANTINED");
            var savepoint = engine.savepoint();
            var semanticImage=semanticVerifier==null?null:semanticVerifier.savepoint();var operationImage=operations.savepoint();
            var originalFresh=originalEngine==null?Set.<String>of():originalEngine.freshContexts();
            var retainedBefore=originalEngine==null?null:snapshots.savepoint();var frozenBefore=latestSnapshot;long boundaryBefore=boundarySequence;
            var profileBefore=originalEngine==null?null:constraints.savepoint();var operationsBefore=operationOutcomes;
            String previousSession = sessionId, previousRevision = modelRevision;
            long previousGeneration = generation;
            Map<String, Completeness> previousSources = sources;
            var previousMoise=Map.copyOf(moiseObservations);var previousCapabilities=runtimeCapabilities;
            Set<MObject> beforeObjects = Set.copyOf(system().state().allObjects());
            Set<MLink> beforeLinks = Set.copyOf(system().state().allLinks());
            Mutation<T> mutation;
            long mutationStarted=System.nanoTime();
            try {
                mutation = action.call();
                faults.accept("AFTER_NATIVE_ACTION");
                if ((mutation.changed() || checkpoint) && !engine.structureTransitionValid(savepoint,mutation.preserveObservedViolations()))
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_STRUCTURE_INVALID");
                faults.accept("AFTER_NATIVE_VALIDATION");
            } catch (Exception error) {
                try { faults.accept("BEFORE_NATIVE_ROLLBACK");engine.rollback(savepoint);operations.restore(operationImage);if(semanticVerifier!=null)semanticVerifier.restore(semanticImage);
                    if(originalEngine!=null){constraints.restore(profileBefore);originalEngine.restoreAdmission(originalFresh);snapshots.restore(retainedBefore);latestSnapshot=frozenBefore;boundarySequence=boundaryBefore;}
                }catch (RuntimeException rollback) {error.addSuppressed(rollback);quarantined=true;if(originalEngine!=null)originalEngine.close();}
                operationOutcomes=operationsBefore;
                sessionId = previousSession; modelRevision = previousRevision;
                generation = previousGeneration; sources = previousSources;
                moiseObservations.clear();moiseObservations.putAll(previousMoise);runtimeCapabilities=previousCapabilities;
                coverageLost = true;
                if(originalEngine!=null)originalEngine.lost();
                if(quarantined&&originalEngine!=null)originalFailure("REJECTED",eventId,sourceId,sequence,observed,payload,"NATIVE_ROLLBACK_FAILED:"+error.getMessage()+":"+java.util.Arrays.stream(error.getSuppressed()).map(Throwable::getMessage).toList());
                else finish("REJECTED", eventId, sourceId, sequence, observed, payload, false, true,error.getClass().getSimpleName() + ":" + error.getMessage());
                publishCurrent();
                if (error instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException(error);
            } finally {performance.record(RuntimePerformanceStats.Metric.USE_MUTATION,System.nanoTime()-mutationStarted);}
            acceptedBoundaries++;
            if(originalEngine!=null)originalEngine.commitIdentity(sessionId,generation,modelRevision);
            if (mutation.changed() || checkpoint) stateVersion++;
            changedFeatures=mutation.changed()?engine.changedFeatures(savepoint):kind.equals("OPERATION_POST")?operations.features():Set.of();
            boolean repeated=mutation.repeatedSourceCut()&&previousSession.equals(sessionId)&&previousGeneration==generation
                    &&previousSources.equals(sources)&&changedFeatures.isEmpty();
            finish(kind, eventId, sourceId, sequence, observed, payload, checkpoint, !mutation.changed() && !checkpoint, "",repeated);
            changedFeatures=Set.of();
            if (mutation.changed() || checkpoint) publish(beforeObjects, beforeLinks);
            return mutation.value();
        });
    }

    public void acceptedSnapshot(String session, long nextGeneration, String revision, Map<String, Completeness> completeness) {
        // Called inside an authoritative snapshot transaction only.
        constraints.rebind(revision);
        if(!sessionId.equals(session) || generation!=nextGeneration || !modelRevision.equals(revision)) {
            snapshots.clear();latestSnapshot=null;boundarySequence=0;
            operations.clear();if(semanticVerifier!=null)semanticVerifier.reset();
        }
        if(coverageLost)operations.clear();
        // Original owner epoch is committed only after the whole native action validates.
        sessionId = session; generation = nextGeneration; modelRevision = revision;
        sources = Map.copyOf(completeness); coverageLost = journal.hasGap();
    }
    public RuntimeVerificationResult loadProfile(Path path) {
        return read(() -> installProfile(() -> constraints.install(path, modelRevision)));
    }
    public RuntimeVerificationResult loadProfileSource(String file, String source) {
        return loadProfileSource(file, source, Map.of());
    }
    public RuntimeVerificationResult loadSavedProfile(ExternalOclConstraintService.Profile profile) {
        return loadProfileSource(profile.sourceFile(), profile.source(), profile.constraints().stream().collect(
                java.util.stream.Collectors.toMap(ExternalOclConstraintService.RegisteredConstraint::constraintId,
                        ExternalOclConstraintService.RegisteredConstraint::enabled)), profile.constraints().stream().collect(
                java.util.stream.Collectors.toMap(ExternalOclConstraintService.RegisteredConstraint::constraintId,
                        ExternalOclConstraintService.RegisteredConstraint::negated)));
    }
    public RuntimeVerificationResult loadProfileSource(String file, String source, Map<String, Boolean> enabled) {
        return loadProfileSource(file, source, enabled, Map.of());
    }
    public RuntimeVerificationResult loadProfileSource(String file, String source, Map<String, Boolean> enabled,
                                                     Map<String, Boolean> negated) {
        return read(() -> installProfile(() -> constraints.installSource(file, source, modelRevision, enabled, negated)));
    }
    private RuntimeVerificationResult installProfile(Callable<ExternalOclConstraintService.Profile> install) throws Exception {
        var state=engine.savepoint(); var oldProfile=constraints.savepoint();
        var beforeObjects=Set.copyOf(system().state().allObjects()); var beforeLinks=Set.copyOf(system().state().allLinks());
        boolean ownsNotifications=deferredNotifications==null;
        if(ownsNotifications) deferredNotifications=new java.util.ArrayList<>();
        int queuedBefore=deferredNotifications.size();
        try {
            var profile=install.call();
            engine.refreshBeliefProjection();
            stateVersion++;
            recordProfileInterval(profile);
            var result = finish("PROFILE", "profile:" + profile.sourceHash(), "ocl", 0, Instant.now(),
                    profilePayload(profile), true, false, "");
            if(ownsNotifications) {
                var notifications=deferredNotifications; deferredNotifications=null;
                notifications.forEach(system().getEventBus()::post);
            }
            publish(beforeObjects,beforeLinks); return result;
        } catch(Exception failure) {
            constraints.restore(oldProfile); engine.rollback(state);
            if(deferredNotifications!=null) deferredNotifications.subList(queuedBefore,deferredNotifications.size()).clear();
            throw failure;
        } finally {
            if(ownsNotifications) deferredNotifications=null;
        }
    }
    private Map<String,Object> profilePayload(ExternalOclConstraintService.Profile profile) {
        return Map.of("sourceFile",profile.sourceFile(),"source",profile.source(),"modelRevision",modelRevision,
                "enabled",profile.constraints().stream().collect(java.util.stream.Collectors.toMap(
                        ExternalOclConstraintService.RegisteredConstraint::constraintId,ExternalOclConstraintService.RegisteredConstraint::enabled)),
                "negated",profile.constraints().stream().collect(java.util.stream.Collectors.toMap(
                        ExternalOclConstraintService.RegisteredConstraint::constraintId,ExternalOclConstraintService.RegisteredConstraint::negated)));
    }
    public RuntimeVerificationResult manualVerify() {
        return read(() -> {
            var beforeObjects=Set.copyOf(system().state().allObjects()); var beforeLinks=Set.copyOf(system().state().allLinks());
            engine.refreshBeliefProjection();
            if(!beforeObjects.equals(system().state().allObjects())) stateVersion++;
            var result = finish("MANUAL", "manual", "user", 0, Instant.now(), Map.of(), false, false, "");
            publish(beforeObjects,beforeLinks); return result;
        });
    }
    public void coverageGap(String diagnostic) {
        coverageGap(null, diagnostic);
    }
    public void coverageGap(org.jacamo.bridge.contract.RuntimeEvent event, String diagnostic) {
        read(() -> { coverageLost = true;if(originalEngine!=null)originalEngine.lost(); finish("COVERAGE", event == null ? "gap" : event.eventId(),
                event == null ? "bridge" : event.sourceId(), event == null ? 0 : event.sourceSequence(),
                event == null ? Instant.now() : event.observedAt(), event == null ? Map.of("diagnostic", diagnostic)
                        : Map.of("diagnostic", diagnostic, "event", org.jacamo.bridge.contract.ContractPayloads.event(event)),
                false, false, diagnostic); publishCurrent(); return null; });
    }
    private RuntimeVerificationResult finish(String kind, String eventId, String source, long sequence, Instant observed,
            Map<String, Object> payload, boolean checkpoint, boolean evidenceOnly, String diagnostic) {
        return finish(kind,eventId,source,sequence,observed,payload,checkpoint,evidenceOnly,diagnostic,false);
    }
    private RuntimeVerificationResult finish(String kind,String eventId,String source,long sequence,Instant observed,
            Map<String,Object> payload,boolean checkpoint,boolean evidenceOnly,String diagnostic,boolean repeatedSourceCut) {
        if(originalEngine==null)return finishUnchecked(kind,eventId,source,sequence,observed,payload,checkpoint,evidenceOnly,diagnostic,false);
        try{return finishUnchecked(kind,eventId,source,sequence,observed,payload,checkpoint,evidenceOnly,diagnostic,repeatedSourceCut);}
        catch(RuntimeException failure){return originalFailure(kind,eventId,source,sequence,observed,payload,"SEMANTIC_PREPARATION_FAILED:"+failure.getClass().getSimpleName()+":"+failure.getMessage());}
    }
    private RuntimeVerificationResult finishUnchecked(String kind,String eventId,String source,long sequence,Instant observed,
            Map<String,Object> payload,boolean checkpoint,boolean evidenceOnly,String diagnostic,boolean repeatedSourceCut) {
        Instant applied = appliedClock.get(); long started = System.nanoTime();
        long exportStarted=System.nanoTime();String commands = new NativeUseSoilExporter().export(system()).commands();performance.record(RuntimePerformanceStats.Metric.USE_EXPORT,System.nanoTime()-exportStarted);
        String hash = ExternalOclConstraintService.sha256(commands.getBytes(StandardCharsets.UTF_8));
        String checkpointId = checkpoint ? kind.toLowerCase() + ":" + stateVersion + ":" + eventId : "";
        if (checkpoint) try { faults.accept("BEFORE_CHECKPOINT");checkpoints.store(checkpointId, stateVersion, hash, commands); }
        catch (RuntimeException error) { coverageLost = true; diagnostic = error.getMessage();if(originalEngine!=null){originalEngine.lost();journal.markGap("CHECKPOINT_PERSISTENCE_FAILED:"+diagnostic);} }
        if(originalEngine!=null)return finishOriginal(kind,eventId,source,sequence,observed,payload,checkpoint,evidenceOnly,diagnostic,applied,started,commands,hash,checkpointId,repeatedSourceCut);
        List<ExternalOclConstraintService.Outcome> outcomes;
        long evaluationStarted=System.nanoTime();
        if (evidenceOnly && !coverageLost) outcomes = system().model().classInvariants().stream()
                .sorted(java.util.Comparator.comparing(org.tzi.use.uml.mm.MClassInvariant::qualifiedName))
                .map(inv -> new ExternalOclConstraintService.Outcome("OBSERVED:" + inv.qualifiedName(), inv.cls().name(),
                        VerificationOutcome.SKIPPED, "EVIDENCE_ONLY_NO_STATE_VERIFICATION", inv.bodyExpression().toString())).toList();
        else { if (!evidenceOnly) verificationCount++; outcomes = constraints.evaluate(sources, coverageLost,
                CheckpointType.journalKind(kind),changedFeatures,runtimeCapabilities); }
        if(!operationOutcomes.isEmpty()) {
            outcomes=new java.util.ArrayList<>(outcomes);
            for(var operation:operationOutcomes) {
                var definition=constraints.runtimeRegistry().stream().filter(c->c.id().equals(operation.constraintId())).findFirst().orElse(null);
                String unavailable=coverageLost?"COVERAGE_LOST":sources.get("cartago")!=Completeness.COMPLETE?"REQUIRED_RUNTIME_SOURCE_INCOMPLETE:cartago"
                        :definition!=null && definition.policy().requiredCapabilities().stream().anyMatch(c->!"COMPLETE".equals(runtimeCapabilities.get(c)))?
                            "REQUIRED_CAPABILITY_UNAVAILABLE":"";
                outcomes.add(unavailable.isBlank()?operation:new ExternalOclConstraintService.Outcome(operation.constraintId(),operation.contextClass(),
                        VerificationOutcome.SKIPPED,unavailable,operation.expression(),operation.contextObject()));
            }
            operationOutcomes=List.of();
        }
        if(!coverageLost && !evidenceOnly && !engine.structureValid()) {
            outcomes=new java.util.ArrayList<>(outcomes);
            outcomes.add(new ExternalOclConstraintService.Outcome("NATIVE:STRUCTURE","Agent",VerificationOutcome.FAIL,
                "NATIVE_MULTIPLICITY_VIOLATION","USE MSystemState.checkStructure"));
            diagnostic="NATIVE_MULTIPLICITY_VIOLATION";
        }
        performance.record(RuntimePerformanceStats.Metric.OCL_EVALUATION,System.nanoTime()-evaluationStarted);
        String coverage = coverageLost ? "INCOMPLETE" : sources.isEmpty() ? "STATIC_ONLY"
                : sources.values().stream().allMatch(value -> value == Completeness.COMPLETE) ? "COMPLETE_OBSERVED" : "INCOMPLETE";
        org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerificationEnvelope semantic=null;
        if(semanticVerifier!=null) {
            long semanticStarted=System.nanoTime();var semanticImage=semanticVerifier.savepoint();String semanticBoundary=sessionId+":"+generation+":"+modelRevision+":"+stateVersion;
            try {semantic=semanticVerifier.evaluate(semanticBoundary,stateVersion,sources,coverageLost,operations.drainDetailed());}
            catch(RuntimeException evaluationFailure){semanticVerifier.restore(semanticImage);coverageLost=true;coverage="INCOMPLETE";diagnostic="SEMANTIC_EVALUATION_FAILED:"+evaluationFailure.getClass().getSimpleName()+":"+evaluationFailure.getMessage();semanticVerifier.lost(stateVersion,diagnostic);semantic=semanticVerifier.failure(semanticBoundary,stateVersion,diagnostic,lastObservation==null?null:lastObservation.semantic());}
            performance.record(RuntimePerformanceStats.Metric.SEMANTIC_EVALUATION,System.nanoTime()-semanticStarted);performance.record(RuntimePerformanceStats.Metric.ISTAR_PROPAGATION,semanticVerifier.lastIntentionalNanos());
            outcomes=semantic.constraints().stream().filter(r->!r.kind().equals("INVARIANT_BODY")).map(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticConstraintResult::legacy).toList();
        }
        RuntimeVerificationResult result = new RuntimeVerificationResult(sessionId, generation, modelRevision,
                stateVersion, eventId, source, sequence, observed, applied, Instant.now(), checkpointId,
                constraints.constraintSetHash(), hash, outcomes, System.nanoTime() - started, coverage,
                coverageLost ? "STALE" : "CURRENT_OBSERVED", diagnostic,semantic);
        VerificationSnapshot captured=null;
        long captureStarted=System.nanoTime();
        if(!evidenceOnly || coverageLost)try {
            captured=freeze(kind,result,commands,payload);snapshots.retain(captured);
            snapshotCounts.merge(captured.metadata().checkpoint(),1L,Long::sum);
        } catch(RuntimeException captureFailure) {
            coverageLost=true;
            journal.markGap("SNAPSHOT_CAPTURE_FAILED:"+captureFailure.getMessage());
            if(semanticVerifier!=null)semanticVerifier.lost(stateVersion,journal.diagnostic());
            result=staleResult(result,journal.diagnostic());
        } finally {performance.record(RuntimePerformanceStats.Metric.SNAPSHOT_BUILD,System.nanoTime()-captureStarted);}
        long journalStarted=System.nanoTime();boolean persisted=journal.append(kind,payload,result,profileInterval==null?"CORE_ONLY":profileInterval.intervalId());performance.record(RuntimePerformanceStats.Metric.JOURNAL_WRITE,System.nanoTime()-journalStarted);
        if (!persisted) {
            coverageLost = true;
            if(semanticVerifier!=null)semanticVerifier.lost(stateVersion,journal.diagnostic());
            result=staleResult(result,journal.diagnostic());
            journal.remember(result);
            captured=null;
        }
        lastObservation = result;
        if (!evidenceOnly || coverageLost) {
            latest = result;
            latestSnapshot=captured;
        }
        if(latestSnapshot!=null && !evidenceOnly && !coverageLost)try{snapshotObserver.accept(latestSnapshot);observerDiagnostic="";}catch(RuntimeException unavailable){observerDiagnostic="VIEW_NOTIFICATION_FAILED:"+unavailable.getClass().getSimpleName()+":"+unavailable.getMessage();}
        return result;
    }
    private RuntimeVerificationResult staleResult(RuntimeVerificationResult old,String reason) {
        var semantic=old.semantic()==null?null:old.semantic().failClosed(reason);var outcomes=semantic==null?constraints.evaluate(sources,true):semantic.constraints().stream().filter(r->!r.kind().equals("INVARIANT_BODY")).map(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticConstraintResult::legacy).toList();
        return new RuntimeVerificationResult(old.sessionId(),old.generation(),old.modelRevision(),old.stateVersion(),old.eventId(),old.sourceId(),old.sourceSequence(),old.observedAt(),old.appliedAt(),Instant.now(),old.checkpointId(),old.constraintSetHash(),old.stateHash(),outcomes,old.durationNanos(),"INCOMPLETE","STALE",reason,semantic);
    }
    /** Canonical route: native mutation is already accepted. Evaluation receives
     * a frozen cut and publishes a separate, versioned completion journal entry. */
    private RuntimeVerificationResult finishOriginal(String kind,String eventId,String source,long sequence,Instant observed,
            Map<String,Object> payload,boolean checkpoint,boolean evidenceOnly,String diagnostic,Instant applied,long started,String commands,String hash,String checkpointId,boolean repeatedSourceCut) {
        String boundary=sessionId+":"+generation+":"+modelRevision+":"+stateVersion+":"+eventId+":consumer:"+(++originalBoundarySequence);
        var prepared=constraints.prepareEvaluation(sources,coverageLost,CheckpointType.journalKind(kind),changedFeatures,runtimeCapabilities,engine,originalEngine.freshContexts());
        var invocation=operations.drainDetailed();var invocationWork=operations.drainPrepared();var operation=operationOutcomes;operationOutcomes=List.of();
        var pending=org.tzi.use.plugins.jacamo.semantics.runtime.SemanticResultAdapter.pending(engine.canonicalProgram(),boundary,"ORDERED_EVALUATION_PENDING");
        var outcomes=new java.util.ArrayList<ExternalOclConstraintService.Outcome>();
        prepared.invariants().forEach(inv->outcomes.add(new ExternalOclConstraintService.Outcome(inv.id(),inv.context(),VerificationOutcome.SKIPPED,"ORDERED_EVALUATION_PENDING",inv.body().toString())));
        outcomes.addAll(operation);
        RuntimeVerificationResult result=new RuntimeVerificationResult(sessionId,generation,modelRevision,stateVersion,eventId,source,sequence,observed,applied,Instant.now(),checkpointId,constraints.constraintSetHash(),hash,outcomes,System.nanoTime()-started,coverageLost?"INCOMPLETE":"EVALUATION_PENDING",coverageLost?"STALE":"PENDING_EVALUATION",diagnostic,pending);
        VerificationSnapshot captured=null;
        if(!evidenceOnly||coverageLost)try{faults.accept("BEFORE_SNAPSHOT_FREEZE");captured=freeze(kind,result,commands,payload);snapshots.retain(captured);snapshotCounts.merge(captured.metadata().checkpoint(),1L,Long::sum);}
        catch(RuntimeException failure){coverageLost=true;journal.markGap("SNAPSHOT_CAPTURE_FAILED:"+failure.getMessage());originalEngine.lost();}
        boolean persisted=journal.append(kind,payload,result,profileInterval==null?"CORE_ONLY":profileInterval.intervalId());
        if(!persisted){coverageLost=true;originalEngine.lost();result=staleOriginal(result,journal.diagnostic());journal.remember(result);captured=null;}
        lastObservation=result;if(!evidenceOnly||coverageLost){latest=result;latestSnapshot=captured;}
        if(!persisted)return result;
        final RuntimeVerificationResult accepted=result;final VerificationSnapshot frozen=captured;
        boolean advance=!evidenceOnly&&!coverageLost&&!repeatedSourceCut&&java.util.Set.of("SNAPSHOT","EVENT","OPERATION_POST","STREAM_BOUNDARY").contains(kind);
        try {
            var cut=originalEngine.prepare(stateVersion,boundary,hash,sources,coverageLost,advance,invocation,prepared,invocationWork,commands.getBytes(StandardCharsets.UTF_8).length);
            if(!evidenceOnly)verificationCount++;
            originalEngine.submit(cut).whenComplete((evaluation,error)->SwingUtilities.invokeLater(()->completeOriginal(cut,accepted,frozen,evaluation,error,evidenceOnly)));
        }catch(RuntimeException failure){return originalFailure("ENGINE_ERROR",eventId,source,sequence,observed,Map.of("boundary",boundary),"ENGINE_PREPARATION_FAILED:"+failure.getMessage());}
        return result;
    }
    private void completeOriginal(org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner.Cut cut,RuntimeVerificationResult accepted,VerificationSnapshot frozen,
            org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner.Evaluation evaluation,Throwable failure,boolean evidenceOnly) {
        if(closed.get()||cut.ticket()!=originalEngine.ticket()||!cut.identity().equals(originalEngine.identity()))return;
        RuntimeVerificationResult result;
        if(failure!=null){originalEngine.lost();result=originalError(accepted,"SEMANTIC_EVALUATION_FAILED:"+failure.getClass().getSimpleName()+":"+failure.getMessage());}
        else {
            String completeCoverage=coverageAt(cut);
            result=new RuntimeVerificationResult(accepted.sessionId(),accepted.generation(),accepted.modelRevision(),accepted.stateVersion(),accepted.eventId(),accepted.sourceId(),accepted.sourceSequence(),accepted.observedAt(),accepted.appliedAt(),Instant.now(),accepted.checkpointId(),accepted.constraintSetHash(),accepted.stateHash(),evaluation.outcomes(),accepted.durationNanos(),completeCoverage,cut.lost()?"STALE":"CURRENT_OBSERVED",accepted.diagnostic(),evaluation.evidence());
        }
        if(frozen!=null) {
            try{faults.accept("BEFORE_SNAPSHOT_COMPLETE");snapshots.validateCompletion(new VerificationSnapshot(frozen.currentVersion(),result,frozen.profile(),frozen.metadata(),frozen.image()));}
            catch(RuntimeException captureFailure){coverageLost=true;originalEngine.lost();journal.markGap("SNAPSHOT_COMPLETION_FAILED:"+captureFailure.getMessage());result=staleOriginal(result,journal.diagnostic());}
        }
        if(!journal.append("ENGINE_RESULT",Map.of("boundary",cut.boundary(),"stateVersion",cut.version(),"stateHash",cut.stateHash()),result,profileInterval==null?"CORE_ONLY":profileInterval.intervalId())){originalEngine.lost();coverageLost=true;result=staleOriginal(result,journal.diagnostic());journal.remember(result);}
        if(frozen!=null) {
            var completed=new VerificationSnapshot(frozen.currentVersion(),result,frozen.profile(),frozen.metadata(),frozen.image());
            try{snapshots.complete(completed);if(latestSnapshot!=null&&latestSnapshot.snapshotId().equals(frozen.snapshotId()))latestSnapshot=completed;}
            catch(RuntimeException capacity){coverageLost=true;originalEngine.lost();journal.markGap("SNAPSHOT_COMPLETION_FAILED:"+capacity.getMessage());result=staleOriginal(result,journal.diagnostic());if(latestSnapshot!=null&&latestSnapshot.snapshotId().equals(frozen.snapshotId()))latestSnapshot=null;}
        }
        // Old accepted boundaries still contribute history and evidence; they never
        // certify a newer live state or a changed external OCL profile.
        if(stateVersion==cut.version()&&constraints.constraintSetHash().equals(accepted.constraintSetHash())) {
            if(lastObservation!=null&&lastObservation.semantic()!=null&&lastObservation.semantic().boundary().equals(cut.boundary()))lastObservation=result;
            if(latest!=null&&latest.semantic()!=null&&latest.semantic().boundary().equals(cut.boundary())) {
                latest=result;
                if(latestSnapshot!=null&&!evidenceOnly)try{snapshotObserver.accept(latestSnapshot);observerDiagnostic="";}catch(RuntimeException error){observerDiagnostic="VIEW_NOTIFICATION_FAILED:"+error.getMessage();}
                publishCurrent();
            }
        }
    }
    /** Native commit has already happened: never throw into the projector's pre-commit rollback. */
    private RuntimeVerificationResult originalFailure(String kind,String event,String source,long sequence,Instant observed,Map<String,Object> payload,String reason) {
        originalEngine.lost();operations.clear();operationOutcomes=List.of();String hash="";try{hash=ExternalOclConstraintService.sha256(new NativeUseSoilExporter().export(system()).commands().getBytes(StandardCharsets.UTF_8));}catch(RuntimeException unavailable){reason+=";STATE_HASH_UNAVAILABLE:"+unavailable.getMessage();}
        String constraintHash;try{constraintHash=constraints.constraintSetHash();}catch(RuntimeException unavailable){constraintHash="UNAVAILABLE";}
        var now=Instant.now();var pending=org.tzi.use.plugins.jacamo.semantics.runtime.SemanticResultAdapter.pending(engine.canonicalProgram(),event,"ERROR");
        var base=new RuntimeVerificationResult(sessionId,generation,modelRevision,stateVersion,event,source,sequence,observed,now,now,"",constraintHash,hash,List.of(),0,"INCOMPLETE","STALE",reason,pending);
        var result=originalError(base,reason);
        if(!journal.append(kind,payload,result,profileInterval==null?"CORE_ONLY":profileInterval.intervalId())){coverageLost=true;journal.remember(result);}
        latestSnapshot=null;lastObservation=result;latest=result;return result;
    }
    private RuntimeVerificationResult originalError(RuntimeVerificationResult accepted,String reason) {
        var row=new org.tzi.use.plugins.jacamo.semantics.evidence.SemanticConstraintResult("ORIGINAL:EVALUATION_ERROR","EVALUATION_ERROR","","","","",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.ERROR,"",reason,accepted.stateVersion(),"",Map.of());
        var program=engine.canonicalProgram();var evidence=new org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerificationEnvelope("1.0",program.profile().engine().name(),program.rules().hash(),accepted.semantic().boundary(),List.of(row),Map.of(),Map.of("semanticHistory","GAP"),List.of(reason),Map.of("originalEngine",Map.of("version","1.0","programHash",program.hash(),"evaluated","ERROR","historyQuality","GAP")));
        return new RuntimeVerificationResult(accepted.sessionId(),accepted.generation(),accepted.modelRevision(),accepted.stateVersion(),accepted.eventId(),accepted.sourceId(),accepted.sourceSequence(),accepted.observedAt(),accepted.appliedAt(),Instant.now(),accepted.checkpointId(),accepted.constraintSetHash(),accepted.stateHash(),List.of(row.legacy()),accepted.durationNanos(),"INCOMPLETE","STALE",reason,evidence);
    }
    private String coverageAt(org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner.Cut cut){return cut.lost()?"INCOMPLETE":cut.sources().isEmpty()?"STATIC_ONLY":cut.sources().values().stream().allMatch(c->c==Completeness.COMPLETE)?"COMPLETE_OBSERVED":"INCOMPLETE";}
    private RuntimeVerificationResult staleOriginal(RuntimeVerificationResult old,String reason) {
        var semantic=old.semantic()==null?null:old.semantic().failClosed(reason);var outcomes=old.outcomes().stream().map(r->new ExternalOclConstraintService.Outcome(r.constraintId(),r.contextClass(),VerificationOutcome.SKIPPED,reason,r.expression(),r.contextObject())).toList();
        return new RuntimeVerificationResult(old.sessionId(),old.generation(),old.modelRevision(),old.stateVersion(),old.eventId(),old.sourceId(),old.sourceSequence(),old.observedAt(),old.appliedAt(),Instant.now(),old.checkpointId(),old.constraintSetHash(),old.stateHash(),outcomes,old.durationNanos(),"INCOMPLETE","STALE",reason,semantic);
    }
    /** A background caller may await this and then cross read() to settle EDT publication. */
    public java.util.concurrent.CompletableFuture<Void> originalEngineDrained(){return originalEngine==null?java.util.concurrent.CompletableFuture.completedFuture(null):originalEngine.drained();}
    /** Background-only lookup of the completion for this exact accepted cut.
     * Reuses the bounded journal; a newer live result cannot stand in for it. */
    public RuntimeVerificationResult awaitEvaluation(RuntimeVerificationResult accepted,long timeoutMillis) {
        if(originalEngine==null)return accepted;
        if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("ENGINE_COMPLETION_WAIT_ON_EDT");
        if(accepted==null||accepted.semantic()==null||timeoutMillis<1)throw new IllegalArgumentException("ENGINE_COMPLETION_REQUEST_INVALID");
        var retained=retainedCompletion(accepted);
        if(retained!=null)return retained;
        try{originalEngineDrained().get(timeoutMillis,java.util.concurrent.TimeUnit.MILLISECONDS);}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("ENGINE_COMPLETION_INTERRUPTED",interrupted);}
        catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException failure){throw new IllegalStateException("ENGINE_COMPLETION_UNAVAILABLE",failure);}
        retained=retainedCompletion(accepted);
        if(retained==null)throw new IllegalStateException("ENGINE_COMPLETION_NOT_RETAINED");return retained;
    }
    private RuntimeVerificationResult retainedCompletion(RuntimeVerificationResult accepted) {
        return read(()->{
            if(!sessionId.equals(accepted.sessionId())||generation!=accepted.generation()||!modelRevision.equals(accepted.modelRevision()))throw new IllegalStateException("ENGINE_COMPLETION_OWNER_REPLACED");
            RuntimeVerificationResult found=null;
            for(var result:journal.history())if(result.semantic()!=null&&result.semantic().boundary().equals(accepted.semantic().boundary())
                    &&result.stateVersion()==accepted.stateVersion()&&result.stateHash().equals(accepted.stateHash())&&result.constraintSetHash().equals(accepted.constraintSetHash())
                    &&result.semantic().observation().get("originalEngine") instanceof Map<?,?> core
                    &&java.util.Set.of("PINNED_NATIVE_CUT","ERROR").contains(core.get("evaluated")))found=result;
            return found;
        });
    }
    public VerificationSnapshot completedSnapshot(RuntimeVerificationResult accepted,long timeoutMillis) {
        if(originalEngine==null)return verificationSnapshot();
        var result=awaitEvaluation(accepted,timeoutMillis);
        return read(()->snapshots.snapshots().stream().filter(cut->cut.result()!=null&&cut.result().semantic()!=null
                &&cut.result().semantic().boundary().equals(result.semantic().boundary())&&cut.result().resultHash().equals(result.resultHash()))
                .findFirst().orElseThrow(()->new IllegalStateException("ENGINE_COMPLETED_SNAPSHOT_NOT_RETAINED")));
    }
    private VerificationSnapshot freeze(String kind,RuntimeVerificationResult result,String commands,Map<String,Object> payload) {
        var objects=new java.util.TreeMap<String,VerificationSnapshot.ObjectState>();
        var aliases=engine.objectIdentityAliases();
        for(var object:system().state().allObjects()) {
            var values=new java.util.TreeMap<String,String>();var types=new java.util.TreeMap<String,String>();
            for(var attribute:object.cls().allAttributes()) {
                var value=object.state(system().state()).attributeValue(attribute);
                values.put(attribute.name(),value.toString());types.put(attribute.name(),value.type().toString());
            }
            String identity=engine.metadata(object,"semanticId");
            objects.put(object.name(),new VerificationSnapshot.ObjectState(object.name(),object.cls().name(),identity,
                    aliases.getOrDefault(object.name(),List.of()),values,types,engine.objectMetadata(object)));
        }
        var links=system().state().allLinks().stream().map(link->new VerificationSnapshot.LinkState(link.association().name(),
                link.linkedObjects().stream().map(MObject::name).toList(),link.getQualifier().stream()
                    .map(end->end.stream().map(Object::toString).toList()).toList()))
                .sorted(Comparator.comparing(VerificationSnapshot.LinkState::association).thenComparing(l->l.participants().toString())).toList();
        var correlation=new java.util.TreeSet<String>();
        Object raw=payload.get("correlationId");if(raw instanceof String id && !id.isBlank()) correlation.add(id);
        var lifecycle=coverageLost?VerificationSnapshot.SynchronizationState.STALE:
                sources.isEmpty()?VerificationSnapshot.SynchronizationState.MODEL_READY:controlledLifecycle==null?
                    VerificationSnapshot.SynchronizationState.LIVE:controlledLifecycle;
        var complete=coverageLost?Completeness.UNAVAILABLE:sources.isEmpty()?Completeness.UNAVAILABLE:
                sources.values().stream().allMatch(v->v==Completeness.COMPLETE)?Completeness.COMPLETE:Completeness.PARTIAL;
        long sequence=++boundarySequence;
        var metadata=new VerificationSnapshot.Metadata(sessionId+":"+generation+":cut:"+sequence,sequence,Instant.now(),generation,
                CheckpointType.journalKind(kind),correlation,lifecycle,runtimeCapabilities,sourceVersions,complete,sources,
                Map.of("contractVersion",VerificationSnapshot.CONTRACT_VERSION,"sessionId",sessionId,"modelRevision",modelRevision,
                        "eventId",result.eventId(),"sourceId",result.sourceId(),"sourceSequence",Long.toString(result.sourceSequence()),
                        "stateHash",result.stateHash(),"projectionPolicy",org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionPolicy.VERSION,
                        "enforcementHash",constraints.enforcementHash(),"controlState",controlState==null?"UNAVAILABLE":controlState.name()));
        return new VerificationSnapshot(stateVersion,result,profileInterval,metadata,new VerificationSnapshot.StateImage(objects,links,commands));
    }
    private void publish(Set<MObject> beforeObjects, Set<MLink> beforeLinks) {
        if (deferredNotifications != null) return;
        StateDifference difference = new StateDifference();
        Set<MObject> afterObjects = Set.copyOf(system().state().allObjects());
        Set<MLink> afterLinks = Set.copyOf(system().state().allLinks());
        difference.addDeletedLinks(beforeLinks.stream().filter(link -> !afterLinks.contains(link)).toList());
        difference.addDeletedObjects(beforeObjects.stream().filter(object -> !afterObjects.contains(object)).toList());
        difference.addNewObjects(afterObjects.stream().filter(object -> !beforeObjects.contains(object)).toList());
        difference.addNewLinks(afterLinks.stream().filter(link -> !beforeLinks.contains(link)).toList());
        difference.addModifiedObjects(afterObjects.stream().filter(beforeObjects::contains).toList());
        java.util.Map<String, AtomicStateChangedEvent.InvariantEvaluation> evaluations = new java.util.LinkedHashMap<>();
        for (var result : latest.outcomes()) evaluations.put(result.constraintId().substring(result.constraintId().indexOf(':') + 1),
                new AtomicStateChangedEvent.InvariantEvaluation(result.outcome().name(), result.diagnostic()));
        try{system().getEventBus().post(new AtomicStateChangedEvent(difference, evaluations));}
        catch(RuntimeException notification){observerDiagnostic="STATE_NOTIFICATION_FAILED:"+notification.getClass().getSimpleName()+":"+notification.getMessage();}
    }
    private void publishCurrent() { publish(Set.copyOf(system().state().allObjects()), Set.copyOf(system().state().allLinks())); }

    /** Seed newly reopened normal USE views with this context's existing native formal result. */
    public void refreshViews() { read(() -> { publishCurrent(); return null; }); }
}
