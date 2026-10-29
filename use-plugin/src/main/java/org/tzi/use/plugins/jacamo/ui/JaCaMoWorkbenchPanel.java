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
    private static final Path AUTHORED_BROWSE_FOLDER=Path.of("D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/examples");
    private static final Path JACAMO_BROWSE_FOLDER=Path.of("D:/_CODE_BANK/Project_/08_Thesis/jacamo/examples");
    private static final Path RUNTIME_LIBS_BROWSE_FOLDER=Path.of("D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/target");
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
    private final JButton pauseRuntime = named(new JButton("Pause Jason"), "pause-runtime");
    private final JButton resumeRuntime = named(new JButton("Resume Jason"), "resume-runtime");
    private final JButton stopMonitor = named(new JButton("Stop monitor"), "stop-monitor");
    private final JButton restartMonitor = named(new JButton("Restart monitor"), "restart-monitor");
    private final JButton prepareProducer=named(new JButton("Prepare + Import..."),"prepare-producer");
    private final JButton stopProducer=named(new JButton("Stop JaCaMo"),"stop-producer");
    private final JButton restartProducer=named(new JButton("Restart JaCaMo"),"restart-producer");
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
    private final javax.swing.JTextField selectedJcm=named(new javax.swing.JTextField(42),"selected-jcm");
    private Map<String,Object> displayedProgram;
    private final Map<String, JButton> actionButtons = new LinkedHashMap<>();
    private boolean autoImportStarted;
    private javax.swing.JDialog resultsWindow;
    private final JTabbedPane tabs=named(new JTabbedPane(),"workbench-tabs");
    private final JTabbedPane diagnostics=named(new JTabbedPane(),"diagnostic-tabs");
    private SemanticEvidencePanel semanticEvidence;
    private ObservationRequirementsPanel observationRequirements;
    private org.tzi.use.plugins.jacamo.ui.use4goal.csl.CslView cslView;
    private org.tzi.use.plugins.jacamo.ui.use4goal.istar.IStarView istarView;
    private org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel observedConformance;
    private final org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePresenter observedPresenter;
    private org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult observedDisplayedResult;
    private boolean observedRefreshPending;
    private long observedDisplayedHistory=-1;
    private org.tzi.use.uml.mm.MModel displayedNativeModel;
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
        restartProducer.addActionListener(event->executeBackground("JaCaMo restart failed",facade::restartOwnedProducer,this::refreshRuntime));
        runtimeClasspath.setText(System.getProperty("use.jacamo.runtime.classpath-file",""));
        journalBudget.setText(Long.toString((facade.nextJournalBudgetBytes()+1048575)/1048576));
        journalBudget.setToolTipText("Disk journal quota for the next new monitor, 1–1024 MiB. Exhaustion keeps a coverage GAP; no old evidence is discarded to manufacture completeness.");
        add(observedWorkflow?observedToolbar():toolbar(), BorderLayout.NORTH);
        if(observedWorkflow)tabs.addTab("Advanced / Diagnostics",diagnostics);
        diagnosticTabs().addTab("Projection Rules", new MappingRulesPanel());
        if(selectable){programTrace.setEditable(false);programTrace.setLineWrap(true);diagnosticTabs().addTab("Program / trace",new javax.swing.JScrollPane(programTrace));}
        if(!observedWorkflow)add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        refreshRuntime();
        autoImportIfConfigured();
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
        executeBackground("Import failed", () -> {if(facade.journalBudgetSupported())facade.nextJournalBudgetBytes(budget);if(owned)facade.prepareOwnedProducer(normalized,selection,classpath,showGui);else if(selection==null)facade.importProject(normalized);else facade.importProject(normalized,selection);}, () -> {
            refreshRuntime();
            setStatus("Imported " + normalized);
            writeReadyEvidence(normalized);
        });
    }

    public void loadVerificationProfile(Path profile) {
        executeBackground("Profile load failed", () -> facade.loadVerificationProfile(profile), this::refreshRuntime);
    }

    public void startRuntime() {
        executeBackground("Runtime start failed", facade::startRuntime, ()->{refreshRuntime();showResultsWindow();});
    }
    public void stopMonitorSession(){if(backgroundOperations!=0){setStatus("WORKBENCH_BUSY: wait for the current operation");return;}viewEpoch++;executeBackground("Stop monitor failed",facade::stopMonitorSession,this::refreshRuntime);}
    public void restartMonitorSession(){if(backgroundOperations!=0){setStatus("WORKBENCH_BUSY: wait for the current operation");return;}viewEpoch++;executeBackground("Restart monitor failed",facade::restartMonitorSession,this::refreshRuntime);}

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
        var program=facade.semanticProgramEvidence();
        if(program!=displayedProgram&&!program.equals(displayedProgram)) {
            displayedProgram=program;
            if(program.get("bindingPreflight") instanceof Map<?,?> rawPreflight){
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
        if(istarView!=null)istarView.setNodeBadges(SemanticDiagramBadges.from(result));
        if(result!=null && result.semantic()!=null) {
            if(semanticEvidence==null){semanticEvidence=new SemanticEvidencePanel();diagnosticTabs().addTab(observedWorkflow?"Verification Details":"Semantic verification",semanticEvidence);}
            semanticEvidence.refresh(result);
        } else if(semanticEvidence!=null) {diagnosticTabs().remove(semanticEvidence);semanticEvidence=null;}
    }
    private void detachSemanticViews() {
        if(cslView!=null){cslView.detachModel();tabs.remove(cslView);cslView=null;}
        if(istarView!=null){istarView.detachModel();tabs.remove(istarView);istarView=null;}
        if(observedConformance!=null){tabs.remove(observedConformance);observedConformance=null;}
        observedDisplayedResult=null;observedDisplayedHistory=-1;
        displayedNativeModel=null;
    }
    private void refreshObservedConformance(org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult result) {
        if(observedConformance==null || observedRefreshPending)return;
        long history=facade.runtimeHistoryTail().persistedEntries();
        if(result==observedDisplayedResult&&history==observedDisplayedHistory)return;
        var panel=observedConformance;var model=displayedNativeModel;long epoch=viewEpoch;
        observedRefreshPending=true;
        new javax.swing.SwingWorker<java.util.List<org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut>,Void>() {
            @Override protected java.util.List<org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut> doInBackground(){return observedPresenter.capture();}
            @Override protected void done(){
                try {
                    var cuts=get();
                    if(epoch==viewEpoch && observedConformance==panel && displayedNativeModel==model){panel.setCuts(cuts);observedDisplayedResult=result;observedDisplayedHistory=history;}
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
        observedConformance=observedWorkflow?org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.liveResults(models.goals()):new org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel();
        if(observedWorkflow){
            tabs.insertTab("Execution & Results",null,observedConformance,null,0);
            tabs.insertTab("CSL model",null,cslView,null,1);tabs.insertTab("iStar model",null,istarView,null,2);
        }else{
            tabs.addTab("use4Goal observed execution",observedConformance);
            tabs.addTab("CSL model",cslView);tabs.addTab("iStar model",istarView);
        }displayedNativeModel=models.nativeModel();
        if(observedWorkflow)tabs.setSelectedComponent(observedConformance);
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
            resultsWindow.add(tabs,BorderLayout.CENTER);resultsWindow.setSize(1250,780);resultsWindow.setLocationRelativeTo(owner);
        }
        return resultsWindow;
    }

    private JPanel observedToolbar(){
        var panel=new JPanel();panel.setLayout(new javax.swing.BoxLayout(panel,javax.swing.BoxLayout.Y_AXIS));
        var inputs=new org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ConformanceInputsPanel(selectedJcm,authoredCsl,authoredIstar,mappingFile,authoredOcl,
                this::chooseObservedJcm,()->chooseModel("csl",authoredCsl),()->chooseModel("istar",authoredIstar),this::chooseMapping,this::chooseAuthoredOcl,
                prepareProducer,()->prepareObservedProject(true));
        registerInputButtons(inputs);panel.add(inputs);
        var controls=new JPanel(new FlowLayout(FlowLayout.LEADING));
        controls.add(startRuntime);controls.add(pauseRuntime);controls.add(resumeRuntime);controls.add(stopProducer);controls.add(restartProducer);
        controls.add(showProducerGui);controls.add(button("Show results","show-results",this::showResultsWindow));
        var advancedToggle=named(new javax.swing.JToggleButton("Advanced controls"),"show-advanced");controls.add(advancedToggle);panel.add(controls);
        var sourceStatus=new JPanel(new FlowLayout(FlowLayout.LEADING));sourceStatus.add(new JLabel("Workflow:"));sourceStatus.add(workflowState);sourceStatus.add(new JLabel("Bridge:"));sourceStatus.add(runtimeState);panel.add(sourceStatus);
        panel.add(producerState);panel.add(programSummary);
        var advanced=named(new JPanel(new GridLayout(0,1)),"advanced-controls");advanced.setVisible(false);
        var transport=new JPanel(new FlowLayout(FlowLayout.LEADING));transport.add(button("Import external project","import-project",()->prepareObservedProject(false)));
        transport.add(stopMonitor);transport.add(restartMonitor);advanced.add(transport);
        var profiles=new JPanel(new FlowLayout(FlowLayout.LEADING));profiles.add(new JLabel("Compatibility profile:"));profiles.add(engineProfile);profiles.add(modelInputMode);profiles.add(strictSourceChecksums);advanced.add(profiles);
        var runtime=new JPanel(new FlowLayout(FlowLayout.LEADING));runtime.add(new JLabel("Runtime dependency classpath (optional in checkout):"));runtime.add(runtimeClasspath);
        runtime.add(button("Runtime libs...","choose-runtime-classpath",this::chooseRuntimeLibraries));advanced.add(runtime);
        var exports=new JPanel(new FlowLayout(FlowLayout.LEADING));
        exports.add(new JLabel("Journal quota (MiB):"));exports.add(journalBudget);advanced.add(exports);panel.add(advanced);
        advancedToggle.addActionListener(event->{advanced.setVisible(advancedToggle.isSelected());revalidate();var owner=javax.swing.SwingUtilities.getWindowAncestor(this);if(owner!=null)owner.setSize(owner.getWidth(),Math.max(440,owner.getPreferredSize().height));});
        return panel;
    }
    private void registerInputButtons(java.awt.Container parent){for(var child:parent.getComponents()){
        if(child instanceof JButton button&&button.getName()!=null&&!button.getName().equals("prepare-producer"))actionButtons.put(button.getName(),button);
        if(child instanceof java.awt.Container nested)registerInputButtons(nested);
    }}
    private void chooseObservedJcm(){var chooser=createProjectChooser(jcmBrowseHint());if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)selectedJcm.setText(chooser.getSelectedFile().toPath().toString());}
    private void prepareObservedProject(boolean owned){
        try {
            if(selectedJcm.getText().isBlank()){chooseObservedJcm();if(selectedJcm.getText().isBlank())return;}
            if(mappingFile.getText().isBlank()||authoredOcl.getText().isBlank()||authoredCsl.getText().isBlank()||authoredIstar.getText().isBlank())throw new IllegalArgumentException("SELECT_JCM_CSL_ISTAR_MAPPING_AND_OCL");
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
        showProducerGui.setEnabled(!busy&&!replayActive&&!ownedActive);
        stopProducer.setEnabled(!replayActive&&ownedActive);
        restartProducer.setEnabled(!busy&&!replayActive&&!producer.runId().isBlank()&&!producer.state().equals("ERROR"));
        runtimeClasspath.setEnabled(!busy&&!replayActive);
        journalBudget.setEnabled(!busy&&!replayActive);
        selectedJcm.setEnabled(!busy&&!replayActive);
        engineProfile.setEnabled(!busy&&!replayActive);mappingFile.setEnabled(!busy&&!replayActive&&!"NATIVE_CURRENT".equals(engineProfile.getSelectedItem()));
        boolean declarative=!busy&&!replayActive&&"JACAMO_DECLARATIVE_XML".equals(engineProfile.getSelectedItem());authoredOcl.setEnabled(declarative);
        modelInputMode.setEnabled(declarative);boolean authored=declarative&&"AUTHORED".equals(modelInputMode.getSelectedItem());authoredCsl.setEnabled(authored);authoredIstar.setEnabled(authored);
        actionButtons.forEach((name, button) -> button.setEnabled(!busy));
        actionButtons.get("import-project").setEnabled(!busy && !replayActive);
        if(selectable)actionButtons.get("choose-mapping").setEnabled(!busy&&!replayActive&&!"NATIVE_CURRENT".equals(engineProfile.getSelectedItem()));
        if(selectable)actionButtons.get("choose-authored-ocl").setEnabled(declarative);
        if(selectable){actionButtons.get("choose-authored-csl").setEnabled(authored);actionButtons.get("choose-authored-istar").setEnabled(authored);}
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
