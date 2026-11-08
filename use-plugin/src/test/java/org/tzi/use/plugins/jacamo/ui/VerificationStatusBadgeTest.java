package org.tzi.use.plugins.jacamo.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol.Check;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.VerifiedCheckpointService.View;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;

class VerificationStatusBadgeTest {
    static View view(String state, boolean reviewable, boolean pending) {
        return new View(state,"cut","task","reason","producer","use","result",Set.of("execution"),reviewable,pending,Map.of());
    }
    static Cut cut(String first, String second, String quality) {
        var rows=new ArrayList<Evaluation>();
        var contexts=new ArrayList<Map<String,Object>>();
        for(int i=0;i<2;i++){
            String context="execution-"+i;
            contexts.add(Map.of("context",context,"scopeView",Map.of("entities",Map.of("entity","identity-"+i))));
            rows.add(new Evaluation("root","",i==0?first:second,quality,"","",true,"GOAL_TASK","","player",context,"occurrence-"+i));
        }
        return new Cut("cut","","",rows,"COMPLETE_OBSERVED","CURRENT_OBSERVED",false,"","","",true,true,false,
                Map.of("requirementInventory",Map.of("selectedIntentionalElements",List.of("root")),"originalEngine",Map.of("contexts",contexts)));
    }
    static Map<String,Check> checks(String truth){return Map.of("required",new Check(truth,"APPLICABLE","original"));}

