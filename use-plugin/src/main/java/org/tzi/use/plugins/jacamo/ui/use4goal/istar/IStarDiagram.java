package org.tzi.use.plugins.jacamo.ui.use4goal.istar;

import java.awt.Font;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

import com.ximpleware.AutoPilot;
import com.ximpleware.NavException;
import com.ximpleware.XPathEvalException;
import com.ximpleware.XPathParseException;
import org.tzi.use.gui.util.PersistHelper;
import org.tzi.use.gui.views.diagrams.DiagramGraph;
import org.tzi.use.gui.views.diagrams.DiagramView;
import org.tzi.use.gui.views.diagrams.PositionChangedListener;
import org.tzi.use.gui.views.diagrams.elements.PlaceableNode;
import org.tzi.use.gui.views.diagrams.elements.edges.EdgeBase;
import org.tzi.use.gui.views.diagrams.event.ActionLoadLayout;
import org.tzi.use.gui.views.diagrams.event.ActionSaveLayout;
import org.tzi.use.gui.views.diagrams.event.DiagramInputHandling;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.w3c.dom.Element;

@SuppressWarnings("serial")
public final class IStarDiagram extends DiagramView {
    private final Map<String, IStarDiagramNode> nodeMap = new LinkedHashMap<>();
    private final DiagramInputHandling inputHandling;
    private GoalModel model;
    private Path sourceFile;
    private IStarViewMode mode = IStarViewMode.SR;
    private String switchActionLabel;
    private Runnable switchAction;
    private boolean syncingBoundary;
    private Map<String, NodeBadge> badges = Collections.emptyMap();
    private Map<String, String> actorLabelOverrides = Collections.emptyMap();
    private Map<String, String> nodeLabelOverrides = Collections.emptyMap();
    private boolean showQualities = true;
    private boolean showContributions = true;
    private boolean showDependencies = true;
    private boolean showRefinements = true;
    private boolean showOclContracts = false;

    public IStarDiagram(PrintWriter log) {
        super(new IStarDiagramOptions(), log);
        inputHandling = new DiagramInputHandling(fNodeSelection, fEdgeSelection, this);
        addMouseListener(inputHandling);
        addKeyListener(inputHandling);
        setFocusable(true);
        fActionSaveLayout = new ActionSaveLayout("USE iStar layout", "dlt", this);
        fActionLoadLayout = new ActionLoadLayout("USE iStar layout", "dlt", this);
        getOptions().setDoAntiAliasing(true);
    }

    @Override
    public IStarDiagramOptions getOptions() {
        return (IStarDiagramOptions) fOpt;
    }

    public void setSwitchAction(String label, Runnable action) {
        this.switchActionLabel = label;
        this.switchAction = action;
    }

    public void setSourceFile(Path sourceFile) {
        this.sourceFile = sourceFile;
        if (sourceFile != null) {
            fOpt = new IStarDiagramOptions(sourceFile);
            fActionSaveLayout = new ActionSaveLayout("USE iStar layout", "dlt", this);
            fActionLoadLayout = new ActionLoadLayout("USE iStar layout", "dlt", this);
            getOptions().setDoAntiAliasing(true);
        }
    }

    public void setModel(GoalModel model) {
        this.model = model;
        rebuild();
        if (model != null) {
            loadDefaultLayout();
            fitAllActorBoundaries();
        }
    }

    /** Optional per-node status overlay (e.g. an IStarScenario satisfaction marking). */
    public void setNodeBadges(Map<String, NodeBadge> badges) {
        this.badges = badges == null ? Collections.emptyMap() : badges;
        applyBadges();
        repaint();
    }

    private void applyBadges() {
        for (IStarDiagramNode node : nodeMap.values()) {
            node.setBadge(badges.get(node.node().id));
        }
    }

    /** Renames an actor's circle/boundary (e.g. role "Participant" -> concrete instance "amr"); keys are actor ids/names. */
    public void setActorLabelOverrides(Map<String, String> actorLabelOverrides) {
        this.actorLabelOverrides = actorLabelOverrides == null ? Collections.emptyMap() : actorLabelOverrides;
        applyLabelOverrides();
        repaint();
    }

