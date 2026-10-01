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
    public record Mutation<T>(T value, boolean changed) { }
    private final NativeRuntimeMutationEngine engine;
    private final ExternalOclConstraintService constraints;
    private final RuntimeEventJournal journal;
    private final RuntimeCheckpointStore checkpoints;
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
    public VerificationSnapshot verificationSnapshot() {
        return read(() -> {
            var profile = constraints.profile();
            var interval = profileInterval == null ? null : new VerificationSnapshot.ProfileInterval(
                    profileInterval.loadedVersion(), profileInterval.intervalId(), profileInterval.installedAt(),
                    profileInterval.sessionId(), profileInterval.generation(), profileInterval.installedModelRevision(), profile);
            var current = latest != null && latest.constraintSetHash().equals(constraints.constraintSetHash()) ? latest : null;
            return new VerificationSnapshot(stateVersion, current, interval);
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
        this.directory = directory.toAbsolutePath().normalize();
        this.cleanupOnClose = cleanupOnClose;
        constraints = new ExternalOclConstraintService(engine.system());
        journal = new RuntimeEventJournal(this.directory, tail, bytes);
        checkpoints = new RuntimeCheckpointStore(this.directory.resolve("checkpoints"));
        Thread hook = null;
        if (cleanupOnClose) {
            hook = new Thread(this::cleanupDirectory, "jacamo-runtime-cleanup");
            try { Runtime.getRuntime().addShutdownHook(hook); }
            catch (IllegalStateException ignored) { hook = null; }
        }
        shutdownHook = hook;
        read(() -> { finish("BASELINE", "baseline", "native", 0, Instant.now(), Map.of(), true, false, ""); return null; });
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
        if (SwingUtilities.isEventDispatchThread()) return call(action);
        int depth = backlog.incrementAndGet(); highWatermark = Math.max(highWatermark, depth);
        FutureTask<T> future = new FutureTask<>(action);
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

    public <T> T transaction(String kind, String eventId, String sourceId, long sequence, Instant observed,
            Map<String, Object> payload, boolean checkpoint, Callable<Mutation<T>> action) {
        return read(() -> {
            var savepoint = engine.savepoint();
            String previousSession = sessionId, previousRevision = modelRevision;
            long previousGeneration = generation;
            Map<String, Completeness> previousSources = sources;
            Set<MObject> beforeObjects = Set.copyOf(system().state().allObjects());
            Set<MLink> beforeLinks = Set.copyOf(system().state().allLinks());
            Mutation<T> mutation;
            try {
                mutation = action.call();
                if ((mutation.changed() || checkpoint) && !engine.structureValid())
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_STRUCTURE_INVALID");
            } catch (Exception error) {
                try { engine.rollback(savepoint); }
                catch (RuntimeException rollback) { error.addSuppressed(rollback); }
                sessionId = previousSession; modelRevision = previousRevision;
                generation = previousGeneration; sources = previousSources;
                coverageLost = true;
                finish("REJECTED", eventId, sourceId, sequence, observed, payload, false, true,
                        error.getClass().getSimpleName() + ":" + error.getMessage());
                publishCurrent();
                if (error instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException(error);
            }
            if (mutation.changed() || checkpoint) stateVersion++;
            finish(kind, eventId, sourceId, sequence, observed, payload, checkpoint, !mutation.changed() && !checkpoint, "");
            if (mutation.changed() || checkpoint) publish(beforeObjects, beforeLinks);
            return mutation.value();
        });
    }

    public void acceptedSnapshot(String session, long nextGeneration, String revision, Map<String, Completeness> completeness) {
        // Called inside an authoritative snapshot transaction only.
        constraints.rebind(revision);
        sessionId = session; generation = nextGeneration; modelRevision = revision;
        sources = Map.copyOf(completeness); coverageLost = journal.hasGap();
    }
    public RuntimeVerificationResult loadProfile(Path path) {
        return read(() -> {
            var profile = constraints.install(path, modelRevision);
            stateVersion++;
            recordProfileInterval(profile);
            var result = finish("PROFILE", "profile:" + profile.sourceHash(), "ocl", 0, Instant.now(),
                    profilePayload(profile), true, false, "");
            publishCurrent(); return result;
        });
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
        return read(() -> {
            var profile = constraints.installSource(file, source, modelRevision, enabled, negated);
            stateVersion++;
            recordProfileInterval(profile);
            var result = finish("PROFILE", "profile:" + profile.sourceHash(), "ocl", 0, Instant.now(),
                    profilePayload(profile), true, false, "");
            publishCurrent(); return result;
        });
    }
    private Map<String,Object> profilePayload(ExternalOclConstraintService.Profile profile) {
        return Map.of("sourceFile",profile.sourceFile(),"source",profile.source(),"modelRevision",modelRevision,
                "enabled",profile.constraints().stream().collect(java.util.stream.Collectors.toMap(
                        ExternalOclConstraintService.RegisteredConstraint::constraintId,ExternalOclConstraintService.RegisteredConstraint::enabled)),
                "negated",profile.constraints().stream().collect(java.util.stream.Collectors.toMap(
                        ExternalOclConstraintService.RegisteredConstraint::constraintId,ExternalOclConstraintService.RegisteredConstraint::negated)));
    }
    public RuntimeVerificationResult manualVerify() {
        return read(() -> { var result = finish("MANUAL", "manual", "user", 0, Instant.now(), Map.of(), false, false, "");
            publishCurrent(); return result; });
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
        if (evidenceOnly && !coverageLost) outcomes = system().model().classInvariants().stream()
                .sorted(java.util.Comparator.comparing(org.tzi.use.uml.mm.MClassInvariant::qualifiedName))
                .map(inv -> new ExternalOclConstraintService.Outcome("OBSERVED:" + inv.qualifiedName(), inv.cls().name(),
                        VerificationOutcome.SKIPPED, "EVIDENCE_ONLY_NO_STATE_VERIFICATION", inv.bodyExpression().toString())).toList();
        else { if (!evidenceOnly) verificationCount++; outcomes = constraints.evaluate(sources, coverageLost); }
        String coverage = coverageLost ? "INCOMPLETE" : sources.isEmpty() ? "STATIC_ONLY"
                : sources.values().stream().allMatch(value -> value == Completeness.COMPLETE) ? "COMPLETE_OBSERVED" : "INCOMPLETE";
        RuntimeVerificationResult result = new RuntimeVerificationResult(sessionId, generation, modelRevision,
                stateVersion, eventId, source, sequence, observed, applied, Instant.now(), checkpointId,
                constraints.constraintSetHash(), hash, outcomes, System.nanoTime() - started, coverage,
                coverageLost ? "STALE" : "CURRENT_OBSERVED", diagnostic);
        if (!journal.append(kind, payload, result, profileInterval == null ? "CORE_ONLY" : profileInterval.intervalId())) {
            coverageLost = true;
            result = new RuntimeVerificationResult(sessionId, generation, modelRevision, stateVersion, eventId, source,
                    sequence, observed, applied, Instant.now(), checkpointId, constraints.constraintSetHash(), hash,
                    constraints.evaluate(sources, true), System.nanoTime() - started, "INCOMPLETE", "STALE", journal.diagnostic());
            journal.remember(result);
        }
        lastObservation = result;
        if (!evidenceOnly || coverageLost) latest = result;
        return result;
    }
    private void publish(Set<MObject> beforeObjects, Set<MLink> beforeLinks) {
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
}
