package org.tzi.use.plugins.jacamo.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeReplayStepController;
import org.tzi.use.plugins.jacamo.runtime.MirrorState;

class JaCaMoWorkbenchPanelTest {
    @Test void simplifiedWorkbenchContainsOnlyProjectionRulesAndRetainedActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new JaCaMoWorkbenchPanel(new RecordingFacade());
            var tabs = component(panel, "workbench-tabs", JTabbedPane.class);
            assertEquals(1, tabs.getTabCount());
            assertEquals("Projection Rules", tabs.getTitleAt(0));
            assertEquals(List.of("Import JaCaMo Project...", "Load OCL...", "Start Runtime",
                    "Export .use...", "Export .cmd..."),
                    components(panel, JButton.class).stream().filter(b -> b.getName() != null).map(JButton::getText).toList());
            assertEquals(1, components(panel, JTable.class).size());
        });
        var handlers = Arrays.stream(JaCaMoWorkbenchPanel.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList();
        for (String removed : List.of("rebuildProject", "runFullVerification", "exportVerificationReport",
                "openStepReplay", "recordedReplay", "reanalyzeReplay", "loadHistoryPage", "refreshVerification",
                "refreshDiagnostics", "navigateGoalSource", "approveSelectedConstraint")) assertFalse(handlers.contains(removed), removed);
    }

    @Test void projectionRulesKeepEveryExactCatalogMappingInThreeReadOnlyColumns() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new JaCaMoWorkbenchPanel(new RecordingFacade());
            var table = component(panel, "mapping-rules-table", JTable.class);
            var rules = new CodeGroundedRuleCatalog().rules();
            assertEquals(List.of("Rule", "JaCaMo Concept", "USE Concept"),
                    java.util.stream.IntStream.range(0, table.getColumnCount()).mapToObj(table::getColumnName).toList());
            assertEquals(rules.size(), table.getRowCount());
            for (int row = 0; row < rules.size(); row++) {
                var rule = rules.get(row);
                assertEquals(rule.ruleId(), table.getValueAt(row, 0));
                assertEquals(rule.sourceKindFqcn(), table.getValueAt(row, 1));
                assertEquals(rule.targetUseKind(), table.getValueAt(row, 2));
                for (int col = 0; col < 3; col++) assertFalse(table.isCellEditable(row, col));
                var event = new MouseEvent(table, MouseEvent.MOUSE_MOVED, 0, 0, 5,
                        table.getRowHeight() * row + 1, 0, false);
                var detail = table.getToolTipText(event);
                assertTrue(detail.contains(rule.fidelity().name()));
                assertTrue(detail.contains(rule.implementationStatus().name()));
                assertTrue(detail.contains(rule.capabilityStatus().name()));
                assertTrue(detail.contains(rule.diagnosticPolicy()));
            }
        });
    }

    @Test void retainedWorkflowDelegatesImportOclAndStartInOrderWithoutCheckingInSwing() throws Exception {
        var facade = new RecordingFacade();
        var panel = new JaCaMoWorkbenchPanel(facade);
        panel.importProject(Path.of("project.jcm"));
        panel.loadVerificationProfile(Path.of("case.ocl"));
        panel.startRuntime();
        panel.exportNativeUse(Path.of("native.use"));
        panel.exportNativeSoil(Path.of("native.cmd"));
        assertEquals(List.of("import", "load", "start"), facade.calls);
        assertEquals(Path.of("project.jcm").toAbsolutePath().normalize(), facade.imported);
        assertEquals(Path.of("case.ocl"), facade.loadedProfile);
        assertEquals(Path.of("native.use"), facade.exportedUse);
        assertEquals(Path.of("native.cmd"), facade.exportedSoil);
        assertEquals("LIVE", label(panel, "runtime-state").getText());
        assertTrue(label(panel, "workflow-state").getText().contains("LIVE"));
        assertFalse(button(panel, "start-runtime").isEnabled());
        String source = Files.readString(Path.of("src/main/java/org/tzi/use/plugins/jacamo/ui/JaCaMoWorkbenchPanel.java"));
        for (String semanticType : List.of("MappingLoader", "TransformationPlanner", "InstancePlanner",
                "OclGenerator", "RuntimeMutationEngine", "DirectUseBackend", "VerificationReport"))
            assertFalse(source.contains(semanticType), semanticType + " must stay behind JaCaMoFacade");
    }

    @Test void interactiveImportBuildsOffEdtAndPublishesUiOnEdt() throws Exception {
        var facade = new RecordingFacade();
        var panel = new JaCaMoWorkbenchPanel(facade, ignored -> { });
        var published = new CountDownLatch(1);
        var publishedOnEdt = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            label(panel, "workbench-status").addPropertyChangeListener("text", event -> {
                if (event.getNewValue().toString().contains("Imported")) {
                    publishedOnEdt.set(SwingUtilities.isEventDispatchThread());
                    published.countDown();
                }
            });
            panel.importProject(Path.of("project.jcm"));
        });
        assertTrue(facade.importedLatch.await(5, TimeUnit.SECONDS));
        assertTrue(published.await(5, TimeUnit.SECONDS));
        assertFalse(facade.importedOnEdt);
        assertTrue(publishedOnEdt.get());
        awaitButton(panel, "load-profile");
    }

    @Test void detachedPanelDoesNotPublishLateImportError() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1); var ended = new CountDownLatch(1);
        var errors = new java.util.concurrent.CopyOnWriteArrayList<String>();
        JaCaMoFacade facade = new JaCaMoFacade() {
            public String status() { return "unit cancellation presentation"; }
            public ProjectSummary importProject(Path path) {
                started.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { throw new IllegalStateException(error); }
                finally { ended.countDown(); }
                throw new IllegalStateException("cancelled");
            }
        };
        var panel = new JaCaMoWorkbenchPanel(facade, errors::add);
        SwingUtilities.invokeAndWait(() -> panel.importProject(Path.of("project.jcm")));
        assertTrue(started.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(panel::removeNotify);
        release.countDown(); assertTrue(ended.await(5, TimeUnit.SECONDS));
        Thread.sleep(100); SwingUtilities.invokeAndWait(() -> { });
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test void displayedRuntimeStatusUpdatesFromCachedFacadeWithoutAConnectOrResyncAction() throws Exception {
        var facade = new RecordingFacade();
        var panel = new JaCaMoWorkbenchPanel(facade);
        SwingUtilities.invokeAndWait(() -> {
            facade.runtime = runtime(MirrorState.LIVE); panel.refreshRuntime(); panel.addNotify();
            assertEquals("LIVE", label(panel, "runtime-state").getText());
            facade.runtime = runtime(MirrorState.STALE);
        });
        var refreshed = new AtomicBoolean();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!refreshed.get() && System.nanoTime() < deadline) {
                SwingUtilities.invokeAndWait(() -> refreshed.set(label(panel, "runtime-state").getText().equals("STALE")));
                Thread.sleep(25);
            }
            assertTrue(refreshed.get(), "cached LIVE label must update without a manual Refresh click");
        } finally { SwingUtilities.invokeAndWait(panel::removeNotify); }
    }

    @Test void externallyOpenedReplayStillBlocksImportOclAndStart() throws Exception {
        var active = new AtomicBoolean(true);
        JaCaMoFacade facade = new JaCaMoFacade() {
            public String status() { return "read-only replay"; }
            public ProjectSummary projectSummary() { return new RecordingFacade().projectSummary(); }
            public WorkflowStatus workflowStatus() { return new WorkflowStatus("MODEL_READY", true, true, "run", "", "", ""); }
            public NativeReplayStepController.Status stepReplayStatus() {
                return active.get() ? new NativeReplayStepController.Status(0, 2, 7, "baseline", "recording", false, false) : null;
            }
        };
        SwingUtilities.invokeAndWait(() -> {
            var panel = new JaCaMoWorkbenchPanel(facade);
            for (String name : List.of("import-project", "load-profile", "start-runtime")) assertFalse(button(panel, name).isEnabled());
            active.set(false); panel.refreshRuntime();
            for (String name : List.of("import-project", "load-profile", "start-runtime")) assertTrue(button(panel, name).isEnabled());
        });
    }

    @Test void failedImportStillShowsItsErrorWithoutADiagnosticsTab() {
        var facade = new RecordingFacade(); facade.importFailure = new IllegalArgumentException("IMPORT_FAILED");
        var errors = new ArrayList<String>();
        var panel = new JaCaMoWorkbenchPanel(facade, errors::add);
        panel.importProject(Path.of("broken.jcm"));
        assertEquals(List.of("Import failed: IMPORT_FAILED"), errors);
        assertTrue(label(panel, "workbench-status").getText().contains("IMPORT_FAILED"));
    }

    @Test void loadOclRequiresImportAndBusyOperationCannotPublishAnOverlappingProfile() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var loaded = new java.util.concurrent.atomic.AtomicInteger();
        JaCaMoFacade facade = new JaCaMoFacade() {
            boolean imported;
            public String status() { return ""; }
            public ProjectSummary projectSummary() { return imported ? new RecordingFacade().projectSummary() : null; }
            public ProjectSummary importProject(Path path) {
                entered.countDown();
                try { release.await(5, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException error) { throw new IllegalStateException(error); }
                imported = true; return projectSummary();
            }
            public void loadVerificationProfile(Path path) { loaded.incrementAndGet(); }
        };
        var panel = new JaCaMoWorkbenchPanel(facade, ignored -> { });
        assertFalse(button(panel, "load-profile").isEnabled());
        javax.swing.SwingUtilities.invokeAndWait(() -> panel.importProject(Path.of("p.jcm")));
        assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        javax.swing.SwingUtilities.invokeAndWait(() -> { assertFalse(button(panel, "load-profile").isEnabled());
            panel.loadVerificationProfile(Path.of("p.ocl")); });
        assertEquals(0, loaded.get()); release.countDown();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (!button(panel, "load-profile").isEnabled() && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(button(panel, "load-profile").isEnabled());
    }

    @Test
    void projectChooserStartsAtAndSelectsTheDerivedJcmHint(@TempDir Path temporaryDirectory) throws Exception {
        Path derivedJcm = temporaryDirectory.resolve("helloworld.jcm");
        Files.writeString(derivedJcm, "project helloworld");

        // FilePane's asynchronous directory model posts changes to EDT; the test must
        // construct, configure and inspect the chooser on that same Swing thread.
        javax.swing.SwingUtilities.invokeAndWait(()->{
            JFileChooser chooser = JaCaMoWorkbenchPanel.createProjectChooser(derivedJcm);
            assertEquals(temporaryDirectory.toAbsolutePath().normalize(),
                    chooser.getCurrentDirectory().toPath().toAbsolutePath().normalize());
            assertEquals(derivedJcm.toAbsolutePath().normalize(),
                    chooser.getSelectedFile().toPath().toAbsolutePath().normalize());
        });
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

    private static JaCaMoFacade.RuntimeStatus runtime(MirrorState state) {
        return new JaCaMoFacade.RuntimeStatus(state, 0, 0, 0, 0, 0, 0, Instant.EPOCH, "", 0, 0, 0);
    }
    private static void awaitButton(JaCaMoWorkbenchPanel panel, String name) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10); var enabled = new AtomicBoolean();
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> enabled.set(button(panel, name).isEnabled()));
            if (enabled.get()) return;
            Thread.sleep(20);
        }
        fail(name);
    }
    private static JLabel label(Container root, String name) { return component(root, name, JLabel.class); }
    private static JButton button(Container root, String name) { return component(root, name, JButton.class); }
    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        return components(root, type).stream().filter(c -> name.equals(c.getName())).findFirst().orElseThrow(() -> new AssertionError(name));
    }
    private static <T extends Component> List<T> components(Container root, Class<T> type) {
        var result = new ArrayList<T>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container nested) result.addAll(components(nested, type));
        }
        return result;
    }
    private static void restoreProperty(String key, String previous) {
        if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
    }
    private static final class RecordingFacade implements JaCaMoFacade {
        private Path imported, loadedProfile, exportedUse, exportedSoil;
        private RuntimeException importFailure;
        private final CountDownLatch importedLatch = new CountDownLatch(1);
        private boolean importedOnEdt;
        private final List<String> calls = new ArrayList<>();
        private RuntimeStatus runtime = RuntimeStatus.offline();
        private String workflow = "NOT_IMPORTED";
        public String status() { return "ready"; }
        public ProjectSummary importProject(Path jcmFile) {
            imported = jcmFile.toAbsolutePath().normalize(); importedOnEdt = SwingUtilities.isEventDispatchThread();
            importedLatch.countDown();
            if (importFailure != null) throw importFailure;
            calls.add("import"); workflow = "MODEL_READY"; return projectSummary();
        }
        public ProjectSummary projectSummary() {
            return new ProjectSummary(Path.of("project.jcm"), Path.of("."), "project", 1,
                    Map.of("AGENT", 4L), "V2", "a".repeat(64), "mapping", "2.2.0", "b".repeat(64), "FROZEN", 60, 20, true, 1, 0);
        }
        public WorkflowStatus workflowStatus() {
            return new WorkflowStatus(workflow, workflow.equals("MODEL_READY") || workflow.equals("OCL_READY"), true, "run", "", "", "");
        }
        public void loadVerificationProfile(Path profile) { loadedProfile = profile; calls.add("load"); workflow = "OCL_READY"; }
        public void startRuntime() { calls.add("start"); workflow = "LIVE"; runtime = runtime(MirrorState.LIVE); }
        public RuntimeStatus runtimeStatus() { return runtime; }
        public void exportNativeUse(Path destination) { exportedUse = destination; }
        public void exportNativeSoil(Path destination) { exportedSoil = destination; }
        public List<SourceRow> sources() { throw new AssertionError("removed Project UI must not load source rows"); }
        public List<TraceRow> traces() { throw new AssertionError("removed Trace UI must not load trace rows"); }
        public org.tzi.use.plugins.jacamo.codegrounded.runtime.GoalViewSnapshot goalView() { throw new AssertionError("removed Goal UI must not query the tree"); }
        public org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot verificationSnapshot() { throw new AssertionError("native USE owns checking; Workbench must not build result views"); }
    }
}
