package org.tzi.use.plugins.jacamo.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.RowFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor;
import org.tzi.use.plugins.jacamo.verification.VerificationReport;
import org.tzi.use.plugins.jacamo.verification.VerificationResult;

/** Swing workbench for the complete JaCaMo workflow. All semantic work is delegated to the facade. */
public final class JaCaMoWorkbenchPanel extends JPanel {
    /** Optional launch-time hint used by the interactive demo to open Import at its derived JCM. */
    static final String PROJECT_FILE_HINT_PROPERTY = "use.jacamo.workbench.project-file";
    /** One-shot launch flag: the generic launcher supplies the JCM once; the panel consumes it. */
    static final String AUTO_IMPORT_PROPERTY = "use.jacamo.workbench.auto-import";
    /** Optional atomic evidence marker written only after a configured import succeeds. */
    static final String READY_FILE_PROPERTY = "use.jacamo.workbench.ready-file";
    private static final int MESSAGE_WRAP_COLUMNS = 96;

    private final JaCaMoFacade facade;
    private final Consumer<String> errorPresenter;
    private final JLabel status = named(new JLabel(displayMessage("Select a .jcm project")), "workbench-status");
    private final JLabel projectId = named(new JLabel("-"), "project-id");
    private final JLabel projectRoot = named(new JLabel("-"), "project-root");
    private final JLabel metamodelBaseline = named(new JLabel("-"), "metamodel-baseline");
    private final JLabel mappingStatus = named(new JLabel("-"), "mapping-status");
    private final JLabel generationStatus = named(new JLabel("-"), "generation-status");
    private final JLabel dimensionCounts = named(new JLabel("-"), "dimension-counts");
    private final JLabel sourceLocation = named(new JLabel("No source selected"), "source-location");
    private final JButton copySource = named(new JButton("Copy source path"), "copy-source");
    private final DefaultTableModel sourcesModel = readOnlyModel("Path", "Kind", "Bytes", "SHA-256");
    private final DefaultTableModel tracesModel = readOnlyModel("Rule", "Source", "Target", "Fidelity", "Status");
    private final DefaultTableModel diagnosticsModel = readOnlyModel("Code", "Severity", "Phase", "File", "Line",
            "Message", "Remediation");
    private final DefaultTableModel verificationModel = readOnlyModel("Constraint", "Origin", "Status", "Context",
            "Detail", "OCL", "Source trace");
    private final JTable sources = named(new JTable(sourcesModel), "sources-table");
    private final JTable traces = named(new JTable(tracesModel), "trace-table");
    private final JTable diagnostics = named(new JTable(diagnosticsModel), "diagnostics-table");
    private final JTable verification = named(new JTable(verificationModel), "verification-table");
    private final JTextArea traceDetail = named(new JTextArea(), "mapping-detail");
    private final JComboBox<String> dimensionFilter = named(new JComboBox<>(), "trace-dimension-filter");
    private final JComboBox<String> statusFilter = named(new JComboBox<>(), "trace-status-filter");
    private final TableRowSorter<DefaultTableModel> traceSorter = new TableRowSorter<>(tracesModel);
    private final JLabel runtimeState = named(new JLabel("OFFLINE"), "runtime-state");
    private final JLabel runtimeQueue = named(new JLabel("0"), "runtime-queue-depth");
    private final JLabel runtimeCounters = named(new JLabel("processed=0 rejected=0 failed=0 dropped=0"), "runtime-counters");
    private final JLabel runtimeLastSync = named(new JLabel("-"), "runtime-last-sync");
    private final JLabel runtimeLastEvent = named(new JLabel(""), "runtime-last-event");
    private final JLabel runtimeLatency = named(new JLabel("0 ns"), "runtime-latency");
    private final JLabel runtimeVerification = named(new JLabel("NOT_RUN"), "runtime-verification");
    private final JLabel runtimeCoverage = named(new JLabel("UNAVAILABLE"), "runtime-coverage");
    private final JLabel runtimeFailures = named(new JLabel("-"), "runtime-failing-constraints");
    private final JTextArea runtimeHistory = named(new JTextArea(6, 60), "runtime-verification-history");
    private final JLabel semanticAuthority = named(new JLabel("BRIDGE"), "semantic-authority");
    private final JLabel bridgeReadiness = named(new JLabel("DISCONNECTED"), "bridge-readiness");
    private final JLabel bridgeCapabilities = named(new JLabel("-"), "bridge-capabilities");
    private final JLabel bridgeCompleteness = named(new JLabel("UNAVAILABLE"), "bridge-completeness");
    private final JLabel bridgeRevision = named(new JLabel("-"), "bridge-model-revision");
    private final JLabel bridgeSession = named(new JLabel("-"), "bridge-session-generation");
    private final JLabel bridgeEndpoint = named(new JLabel("-"), "bridge-endpoint");
    private final JLabel bridgeDiagnostic = named(new JLabel("-"), "bridge-diagnostic");
    private final BindingResolutionPanel bindingPanel;
    private String selectedSource;
    private boolean autoImportStarted;
    private int backgroundOperations;
    // Refresh only cached facade status, never start a second runtime or a network resync.
    private final javax.swing.Timer runtimeStatusTimer = new javax.swing.Timer(1000, event -> {
        if (backgroundOperations == 0) refreshRuntime();
    });

