package org.tzi.use.plugins.jacamo.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult;

class SemanticEvidencePanelTest {
    @Test void everyDetailedVerdictIsDisplayedWithoutCoercionAndRowsAreReadOnly()throws Exception {
        var rows=Arrays.stream(SemanticVerdict.values()).map(v->new SemanticConstraintResult(v.name(),"INVARIANT","X","x","source","true",v,v.name(),"diagnostic",1,"",Map.of())).toList();var envelope=new SemanticVerificationEnvelope("1.0","CSL_OBSERVER_V2","config","boundary",rows,Map.of(),Map.of("cartago","PARTIAL"),List.of("explicit decision"));var now=java.time.Instant.EPOCH;var result=new RuntimeVerificationResult("run",1,"revision",1,"event","source",1,now,now,now,"checkpoint","constraints","state",List.of(),0,"INCOMPLETE","STALE","",envelope);
        SwingUtilities.invokeAndWait(()->{var panel=new SemanticEvidencePanel();panel.refresh(result);var tables=new ArrayList<JTable>();collect(panel,tables);var table=tables.stream().filter(t->"semantic-constraints".equals(t.getName())).findFirst().orElseThrow();assertEquals(7,table.getRowCount());for(int row=0;row<7;row++){assertEquals(rows.get(row).verdict().name(),table.getValueAt(row,2));assertFalse(table.isCellEditable(row,2));}assertThrows(UnsupportedOperationException.class,()->envelope.constraints().clear());});
    }
    private static void collect(java.awt.Container parent,List<JTable> tables){for(var child:parent.getComponents()){if(child instanceof JTable table)tables.add(table);if(child instanceof java.awt.Container container)collect(container,tables);}}
}
