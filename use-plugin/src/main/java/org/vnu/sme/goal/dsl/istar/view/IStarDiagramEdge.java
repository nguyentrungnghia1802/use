package org.vnu.sme.goal.dsl.istar.view;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import org.tzi.use.gui.views.diagrams.edges.DirectedEdgeFactory;
import org.tzi.use.gui.views.diagrams.edges.DirectedEdgeFactory.ArrowStyle;
import org.tzi.use.gui.views.diagrams.elements.PlaceableNode;
import org.tzi.use.gui.views.diagrams.elements.edges.EdgeBase;
import org.tzi.use.gui.views.diagrams.util.Direction;
import org.tzi.use.plugins.jacamo.gui.views.DiagramVisualStyle;

public final class IStarDiagramEdge extends EdgeBase {
    private static final Font EDGE_FONT = DiagramVisualStyle.EDGE_FONT;
    private final org.tzi.use.plugins.jacamo.gui.views.DiagramEdgeRouter router=new org.tzi.use.plugins.jacamo.gui.views.DiagramEdgeRouter();
    private java.util.Collection<? extends PlaceableNode> obstacles=java.util.List.of();
    public IStarDiagramEdge(IStarEdge edge,PlaceableNode source,PlaceableNode target,IStarDiagramOptions opt,java.util.Collection<? extends PlaceableNode> obstacles){this(edge,source,target,opt);this.obstacles=java.util.List.copyOf(obstacles);}
    private final IStarEdge edge;

    public IStarDiagramEdge(IStarEdge edge, PlaceableNode source, PlaceableNode target, IStarDiagramOptions opt) {
        super(source, target, edge.kind().name().toLowerCase(), opt, false);
        this.edge = edge;
    }

    @Override
    protected void onDraw(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(isSelected() ? fOpt.getEDGE_SELECTED_COLOR() : Color.BLACK);
        g2.setStroke(edge.kind() == IStarEdgeKind.QUALIFICATION
                ? DiagramVisualStyle.dashed() : DiagramVisualStyle.solid());
        if(edge.kind()==IStarEdgeKind.ASSOCIATION){
            // Actor associations belong to the actor headers. Connecting the
            // complete rationale rectangles would cut through owned Tasks.
            double sx=source().getX()+source().getWidth()/2,tx=target().getX()+target().getWidth()/2;
            double sy=actorTop(source()),ty=actorTop(target());
            double lane=Math.max(24,Math.min(sy,ty)-20-Math.abs(tx-sx)/40);
            var route=new java.awt.geom.Path2D.Double();route.moveTo(sx,sy);route.lineTo(sx,lane);route.lineTo(tx,lane);route.lineTo(tx,ty);g2.draw(route);
            DirectedEdgeFactory.drawArrow(g2,(int)tx,(int)lane,(int)tx,(int)ty,ArrowStyle.FILLED);
            paintLabel(g2,new Point2D.Double(sx,lane),new Point2D.Double(tx,lane));g2.dispose();return;
        }
        var anchors=new java.util.ArrayList<Point2D>();
        for(var waypoint:getWayPoints()){waypoint.draw(g2);anchors.add(waypoint.getCenter());}
        if (edge.kind() == IStarEdgeKind.DEPENDENCY && anchors.size() >= 2) {
            if (source() instanceof IStarDiagramNode node) {
                anchors.set(0, node.dependencyAnchor(anchors.get(1), anchors.getFirst()));
            }
            if (target() instanceof IStarDiagramNode node) {
                int last = anchors.size() - 1;
                anchors.set(last, node.dependencyAnchor(anchors.get(last - 1), anchors.getLast()));
            }
        }
        var points=router.route(anchors,source(),target(),obstacles);
        Line2D longest = null;
        Line2D besideDependum = null;
        for(int i=1;i<points.size();i++){
            var a=points.get(i-1);var b=points.get(i);
            var segment = new Line2D.Double(a, b);
            if (longest == null || a.distanceSq(b) > longest.getP1().distanceSq(longest.getP2())) longest = segment;
            if (edge.kind() == IStarEdgeKind.DEPENDENCY && a.distance(b) >= 24
                    && (i == 1 && isDependum(source()) || i == points.size() - 1 && isDependum(target()))) {
                besideDependum = segment;
            }
            if(i==points.size()-1)drawEnd(g2,a,b);else g2.draw(segment);
        }
        Line2D labelSegment = besideDependum == null ? longest : besideDependum;
        if (labelSegment != null) paintLabel(g2, labelSegment.getP1(), labelSegment.getP2());
        g2.dispose();
    }

