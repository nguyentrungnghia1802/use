package org.tzi.use.plugins.jacamo.ui;
import java.awt.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import static org.junit.jupiter.api.Assertions.*;
class DetachedAuthoredWorkflowTest {
 static <T> T find(Container parent,String name,Class<T> type){for(var child:parent.getComponents()){if(name.equals(child.getName()))return type.cast(child);if(child instanceof Container nested){var found=find(nested,name,type);if(found!=null)return found;}}return null;}
 static JaCaMoFacade facade(){return new JaCaMoFacade(){public String status(){return "inputs";}public ImportSelection importSelection(){return new ImportSelection("NATIVE_CURRENT",null);}public boolean ownedProducerSupported(){return true;}};}
 @Test void mainInputsDoNotContainRedundantExportsOrSecondOclLoader()throws Exception {
  SwingUtilities.invokeAndWait(()->{var panel=new JaCaMoWorkbenchPanel(facade(),m->fail(m),true);for(String action:java.util.List.of("export-use","export-soil","load-profile"))assertNull(find(panel,action,JButton.class),action+" must not be in the authored input UI");});
 }
 @Test void resultsAreSeparateFromTheInputSurface()throws Exception {
  SwingUtilities.invokeAndWait(()->{var panel=new JaCaMoWorkbenchPanel(facade(),m->fail(m),true);assertNull(find(panel,"workbench-tabs",JTabbedPane.class),"Results must be owned by the separate results window");assertNotNull(find(panel,"show-results",JButton.class));});
 }
 @Test void oneModelessResultsWindowIsReusedAndDisposedWithItsInputOwner()throws Exception {
  SwingUtilities.invokeAndWait(()->{
   var owner=new JDialog((Frame)null,"hidden test owner");var panel=new JaCaMoWorkbenchPanel(facade(),m->fail(m),true);
   try{owner.add(panel);owner.pack();var result=panel.ensureResultsWindow(owner);result.pack();assertSame(owner,result.getOwner());assertEquals(Dialog.ModalityType.MODELESS,result.getModalityType());assertSame(result,panel.ensureResultsWindow(owner));assertTrue(SwingUtilities.isDescendingFrom(panel.resultsContent(),result));assertFalse(SwingUtilities.isDescendingFrom(panel.resultsContent(),panel));
    assertEquals(JDialog.HIDE_ON_CLOSE,result.getDefaultCloseOperation());result.dispatchEvent(new java.awt.event.WindowEvent(result,java.awt.event.WindowEvent.WINDOW_CLOSING));assertTrue(result.isDisplayable());assertSame(result,panel.ensureResultsWindow(owner));owner.dispose();assertFalse(result.isDisplayable());
   }finally{owner.dispose();}
  });
 }

 @Test void technicalTabsAreNestedInAdvancedInsteadOfTheMainResultsSurface()throws Exception {
  SwingUtilities.invokeAndWait(()->{var panel=new JaCaMoWorkbenchPanel(facade(),m->fail(m),true);var tabs=panel.resultsContent();
   assertEquals(1,tabs.getTabCount());assertEquals("Advanced / Diagnostics",tabs.getTitleAt(0));
   var nested=find(tabs,"diagnostic-tabs",JTabbedPane.class);assertNotNull(nested);assertEquals(0,nested.getTabCount(),"Unnecessary technical tabs are removed from the authored workflow");
  });
 }

}
