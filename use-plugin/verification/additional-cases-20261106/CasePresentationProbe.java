package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;

/** Headless rendering and scope checks of authentic native checkpoints only. */
public class CasePresentationProbe {
    static Map<String,Object> read(Path file)throws Exception{try(var input=new java.util.zip.GZIPInputStream(Files.newInputStream(file))){return CanonicalJson.object(CanonicalJson.decode(input.readAllBytes()));}}
    static Component find(Container root,String name){for(var c:root.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container child){var value=find(child,name);if(value!=null)return value;}}return null;}
    static void layout(Container root){root.doLayout();for(var c:root.getComponents())if(c instanceof Container child)layout(child);}
    public static void main(String[] args)throws Exception {
        Path models=Path.of(args[0]),evidence=Path.of(args[1]);
        var manifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(models.resolve("case-manifest.json"))));var inputs=CanonicalJson.object(manifest.get("inputs"));
        var receipt=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(evidence.resolve("audit-receipt.json"))));
        var recorded=new ArrayList<Map<String,Object>>();try(var files=Files.list(evidence)){for(var file:files.filter(p->p.getFileName().toString().startsWith("cut-")).toList())recorded.add(read(file));}
        recorded.sort(Comparator.comparingLong(d->((Number)CanonicalJson.object(d.get("identity")).get("sequence")).longValue()));
        var presenter=new ObservedConformancePresenter(new JaCaMoFacade(){public String status(){return "Authentic authored checkpoint replay";}});
        List<Cut> cuts=List.of();var coverage=new ArrayList<Object>();
        for(var data:recorded) {
            var nativeResult=RuntimeVerificationResult.fromMap(CanonicalJson.object(CanonicalJson.object(data.get("native")).get("result")));
            var identity=VerifiedCheckpointProtocol.Identity.decode(CanonicalJson.object(data.get("identity")));var payload=CanonicalJson.object(data.get("checks"));
            var checks=new TreeMap<String,VerifiedCheckpointProtocol.Check>();payload.forEach((id,value)->{var c=CanonicalJson.object(value);checks.put(id,new VerifiedCheckpointProtocol.Check((String)c.get("truth"),(String)c.get("applicability"),(String)c.get("diagnostic")));});
            var checkpoint=CanonicalJson.object(data.get("view"));String hash=VerifiedCheckpointProtocol.hash(Map.of("version",VerifiedCheckpointProtocol.INVENTORY,"nativeResultHash",nativeResult.resultHash(),"checks",payload));
            var view=new VerifiedCheckpointService.View("VERIFYING",identity.token(),(String)checkpoint.get("checkpoint"),"",identity.producerHash(),nativeResult.stateHash(),hash,identity.executionScope(),false,false,Map.of("identity",data.get("identity"),"barrier",data.get("barrier")));
            cuts=presenter.captureCheckpoint(new VerificationSnapshot(nativeResult.stateVersion(),nativeResult,null),view,checks);
            var assessment=VerificationDataCoverage.assess(ExecutionScopeViews.all(cuts.getLast()));assertEquals("COMPLETE",assessment.status(),assessment.unavailable().toString());
            coverage.add(Map.of("token",identity.token(),"nativeResultHash",nativeResult.resultHash(),"dataCoverage",assessment.status(),"rawSourceCoverage",nativeResult.coverage()));
        }
        var goals=new org.tzi.use.semantic.engine.frontend.IStarFrontend().compile(Files.readString(models.resolve((String)inputs.get("istar"))),"authored",org.tzi.use.semantic.engine.api.EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
        final var shown=cuts;var ui=new TreeMap<String,Object>();
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(goals);panel.setCuts(shown);panel.selectExecution(ExecutionScopeViews.ALL);
            String expected=((List<?>)receipt.get("reviewedFailures")).isEmpty()?"COMPLETE_OBSERVED":"VIOLATION";
            assertEquals(expected,((JLabel)find(panel,"observed-verdict")).getText());
            assertTrue(((JLabel)find(panel,"observed-quality")).getText().contains("Data coverage: COMPLETE"));
            var choices=ExecutionScopeViews.admittedChoices(shown);assertFalse(choices.isEmpty());
            var scoped=new TreeMap<String,Object>();for(var choice:choices.entrySet()){
                panel.selectExecution(choice.getKey());assertTrue(((JLabel)find(panel,"observed-quality")).getText().contains("Data coverage: COMPLETE"));
                scoped.put(choice.getValue(),((JLabel)find(panel,"observed-verdict")).getText());
            }
            panel.selectExecution(ExecutionScopeViews.ALL);var prepared=new PreparationCache().prepare(shown,ExecutionScopeViews.ALL,true,true);
            assertEquals(shown.size(),prepared.audit().size());
            ui.put("allVerdict",expected);ui.put("executions",scoped);ui.put("rawAuditCuts",prepared.audit().size());ui.put("visibleStates",prepared.milestones().size());
            for(String facet:List.of("csl","istar")) {
                var component=facet.equals("csl")?panel.cslVerificationPanel():panel.istarVerificationPanel();component.setSize(1400,820);layout(component);
                var image=new BufferedImage(1400,820,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();component.printAll(graphics);graphics.dispose();
                try{javax.imageio.ImageIO.write(image,"png",evidence.resolve(facet+"-verification.png").toFile());}catch(Exception e){throw new RuntimeException(e);}
            }
        });
        Files.write(evidence.resolve("presentation-receipt.json"),CanonicalJson.encode(Map.of("method","OFFSCREEN_AUTHENTIC_NATIVE_CUTS","manualDesktopAcceptance",false,"ui",ui,"cutCoverage",coverage)));
        System.out.println("CASE_PRESENTATION_PASS "+models.getFileName());System.exit(0);
    }
}
