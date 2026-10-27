package org.tzi.use.plugins.jacamo.ui.use4goal.csl;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import org.tzi.use.gui.views.diagrams.ToolTipProvider;
import org.tzi.use.gui.views.diagrams.elements.CompartmentNode;
import org.tzi.use.plugins.jacamo.ui.use4goal.gui.DiagramVisualStyle;

/** Role, Group and Enumeration node using USE's compartment-node infrastructure. */
public final class CslDiagramNode extends CompartmentNode implements ToolTipProvider {
    private static final int H_PAD = 10;
    private static final int V_PAD = 5;
    private static final int ROLE_HEADER = 40;
    private static final int ENTITY_HEADER = 48;
    private static final int GROUP_HEADER = 38;
    private static final int ENUM_HEADER = 38;
    private static final int GROUP_TAB_WIDTH = 30;
    private static final int GROUP_TAB_HEIGHT = 11;

    private final CslNode node;
    private final CslDiagramOptions opt;

    public CslDiagramNode(CslNode node, CslDiagramOptions opt, Font font) {
        this.node = node;
        this.opt = opt;
        setBackColor(fillColor());
        setBackColorSelected(opt.getNODE_SELECTED_COLOR());
        setFrameColor(opt.getNODE_FRAME_COLOR());
        setTextColor(opt.getNODE_LABEL_COLOR());
        setFont(font);
        setMinWidth(60);
        setMinHeight(38);
        setRequiredWidth(node.id, 60);
        setRequiredHeight(node.id, 38);
    }

    public CslNode node() {
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
        g.setStroke(DiagramVisualStyle.solid());
        g.draw(shape);

        switch (node.kind) {
            case ROLE -> drawRole(g);
            case GROUP, ORG_CONTEXT -> drawGroup(g);
            case ENTITY -> drawEntity(g);
            case ENUM -> drawEnum(g);
        }
        g.dispose();
    }

    private void drawRole(Graphics2D g) {
        drawRoleIcon(g);
        Font nameFont = getFont().deriveFont(isAbstractRole() ? Font.BOLD | Font.ITALIC : Font.BOLD,
                DiagramVisualStyle.FONT_NODE_NAME);
        g.setColor(getTextColor());
        g.setFont(nameFont);
        drawCentered(g, node.label, new Rectangle2D.Double(
                getX() + 22, getY() + V_PAD, getWidth() - 44, ROLE_HEADER - V_PAD * 2));

        if (node.details.isEmpty() || !opt.isShowAttributes()) return;
        g.setColor(getFrameColor());
        g.draw(new Line2D.Double(getX() + 18, getY() + ROLE_HEADER,
                getX() + getWidth() - 18, getY() + ROLE_HEADER));
        drawDetails(g, ROLE_HEADER, 22);
    }

