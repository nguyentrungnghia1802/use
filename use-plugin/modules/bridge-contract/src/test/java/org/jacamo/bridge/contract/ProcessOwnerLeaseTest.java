package org.jacamo.bridge.contract;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcessOwnerLeaseTest {
    @Test void requiresExactOwnerStartIdentityRatherThanPidAlone() {
        var owner=ProcessOwnerLease.current();assertTrue(owner.alive());
        assertFalse(new ProcessOwnerLease(owner.pid(),owner.startedAtEpochMillis()+1).alive());
        assertThrows(IllegalArgumentException.class,()->new ProcessOwnerLease(0,1));
    }
}
