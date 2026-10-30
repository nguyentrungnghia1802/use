package org.tzi.use.plugins.jacamo.ui.use4goal.csl;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.print.PageFormat;
import java.beans.PropertyVetoException;
import java.nio.file.Path;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.gui.main.ViewFrame;
import org.tzi.use.gui.views.PrintableView;
import org.tzi.use.gui.views.View;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.tzi.use.plugins.jacamo.ui.use4goal.gui.DiagramModelBrowser;

@SuppressWarnings("serial")
public final class CslView extends JPanel implements View, PrintableView {
    private final MainWindow mainWindow;
    private final Placement placement;
    private final CslDiagram diagram;
    private final JPanel diagramContent = new JPanel(new BorderLayout());
    private final JTextArea specArea = new JTextArea();
    private Path sourceFile;
    private CslModel model;

    private CslView(MainWindow mainWindow, Placement placement) {
        super(new BorderLayout());
        this.mainWindow = mainWindow;
        this.placement = placement;
        this.diagram = new CslDiagram(mainWindow == null ? new java.io.PrintWriter(java.io.Writer.nullWriter()) : mainWindow.logWriter());
        if (mainWindow != null) {
            diagram.setStatusBar(mainWindow.statusBar());
            diagram.setSwitchAction(placement == Placement.USE_DESKTOP ? "Open popup" : "Open in USE",
                    placement == Placement.USE_DESKTOP ? this::switchToPopupWindow : this::switchToUseDesktop);
        }
        specArea.setEditable(false);
        specArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JTabbedPane tabs = new JTabbedPane();
        diagramContent.add(new JScrollPane(diagram), BorderLayout.CENTER);
        tabs.addTab("Diagram", diagramContent);
        tabs.addTab("Specification", new JScrollPane(specArea));
        add(tabs, BorderLayout.CENTER);
    }

    public static CslView embedded(CslModel model,org.tzi.use.uml.mm.MModel nativeModel) {
        var view=new CslView(null,Placement.USE_DESKTOP);view.diagram.setNativeModel(nativeModel);view.setModel(model);return view;
    }
    public org.tzi.use.uml.mm.MModel nativeModel() {return diagram.nativeModel();}

    public static void openUseDesktop(MainWindow mainWindow, CslModel model, Path sourceFile, org.tzi.use.uml.mm.MModel nativeModel) {
        CslView view = new CslView(mainWindow, Placement.USE_DESKTOP);
        view.diagram.setNativeModel(nativeModel);
        view.setSourceFile(sourceFile);
        view.setModel(model);
        view.showInUseDesktop();
    }

    public static void openPopupWindow(MainWindow mainWindow, CslModel model, Path sourceFile, org.tzi.use.uml.mm.MModel nativeModel) {
        CslView view = new CslView(mainWindow, Placement.POPUP_WINDOW);
        view.diagram.setNativeModel(nativeModel);
        view.setSourceFile(sourceFile);
        view.setModel(model);
        view.showInPopupWindow();
    }

    public void setSourceFile(Path sourceFile) {
        this.sourceFile = sourceFile;
        diagram.setSourceFile(sourceFile);
    }

    public void setModel(CslModel model) {
        this.model = model;
        diagram.setModel(model);
        diagramContent.removeAll();
        if (model == null) {
            diagramContent.add(new JScrollPane(diagram), BorderLayout.CENTER);
        } else {
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    DiagramModelBrowser.forCsl(model,diagram.nativeModel()), new JScrollPane(diagram));
            split.setDividerLocation(280);
            split.setResizeWeight(0.23);
            split.setOneTouchExpandable(true);
            diagramContent.add(split, BorderLayout.CENTER);
            if(diagram.nativeModel()!=null&&diagram.nativeModel().classes().stream().anyMatch(c->!org.tzi.use.plugins.jacamo.semantics.mapping.CanonicalUseBindingAdapter.enactedRoles(c).isEmpty()))
                diagramContent.add(new javax.swing.JLabel("Dashed may enact = explicit mapping possibility. AOL shows actual enactment via the represented Agent."),BorderLayout.NORTH);
        }
        diagramContent.revalidate();
        diagramContent.repaint();
        specArea.setText(model == null ? "" : CslSpecText.render(model));
        specArea.setCaretPosition(0);
    }

    private String title() {
        String version = model == null ? "CSL" : "CSL " + model.version();
        return sourceFile == null ? version : version + " - " + sourceFile.getFileName();
    }

    private void showInUseDesktop() {
        ViewFrame frame = new ViewFrame(title(), this, "Diagram.gif");
        JComponent content = (JComponent) frame.getContentPane();
        content.setLayout(new BorderLayout());
        content.add(this, BorderLayout.CENTER);
        mainWindow.addNewViewFrame(frame);
        frame.setSize(1000, 700);
        try {
            frame.setSelected(true);
        } catch (PropertyVetoException ignored) {}
    }

    private void showInPopupWindow() {
        JFrame frame = new JFrame(title());
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setContentPane(this);
        frame.setSize(1000, 700);
        frame.setLocationRelativeTo(mainWindow);
        frame.setVisible(true);
    }

    private void switchToPopupWindow() {
        openPopupWindow(mainWindow, model, sourceFile, diagram.nativeModel());
        disposeOwner();
    }

    private void switchToUseDesktop() {
        openUseDesktop(mainWindow, model, sourceFile, diagram.nativeModel());
        disposeOwner();
    }

    private void disposeOwner() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (owner != null) owner.dispose();
    }

    @Override public void detachModel() { diagram.stopLayoutThread();setModel(null);diagram.setNativeModel(null); }

    public void update() { repaint(); }

    @Override public void printView(PageFormat pf) { diagram.printDiagram(pf, title()); }

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
