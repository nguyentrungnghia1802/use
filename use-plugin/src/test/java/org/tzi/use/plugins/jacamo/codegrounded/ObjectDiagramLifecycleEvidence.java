package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.jacamo.bridge.contract.*;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.gui.views.diagrams.objectdiagram.NewObjectDiagram;
import org.tzi.use.gui.views.diagrams.objectdiagram.NewObjectDiagramView;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.uml.sys.*;

/** Test-only observation of one persistent native diagram; never creates a model or runtime. */
public final class ObjectDiagramLifecycleEvidence implements AutoCloseable {
    private final DefaultJaCaMoFacade facade;
    private final Session session;
    private final MainWindow window;
    private final NewObjectDiagramView view;
    private final MSystem system;
    private final MSystemState state;
    private final Path directory;
    private final String previousHeight,previousWidth;
    private final List<Map<String,Object>> phases=new ArrayList<>();
    private final List<String> atomicErrors=new ArrayList<>();
    private final Map<String,Set<String>> artifactValues=new TreeMap<>();
    private long atomicChecks;
    private boolean closed;

    public ObjectDiagramLifecycleEvidence(DefaultJaCaMoFacade facade,Session session,Path evidence)throws Exception {
        this.facade=facade;this.session=session;this.system=session.system();this.state=system.state();
        directory=evidence.resolve("diagram-lifecycle");Files.createDirectories(directory);
        previousHeight=System.getProperty("use.gui.view.classdiagram.class.minheight");
        previousWidth=System.getProperty("use.gui.view.classdiagram.class.minwidth");
        System.setProperty("use.gui.view.classdiagram.class.minheight","40");
        System.setProperty("use.gui.view.classdiagram.class.minwidth","140");
        MainWindow.setJavaFxCall(true);
        window=StepReplayProof.onEdt(()->MainWindow.create(session,org.tzi.use.runtime.impl.PluginRuntime.getInstance()));
        view=StepReplayProof.onEdt(()->{facade.showObjectDiagram();var diagram=window.getObjectDiagrams().getFirst();
            diagram.getDiagram().getOptions().setDoAutoLayout(false);return diagram;});
        StepReplayProof.onEdt(()->{system.getEventBus().register(this);return null;});
    }

    @com.google.common.eventbus.Subscribe public void changed(org.tzi.use.uml.sys.events.AtomicStateChangedEvent event) {
        try {assertSame(state,system.state(),"Runtime replaced the active MSystemState");assertGraph(view.getDiagram());atomicChecks++;
            for(var object:state.allObjects())if(DomainProjection.kind(object.cls()).equals("artifact"))
                object.state(state).attributeValueMap().forEach((a,v)->{
                    var values=artifactValues.computeIfAbsent(object.cls().name()+"::"+object.state(state).attributeValue("semanticId")+"::"+a.name(),key->new LinkedHashSet<>());
                    if(values.size()<256)values.add(v.toString());});
        }catch(AssertionError error){if(atomicErrors.size()<16)atomicErrors.add(error.getMessage());}
    }

    public void recordAttributeObservations(String className,String attribute,String before,String after)throws Exception {
        StepReplayProof.onEdt(()->{
            var values=artifactValues.entrySet().stream().filter(e->e.getKey().startsWith(className+"::")&&e.getKey().endsWith("::"+attribute)).toList();
            boolean observedBefore=values.stream().anyMatch(e->e.getValue().contains(before));
            boolean observedAfter=values.stream().anyMatch(e->e.getValue().contains(after));
            assertTrue(observedAfter,"Accepted completed native state never contains "+after);
            Files.write(directory.resolve("attribute-observations.json"),CanonicalJson.encode(Map.of("class",className,"attribute",attribute,
                    "before",before,"after",after,"observedBefore",observedBefore,"observedAfter",observedAfter,
                    "diagnostic",observedBefore?"BOTH_VALUES_OBSERVED":"BEFORE_VALUE_NOT_OBSERVED_AT_ACCEPTED_CUTS")));
            return null;
        });
    }

