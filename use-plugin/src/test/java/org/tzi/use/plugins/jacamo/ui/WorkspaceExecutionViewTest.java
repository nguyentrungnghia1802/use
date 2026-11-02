package org.tzi.use.plugins.jacamo.ui;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;
class WorkspaceExecutionViewTest {
 @Test void explicitCompleteContextWithoutEntityIsVisibleAndNeverBorrowsAnotherScope(){
  var view=Map.<String,Object>of("version","BOUND_EXECUTION_VIEW/2.0","domainBound",false,"complete",true,"entities",Map.of(),"owners",Map.of("scopeA",Map.of("type","RunView")),"bindings",Map.of("physicalRoot","source-A"),"definition","declared-view");
  var context=Map.<String,Object>of("context","one","scopeView",view,"executionConstraints",List.of());
  var cut=new Cut("cut","trace","native",List.of(ExecutionScopeViewsTest.goal("one","PENDING")),"INCOMPLETE","CURRENT_OBSERVED",false,"key","","",false,true,false,
   Map.of("originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","contexts",List.of(context)),"presentationState",Map.of("qualifiedValues",true,"objects",Map.of("RunView:scopeA",Map.of("root","source-A"),"Other:scopeB",Map.of("secret","other")),"links",List.of())));
  var prepared=new PreparationCache().prepare(List.of(cut),"one",true,true);assertEquals(1,prepared.labels().size());assertEquals(1,prepared.milestones().size());
  assertFalse(prepared.milestones().getFirst().businessState().contains("secret"));assertTrue(prepared.milestones().getFirst().businessState().contains("source-A"));
  var incomplete=new TreeMap<String,Object>(view);incomplete.put("complete",false);assertFalse(ExecutionScopeViews.boundContext(Map.of("context","one","scopeView",incomplete)));
 }
}
