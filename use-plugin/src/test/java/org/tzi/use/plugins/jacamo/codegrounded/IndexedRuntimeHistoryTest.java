package org.tzi.use.plugins.jacamo.codegrounded;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

class IndexedRuntimeHistoryTest {
    @TempDir Path root;

    @Test void validatesWholePhysicalPrefixButParsesOnlyRequestedRecords() throws Exception {
        for(boolean compressed:List.of(false,true)) {
            var journal=new RuntimeEventJournal(root,2,32*1024*1024,compressed);
            for(int i=0;i<90;i++)assertTrue(journal.append(i==75?"CHECKPOINT_AUDIT":"EVENT",
                    Map.of("data","native evidence Ω ".repeat(1800)),CompressedRuntimeHistoryTest.result(i)));
            var entries=new ArrayList<RuntimeHistoryPage.Entry>();
            journal.visitRange(70,85,kind->!kind.equals("CHECKPOINT_AUDIT"),entries::add);
            assertEquals(14,entries.size());assertEquals(70,entries.getFirst().ordinal());assertEquals(84,entries.getLast().ordinal());
            var stats=journal.lastRangeRead();assertTrue(stats.indexed());assertEquals(15,stats.parsedRecords());assertEquals(14,stats.decodedResults());
            assertTrue(stats.checkedBytes()>0);
            // Even corruption BEFORE the requested range must retain the hold.
            byte[] bytes=Files.readAllBytes(journal.path());bytes[10]^=1;Files.write(journal.path(),bytes);
            assertThrows(RuntimeException.class,()->journal.visitRange(70,85,kind->!kind.equals("CHECKPOINT_AUDIT"),entry->fail("Corrupt prefix reached verifier")));
        }
    }

    @Test void filteredAuditBytesAndTruncationStillFailClosed() throws Exception {
        for(boolean compressed:List.of(false,true)) {
            var journal=new RuntimeEventJournal(root,2,1024*1024,compressed);
            assertTrue(journal.append("CHECKPOINT_AUDIT",Map.of(),CompressedRuntimeHistoryTest.result(0)));
            byte[] bytes=Files.readAllBytes(journal.path());Files.write(journal.path(),Arrays.copyOf(bytes,bytes.length-1));
            assertThrows(RuntimeException.class,()->journal.visitRange(0,1,kind->false,entry->fail()));
        }
    }

    @Test void concurrentAppendsDoNotExpandTheCheckedPrefix() throws Exception {
        for(boolean compressed:List.of(false,true)) {
            var journal=new RuntimeEventJournal(root,2,1024*1024,compressed);
            for(int i=0;i<5;i++)assertTrue(journal.append("EVENT",Map.of(),CompressedRuntimeHistoryTest.result(i)));
            var append=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
            try {
                var writer=pool.submit(()->{assertTrue(append.await(5,TimeUnit.SECONDS));assertTrue(journal.append("EVENT",Map.of(),CompressedRuntimeHistoryTest.result(5)));return null;});
                var entries=new ArrayList<RuntimeHistoryPage.Entry>();
                journal.visitRange(3,5,entry->{entries.add(entry);append.countDown();try{writer.get(5,TimeUnit.SECONDS);}catch(Exception error){throw new AssertionError(error);}});
                assertEquals(List.of(3L,4L),entries.stream().map(RuntimeHistoryPage.Entry::ordinal).toList());assertEquals(6,journal.persistedEntries());
                journal.visitRange(5,5,entry->fail());assertEquals(0,journal.lastRangeRead().decodedResults());
                journal.visitRange(5,6,entry->assertEquals(5,entry.ordinal()));
            }finally{pool.shutdownNow();}
        }
    }

    @Test void optionalIndexLossFallsBackToOriginalFullChainValidation() throws Exception {
        var journal=new RuntimeEventJournal(root,2,1024*1024,true);
        for(int i=0;i<5;i++)assertTrue(journal.append("EVENT",Map.of(),CompressedRuntimeHistoryTest.result(i)));
        var field=RuntimeEventJournal.class.getDeclaredField("anchors");field.setAccessible(true);((List<?>)field.get(journal)).clear();
        var entries=new ArrayList<RuntimeHistoryPage.Entry>();journal.visitRange(3,5,entries::add);
        assertFalse(journal.lastRangeRead().indexed());assertEquals(5,journal.lastRangeRead().parsedRecords());assertEquals(2,entries.size());
    }
}
