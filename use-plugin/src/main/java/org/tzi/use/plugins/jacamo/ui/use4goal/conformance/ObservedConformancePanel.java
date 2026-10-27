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
    public record Evaluation(String element, String raw, String status, String quality, String evidence) { }
    public record Cut(String id, String trace, String state, List<Evaluation> evaluations,
                      String coverage, String freshness, boolean failed) {
        public Cut { evaluations = List.copyOf(evaluations); }
    }
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
            new String[]{"Goal / Task / OCL", "Raw", "Qualified / propagated", "Quality", "Evidence"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable oclGoalTable = named(new JTable(oclGoalModel), "observed-goals");
    private List<Cut> currentCounterexampleStates = List.of();
    private int selectedStateIndex = -1;
    private int currentFailureCheckpoint = -1;
    private boolean updatingStateComboInternal;

    public ObservedConformancePanel() {
        super(new BorderLayout());
        setName("use4goal-observed-conformance");
        stateSnapshotText.setEditable(false);
        stateSnapshotText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        stateSelectorCombo.setPrototypeDisplayValue("State 000000");
        stateSelectorCombo.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                super.getListCellRendererComponent(list,value,index,selected,focus);
                int ordinal=index<0?selectedStateIndex:index;
                setText(ordinal<0?"Select state":"State "+(ordinal+1));setToolTipText(value==null?null:value.toString());return this;
            }
        });
        add(buildConformancePanel(), BorderLayout.CENTER);
        statePrevButton.setEnabled(false);
        stateNextButton.setEnabled(false);
        jumpCheckpointButton.setEnabled(false);
    }

    public Cut selectedCut() {
        return selectedStateIndex < 0 ? null : currentCounterexampleStates.get(selectedStateIndex);
    }

    public void setCuts(List<Cut> cuts) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("OBSERVED_VIEW_REQUIRES_EDT");
        if (currentCounterexampleStates.equals(cuts)) return;
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
            return;
        }
        selectedStateIndex = Math.max(0, Math.min(currentCounterexampleStates.size() - 1, stateIndex));
        updatingStateComboInternal = true;
        try { stateSelectorCombo.setSelectedIndex(selectedStateIndex); }
        finally { updatingStateComboInternal = false; }
        Cut cut = selectedCut();
        statePrevButton.setEnabled(selectedStateIndex > 0);
        stateNextButton.setEnabled(selectedStateIndex < currentCounterexampleStates.size() - 1);
        jumpCheckpointButton.setEnabled(currentFailureCheckpoint >= 0);
        stateStepInfoLabel.setText("State " + (selectedStateIndex + 1) + " / " + currentCounterexampleStates.size());
        stateSnapshotText.setText(cut.state()); stateSnapshotText.setCaretPosition(0);
        counterexampleTraceArea.setText(cut.trace()); counterexampleTraceArea.setCaretPosition(0);
        for (Evaluation row : cut.evaluations()) oclGoalModel.addRow(new Object[]{row.element(), row.raw(), row.status(), row.quality(), row.evidence()});
        conformanceResultLabel.setText(cut.failed() ? "Observed violation" : "Observed prefix");
        riskResultLabel.setText(cut.coverage() + " / " + cut.freshness());
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
