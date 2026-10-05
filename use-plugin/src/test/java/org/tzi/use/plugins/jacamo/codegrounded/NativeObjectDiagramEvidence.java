package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.uml.sys.*;

/** Native USE diagram and full state evidence from the same active MSystem; no copied verification model. */
final class NativeObjectDiagramEvidence {
    static void capture(MainWindow window,Path directory,String file,String boundary)throws Exception {
        StepReplayProof.onEdt(()->{
            Files.createDirectories(directory);
            var view=window.getObjectDiagrams().getFirst(); var system=view.system(); var state=system.state(); var diagram=view.getDiagram();
            String before=new NativeUseSoilExporter().export(system).commands();
            var ordered=state.allObjects().stream().sorted(Comparator.comparing(MObject::name)).toList();
            var visible=new LinkedHashSet<MObject>();
            // A readable native detail: several literal observations and all three semantic dimensions.
            for(String kind:List.of("agent-program","belief","agent-goal","group","role-association","workspace","artifact","organisation","scheme","mission","organisational-goal"))
                ordered.stream().filter(o->DomainProjection.kind(o.cls()).equals(kind))
                    .sorted(Comparator.comparingInt((MObject o)->detailPriority(o,state)).thenComparing(MObject::name))
                    .limit(kind.equals("belief") ? 3 : 1).forEach(visible::add);
            var links=state.allLinks().stream().sorted(Comparator.comparing((MLink l)->l.association().name())
                    .thenComparing(l->l.linkedObjects().stream().map(MObject::name).toList().toString())).toList();
            // Always show the real owner/endpoints of selected literals, role instances and artifacts.
            for(var link:links) if(link instanceof MObject object && visible.contains(object)
                    || link.linkedObjects().stream().anyMatch(o->visible.contains(o) && Set.of("belief","agent-goal","artifact").contains(DomainProjection.kind(o.cls())))
                        && Set.of(DomainProjection.relation("hasBelief","Agent","Belief"),DomainProjection.relation("hasGoal","Agent","AgentGoal"),DomainProjection.relation("locatedIn","Workspace","Artifact")).contains(link.association().name()))
                visible.addAll(link.linkedObjects());
            // Show an actual ordered child alongside the selected decomposition root.
            links.stream().filter(l->l.association().name().equals(DomainProjection.relation("subGoals","OrganizationalGoal","OrganizationalGoal"))
                    && visible.contains(l.linkedObjects().getFirst())).sorted(Comparator.comparingInt(l->((org.tzi.use.uml.ocl.value.IntegerValue)
                        l.linkedObjects().get(1).state(state).attributeValue("orderInParent")).value())).limit(1).forEach(l->visible.addAll(l.linkedObjects()));
            var anchors=new ArrayList<>(visible);
            for(var link:links) if(link.linkedObjects().stream().allMatch(visible::contains) && link instanceof MObject object) visible.add(object);
            // Include real ownership/context endpoints for selected role, goal and artifact occurrences.
            for(var link:links) if(link.linkedObjects().stream().anyMatch(anchors::contains)
                    && link.linkedObjects().stream().noneMatch(o->Set.of("belief","agent-goal","organisational-goal","mission").contains(DomainProjection.kind(o.cls())))) {
                if(visible.size()+link.linkedObjects().size()+(link instanceof MObject ? 1 : 0)>22) continue;
                visible.addAll(link.linkedObjects()); if(link instanceof MObject object) visible.add(object);
            }
            boolean attributes=diagram.getOptions().isShowAttributes(); boolean layout=diagram.getOptions().isDoAutoLayout();
            try {
                diagram.getOptions().setDoAutoLayout(false); diagram.getOptions().setShowAttributes(true);
                for(var object:ordered) if(!visible.contains(object)) diagram.hideObject(object);
                diagram.invalidateContent(false);
                var measure=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB); var probe=measure.createGraphics(); probe.setClip(0,0,2,2); diagram.drawDiagram(probe); probe.dispose();
                var nodes=diagram.getVisibleData().fObjectToNodeMap.values().stream().sorted(Comparator.comparing(n->n.object().name())).toList();
                int columnWidth=(int)Math.ceil(nodes.stream().mapToDouble(n->n.getWidth()).max().orElse(200))+140;
                int rowHeight=(int)Math.ceil(nodes.stream().mapToDouble(n->n.getHeight()).max().orElse(100))+100;
                int width=columnWidth*2+80,height=((nodes.size()+1)/2)*rowHeight+140;
                assertTrue((long)width*height<90_000_000L,"Native detail too large to render safely: "+width+"x"+height);
                for(int i=0;i<nodes.size();i++) {
                    nodes.get(i).setStrategy(org.tzi.use.gui.views.diagrams.elements.positioning.StrategyFixed.instance);
                    nodes.get(i).setPosition(40+(i%2)*columnWidth,100+(i/2)*rowHeight);
                }
                diagram.setSize(width,height); diagram.invalidateContent(false);
                var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB); var graphics=image.createGraphics();
                graphics.setClip(0,0,width,height); graphics.setColor(Color.WHITE); graphics.fillRect(0,0,width,height); diagram.drawDiagram(graphics);
                for(var node:nodes) assertTrue(node.getX()>=0 && node.getY()>=0 && node.getX()+node.getWidth()<=width && node.getY()+node.getHeight()<=height,
                        "Native node clipped: "+node.object().name());
                graphics.setColor(Color.BLACK); graphics.setFont(new Font("SansSerif",Font.BOLD,18)); graphics.drawString(boundary,30,30);
                graphics.setFont(new Font("SansSerif",Font.PLAIN,15)); graphics.drawString("Native USE detail; "+visible.size()+" visible / "+ordered.size()+" actual objects. Complete state: "+file+".cmd",30,57);
                graphics.dispose(); assertTrue(ImageIO.write(image,"png",directory.resolve(file+".png").toFile()));
                Files.writeString(directory.resolve(file+".cmd"),before);
                var objects=ordered.stream().map(o->{ var values=new TreeMap<String,String>(); o.state(state).attributeValueMap().forEach((a,v)->values.put(a.name(),v.toString()));
                    return Map.of("name",o.name(),"class",o.cls().name(),"values",values,"visible",visible.contains(o)); }).toList();
                var linkRows=links.stream().map(l->Map.of("association",l.association().name(),"participants",l.linkedObjects().stream().map(MObject::name).toList())).toList();
                Files.write(directory.resolve(file+".json"),CanonicalJson.encode(Map.of("boundary",boundary,"nativeSystemIdentity",System.identityHashCode(system),"objects",objects,"links",linkRows,
                    "attributesVisible",true,"render",Map.of("width",width,"height",height,"visibleObjects",visible.size()),"selection","native detail; complete native objects/links in this JSON and SOIL")));
            } finally {
                diagram.showAll(); diagram.showAllLinks(); diagram.getOptions().setShowAttributes(attributes); diagram.getOptions().setDoAutoLayout(layout); diagram.invalidateContent(true);
            }
            assertEquals(before,new NativeUseSoilExporter().export(system).commands()); assertSame(system,view.system());
            return null;
        });
    }
    private static int detailPriority(MObject object,MSystemState state) {
        String kind=DomainProjection.kind(object.cls());
        if(Set.of("belief","agent-goal").contains(kind)) return object.state(state).attributeValue("literal").toString().length();
        if(kind.equals("organisational-goal")) {
            var operator=object.state(state).attributeValue("decompositionOperator");
            return operator.isUndefined() || operator.toString().equals("''") ? 1 : 0;
        }
        if(kind.equals("artifact")) return -(int)object.cls().allAttributes().stream().filter(a->a.getAllAnnotations().keySet().stream()
                .anyMatch(k->k.startsWith("PropertySource_")) && !object.state(state).attributeValue(a).isUndefined()).count();
        return 0;
    }
}
