package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/** Color supplements the original truth label; evaluation values stay untouched. */
final class TruthResultCellRenderer extends DefaultTableCellRenderer {
    private static final Color TRUE_TEXT = new Color(0x166534);
    private static final Color FALSE_TEXT = new Color(0xB91C1C);
    private static final Color TRUE_SELECTION = new Color(0xDCFCE7);
    private static final Color FALSE_SELECTION = new Color(0xFEE2E2);

    @Override public Component getTableCellRendererComponent(JTable table, Object value,
            boolean selected, boolean focused, int row, int column) {
        super.getTableCellRendererComponent(table, value, selected, focused, row, column);
        // A renderer is reused between rows: reset every property changed below.
        setForeground(selected ? table.getSelectionForeground() : table.getForeground());
        setBackground(selected ? table.getSelectionBackground() : table.getBackground());
        setFont(table.getFont());
        if ("True".equals(value) || "False".equals(value)) {
            boolean truth = "True".equals(value);
            setForeground(truth ? TRUE_TEXT : FALSE_TEXT);
            if (selected) setBackground(truth ? TRUE_SELECTION : FALSE_SELECTION);
            setFont(table.getFont().deriveFont(table.getFont().getStyle() | Font.BOLD));
        }
        return this;
    }
}
