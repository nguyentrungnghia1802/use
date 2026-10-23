package org.tzi.use.plugins.jacamo.semantics.auction;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.*;
import org.tzi.use.plugins.jacamo.ui.JaCaMoWorkbenchPanel;
import static org.junit.jupiter.api.Assertions.*;

/** Headful real Swing dialogs and the production static case/engine import.
 * Recorded transport is explicit; live installed Workbench remains E01/E02. */
class AuctionWorkbenchUiTest {
    @Test void nativeUseClassAndObjectDiagramsStillBindTheActualAuctionState()throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless());
        String height=System.getProperty("use.gui.view.classdiagram.class.minheight"),width=System.getProperty("use.gui.view.classdiagram.class.minwidth");
        System.setProperty("use.gui.view.classdiagram.class.minheight","40");System.setProperty("use.gui.view.classdiagram.class.minwidth","140");
        try(var fixture=new AuctionCastFixture(1)) {
            var session=new Session();var window=new AtomicReference<org.tzi.use.gui.main.MainWindow>();var objects=new AtomicReference<org.tzi.use.gui.views.diagrams.objectdiagram.NewObjectDiagramView>();var classes=new AtomicReference<org.tzi.use.gui.views.diagrams.classdiagram.ClassDiagramView>();
            try {
                SwingUtilities.invokeAndWait(()->{session.setSystem(fixture.projector.system());var pluginRuntime=org.tzi.use.runtime.impl.PluginRuntime.getInstance();window.set(org.tzi.use.gui.main.MainWindow.create(session,pluginRuntime));objects.set(new org.tzi.use.gui.views.diagrams.objectdiagram.NewObjectDiagramView(window.get(),session.system()));classes.set(new org.tzi.use.gui.views.diagrams.classdiagram.ClassDiagramView(window.get(),session.system(),false,pluginRuntime));objects.get().getDiagram().getOptions().setDoAutoLayout(false);});
                AuctionNativeOperationTest.invoke(fixture,0,fixture.org.live.actor,new cartago.Op("start","headful native diagram"),false);
                SwingUtilities.invokeAndWait(()->{
                    var image=new java.awt.image.BufferedImage(1000,700,java.awt.image.BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();graphics.setClip(0,0,1000,700);
                    try{objects.get().getDiagram().invalidateContent(false);objects.get().getDiagram().drawDiagram(graphics);classes.get().setSize(1000,700);classes.get().paint(graphics);}finally{graphics.dispose();}
                    for(var artifact:fixture.artifacts){var nativeObject=AuctionNativeOperationTest.artifact(fixture,artifact);assertSame(nativeObject,objects.get().getDiagram().getVisibleData().fObjectToNodeMap.get(nativeObject).object());}
                    assertSame(fixture.projector.system(),session.system());
                });
                assertEquals("true",org.tzi.use.api.UseSystemApi.create(session.system(),false).evaluate("1 + 1 = 2").toString());
            }finally{SwingUtilities.invokeAndWait(()->{if(objects.get()!=null)objects.get().detachModel();if(classes.get()!=null)classes.get().detachModel();if(window.get()!=null)window.get().dispose();});}
        }finally{restore("use.gui.view.classdiagram.class.minheight",height);restore("use.gui.view.classdiagram.class.minwidth",width);}
    }
    @Test void realXmlDialogImportAndRejectedReimportKeepActiveCaseAndNativeOclOnOneSession()throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless(),"Run this required gate with -Djava.awt.headless=false");
        try(var fixture=new AuctionLiveFixture()) {
            var session=new Session();var frame=new AtomicReference<JFrame>();var panel=new AtomicReference<JaCaMoWorkbenchPanel>();var timer=new AtomicReference<javax.swing.Timer>();
            try(var facade=AuctionImportTest.facade(fixture.source,session)) {
                SwingUtilities.invokeAndWait(()->{var view=new JaCaMoWorkbenchPanel(facade);panel.set(view);var window=new JFrame("Auction Workbench V01 acceptance");frame.set(window);window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);window.setContentPane(view);window.setSize(1120,800);window.setLocationRelativeTo(null);window.setVisible(true);});
                try {
                    SwingUtilities.invokeAndWait(()->named(panel.get(),"engine-profile",JComboBox.class).setSelectedItem("AUCTION_OBSERVED_V1"));
                    Path xml=Path.of("examples/auction/auction_jacamo_to_csl_rules.xml").toAbsolutePath();
                    SwingUtilities.invokeLater(()->named(panel.get(),"choose-mapping",JButton.class).doClick());
                    var chooser=awaitChooser();SwingUtilities.invokeAndWait(()->{assertTrue(chooser.getFileFilter().accept(xml.toFile()));assertFalse(chooser.getFileFilter().accept(Path.of("rules.json").toFile()));chooser.setSelectedFile(xml.toFile());chooser.approveSelection();});
                    await(()->xml.toString().equals(named(panel.get(),"mapping-specification",JTextField.class).getText()));
                    var pulses=new AtomicInteger();SwingUtilities.invokeAndWait(()->{timer.set(new javax.swing.Timer(10,e->pulses.incrementAndGet()));timer.get().start();panel.get().importProject(Path.of("examples/auction/auction.jcm"));});
                    await(()->session.hasSystem()&&named(panel.get(),"program-summary",JLabel.class).getText().contains("AUCTION_OBSERVED_V1"));SwingUtilities.invokeAndWait(()->timer.get().stop());
                    var system=session.system();var active=facade.semanticProgramEvidence();var owner=AuctionImportTest.projector(facade).coordinator().originalEngine();
                    assertTrue(pulses.get()>0,"Real UI events must continue while native compiler/import runs on worker");assertEquals(new JaCaMoFacade.ImportSelection("AUCTION_OBSERVED_V1",xml),facade.importSelection());
                    assertNotNull(system.model().getClass("Auction"));assertEquals(5,system.model().prePostConditions().size());assertEquals("true",org.tzi.use.api.UseSystemApi.create(system,false).evaluate("1 + 1 = 2").toString());
                    Path invalid=AuctionImportTest.copyCase(fixture.bundle).resolve("auction_jacamo_to_csl_rules.xml");Files.writeString(invalid,"<invalid/>");
                    SwingUtilities.invokeAndWait(()->{named(panel.get(),"mapping-specification",JTextField.class).setText(invalid.toString());panel.get().importProject(Path.of("examples/auction/auction.jcm"));});
                    await(()->Arrays.stream(Window.getWindows()).anyMatch(w->w instanceof JDialog&&w.isVisible()&&has(w,JOptionPane.class)));
                    SwingUtilities.invokeAndWait(()->{for(var window:Window.getWindows())if(window instanceof JDialog&&window.isVisible()&&has(window,JOptionPane.class))window.dispose();});
                    assertSame(system,session.system());assertSame(owner,AuctionImportTest.projector(facade).coordinator().originalEngine());assertSame(active,facade.semanticProgramEvidence());assertEquals(xml,facade.importSelection().specification());
                    assertEquals("true",org.tzi.use.api.UseSystemApi.create(system,false).evaluate("1 + 1 = 2").toString());
                    SwingUtilities.invokeAndWait(()->{var image=new java.awt.image.BufferedImage(frame.get().getWidth(),frame.get().getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();frame.get().paint(graphics);graphics.dispose();try{javax.imageio.ImageIO.write(image,"png",Path.of("target/auction-implementation/V01-workbench.png").toFile());}catch(java.io.IOException error){throw new IllegalStateException(error);}});
                }finally{SwingUtilities.invokeAndWait(()->{if(timer.get()!=null)timer.get().stop();for(var window:Window.getWindows())if(window instanceof JDialog&&window.isVisible())window.dispose();if(frame.get()!=null)frame.get().dispose();});}
            }
        }
    }
    static JFileChooser awaitChooser()throws Exception {var result=new AtomicReference<JFileChooser>();await(()->{for(var window:Window.getWindows())if(window instanceof JDialog&&window.isVisible()){var found=find(window,JFileChooser.class);if(found!=null){result.set(found);return true;}}return false;});return result.get();}
    static <T extends Component>T named(Component component,String name,Class<T> type){if(name.equals(component.getName())&&type.isInstance(component))return type.cast(component);if(component instanceof Container parent)for(var child:parent.getComponents()){var found=optional(child,name,type);if(found!=null)return found;}throw new AssertionError("Missing widget "+name);}
    static <T extends Component>T optional(Component component,String name,Class<T> type){if(name.equals(component.getName())&&type.isInstance(component))return type.cast(component);if(component instanceof Container parent)for(var child:parent.getComponents()){var found=optional(child,name,type);if(found!=null)return found;}return null;}
    static boolean has(Component component,Class<? extends Component> type){return find(component,type)!=null;}
    static <T extends Component>T find(Component component,Class<T> type){if(type.isInstance(component))return type.cast(component);if(component instanceof Container parent)for(var child:parent.getComponents()){var found=find(child,type);if(found!=null)return found;}return null;}
    static void await(java.util.function.BooleanSupplier ready)throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(System.nanoTime()<until){var result=new AtomicBoolean();SwingUtilities.invokeAndWait(()->result.set(ready.getAsBoolean()));if(result.get())return;Thread.sleep(25);}throw new AssertionError("Headful Auction widget condition timed out");}
    static void restore(String name,String value){if(value==null)System.clearProperty(name);else System.setProperty(name,value);}
}
