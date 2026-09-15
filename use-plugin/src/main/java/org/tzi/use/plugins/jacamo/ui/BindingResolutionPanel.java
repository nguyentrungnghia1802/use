package org.tzi.use.plugins.jacamo.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;

/** Exact-candidate binding editor. It deliberately leaves every ambiguous request unselected. */
public final class BindingResolutionPanel extends JPanel {
    private final JaCaMoFacade facade;
    private final JLabel source = named(new JLabel("No ambiguous binding request"), "binding-source");
    private final DefaultTableModel candidatesModel = readOnlyModel("Semantic ID", "Owner", "Type", "Source");
    private final JTable candidates = named(new JTable(candidatesModel), "binding-candidates-table");
    private final JTextField reason = named(new JTextField("Explicit user selection", 28), "binding-reason");
    private final JButton persist = named(new JButton("Persist explicit binding"), "binding-persist");
    private Path destination;
    private JaCaMoFacade.BindingRequest request;

    public BindingResolutionPanel(JaCaMoFacade facade) {
        super(new BorderLayout(8, 8));
        this.facade = java.util.Objects.requireNonNull(facade, "facade");
        candidates.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        candidates.getSelectionModel().addListSelectionListener(event ->
                persist.setEnabled(!event.getValueIsAdjusting() && candidates.getSelectedRow() >= 0));
        persist.setEnabled(false);
        persist.addActionListener(event -> persistSelection(reason.getText()));
        add(source, BorderLayout.NORTH);
        add(new JScrollPane(candidates), BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        controls.add(new JLabel("Reason:"));
        controls.add(reason);
        controls.add(persist);
        add(controls, BorderLayout.SOUTH);
    }

    public void showRequest(Path destination, JaCaMoFacade.BindingRequest request) {
        this.destination = java.util.Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        this.request = java.util.Objects.requireNonNull(request, "request");
        source.setText(request.sourceId() + " | owner=" + request.owner() + " | type=" + request.type()
                + " | source=" + request.sourcePath());
        candidatesModel.setRowCount(0);
        request.candidates().forEach(candidate -> candidatesModel.addRow(new Object[] {
                candidate.semanticId(), candidate.owner(), candidate.type(), candidate.sourcePath()
        }));
        candidates.clearSelection();
        persist.setEnabled(false);
    }

    public void persistSelection(String explanation) {
        if (request == null || destination == null || candidates.getSelectedRow() < 0)
            throw new IllegalStateException("BINDING_SELECTION_REQUIRED");
        int modelRow = candidates.convertRowIndexToModel(candidates.getSelectedRow());
        String target = String.valueOf(candidatesModel.getValueAt(modelRow, 0));
        facade.persistBinding(destination, request, target, explanation);
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }
    private static <T extends java.awt.Component> T named(T component, String name) {
        component.setName(name);
        return component;
    }
}
