package org.tzi.use.plugins.jacamo.ui;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePresenter;
import static org.junit.jupiter.api.Assertions.*;

class ObservedTimelineHistoryTest {
    @TempDir Path directory;

    private static RuntimeVerificationResult result(String run, int state, boolean pending) {
        var now=Instant.EPOCH;
        return new RuntimeVerificationResult(run,1,"revision",state,"event-"+state,"cartago",state,
                now,now,now,"cut-"+state,"contracts","state-"+state,List.of(),0,
                pending?"EVALUATION_PENDING":"PARTIAL",pending?"PENDING_EVALUATION":"CURRENT_OBSERVED","",null);
    }
    private static final class Source implements JaCaMoFacade {
        RuntimeEventJournal journal;
        RuntimeVerificationResult latest;
        int pageReads;
        boolean stopped;
        Source(RuntimeEventJournal journal){this.journal=journal;}
        public String status(){return "timeline";}
        public RuntimeVerificationResult runtimeVerificationResult(){return latest;}
        public List<RuntimeVerificationResult> runtimeVerificationHistory(){return journal.history();}
        public RuntimeHistoryPage runtimeHistoryTail(){return stopped?RuntimeHistoryPage.empty():journal.tailPage(128);}
        public RuntimeHistoryPage runtimeHistoryPage(long offset,int limit){pageReads++;return journal.page(offset,limit);}
        void append(String run,int state,boolean pending){latest=result(run,state,pending);assertTrue(journal.append("EVENT",Map.of(),latest));}
    }

    @Test void timelineKeepsEveryPersistedBoundaryWhenTheMemoryTailShrinksAfterCompletion() {
        var source=new Source(new RuntimeEventJournal(directory,512,8*1024*1024));
        var presenter=new ObservedConformancePresenter(source);
        for(int i=1;i<=350;i++)source.append("auction",i,false);
        assertEquals(350,presenter.capture().size());
        for(int i=351;i<=550;i++){source.append("auction",i,true);source.append("auction",i,false);}
        assertEquals(512,source.journal.history().size());
        var cuts=presenter.capture();
        assertEquals(550,cuts.size(),"Disk history must survive RAM-tail eviction and pending/completed deduplication");
        assertTrue(cuts.getFirst().id().contains(":event-1:"));
        assertTrue(cuts.getLast().id().contains(":event-550:"));
        assertTrue(cuts.stream().allMatch(c->c.coverage().equals("PARTIAL")),"Presentation cannot manufacture completeness");
        int reads=source.pageReads;
        assertEquals(cuts,presenter.capture());assertEquals(reads,source.pageReads,"Unchanged history needs no disk rescan");
        assertEquals(550,new ObservedConformancePresenter(source).capture().size(),"Reopening the view loads earlier disk pages");
        source.stopped=true;
        assertEquals(cuts,presenter.capture(),"Stopping the monitor must not replace the timeline with its RAM tail");
        source.stopped=false;
        source.journal=new RuntimeEventJournal(directory,512,8*1024*1024);
        source.append("new-run",1,false);
        var restarted=presenter.capture();assertEquals(1,restarted.size());assertTrue(restarted.getFirst().id().startsWith("new-run:"));
    }

    @Test void damagedPersistedHistoryIsNotSilentlyReplacedByTheHealthyMemoryTail() throws Exception {
        var source=new Source(new RuntimeEventJournal(directory,2,128*1024));
        for(int i=1;i<=4;i++)source.append("auction",i,false);
        java.nio.file.Files.writeString(source.journal.path(),"corrupted\n");
        assertThrows(RuntimeException.class,()->new ObservedConformancePresenter(source).capture());
    }
    @Test void incrementalPublicationShowsEveryPrefixAndEventuallyTheExactFullHistory() {
        var source=new Source(new RuntimeEventJournal(directory,128,8*1024*1024));
        for(int i=1;i<=200;i++)source.append("run",i,false);
        var presenter=new ObservedConformancePresenter(source);
        var first=presenter.captureIncremental();assertEquals(64,first.size());assertTrue(presenter.historyPending());
        assertTrue(first.getLast().id().contains(":event-64:"),"Live tail cannot skip ahead of the published prefix");
        assertEquals(128,presenter.captureIncremental().size());assertEquals(192,presenter.captureIncremental().size());
        var finalCuts=presenter.captureIncremental();assertEquals(200,finalCuts.size());assertFalse(presenter.historyPending());
        assertEquals(new ObservedConformancePresenter(source).capture(),finalCuts);
    }

    @Test void normalRefreshConsumesThePersistedWriterTailWithoutRepeatedDiskPrefixScans() {
        var source=new Source(new RuntimeEventJournal(directory,512,8*1024*1024));
        var presenter=new ObservedConformancePresenter(source);
        source.append("auction",1,false);assertEquals(1,presenter.capture().size());
        int reads=source.pageReads;assertTrue(reads>0,"The initial persisted prefix must be checked");
        for(int i=2;i<=40;i++)source.append("auction",i,false);
        assertEquals(40,presenter.capture().size());assertEquals(reads,source.pageReads);
    }

    @Test void aRealQuotaGapKeepsPersistedCutsAndDoesNotBecomeComplete() {
        var source=new Source(new RuntimeEventJournal(directory,2,32768));
        var presenter=new ObservedConformancePresenter(source);
        int state=1;
        while(source.journal.append("EVENT",Map.of(),result("auction",state,false)))state++;
        assertTrue(source.journal.hasGap());assertEquals("JOURNAL_OVERFLOW",source.journal.diagnostic());
        source.latest=result("auction",state,false);
        source.journal.remember(source.latest);
        var cuts=presenter.capture();
        assertEquals(state,cuts.size(),"Keep the disk prefix and label the unpersisted current boundary honestly");
        assertTrue(cuts.stream().allMatch(c->c.coverage().equals("PARTIAL")));
        assertTrue(source.journal.hasGap(),"Reading the timeline cannot heal lost persistence");
    }

    @Test void runtimeLibraryBrowseStartsInTheRequestedTargetDirectory() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(()->{
            var chooser=JaCaMoWorkbenchPanel.createRuntimeLibrariesChooser();
            assertEquals(Path.of("D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/target").toFile(),chooser.getCurrentDirectory());
            assertTrue(chooser.getFileFilter().accept(Path.of("jacamo-bridge-classpath.txt").toFile()));
            assertFalse(chooser.getFileFilter().accept(Path.of("library.jar").toFile()));
        });
    }

    @Test void journalDefaultMatchesTheDeclarativeLauncherAndExplicitQuotaIsStillHonored() {
        String key="use.jacamo.runtime.journal.maxBytes",previous=System.getProperty(key);
        try {
            System.clearProperty(key);assertEquals(256L*1024*1024,RuntimeVerificationCoordinator.defaultJournalMaxBytes());
            System.setProperty(key,"67108864");assertEquals(64L*1024*1024,RuntimeVerificationCoordinator.defaultJournalMaxBytes());
        } finally {if(previous==null)System.clearProperty(key);else System.setProperty(key,previous);}
    }
}
