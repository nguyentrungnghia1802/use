package org.tzi.use.plugins.jacamo.gui.views;

import java.awt.Component;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/** The trigger shown in the table and the full plan reference used for inspection. */
public record CheckpointPlan(String text, String reference) {
    // Older records store source info, the plan ordinal, and the trigger in a single string.
    private static final Pattern PLAN_REFERENCE = Pattern.compile("(?![+-]).*?:[0-9]+:([+-].*)", Pattern.DOTALL);

    public static CheckpointPlan from(Map<String, Object> request) {
        String reference = Objects.toString(request.get("plan"), "");
        if (request.get("checkpoint") instanceof Map<?, ?> metadata
                && metadata.get("planTrigger") instanceof String trigger && !trigger.isBlank()) {
            return new CheckpointPlan(trigger, reference);
        }
        var legacy = PLAN_REFERENCE.matcher(reference);
        return new CheckpointPlan(legacy.matches() ? legacy.group(1) : reference, reference);
    }

    @Override public String toString() { return text; }

    public static void configureColumn(JTable table) {
        var column = table.getColumn("Plan");
        column.setPreferredWidth(320);
        column.setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                    boolean focused, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                setToolTipText(value instanceof CheckpointPlan plan && !plan.reference().isBlank()
                        ? plan.reference() : null);
                return this;
            }
        });
    }
}