    private static boolean isDependum(PlaceableNode node) {
        return node instanceof IStarDiagramNode item && item.node().dependum;
    }

    private static double actorTop(PlaceableNode node){
        if(node instanceof IStarDiagramNode actor)return actor.actorCircle().getMinY();
        return node.getY();
    }

    @Override
    public boolean isLink() {
        return false;
    }

    @Override
    protected String getIdInternal() {
        return "istar::" + edge.kind().name() + "::" + edge.fromId() + "::" + edge.toId();
    }

    @Override
    protected String getStoreType() {
        return "IStarEdge";
    }

    @Override
    public Direction getReflexivePosition() {
        return reflexivePosition == null ? Direction.NORTH_EAST : reflexivePosition;
    }

    private void drawEnd(Graphics2D g, Point2D p1, Point2D p2) {
        int x1 = (int) Math.round(p1.getX()), y1 = (int) Math.round(p1.getY());
        int x2 = (int) Math.round(p2.getX()), y2 = (int) Math.round(p2.getY());
        g.drawLine(x1, y1, x2, y2);
        switch (edge.kind()) {
            case AND_REFINE -> paintAndBar(g, p1, p2);
            case OR_REFINE -> DirectedEdgeFactory.drawArrow(g, x1, y1, x2, y2, ArrowStyle.FILLED);
            case CONTRIBUTION -> DirectedEdgeFactory.drawArrow(g, x1, y1, x2, y2, ArrowStyle.OPEN);
            case DEPENDENCY -> { }
            case NEEDED_BY -> {
                g.setColor(Color.WHITE);
                g.fillOval(x2 - 5, y2 - 5, 10, 10);
                g.setColor(Color.BLACK);
                g.drawOval(x2 - 5, y2 - 5, 10, 10);
            }
            default -> DirectedEdgeFactory.drawArrow(g, x1, y1, x2, y2, ArrowStyle.FILLED);
        }
    }

    private static void paintAndBar(Graphics2D g, Point2D from, Point2D parent) {
        double length = from.distance(parent);
        if (length == 0) return;
        double dx = (parent.getX() - from.getX()) / length;
        double dy = (parent.getY() - from.getY()) / length;
        double offset = Math.min(5, length / 2);
        double x = parent.getX() - offset * dx, y = parent.getY() - offset * dy;
        g.draw(new Line2D.Double(x - 5 * dy, y + 5 * dx, x + 5 * dy, y - 5 * dx));
    }

    private void paintLabel(Graphics2D g, Point2D first, Point2D last) {
        String label = edge.label();
        int x = (int) Math.round((first.getX() + last.getX()) / 2.0);
        int y = (int) Math.round((first.getY() + last.getY()) / 2.0);
        if (edge.kind() == IStarEdgeKind.DEPENDENCY) {
            paintDependencyMark(g, x, y);
            return;
        }
        if (label == null) return;
        g.setFont(EDGE_FONT);
        g.drawString(label, x + 3, y - 4);
    }

    private static void paintDependencyMark(Graphics2D g, int x, int y) {
        g.setFont(EDGE_FONT.deriveFont(Font.BOLD));
        var font = g.getFontMetrics();
        int width = font.stringWidth("D");
        int baseline = y + (font.getAscent() - font.getDescent()) / 2;
        Color ink = g.getColor();
        g.setColor(Color.WHITE);
        g.fill(new Rectangle2D.Double(x - width / 2.0 - 2, baseline - font.getAscent(),
                width + 4, font.getHeight()));
        g.setColor(ink);
        g.drawString("D", x - width / 2f, baseline);
    }
}