    @Test void completionRequiresEveryObservedExecutionAndQualifiedTerminalEvidence(){
        var badge=new VerificationStatusBadge();
        badge.displayed("run","cut",cut("FULFILLED","PENDING","KNOWN"),checks("PASS"));
        assertEquals("Running",badge.status(view("RUNNING",false,false),"RUNNING","LIVE").text());
        badge.displayed("run","cut",cut("FULFILLED","FULFILLED","UNKNOWN"),checks("PASS"));
        assertEquals("Running",badge.status(view("RUNNING",false,false),"RUNNING","LIVE").text());
        badge.displayed("run","cut",cut("FULFILLED","FULFILLED","KNOWN"),checks("PASS"));
        var status=badge.status(view("RUNNING",false,false),"RUNNING","LIVE");
        assertEquals("Completed",status.text());assertEquals(new Color(0x166534),status.foreground());
        var released=new View("RUNNING","","","released","","","",Set.of(),false,false,Map.of());
        assertEquals("Completed",badge.status(released,"RUNNING","LIVE").text());
        var another=new View("RUNNING","another-cut","","","","","",Set.of(),false,false,Map.of());
        assertEquals("Running",badge.status(another,"RUNNING","LIVE").text());
        assertEquals("Verifying",badge.status(view("VERIFYING",false,false),"RUNNING","LIVE").text());
        assertEquals("Paused · violation",badge.status(view("HELD_FAILURE",true,false),"RUNNING","LIVE").text());
    }
    @Test void historicalIssuesRemainAndNewOwnerResetsThem(){
        var badge=new VerificationStatusBadge();
        badge.displayed("run","earlier",cut("PENDING","PENDING","KNOWN"),checks("FAIL"));
        badge.displayed("run","cut",cut("FULFILLED","FULFILLED","KNOWN"),checks("PASS"));
        assertEquals("Completed with issues",badge.status(view("RUNNING",false,false),"RUNNING","LIVE").text());
        badge.displayed("new-run","cut",cut("FULFILLED","FULFILLED","KNOWN"),checks("PASS"));
        assertEquals("Completed",badge.status(view("RUNNING",false,false),"RUNNING","LIVE").text());
        assertEquals("Disconnected",badge.status(view("RUNNING",false,false),"EXITED","LIVE").text());
        assertEquals("Stopped",badge.status(view("RUNNING",false,false),"STOPPED","LIVE").text());
        assertEquals("Preparing",badge.status(view("RUNNING",false,false),"PREPARING","IMPORTING").text());
        badge.reset();assertEquals("Running",badge.status(view("RUNNING",false,false),"RUNNING","LIVE").text());
    }
    @Test void profilesWithoutAnExplicitRootSelectionUseAllRequiredNativeOccurrences(){
        var source=cut("FULFILLED","FULFILLED","KNOWN");
        var evidence=new HashMap<String,Object>(source.visualEvidence());
        evidence.put("requirementInventory",Map.of("selectedIntentionalElements",List.of()));
        var all=new Cut(source.id(),"","",source.evaluations(),source.coverage(),source.freshness(),false,"","","",true,false,false,evidence);
        var badge=new VerificationStatusBadge();badge.displayed("run","cut",all,checks("PASS"));
        assertEquals("Completed",badge.status(view("RUNNING",false,false),"RUNNING","LIVE").text());
    }
    @Test void scopedRootRequirementsCompleteWhenEachScopeFulfillsItsOwnSelectedTarget(){
        var rows=List.of(
                new Evaluation("do_auction","","FULFILLED","KNOWN","","",true,"GOAL_TASK","","bob","ctx-setup","occ-setup"),
                new Evaluation("O_root","","FULFILLED","KNOWN","","",true,"GOAL_TASK","","scheme","ctx-auction","occ-auction"));
        var contexts=List.of(
                Map.<String,Object>of("context","ctx-setup","scopeDefinition","initial-auction-setup","scopeView",Map.of("version","BOUND_EXECUTION_VIEW/2.0","complete",true,"domainBound",false,"definition","initial-auction-setup","owners",Map.of("bob",Map.of()),"bindings",Map.of("id","a1"))),
                Map.<String,Object>of("context","ctx-auction","scopeDefinition","auction-execution","scopeView",Map.of("definition","auction-execution","entities",Map.of("Auction:a1",Map.of("identity","source:a1")))));
        var inventory=Map.<String,Object>of(
                "selectedIntentionalElements",List.of("O_root","do_auction"),
                "scopeSelectedIntentionalElements",Map.of("initial-auction-setup",List.of("do_auction"),"auction-execution",List.of("O_root")));
        var cut=new Cut("cut","","",rows,"COMPLETE_OBSERVED","CURRENT_OBSERVED",false,"","","",true,true,false,
                Map.of("requirementInventory",inventory,"originalEngine",Map.of("contexts",contexts)));
        var badge=new VerificationStatusBadge();
        badge.displayed("run","cut",cut,checks("PASS"));
        assertEquals("Completed",badge.status(view("RUNNING",false,false),"RUNNING","LIVE").text());
    }
    @Test void badgeAndContinueAreAdjacentAtTheRightOfTheHeader()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var label=new JLabel("Running");var button=new JButton("Continue");
            var header=JaCaMoWorkbenchPanel.checkpointHeader(label,button);
            header.setSize(900,40);VerifiedCheckpointControlsTest.layout(header);
            assertSame(label.getParent(),button.getParent());
            assertEquals(BorderLayout.EAST,((BorderLayout)header.getLayout()).getConstraints(label.getParent()));
            assertTrue(button.getX()>label.getX());
            VerificationStatusBadge.apply(label,new VerificationStatusBadge().status(view("HELD_FAILURE",true,false),"RUNNING","LIVE"));
            assertTrue(label.isOpaque());assertEquals(new Color(0xB91C1C),label.getForeground());
        });
    }
    @Test void continuationOnlyAppearsWhenReviewIsActuallyAllowed()throws Exception {
        var current=new AtomicReference<>(view("RUNNING",false,false));
        var facade=new JaCaMoFacade(){public String status(){return "fixture";} public View verifiedCheckpointState(){return current.get();}};
        SwingUtilities.invokeAndWait(()->{
            var panel=JaCaMoWorkbenchPanel.authoredWorkflow(facade);
            var button=(JButton)VerifiedCheckpointControlsTest.find(panel,"acknowledge-checkpoint");
            assertEquals("Continue",button.getText());assertFalse(button.isVisible());
            current.set(view("HELD_UNVERIFIED",false,false));panel.refreshRuntime();assertFalse(button.isVisible());
            current.set(view("HELD_FAILURE",true,false));panel.refreshRuntime();assertTrue(button.isVisible());assertTrue(button.isEnabled());
            current.set(view("HELD_FAILURE",false,true));panel.refreshRuntime();assertFalse(button.isVisible());
            assertEquals("Continuing",((JLabel)VerifiedCheckpointControlsTest.find(panel,"verified-checkpoint-status")).getText());
            current.set(view("HELD_FAILURE",true,false));panel.refreshRuntime();assertTrue(button.isVisible());
            current.set(view("RUNNING",false,false));panel.refreshRuntime();assertFalse(button.isVisible());panel.removeNotify();
        });
    }
}
