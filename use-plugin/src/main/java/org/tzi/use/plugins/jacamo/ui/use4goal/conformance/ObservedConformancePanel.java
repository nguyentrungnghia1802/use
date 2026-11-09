package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/**
 * Selected UI workflow extracted from use4Goal CslStateEvaluatorForm (GPL-3.0).
 * See META-INF/use4goal-workflow-provenance.json for source bytes and patches.
 * The original split layout and state navigation are retained; accepted native
 * evidence replaces the BPMN/AOL compiler and whole-process solver backend.
 * This component owns immutable presentation values only.
 */
public final class ObservedConformancePanel extends JPanel {
    public record Evaluation(String element, String raw, String status, String quality, String evidence,String certifiedHistory,boolean required,String kind,String originalStatus,String player,String context,String occurrence) {
        public Evaluation(String element,String raw,String status,String quality,String evidence,String history,boolean required,String kind,String originalStatus){this(element,raw,status,quality,evidence,history,required,kind,originalStatus,"","","");}
        public Evaluation(String element,String raw,String status,String quality,String evidence,String history,boolean required,String kind){this(element,raw,status,quality,evidence,history,required,kind,kind.equals("GOAL_TASK")?raw:"");}
        public Evaluation(String element,String raw,String status,String quality,String evidence,String history,boolean required){this(element,raw,status,quality,evidence,history,required,"");}
        public Evaluation(String element,String raw,String status,String quality,String evidence,String history){this(element,raw,status,quality,evidence,history,true);}
        public Evaluation(String element,String raw,String status,String quality,String evidence){this(element,raw,status,quality,evidence,"",true);}
    }
    public record Cut(String id, String trace, String state, List<Evaluation> evaluations,
                      String coverage, String freshness, boolean failed,String businessKey,String requirements,String businessState,boolean executionMilestones,boolean selectedRequirements,boolean businessPopulationEmpty,java.util.Map<String,Object> visualEvidence) {
        public Cut(String id,String trace,String state,List<Evaluation> evaluations,String coverage,String freshness,boolean failed,String businessKey,String requirements,String businessState,boolean executionMilestones,boolean selectedRequirements,boolean businessPopulationEmpty){this(id,trace,state,evaluations,coverage,freshness,failed,businessKey,requirements,businessState,executionMilestones,selectedRequirements,businessPopulationEmpty,java.util.Map.of());}
        public Cut(String id,String trace,String state,List<Evaluation> evaluations,String coverage,String freshness,boolean failed,String businessKey,String requirements,String businessState,boolean executionMilestones,boolean selectedRequirements){this(id,trace,state,evaluations,coverage,freshness,failed,businessKey,requirements,businessState,executionMilestones,selectedRequirements,false);}
        public Cut(String id,String trace,String state,List<Evaluation> evaluations,String coverage,String freshness,boolean failed,String businessKey,String requirements,String businessState,boolean executionMilestones){this(id,trace,state,evaluations,coverage,freshness,failed,businessKey,requirements,businessState,executionMilestones,false);}
        public Cut(String id,String trace,String state,List<Evaluation> evaluations,String coverage,String freshness,boolean failed,String businessKey,String requirements,String businessState){this(id,trace,state,evaluations,coverage,freshness,failed,businessKey,requirements,businessState,true);}
        public Cut(String id,String trace,String state,List<Evaluation> evaluations,String coverage,String freshness,boolean failed,String businessKey,String requirements){this(id,trace,state,evaluations,coverage,freshness,failed,businessKey,requirements,"");}
        public Cut(String id,String trace,String state,List<Evaluation> evaluations,String coverage,String freshness,boolean failed){this(id,trace,state,evaluations,coverage,freshness,failed,state,"","");}
        public Cut { evaluations = List.copyOf(evaluations); visualEvidence=java.util.Map.copyOf(visualEvidence); }
    }
    private static final int NARROW_SPLIT_WIDTH = 500;
    private final boolean liveOnly;
    private LiveGoalSummary liveSummary;
    private java.util.List<LiveGoalSummary.Row> liveRows=java.util.List.of();
    private java.util.List<LiveGoalSummary.Row> goalRows=java.util.List.of();
    private java.util.List<LiveGoalSummary.Row> taskRows=java.util.List.of();
    private List<Evaluation> displayedInstances=List.of();
    private List<Evaluation> displayedChecks=List.of();
    private record OccurrenceEntry(String key,String recordType,String occurrenceId,String episode,String invocationId,String checkpointId,String checkpointLabel,String resultLabel,String rawStatus,String certifiedStatus,String quality,String role,String evidence,String certifiedHistory){}
    private record AgentInstanceGroup(String key,String player,String subjectLabel,String context,String executionLabel,String resultLabel,List<Evaluation> currentEvaluations,List<OccurrenceEntry> history){}
    private List<AgentInstanceGroup> displayedAgentRows=List.of();
    private List<OccurrenceEntry> displayedOccurrences=List.of();
    private String selectedElementId="";
    private String selectedAgentKey="";
    private String selectedOccurrenceKey="";
    private boolean updatingElementSelection,updatingAgentSelection,updatingOccurrenceSelection;
    private final DefaultTableModel liveGoals=new DefaultTableModel(new String[]{"Goal","Result"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final DefaultTableModel liveTasks=new DefaultTableModel(new String[]{"Task","Result"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final DefaultTableModel liveInstances=new DefaultTableModel(new String[]{"Agent / Player","Execution","Result"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final DefaultTableModel liveOccurrences=new DefaultTableModel(new String[]{"Type","Occurrence","Invocation","Checkpoint","Result"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final JTable liveInstanceTable=named(new JTable(liveInstances),"live-goal-instances");
    private final JTable liveOccurrenceTable=named(new JTable(liveOccurrences),"live-agent-occurrences");
    private final JLabel selectedElementLabel=named(new JLabel("No Goal or Task selected — select a Goal or Task above to inspect agent instances"),"selected-goal-task-label");
    private JSplitPane goalTaskSplitPane;
    private final JTabbedPane liveDetailTabs=named(new JTabbedPane(),"live-detail-tabs");
    private final JLabel requirementScope=named(new JLabel("No selected verification state"),"observed-requirement-scope");
    private final DefaultTableModel liveChecks=new DefaultTableModel(new String[]{"Name","Body","Result"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final JLabel liveDiagnosticNote = named(new JLabel("Checkpoint and Task diagnostics are retained in Audit details."), "checkpoint-diagnostics-note");
    private final JTable liveCheckTable=named(new JTable(liveChecks),"live-ocl-checks");
    private final JTable liveGoalTable=named(new JTable(liveGoals),"live-goal-status");
    private final JTable liveTaskTable=named(new JTable(liveTasks),"live-task-status");
    private String selectedAuditText="";
    /** Read-only diagnostic text; no evidence editor or panel is part of the results interface. */
    public String selectedAuditText(){return selectedAuditText;}
    private void setAuditText(String text){selectedAuditText=text;}
    private final JTextArea counterexampleTraceArea = new JTextArea();
    private final JTextArea stateSnapshotText = new JTextArea();
    private final JComboBox<String> stateSelectorCombo = named(new JComboBox<>(),"observed-state-selector");
    private final JButton statePrevButton = named(new JButton("< Previous State"), "observed-previous");
    private final JButton stateNextButton = named(new JButton("Next State >"), "observed-next");
    private final JButton jumpCheckpointButton = named(new JButton("Failure Point"), "observed-failure");

    // Companion controls for CSL verification navigation
    private final JComboBox<String> cslStateSelectorCombo = new JComboBox<>();
    private final JButton cslStatePrevButton = new JButton("< Previous State");
    private final JButton cslStateNextButton = new JButton("Next State >");
    private final JButton cslJumpCheckpointButton = new JButton("Failure Point");
    private final JButton cslMilestonesButton = new JButton("States");
    private final JLabel cslStateStepInfoLabel = new JLabel("State 0 / 0");
    private final JComboBox<String> cslExecutionSelector = new JComboBox<>();
    private final JComboBox<String> cslFailureFilter = new JComboBox<>(new String[]{"All failures","Goal / Task","OCL / constraints","Diagnostics","Unmet conditions"});
    private final JComboBox<FailurePoint> cslFailurePoints = new JComboBox<>();
    private final JLabel cslRequirementScope = new JLabel("No selected verification state");

    private JPanel cslVerificationPanel;
    private JPanel istarVerificationPanel;
    private JPanel hiddenPanel;

    public JPanel cslVerificationPanel() { return cslVerificationPanel; }
    public JPanel istarVerificationPanel() { return istarVerificationPanel; }

    private record FailurePoint(Cut cut,Evaluation evaluation,String category,int boundary,int milestone,boolean audit) {
        @Override public String toString(){
            String object=evaluation!=null&&evaluation.kind().equals("INVARIANT_BODY")?evaluation.context():"";
            int separator=object.lastIndexOf(" / ");if(separator>=0)object=object.substring(separator+3);
            return (audit?"Boundary "+boundary:milestone==0?"Before first state":"State "+milestone)+" · "+category+" · "+(evaluation==null?"verification failure":evaluation.element()+" ["+evaluation.status()+"]")+(object.isBlank()?"":" · "+object);
        }
    }
    private final JComboBox<String> failureFilter=named(new JComboBox<>(new String[]{"All failures","Goal / Task","OCL / constraints","Diagnostics","Unmet conditions"}),"observed-failure-filter");
    private final JComboBox<FailurePoint> failurePoints=named(new JComboBox<>(),"observed-failure-points");
    private boolean updatingFailures;
    private FailurePoint failureFocus;
    private List<Cut> businessMilestones=List.of();
    private java.util.Map<String,Integer> milestoneNumbers=java.util.Map.of();
    private final JToggleButton auditToggle=named(new JToggleButton("Audit details"),"observed-audit-toggle");
    private final JButton milestonesButton=named(new JButton("States"),"observed-milestones");
    private final JLabel stateStepInfoLabel = named(new JLabel("State 0 / 0"),"observed-state-position");
    private final JLabel conformanceResultLabel = new JLabel("NOT_EVALUATED", SwingConstants.CENTER);
    private final JLabel riskResultLabel = named(new JLabel("UNAVAILABLE", SwingConstants.CENTER), "observed-quality");
    private final JLabel istarDataCoverageLabel = named(new JLabel("UNAVAILABLE", SwingConstants.CENTER), "istar-data-coverage");
    private final DefaultTableModel oclGoalModel = new DefaultTableModel(
            new String[]{"Goal / Task / OCL", "Raw", "Qualified / propagated", "Quality", "Evidence", "Last certified history"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable oclGoalTable = named(new JTable(oclGoalModel), "observed-goals");
    private List<Cut> currentCounterexampleStates = List.of();
    private List<Cut> auditCuts=List.of(),allCuts=List.of();
    private final JComboBox<String> executionSelector=named(new JComboBox<>(),"execution-scope-selector");
    private java.util.Map<String,String> executionLabels=java.util.Map.of();
    private boolean updatingExecutions;
    private String executionScope="";
    private boolean checkpointTimeline;
    private record Cursor(String cut,boolean follow){}
    private final java.util.Map<String,Cursor> executionCursors=new java.util.HashMap<>();
    private java.util.function.Consumer<String> executionListener=id->{};
    public void onExecutionSelected(java.util.function.Consumer<String> listener){executionListener=java.util.Objects.requireNonNull(listener);}
    public void selectExecution(String id){
        if(id==null||id.isBlank()||java.util.Objects.equals(executionScope,id))return;
        rememberCursor();executionScope=id;failureFocus=null;intervalIds=null;selectedStateIndex=-1;
        setCuts(allCuts);restoreCursor();executionListener.accept(executionScope);
    }
    private void rememberCursor(){if(!executionScope.isBlank()&&selectedCut()!=null)executionCursors.put(executionScope,new Cursor(selectedCut().id(),selectedStateIndex==currentCounterexampleStates.size()-1));}
    private void restoreCursor(){var cursor=executionCursors.get(executionScope);if(cursor==null||cursor.follow())return;for(int i=0;i<currentCounterexampleStates.size();i++)if(currentCounterexampleStates.get(i).id().equals(cursor.cut())||!details.isSelected()&&milestoneNumbers.containsKey(cursor.cut())&&milestoneNumbers.get(cursor.cut()).equals(milestoneNumbers.get(currentCounterexampleStates.get(i).id()))){updateSelectedCounterexampleState(i);break;}}
    private void refreshExecutions(){
        updatingExecutions=true;
        try{var groups=ExecutionGroups.of(allCuts);executionScope=groups.canonical(executionScope);executionLabels=ExecutionScopeViews.withAll(groups.choices());
            executionSelector.removeAllItems();cslExecutionSelector.removeAllItems();
            executionLabels.keySet().forEach(k->{executionSelector.addItem(k);cslExecutionSelector.addItem(k);});
            if(!executionLabels.containsKey(executionScope))executionScope=executionLabels.isEmpty()?"":executionLabels.keySet().iterator().next();
            executionSelector.setSelectedItem(executionScope);
            cslExecutionSelector.setSelectedItem(executionScope);
        }finally{updatingExecutions=false;}
    }
    private java.util.Set<String> intervalIds;
    private final JCheckBox details=named(new JCheckBox("Show all audit boundaries",false),"observed-details");
    private int selectedStateIndex = -1;
    private int currentFailureCheckpoint = -1;
    private boolean updatingStateComboInternal;

    public ObservedConformancePanel() {this(false);}
    public static ObservedConformancePanel liveResults(org.vnu.sme.goal.dsl.istar.mm.GoalModel model){var panel=new ObservedConformancePanel(true);panel.liveSummary=new LiveGoalSummary(model);panel.renderLive(null);return panel;}
    public static ObservedConformancePanel liveResults(org.vnu.sme.goal.dsl.istar.mm.GoalModel model,java.util.Map<String,Object> mapping){var panel=new ObservedConformancePanel(true);panel.liveSummary=new LiveGoalSummary(model,mapping);panel.renderLive(null);return panel;}
    private ObservedConformancePanel(boolean liveOnly) {
        super(new BorderLayout());
        this.liveOnly=liveOnly;
        setName("use4goal-observed-conformance");
        stateSnapshotText.setEditable(false);
        stateSnapshotText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        stateSelectorCombo.setPrototypeDisplayValue("State 000000");
        stateSelectorCombo.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                super.getListCellRendererComponent(list,value,index,selected,focus);
                int ordinal=index<0?selectedStateIndex:index;
                int shown=details.isSelected()&&value!=null?java.util.stream.IntStream.range(0,auditCuts.size()).filter(i->auditCuts.get(i).id().equals(value)).findFirst().orElse(ordinal):liveOnly&&value!=null?milestoneNumbers.getOrDefault(value,0)-1:ordinal;
                setText(shown<0?(value==null?"Select state":"Before first state"):(details.isSelected()?"Boundary ":"State ")+(shown+1));setToolTipText(value==null?null:value.toString());return this;
            }
        });
        if(liveOnly){setName("use4goal-live-results");add(buildLiveResults(),BorderLayout.CENTER);return;}
        add(buildConformancePanel(), BorderLayout.CENTER);
        var controls=new JPanel(new FlowLayout(FlowLayout.LEFT,8,3));
        controls.add(details);
        add(controls,BorderLayout.NORTH);
        details.addActionListener(e->{intervalIds=null;details.setText("Show all audit boundaries");setCuts(allCuts);});
        var intervalButton=named(new JButton("Inspect state interval"),"observed-interval-details");
        controls.add(intervalButton);intervalButton.addActionListener(e->inspectInterval());
        statePrevButton.setEnabled(false);
        stateNextButton.setEnabled(false);
        jumpCheckpointButton.setEnabled(false);
    }

    private java.util.function.Consumer<Cut> selectionListener=cut->{};
    public void onSelectedCut(java.util.function.Consumer<Cut> listener){selectionListener=java.util.Objects.requireNonNull(listener);}
    public Cut selectedCut() {
        return selectedStateIndex < 0 ? null : currentCounterexampleStates.get(selectedStateIndex);
    }

    public record PreparedCuts(List<Cut> all,java.util.Map<String,String> labels,String scope,List<Cut> audit,List<Cut> milestones,java.util.Map<String,Integer> numbers,List<FailurePoint> failures,boolean checkpoints,HistorySummary history) {}
    public record HistorySummary(java.util.Map<String,Integer> positions,java.util.Map<String,Integer> goals,int firstFailure){
        static HistorySummary of(List<Cut> cuts){
            var positions=new java.util.HashMap<String,Integer>();var goals=new java.util.HashMap<String,Integer>();int first=Integer.MAX_VALUE;
            for(int i=0;i<cuts.size();i++){var cut=cuts.get(i);positions.put(cut.id(),i);
                if(cut.failed()||cut.evaluations().stream().anyMatch(r->r.required()&&java.util.Set.of("FAIL","VIOLATED").contains(r.status())))first=Math.min(first,i);
                for(var row:cut.evaluations())if(row.kind().equals("GOAL_TASK")&&java.util.Set.of("VIOLATED","FAILED").contains(row.status())&&row.quality().startsWith("KNOWN"))goals.putIfAbsent(row.element(),i);
            }
            return new HistorySummary(java.util.Map.copyOf(positions),java.util.Map.copyOf(goals),first);
        }
    }
    private HistorySummary historySummary=HistorySummary.of(List.of());
    /** Worker-owned cache. Reuse projections only for the identical immutable cut. */
    public static final class PreparationCache {
        private final java.util.IdentityHashMap<Cut,java.util.Map<String,Cut>> projections=new java.util.IdentityHashMap<>();
        public PreparedCuts prepare(List<Cut> input,String requested,boolean live){
            return prepare(input,requested,live,false);
        }
        public PreparedCuts prepare(List<Cut> input,String requested,boolean live,boolean checkpoints){
            var all=List.copyOf(input);var groups=ExecutionGroups.of(all);
            var labels=live?ExecutionScopeViews.withAll(groups.choices().isEmpty()&&!checkpoints?ExecutionScopeViews.choices(all):groups.choices()):java.util.Map.<String,String>of();
            String scope=groups.canonical(requested);
            if(live&&!labels.containsKey(scope))scope=labels.isEmpty()?"":labels.keySet().iterator().next();
            boolean awaiting=live&&scope.isBlank()&&all.stream().anyMatch(c->c.visualEvidence().containsKey("originalEngine"));
            var audit=new java.util.ArrayList<Cut>();
            var seenContexts=new java.util.LinkedHashSet<String>();
            if(!awaiting)for(var cut:all){String selected=scope;
                ExecutionScopeViews.contexts(cut).stream().filter(ExecutionScopeViews::boundContext).forEach(c->seenContexts.add(java.util.Objects.toString(c.get("context"))));
                var projected=scope.isBlank()?cut:projections.computeIfAbsent(cut,k->new java.util.HashMap<>()).computeIfAbsent(scope,k->ExecutionScopeViews.ALL.equals(selected)?ExecutionScopeViews.all(cut,seenContexts):groups.project(cut,selected));
                if(checkpoints&&!scope.isBlank()&&!ExecutionScopeViews.ALL.equals(scope)){
                    if(groups.contexts(cut,selected).stream().noneMatch(ExecutionScopeViews::boundContext)||!Boolean.TRUE.equals(ExecutionScopeViews.object(projected.visualEvidence().get("presentationState")).get("qualifiedValues")))continue;
                }
                audit.add(projected);
            }
            var grouping=checkpoints&&!scope.isBlank()?CheckpointTimeline.group(audit):null;
            var milestones=grouping!=null?grouping.milestones():checkpoints?List.copyOf(audit):BusinessTimeline.summarize(audit);
            if(!checkpoints&&!scope.isBlank()&&!milestones.isEmpty()&&!audit.isEmpty()&&audit.getLast().businessKey().isBlank()){
                var last=audit.getLast();var retained=new java.util.ArrayList<Cut>(milestones);var phase=retained.getLast();
                retained.set(retained.size()-1,new Cut(last.id(),last.trace(),last.state(),last.evaluations(),last.coverage(),last.freshness(),last.failed(),phase.businessKey(),last.requirements(),last.businessState(),last.executionMilestones(),last.selectedRequirements(),last.businessPopulationEmpty(),last.visualEvidence()));milestones=List.copyOf(retained);
            }
            var numbers=grouping!=null?grouping.numbers():BusinessTimeline.milestoneNumbers(audit,milestones);
            var keep=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Cut,Boolean>());keep.addAll(all);projections.keySet().retainAll(keep);
            return new PreparedCuts(all,labels,scope,List.copyOf(audit),milestones,numbers,live?collectFailurePoints(audit,numbers):List.of(),checkpoints,HistorySummary.of(audit));
        }
    }
    public String executionScope(){return executionScope;}
    public void showCheckpoint(PreparedCuts prepared){
        rememberCursor();
        failureFocus=null;intervalIds=null;details.setSelected(false);
        applyPrepared(prepared);restoreCursor();
    }
    private List<FailurePoint> preparedFailures=List.of();

    public void setCuts(List<Cut> cuts) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("OBSERVED_VIEW_REQUIRES_EDT");
        applyPrepared(new PreparationCache().prepare(cuts,executionScope,liveOnly,checkpointTimeline));
    }
    public void applyPrepared(PreparedCuts prepared){
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("OBSERVED_VIEW_REQUIRES_EDT");
        historySummary=prepared.history();checkpointTimeline=prepared.checkpoints();allCuts=prepared.all();executionScope=prepared.scope();
        if(liveOnly){updatingExecutions=true;try{
            if(!executionLabels.equals(prepared.labels())){
                executionLabels=prepared.labels();
                var choices = executionLabels.keySet().toArray(String[]::new);
                executionSelector.setModel(new DefaultComboBoxModel<>(choices));
                cslExecutionSelector.setModel(new DefaultComboBoxModel<>(choices));
            }
            executionSelector.setSelectedItem(executionScope);
            cslExecutionSelector.setSelectedItem(executionScope);
        }finally{updatingExecutions=false;}}
        auditCuts=prepared.audit();businessMilestones=prepared.milestones();milestoneNumbers=prepared.numbers();preparedFailures=prepared.failures();
        if(failureFocus!=null&&auditCuts.stream().noneMatch(c->c.id().equals(failureFocus.cut().id())))failureFocus=null;
        if(liveOnly)refreshFailurePoints();
        if(intervalIds!=null&&auditCuts.stream().noneMatch(c->intervalIds.contains(c.id()))){intervalIds=null;details.setSelected(false);details.setText("Show all audit boundaries");}
        var cuts=intervalIds!=null?auditCuts.stream().filter(c->intervalIds.contains(c.id())).toList():details.isSelected()?auditCuts:businessMilestones;
        if (currentCounterexampleStates.equals(cuts)) {
            if(liveOnly){
                boolean hasFailures = failurePoints.getItemCount()>0;
                jumpCheckpointButton.setEnabled(hasFailures);
                cslJumpCheckpointButton.setEnabled(hasFailures);
                if(selectedCut()!=null)updatePosition(selectedCut());
                stateSelectorCombo.repaint();
                cslStateSelectorCombo.repaint();
            }
            return;
        }
        String selected = selectedCut() == null ? "" : selectedCut().id();
        boolean follow = selectedStateIndex == currentCounterexampleStates.size() - 1;
        currentCounterexampleStates = List.copyOf(cuts);
        currentFailureCheckpoint = -1;
        int target = cuts.size() - 1;
        updatingStateComboInternal = true;
        try {
            var stateChoices = cuts.stream().map(Cut::id).toArray(String[]::new);
            stateSelectorCombo.setModel(new DefaultComboBoxModel<>(stateChoices));
            cslStateSelectorCombo.setModel(new DefaultComboBoxModel<>(stateChoices));
            for (int i = 0; i < cuts.size(); i++) {
                Cut cut = cuts.get(i);
                if (!follow && (cut.id().equals(selected)||!details.isSelected()&&milestoneNumbers.containsKey(selected)&&milestoneNumbers.get(selected).equals(milestoneNumbers.get(cut.id())))) target = i;
                if (currentFailureCheckpoint < 0 && cut.failed()) currentFailureCheckpoint = i;
            }
        } finally { updatingStateComboInternal = false; }
        updateSelectedCounterexampleState(target);
        if(liveOnly){
            boolean hasFailures = failurePoints.getItemCount()>0;
            jumpCheckpointButton.setEnabled(hasFailures);
            cslJumpCheckpointButton.setEnabled(hasFailures);
        }
    }

    private static boolean failureBoundary(Cut cut){return cut.failed()||cut.evaluations().stream().anyMatch(ObservedConformancePanel::failureEvaluation);}
    private static boolean failureEvaluation(Evaluation row){
        if(row.kind().equals("GOAL_TASK"))return java.util.Set.of("FAILED","VIOLATED").contains(row.status())&&java.util.Set.of("KNOWN","KNOWN_DERIVED","KNOWN_HISTORY","KNOWN_DERIVED_HISTORY").contains(row.quality());
        return java.util.Set.of("FAIL","FAILED","VIOLATED","ERROR","EXECUTION_FAILED").contains(row.status());
    }
    private static String failureKey(Evaluation row){return row.kind()+"\u0000"+row.element()+"\u0000"+row.context()+"\u0000"+row.occurrence();}
    private static boolean unmetCondition(Evaluation row){return java.util.Set.of("GOAL_CONDITION","TASK_PRE","TASK_POST").contains(row.kind())&&row.status().equals("CONDITION_FALSE");}
    private static List<FailurePoint> collectFailurePoints(List<Cut> auditCuts,java.util.Map<String,Integer> milestoneNumbers){
            var points=new java.util.ArrayList<FailurePoint>();var active=new java.util.HashSet<String>();int ordinal=0;String owner="";
            for(var cut:auditCuts){ordinal++;boolean found=false;
                Object core=cut.visualEvidence().get("originalEngine");
                String currentOwner=core instanceof java.util.Map<?,?> engine&&engine.get("identity")!=null?org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(engine.get("identity")):"";
                if(!currentOwner.isBlank()&&!currentOwner.equals(owner)){active.clear();owner=currentOwner;}
                var navigationRows=ExecutionScopeViews.failureEvaluations(cut);
                var failedBodies=navigationRows.stream().filter(r->r.kind().equals("INVARIANT_BODY")&&failureEvaluation(r)).map(Evaluation::element).collect(java.util.stream.Collectors.toSet());
                for(var row:navigationRows) {
                    String key=failureKey(row);
                    boolean recovered=row.kind().equals("GOAL_TASK")?row.status().equals("FULFILLED")&&java.util.Set.of("KNOWN","KNOWN_DERIVED","KNOWN_HISTORY","KNOWN_DERIVED_HISTORY").contains(row.quality()):row.status().equals("PASS")||row.status().equals("CONDITION_TRUE");
                    if(recovered){
                        active.remove(key);
                        if(row.kind().equals("INVARIANT"))active.removeIf(k->k.startsWith("INVARIANT_BODY\u0000"+row.element()+"\u0000"));
                        continue;
                    }
                    boolean unmet=unmetCondition(row);
                    if(!failureEvaluation(row)&&!unmet)continue;
                    found|=!unmet;
                    // The aggregate summarizes these exact counterexamples. Keep
                    // each object witness once instead of adding a second failure.
                    if(row.kind().equals("INVARIANT")&&failedBodies.contains(row.element()))continue;
                    if(!active.add(key))continue;
                    String category=unmet?"Unmet conditions":row.status().equals("ERROR")?"Diagnostics":java.util.Set.of("GOAL_TASK","EXECUTION_OUTCOME").contains(row.kind())?"Goal / Task":"OCL / constraints";
                    points.add(new FailurePoint(cut,row,category,ordinal,milestoneNumbers.getOrDefault(cut.id(),0),false));
                }
                if(cut.failed()&&!found&&active.add("boundary:"+cut.id()))points.add(new FailurePoint(cut,null,"Diagnostics",ordinal,milestoneNumbers.getOrDefault(cut.id(),0),false));
            }
            return List.copyOf(points);
    }
    private void refreshFailurePoints(){
        var selected=(FailurePoint)failurePoints.getSelectedItem();updatingFailures=true;
        try {
            var visible=preparedFailures.stream().filter(p->failureFilter.getSelectedIndex()==0?!p.category().equals("Unmet conditions"):p.category().equals(failureFilter.getSelectedItem())).map(p->new FailurePoint(p.cut(),p.evaluation(),p.category(),p.boundary(),p.milestone(),details.isSelected())).toArray(FailurePoint[]::new);
            failurePoints.setModel(new DefaultComboBoxModel<>(visible));
            cslFailurePoints.setModel(new DefaultComboBoxModel<>(visible));
            failurePoints.setSelectedIndex(-1);
            cslFailurePoints.setSelectedIndex(-1);
            if(selected!=null)for(int i=0;i<failurePoints.getItemCount();i++){
                var point=failurePoints.getItemAt(i);
                if(point.cut().id().equals(selected.cut().id())&&java.util.Objects.equals(point.evaluation(),selected.evaluation())){
                    failurePoints.setSelectedIndex(i);
                    cslFailurePoints.setSelectedIndex(i);
                    break;
                }
            }
            boolean hasFailures = failurePoints.getItemCount()>0;
            failurePoints.setEnabled(hasFailures);
            cslFailurePoints.setEnabled(hasFailures);
            jumpCheckpointButton.setEnabled(hasFailures);
            cslJumpCheckpointButton.setEnabled(hasFailures);
            String tip = hasFailures?"Open the first point in the selected category for this execution; use the list for other points.":"No matching failures in this execution. A false condition is not a violation; select Unmet conditions to inspect it.";
            jumpCheckpointButton.setToolTipText(tip);
            cslJumpCheckpointButton.setToolTipText(tip);
        }finally{updatingFailures=false;}
    }
    private void openAuditedFailure(){
        if(failurePoints.getItemCount()==0)return;
        failurePoints.setSelectedIndex(0);openFailure(failurePoints.getItemAt(0));
    }
    private void openFailure(FailurePoint point){
        if(point==null)return;
        failureFocus=point;
        int target=-1;for(int i=0;i<currentCounterexampleStates.size();i++)if(currentCounterexampleStates.get(i).id().equals(point.cut().id())){target=i;break;}
        if(target<0){intervalIds=java.util.Set.of(point.cut().id());setCuts(allCuts);target=0;}
        updateSelectedCounterexampleState(target);
        var row=point.evaluation();if(row==null){setAuditText(point.cut().trace());liveDetailTabs.setSelectedIndex(1);return;}
        if(row.kind().equals("GOAL_TASK")){
            selectElementById(row.element());
            String groupKey=agentGroupKey(row.context(),row.player(),row.occurrence());
            for(int j=0;j<displayedAgentRows.size();j++)if(displayedAgentRows.get(j).key().equals(groupKey)||displayedAgentRows.get(j).currentEvaluations().contains(row)){
                liveInstanceTable.setRowSelectionInterval(j,j);
                for(int k=0;k<displayedOccurrences.size();k++)if(displayedOccurrences.get(k).occurrenceId().equals(row.occurrence())){
                    liveOccurrenceTable.setRowSelectionInterval(k,k);
                    break;
                }
                break;
            }
        }
        if(!row.kind().equals("GOAL_TASK"))for(int i=0;i<displayedChecks.size();i++){
            var shown=displayedChecks.get(i);
            if(row.element().equals(shown.element())&&(row.kind().equals("INVARIANT_BODY")||row.evidence().equals(shown.evidence()))){liveCheckTable.setRowSelectionInterval(i,i);liveCheckTable.scrollRectToVisible(liveCheckTable.getCellRect(i,0,true));break;}
        }
        // Row selection may render the aggregate. Restore the exact selected object witness afterwards.
        setAuditText(row.element()+"\n"+row.status()+" | "+row.quality()+"\n"+row.evidence()+"\n"+row.certifiedHistory());liveDetailTabs.setSelectedIndex(1);
    }

    private void inspectInterval() {
        if(details.isSelected()||selectedCut()==null)return;
        String previous=selectedStateIndex==0?"":currentCounterexampleStates.get(selectedStateIndex-1).id();String end=selectedCut().id();
        var ids=new java.util.LinkedHashSet<String>();boolean include=previous.isEmpty();
        for(var cut:auditCuts){if(include)ids.add(cut.id());if(cut.id().equals(previous))include=true;if(cut.id().equals(end))break;}
        intervalIds=java.util.Set.copyOf(ids);details.setSelected(true);details.setText("State interval details");setCuts(allCuts);
    }
    private void navigateStateStep(int delta) {
        if (currentCounterexampleStates.isEmpty()) return;
        failureFocus=null;
        updateSelectedCounterexampleState(Math.max(0, Math.min(currentCounterexampleStates.size() - 1, selectedStateIndex + delta)));
    }

    private void updateSelectedCounterexampleState(int stateIndex) {
        oclGoalModel.setRowCount(0);
        if (currentCounterexampleStates.isEmpty()) {
            selectedStateIndex = -1;
            stateSnapshotText.setText(""); counterexampleTraceArea.setText("");
            stateStepInfoLabel.setText("State 0 / 0");
            cslStateStepInfoLabel.setText("State 0 / 0");
            conformanceResultLabel.setText("NOT_EVALUATED"); riskResultLabel.setText("UNAVAILABLE");
            statePrevButton.setEnabled(false); stateNextButton.setEnabled(false); jumpCheckpointButton.setEnabled(false);
            cslStatePrevButton.setEnabled(false); cslStateNextButton.setEnabled(false); cslJumpCheckpointButton.setEnabled(false);
            selectionListener.accept(null);
            if(liveOnly)renderLive(null);
            return;
        }
        selectedStateIndex = Math.max(0, Math.min(currentCounterexampleStates.size() - 1, stateIndex));
        updatingStateComboInternal = true;
        try {
            stateSelectorCombo.setSelectedIndex(selectedStateIndex);
            cslStateSelectorCombo.setSelectedIndex(selectedStateIndex);
        }
        finally { updatingStateComboInternal = false; }
        Cut cut = selectedCut();
        boolean hasPrev = selectedStateIndex > 0;
        statePrevButton.setEnabled(hasPrev);
        cslStatePrevButton.setEnabled(hasPrev);
        boolean hasNext = selectedStateIndex < currentCounterexampleStates.size() - 1;
        stateNextButton.setEnabled(hasNext);
        cslStateNextButton.setEnabled(hasNext);
        boolean jumpEnabled = liveOnly?failurePoints.getItemCount()>0:currentFailureCheckpoint>=0;
        jumpCheckpointButton.setEnabled(jumpEnabled);
        cslJumpCheckpointButton.setEnabled(jumpEnabled);
        boolean milesVisible = details.isSelected()||failureFocus!=null;
        milestonesButton.setVisible(milesVisible);
        cslMilestonesButton.setVisible(milesVisible);
        updatePosition(cut);
        stateSnapshotText.setText((!executionScope.isBlank()||!details.isSelected())&&!cut.businessState().isBlank()?cut.businessState():cut.state()); stateSnapshotText.setCaretPosition(0);
        counterexampleTraceArea.setText(liveOnly?VerificationDataCoverage.diagnostics(cut,VerificationDataCoverage.assess(cut)):cut.trace()); counterexampleTraceArea.setCaretPosition(0);
        selectionListener.accept(cut);
        if(liveOnly){renderLive(cut);return;}
        for (Evaluation row : cut.evaluations()) if(details.isSelected()||row.required()||java.util.Set.of("FAIL","ERROR","VIOLATED").contains(row.status()))oclGoalModel.addRow(new Object[]{row.element(), row.raw(), row.status(), row.quality(), row.evidence(),row.certifiedHistory()});
        conformanceResultLabel.setText(cut.failed() ? "Observed violation" : "Observed prefix");
        riskResultLabel.setToolTipText(cut.trace());
        riskResultLabel.setText("<html>"+cut.requirements()+"<br>Overall sources: "+cut.coverage()+" / "+cut.freshness()+"</html>");
    }

    private void updatePosition(Cut cut){
        String text = details.isSelected()?"Boundary "+(auditCuts.indexOf(cut)+1)+" / "+auditCuts.size():((milestoneNumbers.getOrDefault(cut.id(),0)==0?"Before first state":"State "+milestoneNumbers.get(cut.id())+" / "+businessMilestones.size())+(failureFocus==null?"":failureFocus.category().equals("Unmet conditions")?" — Unmet condition":" — Failure Point"));
        stateStepInfoLabel.setText(text);
        cslStateStepInfoLabel.setText(text);
    }

    @Override public void doLayout() {
        updateGoalTaskSplitOrientation();
        super.doLayout();
    }

    private void updateGoalTaskSplitOrientation() {
        if (goalTaskSplitPane == null) return;
        int width = istarVerificationPanel != null && istarVerificationPanel.getWidth() > 0
                ? istarVerificationPanel.getWidth()
                : goalTaskSplitPane.getWidth() > 0 ? goalTaskSplitPane.getWidth() : getWidth();
        if (width <= 0) return;
        int desired = width < NARROW_SPLIT_WIDTH ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
        if (goalTaskSplitPane.getOrientation() != desired) {
            goalTaskSplitPane.setOrientation(desired);
            goalTaskSplitPane.setDividerLocation(0.5);
        }
    }

    private JPanel buildLiveResults(){
        var goals=liveGoalTable;var tasks=liveTaskTable;var checks=liveCheckTable;
        int[] widths={440,120};
        for(int i=0;i<widths.length;i++){
            goals.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            tasks.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        goals.setToolTipText("Overview uses declared population policies within each execution. Inspect individual agent results below; native verification and history are unchanged.");
        tasks.setToolTipText(goals.getToolTipText());
        int[] instanceWidths={180,260,120};for(int i=0;i<instanceWidths.length;i++)liveInstanceTable.getColumnModel().getColumn(i).setPreferredWidth(instanceWidths[i]);
        int[] occurrenceWidths={150,160,160,130,100};for(int i=0;i<occurrenceWidths.length;i++)liveOccurrenceTable.getColumnModel().getColumn(i).setPreferredWidth(occurrenceWidths[i]);
        goals.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tasks.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        goals.getSelectionModel().addListSelectionListener(e->{
            if(e.getValueIsAdjusting()||updatingElementSelection)return;
            int row=goals.getSelectedRow();
            if(row>=0&&row<goalRows.size()){
                updatingElementSelection=true;
                try{tasks.clearSelection();}finally{updatingElementSelection=false;}
                String nextId=goalRows.get(row).id();
                if(!nextId.equals(selectedElementId)){selectedElementId=nextId;selectedAgentKey="";selectedOccurrenceKey="";}
                showSelectedElementInstances();
                liveDetailTabs.setSelectedIndex(0);
            }else if(tasks.getSelectedRow()<0){
                selectedElementId="";selectedAgentKey="";selectedOccurrenceKey="";
                showSelectedElementInstances();
            }
        });
        tasks.getSelectionModel().addListSelectionListener(e->{
            if(e.getValueIsAdjusting()||updatingElementSelection)return;
            int row=tasks.getSelectedRow();
            if(row>=0&&row<taskRows.size()){
                updatingElementSelection=true;
                try{goals.clearSelection();}finally{updatingElementSelection=false;}
                String nextId=taskRows.get(row).id();
                if(!nextId.equals(selectedElementId)){selectedElementId=nextId;selectedAgentKey="";selectedOccurrenceKey="";}
                showSelectedElementInstances();
                liveDetailTabs.setSelectedIndex(0);
            }else if(goals.getSelectedRow()<0){
                selectedElementId="";selectedAgentKey="";selectedOccurrenceKey="";
                showSelectedElementInstances();
            }
        });
        checks.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()){int row=checks.getSelectedRow();if(row>=0&&row<displayedChecks.size()){var value=displayedChecks.get(row);setAuditText(value.element()+"\n"+value.status()+" | "+value.quality()+"\n"+value.evidence());liveDetailTabs.setSelectedIndex(1);}}});
        checks.getColumnModel().getColumn(0).setPreferredWidth(240);
        checks.getColumnModel().getColumn(1).setPreferredWidth(500);
        checks.getColumnModel().getColumn(2).setMaxWidth(100);
        checks.getColumnModel().getColumn(2).setCellRenderer(new TruthResultCellRenderer());
        liveInstanceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        liveInstanceTable.getSelectionModel().addListSelectionListener(e->{
            if(e.getValueIsAdjusting()||updatingAgentSelection)return;
            int idx=liveInstanceTable.getSelectedRow();
            if(idx>=0&&idx<displayedAgentRows.size()){
                String nextKey=displayedAgentRows.get(idx).key();
                if(!nextKey.equals(selectedAgentKey)){selectedAgentKey=nextKey;selectedOccurrenceKey="";}
            }else{
                selectedAgentKey="";selectedOccurrenceKey="";
            }
            showInstanceEvidence();
        });
        liveOccurrenceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        liveOccurrenceTable.getSelectionModel().addListSelectionListener(e->{
            if(e.getValueIsAdjusting()||updatingOccurrenceSelection)return;
            int idx=liveOccurrenceTable.getSelectedRow();
            selectedOccurrenceKey=idx>=0&&idx<displayedOccurrences.size()?displayedOccurrences.get(idx).key():"";
            renderSelectedOccurrenceDetail();
        });
        liveDetailTabs.addTab("Instances",new JPanel());liveDetailTabs.addTab("Evidence",new JPanel());
        goals.setToolTipText("Goals defined in the iStar model with owner actor. Complete requires qualified fulfilled evidence; missing evidence stays Unknown.");
        tasks.setToolTipText("Tasks defined in the iStar model with owner actor. Complete requires qualified fulfilled evidence; missing evidence stays Unknown.");
        liveInstanceTable.setToolTipText("Individual agent/player outcomes for the selected Goal or Task, execution and state. Original source evidence remains in Audit details.");
        liveOccurrenceTable.setToolTipText("History up to the selected State: Occurrence is an element status; Invocation is an identified runtime call; Execution evidence is a retained certificate. Earlier Unknown results remain historical.");

        setupNavigation(executionSelector, stateSelectorCombo, statePrevButton, stateNextButton, jumpCheckpointButton, milestonesButton, stateStepInfoLabel, failureFilter, failurePoints);
        setupNavigation(cslExecutionSelector, cslStateSelectorCombo, cslStatePrevButton, cslStateNextButton, cslJumpCheckpointButton, cslMilestonesButton, cslStateStepInfoLabel, cslFailureFilter, cslFailurePoints);

        // Build CSL Verification Panel
        cslVerificationPanel = new JPanel(new BorderLayout(5, 5));
        cslVerificationPanel.setName("csl-verification-panel");
        cslVerificationPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        var cslNav = buildNavComponent(cslRequirementScope, cslExecutionSelector, cslStateSelectorCombo, cslStatePrevButton, cslStateNextButton, cslJumpCheckpointButton, cslMilestonesButton, cslStateStepInfoLabel, cslFailureFilter, cslFailurePoints);
        cslVerificationPanel.add(cslNav, BorderLayout.NORTH);

        stateSnapshotText.setEditable(false);
        var checkPanel=new JPanel(new BorderLayout(0,4));checkPanel.add(new JScrollPane(checks),BorderLayout.CENTER);
        checkPanel.add(liveDiagnosticNote,BorderLayout.SOUTH);
        var cslSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                section("Accepted CSL / USE state (read-only)", new JScrollPane(stateSnapshotText)),
                section("OCL / runtime checks", checkPanel));
        cslSplit.setResizeWeight(0.40);
        cslVerificationPanel.add(cslSplit, BorderLayout.CENTER);
        var cslCoverage=section("Data coverage",riskResultLabel);
        cslCoverage.setPreferredSize(new Dimension(500,65));cslVerificationPanel.add(cslCoverage,BorderLayout.SOUTH);

        // Build iStar Verification Panel
        istarVerificationPanel = new JPanel(new BorderLayout(5, 5)) {
            @Override public void doLayout() {
                updateGoalTaskSplitOrientation();
                super.doLayout();
            }
        };
        istarVerificationPanel.setName("istar-verification-panel");
        istarVerificationPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        istarVerificationPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                updateGoalTaskSplitOrientation();
            }
        });
        var istarNav = buildNavComponent(requirementScope, executionSelector, stateSelectorCombo, statePrevButton, stateNextButton, jumpCheckpointButton, milestonesButton, stateStepInfoLabel, failureFilter, failurePoints);
        istarVerificationPanel.add(istarNav, BorderLayout.NORTH);

        var goalsSection = section("Goals", new JScrollPane(goals));
        var tasksSection = section("Tasks", new JScrollPane(tasks));
        goalsSection.setMinimumSize(new Dimension(160, 90));
        tasksSection.setMinimumSize(new Dimension(160, 90));
        goalTaskSplitPane = named(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, goalsSection, tasksSection), "goal-task-split");
        goalTaskSplitPane.setResizeWeight(0.5);
        goalTaskSplitPane.setOneTouchExpandable(true);

        selectedElementLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        selectedElementLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        var instancesSection = section("Agent instances", new JScrollPane(liveInstanceTable));
        var occurrencesSection = section("Occurrences & invocations", new JScrollPane(liveOccurrenceTable));
        instancesSection.setMinimumSize(new Dimension(180, 90));
        occurrencesSection.setMinimumSize(new Dimension(180, 75));
        var agentDetailSplit = named(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, instancesSection, occurrencesSection), "agent-detail-split");
        agentDetailSplit.setResizeWeight(0.42);
        agentDetailSplit.setOneTouchExpandable(true);
        var agentDetailPanel = new JPanel(new BorderLayout(4, 4));
        agentDetailPanel.add(selectedElementLabel, BorderLayout.NORTH);
        agentDetailPanel.add(agentDetailSplit, BorderLayout.CENTER);

