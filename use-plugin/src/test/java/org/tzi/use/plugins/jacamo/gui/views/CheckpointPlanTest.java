package org.tzi.use.plugins.jacamo.gui.views;

import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointPlanTest {
    @Test void usesTheSourceTriggerAndRetainsTheFullReferenceInTheTooltip() throws Exception {
        String reference = "jar:file:D:/tools/runtime.jar!/templates/agent.asl:7:11:+!schedule(Job)";
        var plan = CheckpointPlan.from(Map.of("plan", reference, "checkpoint", Map.of("planTrigger", "+!schedule(Job)")));
        SwingUtilities.invokeAndWait(() -> {
            var table = new JTable(new Object[][] {{plan}, {""}}, new String[] {"Plan"});
            CheckpointPlan.configureColumn(table);
            var cell = (JLabel) table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
            assertEquals("+!schedule(Job)", cell.getText());
            assertEquals(reference, cell.getToolTipText());
            cell = (JLabel) table.prepareRenderer(table.getCellRenderer(1, 0), 1, 0);
            assertNull(cell.getToolTipText());
        });
    }

    @Test void readsOlderPlanReferencesWithoutRemovingNamespacesOrTriggerArguments() {
        String trigger = "+!ns::dispatch(\"10:+!other\",Job)";
        String reference = "file:C:\\project\\agent.asl:3:1:" + trigger;
        assertEquals(trigger, CheckpointPlan.from(Map.of("plan", reference)).text());
        assertEquals(trigger, CheckpointPlan.from(Map.of("plan", trigger)).text());
        assertEquals("-ready(Task)", CheckpointPlan.from(Map.of("plan", "nofile:42:-ready(Task)")).text());
    }

    @Test void displaysStandaloneCheckpointLabelsAndHandlesMissingMetadata() {
        assertEquals("INITIAL", CheckpointPlan.from(Map.of("plan", "INITIAL")).text());
        assertEquals("SOURCE_MESSAGE", CheckpointPlan.from(Map.of("plan", "SOURCE_MESSAGE",
                "checkpoint", Map.of("planTrigger", ""))).text());
        assertEquals("", CheckpointPlan.from(Map.of()).text());
    }
}
