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
            assertEquals("NATIVE_CURRENT", summary.mappingStatus());
            assertTrue(summary.structureValid());
            assertEquals(facade.materializedSystem().model().classes().size(), summary.generatedClasses());
            assertTrue(summary.generatedObjects() > 10);
            assertFalse(facade.sources().isEmpty());
            assertFalse(facade.traces().isEmpty());
            assertFalse(facade.constraints().isEmpty());
            assertFalse(facade.latestVerification().results().isEmpty());
            JaCaMoFacade.FormalStateStatus formal = facade.formalStateStatus();
            assertTrue(formal.classCount() >= summary.generatedClasses());
            assertEquals(summary.generatedObjects(), formal.objectCount());
            assertTrue(formal.associationCount() > 0);
            assertTrue(formal.linkCount() > 0);
            assertEquals(64, formal.sha256().length());
        }
    }

    @Test
    void packagedGuiRootUsesClasspathBaselineDuringBridgeImport() {
        Path stagedGuiRoot = temporary.resolve("use-gui-demo");

        try (DefaultJaCaMoFacade facade = BridgeFacadeTestSupport.facade(auction(), stagedGuiRoot)) {
            JaCaMoFacade.ProjectSummary summary = facade.importProject(auction());

            assertEquals("NATIVE_CURRENT", summary.mappingStatus());
            assertTrue(summary.structureValid());
            assertTrue(summary.generatedClasses() > 0);
            assertTrue(summary.generatedObjects() > 0);
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
            assertTrue(metrics.runtimeLastLatencyNanos() > 0);
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
