import java.nio.file.*;
import java.util.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.imageio.ImageIO;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.state.IStarInstancesPanel;
import org.tzi.use.plugins.jacamo.ui.use4goal.istar.IStarDiagram;
public class FrozenPresentationProbe {
 public static void main(String[] args){try{run(args);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
 static void run(String[] args)throws Exception {
  Path output=Path.of(args[2]);Files.createDirectories(output);
  Properties defaults=new Properties();try(var in=FrozenPresentationProbe.class.getResourceAsStream("/etc/use.properties")){defaults.load(in);}defaults.forEach((k,v)->{if(System.getProperty(k.toString())==null)System.setProperty(k.toString(),v.toString());});
  var result=RuntimeVerificationResult.fromMap(CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(Path.of(args[0])))));
  var snapshot=new VerificationSnapshot(result.stateVersion(),result,null);
  var presenter=new ObservedConformancePresenter(new JaCaMoFacade(){public String status(){return "Frozen source recording";}public VerificationSnapshot verificationSnapshot(){return snapshot;}});
  var cuts=presenter.captureCurrent();var cut=cuts.getLast();var groups=ExecutionGroups.of(cuts);
  var goals=new org.tzi.use.semantic.engine.frontend.IStarFrontend().compile(Files.readString(Path.of(args[1])),"authored",org.tzi.use.semantic.engine.api.EngineSemanticsProfile.valueOf(ExecutionScopeViews.object(cut.visualEvidence().get("originalEngine")).get("engineProfile").toString())).requireModel();
  String before=org.tzi.use.semantic.engine.api.OwnedGoalModel.fingerprint(goals);var receipt=new TreeMap<String,Object>();
  receipt.put("sourceSession",result.sessionId());receipt.put("sourceStateVersion",result.stateVersion());receipt.put("nativeContexts",ExecutionScopeViews.contexts(cut).size());receipt.put("displayExecutions",groups.ids().size());
  var reports=new ArrayList<Map<String,Object>>();
  for(String id:groups.ids()){
   var p=groups.project(cut,id);var population=new ArrayList<Map<String,Object>>();
   for(var row:new LiveGoalSummary(goals).rows(p.evaluations()))if(!row.instances().isEmpty())population.add(Map.of("element",row.id(),"result",row.resultText(),"required",row.resultRole()));
   reports.add(Map.of("id",id,"members",groups.members(id),"goalVerdict",ExecutionScopeViews.goalVerdict(p),"results",population));
  }
  receipt.put("executions",reports);
  SwingUtilities.invokeAndWait(()->{
   var panel=ObservedConformancePanel.liveResults(goals);panel.setCuts(cuts);panel.selectExecution(groups.ids().getFirst());
   for(var table:find(panel,JTable.class))if("live-goal-status".equals(table.getName()))for(int row=0;row<table.getRowCount();row++)if(table.getValueAt(row,1).toString().contains("Incomplete")){table.setRowSelectionInterval(row,row);break;}
   try{render(panel.istarVerificationPanel(),output.resolve("verification.png"));}catch(Exception e){throw new RuntimeException(e);}
   var instances=new IStarInstancesPanel(goals);instances.setActive(true);instances.update(cuts);
   if(find(instances,IStarDiagram.class).size()!=groups.ids().size())throw new AssertionError("One graph per display execution required");
   receipt.put("renderedGraphs",find(instances,IStarDiagram.class).size());instances.dispose();
  });
  if(!before.equals(org.tzi.use.semantic.engine.api.OwnedGoalModel.fingerprint(goals)))throw new AssertionError("Authored graph mutated");
  receipt.put("nativeModelUnchanged",true);receipt.put("acceptance","FROZEN_SOURCE_REPLAY_AND_OFFSCREEN_SWING");
  Files.writeString(output.resolve("receipt.json"),new String(CanonicalJson.encode(receipt),java.nio.charset.StandardCharsets.UTF_8));
  System.out.println("FROZEN_PRESENTATION_PASS nativeContexts="+ExecutionScopeViews.contexts(cut).size()+" displayExecutions="+groups.ids().size()+" renderedGraphs="+receipt.get("renderedGraphs"));
 }
 static void render(JPanel panel,Path path)throws Exception{var frame=new JFrame();try{frame.add(panel);frame.setSize(1400,980);frame.addNotify();frame.validate();for(var scroll:find(panel,JScrollPane.class)){scroll.getHorizontalScrollBar().setValue(0);scroll.getViewport().setViewPosition(new Point(0,0));}var image=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();try{panel.printAll(g);}finally{g.dispose();}ImageIO.write(image,"png",path.toFile());}finally{frame.dispose();}}
 static <T>java.util.List<T> find(Container root,Class<T> type){var rows=new ArrayList<T>();for(var c:root.getComponents()){if(type.isInstance(c))rows.add(type.cast(c));if(c instanceof Container child)rows.addAll(find(child,type));}return rows;}
}
