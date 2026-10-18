package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.semantics.CanonicalGoalMappingTest;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalImportSelectionTest {
    @Test void canonicalReplaySwitchRevokesLiveEngineAndRoutesOneReadOnlyOfflineSystem() throws Exception {
        var jcm=Path.of("src/test/resources/semantics/original-engine/monitor.jcm").toAbsolutePath();var directory=Files.createTempDirectory(Path.of("target"),"canonical-offline-switch-");var file=directory.resolve("rules.json");
        Files.write(file,CanonicalGoalMappingTest.rules(CanonicalGoalMappingTest.source()).bytes());var host=new Session();
        try(var facade=BridgeFacadeTestSupport.nativeFacade(jcm,host,()->false)) {
            facade.importProject(jcm,new JaCaMoFacade.ImportSelection("USE4GOAL_OBSERVED_V1",file));var oldSystem=host.system();var owner=CanonicalMonitorLifecycleTest.projector(facade).coordinator().originalEngine();
            var bundle=directory.resolve("replay");facade.exportRuntimeReplay(bundle);facade.disconnectRuntime();facade.openStepReplay(bundle);
            CanonicalMonitorLifecycleTest.await(()->Boolean.TRUE.equals(owner.metrics().get("terminated")));assertEquals(0,owner.metrics().get("expressions"));assertEquals(0,owner.metrics().get("pendingPins"));
            assertNotSame(oldSystem,host.system());assertSame(host.system(),facade.materializedSystem());assertTrue(host.system().isReadOnly());assertEquals("REPLAY",facade.workflowStatus().state());
            assertEquals("USE4GOAL_OBSERVED_V1",facade.verificationSnapshot().result().semantic().profile());assertThrows(IllegalStateException.class,facade::restartMonitorSession);facade.resetStepReplay();assertSame(host.system(),facade.materializedSystem());
        }
        assertFalse(host.hasSystem());
    }
    @Test void explicitSelectionIsFrozenUntilImportAndFailedSelectionPreservesOwner() throws Exception {
        var jcm=Path.of("src/test/resources/semantics/original-engine/monitor.jcm").toAbsolutePath();
        var file=Files.createTempDirectory(Path.of("target"),"ui-selection-").resolve("rules.json");
        Files.write(file,CanonicalGoalMappingTest.rules(CanonicalGoalMappingTest.source()).bytes());
        var request=new JaCaMoFacade.ImportSelection("USE4GOAL_OBSERVED_V1",file);var host=new Session();
        try(var facade=BridgeFacadeTestSupport.nativeFacade(jcm,host,()->false)) {
            facade.importProject(jcm,request);var first=host.system();var evidence=facade.semanticProgramEvidence();
            assertEquals("USE4GOAL_OBSERVED_V1",facade.runtimeVerificationResult().semantic().profile());
            assertEquals("2.0",evidence.get("rulesVersion"));assertEquals(request,facade.importSelection());
            assertFalse(((java.util.List<?>)evidence.get("traceBindings")).isEmpty());
            Files.writeString(file,"{}");facade.resyncRuntime();assertSame(first,host.system());assertSame(evidence,facade.semanticProgramEvidence());
            assertThrows(IllegalArgumentException.class,()->facade.importProject(jcm,request));assertSame(first,host.system());assertEquals(request,facade.importSelection());
            assertEquals("CURRENT_OBSERVED",facade.runFullVerification().fingerprints().get("freshness"));
            SwingUtilities.invokeAndWait(()->assertThrows(IllegalStateException.class,facade::runFullVerification));
            facade.stopMonitorSession();assertSame(first,host.system());facade.restartMonitorSession();assertNotSame(first,host.system());assertEquals(evidence,facade.semanticProgramEvidence());
            facade.importProject(jcm,new JaCaMoFacade.ImportSelection("NATIVE_CURRENT",null));assertNull(facade.runtimeVerificationResult().semantic());assertNotSame(first,host.system());
        }
    }
    @Test void missingUnsupportedOrEdtSelectionFailsBeforeHostReplacement() throws Exception {
        assertThrows(IllegalArgumentException.class,()->new JaCaMoFacade.ImportSelection("not-a-profile",null));
        var jcm=Path.of("src/test/resources/semantics/original-engine/monitor.jcm");var host=new Session();
        try(var facade=BridgeFacadeTestSupport.nativeFacade(jcm,host,()->false)) {
            assertThrows(IllegalArgumentException.class,()->facade.importProject(jcm,new JaCaMoFacade.ImportSelection("USE4GOAL_OBSERVED_V1",null)));
            SwingUtilities.invokeAndWait(()->assertThrows(IllegalStateException.class,()->facade.importProject(jcm,new JaCaMoFacade.ImportSelection("NATIVE_CURRENT",null))));
            assertFalse(host.hasSystem());
        }
    }
}
