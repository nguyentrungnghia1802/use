package org.tzi.use.plugins.jacamo.verification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import org.tzi.use.plugins.jacamo.runtime.MirrorState;
import org.tzi.use.plugins.jacamo.runtime.MutationResult;
import org.tzi.use.plugins.jacamo.runtime.MutationStatus;
import org.tzi.use.plugins.jacamo.runtime.RuntimeDriftReport;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEvent;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEventKind;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEventObserver;
import org.tzi.use.plugins.jacamo.runtime.RuntimeQueueBackpressureException;
import org.tzi.use.plugins.jacamo.runtime.RuntimeSnapshot;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MSystem;

/** Event-driven OCL coordinator. It observes JaCaMo and never blocks or controls its execution. */
public final class RuntimeVerificationEngine implements RuntimeEventObserver {
    private static final Set<RuntimeEventKind> STATE_CHANGES = Set.of(
            RuntimeEventKind.CREATE_OBJECT, RuntimeEventKind.DESTROY_OBJECT,
            RuntimeEventKind.SET_ATTRIBUTE, RuntimeEventKind.INSERT_LINK, RuntimeEventKind.DELETE_LINK,
            RuntimeEventKind.OBS_PROPERTY_ADDED, RuntimeEventKind.OBS_PROPERTY_CHANGED,
            RuntimeEventKind.OBS_PROPERTY_REMOVED);

    private final MSystem system;
    private final ConstraintRegistry registry;
    private final TraceIndex trace;
    private final VerificationService verification;
    private final ConstraintDependencyIndex dependencies;
    private final LongSupplier nanoTime;
    private final Object operationLifecycle = new Object();
    private final ConcurrentMap<String, VerificationOperationState> operationCorrelations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> receivedNanos = new ConcurrentHashMap<>();
    private final List<RuntimeVerificationReport> reports = new ArrayList<>();
    private MirrorState connectionState = MirrorState.OFFLINE;
    private long snapshotVersion;
    private long completedThroughSequence = -1;
    private String snapshotFingerprint = "";

    public RuntimeVerificationEngine(MSystem system, ConstraintRegistry registry, TraceIndex trace) {
        this(system, registry, trace, new DefaultVerificationService());
    }

    public RuntimeVerificationEngine(MSystem system, ConstraintRegistry registry, TraceIndex trace,
                                     VerificationService verification) {
        this(system, registry, trace, verification, System::nanoTime);
    }

    RuntimeVerificationEngine(MSystem system, ConstraintRegistry registry, TraceIndex trace,
                              VerificationService verification, LongSupplier nanoTime) {
        if (system == null || registry == null || trace == null || verification == null || nanoTime == null)
            throw new IllegalArgumentException("RUNTIME_VERIFICATION_INVALID");
        this.system = system;
        this.registry = registry;
        this.trace = trace;
        this.verification = verification;
        this.nanoTime = nanoTime;
        this.dependencies = new ConstraintDependencyIndex(registry.descriptors());
    }

    @Override public synchronized void stateChanged(MirrorState state) { connectionState = state; }

    @Override public void eventReceived(RuntimeEvent event) {
        receivedNanos.putIfAbsent(event.eventId(), nanoTime.getAsLong());
    }

    @Override public void eventRejected(RuntimeEvent event, RuntimeException reason) {
        receivedNanos.remove(event.eventId());
        if (!isOperationTerminal(event) || event.correlationId() == null
                || !(reason instanceof RuntimeQueueBackpressureException backpressure)) return;
        synchronized (operationLifecycle) {
            operationCorrelations.compute(event.correlationId(), (correlation, current) ->
                    current != null && current.sequence() > event.sequence() ? current
                            : backpressure.acceptedThroughSequence() <= completedThroughSequence ? null
                            : VerificationOperationState.rejected(event.sequence(), backpressure.acceptedThroughSequence()));
        }
    }

