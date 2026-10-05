package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.jacamo.bridge.contract.RuntimeControlContract.State.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReasoningCycleGateTest {
    @Test void allAgentAckTimeoutAndResumeAreSeparateTransitions() {
        var gate=new ReasoningCycleGate();gate.admit("a#1",true);gate.admit("b#1",true);
        assertTrue(gate.grant("a#1",0,0));assertTrue(gate.grant("b#1",0,0));gate.pause("p");
        gate.boundary("a#1",0);gate.timeout();assertEquals(PAUSE_REQUESTED,gate.state());assertTrue(gate.diagnostic().contains("b#1"));
        assertThrows(IllegalStateException.class,()->gate.resume("r"));gate.boundary("b#1",0);assertEquals(PAUSED,gate.state());
        gate.pause("duplicate");assertEquals("p",gate.requestId());gate.resume("r");long epoch=gate.epoch();
        assertTrue(gate.grant("a#1",1,epoch));gate.resumeAcknowledged("a#1",epoch);assertEquals(RESUME_REQUESTED,gate.state());
        assertTrue(gate.grant("b#1",1,epoch));gate.resumeAcknowledged("b#1",epoch);assertEquals(RUNNING,gate.state());
        gate.resume("duplicate");assertEquals("r",gate.requestId());
    }
    @Test void newIncarnationMustAckAndDepartingAgentCannotInventAck() {
        var gate=new ReasoningCycleGate();gate.admit("a#1",true);gate.grant("a#1",0,0);gate.pause("p");
        gate.admit("b#1",false);gate.boundary("a#1",0);assertEquals(PAUSE_REQUESTED,gate.state());
        gate.depart("b#1");assertEquals(PAUSE_REQUESTED,gate.state());assertEquals(Set.of("a#1"),gate.acknowledged());
        gate.admit("b#2",true);gate.boundary("b#1",0);assertEquals(PAUSE_REQUESTED,gate.state());
        gate.pause("explicit-new-cohort");assertEquals(PAUSED,gate.state());assertEquals(Set.of("a#1","b#2"),gate.required());
    }
    @Test void initialAdmissionWhilePausedIsHeldAndOldReleasesAreRejected() {
        var gate=new ReasoningCycleGate();gate.admit("a",true);gate.pause("p");assertEquals(PAUSED,gate.state());
        gate.admit("new",true);assertEquals(PAUSED,gate.state());assertTrue(gate.acknowledged().contains("new"));
        assertFalse(gate.grant("new",0,0));assertFalse(gate.grant("new",0,gate.epoch()));
        gate.unavailable("DISCONNECTED");assertEquals(PAUSE_REQUESTED,gate.state());
    }
    @Test void unprovedAdmissionAndUnavailableCapabilityCannotCompletePause() {
        var gate=new ReasoningCycleGate();gate.admit("unproved",false);gate.pause("p");assertEquals(PAUSE_REQUESTED,gate.state());
        assertFalse(gate.acknowledged().contains("unproved"));
        var valid=new ReasoningCycleGate();valid.admit("a",true);valid.grant("a",0,0);valid.pause("p");
        valid.unavailable("CONTROL_SCHEDULER_UNSUPPORTED");valid.boundary("a",0);assertEquals(PAUSE_REQUESTED,valid.state());
    }
}
