package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cartago.ArtifactId;
import cartago.CartagoEnvironment;
import cartago.Op;
import cartago.util.agent.ActionFailedException;
import cartago.util.agent.CartagoBasicContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Map;
import jason.asSemantics.Agent;
import jason.asSemantics.Event;
import jason.asSemantics.Intention;
import jason.asSyntax.Trigger;
import moise.oe.GroupInstance;
import moise.oe.OE;
import moise.oe.OEAgent;
import moise.oe.SchemeInstance;
import moise.os.OSBuilder;
import moise.os.ns.NS;
import moise.os.ns.Norm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.constraint.ConstraintExtractor;
import org.tzi.use.plugins.jacamo.evidence.EvidenceNormalizer;
import org.tzi.use.plugins.jacamo.evidence.EvidenceSourceCommit;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.ocl.OclGenerator;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader;
import org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.plugins.jacamo.trace.TraceRecord;
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;
import org.tzi.use.plugins.jacamo.verification.ConstraintRegistry;
import org.tzi.use.plugins.jacamo.verification.RuntimeVerificationEngine;
import org.tzi.use.plugins.jacamo.verification.RuntimeVerificationReport;
import org.tzi.use.plugins.jacamo.verification.RuntimeVerificationReportExporter;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;

class LiveJaCaMoAuctionIntegrationTest {
    @TempDir Path compiledAuctionClasses;

