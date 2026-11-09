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
    private static Path resolveExisting(String fallback, String... candidates) {
        for (String candidate : candidates) {
            Path p = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.exists(p)) return p;
        }
        return Path.of(fallback).toAbsolutePath().normalize();
    }
    static final Path AUTHORED_BROWSE_FOLDER = resolveExisting(
            "D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/examples",
            "D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/examples",
            "D:\\_CODE_BANK\\Project_\\08_Thesis\\use\\use-plugin\\examples",
            "examples", "use-plugin/examples", "../use-plugin/examples", "../../use-plugin/examples");
    static final Path JACAMO_BROWSE_FOLDER = resolveExisting(
            "D:/_CODE_BANK/Project_/vnu-sme-lab/jacamo/examples",
            "D:/_CODE_BANK/Project_/vnu-sme-lab/jacamo/examples",
            "D:\\_CODE_BANK\\Project_\\vnu-sme-lab\\jacamo\\examples",
            "../jacamo/examples", "../../jacamo/examples", "jacamo/examples");
    static final Path RUNTIME_LIBS_BROWSE_FOLDER = resolveExisting("../jacamo-bridge-jacamo/target", "../jacamo-bridge-jacamo/target", "jacamo-bridge-jacamo/target", "use/jacamo-bridge-jacamo/target");
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
    private final JButton startRuntime = named(new JButton("Start", WorkbenchIcons.getStartIcon()), "start-runtime");
    private final JButton pauseRuntime = named(new JButton("Pause Jason"), "pause-runtime");
    private final JButton resumeRuntime = named(new JButton("Resume Jason"), "resume-runtime");
    private final JButton stopMonitor = named(new JButton("Stop monitor"), "stop-monitor");
    private final JButton restartMonitor = named(new JButton("Restart monitor"), "restart-monitor");
    private final JButton prepareProducer=named(new JButton("Prepare + Import..."),"prepare-producer");
    private final JButton stopProducer=named(new JButton("Stop", WorkbenchIcons.getStopIcon()),"stop-producer");
    private final JButton restartProducer=named(new JButton("Restart", WorkbenchIcons.getRestartIcon()),"restart-producer");
    private final javax.swing.JCheckBox strictSourceChecksums=named(new javax.swing.JCheckBox("Strict source checksums",false),"strict-source-checksums");
    private final javax.swing.JCheckBox showProducerGui=named(new javax.swing.JCheckBox("Show JaCaMo GUI",true),"show-producer-gui");
    private final javax.swing.JTextField runtimeClasspath=named(new javax.swing.JTextField(48),"runtime-classpath");
    private final JLabel producerState=named(new JLabel("IDLE"),"owned-producer-state");
    private final JButton loadProfile = named(new JButton("Load OCL..."), "load-profile");
    private final javax.swing.JComboBox<String> engineProfile = named(new javax.swing.JComboBox<>(new String[]{"NATIVE_CURRENT","JACAMO_DECLARATIVE_XML","USE4GOAL_OBSERVED_V1","CSL_OBSERVER_V1","CSL_OBSERVER_V2"}),"engine-profile");
    private final javax.swing.JTextField mappingFile=named(new javax.swing.JTextField(38),"mapping-specification");
    private final javax.swing.JTextField authoredOcl=named(new javax.swing.JTextField(52),"authored-ocl");
    private final javax.swing.JComboBox<String> modelInputMode=named(new javax.swing.JComboBox<>(new String[]{"GENERATED","AUTHORED"}),"model-input-mode");
    private final javax.swing.JTextField authoredCsl=named(new javax.swing.JTextField(42),"authored-csl");
    private final javax.swing.JTextField authoredIstar=named(new javax.swing.JTextField(42),"authored-istar");
    private final javax.swing.JTextField journalBudget=named(new javax.swing.JTextField(5),"journal-budget-mib");
    private final JLabel programSummary=named(new JLabel("No canonical program imported"),"program-summary");
    private final javax.swing.JTextArea programTrace=named(new javax.swing.JTextArea(),"program-trace");
    private final boolean selectable;
    private final boolean observedWorkflow;
    private final javax.swing.JTextField caseFolder=named(new javax.swing.JTextField(52),"case-folder");
    private final javax.swing.JTextField selectedJcm=named(new javax.swing.JTextField(42),"selected-jcm");
    private Map<String,Object> displayedProgram;
    private final Map<String, JButton> actionButtons = new LinkedHashMap<>();
    private boolean autoImportStarted;
    private javax.swing.JDialog resultsWindow;
    private final JLabel checkpointMode=named(new JLabel("Verified checkpoints"),"checkpoint-execution-mode");
    private final JLabel checkpointStatus=named(new JLabel("Passive observation"),"verified-checkpoint-status");
    private final JLabel checkpointResultStatus=named(new JLabel("Passive observation"),"verified-checkpoint-result-status");
    private final JButton acknowledgeCheckpoint=named(new JButton("Continue"),"acknowledge-checkpoint");
    private final JButton acknowledgeResultCheckpoint=named(new JButton("Continue"),"acknowledge-result-checkpoint");
    private final VerificationStatusBadge verificationBadge=new VerificationStatusBadge();
    private final JTabbedPane tabs=named(new JTabbedPane(),"workbench-tabs");
    private final JTabbedPane cslTabs=named(new JTabbedPane(),"csl-tabs");
    private final JTabbedPane istarTabs=named(new JTabbedPane(),"istar-tabs");
    private final JTabbedPane diagnostics=named(new JTabbedPane(),"diagnostic-tabs");
    private org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews runtimeStateViews;
    private SemanticEvidencePanel semanticEvidence;
    private ObservationRequirementsPanel observationRequirements;
    private org.tzi.use.plugins.jacamo.ui.use4goal.csl.CslView cslView;
    private org.tzi.use.plugins.jacamo.ui.use4goal.istar.IStarView istarView;
    private org.tzi.use.plugins.jacamo.ui.use4goal.state.IStarInstancesPanel istarInstances;
    private org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel observedConformance;
    private final org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePresenter observedPresenter;
    private org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult observedDisplayedResult;
    private boolean observedRefreshPending;
    private AutoCloseable checkpointPresentation;
    private boolean checkpointLive;
    private long presentationEpoch;
    private long observedDisplayedHistory=-1;
    private final org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.PreparationCache observedPreparation=new org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.PreparationCache();
    private final JLabel runtimeProgress=named(new JLabel("Waiting for runtime observations"),"runtime-progress");
    private org.tzi.use.uml.mm.MModel displayedNativeModel;
    private int backgroundOperations;
    private long viewEpoch;
    // Refresh only cached facade status, never start a second runtime or a network resync.
    private final javax.swing.Timer runtimeStatusTimer = new javax.swing.Timer(200, event -> {
        refreshRuntimeIfIdle();
    });
    private void refreshRuntimeIfIdle() {
        var ingress=facade.ingressProgress();
        if(!ingress.isEmpty())runtimeProgress.setText("Events saved: "+ingress.get("saved")+" · processed: "+ingress.get("processed")+" · waiting: "+ingress.get("pending")+(ingress.getOrDefault("failure","").toString().isBlank()?"":" · "+ingress.get("failure")));
        if (backgroundOperations == 0 && !facade.stepReplayBusy()) refreshRuntime();
    }

    @Override public void addNotify() {
        super.addNotify();
        registerCheckpointPresentation();
        runtimeStatusTimer.start();
    }

    @Override public void removeNotify() {
        runtimeStatusTimer.stop();
        if(checkpointPresentation!=null){try{checkpointPresentation.close();}catch(Exception ignored){}checkpointPresentation=null;}
        presentationEpoch++;
        if(resultsWindow!=null){resultsWindow.dispose();resultsWindow=null;}
        detachSemanticViews();
        viewEpoch++;
        super.removeNotify();
    }

    public JaCaMoWorkbenchPanel(JaCaMoFacade facade) {
        this(facade, null);
    }

    JaCaMoWorkbenchPanel(JaCaMoFacade facade, Consumer<String> errorPresenter) {
        this(facade,errorPresenter,false);
    }
    public static JaCaMoWorkbenchPanel authoredWorkflow(JaCaMoFacade facade){return new JaCaMoWorkbenchPanel(facade,null,true);}
    JaCaMoWorkbenchPanel(JaCaMoFacade facade, Consumer<String> errorPresenter,boolean observedWorkflow) {
        super(new BorderLayout(8, 8));
        this.observedWorkflow=observedWorkflow;
        this.facade = java.util.Objects.requireNonNull(facade, "facade");
        this.observedPresenter = new org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePresenter(facade);
        tabs.addChangeListener(e->refreshGraphVisibility());
        cslTabs.addChangeListener(e->refreshGraphVisibility());
        istarTabs.addChangeListener(e->refreshGraphVisibility());
        tabs.addHierarchyListener(e->{if((e.getChangeFlags()&java.awt.event.HierarchyEvent.SHOWING_CHANGED)!=0)refreshGraphVisibility();});
        this.errorPresenter = errorPresenter!=null?errorPresenter:message->JOptionPane.showMessageDialog(this,new JLabel(displayMessage(message)),"JaCaMo",JOptionPane.ERROR_MESSAGE);
        var selection=facade.importSelection();selectable=selection!=null;
        if(selectable){engineProfile.setSelectedItem(selection.profile());mappingFile.setText(selection.specification()==null?"":selection.specification().toString());authoredOcl.setText(selection.ocl()==null?"":selection.ocl().toString());}
        if(selectable){strictSourceChecksums.setSelected(!observedWorkflow&&selection.strictSourceChecksums());modelInputMode.setSelectedItem(selection.csl()==null?"GENERATED":"AUTHORED");authoredCsl.setText(selection.csl()==null?"":selection.csl().toString());authoredIstar.setText(selection.istar()==null?"":selection.istar().toString());}
        if(observedWorkflow){engineProfile.setSelectedItem("JACAMO_DECLARATIVE_XML");modelInputMode.setSelectedItem("AUTHORED");
            var hint=projectFileHint();if(hint!=null)selectedJcm.setText(hint.toString());}
        engineProfile.addActionListener(event->refreshWorkflowControls());
        modelInputMode.addActionListener(event->refreshWorkflowControls());
        startRuntime.addActionListener(event -> startRuntime());
        startRuntime.setToolTipText("Release reasoning in the separately launched JaCaMo process after model import");
        pauseRuntime.setToolTipText("Pause Jason at reasoning boundaries; CArtAgO/Moise operations may continue");
        pauseRuntime.addActionListener(event -> executeBackground("Pause failed", () -> facade.pauseRuntime("Workbench manual pause").join(),this::refreshRuntime));
        resumeRuntime.addActionListener(event -> executeBackground("Resume failed", () -> facade.resumeRuntime().join(),this::refreshRuntime));
        loadProfile.addActionListener(event -> chooseProfile());
        stopMonitor.setToolTipText("Close USE observation and semantic history; the external JaCaMo producer keeps running");
        restartMonitor.setToolTipText("Reconnect this monitor with fresh semantic history; does not restart the producer");
        stopMonitor.addActionListener(event->stopMonitorSession());restartMonitor.addActionListener(event->restartMonitorSession());
        if(!observedWorkflow)prepareProducer.addActionListener(event->{var chooser=createProjectChooser(jcmBrowseHint());if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)prepareOwnedProject(chooser.getSelectedFile().toPath());});
        prepareProducer.setToolTipText("Stage a copy, launch an owned external JaCaMo JVM and import into this USE session; Start Runtime releases reasoning");
        stopProducer.addActionListener(event->stopOwnedProject());
        restartProducer.addActionListener(event->{verificationBadge.reset();executeBackground("JaCaMo restart failed",facade::restartOwnedProducer,this::refreshRuntime);});
        String configuredClasspath=System.getProperty("use.jacamo.runtime.classpath-file","").trim();
        Path checkoutClasspath=RUNTIME_LIBS_BROWSE_FOLDER.resolve("jacamo-bridge-classpath.txt");
        Path suggestedClasspath=facade.defaultRuntimeClasspathFile();
        runtimeClasspath.setText(!configuredClasspath.isEmpty()?configuredClasspath:
                suggestedClasspath!=null&&Files.isRegularFile(suggestedClasspath)?suggestedClasspath.toString():Files.isRegularFile(checkoutClasspath)?checkoutClasspath.toString():suggestedClasspath==null?"":suggestedClasspath.toString());
        runtimeClasspath.setToolTipText("Loaded automatically for producer startup; Runtime libs can override the selection");
        engineProfile.setToolTipText("Projection compatibility: use JACAMO_DECLARATIVE_XML for current model folders");
        modelInputMode.setToolTipText("AUTHORED loads CSL/iStar files; GENERATED builds models from explicit XML rules");
        journalBudget.setText(Long.toString((facade.nextJournalBudgetBytes()+1048575)/1048576));
        journalBudget.setToolTipText("Disk journal quota for the next new monitor, 1–1024 MiB. Exhaustion keeps a coverage GAP; no old evidence is discarded to manufacture completeness.");
        add(observedWorkflow?observedToolbar():toolbar(), BorderLayout.NORTH);
        if(observedWorkflow){tabs.addTab("CSL",cslTabs);tabs.addTab("iStar",istarTabs);tabs.addTab("Advanced",diagnostics);}
        if(!observedWorkflow)diagnosticTabs().addTab("Projection Rules", new MappingRulesPanel());
        if(selectable&&!observedWorkflow){programTrace.setEditable(false);programTrace.setLineWrap(true);diagnosticTabs().addTab("Program / trace",new javax.swing.JScrollPane(programTrace));}
        if(!observedWorkflow)add(tabs, BorderLayout.CENTER);
        if(observedWorkflow)status.setVisible(false);
        add(status, BorderLayout.SOUTH);
        refreshRuntime();
        registerCheckpointPresentation();
        autoImportIfConfigured();
    }
    private void registerCheckpointPresentation(){
        if(observedWorkflow&&checkpointPresentation==null)checkpointPresentation=facade.registerCheckpointPresenter(this::presentCheckpoint);
    }
    private java.util.concurrent.CompletableFuture<Void> presentCheckpoint(
            org.tzi.use.plugins.jacamo.codegrounded.runtime.VerifiedCheckpointService.View checkpoint,
            org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot cut,
            Map<String,org.jacamo.bridge.contract.VerifiedCheckpointProtocol.Check> checks) {
        var displayed=new java.util.concurrent.CompletableFuture<Void>();
        javax.swing.SwingUtilities.invokeLater(()->{
            try {
                if(checkpointPresentation==null||!java.awt.GraphicsEnvironment.isHeadless()&&!isShowing())throw new IllegalStateException("CHECKPOINT_VIEW_NOT_SHOWING");
                refreshSemanticViews();showResultsWindow();
                var panel=observedConformance;if(panel==null)throw new IllegalStateException("CHECKPOINT_MODEL_VIEW_UNAVAILABLE");
                long ticket=++presentationEpoch,epoch=viewEpoch;checkpointLive=true;
                class CheckpointPresentation extends javax.swing.SwingWorker<org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.PreparedCuts,Void> {
                    private java.util.List<org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut> captured;
                    private final String scope=panel.executionScope();
                    CheckpointPresentation(java.util.List<org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut> captured){this.captured=captured;}
                    @Override protected org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.PreparedCuts doInBackground(){
                        if(captured==null)captured=observedPresenter.captureCheckpoint(cut,checkpoint,checks);
                        return new org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.PreparationCache().prepare(captured,scope,true,true);
                    }
                    @Override protected void done(){
                        try {
                            if(ticket!=presentationEpoch||epoch!=viewEpoch||panel!=observedConformance||checkpointPresentation==null)throw new IllegalStateException("CHECKPOINT_VIEW_REPLACED");
                            var prepared=get();
                            // The user may switch execution while this worker prepares
                            // the immutable cut. Preserve that choice at publication.
                            if(!panel.executionScope().equals(scope)){new CheckpointPresentation(captured).execute();return;}
                            panel.showCheckpoint(prepared);if(istarInstances!=null)istarInstances.update(prepared.all());observedDisplayedResult=cut.result();
                            verificationBadge.displayed(cut.result().sessionId()+":"+cut.result().generation()+":"+cut.result().modelRevision(),checkpoint.token(),captured.getLast(),checks);
                            runtimeProgress.setText("Checkpoint "+checkpoint.checkpoint()+" · USE state "+cut.currentVersion()+" · evaluated and displayed · JaCaMo waiting for decision");
                            refreshRuntime();
                            // Flush this exact immutable cut before acknowledging presentation.
                            // Native work, disk history and layout preparation have already finished.
                            if(tabs.isShowing())tabs.paintImmediately(tabs.getVisibleRect());
                            displayed.complete(null);
                        }catch(Exception failure){displayed.completeExceptionally(failure);}
                    }
                }
                new CheckpointPresentation(null).execute();
            }catch(Exception failure){displayed.completeExceptionally(failure);}
        });
        return displayed;
    }

    public void importProject(Path jcmFile) {
        importSelectedProject(jcmFile,false);
    }
    public void prepareOwnedProject(Path jcmFile){importSelectedProject(jcmFile,true);}
    public void stopOwnedProject(){
        // Cancellation must remain available while the preparation worker is busy.
        stopProducer.setEnabled(false);
        new javax.swing.SwingWorker<Void,Void>() {
            @Override protected Void doInBackground(){facade.stopOwnedProducer();return null;}
            @Override protected void done(){try{get();setStatus("Owned JaCaMo stopped");}catch(Exception error){errorPresenter.accept("JaCaMo stop failed: "+error.getMessage());}finally{refreshRuntime();}}
        }.execute();
    }
    private void importSelectedProject(Path jcmFile,boolean owned) {
        Path normalized = java.util.Objects.requireNonNull(jcmFile, "jcmFile").toAbsolutePath().normalize();
        // Capture widget values before leaving the EDT. Parsing/validation belongs
        // to the background facade call, and selection takes effect only on Import.
        JaCaMoFacade.ImportSelection selection;
        try {
            boolean declarative=engineProfile.getSelectedItem().equals("JACAMO_DECLARATIVE_XML"),authored=declarative&&modelInputMode.getSelectedItem().equals("AUTHORED");
            if(authored&&(authoredCsl.getText().isBlank()||authoredIstar.getText().isBlank()))throw new IllegalArgumentException("SELECT_BOTH_AUTHORED_CSL_AND_ISTAR");
            selection=selectable?new JaCaMoFacade.ImportSelection((String)engineProfile.getSelectedItem(),engineProfile.getSelectedItem().equals("NATIVE_CURRENT")||mappingFile.getText().isBlank()?null:Path.of(mappingFile.getText().trim()),declarative&&!authoredOcl.getText().isBlank()?Path.of(authoredOcl.getText().trim()):null,
                    authored?Path.of(authoredCsl.getText().trim()):null,authored?Path.of(authoredIstar.getText().trim()):null,strictSourceChecksums.isSelected()):null;
        }
        catch(RuntimeException error){setStatus("Import failed: "+error.getMessage());errorPresenter.accept("Import failed: "+error.getMessage());return;}
        Path classpath;
        try{classpath=runtimeClasspath.getText().isBlank()?null:Path.of(runtimeClasspath.getText().trim());}
        catch(RuntimeException error){errorPresenter.accept("Runtime classpath invalid: "+error.getMessage());return;}
        long budget;
        try{long mib=Long.parseLong(journalBudget.getText().trim());if(mib<1||mib>1024)throw new NumberFormatException();budget=mib*1048576;}
        catch(NumberFormatException invalid){errorPresenter.accept("Journal quota must be an integer from 1 to 1024 MiB");return;}
        boolean showGui=showProducerGui.isSelected();
        boolean verified=observedWorkflow||facade.verifiedCheckpointMode();
        executeBackground("Import failed", () -> {if(owned){try{facade.verifiedCheckpointMode(verified);}catch(UnsupportedOperationException ignored){}}if(facade.journalBudgetSupported())facade.nextJournalBudgetBytes(budget);if(owned)facade.prepareOwnedProducer(normalized,selection,classpath,showGui);else if(selection==null)facade.importProject(normalized);else facade.importProject(normalized,selection);}, () -> {
            checkpointLive=false;presentationEpoch++;verificationBadge.reset();
            refreshRuntime();
            setStatus("Imported " + normalized);
            writeReadyEvidence(normalized);
        });
    }

    public void loadVerificationProfile(Path profile) {
        executeBackground("Profile load failed", () -> facade.loadVerificationProfile(profile), this::refreshRuntime);
    }

    public void startRuntime() {
        verificationBadge.reset();
        showResultsWindow();
        executeBackground("Runtime start failed", facade::startRuntime, ()->{refreshRuntime();showResultsWindow();});
    }
    public void stopMonitorSession(){if(backgroundOperations!=0){setStatus("WORKBENCH_BUSY: wait for the current operation");return;}viewEpoch++;executeBackground("Stop monitor failed",facade::stopMonitorSession,this::refreshRuntime);}
    public void restartMonitorSession(){if(backgroundOperations!=0){setStatus("WORKBENCH_BUSY: wait for the current operation");return;}viewEpoch++;verificationBadge.reset();executeBackground("Restart monitor failed",facade::restartMonitorSession,this::refreshRuntime);}

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
        var held=facade.verifiedCheckpointState();
        var badge=verificationBadge.status(held,facade.ownedProducerStatus().state(),facade.workflowStatus().state());
        VerificationStatusBadge.apply(checkpointStatus,badge);VerificationStatusBadge.apply(checkpointResultStatus,badge);
        String checkpointTip="Completed refers to terminal selected requirements in all currently observed executions; it does not mean the MAS process has exited. Historical issues remain recorded. | "+held.reason()+" | checkpoint="+held.checkpoint()+" | executions="+held.executionScope()+" | token="+held.token()+" | result="+held.resultHash();
        checkpointStatus.setToolTipText(checkpointTip);checkpointResultStatus.setToolTipText(checkpointTip);
        boolean canContinue=held.reviewable()&&!held.pending();
        acknowledgeCheckpoint.setVisible(canContinue);acknowledgeResultCheckpoint.setVisible(canContinue);
        acknowledgeCheckpoint.setEnabled(canContinue);acknowledgeResultCheckpoint.setEnabled(canContinue);
        String reviewTip="Record review and continue this exact checkpoint. Original state, verdict and evidence remain in history.";
        acknowledgeCheckpoint.setToolTipText(reviewTip);acknowledgeResultCheckpoint.setToolTipText(reviewTip);
        runtimeState.setText(facade.runtimeStatus().state().name());
        var program=facade.semanticProgramEvidence();
        if(program!=displayedProgram&&!program.equals(displayedProgram)) {
            displayedProgram=program;
            if(!observedWorkflow&&program.get("bindingPreflight") instanceof Map<?,?> rawPreflight){
                if(observationRequirements==null){observationRequirements=new ObservationRequirementsPanel();diagnosticTabs().addTab("Observation plan",observationRequirements);}
                observationRequirements.refresh(CanonicalJson.object(rawPreflight));
            }else if(observationRequirements!=null){diagnosticTabs().remove(observationRequirements);observationRequirements=null;}
            programSummary.setText(displayMessage(program.getOrDefault("profile","No program")+" | "+program.getOrDefault("mapping","")+" | "+program.getOrDefault("programHash",program.getOrDefault("status",""))));
            if(program.get("caseProgram") instanceof Map<?,?> rawCase) {
                var caseProgram=CanonicalJson.object(rawCase);
                var activeSelection=facade.importSelection();
                programSummary.setText(displayMessage("Active "+(activeSelection==null?"canonical case":activeSelection.profile())+" | XML SHA-256 "+caseProgram.get("specificationHash")+" | Source model SHA-256 "+caseProgram.get("sourceHash")));
                programSummary.setToolTipText(displayMessage("Program SHA-256 "+program.get("programHash")+" | Bundle SHA-256 "+caseProgram.get("bundleHash")+" | Engine policy "+program.get("profile")+" | Case OCL contracts installed during Import; Load OCL adds an optional invariant profile."));
            }else if(program.get("declarativeProgram") instanceof Map<?,?> declarative) {
                programSummary.setText(displayMessage((declarative.containsKey("modelInputMode")?"Authored CSL / iStar":"Generated CSL / iStar")+" | XML SHA-256 "+declarative.get("specificationHash")));
                programSummary.setToolTipText(displayMessage("Authored OCL SHA-256 "+declarative.get("oclHash")+" | Program SHA-256 "+program.get("programHash")));
            }else programSummary.setToolTipText(null);
            if(selectable){String text=new String(CanonicalJson.encode(program),java.nio.charset.StandardCharsets.UTF_8);programTrace.setText(text.length()>131072?text.substring(0,131072)+"\nDISPLAY TRUNCATED; full trace is available through the facade":text);programTrace.setCaretPosition(0);}
        }
        refreshSemanticViews();
        var result=facade.runtimeVerificationResult();
        refreshObservedConformance(result);
        // The Model tab shows authored types. Runtime badges belong to State only.
        if(result!=null && result.semantic()!=null) {
            if(semanticEvidence==null){semanticEvidence=new SemanticEvidencePanel();diagnosticTabs().addTab(observedWorkflow?"Verification Details":"Semantic verification",semanticEvidence);}
            if(semanticEvidence.isShowing())semanticEvidence.refresh(result);
        } else if(semanticEvidence!=null) {diagnosticTabs().remove(semanticEvidence);semanticEvidence=null;}
    }
    private void detachSemanticViews() {
        if(istarInstances!=null){istarInstances.dispose();istarInstances=null;}
        if(runtimeStateViews!=null){runtimeStateViews.dispose();if(observedWorkflow){cslTabs.remove(runtimeStateViews.aolPanel);istarTabs.remove(runtimeStateViews.iscnPanel);}else{tabs.remove(runtimeStateViews.aolPanel);tabs.remove(runtimeStateViews.iscnPanel);}runtimeStateViews=null;}
        if(cslView!=null){cslView.detachModel();if(observedWorkflow)cslTabs.remove(cslView);else diagnosticTabs().remove(cslView);cslView=null;}
        if(istarView!=null){istarView.detachModel();if(observedWorkflow)istarTabs.remove(istarView);else diagnosticTabs().remove(istarView);istarView=null;}
        if(observedConformance!=null){if(observedWorkflow){cslTabs.remove(observedConformance.cslVerificationPanel());istarTabs.remove(observedConformance);}else tabs.remove(observedConformance);observedConformance=null;}
        observedDisplayedResult=null;observedDisplayedHistory=-1;
        displayedNativeModel=null;
    }
    private void refreshGraphVisibility(){
        if(runtimeStateViews!=null){
            boolean aolVisible = observedWorkflow ? tabs.isShowing()&&tabs.getSelectedComponent()==cslTabs&&cslTabs.getSelectedComponent()==runtimeStateViews.aolPanel : tabs.isShowing()&&tabs.getSelectedComponent()==runtimeStateViews.aolPanel;
            boolean iscnVisible = observedWorkflow ? tabs.isShowing()&&tabs.getSelectedComponent()==istarTabs&&istarTabs.getSelectedComponent()==istarInstances : tabs.isShowing()&&tabs.getSelectedComponent()==runtimeStateViews.iscnPanel;
            runtimeStateViews.setActive(aolVisible, false);if(istarInstances!=null)istarInstances.setActive(iscnVisible);
        }
    }
    private void refreshObservedConformance(org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult result) {
        // A verified run has one presentation authority, from the first Start:
        // presentCheckpoint renders an exact cut before its release. Replaying
        // raw ingress here used to animate startup prefixes, then append late
        // "settled" states between source checkpoints. Diagnostics/replay retain
        // that history without replacing the reviewed live state.
        if(observedWorkflow&&facade.verifiedCheckpointMode()&&facade.stepReplayStatus()==null)return;
        boolean settledCheckpoint=checkpointLive&&facade.verifiedCheckpointState().state().equals("RUNNING");
        if(checkpointLive&&!settledCheckpoint)return;
        if(observedConformance==null || observedRefreshPending)return;
        long history=facade.runtimeHistoryTail().persistedEntries();
        if(result==observedDisplayedResult&&history==observedDisplayedHistory&&!observedPresenter.historyPending())return;
        var panel=observedConformance;var model=displayedNativeModel;long epoch=viewEpoch;
        long presentation=presentationEpoch;
        observedRefreshPending=true;
        String requestedScope=panel.executionScope();
        new javax.swing.SwingWorker<org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.PreparedCuts,Void>() {
            private String progress;
            @Override protected org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.PreparedCuts doInBackground(){
                var cuts=settledCheckpoint?observedPresenter.captureSettled():observedWorkflow&&!facade.verifiedCheckpointState().token().isBlank()?observedPresenter.captureCurrent():observedPresenter.captureIncremental();
                var tail=facade.runtimeHistoryTail();var snapshot=facade.verificationSnapshot();
                long verified=tail.entries().stream().map(org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeHistoryPage.Entry::result).filter(r->r.freshness().equals("CURRENT_OBSERVED")&&r.semantic()!=null&&"PINNED_NATIVE_CUT".equals(CanonicalJson.object(r.semantic().observation().getOrDefault("originalEngine",Map.of())).get("evaluated"))).mapToLong(org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult::stateVersion).max().orElse(-1);
                progress="USE state: "+snapshot.currentVersion()+" · latest verified state: "+(verified<0?"pending":verified)+" · "+(tail.gap()?"GAP: "+tail.diagnostic():verified<snapshot.currentVersion()?"processing / awaiting evidence":"no state-version backlog")+" · journal: "+tail.persistedEntries();
                var ingress=facade.ingressProgress();if(!ingress.isEmpty())progress="Events saved: "+ingress.get("saved")+" · processed: "+ingress.get("processed")+" · waiting: "+ingress.get("pending")+" · "+progress;
                return observedPreparation.prepare(cuts,requestedScope,observedWorkflow,settledCheckpoint);
            }
            @Override protected void done(){
                try {
                    var cuts=get();
                    if(presentation==presentationEpoch&&(!checkpointLive||settledCheckpoint&&facade.verifiedCheckpointState().state().equals("RUNNING"))&&epoch==viewEpoch && observedConformance==panel && displayedNativeModel==model&&panel.executionScope().equals(requestedScope)){if(settledCheckpoint)panel.showCheckpoint(cuts);else panel.applyPrepared(cuts);if(istarInstances!=null)istarInstances.update(cuts.all());runtimeProgress.setText(progress);observedDisplayedResult=result;observedDisplayedHistory=history;}
                }catch(Exception error){
                    if(epoch==viewEpoch && observedConformance==panel)setStatus("Observed evidence unavailable: "+error.getMessage());
                }finally{observedRefreshPending=false;}
            }
        }.execute();
    }
    private void refreshSemanticViews() {
        var models=facade.semanticModels();
        if(models==null){if(displayedNativeModel!=null)detachSemanticViews();return;}
        if(models.nativeModel()==displayedNativeModel)return;
        detachSemanticViews();
        cslView=org.tzi.use.plugins.jacamo.ui.use4goal.csl.CslView.embedded(models.csl(),models.nativeModel());cslView.setName("canonical-csl-view");
        istarView=new org.tzi.use.plugins.jacamo.ui.use4goal.istar.IStarView();istarView.setModel(models.goals());istarView.setName("canonical-istar-view");
        var declarative=CanonicalJson.object(facade.semanticProgramEvidence().getOrDefault("declarativeProgram",Map.of()));
        observedConformance=observedWorkflow?org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.liveResults(models.goals(),declarative):new org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel();
        if(observedWorkflow){
            var policies=new java.util.TreeMap<String,org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander.Quantifier>();
            CanonicalJson.object(declarative.getOrDefault("dependencyPopulations",Map.of())).forEach((id,value)->policies.put(id,org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander.Quantifier.valueOf(value.toString())));
            runtimeStateViews=new org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews(models.csl(),models.goals(),policies);
            runtimeStateViews.setActive(false,false);
            istarInstances=new org.tzi.use.plugins.jacamo.ui.use4goal.state.IStarInstancesPanel(models.goals(),declarative);
            observedConformance.onSelectedCut(runtimeStateViews::select);
            observedConformance.onExecutionSelected(runtimeStateViews::selectScope);
            runtimeStateViews.onScopeSelected(observedConformance::selectExecution);
            cslTabs.removeAll();
            cslTabs.addTab("Model", cslView);
            cslTabs.addTab("State", runtimeStateViews.aolPanel);
            cslTabs.addTab("Verification", observedConformance.cslVerificationPanel());
            istarTabs.removeAll();
            istarTabs.addTab("Model", istarView);
            istarTabs.addTab("State", istarInstances);
            istarTabs.addTab("Verification", observedConformance);

        }else{
            tabs.addTab("use4Goal observed execution",observedConformance);
            tabs.addTab("CSL model",cslView);tabs.addTab("iStar model",istarView);
        }displayedNativeModel=models.nativeModel();
        if(observedWorkflow)tabs.setSelectedComponent(cslTabs);
    }

    private JTabbedPane diagnosticTabs(){return observedWorkflow?diagnostics:tabs;}
    JTabbedPane resultsContent(){return tabs;}
    void showResultsWindow(){
        if(!observedWorkflow||java.awt.GraphicsEnvironment.isHeadless()||!isShowing())return;
        var window=ensureResultsWindow(javax.swing.SwingUtilities.getWindowAncestor(this));
        window.setVisible(true);window.toFront();
    }
    javax.swing.JDialog ensureResultsWindow(java.awt.Window owner){
        if(!observedWorkflow)throw new IllegalStateException("DETACHED_RESULTS_REQUIRE_AUTHORED_WORKFLOW");
        if(resultsWindow==null||!resultsWindow.isDisplayable()){
            resultsWindow=new javax.swing.JDialog(owner,"use4Goal — Verification results",java.awt.Dialog.ModalityType.MODELESS);
            resultsWindow.setName("use4goal-results-window");resultsWindow.setDefaultCloseOperation(javax.swing.JDialog.HIDE_ON_CLOSE);
            resultsWindow.add(tabs,BorderLayout.CENTER);
            var checkpointControls=checkpointHeader(checkpointResultStatus,acknowledgeResultCheckpoint);checkpointControls.setBorder(javax.swing.BorderFactory.createEmptyBorder(6,12,6,12));
            acknowledgeResultCheckpoint.addActionListener(event->reviewActiveCheckpoint());resultsWindow.add(checkpointControls,BorderLayout.NORTH);
            resultsWindow.setSize(1250,780);resultsWindow.setLocationRelativeTo(owner);
        }
        return resultsWindow;
    }

    private JPanel observedToolbar(){
        var panel=new JPanel();panel.setLayout(new javax.swing.BoxLayout(panel,javax.swing.BoxLayout.Y_AXIS));
        var inputs=new org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ConformanceInputsPanel(selectedJcm,caseFolder,authoredCsl,authoredIstar,mappingFile,authoredOcl,
                this::chooseObservedJcm,this::chooseCaseFolder,
                prepareProducer,()->prepareObservedProject(true));
        registerInputButtons(inputs);panel.add(inputs);
        var controls=new JPanel(new FlowLayout(FlowLayout.LEADING));
        controls.add(startRuntime);controls.add(stopProducer);controls.add(restartProducer);
        acknowledgeCheckpoint.addActionListener(event->reviewActiveCheckpoint());
        controls.add(button("Show results","show-results",this::showResultsWindow));
        var advancedToggle=named(new javax.swing.JToggleButton("Advanced controls"),"show-advanced");controls.add(advancedToggle);panel.add(controls);
        var checkpointControls=checkpointHeader(checkpointStatus,acknowledgeCheckpoint);checkpointControls.add(checkpointMode,BorderLayout.WEST);panel.add(checkpointControls);
        var sourceStatus=new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));sourceStatus.add(new JLabel("Bridge:"));sourceStatus.add(runtimeState);sourceStatus.add(new JLabel("·"));sourceStatus.add(producerState);panel.add(sourceStatus);
        var progressRow=new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 1));progressRow.add(runtimeProgress);panel.add(progressRow);
        var summaryRow=new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 1));summaryRow.add(programSummary);panel.add(summaryRow);
        var advanced=named(new JPanel(new GridLayout(0,1)),"advanced-controls");advanced.setVisible(false);
        var cycleControls=new JPanel(new FlowLayout(FlowLayout.LEADING));cycleControls.add(new JLabel("Jason cycle controls:"));cycleControls.add(pauseRuntime);cycleControls.add(resumeRuntime);advanced.add(cycleControls);
        var transport=new JPanel(new FlowLayout(FlowLayout.LEADING));transport.add(button("Import external project","import-project",()->prepareObservedProject(false)));
        transport.add(stopMonitor);transport.add(restartMonitor);advanced.add(transport);
        var profiles=new JPanel(new FlowLayout(FlowLayout.LEADING));profiles.add(new JLabel("Compatibility profile:"));profiles.add(engineProfile);profiles.add(modelInputMode);profiles.add(strictSourceChecksums);advanced.add(profiles);
        var runtime=new JPanel(new FlowLayout(FlowLayout.LEADING));runtime.add(new JLabel("Runtime dependency classpath (auto-loaded):"));runtime.add(runtimeClasspath);
        runtime.add(button("Runtime libs...","choose-runtime-classpath",this::chooseRuntimeLibraries));advanced.add(runtime);
        var exports=new JPanel(new FlowLayout(FlowLayout.LEADING));
        exports.add(new JLabel("Journal quota (MiB):"));exports.add(journalBudget);advanced.add(exports);panel.add(advanced);
        advancedToggle.addActionListener(event->{advanced.setVisible(advancedToggle.isSelected());revalidate();var owner=javax.swing.SwingUtilities.getWindowAncestor(this);if(owner!=null)owner.setSize(owner.getWidth(),Math.max(440,owner.getPreferredSize().height));});
        var footer=new JPanel(new FlowLayout(FlowLayout.LEADING,6,2));footer.add(showProducerGui);panel.add(footer);
        return panel;
    }
    private void registerInputButtons(java.awt.Container parent){for(var child:parent.getComponents()){
        if(child instanceof JButton button&&button.getName()!=null&&!button.getName().equals("prepare-producer"))actionButtons.put(button.getName(),button);
        if(child instanceof java.awt.Container nested)registerInputButtons(nested);
    }}
    private void chooseObservedJcm(){
        var chooser=createProjectChooser(jcmBrowseHint());
        if(!selectedJcm.getText().isBlank()){
            Path current=Path.of(selectedJcm.getText().trim());
            if(Files.exists(current))chooser.setSelectedFile(current.toFile());
        }
        if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)
            selectedJcm.setText(chooser.getSelectedFile().toPath().toString());
    }
    private void chooseCaseFolder(){
        var chooser=authoredInputChooser();chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setDialogTitle("Select system model specification (*.mrv)");
        chooser.resetChoosableFileFilters();chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("System model (*.mrv)","mrv"));

        if(!caseFolder.getText().isBlank()){
            Path current=Path.of(caseFolder.getText().trim());
            if(Files.exists(current))chooser.setSelectedFile(current.toFile());
            else if(Files.isDirectory(AUTHORED_BROWSE_FOLDER))chooser.setCurrentDirectory(AUTHORED_BROWSE_FOLDER.toFile());
        }
        if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)
            execute("Model file selection failed",()->selectCaseFolder(chooser.getSelectedFile().toPath()));
    }
    void selectCaseFolder(Path directory){
        var selected=CaseStudyFiles.read(directory);
        caseFolder.setText(selected.folder().toString());authoredCsl.setText(selected.csl().toString());
        authoredIstar.setText(selected.istar().toString());mappingFile.setText(selected.mapping().toString());
        authoredOcl.setText(selected.ocl()==null?"":selected.ocl().toString());
    }
    private void prepareObservedProject(boolean owned){
        try {
            if(selectedJcm.getText().isBlank()){chooseObservedJcm();if(selectedJcm.getText().isBlank())return;}
            if(!caseFolder.getText().isBlank())selectCaseFolder(Path.of(caseFolder.getText()));
            if(mappingFile.getText().isBlank()||authoredCsl.getText().isBlank()||authoredIstar.getText().isBlank())throw new IllegalArgumentException("SELECT_SYSTEM_MODEL_MRV_WITH_CSL_ISTAR_XML");
            importSelectedProject(Path.of(selectedJcm.getText().trim()),owned);
        }catch(RuntimeException error){setStatus("Import failed: "+error.getMessage());errorPresenter.accept("Import failed: "+error.getMessage());}
    }

    private JPanel toolbar() {
        JPanel toolbar = new JPanel(new GridLayout(0, 1));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        actions.add(button("Import JaCaMo Project...", "import-project", this::chooseProject));
        actions.add(loadProfile);
        actions.add(startRuntime);
        actions.add(pauseRuntime);actions.add(resumeRuntime);
        actions.add(stopMonitor);actions.add(restartMonitor);
        JPanel exports = new JPanel(new FlowLayout(FlowLayout.LEADING));
        exports.add(button("Export .use...", "export-use", this::chooseNativeUse));
        exports.add(button("Export .cmd...", "export-soil", this::chooseNativeSoil));
        exports.add(new JLabel("Workflow:"));
        exports.add(workflowState);
        exports.add(new JLabel("Runtime:"));
        exports.add(runtimeState);
        toolbar.add(actions);
        if(facade.ownedProducerSupported()){
            var producerActions=new JPanel(new FlowLayout(FlowLayout.LEADING));producerActions.add(prepareProducer);producerActions.add(stopProducer);producerActions.add(restartProducer);producerActions.add(showProducerGui);producerActions.add(producerState);toolbar.add(producerActions);
        }
        if(selectable) {
            var selection=new JPanel(new FlowLayout(FlowLayout.LEADING));selection.add(new JLabel("On next Import:"));selection.add(engineProfile);selection.add(mappingFile);
            selection.add(button("Mapping/spec...","choose-mapping",this::chooseMapping));toolbar.add(selection);toolbar.add(programSummary);
            var oclSelection=new JPanel(new FlowLayout(FlowLayout.LEADING));oclSelection.add(new JLabel("Authored OCL (declarative XML):"));oclSelection.add(authoredOcl);
            oclSelection.add(button("OCL input...","choose-authored-ocl",this::chooseAuthoredOcl));toolbar.add(oclSelection);
            var modelSelection=new JPanel(new FlowLayout(FlowLayout.LEADING));modelSelection.add(new JLabel("Model input mode:"));modelSelection.add(modelInputMode);toolbar.add(modelSelection);
            var cslSelection=new JPanel(new FlowLayout(FlowLayout.LEADING));cslSelection.add(new JLabel("Authored CSL:"));cslSelection.add(authoredCsl);cslSelection.add(button("CSL input...","choose-authored-csl",()->chooseModel("csl",authoredCsl)));toolbar.add(cslSelection);
            var istarSelection=new JPanel(new FlowLayout(FlowLayout.LEADING));istarSelection.add(new JLabel("Authored iStar:"));istarSelection.add(authoredIstar);istarSelection.add(button("iStar input...","choose-authored-istar",()->chooseModel("istar",authoredIstar)));toolbar.add(istarSelection);
        }
        toolbar.add(exports);
        if(facade.ownedProducerSupported()){
            var runtime=new JPanel(new FlowLayout(FlowLayout.LEADING));runtime.add(new JLabel("JaCaMo runtime classpath file (optional in checkout):"));runtime.add(runtimeClasspath);
            runtime.add(button("Runtime libs...","choose-runtime-classpath",this::chooseRuntimeLibraries));toolbar.add(runtime);
        }
        if(facade.journalBudgetSupported()){
            var storage=new JPanel(new FlowLayout(FlowLayout.LEADING));storage.add(new JLabel("On next new monitor: journal quota (MiB)"));storage.add(journalBudget);storage.add(new JLabel("1–1024; quota exhaustion remains a coverage GAP"));toolbar.add(storage);
        }
        return toolbar;
    }

    private void chooseProject() {
        JFileChooser chooser = createProjectChooser(jcmBrowseHint());
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) importProject(chooser.getSelectedFile().toPath());
    }
    private void chooseMapping() {
        Path hint=null;try{if(!mappingFile.getText().isBlank())hint=Path.of(mappingFile.getText().trim());}catch(java.nio.file.InvalidPathException ignored){/* The chooser can replace invalid input. */}
        var chooser=createMappingChooser((String)engineProfile.getSelectedItem(),Files.isDirectory(AUTHORED_BROWSE_FOLDER)?AUTHORED_BROWSE_FOLDER:hint);
        if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)mappingFile.setText(chooser.getSelectedFile().toPath().toString());
    }
    static JFileChooser createMappingChooser(String profile,Path hint) {
        var chooser=createProjectChooser(hint);chooser.resetChoosableFileFilters();chooser.setAcceptAllFileFilterUsed(false);
        boolean xml="JACAMO_DECLARATIVE_XML".equals(profile);chooser.setDialogTitle(xml?"Select mapping specification":"Select authored mapping/configuration");
        chooser.setFileFilter(xml?new FileNameExtensionFilter("Mapping specification (*.xml)","xml"):new FileNameExtensionFilter("Authored mapping/config (*.json)","json"));
        return chooser;
    }
    private void chooseAuthoredOcl() {
        var chooser=authoredInputChooser();chooser.setDialogTitle("Select authored verification requirements");chooser.setAcceptAllFileFilterUsed(false);chooser.setFileFilter(new FileNameExtensionFilter("OCL requirements (*.ocl)","ocl"));
        if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)authoredOcl.setText(chooser.getSelectedFile().toPath().toString());
    }
    private void chooseModel(String extension,javax.swing.JTextField field) {
        var chooser=authoredInputChooser();chooser.setDialogTitle("Select authored ."+extension+" model");chooser.setAcceptAllFileFilterUsed(false);chooser.setFileFilter(new FileNameExtensionFilter("Authored model (*."+extension+")",extension));
        if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)field.setText(chooser.getSelectedFile().toPath().toString());
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

    private static Path jcmBrowseHint(){return Files.isDirectory(JACAMO_BROWSE_FOLDER)?JACAMO_BROWSE_FOLDER:projectFileHint();}
    private void chooseRuntimeLibraries(){
        var chooser=createRuntimeLibrariesChooser();
        if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)runtimeClasspath.setText(chooser.getSelectedFile().toPath().toString());
    }
    static JFileChooser createRuntimeLibrariesChooser(){
        var chooser=new JFileChooser();
        chooser.setDialogTitle("Select JaCaMo runtime dependency classpath text file");
        chooser.setFileFilter(new FileNameExtensionFilter("Runtime classpath (*.txt)","txt"));
        if(Files.isDirectory(RUNTIME_LIBS_BROWSE_FOLDER))chooser.setCurrentDirectory(RUNTIME_LIBS_BROWSE_FOLDER.toFile());
        return chooser;
    }
    private static JFileChooser authoredInputChooser(){
        var chooser=new JFileChooser();
        if(Files.isDirectory(AUTHORED_BROWSE_FOLDER))chooser.setCurrentDirectory(AUTHORED_BROWSE_FOLDER.toFile());
        return chooser;
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
        workflowState.setToolTipText(workflow.managed()?"Managed launcher run "+workflow.runId()+"; only Start Runtime releases its startup ACK":"External producer is not owned by this monitor; manage its process with its launcher");
        startRuntime.setEnabled(!busy && !replayActive && workflow.startAvailable());
        var control=facade.runtimeControlState();
        pauseRuntime.setEnabled(!busy && !replayActive && control!=null && control.capable() && control.liveReady() && control.state().name().equals("RUNNING")
                && java.util.Set.of("RUNNING","LIVE").contains(workflow.state()));
        resumeRuntime.setEnabled(!busy && !replayActive && control!=null && control.capable() && control.state().name().equals("PAUSED")
                && java.util.Set.of("RUNNING","LIVE").contains(workflow.state()));
        loadProfile.setEnabled(!busy && !replayActive && facade.projectSummary() != null&&!workflow.state().equals("MONITOR_STOPPED"));
        boolean imported=facade.projectSummary()!=null;
        stopMonitor.setEnabled(!busy&&!replayActive&&imported&&!workflow.state().equals("MONITOR_STOPPED"));
        restartMonitor.setEnabled(!busy&&!replayActive&&imported);
        var producer=facade.ownedProducerStatus();
        boolean ownedActive=java.util.Set.of("PREPARING","WAITING_FOR_START","RUNNING","EXITED").contains(producer.state());
        producerState.setText(producer.state()+" | JaCaMo PID="+producer.pid()+" | USE PID="+ProcessHandle.current().pid());producerState.setToolTipText(producer.diagnostic());
        prepareProducer.setEnabled(!busy&&!replayActive&&!ownedActive);
        checkpointMode.setEnabled(!busy&&!replayActive&&!ownedActive);
        showProducerGui.setEnabled(!busy&&!replayActive&&!ownedActive);
        stopProducer.setEnabled(!replayActive&&ownedActive);
        restartProducer.setEnabled(!busy&&!replayActive&&!producer.runId().isBlank()&&!producer.state().equals("ERROR"));
        runtimeClasspath.setEnabled(!busy&&!replayActive);
        journalBudget.setEnabled(!busy&&!replayActive);
        selectedJcm.setEnabled(!busy&&!replayActive);caseFolder.setEnabled(!busy&&!replayActive);
        engineProfile.setEnabled(!busy&&!replayActive);mappingFile.setEnabled(!busy&&!replayActive&&!"NATIVE_CURRENT".equals(engineProfile.getSelectedItem()));
        boolean declarative=!busy&&!replayActive&&"JACAMO_DECLARATIVE_XML".equals(engineProfile.getSelectedItem());authoredOcl.setEnabled(declarative);
        modelInputMode.setEnabled(declarative);boolean authored=declarative&&"AUTHORED".equals(modelInputMode.getSelectedItem());authoredCsl.setEnabled(authored);authoredIstar.setEnabled(authored);
        actionButtons.forEach((name, button) -> button.setEnabled(!busy));
        actionButtons.get("import-project").setEnabled(!busy && !replayActive);
        if(observedWorkflow)actionButtons.get("choose-case-folder").setEnabled(!busy&&!replayActive);
        if(selectable&&!observedWorkflow)actionButtons.get("choose-mapping").setEnabled(!busy&&!replayActive&&!"NATIVE_CURRENT".equals(engineProfile.getSelectedItem()));
        if(selectable&&!observedWorkflow)actionButtons.get("choose-authored-ocl").setEnabled(declarative);
        if(selectable&&!observedWorkflow){actionButtons.get("choose-authored-csl").setEnabled(authored);actionButtons.get("choose-authored-istar").setEnabled(authored);}
    }

    private void setStatus(String message) { status.setText(displayMessage(message)); }
    static JPanel checkpointHeader(JLabel status,JButton continuation){
        var header=named(new JPanel(new BorderLayout()),"verification-status-header");
        var right=new JPanel(new FlowLayout(FlowLayout.TRAILING,6,2));right.add(status);right.add(continuation);
        header.add(right,BorderLayout.EAST);return header;
    }
    private void reviewActiveCheckpoint(){
        var active=facade.verifiedCheckpointState();
        if(!active.reviewable())return;
        acknowledgeCheckpoint.setEnabled(false);acknowledgeResultCheckpoint.setEnabled(false);
        var result=facade.acknowledgeVerifiedCheckpoint(active.token(),active.resultHash());
        refreshRuntime();
        result.whenComplete((receipt,error)->javax.swing.SwingUtilities.invokeLater(()->{
            if(error!=null)errorPresenter.accept("Checkpoint continuation unavailable: "+error.getMessage());refreshRuntime();
        }));
    }

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
