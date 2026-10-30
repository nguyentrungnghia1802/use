package org.tzi.use.plugins.jacamo.ui;
import java.nio.file.*;import java.util.*;import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews;
import org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander;
import org.tzi.use.semantic.engine.frontend.*;
import org.tzi.use.semantic.engine.api.EngineSemanticsProfile;
import static org.junit.jupiter.api.Assertions.*;
class BoundExecutionViewsTest {
 @Test void authenticTwoExecutionReceiptLabelsAndSynchronizesSeparateIscnGraphsWithoutReevaluation()throws Exception {
  byte[] bytes;try(var stream=new java.util.zip.GZIPInputStream(Files.newInputStream(Path.of("src/test/resources/semantics/recorded-scoped-native-execution.json.gz")))){bytes=stream.readAllBytes();}
  var receipt=CanonicalJson.object(CanonicalJson.decode(bytes));var evidence=CanonicalJson.object(CanonicalJson.object(receipt.get("semantic")).get("observation"));
  var core=CanonicalJson.object(evidence.get("originalEngine"));var profile=EngineSemanticsProfile.valueOf(core.get("engineProfile").toString());
  var goals=new IStarFrontend().compile(Files.readString(Path.of("examples/auction/auction.istar")),"current",profile).requireModel();
  var csl=new CslFrontend().compile(Files.readString(Path.of("examples/auction/auction.csl")),"current").requireModel();
  var policies=OccurrenceGoalModelExpander.policies(new org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingXmlReader().parse(Files.readAllBytes(Path.of("examples/auction/auction-mapping.xml")),"case mapping"),goals);
  var rows=new ArrayList<Evaluation>();
  for(Object raw:(List<?>)core.get("contexts")){
   var context=CanonicalJson.object(raw);
   var expanded=RuntimeStateViews.expand(goals,context,profile,policies);
   assertEquals(11,expanded.model().getActors().size());
   for(var entry:CanonicalJson.object(context.get("occurrences")).entrySet()){
    var row=CanonicalJson.object(entry.getValue());if(!"O_root".equals(row.get("sourceElement")))continue;
    rows.add(new Evaluation("O_root",Objects.toString(row.get("leafStatus"),"UNAVAILABLE"),RuntimeStateViews.qualifiedStatus(context,row),row.get("executionQuality").toString(),row.get("executionEvidence").toString(),"",true,"GOAL_TASK",row.get("aggregateStatus").toString(),row.get("player").toString(),context.get("context").toString(),entry.getKey()));
   }
  }
  assertEquals(2,rows.size());assertTrue(rows.stream().allMatch(r->r.status().equals("FULFILLED")));
  var result=org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult.fromMap(receipt);
  var cut=new ObservedConformancePresenter(new org.tzi.use.plugins.jacamo.JaCaMoFacade(){
   public String status(){return "authentic frozen cut";}
   public List<org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult> runtimeVerificationHistory(){return List.of(result);}
  }).capture().getFirst();
  var choices=ExecutionScopeViews.choices(List.of(cut));assertEquals(2,choices.size());
  assertTrue(choices.values().stream().anyMatch(s->s.contains("'a1'")));assertTrue(choices.values().stream().anyMatch(s->s.contains("'a2'")));
  var defaults=new Properties();try(var stream=getClass().getResourceAsStream("/etc/use.properties")){defaults.load(stream);}
  var introduced=new HashSet<String>();for(String key:defaults.stringPropertyNames())if(System.getProperty(key)==null){introduced.add(key);System.setProperty(key,defaults.getProperty(key));}
  try{SwingUtilities.invokeAndWait(()->{
   var views=new RuntimeStateViews(csl,goals,policies);
   var panel=ObservedConformancePanel.liveResults(goals);
   panel.onSelectedCut(views::select);panel.onExecutionSelected(views::selectScope);views.onScopeSelected(panel::selectExecution);
   try{panel.setCuts(List.of(cut));int ordinal=0;
    for(var choice:choices.entrySet()){
     // Selecting in ISCN must drive the exact same execution in Results.
     views.selectScope(choice.getKey());panel.selectExecution(choice.getKey());
     assertEquals(choice.getKey(),ExecutionScopeViews.selected(panel.selectedCut()));
     var selector=DetachedAuthoredWorkflowTest.find(views.iscnPanel,"iscn-context-selector",JComboBox.class);
     assertEquals(2,selector.getItemCount());assertEquals(choice.getKey(),selector.getSelectedItem());
     assertTrue(RuntimeStateViewsTest.labels(views.iscnPanel).stream().anyMatch(t->t.startsWith("ISCN — actor instances")));
     assertEquals(1,panel.selectedCut().evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")&&r.element().equals("O_root")).count());
     assertEquals("COMPLETE_OBSERVED",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText());
     var aol=RuntimeStateViews.aol(csl,panel.selectedCut().visualEvidence(),"");
     assertEquals(1,aol.objects().values().stream().filter(o->o.kind()==org.tzi.use.plugins.jacamo.ui.use4goal.state.AcceptedAolState.Kind.ENTITY).count());
     assertEquals(5,aol.associationLinks().stream().filter(l->l.relationName().equals("participates (execution)")).count());
     var executionSelector=DetachedAuthoredWorkflowTest.find(panel,"execution-scope-selector",JComboBox.class);assertEquals(2,executionSelector.getItemCount());
     assertNotNull(DetachedAuthoredWorkflowTest.find(views.aolPanel,"aol-context-selector",JComboBox.class));
     assertTrue(panel.selectedCut().evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")).allMatch(r->r.context().equals(choice.getKey())));
     LiveGoalSummaryTest.renderPanel(panel,"bound-execution-"+(++ordinal)+".png");
     LiveGoalSummaryTest.renderPanel(views.iscnPanel,"bound-iscn-"+ordinal+".png");
     LiveGoalSummaryTest.renderPanel(views.aolPanel,"bound-aol-"+ordinal+".png");
    }
    panel.selectExecution("");assertEquals(1,panel.selectedCut().evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")&&r.element().equals("O_root")).count());
    var missing=choices.keySet().iterator().next();
    var absentCore=new TreeMap<String,Object>(core);absentCore.put("contexts",ExecutionScopeViews.contexts(cut).stream().filter(c->!missing.equals(c.get("context"))).toList());
    var absentEvidence=new TreeMap<String,Object>(evidence);absentEvidence.put("originalEngine",absentCore);absentEvidence.put("selectedExecution",missing);
    var absent=new Cut("absent","trace","native",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false,"state","unavailable","state",false,true,false,absentEvidence);
    views.select(absent);
    assertEquals(-1,DetachedAuthoredWorkflowTest.find(views.iscnPanel,"iscn-context-selector",JComboBox.class).getSelectedIndex());
    assertTrue(RuntimeStateViewsTest.labels(views.iscnPanel).stream().anyMatch(t->t.startsWith("No admitted actor instances")),"Missing selected context must not display another execution graph");
   }finally{views.dispose();}
  });}finally{introduced.forEach(System::clearProperty);}
 }
}
