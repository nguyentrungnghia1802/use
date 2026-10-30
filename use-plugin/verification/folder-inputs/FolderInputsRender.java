package org.tzi.use.plugins.jacamo.ui;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
public class FolderInputsRender {
 public static void main(String[] args)throws Exception {
  SwingUtilities.invokeAndWait(()->{
   JDialog dialog=new JDialog((Frame)null);
   try {
    var facade=new JaCaMoFacade(){public String status(){return "Ready to select external project";}
     public ImportSelection importSelection(){return new ImportSelection("NATIVE_CURRENT",null);}};
    var view=new JaCaMoWorkbenchPanel(facade,message->{throw new AssertionError(message);},true);
    view.selectCaseFolder(Path.of("use-plugin/examples/auction"));
    ((JTextField)find(view,"selected-jcm")).setText("D:/_CODE_BANK/Project_/08_Thesis/jacamo/examples/auction/auction.jcm");
    dialog.add(view);dialog.pack();dialog.addNotify();dialog.validate();
    save(view,"use-plugin/verification/folder-inputs/inputs.png");
    ((JToggleButton)find(view,"show-advanced")).doClick();dialog.pack();dialog.validate();
    save(view,"use-plugin/verification/folder-inputs/inputs-advanced.png");
   }catch(Exception e){throw new RuntimeException(e);}finally{dialog.dispose();}
  });
 }
 static Component find(Container root,String name){for(Component c:root.getComponents()){
  if(name.equals(c.getName()))return c;if(c instanceof Container nested){var found=find(nested,name);if(found!=null)return found;}}
  return null;
 }
 static void save(JPanel panel,String name)throws Exception {
  var image=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);
  var g=image.createGraphics();try{panel.printAll(g);}finally{g.dispose();}
  javax.imageio.ImageIO.write(image,"png",Path.of(name).toFile());
 }
}
