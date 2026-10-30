package org.tzi.use.plugins.jacamo.ui.use4goal.aol.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.tzi.use.gui.views.diagrams.elements.PlaceableNode;
import org.tzi.use.gui.views.diagrams.elements.edges.EdgeBase;
import org.tzi.use.gui.views.diagrams.util.Direction;
import org.tzi.use.gui.views.diagrams.waypoints.WayPoint;

public final class AolDiagramEdge extends EdgeBase {
    private static final Font LABEL_FONT = new Font(Font.SERIF, Font.ITALIC, 11);
    private static final double MARKER_LENGTH = 13.0;
    private static final double MARKER_HALF_WIDTH = 5.0;

    private final AolEdge edge;
    private final AolDiagramOptions opt;
    private final java.util.Collection<PlaceableNode> obstacles;

    public AolDiagramEdge(AolEdge edge, PlaceableNode source, PlaceableNode target, AolDiagramOptions opt) {
        this(edge,source,target,opt,List.of());
    }
    public AolDiagramEdge(AolEdge edge, PlaceableNode source, PlaceableNode target, AolDiagramOptions opt,java.util.Collection<? extends PlaceableNode> obstacles) {
        super(source, target, edge.kind().name().toLowerCase(), opt, false);
        this.obstacles=List.copyOf(obstacles);
        this.edge = edge;
        this.opt = opt;
    }

    @Override
    protected void onDraw(Graphics2D graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setColor(isSelected() ? fOpt.getEDGE_SELECTED_COLOR() : edgeColor());
        g.setStroke(edgeStroke());

        List<Point2D> points = new ArrayList<>();
        for (WayPoint wayPoint : getWayPoints()) {
            wayPoint.draw(g);
            points.add(wayPoint.getCenter());
        }
        if (points.size() < 2) {
            g.dispose();
            return;
        }

        Point2D start = points.get(0);
        Point2D end = points.get(points.size() - 1);
        if(points.size()==2&&edge.kind()==AolEdgeKind.LINK&&fSource.getY()+fSource.getHeight()+28<fTarget.getY()) {
            // Keep membership links away from intervening attribute boxes. This
            // is geometry only: source/target identities and association stay exact.
            double x=fSource.getCenter().getX(),y=fSource.getY()+fSource.getHeight();
            double targetX=fTarget.getCenter().getX(),busY=fTarget.getY()-18;
            var candidates=new java.util.TreeSet<Double>(java.util.Comparator.comparingDouble(v->Math.abs(v-x)));
            candidates.add(x);
            for(var node:obstacles)if(node!=fSource&&node!=fTarget&&node.getY()<busY&&node.getY()+node.getHeight()>y+10){candidates.add(node.getX()-12);candidates.add(node.getX()+node.getWidth()+12);}
            double trunk=candidates.stream().filter(v->obstacles.stream().filter(n->n!=fSource&&n!=fTarget).noneMatch(n->new java.awt.geom.Rectangle2D.Double(n.getX()-8,n.getY()-8,n.getWidth()+16,n.getHeight()+16).intersectsLine(v,y+10,v,busY))).findFirst().orElse(x);
            points=List.of(new Point2D.Double(x,y),new Point2D.Double(x,y+10),new Point2D.Double(trunk,y+10),new Point2D.Double(trunk,busY),new Point2D.Double(targetX,busY),new Point2D.Double(targetX,fTarget.getY()));
            start=points.get(3);end=points.getLast();
        }
        for (int i = 1; i < points.size(); i++) {
            Point2D from = points.get(i - 1);
            Point2D to = points.get(i);
            g.drawLine((int) Math.round(from.getX()), (int) Math.round(from.getY()),
                    (int) Math.round(to.getX()), (int) Math.round(to.getY()));
        }
        Point2D endInside = points.get(points.size() - 2);

        paintEndMarker(g, endInside, end);
        paintLabel(g, start, points.size()==6?points.get(4):end);
        g.dispose();
    }

    private enum Marker { FILLED_DIAMOND, OPEN_DIAMOND, OPEN_TRIANGLE, OPEN_ARROW, NONE }

    private void paintEndMarker(Graphics2D g, Point2D inside, Point2D endpoint) {
        Marker marker = switch (edge.kind()) {
            case SUBGROUP_COMPOSITION, PLAY_COMPOSITION -> Marker.FILLED_DIAMOND;
            case ENTITY_AGGREGATION -> Marker.OPEN_DIAMOND;
            case ROLE_GENERALIZATION -> Marker.OPEN_TRIANGLE;
            case PLAYED_BY -> Marker.OPEN_ARROW;
            case LINK -> "enacts".equals(edge.label())||"participates (execution)".equals(edge.label())?Marker.OPEN_ARROW:Marker.NONE;
        };
        if (marker != Marker.NONE) {
            drawMarker(g, inside, endpoint, marker);
        }
    }