    public void capture(String phase)throws Exception {
        StepReplayProof.onEdt(()->{
            assertSame(system,session.system());assertTrue(atomicErrors.isEmpty(),atomicErrors.toString());
            assertSame(system,view.system());assertSame(state,system.state());
            var diagram=view.getDiagram();
            // Check the existing view before showing anything: stale/extra/hidden nodes cannot mask a bug.
            assertGraph(diagram);
            String before=new NativeUseSoilExporter().export(system).commands();
            var snapshot=facade.verificationSnapshot();
            assertEquals(state.allObjects().stream().map(MObject::name).collect(java.util.stream.Collectors.toSet()),snapshot.image().objects().keySet(),"Facade snapshot / diagram object parity");
            var objects=state.allObjects().stream().sorted(Comparator.comparing(MObject::name)).toList();
            var links=state.allLinks().stream().sorted(Comparator.comparing(ObjectDiagramLifecycleEvidence::linkKey)).toList();
            var rows=objects.stream().map(o->{var values=new TreeMap<String,String>();
                o.state(state).attributeValueMap().forEach((a,v)->values.put(a.name(),v.toString()));
                return Map.of("name",o.name(),"class",o.cls().name(),"kind",DomainProjection.kind(o.cls()),"values",values);}).toList();
            var linkRows=links.stream().map(l->Map.of("association",l.association().name(),
                "participants",l.linkedObjects().stream().map(MObject::name).toList())).toList();
            var summary=new LinkedHashMap<String,Object>();
            summary.put("phase",phase);summary.put("version",snapshot.currentVersion());
            summary.put("workflow",facade.workflowStatus().state());summary.put("objects",objects.size());summary.put("links",links.size());
            summary.put("synchronization",facade.runtimeStatus().state().name());
            summary.put("freshness",snapshot.result().freshness());summary.put("coverage",snapshot.result().coverage());
            summary.put("control",facade.runtimeControlState()==null?"UNAVAILABLE":facade.runtimeControlState().state().name());
            summary.put("nativeSystemIdentity",System.identityHashCode(system));summary.put("nativeStateIdentity",System.identityHashCode(state));
            summary.put("exactNativeGraph",true);summary.put("structureValid",state.checkStructure(new java.io.PrintWriter(new java.io.StringWriter()),true));
            var counts=new TreeMap<String,Long>();objects.forEach(o->counts.merge(o.cls().name(),1L,Long::sum));summary.put("counts",counts);
            Files.write(directory.resolve(phase+".json"),CanonicalJson.encode(Map.of("summary",summary,"objects",rows,"links",linkRows)));
            Files.writeString(directory.resolve(phase+".cmd"),before);
            render(diagram,objects,phase+"-all",phase+" — all native objects and links",false);
            var domain=objects.stream().filter(o->!Set.of("belief","agent-goal","organisational-goal","mission").contains(DomainProjection.kind(o.cls()))).toList();
            render(diagram,domain,phase+"-domain",phase+" — native Agent / Workspace / Artifact / Organization context",true);
            diagram.showAll();diagram.showAllLinks();assertGraph(diagram);assertEquals(before,new NativeUseSoilExporter().export(system).commands());
            phases.add(summary);Files.write(directory.resolve("summary.json"),CanonicalJson.encode(phases));return null;
        });
    }

