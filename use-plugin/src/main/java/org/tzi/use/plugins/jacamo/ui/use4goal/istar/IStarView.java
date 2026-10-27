package org.tzi.use.plugins.jacamo.ui.use4goal.istar;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.print.PageFormat;
import java.beans.PropertyVetoException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.gui.main.ViewFrame;
import org.tzi.use.gui.views.PrintableView;
import org.tzi.use.gui.views.View;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.tzi.use.plugins.jacamo.ui.use4goal.gui.DiagramModelBrowser;

@SuppressWarnings("serial")
public final class IStarView extends JPanel implements View, PrintableView {
    private final MainWindow mainWindow;
    private final IStarDiagram diagram;
    private final JPanel modelContent = new JPanel(new BorderLayout());
    private final Placement placement;
    private Path sourceFile;
    private GoalModel model;
    private Map<String, NodeBadge> badges = Collections.emptyMap();
    private Map<String, String> actorLabelOverrides = Collections.emptyMap();
    private Map<String, String> nodeLabelOverrides = Collections.emptyMap();
    private String title = "iStar";
    private String titleSuffix;

    public IStarView() {
        this(null, Placement.USE_DESKTOP);
    }

    private IStarView(MainWindow mainWindow, Placement placement) {
        super(new BorderLayout());
        this.mainWindow = mainWindow;
        this.placement = placement;
        this.diagram = new IStarDiagram(mainWindow == null ? new java.io.PrintWriter(java.io.Writer.nullWriter()) : mainWindow.logWriter());
        if (mainWindow != null) {
            diagram.setStatusBar(mainWindow.statusBar());
            diagram.setSwitchAction(placement == Placement.USE_DESKTOP ? "Open popup" : "Open in USE",
                    placement == Placement.USE_DESKTOP ? this::switchToPopupWindow : this::switchToUseDesktop);
        }
        modelContent.add(new JScrollPane(diagram), BorderLayout.CENTER);
        add(modelContent, BorderLayout.CENTER);
    }

    public static void openUseDesktop(MainWindow mainWindow, GoalModel model, Path sourceFile) {
        openUseDesktop(mainWindow, model, sourceFile, Collections.emptyMap());
    }

    public static void openUseDesktop(MainWindow mainWindow, GoalModel model, Path sourceFile,
                                      Map<String, NodeBadge> badges) {
        openUseDesktop(mainWindow, model, sourceFile, badges, null);
    }

    /** {@code titleSuffix} (e.g. an instance id) is appended to the window title, so multiple
     *  badge-annotated views of the same model — one per scenario instance — stay distinguishable. */
    public static void openUseDesktop(MainWindow mainWindow, GoalModel model, Path sourceFile,
                                      Map<String, NodeBadge> badges, String titleSuffix) {
        openUseDesktop(mainWindow, model, sourceFile, badges, titleSuffix, Collections.emptyMap());
    }

    /** {@code actorLabelOverrides} renames actor circles/boundaries (role id -> concrete instance name, e.g. "Participant" -> "amr"). */
    public static void openUseDesktop(MainWindow mainWindow, GoalModel model, Path sourceFile,
                                      Map<String, NodeBadge> badges, String titleSuffix,
                                      Map<String, String> actorLabelOverrides) {
        openUseDesktop(mainWindow, model, sourceFile, badges, titleSuffix, actorLabelOverrides, Collections.emptyMap());
    }

    public static void openUseDesktop(MainWindow mainWindow, GoalModel model, Path sourceFile,
                                      Map<String, NodeBadge> badges, String titleSuffix,
                                      Map<String, String> actorLabelOverrides,
                                      Map<String, String> nodeLabelOverrides) {
        IStarView view = new IStarView(mainWindow, Placement.USE_DESKTOP);
        view.setSourceFile(sourceFile);
        view.setTitleSuffix(titleSuffix);
        view.setModel(model);
        view.setNodeBadges(badges);
        view.setActorLabelOverrides(actorLabelOverrides);
        view.setNodeLabelOverrides(nodeLabelOverrides);
        view.showInUseDesktop();
    }

    public static void openPopupWindow(MainWindow mainWindow, GoalModel model, Path sourceFile) {
        openPopupWindow(mainWindow, model, sourceFile, Collections.emptyMap());
    }

    public static void openPopupWindow(MainWindow mainWindow, GoalModel model, Path sourceFile,
                                       Map<String, NodeBadge> badges) {
        openPopupWindow(mainWindow, model, sourceFile, badges, Collections.emptyMap(), Collections.emptyMap());
    }

