package org.tzi.use.plugins.jacamo.ui.use4goal.csl;


/**
 * =============================================================================
 * MODULE: CSL diagram rendering: CslDiagram
 * =============================================================================
 * 1. PURPOSE:
 *    Adapts the CSL semantic model for diagram presentation. Semantic nodes/links and display options are input; positioned graph elements, rendered figures, or specification text are output.
 *
 * 2. CORE MAPPING / LOGIC RULES:
 *    - Keep semantic identity separate from mutable screen geometry.
 *    - Derive edge/node kinds from model relationships, not visual state.
 *    - Apply deterministic layout ordering so repeated renders remain stable.
 *    - Main operations exposed by this file: getOptions(), setSwitchAction(), setSourceFile(), setModel(), rebuild(), unionOfPopUpMenu(), actionPerformed().
 *
 * 3. PIPELINE / WORKFLOW:
 *      1. CslView receives model
 *      2. CslLayoutBuilder builds graph/layout
 *      3. CslDiagram renders nodes and edges
 *      4. selection/options trigger repaint
 * =============================================================================
 */
import java.awt.Dimension;
import java.awt.Font;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
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
import org.tzi.use.gui.views.diagrams.elements.edges.BinaryAssociationOrLinkEdge;
import org.tzi.use.gui.views.diagrams.event.ActionLoadLayout;
import org.tzi.use.gui.views.diagrams.event.ActionSaveLayout;
import org.tzi.use.gui.views.diagrams.event.DiagramInputHandling;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.w3c.dom.Element;

@SuppressWarnings("serial")
public final class CslDiagram extends DiagramView {
    private final Map<String, PlaceableNode> nodeMap = new LinkedHashMap<>();
    private final DiagramInputHandling inputHandling;
    private CslModel model;
    private CslUseViewModel useViewModel;
    private org.tzi.use.uml.mm.MModel nativeModel;
    public void setNativeModel(org.tzi.use.uml.mm.MModel model) {nativeModel=model;}
    public org.tzi.use.uml.mm.MModel nativeModel() {return nativeModel;}
    private Path sourceFile;
    private String switchActionLabel;
    private Runnable switchAction;

    public CslDiagram(PrintWriter log) {
        super(new CslDiagramOptions(), log);
        inputHandling = new DiagramInputHandling(fNodeSelection, fEdgeSelection, this);
        addMouseListener(inputHandling);
        addKeyListener(inputHandling);
        setFocusable(true);
        fActionSaveLayout = new ActionSaveLayout("USE CSL layout", "dlt", this);
        fActionLoadLayout = new ActionLoadLayout("USE CSL layout", "dlt", this);
        getOptions().setDoAntiAliasing(true);
    }

    @Override
    public CslDiagramOptions getOptions() {
        return (CslDiagramOptions) fOpt;
    }

    public void setSwitchAction(String label, Runnable action) {
        this.switchActionLabel = label;
        this.switchAction = action;
    }

    public void setSourceFile(Path sourceFile) {
        this.sourceFile = sourceFile;
        if (sourceFile != null) {
            fOpt = new CslDiagramOptions(sourceFile);
            fActionSaveLayout = new ActionSaveLayout("USE CSL layout", "dlt", this);
            fActionLoadLayout = new ActionLoadLayout("USE CSL layout", "dlt", this);
            getOptions().setDoAntiAliasing(true);
        }
    }

    public void setModel(CslModel model) {
        this.model = model;
        rebuild();
    }/**
 * WHY: Diagram geometry is mutually dependent; rebuild computes the connected
 * positions together to avoid overlaps and unstable results from piecemeal updates.
 */


