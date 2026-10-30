package org.tzi.use.plugins.jacamo.ui;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;
class PreparedRuntimeResultsTest {
 @Test void workerPreparationPreservesFailureEpisodesAndDoesNotTouchSwing()throws Exception{
  var cache=new ObservedConformancePanel.PreparationCache();
  var fail=new Cut("one","trace","state",List.of(new Evaluation("Safety","false","FAIL","KNOWN","exact witness")), "COMPLETE","CURRENT_OBSERVED",true);
  var pass=new Cut("two","trace","state2",List.of(new Evaluation("Safety","true","PASS","KNOWN","recovery")), "COMPLETE","CURRENT_OBSERVED",false);
  var worker=Executors.newSingleThreadExecutor();
  try{
   var prepared=worker.submit(()->cache.prepare(List.of(fail,pass),"",true)).get();
   assertEquals(List.of(fail,pass),prepared.audit());assertEquals(1,prepared.failures().size());
   SwingUtilities.invokeAndWait(()->{
    var view=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());view.applyPrepared(prepared);
    var failures=DetachedAuthoredWorkflowTest.find(view,"observed-failure-points",JComboBox.class);
    assertEquals(1,failures.getItemCount());
    assertTrue(DetachedAuthoredWorkflowTest.find(view,"observed-failure",JButton.class).isEnabled());
   });
   var corrected=new Cut("one","trace","state",List.of(new Evaluation("Safety","true","PASS","KNOWN","corrected")), "COMPLETE","CURRENT_OBSERVED",false);
   var replacement=worker.submit(()->cache.prepare(List.of(corrected,pass),"",true)).get();
   assertEquals(0,replacement.failures().size(),"Never reuse an old result solely because its boundary ID is equal");
   assertEquals(0,worker.submit(()->cache.prepare(List.of(),"",true)).get().audit().size(),"Dispose/reset cannot retain the previous timeline");
  }finally{worker.shutdownNow();}
 }
}
