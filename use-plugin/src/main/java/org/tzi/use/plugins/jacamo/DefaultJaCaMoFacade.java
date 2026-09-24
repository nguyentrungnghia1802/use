package org.tzi.use.plugins.jacamo;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tzi.use.plugins.jacamo.binding.BindingEntry;
import org.tzi.use.plugins.jacamo.binding.BindingFile;
import org.tzi.use.plugins.jacamo.binding.BindingStore;
import org.tzi.use.plugins.jacamo.constraint.ConstraintExtractor;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.extraction.ImportResult;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.MappingModel;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlan;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.ocl.OclGenerator;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader;
import org.tzi.use.plugins.jacamo.runtime.QueueMetrics;
import org.tzi.use.plugins.jacamo.runtime.RuntimeConnector;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMirrorService;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.semantic.Dimension;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;
import org.tzi.use.plugins.jacamo.verification.ConstraintRegistry;
import org.tzi.use.plugins.jacamo.verification.DefaultVerificationService;
import org.tzi.use.plugins.jacamo.verification.RuntimeVerificationEngine;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.plugins.jacamo.verification.VerificationReport;
import org.tzi.use.plugins.jacamo.verification.VerificationReportExporter;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;

/** Stateful application service behind the USE workbench. All parsing/transformation/verification lives here. */
public final class DefaultJaCaMoFacade implements JaCaMoFacade, AutoCloseable {
    public static final DefaultJaCaMoFacade INSTANCE = new DefaultJaCaMoFacade(detectCheckout());

    private final Path checkout;
    private Path entry;
    private Path userProfile;
    private Workspace workspace;
    private RuntimeConnector configuredConnector;
    private URI configuredEndpoint;
    private int configuredQueueCapacity = 256;
    private RuntimeMirrorService runtime;
    private RuntimeVerificationEngine runtimeVerification;
    private List<Diagnostic> lastDiagnostics = List.of();
    private long lastFullCheckNanos;

    public DefaultJaCaMoFacade(Path checkout) {
        this.checkout = checkout.toAbsolutePath().normalize();
    }

    @Override public synchronized String status() {
        return workspace == null ? "JaCaMo plugin ready; no project imported"
                : "JaCaMo project " + workspace.summary.projectId() + " ready";
    }

    @Override public synchronized ProjectSummary importProject(Path jcmFile) {
        if (jcmFile == null || !jcmFile.getFileName().toString().toLowerCase().endsWith(".jcm"))
            throw new IllegalArgumentException("IMPORT_JCM_REQUIRED");
        Path candidate = jcmFile.toAbsolutePath().normalize();
        Workspace next = build(candidate, null);
        installWorkspace(next, candidate, null);
        return workspace.summary;
    }

    @Override public synchronized ProjectSummary rebuild() {
        requireWorkspace();
        installWorkspace(build(entry, userProfile), entry, userProfile);
        return workspace.summary;
    }

    @Override public synchronized ProjectSummary projectSummary() { return workspace == null ? null : workspace.summary; }
    @Override public synchronized List<SourceRow> sources() { return workspace == null ? List.of() : workspace.sources; }
    @Override public synchronized List<Diagnostic> diagnostics() { return lastDiagnostics; }
    @Override public synchronized List<TraceRow> traces() { return workspace == null ? List.of() : workspace.traces; }
    @Override public synchronized List<org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor> constraints() {
        return workspace == null ? List.of() : workspace.registry.descriptors();
    }

    @Override public synchronized VerificationReport runFullVerification() {
        requireWorkspace();
        long started = System.nanoTime();
        workspace.latest = new DefaultVerificationService().runFullVerification(workspace.direct.system(),
                workspace.registry, workspace.trace);
        lastFullCheckNanos = System.nanoTime() - started;
        return workspace.latest;
    }

    @Override public synchronized VerificationReport latestVerification() {
        return workspace == null ? null : workspace.latest;
    }

