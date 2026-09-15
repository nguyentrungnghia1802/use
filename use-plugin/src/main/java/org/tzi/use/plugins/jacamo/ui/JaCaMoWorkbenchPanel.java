package org.tzi.use.plugins.jacamo.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
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
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor;
import org.tzi.use.plugins.jacamo.verification.VerificationReport;
import org.tzi.use.plugins.jacamo.verification.VerificationResult;

/** Swing workbench for the complete JaCaMo workflow. All semantic work is delegated to the facade. */
public final class JaCaMoWorkbenchPanel extends JPanel {
    private final JaCaMoFacade facade;
    private final Consumer<String> errorPresenter;
    private final JLabel status = named(new JLabel("Select a .jcm project"), "workbench-status");
    private final JLabel projectId = named(new JLabel("-"), "project-id");
    private final JLabel projectRoot = named(new JLabel("-"), "project-root");
    private final JLabel mappingStatus = named(new JLabel("-"), "mapping-status");
    private final JLabel generationStatus = named(new JLabel("-"), "generation-status");
    private final JLabel dimensionCounts = named(new JLabel("-"), "dimension-counts");
    private final JLabel sourceLocation = named(new JLabel("No source selected"), "source-location");
    private final JButton copySource = named(new JButton("Copy source path"), "copy-source");
    private final DefaultTableModel sourcesModel = readOnlyModel("Path", "Kind", "Bytes", "SHA-256");
    private final DefaultTableModel tracesModel = readOnlyModel("Source", "Source kind", "USE target", "Target kind",
            "Mapping rule", "Projection rule", "Status", "Source path", "Line", "Dimension");
    private final DefaultTableModel diagnosticsModel = readOnlyModel("Code", "Severity", "Phase", "File", "Line",
            "Message", "Remediation");
    private final DefaultTableModel verificationModel = readOnlyModel("Constraint", "Origin", "Status", "Context",
            "Detail", "OCL", "Source trace");
    private final JTable sources = named(new JTable(sourcesModel), "sources-table");
    private final JTable traces = named(new JTable(tracesModel), "trace-table");
    private final JTable diagnostics = named(new JTable(diagnosticsModel), "diagnostics-table");
    private final JTable verification = named(new JTable(verificationModel), "verification-table");
    private final JComboBox<String> dimensionFilter = named(new JComboBox<>(), "trace-dimension-filter");
    private final JComboBox<String> statusFilter = named(new JComboBox<>(), "trace-status-filter");
    private final TableRowSorter<DefaultTableModel> traceSorter = new TableRowSorter<>(tracesModel);
    private final JLabel runtimeState = named(new JLabel("OFFLINE"), "runtime-state");
    private final JLabel runtimeQueue = named(new JLabel("0"), "runtime-queue-depth");
    private final JLabel runtimeCounters = named(new JLabel("processed=0 rejected=0 failed=0 dropped=0"), "runtime-counters");
    private final JLabel runtimeLastSync = named(new JLabel("-"), "runtime-last-sync");
    private final JLabel runtimeLastEvent = named(new JLabel(""), "runtime-last-event");
    private final JLabel runtimeLatency = named(new JLabel("0 ns"), "runtime-latency");
    private final BindingResolutionPanel bindingPanel;
    private Path selectedSource;

    public JaCaMoWorkbenchPanel(JaCaMoFacade facade) {
        this(facade, message -> JOptionPane.showMessageDialog(null, message, "JaCaMo", JOptionPane.ERROR_MESSAGE));
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
        tabs.addTab("Trace", tracePanel());
        tabs.addTab("Diagnostics", tablePanel(diagnostics));
        tabs.addTab("Verification", verificationPanel());
        tabs.addTab("Runtime", runtimePanel());
        tabs.addTab("Binding", bindingPanel);
        add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        refreshRuntime();
    }

    public void importProject(Path jcmFile) {
        Path normalized = java.util.Objects.requireNonNull(jcmFile, "jcmFile").toAbsolutePath().normalize();
        execute("Import failed", () -> {
            facade.importProject(normalized);
            refreshProject();
            status.setText("Imported " + normalized);
        });
        refreshDiagnostics();
    }

    public void rebuildProject() {
        execute("Rebuild failed", () -> { facade.rebuild(); refreshProject(); status.setText("Project rebuilt"); });
    }

    public void runFullVerification() {
        execute("Verification failed", () -> { facade.runFullVerification(); refreshVerification(); });
    }

    public void loadVerificationProfile(Path profile) {
        execute("Profile load failed", () -> { facade.loadVerificationProfile(profile); refreshProject(); });
    }

    public void exportVerificationReport(Path destination) {
        execute("Report export failed", () -> { facade.exportVerificationReport(destination); status.setText("Report: " + destination); });
    }

