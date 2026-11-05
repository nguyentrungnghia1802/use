package org.tzi.use.plugins.jacamo.ui;
import java.nio.file.*;import java.util.*;import javax.swing.*;
public class InstanceViewProbe {
 public static void main(String[] args)throws Exception {
  var defaults=new IStarInstancesPanelTest();defaults.useBundledDiagramDefaults();
  try {
   var goals=new org.tzi.use.semantic.engine.frontend.IStarFrontend().compile(Files.readString(Path.of("examples/auction/auction.istar")),"current",org.tzi.use.semantic.engine.api.EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
   var cut=IStarInstancesPanelTest.recorded();
   SwingUtilities.invokeAndWait(()->{var panel=new org.tzi.use.plugins.jacamo.ui.use4goal.state.IStarInstancesPanel(goals);try{panel.setActive(true);panel.update(List.of(cut));RuntimeStateViewsTest.render(panel,"current-instances.png");}catch(Exception e){throw new RuntimeException(e);}finally{panel.dispose();}});
  }finally{defaults.restoreDefaults();}System.out.println("ISTAR_CURRENT_INSTANCES_OFFSCREEN_RENDER_PASS");System.exit(0);
 }
}
