package org.tzi.use.plugins.jacamo.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.SemanticAuthority;
import org.tzi.use.plugins.jacamo.bridge.BridgeClientState;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.runtime.MirrorState;
import org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.plugins.jacamo.verification.VerificationReport;
import org.tzi.use.plugins.jacamo.verification.VerificationResult;

class JaCaMoWorkbenchPanelTest {
    @Test
    void projectChooserStartsAtAndSelectsTheDerivedJcmHint(@TempDir Path temporaryDirectory) throws Exception {
        Path derivedJcm = temporaryDirectory.resolve("helloworld.jcm");
        Files.writeString(derivedJcm, "project helloworld");

        JFileChooser chooser = JaCaMoWorkbenchPanel.createProjectChooser(derivedJcm);

        assertEquals(temporaryDirectory.toAbsolutePath().normalize(),
                chooser.getCurrentDirectory().toPath().toAbsolutePath().normalize());
        assertEquals(derivedJcm.toAbsolutePath().normalize(),
                chooser.getSelectedFile().toPath().toAbsolutePath().normalize());
    }

    @Test
    void importRefreshesOverviewTraceDiagnosticsAndVerificationFromFacade() {
        RecordingFacade facade = new RecordingFacade();
        JaCaMoWorkbenchPanel panel = new JaCaMoWorkbenchPanel(facade, ignored -> { });

        panel.importProject(Path.of("auction.jcm"));

        assertEquals("auction", label(panel, "project-id").getText());
        assertEquals("V2 | sha256=" + "a".repeat(64), label(panel, "metamodel-baseline").getText());
        assertEquals("JaCaMo-agentmetamodel-v2__to__USE-v2.2 | schema=2.2.0 | FROZEN"
                + " | sha256=" + "b".repeat(64), label(panel, "mapping-status").getText());
        assertTrue(label(panel, "dimension-counts").getText().contains("AGENT=4"));
        assertEquals(1, table(panel, "sources-table").getRowCount());
        assertEquals(1, table(panel, "trace-table").getRowCount());
        assertEquals(List.of("Rule", "Source", "Target", "Fidelity", "Status"),
                java.util.stream.IntStream.range(0, table(panel, "trace-table").getColumnCount())
                        .mapToObj(index -> table(panel, "trace-table").getColumnName(index)).toList());
        assertEquals(1, table(panel, "diagnostics-table").getRowCount());
        assertEquals(1, table(panel, "verification-table").getRowCount());
        assertEquals(Path.of("auction.jcm").toAbsolutePath().normalize(), facade.imported);
    }

