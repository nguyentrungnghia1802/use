package org.tzi.use.plugins.jacamo.verification;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.runtime.*;
import org.tzi.use.plugins.jacamo.semantic.Dimension;
class RuntimeHistoryVerifierTest {
    private RuntimeEvent event(long n,RuntimeEventKind kind,String runtime,String correlation) {
        return RuntimeEvent.create("e"+n,java.time.Instant.EPOCH,n,Dimension.ENVIRONMENT,kind,runtime,"semantic",
            kind==RuntimeEventKind.OP_ENTER ? Map.of("operation","work","arguments",List.of()) : Map.of(),correlation);
    }
    @Test void exactOrderCrossTargetDuplicateTerminalAndRetiredGeneration() {
        var trace=new RuntimeTrace();long g=trace.begin("test");
        trace.accept(g,event(1,RuntimeEventKind.OP_ENTER,"target","c"));
        trace.accept(g,event(2,RuntimeEventKind.OP_EXIT,"wrong-target","c"));
        trace.accept(g,event(3,RuntimeEventKind.OP_EXIT,"target","c"));
        trace.accept(g,event(4,RuntimeEventKind.OP_EXIT,"target","c"));
        var checker=new RuntimeHistoryVerifier();var results=checker.verify(trace);
        assertEquals(2,results.stream().filter(r->r.outcome()==VerificationOutcome.FAIL).count());
        assertEquals(VerificationOutcome.PASS,checker.happenedBefore(trace,g,"e1","e3").outcome());
        assertEquals(VerificationOutcome.FAIL,checker.happenedBefore(trace,g,"e3","e1").outcome());
        assertEquals(VerificationOutcome.SKIPPED,checker.happenedBefore(trace,g,"missing","e1").outcome());
        trace.begin("reconnect");
        assertThrows(IllegalArgumentException.class,()->trace.accept(g,event(5,RuntimeEventKind.OP_EXIT,"target","c")));
        assertTrue(checker.verify(trace).stream().anyMatch(r->r.diagnostic().contains("RETIRED_GENERATION")));
    }
    @Test void terminalCannotCrossStreamAndFinitePrefixIsNotLivenessFailure() {
        var trace=new RuntimeTrace();long g=trace.begin("one");trace.accept(g,event(1,RuntimeEventKind.OP_ENTER,"target","c"));
        long next=trace.begin("two");trace.accept(next,event(2,RuntimeEventKind.OP_EXIT,"target","c"));
        var results=new RuntimeHistoryVerifier().verify(trace);
        assertTrue(results.stream().anyMatch(r->r.ruleId().equals("TERMINAL_NOT_OBSERVED") && r.outcome()==VerificationOutcome.SKIPPED));
        assertTrue(results.stream().anyMatch(r->r.ruleId().equals("START_BEFORE_TERMINAL") && r.outcome()==VerificationOutcome.FAIL));
    }
}