    @Test
    void mirrorsRealJasonCartagoAndMoiseAuctionThenReconnectsWithFullResync() throws Exception {
        String sourceCommit = EvidenceSourceCommit.verify(Path.of(".."), EvidenceSourceCommit.ACTIVE_V2_INPUTS);
        Path project = Path.of("src/test/resources/auction").toAbsolutePath().normalize();
        var imported = new StaticProjectImporter().importProject(
                project.resolve("auction.jcm"));
        JaCaMoSemanticModel semantic = imported.model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var baseline = new TransformationPlanner().plan(semantic, mapping);
        var structure = new VerificationSemanticLayer().apply(baseline,
                new VerificationProfileLoader().loadActive(mapping)).transformation();
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        OclProfileLoader ocl = new OclProfileLoader();
        var caseProfile = ocl.loadCase(project, Path.of("verification/auction.ocl"));
        var generatedOcl = new OclGenerator().generate("auction", structure,
                new ConstraintExtractor().extract(semantic, structure, Map.of()), List.of(ocl.loadCore(), caseProfile));
        var generated = new TextBackend().generate("auction", structure, instances);
        DirectUseBackend.Result direct = new DirectUseBackend().materialize(
                new TextBackend.GeneratedArtifacts(generatedOcl.useModel(), generated.initialCommands()), instances);
        TraceIndex trace = new TraceBuilder().build(semantic, mapping, structure, instances);
        ConstraintRegistry registry = ConstraintRegistry.load(direct.system().model(), generatedOcl,
                List.of(ConstraintRegistry.profile(ConstraintOrigin.CORE, ocl.loadCore()),
                        ConstraintRegistry.profile(ConstraintOrigin.CASE, caseProfile)));

        String agentSemantic = semanticId(semantic, MetamodelKind.Agent, "auctioneer");
        String artifactSemantic = semanticId(semantic, MetamodelKind.Artifact, "auction1");
        String organisationSemantic = semanticId(semantic, MetamodelKind.Organization, "auction_org");
        String groupSemantic = semanticId(semantic, MetamodelKind.Group, "auction_group");
        String schemeSemantic = semanticId(semantic, MetamodelKind.Scheme, "auction_scheme");

        CartagoEnvironment environment = CartagoEnvironment.getInstance();
        environment.init();
        AuctionSourceRuntime sourceRuntime = AuctionSourceRuntime.load(project, compiledAuctionClasses, environment);
        Agent jasonAgent = sourceRuntime.jasonAgent();
        JasonRuntimeConnector jason = new JasonRuntimeConnector("jason-live",
                Map.of("auctioneer", jasonAgent.getTS()), Map.of("auctioneer", agentSemantic));

        CartagoBasicContext context = new CartagoBasicContext("auctioneer");
        ArtifactId artifact = context.makeArtifact(context.getJoinedWspId("main"), "auction1",
                sourceRuntime.artifactClassName());
        assertEquals("auction.AuctionArtifact", artifact.getArtifactType(),
                "the live run must execute the same Auction Artifact source imported into the evidence model");
        CartagoArtifactBinding artifactBinding = new CartagoArtifactBinding("/main", "auction1", artifactSemantic,
                Map.of("open", "open"), Map.of("closeAuction", "closeAuction", "placeBid", "placeBid"));
        CartagoRuntimeConnector cartago = new CartagoRuntimeConnector("cartago-live",
                new OfficialCartagoRuntimeAccess(environment), List.of(artifactBinding));

        OSBuilder os = new OSBuilder();
        os.addRootGroup("auction_group");
        os.addRole("auction_group", "auctioneer");
        os.addScheme("auction_scheme", "sell_item");
        os.addMission("auction_scheme", "run_auction", "sell_item");
        Norm norm = new Norm(os.getOS().getSS().getRoleDef("auctioneer"),
                os.getOS().getFS().findMission("run_auction"), os.getOS().getNS(), NS.OpTypes.obligation);
        norm.setId("n1");
        os.getOS().getNS().addNorm(norm);
        OE organisation = new OE(null, os.getOS());
        GroupInstance group = organisation.addGroup("auction_group", "auction_group");
        SchemeInstance scheme = organisation.startScheme("auction_scheme", "auction_scheme");
        scheme.addResponsibleGroup(group);
        OEAgent organisationalAgent = organisation.addAgent("auctioneer");
        organisationalAgent.adoptRole("auctioneer", group);
        organisationalAgent.commitToMission("run_auction", scheme);
        MoiseRuntimeBinding moiseBinding = new MoiseRuntimeBinding("auction_org", organisationSemantic,
                Map.of("auctioneer", agentSemantic), Map.of("auction_group", groupSemantic),
                Map.of("auction_scheme", schemeSemantic));
        MoiseRuntimeConnector moise = new MoiseRuntimeConnector("moise-live", organisation, moiseBinding);

        register(trace, agentSemantic, "jason:agent:auctioneer");
        register(trace, agentSemantic, moiseBinding.agentRuntimeId("auctioneer"));
        register(trace, artifactSemantic, artifactBinding.runtimeSourceId());
        register(trace, organisationSemantic, moiseBinding.organisationRuntimeId());
        register(trace, groupSemantic, moiseBinding.groupRuntimeId("auction_group"));
        register(trace, schemeSemantic, moiseBinding.schemeRuntimeId("auction_scheme"));

        CompositeRuntimeConnector composite = new CompositeRuntimeConnector("jacamo-live",
                List.of(jason, cartago, moise));
        RuntimeVerificationEngine verifier = new RuntimeVerificationEngine(direct.system(), registry, trace);
        EvidenceEventObserver evidenceEvents = new EvidenceEventObserver(verifier);
        RuntimeMutationEngine mutations = new RuntimeMutationEngine(direct.system(), trace);
        RuntimeMirrorService mirror = new RuntimeMirrorService(composite, mutations, 64, evidenceEvents);
        try {
            mirror.connect(URI.create("jacamo://local/auction"));
            assertEquals(MirrorState.LIVE, mirror.state());
            assertTrue(openValue(direct, trace, artifactSemantic));
            assertFalse(mirror.checkDrift(DriftResyncPolicy.REPORT_ONLY).drifted());

            int checkpoint = verifier.reports().size();
            context.doAction(artifact, new Op("placeBid", "item1", 10));
            mirror.awaitIdle(Duration.ofSeconds(5));
            List<RuntimeVerificationReport> validBid = reportsAfter(verifier, checkpoint);
            assertFalse(hasFailure(validBid, registry, "Guard_canBid"));
            assertFalse(hasFailure(validBid, registry, "AuctionOpenForBid"));
            assertFalse(hasFailure(validBid, registry, "PositiveBidAmount"));

            checkpoint = verifier.reports().size();
            assertThrows(ActionFailedException.class,
                    () -> context.doAction(artifact, new Op("placeBid", "item1", 0)));
            mirror.awaitIdle(Duration.ofSeconds(5));
            List<RuntimeVerificationReport> invalidAmount = reportsAfter(verifier, checkpoint);
            assertTrue(hasFailure(invalidAmount, registry, "PositiveBidAmount"),
                    "authored case precondition must mirror the source body's explicit positive-amount boundary");

            context.doAction(artifact, new Op("closeAuction"));
            mirror.awaitIdle(Duration.ofSeconds(5));
            assertFalse(openValue(direct, trace, artifactSemantic));
            assertFalse(mirror.checkDrift(DriftResyncPolicy.REPORT_ONLY).drifted());

            checkpoint = verifier.reports().size();
            context.doAction(artifact, new Op("placeBid", "item1", 10));
            mirror.awaitIdle(Duration.ofSeconds(5));
            List<RuntimeVerificationReport> closedBid = reportsAfter(verifier, checkpoint);
            assertTrue(hasFailure(closedBid, registry, "AuctionOpenForBid"),
                    "authored case precondition must detect a bid while the auction is closed");

            jasonAgent.getTS().getC().addEvent(new Event(
                    Trigger.parseTrigger("+bid_seen(item1)"), Intention.EmptyInt));
            scheme.getGoal("sell_item").setAchieved(organisationalAgent);
            moise.pollChanges();
            mirror.awaitIdle(Duration.ofSeconds(5));
            assertEquals(0, mirror.metrics().dropped());
            assertTrue(mirror.metrics().processed() >= 9);
            assertTrue(verifier.reports().stream().anyMatch(report -> report.event() != null
                    && report.hasViolation() && report.verification().results().stream().anyMatch(result ->
                    result.outcome() == VerificationOutcome.FAIL
                            && result.sourceTrace().contains(artifactSemantic)
                            && result.runtimeEventIds().contains(report.event().eventId()))),
                    "real CArtAgO execution must produce an event-correlated violation with JaCaMo trace");

            QueueMetrics scenarioMetrics = mirror.metrics();
            List<RuntimeEvent> scenarioEvents = evidenceEvents.events();
            assertEquals(MirrorState.LIVE, mirror.state());
            assertEquals(0, scenarioMetrics.failed(), "no received event may fail USE mutation");
            assertEquals(0, scenarioMetrics.rejected(), "no received event may be rejected");
            assertEquals(0, scenarioMetrics.dropped(), "no received event may be dropped");
            mirror.disconnect();
            assertEquals(MirrorState.STALE, mirror.state());
            context.doAction(artifact, new Op("removeOpen"));
            mirror.reconnectAndResync();
            assertEquals(MirrorState.LIVE, mirror.state());
            assertEquals(UndefinedValue.instance, attributeValue(direct, trace, artifactSemantic, "open"));
            assertFalse(mirror.lastSnapshotFingerprint().isBlank());
            RuntimeDriftReport reconnectComparison = mirror.checkDrift(DriftResyncPolicy.REPORT_ONLY);
            assertFalse(reconnectComparison.drifted(),
                    "the full USE mirror must equal the authoritative runtime snapshot after reconnect");
            assertEquals(mirror.lastSnapshotFingerprint(), reconnectComparison.fingerprint());
            writeRuntimeEvidence(Path.of("target/phase35-auction-evidence/runtime"), verifier.reports(), scenarioEvents,
                    registry,
                    scenarioMetrics, reconnectComparison, evidenceEvents.snapshots(), validBid, invalidAmount, closedBid,
                    project, artifact.getArtifactType(), sourceCommit);
            Path mirrorEvidence = Path.of("target/phase19-mirror-evidence");
            Files.createDirectories(mirrorEvidence);
            Files.writeString(mirrorEvidence.resolve("model.use"), generatedOcl.useModel());
            Files.writeString(mirrorEvidence.resolve("initial-state.cmd"), generated.initialCommands());
            new RuntimeEventCodec().writeEvents(mirrorEvidence.resolve("runtime-events.json"), scenarioEvents);
            ObjectMapper evidenceJson = new ObjectMapper();
            var runtimeMapping = new RuntimeMappingLoader().loadDefault();
            var decisions = mutations.runtimeTrace().entries().stream().map(entry -> {
                Map<String,Object> row = new java.util.LinkedHashMap<>();
                row.put("generation", entry.generation()); row.put("eventId", entry.event().eventId());
                row.put("sequence", entry.event().sequence()); row.put("timestamp", entry.event().timestamp().toString());
                row.put("runtimeSourceId", entry.event().runtimeSourceId());
                row.put("semanticId", entry.event().semanticSourceId());
                row.put("useTarget", trace.byRuntimeKey(entry.event().runtimeSourceId()).map(TraceRecord::targetUseId).orElse(null));
                row.put("mappingRule", runtimeMapping.select(entry.event()).id());
                row.put("action", runtimeMapping.select(entry.event()).action().name());
                row.put("disposition", entry.disposition()); row.put("diagnostic", entry.diagnostic());
                return row;
            }).toList();
            evidenceJson.writerWithDefaultPrettyPrinter().writeValue(mirrorEvidence.resolve("trace.json").toFile(), decisions);
            evidenceJson.writerWithDefaultPrettyPrinter().writeValue(mirrorEvidence.resolve("mirror-correctness.json").toFile(), Map.of(
                "status", "SUPPORTED_SUBSET_COMPLETE", "fixtureSourceCommit", sourceCommit,
                "checkpoints", List.of("INITIAL_SYNC", "AFTER_STATE_CHANGE", "OPERATION_ENTER_EXIT_FAIL", "RECONNECT_RESYNC"),
                "unexplainedDrift", reconnectComparison.differences().size(), "failed", scenarioMetrics.failed(),
                "rejected", scenarioMetrics.rejected(), "dropped", scenarioMetrics.dropped(),
                "excluded", List.of("Moise instance state and Jason beliefs/goals are trace-only", "Global causal ordering and cross-dimensional invocation join unproven", "Standalone launcher not covered by this test")));
            Map<String,String> hashes = new java.util.TreeMap<>();
            try (var artifacts = Files.list(mirrorEvidence)) {
                for (Path path : artifacts.filter(Files::isRegularFile).toList())
                    if (!path.getFileName().toString().equals("manifest.json"))
                        hashes.put(path.getFileName().toString(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
            }
            hashes.put("runtimeMapping", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                RuntimeMappingLoader.resource("jacamo-use-runtime-mapping-v2.json"))));
            evidenceJson.writerWithDefaultPrettyPrinter().writeValue(mirrorEvidence.resolve("manifest.json").toFile(),hashes);
            assertBalancedLifecycleForInvalidAmount(scenarioEvents);
            var scenario = new ObjectMapper().readTree(
                    Path.of("target/phase35-auction-evidence/runtime/scenario-summary.json").toFile());
            assertEquals(new ObjectMapper().readTree(Path.of("compatibility.json").toFile()).path("plugin").path("version").asText(), scenario.path("pluginVersion").asText(),
                    "runtime evidence must identify the plugin release, not the USE parent Maven version");
            assertEquals(0, scenario.path("reconnectDriftDifferenceCount").asInt(-1),
                    "R5 evidence must record a full authoritative snapshot comparison after reconnect");
            assertEquals(2, scenario.path("synchronizations").size(),
                    "R5 evidence must retain both initial and reconnect snapshot provenance");
            assertEquals(0, scenario.path("failedEvents").asInt(-1),
                    "an event may be processed but still fail during USE mutation");
            assertEquals(0, scenario.path("rejectedEvents").asInt(-1),
                    "the scenario must not silently reject a received runtime event");
            assertEquals("PROGRAMMATIC_REAL_MOISE_API_SUBSET_NOT_LOADED_FROM_STATIC_XML",
                    scenario.path("moiseRuntimeProvenance").asText());
            assertEquals("STATIC_IMPORT_PROVENANCE_ONLY", scenario.path("moiseXmlRole").asText());
            assertFalse(scenario.path("assumptions").isEmpty());
            assertFalse(scenario.path("limitations").isEmpty());
        } finally {
            mirror.close();
            environment.getController("/main").removeArtifact("auction1");
            sourceRuntime.close();
        }
    }