    @Override public void eventCompleted(RuntimeEvent event) {
        receivedNanos.remove(event.eventId());
        synchronized (operationLifecycle) {
            completedThroughSequence = Math.max(completedThroughSequence, event.sequence());
            operationCorrelations.entrySet().removeIf(entry -> entry.getValue().rejected()
                    && entry.getValue().retireAfterSequence() <= completedThroughSequence);
        }
    }

    @Override public synchronized void eventStreamClosed() {
        receivedNanos.clear();
        resetOperationLifecycle();
    }

    int pendingOperationRejections() {
        synchronized (operationLifecycle) {
            return (int) operationCorrelations.values().stream().filter(VerificationOperationState::rejected).count();
        }
    }

    @Override public synchronized void snapshotApplied(RuntimeSnapshot snapshot) {
        snapshotVersion++;
        snapshotFingerprint = snapshot.fingerprint();
        long started = nanoTime.getAsLong();
        VerificationReport full = runtimeFull("snapshot:" + snapshot.snapshotId());
        append(null, full, nanoTime.getAsLong() - started, List.of("RUNTIME_AUTHORITATIVE_SNAPSHOT"));
    }

    @Override public synchronized void beforeMutation(RuntimeEvent event) {
        if (event.kind() != RuntimeEventKind.OP_ENTER) return;
        long started = eventStart(event);
        try {
            String objectName = objectName(event);
            String operationName = text(event.payload(), "operation");
            MOperation operation = system.state().objectByName(objectName).cls().operation(operationName, true);
            if (operation == null) throw new IllegalArgumentException("USE_OPERATION_MISSING: " + operationName);
            List<Value> arguments = arguments(operation, event.payload().get("arguments"));
            OperationRequest request = new OperationRequest(objectName, operationName, arguments,
                    event.correlationId(), List.of(event.eventId()));
            OperationCheck check = verification.beginOperation(system, registry, trace, request);
            boolean[] duplicate = { false };
            synchronized (operationLifecycle) {
                operationCorrelations.compute(event.correlationId(), (ignored, current) -> {
                    if (current == null || current.rejected() && current.sequence() < event.sequence()) {
                        return VerificationOperationState.active(event.sequence(), check);
                    }
                    if (!current.rejected()) duplicate[0] = true;
                    return current;
                });
            }
            if (duplicate[0])
                throw new IllegalArgumentException("OPERATION_CORRELATION_ACTIVE: " + event.correlationId());
            append(event, enrich(check.preconditions(), event), nanoTime.getAsLong() - started,
                    List.of("RUNTIME_OPERATION_PRE_CAPTURED"));
        } catch (RuntimeException exception) {
            append(event, diagnostic("RUNTIME_OPERATION_ENTER_ERROR", VerificationOutcome.ERROR,
                    exception.getMessage(), event), nanoTime.getAsLong() - started,
                    List.of("RUNTIME_OPERATION_ENTER_FAILED"));
        }
    }

    @Override public synchronized void afterMutation(RuntimeEvent event, MutationResult mutation) {
        long started = eventStart(event);
        if (mutation.status() != MutationStatus.APPLIED) {
            append(event, diagnostic("RUNTIME_MUTATION", VerificationOutcome.ERROR, mutation.diagnostic(), event),
                    nanoTime.getAsLong() - started, List.of("RUNTIME_MUTATION_NOT_APPLIED"));
            return;
        }
        if (event.kind() == RuntimeEventKind.OP_EXIT) {
            complete(event, started);
            return;
        }
        if (event.kind() == RuntimeEventKind.OP_FAIL) {
            abort(event, started);
            return;
        }
        if (!STATE_CHANGES.contains(event.kind())) return;
        snapshotVersion++;
        ConstraintDependencyIndex.Selection selection = dependencies.select(changedDependencies(event));
        VerificationReport report = selection.fullCheckFallback()
                ? runtimeFull("event:" + event.eventId())
                : verification.runTargetedVerification(system, registry, trace, selection.constraintIds(),
                        "event:" + event.eventId());
        append(event, enrich(report, event), nanoTime.getAsLong() - started, List.of(selection.reason()));
    }

