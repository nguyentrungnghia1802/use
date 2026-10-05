package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.impl.PluginRuntime;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;

/** Exercises the existing Swing views against the one activated native MSystem. */
class NativeUseGuiEndToEndIT {
    private String previousHeight, previousWidth;
    @org.junit.jupiter.api.BeforeEach void configureDiagramSizes() {
        previousHeight=System.getProperty("use.gui.view.classdiagram.class.minheight");
        previousWidth=System.getProperty("use.gui.view.classdiagram.class.minwidth");
        System.setProperty("use.gui.view.classdiagram.class.minheight","40");
        System.setProperty("use.gui.view.classdiagram.class.minwidth","140");
    }
    @org.junit.jupiter.api.AfterEach void restoreDiagramSizes() {
        restoreProperty("use.gui.view.classdiagram.class.minheight",previousHeight);
        restoreProperty("use.gui.view.classdiagram.class.minwidth",previousWidth);
    }
    @Test void auctionBrowserDiagramsAttributesLinksAndOclShareTheRuntimeSystem() throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless());
        try (var observed=new AuctionDomainFixture()) {
            Session session=new Session(); MainWindow.setJavaFxCall(true);
            MainWindow window=onEdt(()->MainWindow.create(session,PluginRuntime.getInstance()));
            try {
                new NativeUseSessionActivator().activate(session,observed.pipeline); flushEdt();
                var system=observed.projector.system(); assertSame(system,session.system());
                onEdt(()->{window.setSize(1400,950);window.setVisible(true);return null;});
                var browser=onEdt(()->{
                    var tree=components(window.getModelBrowser(),JTree.class).getFirst();
                    for(int row=0;row<tree.getRowCount();row++) tree.expandRow(row);
                    return java.util.Collections.list(((DefaultMutableTreeNode)tree.getModel().getRoot()).depthFirstEnumeration()).stream()
                        .map(n->((DefaultMutableTreeNode)n).getUserObject()).toList();
                });
                for(var cls:system.model().classes()) assertTrue(browser.contains(cls),cls.name());
                var expectedClasses=new java.util.HashSet<>(org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionPolicy.BASE_CLASSES);
                expectedClasses.addAll(java.util.Set.of("auction_capabilities_Agent","AuctionArtifact","auction_Organization","auctionGroup","doAuction_Scheme","auctioneer","participant"));
                assertEquals(expectedClasses,system.model().classes().stream().map(MClass::name).collect(java.util.stream.Collectors.toSet()));
                assertTrue(system.model().classes().stream().allMatch(org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionPolicy::allowsClass));
                onEdt(()->{menuItem(window.getJMenuBar(),"Class diagram").doClick();return null;});
                var classes=onEdt(()->window.getClassDiagrams().getFirst()); assertSame(system,classes.system());
                onEdt(()->{menuItem(window.getJMenuBar(),"Object diagram").doClick();return null;});
                var objects=onEdt(()->window.getObjectDiagrams().getFirst()); assertSame(system,objects.system());
                assertTrue(onEdt(()->objects.getDiagram().getVisibleData().fObjectToNodeMap.keySet().containsAll(system.state().allObjects())));
                assertTrue(onEdt(()->system.state().allLinks().stream().allMatch(objects.getDiagram().getVisibleData()::containsLink)));
                observed.projector.coordinator().loadProfile(AuctionExternalOclProfileTest.RUNTIME_PROFILE);
                var checked=observed.projector.coordinator().latest().outcomes();
                assertTrue(checked.stream().filter(o->o.constraintId().startsWith("EXTERNAL:")).allMatch(o->o.outcome()==org.tzi.use.plugins.jacamo.verification.VerificationOutcome.PASS),checked.toString());
                assertTrue(checked.stream().noneMatch(o->o.outcome()==org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL
                        || o.outcome()==org.tzi.use.plugins.jacamo.verification.VerificationOutcome.ERROR),checked.toString());
                // This retained recording predates the Goal-state capability contract.
                // It cannot turn unavailable supported-domain evidence into a fake PASS.
                assertTrue(checked.stream().filter(o->o.outcome()==org.tzi.use.plugins.jacamo.verification.VerificationOutcome.SKIPPED)
                        .allMatch(o->o.diagnostic().contains("CAPABILITY") || o.diagnostic().contains("UNAVAILABLE")),checked.toString());
                var artifact=system.state().objectsOfClass(system.model().getClass("AuctionArtifact")).iterator().next();
                assertEquals("false",artifact.state(system.state()).attributeValue("running").toString());
                assertTrue(onEdt(()->UseSystemApi.create(session).evaluate("Agent.allInstances()->size() = 5").toString().equals("true")));
                onEdt(()->{
                    menuItem(window.getJMenuBar(),"Tile").doClick();
                    var directory=java.nio.file.Path.of("target/domain-gui");java.nio.file.Files.createDirectories(directory);
                    classes.getClassDiagram().getOptions().setShowRolenames(false);
                    classes.getClassDiagram().getOptions().setShowAssocNames(true);
                    objects.getDiagram().getOptions().setShowRolenames(false);
                    objects.getDiagram().getOptions().setShowAssocNames(true);
                    captureDiagram(classes.getClassDiagram(),directory.resolve("auction-classes.png"));
                    captureDiagram(objects.getDiagram(),directory.resolve("auction-objects.png"));
                    objects.getDiagram().getOptions().setShowAttributes(false);
                    captureDiagram(objects.getDiagram(),directory.resolve("auction-role-links.png"));
                    for(var entry:java.util.Map.of("auction-window",(Component)window).entrySet()) {
                        var component=entry.getValue();
                        var pixels=new java.awt.image.BufferedImage(Math.max(1,component.getWidth()),Math.max(1,component.getHeight()),java.awt.image.BufferedImage.TYPE_INT_RGB);
                        var graphics=pixels.createGraphics();component.paintAll(graphics);graphics.dispose();
                        javax.imageio.ImageIO.write(pixels,"png",directory.resolve(entry.getKey()+".png").toFile());
                    }
                    return null;
                });
                assertSame(system,session.system());
            } finally {
                onEdt(()->{window.getObjectDiagrams().forEach(v->v.detachModel());window.getClassDiagrams().forEach(v->v.detachModel());window.dispose();MainWindow.setJavaFxCall(false);return null;});
            }
        }
    }
    @Test void schemaDiscoveredAfterViewsOpenRefreshesBrowserDiagramAndCounts() throws Exception {
        var pipeline=CodeGroundedTestFixtures.guiPipeline();
        var session=new Session(); MainWindow.setJavaFxCall(true);
        var window=onEdt(()->MainWindow.create(session,PluginRuntime.getInstance()));
        var projector=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector(pipeline,RuntimeVerificationFixtures.SESSION,1,RuntimeVerificationFixtures.REVISION);
        org.tzi.use.gui.views.ObjectCountView[] counts=new org.tzi.use.gui.views.ObjectCountView[1];
        try {
            new NativeUseSessionActivator().activate(session,pipeline);
            onEdt(()->{menuItem(window.getJMenuBar(),"Class diagram").doClick();menuItem(window.getJMenuBar(),"Object diagram").doClick();counts[0]=new org.tzi.use.gui.views.ObjectCountView(session.system());return null;});
            projector.applySnapshot(RuntimeVerificationFixtures.snapshot("discovered",0));
            onEdt(()->{
                var cls=session.system().model().getClass("LiveRuntimePropertyArtifact");
                var tree=components(window.getModelBrowser(),JTree.class).getFirst();
                assertTrue(java.util.Collections.list(((DefaultMutableTreeNode)tree.getModel().getRoot()).depthFirstEnumeration()).stream()
                    .anyMatch(n->((DefaultMutableTreeNode)n).getUserObject()==cls));
                assertTrue(window.getClassDiagrams().getFirst().getClassDiagram().isVisible(cls));
                assertTrue(window.getObjectDiagrams().getFirst().getDiagram().getVisibleData().fObjectToNodeMap.keySet().containsAll(session.system().state().allObjects()));
                return null;
            });
            var later=new java.util.LinkedHashMap<>(RuntimeVerificationFixtures.property("late"));
            later.put("semanticId",RuntimeVerificationFixtures.PROPERTY+"-late");later.put("propertyId","late");later.put("name","later");
            projector.apply(RuntimeVerificationFixtures.event("late-attribute",1,org.jacamo.bridge.contract.RuntimeEventKind.CHANGED,RuntimeVerificationFixtures.ID,
                java.util.Map.of("normalizedEventKind","APPLY_CARTAGO_PROPERTY_DELTA","semanticId",RuntimeVerificationFixtures.ARTIFACT,"properties",java.util.List.of(later),"removedPropertySemanticIds",java.util.List.of())));
            onEdt(()->{assertNotNull(session.system().model().getClass("LiveRuntimePropertyArtifact").attribute("later",false));
                var node=window.getObjectDiagrams().getFirst().getDiagram().getVisibleData().fObjectToNodeMap.get(session.system().state().objectByName("box"));
                node.updateContent();
                var values=objectNodeValues(node);
                assertTrue(java.util.Arrays.stream(values).anyMatch(v->v.contains("later")&&v.contains("late")));
                window.getClassDiagrams().getFirst().getClassDiagram().paint(new java.awt.image.BufferedImage(1000,800,java.awt.image.BufferedImage.TYPE_INT_RGB).createGraphics());return null;});
            String descriptorId="cartago:operation:"+RuntimeVerificationFixtures.ARTIFACT+":set/1";
            var descriptor=new org.jacamo.bridge.contract.semantic.CartagoSemanticContract.OperationDescriptorSemantic(
                operationMetadata(descriptorId,"CARTAGO_OPERATION_DESCRIPTOR"),RuntimeVerificationFixtures.ARTIFACT,"set/1","set",1,false,false,false,false);
            var backing=new org.jacamo.bridge.contract.semantic.CartagoSemanticContract.BackingJavaOperationSemantic(
                operationMetadata(descriptorId+":backing","CARTAGO_BACKING_JAVA_OPERATION"),descriptorId,LiveRuntimePropertyArtifact.class.getName(),"set",
                List.of("java.lang.String"),"void",false,LiveRuntimePropertyArtifact.class.getMethod("set",String.class).toGenericString());
            assertTrue(projector.apply(RuntimeVerificationFixtures.event("late-operation",2,org.jacamo.bridge.contract.RuntimeEventKind.CHANGED,
                org.jacamo.bridge.contract.RuntimeFactKind.OPERATION,
                new org.jacamo.bridge.contract.BridgeEntityId("cartago","environment","operation-descriptor",RuntimeVerificationFixtures.ARTIFACT,"set/1","snapshot"),
                java.util.Map.of("normalizedEventKind","UPSERT_CARTAGO_OPERATION","semanticId",descriptorId,
                    "operationDescriptor",org.jacamo.bridge.contract.semantic.SemanticContractCodec.operationToTree(descriptor),
                    "backingOperation",org.jacamo.bridge.contract.semantic.SemanticContractCodec.backingOperationToTree(backing)))));
            onEdt(()->{
                var cls=session.system().model().getClass("LiveRuntimePropertyArtifact");
                var operation=cls.operations().getFirst();
                var diagramData=(org.tzi.use.gui.views.diagrams.classdiagram.ClassDiagramData)
                    window.getClassDiagrams().getFirst().getClassDiagram().getVisibleData();
                var node=diagramData.fClassToNodeMap.get(cls);
                var signatures=node.getClass().getDeclaredField("fOprSignatures"); signatures.setAccessible(true);
                assertTrue(java.util.Arrays.asList((String[])signatures.get(node)).contains(operation.signature()),
                    "The already-open native class node must show the newly observed operation");
                var tree=components(window.getModelBrowser(),JTree.class).getFirst();
                var classNode=java.util.Collections.list(((DefaultMutableTreeNode)tree.getModel().getRoot()).depthFirstEnumeration()).stream()
                    .map(DefaultMutableTreeNode.class::cast).filter(n->n.getUserObject()==cls).findFirst().orElseThrow();
                tree.setSelectionPath(new javax.swing.tree.TreePath(classNode.getPath()));
                assertTrue(components(window.getModelBrowser(),javax.swing.JEditorPane.class).getFirst().getText().contains(operation.name()));
                assertEquals(1,cls.operations().size()); return null;
            });
            assertSame(pipeline.state().system(),session.system());
        } finally {
            projector.close();onEdt(()->{if(counts[0]!=null)counts[0].detachModel();window.getObjectDiagrams().forEach(v->v.detachModel());window.getClassDiagrams().forEach(v->v.detachModel());window.dispose();MainWindow.setJavaFxCall(false);return null;});
        }
    }
    @Test void atomicRuntimeCheckpointRefreshesObjectDiagramAndUsesPrecomputedInvariantResults() throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless());
        String oldHeight = System.getProperty("use.gui.view.classdiagram.class.minheight");
        String oldWidth = System.getProperty("use.gui.view.classdiagram.class.minwidth");
        System.setProperty("use.gui.view.classdiagram.class.minheight", "40");
        System.setProperty("use.gui.view.classdiagram.class.minwidth", "140");
        var pipeline = CodeGroundedTestFixtures.guiPipeline();
        var projector = new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector(pipeline,
                RuntimeVerificationFixtures.SESSION, 1, RuntimeVerificationFixtures.REVISION);
        projector.applySnapshot(RuntimeVerificationFixtures.snapshot("gui-baseline", 0));
        Session session = new Session(); MainWindow.setJavaFxCall(true);
        MainWindow window = onEdt(() -> MainWindow.create(session, PluginRuntime.getInstance()));
        org.tzi.use.gui.views.ClassInvariantView[] invariantView = new org.tzi.use.gui.views.ClassInvariantView[1];
        try {
            new NativeUseSessionActivator().activate(session, pipeline); flushEdt();
            onEdt(() -> { menuItem(window.getJMenuBar(), "Object diagram").doClick();
                invariantView[0] = new org.tzi.use.gui.views.ClassInvariantView(window, session.system()); return null; });
            projector.coordinator().loadProfileSource("gui.ocl", RuntimeVerificationCoordinatorTest.PROFILE);
            var diagram = onEdt(() -> window.getObjectDiagrams().getFirst());
            long checks = projector.coordinator().verificationCount();
            projector.apply(RuntimeVerificationFixtures.delta("gui-B", 1, "B"));
            onEdt(() -> {
                assertSame(session.system(), diagram.system());
                assertTrue(session.system().state().allObjects().stream().allMatch(diagram.getDiagram().getVisibleData().fObjectToNodeMap::containsKey));
                assertTrue(session.system().state().allLinks().stream().allMatch(diagram.getDiagram().getVisibleData()::containsLink));
                var table = components(invariantView[0], javax.swing.JTable.class).getFirst();
                int row = java.util.stream.IntStream.range(0, table.getRowCount()).filter(index -> table.getValueAt(index, 0).toString()
                        .contains("LiveRuntimePropertyArtifact::NoB")).findFirst().orElseThrow();
                assertTrue(table.getValueAt(row, 1).toString().contains("false")); return null;
            });
            assertEquals(checks + 1, projector.coordinator().verificationCount());
            var originalObjects = java.util.Set.copyOf(session.system().state().allObjects());
            var brokenProperty = new java.util.LinkedHashMap<>(RuntimeVerificationFixtures.property("A"));
            brokenProperty.put("valueTypes", java.util.List.of());
            assertThrows(RuntimeException.class, () -> projector.apply(RuntimeVerificationFixtures.event("gui-rejected", 2,
                    org.jacamo.bridge.contract.RuntimeEventKind.CHANGED, RuntimeVerificationFixtures.ID,
                    java.util.Map.of("normalizedEventKind", "APPLY_CARTAGO_PROPERTY_DELTA", "semanticId", RuntimeVerificationFixtures.ARTIFACT,
                            "removedPropertySemanticIds", java.util.List.of(RuntimeVerificationFixtures.PROPERTY), "properties", java.util.List.of(brokenProperty)))));
            onEdt(() -> { assertTrue(originalObjects.stream().allMatch(object -> session.system().state().objectByName(object.name()) == object));
                assertEquals(originalObjects, diagram.getDiagram().getVisibleData().fObjectToNodeMap.keySet()); return null; });
            projector.applySnapshot(RuntimeVerificationFixtures.snapshot("gui-resync", 1));
            projector.apply(RuntimeVerificationFixtures.event("gui-dispose", 2, org.jacamo.bridge.contract.RuntimeEventKind.DISPOSED,
                    RuntimeVerificationFixtures.ID, java.util.Map.of("normalizedEventKind", "DELETE_CARTAGO_ARTIFACT",
                            "semanticId", RuntimeVerificationFixtures.ARTIFACT)));
            onEdt(() -> { assertEquals(session.system().state().numObjects(), diagram.getDiagram().getVisibleData().fObjectToNodeMap.size()); return null; });
        } finally {
            onEdt(() -> { if (invariantView[0] != null) invariantView[0].detachModel();
                window.getObjectDiagrams().forEach(view -> view.detachModel()); window.dispose(); MainWindow.setJavaFxCall(false);
                restoreProperty("use.gui.view.classdiagram.class.minheight", oldHeight);
                restoreProperty("use.gui.view.classdiagram.class.minwidth", oldWidth); return null; });
        }
    }
    @Test
    void modelBrowserDiagramsOclAndEventBusUseTheActivatedNativeSystem() throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless(), "GUI closure evidence requires a real graphics environment");
        String oldClassDiagramMinHeight = System.getProperty("use.gui.view.classdiagram.class.minheight");
        String oldClassDiagramMinWidth = System.getProperty("use.gui.view.classdiagram.class.minwidth");
        System.setProperty("use.gui.view.classdiagram.class.minheight", "40");
        System.setProperty("use.gui.view.classdiagram.class.minwidth", "140");
        var pipeline = CodeGroundedTestFixtures.guiPipeline();
        Session session = new Session();
        MainWindow.setJavaFxCall(true);
        MainWindow window = null;
        JDialog oclDialog = null;
        try {
            window = onEdt(() -> MainWindow.create(session, PluginRuntime.getInstance()));
            new NativeUseSessionActivator().activate(session, pipeline);
            flushEdt();
            assertSame(pipeline.state().system(), session.system());

            MainWindow activeWindow = window;
            var browserElements = onEdt(() -> {
                JTree tree = components(activeWindow.getModelBrowser(), JTree.class).getFirst();
                DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
                return java.util.Collections.list(root.depthFirstEnumeration()).stream()
                        .map(value -> ((DefaultMutableTreeNode) value).getUserObject()).toList();
            });
            for (MClass cls : session.system().model().classes())
                assertTrue(browserElements.stream().anyMatch(value -> value == cls), "Model Browser: " + cls.name());

            onEdt(() -> {
                JMenuItem item = menuItem(activeWindow.getJMenuBar(), "Class diagram");
                item.getAction().actionPerformed(new ActionEvent(item, ActionEvent.ACTION_PERFORMED,
                        "Class diagram", ActionEvent.SHIFT_MASK));
                return null;
            });
            var classDiagram = onEdt(() -> activeWindow.getClassDiagrams().getFirst());
            assertSame(session.system(), classDiagram.system());
            for (MClass cls : session.system().model().classes())
                assertTrue(onEdt(() -> classDiagram.getClassDiagram().isVisible(cls)),
                        "Class Diagram: " + cls.name());

            onEdt(() -> {
                menuItem(activeWindow.getJMenuBar(), "Object diagram").doClick();
                return null;
            });
            var objectDiagram = onEdt(() -> activeWindow.getObjectDiagrams().getFirst());
            assertSame(session.system(), objectDiagram.system());
            assertTrue(onEdt(() -> session.system().state().allLinks().stream()
                    .allMatch(objectDiagram.getDiagram().getVisibleData()::containsLink)));

            JMenuItem saveScript = onEdt(() -> findMenuItem(activeWindow.getJMenuBar(), "Save script (.soil)..."));
            assertFalse(onEdt(saveScript::isEnabled));
            GuiMutation mutation = onEdt(() -> createGuiMutation(session));
            assertTrue(onEdt(() -> objectDiagram.getDiagram().getVisibleData().fObjectToNodeMap
                    .keySet().containsAll(List.of(mutation.left(), mutation.right()))),
                    "Object Diagram must receive ObjectCreatedEvent");
            assertTrue(onEdt(() -> objectDiagram.getDiagram().getVisibleData().containsLink(mutation.link())),
                    "Object Diagram must receive LinkInsertedEvent");
            assertTrue(onEdt(saveScript::isEnabled), "MainWindow must receive StatementExecutedEvent");

            onEdt(() -> {
                menuItem(activeWindow.getJMenuBar(), "Evaluate OCL expression...").doClick();
                return null;
            });
            oclDialog = onEdt(() -> Arrays.stream(Window.getWindows())
                    .filter(JDialog.class::isInstance).map(JDialog.class::cast)
                    .filter(value -> value.isDisplayable() && value.getTitle().equals("Evaluate OCL expression"))
                    .findFirst().orElseThrow());
            JDialog activeDialog = oclDialog;
            JTextArea input = components(activeDialog, JTextArea.class).stream()
                    .filter(JTextArea::isEditable).findFirst().orElseThrow();
            JTextArea output = components(activeDialog, JTextArea.class).stream()
                    .filter(value -> !value.isEditable()).findFirst().orElseThrow();
            onEdt(() -> {
                input.setText(mutation.queryClass().name() + ".allInstances->size() > 0");
                button(activeDialog, "Evaluate").doClick();
                return null;
            });
            assertTrue(onEdt(output::getText).startsWith("true"));
            assertSame(session.system(), pipeline.state().system());
        } finally {
            JDialog dialogToClose = oclDialog;
            MainWindow windowToClose = window;
            onEdt(() -> {
                if (dialogToClose != null && dialogToClose.isDisplayable()) {
                    JButton close = button(dialogToClose, "Close");
                    close.doClick();
                }
                if (windowToClose != null) {
                    windowToClose.getObjectDiagrams().forEach(value -> value.detachModel());
                    windowToClose.getClassDiagrams().forEach(value -> value.detachModel());
                    windowToClose.dispose();
                }
                MainWindow.setJavaFxCall(false);
                restoreProperty("use.gui.view.classdiagram.class.minheight", oldClassDiagramMinHeight);
                restoreProperty("use.gui.view.classdiagram.class.minwidth", oldClassDiagramMinWidth);
                return null;
            });
        }
    }

    private static GuiMutation createGuiMutation(Session session) throws Exception {
        var constrainedClasses = session.system().model().classInvariants().stream()
                .map(value -> value.cls()).collect(java.util.stream.Collectors.toSet());
        MAssociation association = session.system().model().associations().stream()
                .filter(value -> value.associationEnds().size() == 2)
                .filter(value -> value.associationEnds().stream().allMatch(end -> !end.cls().isAbstract()
                        && !constrainedClasses.contains(end.cls()) && !end.hasQualifiers()))
                .findFirst().orElseThrow(() -> new AssertionError("No simple concrete GUI association"));
        MClass leftClass = association.associationEnds().get(0).cls();
        MClass rightClass = association.associationEnds().get(1).cls();
        UseSystemApi api = UseSystemApi.create(session);
        MObject left = api.createObject(leftClass.name(), "gui_event_left");
        MObject right = api.createObject(rightClass.name(), "gui_event_right");
        MLink link = api.createLink(association.name(), left.name(), right.name());
        return new GuiMutation(left, right, link, leftClass);
    }

    private record GuiMutation(MObject left, MObject right, MLink link, MClass queryClass) { }

    private static org.jacamo.bridge.contract.semantic.SemanticMetadata operationMetadata(String id,String kind) {
        return new org.jacamo.bridge.contract.semantic.SemanticMetadata(id,kind,LiveRuntimePropertyArtifact.class.getName(),
            org.jacamo.bridge.contract.semantic.EvidenceAuthority.OFFICIAL_CARTAGO_API,org.jacamo.bridge.contract.semantic.Fidelity.EXACT,
            org.jacamo.bridge.contract.CapabilityStatus.COMPLETE,List.of(),List.of());
    }

    private static String[] objectNodeValues(org.tzi.use.gui.views.diagrams.objectdiagram.ObjectNode node) throws Exception {
        var field=node.getClass().getDeclaredField("fValues");field.setAccessible(true);return (String[])field.get(node);
    }

    private static void captureDiagram(org.tzi.use.gui.views.diagrams.DiagramView diagram,java.nio.file.Path path) throws Exception {
        diagram.stopLayoutThread();
        var graphics=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB).createGraphics();
        graphics.setFont(diagram.getFont());
        var nodes=diagram.getVisibleData().getNodes().stream().sorted(java.util.Comparator.comparing(n->n.name())).toList();
        double width=0,height=0;
        for(var node:nodes){node.setSizeIsCalculated(false);node.calculateSize(graphics);width=Math.max(width,node.getWidth());height=Math.max(height,node.getHeight());}
        graphics.dispose();width+=180;height+=130;
        int columns=3,rows=(nodes.size()+columns-1)/columns;
        for(int index=0;index<nodes.size();index++) nodes.get(index).setPosition(90+(index%columns)*width,65+(index/columns)*height);
        int canvasWidth=(int)(columns*width+100),canvasHeight=(int)(rows*height+100);
        diagram.setSize(canvasWidth,canvasHeight);
        var pixels=new java.awt.image.BufferedImage(canvasWidth,canvasHeight,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var paint=pixels.createGraphics();diagram.paint(paint);paint.dispose();
        javax.imageio.ImageIO.write(pixels,"png",path.toFile());
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) System.clearProperty(name);
        else System.setProperty(name, previousValue);
    }

    private static JMenuItem menuItem(JMenuBar bar, String text) {
        JMenuItem item = findMenuItem(bar, text);
        assertTrue(item.isEnabled(), "GUI menu item disabled: " + text);
        return item;
    }

    private static JMenuItem findMenuItem(JMenuBar bar, String text) {
        for (int index = 0; index < bar.getMenuCount(); index++) {
            JMenuItem found = findMenuItem(bar.getMenu(index), text);
            if (found != null) return found;
        }
        throw new AssertionError("GUI menu item missing: " + text);
    }

    private static JMenuItem findMenuItem(JMenu menu, String text) {
        if (menu == null) return null;
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JMenu child) {
                JMenuItem found = findMenuItem(child, text);
                if (found != null) return found;
            } else if (component instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        return null;
    }

    private static JButton button(Container root, String text) {
        return components(root, JButton.class).stream().filter(value -> text.equals(value.getText()))
                .findFirst().orElseThrow(() -> new AssertionError("GUI button missing: " + text));
    }

    private static <T extends Component> List<T> components(Component root, Class<T> type) {
        List<T> values = new ArrayList<>();
        if (type.isInstance(root)) values.add(type.cast(root));
        if (root instanceof Container container)
            for (Component child : container.getComponents()) values.addAll(components(child, type));
        return values;
    }

    private static void flushEdt() throws Exception { onEdt(() -> null); }

    private static <T> T onEdt(Callable<T> operation) throws Exception {
        assertNotNull(operation);
        if (SwingUtilities.isEventDispatchThread()) return operation.call();
        FutureTask<T> task = new FutureTask<>(operation);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