        var istarMainSplit = named(new JSplitPane(JSplitPane.VERTICAL_SPLIT, goalTaskSplitPane, agentDetailPanel), "istar-main-split");
        istarMainSplit.setResizeWeight(0.46);
        istarMainSplit.setOneTouchExpandable(true);
        istarVerificationPanel.add(istarMainSplit, BorderLayout.CENTER);

        conformanceResultLabel.setName("observed-verdict");
        conformanceResultLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        riskResultLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        var verdictPanel = section("Verification result", conformanceResultLabel);
        verdictPanel.setPreferredSize(new Dimension(800, 65));
        istarDataCoverageLabel.setFont(new Font(Font.SANS_SERIF,Font.BOLD,14));
        istarVerificationPanel.add(verdictPanel, BorderLayout.SOUTH);
        var hiddenIstar = new JPanel();
        hiddenIstar.setVisible(false);
        hiddenIstar.add(istarDataCoverageLabel);
        istarVerificationPanel.add(hiddenIstar, BorderLayout.WEST);

        // Hidden panel for backward-compatibility with tests querying deleted/hidden components
        hiddenPanel = new JPanel();
        hiddenPanel.setVisible(false);
        hiddenPanel.add(liveDetailTabs);
        hiddenPanel.add(auditToggle);
        hiddenPanel.add(details);
        auditToggle.addActionListener(e -> {
            var focus = failureFocus;
            String selected = selectedCut() == null ? "" : selectedCut().id();
            details.setSelected(auditToggle.isSelected());
            intervalIds = null;
            setCuts(allCuts);
            if (focus != null) openFailure(focus);
            else for (int i = 0; i < currentCounterexampleStates.size(); i++) if (currentCounterexampleStates.get(i).id().equals(selected)) {
                updateSelectedCounterexampleState(i);
                break;
            }
            stateSelectorCombo.repaint();
            cslStateSelectorCombo.repaint();
        });

