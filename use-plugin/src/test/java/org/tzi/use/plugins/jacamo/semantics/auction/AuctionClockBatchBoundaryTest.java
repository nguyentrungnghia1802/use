package org.tzi.use.plugins.jacamo.semantics.auction;

import java.time.Instant;
import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuctionClockBatchBoundaryTest {
    private RuntimeEvent event(String id,String source,long sequence) {
        return new RuntimeEvent(id,"run",1,"revision",source,source,sequence,Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.RUNTIME_EVENT,
                ProjectionStatus.EVIDENCE_ONLY,new BridgeEntityId("bridge","runtime","test","run",id,"1"),null,"","",Map.of(),Map.of(),new SourceWatermark(source,sequence),Completeness.COMPLETE,List.of());
    }
    private RuntimeEvent boundary(RuntimeEvent cause,String run) {
        return new RuntimeEvent("boundary",run,1,"revision","bridge",VerificationBoundary.SOURCE_ID,1,Instant.EPOCH,RuntimeEventKind.STREAM_BOUNDARY,
                RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,new BridgeEntityId("bridge","runtime","verification-boundary",run,"batch",cause.eventId()),
                null,cause.eventId(),cause.eventId(),Map.of(),Map.of("boundarySource",cause.sourceId(),"boundarySourceSequence",cause.sourceSequence(),"boundaryEventId",cause.eventId()),
                new SourceWatermark(VerificationBoundary.SOURCE_ID,1),Completeness.COMPLETE,List.of());
    }
    private boolean covered(RuntimeEvent... events){return AuctionClockLiveTcpTest.coveredClockBatch(List.of(events),Set.of("clock"),"run",1,"revision");}
    @Test void lastTriggerMayDifferFromTheClockInTheSameObservedBatch() {
        var clock=event("clock",VerificationObservation.SOURCE_ID,2);var action=event("action","cartago",7);
        assertTrue(covered(clock,action,boundary(action,"run")));
        assertTrue(covered(clock,boundary(clock,"run")));
    }
    @Test void earlierBoundaryMissingCauseAndForeignEpochCannotCertifyTheClock() {
        var clock=event("clock",VerificationObservation.SOURCE_ID,2);var action=event("action","cartago",7);var end=boundary(action,"run");
        assertFalse(covered(action,end,clock));assertFalse(covered(clock,end));assertFalse(covered(clock,action,boundary(action,"other-run")));
    }
    @Test void evidencePrefixLargerThanOneWireFramePreservesEveryBoundedEvent(@org.junit.jupiter.api.io.TempDir Path directory)throws Exception {
        var events=new ArrayList<RuntimeEvent>();String payload="x".repeat(900_000);
        for(int i=0;i<20;i++)events.add(new RuntimeEvent("payload-"+i,"run",1,"revision","cartago","cartago",i,Instant.EPOCH,
                RuntimeEventKind.CHANGED,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,
                new BridgeEntityId("bridge","runtime","test","run","payload-"+i,"1"),null,"","",Map.of(),Map.of("data",payload),
                new SourceWatermark("cartago",i),Completeness.COMPLETE,List.of()));
        assertEquals("payload exceeds maximum encoded size",assertThrows(ContractException.class,
                ()->CanonicalJson.encode(events.stream().map(ContractPayloads::event).toList())).getMessage());
        Path output=directory.resolve("events.ndjson");AuctionClockLiveTcpTest.writeEvents(output,events);
        assertTrue(Files.size(output)>CanonicalJson.DEFAULT_MAX_BYTES);
        try(var lines=Files.lines(output)) {
            var iterator=lines.iterator();
            for(var event:events) {
                assertTrue(iterator.hasNext());var line=iterator.next().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(line.length<CanonicalJson.DEFAULT_MAX_BYTES);
                assertArrayEquals(CanonicalJson.encode(ContractPayloads.event(event)),CanonicalJson.encode(CanonicalJson.decode(line)));
            }
            assertFalse(iterator.hasNext());
        }
    }
}