    private List<RuntimeVerificationReport> reportsAfter(RuntimeVerificationEngine verifier, int checkpoint) {
        List<RuntimeVerificationReport> reports = verifier.reports();
        return List.copyOf(reports.subList(checkpoint, reports.size()));
    }

    private boolean hasFailure(List<RuntimeVerificationReport> reports, ConstraintRegistry registry, String name) {
        return reports.stream().flatMap(report -> report.verification().results().stream())
                .anyMatch(result -> result.outcome() == VerificationOutcome.FAIL
                        && registry.byId(result.constraintId()) != null
                        && name.equals(registry.byId(result.constraintId()).name()));
    }

    private void assertBalancedLifecycleForInvalidAmount(List<RuntimeEvent> events) {
        RuntimeEvent enter = events.stream().filter(event -> event.kind() == RuntimeEventKind.OP_ENTER
                        && "placeBid".equals(event.payload().get("operation"))
                        && List.of("item1", 0).equals(event.payload().get("arguments")))
                .findFirst().orElseThrow();
        List<RuntimeEvent> terminals = events.stream().filter(event -> enter.correlationId().equals(event.correlationId())
                        && (event.kind() == RuntimeEventKind.OP_EXIT || event.kind() == RuntimeEventKind.OP_FAIL))
                .toList();
        assertEquals(1, terminals.size(), "invalid amount must have exactly one terminal runtime event");
        assertEquals(RuntimeEventKind.OP_FAIL, terminals.get(0).kind(),
                "the source operation body must fail invalid amount after the lifecycle starts");
    }

