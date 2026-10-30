package org.tzi.use.plugins.jacamo.ui;
import java.nio.file.*;import java.util.*;import javax.swing.*;import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.ui.use4goal.state.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut;
import org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander;
import org.tzi.use.semantic.engine.frontend.*;import org.tzi.use.semantic.engine.api.EngineSemanticsProfile;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeStateViewsTest {
 @Test void actualRecordedCutBuildsInstanceGraphsWithoutEvaluatingOrCombiningPlayers()throws Exception {
  var evidence=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(Path.of("src/test/resources/semantics/state-view-cut.json"))));
  // This recording belongs to the previous Role-specialization model revision.
  // New Agent/OrgCtx live recordings are exercised by AuthoredAuctionResultsIT.
  var goals=new IStarFrontend().compile(Files.readString(Path.of("src/test/resources/auction-reference/authored-v1/auction_istar_final.istar")),"case",EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
  var csl=new CslFrontend().compile(Files.readString(Path.of("src/test/resources/auction-reference/authored-v1/auction_csl.csl")),"case").requireModel();
  var core=CanonicalJson.object(evidence.get("originalEngine"));var contexts=(List<?>)core.get("contexts");assertEquals(2,contexts.size());
  var policies=Map.of("D_bid",OccurrenceGoalModelExpander.Quantifier.ANY);
  for(Object raw:contexts){var context=CanonicalJson.object(raw);var expanded=RuntimeStateViews.expand(goals,context,EngineSemanticsProfile.ROLE_SPECIALIZATION_V1,policies);assertEquals(6,expanded.model().getActors().size());
   for(Object row:CanonicalJson.object(context.get("occurrences")).values()){var occurrence=CanonicalJson.object(row);if("SOURCE_ELEMENT".equals(occurrence.get("expansionKind"))&&Set.of("GOAL","TASK").contains(occurrence.get("elementKind")))assertEquals("FULFILLED",RuntimeStateViews.qualifiedStatus(context,occurrence));}
  }
  var state=RuntimeStateViews.aol(csl,evidence,"{}");assertFalse(state.objects().isEmpty());assertTrue(state.objects().values().stream().anyMatch(v->v.type().equals("Auction")&&v.attributes().containsKey("winner")));
  assertTrue(RuntimeStateViews.aol(csl,Map.of(),"{}").objects().isEmpty());
  var raw=Map.<String,Object>of("executionQuality","UNAVAILABLE","aggregateStatus","FULFILLED");assertEquals("UNKNOWN",RuntimeStateViews.qualifiedStatus(Map.of(),raw));
  var defaults=new Properties();try(var input=getClass().getResourceAsStream("/etc/use.properties")){defaults.load(input);}
  var introduced=new HashSet<String>();for(String name:defaults.stringPropertyNames())if(System.getProperty(name)==null){introduced.add(name);System.setProperty(name,defaults.getProperty(name));}
  try{SwingUtilities.invokeAndWait(()->{
   var nativeModel=new org.tzi.use.plugins.jacamo.semantics.mapping.ocl.CslScopeModelAdapter().compile(csl).model();
   var facade=new org.tzi.use.plugins.jacamo.JaCaMoFacade(){public String status(){return "component";}public SemanticModels semanticModels(){return new SemanticModels(csl,goals,nativeModel,"program");}public Map<String,Object> semanticProgramEvidence(){return Map.of("declarativeProgram",Map.of("dependencyPopulations",Map.of("D_bid","ANY")));}};
   var workbench=new JaCaMoWorkbenchPanel(facade,m->fail(m),true);var tabs=workbench.resultsContent();assertEquals(4,tabs.getTabCount());assertEquals("CSL state (AOL)",tabs.getTitleAt(1));assertEquals("iStar instances (ISCN)",tabs.getTitleAt(2));assertEquals("Advanced / Diagnostics",tabs.getTitleAt(3));
   var diagnostics=DetachedAuthoredWorkflowTest.find(tabs,"diagnostic-tabs",JTabbedPane.class);assertEquals(2,diagnostics.getTabCount());assertEquals("CSL definitions",diagnostics.getTitleAt(0));
   var views=new RuntimeStateViews(csl,goals,policies);views.select(new Cut("state","trace","{}",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false,"key","known","state",false,true,false,evidence));assertNotNull(DetachedAuthoredWorkflowTest.find(views.iscnPanel,"iscn-context-selector",JComboBox.class));assertTrue(labels(views.iscnPanel).stream().anyMatch(t->t.startsWith("ISCN — actor instances")),labels(views.iscnPanel).toString());
   var selector=DetachedAuthoredWorkflowTest.find(views.iscnPanel,"iscn-context-selector",JComboBox.class);assertEquals(2,selector.getItemCount());selector.setSelectedIndex(1);
   assertTrue(labels(views.iscnPanel).stream().anyMatch(t->t.startsWith("ISCN — actor instances")));
   try{render(views.aolPanel,"aol.png");render(views.iscnPanel,"iscn.png");}catch(Exception e){throw new RuntimeException(e);}views.dispose();});}finally{introduced.forEach(System::clearProperty);}
 }
 @Test void anotherCaseRetainsNativeLinksAndUnavailableDoesNotFabricateObjects(){
  var csl=new CslFrontend().compile("csl v4.0 Warehouse { entity Parcel { weight : Integer mutable; } }","neutral").requireModel();
  var evidence=Map.<String,Object>of("presentationState",Map.of("qualifiedValues",true,"objects",Map.of("Parcel:p1",Map.of("weight","3"),"Parcel:p2",Map.of("weight","7"))));
  String image=new String(CanonicalJson.encode(Map.of("links",List.of(Map.of("association","next","participants",List.of("p1","p2"))))),java.nio.charset.StandardCharsets.UTF_8);
  var state=RuntimeStateViews.aol(csl,evidence,image);assertEquals(2,state.objects().size());assertEquals("3",state.objects().get("p1").attributes().get("weight"));assertEquals(1,state.associationLinks().size());assertEquals("next",state.associationLinks().getFirst().relationName());
 }
 @Test void stateNavigationNotifiesViewsAndClearingHistoryClearsViews()throws Exception {
  SwingUtilities.invokeAndWait(()->{var panel=org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());var selected=new java.util.concurrent.atomic.AtomicReference<Cut>();panel.onSelectedCut(selected::set);var one=new Cut("one","one","one",List.of(),"COMPLETE","CURRENT_OBSERVED",false);var two=new Cut("two","two","two",List.of(),"COMPLETE","CURRENT_OBSERVED",false);panel.setCuts(List.of(one,two));assertEquals("two",selected.get().id());DetachedAuthoredWorkflowTest.find(panel,"observed-previous",JButton.class).doClick();assertEquals("one",selected.get().id());panel.setCuts(List.of());assertNull(selected.get());});
 }
 static List<String> labels(java.awt.Container root){var output=new ArrayList<String>();for(var c:root.getComponents()){if(c instanceof JLabel label)output.add(label.getText());if(c instanceof java.awt.Container container)output.addAll(labels(container));}return output;}
 static void render(java.awt.Container root,String name)throws Exception{
  root.setSize(1400,850);layout(root);var image=new java.awt.image.BufferedImage(1400,850,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,1400,850);root.printAll(g);g.dispose();var directory=Path.of("target/state-views");Files.createDirectories(directory);javax.imageio.ImageIO.write(image,"png",directory.resolve(name).toFile());
 }
 static void layout(java.awt.Container root){root.doLayout();for(var c:root.getComponents())if(c instanceof java.awt.Container container)layout(container);}
}
