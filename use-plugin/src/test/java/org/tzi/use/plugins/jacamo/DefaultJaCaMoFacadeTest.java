package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.runtime.MirrorState;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;

class DefaultJaCaMoFacadeTest {
    @TempDir Path temporary;

    @Test
    void originalAuctionBridgeImportExposesPipelineSummaryTraceDiagnosticsAndVerification() {
        try (DefaultJaCaMoFacade facade = bridgeAuction()) {
            JaCaMoFacade.ProjectSummary summary = facade.importProject(auction());

            assertEquals("auction", summary.projectId());
            assertEquals("FROZEN", summary.mappingStatus());
            assertTrue(summary.structureValid());
            assertEquals(new org.tzi.use.plugins.jacamo.mapping.MappingLoader().loadCanonical(Path.of(".")).classes().size(),
                    summary.generatedClasses(), "official Bridge path must not add a parser-derived synthetic class");
            assertTrue(summary.generatedObjects() > 10);
            assertFalse(facade.sources().isEmpty());
            assertFalse(facade.traces().isEmpty());
            assertFalse(facade.constraints().isEmpty());
            assertFalse(facade.latestVerification().results().isEmpty());
        }
    }

    @Test
    void runtimeDashboardStatusComesFromTheAuthoritativeBridge() {
        try (DefaultJaCaMoFacade facade = bridgeAuction()) {
            facade.importProject(auction());
            JaCaMoFacade.RuntimeStatus live = facade.runtimeStatus();
            assertEquals(MirrorState.LIVE, live.state());
            facade.disconnectRuntime();
            assertEquals(MirrorState.STALE, facade.runtimeStatus().state());
            facade.connectRuntime();
            assertEquals(MirrorState.LIVE, facade.runtimeStatus().state());
        }
    }

    @Test
    void bindingPersistenceRejectsAnyTargetOutsideTheExactCandidateSet() {
        try (DefaultJaCaMoFacade facade = bridgeAuction()) {
            facade.importProject(auction());
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
        try (DefaultJaCaMoFacade facade = bridgeAuction()) {
            facade.importProject(auction());
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
        var imported = new StaticProjectImporter().importProject(malformed);
        assertFalse(imported.success());
        assertFalse(imported.diagnostics().isEmpty());
        assertTrue(imported.diagnostics().stream().allMatch(value -> value.code() != null
                    && value.phase() != null && !value.remediation().isBlank()));
    }

    @Test
    void repeatedExplicitBindingsPreserveEarlierExactSelections() throws Exception {
        try (DefaultJaCaMoFacade facade = bridgeAuction()) {
            facade.importProject(auction());
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
        try (DefaultJaCaMoFacade facade = bridgeAuction()) {
            facade.importProject(auction());
            Path unsafe = temporary.resolve("report.txt");
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> facade.exportVerificationReport(unsafe));
            assertTrue(error.getMessage().contains("REPORT_EXPORT_EXTENSION"));
            assertFalse(Files.exists(unsafe));
        }
    }

    @Test
    void reportExportSupportsMarkdownDestinations() throws Exception {
        try (DefaultJaCaMoFacade facade = bridgeAuction()) {
            facade.importProject(auction());
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
        try (DefaultJaCaMoFacade facade = bridgeAuction()) {
            facade.importProject(auction());

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> facade.exportVerificationReport(linked.resolve("report.json")));

            assertTrue(error.getMessage().contains("REPORT_EXPORT_SYMLINK"));
            assertTrue(error.getMessage().contains("choose a non-linked destination"));
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
        try (DefaultJaCaMoFacade facade = bridgeAuction()) {
            facade.importProject(auction());
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

    private DefaultJaCaMoFacade bridgeAuction() {
        return BridgeFacadeTestSupport.facade(auction());
    }

    private Path auction() {
        return Path.of("..", "..", "JaCaMo", "examples", "auction", "auction.jcm")
                .toAbsolutePath().normalize();
    }
}