    private void writeRuntimeEvidence(Path output, List<RuntimeVerificationReport> reports,
                                      List<RuntimeEvent> authoritativeEvents,
                                      ConstraintRegistry registry, QueueMetrics metrics,
                                      RuntimeDriftReport reconnectComparison, List<RuntimeSnapshot> synchronizations,
                                      List<RuntimeVerificationReport> validBid,
                                      List<RuntimeVerificationReport> invalidAmount,
                                      List<RuntimeVerificationReport> closedBid, Path project,
                                      String artifactRuntimeClass, String sourceCommit) throws Exception {
        Files.createDirectories(output);
        assertEquals(2, synchronizations.size(), "initial connect and reconnect must each apply a full snapshot");
        assertEquals(metrics.processed(), authoritativeEvents.size(),
                "event evidence must come from the complete authoritative runtime stream");
        assertEquals(13, authoritativeEvents.size(), "the evidence scenario must preserve all 13 runtime events");
        new RuntimeEventCodec().writeEvents(output.resolve("event-log.json"), authoritativeEvents);

        ObjectMapper json = new ObjectMapper();
        var reportDocument = json.createObjectNode().put("schemaVersion", "1.0.0");
        var reportArray = reportDocument.putArray("reports");
        RuntimeVerificationReportExporter exporter = new RuntimeVerificationReportExporter();
        for (RuntimeVerificationReport report : reports) reportArray.add(json.readTree(exporter.toJson(report)));
        Files.writeString(output.resolve("verification-reports.json"),
                EvidenceNormalizer.normalizeJson(
                        json.writerWithDefaultPrettyPrinter().writeValueAsString(reportDocument) + "\n",
                        project, "<auction>"));

        var summary = json.createObjectNode().put("schemaVersion", "1.0.0")
                .put("artifactKind", "V2_FROZEN_RUNTIME_SCENARIO_SUMMARY")
                .put("hashPolicy", "LF_NORMALIZED_UTF8")
                .put("repositoryBaseCommit", sourceCommit)
                .put("pluginVersion", json.readTree(Path.of("compatibility.json").toFile()).path("plugin").path("version").asText())
                .put("useVersion", "7.5.0")
                .put("auctionArtifactRuntimeClass", artifactRuntimeClass)
                .put("auctionArtifactSource", "<auction>/src/env/auction/AuctionArtifact.java")
                .put("auctionArtifactSourceSha256", sha256(project.resolve("src/env/auction/AuctionArtifact.java")))
                .put("jasonRuntimeSource", "<auction>/src/agt/auctioneer.asl")
                .put("jasonSourceSha256", sha256(project.resolve("src/agt/auctioneer.asl")))
                .put("moiseRuntimeProvenance", "PROGRAMMATIC_REAL_MOISE_API_SUBSET_NOT_LOADED_FROM_STATIC_XML")
                .put("moiseXmlRole", "STATIC_IMPORT_PROVENANCE_ONLY")
                .put("moiseSource", "<auction>/src/org/auction.xml")
                .put("moiseSourceSha256", sha256(project.resolve("src/org/auction.xml")))
                .put("jasonVersion", "3.3.0").put("cartagoVersion", "3.1").put("moiseVersion", "1.1")
                .put("validBid", hasFailure(validBid, registry, "Guard_canBid")
                        || hasFailure(validBid, registry, "AuctionOpenForBid") ? "UNEXPECTED_FAIL" : "PASS")
                .put("invalidAmount", hasFailure(invalidAmount, registry, "PositiveBidAmount")
                        ? "EXPECTED_CASE_PRECONDITION_FAIL" : "MISSING_FAIL")
                .put("closedAuctionBid", hasFailure(closedBid, registry, "AuctionOpenForBid")
                        ? "EXPECTED_CASE_PRECONDITION_FAIL" : "MISSING_FAIL")
                .put("reconnectResync", reconnectComparison.drifted() ? "DRIFT_REMAINS" : "PASS")
                .put("processedEvents", metrics.processed()).put("droppedEvents", metrics.dropped())
                .put("failedEvents", metrics.failed()).put("rejectedEvents", metrics.rejected())
                .put("eventEvidenceCount", authoritativeEvents.size()).put("runtimeReportCount", reports.size())
                .put("resyncFingerprint", synchronizations.get(1).fingerprint())
                .put("reconnectComparedSnapshotId", reconnectComparison.snapshotId())
                .put("reconnectComparedFingerprint", reconnectComparison.fingerprint())
                .put("reconnectDriftDifferenceCount", reconnectComparison.differences().size())
                .put("eventLogSha256", sha256(output.resolve("event-log.json")))
                .put("verificationReportsSha256", sha256(output.resolve("verification-reports.json")));
        var snapshotArray = summary.putArray("synchronizations");
        for (int index = 0; index < synchronizations.size(); index++) {
            RuntimeSnapshot snapshot = synchronizations.get(index);
            snapshotArray.addObject().put("phase", index == 0 ? "INITIAL_CONNECT" : "RECONNECT")
                    .put("snapshotId", snapshot.snapshotId())
                    .put("fingerprint", snapshot.fingerprint())
                    .put("mutationCount", snapshot.mutations().size());
        }
        Path verificationProfile = Path.of("src/main/resources/org/tzi/use/plugins/jacamo/verification/"
                + "jacamo-verification-profile-v2.json");
        summary.putObject("verificationProfile")
                .put("path", "use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/verification/"
                        + "jacamo-verification-profile-v2.json")
                .put("sha256", sha256(verificationProfile));
        summary.putObject("runtimeBindings")
                .put("auctioneer", "jason:agent:auctioneer")
                .put("auction1", "cartago:artifact:main/auction1")
                .put("auction_org", "moise:organisation:auction_org")
                .put("auction_group", "moise:group:auction_org/auction_group")
                .put("auction_scheme", "moise:scheme:auction_org/auction_scheme");
        summary.putArray("assumptions")
                .add("Runtime verification observes JaCaMo and does not block agent actions")
                .add("AuctionOpenForBid is an authored case-study policy; the Artifact body does not reject closed-auction bids")
                .add("PositiveBidAmount mirrors the explicit source body failure for zero after the non-negative guard admits the operation");
        summary.putArray("limitations")
                .add("The real Moise OS and OE API subset is constructed programmatically; the checked-in XML is not loaded as runtime semantics")
                .add("Communication links, formation cardinality, sequence plans, normative time constraints, and norm lifecycle are not executed or claimed")
                .add("Runtime timestamps and report UUIDs are intentionally nondeterministic");
        summary.putObject("provenance")
                .put("cartagoLifecycle", "OFFICIAL_LOGGER_OP_STARTED_OP_COMPLETED_OP_FAILED")
                .put("invalidAmount", "CHECKED_IN_ARTIFACT_BODY_FAILED_BOUNDARY")
                .put("closedAuctionBid", "AUTHORED_CASE_STUDY_POLICY")
                .put("moiseRuntime", "PROGRAMMATIC_REAL_API_SUBSET")
                .put("moiseStaticXml", "HASHED_STATIC_IMPORT_SOURCE_ONLY");
        Files.writeString(output.resolve("scenario-summary.json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(summary) + "\n");
    }

    private String sha256(Path path) throws Exception {
        byte[] normalized = Files.readString(path).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized));
    }