    private void rebuild() {
        nodeMap.clear();
        fGraph = new DiagramGraph();
        if (model == null) {
            repaint();
            return;
        }
        useViewModel = CslUseViewModel.build(model,nativeModel);
        var enactments=new java.util.TreeMap<String,java.util.Set<String>>();
        if(nativeModel!=null)for(var cls:nativeModel.classes()) {
            var roles=org.tzi.use.plugins.jacamo.semantics.mapping.CanonicalUseBindingAdapter.enactedRoles(cls);
            if(!roles.isEmpty())enactments.put(cls.name(),roles);
        }
        CslLayout layout = CslLayoutBuilder.build(model,enactments);
        setPreferredSize(new Dimension(layout.width, layout.height));
        Font font = Font.getFont("use.gui.view.objectdiagram", getFont());
        for (CslNode item : layout.nodes.values()) {
            PlaceableNode node;
            if (item.kind == CslNodeKind.ENTITY) {
                node = new CslEntityNode(useViewModel.classes.get(item.label), getOptions());
            } else {
                node = new CslDiagramNode(item, getOptions(), font);
            }
            node.setPosition(item.x, item.y);
            node.setExactBounds(item.w, item.h);
            nodeMap.put(item.id, node);
            fGraph.add(node);
        }
        for (CslEdge item : layout.edges) {
            PlaceableNode source = nodeMap.get(item.fromId());
            PlaceableNode target = nodeMap.get(item.toId());
            if (source != null && target != null) {
                var association = item.label() == null ? null : useViewModel.associations.get(item.label());
                if (association != null && (item.kind() == CslEdgeKind.ASSOCIATION
                        || item.kind() == CslEdgeKind.AGGREGATION
                        || item.kind() == CslEdgeKind.COMPOSITION)) {
                    var ends = association.associationEnds().iterator();
                    fGraph.addEdge(BinaryAssociationOrLinkEdge.create(source, target,
                            ends.next(), ends.next(), this, association));
                } else {
                    fGraph.addEdge(new CslDiagramEdge(item, source, target, getOptions()));
                }
            }
        }
        initialize();
        invalidateContent(true);
        revalidate();
        repaint();
    }

    @Override
    protected PopupMenuInfo unionOfPopUpMenu() {
        // Preserve the complete USE DiagramView popup (attributes, association names,
        // role names, alignment, grid, anti-aliasing, grayscale and every layout).
        // CSL contributes only its own navigation command.
        PopupMenuInfo popupInfo = super.unionOfPopUpMenu();
        JPopupMenu popupMenu = popupInfo.popupMenu;

        // Multiplicity is a ClassDiagram-specific addition in USE (not part of the
        // DiagramView base popup), but CSL uses the same binary association edge.
        JCheckBoxMenuItem multiplicities = new JCheckBoxMenuItem("Show multiplicities");
        multiplicities.setState(getOptions().isShowMutliplicities());
        multiplicities.addItemListener(event -> {
            getOptions().setShowMutliplicities(event.getStateChange() == java.awt.event.ItemEvent.SELECTED);
            invalidateContent(true);
        });
        popupMenu.insert(multiplicities, popupInfo.generalShowHideStart + 2);
        popupInfo.generalShowHideLength++;

        if (switchAction != null && switchActionLabel != null) {
            popupMenu.addSeparator();
            popupMenu.add(new AbstractAction(switchActionLabel) {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    switchAction.run();
                }
            });
        }
        return popupInfo;
    }

    @Override
    public Set<? extends PlaceableNode> getHiddenNodes() {
        return Collections.emptySet();
    }

    @Override
    public void storePlacementInfos(PersistHelper helper, Element rootElement) {
        for (PlaceableNode node : nodeMap.values()) {
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
                PlaceableNode node = nodeMap.get(name);
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
        throw new UnsupportedOperationException("CSL diagram does not support hiding all nodes.");
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
        setModel(model);
    }

    @Override
    protected String getDefaultLayoutFileSuffix() {
        String name = sourceFile == null ? (model == null ? "csl" : model.name()) : sourceFile.getFileName().toString();
        return "_" + name + "_default.dlt";
    }
}
