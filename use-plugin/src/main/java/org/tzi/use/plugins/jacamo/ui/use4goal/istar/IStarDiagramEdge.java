package org.tzi.use.plugins.jacamo.ui.use4goal.istar;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.util.Iterator;

import org.tzi.use.gui.views.diagrams.edges.DirectedEdgeFactory;
import org.tzi.use.gui.views.diagrams.edges.DirectedEdgeFactory.ArrowStyle;
import org.tzi.use.gui.views.diagrams.elements.PlaceableNode;
import org.tzi.use.gui.views.diagrams.elements.edges.EdgeBase;
import org.tzi.use.gui.views.diagrams.util.Direction;
import org.tzi.use.gui.views.diagrams.waypoints.WayPoint;
import org.tzi.use.plugins.jacamo.ui.use4goal.gui.DiagramVisualStyle;

public final class IStarDiagramEdge extends EdgeBase {
    private static final Font EDGE_FONT = DiagramVisualStyle.EDGE_FONT;
    private final IStarEdge edge;

    public IStarDiagramEdge(IStarEdge edge, PlaceableNode source, PlaceableNode target, IStarDiagramOptions opt) {
        super(source, target, edge.kind().name().toLowerCase(), opt, false);
        this.edge = edge;
    }

    @Override
    protected void onDraw(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(isSelected() ? fOpt.getEDGE_SELECTED_COLOR() : Color.BLACK);
        g2.setStroke(stroke());
        if(edge.kind()==IStarEdgeKind.ASSOCIATION){
            // Actor associations belong to the actor headers. Connecting the
            // complete rationale rectangles would cut through owned Tasks.
            double sx=source().getX()+source().getWidth()/2,tx=target().getX()+target().getWidth()/2;
            double sy=actorTop(source(),g2),ty=actorTop(target(),g2);
            double lane=Math.max(24,Math.min(sy,ty)-20-Math.abs(tx-sx)/40);
            var route=new java.awt.geom.Path2D.Double();route.moveTo(sx,sy);route.lineTo(sx,lane);route.lineTo(tx,lane);route.lineTo(tx,ty);g2.draw(route);
            DirectedEdgeFactory.drawArrow(g2,(int)tx,(int)lane,(int)tx,(int)ty,ArrowStyle.FILLED);
            paintLabel(g2,new Point2D.Double(sx,lane),new Point2D.Double(tx,lane));g2.dispose();return;
        }
        Point2D last = null;
        Point2D first = null;
        if (!getWayPoints().isEmpty()) {
            Iterator<WayPoint> it = getWayPoints().iterator();
            WayPoint previous = it.next();
            first = previous.getCenter();
            previous.draw(g2);
            while (it.hasNext()) {
                WayPoint current = it.next();
                current.draw(g2);
                Point2D p1 = previous.getCenter();
                Point2D p2 = current.getCenter();
                if (!it.hasNext()) {
                    drawEnd(g2, p1, p2);
                    last = p2;
                } else {
                    g2.drawLine((int) p1.getX(), (int) p1.getY(), (int) p2.getX(), (int) p2.getY());
                }
                previous = current;
            }
        }
        paintLabel(g2, first, last);
        g2.dispose();
    }

    private static double actorTop(PlaceableNode node,Graphics2D graphics){
        if(node instanceof IStarDiagramNode actor)return actor.actorHeaderTop(graphics);
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

    private Stroke stroke() {
        return switch (edge.kind()) {
            case QUALIFICATION, DEPENDENCY ->
                    DiagramVisualStyle.dashed();
            default -> DiagramVisualStyle.solid();
        };
    }

    private void drawEnd(Graphics2D g, Point2D p1, Point2D p2) {
        int x1 = (int) Math.round(p1.getX()), y1 = (int) Math.round(p1.getY());
        int x2 = (int) Math.round(p2.getX()), y2 = (int) Math.round(p2.getY());
        g.drawLine(x1, y1, x2, y2);
        switch (edge.kind()) {
            case AND_REFINE, OR_REFINE -> { }
            case NEEDED_BY -> {
                g.setColor(Color.WHITE);
                g.fillOval(x2 - 5, y2 - 5, 10, 10);
                g.setColor(Color.BLACK);
                g.drawOval(x2 - 5, y2 - 5, 10, 10);
            }
            default -> DirectedEdgeFactory.drawArrow(g, x1, y1, x2, y2, ArrowStyle.FILLED);
        }
    }

    private void paintLabel(Graphics2D g, Point2D first, Point2D last) {
        String label = edge.label();
        if (label == null || first == null || last == null) return;
        int x = (int) Math.round((first.getX() + last.getX()) / 2.0);
        int y = (int) Math.round((first.getY() + last.getY()) / 2.0);
        if (edge.kind() == IStarEdgeKind.DEPENDENCY && "D".equals(label)) {
            paintDependencyBadge(g, x, y);
            return;
        }
        g.setFont(EDGE_FONT);
        g.setColor(Color.BLACK);
        g.drawString(label, x + 3, y - 4);
    }

    private static void paintDependencyBadge(Graphics2D g, int x, int y) {
        g.setColor(Color.WHITE);
        g.fillOval(x - 8, y - 8, 16, 16);
        g.setColor(Color.BLACK);
        g.drawOval(x - 8, y - 8, 16, 16);
        g.setFont(EDGE_FONT.deriveFont(Font.BOLD));
        g.drawString("D", x - 4, y + 4);
    }
}