    @Test
    void interactiveImportBuildsOffEdtAndPublishesUiOnEdt() throws Exception {
        RecordingFacade facade = new RecordingFacade();
        JaCaMoWorkbenchPanel panel = new JaCaMoWorkbenchPanel(facade, ignored -> { });
        javax.swing.SwingUtilities.invokeAndWait(() -> panel.importProject(Path.of("auction.jcm")));
        assertTrue(facade.importedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(facade.publishedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertFalse(facade.importedOnEdt);
        assertEquals("auction", label(panel, "project-id").getText());
    }

    @Test
    void oneShotLauncherPropertyAutoImportsTheSingleJcmAndLeavesChooserAsFallback() throws Exception {
        String previousPath = System.getProperty(JaCaMoWorkbenchPanel.PROJECT_FILE_HINT_PROPERTY);
        String previousAuto = System.getProperty(JaCaMoWorkbenchPanel.AUTO_IMPORT_PROPERTY);
        String previousReady = System.getProperty(JaCaMoWorkbenchPanel.READY_FILE_PROPERTY);
        Path staged = Path.of("C:/temp/jacamo-staged/auction.jcm").toAbsolutePath().normalize();
        Path ready = Files.createTempDirectory("jacamo-workbench-ready-").resolve("ready.json");
        try {
            System.setProperty(JaCaMoWorkbenchPanel.PROJECT_FILE_HINT_PROPERTY, staged.toString());
            System.setProperty(JaCaMoWorkbenchPanel.AUTO_IMPORT_PROPERTY, "true");
            System.setProperty(JaCaMoWorkbenchPanel.READY_FILE_PROPERTY, ready.toString());
            RecordingFacade facade = new RecordingFacade();
            new JaCaMoWorkbenchPanel(facade, ignored -> { });

            assertTrue(facade.importedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(staged, facade.imported);
            assertNull(System.getProperty(JaCaMoWorkbenchPanel.AUTO_IMPORT_PROPERTY));
            assertNull(System.getProperty(JaCaMoWorkbenchPanel.READY_FILE_PROPERTY));
            assertTrue(Files.readString(ready).contains("\"status\":\"READY\""));
            assertTrue(Files.readString(ready).contains("\"projectFile\":"));
        } finally {
            restoreProperty(JaCaMoWorkbenchPanel.PROJECT_FILE_HINT_PROPERTY, previousPath);
            restoreProperty(JaCaMoWorkbenchPanel.AUTO_IMPORT_PROPERTY, previousAuto);
            restoreProperty(JaCaMoWorkbenchPanel.READY_FILE_PROPERTY, previousReady);
        }
    }

    @Test
    void traceFiltersAreExactAndFidelityRemainsVisible() {
        RecordingFacade facade = new RecordingFacade();
        facade.traces = List.of(
                new JaCaMoFacade.TraceRow("agent-id", "Goal", "object:g", "OBJECT", "M001", "VP006",
                        "PROJECTED", Path.of("agent.asl"), 7, "AGENT"),
                new JaCaMoFacade.TraceRow("env-id", "Artifact", "object:a", "OBJECT", "M002", "VP001",
                        "RESOLVED", Path.of("Auction.java"), 9, "ENVIRONMENT"));
        JaCaMoWorkbenchPanel panel = new JaCaMoWorkbenchPanel(facade);
        panel.importProject(Path.of("auction.jcm"));

        combo(panel, "trace-dimension-filter").setSelectedItem("AGENT");
        combo(panel, "trace-status-filter").setSelectedItem("PROJECTED");

        JTable table = table(panel, "trace-table");
        assertEquals(1, table.getRowCount());
        assertEquals("VP006", table.getValueAt(0, 3));
        table.setRowSelectionInterval(0, 0);
        assertTrue(label(panel, "source-location").getText().endsWith("agent.asl:7"));
        assertTrue(button(panel, "copy-source").isEnabled());
    }

    @Test
    void mappingInspectorShowsAllCatalogRulesAndRequiredStatusFilters() {
        RecordingFacade facade = new RecordingFacade();
        facade.traces = new CodeGroundedRuleCatalog().rules().stream().map(rule -> new JaCaMoFacade.TraceRow(
                rule.ruleId(), rule.sourceKindFqcn(), "association:" + rule.ruleId(), "ASSOCIATION",
                rule.ruleId(), rule.fidelity().name(), rule.capabilityStatus().name(),
                Path.of("mapping.java"), 1, rule.dimension().name(), rule.sourceAuthority().name(),
                List.of(rule.diagnosticPolicy()))).toList();
        JaCaMoWorkbenchPanel panel = new JaCaMoWorkbenchPanel(facade);
        panel.importProject(Path.of("auction.jcm"));

        assertEquals(105, table(panel, "trace-table").getRowCount());
        for (String value : List.of("J", "A", "C", "M", "X"))
            assertTrue(comboContains(combo(panel, "trace-dimension-filter"), value), value);
        for (String value : List.of("APPLIED", "UNRESOLVED", "UNAVAILABLE", "UNSUPPORTED"))
            assertTrue(comboContains(combo(panel, "trace-status-filter"), value), value);
    }

    @Test
    void mappingInspectorDetailShowsFqcnTargetEvidenceFidelityAndDiagnostics() {
        RecordingFacade facade = new RecordingFacade();
        facade.traces = List.of(new JaCaMoFacade.TraceRow("agent-id", "jason.asSemantics.Agent",
                "object:agent_1", "OBJECT", "A06", "EXACT", "APPLIED", Path.of("agent.asl"), 7,
                "A", "OFFICIAL_JASON_API", List.of("UNRESOLVED_UNTIL_X")));
        JaCaMoWorkbenchPanel panel = new JaCaMoWorkbenchPanel(facade);
        panel.importProject(Path.of("auction.jcm"));
        table(panel, "trace-table").setRowSelectionInterval(0, 0);
        String detail = textArea(panel, "mapping-detail").getText();
        assertTrue(detail.contains("jason.asSemantics.Agent"));
        assertTrue(detail.contains("object:agent_1"));
        assertTrue(detail.contains("OFFICIAL_JASON_API"));
        assertTrue(detail.contains("EXACT"));
        assertTrue(detail.contains("UNRESOLVED_UNTIL_X"));
    }

    @Test
    void runtimeRefreshReadsChangingFacadeStatusAndControlsCallTheFacade() {
        RecordingFacade facade = new RecordingFacade();
        JaCaMoWorkbenchPanel panel = new JaCaMoWorkbenchPanel(facade);
        panel.importProject(Path.of("auction.jcm"));
        facade.runtime = new JaCaMoFacade.RuntimeStatus(MirrorState.LIVE, 3, 5, 8, 1, 2, 0,
                Instant.parse("2026-09-15T09:00:00Z"), "event-8", 42, 11, 2);
        facade.authority = new JaCaMoFacade.AuthorityStatus(SemanticAuthority.BRIDGE, BridgeClientState.LIVE,
                Map.of("official.model", "COMPLETE", "runtime.snapshot", "COMPLETE"), "COMPLETE",
                "model-9", "session-9", 9, "tcp://127.0.0.1:6553", "");
        facade.latest = VerificationReport.offline("runtime-event-8", true,
                List.of(new VerificationResult("C-LIVE", VerificationOutcome.FAIL, "object",
                        "live violation", "context C inv: false", List.of("source"), null, List.of())));

        panel.refreshRuntime();
        button(panel, "runtime-disconnect").doClick();
        button(panel, "runtime-resync").doClick();

        assertEquals("LIVE", label(panel, "runtime-state").getText());
        assertEquals("3", label(panel, "runtime-queue-depth").getText());
        assertEquals("event-8", label(panel, "runtime-last-event").getText());
        assertEquals("BRIDGE", label(panel, "semantic-authority").getText());
        assertEquals("LIVE", label(panel, "bridge-readiness").getText());
        assertEquals("COMPLETE", label(panel, "bridge-completeness").getText());
        assertEquals("model-9", label(panel, "bridge-model-revision").getText());
        assertEquals("session-9 / generation=9", label(panel, "bridge-session-generation").getText());
        assertEquals("tcp://127.0.0.1:6553", label(panel, "bridge-endpoint").getText());
        assertEquals(VerificationOutcome.FAIL, table(panel, "verification-table").getValueAt(0, 2));
        assertEquals(1, facade.disconnects);
        assertEquals(1, facade.resyncs);
    }

    @Test
    void verificationSourceTraceNavigatesToTheExactFacadeTraceLocation() {
        JaCaMoWorkbenchPanel panel = new JaCaMoWorkbenchPanel(new RecordingFacade());
        panel.importProject(Path.of("auction.jcm"));

        JTable results = table(panel, "verification-table");
        assertEquals(1, results.getRowCount());
        assertTrue(String.valueOf(results.getValueAt(0, 6)).endsWith("agent.asl:7"));
    }

    @Test
    void bindingPanelNeverPreselectsAndPersistsOnlyTheExplicitCandidate() {
        RecordingFacade facade = new RecordingFacade();
        BindingResolutionPanel panel = new BindingResolutionPanel(facade);
        Path destination = Path.of("binding.json");
        JaCaMoFacade.BindingRequest request = new JaCaMoFacade.BindingRequest("source", "buyer", "ExternalAction",
                Path.of("buyer.asl"), "abc", List.of(
                new JaCaMoFacade.BindingCandidate("target-a", "AuctionA", "Operation", Path.of("A.java")),
                new JaCaMoFacade.BindingCandidate("target-b", "AuctionB", "Operation", Path.of("B.java"))));

        panel.showRequest(destination, request);

        JTable candidates = table(panel, "binding-candidates-table");
        assertEquals(-1, candidates.getSelectedRow(), "ambiguous bindings must never be auto-selected");
        assertFalse(button(panel, "binding-persist").isEnabled());
        assertThrows(IllegalStateException.class, () -> panel.persistSelection("manual evidence"));
        candidates.setRowSelectionInterval(1, 1);
        assertTrue(button(panel, "binding-persist").isEnabled());
        panel.persistSelection("manual evidence");
        assertEquals("target-b", facade.persistedTarget);
        assertEquals("manual evidence", facade.persistedReason);
    }

    @Test
    void workbenchProvidesAllPhaseTwelveViews() {
        JTabbedPane tabs = component(new JaCaMoWorkbenchPanel(new RecordingFacade()), "workbench-tabs", JTabbedPane.class);
        List<String> titles = new ArrayList<>();
        for (int index = 0; index < tabs.getTabCount(); index++) titles.add(tabs.getTitleAt(index));
        assertEquals(List.of("Project", "Mapping Inspector", "Diagnostics", "Verification", "Runtime", "Binding"), titles);
    }

    @Test
    void failedImportStillRefreshesServiceDiagnostics() {
        RecordingFacade facade = new RecordingFacade();
        facade.importFailure = new IllegalArgumentException("IMPORT_FAILED");
        JaCaMoWorkbenchPanel panel = new JaCaMoWorkbenchPanel(facade, ignored -> { });

        panel.importProject(Path.of("broken.jcm"));

        assertEquals(1, table(panel, "diagnostics-table").getRowCount());
        assertTrue(label(panel, "workbench-status").getText().contains("IMPORT_FAILED"));
    }

    @Test
    void longErrorMessagesAreWrappedForStatusAndDialogPresentation() {
        String longError = "BRIDGE_TCP_REQUEST_FAILED:" + "x".repeat(160);
        RecordingFacade facade = new RecordingFacade();
        facade.importFailure = new IllegalArgumentException(longError);
        List<String> presented = new ArrayList<>();
        JaCaMoWorkbenchPanel panel = new JaCaMoWorkbenchPanel(facade, presented::add);

        panel.importProject(Path.of("broken.jcm"));

        assertEquals(1, presented.size());
        assertTrue(presented.get(0).contains(longError));
        assertTrue(JaCaMoWorkbenchPanel.displayMessage(presented.get(0)).contains("<br>"));
        assertTrue(label(panel, "workbench-status").getText().contains("<br>"));
    }

    @Test
    void completeWorkflowDelegatesToFacadeAndKeepsSemanticPipelineOutOfSwing() throws Exception {
        RecordingFacade facade = new RecordingFacade();
        JaCaMoWorkbenchPanel panel = new JaCaMoWorkbenchPanel(facade);
        panel.importProject(Path.of("auction.jcm"));

        panel.rebuildProject();
        panel.loadVerificationProfile(Path.of("case.ocl"));
        panel.runFullVerification();
        panel.exportVerificationReport(Path.of("report.json"));
        panel.exportNativeUse(Path.of("native.use"));
        panel.exportNativeSoil(Path.of("native.cmd"));
        assertTrue(button(panel, "export-use").isEnabled());
        assertTrue(button(panel, "export-soil").isEnabled());
        button(panel, "runtime-connect").doClick();
        button(panel, "runtime-disconnect").doClick();
        button(panel, "runtime-reconnect").doClick();
        button(panel, "runtime-resync").doClick();

        assertEquals(1, facade.rebuilds);
        assertEquals(Path.of("case.ocl"), facade.loadedProfile);
        assertEquals(1, facade.fullChecks);
        assertEquals(Path.of("report.json"), facade.exportedReport);
        assertEquals(Path.of("native.use"), facade.exportedUse);
        assertEquals(Path.of("native.cmd"), facade.exportedSoil);
        assertEquals(2, facade.connects);
        assertEquals(1, facade.disconnects);
        assertEquals(1, facade.resyncs);

        String source = Files.readString(Path.of("src/main/java/org/tzi/use/plugins/jacamo/ui/JaCaMoWorkbenchPanel.java"));
        for (String semanticType : List.of("MappingLoader", "TransformationPlanner", "InstancePlanner",
                "OclGenerator", "RuntimeMutationEngine", "DirectUseBackend")) {
            assertFalse(source.contains(semanticType), semanticType + " must stay behind JaCaMoFacade");
        }
    }

    private static JTable table(Container root, String name) { return component(root, name, JTable.class); }
    private static JLabel label(Container root, String name) { return component(root, name, JLabel.class); }
    private static JTextArea textArea(Container root, String name) { return component(root, name, JTextArea.class); }
    private static JButton button(Container root, String name) { return component(root, name, JButton.class); }
    @SuppressWarnings("unchecked")
    private static JComboBox<String> combo(Container root, String name) {
        return component(root, name, JComboBox.class);
    }
    private static boolean comboContains(JComboBox<String> combo, String value) {
        for (int index = 0; index < combo.getItemCount(); index++)
            if (value.equals(combo.getItemAt(index))) return true;
        return false;
    }
    private static void restoreProperty(String key, String previous) {
        if (previous == null) System.clearProperty(key);
        else System.setProperty(key, previous);
    }
    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T match = componentOrNull(nested, name, type);
                if (match != null) return match;
            }
        }
        throw new AssertionError("Component not found: " + name);
    }
    private static <T extends Component> T componentOrNull(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T match = componentOrNull(nested, name, type);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static final class RecordingFacade implements JaCaMoFacade {
        private Path imported;
        private int disconnects;
        private int resyncs;
        private int rebuilds;
        private int fullChecks;
        private int connects;
        private Path loadedProfile;
        private Path exportedReport;
        private Path exportedUse;
        private Path exportedSoil;
        private String persistedTarget;
        private String persistedReason;
        private RuntimeException importFailure;
        private final java.util.concurrent.CountDownLatch importedLatch = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch publishedLatch = new java.util.concurrent.CountDownLatch(1);
        private boolean importedOnEdt;
        private List<TraceRow> traces = List.of(new TraceRow("source", "Goal", "object:g", "OBJECT", "M001",
                "VP006", "PROJECTED", Path.of("agent.asl"), 7, "AGENT"));
        private RuntimeStatus runtime = RuntimeStatus.offline();
        private AuthorityStatus authority = AuthorityStatus.offline();
        private VerificationReport latest = VerificationReport.offline("run", true,
                List.of(new VerificationResult("C1", VerificationOutcome.PASS, "object", "holds",
                        "context C inv: true", List.of("source"), null, List.of())));

        @Override public String status() { return "ready"; }
        @Override public ProjectSummary importProject(Path jcmFile) {
            imported = jcmFile.toAbsolutePath().normalize();
            importedOnEdt = javax.swing.SwingUtilities.isEventDispatchThread();
            importedLatch.countDown();
            if (importFailure != null) throw importFailure;
            return projectSummary();
        }
        @Override public ProjectSummary projectSummary() {
            if (javax.swing.SwingUtilities.isEventDispatchThread()) publishedLatch.countDown();
            return new ProjectSummary(Path.of("auction.jcm"), Path.of("."), "auction", 1,
                    Map.of("AGENT", 4L), "V2", "a".repeat(64),
                    "JaCaMo-agentmetamodel-v2__to__USE-v2.2", "2.2.0", "b".repeat(64),
                    "FROZEN", 60, 20, true, 1, 0);
        }
        @Override public List<SourceRow> sources() {
            return List.of(new SourceRow(Path.of("auction.jcm"), "JCM", 100, "abc"));
        }
        @Override public List<Diagnostic> diagnostics() {
            return List.of(new Diagnostic("WARN", org.tzi.use.plugins.jacamo.diagnostics.Severity.WARNING,
                    org.tzi.use.plugins.jacamo.diagnostics.Phase.RESOLUTION, null, null, null,
                    "warning", "evidence", "fix source"));
        }
        @Override public List<TraceRow> traces() { return traces; }
        @Override public List<ConstraintDescriptor> constraints() { return List.of(); }
        @Override public VerificationReport latestVerification() {
            return latest;
        }
        @Override public RuntimeStatus runtimeStatus() { return runtime; }
        @Override public AuthorityStatus authorityStatus() { return authority; }
        @Override public ProjectSummary rebuild() { rebuilds++; return projectSummary(); }
        @Override public void loadVerificationProfile(Path profile) { loadedProfile = profile; }
        @Override public VerificationReport runFullVerification() { fullChecks++; return latestVerification(); }
        @Override public void exportVerificationReport(Path destination) { exportedReport = destination; }
        @Override public void exportNativeUse(Path destination) { exportedUse = destination; }
        @Override public void exportNativeSoil(Path destination) { exportedSoil = destination; }
        @Override public void connectRuntime() { connects++; }
        @Override public void disconnectRuntime() { disconnects++; }
        @Override public void resyncRuntime() { resyncs++; }
        @Override public void persistBinding(Path destination, BindingRequest request, String selectedTargetId,
                                             String reason) {
            persistedTarget = selectedTargetId;
            persistedReason = reason;
        }
    }
}