    @Override public void addNotify() {
        super.addNotify();
        runtimeStatusTimer.start();
    }

    @Override public void removeNotify() {
        runtimeStatusTimer.stop();
        super.removeNotify();
    }

    public JaCaMoWorkbenchPanel(JaCaMoFacade facade) {
        this(facade, message -> JOptionPane.showMessageDialog(null, new JLabel(displayMessage(message)),
                "JaCaMo", JOptionPane.ERROR_MESSAGE));
    }

    JaCaMoWorkbenchPanel(JaCaMoFacade facade, Consumer<String> errorPresenter) {
        super(new BorderLayout(8, 8));
        this.facade = java.util.Objects.requireNonNull(facade, "facade");
        this.errorPresenter = java.util.Objects.requireNonNull(errorPresenter, "errorPresenter");
        this.bindingPanel = new BindingResolutionPanel(facade);
        copySource.setEnabled(false);
        copySource.addActionListener(event -> copySelectedSource());
        add(toolbar(), BorderLayout.NORTH);
        JTabbedPane tabs = named(new JTabbedPane(), "workbench-tabs");
        tabs.addTab("Project", projectPanel());
        tabs.addTab("Mapping Inspector", tracePanel());
        tabs.addTab("Diagnostics", tablePanel(diagnostics));
        tabs.addTab("Verification", verificationPanel());
        tabs.addTab("Runtime", runtimePanel());
        tabs.addTab("Binding", bindingPanel);
        add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        refreshRuntime();
        autoImportIfConfigured();
    }

