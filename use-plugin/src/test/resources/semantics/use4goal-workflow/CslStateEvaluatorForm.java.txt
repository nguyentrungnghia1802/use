package org.vnu.sme.goal.feature.cslstate;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import javax.swing.JComboBox;
import org.tzi.use.gui.main.MainWindow;
import org.vnu.sme.goal.dsl.aol.state.CslSystemStateCompiler;
import org.vnu.sme.goal.dsl.aol.view.AolView;
import org.vnu.sme.goal.verify.cslstate.CslStateEvaluationSession;
import org.vnu.sme.goal.verify.cslstate.CslStateEvaluationSession.ConstraintResult;
import org.vnu.sme.goal.verify.cslstate.CslStateEvaluationSession.StateResult;
import org.vnu.sme.goal.verify.cslstate.CslStateEvaluationSession.TruthValue;
import org.vnu.sme.goal.verify.cslstate.CslBpmnStateTraceEvaluator;
import org.vnu.sme.goal.verify.cslstate.CslBpmnStateTraceEvaluator.StepAlternative;
import org.vnu.sme.goal.verify.cslstate.CslBpmnStateTraceEvaluator.StepResult;
import org.vnu.sme.goal.verify.cslstate.CslBpmnStateTraceEvaluator.TraceResult;
import org.vnu.sme.goal.verify.cslstate.CslBpmnWholeProcessValidator;
import org.vnu.sme.goal.verify.cslstate.CslBpmnWholeProcessValidator.GoalEvidence;
import org.vnu.sme.goal.verify.cslstate.CslBpmnWholeProcessValidator.GoalStatus;
import org.vnu.sme.goal.verify.cslstate.CslBpmnWholeProcessValidator.MappingEntry;
import org.vnu.sme.goal.verify.cslstate.CslBpmnWholeProcessValidator.ProcessResult;
import org.vnu.sme.goal.verify.cslstate.CslBpmnWholeProcessValidator.ValidationResult;
import org.vnu.sme.goal.verify.cslstate.CslStateScenario;
import org.vnu.sme.goal.feature.cslstate.ConformanceReportBuilder.Row;
import org.vnu.sme.goal.gui.OfficialExamples;

/**
 * Validation-mode UI for CSL states. The CSL file defines the state space and
 * embeds its OCL invariants; each added AOL file contributes one complete state.
 */
@SuppressWarnings("serial")
public final class CslStateEvaluatorForm extends JDialog {
    private static final Preferences PREFS = Preferences.userNodeForPackage(CslStateEvaluatorForm.class);
    private static final String PREF_CSL = "cslStateEvaluator.cslFile";
    private static final String PREF_AOL_DIR = "cslStateEvaluator.aolDirectory";
    private static final String PREF_BPMN = "cslStateEvaluator.bpmnFile";
    private static final String PREF_ISTAR = "cslStateEvaluator.istarFile";
    private static final String PREF_BOUNDARY = "cslStateEvaluator.boundaryFile";
    private static final String PREF_SCENARIO_DIR = "cslStateEvaluator.scenarioDirectory";
    private static final Color GREEN = new Color(0, 125, 45);
    private static final Color RED = new Color(175, 35, 35);
    private static final Color AMBER = new Color(175, 115, 0);
    private static final Color GRAY = new Color(115, 115, 115);

    private final JTextField cslField = new JTextField(56);
    private final JTextField bpmnField = new JTextField(56);
    private final JTextField istarField = new JTextField(56);
    private final JTextField boundaryField = new JTextField(56);
    private final JButton addStateButton = new JButton("Add state snapshot(s)...");
    private final JButton loadBpmnButton = new JButton("Load BPMN");
    private final JButton loadIStarButton = new JButton("Load iStar");
    private final JButton loadBoundaryButton = new JButton("Load boundary");
    private final JButton loadScenarioButton = new JButton("Load state-only scenario...");
    private final JButton validateWholeButton = new JButton("Validate whole consistency");
    private final JButton previousButton = new JButton("Previous state");
    private final JButton nextButton = new JButton("Next state");
    private final JLabel summaryLabel = new JLabel("Load a CSL specification to begin.");
    private final JLabel statusLabel = new JLabel(" ");
    private final DefaultTableModel statesModel = readOnlyModel(
            "State", "Snapshot", "Objects", "Links", "Structure",
            "True", "False", "Undefined", "Error");
    private final DefaultTableModel constraintsModel = readOnlyModel(
            "Invariant", "Context", "Result", "OCL expression", "Detail");
    private final DefaultTableModel bpmnStepsModel = readOnlyModel(
            "Step", "State pair", "Process", "Self", "Flow", "Source", "Target",
            "Pass", "Next", "Result", "Alternatives");
    private final DefaultTableModel wholeProcessModel = readOnlyModel(
            "Process", "Self", "Consistency", "Risk", "Backend", "Loop bound", "Snapshots",
            "BPMN configs", "Transitions", "Executions", "Completed", "Conforming routes",
            "Non-conforming routes", "Risky routes", "Loop cutoffs", "State cutoffs",
            "Solver calls", "Detail");
    private final DefaultTableModel mappingModel = readOnlyModel(
            "Process", "BPMN activity", "Activity name", "Lane", "iStar actor",
            "Leaf kind", "Leaf element", "Score", "Mapping basis");
    private final DefaultTableModel reportModel = readOnlyModel(
            "Type", "Process", "BPMN element", "iStar goal/element", "Status",
            "Evidence / countermeasure");
    private final JTable statesTable = new JTable(statesModel);
    private final JTable constraintsTable = new JTable(constraintsModel);
    private final JTable bpmnStepsTable = new JTable(bpmnStepsModel);
    private final JTable wholeProcessTable = new JTable(wholeProcessModel);
    private final JTable mappingTable = new JTable(mappingModel);
    private final JTable reportTable = new JTable(reportModel);
    private final JTextArea cslOclSource = new JTextArea();
    private final JTextArea bpmnSource = new JTextArea();
    private final JTextArea istarSource = new JTextArea();
    private final JTextArea bpmnDetail = new JTextArea();
    private final JTextArea wholeProcessDetail = new JTextArea();