    @Override public synchronized void loadVerificationProfile(Path profile) {
        requireWorkspace();
        if (profile == null || !Files.isRegularFile(profile.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("OCL_USER_PROFILE_IO");
        Path candidate = profile.toAbsolutePath().normalize();
        Workspace next = build(entry, candidate);
        installWorkspace(next, entry, candidate);
    }

    @Override public synchronized void exportVerificationReport(Path destination) {
        requireWorkspace();
        if (workspace.latest == null) runFullVerification();
        Path temporary = null;
        try {
            Path output = destination.toAbsolutePath().normalize();
            String name = output.getFileName() == null ? "" : output.getFileName().toString().toLowerCase();
            if (!name.endsWith(".json") && !name.endsWith(".md"))
                throw new IllegalArgumentException("REPORT_EXPORT_EXTENSION: use .json or .md");
            rejectSymbolicPath(output);
            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            String content = name.endsWith(".json")
                    ? new VerificationReportExporter().toJson(workspace.latest)
                    : new VerificationReportExporter().toMarkdown(workspace.latest);
            temporary = Files.createTempFile(parent, ".jacamo-report-", ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
        } catch (Exception exception) {
            Exception cleanup = cleanupTemporaryReport(temporary, exception);
            boolean reportDiagnostic = exception instanceof IllegalArgumentException
                    && exception.getMessage() != null && exception.getMessage().startsWith("REPORT_EXPORT_");
            if (reportDiagnostic && cleanup == null) throw (IllegalArgumentException) exception;
            String message = reportDiagnostic ? exception.getMessage()
                    : "REPORT_EXPORT_FAILED: " + exception.getMessage();
            if (cleanup != null) message += "; REPORT_EXPORT_CLEANUP_FAILED: " + cleanup.getMessage();
            throw new IllegalArgumentException(message, exception);
        }
    }

    static Exception cleanupTemporaryReport(Path temporary, Exception primary) {
        if (temporary == null) return null;
        try {
            Files.deleteIfExists(temporary);
            return null;
        } catch (Exception cleanup) {
            primary.addSuppressed(cleanup);
            return cleanup;
        }
    }

    @Override public synchronized void configureRuntime(RuntimeConnector connector, URI endpoint, int queueCapacity) {
        if (connector == null || endpoint == null || queueCapacity < 1)
            throw new IllegalArgumentException("RUNTIME_CONFIGURATION_INVALID");
        configuredConnector = connector;
        configuredEndpoint = endpoint;
        configuredQueueCapacity = queueCapacity;
    }

    @Override public synchronized void connectRuntime() {
        requireWorkspace();
        if (configuredConnector == null) throw new IllegalStateException("RUNTIME_CONNECTOR_NOT_CONFIGURED");
        if (runtime != null) runtime.close();
        runtimeVerification = new RuntimeVerificationEngine(workspace.direct.system(), workspace.registry, workspace.trace);
        runtime = new RuntimeMirrorService(configuredConnector,
                new RuntimeMutationEngine(workspace.direct.system(), workspace.trace), configuredQueueCapacity,
                runtimeVerification);
        runtime.connect(configuredEndpoint);
    }

    @Override public synchronized void disconnectRuntime() {
        if (runtime != null) runtime.disconnect();
    }

    @Override public synchronized void resyncRuntime() {
        if (runtime == null) throw new IllegalStateException("RUNTIME_NOT_CONNECTED");
        runtime.resync();
    }

    @Override public synchronized RuntimeStatus runtimeStatus() {
        if (runtime == null) return RuntimeStatus.offline();
        QueueMetrics metrics = runtime.metrics();
        var latest = runtimeVerification == null ? null : runtimeVerification.latestReport();
        String event = latest == null || latest.event() == null ? "" : latest.event().eventId();
        long latency = latest == null ? 0 : latest.latencyNanos();
        long version = runtimeVerification == null ? 0 : runtimeVerification.snapshotVersion();
        int violations = runtimeVerification == null ? 0 : (int) runtimeVerification.reports().stream()
                .flatMap(report -> report.verification().results().stream())
                .filter(result -> result.outcome() == VerificationOutcome.FAIL).count();
        return new RuntimeStatus(runtime.state(), metrics.depth(), metrics.highWatermark(), metrics.processed(),
                metrics.rejected(), metrics.failed(), metrics.dropped(), runtime.lastSyncAt(), event, latency,
                version, violations);
    }

    @Override public synchronized PerformanceMetrics performanceMetrics() {
        if (workspace == null) return PerformanceMetrics.empty();
        RuntimeStatus runtimeStatus = runtimeStatus();
        Runtime jvm = Runtime.getRuntime();
        return new PerformanceMetrics(workspace.importNanos, workspace.generationNanos, lastFullCheckNanos,
                runtimeStatus.lastLatencyNanos(), jvm.totalMemory() - jvm.freeMemory());
    }

    @Override public synchronized void persistBinding(Path destination, BindingRequest request,
                                                      String selectedTargetId, String reason) {
        requireWorkspace();
        BindingCandidate candidate = request.candidates().stream()
                .filter(value -> value.semanticId().equals(selectedTargetId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("BINDING_TARGET_NOT_A_CANDIDATE"));
        BindingEntry entry = BindingEntry.active(request.sourceId(), candidate.semanticId(), "EXPLICIT_BINDING",
                reason == null || reason.isBlank() ? "Explicit user selection" : reason,
                request.sourceHash(), "USE JaCaMo Plugin UI");
        BindingStore store = new BindingStore();
        List<BindingEntry> entries = new ArrayList<>();
        Path output = destination.toAbsolutePath().normalize();
        if (Files.isRegularFile(output)) entries.addAll(store.read(output, workspace.sourceHashes).entries().stream()
                .filter(existing -> !existing.source().equals(request.sourceId())).toList());
        entries.add(entry);
        store.write(output, new BindingFile("1.0.0", entries));
    }

    @Override public synchronized void close() { if (runtime != null) runtime.close(); }

    private void installWorkspace(Workspace next, Path nextEntry, Path nextProfile) {
        Workspace previous = workspace;
        var verification = new RuntimeVerificationEngine(next.direct.system(), next.registry, next.trace);
        Runnable install = () -> {
            if (previous != null) next.trace.copyRuntimeKeysFrom(previous.trace);
            workspace = next;
            entry = nextEntry;
            userProfile = nextProfile;
            runtimeVerification = verification;
            next.latest = null;
        };
        if (runtime == null) install.run();
        else runtime.replaceWorkspace(new RuntimeMutationEngine(next.direct.system(), next.trace), verification, install);
        // Static verification predates the authoritative runtime snapshot.
        runFullVerification();
    }

    private Workspace build(Path jcmFile, Path verificationProfile) {
        long importStarted = System.nanoTime();
        ImportResult imported = new StaticProjectImporter().importProject(jcmFile);
        long importNanos = System.nanoTime() - importStarted;
        lastDiagnostics = imported.diagnostics();
        if (!imported.success()) throw new IllegalArgumentException("IMPORT_FAILED: " + imported.diagnostics());
        long generationStarted = System.nanoTime();
        MappingModel mapping = new MappingLoader().loadCanonical(checkout);
        var baseline = new TransformationPlanner().plan(imported.model(), mapping);
        var structure = new VerificationSemanticLayer().apply(baseline, mapping,
                new VerificationProfileLoader().loadActive(mapping)).transformation();
        InstancePlan instances = new InstancePlanner().plan(imported.model(), mapping, structure);
        var constraints = new ConstraintExtractor().extract(imported.model(), structure, Map.of());
        OclProfileLoader profiles = new OclProfileLoader();
        List<OclProfileLoader.LoadedProfile> loaded = new ArrayList<>();
        loaded.add(profiles.loadCore());
        Path project = imported.model().projectRoot().path();
        Path caseProfile = project.resolve("verification/" + imported.model().projectId() + ".ocl");
        if (Files.isRegularFile(caseProfile)) loaded.add(profiles.loadCase(project, project.relativize(caseProfile)));
        if (verificationProfile != null)
            loaded.add(profiles.loadUser(verificationProfile.getParent(), verificationProfile.getFileName()));
        var generated = new OclGenerator().generate(imported.model().projectId(), structure, constraints, loaded);
        String commands = new TextBackend().generate(imported.model().projectId(), structure, instances).initialCommands();
        DirectUseBackend.Result direct = new DirectUseBackend().materialize(
                new TextBackend.GeneratedArtifacts(generated.useModel(), commands), instances);
        TraceIndex trace = new TraceBuilder().build(imported.model(), mapping, structure, instances);
        List<ConstraintRegistry.RegisteredProfile> registrations = new ArrayList<>();
        for (OclProfileLoader.LoadedProfile profile : loaded) {
            ConstraintOrigin origin = profile.origin().toString().contains("jacamo-core")
                    ? ConstraintOrigin.CORE : profile.origin().equals(verificationProfile)
                    ? ConstraintOrigin.USER : ConstraintOrigin.CASE;
            registrations.add(ConstraintRegistry.profile(origin, profile));
        }
        ConstraintRegistry registry = ConstraintRegistry.load(direct.system().model(), generated, registrations);
        long generationNanos = System.nanoTime() - generationStarted;
        long verificationStarted = System.nanoTime();
        VerificationReport latest = new DefaultVerificationService().runFullVerification(direct.system(), registry, trace);
        lastFullCheckNanos = System.nanoTime() - verificationStarted;
        List<Diagnostic> diagnostics = new ArrayList<>(imported.diagnostics());
        diagnostics.addAll(direct.diagnostics());
        lastDiagnostics = List.copyOf(diagnostics);
        Map<Dimension, Long> counts = new EnumMap<>(Dimension.class);
        for (Dimension dimension : Dimension.values()) counts.put(dimension, imported.model().elements().stream()
                .filter(element -> element.kind().dimension() == dimension).count());
        Map<String, Long> dimensionCounts = new LinkedHashMap<>();
        counts.forEach((key, value) -> dimensionCounts.put(key.name(), value));
        long warnings = diagnostics.stream().filter(value -> value.severity().name().equals("WARNING")).count();
        long errors = diagnostics.stream().filter(value -> value.severity().name().matches("ERROR|FATAL")).count();
        ProjectSummary summary = new ProjectSummary(jcmFile, project, imported.model().projectId(),
                imported.model().sourceIndex().size(), dimensionCounts, mapping.mappingId(), mapping.status(),
                structure.classes().size(), instances.objects().size(), direct.structureValid(),
                Math.toIntExact(warnings), Math.toIntExact(errors));
        List<SourceRow> sources = imported.model().sourceIndex().values().stream().map(source ->
                new SourceRow(source.path(), source.kind().name(), source.byteLength(), source.sha256())).toList();
        List<TraceRow> traces = trace.records().stream().map(record -> new TraceRow(record.sourceSemanticId(),
                record.sourceKind(), record.targetUseId(), record.targetKind(), record.mappingRuleId(),
                record.projectionRuleId(), record.status().name(),
                record.sourceSpan() == null ? null : record.sourceSpan().path(),
                record.sourceSpan() == null ? 0 : record.sourceSpan().startLine(),
                dimension(record.sourceSemanticId()))).toList();
        Map<String, String> sourceHashes = imported.model().elements().stream().collect(java.util.stream.Collectors.toMap(
                element -> element.id().value(), element -> element.provenance().getFirst().sourceHash()));
        return new Workspace(summary, sources, List.copyOf(diagnostics), traces, direct, trace, registry, latest,
                sourceHashes, importNanos, generationNanos);
    }

    private String dimension(String semanticId) {
        String[] parts = semanticId.split(":", 6);
        return parts.length > 2 ? parts[2].toUpperCase() : "UNKNOWN";
    }

    private void requireWorkspace() {
        if (workspace == null) throw new IllegalStateException("PROJECT_NOT_IMPORTED");
    }

    private void rejectSymbolicPath(Path output) throws java.io.IOException {
        Path current = output.getRoot();
        for (Path part : output) {
            current = current == null ? part : current.resolve(part);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) continue;
            BasicFileAttributes attributes = Files.readAttributes(current, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther())
                throw new IllegalArgumentException("REPORT_EXPORT_SYMLINK: " + current
                        + "; choose a non-linked destination");
        }
    }

    private static Path detectCheckout() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("use-plugin/Core")) || Files.isDirectory(candidate.resolve("Core")))
                return candidate;
        }
        return current;
    }

    private static final class Workspace {
        private final ProjectSummary summary;
        private final List<SourceRow> sources;
        private final List<Diagnostic> diagnostics;
        private final List<TraceRow> traces;
        private final DirectUseBackend.Result direct;
        private final TraceIndex trace;
        private final ConstraintRegistry registry;
        private final Map<String, String> sourceHashes;
        private final long importNanos;
        private final long generationNanos;
        private VerificationReport latest;

        private Workspace(ProjectSummary summary, List<SourceRow> sources, List<Diagnostic> diagnostics,
                          List<TraceRow> traces, DirectUseBackend.Result direct, TraceIndex trace,
                          ConstraintRegistry registry, VerificationReport latest, Map<String, String> sourceHashes,
                          long importNanos, long generationNanos) {
            this.summary = summary;
            this.sources = sources;
            this.diagnostics = diagnostics;
            this.traces = traces;
            this.direct = direct;
            this.trace = trace;
            this.registry = registry;
            this.latest = latest;
            this.sourceHashes = Map.copyOf(sourceHashes);
            this.importNanos = importNanos;
            this.generationNanos = generationNanos;
        }
    }
}
