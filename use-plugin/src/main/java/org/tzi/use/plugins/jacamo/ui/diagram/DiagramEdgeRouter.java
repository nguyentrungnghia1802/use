package org.tzi.use.plugins.jacamo.ui.diagram;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.*;
import org.tzi.use.gui.views.diagrams.elements.PlaceableNode;

/** Geometry on top of USE's nodes/anchors. Explicit USE waypoints take priority. */
public final class DiagramEdgeRouter {
    private List<Rectangle2D> prior = List.of();
    private List<Point2D> endpoints = List.of(), cached = List.of();
    public List<Point2D> route(List<Point2D> points, PlaceableNode source, PlaceableNode target,
                               Collection<? extends PlaceableNode> nodes) {
        if (points.size() != 2 || source == target) return points;
        var boxes = nodes.stream().filter(n -> n != source && n != target)
            .map(n -> (Rectangle2D)new Rectangle2D.Double(n.getX()-10,n.getY()-10,n.getWidth()+20,n.getHeight()+20))
            .filter(r -> !r.contains(source.getCenter()) && !r.contains(target.getCenter())).toList();
        if (points.equals(endpoints) && boxes.equals(prior)) return cached;
        endpoints = List.copyOf(points); prior = boxes;
        cached = route(points.getFirst(), points.getLast(), boxes);
        return cached;
    }
    public static List<Point2D> route(Point2D a, Point2D b, List<Rectangle2D> boxes) {
        var candidates = new ArrayList<List<Point2D>>();
        candidates.add(List.of(a,b));
        candidates.add(List.of(a,new Point2D.Double(a.getX(),b.getY()),b));
        candidates.add(List.of(a,new Point2D.Double(b.getX(),a.getY()),b));
        var xs = new TreeSet<Double>(); var ys = new TreeSet<Double>();
        xs.add((a.getX()+b.getX())/2);ys.add((a.getY()+b.getY())/2);
        for(var box:boxes){xs.add(box.getMinX()-8);xs.add(box.getMaxX()+8);ys.add(box.getMinY()-8);ys.add(box.getMaxY()+8);}
        for(double x:xs)candidates.add(List.of(a,new Point2D.Double(x,a.getY()),new Point2D.Double(x,b.getY()),b));
        for(double y:ys)candidates.add(List.of(a,new Point2D.Double(a.getX(),y),new Point2D.Double(b.getX(),y),b));
        return candidates.stream().min(Comparator.comparingDouble(p -> score(p,boxes))).orElse(List.of(a,b));
    }
    private static double score(List<Point2D> points,List<Rectangle2D> boxes){
        double cost=points.size()*8;
        for(int i=1;i<points.size();i++){var a=points.get(i-1);var b=points.get(i);cost+=a.distance(b);
            for(var box:boxes)if(box.intersectsLine(a.getX(),a.getY(),b.getX(),b.getY()))cost+=1_000_000;}
        return cost;
    }
}