    private void paintLabel(Graphics2D g, Point2D start, Point2D end) {
        if (!opt.isShowAssociationNames()) return;
        // The AOL toolbar explains this overlay once; repeating its label on
        // every participant obscures the native membership links.
        if ("participates (execution)".equals(edge.label())) return;
        if (edge.label() == null || edge.label().isBlank()) return;
        g.setFont(LABEL_FONT);
        g.setColor(isSelected() ? fOpt.getEDGE_SELECTED_COLOR() : edgeColor());
        double centerX = (start.getX() + end.getX()) / 2.0;
        double y = (start.getY() + end.getY()) / 2.0 - 5.0;
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(edge.label());
        double x = centerX - width / 2.0;
        g.setColor(new Color(255, 255, 255, 235));
        g.fillRoundRect((int) x - 3, (int) y - metrics.getAscent() - 2, width + 6, metrics.getHeight() + 3, 5, 5);
        g.setColor(isSelected() ? fOpt.getEDGE_SELECTED_COLOR() : edgeColor());
        g.drawString(edge.label(), (float) x, (float) y);
    }

    private static void drawMarker(Graphics2D g, Point2D inside, Point2D endpoint, Marker marker) {
        double dx = endpoint.getX() - inside.getX();
        double dy = endpoint.getY() - inside.getY();
        double length = Math.hypot(dx, dy);
        if (length < 0.01) return;
        double ux = dx / length;
        double uy = dy / length;
        double px = -uy;
        double py = ux;

        double baseX = endpoint.getX() - ux * MARKER_LENGTH;
        double baseY = endpoint.getY() - uy * MARKER_LENGTH;

        if (marker == Marker.OPEN_ARROW) {
            Path2D path = new Path2D.Double();
            path.moveTo(baseX + px * MARKER_HALF_WIDTH, baseY + py * MARKER_HALF_WIDTH);
            path.lineTo(endpoint.getX(), endpoint.getY());
            path.lineTo(baseX - px * MARKER_HALF_WIDTH, baseY - py * MARKER_HALF_WIDTH);
            g.draw(path);
            return;
        }

        if (marker == Marker.OPEN_TRIANGLE) {
            Path2D path = new Path2D.Double();
            path.moveTo(endpoint.getX(), endpoint.getY());
            path.lineTo(baseX + px * MARKER_HALF_WIDTH, baseY + py * MARKER_HALF_WIDTH);
            path.lineTo(baseX - px * MARKER_HALF_WIDTH, baseY - py * MARKER_HALF_WIDTH);
            path.closePath();
            Color lineColor = g.getColor();
            g.setColor(Color.WHITE);
            g.fill(path);
            g.setColor(lineColor);
            g.draw(path);
            return;
        }

        double centerX = endpoint.getX() - ux * MARKER_LENGTH / 2.0;
        double centerY = endpoint.getY() - uy * MARKER_LENGTH / 2.0;
        Path2D path = new Path2D.Double();
        path.moveTo(endpoint.getX(), endpoint.getY());
        path.lineTo(centerX + px * MARKER_HALF_WIDTH, centerY + py * MARKER_HALF_WIDTH);
        path.lineTo(baseX, baseY);
        path.lineTo(centerX - px * MARKER_HALF_WIDTH, centerY - py * MARKER_HALF_WIDTH);
        path.closePath();

        if (marker == Marker.OPEN_DIAMOND) {
            Color lineColor = g.getColor();
            g.setColor(Color.WHITE);
            g.fill(path);
            g.setColor(lineColor);
            g.draw(path);
        } else {
            g.fill(path);
        }
    }

    @Override
    public boolean isLink() {
        return false;
    }

    @Override
    protected String getIdInternal() {
        return "aol::" + edge.kind().name() + "::" + edge.fromId() + "::" + edge.toId();
    }

    @Override
    protected String getStoreType() {
        return "AolEdge";
    }

    @Override
    public Direction getReflexivePosition() {
        return reflexivePosition == null ? Direction.NORTH_EAST : reflexivePosition;
    }

    private Color edgeColor() {
        return edge.kind()==AolEdgeKind.LINK&&"participates (execution)".equals(edge.label())?new Color(35,95,170):opt.getColor(AolDiagramOptions.EDGE_COLOR);
    }

    private Stroke edgeStroke() {
        if (edge.kind() == AolEdgeKind.PLAYED_BY || edge.kind()==AolEdgeKind.LINK&&"participates (execution)".equals(edge.label())) {
            return new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10f, new float[] {2f, 5f}, 0f);
        }
        return new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }
}