    private void assertGraph(NewObjectDiagram diagram) {
        var visible=diagram.getVisibleData();var hidden=(NewObjectDiagram.ObjectDiagramData)diagram.getHiddenData();
        assertTrue(Collections.disjoint(visible.fObjectToNodeMap.keySet(),hidden.fObjectToNodeMap.keySet()));
        var objects=new HashSet<>(visible.fObjectToNodeMap.keySet());objects.addAll(hidden.fObjectToNodeMap.keySet());
        assertEquals(new HashSet<>(state.allObjects()),objects,"Native diagram has missing/extra/stale objects");
        java.util.stream.Stream.concat(visible.fObjectToNodeMap.values().stream(),hidden.fObjectToNodeMap.values().stream())
            .forEach(node->assertSame(state.objectByName(node.object().name()),node.object(),"Stale native ObjectNode binding: "+node.object().name()));
        var links=graphLinks(visible);assertTrue(Collections.disjoint(links,graphLinks(hidden)));links.addAll(graphLinks(hidden));
        assertEquals(new HashSet<>(state.allLinks()),links,"Native diagram has missing/extra/stale links");
        assertTrue(state.allLinks().stream().allMatch(l->state.allObjects().containsAll(l.linkedObjects())),"Orphan native endpoint");
    }
    private static Set<MLink> graphLinks(NewObjectDiagram.ObjectDiagramData data) {
        var result=new HashSet<MLink>(data.fBinaryLinkToEdgeMap.keySet());result.addAll(data.fNaryLinkToDiamondNodeMap.keySet());
        result.addAll(data.fLinkObjectToNodeEdge.keySet());return result;
    }
    private static String linkKey(MLink link){return link.association().name()+":"+link.linkedObjects().stream().map(MObject::name).toList();}

