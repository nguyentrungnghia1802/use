package org.tzi.use.plugins.jacamo.ui;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.semantic.engine.frontend.IStarFrontend;
import org.tzi.use.semantic.engine.api.EngineSemanticsProfile;
import org.tzi.use.plugins.jacamo.ui.use4goal.istar.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Evaluation;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityApplicabilityViewsTest {
 @Test void explicitUnassignedInstancesDoNotHideMissingAssignedEvidence() {
  var model=new IStarFrontend().compile("istar Skills { agent Worker { task Execute } }","skills",EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
  var summary=new LiveGoalSummary(model);
  var done=new Evaluation("Execute","FULFILLED","FULFILLED","KNOWN","proof","",false,"GOAL_TASK");
  var absent=new Evaluation("Execute","PENDING","NOT_ASSIGNED","SOURCE_NOT_ASSIGNED","source assignment","",false,"GOAL_TASK");
  var unknown=new Evaluation("Execute","PENDING","UNKNOWN","UNAVAILABLE","missing","",false,"GOAL_TASK");
  assertEquals("FULFILLED",summary.rows(List.of(done,absent)).getFirst().status());
  assertEquals("NOT_ASSIGNED",summary.rows(List.of(absent)).getFirst().status());
  assertEquals("UNKNOWN",summary.rows(List.of(done,absent,unknown)).getFirst().status());
 }
 @Test void authoredActorAssociationsAreRenderedInBothViewsWithoutChangingDefinitions() throws Exception {
  var model=new IStarFrontend().compile(Files.readString(Path.of("examples/auction/auction.istar")),"auction",EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
  long count=model.getActors().stream().flatMap(a->a.associations().stream()).count();
  assertEquals(2,count);
  for(var mode:IStarViewMode.values())assertEquals(count,IStarLayoutBuilder.build(model,mode).edges.stream().filter(e->e.kind()==IStarEdgeKind.ASSOCIATION).count());
  var defaults=new Properties();try(var input=getClass().getResourceAsStream("/etc/use.properties")){defaults.load(input);}
  var introduced=new HashSet<String>();for(String name:defaults.stringPropertyNames())if(System.getProperty(name)==null){introduced.add(name);System.setProperty(name,defaults.getProperty(name));}
  try{javax.swing.SwingUtilities.invokeAndWait(()->{
   var view=new IStarView();try{view.setModel(model);LiveGoalSummaryTest.renderPanel(view,"applicability-istar-definitions.png");}finally{view.detachModel();}
  });}finally{introduced.forEach(System::clearProperty);}
 }
}
