package org.tzi.use.plugins.jacamo.ui;

import java.awt.BorderLayout;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import org.jacamo.bridge.contract.CanonicalJson;

/** Read-only explanation of frozen compiler/XML dependencies, not a receipt of
 * successful source observation. Provider availability belongs to live evidence. */
public final class ObservationRequirementsPanel extends JPanel {
    private final DefaultTableModel rows=new DefaultTableModel(
            new String[]{"Requirement", "Field / population / link", "Source / mapping", "Capability", "Status"},0) {
        @Override public boolean isCellEditable(int row,int column){return false;}
    };
    private final JLabel summary=new JLabel();
    public ObservationRequirementsPanel() {
        super(new BorderLayout(4,4));setName("observation-requirements");add(summary,BorderLayout.NORTH);
        var table=new JTable(rows);table.setName("observation-read-set");table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(i==2?500:210);
        add(new JScrollPane(table),BorderLayout.CENTER);
    }
    public void refresh(Map<String,Object> preflight) {
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("OBSERVATION_REQUIREMENTS_REQUIRES_EDT");
        rows.setRowCount(0);
        summary.setText("Declared observation scope; availability is not yet proven. Read set: "+preflight.getOrDefault("readSetHash","UNAVAILABLE"));
        for(Object raw:(List<?>)preflight.getOrDefault("reads",List.of())) {
            var read=CanonicalJson.object(raw);var sources=(List<?>)read.getOrDefault("sources",List.of());
            String subject=read.containsKey("field")?read.get("field")+(Boolean.TRUE.equals(read.get("preState"))?" @pre":""):
                    read.containsKey("population")?"population "+read.get("population"):"link "+read.get("association");
            var providers=sources.stream().map(CanonicalJson::object).map(s->s.getOrDefault("provider","")).filter(p->!p.equals("")).distinct().toList();
            rows.addRow(new Object[]{read.get("contract"),subject,sources+" / "+read.getOrDefault("mappingRules",List.of()),providers,read.get("status")});
        }
        for(Object raw:(List<?>)preflight.getOrDefault("capabilities",List.of())) {
            var capability=CanonicalJson.object(raw);
            rows.addRow(new Object[]{"XML request: "+capability.get("id"),"framework observation",capability.get("provider"),capability.get("id"),capability.get("status")});
        }
    }
}
