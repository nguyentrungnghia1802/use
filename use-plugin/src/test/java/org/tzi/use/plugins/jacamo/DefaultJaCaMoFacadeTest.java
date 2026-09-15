package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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

    @Test
    void exportRejectsUnsupportedDestinationsInsteadOfGuessingAFormat() {
        try (DefaultJaCaMoFacade facade = new DefaultJaCaMoFacade(Path.of("."))) {
            facade.importProject(Path.of("src/test/resources/auction/auction.jcm"));
            Path unsafe = temporary.resolve("report.txt");
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> facade.exportVerificationReport(unsafe));
            assertTrue(error.getMessage().contains("REPORT_EXPORT_EXTENSION"));
            assertFalse(Files.exists(unsafe));
        }
    }

    @Test
    void reportExportSupportsMarkdownDestinations() throws Exception {
        try (DefaultJaCaMoFacade facade = new DefaultJaCaMoFacade(Path.of("."))) {
            facade.importProject(Path.of("src/test/resources/auction/auction.jcm"));
            Path report = temporary.resolve("report.md");

            facade.exportVerificationReport(report);

            String markdown = Files.readString(report);
            assertTrue(markdown.startsWith("# Verification Report"));
            assertTrue(markdown.contains("| Constraint | Outcome | Context |"));
        }
    }

    @Test
    void reportExportRejectsLinkedDirectoryDestinations() throws Exception {
        Path selectedRoot = Files.createDirectory(temporary.resolve("selected"));
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path linked = PathLinkSupport.createDirectoryLink(selectedRoot.resolve("linked"), outside);
        try (DefaultJaCaMoFacade facade = new DefaultJaCaMoFacade(Path.of("."))) {
            facade.importProject(Path.of("src/test/resources/auction/auction.jcm"));

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> facade.exportVerificationReport(linked.resolve("report.json")));

            assertTrue(error.getMessage().contains("REPORT_EXPORT_SYMLINK"));
            assertFalse(Files.exists(outside.resolve("report.json")));
        }
    }

    @Test
    void reportCleanupFailureIsAttachedToThePrimaryExportFailure() throws Exception {
        Path nonEmptyTemporary = Files.createDirectory(temporary.resolve("non-empty.tmp"));
        Files.writeString(nonEmptyTemporary.resolve("child"), "prevents directory deletion");
        IOException primary = new IOException("primary write failure");

        Exception cleanup = DefaultJaCaMoFacade.cleanupTemporaryReport(nonEmptyTemporary, primary);

        assertEquals(cleanup, primary.getSuppressed()[0]);
        assertTrue(cleanup.getMessage().contains("non-empty.tmp"));
    }

    @Test
    void auctionPerformanceMetricsAreMeasuredWithoutFlakyWallClockThresholds() {
        try (DefaultJaCaMoFacade facade = new DefaultJaCaMoFacade(Path.of("."))) {
            facade.importProject(Path.of("src/test/resources/auction/auction.jcm"));
            facade.runFullVerification();

            JaCaMoFacade.PerformanceMetrics metrics = facade.performanceMetrics();
            System.out.printf("PHASE13_PERFORMANCE import=%dns generation=%dns fullCheck=%dns memory=%dB maxMemory=%dB%n",
                    metrics.importNanos(), metrics.generationNanos(), metrics.fullCheckNanos(),
                    metrics.usedMemoryBytes(), Runtime.getRuntime().maxMemory());
            assertTrue(metrics.importNanos() > 0);
            assertTrue(metrics.generationNanos() > 0);
            assertTrue(metrics.fullCheckNanos() > 0);
            assertTrue(metrics.usedMemoryBytes() > 0);
            assertTrue(metrics.usedMemoryBytes() <= Runtime.getRuntime().maxMemory());
            assertEquals(0, metrics.runtimeLastLatencyNanos());
        }
    }
}