    private void applyLabelOverrides() {
        for (IStarDiagramNode node : nodeMap.values()) {
            if (node.node().kind == IStarNodeKind.ACTOR) {
                node.setLabelOverride(actorLabelOverrides.get(node.node().id));
            } else {
                node.setLabelOverride(nodeLabelOverrides.get(node.node().id));
            }
        }
    }

    /** Overrides intentional-element labels in generated views, e.g. "Task [xing]". */
    public void setNodeLabelOverrides(Map<String, String> nodeLabelOverrides) {
        this.nodeLabelOverrides = nodeLabelOverrides == null ? Collections.emptyMap() : nodeLabelOverrides;
        applyLabelOverrides();
        repaint();
    }

    private void setMode(IStarViewMode mode) {
        this.mode = mode;
        rebuild();
        loadDefaultLayout();
        fitAllActorBoundaries();
    }

    private void rebuild() {
        nodeMap.clear();
        fGraph = new DiagramGraph();
        if (model == null) {
            repaint();
            return;
        }
        IStarLayout layout = IStarLayoutBuilder.build(model, mode, showOclContracts);
        setPreferredSize(new java.awt.Dimension(layout.width,layout.height));
        Font font = Font.getFont("use.gui.view.objectdiagram", getFont());
        for (IStarNode item : layout.nodes.values()) {
            if (!isNodeVisible(item)) continue;
            IStarDiagramNode node = new IStarDiagramNode(item, getOptions(), font);
            node.setPosition(item.x, item.y);
            node.setExactBounds(item.w, item.h);
            nodeMap.put(item.id, node);
            fGraph.add(node);
        }
        wireActorBoundaryDrag();
        wireChildBoundaryFit();
        for (IStarEdge item : layout.edges) {
            if (!isEdgeVisible(item)) continue;
            PlaceableNode source = nodeMap.get(item.fromId());
            PlaceableNode target = nodeMap.get(item.toId());
            if (source != null && target != null) {
                fGraph.addEdge(new IStarDiagramEdge(item, source, target, getOptions()));
            }
        }
        applyBadges();
        applyLabelOverrides();
        initialize();
        invalidateContent(true);
    }

    private boolean isNodeVisible(IStarNode node) {
        if (!showQualities && node.kind == IStarNodeKind.QUALITY) return false;
        return showDependencies || !node.dependencyMarker;
    }

    private boolean isEdgeVisible(IStarEdge edge) {
        if (!showDependencies && edge.kind() == IStarEdgeKind.DEPENDENCY) return false;
        if (!showContributions && edge.kind() == IStarEdgeKind.CONTRIBUTION) return false;
        if (!showRefinements && (edge.kind() == IStarEdgeKind.AND_REFINE || edge.kind() == IStarEdgeKind.OR_REFINE)) {
            return false;
        }
        return true;
    }

    private void wireActorBoundaryDrag() {
        for (IStarDiagramNode actorNode : nodeMap.values()) {
            if (!actorNode.node().actorBoundary) continue;
            actorNode.addPositionChangedListener(new PositionChangedListener() {
                @Override
                public void positionChanged(Object source, Point2D newPosition, double deltaX, double deltaY) {
                    if (!syncingBoundary) {
                        moveActorChildren(actorNode.node().id, deltaX, deltaY);
                    }
                }

                @Override
                public void boundsChanged(Object source, Rectangle2D oldBounds, Rectangle2D newBounds) {}
            });
        }
    }

    private void moveActorChildren(String actorId, double deltaX, double deltaY) {
        syncingBoundary = true;
        for (IStarDiagramNode child : nodeMap.values()) {
            if (!actorId.equals(child.node().actorId)) continue;
            child.setPosition(child.getX() + deltaX, child.getY() + deltaY);
            invalidateNode(child);
        }
        syncingBoundary = false;
    }

    private void wireChildBoundaryFit() {
        for (IStarDiagramNode childNode : nodeMap.values()) {
            if (childNode.node().actorId == null) continue;
            childNode.addPositionChangedListener(new PositionChangedListener() {
                @Override
                public void positionChanged(Object source, Point2D newPosition, double deltaX, double deltaY) {
                    if (!syncingBoundary) {
                        fitActorBoundary(childNode.node().actorId);
                    }
                }

                @Override
                public void boundsChanged(Object source, Rectangle2D oldBounds, Rectangle2D newBounds) {
                    if (!syncingBoundary) {
                        fitActorBoundary(childNode.node().actorId);
                    }
                }
            });
        }
    }

