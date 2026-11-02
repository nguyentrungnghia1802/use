package org.tzi.use.plugins.jacamo.ui;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.VerifiedCheckpointService.View;

/** Offscreen Swing acceptance, not a manual desktop claim. */
class VerifiedCheckpointControlsTest {
    static Component find(Container root,String name){for(Component child:root.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container nested){var found=find(nested,name);if(found!=null)return found;}}return null;}
    static void layout(Container root){root.doLayout();for(Component child:root.getComponents())if(child instanceof Container nested)layout(nested);}
    @Test void mainPrepareAlwaysSelectsVerificationWithoutAPassiveSelector()throws Exception {
        var mode=new AtomicBoolean();var prepared=new CountDownLatch(1);var panel=new AtomicReference<JaCaMoWorkbenchPanel>();
        var facade=new JaCaMoFacade(){
            public String status(){return "main workflow";}
            public boolean ownedProducerSupported(){return true;}
            public ImportSelection importSelection(){return new ImportSelection("JACAMO_DECLARATIVE_XML",Path.of("mapping.xml"),null,Path.of("model.csl"),Path.of("model.istar"),false);}
            public void verifiedCheckpointMode(boolean enabled){mode.set(enabled);}
            public ProjectSummary prepareOwnedProducer(Path project,ImportSelection selection,Path classpath,boolean gui){assertTrue(mode.get());prepared.countDown();return null;}
        };
        SwingUtilities.invokeAndWait(()->{panel.set(new JaCaMoWorkbenchPanel(facade,m->fail(m),true));assertInstanceOf(JLabel.class,find(panel.get(),"checkpoint-execution-mode"));panel.get().prepareOwnedProject(Path.of("source.jcm"));});
        assertTrue(prepared.await(5,TimeUnit.SECONDS));assertTrue(mode.get());
        SwingUtilities.invokeAndWait(()->panel.get().removeNotify());
    }
    @Test void liveReviewUsesActiveTokenAndWaitsForProducerReceipt() throws Exception {
        var state=new AtomicReference<>(new View("HELD_FAILURE","active-token","operation-post:source","Original native postcondition failed","producer","use","result",Set.of("execution-one","execution-two"),true,false,Map.of()));
        var sent=new AtomicReference<String>();var receipt=new CompletableFuture<Object>();
        var facade=new JaCaMoFacade(){
            public String status(){return "fixture";}
            public boolean verifiedCheckpointMode(){return true;}
            public View verifiedCheckpointState(){return state.get();}
            public CompletableFuture<?> acknowledgeVerifiedCheckpoint(String token,String result){sent.set(token+":"+result);state.set(new View("HELD_FAILURE",token,"operation-post:source","Original native postcondition failed","producer","use",result,Set.of("execution-one","execution-two"),false,true,Map.of()));return receipt;}
        };
        var panel=new AtomicReference<JaCaMoWorkbenchPanel>();
        SwingUtilities.invokeAndWait(()->{
            panel.set(JaCaMoWorkbenchPanel.authoredWorkflow(facade));panel.get().refreshRuntime();
            var mode=(JLabel)find(panel.get(),"checkpoint-execution-mode");assertEquals("Verified checkpoints",mode.getText());
            var button=(JButton)find(panel.get(),"acknowledge-checkpoint");assertTrue(button.isEnabled());button.doClick();assertEquals("active-token:result",sent.get());
            assertFalse(button.isEnabled());assertNotEquals("Running",((JLabel)find(panel.get(),"verified-checkpoint-status")).getText());
            panel.get().setSize(1300,550);layout(panel.get());var image=new BufferedImage(1300,550,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();panel.get().printAll(graphics);graphics.dispose();
            try{Path out=Path.of("verification/verified-checkpoints/offscreen-controls.png");Files.createDirectories(out.getParent());javax.imageio.ImageIO.write(image,"png",out.toFile());}catch(java.io.IOException failed){throw new RuntimeException(failed);}
        });
        state.set(new View("RUNNING","active-token","operation-post:source","REVIEWED_CHECKPOINT_CONTINUED","producer","use","result",Set.of("execution-one","execution-two"),false,false,Map.of()));receipt.complete(Map.of("released",true));
        SwingUtilities.invokeAndWait(()->{panel.get().refreshRuntime();assertTrue(((JLabel)find(panel.get(),"verified-checkpoint-status")).getText().startsWith("Running"));assertFalse(((JButton)find(panel.get(),"acknowledge-checkpoint")).isEnabled());panel.get().removeNotify();});
    }
}
