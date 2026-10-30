package org.tzi.use.plugins.jacamo.ui;

import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.ui.use4goal.state.*;
import static org.junit.jupiter.api.Assertions.*;

class EnactmentViewsTest {
    @Test void realRecordedPlayersHaveCompactLabelsButKeepExactGraphIdentitiesAndWitnesses()throws Exception {
        byte[] bytes;try(var stream=new GZIPInputStream(getClass().getResourceAsStream("/semantics/recorded-enactment-result.json.gz"))){bytes=stream.readAllBytes();}
        var result=CanonicalJson.object(CanonicalJson.decode(bytes));
        assertEquals("d6b5ad5b59319e67db78125bbf8b3b8b699b63c227bd0ff5a8166bf80b58af60",result.get("resultHash"));
        var evidence=CanonicalJson.object(CanonicalJson.object(result.get("semantic")).get("observation"));
        var csl=new org.tzi.use.semantic.engine.frontend.CslFrontend().compile(Files.readString(Path.of("examples/auction/auction.csl")),"current").requireModel();
        var state=RuntimeStateViews.aol(csl,evidence,"!new Hidden('audit_only')");
        var projection=CanonicalJson.object(evidence.get("presentationState"));
        var recordedIds=new TreeSet<String>();CanonicalJson.object(projection.get("objects")).keySet().forEach(k->recordedIds.add(k.substring(k.indexOf(':')+1)));
        assertEquals(recordedIds,state.objects().keySet());
        var joins=((List<?>)projection.get("links")).stream().map(CanonicalJson::object).filter(l->"enacts".equals(l.get("association"))).toList();assertEquals(5,joins.size());
        for(var join:joins)for(Object id:(List<?>)join.get("participants"))assertEquals(join.get("via")+": "+state.objects().get(id).type(),state.objects().get(id).displayName());
        var layout=org.tzi.use.plugins.jacamo.ui.use4goal.aol.view.AolLayoutBuilder.build(state);
        assertTrue(layout.width<=1400,"The 5-player case should fit a normal results window: "+layout.width);
        assertTrue(layout.nodes.values().stream().allMatch(n->n.subtitle.contains("identity=")));
        assertEquals(5,layout.edges.stream().filter(e->"enacts".equals(e.label())).count());
        var defaults=new Properties();try(var stream=getClass().getResourceAsStream("/etc/use.properties")){defaults.load(stream);}
        var introduced=new HashSet<String>();for(String key:defaults.stringPropertyNames())if(System.getProperty(key)==null){introduced.add(key);System.setProperty(key,defaults.getProperty(key));}
        try {SwingUtilities.invokeAndWait(()->{
            var aol=new org.tzi.use.plugins.jacamo.ui.use4goal.aol.view.AolDiagram(new java.io.PrintWriter(java.io.Writer.nullWriter()));aol.setState(state);
            LiveGoalSummaryTest.renderPanel(new JPanel(new java.awt.BorderLayout()){{add(new JScrollPane(aol));}},"enactment-aol-final.png");aol.stopLayoutThread();
            var nativeModel=new org.tzi.use.plugins.jacamo.semantics.mapping.ocl.CslScopeModelAdapter().compile(csl).model();
            nativeModel.getClass("auction_capabilities").addAnnotation(new org.tzi.use.uml.mm.MElementAnnotation("CslEnactmentView",Map.of("version","SINGLE_AGENT_ENACTMENT/1.0","roles","auctioneer participant")));
            var view=org.tzi.use.plugins.jacamo.ui.use4goal.csl.CslView.embedded(csl,nativeModel);LiveGoalSummaryTest.renderPanel(view,"enactment-csl-final.png");view.detachModel();
        });}finally{introduced.forEach(System::clearProperty);}
    }
}