    private final JTextArea counterexampleTraceArea = new JTextArea(4, 50);
    private final JTextArea aolModelArea = new JTextArea(10, 30);
    private final JTextArea oclStatusArea = new JTextArea(10, 30);
    private final JLabel conformanceResultLabel = new JLabel("NOT_EVALUATED", SwingConstants.CENTER);
    private final JLabel riskResultLabel = new JLabel("NOT_EVALUATED", SwingConstants.CENTER);

    private final JComboBox<String> stateSelectorCombo = new JComboBox<>();
    private final JButton statePrevButton = new JButton("< Previous State");
    private final JButton stateNextButton = new JButton("Next State >");
    private final JButton jumpCheckpointButton = new JButton("Go to Failure Checkpoint");
    private final JLabel stateStepInfoLabel = new JLabel("State 0 / 0");

    private final AolView aolView;
    private final DefaultTableModel oclGoalModel = readOnlyModel(
            "Goal / Task", "Status");
    private final JTable oclGoalTable = new JTable(oclGoalModel);
    private final JTextArea repairHintArea = new JTextArea(3, 40);

    private final List<String> currentCounterexampleStates = new java.util.ArrayList<>();
    private final List<GoalEvidence> currentCounterexampleGoals = new java.util.ArrayList<>();
    private final List<String> currentRepairHints = new java.util.ArrayList<>();
    private final List<String> currentCounterexampleTrace = new java.util.ArrayList<>();
    private int currentFailureCheckpoint = -1;
    private String currentInvalidatingStep = "-";
    private int selectedStateIndex = 0;
    private boolean updatingStateComboInternal = false;

    private CslStateEvaluationSession evaluation;
    private TraceResult bpmnTrace;
    private ValidationResult wholeProcessValidation;

    public CslStateEvaluatorForm(MainWindow owner) {
        super(owner, "Goal–Process Conformance", false);
        this.aolView = AolView.embedded(owner);
        buildUi();
        cslField.setText(PREFS.get(PREF_CSL, ""));
        bpmnField.setText(PREFS.get(PREF_BPMN, ""));
        istarField.setText(PREFS.get(PREF_ISTAR, ""));
        boundaryField.setText(PREFS.get(PREF_BOUNDARY, ""));
        setSize(1280, 760);
        setMinimumSize(new Dimension(920, 580));
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(7, 7));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        root.add(buildInputs(), BorderLayout.NORTH);
        root.add(buildContent(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JPanel buildInputs() {
        JPanel inputs = new JPanel();
        inputs.setLayout(new BoxLayout(inputs, BoxLayout.Y_AXIS));
        JPanel cslRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel label = new JLabel("CSL state model:");
        label.setPreferredSize(new Dimension(125, label.getPreferredSize().height));
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> chooseCsl());
        cslRow.add(label);
        cslRow.add(cslField);
        cslRow.add(browse);

        JPanel bpmnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel bpmnLabel = new JLabel("BPMN process:");
        bpmnLabel.setPreferredSize(new Dimension(125, bpmnLabel.getPreferredSize().height));
        JButton browseBpmn = new JButton("Browse...");
        browseBpmn.addActionListener(e -> chooseBpmn());
        loadBpmnButton.setEnabled(false);
        loadBpmnButton.addActionListener(e -> loadBpmn());
        bpmnField.addActionListener(e -> loadBpmn());
        bpmnRow.add(bpmnLabel);
        bpmnRow.add(bpmnField);
        bpmnRow.add(browseBpmn);

        JPanel istarRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel istarLabel = new JLabel("iStar requirements:");
        istarLabel.setPreferredSize(new Dimension(125, istarLabel.getPreferredSize().height));
        JButton browseIStar = new JButton("Browse...");
        browseIStar.addActionListener(e -> chooseIStar());
        loadIStarButton.setEnabled(false);
        loadIStarButton.addActionListener(e -> loadIStar());
        istarField.addActionListener(e -> loadIStar());
        istarRow.add(istarLabel);
        istarRow.add(istarField);
        istarRow.add(browseIStar);

        JPanel boundaryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel boundaryLabel = new JLabel("Validation boundary:");
        boundaryLabel.setPreferredSize(new Dimension(125, boundaryLabel.getPreferredSize().height));
        JButton browseBoundary = new JButton("Browse...");
        browseBoundary.addActionListener(e -> chooseBoundary());
        loadBoundaryButton.setEnabled(false);
        loadBoundaryButton.addActionListener(e -> loadBoundary());
        boundaryField.addActionListener(e -> loadBoundary());
        boundaryRow.add(boundaryLabel);
        boundaryRow.add(boundaryField);
        boundaryRow.add(browseBoundary);

        JPanel stateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        validateWholeButton.setText("Verify conformance");
        validateWholeButton.setFont(validateWholeButton.getFont().deriveFont(Font.BOLD));
        validateWholeButton.setEnabled(true);
        validateWholeButton.setToolTipText(
                "Load CSL, BPMN, iStar and boundary, then produce one conformance report");
        validateWholeButton.addActionListener(e -> verifySelectedModels());
        stateRow.add(validateWholeButton);
        stateRow.add(new JLabel(
                "Consistent: mapping table. Otherwise: partial mappings and countermeasure."));

        inputs.add(cslRow);
        inputs.add(bpmnRow);
        inputs.add(istarRow);
        inputs.add(boundaryRow);
        inputs.add(stateRow);
        return inputs;
    }

    private JPanel buildConformanceAolPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // 1. Counterexample trace & State navigation bar
        JPanel topPanel = new JPanel(new BorderLayout(3, 3));
        topPanel.setBorder(BorderFactory.createTitledBorder("Counterexample Trace & State Navigation"));
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
        leftAolPanel.setBorder(BorderFactory.createTitledBorder("State Snapshot View"));
        leftAolPanel.add(aolView, BorderLayout.CENTER);
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
        leftConfPanel.setBorder(BorderFactory.createTitledBorder("Conformance Result"));
        conformanceResultLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        leftConfPanel.add(conformanceResultLabel, BorderLayout.CENTER);

        JPanel rightRiskPanel = new JPanel(new BorderLayout(3, 3));
        rightRiskPanel.setBorder(BorderFactory.createTitledBorder("Risk Assessment"));
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

    private Component buildContent() {
        configureTables();
        javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();

        tabs.addTab("Conformance & State View", buildConformanceAolPanel());

        reportTable.setAutoCreateRowSorter(true);
        reportTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {120, 130, 260, 260, 190, 680};
        for (int index = 0; index < widths.length; index++) {
            reportTable.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
        }
        reportTable.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());
        JPanel report = new JPanel(new BorderLayout(0, 4));
        report.setBorder(BorderFactory.createTitledBorder("Integrated conformance report"));
        report.add(new JScrollPane(reportTable), BorderLayout.CENTER);
        tabs.addTab("Integrated Report", report);

        JPanel mappingsPanel = new JPanel(new BorderLayout(0, 4));
        mappingsPanel.setBorder(BorderFactory.createTitledBorder("Inferred BPMN - iStar Mappings"));
        mappingsPanel.add(new JScrollPane(mappingTable), BorderLayout.CENTER);
        tabs.addTab("Goal-Process Mappings", mappingsPanel);

        JPanel wholePanel = new JPanel(new BorderLayout(0, 4));
        wholePanel.setBorder(BorderFactory.createTitledBorder("Process Execution Verification Details"));
        javax.swing.JSplitPane split = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(wholeProcessTable), new JScrollPane(wholeProcessDetail));
        split.setDividerLocation(160);
        wholePanel.add(split, BorderLayout.CENTER);
        tabs.addTab("Process Exploration Details", wholePanel);

