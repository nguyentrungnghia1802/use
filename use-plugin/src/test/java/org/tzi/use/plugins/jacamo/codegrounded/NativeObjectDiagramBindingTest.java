package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.gui.views.diagrams.objectdiagram.NewObjectDiagramView;
import org.tzi.use.main.Session;
import org.tzi.use.uml.sys.*;
import org.tzi.use.uml.sys.events.AtomicStateChangedEvent;
import org.tzi.use.util.soil.StateDifference;

class NativeObjectDiagramBindingTest {
    @Test void hiddenAssociationClassRecreationAndInsertionKeepCurrentBindings() throws Exception {
        String height=System.getProperty("use.gui.view.classdiagram.class.minheight");
        String width=System.getProperty("use.gui.view.classdiagram.class.minwidth");
        System.setProperty("use.gui.view.classdiagram.class.minheight","40");
        System.setProperty("use.gui.view.classdiagram.class.minwidth","140");MainWindow.setJavaFxCall(true);
        try {
            StepReplayProof.onEdt(()->{
                var model=new UseModelApi("hiddenEnactments");
                var playerClass=model.createClass("Player",false);var teamClass=model.createClass("Team",false);
                var enactment=model.createAssociationClass("Enactment",false,
                        "Player","players","*",0,"Team","teams","*",0);
                var api=UseSystemApi.create(model.getModel(),false);var system=api.getSystem();var state=system.state();
                var player=api.createObjectEx(playerClass,"player");var team=api.createObjectEx(teamClass,"team");
                var original=api.createLinkObjectEx(enactment,"enactment",new MObject[]{player,team});
                var session=new Session();session.setSystem(system);
                var window=MainWindow.create(session,org.tzi.use.runtime.impl.PluginRuntime.getInstance());
                var view=new NewObjectDiagramView(window,system);
                try {
                    var diagram=view.getDiagram();diagram.getOptions().setDoAutoLayout(false);
                    paint(view);
                    var previousStrategy=diagram.getVisibleData().fObjectToNodeMap.get(original).getStrategy();
                    diagram.hideObject(original);
                    api.deleteObjectEx(original);
                    var current=api.createLinkObjectEx(enactment,"enactment",new MObject[]{player,team});
                    var recreated=new StateDifference();recreated.addModifiedObject(current);
                    assertDoesNotThrow(()->view.onAtomicStateChanged(new AtomicStateChangedEvent(recreated)));
                    var hidden=(org.tzi.use.gui.views.diagrams.objectdiagram.NewObjectDiagram.ObjectDiagramData)diagram.getHiddenData();
                    assertSame(current,hidden.fObjectToNodeMap.get(current).object());
                    assertEquals(1,hidden.fLinkObjectToNodeEdge.size());
                    assertSame(current,hidden.fLinkObjectToNodeEdge.keySet().iterator().next());
                    assertNotNull(hidden.fLinkObjectToNodeEdge.get(current));
                    diagram.showObject(current);paint(view);
                    assertNotSame(previousStrategy,diagram.getVisibleData().fObjectToNodeMap.get(current).getStrategy(),
                            "Recreated nodes must not retain a strategy bound to a disposed edge");
                    // A newly admitted reified link must remain hidden with its hidden endpoint.
                    diagram.hideObject(team);
                    var secondPlayer=api.createObjectEx(playerClass,"secondPlayer");
                    var second=api.createLinkObjectEx(enactment,"second",new MObject[]{secondPlayer,team});
                    var inserted=new StateDifference();inserted.addNewObjects(List.of(secondPlayer,second));inserted.addNewLink(second);
                    assertDoesNotThrow(()->view.onAtomicStateChanged(new AtomicStateChangedEvent(inserted)));
                    assertSame(team,hidden.fObjectToNodeMap.get(team).object());
                    assertSame(second,hidden.fObjectToNodeMap.get(second).object());
                    assertNotNull(hidden.fLinkObjectToNodeEdge.get(current));assertNotNull(hidden.fLinkObjectToNodeEdge.get(second));
                    assertFalse(diagram.getVisibleData().fLinkObjectToNodeEdge.containsKey(second));
                    diagram.showAll();diagram.showAllLinks();paint(view);
                    assertEquals(2,diagram.getVisibleData().fLinkObjectToNodeEdge.size());
                    assertTrue(hidden.fObjectToNodeMap.isEmpty());assertTrue(hidden.fLinkObjectToNodeEdge.isEmpty());
                    assertSame(state,system.state());assertEquals(5,state.numObjects());return null;
                } finally {view.detachModel();window.dispose();}
            });
        } finally {
            MainWindow.setJavaFxCall(false);
            if(height==null)System.clearProperty("use.gui.view.classdiagram.class.minheight");else System.setProperty("use.gui.view.classdiagram.class.minheight",height);
            if(width==null)System.clearProperty("use.gui.view.classdiagram.class.minwidth");else System.setProperty("use.gui.view.classdiagram.class.minwidth",width);
        }
    }

