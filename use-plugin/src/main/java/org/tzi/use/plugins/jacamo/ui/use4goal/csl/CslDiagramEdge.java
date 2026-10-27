package org.tzi.use.plugins.jacamo.ui.use4goal.csl;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.util.ArrayList;
import java.util.List;

import org.tzi.use.gui.views.diagrams.elements.PlaceableNode;
import org.tzi.use.gui.views.diagrams.elements.edges.EdgeBase;
import org.tzi.use.gui.views.diagrams.util.Direction;
import org.tzi.use.gui.views.diagrams.waypoints.WayPoint;
import org.tzi.use.plugins.jacamo.ui.use4goal.gui.DiagramVisualStyle;

public final class CslDiagramEdge extends EdgeBase {
    private static final Font EDGE_FONT = DiagramVisualStyle.EDGE_FONT;
    private static final double MARKER_LENGTH = 14.0;
    private static final double MARKER_HALF_WIDTH = 5.5;
    private final CslEdge edge;
    private final CslDiagramOptions opt;

    public CslDiagramEdge(CslEdge edge, PlaceableNode source, PlaceableNode target, CslDiagramOptions opt) {
        super(source, target, edge.kind().name().toLowerCase(), opt, false);
        this.edge = edge;
        this.opt = opt;
    }

    @Override protected void onDraw(Graphics2D graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setColor(isSelected() ? fOpt.getEDGE_SELECTED_COLOR() : edgeColor());
        g.setStroke(edgeStroke());
        List<Point2D> points = new ArrayList<>();
        for (WayPoint point : getWayPoints()) { point.draw(g); points.add(point.getCenter()); }
        if (points.size() < 2) { g.dispose(); return; }

        Point2D start = points.get(0), end = points.get(points.size() - 1);
        Point2D startInside, endInside;
        if (edge.routeCount() > 1 && points.size() == 2 && start.distance(end) > 1.0) {
            Point2D control = controlPoint(start, end);
            g.draw(new QuadCurve2D.Double(start.getX(), start.getY(), control.getX(), control.getY(), end.getX(), end.getY()));
            startInside = control; endInside = control;
        } else {
            for (int i = 1; i < points.size(); i++) {
                Point2D from = points.get(i - 1), to = points.get(i);
                g.drawLine((int) Math.round(from.getX()), (int) Math.round(from.getY()),
                        (int) Math.round(to.getX()), (int) Math.round(to.getY()));
            }
            startInside = points.get(1); endInside = points.get(points.size() - 2);
        }
        paintMarkers(g, start, startInside, endInside, end);
        paintNearEnd(g, start, startInside, edge.sourceCardinality());
        paintNearEnd(g, end, endInside, edge.targetCardinality());
        paintLabel(g, start, end);
        g.dispose();
    }

    private void paintMarkers(Graphics2D g, Point2D start, Point2D startInside,
                              Point2D endInside, Point2D end) {
        switch (edge.kind()) {
            case GENERALIZATION -> drawMarker(g, endInside, end, Marker.OPEN_TRIANGLE);
            case OWNER -> drawMarker(g, startInside, start, Marker.OPEN_SQUARE);
            case AGGREGATION -> drawMarker(g, startInside, start, Marker.OPEN_DIAMOND);
            case COMPOSITION -> drawMarker(g, startInside, start, Marker.FILLED_DIAMOND);
            case COMPATIBILITY -> {
                drawMarker(g, endInside, end, Marker.OPEN_CIRCLE);
                if (edge.bidirectional()) drawMarker(g, startInside, start, Marker.OPEN_CIRCLE);
            }
            case ASSOCIATION -> { }
        }
    }

    private void paintNearEnd(Graphics2D g, Point2D endpoint, Point2D inside, String text) {
        if (text == null || text.isBlank()) return;
        Point2D anchor = offsetLabel(endpoint, inside, 23.0, 11.0);
        paintText(g, text, anchor);
    }

    private void paintLabel(Graphics2D g, Point2D start, Point2D end) {
        if (!fOpt.isShowAssocNames() || edge.label() == null || edge.label().isBlank()) return;
        paintText(g, edge.label(), new Point2D.Double((start.getX() + end.getX()) / 2.0,
                (start.getY() + end.getY()) / 2.0 - 7.0));
    }