        return tabs;
    }

    private void configureTables() {
        statesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        statesTable.setAutoCreateRowSorter(true);
        statesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelectedState();
        });
        statesTable.getColumnModel().getColumn(1).setPreferredWidth(360);
        statesTable.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());

        constraintsTable.setAutoCreateRowSorter(true);
        constraintsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        constraintsTable.getColumnModel().getColumn(0).setPreferredWidth(250);
        constraintsTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        constraintsTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        constraintsTable.getColumnModel().getColumn(3).setPreferredWidth(590);
        constraintsTable.getColumnModel().getColumn(4).setPreferredWidth(260);
        constraintsTable.getColumnModel().getColumn(2).setCellRenderer(new StatusRenderer());

        bpmnStepsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bpmnStepsTable.setAutoCreateRowSorter(true);
        bpmnStepsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {55, 190, 120, 90, 180, 140, 140, 220, 220, 120, 90};
        for (int i = 0; i < widths.length; i++) bpmnStepsTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        bpmnStepsTable.getColumnModel().getColumn(9).setCellRenderer(new StatusRenderer());
        bpmnStepsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelectedBpmnStep();
        });

        wholeProcessTable.setAutoCreateRowSorter(true);
        wholeProcessTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] wholeWidths = {140, 100, 130, 110, 130, 90, 90, 110, 100, 100, 100,
                120, 145, 100, 100, 100, 100, 520};
        for (int i = 0; i < wholeWidths.length; i++) {
            wholeProcessTable.getColumnModel().getColumn(i).setPreferredWidth(wholeWidths[i]);
        }
        wholeProcessTable.getColumnModel().getColumn(2).setCellRenderer(new StatusRenderer());
        wholeProcessTable.getColumnModel().getColumn(3).setCellRenderer(new StatusRenderer());
        wholeProcessTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelectedWholeProcess();
        });

        mappingTable.setAutoCreateRowSorter(true);
        mappingTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] mappingWidths = {120, 190, 220, 100, 110, 90, 210, 70, 330};
        for (int i = 0; i < mappingWidths.length; i++) {
            mappingTable.getColumnModel().getColumn(i).setPreferredWidth(mappingWidths[i]);
        }

        oclGoalTable.setAutoCreateRowSorter(true);
        oclGoalTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        oclGoalTable.getColumnModel().getColumn(0).setPreferredWidth(480);
        oclGoalTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        oclGoalTable.getColumnModel().getColumn(1).setCellRenderer(new StatusRenderer());
    }

    private void navigateStateStep(int delta) {
        if (currentCounterexampleStates.isEmpty()) return;
        int target = Math.max(0, Math.min(currentCounterexampleStates.size() - 1, selectedStateIndex + delta));
        updateSelectedCounterexampleState(target);
    }

    private void updateSelectedCounterexampleState(int stateIndex) {
        if (currentCounterexampleStates.isEmpty()) {
            aolView.setState(null, "");
            oclGoalModel.setRowCount(0);
            stateStepInfoLabel.setText("State 0 / 0");
            statePrevButton.setEnabled(false);
            stateNextButton.setEnabled(false);
            jumpCheckpointButton.setEnabled(false);
            return;
        }

        selectedStateIndex = Math.max(0, Math.min(currentCounterexampleStates.size() - 1, stateIndex));

        updatingStateComboInternal = true;
        try {
            if (stateSelectorCombo.getSelectedIndex() != selectedStateIndex) {
                stateSelectorCombo.setSelectedIndex(selectedStateIndex);
            }
        } finally {
            updatingStateComboInternal = false;
        }

        statePrevButton.setEnabled(selectedStateIndex > 0);
        stateNextButton.setEnabled(selectedStateIndex < currentCounterexampleStates.size() - 1);
        jumpCheckpointButton.setEnabled(currentFailureCheckpoint >= 0);

        boolean isCheckpoint = selectedStateIndex == currentFailureCheckpoint;
        String stepInfo = "State " + selectedStateIndex + " / " + (currentCounterexampleStates.size() - 1)
                + (isCheckpoint ? " [CHECKPOINT LỖI: " + currentInvalidatingStep + "]" : "");
        stateStepInfoLabel.setText(stepInfo);

        // Update AOL Snapshot & Graphical View for selected state
        String aolText = currentCounterexampleStates.get(selectedStateIndex);
        Path cslPath = cslField.getText().isBlank() ? null : Path.of(cslField.getText().trim());
        if (cslPath != null && java.nio.file.Files.exists(cslPath)) {
            try {
                var compiled = CslSystemStateCompiler.compileContent(
                        aolText, cslPath, evaluation.cslModel());
                if (compiled.valid()) {
                    aolView.setState(compiled.state(), aolText);
                } else {
                    System.err.println("[CslStateEvaluatorForm] AOL compilation diagnostics: " + compiled.diagnostics());
                    aolView.setState(null, aolText);
                }
            } catch (Exception e) {
                e.printStackTrace();
                aolView.setState(null, aolText);
            }
        } else {
            aolView.setState(null, aolText);
        }

        // Update OCL & iStar Goal/Task Table for selected state (2 columns: Goal/Task, Status)
        oclGoalModel.setRowCount(0);
        for (GoalEvidence goal : currentCounterexampleGoals) {
            String status = goal.status().toString();
            if (selectedStateIndex < currentFailureCheckpoint && goal.status() == GoalStatus.VIOLATED) {
                status = "UNKNOWN";
            }
            oclGoalModel.addRow(new Object[] {
                    goal.goal(),
                    status
            });
        }
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(4, 3));
        summaryLabel.setHorizontalAlignment(SwingConstants.CENTER);
        footer.add(summaryLabel, BorderLayout.NORTH);
        footer.add(statusLabel, BorderLayout.SOUTH);
        return footer;
    }

    private void chooseCsl() {
        JFileChooser chooser = OfficialExamples.chooser(cslField.getText().trim());
        chooser.setFileFilter(new FileNameExtensionFilter("CSL state models (*.csl)", "csl"));
        if (!cslField.getText().isBlank()) chooser.setSelectedFile(new File(cslField.getText().trim()));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            cslField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseBpmn() {
        JFileChooser chooser = OfficialExamples.chooser(bpmnField.getText().trim());
        chooser.setFileFilter(new FileNameExtensionFilter("BPMN process specifications (*.bpmn2, *.bpmn)",
                "bpmn2", "bpmn"));
        if (!bpmnField.getText().isBlank()) chooser.setSelectedFile(new File(bpmnField.getText().trim()));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            bpmnField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseIStar() {
        JFileChooser chooser = OfficialExamples.chooser(istarField.getText().trim());
        chooser.setFileFilter(new FileNameExtensionFilter("iStar requirement models (*.istar)", "istar"));
        if (!istarField.getText().isBlank()) {
            chooser.setSelectedFile(new File(istarField.getText().trim()));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            istarField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseBoundary() {
        JFileChooser chooser = OfficialExamples.chooser(boundaryField.getText().trim());
        chooser.setFileFilter(new FileNameExtensionFilter(
                "CSL/BPMN boundaries (*.cslboundary)", "cslboundary"));
        if (!boundaryField.getText().isBlank()) {
            chooser.setSelectedFile(new File(boundaryField.getText().trim()));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            boundaryField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadCsl() {
        String input = cslField.getText().trim();
        if (input.isEmpty()) {
            status("Select a CSL state model first.", RED);
            return;
        }
        try {
            CslStateEvaluationSession loaded = CslStateEvaluationSession.load(Path.of(input));
            evaluation = loaded;
            statesModel.setRowCount(0);
            constraintsModel.setRowCount(0);
            cslOclSource.setText(loaded.cslSource());
            cslOclSource.setCaretPosition(0);
            addStateButton.setEnabled(true);
            loadBpmnButton.setEnabled(true);
            loadIStarButton.setEnabled(true);
            loadBoundaryButton.setEnabled(true);
            validateWholeButton.setEnabled(false);
            bpmnStepsModel.setRowCount(0);
            wholeProcessModel.setRowCount(0);
            bpmnSource.setText("");
            istarSource.setText("");
            bpmnDetail.setText("Load a BPMN process, then add at least two ordered AOL states.");
            wholeProcessDetail.setText("Load BPMN and a .cslboundary file for whole validation.");
            bpmnTrace = null;
            wholeProcessValidation = null;
            PREFS.put(PREF_CSL, loaded.cslFile().toString());
            summaryLabel.setText("No AOL states added yet.");
            updateNavigation();
            status("Loaded " + loaded.cslFile().getFileName() + ": "
                    + loaded.cslModel().invariants().size() + " native CSL/OCL invariant(s).", GREEN);
        } catch (Exception ex) {
            evaluation = null;
            addStateButton.setEnabled(false);
            loadBpmnButton.setEnabled(false);
            loadIStarButton.setEnabled(false);
            loadBoundaryButton.setEnabled(false);
            validateWholeButton.setEnabled(false);
            cslOclSource.setText("");
            showError("Cannot load CSL", ex);
            status("CSL load failed.", RED);
        }
    }

    private void loadIStar() {
        if (evaluation == null) {
            status("Load a CSL state model before loading iStar.", RED);
            return;
        }
        String input = istarField.getText().trim();
        if (input.isEmpty()) {
            status("Select an iStar requirement model first.", RED);
            return;
        }
        try {
            var loaded = evaluation.loadIStar(Path.of(input));
            istarSource.setText(evaluation.istarSource());
            istarSource.setCaretPosition(0);
            istarField.setText(evaluation.istarFile().toString());
            PREFS.put(PREF_ISTAR, evaluation.istarFile().toString());
            validateWholeButton.setEnabled(evaluation.bpmnEvaluator() != null
                    && evaluation.boundary() != null);
            resetWholeProcessPrompt();
            long goals = loaded.allElements().values().stream()
                    .filter(org.vnu.sme.goal.dsl.istar.mm.Goal.class::isInstance).count();
            status("Loaded " + evaluation.istarFile().getFileName() + ": " + goals
                    + " goal(s). Non-leaf goal conditions are rejected by the compiler.", GREEN);
        } catch (Exception ex) {
            istarSource.setText("");
            validateWholeButton.setEnabled(false);
            showError("Cannot load iStar", ex);
            status("iStar load failed.", RED);
        }
    }

    private void loadBpmn() {
        if (evaluation == null) {
            status("Load a CSL state model before loading BPMN.", RED);
            return;
        }
        String input = bpmnField.getText().trim();
        if (input.isEmpty()) {
            status("Select a BPMN process specification first.", RED);
            return;
        }
        try {
            CslBpmnStateTraceEvaluator loaded = evaluation.loadBpmn(Path.of(input));
            bpmnSource.setText(loaded.bpmnSource());
            bpmnSource.setCaretPosition(0);
            PREFS.put(PREF_BPMN, loaded.bpmnFile().toString());
            validateWholeButton.setEnabled(evaluation.boundary() != null
                    && evaluation.goalModel() != null);
            refreshBpmnTrace();
            resetWholeProcessPrompt();
            status("Loaded " + loaded.bpmnFile().getFileName() + ": "
                    + loaded.bpmnModel().processes().size() + " process(es), "
                    + loaded.bpmnModel().flowElementCount() + " flow element(s).", GREEN);
        } catch (Exception ex) {
            bpmnSource.setText("");
            bpmnStepsModel.setRowCount(0);
            wholeProcessModel.setRowCount(0);
            bpmnTrace = null;
            wholeProcessValidation = null;
            validateWholeButton.setEnabled(false);
            showError("Cannot load BPMN", ex);
            status("BPMN load failed.", RED);
        }
    }

    private void loadBoundary() {
        if (evaluation == null) {
            status("Load a CSL state model before loading its boundary.", RED);
            return;
        }
        String input = boundaryField.getText().trim();
        if (input.isEmpty()) {
            status("Select a .cslboundary file first.", RED);
            return;
        }
        try {
            var loaded = evaluation.loadBoundary(Path.of(input));
            boundaryField.setText(loaded.file().toString());
            PREFS.put(PREF_BOUNDARY, loaded.file().toString());
            validateWholeButton.setEnabled(evaluation.bpmnEvaluator() != null
                    && evaluation.goalModel() != null);
            resetWholeProcessPrompt();
            status("Loaded boundary " + loaded.file().getFileName() + ": snapshots="
                    + loaded.snapshots() + ", loop-bound=" + loaded.loopBound()
                    + ", classifier scopes=" + loaded.objectScopes().size() + ".", GREEN);
        } catch (Exception ex) {
            validateWholeButton.setEnabled(false);
            showError("Cannot load validation boundary", ex);
            status("Boundary load failed.", RED);
        }
    }

    private void loadScenario() {
        String fallbackDirectory = ".";
        if (!cslField.getText().isBlank()) {
            File parent = new File(cslField.getText().trim()).getAbsoluteFile().getParentFile();
            if (parent != null) fallbackDirectory = parent.getAbsolutePath();
        }
        JFileChooser chooser = new JFileChooser(PREFS.get(PREF_SCENARIO_DIR, fallbackDirectory));
        chooser.setFileFilter(new FileNameExtensionFilter(
                "CSL state-only scenarios (*.cslscenario)", "cslscenario"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File selected = chooser.getSelectedFile();
        if (selected.getParentFile() != null) {
            PREFS.put(PREF_SCENARIO_DIR, selected.getParentFile().getAbsolutePath());
        }
        try {
            CslStateScenario scenario = CslStateScenario.load(selected.toPath());
            CslStateEvaluationSession loaded = CslStateEvaluationSession.load(scenario.cslFile());
            loaded.loadBpmn(scenario.bpmnFile());
            for (Path state : scenario.stateFiles()) loaded.addState(state);

            evaluation = loaded;
            cslField.setText(loaded.cslFile().toString());
            bpmnField.setText(loaded.bpmnEvaluator().bpmnFile().toString());
            cslOclSource.setText(loaded.cslSource());
            cslOclSource.setCaretPosition(0);
            bpmnSource.setText(loaded.bpmnEvaluator().bpmnSource());
            bpmnSource.setCaretPosition(0);
            addStateButton.setEnabled(true);
            loadBpmnButton.setEnabled(true);
            loadIStarButton.setEnabled(true);
            loadBoundaryButton.setEnabled(true);
            validateWholeButton.setEnabled(false);
            statesModel.setRowCount(0);
            constraintsModel.setRowCount(0);
            for (StateResult state : loaded.states()) appendState(state);
            if (!loaded.states().isEmpty()) statesTable.setRowSelectionInterval(0, 0);
            PREFS.put(PREF_CSL, loaded.cslFile().toString());
            PREFS.put(PREF_BPMN, loaded.bpmnEvaluator().bpmnFile().toString());
            refreshBpmnTrace();
            resetWholeProcessPrompt();
            status("Loaded state-only scenario " + scenario.name() + " with "
                    + scenario.stateFiles().size() + " CSL snapshots. " + bpmnTrace.summary(),
                    colorFor(bpmnTrace.verdict()));
        } catch (Exception ex) {
            showError("Cannot load state-only scenario", ex);
            status("Scenario load failed.", RED);
        }
    }

    private void addStates() {
        if (evaluation == null) return;
        JFileChooser chooser = new JFileChooser(PREFS.get(PREF_AOL_DIR,
                evaluation.cslFile().getParent().toString()));
        chooser.setFileFilter(new FileNameExtensionFilter("Formal AOL v2 state snapshots (*.aol)", "aol"));
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File[] selected = chooser.getSelectedFiles();
        if (selected.length == 0 && chooser.getSelectedFile() != null) {
            selected = new File[] { chooser.getSelectedFile() };
        }
        if (selected.length == 0) return;
        Arrays.sort(selected, Comparator.comparing(File::getName));
        File parent = selected[0].getParentFile();
        if (parent != null) PREFS.put(PREF_AOL_DIR, parent.getAbsolutePath());

        int added = 0;
        for (File file : selected) {
            try {
                StateResult result = evaluation.addState(file.toPath());
                appendState(result);
                added++;
            } catch (Exception ex) {
                showError("Cannot add state snapshot " + file.getName(), ex);
                break;
            }
        }
        if (added > 0) {
            int row = statesModel.getRowCount() - 1;
            statesTable.setRowSelectionInterval(row, row);
            status("Added and evaluated " + added + " state snapshot(s).", GREEN);
            refreshBpmnTrace();
            resetWholeProcessPrompt();
        }
        updateNavigation();
    }

    private void appendState(StateResult state) {
        statesModel.addRow(new Object[] {
                "s" + state.index(), state.aolFile().getFileName().toString(),
                state.objectCount(), state.linkCount(), state.structureValid() ? "VALID" : "INVALID",
                state.trueCount(), state.falseCount(), state.undefinedCount(), state.errorCount()
        });
    }

    private void showSelectedState() {
        constraintsModel.setRowCount(0);
        if (evaluation == null) {
            updateNavigation();
            return;
        }
        int viewRow = statesTable.getSelectedRow();
        if (viewRow < 0) {
            updateNavigation();
            return;
        }
        int modelRow = statesTable.convertRowIndexToModel(viewRow);
        List<StateResult> states = evaluation.states();
        if (modelRow >= states.size()) return;
        StateResult state = states.get(modelRow);
        for (ConstraintResult constraint : state.constraints()) {
            constraintsModel.addRow(new Object[] {
                    constraint.name(), constraint.context(), constraint.result(),
                    constraint.expression(), constraint.detail()
            });
        }
        summaryLabel.setText("s" + state.index() + " — OCL: " + state.trueCount() + " true, "
                + state.falseCount() + " false, " + state.undefinedCount() + " undefined, "
                + state.errorCount() + " error; structure: "
                + (state.structureValid() ? "valid" : "invalid")
                + (state.inputDiagnostics().isEmpty() ? "" : "; AOL diagnostics: " + state.inputDiagnostics().size()));
        status(state.structureValid() && state.falseCount() == 0
                        && state.undefinedCount() == 0 && state.errorCount() == 0
                        ? "Selected state satisfies its structure and every embedded OCL invariant."
                        : !state.inputDiagnostics().isEmpty()
                                ? String.join("\n", state.inputDiagnostics())
                                : state.structureReport().isBlank()
                                ? "Selected state contains failed or undefined OCL results."
                                : state.structureReport(),
                state.structureValid() && state.falseCount() == 0
                        && state.undefinedCount() == 0 && state.errorCount() == 0 ? GREEN : RED);
        updateNavigation();
    }

    private void refreshBpmnTrace() {
        bpmnStepsModel.setRowCount(0);
        bpmnDetail.setText("");
        bpmnTrace = null;
        if (evaluation == null || evaluation.bpmnEvaluator() == null) {
            bpmnDetail.setText("Load a BPMN process to infer the execution route.");
            return;
        }
        if (evaluation.states().size() < 2) {
            bpmnDetail.setText("Add at least two ordered AOL snapshots. Each adjacent pair denotes one formal flow execution.");
            return;
        }
        bpmnTrace = evaluation.evaluateBpmnTrace();
        for (StepResult step : bpmnTrace.steps()) {
            StepAlternative first = step.alternatives().isEmpty() ? null : step.alternatives().get(0);
            bpmnStepsModel.addRow(new Object[] {
                    step.index() + 1,
                    step.beforeFile().getFileName() + " -> " + step.afterFile().getFileName(),
                    first == null ? "-" : first.processId(),
                    first == null ? "-" : first.selfObject(),
                    first == null ? "-" : first.flowId(),
                    first == null ? "-" : first.sourceId(),
                    first == null ? "-" : first.targetId(),
                    first == null ? "-" : first.pass(),
                    first == null ? "-" : first.next(),
                    step.verdict(),
                    step.alternatives().size()
            });
        }
        if (bpmnStepsModel.getRowCount() > 0) {
            bpmnStepsTable.setRowSelectionInterval(0, 0);
        } else {
            bpmnDetail.setText(bpmnTrace.summary());
        }
    }

    private void showSelectedBpmnStep() {
        if (bpmnTrace == null) return;
        int viewRow = bpmnStepsTable.getSelectedRow();
        if (viewRow < 0) return;
        int row = bpmnStepsTable.convertRowIndexToModel(viewRow);
        if (row >= bpmnTrace.steps().size()) return;
        StepResult step = bpmnTrace.steps().get(row);
        StringBuilder text = new StringBuilder();
        text.append("s").append(step.index()).append(" -> s").append(step.index() + 1)
                .append(": ").append(step.verdict()).append('\n')
                .append(step.detail()).append("\n\n");
        for (int i = 0; i < step.alternatives().size(); i++) {
            StepAlternative alternative = step.alternatives().get(i);
            text.append(i + 1).append(". process=").append(alternative.processId())
                    .append(", self=").append(alternative.selfObject())
                    .append(", flow=").append(alternative.flowId()).append('\n')
                    .append("   route: ").append(alternative.route()).append('\n')
                    .append("   Pass: ").append(alternative.pass()).append('\n')
                    .append("   Next: ").append(alternative.next()).append('\n')
                    .append("   contract: ").append(alternative.contract()).append('\n')
                    .append("   next activations: ").append(alternative.resultingActivations()).append("\n");
        }
        text.append("\nTrace: ").append(bpmnTrace.summary());
        bpmnDetail.setText(text.toString());
        bpmnDetail.setCaretPosition(0);
    }

    private void verifySelectedModels() {
        reportModel.setRowCount(0);
        List<String> missing = new java.util.ArrayList<>();
        if (cslField.getText().isBlank()) missing.add("CSL");
        if (bpmnField.getText().isBlank()) missing.add("BPMN");
        if (istarField.getText().isBlank()) missing.add("iStar");
        if (boundaryField.getText().isBlank()) missing.add("boundary");
        if (!missing.isEmpty()) {
            String detail = "Select the required input(s): " + String.join(", ", missing) + ".";
            reportModel.addRow(new Object[] {"DIAGNOSTIC", "-", "-", "-",
                    "INPUT_REQUIRED", detail});
            status(detail, RED);
            return;
        }
        validateWholeButton.setEnabled(false);
        try {
            loadCsl();
            if (evaluation == null) return;
            loadBpmn();
            if (evaluation.bpmnEvaluator() == null) return;
            loadIStar();
            if (evaluation.goalModel() == null) return;
            loadBoundary();
            if (evaluation.boundary() == null) return;
            refreshWholeProcessValidation();
        } finally {
            validateWholeButton.setEnabled(true);
        }
    }

    private void refreshWholeProcessValidation() {
        reportModel.setRowCount(0);
        wholeProcessModel.setRowCount(0);
        mappingModel.setRowCount(0);
        wholeProcessDetail.setText("");
        wholeProcessValidation = null;
        if (evaluation == null || evaluation.bpmnEvaluator() == null) {
            wholeProcessDetail.setText("Load a CSL model and BPMN process first.");
            return;
        }
        if (evaluation.boundary() == null) {
            wholeProcessDetail.setText("Load a .cslboundary file. Whole validation does not use manual snapshots.");
            return;
        }
        if (evaluation.goalModel() == null) {
            wholeProcessDetail.setText("Load an iStar model. Its markings will be derived from generated CSL states.");
            return;
        }
        wholeProcessValidation = evaluation.validateWholeBpmnProcess();
        for (ProcessResult process : wholeProcessValidation.processes()) {
            wholeProcessModel.addRow(new Object[] {
                    process.processId(), process.selfObject(), wholeProcessValidation.consistency(),
                    wholeProcessValidation.risk(), wholeProcessValidation.backend(),
                    wholeProcessValidation.loopBound(), wholeProcessValidation.snapshots(),
                    process.productStates(), process.transitions(), process.boundedExecutions(),
                    process.completedStates(), process.goalAchievingExecutions(),
                    process.nonGoalAchievingExecutions(), process.riskyExecutions(),
                    process.loopCutoffs(), process.snapshotCutoffs(), process.solverCalls(),
                    process.detail()
            });
        }
        for (MappingEntry mapping : wholeProcessValidation.mappings()) {
            mappingModel.addRow(new Object[] {
                    mapping.processId(), mapping.activityId(), mapping.activityName(), mapping.lane(),
                    mapping.actor(), mapping.leafKind(), mapping.leafId(), mapping.score(), mapping.basis()
            });
        }
        for (Row row : ConformanceReportBuilder.build(wholeProcessValidation)) {
            reportModel.addRow(new Object[] {row.type(), row.process(), row.bpmnElement(),
                    row.iStarElement(), row.status(), row.evidenceOrAction()});
        }

        currentCounterexampleStates.clear();
        currentCounterexampleGoals.clear();
        currentRepairHints.clear();
        currentCounterexampleTrace.clear();
        currentFailureCheckpoint = -1;
        currentInvalidatingStep = "-";

        if (!wholeProcessValidation.processes().isEmpty()) {
            ProcessResult process = wholeProcessValidation.processes().get(0);
            boolean isInconclusive = wholeProcessValidation.consistency() == CslBpmnWholeProcessValidator.ConsistencyVerdict.INCONCLUSIVE
                    || process.verdict() == CslBpmnWholeProcessValidator.Verdict.INCONCLUSIVE;

            if (!process.counterexample().isEmpty()) {
                currentCounterexampleTrace.addAll(process.counterexample());
                counterexampleTraceArea.setText(formatInlineTrace(process.counterexample()));
            } else if (isInconclusive) {
                counterexampleTraceArea.setText("Verification inconclusive (INCONCLUSIVE):\n" + process.detail());
            } else {
                counterexampleTraceArea.setText("No counterexample found (all BPMN paths satisfy iStar goals and OCL invariants).");
            }

            currentCounterexampleStates.addAll(process.counterexampleStates());
            currentCounterexampleGoals.addAll(process.counterexampleGoals());
            currentRepairHints.addAll(process.repairHints());
            currentFailureCheckpoint = process.failureCheckpoint();
            currentInvalidatingStep = process.invalidatingStep();

            updatingStateComboInternal = true;
            try {
                stateSelectorCombo.removeAllItems();
                if (!currentCounterexampleStates.isEmpty()) {
                    for (int i = 0; i < currentCounterexampleStates.size(); i++) {
                        String label = "State " + i;
                        if (i == 0) label += " (Initial)";
                        else if (i - 1 < currentCounterexampleTrace.size()) {
                            label += " (After: " + currentCounterexampleTrace.get(i - 1) + ")";
                        }
                        if (i == currentFailureCheckpoint) {
                            label += " [FAILURE CHECKPOINT]";
                        }
                        stateSelectorCombo.addItem(label);
                    }
                }
            } finally {
                updatingStateComboInternal = false;
            }

            int initialIndex = currentFailureCheckpoint >= 0 && currentFailureCheckpoint < currentCounterexampleStates.size()
                    ? currentFailureCheckpoint : 0;
            updateSelectedCounterexampleState(initialIndex);

            conformanceResultLabel.setText(wholeProcessValidation.consistency().toString());
            conformanceResultLabel.setForeground(
                wholeProcessValidation.consistency() == CslBpmnWholeProcessValidator.ConsistencyVerdict.CONSISTENT ? GREEN :
                wholeProcessValidation.consistency() == CslBpmnWholeProcessValidator.ConsistencyVerdict.WEAK_CONFORMANCE ? AMBER : RED
            );

            riskResultLabel.setText(wholeProcessValidation.risk().toString());
            riskResultLabel.setForeground(
                wholeProcessValidation.risk() == CslBpmnWholeProcessValidator.RiskVerdict.NON_RISKY ? GREEN : RED
            );
        }

        summaryLabel.setText(wholeProcessValidation.consistency() + " / "
                + wholeProcessValidation.risk());
        if (wholeProcessModel.getRowCount() > 0) {
            wholeProcessTable.setRowSelectionInterval(0, 0);
        } else {
            wholeProcessDetail.setText(wholeProcessValidation.summary());
        }
        status(wholeProcessValidation.summary(), colorFor(wholeProcessValidation.verdict()));
    }

    private static String formatInlineTrace(List<String> rawTrace) {
        if (rawTrace == null || rawTrace.isEmpty()) return "";
        List<String> cleanNodes = new ArrayList<>();
        for (String step : rawTrace) {
            String[] parts = step.split("\\s*->\\s*");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && (cleanNodes.isEmpty() || !cleanNodes.get(cleanNodes.size() - 1).equals(trimmed))) {
                    cleanNodes.add(trimmed);
                }
            }
        }
        return String.join(" -> ", cleanNodes);
    }

    private void resetWholeProcessPrompt() {
        wholeProcessModel.setRowCount(0);
        mappingModel.setRowCount(0);
        wholeProcessValidation = null;
        wholeProcessDetail.setText("Load CSL + BPMN + iStar + .cslboundary, then press 'Verify conformance'. "
                + "Kodkod generates CSL state paths; iStar has no separate state space.");
    }

    private void showSelectedWholeProcess() {
        if (wholeProcessValidation == null) return;
        int viewRow = wholeProcessTable.getSelectedRow();
        if (viewRow < 0) return;
        int row = wholeProcessTable.convertRowIndexToModel(viewRow);
        if (row >= wholeProcessValidation.processes().size()) return;
        ProcessResult process = wholeProcessValidation.processes().get(row);
        StringBuilder text = new StringBuilder();
        text.append("process=").append(process.processId())
                .append(", self=").append(process.selfObject())
                .append(", result=").append(process.verdict())
                .append(", consistency=").append(wholeProcessValidation.consistency())
                .append(", risk=").append(wholeProcessValidation.risk())
                .append(", backend=").append(wholeProcessValidation.backend())
                .append(", loop bound=").append(wholeProcessValidation.loopBound())
                .append(", snapshots=").append(wholeProcessValidation.snapshots())
                .append("\nboundary=").append(wholeProcessValidation.boundaryFile()).append("\n\n")
                .append(process.detail()).append("\n\n")
                .append("Explored BPMN configurations: ").append(process.productStates()).append('\n')
                .append("Explored formal transitions: ").append(process.transitions()).append('\n')
                .append("Maximal bounded executions: ").append(process.boundedExecutions()).append('\n')
                .append("Fully consumed End executions: ").append(process.completedStates()).append('\n')
                .append("Loop-bound cutoffs: ").append(process.loopCutoffs()).append('\n')
                .append("Snapshot-bound cutoffs: ").append(process.snapshotCutoffs()).append('\n')
                .append("Kodkod solver calls: ").append(process.solverCalls()).append('\n');
        text.append("Realizable BPMN executions: ").append(process.realizableExecutions()).append('\n')
                .append("Routes admitting a conforming state path: ")
                .append(process.goalAchievingExecutions()).append('\n')
                .append("Routes admitting a non-conforming state path: ")
                .append(process.nonGoalAchievingExecutions()).append('\n')
                .append("Routes admitting a risky state path: ")
                .append(process.riskyExecutions()).append('\n')
                .append("Root iStar goals: ").append(wholeProcessValidation.rootGoals()).append('\n');
        if (!process.counterexample().isEmpty()) {
            text.append("\nCounterexample execution:\n  ")
                    .append(String.join("\n  -> ", process.counterexample())).append('\n');
        }
        if (!process.counterexampleGoals().isEmpty()) {
            text.append("\nGoal marking on the counterexample:\n");
            process.counterexampleGoals().forEach(goal -> text.append("  ")
                    .append(goal.root() ? "[root] " : "[leaf] ")
                    .append(goal.goal()).append(" = ").append(goal.status())
                    .append("\n    condition: ").append(goal.condition()).append('\n'));
        }
        if (!process.counterexampleStates().isEmpty()) {
            text.append("\nCounterexample CSL state path:\n  ")
                    .append(String.join("\n  -> ", process.counterexampleStates())).append('\n');
        }
        if (!process.repairHints().isEmpty()) {
            text.append("\nCountermeasure candidates (repair obligations, not proven repairs):\n");
            process.repairHints().forEach(hint -> text.append("  - ").append(hint).append('\n'));
        }
        if (!process.witnessStates().isEmpty()) {
            text.append("\nExample SAT witness (state_at_i):\n  ")
                    .append(String.join("\n  -> ", process.witnessStates())).append('\n');
        }
        text.append("\n").append(wholeProcessValidation.summary())
                .append("\n\nThe displayed CSL states were generated by Kodkod from the boundary;")
                .append(" iStar markings were evaluated over that path.")
                .append("\nThe mapping tab is explanatory inference; the verdict is obtained from OCL formulas.");
        wholeProcessDetail.setText(text.toString());
        wholeProcessDetail.setCaretPosition(0);
    }

    private void moveSelection(int delta) {
        int count = statesTable.getRowCount();
        if (count == 0) return;
        int row = statesTable.getSelectedRow();
        int next = Math.max(0, Math.min(count - 1, row < 0 ? 0 : row + delta));
        statesTable.setRowSelectionInterval(next, next);
        statesTable.scrollRectToVisible(statesTable.getCellRect(next, 0, true));
    }

    private void updateNavigation() {
        int row = statesTable.getSelectedRow();
        previousButton.setEnabled(row > 0);
        nextButton.setEnabled(row >= 0 && row + 1 < statesTable.getRowCount());
    }

    private void showError(String title, Exception ex) {
        JTextArea text = new JTextArea(message(ex));
        text.setEditable(false);
        text.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(850, 360));
        JOptionPane.showMessageDialog(this, scroll, title, JOptionPane.ERROR_MESSAGE);
    }

    private void status(String text, Color color) {
        statusLabel.setText("<html>" + escape(text).replace("\n", "<br>") + "</html>");
        statusLabel.setForeground(color);
    }

    private static Color colorFor(CslBpmnStateTraceEvaluator.Verdict verdict) {
        return switch (verdict) {
            case CONFORMANT -> GREEN;
            case AMBIGUOUS -> AMBER;
            case NON_CONFORMANT -> RED;
        };
    }

    private static Color colorFor(CslBpmnWholeProcessValidator.Verdict verdict) {
        return switch (verdict) {
            case VALID -> GREEN;
            case INCONCLUSIVE -> AMBER;
            case INVALID -> RED;
        };
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private static String message(Throwable throwable) {
        String value = throwable.getMessage();
        return value == null || value.isBlank() ? throwable.toString() : value;
    }

    private static String escape(String text) {
        return String.valueOf(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class StatusRenderer extends DefaultTableCellRenderer {
        StatusRenderer() { setHorizontalAlignment(SwingConstants.CENTER); }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                        boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            if (!selected) {
                String status = String.valueOf(value);
                Color color = status.contains("INVALIDATING") || status.contains("INCONSISTENT")
                        || status.equals("RISKY") || status.equals("FALSE")
                        || status.equals("INVALID") || status.equals("ERROR") || status.equals("VIOLATED") ? RED
                        : status.equals("WEAK_CONFORMANCE") || status.contains("INCONCLUSIVE")
                                || status.contains("CANDIDATE") || status.contains("UNMAPPED")
                                || status.equals("UNDEFINED") || status.equals("AMBIGUOUS") || status.equals("UNKNOWN") ? AMBER
                        : status.contains("MAPPED") || status.contains("CONSISTENT")
                                || status.equals("NON_RISKY") || status.equals("TRUE")
                                || status.equals("VALID") || status.equals("CONFORMANT") || status.equals("SATISFIED") ? GREEN
                        : GRAY;
                setForeground(color);
                setFont(getFont().deriveFont(Font.BOLD));
            }
            return this;
        }
    }
}
