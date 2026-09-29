package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
