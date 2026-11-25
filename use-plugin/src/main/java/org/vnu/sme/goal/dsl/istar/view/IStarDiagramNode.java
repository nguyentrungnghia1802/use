package org.vnu.sme.goal.dsl.istar.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import org.tzi.use.gui.views.diagrams.ToolTipProvider;
import org.tzi.use.gui.views.diagrams.elements.PlaceableNode;

public final class IStarDiagramNode extends PlaceableNode implements ToolTipProvider {
    private final IStarNode node;
    private NodeBadge badge;
    private String labelOverride;

    public IStarDiagramNode(IStarNode node, IStarDiagramOptions opt, Font font) {
        this.node = node;
        setBackColor(Color.WHITE);
        setBackColorSelected(opt.getNODE_SELECTED_COLOR());
        setFrameColor(opt.getNODE_FRAME_COLOR());
        setTextColor(opt.getNODE_LABEL_COLOR());
        setFont(font);
        setMinWidth(Math.max(30, node.w));
        setMinHeight(Math.max(24, node.h));
        setRequiredWidth(node.id, Math.max(30, node.w));
        setRequiredHeight(node.id, Math.max(24, node.h));
    }

    public IStarNode node() {
        return node;
    }

    public void setBadge(NodeBadge badge) {
        this.badge = badge;
    }

    /** Overrides the painted label (e.g. showing a concrete instance name like "amr" instead of the role "Participant"). */
    public void setLabelOverride(String labelOverride) {
        this.labelOverride = labelOverride;
    }

    private String displayLabel() {
        return labelOverride != null ? labelOverride : node.label;
    }

    @Override
    public String getId() {
        return node.id;
    }

    @Override
    public String name() {
        return node.id;
    }

    @Override
    public boolean isResizable() {
        return node.kind != IStarNodeKind.ACTOR;
    }

    @Override
    public Area getArea() {
        return new Area(getShape());
    }

    @Override
    public Shape getShape() {
        if (!node.actorBoundary) {
            return shape();
        }

        double x = getX(), y = getY(), w = getWidth(), h = getHeight();
        Area hit = new Area(actorCircle());

        Area outer = new Area(new RoundRectangle2D.Double(x, y, w, h, 18, 18));
        Area inner = new Area(new RoundRectangle2D.Double(x + 8, y + 8, Math.max(0, w - 16), Math.max(0, h - 16), 14, 14));
        outer.subtract(inner);
        hit.add(outer);
        return hit;
    }

