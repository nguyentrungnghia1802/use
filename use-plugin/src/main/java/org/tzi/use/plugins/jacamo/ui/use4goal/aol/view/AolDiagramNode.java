package org.tzi.use.plugins.jacamo.ui.use4goal.aol.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import org.tzi.use.gui.views.diagrams.ToolTipProvider;
import org.tzi.use.gui.views.diagrams.elements.PlaceableNode;

public final class AolDiagramNode extends PlaceableNode implements ToolTipProvider {
    private static final int H_PAD = 10;
    private static final int V_PAD = 5;
    private static final int PLAY_HEADER = 40;
    private static final int ENTITY_HEADER = 40;
    private static final int AGENT_HEADER = 34;
    private static final int GROUP_HEADER = 40;
    private static final int GROUP_TAB_WIDTH = 30;
    private static final int GROUP_TAB_HEIGHT = 11;

    private final AolNode node;
    private final AolDiagramOptions opt;

    public AolDiagramNode(AolNode node, AolDiagramOptions opt, Font font) {
        this.node = node;
        this.opt = opt;
        setBackColor(fillColor());
        setBackColorSelected(opt.getNODE_SELECTED_COLOR());
        setFrameColor(opt.getNODE_FRAME_COLOR());
        setTextColor(opt.getNODE_LABEL_COLOR());
        setFont(font);
        setMinWidth(Math.max(80, node.w));
        setMinHeight(Math.max(36, node.h));
        setRequiredWidth(node.id, Math.max(80, node.w));
        setRequiredHeight(node.id, Math.max(36, node.h));
    }

    public AolNode node() {
        return node;
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
        return true;
    }

    @Override
    public Area getArea() {
        return new Area(shape());
    }

    @Override
    protected void onDraw(Graphics2D graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        Shape shape = shape();
        g.setColor(isSelected() ? getBackColorSelected() : fillColor());
        g.fill(shape);
        g.setColor(getFrameColor());
        g.setStroke(new BasicStroke(1.4f));
        g.draw(shape);

        switch (node.kind) {
            case AGENT -> drawAgent(g);
            case GROUP_INSTANCE -> drawGroup(g);
            case PLAY -> drawPlay(g);
            case ENTITY_INSTANCE -> drawEntity(g);
        }
        g.dispose();
    }

    private void drawAgent(Graphics2D g) {
        g.setColor(getTextColor());
        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
        drawCentered(g, node.label, new Rectangle2D.Double(
                getX() + H_PAD, getY() + V_PAD, getWidth() - H_PAD * 2, AGENT_HEADER - V_PAD * 2));
        if (node.details.isEmpty()) return;
        drawDetails(g, AGENT_HEADER, H_PAD * 2);
    }

    private void drawGroup(Graphics2D g) {
        g.setColor(getTextColor());
        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
        drawCentered(g, node.label, new Rectangle2D.Double(
                getX() + H_PAD, getY() + GROUP_TAB_HEIGHT,
                getWidth() - H_PAD * 2, GROUP_HEADER - GROUP_TAB_HEIGHT));
        if (node.details.isEmpty()) return;
        g.setColor(getFrameColor());
        g.draw(new Line2D.Double(getX(), getY() + GROUP_HEADER,
                getX() + getWidth(), getY() + GROUP_HEADER));
        drawDetails(g, GROUP_HEADER, H_PAD);
    }

    private void drawPlay(Graphics2D g) {
        drawRoleIcon(g);
        g.setColor(getTextColor());
        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
        drawCentered(g, node.label, new Rectangle2D.Double(
                getX() + 24, getY() + V_PAD, getWidth() - 40, PLAY_HEADER - V_PAD * 2));

        if (node.details.isEmpty()) return;
        g.setColor(getFrameColor());
        g.draw(new Line2D.Double(getX() + H_PAD, getY() + PLAY_HEADER,
                getX() + getWidth() - H_PAD, getY() + PLAY_HEADER));
        drawDetails(g, PLAY_HEADER, H_PAD);
    }

