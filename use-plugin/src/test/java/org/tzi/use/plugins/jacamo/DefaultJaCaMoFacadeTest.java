package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.runtime.MirrorState;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEventCodec;
import org.tzi.use.plugins.jacamo.runtime.RuntimeSnapshot;
import org.tzi.use.plugins.jacamo.runtime.SyntheticRuntimeConnector;

class DefaultJaCaMoFacadeTest {
    @TempDir Path temporary;

    @Test
    void realAuctionImportExposesPipelineSummaryTraceDiagnosticsAndVerification() {
        try (DefaultJaCaMoFacade facade = new DefaultJaCaMoFacade(Path.of("."))) {
            JaCaMoFacade.ProjectSummary summary = facade.importProject(Path.of("src/test/resources/auction/auction.jcm"));

            assertEquals("auction", summary.projectId());
            assertEquals("FROZEN", summary.mappingStatus());
            assertTrue(summary.structureValid());
            assertEquals(38, summary.generatedClasses());
            assertTrue(summary.generatedObjects() > 10);
            assertFalse(facade.sources().isEmpty());
            assertFalse(facade.traces().isEmpty());
            assertFalse(facade.constraints().isEmpty());
            assertFalse(facade.latestVerification().results().isEmpty());
        }
    }

    @Test
    void runtimeDashboardStatusComesFromTheRealMirrorService() throws Exception {
        try (DefaultJaCaMoFacade facade = new DefaultJaCaMoFacade(Path.of("."))) {
            facade.importProject(Path.of("src/test/resources/auction/auction.jcm"));
            Path replay = Files.writeString(temporary.resolve("events.json"), "[]");
            RuntimeSnapshot snapshot = new RuntimeSnapshot("snapshot-0", Instant.parse("2026-09-15T09:00:00Z"),
                    0, List.of(), "fingerprint-0");
            SyntheticRuntimeConnector connector = new SyntheticRuntimeConnector("ui-smoke", snapshot, replay,
                    new RuntimeEventCodec());
            facade.configureRuntime(connector, URI.create("synthetic://auction"), 8);

            facade.connectRuntime();
            JaCaMoFacade.RuntimeStatus live = facade.runtimeStatus();
            assertEquals(MirrorState.LIVE, live.state());
            assertEquals(Instant.parse("2026-09-15T09:00:00Z"), live.lastSync());
            facade.disconnectRuntime();
            assertEquals(MirrorState.STALE, facade.runtimeStatus().state());
        }
    }

    @Test
    void bindingPersistenceRejectsAnyTargetOutsideTheExactCandidateSet() {
        try (DefaultJaCaMoFacade facade = new DefaultJaCaMoFacade(Path.of("."))) {
            facade.importProject(Path.of("src/test/resources/auction/auction.jcm"));
            String source = "jacamo:auction:AGENT:ExternalAction:buyer:bid";
            String target = "jacamo:auction:ENVIRONMENT:Operation:auction:bid";
            JaCaMoFacade.BindingRequest request = new JaCaMoFacade.BindingRequest(source, "buyer", "ExternalAction",
                    Path.of("buyer.asl"), "a".repeat(64), List.of(new JaCaMoFacade.BindingCandidate(target, "auction",
                    "Operation", Path.of("AuctionArtifact.java"))));

            assertThrows(IllegalArgumentException.class, () -> facade.persistBinding(
                    temporary.resolve("invalid.json"), request, target + "-fuzzy", "manual"));
            Path output = temporary.resolve("binding.json");
            facade.persistBinding(output, request, target, "manual exact selection");
            assertTrue(Files.isRegularFile(output));
        }
    }

    @Test
    void reportExportUsesTheRealLatestVerification() throws Exception {
        try (DefaultJaCaMoFacade facade = new DefaultJaCaMoFacade(Path.of("."))) {
            facade.importProject(Path.of("src/test/resources/auction/auction.jcm"));
            Path report = temporary.resolve("report.json");
            facade.exportVerificationReport(report);
            String json = Files.readString(report);
            assertTrue(json.contains("\"schemaVersion\""));
            assertTrue(json.contains("\"results\""));
        }
    }

    @Test
    void failedImportPreservesActionableDiagnosticsFromTheRealImporter() throws Exception {
        Path malformed = Files.writeString(temporary.resolve("broken.jcm"), "this is not a JaCaMo project");
        try (DefaultJaCaMoFacade facade = new DefaultJaCaMoFacade(Path.of("."))) {
            assertThrows(IllegalArgumentException.class, () -> facade.importProject(malformed));
            assertFalse(facade.diagnostics().isEmpty());
            assertTrue(facade.diagnostics().stream().allMatch(value -> value.code() != null
                    && value.phase() != null && !value.remediation().isBlank()));
        }
    }

    @Test
    void repeatedExplicitBindingsPreserveEarlierExactSelections() throws Exception {
        try (DefaultJaCaMoFacade facade = new DefaultJaCaMoFacade(Path.of("."))) {
            facade.importProject(Path.of("src/test/resources/auction/auction.jcm"));
            Path output = temporary.resolve("binding.json");
            String hash = "b".repeat(64);
            String sourceA = "jacamo:auction:AGENT:ExternalAction:buyer:bid";
            String targetA = "jacamo:auction:ENVIRONMENT:Operation:auction:bid";
            String sourceB = "jacamo:auction:AGENT:ExternalAction:seller:close";
            String targetB = "jacamo:auction:ENVIRONMENT:Operation:auction:close";
            JaCaMoFacade.BindingRequest first = new JaCaMoFacade.BindingRequest(sourceA, "buyer", "ExternalAction",
                    Path.of("buyer.asl"), hash, List.of(new JaCaMoFacade.BindingCandidate(targetA, "auction",
                    "Operation", Path.of("AuctionArtifact.java"))));
            JaCaMoFacade.BindingRequest second = new JaCaMoFacade.BindingRequest(sourceB, "seller", "ExternalAction",
                    Path.of("seller.asl"), hash, List.of(new JaCaMoFacade.BindingCandidate(targetB, "auction",
                    "Operation", Path.of("AuctionArtifact.java"))));

            facade.persistBinding(output, first, targetA, "first exact choice");
            facade.persistBinding(output, second, targetB, "second exact choice");

            String json = Files.readString(output);
            assertTrue(json.contains(targetA));
            assertTrue(json.contains(targetB));
        }
    }
}
