package org.tzi.use.plugins.jacamo.ui.use4goal.aol.view;

import java.awt.Dimension;
import java.awt.Font;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JPopupMenu;

import com.ximpleware.AutoPilot;
import com.ximpleware.NavException;
import com.ximpleware.XPathEvalException;
import com.ximpleware.XPathParseException;
import org.tzi.use.gui.util.PersistHelper;
import org.tzi.use.gui.views.diagrams.DiagramGraph;
import org.tzi.use.gui.views.diagrams.DiagramView;
import org.tzi.use.gui.views.diagrams.elements.PlaceableNode;
import org.tzi.use.gui.views.diagrams.elements.edges.EdgeBase;
import org.tzi.use.gui.views.diagrams.event.ActionLoadLayout;
import org.tzi.use.gui.views.diagrams.event.ActionSaveLayout;
import org.tzi.use.gui.views.diagrams.event.DiagramInputHandling;
import org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm.AolModel;
import org.tzi.use.plugins.jacamo.ui.use4goal.state.AcceptedAolState;
import org.w3c.dom.Element;

@SuppressWarnings("serial")
public final class AolDiagram extends DiagramView {
    private final Map<String, AolDiagramNode> nodeMap = new LinkedHashMap<>();
    private final DiagramInputHandling inputHandling;
    private AolModel model;
    private AcceptedAolState state;
    private Path sourceFile;
    private String switchActionLabel;
    private Runnable switchAction;

    public AolDiagram(PrintWriter log) {
        super(new AolDiagramOptions(), log);
        inputHandling = new DiagramInputHandling(fNodeSelection, fEdgeSelection, this);
        addMouseListener(inputHandling);
        addKeyListener(inputHandling);
        setFocusable(true);
        fActionSaveLayout = new ActionSaveLayout("USE state layout", "dlt", this);
        fActionLoadLayout = new ActionLoadLayout("USE state layout", "dlt", this);
        getOptions().setDoAntiAliasing(true);
    }

    @Override
    public AolDiagramOptions getOptions() {
        return (AolDiagramOptions) fOpt;
    }

    public void setSwitchAction(String label, Runnable action) {
        this.switchActionLabel = label;
        this.switchAction = action;
    }

    public void setSourceFile(Path sourceFile) {
        this.sourceFile = sourceFile;
        if (sourceFile != null) {
            fOpt = new AolDiagramOptions(sourceFile);
            fActionSaveLayout = new ActionSaveLayout("USE state layout", "dlt", this);
            fActionLoadLayout = new ActionLoadLayout("USE state layout", "dlt", this);
            getOptions().setDoAntiAliasing(true);
        }
    }

    public void setModel(AolModel model) {
        this.model = model;
        this.state = null;
        rebuild();
    }

    public void setState(AcceptedAolState state) {
        this.model = null;
        this.state = state;
        rebuild();
    }

    private void rebuild() {
        nodeMap.clear();
        fGraph = new DiagramGraph();
        if (model == null && state == null) {
            repaint();
            return;
        }
        AolLayout layout = state == null ? AolLayoutBuilder.build(model) : AolLayoutBuilder.build(state);
        setPreferredSize(new Dimension(layout.width, layout.height));
        Font font = Font.getFont("use.gui.view.objectdiagram", getFont());
        for (AolNode item : layout.nodes.values()) {
            AolDiagramNode node = new AolDiagramNode(item, getOptions(), font);
            node.setPosition(item.x, item.y);
            node.setExactBounds(item.w, item.h);
            nodeMap.put(item.id, node);
            fGraph.add(node);
        }
        for (AolEdge item : layout.edges) {
            PlaceableNode source = nodeMap.get(item.fromId());
            PlaceableNode target = nodeMap.get(item.toId());
            if (source != null && target != null) {
                fGraph.addEdge(new AolDiagramEdge(item, source, target, getOptions(),nodeMap.values()));
            }
        }
        initialize();
        invalidateContent(true);
        revalidate();
        repaint();
    }

    @Override
    protected PopupMenuInfo unionOfPopUpMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        PopupMenuInfo popupInfo = new PopupMenuInfo(popupMenu);
        popupMenu.add(getMenuItemCommentNode(popupInfo));
        popupMenu.addSeparator();
        popupMenu.add(getMenuAlign());
        popupMenu.add(getMenuItemAntiAliasing());
        popupMenu.add(getMenuItemShowGrid());
        popupMenu.add(getMenuItemGrayscale());
        javax.swing.JCheckBoxMenuItem assocNamesItem = new javax.swing.JCheckBoxMenuItem(
                "Assoc. names", getOptions().isShowAssociationNames());
        assocNamesItem.addActionListener(e -> {
            getOptions().setShowAssociationNames(assocNamesItem.isSelected());
            repaint();
        });
        popupMenu.add(assocNamesItem);
        if (switchAction != null && switchActionLabel != null) {
            popupMenu.addSeparator();
            popupMenu.add(new AbstractAction(switchActionLabel) {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    switchAction.run();
                }
            });
        }
        addLayoutMenuItems(popupMenu);
        return popupInfo;
    }

    @Override
    public Set<? extends PlaceableNode> getHiddenNodes() {
        return Collections.emptySet();
    }

    @Override
    public void storePlacementInfos(PersistHelper helper, Element rootElement) {
        for (AolDiagramNode node : nodeMap.values()) {
            node.storePlacementInfo(helper, rootElement, false);
        }
        for (EdgeBase edge : fGraph.getEdges()) {
            edge.storePlacementInfo(helper, rootElement, false);
        }
    }

    @Override
    public void restorePlacementInfos(PersistHelper helper, int version) {
        helper.getNav().push();
        AutoPilot ap = new AutoPilot(helper.getNav());
        try {
            ap.selectXPath("./node");
            while (ap.evalXPath() != -1) {
                String name = helper.getElementStringValue("name");
                AolDiagramNode node = nodeMap.get(name);
                if (node != null) {
                    node.restorePlacementInfo(helper, version);
                    invalidateNode(node);
                }
            }
        } catch (XPathParseException | XPathEvalException | NavException ex) {
            fLog.append(ex.getMessage()).append('\n');
        } finally {
            ap.resetXPath();
            helper.getNav().pop();
        }
    }

    @Override
    public void showAll() {}

    @Override
    public void hideAll() {
        throw new UnsupportedOperationException("The state diagram does not support hiding all nodes.");
    }

    @Override
    public DiagramData getVisibleData() {
        return new DiagramData() {
            @Override public Set<PlaceableNode> getNodes() { return new java.util.LinkedHashSet<>(fGraph.getNodes()); }
            @Override public Set<EdgeBase> getEdges() { return Collections.unmodifiableSet(fGraph.getEdges()); }
            @Override public boolean hasNodes() { return !fGraph.isEmpty(); }
        };
    }

    @Override
    public DiagramData getHiddenData() {
        return new DiagramData() {
            @Override public Set<PlaceableNode> getNodes() { return Collections.emptySet(); }
            @Override public Set<EdgeBase> getEdges() { return Collections.emptySet(); }
            @Override public boolean hasNodes() { return false; }
        };
    }

    @Override
    public void resetLayout() {
        if (state == null) setModel(model);
        else setState(state);
    }

    @Override
    protected String getDefaultLayoutFileSuffix() {
        String name = sourceFile == null ? (model == null ? "aol" : model.name()) : sourceFile.getFileName().toString();
        return "_" + name + "_default.dlt";
    }
}
