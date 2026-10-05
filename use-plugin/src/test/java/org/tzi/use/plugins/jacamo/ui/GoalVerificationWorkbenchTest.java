package org.tzi.use.plugins.jacamo.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.RuntimeConstraintPolicy;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class GoalVerificationWorkbenchTest {
    private static final String CONSTRAINT="EXTERNAL:OrganizationalGoal::Consistency";
    private static VerificationSnapshot cut(String id,long sequence,String value,VerificationSnapshot.SynchronizationState lifecycle,boolean stale) {
        var object=new VerificationSnapshot.ObjectState("goalOccurrence","OrganizationalGoal","exact-goal",List.of("board-uuid/goal"),
                Map.of("runtimeState",value),Map.of("runtimeState","String"));
        var result=new RuntimeVerificationResult("session",1,"revision",sequence,"event","moise",sequence,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,
                "checkpoint","set-hash","state-hash",List.of(),1,"COMPLETE_OBSERVED",stale?"STALE":"CURRENT_OBSERVED","");
        return new VerificationSnapshot(sequence,result,null,new VerificationSnapshot.Metadata(id,sequence,Instant.EPOCH,1,CheckpointType.SNAPSHOT,Set.of(),
                lifecycle,Map.of(),Map.of(),Completeness.COMPLETE,Map.of("moise.domain",Completeness.COMPLETE),Map.of()),
                new VerificationSnapshot.StateImage(Map.of(object.name(),object),List.of(),""));
    }
    private static VerificationViolation violation(String id,VerificationSnapshot failure,VerificationSnapshot confirmation) {
        return new VerificationViolation(id,CONSTRAINT,"Consistency",VerificationOutcome.FAIL,RuntimeConstraintPolicy.Severity.HARD,
                RuntimeConstraintPolicy.Enforcement.PAUSE_ON_FAIL,CheckpointType.AFTER_MUTATION,failure.snapshotId(),failure.currentVersion(),1,Instant.EPOCH,
                "goalOccurrence",failure.image().objects(),Map.of("conditionTruth","true"),Map.of("conditionTruth","false"),
                List.of(new VerificationViolation.SourceTrace("organization.xml",0,"exact-goal",List.of("board-uuid/goal"),"M11","goalOccurrence")),
                RuntimeControlContract.State.PAUSED,VerificationViolation.Confirmation.CONFIRMED,confirmation.snapshotId(),"");
    }
    private static final class Facade implements JaCaMoFacade {
        VerificationSnapshot failure=cut("failure",2,"SATISFIED",VerificationSnapshot.SynchronizationState.LIVE,false);
        VerificationSnapshot before=cut("before",1,"WAITING",VerificationSnapshot.SynchronizationState.LIVE,false);
        VerificationSnapshot confirmation=cut("confirmation",3,"ENABLED",VerificationSnapshot.SynchronizationState.LIVE,false);
        VerificationSnapshot current=confirmation;
        RuntimeControlService.View control=new RuntimeControlService.View(RuntimeControlContract.State.PAUSED,true,true,false,Set.of("one","two"),Set.of("one","two"),"",RuntimeControlContract.LIMITATION);
        List<VerificationViolation> violations=List.of(violation("first",failure,confirmation));
        AtomicInteger resumes=new AtomicInteger();String source="";
        public String status(){return "Ready";}
        public VerificationSnapshot verificationSnapshot(){return current;}
        public VerificationSnapshot failingSnapshot(){return failure;}
        public VerificationSnapshot confirmationSnapshot(){return confirmation;}
        public VerificationSnapshot previousFailureSnapshot(){return before;}
        public VerificationSnapshot lastPassingBeforeFailure(){return before;}
        public RuntimeControlService.View runtimeControlState(){return control;}
        public List<VerificationViolation> runtimeViolations(){return violations;}
        public CompletableFuture<RuntimeControlContract.Status> resumeRuntime(){resumes.incrementAndGet();return CompletableFuture.completedFuture(null);}
        public String sourceExcerpt(Path file,int line){source=file+":"+line;return "Exact file; line unavailable";}
        public GoalViewSnapshot goalView() {
            var goal=new GoalViewSnapshot.Goal("goalOccurrence","exact-goal","spec-goal","decide","SATISFIED","Official typed observation","",1,"",List.of(),
                    List.of(),List.of(),List.of(),List.of(new GoalViewSnapshot.Source("organization.xml",0,"exact-goal","M11")),violations,"FAIL");
            return new GoalViewSnapshot(List.of(new GoalViewSnapshot.Scheme("schemeInstance","scheme","exact-scheme","spec-scheme","board-uuid",true,"{}",List.of(),List.of(goal))),"LIVE","PAUSED","");
        }
    }
    @Test void failureSelectsExactGoalShowsImmutableBeforeAfterAndSourceWithoutGuessingLine() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var facade=new Facade();var panel=new JaCaMoWorkbenchPanel(facade);
            assertEquals(1,component(panel,"workbench-tabs",JTabbedPane.class).getSelectedIndex());
            assertTrue(component(panel,"goal-tree",JTree.class).getLastSelectedPathComponent().toString().contains("decide"));
            var detail=component(panel,"violation-detail",JTextArea.class).getText();
            assertTrue(detail.contains("SATISFIED"));assertTrue(detail.contains("Previous checkpoint values: {runtimeState=WAITING}"));
            assertTrue(detail.contains("Confirmation values: {runtimeState=ENABLED}"));assertTrue(detail.contains("Last passing eligible checks: before"));
            assertTrue(detail.contains("organization.xml (line unavailable)"));
            component(panel,"violation-source",JButton.class).doClick();
            assertEquals("organization.xml:0",facade.source);assertTrue(component(panel,"source-location",JLabel.class).getText().contains("line unavailable"));
            facade.violations=List.of(violation("replacement-with-same-history-size",facade.failure,facade.confirmation));
            panel.refreshRuntime();assertEquals(1,component(panel,"workbench-tabs",JTabbedPane.class).getSelectedIndex());
        });
    }
    @Test void fourControlStatesAndStaleDisconnectOrMissingAckCannotEnableResume() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var facade=new Facade();var panel=new JaCaMoWorkbenchPanel(facade);var resume=component(panel,"runtime-resume",JButton.class);
            assertTrue(resume.isEnabled());
            for(var state:RuntimeControlContract.State.values()) {
                facade.control=new RuntimeControlService.View(state,true,true,false,Set.of("one"),Set.of("one"),"",RuntimeControlContract.LIMITATION);
                panel.refreshRuntime();assertEquals(state==RuntimeControlContract.State.PAUSED,resume.isEnabled());
                assertTrue(component(panel,"jason-control-state",JLabel.class).getText().contains(state.name()));
            }
            facade.control=new RuntimeControlService.View(RuntimeControlContract.State.PAUSED,true,true,false,Set.of("one"),Set.of("one"),"",RuntimeControlContract.LIMITATION);
            facade.current=cut("stale",4,"SATISFIED",VerificationSnapshot.SynchronizationState.STALE,true);panel.refreshRuntime();assertFalse(resume.isEnabled());
            facade.current=facade.confirmation;
            facade.control=new RuntimeControlService.View(RuntimeControlContract.State.PAUSED,true,false,false,Set.of("one"),Set.of("one"),"",RuntimeControlContract.LIMITATION);
            panel.refreshRuntime();assertFalse(resume.isEnabled());
            facade.control=new RuntimeControlService.View(RuntimeControlContract.State.PAUSED,true,true,false,Set.of("one","two"),Set.of("one"),"",RuntimeControlContract.LIMITATION);
            panel.refreshRuntime();assertFalse(resume.isEnabled());
            facade.control=new RuntimeControlService.View(RuntimeControlContract.State.PAUSED,true,true,false,Set.of("one"),Set.of("one"),"RESYNC_FAILED",RuntimeControlContract.LIMITATION);
            panel.refreshRuntime();assertFalse(resume.isEnabled());assertEquals(0,facade.resumes.get());
        });
    }
    @Test void unchangedGoalDtoKeepsTreeSelectionAndModelRatherThanRebuildingEachTimerTick() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var facade=new Facade();var panel=new GoalViewPanel(ignored->{});panel.refresh(facade.goalView());panel.selectGoal("goalOccurrence");
            var tree=component(panel,"goal-tree",JTree.class);var model=tree.getModel();var selected=tree.getSelectionPath();
            panel.refresh(facade.goalView());assertSame(model,tree.getModel());assertEquals(selected,tree.getSelectionPath());
        });
    }
    private static <T extends Component>T component(Container root,String name,Class<T> type) {
        for(var child:root.getComponents()) {
            if(name.equals(child.getName()) && type.isInstance(child))return type.cast(child);
            if(child instanceof Container nested)try{return component(nested,name,type);}catch(NoSuchElementException ignored){}
        }
        throw new NoSuchElementException(name);
    }
}