    @Override public synchronized void driftChecked(RuntimeDriftReport report) {
        if (!report.drifted()) return;
        List<String> diagnostics = report.differences().stream().map(value -> value.diagnosticCode() + ":"
                + value.target()).toList();
        append(null, diagnostic("RUNTIME_DRIFT", VerificationOutcome.ERROR,
                report.differences().size() + " authoritative difference(s)", null), 0, diagnostics);
    }

    public synchronized List<RuntimeVerificationReport> reports() { return List.copyOf(reports); }
    public synchronized RuntimeVerificationReport latestReport() {
        return reports.isEmpty() ? null : reports.getLast();
    }
    public synchronized long snapshotVersion() { return snapshotVersion; }

    private VerificationReport runtimeFull(String runId) {
        VerificationReport full = verification.runFullVerification(system, registry, trace);
        return VerificationReport.runtime(runId, "RUNTIME_FULL", full.structureValid(), full.results(),
                full.fingerprints());
    }

    private void complete(RuntimeEvent event, long started) {
        OperationCheck check = removeActiveOperation(event);
        if (check == null) {
            append(event, diagnostic("RUNTIME_OPERATION_EXIT_UNMATCHED", VerificationOutcome.ERROR,
                    "operation exit has no captured pre-state", event), nanoTime.getAsLong() - started,
                    List.of("RUNTIME_OPERATION_CORRELATION_MISSING"));
            return;
        }
        VerificationReport post = verification.completeOperation(check, system.state(), null, List.of(event.eventId()));
        append(event, enrich(post, event), nanoTime.getAsLong() - started, List.of("RUNTIME_OPERATION_POST_CHECKED"));
    }

    private void abort(RuntimeEvent event, long started) {
        OperationCheck check = removeActiveOperation(event);
        String explanation = check == null ? "failed operation has no captured pre-state"
                : "JaCaMo operation aborted/failed; postconditions were not evaluated";
        append(event, diagnostic("RUNTIME_OPERATION_ABORTED", check == null ? VerificationOutcome.ERROR
                : VerificationOutcome.SKIPPED, explanation, event), nanoTime.getAsLong() - started,
                List.of(check == null ? "RUNTIME_OPERATION_CORRELATION_MISSING" : "RUNTIME_OPERATION_ABORTED"));
    }

    private OperationCheck removeActiveOperation(RuntimeEvent event) {
        OperationCheck[] matched = { null };
        synchronized (operationLifecycle) {
            operationCorrelations.compute(event.correlationId(), (ignored, current) -> {
                if (current == null || current.sequence() > event.sequence()) return current;
                if (!current.rejected()) matched[0] = current.check();
                return null;
            });
        }
        return matched[0];
    }

    private boolean isOperationTerminal(RuntimeEvent event) {
        return event.kind() == RuntimeEventKind.OP_EXIT || event.kind() == RuntimeEventKind.OP_FAIL;
    }

    private void resetOperationLifecycle() {
        synchronized (operationLifecycle) {
            operationCorrelations.clear();
            completedThroughSequence = -1;
        }
    }

    private Set<String> changedDependencies(RuntimeEvent event) {
        LinkedHashSet<String> changed = new LinkedHashSet<>();
        if (event.semanticSourceId() != null && !event.semanticSourceId().isBlank())
            changed.add(event.semanticSourceId());
        Object dependency = event.payload().get("dependencySemanticId");
        if (dependency != null && !dependency.toString().isBlank()) changed.add(dependency.toString());
        return changed;
    }