    private static void paint(NewObjectDiagramView view) {
        var image=new BufferedImage(1000,700,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();
        try {graphics.setClip(0,0,1000,700);view.getDiagram().invalidateContent(false);view.getDiagram().drawDiagram(graphics);}
        finally {graphics.dispose();}
    }

    @Test void acceptedPropertyABAMutationsAndRemovalUpdateNativePaintedValues() throws Exception {
        String height=System.getProperty("use.gui.view.classdiagram.class.minheight");
        String width=System.getProperty("use.gui.view.classdiagram.class.minwidth");
        System.setProperty("use.gui.view.classdiagram.class.minheight","40");
        System.setProperty("use.gui.view.classdiagram.class.minwidth","140");MainWindow.setJavaFxCall(true);
        try {
            StepReplayProof.onEdt(()->{
                var projector=RuntimeVerificationFixtures.projector();var system=projector.system();var state=system.state();
                var session=new Session();session.setSystem(system);
                var window=MainWindow.create(session,org.tzi.use.runtime.impl.PluginRuntime.getInstance());
                var view=new NewObjectDiagramView(window,system);
                try {
                    var object=state.allObjects().stream().filter(o->o.cls().name().equals("LiveRuntimePropertyArtifact")).findFirst().orElseThrow();
                    assertPaintedValue(view,object,"status='A'");
                    projector.apply(RuntimeVerificationFixtures.delta("actual-B",1,"B"));assertPaintedValue(view,object,"status='B'");
                    projector.apply(RuntimeVerificationFixtures.delta("actual-A",2,"A"));assertPaintedValue(view,object,"status='A'");
                    projector.apply(RuntimeVerificationFixtures.event("actual-remove",3,org.jacamo.bridge.contract.RuntimeEventKind.CHANGED,
                            RuntimeVerificationFixtures.ID,java.util.Map.of("normalizedEventKind","APPLY_CARTAGO_PROPERTY_DELTA",
                                    "semanticId",RuntimeVerificationFixtures.ARTIFACT,"properties",List.of(),
                                    "removedPropertySemanticIds",List.of(RuntimeVerificationFixtures.PROPERTY))));
                    assertPaintedValue(view,object,"status=null");
                    assertSame(state,system.state());assertSame(object,state.objectByName(object.name()));return null;
                } finally {view.detachModel();window.dispose();projector.coordinator().close();}
            });
        } finally {
            MainWindow.setJavaFxCall(false);
            if(height==null)System.clearProperty("use.gui.view.classdiagram.class.minheight");else System.setProperty("use.gui.view.classdiagram.class.minheight",height);
            if(width==null)System.clearProperty("use.gui.view.classdiagram.class.minwidth");else System.setProperty("use.gui.view.classdiagram.class.minwidth",width);
        }
    }

    private static void assertPaintedValue(NewObjectDiagramView view,MObject object,String expected)throws Exception {
        var image=new BufferedImage(1000,700,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();
        try {graphics.setClip(0,0,1000,700);view.getDiagram().invalidateContent(false);view.getDiagram().drawDiagram(graphics);}
        finally {graphics.dispose();}
        var node=view.getDiagram().getVisibleData().fObjectToNodeMap.get(object);
        var values=org.tzi.use.gui.views.diagrams.objectdiagram.ObjectNode.class.getDeclaredField("fValues");values.setAccessible(true);
        assertTrue(java.util.Arrays.asList((String[])values.get(node)).contains(expected),"Stale native painted value: "+expected);
    }

    @Test void recreatedObjectsRebindNativeNodesAndLinksIncludingHiddenObjects() throws Exception {
        String height=System.getProperty("use.gui.view.classdiagram.class.minheight");
        String width=System.getProperty("use.gui.view.classdiagram.class.minwidth");
        System.setProperty("use.gui.view.classdiagram.class.minheight","40");
        System.setProperty("use.gui.view.classdiagram.class.minwidth","140");MainWindow.setJavaFxCall(true);
        try {
            StepReplayProof.onEdt(()->{
                var model=new UseModelApi("binding");
                var oldClass=model.createClass("Initial",false);var newClass=model.createClass("Discovered",false);
                var peerClass=model.createClass("Peer",false);
                var oldAttribute=model.createAttribute("Initial","name","String");
                var newAttribute=model.createAttribute("Discovered","name","String");
                var originalAssociation=model.createAssociation("Before","Initial","initial","*",0,"Peer","beforePeers","*",0);
                var replacementAssociation=model.createAssociation("After","Discovered","discovered","*",0,"Peer","afterPeers","*",0);
                var api=UseSystemApi.create(model.getModel(),false);var system=api.getSystem();var state=system.state();
                var original=api.createObjectEx(oldClass,"shared");var peer=api.createObjectEx(peerClass,"peer");
                api.setAttributeValueEx(original,oldAttribute,new org.tzi.use.uml.ocl.value.StringValue("before"));
                var originalLink=api.createLinkEx(originalAssociation,new MObject[]{original,peer});
                var session=new Session();session.setSystem(system);
                var window=MainWindow.create(session,org.tzi.use.runtime.impl.PluginRuntime.getInstance());
                var view=new NewObjectDiagramView(window,system);
                try {
                    var diagram=view.getDiagram();diagram.getOptions().setDoAutoLayout(false);
                    diagram.hideObject(peer);
                    api.deleteObjectEx(original);
                    var replacement=api.createObjectEx(newClass,"shared");
                    api.setAttributeValueEx(replacement,newAttribute,new org.tzi.use.uml.ocl.value.StringValue("after"));
                    var replacementLink=api.createLinkEx(replacementAssociation,new MObject[]{replacement,peer});
                    var difference=new StateDifference();difference.addDeletedLink(originalLink);
                    difference.addNewLink(replacementLink);difference.addModifiedObject(replacement);
                    system.getEventBus().post(new AtomicStateChangedEvent(difference));
                    assertSame(replacement,diagram.getVisibleData().fObjectToNodeMap.get(replacement).object());
                    var hidden=(org.tzi.use.gui.views.diagrams.objectdiagram.NewObjectDiagram.ObjectDiagramData)diagram.getHiddenData();
                    assertSame(peer,hidden.fObjectToNodeMap.get(peer).object());
                    assertTrue(hidden.fBinaryLinkToEdgeMap.containsKey(replacementLink));
                    diagram.showAll();diagram.showAllLinks();
                    // Same-class, same-name recreation must also remove stale Java bindings.
                    api.deleteLinkEx(replacementLink);api.deleteObjectEx(peer);
                    var currentPeer=api.createObjectEx(peerClass,"peer");
                    var currentLink=api.createLinkEx(replacementAssociation,new MObject[]{replacement,currentPeer});
                    var recreated=new StateDifference();recreated.addModifiedObjects(List.of(replacement,currentPeer));
                    system.getEventBus().post(new AtomicStateChangedEvent(recreated));
                    assertSame(currentPeer,diagram.getVisibleData().fObjectToNodeMap.get(currentPeer).object());
                    var represented=diagram.getVisibleData().fBinaryLinkToEdgeMap.keySet();
                    assertEquals(1,represented.size());assertSame(currentLink,represented.iterator().next());
                    var image=new BufferedImage(1000,700,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();
                    try {graphics.setClip(0,0,1000,700);assertDoesNotThrow(()->diagram.drawDiagram(graphics));}
                    finally {graphics.dispose();}
                    assertSame(state,system.state());assertEquals(2,state.numObjects());return null;
                } finally {view.detachModel();window.dispose();}
            });
        } finally {
            MainWindow.setJavaFxCall(false);
            if(height==null)System.clearProperty("use.gui.view.classdiagram.class.minheight");else System.setProperty("use.gui.view.classdiagram.class.minheight",height);
            if(width==null)System.clearProperty("use.gui.view.classdiagram.class.minwidth");else System.setProperty("use.gui.view.classdiagram.class.minwidth",width);
        }
    }
}
