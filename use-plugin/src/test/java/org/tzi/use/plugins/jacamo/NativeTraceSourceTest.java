package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.trace.*;

class NativeTraceSourceTest {
    @Test void nativeFacadeResolvesOnlyRealLocalSourcesAndPreservesJarUris() {
        Path jcm = Path.of("target", "fixture", "project.jcm").toAbsolutePath();
        var local = DefaultJaCaMoFacade.nativeTraceRow(record("project:/inc/agent.asl"), jcm);
        assertEquals(jcm.getParent().resolve("inc/agent.asl"), local.sourcePath());
        assertEquals(7, local.sourceLine());
        var external = DefaultJaCaMoFacade.nativeTraceRow(record(jcm.getParent().resolve("external.asl").toUri().toString()), jcm);
        assertEquals(jcm.getParent().resolve("external.asl"), external.sourcePath());
        var jar = DefaultJaCaMoFacade.nativeTraceRow(record("jar:file:/dependency.jar!/inc/agent.asl"), jcm);
        assertNull(jar.sourcePath());
        assertEquals("jar:file:/dependency.jar!/inc/agent.asl", jar.sourceEvidence().getFirst().sourceUri());
        var noEvidence = new CodeGroundedTraceRecord("A03", TracePhase.INSTANCE_MATERIALIZATION,
                "JASON_PLAN", "jason.asSyntax.Plan", "p", "MObject", "p", EvidenceAuthority.OFFICIAL_JASON_API,
                Fidelity.EXACT, CapabilityStatus.COMPLETE, List.of());
        assertNull(DefaultJaCaMoFacade.nativeTraceRow(noEvidence, jcm).sourcePath());
    }

    private CodeGroundedTraceRecord record(String uri) {
        var evidence = new SourceEvidence(EvidenceAuthority.OFFICIAL_JASON_API, uri, "a".repeat(64),
                "jason.asSyntax.Plan", "p", 7, 11, "", "", 0, Fidelity.EXACT, CapabilityStatus.COMPLETE, List.of());
        return new CodeGroundedTraceRecord("A03", TracePhase.INSTANCE_MATERIALIZATION,
                "JASON_PLAN", "jason.asSyntax.Plan", "p", "MObject", "p", EvidenceAuthority.OFFICIAL_JASON_API,
                Fidelity.EXACT, CapabilityStatus.COMPLETE, List.of(), List.of(evidence));
    }
}