    private VerificationReport enrich(VerificationReport report, RuntimeEvent event) {
        List<VerificationResult> results = report.results().stream().map(result -> new VerificationResult(
                result.constraintId(), result.outcome(), result.contextObject(), result.explanation(),
                result.oclSource(), result.sourceTrace(), event.correlationId(),
                merge(result.runtimeEventIds(), event.eventId()))).toList();
        return VerificationReport.runtime(report.runId(), report.mode().startsWith("RUNTIME_")
                ? report.mode() : "RUNTIME_" + report.mode(), report.structureValid(), results, report.fingerprints());
    }

    private VerificationReport diagnostic(String id, VerificationOutcome outcome, String explanation,
                                          RuntimeEvent event) {
        List<String> source = event == null || event.semanticSourceId() == null
                ? List.of() : List.of(event.semanticSourceId());
        List<String> events = event == null ? List.of() : List.of(event.eventId());
        VerificationResult result = new VerificationResult(id, outcome, objectNameOrNull(event), explanation,
                "", source, event == null ? null : event.correlationId(), events);
        return VerificationReport.runtime(UUID.randomUUID().toString(), "RUNTIME_DIAGNOSTIC", true,
                List.of(result), registry.fingerprints());
    }

    private void append(RuntimeEvent event, VerificationReport report, long latency, List<String> diagnostics) {
        reports.add(new RuntimeVerificationReport("1.0.0", UUID.randomUUID().toString(), Instant.now(),
                connectionState, snapshotVersion, snapshotFingerprint, event, report, latency, diagnostics));
    }

    private String objectName(RuntimeEvent event) {
        return trace.byRuntimeKey(event.runtimeSourceId()).map(record -> record.targetUseId())
                .filter(value -> value.startsWith("object:"))
                .map(value -> value.substring("object:".length()))
                .orElseThrow(() -> new IllegalArgumentException("RUNTIME_TRACE_UNRESOLVED: "
                        + event.runtimeSourceId()));
    }

    private String objectNameOrNull(RuntimeEvent event) {
        if (event == null) return null;
        try { return objectName(event); } catch (RuntimeException ignored) { return null; }
    }

    private List<Value> arguments(MOperation operation, Object raw) {
        List<?> values = raw instanceof List<?> list ? list : List.of();
        if (values.size() != operation.paramList().size())
            throw new IllegalArgumentException("OPERATION_ARGUMENT_COUNT");
        List<Value> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            String type = operation.paramList().varDecl(index).type().toString();
            Object value = values.get(index);
            result.add(switch (type) {
                case "Boolean" -> BooleanValue.get(value instanceof Boolean booleanValue ? booleanValue
                        : Boolean.parseBoolean(String.valueOf(value)));
                case "Integer" -> IntegerValue.valueOf(value instanceof Number number ? number.intValue()
                        : Integer.parseInt(String.valueOf(value)));
                case "Real" -> new RealValue(value instanceof Number number ? number.doubleValue()
                        : Double.parseDouble(String.valueOf(value)));
                case "String" -> new StringValue(String.valueOf(value));
                default -> UndefinedValue.instance;
            });
        }
        return result;
    }

    private String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank())
            throw new IllegalArgumentException("RUNTIME_PAYLOAD_INVALID: " + key);
        return value.toString();
    }

    private List<String> merge(List<String> existing, String eventId) {
        LinkedHashSet<String> result = new LinkedHashSet<>(existing);
        result.add(eventId);
        return List.copyOf(result);
    }

    private long eventStart(RuntimeEvent event) {
        return receivedNanos.getOrDefault(event.eventId(), nanoTime.getAsLong());
    }

    private record VerificationOperationState(long sequence, OperationCheck check, long retireAfterSequence) {
        private static VerificationOperationState active(long sequence, OperationCheck check) {
            return new VerificationOperationState(sequence, check, -1);
        }

        private static VerificationOperationState rejected(long sequence, long retireAfterSequence) {
            return new VerificationOperationState(sequence, null, retireAfterSequence);
        }

        private boolean rejected() { return check == null; }
    }
}
