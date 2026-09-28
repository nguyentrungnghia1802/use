package org.tzi.use.plugins.jacamo.bridge;

import java.io.BufferedOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ContractPayloads;
import org.jacamo.bridge.contract.ModelFact;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeSnapshot;
import org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.SemanticAuthority;
import org.tzi.use.plugins.jacamo.mapping.ActiveBaseline;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;

/**
 * Process-B executable for a real two-JVM JaCaMo/USE acceptance run.
 *
 * <p>The executable is deliberately case-neutral. It consumes the official Bridge contract, observes live events,
 * reconnects with a fresh client, and (when the extended arguments are supplied) exercises the same facade used by
 * the Swing workbench. Its runtime classpath is filtered by the launcher so no JaCaMo implementation JAR is present.
 */
public final class LiveBridgeConsumerMain {
    private static final Set<String> REQUIRED_CAPABILITIES = Set.of("official.model", "runtime.snapshot");

    private LiveBridgeConsumerMain() { }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        Files.createDirectories(options.evidenceDirectory());
        byte[] secret = readSecret(options.secretArgument());
        var events = new ConcurrentLinkedQueue<RuntimeEvent>();
        BridgeClient.Accepted initial;
        String observedMirrorFingerprint;
        int observedFactCount;

        var initialMirror = new BridgeMirrorStateMachine(8_192);
        try (var client = client(options, secret, initialMirror, events::add)) {
            initial = client.synchronize();
            requireLive(initialMirror, "REAL_BRIDGE_RUNTIME_EMPTY");
            validateSelection(initial, options.projectKey());
            writeCanonical(options.evidenceDirectory().resolve("model-snapshot.json"),
                    ContractPayloads.model(initial.model()));
            writeCanonical(options.evidenceDirectory().resolve("runtime-snapshot-initial.json"),
                    ContractPayloads.runtime(initial.runtime()));
            observe(initialMirror, client, options.observationSeconds());
            requireLive(initialMirror, "REAL_BRIDGE_RUNTIME_NOT_LIVE_AFTER_OBSERVATION");
            observedMirrorFingerprint = initialMirror.fingerprint();
            observedFactCount = initialMirror.facts().size();
        }

        List<RuntimeEvent> eventLog = List.copyOf(events);
        String eventDigest = writeEventLog(options.evidenceDirectory().resolve("runtime-events.jsonl"), eventLog);
        BridgeClient.Accepted reconnected;
        String reconnectMirrorFingerprint;
        int reconnectFactCount;
        var reconnectMirror = new BridgeMirrorStateMachine(8_192);
        try (var client = client(options, secret, reconnectMirror, event -> { })) {
            reconnected = client.synchronize();
            requireLive(reconnectMirror, "REAL_BRIDGE_RECONNECT_RUNTIME_EMPTY");
            requireSameAuthority(initial, reconnected);
            reconnectMirrorFingerprint = reconnectMirror.fingerprint();
            reconnectFactCount = reconnectMirror.facts().size();
            writeCanonical(options.evidenceDirectory().resolve("runtime-snapshot-reconnect.json"),
                    ContractPayloads.runtime(reconnected.runtime()));
        }

        var adapted = new NativeSemanticAdapter().adapt(reconnected.model(), options.projectRoot(),
                options.projectKey());
        var baseline = new ActiveBaseline().packaged();
        var structure = new TransformationPlanner().plan(adapted.model(), baseline.mapping());
        var instances = new InstancePlanner().plan(adapted.model(), baseline.mapping(), structure);
        var materialized = new DirectUseBackend().materialize(
                new TextBackend().generate(options.projectKey() + "_real_bridge", structure, instances), instances);
        if (!materialized.structureValid() || materialized.system().state().numObjects() == 0)
            throw new IllegalStateException("REAL_BRIDGE_USE_MATERIALIZATION_INVALID");

        Map<String, Object> evidence = baseEvidence(options, initial, reconnected, eventLog, eventDigest,
                observedMirrorFingerprint, observedFactCount, reconnectMirrorFingerprint, reconnectFactCount,
                materialized);
        if (options.fullFacadeRun()) exerciseFacade(options, evidence);
        writeCanonical(options.evidenceDirectory().resolve("consumer-evidence.json"), evidence);

