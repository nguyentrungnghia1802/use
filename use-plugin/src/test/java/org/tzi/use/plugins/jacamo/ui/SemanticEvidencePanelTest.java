package org.tzi.use.plugins.jacamo.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult;

class SemanticEvidencePanelTest {
    @Test void declarativePlanEvidenceAndScopeAreVisibleWithoutLegacyCaseKeys()throws Exception {
        var generic=new SemanticConstraintResult("invariant","INVARIANT_BODY","Pallet","pallet","physical","self.weight >= 0",SemanticVerdict.SKIPPED,"","MISSING_SOURCE_FIELD",1,"",Map.of("mappingPlanHash","plan-hash","scope","scope-17","observationQuality","UNAVAILABLE"));
        var legacy=new SemanticConstraintResult("legacy","INVARIANT_BODY","Counter","counter","physical","true",SemanticVerdict.PASS,"true","",1,"",Map.of("casePlanHash","legacy-hash","scheme","legacy-scheme"));
        var envelope=new SemanticVerificationEnvelope("1.0","USE4GOAL_OBSERVED_V1","config","boundary",List.of(generic,legacy),Map.of(),Map.of(),List.of());var now=java.time.Instant.EPOCH;
        var result=new RuntimeVerificationResult("run",1,"revision",1,"event","source",1,now,now,now,"","constraints","state",List.of(),0,"INCOMPLETE","CURRENT_OBSERVED","",envelope);
        SwingUtilities.invokeAndWait(()->{var panel=new SemanticEvidencePanel();panel.refresh(result);var tables=new ArrayList<JTable>();collect(panel,tables);var table=tables.stream().filter(t->"semantic-case-evidence".equals(t.getName())).findFirst().orElseThrow();
            assertEquals(2,table.getRowCount());assertEquals("scope-17",table.getValueAt(0,2));assertEquals("SKIPPED",table.getValueAt(0,1));assertTrue(table.getValueAt(0,11).toString().contains("UNAVAILABLE"));assertEquals("legacy-scheme",table.getValueAt(1,2));assertFalse(table.isCellEditable(0,1));});
    }
    @Test void originalRawPendingQualityAndInvocationFailRemainIndependentAndEmptyIsNotPass() throws Exception {
        var row=new SemanticConstraintResult("post","INVOCATION_CONFORMANCE","Counter","c","source","self.p = self.p@pre + 1",SemanticVerdict.FAIL,"false","",1,"call",Map.of());
        var occurrence=Map.<String,Object>of("sourceElement","none","leafStatus","PENDING","aggregateStatus","UNKNOWN","quality","HISTORY_GAP","goal",Map.of("status","PENDING"),"stack",List.of("full","stack"),"key",Map.of("episode","actual-incarnation"));
        var core=Map.<String,Object>of("evaluated","PINNED_NATIVE_CUT","historyQuality","GAP","contexts",List.of(Map.of("context","group1","boundary",3,"occurrences",Map.of("exact-key",occurrence))));
        var envelope=new SemanticVerificationEnvelope("1.0","USE4GOAL_OBSERVED_V1","config","b",List.of(row),Map.of(),Map.of("history","GAP"),List.of(),Map.of("originalEngine",core));
        var now=java.time.Instant.EPOCH;var result=new RuntimeVerificationResult("run",1,"revision",1,"event","source",1,now,now,now,"checkpoint","constraints","state",List.of(),0,"INCOMPLETE","CURRENT_OBSERVED","",envelope);
        SwingUtilities.invokeAndWait(()->{
            var panel=new SemanticEvidencePanel();panel.refresh(result);var tables=new ArrayList<JTable>();collect(panel,tables);
            var original=tables.stream().filter(t->"semantic-original-history".equals(t.getName())).findFirst().orElseThrow();
            assertEquals(1,original.getRowCount());assertEquals("PENDING",original.getValueAt(0,4));assertEquals("UNKNOWN",original.getValueAt(0,5));assertEquals("HISTORY_GAP",original.getValueAt(0,6));assertEquals(List.of("full","stack"),original.getValueAt(0,3));
            var constraints=tables.stream().filter(t->"semantic-constraints".equals(t.getName())).findFirst().orElseThrow();assertEquals("FAIL",constraints.getValueAt(0,2));
            assertFalse(original.isCellEditable(0,4));
            var pending=new SemanticVerificationEnvelope("1.0","USE4GOAL_OBSERVED_V1","config","next",List.of(),Map.of(),Map.of(),List.of(),Map.of("originalEngine",Map.of("evaluated","PENDING")));
            panel.refresh(new RuntimeVerificationResult("run",1,"revision",2,"event","source",1,now,now,now,"checkpoint","constraints","state",List.of(),0,"INCOMPLETE","PENDING","",pending));
            assertEquals(0,original.getRowCount());var summary=java.util.Arrays.stream(panel.getComponents()).filter(c->c instanceof JLabel).map(c->((JLabel)c).getText()).findFirst().orElseThrow();assertTrue(summary.contains("no occurrence verdict available"));assertFalse(summary.contains("PASS"));
        });
    }
    @Test void everyDetailedVerdictIsDisplayedWithoutCoercionAndRowsAreReadOnly()throws Exception {
        var rows=Arrays.stream(SemanticVerdict.values()).map(v->new SemanticConstraintResult(v.name(),"INVARIANT","X","x","source","true",v,v.name(),"diagnostic",1,"",Map.of())).toList();var envelope=new SemanticVerificationEnvelope("1.0","CSL_OBSERVER_V2","config","boundary",rows,Map.of(),Map.of("cartago","PARTIAL"),List.of("explicit decision"));var now=java.time.Instant.EPOCH;var result=new RuntimeVerificationResult("run",1,"revision",1,"event","source",1,now,now,now,"checkpoint","constraints","state",List.of(),0,"INCOMPLETE","STALE","",envelope);
        SwingUtilities.invokeAndWait(()->{var panel=new SemanticEvidencePanel();panel.refresh(result);var tables=new ArrayList<JTable>();collect(panel,tables);var table=tables.stream().filter(t->"semantic-constraints".equals(t.getName())).findFirst().orElseThrow();assertEquals(7,table.getRowCount());for(int row=0;row<7;row++){assertEquals(rows.get(row).verdict().name(),table.getValueAt(row,2));assertFalse(table.isCellEditable(row,2));}assertThrows(UnsupportedOperationException.class,()->envelope.constraints().clear());});
    }
    private static void collect(java.awt.Container parent,List<JTable> tables){for(var child:parent.getComponents()){if(child instanceof JTable table)tables.add(table);if(child instanceof java.awt.Container container)collect(container,tables);}}
}