    private void drawRoleIcon(Graphics2D g) {
        double x = getX() + 12, y = getY() + 8;
        g.setColor(getFrameColor());
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new Ellipse2D.Double(x - 3, y, 6, 6));
        g.draw(new Line2D.Double(x, y + 6, x, y + 16));
        g.draw(new Line2D.Double(x - 5, y + 10, x + 5, y + 10));
        g.draw(new Line2D.Double(x, y + 16, x - 4, y + 22));
        g.draw(new Line2D.Double(x, y + 16, x + 4, y + 22));
    }

    private void drawEntity(Graphics2D g) {
        g.setColor(getTextColor());
        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
        drawCentered(g, node.label, new Rectangle2D.Double(
                getX() + H_PAD, getY() + V_PAD, getWidth() - H_PAD * 2, ENTITY_HEADER - V_PAD * 2));

        g.setColor(getFrameColor());
        g.draw(new Line2D.Double(getX(), getY() + ENTITY_HEADER, getX() + getWidth(), getY() + ENTITY_HEADER));
        drawDetails(g, ENTITY_HEADER, H_PAD);
    }

    private void drawDetails(Graphics2D g, int headerHeight, int horizontalPadding) {
        g.setColor(getTextColor());
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics metrics = g.getFontMetrics();
        int y = (int) getY() + headerHeight + V_PAD + metrics.getAscent();
        for (String detail : node.details) {
            if (y > getY() + getHeight() - V_PAD) break;
            g.drawString(clip(detail, metrics, (int) getWidth() - horizontalPadding * 2),
                    (float) getX() + horizontalPadding, y);
            y += metrics.getHeight();
        }
    }

    @Override
    protected void doCalculateSize(Graphics2D g) {
        FontMetrics nameMetrics = g.getFontMetrics(getFont().deriveFont(Font.BOLD, 13f));
        FontMetrics detailMetrics = g.getFontMetrics(getFont().deriveFont(Font.PLAIN, 11f));
        int width = nameMetrics.stringWidth(node.label) + H_PAD * 4;
        for (String detail : node.details) {
            width = Math.max(width, detailMetrics.stringWidth(detail) + H_PAD * 4);
        }
        int height = switch (node.kind) {
            case AGENT -> node.details.isEmpty() ? 44
                    : AGENT_HEADER + node.details.size() * detailMetrics.getHeight() + 10;
            case GROUP_INSTANCE -> node.details.isEmpty() ? 56
                    : GROUP_HEADER + node.details.size() * detailMetrics.getHeight() + 10;
            case PLAY -> PLAY_HEADER + (node.details.isEmpty() ? 0 : node.details.size() * detailMetrics.getHeight() + 10);
            case ENTITY_INSTANCE -> ENTITY_HEADER + Math.max(1, node.details.size()) * detailMetrics.getHeight() + 10;
        };
        setCalculatedBounds(Math.max(node.w, width), Math.max(node.h, height));
    }

    @Override
    protected String getStoreType() {
        return "AolNode";
    }

    @Override
    public String getToolTip(MouseEvent event) {
        return "<html><b>" + node.label + "</b><br/>" + node.subtitle + "</html>";
    }

    private Shape shape() {
        return switch (node.kind) {
            case AGENT -> new Ellipse2D.Double(getX(), getY(), getWidth(), getHeight());
            case GROUP_INSTANCE -> groupShape();
            case PLAY -> {
                double arc = Math.min(40.0, getHeight());
                yield new RoundRectangle2D.Double(getX(), getY(), getWidth(), getHeight(), arc, arc);
            }
            case ENTITY_INSTANCE -> new Rectangle2D.Double(getX(), getY(), getWidth(), getHeight());
        };
    }

    private Shape groupShape() {
        Path2D path = new Path2D.Double();
        path.moveTo(getX(), getY());
        path.lineTo(getX() + GROUP_TAB_WIDTH, getY());
        path.lineTo(getX() + GROUP_TAB_WIDTH, getY() + GROUP_TAB_HEIGHT);
        path.lineTo(getX() + getWidth(), getY() + GROUP_TAB_HEIGHT);
        path.lineTo(getX() + getWidth(), getY() + getHeight());
        path.lineTo(getX(), getY() + getHeight());
        path.closePath();
        return path;
    }

    private Color fillColor() {
        return switch (node.kind) {
            case AGENT -> opt.getColor(AolDiagramOptions.AGENT_FILL);
            case GROUP_INSTANCE -> opt.getColor(AolDiagramOptions.GROUP_FILL);
            case PLAY -> opt.getColor(AolDiagramOptions.PLAY_FILL);
            case ENTITY_INSTANCE -> opt.getColor(AolDiagramOptions.ENTITY_FILL);
        };
    }

    private static void drawCentered(Graphics2D g, String text, Rectangle2D bounds) {
        FontMetrics metrics = g.getFontMetrics();
        String clipped = clip(text, metrics, (int) bounds.getWidth());
        float x = (float) (bounds.getX() + (bounds.getWidth() - metrics.stringWidth(clipped)) / 2.0);
        float y = (float) (bounds.getY() + (bounds.getHeight() - metrics.getHeight()) / 2.0 + metrics.getAscent());
        g.drawString(clipped, x, y);
    }

    private static String clip(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) return text;
        String ellipsis = "...";
        int length = text.length();
        while (length > 0 && metrics.stringWidth(text.substring(0, length) + ellipsis) > width) length--;
        return length <= 0 ? ellipsis : text.substring(0, length) + ellipsis;
    }
}
