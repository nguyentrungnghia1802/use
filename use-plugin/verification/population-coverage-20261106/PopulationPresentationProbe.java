package org.tzi.use.plugins.jacamo;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offscreen replay of authentic cuts; never edits a result or a native marking. */
public class PopulationPresentationProbe {
    static Map<String,Object> read(Path path)throws Exception {
        try(var input=new java.util.zip.GZIPInputStream(Files.newInputStream(path))){return CanonicalJson.object(CanonicalJson.decode(input.readAllBytes(),16*1024*1024,128,16*1024*1024));}
    }
    static Component find(Container root,String name){for(Component c:root.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container child){var found=find(child,name);if(found!=null)return found;}}return null;}
    static void layout(Container root){root.doLayout();for(var c:root.getComponents())if(c instanceof Container child)layout(child);}
    public static void main(String[] args)throws Exception {
        Path run=Path.of(args[0]),output=Path.of(args[1]);
        var receipt=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(run.resolve("audit-receipt.json"))));
        var recorded=new ArrayList<Map<String,Object>>();try(var paths=Files.list(run)){for(var p:paths.filter(p->p.getFileName().toString().startsWith("cut-")).toList())recorded.add(read(p));}
        recorded.sort(Comparator.comparingLong(d->((Number)CanonicalJson.object(d.get("identity")).get("sequence")).longValue()));
        var presenter=new ObservedConformancePresenter(new JaCaMoFacade(){public String status(){return "Authentic frozen source checkpoints";}});
        List<Cut> cuts=List.of();var nativeRows=new ArrayList<Object>();
        for(var data:recorded) {
            var frozen=CanonicalJson.object(data.get("native"));var nativeResult=RuntimeVerificationResult.fromMap(CanonicalJson.object(frozen.get("result")));
            var compatibility=nativeResult.outcomes().stream().filter(r->r.constraintId().endsWith("::CslV4RoleCompatibility")).findFirst().orElseThrow();
            assertEquals(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.PASS,compatibility.outcome());
            assertTrue(compatibility.diagnostic().contains("POPULATION_COMPLETE"));
            var checks=new TreeMap<String,VerifiedCheckpointProtocol.Check>();var payload=CanonicalJson.object(data.get("checks"));
            payload.forEach((id,value)->{var c=CanonicalJson.object(value);checks.put(id,new VerifiedCheckpointProtocol.Check((String)c.get("truth"),(String)c.get("applicability"),(String)c.get("diagnostic")));});
            var identity=VerifiedCheckpointProtocol.Identity.decode(CanonicalJson.object(data.get("identity")));
            String resultHash=VerifiedCheckpointProtocol.hash(Map.of("version",VerifiedCheckpointProtocol.INVENTORY,"nativeResultHash",nativeResult.resultHash(),"checks",payload));
            var checkpoint=CanonicalJson.object(data.get("view"));
            var view=new VerifiedCheckpointService.View("VERIFYING",identity.token(),(String)checkpoint.get("checkpoint"),"",identity.producerHash(),nativeResult.stateHash(),resultHash,identity.executionScope(),false,false,
                    Map.of("identity",data.get("identity"),"barrier",data.get("barrier")));
            cuts=presenter.captureCheckpoint(new VerificationSnapshot(nativeResult.stateVersion(),nativeResult,null),view,checks);
            var assessment=VerificationDataCoverage.assess(ExecutionScopeViews.all(cuts.getLast()));
            assertEquals("COMPLETE",assessment.status(),assessment.unavailable().toString());
            assertEquals("INCOMPLETE",nativeResult.coverage(),"Historical source coverage is intentionally not upgraded");
            nativeRows.add(Map.of("token",identity.token(),"nativeResultHash",nativeResult.resultHash(),"compatibility",compatibility.outcome().name(),"dataCoverage",assessment.status(),"rawSourceCoverage",nativeResult.coverage()));
        }
        final var shown=cuts;
        var goals=new org.tzi.use.semantic.engine.frontend.IStarFrontend().compile(Files.readString(Path.of("examples/auction/auction.istar")),"authored",org.tzi.use.semantic.engine.api.EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
        var ui=new TreeMap<String,Object>();
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(goals);panel.setCuts(shown);panel.selectExecution(ExecutionScopeViews.ALL);
            var verdict=(JLabel)find(panel,"observed-verdict");var quality=(JLabel)find(panel,"observed-quality");
            if(((Number)receipt.get("reviewedFailedCuts")).intValue()>0)assertEquals("VIOLATION",verdict.getText(),"Review must not erase a real failure from All");
            assertTrue(quality.getText().contains("Data coverage: COMPLETE"));assertTrue(quality.getToolTipText().contains("historical/global observation): INCOMPLETE"));
            ui.put("allVerdict",verdict.getText());ui.put("dataCoverageLabel",quality.getText());
            var choices=ExecutionScopeViews.admittedChoices(shown);assertEquals(2,choices.size());
            var scoped=new TreeMap<String,Object>();for(var entry:choices.entrySet()){
                panel.selectExecution(entry.getKey());assertTrue(((JLabel)find(panel,"observed-quality")).getText().contains("Data coverage: COMPLETE"));
                scoped.put(entry.getValue(),((JLabel)find(panel,"observed-verdict")).getText());
            }
            ui.put("scopedVerdicts",scoped);panel.selectExecution(ExecutionScopeViews.ALL);
            var prepared=new PreparationCache().prepare(shown,ExecutionScopeViews.ALL,true,true);assertEquals(shown.size(),prepared.audit().size());
            ui.put("retainedAuditCuts",prepared.audit().size());ui.put("displayedStates",prepared.milestones().size());
            for(String facet:List.of("csl","istar")) {
                var component=facet.equals("csl")?panel.cslVerificationPanel():panel.istarVerificationPanel();
                component.setSize(1400,820);layout(component);var image=new BufferedImage(1400,820,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();component.printAll(graphics);graphics.dispose();
                try{javax.imageio.ImageIO.write(image,"png",output.resolve(facet+"-verification-coverage.png").toFile());}catch(Exception e){throw new RuntimeException(e);}
            }
        });
        var result=Map.of("method","OFFSCREEN_AUTHENTIC_CHECKPOINT_PRESENTATION","manualDesktopAcceptance",false,"ui",ui,"cuts",nativeRows);
        Files.write(output.resolve("presentation-receipt.json"),CanonicalJson.encode(result));
        System.out.println("AUTHENTIC_POPULATION_COVERAGE_PRESENTATION_PASS");System.exit(0);
    }
}
