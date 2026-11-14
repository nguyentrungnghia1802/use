package org.tzi.use.plugins.jacamo.bridge;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Small CLI viewer; lifecycle and evaluation live in StateVerificationSession. */
final class StandaloneStateView implements StateVerificationSession.Listener {
    private final JLabel status = new JLabel("Preparing JaCaMo...");
    private final DefaultTableModel state = table();
    private final DefaultTableModel goals = table("CSL object", "Actor", "Goal / Task", "Status");
    private final DefaultTableModel checks = table("CSL object", "Condition", "Kind", "OCL", "Value", "Diagnostic");
    private final DefaultTableModel history = table("Agent", "Plan", "Source outcome", "Decision");
    StandaloneStateView(Runnable stop) {
        var window = new JFrame("USE — CSL / iStar evaluation");
        window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        var tabs = new JTabbedPane();
        tabs.addTab("CSL state", new JScrollPane(new JTable(state)));
        tabs.addTab("iStar state", new JScrollPane(new JTable(goals)));
        tabs.addTab("OCL", new JScrollPane(new JTable(checks)));
        tabs.addTab("Checkpoints", new JScrollPane(new JTable(history)));
        var button = new JButton("Stop JaCaMo"); button.addActionListener(e -> stop.run());
        var bar = new JPanel(new BorderLayout()); bar.add(status); bar.add(button, BorderLayout.EAST);
        window.add(bar, BorderLayout.NORTH); window.add(tabs); window.setSize(1200, 680);
        window.addWindowListener(new WindowAdapter() { public void windowClosed(WindowEvent event) { stop.run(); } });
        window.setLocationByPlatform(true); window.setVisible(true);
    }
    private static DefaultTableModel table(String... columns) {
        return new DefaultTableModel(columns, 0) { public boolean isCellEditable(int row, int column) { return false; } };
    }
    public void status(String text) { SwingUtilities.invokeLater(() -> status.setText(text)); }
    public void checkpoint(Map<String,Object> request, Map<String,Object> response, CslStateVerifier.Result result) {
        SwingUtilities.invokeLater(() -> {
            if (result != null) {
                var columns = new TreeSet<String>(); result.state().forEach(row -> columns.addAll(row.keySet()));
                var ordered = new ArrayList<>(columns); state.setColumnIdentifiers(ordered.toArray()); state.setRowCount(0);
                result.state().forEach(row -> state.addRow(ordered.stream().map(c -> row.getOrDefault(c, "")).toArray()));
                goals.setRowCount(0); result.elements().forEach(e -> goals.addRow(new Object[]{e.context(), e.actor(), e.element(), e.status()}));
                checks.setRowCount(0); result.checks().forEach(c -> checks.addRow(new Object[]{c.context(), c.id(), c.kind(), c.expression(), c.verdict(), c.diagnostic()}));
            }
            history.addRow(new Object[]{request.get("agent"), request.get("plan"), request.get("outcome"), response.get("decision")});
            status.setText(response.get("decision") + " · " + response.get("diagnostic"));
        });
    }
}
