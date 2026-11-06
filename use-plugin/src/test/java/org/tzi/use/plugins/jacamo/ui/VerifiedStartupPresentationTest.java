package org.tzi.use.plugins.jacamo.ui;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import static org.junit.jupiter.api.Assertions.*;

public class VerifiedStartupPresentationTest {
    public static void main(String[] args)throws Exception {new VerifiedStartupPresentationTest().startupAndReleasedIntervalsCannotAnimateRawHistoryOverTheReviewedCut();System.exit(0);}
    @Test void startupAndReleasedIntervalsCannotAnimateRawHistoryOverTheReviewedCut() throws Exception {
        if(!java.awt.GraphicsEnvironment.isHeadless()) {
            var child=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),getClass().getName()).inheritIO().start();
            assertTrue(child.waitFor(30,TimeUnit.SECONDS));assertEquals(0,child.exitValue());return;
        }
        byte[] bytes;try(var input=new java.util.zip.GZIPInputStream(getClass().getResourceAsStream("/semantics/checkpoint-display-user-result-20261105.json.gz"))){bytes=input.readAllBytes();}
        var result=RuntimeVerificationResult.fromMap(CanonicalJson.object(CanonicalJson.decode(bytes)));
        var snapshot=new VerificationSnapshot(result.stateVersion(),result,null);
        var registered=new AtomicReference<JaCaMoFacade.CheckpointPresenter>();var historyReads=new AtomicInteger();
        var facade=new JaCaMoFacade(){
            public String status(){return "Verified startup regression";}
            public boolean verifiedCheckpointMode(){return true;}
            public AutoCloseable registerCheckpointPresenter(CheckpointPresenter p){registered.set(p);return ()->registered.compareAndSet(p,null);}
            public RuntimeVerificationResult runtimeVerificationResult(){return result;}
            public VerificationSnapshot verificationSnapshot(){historyReads.incrementAndGet();return snapshot;}
            public RuntimeHistoryPage runtimeHistoryTail(){historyReads.incrementAndGet();return RuntimeHistoryPage.empty();}
            public RuntimeHistoryPage runtimeHistoryPage(long offset,int limit){throw new AssertionError("Verified live refresh must not replay raw disk history");}
        };
        var workbench=new AtomicReference<JaCaMoWorkbenchPanel>();var results=new AtomicReference<ObservedConformancePanel>();
        SwingUtilities.invokeAndWait(()->{
            workbench.set(JaCaMoWorkbenchPanel.authoredWorkflow(facade));results.set(ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model()));
            // Native model diagrams are outside this offscreen Verification test.
            try{var field=JaCaMoWorkbenchPanel.class.getDeclaredField("observedConformance");field.setAccessible(true);field.set(workbench.get(),results.get());}
            catch(ReflectiveOperationException error){throw new AssertionError(error);}
            for(int i=0;i<25;i++)workbench.get().refreshRuntime();
            assertEquals(0,historyReads.get());
            assertEquals(0,DetachedAuthoredWorkflowTest.find(results.get(),"observed-state-selector",JComboBox.class).getItemCount());
        });
        Thread.sleep(250);assertEquals(0,historyReads.get(),"No delayed startup history worker may run either");
        var view=new VerifiedCheckpointService.View("VERIFYING","source-token","source-post","","",result.stateHash(),result.resultHash(),Set.of(),false,false,Map.of());
        registered.get().present(view,snapshot,VerifiedCheckpointInventory.evaluate(snapshot)).get(10,TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(()->{
            var states=DetachedAuthoredWorkflowTest.find(results.get(),"observed-state-selector",JComboBox.class);assertTrue(states.getItemCount()>0);
            int count=states.getItemCount();Object selected=states.getSelectedItem();
            for(int i=0;i<25;i++)workbench.get().refreshRuntime();
            assertEquals(count,states.getItemCount());assertEquals(selected,states.getSelectedItem());assertEquals(0,historyReads.get());
            assertEquals(result.resultHash(),snapshot.result().resultHash());workbench.get().removeNotify();
        });
    }
}
