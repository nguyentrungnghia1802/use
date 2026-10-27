package org.tzi.use.plugins.jacamo.ui.use4goal.gui;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Stroke;

/** Shared visual constants for language diagrams hosted by USE. */
public final class DiagramVisualStyle {
    public static final float STROKE_NORMAL = 1.4f;
    public static final float STROKE_EMPHASIS = 3.0f;
    public static final float FONT_NODE_NAME = 13f;
    public static final float FONT_DETAIL = 11f;
    public static final float FONT_EDGE = 11f;
    public static final Font EDGE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, (int) FONT_EDGE);

    private static final float[] DASH = { 7f, 5f };

    private DiagramVisualStyle() {}

    public static Stroke solid() {
        return new BasicStroke(STROKE_NORMAL, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    public static Stroke dashed() {
        return new BasicStroke(STROKE_NORMAL, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, DASH.clone(), 0f);
    }
}
