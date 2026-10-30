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
    private final boolean liveOnly;
    private LiveGoalSummary liveSummary;
    private java.util.List<LiveGoalSummary.Row> liveRows=java.util.List.of();
    private List<Evaluation> displayedInstances=List.of();
    private final DefaultTableModel liveGoals=new DefaultTableModel(new String[]{"Goal / Task","Raw summary","Certified summary","Result role","Instance evidence"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final DefaultTableModel liveInstances=new DefaultTableModel(new String[]{"Player","Context","Raw","Certified","Quality / reason"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final JTable liveInstanceTable=named(new JTable(liveInstances),"live-goal-instances");
    private final JTabbedPane liveDetailTabs=named(new JTabbedPane(),"live-detail-tabs");
    private final JLabel requirementScope=named(new JLabel("No selected verification state"),"observed-requirement-scope");
    private final DefaultTableModel liveChecks=new DefaultTableModel(new String[]{"OCL / Binding check","Result","Quality / reason","Context / invocation / expression / witness"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final JTable liveCheckTable=named(new JTable(liveChecks),"live-ocl-checks");
    private final JTable liveGoalTable=named(new JTable(liveGoals),"live-goal-status");
    private final JTextArea liveDetails=named(new JTextArea(),"live-evidence-details");
    private final JTextArea counterexampleTraceArea = new JTextArea();
    private final JTextArea stateSnapshotText = new JTextArea();
    private final JComboBox<String> stateSelectorCombo = named(new JComboBox<>(),"observed-state-selector");
    private final JButton statePrevButton = named(new JButton("< Previous State"), "observed-previous");
    private final JButton stateNextButton = named(new JButton("Next State >"), "observed-next");
    private final JButton jumpCheckpointButton = named(new JButton("Failure Point"), "observed-failure");
    private record FailurePoint(Cut cut,Evaluation evaluation,String category,int boundary,int milestone,boolean audit) {
        @Override public String toString(){
            String object=evaluation!=null&&evaluation.kind().equals("INVARIANT_BODY")?evaluation.context():"";
            int separator=object.lastIndexOf(" / ");if(separator>=0)object=object.substring(separator+3);
            return (audit?"Boundary "+boundary:milestone==0?"Before first milestone":"Milestone "+milestone)+" · "+category+" · "+(evaluation==null?"verification failure":evaluation.element()+" ["+evaluation.status()+"]")+(object.isBlank()?"":" · "+object);
        }
    }
    private final JComboBox<String> failureFilter=named(new JComboBox<>(new String[]{"All failures","Goal / Task","OCL / constraints","Diagnostics","Unmet conditions"}),"observed-failure-filter");
    private final JComboBox<FailurePoint> failurePoints=named(new JComboBox<>(),"observed-failure-points");
    private boolean updatingFailures;
    private FailurePoint failureFocus;
    private List<Cut> businessMilestones=List.of();
    private java.util.Map<String,Integer> milestoneNumbers=java.util.Map.of();
    private final JToggleButton auditToggle=named(new JToggleButton("Audit details"),"observed-audit-toggle");
    private final JButton milestonesButton=named(new JButton("Milestones"),"observed-milestones");
    private final JLabel stateStepInfoLabel = named(new JLabel("State 0 / 0"),"observed-state-position");
    private final JLabel conformanceResultLabel = new JLabel("NOT_EVALUATED", SwingConstants.CENTER);
    private final JLabel riskResultLabel = named(new JLabel("UNAVAILABLE", SwingConstants.CENTER), "observed-quality");
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
    private java.util.function.Consumer<String> executionListener=id->{};
    public void onExecutionSelected(java.util.function.Consumer<String> listener){executionListener=java.util.Objects.requireNonNull(listener);}
    public void selectExecution(String id){
        if(id==null||id.isBlank()||java.util.Objects.equals(executionScope,id))return;
        executionScope=id==null?"":id;failureFocus=null;intervalIds=null;selectedStateIndex=-1;
        setCuts(allCuts);executionListener.accept(executionScope);
    }
    private void refreshExecutions(){
        updatingExecutions=true;
        try{executionLabels=ExecutionScopeViews.choices(allCuts);executionSelector.removeAllItems();
            executionLabels.keySet().forEach(executionSelector::addItem);
            if(!executionLabels.containsKey(executionScope))executionScope=executionLabels.isEmpty()?"":executionLabels.keySet().iterator().next();
            executionSelector.setSelectedItem(executionScope);
        }finally{updatingExecutions=false;}
    }
    private java.util.Set<String> intervalIds;
    private final JCheckBox details=named(new JCheckBox("Show all audit boundaries",false),"observed-details");
    private int selectedStateIndex = -1;
    private int currentFailureCheckpoint = -1;
    private boolean updatingStateComboInternal;

    public ObservedConformancePanel() {this(false);}
    public static ObservedConformancePanel liveResults(org.vnu.sme.goal.dsl.istar.mm.GoalModel model){var panel=new ObservedConformancePanel(true);panel.liveSummary=new LiveGoalSummary(model);panel.renderLive(null);return panel;}
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
                setText(shown<0?(value==null?"Select state":"Before first milestone"):(details.isSelected()?"Boundary ":"Milestone ")+(shown+1));setToolTipText(value==null?null:value.toString());return this;
            }
        });
        if(liveOnly){setName("use4goal-live-results");add(buildLiveResults(),BorderLayout.CENTER);return;}
        add(buildConformancePanel(), BorderLayout.CENTER);
        var controls=new JPanel(new FlowLayout(FlowLayout.LEFT,8,3));
        controls.add(details);
        add(controls,BorderLayout.NORTH);
        details.addActionListener(e->{intervalIds=null;details.setText("Show all audit boundaries");setCuts(allCuts);});
        var intervalButton=named(new JButton("Inspect milestone interval"),"observed-interval-details");
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

    public record PreparedCuts(List<Cut> all,java.util.Map<String,String> labels,String scope,List<Cut> audit,List<Cut> milestones,java.util.Map<String,Integer> numbers,List<FailurePoint> failures) {}
    /** Worker-owned cache. Reuse projections only for the identical immutable cut. */
    public static final class PreparationCache {
        private final java.util.IdentityHashMap<Cut,java.util.Map<String,Cut>> projections=new java.util.IdentityHashMap<>();
        public PreparedCuts prepare(List<Cut> input,String requested,boolean live){
            var all=List.copyOf(input);var labels=live?ExecutionScopeViews.choices(all):java.util.Map.<String,String>of();
            String scope=requested;
            if(live&&!labels.containsKey(scope))scope=labels.isEmpty()?"":labels.keySet().iterator().next();
            boolean awaiting=live&&scope.isBlank()&&all.stream().anyMatch(c->c.visualEvidence().containsKey("originalEngine"));
            var audit=new java.util.ArrayList<Cut>();
            if(!awaiting)for(var cut:all){String selected=scope;audit.add(scope.isBlank()?cut:projections.computeIfAbsent(cut,k->new java.util.HashMap<>()).computeIfAbsent(scope,k->ExecutionScopeViews.project(cut,selected)));}
            var milestones=BusinessTimeline.summarize(audit);
            if(!scope.isBlank()&&!milestones.isEmpty()&&!audit.isEmpty()&&audit.getLast().businessKey().isBlank()){
                var last=audit.getLast();var retained=new java.util.ArrayList<Cut>(milestones);var phase=retained.getLast();
                retained.set(retained.size()-1,new Cut(last.id(),last.trace(),last.state(),last.evaluations(),last.coverage(),last.freshness(),last.failed(),phase.businessKey(),last.requirements(),last.businessState(),last.executionMilestones(),last.selectedRequirements(),last.businessPopulationEmpty(),last.visualEvidence()));milestones=List.copyOf(retained);
            }
            var numbers=BusinessTimeline.milestoneNumbers(audit,milestones);
            var keep=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Cut,Boolean>());keep.addAll(all);projections.keySet().retainAll(keep);
            return new PreparedCuts(all,labels,scope,List.copyOf(audit),milestones,numbers,live?collectFailurePoints(audit,numbers):List.of());
        }
    }
    public String executionScope(){return executionScope;}
    private List<FailurePoint> preparedFailures=List.of();

    public void setCuts(List<Cut> cuts) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("OBSERVED_VIEW_REQUIRES_EDT");
        applyPrepared(new PreparationCache().prepare(cuts,executionScope,liveOnly));
    }
    public void applyPrepared(PreparedCuts prepared){
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("OBSERVED_VIEW_REQUIRES_EDT");
        allCuts=prepared.all();executionScope=prepared.scope();
        if(liveOnly){updatingExecutions=true;try{
            if(!executionLabels.equals(prepared.labels())){executionLabels=prepared.labels();executionSelector.setModel(new DefaultComboBoxModel<>(executionLabels.keySet().toArray(String[]::new)));}
            executionSelector.setSelectedItem(executionScope);
        }finally{updatingExecutions=false;}}
        auditCuts=prepared.audit();businessMilestones=prepared.milestones();milestoneNumbers=prepared.numbers();preparedFailures=prepared.failures();
        if(failureFocus!=null&&auditCuts.stream().noneMatch(c->c.id().equals(failureFocus.cut().id())))failureFocus=null;
        if(liveOnly)refreshFailurePoints();
        if(intervalIds!=null&&auditCuts.stream().noneMatch(c->intervalIds.contains(c.id()))){intervalIds=null;details.setSelected(false);details.setText("Show all audit boundaries");}
        var cuts=intervalIds!=null?auditCuts.stream().filter(c->intervalIds.contains(c.id())).toList():details.isSelected()?auditCuts:businessMilestones;
        if (currentCounterexampleStates.equals(cuts)) {if(liveOnly){jumpCheckpointButton.setEnabled(failurePoints.getItemCount()>0);if(selectedCut()!=null)updatePosition(selectedCut());stateSelectorCombo.repaint();}return;}
        String selected = selectedCut() == null ? "" : selectedCut().id();
        boolean follow = selectedStateIndex == currentCounterexampleStates.size() - 1;
        currentCounterexampleStates = List.copyOf(cuts);
        currentFailureCheckpoint = -1;
        int target = cuts.size() - 1;
        updatingStateComboInternal = true;
        try {
            stateSelectorCombo.removeAllItems();
            for (int i = 0; i < cuts.size(); i++) {
                Cut cut = cuts.get(i);
                stateSelectorCombo.addItem(cut.id());
                if (!follow && cut.id().equals(selected)) target = i;
                if (currentFailureCheckpoint < 0 && cut.failed()) currentFailureCheckpoint = i;
            }
        } finally { updatingStateComboInternal = false; }
        updateSelectedCounterexampleState(target);
        if(liveOnly)jumpCheckpointButton.setEnabled(failurePoints.getItemCount()>0);
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
            failurePoints.setSelectedIndex(-1);
            if(selected!=null)for(int i=0;i<failurePoints.getItemCount();i++){var point=failurePoints.getItemAt(i);if(point.cut().id().equals(selected.cut().id())&&java.util.Objects.equals(point.evaluation(),selected.evaluation())){failurePoints.setSelectedIndex(i);break;}}
            failurePoints.setEnabled(failurePoints.getItemCount()>0);jumpCheckpointButton.setEnabled(failurePoints.getItemCount()>0);
            jumpCheckpointButton.setToolTipText(failurePoints.getItemCount()>0?"Open the first point in the selected category for this execution; use the list for other points.":"No matching failures in this execution. A false condition is not a violation; select Unmet conditions to inspect it.");
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
        var row=point.evaluation();if(row==null){liveDetails.setText(point.cut().trace());liveDetailTabs.setSelectedIndex(1);return;}
        if(row.kind().equals("GOAL_TASK"))for(int i=0;i<liveRows.size();i++)if(liveRows.get(i).id().equals(row.element())){
            liveGoalTable.setRowSelectionInterval(i,i);showGoalInstances(i);
            for(int j=0;j<displayedInstances.size();j++)if(displayedInstances.get(j).equals(row)){liveInstanceTable.setRowSelectionInterval(j,j);break;}
            break;
        }
        if(!row.kind().equals("GOAL_TASK"))for(int i=0;i<liveChecks.getRowCount();i++)if(row.element().equals(liveChecks.getValueAt(i,0))&&(row.kind().equals("INVARIANT_BODY")||row.evidence().equals(liveChecks.getValueAt(i,3)))){liveCheckTable.setRowSelectionInterval(i,i);liveCheckTable.scrollRectToVisible(liveCheckTable.getCellRect(i,0,true));break;}
        // Row selection may render the aggregate. Restore the exact selected object witness afterwards.
        liveDetails.setText(row.element()+"\n"+row.status()+" | "+row.quality()+"\n"+row.evidence()+"\n"+row.certifiedHistory());liveDetails.setCaretPosition(0);liveDetailTabs.setSelectedIndex(1);
    }

    private void inspectInterval() {
        if(details.isSelected()||selectedCut()==null)return;
        String previous=selectedStateIndex==0?"":currentCounterexampleStates.get(selectedStateIndex-1).id();String end=selectedCut().id();
        var ids=new java.util.LinkedHashSet<String>();boolean include=previous.isEmpty();
        for(var cut:auditCuts){if(include)ids.add(cut.id());if(cut.id().equals(previous))include=true;if(cut.id().equals(end))break;}
        intervalIds=java.util.Set.copyOf(ids);details.setSelected(true);details.setText("Milestone interval details");setCuts(allCuts);
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
            conformanceResultLabel.setText("NOT_EVALUATED"); riskResultLabel.setText("UNAVAILABLE");
            statePrevButton.setEnabled(false); stateNextButton.setEnabled(false); jumpCheckpointButton.setEnabled(false);
            selectionListener.accept(null);
            if(liveOnly)renderLive(null);
            return;
        }
        selectedStateIndex = Math.max(0, Math.min(currentCounterexampleStates.size() - 1, stateIndex));
        updatingStateComboInternal = true;
        try { stateSelectorCombo.setSelectedIndex(selectedStateIndex); }
        finally { updatingStateComboInternal = false; }
        Cut cut = selectedCut();
        statePrevButton.setEnabled(selectedStateIndex > 0);
        stateNextButton.setEnabled(selectedStateIndex < currentCounterexampleStates.size() - 1);
        jumpCheckpointButton.setEnabled(liveOnly?failurePoints.getItemCount()>0:currentFailureCheckpoint>=0);
        milestonesButton.setVisible(details.isSelected()||failureFocus!=null);
        updatePosition(cut);
        stateSnapshotText.setText((!executionScope.isBlank()||!details.isSelected())&&!cut.businessState().isBlank()?cut.businessState():cut.state()); stateSnapshotText.setCaretPosition(0);
        counterexampleTraceArea.setText(cut.trace()); counterexampleTraceArea.setCaretPosition(0);
        selectionListener.accept(cut);
        if(liveOnly){renderLive(cut);return;}
        for (Evaluation row : cut.evaluations()) if(details.isSelected()||row.required()||java.util.Set.of("FAIL","ERROR","VIOLATED").contains(row.status()))oclGoalModel.addRow(new Object[]{row.element(), row.raw(), row.status(), row.quality(), row.evidence(),row.certifiedHistory()});
        conformanceResultLabel.setText(cut.failed() ? "Observed violation" : "Observed prefix");
        riskResultLabel.setToolTipText(cut.trace());
        riskResultLabel.setText("<html>"+cut.requirements()+"<br>Overall sources: "+cut.coverage()+" / "+cut.freshness()+"</html>");
    }

    private void updatePosition(Cut cut){
        stateStepInfoLabel.setText(details.isSelected()?"Boundary "+(auditCuts.indexOf(cut)+1)+" / "+auditCuts.size():((milestoneNumbers.getOrDefault(cut.id(),0)==0?"Before first milestone":"Milestone "+milestoneNumbers.get(cut.id())+" / "+businessMilestones.size())+(failureFocus==null?"":failureFocus.category().equals("Unmet conditions")?" — Unmet condition":" — Failure Point")));
    }

    private JPanel buildLiveResults(){
        var goals=liveGoalTable;var checks=liveCheckTable;
        int[] widths={260,110,130,135,185};for(int i=0;i<widths.length;i++)goals.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        int[] instanceWidths={110,110,100,100,350};for(int i=0;i<instanceWidths.length;i++)liveInstanceTable.getColumnModel().getColumn(i).setPreferredWidth(instanceWidths[i]);
        goals.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);liveDetails.setEditable(false);liveDetails.setLineWrap(true);liveDetails.setWrapStyleWord(true);
        goals.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()){showGoalInstances(goals.getSelectedRow());if(goals.getSelectedRow()>=0)liveDetailTabs.setSelectedIndex(0);}});
        checks.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()){int row=checks.getSelectedRow();if(row>=0){liveDetails.setText(checks.getValueAt(row,0)+"\n"+checks.getValueAt(row,1)+" | "+checks.getValueAt(row,2)+"\n"+checks.getValueAt(row,3));liveDetails.setCaretPosition(0);liveDetailTabs.setSelectedIndex(1);}}});
        liveInstanceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        liveInstanceTable.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())showInstanceEvidence();});
        liveDetailTabs.addTab("Instances",new JScrollPane(liveInstanceTable));liveDetailTabs.addTab("Evidence",new JScrollPane(liveDetails));
        goals.setToolTipText("Each row summarizes one definition across observed instances. MIXED/UNKNOWN is not a verdict for the selected requirement. Select a row to inspect each player.");
        var upper=new JSplitPane(JSplitPane.VERTICAL_SPLIT,section("Goal & Task Status — selected execution",new JScrollPane(goals)),section("OCL / runtime binding checks — selected execution",new JScrollPane(checks)));upper.setResizeWeight(.55);
        var right=new JSplitPane(JSplitPane.VERTICAL_SPLIT,upper,section("Selected evidence / instance details",liveDetailTabs));right.setResizeWeight(.7);
        stateSnapshotText.setEditable(false);var split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,section("Accepted CSL / USE state (read-only)",new JScrollPane(stateSnapshotText)),right);split.setResizeWeight(.4);
        var panel=new JPanel(new BorderLayout(5,5));
        var navigation=new JPanel(new BorderLayout());navigation.setBorder(BorderFactory.createTitledBorder("State navigation"));
        executionSelector.setPrototypeDisplayValue("Execution 123 — Entity · id='value'");
        executionSelector.setRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
            var label=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);String id=java.util.Objects.toString(value,"");
            label.setText(executionLabels.getOrDefault(id,id.isBlank()?"No admitted execution":id));label.setToolTipText(id);return label;}});
        executionSelector.addActionListener(e->{if(!updatingExecutions)selectExecution((String)executionSelector.getSelectedItem());});
        var buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,6,3));buttons.add(new JLabel("Execution:"));buttons.add(executionSelector);buttons.add(new JLabel("Go to State:"));buttons.add(stateSelectorCombo);buttons.add(statePrevButton);buttons.add(stateNextButton);buttons.add(jumpCheckpointButton);buttons.add(milestonesButton);buttons.add(stateStepInfoLabel);
        var options=new JPanel(new FlowLayout(FlowLayout.LEFT,6,3));
        options.add(failureFilter);failurePoints.setPrototypeDisplayValue(new FailurePoint(new Cut("","","",List.of(),"","",false),new Evaluation("Constraint or Task definition","","FAIL","",""),"OCL / constraints",9999,9999,false));options.add(failurePoints);
        failurePoints.setRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
            super.getListCellRendererComponent(list,value,index,selected,focus);
            if(value==null&&failurePoints.getItemCount()==0)setText("Unmet conditions".equals(failureFilter.getSelectedItem())?"No unmet conditions in this execution":"No matching failures in this execution");
            return this;
        }});
        options.add(auditToggle);details.setVisible(false);options.add(details);
        auditToggle.addActionListener(e->{
            var focus=failureFocus;String selected=selectedCut()==null?"":selectedCut().id();
            details.setSelected(auditToggle.isSelected());intervalIds=null;setCuts(allCuts);
            if(focus!=null)openFailure(focus);
            else for(int i=0;i<currentCounterexampleStates.size();i++)if(currentCounterexampleStates.get(i).id().equals(selected)){updateSelectedCounterexampleState(i);break;}
            stateSelectorCombo.repaint();
        });
        milestonesButton.setVisible(false);milestonesButton.addActionListener(e->{failureFocus=null;intervalIds=null;auditToggle.setSelected(false);details.setSelected(false);details.setText("Show all audit boundaries");setCuts(allCuts);if(selectedStateIndex>=0)updateSelectedCounterexampleState(selectedStateIndex);});
        failureFilter.addActionListener(e->{refreshFailurePoints();});failurePoints.addActionListener(e->{if(!updatingFailures)openFailure((FailurePoint)failurePoints.getSelectedItem());});
        failurePoints.setToolTipText("Points belong only to the selected execution. All failures excludes unmet conditions. Persistent failures appear once; only qualified PASS/FULFILLED starts recovery. Pending/SKIPPED never erase a failure. Supporting Task failure does not automatically violate a selected requirement.");
        failureFilter.setToolTipText("Unmet conditions are inspectable progress states, not verification failures. Diagnostics contains evaluation errors; missing evidence remains UNKNOWN.");
        navigation.add(requirementScope,BorderLayout.NORTH);navigation.add(buttons,BorderLayout.CENTER);navigation.add(options,BorderLayout.SOUTH);
        statePrevButton.addActionListener(e->navigateStateStep(-1));stateNextButton.addActionListener(e->navigateStateStep(1));jumpCheckpointButton.addActionListener(e->openAuditedFailure());
        stateSelectorCombo.addActionListener(e->{if(!updatingStateComboInternal&&stateSelectorCombo.getSelectedIndex()>=0){failureFocus=null;updateSelectedCounterexampleState(stateSelectorCombo.getSelectedIndex());}});
        details.addActionListener(e->{intervalIds=null;details.setText("Show all audit boundaries");setCuts(allCuts);});
        conformanceResultLabel.setName("observed-verdict");conformanceResultLabel.setFont(new Font(Font.SANS_SERIF,Font.BOLD,16));riskResultLabel.setFont(new Font(Font.SANS_SERIF,Font.BOLD,14));
        var footer=new JPanel(new GridLayout(1,2,5,5));footer.setPreferredSize(new Dimension(800,95));footer.add(section("Verification result",conformanceResultLabel));footer.add(section("Completeness / freshness",riskResultLabel));
        panel.add(navigation,BorderLayout.NORTH);panel.add(split,BorderLayout.CENTER);panel.add(footer,BorderLayout.SOUTH);return panel;
    }
    private JPanel section(String title,Component content){var panel=new JPanel(new BorderLayout());panel.setBorder(BorderFactory.createTitledBorder(title));panel.add(content,BorderLayout.CENTER);return panel;}
    private void renderLive(Cut cut){
        if(liveSummary==null)return;
        liveRows=liveSummary.rows(cut==null?List.of():cut.evaluations());
        // The M1 row set/order never depends on callback count or runtime population.
        if(liveGoals.getRowCount()!=liveRows.size()){liveGoals.setRowCount(0);for(var row:liveRows)liveGoals.addRow(new Object[]{scopedLabel(row.label(),cut),row.originalStatus(),row.status(),row.resultRole(),row.counts()});}
        else for(int i=0;i<liveRows.size();i++){liveGoals.setValueAt(scopedLabel(liveRows.get(i).label(),cut),i,0);liveGoals.setValueAt(liveRows.get(i).originalStatus(),i,1);liveGoals.setValueAt(liveRows.get(i).status(),i,2);liveGoals.setValueAt(liveRows.get(i).resultRole(),i,3);liveGoals.setValueAt(liveRows.get(i).counts(),i,4);}
        liveChecks.setRowCount(0);
        if(cut==null){conformanceResultLabel.setText("NOT_EVALUATED");riskResultLabel.setText("UNAVAILABLE");requirementScope.setText("No selected verification state");displayedInstances=List.of();liveInstances.setRowCount(0);liveDetails.setText("");stateSnapshotText.setText("");statePrevButton.setEnabled(false);stateNextButton.setEnabled(false);jumpCheckpointButton.setEnabled(false);return;}
        var selected=liveRows.stream().filter(r->r.resultRole().equals("Selected requirement")).map(LiveGoalSummary.Row::label).toList();
        Object inventory=cut.visualEvidence().get("requirementInventory");
        boolean explicitSelection=cut.selectedRequirements()||inventory instanceof java.util.Map<?,?> values&&values.get("selectedIntentionalElements") instanceof List<?> ids&&!ids.isEmpty();
        String scope=explicitSelection?"Selected Goal/Task requirements: "+(selected.isEmpty()?"no admitted result":String.join(", ",selected)):"Goal/Task requirements: all model definitions in observed instances";
        requirementScope.setText(scope+" | Every authored OCL invariant remains required.");
        requirementScope.setToolTipText("COMPLETE_OBSERVED applies to these requirements at the selected observed cut. Supporting skill summaries may remain UNKNOWN. Missing demand is not proof of non-applicability; overall source coverage is reported separately.");
        for(var row:cut.evaluations())if(!row.kind().equals("GOAL_TASK"))liveChecks.addRow(new Object[]{row.element(),row.status(),row.quality(),row.evidence()});
        stateSnapshotText.setText((!executionScope.isBlank()||!details.isSelected())&&!cut.businessState().isBlank()?cut.businessState():cut.state());stateSnapshotText.setCaretPosition(0);
        conformanceResultLabel.setText(verdict(cut));
        conformanceResultLabel.setToolTipText("Selected execution: independent Goal/Task root and scoped native OCL, including applicable shared structural rules");
        riskResultLabel.setToolTipText(cut.trace());
        String overall="";
        riskResultLabel.setText("<html>"+overall+cut.requirements().replace(" | Last completion delay:","<br>Last completion delay:")+"<br>Sources: "+cut.coverage()+" / "+cut.freshness()+"</html>");
        showGoalInstances(liveGoalTable.getSelectedRow());
    }

    private String scopedLabel(String label,Cut cut){
        if(cut==null||executionScope.isBlank())return label;
        var context=ExecutionScopeViews.contexts(cut).stream().filter(c->executionScope.equals(c.get("context"))).findFirst().orElse(java.util.Map.of());
        return label+" · "+ExecutionScopeViews.actorLabel(context,new java.util.ArrayList<>(executionLabels.keySet()).indexOf(executionScope)+1);
    }
    private void showGoalInstances(int index){
        int prior=liveInstanceTable.getSelectedRow();
        String selected=prior>=0&&prior<displayedInstances.size()?displayedInstances.get(prior).occurrence():"";
        liveInstances.setRowCount(0);
        displayedInstances=List.of();
        if(index<0||index>=liveRows.size()){liveDetails.setText("");return;}
        var row=liveRows.get(index);liveDetails.setText(row.detail());liveDetails.setCaretPosition(0);
        displayedInstances=row.instances();
        var contexts=new java.util.TreeSet<String>();displayedInstances.stream().map(Evaluation::context).filter(c->!c.isBlank()).forEach(contexts::add);
        var contextLabels=new java.util.LinkedHashMap<String,String>();for(String context:contexts)contextLabels.put(context,executionLabels.getOrDefault(context,context.length()>30?"Context "+(contextLabels.size()+1):context));
        for(var instance:displayedInstances)liveInstances.addRow(new Object[]{playerLabel(instance.player()),contextLabels.getOrDefault(instance.context(),"Unavailable"),instance.originalStatus(),instance.status(),instance.quality()});
        if(!selected.isBlank())for(int i=0;i<displayedInstances.size();i++)if(selected.equals(displayedInstances.get(i).occurrence())){liveInstanceTable.setRowSelectionInterval(i,i);break;}
    }
    private void showInstanceEvidence(){
        int goal=liveGoalTable.getSelectedRow(),instance=liveInstanceTable.getSelectedRow();
        if(goal<0||goal>=liveRows.size()||instance<0||instance>=displayedInstances.size())return;
        var row=liveRows.get(goal);var evidence=displayedInstances.get(instance);
        liveDetails.setText(row.label()+"\nPlayer: "+evidence.player()+"\nContext: "+evidence.context()+"\nOccurrence: "+evidence.occurrence()+"\nRaw: "+evidence.originalStatus()+" | Certified: "+evidence.status()+"\nQuality: "+evidence.quality()+"\n"+evidence.evidence()+"\n"+evidence.certifiedHistory());liveDetails.setCaretPosition(0);
    }
    private static String playerLabel(String identity){
        if(identity.isBlank())return "Unavailable";
        try{return org.jacamo.bridge.contract.BridgeEntityId.parse(identity).localId();}catch(org.jacamo.bridge.contract.ContractException invalid){return identity;}
    }

    private String verdict(Cut cut){
        if(cut.failed()||cut.evaluations().stream().anyMatch(r->!java.util.Set.of("GOAL_TASK","EXECUTION_OUTCOME").contains(r.kind())&&java.util.Set.of("FAIL","VIOLATED").contains(r.status())))return "VIOLATION";
        if(cut.evaluations().isEmpty())return "NOT_EVALUATED";
        // Direct/older cuts may not carry the presenter's missing-result rows.
        // Passing OCL alone cannot certify an unobserved declared Goal or Task.
        if(!cut.selectedRequirements()&&liveRows.stream().anyMatch(r->r.instances().isEmpty()))return "INCOMPLETE";
        Object inventory=cut.visualEvidence().get("requirementInventory");
        if(inventory instanceof java.util.Map<?,?> values&&values.get("selectedIntentionalElements") instanceof List<?> targets)
            for(Object target:targets)if(cut.evaluations().stream().noneMatch(r->r.kind().equals("GOAL_TASK")&&r.element().equals(target)))return "INCOMPLETE";
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