    public void refreshRuntime() {
        JaCaMoFacade.RuntimeStatus current = facade.runtimeStatus();
        runtimeState.setText(current.state().name());
        runtimeQueue.setText(Integer.toString(current.queueDepth()));
        runtimeCounters.setText("processed=" + current.processed() + " rejected=" + current.rejected()
                + " failed=" + current.failed() + " dropped=" + current.dropped()
                + " high-watermark=" + current.highWatermark() + " violations=" + current.violationCount());
        runtimeLastSync.setText(current.lastSync() == null ? "-" : current.lastSync().toString());
        runtimeLastEvent.setText(current.lastEvent());
        runtimeLatency.setText(current.lastLatencyNanos() + " ns | snapshot=" + current.snapshotVersion());
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
        return toolbar;
    }

    private JPanel projectPanel() {
        JPanel summary = new JPanel(new GridLayout(0, 2, 8, 4));
        addField(summary, "Project", projectId);
        addField(summary, "Root", projectRoot);
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
        for (String value : List.of("AGENT", "ENVIRONMENT", "ORGANISATION", "UNKNOWN")) dimensionFilter.addItem(value);
        for (String value : List.of("RESOLVED", "PROJECTED", "AMBIGUOUS", "UNRESOLVED", "STALE")) statusFilter.addItem(value);
        dimensionFilter.addActionListener(event -> applyTraceFilter());
        statusFilter.addActionListener(event -> applyTraceFilter());
        traces.setRowSorter(traceSorter);
        traces.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && traces.getSelectedRow() >= 0) {
                int row = traces.convertRowIndexToModel(traces.getSelectedRow());
                Object path = tracesModel.getValueAt(row, 7);
                if (path instanceof Path sourcePath) {
                    selectedSource = sourcePath.toAbsolutePath().normalize();
                    sourceLocation.setText(selectedSource + ":" + tracesModel.getValueAt(row, 8));
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
        panel.add(new JScrollPane(traces), BorderLayout.CENTER);
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
        addField(values, "State", runtimeState);
        addField(values, "Queue depth", runtimeQueue);
        addField(values, "Counters", runtimeCounters);
        addField(values, "Last sync", runtimeLastSync);
        addField(values, "Last event", runtimeLastEvent);
        addField(values, "Latency", runtimeLatency);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        controls.add(button("Connect", "runtime-connect", () -> execute("Runtime connect failed", () -> {
            facade.connectRuntime(); refreshRuntime();
        })));
        controls.add(button("Disconnect", "runtime-disconnect", () -> execute("Runtime disconnect failed", () -> {
            facade.disconnectRuntime(); refreshRuntime();
        })));
        controls.add(button("Reconnect", "runtime-reconnect", () -> execute("Runtime reconnect failed", () -> {
            facade.connectRuntime(); refreshRuntime();
        })));
        controls.add(button("Resync", "runtime-resync", () -> execute("Runtime resync failed", () -> {
            facade.resyncRuntime(); refreshRuntime();
        })));
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
        mappingStatus.setText(summary.mappingStatus());
        generationStatus.setText("classes=" + summary.generatedClasses() + " objects=" + summary.generatedObjects()
                + " structure=" + (summary.structureValid() ? "PASS" : "FAIL") + " warnings=" + summary.warningCount()
                + " errors=" + summary.errorCount());
        dimensionCounts.setText(summary.dimensionCounts().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining(", ")));
        replaceRows(sourcesModel, facade.sources().stream().map(row -> new Object[] {
                row.path(), row.kind(), row.bytes(), row.sha256()
        }).toList());
        replaceRows(tracesModel, facade.traces().stream().map(row -> new Object[] {
                row.semanticId(), row.sourceKind(), row.targetUseId(), row.targetKind(), row.mappingRule(),
                row.projectionRule(), row.status(), row.sourcePath(), row.sourceLine(), row.dimension()
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
                new StringSelection(selectedSource.toString()), null);
    }

    private void applyTraceFilter() {
        String dimension = String.valueOf(dimensionFilter.getSelectedItem());
        String state = String.valueOf(statusFilter.getSelectedItem());
        java.util.ArrayList<RowFilter<DefaultTableModel, Integer>> filters = new java.util.ArrayList<>();
        if (!"ALL".equals(dimension)) filters.add(RowFilter.regexFilter("^" + Pattern.quote(dimension) + "$", 9));
        if (!"ALL".equals(state)) filters.add(RowFilter.regexFilter("^" + Pattern.quote(state) + "$", 6));
        traceSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    private void chooseProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JaCaMo project (*.jcm)", "jcm"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) importProject(chooser.getSelectedFile().toPath());
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

    private void execute(String title, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException exception) {
            status.setText(title + ": " + exception.getMessage());
            errorPresenter.accept(title + ": " + exception.getMessage());
        }
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
