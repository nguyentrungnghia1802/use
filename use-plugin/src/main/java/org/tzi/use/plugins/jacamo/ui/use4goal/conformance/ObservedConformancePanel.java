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
    public record Evaluation(String element, String raw, String status, String quality, String evidence,String certifiedHistory,boolean required,String kind,String originalStatus) {
        public Evaluation(String element,String raw,String status,String quality,String evidence,String history,boolean required,String kind){this(element,raw,status,quality,evidence,history,required,kind,kind.equals("GOAL_TASK")?raw:"");}
        public Evaluation(String element,String raw,String status,String quality,String evidence,String history,boolean required){this(element,raw,status,quality,evidence,history,required,"");}
        public Evaluation(String element,String raw,String status,String quality,String evidence,String history){this(element,raw,status,quality,evidence,history,true);}
        public Evaluation(String element,String raw,String status,String quality,String evidence){this(element,raw,status,quality,evidence,"",true);}
    }
    public record Cut(String id, String trace, String state, List<Evaluation> evaluations,
                      String coverage, String freshness, boolean failed,String businessKey,String requirements,String businessState,boolean executionMilestones,boolean selectedRequirements,boolean businessPopulationEmpty) {
        public Cut(String id,String trace,String state,List<Evaluation> evaluations,String coverage,String freshness,boolean failed,String businessKey,String requirements,String businessState,boolean executionMilestones,boolean selectedRequirements){this(id,trace,state,evaluations,coverage,freshness,failed,businessKey,requirements,businessState,executionMilestones,selectedRequirements,false);}
        public Cut(String id,String trace,String state,List<Evaluation> evaluations,String coverage,String freshness,boolean failed,String businessKey,String requirements,String businessState,boolean executionMilestones){this(id,trace,state,evaluations,coverage,freshness,failed,businessKey,requirements,businessState,executionMilestones,false);}
        public Cut(String id,String trace,String state,List<Evaluation> evaluations,String coverage,String freshness,boolean failed,String businessKey,String requirements,String businessState){this(id,trace,state,evaluations,coverage,freshness,failed,businessKey,requirements,businessState,true);}
        public Cut(String id,String trace,String state,List<Evaluation> evaluations,String coverage,String freshness,boolean failed,String businessKey,String requirements){this(id,trace,state,evaluations,coverage,freshness,failed,businessKey,requirements,"");}
        public Cut(String id,String trace,String state,List<Evaluation> evaluations,String coverage,String freshness,boolean failed){this(id,trace,state,evaluations,coverage,freshness,failed,state,"","");}
        public Cut { evaluations = List.copyOf(evaluations); }
    }
    private final boolean liveOnly;
    private LiveGoalSummary liveSummary;
    private java.util.List<LiveGoalSummary.Row> liveRows=java.util.List.of();
    private final DefaultTableModel liveGoals=new DefaultTableModel(new String[]{"Goal / Task","Original history (raw)","Certified execution"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final DefaultTableModel liveChecks=new DefaultTableModel(new String[]{"OCL / Binding check","Result","Quality / reason","Context / invocation / expression / witness"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final JTable liveGoalTable=named(new JTable(liveGoals),"live-goal-status");
    private final JTextArea liveDetails=named(new JTextArea(),"live-evidence-details");
    private final JTextArea counterexampleTraceArea = new JTextArea();
    private final JTextArea stateSnapshotText = new JTextArea();
    private final JComboBox<String> stateSelectorCombo = new JComboBox<>();
    private final JButton statePrevButton = named(new JButton("< Previous State"), "observed-previous");
    private final JButton stateNextButton = named(new JButton("Next State >"), "observed-next");
    private final JButton jumpCheckpointButton = named(new JButton("Go to Failure Checkpoint"), "observed-failure");
    private final JLabel stateStepInfoLabel = new JLabel("State 0 / 0");
    private final JLabel conformanceResultLabel = new JLabel("NOT_EVALUATED", SwingConstants.CENTER);
    private final JLabel riskResultLabel = named(new JLabel("UNAVAILABLE", SwingConstants.CENTER), "observed-quality");
    private final DefaultTableModel oclGoalModel = new DefaultTableModel(
            new String[]{"Goal / Task / OCL", "Raw", "Qualified / propagated", "Quality", "Evidence", "Last certified history"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable oclGoalTable = named(new JTable(oclGoalModel), "observed-goals");
    private List<Cut> currentCounterexampleStates = List.of();
    private List<Cut> auditCuts=List.of();
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
                setText(ordinal<0?"Select state":(details.isSelected()?"Boundary ":"Milestone ")+(ordinal+1));setToolTipText(value==null?null:value.toString());return this;
            }
        });
        if(liveOnly){setName("use4goal-live-results");add(buildLiveResults(),BorderLayout.CENTER);return;}
        add(buildConformancePanel(), BorderLayout.CENTER);
        var controls=new JPanel(new FlowLayout(FlowLayout.LEFT,8,3));
        controls.add(details);
        add(controls,BorderLayout.NORTH);
        details.addActionListener(e->{intervalIds=null;details.setText("Show all audit boundaries");setCuts(auditCuts);});
        var intervalButton=named(new JButton("Inspect milestone interval"),"observed-interval-details");
        controls.add(intervalButton);intervalButton.addActionListener(e->inspectInterval());
        statePrevButton.setEnabled(false);
        stateNextButton.setEnabled(false);
        jumpCheckpointButton.setEnabled(false);
    }

    public Cut selectedCut() {
        return selectedStateIndex < 0 ? null : currentCounterexampleStates.get(selectedStateIndex);
    }

    public void setCuts(List<Cut> cuts) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("OBSERVED_VIEW_REQUIRES_EDT");
        auditCuts=List.copyOf(cuts);
        if(intervalIds!=null&&auditCuts.stream().noneMatch(c->intervalIds.contains(c.id()))){intervalIds=null;details.setSelected(false);details.setText("Show all audit boundaries");}
        cuts=details.isSelected()?(intervalIds==null?auditCuts:auditCuts.stream().filter(c->intervalIds.contains(c.id())).toList()):BusinessTimeline.summarize(auditCuts);
        if (currentCounterexampleStates.equals(cuts)) {if(liveOnly)jumpCheckpointButton.setEnabled(auditCuts.stream().anyMatch(ObservedConformancePanel::failureBoundary));return;}
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
        if(liveOnly)jumpCheckpointButton.setEnabled(auditCuts.stream().anyMatch(ObservedConformancePanel::failureBoundary));
    }

    private static boolean failureBoundary(Cut cut){return cut.failed()||cut.evaluations().stream().anyMatch(r->java.util.Set.of("FAIL","FAILED","VIOLATED","ERROR").contains(r.status()));}
    private void openAuditedFailure() {
        var first=auditCuts.stream().filter(ObservedConformancePanel::failureBoundary).findFirst();if(first.isEmpty())return;
        for(int i=0;i<currentCounterexampleStates.size();i++)if(currentCounterexampleStates.get(i).id().equals(first.get().id())){updateSelectedCounterexampleState(i);return;}
        // A diagnostic boundary is not a new business state. Open the exact
        // retained audit cut without synthesizing a state or dropping history.
        intervalIds=java.util.Set.of(first.get().id());details.setSelected(true);details.setText("Failure checkpoint (audit)");setCuts(auditCuts);
    }

    private void inspectInterval() {
        if(details.isSelected()||selectedCut()==null)return;
        String previous=selectedStateIndex==0?"":currentCounterexampleStates.get(selectedStateIndex-1).id();String end=selectedCut().id();
        var ids=new java.util.LinkedHashSet<String>();boolean include=previous.isEmpty();
        for(var cut:auditCuts){if(include)ids.add(cut.id());if(cut.id().equals(previous))include=true;if(cut.id().equals(end))break;}
        intervalIds=java.util.Set.copyOf(ids);details.setSelected(true);details.setText("Milestone interval details");setCuts(auditCuts);
    }
    private void navigateStateStep(int delta) {
        if (currentCounterexampleStates.isEmpty()) return;
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
        jumpCheckpointButton.setEnabled(currentFailureCheckpoint >= 0 || liveOnly&&auditCuts.stream().anyMatch(ObservedConformancePanel::failureBoundary));
        stateStepInfoLabel.setText((details.isSelected()?"Boundary ":"Milestone ") + (selectedStateIndex + 1) + " / " + currentCounterexampleStates.size()+" ("+auditCuts.size()+" audit boundaries)");
        stateSnapshotText.setText(!details.isSelected()&&!cut.businessState().isBlank()?cut.businessState():cut.state()); stateSnapshotText.setCaretPosition(0);
        counterexampleTraceArea.setText(cut.trace()); counterexampleTraceArea.setCaretPosition(0);
        if(liveOnly){renderLive(cut);return;}
        for (Evaluation row : cut.evaluations()) if(details.isSelected()||row.required()||java.util.Set.of("FAIL","ERROR","VIOLATED").contains(row.status()))oclGoalModel.addRow(new Object[]{row.element(), row.raw(), row.status(), row.quality(), row.evidence(),row.certifiedHistory()});
        conformanceResultLabel.setText(cut.failed() ? "Observed violation" : "Observed prefix");
        riskResultLabel.setToolTipText(cut.trace());
        riskResultLabel.setText("<html>"+cut.requirements()+"<br>Overall sources: "+cut.coverage()+" / "+cut.freshness()+"</html>");
    }

    private JPanel buildLiveResults(){
        var goals=liveGoalTable;var checks=named(new JTable(liveChecks),"live-ocl-checks");
        goals.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);liveDetails.setEditable(false);liveDetails.setLineWrap(true);liveDetails.setWrapStyleWord(true);
        goals.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()){int row=goals.getSelectedRow();if(row>=0&&row<liveRows.size()){liveDetails.setText(liveRows.get(row).detail());liveDetails.setCaretPosition(0);}}});
        checks.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()){int row=checks.getSelectedRow();if(row>=0){liveDetails.setText(checks.getValueAt(row,0)+"\n"+checks.getValueAt(row,1)+" | "+checks.getValueAt(row,2)+"\n"+checks.getValueAt(row,3));liveDetails.setCaretPosition(0);}}});
        var upper=new JSplitPane(JSplitPane.VERTICAL_SPLIT,section("Goal & Task Status — model definitions",new JScrollPane(goals)),section("OCL / runtime binding checks",new JScrollPane(checks)));upper.setResizeWeight(.55);
        var right=new JSplitPane(JSplitPane.VERTICAL_SPLIT,upper,section("Selected evidence / instance details",new JScrollPane(liveDetails)));right.setResizeWeight(.7);
        stateSnapshotText.setEditable(false);var split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,section("Accepted CSL / USE state (read-only)",new JScrollPane(stateSnapshotText)),right);split.setResizeWeight(.4);
        var panel=new JPanel(new BorderLayout(5,5));
        var navigation=new JPanel(new BorderLayout());navigation.setBorder(BorderFactory.createTitledBorder("State navigation"));
        var buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,6,3));buttons.add(new JLabel("Go to State:"));buttons.add(stateSelectorCombo);buttons.add(statePrevButton);buttons.add(stateNextButton);buttons.add(jumpCheckpointButton);buttons.add(stateStepInfoLabel);
        var options=new JPanel(new FlowLayout(FlowLayout.LEFT,6,3));options.add(details);var interval=named(new JButton("Inspect milestone interval"),"observed-interval-details");options.add(interval);
        navigation.add(buttons,BorderLayout.CENTER);navigation.add(options,BorderLayout.SOUTH);
        statePrevButton.addActionListener(e->navigateStateStep(-1));stateNextButton.addActionListener(e->navigateStateStep(1));jumpCheckpointButton.addActionListener(e->openAuditedFailure());
        stateSelectorCombo.addActionListener(e->{if(!updatingStateComboInternal&&stateSelectorCombo.getSelectedIndex()>=0)updateSelectedCounterexampleState(stateSelectorCombo.getSelectedIndex());});
        details.addActionListener(e->{intervalIds=null;details.setText("Show all audit boundaries");setCuts(auditCuts);});interval.addActionListener(e->inspectInterval());
        conformanceResultLabel.setName("observed-verdict");conformanceResultLabel.setFont(new Font(Font.SANS_SERIF,Font.BOLD,16));riskResultLabel.setFont(new Font(Font.SANS_SERIF,Font.BOLD,14));
        var footer=new JPanel(new GridLayout(1,2,5,5));footer.setPreferredSize(new Dimension(800,95));footer.add(section("Verification result",conformanceResultLabel));footer.add(section("Completeness / freshness",riskResultLabel));
        panel.add(navigation,BorderLayout.NORTH);panel.add(split,BorderLayout.CENTER);panel.add(footer,BorderLayout.SOUTH);return panel;
    }
    private JPanel section(String title,Component content){var panel=new JPanel(new BorderLayout());panel.setBorder(BorderFactory.createTitledBorder(title));panel.add(content,BorderLayout.CENTER);return panel;}
    private void renderLive(Cut cut){
        if(liveSummary==null)return;
        liveRows=liveSummary.rows(cut==null?List.of():cut.evaluations());
        // The M1 row set/order never depends on callback count or runtime population.
        if(liveGoals.getRowCount()!=liveRows.size()){liveGoals.setRowCount(0);for(var row:liveRows)liveGoals.addRow(new Object[]{row.label(),row.originalStatus(),row.status()});}
        else for(int i=0;i<liveRows.size();i++){liveGoals.setValueAt(liveRows.get(i).originalStatus(),i,1);liveGoals.setValueAt(liveRows.get(i).status(),i,2);}
        liveChecks.setRowCount(0);
        if(cut==null){conformanceResultLabel.setText("NOT_EVALUATED");riskResultLabel.setText("UNAVAILABLE");stateSnapshotText.setText("");statePrevButton.setEnabled(false);stateNextButton.setEnabled(false);jumpCheckpointButton.setEnabled(false);return;}
        for(var row:cut.evaluations())if(!row.kind().equals("GOAL_TASK"))liveChecks.addRow(new Object[]{row.element(),row.status(),row.quality(),row.evidence()});
        stateSnapshotText.setText(!details.isSelected()&&!cut.businessState().isBlank()?cut.businessState():cut.state());stateSnapshotText.setCaretPosition(0);
        conformanceResultLabel.setText(verdict(cut));
        riskResultLabel.setToolTipText(cut.trace());
        riskResultLabel.setText("<html>"+cut.requirements().replace(" | Last completion delay:","<br>Last completion delay:")+"<br>Sources: "+cut.coverage()+" / "+cut.freshness()+"</html>");
        int selected=liveGoalTable.getSelectedRow();if(selected>=0&&selected<liveRows.size()){liveDetails.setText(liveRows.get(selected).detail());liveDetails.setCaretPosition(0);}
    }

    private String verdict(Cut cut){
        if(cut.failed())return "VIOLATION";
        if(cut.evaluations().isEmpty())return "NOT_EVALUATED";
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