    private void fitAllActorBoundaries() {
        if (mode != IStarViewMode.SR) return;
        for (IStarDiagramNode node : nodeMap.values()) {
            if (node.node().actorBoundary) {
                fitActorBoundary(node.node().id);
            }
        }
    }

    private void fitActorBoundary(String actorId) {
        IStarDiagramNode actor = nodeMap.get(actorId);
        if (actor == null || !actor.node().actorBoundary) return;

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (IStarDiagramNode child : nodeMap.values()) {
            if (!actorId.equals(child.node().actorId)) continue;
            minX = Math.min(minX, child.getX());
            minY = Math.min(minY, child.getY());
            maxX = Math.max(maxX, child.getX() + child.getWidth());
            maxY = Math.max(maxY, child.getY() + child.getHeight());
        }
        if (!Double.isFinite(minX)) return;

        double pad = 28.0;
        double circleD = Math.max(IStarLayoutBuilder.ACTOR_D,
                (actor.node().label == null ? 0 : actor.node().label.length() * 8) + 24);
        double x = minX - pad;
        double y = Math.min(minY - pad, actor.getY());
        double w = Math.max(maxX - minX + pad * 2, circleD + pad);
        double h = Math.max(maxY - y + pad, IStarLayoutBuilder.ACTOR_D + pad);

        syncingBoundary = true;
        actor.setPosition(x, y);
        actor.setExactBounds(w, h);
        invalidateNode(actor);
        syncingBoundary = false;
    }

