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
    public record Mutation<T>(T value, boolean changed, boolean preserveObservedViolations) {
        public Mutation(T value,boolean changed) { this(value,changed,false); }
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
    private final AtomicInteger backlog = new AtomicInteger();
    private volatile int highWatermark;
    private volatile RuntimeVerificationResult latest;
    private volatile RuntimeVerificationResult lastObservation;
    private String sessionId, modelRevision;
    private long generation, stateVersion, verificationCount;
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
    void observedSources(List<org.jacamo.bridge.contract.RuntimeFact> facts,boolean replacement) {
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
        if(event.entityId()!=null && (event.factKind()==org.jacamo.bridge.contract.RuntimeFactKind.GROUP_BOARD || event.factKind()==org.jacamo.bridge.contract.RuntimeFactKind.SCHEME_BOARD))
            observedSources(List.of(new org.jacamo.bridge.contract.RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),event.evidence())),false);
    }
    private Set<String> changedFeatures=Set.of();
    private final NativeOperationCheckpoints operations;
    private List<ExternalOclConstraintService.Outcome> operationOutcomes=List.of();
    private java.util.function.Consumer<VerificationSnapshot> snapshotObserver=s->{};
    private VerificationSnapshot.SynchronizationState controlledLifecycle;
    private org.jacamo.bridge.contract.RuntimeControlContract.State controlState;
    public void snapshotObserver(java.util.function.Consumer<VerificationSnapshot> observer) {read(()->{snapshotObserver=java.util.Objects.requireNonNull(observer);return null;});}
    public void controlledLifecycle(VerificationSnapshot.SynchronizationState state) {read(()->{controlledLifecycle=state;return null;});}
    public void controlState(org.jacamo.bridge.contract.RuntimeControlContract.State state) {read(()->{controlState=state;return null;});}
    void operationCheckpoint(org.jacamo.bridge.contract.RuntimeEvent event) {operationOutcomes=operations.observe(event);}
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
                    createEphemeralDirectory(defaultRuntimeRoot()), 512, 64L * 1024 * 1024, true); }
    public RuntimeVerificationCoordinator(NativeRuntimeMutationEngine engine, String sessionId, long generation,
            String revision, Path directory, int tail, long bytes) {
        this(engine, sessionId, generation, revision, directory, tail, bytes, false);
    }
    public RuntimeVerificationCoordinator(NativeRuntimeMutationEngine engine, String sessionId, long generation,
            String revision, Path directory, int tail, long bytes, boolean cleanupOnClose) {
        this.engine = engine; this.sessionId = sessionId; this.generation = generation; this.modelRevision = revision;
        operations=new NativeOperationCheckpoints(engine);
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
        if (shutdownHook != null) {
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
            catch (IllegalStateException ignored) { }
        }
        if (cleanupOnClose) cleanupDirectory();
    }
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
        FutureTask<T> future = new FutureTask<>(() -> {
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
                engine.rollback(image); constraints.restore(profile);
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
            var savepoint = engine.savepoint();
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
                if ((mutation.changed() || checkpoint) && !engine.structureTransitionValid(savepoint,mutation.preserveObservedViolations()))
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_STRUCTURE_INVALID");
            } catch (Exception error) {
                try { engine.rollback(savepoint); }
                catch (RuntimeException rollback) { error.addSuppressed(rollback); }
                sessionId = previousSession; modelRevision = previousRevision;
                generation = previousGeneration; sources = previousSources;
                moiseObservations.clear();moiseObservations.putAll(previousMoise);runtimeCapabilities=previousCapabilities;
                coverageLost = true;
                finish("REJECTED", eventId, sourceId, sequence, observed, payload, false, true,
                        error.getClass().getSimpleName() + ":" + error.getMessage());
                publishCurrent();
                if (error instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException(error);
            } finally {performance.record(RuntimePerformanceStats.Metric.USE_MUTATION,System.nanoTime()-mutationStarted);}
            if (mutation.changed() || checkpoint) stateVersion++;
            changedFeatures=mutation.changed()?engine.changedFeatures(savepoint):kind.equals("OPERATION_POST")?operations.features():Set.of();
            finish(kind, eventId, sourceId, sequence, observed, payload, checkpoint, !mutation.changed() && !checkpoint, "");
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
            operations.clear();
        }
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
        read(() -> { coverageLost = true; finish("COVERAGE", event == null ? "gap" : event.eventId(),
                event == null ? "bridge" : event.sourceId(), event == null ? 0 : event.sourceSequence(),
                event == null ? Instant.now() : event.observedAt(), event == null ? Map.of("diagnostic", diagnostic)
                        : Map.of("diagnostic", diagnostic, "event", org.jacamo.bridge.contract.ContractPayloads.event(event)),
                false, false, diagnostic); publishCurrent(); return null; });
    }
    private RuntimeVerificationResult finish(String kind, String eventId, String source, long sequence, Instant observed,
            Map<String, Object> payload, boolean checkpoint, boolean evidenceOnly, String diagnostic) {
        Instant applied = Instant.now(); long started = System.nanoTime();
        String commands = new NativeUseSoilExporter().export(system()).commands();
        String hash = ExternalOclConstraintService.sha256(commands.getBytes(StandardCharsets.UTF_8));
        String checkpointId = checkpoint ? kind.toLowerCase() + ":" + stateVersion + ":" + eventId : "";
        if (checkpoint) try { checkpoints.store(checkpointId, stateVersion, hash, commands); }
        catch (RuntimeException error) { coverageLost = true; diagnostic = error.getMessage(); }
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
        RuntimeVerificationResult result = new RuntimeVerificationResult(sessionId, generation, modelRevision,
                stateVersion, eventId, source, sequence, observed, applied, Instant.now(), checkpointId,
                constraints.constraintSetHash(), hash, outcomes, System.nanoTime() - started, coverage,
                coverageLost ? "STALE" : "CURRENT_OBSERVED", diagnostic);
        VerificationSnapshot captured=null;
        long captureStarted=System.nanoTime();
        if(!evidenceOnly || coverageLost)try {
            captured=freeze(kind,result,commands,payload);snapshots.retain(captured);
            snapshotCounts.merge(captured.metadata().checkpoint(),1L,Long::sum);
        } catch(RuntimeException captureFailure) {
            coverageLost=true;
            journal.markGap("SNAPSHOT_CAPTURE_FAILED:"+captureFailure.getMessage());
        } finally {performance.record(RuntimePerformanceStats.Metric.SNAPSHOT_BUILD,System.nanoTime()-captureStarted);}
        if (!journal.append(kind, payload, result, profileInterval == null ? "CORE_ONLY" : profileInterval.intervalId())) {
            coverageLost = true;
            result = new RuntimeVerificationResult(sessionId, generation, modelRevision, stateVersion, eventId, source,
                    sequence, observed, applied, Instant.now(), checkpointId, constraints.constraintSetHash(), hash,
                    constraints.evaluate(sources, true), System.nanoTime() - started, "INCOMPLETE", "STALE", journal.diagnostic());
            journal.remember(result);
            captured=null;
        }
        lastObservation = result;
        if (!evidenceOnly || coverageLost) {
            latest = result;
            latestSnapshot=captured;
        }
        if(latestSnapshot!=null && !evidenceOnly && !coverageLost)snapshotObserver.accept(latestSnapshot);
        return result;
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
        system().getEventBus().post(new AtomicStateChangedEvent(difference, evaluations));
    }
    private void publishCurrent() { publish(Set.copyOf(system().state().allObjects()), Set.copyOf(system().state().allLinks())); }

    /** Seed newly reopened normal USE views with this context's existing native formal result. */
    public void refreshViews() { read(() -> { publishCurrent(); return null; }); }
}