    private static final class EvidenceEventObserver implements RuntimeEventObserver {
        private final RuntimeEventObserver delegate;
        private final CopyOnWriteArrayList<RuntimeEvent> events = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<RuntimeSnapshot> snapshots = new CopyOnWriteArrayList<>();

        private EvidenceEventObserver(RuntimeEventObserver delegate) { this.delegate = delegate; }
        private List<RuntimeEvent> events() { return List.copyOf(events); }
        private List<RuntimeSnapshot> snapshots() { return List.copyOf(snapshots); }

        @Override public void stateChanged(MirrorState state) { delegate.stateChanged(state); }
        @Override public void snapshotApplied(RuntimeSnapshot snapshot) {
            snapshots.add(snapshot);
            delegate.snapshotApplied(snapshot);
        }
        @Override public void eventReceived(RuntimeEvent event) {
            events.add(event);
            delegate.eventReceived(event);
        }
        @Override public void eventRejected(RuntimeEvent event, RuntimeException reason) {
            delegate.eventRejected(event, reason);
        }
        @Override public void beforeMutation(RuntimeEvent event) { delegate.beforeMutation(event); }
        @Override public void afterMutation(RuntimeEvent event, MutationResult result) {
            delegate.afterMutation(event, result);
        }
        @Override public void eventCompleted(RuntimeEvent event) { delegate.eventCompleted(event); }
        @Override public void eventStreamClosed() { delegate.eventStreamClosed(); }
        @Override public void driftChecked(RuntimeDriftReport report) { delegate.driftChecked(report); }
    }
    private String semanticId(JaCaMoSemanticModel model, MetamodelKind kind, String name) {
        return model.elements().stream().filter(element -> element.kind() == kind && name.equals(element.name()))
                .map(element -> element.id().value()).findFirst().orElseThrow();
    }

    private void register(TraceIndex trace, String semanticId, String runtimeKey) {
        TraceRecord record = trace.bySemanticId(semanticId).stream()
                .filter(value -> "OBJECT".equals(value.targetKind())).findFirst().orElseThrow();
        trace.registerRuntimeKey(record.traceId(), runtimeKey);
    }

    private boolean openValue(DirectUseBackend.Result direct, TraceIndex trace, String artifactSemantic) {
        return ((BooleanValue) attributeValue(direct, trace, artifactSemantic, "open")).value();
    }

    private Object attributeValue(DirectUseBackend.Result direct, TraceIndex trace, String artifactSemantic,
                                  String attribute) {
        TraceRecord record = trace.bySemanticId(artifactSemantic).stream()
                .filter(value -> "OBJECT".equals(value.targetKind())).findFirst().orElseThrow();
        var object = direct.system().state().objectByName(record.targetUseId().substring("object:".length()));
        return object.state(direct.system().state()).attributeValue(object.cls().attribute(attribute, true));
    }
}