    @Override
    protected PopupMenuInfo unionOfPopUpMenu() {
        JPopupMenu menu = new JPopupMenu();
        PopupMenuInfo info = new PopupMenuInfo(menu);
        ButtonGroup group = new ButtonGroup();
        JRadioButtonMenuItem sr = new JRadioButtonMenuItem("SR view", mode == IStarViewMode.SR);
        sr.addActionListener(e -> setMode(IStarViewMode.SR));
        JRadioButtonMenuItem sd = new JRadioButtonMenuItem("SD view", mode == IStarViewMode.SD);
        sd.addActionListener(e -> setMode(IStarViewMode.SD));
        group.add(sr);
        group.add(sd);
        menu.add(sr);
        menu.add(sd);
        menu.addSeparator();
        menu.add(getMenuItemCommentNode(info));
        menu.addSeparator();
        menu.add(getMenuAlign());
        menu.add(getMenuItemAntiAliasing());
        menu.add(getMenuItemShowGrid());
        menu.add(getMenuItemGrayscale());
        menu.addSeparator();
        addVisibilityMenuItems(menu);
        menu.addSeparator();
        menu.add(new AbstractAction("Save default") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                Path layout = getDefaultLayoutFile();
                if (layout != null) {
                    saveLayout(layout);
                }
            }
        });
        menu.add(new AbstractAction("Load default") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                loadDefaultLayout();
                fitAllActorBoundaries();
            }
        });
        if (switchAction != null && switchActionLabel != null) {
            menu.addSeparator();
            menu.add(new AbstractAction(switchActionLabel) {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    switchAction.run();
                }
            });
        }
        menu.addSeparator();
        menu.add(new AbstractAction("Specification...") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                openSpecificationWindow();
            }
        });
        addLayoutMenuItems(menu);
        return info;
    }

    private void addVisibilityMenuItems(JPopupMenu menu) {
        JCheckBoxMenuItem qualities = new JCheckBoxMenuItem("Show softgoals", showQualities);
        qualities.addActionListener(e -> {
            showQualities = qualities.isSelected();
            rebuild();
            fitAllActorBoundaries();
        });
        menu.add(qualities);

        JCheckBoxMenuItem refinements = new JCheckBoxMenuItem("Show refinements", showRefinements);
        refinements.addActionListener(e -> {
            showRefinements = refinements.isSelected();
            rebuild();
            fitAllActorBoundaries();
        });
        menu.add(refinements);

        JCheckBoxMenuItem contributions = new JCheckBoxMenuItem("Show contributions", showContributions);
        contributions.addActionListener(e -> {
            showContributions = contributions.isSelected();
            rebuild();
            fitAllActorBoundaries();
        });
        menu.add(contributions);

        JCheckBoxMenuItem dependencies = new JCheckBoxMenuItem("Show dependencies", showDependencies);
        dependencies.addActionListener(e -> {
            showDependencies = dependencies.isSelected();
            rebuild();
            fitAllActorBoundaries();
        });
        menu.add(dependencies);

        JCheckBoxMenuItem ocl = new JCheckBoxMenuItem("Show OCL contracts", showOclContracts);
        ocl.addActionListener(e -> {
            showOclContracts = ocl.isSelected();
            rebuild();
            loadDefaultLayout();
            fitAllActorBoundaries();
        });
        menu.add(ocl);
    }

    /** Same content this diagram draws (model/badges/label overrides), as read-only text. */
    private void openSpecificationWindow() {
        String text = model == null ? "(no model loaded)"
                : IStarSpecText.generate(model, badges, actorLabelOverrides, nodeLabelOverrides);

        javax.swing.JTextArea area = new javax.swing.JTextArea(text);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        area.setEditable(false);
        area.setBackground(javax.swing.UIManager.getColor("TextArea.background"));
        area.setCaretPosition(0);

        javax.swing.JFrame frame = new javax.swing.JFrame(
                "Specification — " + (model == null ? "" : model.getName()));
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        frame.getContentPane().add(new javax.swing.JScrollPane(area), java.awt.BorderLayout.CENTER);
        frame.setSize(720, 560);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    @Override
    public Set<? extends PlaceableNode> getHiddenNodes() {
        return Collections.emptySet();
    }

    @Override
    public void storePlacementInfos(PersistHelper helper, Element rootElement) {
        for (IStarDiagramNode node : nodeMap.values()) {
            node.storePlacementInfo(helper, rootElement, false);
        }
        for (EdgeBase edge : fGraph.getEdges()) {
            edge.storePlacementInfo(helper, rootElement, false);
        }
    }

    @Override
    public void restorePlacementInfos(PersistHelper helper, int version) {
        syncingBoundary = true;
        restoreNodes(helper, version);
        syncingBoundary = false;
        fitAllActorBoundaries();
        restoreEdges(helper, version);
        invalidateContent(true);
    }

    private void restoreNodes(PersistHelper helper, int version) {
        helper.getNav().push();
        AutoPilot ap = new AutoPilot(helper.getNav());
        try {
            ap.selectXPath("./node");
            while (ap.evalXPath() != -1) {
                String name = helper.getElementStringValue("name");
                IStarDiagramNode node = nodeMap.get(name);
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

    private void restoreEdges(PersistHelper helper, int version) {
        Map<String, EdgeBase> edges = new LinkedHashMap<>();
        for (EdgeBase edge : fGraph.getEdges()) {
            edges.put(edge.getId(), edge);
        }

        helper.getNav().push();
        AutoPilot ap = new AutoPilot(helper.getNav());
        try {
            ap.selectXPath("./edge");
            while (ap.evalXPath() != -1) {
                String source = helper.getElementStringValue("source");
                String target = helper.getElementStringValue("target");
                String name = helper.getElementStringValue("name");
                EdgeBase edge = edges.get("istar::" + name.toUpperCase(Locale.ROOT) + "::" + source + "::" + target);
                if (edge != null) {
                    edge.restorePlacementInfo(helper, version);
                }
            }
        } catch (XPathParseException | XPathEvalException | NavException ex) {
            fLog.append(ex.getMessage()).append('\n');
        } finally {
            ap.resetXPath();
            helper.getNav().pop();
        }
    }

    @Override public void showAll() {
        showQualities = true;
        showContributions = true;
        showDependencies = true;
        showRefinements = true;
        showOclContracts = true;
        rebuild();
        fitAllActorBoundaries();
    }

    @Override public void hideAll() {
        showQualities = false;
        showContributions = false;
        showDependencies = false;
        showRefinements = false;
        showOclContracts = false;
        rebuild();
        fitAllActorBoundaries();
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

    @Override public void resetLayout() { rebuild(); }

    @Override
    protected String getDefaultLayoutFileSuffix() {
        String name = sourceFile == null ? "istar" : sourceFile.getFileName().toString();
        return "_" + name + "_" + mode.name().toLowerCase() + "_default.dlt";
    }
}