    public void importProject(Path jcmFile) {
        Path normalized = java.util.Objects.requireNonNull(jcmFile, "jcmFile").toAbsolutePath().normalize();
        executeBackground("Import failed", () -> facade.importProject(normalized), () -> {
            refreshProject();
            setStatus("Imported " + normalized);
            writeReadyEvidence(normalized);
        });
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) refreshDiagnostics();
    }

    public void rebuildProject() {
        executeBackground("Rebuild failed", facade::rebuild,
                () -> { refreshProject(); setStatus("Project rebuilt"); });
    }

    public void runFullVerification() {
        executeBackground("Verification failed", facade::runFullVerification, this::refreshVerification);
    }

    public void loadVerificationProfile(Path profile) {
        executeBackground("Profile load failed", () -> facade.loadVerificationProfile(profile), this::refreshProject);
    }

    public void exportVerificationReport(Path destination) {
        executeBackground("Report export failed", () -> facade.exportVerificationReport(destination),
                () -> setStatus("Report: " + destination));
    }

    public void exportNativeUse(Path destination) {
        executeBackground(".use export failed", () -> facade.exportNativeUse(destination),
                () -> setStatus("Native .use: " + destination));
    }

    public void exportNativeSoil(Path destination) {
        executeBackground(".cmd export failed", () -> facade.exportNativeSoil(destination),
                () -> setStatus("Native .cmd: " + destination));
    }

    public void refreshRuntime() {
        JaCaMoFacade.RuntimeStatus current = facade.runtimeStatus();
        JaCaMoFacade.AuthorityStatus authority = facade.authorityStatus();
        runtimeState.setText(current.state().name());
        runtimeQueue.setText(Integer.toString(current.queueDepth()));
        runtimeCounters.setText("processed=" + current.processed() + " rejected=" + current.rejected()
                + " failed=" + current.failed() + " dropped=" + current.dropped()
                + " high-watermark=" + current.highWatermark() + " violations=" + current.violationCount());
        runtimeLastSync.setText(current.lastSync() == null ? "-" : current.lastSync().toString());
        runtimeLastEvent.setText(current.lastEvent());
        runtimeLatency.setText(current.lastLatencyNanos() + " ns | snapshot=" + current.snapshotVersion());
        var result = facade.runtimeVerificationResult();
        if (result != null) {
            runtimeVerification.setText("stateVersion=" + result.stateVersion() + " PASS=" + result.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.PASS)
                    + " FAIL=" + result.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL)
                    + " ERROR=" + result.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.ERROR)
                    + " SKIPPED=" + result.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.SKIPPED));
            runtimeCoverage.setText(displayMessage(result.coverage() + " / " + result.freshness() + " / " + result.diagnostic()));
            runtimeFailures.setText(displayMessage(String.join(", ", result.failingConstraints())));
            var history = facade.runtimeVerificationHistory();
            runtimeHistory.setText(history.stream().skip(Math.max(0, history.size() - 12)).map(item ->
                    "v" + item.stateVersion() + " " + item.eventId() + " PASS=" + item.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.PASS)
                    + " FAIL=" + item.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL)
                    + " ERROR=" + item.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.ERROR)
                    + " SKIPPED=" + item.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.SKIPPED)
                    + " " + item.coverage() + " " + item.failingConstraints()).collect(java.util.stream.Collectors.joining("\n")));
        }
        semanticAuthority.setText(authority.authority().name());
        bridgeReadiness.setText(authority.readiness().name());
        bridgeCapabilities.setText(authority.capabilities().isEmpty() ? "-" : authority.capabilities().entrySet()
                .stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", ")));
        bridgeCompleteness.setText(authority.completeness());
        bridgeRevision.setText(authority.modelRevision().isBlank() ? "-" : authority.modelRevision());
        bridgeSession.setText(authority.sessionId().isBlank() ? "-"
                : authority.sessionId() + " / generation=" + authority.generation());
        bridgeEndpoint.setText(authority.endpoint().isBlank() ? "-" : authority.endpoint());
        bridgeDiagnostic.setText(authority.diagnostic().isBlank() ? "-" : authority.diagnostic());
        refreshVerification();
    }

    public void showBindingRequest(Path destination, JaCaMoFacade.BindingRequest request) {
        bindingPanel.showRequest(destination, request);
    }

    private JPanel toolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING));
        toolbar.add(button("Import JaCaMo Project...", "import-project", this::chooseProject));
        toolbar.add(button("Rebuild", "rebuild-project", this::rebuildProject));
        toolbar.add(button("Load OCL...", "load-profile", this::chooseProfile));
        toolbar.add(button("Run Full Verification", "run-verification", this::runFullVerification));
        toolbar.add(button("Export Report...", "export-report", this::chooseReport));
        toolbar.add(button("Export .use...", "export-use", this::chooseNativeUse));
        toolbar.add(button("Export .cmd...", "export-soil", this::chooseNativeSoil));
        toolbar.add(button("Export replay...", "export-replay", this::chooseRuntimeReplay));
        return toolbar;
    }

    private JPanel projectPanel() {
        JPanel summary = new JPanel(new GridLayout(0, 2, 8, 4));
        addField(summary, "Project", projectId);
        addField(summary, "Root", projectRoot);
        addField(summary, "Active metamodel", metamodelBaseline);
        addField(summary, "Mapping compatibility", mappingStatus);
        addField(summary, "Generation", generationStatus);
        addField(summary, "Dimensions", dimensionCounts);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, summary, tablePanel(sources));
        split.setResizeWeight(0.25);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel tracePanel() {
        dimensionFilter.addItem("ALL");
        statusFilter.addItem("ALL");
        for (String value : List.of("J", "A", "C", "M", "X", "AGENT", "ENVIRONMENT", "ORGANISATION", "UNKNOWN"))
            dimensionFilter.addItem(value);
        for (String value : List.of("APPLIED", "COMPLETE", "PARTIAL", "UNAVAILABLE", "UNSUPPORTED",
                "RESOLVED", "PROJECTED", "AMBIGUOUS", "UNRESOLVED", "STALE")) statusFilter.addItem(value);
        dimensionFilter.addActionListener(event -> applyTraceFilter());
        statusFilter.addActionListener(event -> applyTraceFilter());
        traces.setRowSorter(traceSorter);
        traceDetail.setEditable(false);
        traceDetail.setLineWrap(true);
        traceDetail.setWrapStyleWord(true);
        traces.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && traces.getSelectedRow() >= 0) {
                int row = traces.convertRowIndexToModel(traces.getSelectedRow());
                JaCaMoFacade.TraceRow selected = facade.traces().get(row);
                traceDetail.setText("Rule: " + selected.mappingRule() + "\nSource FQCN: " + selected.sourceKind()
                        + "\nSemantic ID: " + selected.semanticId() + "\nTarget: " + selected.targetKind() + " "
                        + selected.targetUseId() + "\nEvidence: " + selected.evidenceAuthority()
                        + selected.sourceEvidence().stream().map(value -> "\nSource URI: " + value.sourceUri()
                                + "\nSource lines: " + value.startLine() + "-" + value.endLine()
                                + "\nSource SHA-256: " + value.sourceDigest()).collect(java.util.stream.Collectors.joining())
                        + "\nFidelity: " + selected.projectionRule() + "\nCapability/status: " + selected.status()
                        + (selected.traceDiagnostics().isEmpty() ? "" : "\nDiagnostics: "
                        + String.join(", ", selected.traceDiagnostics())));
                Path sourcePath = selected.sourcePath();
                if (sourcePath != null) {
                    selectedSource = sourcePath.toAbsolutePath().normalize().toString();
                    sourceLocation.setText(selectedSource + ":" + selected.sourceLine());
                    copySource.setEnabled(true);
                } else if (!selected.sourceEvidence().isEmpty()) {
                    selectedSource = selected.sourceEvidence().getFirst().sourceUri();
                    sourceLocation.setText(selectedSource + ":" + selected.sourceLine());
                    copySource.setEnabled(true);
                } else {
                    selectedSource = null;
                    sourceLocation.setText("No source location for this declaration trace");
                    copySource.setEnabled(false);
                }
            }
        });
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEADING));
        filters.add(new JLabel("Dimension:")); filters.add(dimensionFilter);
        filters.add(new JLabel("Status:")); filters.add(statusFilter);
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(filters, BorderLayout.NORTH);
        JSplitPane traceSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(traces),
                new JScrollPane(traceDetail));
        traceSplit.setResizeWeight(0.75);
        panel.add(traceSplit, BorderLayout.CENTER);
        JPanel sourceControls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        sourceControls.add(sourceLocation);
        sourceControls.add(copySource);
        panel.add(sourceControls, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel verificationPanel() {
        JTextArea detail = named(new JTextArea(), "verification-detail");
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        verification.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && verification.getSelectedRow() >= 0) {
                int row = verification.convertRowIndexToModel(verification.getSelectedRow());
                detail.setText("Context: " + verificationModel.getValueAt(row, 3) + "\n"
                        + verificationModel.getValueAt(row, 4) + "\n\n" + verificationModel.getValueAt(row, 5)
                        + "\n\nSource: " + verificationModel.getValueAt(row, 6));
            }
        });
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(verification), new JScrollPane(detail));
        split.setResizeWeight(0.65);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel runtimePanel() {
        JPanel values = new JPanel(new GridLayout(0, 2, 8, 4));
        addField(values, "Semantic authority", semanticAuthority);
        addField(values, "Bridge readiness", bridgeReadiness);
        addField(values, "Capabilities", bridgeCapabilities);
        addField(values, "Completeness", bridgeCompleteness);
        addField(values, "Model revision", bridgeRevision);
        addField(values, "Session/generation", bridgeSession);
        addField(values, "Endpoint", bridgeEndpoint);
        addField(values, "Bridge diagnostic", bridgeDiagnostic);
        addField(values, "State", runtimeState);
        addField(values, "Queue depth", runtimeQueue);
        addField(values, "Counters", runtimeCounters);
        addField(values, "Last sync", runtimeLastSync);
        addField(values, "Last event", runtimeLastEvent);
        addField(values, "Latency", runtimeLatency);
        addField(values, "Verification", runtimeVerification);
        addField(values, "Coverage / GAP", runtimeCoverage);
        addField(values, "Failing constraints", runtimeFailures);
        runtimeHistory.setEditable(false);
        values.add(new JLabel("Retained observations (last 12)"));
        values.add(new JScrollPane(runtimeHistory));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        controls.add(button("Connect", "runtime-connect", () -> executeBackground("Runtime connect failed",
                facade::connectRuntime, this::refreshRuntime)));
        controls.add(button("Disconnect", "runtime-disconnect", () -> execute("Runtime disconnect failed", () -> {
            facade.disconnectRuntime(); refreshRuntime();
        })));
        controls.add(button("Reconnect", "runtime-reconnect", () -> executeBackground("Runtime reconnect failed",
                facade::connectRuntime, this::refreshRuntime)));
        controls.add(button("Resync", "runtime-resync", () -> executeBackground("Runtime resync failed",
                facade::resyncRuntime, this::refreshRuntime)));
        controls.add(button("Refresh", "runtime-refresh", this::refreshRuntime));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(values, BorderLayout.CENTER);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshProject() {
        JaCaMoFacade.ProjectSummary summary = facade.projectSummary();
        if (summary == null) return;
        projectId.setText(summary.projectId());
        projectRoot.setText(summary.projectRoot().toString());
        metamodelBaseline.setText(summary.metamodelVersion() + " | sha256=" + summary.metamodelSha256());
        mappingStatus.setText(summary.mappingId() + " | schema=" + summary.mappingVersion()
                + " | " + summary.mappingStatus() + " | sha256=" + summary.mappingSha256());
        generationStatus.setText("classes=" + summary.generatedClasses() + " objects=" + summary.generatedObjects()
                + " structure=" + (summary.structureValid() ? "PASS" : "FAIL") + " warnings=" + summary.warningCount()
                + " errors=" + summary.errorCount());
        dimensionCounts.setText(summary.dimensionCounts().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining(", ")));
        replaceRows(sourcesModel, facade.sources().stream().map(row -> new Object[] {
                row.path(), row.kind(), row.bytes(), row.sha256()
        }).toList());
        replaceRows(tracesModel, facade.traces().stream().map(row -> new Object[] {
                row.mappingRule(), row.sourceKind() + " | " + row.semanticId(),
                row.targetKind() + " | " + row.targetUseId(), row.projectionRule(), row.status()
        }).toList());
        refreshDiagnostics();
        refreshVerification();
        refreshRuntime();
    }

    private void refreshVerification() {
        Map<String, ConstraintDescriptor> descriptors = new LinkedHashMap<>();
        facade.constraints().forEach(value -> descriptors.put(value.id(), value));
        VerificationReport report = facade.latestVerification();
        Map<String, VerificationResult> results = new LinkedHashMap<>();
        if (report != null) report.results().forEach(value -> results.put(value.constraintId(), value));
        Map<String, String> sourceLocations = new LinkedHashMap<>();
        facade.traces().stream().filter(value -> value.sourcePath() != null).forEach(value -> sourceLocations.putIfAbsent(
                value.semanticId(), value.sourcePath().toAbsolutePath().normalize() + ":" + value.sourceLine()));
        LinkedHashSet<String> ids = new LinkedHashSet<>(descriptors.keySet());
        ids.addAll(results.keySet());
        replaceRows(verificationModel, ids.stream().map(id -> verificationRow(descriptors.get(id), results.get(id),
                sourceLocations)).toList());
    }

    private Object[] diagnosticRow(Diagnostic value) {
        Path path = value.sourceLocation() == null ? null : value.sourceLocation().path();
        int line = value.sourceLocation() == null ? 0 : value.sourceLocation().startLine();
        return new Object[] { value.code(), value.severity(), value.phase(), path, line, value.message(), value.remediation() };
    }

    private void refreshDiagnostics() {
        replaceRows(diagnosticsModel, facade.diagnostics().stream().map(this::diagnosticRow).toList());
    }

    private Object[] verificationRow(ConstraintDescriptor descriptor, VerificationResult result,
                                     Map<String, String> sourceLocations) {
        return new Object[] {
                descriptor == null ? result.constraintId() : descriptor.id(),
                descriptor == null ? "SYSTEM" : descriptor.origin(),
                result == null ? "NOT_RUN" : result.outcome(),
                result == null ? "" : nullToEmpty(result.contextObject()),
                result == null ? "" : result.explanation(),
                result == null ? descriptor.oclSource() : result.oclSource(),
                result == null ? "" : result.sourceTrace().stream()
                        .map(source -> sourceLocations.getOrDefault(source, source))
                        .collect(java.util.stream.Collectors.joining(", "))
        };
    }

    private void copySelectedSource() {
        if (selectedSource == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new StringSelection(selectedSource), null);
    }

    private void applyTraceFilter() {
        String dimension = String.valueOf(dimensionFilter.getSelectedItem());
        String state = String.valueOf(statusFilter.getSelectedItem());
        if ("ALL".equals(dimension) && "ALL".equals(state)) {
            traceSorter.setRowFilter(null);
            return;
        }
        traceSorter.setRowFilter(new RowFilter<>() {
            @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                JaCaMoFacade.TraceRow row = facade.traces().get(entry.getIdentifier());
                return ("ALL".equals(dimension) || dimension.equals(row.dimension()))
                        && ("ALL".equals(state) || state.equals(row.status()));
            }
        });
    }

    private void chooseProject() {
        JFileChooser chooser = createProjectChooser(projectFileHint());
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) importProject(chooser.getSelectedFile().toPath());
    }

    private void autoImportIfConfigured() {
        if (!Boolean.parseBoolean(System.getProperty(AUTO_IMPORT_PROPERTY, "false")) || autoImportStarted) return;
        autoImportStarted = true;
        // Consume the one-shot flag before starting asynchronous work. A reopened panel must not
        // select the staged JCM a second time; the ordinary chooser remains available as fallback.
        System.clearProperty(AUTO_IMPORT_PROPERTY);
        Path configured = projectFileHint();
        if (configured == null) {
            String message = "Auto-import failed: WORKBENCH_PROJECT_FILE_REQUIRED";
            setStatus(message);
            errorPresenter.accept(message);
            return;
        }
        importProject(configured);
    }

    private void writeReadyEvidence(Path imported) {
        String configured = System.getProperty(READY_FILE_PROPERTY, "").trim();
        if (configured.isEmpty()) return;
        System.clearProperty(READY_FILE_PROPERTY);
        try {
            Path ready = Path.of(configured).toAbsolutePath().normalize();
            if (ready.getParent() != null) Files.createDirectories(ready.getParent());
            var formal = facade.formalStateStatus();
            var authority = facade.authorityStatus();
            var evidence = new LinkedHashMap<String, Object>();
            evidence.put("status", "READY");
            if (facade instanceof org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade nativeFacade)
                evidence.put("projectionMode", nativeFacade.projectionMode().name());
            evidence.put("projectFile", imported.toString());
            evidence.put("classCount", formal.classCount());
            evidence.put("associationCount", formal.associationCount());
            evidence.put("objectCount", formal.objectCount());
            evidence.put("linkCount", formal.linkCount());
            evidence.put("stateSha256", formal.sha256());
            evidence.put("bridgeReadiness", authority.readiness().name());
            evidence.put("bridgeCompleteness", authority.completeness());
            evidence.put("modelRevision", authority.modelRevision());
            evidence.put("sessionId", authority.sessionId());
            evidence.put("generation", authority.generation());
            Path temporary = ready.resolveSibling(ready.getFileName() + ".tmp");
            Files.write(temporary, CanonicalJson.encode(evidence));
            try {
                Files.move(temporary, ready, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, ready, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception error) {
            String message = "Ready evidence failed: " + error.getMessage();
            setStatus(message);
            errorPresenter.accept(message);
        }
    }

    static JFileChooser createProjectChooser(Path projectFileHint) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JaCaMo project (*.jcm)", "jcm"));
        if (projectFileHint == null) return chooser;

        Path normalized = projectFileHint.toAbsolutePath().normalize();
        Path directory = Files.isDirectory(normalized) ? normalized : normalized.getParent();
        if (directory != null && Files.isDirectory(directory)) {
            chooser.setCurrentDirectory(directory.toFile());
            if (Files.isRegularFile(normalized)) chooser.setSelectedFile(normalized.toFile());
        }
        return chooser;
    }

    private static Path projectFileHint() {
        String configured = System.getProperty(PROJECT_FILE_HINT_PROPERTY, "").trim();
        if (configured.isEmpty()) return null;
        try {
            return Path.of(configured);
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private void chooseProfile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("OCL profile (*.ocl)", "ocl"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) loadVerificationProfile(chooser.getSelectedFile().toPath());
    }
    private void chooseReport() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) exportVerificationReport(chooser.getSelectedFile().toPath());
    }
    private void chooseNativeUse() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("USE model (*.use)", "use"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
            exportNativeUse(chooser.getSelectedFile().toPath());
    }
    private void chooseNativeSoil() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("USE SOIL commands (*.cmd)", "cmd"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
            exportNativeSoil(chooser.getSelectedFile().toPath());
    }
    private void chooseRuntimeReplay() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Export observed runtime replay to an empty directory");
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path directory = chooser.getSelectedFile().toPath();
            executeBackground("Replay export failed", () -> facade.exportRuntimeReplay(directory),
                    () -> setStatus("Observed runtime replay: " + directory));
        }
    }

    private void execute(String title, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException exception) {
            String message = title + ": " + exception.getMessage();
            setStatus(message);
            errorPresenter.accept(message);
        }
    }

    private void executeBackground(String title, Runnable operation, Runnable afterSuccess) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            execute(title, () -> { operation.run(); afterSuccess.run(); });
            return;
        }
        setStatus(title.replace(" failed", "") + "...");
        backgroundOperations++;
        new javax.swing.SwingWorker<Void,Void>() {
            @Override protected Void doInBackground() { operation.run(); return null; }
            @Override protected void done() {
                try { get(); afterSuccess.run(); }
                catch (Exception error) {
                    Throwable cause = error instanceof java.util.concurrent.ExecutionException && error.getCause() != null
                            ? error.getCause() : error;
                    String message = title + ": " + cause.getMessage();
                    setStatus(message);
                    errorPresenter.accept(message);
                }
                finally { backgroundOperations--; }
            }
        }.execute();
    }

    private void setStatus(String message) { status.setText(displayMessage(message)); }

    static String displayMessage(String message) {
        return "<html>" + escapeHtml(wrapMessage(message)).replace("\n", "<br>") + "</html>";
    }

    private static String wrapMessage(String message) {
        String normalized = message == null ? "" : message.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder wrapped = new StringBuilder(normalized.length() + lines.length * 4);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) wrapped.append('\n');
            appendWrappedLine(wrapped, lines[index]);
        }
        return wrapped.toString();
    }

    private static void appendWrappedLine(StringBuilder output, String line) {
        if (line.isBlank()) return;
        int column = 0;
        for (String word : line.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            if (column > 0 && column + 1 + word.length() <= MESSAGE_WRAP_COLUMNS) {
                output.append(' ');
                column++;
            } else if (column > 0) {
                output.append('\n');
                column = 0;
            }
            int offset = 0;
            while (offset < word.length()) {
                int remaining = MESSAGE_WRAP_COLUMNS - column;
                int length = Math.min(remaining, word.length() - offset);
                output.append(word, offset, offset + length);
                offset += length;
                column += length;
                if (offset < word.length()) {
                    output.append('\n');
                    column = 0;
                }
            }
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static JPanel tablePanel(JTable table) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }
    private static void addField(JPanel panel, String label, JLabel value) { panel.add(new JLabel(label)); panel.add(value); }
    private static JButton button(String text, String name, Runnable action) {
        JButton button = named(new JButton(text), name);
        button.addActionListener(event -> action.run());
        return button;
    }
    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }
    private static void replaceRows(DefaultTableModel model, List<Object[]> rows) {
        model.setRowCount(0);
        rows.forEach(model::addRow);
    }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private static <T extends java.awt.Component> T named(T component, String name) { component.setName(name); return component; }
}