    public static void openPopupWindow(MainWindow mainWindow, GoalModel model, Path sourceFile,
                                       Map<String, NodeBadge> badges,
                                       Map<String, String> actorLabelOverrides,
                                       Map<String, String> nodeLabelOverrides) {
        IStarView view = new IStarView(mainWindow, Placement.POPUP_WINDOW);
        view.setSourceFile(sourceFile);
        view.setModel(model);
        view.setNodeBadges(badges);
        view.setActorLabelOverrides(actorLabelOverrides);
        view.setNodeLabelOverrides(nodeLabelOverrides);
        view.showInPopupWindow();
    }

    public void setSourceFile(Path sourceFile) {
        this.sourceFile = sourceFile;
        rebuildTitle();
        diagram.setSourceFile(sourceFile);
    }

    public void setTitleSuffix(String titleSuffix) {
        this.titleSuffix = titleSuffix;
        rebuildTitle();
    }

    private void rebuildTitle() {
        String base = sourceFile == null ? "iStar" : "iStar - " + sourceFile.getFileName();
        this.title = (titleSuffix == null || titleSuffix.isEmpty()) ? base : base + " [" + titleSuffix + "]";
    }

    public void setModel(GoalModel model) {
        this.model = model;
        diagram.setModel(model);
        modelContent.removeAll();
        if (model == null) {
            modelContent.add(new JScrollPane(diagram), BorderLayout.CENTER);
        } else {
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    DiagramModelBrowser.forIStar(model), new JScrollPane(diagram));
            split.setDividerLocation(270);
            split.setResizeWeight(0.22);
            split.setOneTouchExpandable(true);
            modelContent.add(split, BorderLayout.CENTER);
        }
        modelContent.revalidate();
        modelContent.repaint();
    }

    public void setNodeBadges(Map<String, NodeBadge> badges) {
        this.badges = badges == null ? Collections.emptyMap() : badges;
        diagram.setNodeBadges(this.badges);
    }

    public void setActorLabelOverrides(Map<String, String> actorLabelOverrides) {
        this.actorLabelOverrides = actorLabelOverrides == null ? Collections.emptyMap() : actorLabelOverrides;
        diagram.setActorLabelOverrides(this.actorLabelOverrides);
    }

    public void setNodeLabelOverrides(Map<String, String> nodeLabelOverrides) {
        this.nodeLabelOverrides = nodeLabelOverrides == null ? Collections.emptyMap() : nodeLabelOverrides;
        diagram.setNodeLabelOverrides(this.nodeLabelOverrides);
    }

    private void switchToPopupWindow() {
        openPopupWindow(mainWindow, model, sourceFile, badges, actorLabelOverrides, nodeLabelOverrides);
        ViewFrame owner = (ViewFrame) SwingUtilities.getAncestorOfClass(ViewFrame.class, this);
        if (owner != null) owner.dispose();
    }

    private void switchToUseDesktop() {
        openUseDesktop(mainWindow, model, sourceFile, badges, titleSuffix, actorLabelOverrides, nodeLabelOverrides);
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (owner != null) owner.dispose();
    }

    private void showInUseDesktop() {
        ViewFrame frame = new ViewFrame(title, this, "Diagram.gif");
        frame.addInternalFrameListener(new InternalFrameAdapter() {
            @Override public void internalFrameActivated(InternalFrameEvent ev) {
                mainWindow.statusBar().showTmpMessage("Move iStar elements with the left mouse button; use right-click for options.");
            }
            @Override public void internalFrameDeactivated(InternalFrameEvent ev) {
                mainWindow.statusBar().clearMessage();
            }
        });
        JComponent content = (JComponent) frame.getContentPane();
        content.setLayout(new BorderLayout());
        content.add(this, BorderLayout.CENTER);
        mainWindow.addNewViewFrame(frame);
        frame.setSize(950, 650);
        try {
            frame.setSelected(true);
        } catch (PropertyVetoException ignored) {}
    }

    private void showInPopupWindow() {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setContentPane(this);
        frame.setSize(1000, 700);
        frame.setLocationRelativeTo(mainWindow);
        frame.setVisible(true);
    }

    @Override public void detachModel() { diagram.stopLayoutThread();setModel(null);setNodeBadges(Collections.emptyMap());setActorLabelOverrides(Collections.emptyMap());setNodeLabelOverrides(Collections.emptyMap()); }

    public void update() { repaint(); }

    @Override public void printView(PageFormat pf) { diagram.printDiagram(pf, title); }

    @Override public void export(Graphics2D g) {
        boolean oldDb = diagram.isDoubleBuffered();
        diagram.setDoubleBuffered(false);
        diagram.paint(g);
        diagram.setDoubleBuffered(oldDb);
    }

    @Override public float getContentHeight() { return diagram.getPreferredSize().height; }

    @Override public float getContentWidth() { return diagram.getPreferredSize().width; }

    private enum Placement {
        USE_DESKTOP,
        POPUP_WINDOW
    }
}
