package org.tzi.use.plugins.jacamo.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/** Icons for JaCaMo workbench actions with automatic vector fallback. */
public final class WorkbenchIcons {
    private WorkbenchIcons() {}

    public static Icon getStartIcon() {
        return loadOrFallback("/icons/start.png", new VectorIcon(VectorIcon.Kind.START));
    }

    public static Icon getStopIcon() {
        return loadOrFallback("/icons/stop.png", new VectorIcon(VectorIcon.Kind.STOP));
    }

    public static Icon getRestartIcon() {
        return loadOrFallback("/icons/restart.png", new VectorIcon(VectorIcon.Kind.RESTART));
    }

    private static Icon loadOrFallback(String path, Icon fallback) {
        try {
            URL resource = WorkbenchIcons.class.getResource(path);
            if (resource != null) {
                return new ImageIcon(resource);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static final class VectorIcon implements Icon {
        enum Kind { START, STOP, RESTART }
        private final Kind kind;

        VectorIcon(Kind kind) { this.kind = kind; }

        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                switch (kind) {
                    case START -> {
                        g2.setColor(new Color(40, 167, 69));
                        Path2D triangle = new Path2D.Double();
                        triangle.moveTo(x + 3.5, y + 2.5);
                        triangle.lineTo(x + 13.5, y + 8.0);
                        triangle.lineTo(x + 3.5, y + 13.5);
                        triangle.closePath();
                        g2.fill(triangle);
                    }
                    case STOP -> {
                        g2.setColor(new Color(220, 53, 69));
                        g2.fill(new RoundRectangle2D.Double(x + 3.0, y + 3.0, 10.0, 10.0, 2.0, 2.0));
                    }
                    case RESTART -> {
                        g2.setColor(new Color(13, 110, 253));
                        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.draw(new Arc2D.Double(x + 2.5, y + 2.5, 11.0, 11.0, 45, 270, Arc2D.OPEN));
                        Path2D arrow = new Path2D.Double();
                        arrow.moveTo(x + 9.5, y + 0.5);
                        arrow.lineTo(x + 14.0, y + 4.0);
                        arrow.lineTo(x + 9.5, y + 7.5);
                        arrow.closePath();
                        g2.fill(arrow);
                    }
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