    /** Small person icon in the header; the rest of the Role component stays compact. */
    private void drawRoleIcon(Graphics2D g) {
        double x = getX() + 14, y = getY() + 10;
        g.setColor(getFrameColor());
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new java.awt.geom.Ellipse2D.Double(x - 3, y, 6, 6));
        g.draw(new Line2D.Double(x, y + 6, x, y + 17));
        g.draw(new Line2D.Double(x - 6, y + 10, x + 6, y + 10));
        g.draw(new Line2D.Double(x, y + 17, x - 5, y + 23));
        g.draw(new Line2D.Double(x, y + 17, x + 5, y + 23));
    }

    private void drawGroup(Graphics2D g) {
        g.setColor(getTextColor());
        g.setFont(getFont().deriveFont(Font.BOLD, DiagramVisualStyle.FONT_NODE_NAME));
        drawCentered(g, node.label, new Rectangle2D.Double(
                getX() + H_PAD, getY() + GROUP_TAB_HEIGHT,
                getWidth() - H_PAD * 2, GROUP_HEADER - GROUP_TAB_HEIGHT));

        g.setColor(getFrameColor());
        g.draw(new Line2D.Double(getX(), getY() + GROUP_HEADER,
                getX() + getWidth(), getY() + GROUP_HEADER));
        if (opt.isShowAttributes()) drawDetails(g, GROUP_HEADER, H_PAD);
    }

    private void drawEntity(Graphics2D g) {
        g.setColor(getTextColor());
        g.setFont(getFont().deriveFont(Font.PLAIN, 10f));
        g.drawString("«entity»", (float) getX() + H_PAD,
                (float) getY() + V_PAD + g.getFontMetrics().getAscent());

        g.setFont(getFont().deriveFont(Font.BOLD, DiagramVisualStyle.FONT_NODE_NAME));
        drawCentered(g, node.label, new Rectangle2D.Double(
                getX() + H_PAD, getY() + 16, getWidth() - H_PAD * 2, 26));

        g.setColor(getFrameColor());
        g.draw(new Line2D.Double(getX(), getY() + ENTITY_HEADER,
                getX() + getWidth(), getY() + ENTITY_HEADER));
        if (opt.isShowAttributes()) drawDetails(g, ENTITY_HEADER, H_PAD);
    }

    private void drawEnum(Graphics2D g) {
        g.setColor(getTextColor());
        g.setFont(getFont().deriveFont(Font.BOLD, DiagramVisualStyle.FONT_NODE_NAME));
        drawCentered(g, node.label, new Rectangle2D.Double(
                getX() + H_PAD, getY() + V_PAD, getWidth() - H_PAD * 2, ENUM_HEADER - V_PAD * 2));
        g.setColor(getFrameColor());
        g.draw(new Line2D.Double(getX(), getY() + ENUM_HEADER,
                getX() + getWidth(), getY() + ENUM_HEADER));
        drawDetails(g, ENUM_HEADER, H_PAD);
    }

    private void drawDetails(Graphics2D g, int headerHeight, int horizontalPadding) {
        g.setColor(getTextColor());
        g.setFont(getFont().deriveFont(Font.PLAIN, DiagramVisualStyle.FONT_DETAIL));
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
        FontMetrics nameMetrics = g.getFontMetrics(getFont().deriveFont(Font.BOLD, DiagramVisualStyle.FONT_NODE_NAME));
        FontMetrics detailMetrics = g.getFontMetrics(getFont().deriveFont(Font.PLAIN, DiagramVisualStyle.FONT_DETAIL));
        // Role headers reserve 44 pixels for their person icon and rounded ends.
        int width = nameMetrics.stringWidth(node.label) + (node.kind == CslNodeKind.ROLE ? 44 : H_PAD * 2 + 8);
        for (String detail : node.details) {
            width = Math.max(width, detailMetrics.stringWidth(detail) + H_PAD * 2 + 8);
        }
        width = Math.max(60, width);
        int visibleAttributeCount = opt.isShowAttributes() ? node.details.size() : 0;
        int height = switch (node.kind) {
            case ROLE -> ROLE_HEADER + (visibleAttributeCount == 0 ? 0 : visibleAttributeCount * detailMetrics.getHeight() + 10);
            case GROUP, ORG_CONTEXT -> GROUP_HEADER
                    + (visibleAttributeCount == 0 ? 10 : visibleAttributeCount * detailMetrics.getHeight() + 10);
            case ENTITY -> ENTITY_HEADER + (visibleAttributeCount == 0 ? 10 : visibleAttributeCount * detailMetrics.getHeight() + 10);
            case ENUM -> ENUM_HEADER + Math.max(1, node.details.size()) * detailMetrics.getHeight() + 10;
        };
        setCalculatedBounds(width, Math.max(node.h, height));
    }

    @Override
    protected String getStoreType() {
        return "CslNode";
    }

    @Override
    public String getToolTip(MouseEvent event) {
        return "<html><b>" + node.label + "</b><br/>" + node.subtitle + "</html>";
    }

    private Shape shape() {
        return switch (node.kind) {
            case ROLE -> {
                double arc = Math.min(56.0, getHeight());
                yield new RoundRectangle2D.Double(getX(), getY(), getWidth(), getHeight(), arc, arc);
            }
            case GROUP, ORG_CONTEXT -> groupShape();
            case ENTITY -> new Rectangle2D.Double(getX(), getY(), getWidth(), getHeight());
            case ENUM -> new Rectangle2D.Double(getX(), getY(), getWidth(), getHeight());
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
            case ENTITY -> opt.getColor(CslDiagramOptions.ENTITY_FILL);
            case ROLE -> opt.getColor(CslDiagramOptions.ROLE_FILL);
            case GROUP, ORG_CONTEXT -> opt.getColor(CslDiagramOptions.GROUP_FILL);
            case ENUM -> opt.getColor(CslDiagramOptions.ENUM_FILL);
        };
    }

    private boolean isAbstractRole() {
        return node.subtitle.startsWith("abstract");
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