    @Override
    protected void onDraw(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Shape shape = shape();
        if (!node.actorBoundary) {
            g2.setColor(isSelected() ? getBackColorSelected() : statusFillColor());
            g2.fill(shape);
        }
        g2.setColor(statusFrameColor());
        if (node.actorBoundary) {
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10f, new float[]{1f, 3f}, 0f));
        } else {
            g2.setStroke(new BasicStroke(1.5f));
        }
        g2.draw(shape);
        if (node.kind == IStarNodeKind.ACTOR) {
            paintActorMark(g2);
        }
        if (badge != null) {
            paintStatusBadge(g2, badge);
        }
        g2.setColor(getTextColor());
        g2.setFont(getFont().deriveFont(Font.PLAIN, node.kind == IStarNodeKind.ACTOR ? 11f : 10f));
        if (!node.actorBoundary && node.kind != IStarNodeKind.ACTOR) {
            paintContent(g2);
        }
        g2.dispose();
    }

    @Override
    protected void doCalculateSize(Graphics2D g) {
        setCalculatedBounds(Math.max(30, node.w), Math.max(24, node.h));
    }

    @Override
    protected String getStoreType() {
        return "IStarNode";
    }

    @Override
    public String getToolTip(MouseEvent event) {
        String base = "<html><b>" + node.id + "</b><br/>" + node.kind + ": " + node.label;
        if (node.goalType != null) {
            base += "<br/><b>Goal type:</b> " + htmlEscape(node.goalType);
        }
        if (!node.oclContracts.isEmpty()) {
            base += "<br/><br/><b>OCL contract</b><br/>"
                    + node.oclContracts.stream().map(IStarDiagramNode::htmlEscape)
                            .reduce((a, b) -> a + "<br/>" + b).orElse("");
        }
        return badge == null ? base + "</html>" : base + "<br/>" + badge.tooltip() + "</html>";
    }

    private void paintContent(Graphics2D g) {
        if (node.oclContracts.isEmpty() && node.goalType == null) {
            drawCentered(g, displayLabel(), new Rectangle2D.Double(getX(), getY(), getWidth(), getHeight()));
            return;
        }
        double headerHeight = node.goalType == null ? 30 : 42;
        headerHeight = Math.min(headerHeight, getHeight());
        paintHeader(g, headerHeight);
        if (node.oclContracts.isEmpty()) return;
        g.setColor(new Color(190, 190, 190));
        g.setStroke(new BasicStroke(0.8f));
        g.drawLine((int) (getX() + 16), (int) (getY() + headerHeight),
                (int) (getX() + getWidth() - 16), (int) (getY() + headerHeight));
        g.setColor(getTextColor());
        g.setFont(getFont().deriveFont(Font.PLAIN, 9f));
        FontMetrics fm = g.getFontMetrics();
        float y = (float) (getY() + headerHeight + fm.getAscent() + 4);
        for (String contract : node.oclContracts) {
            g.drawString(ellipsize(contract, fm, getWidth() - 24), (float) getX() + 12, y);
            y += 14;
        }
    }

    private void paintHeader(Graphics2D g, double headerHeight) {
        if (node.goalType == null) {
            drawCentered(g, displayLabel(), new Rectangle2D.Double(getX(), getY(), getWidth(), headerHeight));
            return;
        }
        g.setFont(getFont().deriveFont(Font.PLAIN, 10f));
        drawCentered(g, displayLabel(), new Rectangle2D.Double(getX(), getY() + 2, getWidth(), 22));
        g.setFont(getFont().deriveFont(Font.ITALIC, 9f));
        drawCentered(g, "«" + node.goalType + "»",
                new Rectangle2D.Double(getX(), getY() + 20, getWidth(), Math.max(16, headerHeight - 20)));
    }

    private static String ellipsize(String text, FontMetrics fm, double width) {
        if (fm.stringWidth(text) <= width) return text;
        String result = text;
        while (result.length() > 1 && fm.stringWidth(result + "...") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private static String htmlEscape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private Shape shape() {
        double x = getX(), y = getY(), w = getWidth(), h = getHeight();
        return switch (node.kind) {
            case ACTOR -> node.actorBoundary ? new RoundRectangle2D.Double(x, y, w, h, 18, 18) : actorCircle();
            case GOAL -> new Ellipse2D.Double(x, y, w, h);
            case TASK -> hex(x, y, w, h);
            case QUALITY -> cloud(x, y, w, h);
            case RESOURCE -> new Rectangle2D.Double(x, y, w, h);
        };
    }

    private void paintActorMark(Graphics2D g) {
        Ellipse2D circle = actorCircle();
        if (node.actorBoundary) {
            g.setColor(Color.WHITE);
            g.fill(circle);
            g.setColor(getFrameColor());
            g.setStroke(new BasicStroke(1.8f));
            g.draw(circle);
        }
        g.setColor(getFrameColor());
        g.setStroke(new BasicStroke(1.4f));
        double h = circle.getHeight();
        double cx = circle.getCenterX(), cy = circle.getCenterY();
        if (node.actorSymbol == IStarNode.ActorSymbol.ROLE) {
            double y = circle.getY() + h * 0.84;
            double half = chordHalfWidth(circle, y);
            g.draw(new QuadCurve2D.Double(cx - half, y, cx, circle.getY() + h * 0.98, cx + half, y));
        } else if (node.actorSymbol == IStarNode.ActorSymbol.AGENT) {
            double y = circle.getY() + h * 0.31;
            double half = chordHalfWidth(circle, y);
            g.draw(new Line2D.Double(cx - half, y, cx + half, y));
            cy += h * 0.08;
        }
        g.setColor(getTextColor());
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        drawCentered(g, displayLabel(), new Rectangle2D.Double(circle.getX(), cy - h / 2, circle.getWidth(), h));
    }

    private static double chordHalfWidth(Ellipse2D circle, double y) {
        double offset = (y - circle.getCenterY()) / (circle.getHeight() / 2);
        return circle.getWidth() / 2 * Math.sqrt(1 - offset * offset);
    }

    Ellipse2D actorCircle() {
        if (!node.actorBoundary) {
            return new Ellipse2D.Double(getX(), getY(), getWidth(), getHeight());
        }
        double d = IStarLayoutBuilder.actorDiameter(displayLabel());
        return new Ellipse2D.Double(getX() + (getWidth() - d) / 2, getY() - d / 2, d, d);
    }

    Point2D dependencyAnchor(Point2D toward, Point2D existing) {
        if (!node.actorBoundary) return existing;
        var circle = actorCircle();
        double dx = toward.getX() - circle.getCenterX();
        double dy = toward.getY() - circle.getCenterY();
        double distance = Math.hypot(dx, dy);
        if (distance == 0) return existing;
        double scale = circle.getWidth() / (2 * distance);
        return new Point2D.Double(circle.getCenterX() + dx * scale, circle.getCenterY() + dy * scale);
    }

    private void paintStatusBadge(Graphics2D g, NodeBadge badge) {
        int cx = (int) Math.round(getX() + getWidth() - 4);
        int cy = (int) Math.round(getY() + 4);
        g.setColor(badge.color());
        g.fillOval(cx - 7, cy - 7, 14, 14);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1f));
        g.drawOval(cx - 7, cy - 7, 14, 14);
        g.setFont(getFont().deriveFont(Font.BOLD, 9f));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(badge.glyph(), cx - fm.stringWidth(badge.glyph()) / 2, cy + fm.getAscent() / 2 - 1);
    }

    private Color statusFillColor() {
        if (badge == null) return getBackColor();
        String tip = badge.tooltip() == null ? "" : badge.tooltip();
        if (tip.startsWith("Fulfilled") || tip.startsWith("True")) return new Color(213, 232, 212);
        if (tip.startsWith("Pending")) return new Color(255, 242, 204);
        if (tip.startsWith("False")) return new Color(248, 206, 204);
        if (tip.startsWith("Unknown")) return new Color(245, 245, 245);
        return getBackColor();
    }

    private Color statusFrameColor() {
        if (badge == null) return getFrameColor();
        String tip = badge.tooltip() == null ? "" : badge.tooltip();
        if (tip.startsWith("Fulfilled") || tip.startsWith("True")) return new Color(130, 179, 102);
        if (tip.startsWith("Pending")) return new Color(214, 182, 86);
        if (tip.startsWith("False")) return new Color(184, 84, 80);
        if (tip.startsWith("Unknown")) return new Color(153, 153, 153);
        return getFrameColor();
    }

    private static Shape hex(double x, double y, double w, double h) {
        double inset = Math.min(w / 4, h / 2);
        Path2D.Double path = new Path2D.Double();
        path.moveTo(x + inset, y);
        path.lineTo(x + w - inset, y);
        path.lineTo(x + w, y + h / 2);
        path.lineTo(x + w - inset, y + h);
        path.lineTo(x + inset, y + h);
        path.lineTo(x, y + h / 2);
        path.closePath();
        return path;
    }

    private static Shape cloud(double x, double y, double w, double h) {
        var outline = new Path2D.Double();
        outline.moveTo(.17, .22);
        outline.curveTo(.22, .02, .39, .02, .47, .18);
        outline.curveTo(.59, .02, .84, .09, .86, .33);
        outline.curveTo(1.02, .32, 1.05, .64, .88, .69);
        outline.curveTo(.90, .88, .73, .96, .60, .81);
        outline.curveTo(.51, 1.02, .31, .98, .26, .84);
        outline.curveTo(.11, .93, -.02, .75, .09, .62);
        outline.curveTo(-.05, .56, .02, .22, .17, .22);
        outline.closePath();
        var scale = AffineTransform.getTranslateInstance(x, y);
        scale.scale(w, h);
        return scale.createTransformedShape(outline);
    }

    private static void drawCentered(Graphics2D g, String text, Rectangle2D bounds) {
        FontMetrics fm = g.getFontMetrics();
        String s = text == null ? "" : text;
        while (fm.stringWidth(s) > bounds.getWidth() - 10 && s.length() > 3) {
            s = s.substring(0, s.length() - 2) + ".";
        }
        float x = (float) (bounds.getX() + (bounds.getWidth() - fm.stringWidth(s)) / 2.0);
        float y = (float) (bounds.getY() + (bounds.getHeight() - fm.getHeight()) / 2.0 + fm.getAscent());
        g.drawString(s, x, y);
    }
}
