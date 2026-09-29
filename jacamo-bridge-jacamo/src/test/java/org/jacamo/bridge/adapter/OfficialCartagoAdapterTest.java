package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;

import cartago.CartagoEnvironment;
import java.util.List;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.junit.jupiter.api.Test;

class OfficialCartagoAdapterTest {
    @Test
    void initializedEnvironmentUsesOfficialWorkspaceDescriptorIdentityAndClosedLivePropertyBoundary() throws Exception {
        CartagoEnvironment environment = CartagoEnvironment.getInstance();
        environment.init();

        var adapter = new OfficialCartagoAdapter();
        var first = adapter.capture(environment);
        var second = adapter.capture(environment);

        assertEquals(first.metadata().semanticId(), second.metadata().semanticId());
        assertEquals(first.workspaces(), second.workspaces());
        assertFalse(first.workspaces().isEmpty());
        var root = first.workspaces().stream().filter(value -> value.parentSemanticId().isBlank()).findFirst().orElseThrow();
        assertEquals(environment.getRootWSP().getId().getFullName(), root.fullName());
        assertEquals(EvidenceAuthority.OFFICIAL_CARTAGO_API, first.metadata().evidenceAuthority());
        assertEquals(Fidelity.EXACT, root.metadata().fidelity());
        assertEquals(CapabilityStatus.COMPLETE, root.metadata().capabilityStatus());
        assertEquals(List.of(), first.liveProperties());
        assertEquals(List.of(), first.signals());
        assertEquals(List.of(), first.focuses());
    }
}
