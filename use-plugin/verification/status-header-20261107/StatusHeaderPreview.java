package org.tzi.use.plugins.jacamo.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol.Check;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.VerifiedCheckpointService.View;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;

/** Visual fixtures only; no domain verdict is created or changed. */
public class StatusHeaderPreview {
 public static void main(String[] args)throws Exception {
  SwingUtilities.invokeAndWait(()->{try{
   var root=new JPanel(new GridLayout(5,1,0,6));
   String[] titles={"While running","While checking","Violation ready for review","Verification completed","Completed after a reviewed issue"};
   String[] states={"RUNNING","VERIFYING","HELD_FAILURE","RUNNING","RUNNING"};
   for(int i=0;i<5;i++){
    var badge=new VerificationStatusBadge();
    if(i>=3){
     var row=new Evaluation("root","","FULFILLED","KNOWN","","",true,"GOAL_TASK","","agent","execution","occurrence");
     var cut=new Cut("fixture","","",List.of(row),"COMPLETE_OBSERVED","CURRENT_OBSERVED",false,"","","",true,true,false,
      Map.of("requirementInventory",Map.of("selectedIntentionalElements",List.of("root")),"originalEngine",Map.of("contexts",List.of(Map.of("context","execution","scopeView",Map.of("entities",Map.of("entity","identity")))))));
     badge.displayed("fixture","cut",cut,Map.of("required",new Check(i==4?"FAIL":"PASS","APPLICABLE","fixture")));
    }
    var view=new View(states[i],"cut","task","fixture","producer","use","result",Set.of("execution"),i==2,false,Map.of());
    var label=new JLabel();VerificationStatusBadge.apply(label,badge.status(view,"RUNNING","LIVE"));
    var button=new JButton("Continue");button.setVisible(view.reviewable());
    var header=JaCaMoWorkbenchPanel.checkpointHeader(label,button);
    header.setBorder(BorderFactory.createEmptyBorder(4,12,4,12));header.add(new JLabel(titles[i]),BorderLayout.WEST);root.add(header);
   }
   root.setSize(1000,230);layout(root);
   var image=new BufferedImage(1000,230,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();root.paint(g);g.dispose();
   ImageIO.write(image,"png",Path.of(args[0]).toFile());
   System.out.println("INSTALLED_STATUS_HEADER_PREVIEW_PASS "+VerificationStatusBadge.class.getProtectionDomain().getCodeSource().getLocation());
  }catch(Exception error){throw new RuntimeException(error);}});
 }
 static void layout(Container c){c.doLayout();for(var child:c.getComponents())if(child instanceof Container nested)layout(nested);}
}
