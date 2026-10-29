package org.tzi.use.plugins.jacamo.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.CslScopeModelAdapter;
import org.tzi.use.plugins.jacamo.ui.use4goal.csl.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.istar.*;
import org.tzi.use.semantic.engine.api.EngineSemanticsProfile;
import org.tzi.use.semantic.engine.frontend.*;
import static org.junit.jupiter.api.Assertions.*;

class ReusedSemanticViewsTest {
    @Test void diagramQualityAndMultiplePlayersNeverBecomeAnInventedCombinedVerdict() {
        var occurrence=Map.<String,Object>of("sourceElement","task","aggregateStatus","FULFILLED","quality","KNOWN","player","alice","role","r1");
        var core=new LinkedHashMap<String,Object>();core.put("evaluated","PINNED_NATIVE_CUT");core.put("historyQuality","OBSERVED_PREFIX");core.put("contexts",List.of(Map.of("occurrences",Map.of("a",occurrence))));
        assertEquals("+",SemanticDiagramBadges.from(result(core)).get("task").glyph());
        core.put("qualityOverlay","HISTORY_GAP");assertEquals("?",SemanticDiagramBadges.from(result(core)).get("task").glyph());core.remove("qualityOverlay");
        var other=new LinkedHashMap<>(occurrence);other.put("player","bob");other.put("role","r2");
        core.put("contexts",List.of(Map.of("occurrences",Map.of("a",occurrence,"b",other))));
        assertEquals("2",SemanticDiagramBadges.from(result(core)).get("task").glyph());
        assertTrue(SemanticDiagramBadges.from(result(core)).get("task").tooltip().contains("not a combined verdict"));
        assertNotEquals(new Color(30,130,60),SemanticDiagramBadges.from(result(core)).get("task").color());
    }
    private static org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult result(Map<String,Object> core) {
        var evidence=new org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerificationEnvelope("1.0","JACAMO_DECLARATIVE_XML","config","cut",List.of(),Map.of(),Map.of(),List.of(),Map.of("originalEngine",core));
        var now=java.time.Instant.EPOCH;
        return new org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult("run",1,"revision",1,"event","source",1,now,now,now,"checkpoint","constraints","state",List.of(),0,"PARTIAL","CURRENT_OBSERVED","",evidence);
    }
    @Test void selectedSourceClosureHasPinnedAttributionAndNoDuplicateCoreOrLegacyBackend()throws Exception {
        var manifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(Path.of("src/main/resources/META-INF/use4goal-view-provenance.json"))));
        var files=(List<?>)manifest.get("files");assertEquals(30,files.size());
        try(var archive=new ZipFile("src/test/resources/semantics/use4goal-selected-views.zip")) {
            for(var raw:files) {
                var file=CanonicalJson.object(raw);String target=(String)file.get("targetPath"),upstream=(String)file.get("upstreamPath");
                byte[] original=archive.getInputStream(archive.getEntry(upstream)).readAllBytes();
                assertEquals(file.get("sourceSha256"),hash(original));assertEquals(file.get("targetSha256"),hash(Files.readAllBytes(Path.of(target))),target);
                String text=Files.readString(Path.of(target));
                for(String forbidden:List.of("new MSystem(","new ModelFactory(","Csl2UseTranslator","CslSystemState","dsl.bpmn","dsl.aol"))assertFalse(text.contains(forbidden),target+":"+forbidden);
                if(((List<?>)file.get("patches")).size()==1) {
                    String expected=new String(original,java.nio.charset.StandardCharsets.UTF_8);
                    for(var entry:CanonicalJson.object(manifest.get("relocations")).entrySet())expected=expected.replace(entry.getKey(),(String)entry.getValue());
                    if(upstream.contains("/gui/"))expected=expected.replace("package org.vnu.sme.goal.gui;","package org.tzi.use.plugins.jacamo.ui.use4goal.gui;");
                    assertEquals(expected.replace("\r\n","\n"),text.replace("\r\n","\n"),target);
                }
            }
        }
    }
    @Test void originalViewsRenderAuthoredGraphBorrowInstalledModelAndDetachOnReimport()throws Exception {
        Path inputs=Path.of("src/test/resources/auction-reference/authored-v1");
        var csl=new CslFrontend().compile(Files.readString(inputs.resolve("auction_csl.csl")),"authored.csl").requireModel();
        var goals=new IStarFrontend().compile(Files.readString(inputs.resolve("auction_istar_final.istar")),"authored.istar",EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
        var first=new CslScopeModelAdapter().compile(csl);var second=new CslScopeModelAdapter().compile(csl);
        var current=new java.util.concurrent.atomic.AtomicReference<>(new JaCaMoFacade.SemanticModels(csl,goals,first.model(),"program"));
        var facade=new JaCaMoFacade(){public String status(){return "view component test";}public SemanticModels semanticModels(){return current.get();}};
        // Same bundled GUI properties loaded by USE startup. Restore test globals;
        // this component gate does not read/write the user's USE configuration.
        var defaults=new Properties();try(var stream=getClass().getResourceAsStream("/etc/use.properties")){assertNotNull(stream);defaults.load(stream);}
        var introduced=new HashSet<String>();for(String name:defaults.stringPropertyNames())if(System.getProperty(name)==null){introduced.add(name);System.setProperty(name,defaults.getProperty(name));}
        try {SwingUtilities.invokeAndWait(()->{
            var panel=new JaCaMoWorkbenchPanel(facade,message->fail(message));
            assertNotNull(find(panel,org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.class));
            CslView old=find(panel,CslView.class);IStarView rationale=find(panel,IStarView.class);
            assertSame(first.model(),old.nativeModel());
            var structure=CslLayoutBuilder.build(csl);assertEquals(2,structure.edges.stream().filter(e->e.kind()==CslEdgeKind.GENERALIZATION).count());
            var intentional=IStarLayoutBuilder.build(goals,IStarViewMode.SR);assertTrue(intentional.nodes.containsKey("A_decide_t2"));assertFalse(intentional.nodes.containsKey("A_decide_t1"));
            try {render(old,"P07-csl-view.png");render(rationale,"P07-istar-view.png");}catch(Exception e){throw new RuntimeException(e);}
            current.set(new JaCaMoFacade.SemanticModels(csl,goals,second.model(),"program"));panel.refreshRuntime();
            assertNull(old.nativeModel());assertSame(second.model(),find(panel,CslView.class).nativeModel());assertNotSame(rationale,find(panel,IStarView.class));
            current.set(null);panel.refreshRuntime();assertFalse(contains(panel,CslView.class));assertFalse(contains(panel,IStarView.class));
        });}finally {introduced.forEach(System::clearProperty);}
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void render(org.tzi.use.gui.views.PrintableView view,String name)throws Exception {
        // An export from a not-yet-realized Swing component otherwise paints a
        // zero-sized canvas. Exercise the real diagram at its native bounds.
        var diagram=find((Container)view,org.tzi.use.gui.views.diagrams.DiagramView.class);
        var measure=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);var sizing=measure.createGraphics();
        try {for(int pass=0;pass<2;pass++){diagram.setSize(diagram.getPreferredSize());diagram.drawDiagram(sizing);}}finally{sizing.dispose();}
        diagram.setSize(diagram.getPreferredSize());diagram.doLayout();
        int width=1600,height=1000;var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();graphics.setColor(Color.WHITE);graphics.fillRect(0,0,width,height);
        double scale=Math.min((width-20)/Math.max(1,view.getContentWidth()),(height-20)/Math.max(1,view.getContentHeight()));graphics.translate(10,10);graphics.scale(scale,scale);view.export(graphics);graphics.dispose();
        long ink=0;for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((image.getRGB(x,y)&0xffffff)!=0xffffff)ink++;
        assertTrue(ink>1000,"Export must contain actual model glyphs and edges, not a blank bitmap: "+ink);
        Path target=Path.of("verification/authored-models",name);Files.createDirectories(target.getParent());assertTrue(ImageIO.write(image,"png",target.toFile()));
    }
    private static <T> T find(Container parent,Class<T> type){for(var c:parent.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container nested&&contains(nested,type))return find(nested,type);}throw new AssertionError(type.getName());}
    private static boolean contains(Container parent,Class<?> type){for(var c:parent.getComponents()){if(type.isInstance(c))return true;if(c instanceof Container nested&&contains(nested,type))return true;}return false;}
}
