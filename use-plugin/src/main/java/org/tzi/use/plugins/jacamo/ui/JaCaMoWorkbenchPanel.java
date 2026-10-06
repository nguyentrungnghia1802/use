package org.tzi.use.plugins.jacamo.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;

/** Import, OCL loading and runtime controls over the existing facade; native USE owns checking/views. */
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
    private final JLabel workflowState = named(new JLabel("NOT_IMPORTED"), "workflow-state");
    private final JLabel runtimeState = named(new JLabel("OFFLINE"), "runtime-state");
    private final JButton startRuntime = named(new JButton("Start Runtime"), "start-runtime");
    private final JButton loadProfile = named(new JButton("Load OCL..."), "load-profile");
    private final Map<String, JButton> actionButtons = new LinkedHashMap<>();
    private boolean autoImportStarted;
    private int backgroundOperations;
    private long viewEpoch;
    // Refresh only cached facade status, never start a second runtime or a network resync.
    private final javax.swing.Timer runtimeStatusTimer = new javax.swing.Timer(1000, event -> {
        refreshRuntimeIfIdle();
    });
    private void refreshRuntimeIfIdle() { if (backgroundOperations == 0 && !facade.stepReplayBusy()) refreshRuntime(); }

    @Override public void addNotify() {
        super.addNotify();
        runtimeStatusTimer.start();
    }

    @Override public void removeNotify() {
        runtimeStatusTimer.stop();
        viewEpoch++;
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
        startRuntime.addActionListener(event -> startRuntime());
        loadProfile.addActionListener(event -> chooseProfile());
        add(toolbar(), BorderLayout.NORTH);
        JTabbedPane tabs = named(new JTabbedPane(), "workbench-tabs");
        tabs.addTab("Projection Rules", new MappingRulesPanel());
        add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        refreshRuntime();
        autoImportIfConfigured();
    }

    public void importProject(Path jcmFile) {
        Path normalized = java.util.Objects.requireNonNull(jcmFile, "jcmFile").toAbsolutePath().normalize();
        executeBackground("Import failed", () -> facade.importProject(normalized), () -> {
            refreshRuntime();
            setStatus("Imported " + normalized);
            writeReadyEvidence(normalized);
        });
    }

    public void loadVerificationProfile(Path profile) {
        executeBackground("Profile load failed", () -> facade.loadVerificationProfile(profile), this::refreshRuntime);
    }

    public void startRuntime() {
        executeBackground("Runtime start failed", facade::startRuntime, this::refreshRuntime);
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
        refreshWorkflowControls();
        runtimeState.setText(facade.runtimeStatus().state().name());
    }

    private JPanel toolbar() {
        JPanel toolbar = new JPanel(new GridLayout(0, 1));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        actions.add(button("Import JaCaMo Project...", "import-project", this::chooseProject));
        actions.add(loadProfile);
        actions.add(startRuntime);
        JPanel exports = new JPanel(new FlowLayout(FlowLayout.LEADING));
        exports.add(button("Export .use...", "export-use", this::chooseNativeUse));
        exports.add(button("Export .cmd...", "export-soil", this::chooseNativeSoil));
        exports.add(new JLabel("Workflow:"));
        exports.add(workflowState);
        exports.add(new JLabel("Runtime:"));
        exports.add(runtimeState);
        toolbar.add(actions);
        toolbar.add(exports);
        return toolbar;
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
    private void execute(String title, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException exception) {
            String message = title + ": " + exception.getMessage();
            setStatus(message);
            errorPresenter.accept(message);
        }
    }

    private void executeBackground(String title, Runnable operation, Runnable afterSuccess) {
        if (backgroundOperations != 0) { setStatus("WORKBENCH_BUSY: wait for the current operation"); return; }
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            execute(title, () -> { operation.run(); afterSuccess.run(); });
            return;
        }
        setStatus(title.replace(" failed", "") + "...");
        backgroundOperations++;
        long ticket = viewEpoch;
        refreshWorkflowControls();
        new javax.swing.SwingWorker<Void,Void>() {
            @Override protected Void doInBackground() { operation.run(); return null; }
            @Override protected void done() {
                try { get(); if (ticket == viewEpoch) afterSuccess.run(); }
                catch (Exception error) {
                    if (ticket != viewEpoch) return;
                    Throwable cause = error instanceof java.util.concurrent.ExecutionException && error.getCause() != null
                            ? error.getCause() : error;
                    String message = title + ": " + cause.getMessage();
                    setStatus(message);
                    errorPresenter.accept(message);
                }
                finally { backgroundOperations--; if (ticket == viewEpoch) refreshWorkflowControls(); }
            }
        }.execute();
    }
    private void refreshWorkflowControls() {
        var workflow = facade.workflowStatus();
        boolean busy = backgroundOperations != 0 || facade.stepReplayBusy();
        boolean replayActive = facade.stepReplayStatus() != null;
        workflowState.setText(displayMessage(workflow.state() + " | " + workflow.diagnostic()));
        startRuntime.setEnabled(!busy && !replayActive && workflow.startAvailable());
        loadProfile.setEnabled(!busy && !replayActive && facade.projectSummary() != null);
        actionButtons.forEach((name, button) -> button.setEnabled(!busy));
        actionButtons.get("import-project").setEnabled(!busy && !replayActive);
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

    private JButton button(String text, String name, Runnable action) {
        JButton button = named(new JButton(text), name);
        actionButtons.put(name, button);
        button.addActionListener(event -> action.run());
        return button;
    }
    private static <T extends java.awt.Component> T named(T component, String name) { component.setName(name); return component; }
}