    private void paintText(Graphics2D g, String text, Point2D anchor) {
        g.setFont(EDGE_FONT);
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text);
        g.setColor(new Color(255, 255, 255, 235));
        g.fillRoundRect((int) anchor.getX() - 3, (int) anchor.getY() - metrics.getAscent() - 2,
                width + 6, metrics.getHeight() + 3, 5, 5);
        g.setColor(isSelected() ? fOpt.getEDGE_SELECTED_COLOR() : edgeColor());
        g.drawString(text, (float) anchor.getX(), (float) anchor.getY());
    }

    private static Point2D offsetLabel(Point2D from, Point2D toward, double forward, double side) {
        double dx = toward.getX() - from.getX(), dy = toward.getY() - from.getY();
        double length = Math.max(.01, Math.hypot(dx, dy));
        return new Point2D.Double(from.getX() + dx / length * forward + dy / length * side,
                from.getY() + dy / length * forward - dx / length * side);
    }

    private Point2D controlPoint(Point2D start, Point2D end) {
        double dx = end.getX() - start.getX(), dy = end.getY() - start.getY();
        double length = Math.max(1.0, Math.hypot(dx, dy));
        double slot = edge.routeIndex() - (edge.routeCount() - 1) / 2.0;
        if (edge.fromId().compareTo(edge.toId()) > 0) slot = -slot;
        double offset = slot * 38.0;
        return new Point2D.Double((start.getX() + end.getX()) / 2.0 - dy / length * offset,
                (start.getY() + end.getY()) / 2.0 + dx / length * offset);
    }

    private static void drawMarker(Graphics2D g, Point2D inside, Point2D endpoint, Marker marker) {
        double dx = endpoint.getX() - inside.getX(), dy = endpoint.getY() - inside.getY();
        double length = Math.hypot(dx, dy); if (length < .01) return;
        double ux = dx / length, uy = dy / length, px = -uy, py = ux;
        if (marker == Marker.OPEN_CIRCLE) {
            double cx = endpoint.getX() - ux * 5, cy = endpoint.getY() - uy * 5;
            Color line = g.getColor(); g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8)); g.setColor(line);
            g.draw(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8)); return;
        }
        if (marker == Marker.OPEN_SQUARE) {
            double cx = endpoint.getX() - ux * 4, cy = endpoint.getY() - uy * 4;
            Color line = g.getColor(); g.setColor(Color.WHITE);
            g.fill(new java.awt.geom.Rectangle2D.Double(cx - 4, cy - 4, 8, 8)); g.setColor(line);
            g.draw(new java.awt.geom.Rectangle2D.Double(cx - 4, cy - 4, 8, 8)); return;
        }
        double bx = endpoint.getX() - ux * MARKER_LENGTH, by = endpoint.getY() - uy * MARKER_LENGTH;
        Path2D path = new Path2D.Double();
        if (marker == Marker.OPEN_DIAMOND || marker == Marker.FILLED_DIAMOND) {
            double cx = endpoint.getX() - ux * MARKER_LENGTH / 2, cy = endpoint.getY() - uy * MARKER_LENGTH / 2;
            path.moveTo(endpoint.getX(), endpoint.getY()); path.lineTo(cx + px * MARKER_HALF_WIDTH, cy + py * MARKER_HALF_WIDTH);
            path.lineTo(bx, by); path.lineTo(cx - px * MARKER_HALF_WIDTH, cy - py * MARKER_HALF_WIDTH);
        } else {
            path.moveTo(endpoint.getX(), endpoint.getY()); path.lineTo(bx + px * MARKER_HALF_WIDTH, by + py * MARKER_HALF_WIDTH);
            path.lineTo(bx - px * MARKER_HALF_WIDTH, by - py * MARKER_HALF_WIDTH);
        }
        path.closePath();
        if (marker == Marker.OPEN_TRIANGLE || marker == Marker.OPEN_DIAMOND) {
            Color line = g.getColor(); g.setColor(Color.WHITE); g.fill(path); g.setColor(line); g.draw(path);
        } else g.fill(path);
    }

    private Stroke edgeStroke() {
        if (edge.kind() == CslEdgeKind.COMPATIBILITY && edge.interGroup())
            return DiagramVisualStyle.dashed();
        return DiagramVisualStyle.solid();
    }

    private Color edgeColor() { return opt.getColor(CslDiagramOptions.MOISE_EDGE_COLOR); }
    @Override public boolean isLink() { return false; }
    @Override protected String getIdInternal() { return "csl::" + edge.kind() + "::" + edge.fromId() + "::" + edge.toId() + "::" + edge.routeIndex(); }
    @Override protected String getStoreType() { return "CslEdge"; }
    @Override public Direction getReflexivePosition() { return reflexivePosition == null ? Direction.NORTH_EAST : reflexivePosition; }
    private enum Marker { OPEN_TRIANGLE, OPEN_DIAMOND, FILLED_DIAMOND, OPEN_CIRCLE, OPEN_SQUARE }
}