        long evidenceOnly = reconnectMirror.facts().values().stream().filter(fact ->
                fact.projectionStatus() != ProjectionStatus.MATERIALIZED_FAITHFULLY
                        || fact.completeness() != Completeness.COMPLETE).count();
        System.out.println("REAL_BRIDGE_CONSUMER_OK modelRevision=" + initial.model().modelRevision()
                + " session=" + initial.sessionId() + " generation=" + initial.generation()
                + " facts=" + reconnectFactCount + " evidenceOnly=" + evidenceOnly
                + " events=" + eventLog.size() + " eventDigest=" + eventDigest
                + " objects=" + materialized.system().state().numObjects()
                + " sources=" + reconnected.runtime().endWatermarks().keySet()
                + " reconnect=true facade=" + options.fullFacadeRun());
    }

    private static BridgeClient client(Options options, byte[] secret, BridgeMirrorStateMachine mirror,
                                       java.util.function.Consumer<RuntimeEvent> acceptedEvent) {
        return new BridgeClient(new LocalTcpBridgeTransport(InetAddress.getLoopbackAddress(), options.port(),
                secret, 4 * 1024 * 1024, options.requestTimeoutMillis()), mirror, options.distribution(), REQUIRED_CAPABILITIES,
                options.maxBufferedEvents(), acceptedEvent);
    }

    private static void exerciseFacade(Options options, Map<String, Object> evidence) throws Exception {
        BridgeConnectionConfig configuration = new BridgeConnectionConfig(
                URI.create("tcp://127.0.0.1:" + options.port()), options.secretFile(), options.distribution(),
                REQUIRED_CAPABILITIES, 4 * 1024 * 1024, options.requestTimeoutMillis(), options.maxBufferedEvents());
        try (var facade = new DefaultJaCaMoFacade(options.useCheckout(), SemanticAuthority.BRIDGE,
                () -> configuration, BridgeTransportFactory.localTcp())) {
            facade.configureBridge(configuration);
            JaCaMoFacade.ProjectSummary imported = facade.importProject(options.jcmFile());
            JaCaMoFacade.AuthorityStatus authorityBefore = facade.authorityStatus();
            JaCaMoFacade.RuntimeStatus runtimeBefore = facade.runtimeStatus();
            JaCaMoFacade.FormalStateStatus formalBefore = facade.formalStateStatus();
            var verificationBefore = facade.runFullVerification();
            Path jsonReport = options.evidenceDirectory().resolve("verification-report.json");
            Path markdownReport = options.evidenceDirectory().resolve("verification-report.md");
            facade.exportVerificationReport(jsonReport);
            facade.exportVerificationReport(markdownReport);

            facade.resyncRuntime();
            JaCaMoFacade.AuthorityStatus authorityAfter = facade.authorityStatus();
            JaCaMoFacade.RuntimeStatus runtimeAfter = facade.runtimeStatus();
            JaCaMoFacade.FormalStateStatus formalAfter = facade.formalStateStatus();
            var verificationAfter = facade.runFullVerification();
            facade.exportVerificationReport(options.evidenceDirectory().resolve("verification-report-resync.json"));
            requireFacadeLive(imported, authorityBefore, authorityAfter, formalBefore, formalAfter);

            Map<String, Object> facadeEvidence = new LinkedHashMap<>();
            facadeEvidence.put("projectSummary", projectSummary(imported));
            facadeEvidence.put("authorityBeforeResync", authority(authorityBefore));
            facadeEvidence.put("authorityAfterResync", authority(authorityAfter));
            facadeEvidence.put("runtimeBeforeResync", runtime(runtimeBefore));
            facadeEvidence.put("runtimeAfterResync", runtime(runtimeAfter));
            facadeEvidence.put("formalStateBeforeResync", formal(formalBefore));
            facadeEvidence.put("formalStateAfterResync", formal(formalAfter));
            facadeEvidence.put("traceCount", facade.traces().size());
            facadeEvidence.put("traceStatusCounts", counts(facade.traces().stream().map(JaCaMoFacade.TraceRow::status)));
            facadeEvidence.put("constraintCount", facade.constraints().size());
            facadeEvidence.put("diagnosticCounts", counts(facade.diagnostics().stream()
                    .map(value -> value.severity().name() + ":" + value.code())));
            facadeEvidence.put("verificationBeforeResync", verification(verificationBefore));
            facadeEvidence.put("verificationAfterResync", verification(verificationAfter));
            facadeEvidence.put("performance", Map.of(
                    "importNanos", facade.performanceMetrics().importNanos(),
                    "generationNanos", facade.performanceMetrics().generationNanos(),
                    "fullCheckNanos", facade.performanceMetrics().fullCheckNanos(),
                    "runtimeLastLatencyNanos", facade.performanceMetrics().runtimeLastLatencyNanos(),
                    "usedMemoryBytes", facade.performanceMetrics().usedMemoryBytes()));
            evidence.put("facade", facadeEvidence);
            System.out.println("REAL_BRIDGE_FACADE_OK project=" + imported.projectId()
                    + " classes=" + formalAfter.classCount() + " objects=" + formalAfter.objectCount()
                    + " links=" + formalAfter.linkCount() + " stateSha256=" + formalAfter.sha256()
                    + " trace=" + facade.traces().size() + " constraints=" + facade.constraints().size()
                    + " outcomes=" + verificationAfter.results().size() + " resync=true");
        }
    }

    private static Map<String, Object> baseEvidence(Options options, BridgeClient.Accepted initial,
                                                    BridgeClient.Accepted reconnected,
                                                    List<RuntimeEvent> events, String eventDigest,
                                                    String observedMirrorFingerprint, int observedFactCount,
                                                    String reconnectMirrorFingerprint, int reconnectFactCount,
                                                    DirectUseBackend.Result materialized) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "1.0.0");
        result.put("status", "SUPPORTED_SCOPE_PASS");
        result.put("capturedAt", Instant.now().toString());
        result.put("projectKey", options.projectKey());
        result.put("jcm", options.jcmFile() == null ? "" : options.jcmFile().toString());
        result.put("distributionSha256", options.distribution());
        result.put("modelRevision", initial.modelRevision());
        result.put("modelSnapshotSha256", sha256(ContractPayloads.model(initial.model())));
        result.put("runtimeSnapshotInitialSha256", sha256(ContractPayloads.runtime(initial.runtime())));
        result.put("runtimeSnapshotReconnectSha256", sha256(ContractPayloads.runtime(reconnected.runtime())));
        result.put("sessionId", initial.sessionId());
        result.put("generation", initial.generation());
        result.put("capabilities", initial.capabilities().stream().collect(Collectors.toMap(
                value -> value.name(), value -> value.status().name(), (left, right) -> left, TreeMap::new)));
        result.put("completeness", initial.completeness().name());
        result.put("modelFactCounts", modelFactCounts(initial));
        result.put("configuredAgentInstances", configuredAgentInstances(initial));
        result.put("modelUnresolvedCounts", counts(initial.model().unresolvedFacts().stream()
                .map(value -> value.status().name() + ":" + value.kind())));
        result.put("runtimeInitial", runtimeSnapshot(initial.runtime()));
        result.put("runtimeReconnect", runtimeSnapshot(reconnected.runtime()));
        result.put("observedMirrorFingerprint", observedMirrorFingerprint);
        result.put("observedFactCount", observedFactCount);
        result.put("reconnectMirrorFingerprint", reconnectMirrorFingerprint);
        result.put("reconnectFactCount", reconnectFactCount);
        result.put("reconnectSameIdentity", initial.sessionId().equals(reconnected.sessionId())
                && initial.generation() == reconnected.generation()
                && initial.modelRevision().equals(reconnected.modelRevision()));
        result.put("eventCount", events.size());
        result.put("eventLogSha256", eventDigest);
        result.put("eventKindCounts", counts(events.stream().map(value -> value.kind().name())));
        result.put("eventFactKindCounts", counts(events.stream().map(value -> value.factKind() == null
                ? "NONE" : value.factKind().name())));
        result.put("eventSourceCounts", counts(events.stream().map(RuntimeEvent::sourceId)));
        result.put("eventProjectionCounts", counts(events.stream().map(value -> value.projectionStatus() == null
                ? "NONE" : value.projectionStatus().name())));
        result.put("eventCompletenessCounts", counts(events.stream().map(value -> value.completeness().name())));
        result.put("correlatedEventCount", events.stream().filter(value -> !value.correlationId().isBlank()).count());
        result.put("causedEventCount", events.stream().filter(value -> !value.causationId().isBlank()).count());
        result.put("directMaterialization", Map.of(
                "structureValid", materialized.structureValid(),
                "objectCount", materialized.system().state().numObjects(),
                "linkCount", materialized.system().state().allLinks().size(),
                "stateSha256", formalStateFingerprint(materialized)));
        result.put("observationSeconds", options.observationSeconds());
        result.put("maxBufferedEvents", options.maxBufferedEvents());
        result.put("requestTimeoutMillis", options.requestTimeoutMillis());
        result.put("consumerRuntimeHasJaCaMoJars", false);
        return result;
    }

    private static Map<String, Object> modelFactCounts(BridgeClient.Accepted accepted) {
        Stream<ModelFact> facts = Stream.of(accepted.model().sources(), accepted.model().agentDeclarations(),
                        accepted.model().workspaces(), accepted.model().configuredArtifacts(),
                        accepted.model().organisationFacts(), accepted.model().crossDimensionalRelations())
                .flatMap(List::stream);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("byKind", counts(facts.map(ModelFact::factKind)));
        result.put("groupRoleCardinalities", accepted.model().groupRoleCardinalities().size());
        result.put("parentSubGroupCardinalities", accepted.model().parentSubGroupCardinalities().size());
        return result;
    }

    private static long configuredAgentInstances(BridgeClient.Accepted accepted) {
        return accepted.model().agentDeclarations().stream().filter(value -> "agent-declaration".equals(value.factKind()))
                .mapToLong(value -> {
                    try { return Long.parseLong(value.attributes().getOrDefault("instances", "1")); }
                    catch (NumberFormatException ignored) { return 0L; }
                }).sum();
    }

    private static Map<String, Object> runtimeSnapshot(RuntimeSnapshot snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotId", snapshot.snapshotId());
        result.put("stateFingerprint", snapshot.stateFingerprint());
        result.put("factCount", snapshot.facts().size());
        result.put("factKindCounts", counts(snapshot.facts().stream().map(value -> value.kind().name())));
        result.put("projectionCounts", counts(snapshot.facts().stream().map(value -> value.projectionStatus().name())));
        result.put("completenessCounts", counts(snapshot.facts().stream().map(value -> value.completeness().name())));
        result.put("sourceCompleteness", snapshot.sourceCompleteness().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, value -> value.getValue().name(), (left, right) -> left, TreeMap::new)));
        result.put("startWatermarks", watermarks(snapshot.startWatermarks()));
        result.put("endWatermarks", watermarks(snapshot.endWatermarks()));
        result.put("validationAttempts", snapshot.validationAttempts());
        result.put("captureDurationNanos", Duration.between(snapshot.captureStartedAt(), snapshot.captureEndedAt()).toNanos());
        return result;
    }

    private static Map<String, Long> watermarks(Map<String, org.jacamo.bridge.contract.SourceWatermark> values) {
        return values.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                value -> value.getValue().sequence(), (left, right) -> left, TreeMap::new));
    }

    private static Map<String, Object> projectSummary(JaCaMoFacade.ProjectSummary value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entry", value.entry().toString());
        result.put("projectRoot", value.projectRoot().toString());
        result.put("projectId", value.projectId());
        result.put("sourceCount", value.sourceCount());
        result.put("dimensionCounts", value.dimensionCounts());
        result.put("metamodelVersion", value.metamodelVersion());
        result.put("metamodelSha256", value.metamodelSha256());
        result.put("mappingId", value.mappingId());
        result.put("mappingVersion", value.mappingVersion());
        result.put("mappingSha256", value.mappingSha256());
        result.put("mappingStatus", value.mappingStatus());
        result.put("generatedClasses", value.generatedClasses());
        result.put("generatedObjects", value.generatedObjects());
        result.put("structureValid", value.structureValid());
        result.put("warningCount", value.warningCount());
        result.put("errorCount", value.errorCount());
        return result;
    }

    private static Map<String, Object> authority(JaCaMoFacade.AuthorityStatus value) {
        return Map.of("authority", value.authority().name(), "readiness", value.readiness().name(),
                "capabilities", value.capabilities(), "completeness", value.completeness(),
                "modelRevision", value.modelRevision(), "sessionId", value.sessionId(),
                "generation", value.generation(), "endpoint", value.endpoint(), "diagnostic", value.diagnostic());
    }

    private static Map<String, Object> runtime(JaCaMoFacade.RuntimeStatus value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", value.state().name());
        result.put("processed", value.processed());
        result.put("failed", value.failed());
        result.put("lastSync", value.lastSync() == null ? "" : value.lastSync().toString());
        result.put("snapshotVersion", value.snapshotVersion());
        result.put("violationCount", value.violationCount());
        return result;
    }

    private static Map<String, Object> formal(JaCaMoFacade.FormalStateStatus value) {
        return Map.of("classCount", value.classCount(), "associationCount", value.associationCount(),
                "objectCount", value.objectCount(), "linkCount", value.linkCount(), "sha256", value.sha256());
    }

    private static Map<String, Object> verification(org.tzi.use.plugins.jacamo.verification.VerificationReport report) {
        return Map.of("mode", report.mode(), "structureValid", report.structureValid(),
                "resultCount", report.results().size(),
                "outcomeCounts", counts(report.results().stream().map(value -> value.outcome().name())),
                "fingerprints", report.fingerprints());
    }

    private static void requireFacadeLive(JaCaMoFacade.ProjectSummary imported,
                                          JaCaMoFacade.AuthorityStatus before,
                                          JaCaMoFacade.AuthorityStatus after,
                                          JaCaMoFacade.FormalStateStatus formalBefore,
                                          JaCaMoFacade.FormalStateStatus formalAfter) {
        if (!imported.structureValid() || before.readiness() != BridgeClientState.LIVE
                || after.readiness() != BridgeClientState.LIVE)
            throw new IllegalStateException("REAL_BRIDGE_FACADE_NOT_LIVE:before=" + before.readiness()
                    + ":beforeDiagnostic=" + before.diagnostic() + ":after=" + after.readiness()
                    + ":afterDiagnostic=" + after.diagnostic());
        if (!before.sessionId().equals(after.sessionId()) || before.generation() != after.generation()
                || !before.modelRevision().equals(after.modelRevision()))
            throw new IllegalStateException("REAL_BRIDGE_FACADE_RESYNC_IDENTITY_CHANGED");
        if (formalBefore.objectCount() < 1 || formalAfter.objectCount() < 1
                || formalBefore.sha256().isBlank() || formalAfter.sha256().isBlank())
            throw new IllegalStateException("REAL_BRIDGE_FACADE_FORMAL_STATE_EMPTY");
    }

    private static void validateSelection(BridgeClient.Accepted accepted, String projectKey) {
        if (!accepted.projectKey().equals(projectKey))
            throw new IllegalStateException("REAL_BRIDGE_PROJECT_KEY_MISMATCH expected=" + projectKey
                    + " actual=" + accepted.projectKey());
    }

    private static void requireSameAuthority(BridgeClient.Accepted before, BridgeClient.Accepted after) {
        if (!before.sessionId().equals(after.sessionId()))
            throw new IllegalStateException("REAL_BRIDGE_RECONNECT_SESSION_CHANGED");
        if (before.generation() != after.generation())
            throw new IllegalStateException("REAL_BRIDGE_RECONNECT_GENERATION_CHANGED");
        if (!before.modelRevision().equals(after.modelRevision()))
            throw new IllegalStateException("REAL_BRIDGE_RECONNECT_MODEL_CHANGED");
    }

    private static void requireLive(BridgeMirrorStateMachine mirror, String message) {
        if (mirror.state() != BridgeClientState.LIVE || mirror.facts().isEmpty())
            throw new IllegalStateException(message + " state=" + mirror.state() + " facts=" + mirror.facts().size());
    }

    private static void observe(BridgeMirrorStateMachine mirror, BridgeClient client, int seconds)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (mirror.state() != BridgeClientState.LIVE)
                throw new IllegalStateException("REAL_BRIDGE_OBSERVATION_REQUIRES_RESYNC:" + mirror.state()
                        + ":" + client.diagnostic());
            Thread.sleep(100L);
        }
    }

    private static String formalStateFingerprint(DirectUseBackend.Result direct) {
        List<String> rows = new ArrayList<>();
        var state = direct.system().state();
        direct.system().model().classes().forEach(value -> rows.add("class|" + value.name()));
        direct.system().model().associations().forEach(value -> rows.add("association|" + value.name()));
        state.allObjects().forEach(object -> {
            rows.add("object|" + object.name() + "|" + object.cls().name());
            object.state(state).attributeValueMap().forEach((attribute, value) ->
                    rows.add("attribute|" + object.name() + "|" + attribute.name() + "|" + value));
        });
        state.allLinks().forEach(value -> rows.add("link|" + value));
        rows.sort(String::compareTo);
        return sha256(String.join("\n", rows).getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readSecret(String argument) throws Exception {
        Path candidate = Path.of(argument).toAbsolutePath().normalize();
        String encoded = Files.isRegularFile(candidate) ? Files.readString(candidate, StandardCharsets.US_ASCII).trim()
                : argument;
        if (!encoded.matches("[0-9a-fA-F]{64,}") || (encoded.length() & 1) != 0)
            throw new IllegalArgumentException("REAL_BRIDGE_SECRET_INVALID");
        return HexFormat.of().parseHex(encoded);
    }

    private static void writeCanonical(Path destination, Object value) throws Exception {
        Files.write(destination, CanonicalJson.encode(value));
    }

    private static String writeEventLog(Path destination, List<RuntimeEvent> events) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var output = new BufferedOutputStream(Files.newOutputStream(destination))) {
            for (RuntimeEvent event : events) {
                byte[] bytes = CanonicalJson.encode(ContractPayloads.event(event));
                output.write(bytes);
                output.write('\n');
                digest.update(bytes);
                digest.update((byte) '\n');
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(Object canonicalValue) {
        return sha256(CanonicalJson.encode(canonicalValue));
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static Map<String, Long> counts(Stream<String> values) {
        return values.collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
    }

    private record Options(int port, String secretArgument, Path secretFile, String distribution,
                           Path projectRoot, String projectKey, Path jcmFile, Path useCheckout,
                           Path evidenceDirectory, int observationSeconds, int maxBufferedEvents,
                           int requestTimeoutMillis, boolean fullFacadeRun) {
        private static Options parse(String[] args) {
            if (args.length < 5) throw new IllegalArgumentException("REAL_BRIDGE_CONSUMER_ARGS_REQUIRED");
            int port = Integer.parseInt(args[0]);
            String secret = args[1];
            Path secretFile = Path.of(secret).toAbsolutePath().normalize();
            String distribution = args[2];
            Path projectRoot = Path.of(args[3]).toAbsolutePath().normalize();
            String projectKey = args[4];
            boolean full = args.length >= 8;
            Path jcm = args.length > 5 ? Path.of(args[5]).toAbsolutePath().normalize() : null;
            Path checkout = args.length > 6 ? Path.of(args[6]).toAbsolutePath().normalize() : null;
            Path evidence = args.length > 7 ? Path.of(args[7]).toAbsolutePath().normalize()
                    : projectRoot.resolve("bridge-consumer-evidence");
            int observation = args.length > 8 ? Integer.parseInt(args[8]) : 0;
            int maxBuffered = args.length > 9 ? Integer.parseInt(args[9]) : 1_024;
            int requestTimeout = args.length > 10 ? Integer.parseInt(args[10]) : 30_000;
            if (port < 1 || port > 65_535 || projectKey.isBlank() || observation < 0 || maxBuffered < 1
                    || requestTimeout < 100)
                throw new IllegalArgumentException("REAL_BRIDGE_CONSUMER_ARGS_INVALID");
            if (full && (!Files.isRegularFile(jcm) || !Files.isDirectory(checkout)
                    || !Files.isRegularFile(secretFile)))
                throw new IllegalArgumentException("REAL_BRIDGE_FACADE_ARGS_INVALID");
            return new Options(port, secret, secretFile, distribution, projectRoot, projectKey, jcm, checkout,
                    evidence, observation, maxBuffered, requestTimeout, full);
        }
    }
}
