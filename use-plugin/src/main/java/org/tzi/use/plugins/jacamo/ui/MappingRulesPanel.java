package org.tzi.use.plugins.jacamo.ui;

import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;

/** Catalog contract, not materialized project instances or live capabilities. */
final class MappingRulesPanel extends JPanel {
    MappingRulesPanel() {
        super(new BorderLayout(4, 4));
        var rules = new CodeGroundedRuleCatalog().rules();
        var model = new DefaultTableModel(new String[]{"Rule", "JaCaMo → USE"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        rules.forEach(rule -> model.addRow(new Object[]{rule.ruleId(), rule.sourceKindFqcn()
                + " → " + rule.targetUseKind() + " [" + rule.fidelity() + "; "
                + rule.implementationStatus() + "]"}));
        JTable table = new JTable(model) {
            @Override public String getToolTipText(MouseEvent event) {
                int row = rowAtPoint(event.getPoint());
                if (row < 0) return null;
                var rule = rules.get(convertRowIndexToModel(row));
                return rule.sourceAuthority() + " | capability=" + rule.capabilityStatus()
                        + " | " + rule.diagnosticPolicy();
            }
        };
        table.setName("mapping-rules-table");
        table.getColumnModel().getColumn(0).setMaxWidth(90);
        add(new JLabel("Catalog " + CodeGroundedRuleCatalog.VERSION
                + " — contract only; CONDITIONAL / RUNTIME_ONLY / PROVENANCE_ONLY are not live evidence;"
                + " UNAVAILABLE / PLANNED remain capability-gated."), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }
}