        JPanel combined = new JPanel(new BorderLayout());
        combined.add(istarVerificationPanel, BorderLayout.CENTER);
        combined.add(cslVerificationPanel, BorderLayout.EAST);
        combined.add(hiddenPanel, BorderLayout.SOUTH);
        return combined;
    }

    private JPanel buildNavComponent(JLabel reqScope, JComboBox<String> execSel, JComboBox<String> stateSel, JButton prevBtn, JButton nextBtn, JButton jumpBtn, JButton milesBtn, JLabel stepInfo, JComboBox<String> failFilter, JComboBox<FailurePoint> failPts) {
        var nav = new JPanel(new BorderLayout());
        nav.setBorder(BorderFactory.createTitledBorder("State navigation"));
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        buttons.add(new JLabel("Execution:"));
        buttons.add(execSel);
        buttons.add(new JLabel("Go to State:"));
        buttons.add(stateSel);
        buttons.add(prevBtn);
        buttons.add(nextBtn);
        buttons.add(jumpBtn);
        buttons.add(milesBtn);
        buttons.add(stepInfo);

        var options = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        options.add(failFilter);
        options.add(failPts);

        nav.add(reqScope, BorderLayout.NORTH);
        nav.add(buttons, BorderLayout.CENTER);
        nav.add(options, BorderLayout.SOUTH);
        return nav;
    }

    private void setupNavigation(JComboBox<String> execSel, JComboBox<String> stateSel, JButton prevBtn, JButton nextBtn, JButton jumpBtn, JButton milesBtn, JLabel stepInfo, JComboBox<String> failFilter, JComboBox<FailurePoint> failPts) {
        execSel.setPrototypeDisplayValue("Execution 123 — Entity · id='value'");
        execSel.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                var label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                String id = java.util.Objects.toString(value, "");
                label.setText(executionLabels.getOrDefault(id, id.isBlank() ? "No admitted execution" : id));
                label.setToolTipText(id);
                return label;
            }
        });
        execSel.addActionListener(e -> {
            if (!updatingExecutions) selectExecution((String) execSel.getSelectedItem());
        });

        stateSel.setPrototypeDisplayValue("State 000000");
        stateSel.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                int ordinal = index < 0 ? selectedStateIndex : index;
                int shown = details.isSelected() && value != null
                        ? java.util.stream.IntStream.range(0, auditCuts.size()).filter(i -> auditCuts.get(i).id().equals(value)).findFirst().orElse(ordinal)
                        : liveOnly && value != null ? milestoneNumbers.getOrDefault(value, 0) - 1 : ordinal;
                setText(shown < 0 ? (value == null ? "Select state" : "Before first state") : (details.isSelected() ? "Boundary " : "State ") + (shown + 1));
                if(checkpointTimeline&&!details.isSelected()&&shown>0&&shown<businessMilestones.size()
                        &&businessMilestones.get(shown).businessState().equals(businessMilestones.get(shown-1).businessState()))
                    setText(getText()+" · CSL unchanged");
                setToolTipText(value == null ? null : value.toString());
                return this;
            }
        });
        stateSel.addActionListener(e -> {
            if (!updatingStateComboInternal && stateSel.getSelectedIndex() >= 0) {
                failureFocus = null;
                updateSelectedCounterexampleState(stateSel.getSelectedIndex());
            }
        });

        prevBtn.addActionListener(e -> navigateStateStep(-1));
        nextBtn.addActionListener(e -> navigateStateStep(1));
        jumpBtn.addActionListener(e -> openAuditedFailure());
        milesBtn.setVisible(false);
        milesBtn.addActionListener(e -> {
            failureFocus = null;
            intervalIds = null;
            auditToggle.setSelected(false);
            details.setSelected(false);
            details.setText("Show all audit boundaries");
            setCuts(allCuts);
            if (selectedStateIndex >= 0) updateSelectedCounterexampleState(selectedStateIndex);
        });

        failPts.setPrototypeDisplayValue(new FailurePoint(new Cut("", "", "", List.of(), "", "", false), new Evaluation("Constraint or Task definition", "", "FAIL", "", ""), "OCL / constraints", 9999, 9999, false));
        failPts.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value == null && failPts.getItemCount() == 0) setText("Unmet conditions".equals(failFilter.getSelectedItem()) ? "No unmet conditions in this execution" : "No matching failures in this execution");
                return this;
            }
        });
        failFilter.addActionListener(e -> {
            if (failFilter == cslFailureFilter) {
                if (failureFilter.getSelectedIndex() != cslFailureFilter.getSelectedIndex()) failureFilter.setSelectedIndex(cslFailureFilter.getSelectedIndex());
            } else {
                if (cslFailureFilter.getSelectedIndex() != failureFilter.getSelectedIndex()) cslFailureFilter.setSelectedIndex(failureFilter.getSelectedIndex());
            }
            refreshFailurePoints();
        });
        failPts.addActionListener(e -> {
            if (!updatingFailures) {
                var point = (FailurePoint) failPts.getSelectedItem();
                if (point != null) openFailure(point);
            }
        });
        failPts.setToolTipText("Points belong only to the selected execution. All failures excludes unmet conditions. Persistent failures appear once; only qualified PASS/FULFILLED starts recovery. Pending/SKIPPED never erase a failure. Supporting Task failure does not automatically violate a selected requirement.");
        failFilter.setToolTipText("Unmet conditions are inspectable progress states, not verification failures. Diagnostics contains evaluation errors; missing evidence remains UNKNOWN.");
    }

    private JPanel section(String title,Component content){var panel=new JPanel(new BorderLayout());panel.setBorder(BorderFactory.createTitledBorder(title));panel.add(content,BorderLayout.CENTER);return panel;}
    private void renderLive(Cut cut){
        if(liveSummary==null)return;
        String priorElement=selectedElementId;
        String priorAgent=selectedAgentKey;
        String priorOccurrence=selectedOccurrenceKey;
        liveRows=liveSummary.rows(cut==null?List.of():cut.evaluations()).stream()
                .filter(r->cut==null||r.instances().isEmpty()&&ExecutionScopeViews.contexts(cut).isEmpty()||!r.displayInstances().isEmpty()).toList();
        goalRows=liveRows.stream().filter(LiveGoalSummary.Row::isGoal).toList();
        taskRows=liveRows.stream().filter(LiveGoalSummary.Row::isTask).toList();
        // Model order is stable; admitted phase membership controls which definitions are shown.
        updatingElementSelection=true;
        try{
            liveGoals.setRowCount(0);
            for(var row:goalRows)liveGoals.addRow(new Object[]{scopedLabel(row.ownerLabel(),cut),row.resultText()});
            liveTasks.setRowCount(0);
            for(var row:taskRows)liveTasks.addRow(new Object[]{scopedLabel(row.ownerLabel(),cut),row.resultText()});
            restoreElementSelection(priorElement);
        }finally{updatingElementSelection=false;}
        selectedAgentKey=priorAgent;
        selectedOccurrenceKey=priorOccurrence;
        liveChecks.setRowCount(0);
        displayedChecks=cut==null?List.of():PresentationResults.checks(cut);
        long diagnosticCount=cut==null?0:cut.evaluations().stream().filter(r->r.kind().equals("CHECKPOINT_CHECK")).count();
        long taskFailures=cut==null?0:cut.evaluations().stream().filter(r->r.kind().equals("EXECUTION_OUTCOME")).map(Evaluation::occurrence).distinct().count();
        liveDiagnosticNote.setText("Checkpoint diagnostics: "+diagnosticCount+"; Task failures: "+taskFailures+". See Audit details.");
        if(cut==null){
            conformanceResultLabel.setText("NOT_EVALUATED");riskResultLabel.setText("UNAVAILABLE");istarDataCoverageLabel.setText("UNAVAILABLE");
            requirementScope.setText("No selected verification state");cslRequirementScope.setText("No selected verification state");
            selectedElementLabel.setText("No Goal or Task selected — select a Goal or Task above to inspect agent instances");
            displayedInstances=List.of();displayedAgentRows=List.of();displayedOccurrences=List.of();
            updatingAgentSelection=true;try{liveInstances.setRowCount(0);}finally{updatingAgentSelection=false;}
            updatingOccurrenceSelection=true;try{liveOccurrences.setRowCount(0);}finally{updatingOccurrenceSelection=false;}
            setAuditText("");stateSnapshotText.setText("");
            statePrevButton.setEnabled(false);stateNextButton.setEnabled(false);jumpCheckpointButton.setEnabled(false);
            cslStatePrevButton.setEnabled(false);cslStateNextButton.setEnabled(false);cslJumpCheckpointButton.setEnabled(false);
            return;
        }
        var selected=liveRows.stream().filter(r->r.resultRole().equals("Selected requirement")).map(LiveGoalSummary.Row::label).toList();
        Object inventory=cut.visualEvidence().get("requirementInventory");
        boolean explicitSelection=cut.selectedRequirements()||inventory instanceof java.util.Map<?,?> values&&values.get("selectedIntentionalElements") instanceof List<?> ids&&!ids.isEmpty();
        String scope=explicitSelection?"Selected Goal/Task requirements: "+(selected.isEmpty()?"no admitted result":String.join(", ",selected)):"Goal/Task requirements: all model definitions in observed instances";
        requirementScope.setText(scope+" | Every authored OCL invariant remains required.");
        cslRequirementScope.setText(scope+" | Every authored OCL invariant remains required.");
        requirementScope.setToolTipText("COMPLETE_OBSERVED applies to these requirements at the selected observed cut. Supporting skill summaries may remain UNKNOWN. Missing demand is not proof of non-applicability; overall source coverage is reported separately.");
        for(var row:displayedChecks)liveChecks.addRow(new Object[]{checkLabel(row,cut),PresentationResults.body(row),PresentationResults.truth(row.status())});
        stateSnapshotText.setText((!executionScope.isBlank()||!details.isSelected())&&!cut.businessState().isBlank()?cut.businessState():cut.state());stateSnapshotText.setCaretPosition(0);
        conformanceResultLabel.setText(verdict(cut));
        conformanceResultLabel.setToolTipText("Selected execution: independent Goal/Task root and scoped native OCL, including applicable shared structural rules");
        var dataCoverage=VerificationDataCoverage.assess(cut);
        riskResultLabel.setToolTipText(VerificationDataCoverage.diagnostics(cut,dataCoverage));
        String freshness=cut.freshness().equals("CURRENT_OBSERVED")?cut.visualEvidence().containsKey("verifiedCheckpointToken")?"Current checkpoint":"Current observation":"Evidence is not current";
        riskResultLabel.setText("<html>Data coverage: "+dataCoverage.status()+"<br>"+freshness+"</html>");
        istarDataCoverageLabel.setText(riskResultLabel.getText());istarDataCoverageLabel.setToolTipText(riskResultLabel.getToolTipText());
        showSelectedElementInstances();
        if(failureFocus!=null&&failureFocus.cut().id().equals(cut.id())&&failureFocus.evaluation()!=null&&!failureFocus.evaluation().kind().equals("GOAL_TASK")){
            var row=failureFocus.evaluation();
            setAuditText(row.element()+"\n"+row.status()+" | "+row.quality()+"\n"+row.evidence()+"\n"+row.certifiedHistory());

        }
    }

    private void restoreElementSelection(String elementId){
        if(elementId==null||elementId.isBlank()){selectedElementId="";return;}
        for(int i=0;i<goalRows.size();i++)if(goalRows.get(i).id().equals(elementId)){liveGoalTable.setRowSelectionInterval(i,i);selectedElementId=elementId;return;}
        for(int i=0;i<taskRows.size();i++)if(taskRows.get(i).id().equals(elementId)){liveTaskTable.setRowSelectionInterval(i,i);selectedElementId=elementId;return;}
        selectedElementId="";
    }

    private void selectElementById(String elementId){
        updatingElementSelection=true;
        try{
            liveGoalTable.clearSelection();
            liveTaskTable.clearSelection();
            restoreElementSelection(elementId);
        }finally{updatingElementSelection=false;}
        showSelectedElementInstances();
    }

    private LiveGoalSummary.Row findSelectedRow(){
        if(selectedElementId==null||selectedElementId.isBlank())return null;
        for(var row:liveRows)if(row.id().equals(selectedElementId))return row;
        return null;
    }

    private String scopedLabel(String label,Cut cut){
        if(cut==null||executionScope.isBlank()||ExecutionScopeViews.ALL.equals(executionScope))return label;
        var contexts=ExecutionGroups.of(List.of(cut)).contexts(cut,executionScope);
        var context=contexts.stream().filter(c->!ExecutionScopeViews.object(ExecutionScopeViews.object(c.get("scopeView")).get("entities")).isEmpty()).findFirst().orElse(contexts.isEmpty()?java.util.Map.of():contexts.getFirst());
        return label+" · "+ExecutionScopeViews.actorLabel(context,new java.util.ArrayList<>(executionLabels.keySet()).indexOf(executionScope)+1);
    }

    private static String agentGroupKey(String context,String player,String occurrence){
        return (context==null?"":context)+"\u0000"+(player!=null&&!player.isBlank()?player:"occurrence:"+(occurrence==null?"":occurrence));
    }

    private String executionLabelFor(String context,int fallbackOrdinal){
        if(context==null||context.isBlank()||"UNAVAILABLE".equalsIgnoreCase(context))return "Unavailable";
        if(executionLabels.containsKey(context))return executionLabels.get(context);
        if(selectedCut()!=null){String group=ExecutionGroups.of(List.of(selectedCut())).canonical(context);if(executionLabels.containsKey(group))return executionLabels.get(group);}
        if(context.startsWith("beid:v1:"))return PresentationResults.shortId(context);
        return context.length()>30?"Context "+fallbackOrdinal:context;
    }

    private void showSelectedElementInstances(){
        String priorAgent=selectedAgentKey;
        String priorOccurrence=selectedOccurrenceKey;
        var row=findSelectedRow();
        Cut cut=selectedCut();
        if(row==null||cut==null){
            updatingAgentSelection=true;try{liveInstances.setRowCount(0);}finally{updatingAgentSelection=false;}
            updatingOccurrenceSelection=true;try{liveOccurrences.setRowCount(0);}finally{updatingOccurrenceSelection=false;}
            displayedInstances=List.of();displayedAgentRows=List.of();displayedOccurrences=List.of();
            selectedElementLabel.setText("No Goal or Task selected — select a Goal or Task above to inspect agent instances");
            if(cut==null||(failureFocus==null&&liveCheckTable.getSelectedRow()<0))setAuditText("");
            return;
        }
        String summaryStatus=row.resultText();
        selectedElementLabel.setText("Selected "+(row.isGoal()?"Goal: ":"Task: ")+scopedLabel(row.ownerLabel(),cut)+" ("+summaryStatus+")");
        displayedInstances=row.displayInstances();
        var contexts=new java.util.TreeSet<String>();
        displayedInstances.stream().map(Evaluation::context).filter(c->!c.isBlank()).forEach(contexts::add);
        var contextLabels=new java.util.LinkedHashMap<String,String>();
        for(String context:contexts)contextLabels.put(context,executionLabelFor(context,contextLabels.size()+1));
        var grouped=new java.util.LinkedHashMap<String,java.util.List<Evaluation>>();
        for(var instance:displayedInstances){
            grouped.computeIfAbsent(agentGroupKey(instance.context(),instance.player(),instance.occurrence()),k->new java.util.ArrayList<>()).add(instance);
        }
        var groups=new java.util.ArrayList<AgentInstanceGroup>();
        for(var entry:grouped.entrySet()){
            var evals=List.copyOf(entry.getValue());
            var first=evals.getFirst();
            String subj=PresentationResults.subjectLabel(first.player());
            String execLbl=first.context().isBlank()?"Unavailable":contextLabels.getOrDefault(first.context(),executionLabelFor(first.context(),1));
            String resLbl=PresentationResults.agentAggregateResult(evals);
            var history=collectAgentHistory(row.id(),first.context(),first.player(),evals,cut);
            groups.add(new AgentInstanceGroup(entry.getKey(),first.player(),subj,first.context(),execLbl,resLbl,evals,history));
        }
        displayedAgentRows=List.copyOf(groups);
        int restoreIdx=-1;
        updatingAgentSelection=true;
        try{
            liveInstances.setRowCount(0);
            for(int i=0;i<displayedAgentRows.size();i++){
                var g=displayedAgentRows.get(i);
                liveInstances.addRow(new Object[]{g.subjectLabel(),g.executionLabel(),g.resultLabel()});
                if(!priorAgent.isBlank()&&g.key().equals(priorAgent))restoreIdx=i;
            }
            if(restoreIdx>=0){
                liveInstanceTable.setRowSelectionInterval(restoreIdx,restoreIdx);
                selectedAgentKey=priorAgent;
                selectedOccurrenceKey=priorOccurrence;
            }else{
                selectedAgentKey="";
                selectedOccurrenceKey="";
            }
        }finally{updatingAgentSelection=false;}
        if(restoreIdx>=0){
            showInstanceEvidence();
        }else{
            updatingOccurrenceSelection=true;try{liveOccurrences.setRowCount(0);}finally{updatingOccurrenceSelection=false;}
            displayedOccurrences=List.of();
            setAuditText(row.detail());

        }
    }

    private void showInstanceEvidence(){
        String priorOccurrence=selectedOccurrenceKey;
        var row=findSelectedRow();
        int idx=liveInstanceTable.getSelectedRow();
        if(row==null||idx<0||idx>=displayedAgentRows.size()){
            updatingOccurrenceSelection=true;try{liveOccurrences.setRowCount(0);}finally{updatingOccurrenceSelection=false;}
            displayedOccurrences=List.of();
            if(row!=null){setAuditText(row.detail());}
            return;
        }
        var group=displayedAgentRows.get(idx);
        displayedOccurrences=group.history();
        int restoreOccIdx=-1;
        updatingOccurrenceSelection=true;
        try{
            liveOccurrences.setRowCount(0);
            for(int i=0;i<displayedOccurrences.size();i++){
                var entry=displayedOccurrences.get(i);
                String occDisplay=entry.occurrenceId().isBlank()?"—":PresentationResults.shortId(entry.occurrenceId())+(entry.episode().isBlank()?"":" ["+PresentationResults.shortId(entry.episode())+"]");
                String invDisplay=entry.invocationId().isBlank()?"—":PresentationResults.shortId(entry.invocationId());
                liveOccurrences.addRow(new Object[]{entry.recordType(),occDisplay,invDisplay,entry.checkpointLabel(),entry.resultLabel()});
                if(!priorOccurrence.isBlank()&&entry.key().equals(priorOccurrence))restoreOccIdx=i;
            }
            if(restoreOccIdx>=0){
                liveOccurrenceTable.setRowSelectionInterval(restoreOccIdx,restoreOccIdx);
                selectedOccurrenceKey=priorOccurrence;
            }else{
                selectedOccurrenceKey="";
            }
        }finally{updatingOccurrenceSelection=false;}
        renderSelectedOccurrenceDetail();
    }

    private void renderSelectedOccurrenceDetail(){
        var row=findSelectedRow();
        int agentIdx=liveInstanceTable.getSelectedRow();
        if(row==null||agentIdx<0||agentIdx>=displayedAgentRows.size())return;
        var group=displayedAgentRows.get(agentIdx);
        int occIdx=liveOccurrenceTable.getSelectedRow();
        var sb=new StringBuilder();
        sb.append(row.label()).append('\n');
        sb.append("Agent / Player: ").append(group.subjectLabel()).append(" (full ID: ").append(group.player().isBlank()?"Unavailable":group.player()).append(")\n");
        sb.append("Execution: ").append(group.executionLabel()).append(" (full ID: ").append(group.context().isBlank()?"Unavailable":group.context()).append(")\n");
        sb.append("Current Result: ").append(group.resultLabel()).append(" | Recorded occurrences & invocations: ").append(displayedOccurrences.size()).append('\n');
        if(occIdx>=0&&occIdx<displayedOccurrences.size()){
            var entry=displayedOccurrences.get(occIdx);
            sb.append("\n--- Selected Record ---\n");
            appendOccurrenceDetail(sb,entry);
        }
        sb.append("\n--- Full Occurrence & Invocation History ---\n");
        int index=1;
        for(var entry:displayedOccurrences){
            sb.append("\n[").append(index++).append("] ");
            appendOccurrenceDetail(sb,entry);
        }
        setAuditText(sb.toString());

    }

    private static void appendOccurrenceDetail(StringBuilder sb,OccurrenceEntry entry){
        sb.append("Type: ").append(entry.recordType()).append(" | Result: ").append(entry.resultLabel()).append('\n');
        sb.append("Occurrence ID: ").append(entry.occurrenceId().isBlank()?"—":entry.occurrenceId());
        if(!entry.episode().isBlank())sb.append(" | Episode: ").append(entry.episode());
        if(!entry.role().isBlank())sb.append(" | Role: ").append(entry.role());
        sb.append('\n');
        sb.append("Invocation ID: ").append(entry.invocationId().isBlank()?"—":entry.invocationId()).append('\n');
        sb.append("Checkpoint: ").append(entry.checkpointLabel()).append(" (boundary ID: ").append(entry.checkpointId()).append(")\n");
        sb.append("Raw: ").append(entry.rawStatus()).append(" | Certified: ").append(entry.certifiedStatus()).append(" | Quality: ").append(entry.quality()).append('\n');
        if(!entry.evidence().isBlank())sb.append("Evidence: ").append(entry.evidence()).append('\n');
        if(!entry.certifiedHistory().isBlank())sb.append("Certified history: ").append(entry.certifiedHistory()).append('\n');
    }

    private List<OccurrenceEntry> collectAgentHistory(String elementId,String contextId,String playerId,List<Evaluation> currentEvaluations,Cut currentCut){
        var prefix=historyPrefixFor(currentCut);
        var currentOccIds=currentEvaluations.stream().map(Evaluation::occurrence).collect(java.util.stream.Collectors.toSet());
        var entries=new java.util.ArrayList<OccurrenceEntry>();
        var lastOccurrenceState=new java.util.HashMap<String,String>();
        var seenInvocations=new java.util.LinkedHashSet<String>();
        for(var cut:prefix){
            String cpLabel=checkpointLabelFor(cut);
            var occMaps=structuredOccurrencesFor(cut,contextId,elementId,playerId);
            for(var eval:cut.evaluations()){
                if(!"GOAL_TASK".equals(eval.kind())||!eval.element().equals(elementId))continue;
                if(PopulationOverview.notAssigned(eval))continue;
                if(!java.util.Objects.equals(eval.context(),contextId)||!java.util.Objects.equals(eval.player(),playerId))continue;
                if(playerId.isBlank()&&!currentOccIds.contains(eval.occurrence()))continue;
                var occMap=occMaps.getOrDefault(eval.occurrence(),java.util.Map.of());
                String episode=extractEpisode(occMap);
                String role=java.util.Objects.toString(occMap.getOrDefault("role",""),"");
                String occKey=eval.occurrence()+"\u0000"+episode;
                String stateSig=eval.status()+"\u0000"+eval.originalStatus()+"\u0000"+eval.quality();
                if(!stateSig.equals(lastOccurrenceState.get(occKey))){
                    lastOccurrenceState.put(occKey,stateSig);
                    String entryKey="OCC\u0000"+occKey+"\u0000"+cut.id()+"\u0000"+entries.size();
                    String occPhase=executionPhase(eval,occMap);
                    String occType=occPhase.isEmpty()?"Occurrence":"Occurrence ("+occPhase+")";
                    entries.add(new OccurrenceEntry(entryKey,occType,eval.occurrence(),episode,"",cut.id(),cpLabel,PresentationResults.agentResult(eval),eval.originalStatus(),eval.status(),eval.quality(),role,eval.evidence(),eval.certifiedHistory()));
                }
                if(occMap.get("executionEvidence") instanceof java.util.Map<?,?> execEv){
                    for(var certEntry:execEv.entrySet()){
                        if(!(certEntry.getValue() instanceof java.util.Map<?,?> rawCert))continue;
                        var cert=ExecutionScopeViews.object(rawCert);
                        String kind=java.util.Objects.toString(cert.getOrDefault("kind","CERTIFICATE"),"CERTIFICATE");
                        String invId=java.util.Objects.toString(cert.getOrDefault("invocation",""),"");
                        String demandEp=java.util.Objects.toString(cert.getOrDefault("demandEpisode",episode),episode);
                        String stateVer=java.util.Objects.toString(cert.getOrDefault("stateVersion",""),"");
                        String stateHash=java.util.Objects.toString(cert.getOrDefault("stateHash",""),"");
                        String policy=java.util.Objects.toString(cert.getOrDefault("policy",""),"");
                        String failReason=java.util.Objects.toString(cert.getOrDefault("failureReason",""),"");
                        if(invId.isBlank()&&policy.isBlank())continue;
                        String certSig="CERT\u0000"+eval.occurrence()+"\u0000"+invId+"\u0000"+kind+"\u0000"+stateHash;
                        if(seenInvocations.add(certSig)){
                            boolean certifiedSuccess=policy.equals("CERTIFIED_EXECUTION/1.0")&&java.util.Set.of("TASK","ACHIEVE","TASK_COMPLETED").contains(kind);
                            String certRes="TASK_FAILED".equals(kind)?"Incomplete":certifiedSuccess?"Complete":PresentationResults.agentResult(eval);
                            String certPhase="TASK_FAILED".equals(kind)?"Failed":certifiedSuccess?"Succeeded":"";
                            String certType=(invId.isBlank()?"Execution evidence (":"Invocation (")+kind+(certPhase.isEmpty()?"":" · "+certPhase)+")";
                            String certEv="policy="+policy+"; kind="+kind+(certPhase.isEmpty()?"":"; phase="+certPhase)+"; invocation="+invId+"; stateVersion="+stateVer+"; stateHash="+stateHash+(failReason.isBlank()?"":"; failureReason="+failReason);
                            String cpWithVer=stateVer.isBlank()?cpLabel:cpLabel+" (v"+stateVer+")";
                            entries.add(new OccurrenceEntry(certSig,certType,eval.occurrence(),demandEp,invId,cut.id(),cpWithVer,certRes,kind,eval.status(),eval.quality(),role,certEv,eval.certifiedHistory()));
                        }
                    }
                }
            }
            for(var cm:structuredConstraintsFor(cut,contextId,elementId,playerId)){
                String kind=java.util.Objects.toString(cm.getOrDefault("kind",""),"");
                var witness=ExecutionScopeViews.object(cm.get("witness"));
                String invId=java.util.Objects.toString(cm.getOrDefault("invocation",witness.getOrDefault("stepInvocation",witness.getOrDefault("goalInvocation",""))),"");
                String occId=java.util.Objects.toString(witness.getOrDefault("occurrence",""),"");
                if(occId.isBlank()&&!occMaps.isEmpty())occId=occMaps.keySet().iterator().next();
                if(occId.isBlank()&&!currentEvaluations.isEmpty())occId=currentEvaluations.getFirst().occurrence();
                String ep=java.util.Objects.toString(witness.getOrDefault("demandEpisode",""),"");
                String role=java.util.Objects.toString(witness.getOrDefault("role",""),"");
                String verdict=java.util.Objects.toString(cm.getOrDefault("verdict",""),"");
                String diag=java.util.Objects.toString(cm.getOrDefault("diagnostic",""),"");
                String expr=java.util.Objects.toString(cm.getOrDefault("expression",""),"");
                String backendVal=java.util.Objects.toString(cm.getOrDefault("backendValue",""),"");
                String stateVer=java.util.Objects.toString(cm.getOrDefault("stateVersion",""),"");
                String sig="CONSTR\u0000"+kind+"\u0000"+occId+"\u0000"+invId+"\u0000"+verdict+"\u0000"+stateVer;
                if(seenInvocations.add(sig)){
                    seenInvocations.add("EVAL_DEDUP\u0000"+kind+"\u0000"+invId);
                    String resLabel="EXECUTION_OUTCOME".equals(kind)?(java.util.Set.of("FAIL","FAILED","INCONCLUSIVE","EXECUTION_FAILED").contains(verdict)?"Incomplete":"Complete"):PresentationResults.truth(verdict);
                    String wPhase=formatPhase(java.util.Objects.toString(witness.getOrDefault("phase",""),""),kind,verdict,diag);
                    String recType="Invocation ("+kind+(wPhase.isEmpty()?"":" · "+wPhase)+")";
                    String evText="expression="+expr+"; backendValue="+backendVal+"; diagnostic="+diag+(wPhase.isEmpty()?"":"; phase="+wPhase)+"; witness="+witness;
                    String cpWithVer=stateVer.isBlank()?cpLabel:cpLabel+" (v"+stateVer+")";
                    entries.add(new OccurrenceEntry(sig,recType,occId,ep,invId,cut.id(),cpWithVer,resLabel,backendVal,verdict,diag,role,evText,""));
                }
            }
            for(var eval:cut.evaluations()){
                if(!java.util.Set.of("TASK_PRE","TASK_POST","TASK_INVOCATION","INVOCATION_CONFORMANCE","EXECUTION_OUTCOME","GOAL_CONDITION").contains(eval.kind()))continue;
                if(!matchesConstraintEvaluation(eval,elementId,contextId,playerId))continue;
                String invId=eval.occurrence();
                if(seenInvocations.contains("EVAL_DEDUP\u0000"+eval.kind()+"\u0000"+invId))continue;
                String occId=!occMaps.isEmpty()?occMaps.keySet().iterator().next():(currentEvaluations.isEmpty()?"":currentEvaluations.getFirst().occurrence());
                String sig="EVAL\u0000"+eval.kind()+"\u0000"+occId+"\u0000"+invId+"\u0000"+eval.status();
                if(seenInvocations.add(sig)){
                    String resLabel="EXECUTION_OUTCOME".equals(eval.kind())&&java.util.Set.of("EXECUTION_FAILED","FAIL","FAILED","INCONCLUSIVE").contains(eval.status())?"Incomplete":PresentationResults.truth(eval.status());
                    String ePhase="EXECUTION_OUTCOME".equals(eval.kind())&&java.util.Set.of("EXECUTION_FAILED","FAIL","FAILED","INCONCLUSIVE").contains(eval.status())?"Failed":"";
                    String recType="Invocation ("+eval.kind()+(ePhase.isEmpty()?"":" · "+ePhase)+")";
                    entries.add(new OccurrenceEntry(sig,recType,occId,"",invId,cut.id(),cpLabel,resLabel,eval.raw(),eval.status(),eval.quality(),"",eval.evidence(),eval.certifiedHistory()));
                }
            }
        }
        return List.copyOf(entries);
    }

    private static String executionPhase(Evaluation eval,java.util.Map<String,Object> occMap){
        if(eval==null)return "";
        String q=eval.quality()==null?"":eval.quality();
        if("FAILED".equals(eval.status())||q.startsWith("TASK_EXECUTION_FAILED:"))return "Failed";
        if(q.contains("CANCELLED"))return "Cancelled";
        if(q.contains("TASK_STEP_WAITING"))return "Waiting";
        if(q.contains("TASK_POST_PENDING_EXECUTION")||q.contains("ORIGINAL_ENGINE_DEFERRED"))return "Executing";
        if("TASK".equals(occMap.get("elementKind"))&&"FULFILLED".equals(eval.status()))return "Succeeded";
        return "";
    }

    private static String formatPhase(String rawPhase,String kind,String verdict,String diagnostic){
        if("EXECUTION_OUTCOME".equals(kind)&&java.util.Set.of("FAIL","FAILED","INCONCLUSIVE","EXECUTION_FAILED").contains(verdict))return "Failed";
        return switch(rawPhase==null?"":rawPhase.trim()){
            case "ENABLED","WAITING"->"Waiting";
            case "EXECUTING"->"Executing";
            case "SUCCEEDED"->"Succeeded";
            case "FAILED"->"Failed";
            case "CANCELLED"->"Cancelled";
            default->diagnostic!=null&&diagnostic.contains("CANCELLED")?"Cancelled":"";
        };
    }

    private List<Cut> historyPrefixFor(Cut currentCut){
        if(currentCut==null)return List.of();
        int idx=-1;
        for(int i=0;i<auditCuts.size();i++)if(auditCuts.get(i).id().equals(currentCut.id())){idx=i;break;}
        if(idx<0)return List.of(currentCut);
        String targetOwner=runtimeOwner(currentCut);
        var result=new java.util.ArrayList<Cut>();
        for(int i=0;i<=idx;i++){
            var c=auditCuts.get(i);
            String owner=runtimeOwner(c);
            if(targetOwner.isBlank()||owner.isBlank()||targetOwner.equals(owner))result.add(c);
        }
        return result.isEmpty()?List.of(currentCut):List.copyOf(result);
    }

    private static String runtimeOwner(Cut cut){
        if(cut==null)return "";
        Object core=cut.visualEvidence().get("originalEngine");
        return core instanceof java.util.Map<?,?> engine&&engine.get("identity")!=null
                ?org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(engine.get("identity"))
                :"";
    }

    private String checkpointLabelFor(Cut cut){
        if(cut==null)return "—";
        int m=milestoneNumbers.getOrDefault(cut.id(),0);
        if(!details.isSelected()&&m>0)return "State "+m;
        int b=-1;
        for(int i=0;i<auditCuts.size();i++)if(auditCuts.get(i).id().equals(cut.id())){b=i+1;break;}
        if(b>0)return (m>0?"State "+m+" · ":"")+"Boundary "+b;
        return PresentationResults.shortId(cut.id());
    }

    private static java.util.Map<String,java.util.Map<String,Object>> structuredOccurrencesFor(Cut cut,String contextId,String elementId,String playerId){
        var result=new java.util.LinkedHashMap<String,java.util.Map<String,Object>>();
        for(var ctx:ExecutionScopeViews.contexts(cut)){
            if(!java.util.Objects.equals(java.util.Objects.toString(ctx.get("context"),""),contextId))continue;
            var occs=ExecutionScopeViews.object(ctx.get("occurrences"));
            for(var entry:occs.entrySet()){
                var occ=ExecutionScopeViews.object(entry.getValue());
                if(!elementId.equals(java.util.Objects.toString(occ.get("sourceElement"),"")))continue;
                if(!playerId.equals(java.util.Objects.toString(occ.getOrDefault("player",""),"")))continue;
                result.put(entry.getKey(),occ);
            }
        }
        return result;
    }

    private static List<java.util.Map<String,Object>> structuredConstraintsFor(Cut cut,String contextId,String elementId,String playerId){
        var result=new java.util.ArrayList<java.util.Map<String,Object>>();
        for(var ctx:ExecutionScopeViews.contexts(cut)){
            if(!java.util.Objects.equals(java.util.Objects.toString(ctx.get("context"),""),contextId))continue;
            if(!(ctx.get("executionConstraints") instanceof List<?> list))continue;
            for(Object item:list){
                var cm=ExecutionScopeViews.object(item);
                String kind=java.util.Objects.toString(cm.getOrDefault("kind",""),"");
                if(!java.util.Set.of("TASK_PRE","TASK_POST","TASK_INVOCATION","INVOCATION_CONFORMANCE","EXECUTION_OUTCOME","GOAL_CONDITION").contains(kind))continue;
                var witness=ExecutionScopeViews.object(cm.get("witness"));
                String target=java.util.Objects.toString(witness.getOrDefault("targetTaskId",witness.getOrDefault("targetGoalId",witness.getOrDefault("element",""))),"");
                String id=java.util.Objects.toString(cm.getOrDefault("id",""),"");
                if(!elementId.equals(target)&&!elementId.equals(id)&&!id.endsWith(":"+elementId)&&!id.endsWith("::"+elementId))continue;
                String wPlayer=java.util.Objects.toString(witness.getOrDefault("player",""),"");
                if(!playerId.isBlank()&&!wPlayer.isBlank()&&!playerId.equals(wPlayer))continue;
                result.add(cm);
            }
        }
        return result;
    }

    private static boolean matchesConstraintEvaluation(Evaluation eval,String elementId,String contextId,String playerId){
        String el=eval.element()==null?"":eval.element();
        if(!el.equals(elementId)&&!el.endsWith(":"+elementId)&&!el.endsWith("::"+elementId))return false;
        if(!contextId.isBlank()&&!eval.context().isBlank()&&!eval.context().equals(contextId)&&!eval.context().startsWith(contextId+" / "))return false;
        if(!playerId.isBlank()&&!eval.player().isBlank()&&!eval.player().equals(playerId))return false;
        return true;
    }

    private static String extractEpisode(java.util.Map<String,Object> occMap){
        if(occMap.get("key") instanceof java.util.Map<?,?> rawKey){
            var k=ExecutionScopeViews.object(rawKey);
            return java.util.Objects.toString(k.getOrDefault("episode",""),"");
        }
        return "";
    }

    private boolean historicalFailure(Cut selected,String element){
        if(selected==null)return false;
        int position=historySummary.positions().getOrDefault(selected.id(),-1);
        return (element==null?historySummary.firstFailure():historySummary.goals().getOrDefault(element,Integer.MAX_VALUE))<=position;
    }
    private String checkLabel(Evaluation row,Cut cut){
        if(row.context().equals(ExecutionScopeViews.WHOLE_SYSTEM))return row.element()+" · Whole system";
        if(!ExecutionScopeViews.ALL.equals(executionScope)||row.context().isBlank())return row.element();
        String context=row.context().split(" / ",2)[0];
        return row.element()+" · "+executionLabelFor(context,1);
    }
    private String verdict(Cut cut){
        if(ExecutionScopeViews.ALL.equals(executionScope)&&historicalFailure(cut,null))return "VIOLATION";
        if(cut.failed()||cut.evaluations().stream().anyMatch(r->!java.util.Set.of("GOAL_TASK","EXECUTION_OUTCOME","CHECKPOINT_CHECK").contains(r.kind())&&java.util.Set.of("FAIL","VIOLATED").contains(r.status())))return "VIOLATION";
        if(cut.evaluations().isEmpty())return "NOT_EVALUATED";
        // Direct/older cuts may not carry the presenter's missing-result rows.
        // Passing OCL alone cannot certify an unobserved declared Goal or Task.
        if(!cut.selectedRequirements()&&liveRows.stream().anyMatch(r->r.instances().isEmpty()))return "INCOMPLETE";
        Object inventory=cut.visualEvidence().get("requirementInventory");
        if(inventory instanceof java.util.Map<?,?> values&&values.get("selectedIntentionalElements") instanceof List<?> targets)
            for(Object target:targets)if(cut.evaluations().stream().noneMatch(r->r.kind().equals("GOAL_TASK")&&r.element().equals(target)&&!r.status().equals("NOT_ASSIGNED")))return "INCOMPLETE";
        if(cut.evaluations().stream().filter(Evaluation::required).anyMatch(r->r.kind().equals("GOAL_TASK")&&r.status().equals("UNKNOWN")))return "INCOMPLETE";
        if(!cut.selectedRequirements()&&!java.util.Set.of("COMPLETE","COMPLETE_OBSERVED").contains(cut.coverage())||!cut.freshness().equals("CURRENT_OBSERVED")
                ||cut.evaluations().stream().filter(Evaluation::required).anyMatch(r->java.util.Set.of("UNKNOWN","UNAVAILABLE","UNDEFINED","SKIPPED","UNSUPPORTED","INCONCLUSIVE","ERROR").contains(r.status())))return "INCOMPLETE";
        if(cut.evaluations().stream().filter(Evaluation::required).anyMatch(r->java.util.Set.of("PENDING","FAILED").contains(r.status())))return "PENDING";
        return "COMPLETE_OBSERVED";
    }

    private static <T extends Component> T named(T component, String name) { component.setName(name); return component; }

    private JPanel buildConformancePanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // 1. Counterexample trace & State navigation bar
        JPanel topPanel = new JPanel(new BorderLayout(3, 3));
        topPanel.setBorder(BorderFactory.createTitledBorder("Observed Prefix & State Navigation"));
        counterexampleTraceArea.setEditable(false);
        counterexampleTraceArea.setLineWrap(true);
        counterexampleTraceArea.setWrapStyleWord(true);
        counterexampleTraceArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        counterexampleTraceArea.setRows(2);

        JPanel navBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        navBar.add(new JLabel("Go to State:"));
        navBar.add(stateSelectorCombo);
        navBar.add(statePrevButton);
        navBar.add(stateNextButton);
        navBar.add(jumpCheckpointButton);
        navBar.add(stateStepInfoLabel);

        statePrevButton.addActionListener(e -> navigateStateStep(-1));
        stateNextButton.addActionListener(e -> navigateStateStep(1));
        jumpCheckpointButton.addActionListener(e -> {
            if (currentFailureCheckpoint >= 0 && currentFailureCheckpoint < currentCounterexampleStates.size()) {
                updateSelectedCounterexampleState(currentFailureCheckpoint);
            }
        });
        stateSelectorCombo.addActionListener(e -> {
            if (!updatingStateComboInternal) {
                int index = stateSelectorCombo.getSelectedIndex();
                if (index >= 0 && index < currentCounterexampleStates.size()) {
                    updateSelectedCounterexampleState(index);
                }
            }
        });

        topPanel.add(new JScrollPane(counterexampleTraceArea), BorderLayout.CENTER);
        topPanel.add(navBar, BorderLayout.SOUTH);
        topPanel.setPreferredSize(new Dimension(400, 105));

        // 2. Cột bên trái (Left Pane): Biểu đồ đồ họa AOL (AOL Diagram Viewer) - Chiếm trọn 100% chiều cao bên trái
        JPanel leftAolPanel = new JPanel(new BorderLayout(3, 3));
        leftAolPanel.setBorder(BorderFactory.createTitledBorder("Accepted USE State (read-only)"));
        leftAolPanel.add(new JScrollPane(stateSnapshotText), BorderLayout.CENTER);
        // Cho phép kéo JSplitPane sang phải thoải mái — không bị chặn bởi preferred/minimum size của aolView
        leftAolPanel.setMinimumSize(new Dimension(80, 0));

        // 3. Cột bên phải (Right Pane): Trình bày theo chiều dọc:
        //    Top: Chuỗi kịch bản & Điều hướng State
        //    Middle: Bảng Eval Trạng thái Mục tiêu (Goals) & Tác vụ (Tasks)
        //    Bottom: Kết quả Conformance & Risk
        JPanel rightOclPanel = new JPanel(new BorderLayout(3, 3));
        rightOclPanel.setBorder(BorderFactory.createTitledBorder("Goal & Task Status at Selected State"));
        // Compact table: fixed-width Status column, Goal/Task takes remaining space
        oclGoalTable.setRowHeight(16);
        oclGoalTable.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        oclGoalTable.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        oclGoalTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
        oclGoalTable.getColumnModel().getColumn(1).setMinWidth(70);
        oclGoalTable.getColumnModel().getColumn(1).setMaxWidth(92);
        oclGoalTable.getColumnModel().getColumn(1).setPreferredWidth(82);
        rightOclPanel.add(new JScrollPane(oclGoalTable), BorderLayout.CENTER);

        JPanel resultPanel = new JPanel(new java.awt.GridLayout(1, 2, 5, 5));
        resultPanel.setPreferredSize(new Dimension(350, 70));

        JPanel leftConfPanel = new JPanel(new BorderLayout(3, 3));
        leftConfPanel.setBorder(BorderFactory.createTitledBorder("Observed prefix"));
        conformanceResultLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        leftConfPanel.add(conformanceResultLabel, BorderLayout.CENTER);

        JPanel rightRiskPanel = new JPanel(new BorderLayout(3, 3));
        rightRiskPanel.setBorder(BorderFactory.createTitledBorder("Coverage / freshness"));
        riskResultLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        rightRiskPanel.add(riskResultLabel, BorderLayout.CENTER);

        resultPanel.add(leftConfPanel);
        resultPanel.add(rightRiskPanel);

        // Sub-split ở cột bên phải (Trên: Bảng Eval, Dưới: Kết quả Conformance/Risk)
        javax.swing.JSplitPane rightEvalSplitPane = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.VERTICAL_SPLIT, rightOclPanel, resultPanel);
        rightEvalSplitPane.setResizeWeight(0.80);
        rightEvalSplitPane.setOneTouchExpandable(true);

        // Right pane: vertical split between trace/nav (top) and eval table + result (bottom)
        javax.swing.JSplitPane rightPane = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.VERTICAL_SPLIT, topPanel, rightEvalSplitPane);
        rightPane.setResizeWeight(0.18);          // trace/nav starts at ~18% height
        rightPane.setOneTouchExpandable(true);
        rightPane.setMinimumSize(new Dimension(180, 0));

        // Căn chỉnh 2 cột ở giữa: SplitPane ngang (Trái: AOL Diagram 100% height, Phải: RightPane)
        javax.swing.JSplitPane mainSplitPane = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.HORIZONTAL_SPLIT, leftAolPanel, rightPane);
        mainSplitPane.setResizeWeight(0.55); // Mặc định dành 55% độ rộng cho AOL Diagram bên trái
        mainSplitPane.setOneTouchExpandable(true);
        // Đặt divider location sau khi panel được render để tránh bị override
        mainSplitPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean initialized = false;
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if (!initialized && mainSplitPane.getWidth() > 0) {
                    initialized = true;
                    mainSplitPane.setDividerLocation(0.55);
                }
            }
        });

        mainPanel.add(mainSplitPane, BorderLayout.CENTER);

        return mainPanel;
    }
}