    private void render(NewObjectDiagram diagram,List<MObject> selected,String file,String title,boolean domain)throws Exception {
        diagram.showAll();diagram.showAllLinks();
        var selection=new HashSet<>(selected);for(var object:state.allObjects())if(!selection.contains(object))diagram.hideObject(object);
        var data=diagram.getVisibleData();assertEquals(selection,data.fObjectToNodeMap.keySet());
        var expected=state.allLinks().stream().filter(l->selection.containsAll(l.linkedObjects())
            && (!(l instanceof MObject o)||selection.contains(o))).collect(java.util.stream.Collectors.toSet());
        assertEquals(expected,graphLinks(data),"Filtered native diagram must show every link between visible endpoints");
        boolean attributes=diagram.getOptions().isShowAttributes(),roles=diagram.getOptions().isShowRolenames(),names=diagram.getOptions().isShowAssocNames();
        try {
            diagram.getOptions().setShowAttributes(false);diagram.getOptions().setShowRolenames(false);diagram.getOptions().setShowAssocNames(false);
            diagram.invalidateContent(false);var measure=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);var probe=measure.createGraphics();
            probe.setClip(0,0,2,2);diagram.drawDiagram(probe);probe.dispose();
            var nodes=data.fObjectToNodeMap.values().stream().sorted(Comparator.comparingInt((org.tzi.use.gui.views.diagrams.objectdiagram.ObjectNode n)->column(n.object()))
                .thenComparing(n->n.object().name())).toList();
            int columns=domain?4:(nodes.size()>80?6:4);
            int cellWidth=(int)Math.ceil(nodes.stream().mapToDouble(n->n.getWidth()).max().orElse(180))+110;
            int cellHeight=(int)Math.ceil(nodes.stream().mapToDouble(n->n.getHeight()).max().orElse(40))+90;
            int[] occupied=new int[columns];int maximum=0;
            for(int i=0;i<nodes.size();i++) {int c=domain?column(nodes.get(i).object()):i%columns;int r=occupied[c]++;
                nodes.get(i).setStrategy(org.tzi.use.gui.views.diagrams.elements.positioning.StrategyFixed.instance);
                nodes.get(i).setPosition(40+c*cellWidth,110+r*cellHeight);maximum=Math.max(maximum,r+1);}
            int width=columns*cellWidth+80,height=Math.max(300,maximum*cellHeight+150);
            assertTrue((long)width*height<90_000_000L,"Native overview too large");diagram.setSize(width,height);diagram.invalidateContent(false);
            var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();graphics.setClip(0,0,width,height);
            graphics.setColor(Color.WHITE);graphics.fillRect(0,0,width,height);diagram.drawDiagram(graphics);
            // Verify the text values maintained by native ObjectNode after its normal paint.
            var attributesField=org.tzi.use.gui.views.diagrams.objectdiagram.ObjectNode.class.getDeclaredField("fAttributes");attributesField.setAccessible(true);
            var valuesField=org.tzi.use.gui.views.diagrams.objectdiagram.ObjectNode.class.getDeclaredField("fValues");valuesField.setAccessible(true);
            for(var node:nodes) {
                @SuppressWarnings("unchecked") var nativeAttributes=(List<org.tzi.use.uml.mm.MAttribute>)attributesField.get(node);
                var nativeValues=(String[])valuesField.get(node);assertEquals(nativeAttributes.size(),nativeValues.length);
                for(int i=0;i<nativeAttributes.size();i++) {var a=nativeAttributes.get(i);var v=node.object().state(state).attributeValue(a);
                    String value=v instanceof org.tzi.use.uml.ocl.value.EnumValue e?"#"+e.value():v.toString();
                    assertEquals((a.isDerived()?"/":"")+a.name()+"="+value,nativeValues[i],"Stale native attribute: "+node.object().name()+"::"+a.name());}
                assertTrue(node.getX()>=0&&node.getY()>=0&&node.getX()+node.getWidth()<=width&&node.getY()+node.getHeight()<=height,"Clipped native node");
            }
            graphics.setColor(Color.BLACK);graphics.setFont(new Font("SansSerif",Font.BOLD,18));graphics.drawString(title,30,30);
            graphics.setFont(new Font("SansSerif",Font.PLAIN,15));graphics.drawString(selected.size()+" visible / "+state.numObjects()+" native objects; "+expected.size()+" visible / "+state.allLinks().size()+" native links. Values: "+file.replace("-all","").replace("-domain","")+".json",30,57);
            graphics.dispose();assertTrue(ImageIO.write(image,"png",directory.resolve(file+".png").toFile()));
        } finally {diagram.showAll();diagram.showAllLinks();diagram.getOptions().setShowAttributes(attributes);diagram.getOptions().setShowRolenames(roles);diagram.getOptions().setShowAssocNames(names);}
    }
    private static int column(MObject object) {return switch(DomainProjection.kind(object.cls())) {
        case "agent-program"->0;case "workspace","organisation","group"->1;case "artifact","scheme"->2;default->3;};}

    /** Compare actual source endpoints at an explicitly accepted authoritative resync cut. */
    public void assertSourceRelations(RuntimeSnapshot source,String phase)throws Exception {
        StepReplayProof.onEdt(()->{
            var cut=facade.verificationSnapshot();var aliases=new HashMap<String,String>();
            cut.image().objects().values().forEach(o->o.exactIdentities().forEach(id->{var previous=aliases.putIfAbsent(id,o.name());assertTrue(previous==null||previous.equals(o.name()),"Ambiguous identity: "+id);}));
            java.util.function.Function<String,String> exact=id->{var name=aliases.get(id);assertNotNull(name,"Source endpoint unavailable: "+id);return name;};
            var domainArtifacts=source.facts().stream().map(f->f.values()).filter(v->"UPSERT_CARTAGO_ARTIFACT".equals(v.get("normalizedEventKind")))
                .map(v->(String)v.get("semanticId")).filter(aliases::containsKey).filter(id->DomainProjection.kind(state.objectByName(aliases.get(id)).cls()).equals("artifact"))
                .collect(java.util.stream.Collectors.toSet());
            String joins=DomainProjection.relation("memberOf","Agent","Workspace"),focuses=DomainProjection.relation("focuses","Agent","Artifact"),
                commitments=DomainProjection.relation("committedTo","Agent","Mission"),contains=DomainProjection.relation("locatedIn","Workspace","Artifact"),
                responsible=DomainProjection.relation("responsibleFor","Group","Scheme"),schemes=DomainProjection.relation("containsScheme","Organization","Scheme");
            var selected=new HashSet<>(List.of(joins,focuses,commitments,contains,responsible,schemes));var expected=new TreeSet<String>();
            for(var fact:source.facts()) {var v=fact.values();String kind=(String)v.get("normalizedEventKind");
                if("UPSERT_CARTAGO_AGENT_IDENTITY".equals(kind)&&v.get("agentDeclarationSemanticId") instanceof String)
                    expected.add(joins+":"+List.of(exact.apply((String)v.get("semanticId")),exact.apply((String)v.get("workspaceSemanticId"))));
                if("UPSERT_CARTAGO_ARTIFACT".equals(kind)&&domainArtifacts.contains(v.get("semanticId")))
                    expected.add(contains+":"+List.of(exact.apply((String)v.get("workspaceSemanticId")),exact.apply((String)v.get("semanticId"))));
                if("SET_CARTAGO_FOCUS".equals(kind)&&Boolean.TRUE.equals(v.get("focused"))&&domainArtifacts.contains(v.get("artifactSemanticId")))
                    expected.add(focuses+":"+List.of(exact.apply((String)v.get("agentSemanticId")),exact.apply((String)v.get("artifactSemanticId"))));
                if("UPSERT_MOISE_GROUP".equals(kind))for(Object entry:(List<?>)v.get("players")) {
                    var p=CanonicalJson.object(entry);var a=MoiseDomainProjection.roleAssociation(system.model(),(String)v.get("organisationSpecSemanticId"),(String)v.get("groupSpecSemanticId"),(String)p.get("roleSemanticId"));
                    assertNotNull(a);expected.add(a.name()+":"+List.of(exact.apply((String)p.get("agentSemanticId")),exact.apply((String)v.get("semanticId"))));}
                if("UPSERT_MOISE_SCHEME".equals(kind)) {
                    String id=(String)v.get("semanticId");expected.add(schemes+":"+List.of(exact.apply((String)v.get("organisationSemanticId")),exact.apply(id)));
                    for(Object group:(List<?>)v.get("responsibleGroupSemanticIds"))expected.add(responsible+":"+List.of(exact.apply((String)group),exact.apply(id)));
                    for(Object entry:(List<?>)v.get("commitments")) {var p=CanonicalJson.object(entry);String mission=MoiseDomainProjection.functionalObjectId("mission",id,(String)p.get("missionSemanticId"));
                        expected.add(commitments+":"+List.of(exact.apply((String)p.get("agentSemanticId")),exact.apply(mission)));}
                }
            }
            var observed=state.allLinks().stream().filter(l->selected.contains(l.association().name())||l.association().getAnnotation(MoiseDomainProjection.ROLE_ASSOCIATION)!=null)
                .map(ObjectDiagramLifecycleEvidence::linkKey).collect(java.util.stream.Collectors.toCollection(TreeSet::new));
            assertEquals(expected,observed,"Exact authoritative source/native relation parity at "+phase);
            Files.write(directory.resolve(phase+"-source-parity.json"),CanonicalJson.encode(Map.of("snapshot",source.snapshotId(),"exactParity",true,"relations",expected)));
            return null;
        });
    }

    @Override public void close()throws Exception {
        if(closed)return;closed=true;
        StepReplayProof.onEdt(()->{system.getEventBus().unregister(this);
            Files.write(directory.resolve("atomic-checks.json"),CanonicalJson.encode(Map.of("checks",atomicChecks,"errors",atomicErrors,"artifactValues",artifactValues)));
            view.detachModel();window.dispose();MainWindow.setJavaFxCall(false);assertTrue(atomicErrors.isEmpty(),atomicErrors.toString());return null;});
        restore("use.gui.view.classdiagram.class.minheight",previousHeight);restore("use.gui.view.classdiagram.class.minwidth",previousWidth);
    }
    private static void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
}
