package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.main.Session;

class ReplayStepFacadeTest {
    @TempDir Path root;
    @Test void freezeBeforeDisconnectThenActivateOnlyOfflineIsolatedSessionAndRouteAllReads() throws Exception {
        Path jcm=Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath();
        var session=new Session();
        try(var facade=BridgeFacadeTestSupport.nativeFacade(jcm,session,()->false)) {
            facade.importProject(jcm);var original=session.system();
            var profile=root.resolve("user.ocl");Files.writeString(profile,"context Plan inv Recorded: true");
            facade.loadVerificationProfile(profile);
            Path recording=root.resolve("recording");facade.exportRuntimeReplay(recording);
            String bytes=Files.readString(recording.resolve("runtime.jsonl"));
            try(var foreign=DefaultJaCaMoFacade.forSession(session)) {
                var error=assertThrows(IllegalStateException.class,()->foreign.openStepReplay(recording));
                assertTrue(error.getMessage().startsWith("REPLAY_SESSION_WORKSPACE_NOT_OWNED"));
                assertSame(original,session.system());assertNull(foreign.stepReplayStatus());
            }
            assertThrows(IllegalStateException.class,()->facade.openStepReplay(recording));assertSame(original,session.system());
            facade.disconnectRuntime();var before=facade.verificationSnapshot();
            var workspaceField=facade.getClass().getDeclaredField("nativeWorkspace");workspaceField.setAccessible(true);
            Object workspaceValue=workspaceField.get(facade);var stopField=workspaceValue.getClass().getDeclaredField("stopRuntimeDelivery");stopField.setAccessible(true);
            Runnable originalStop=(Runnable)stopField.get(workspaceValue);var settleCalls=new java.util.concurrent.atomic.AtomicInteger();
            stopField.set(workspaceValue,(Runnable)()->{assertFalse(javax.swing.SwingUtilities.isEventDispatchThread());settleCalls.incrementAndGet();originalStop.run();});
            assertEquals("STALE",before.result().freshness());
            Path stale=root.resolve("stale");facade.exportRuntimeReplay(stale);
            assertThrows(IllegalArgumentException.class,()->facade.openStepReplay(stale));assertSame(original,session.system());
            facade.openStepReplay(recording);
            assertEquals(2,settleCalls.get(),"Both rejected and valid Open must settle delivery after disconnect before selection");
            assertSame(session.system(),facade.materializedSystem());assertNotSame(original,session.system());
            assertNotSame(original.model(),session.system().model());assertTrue(session.system().isReadOnly());
            assertEquals("REPLAY",facade.workflowStatus().state());assertFalse(facade.workflowStatus().startAvailable());
            assertEquals(org.tzi.use.plugins.jacamo.runtime.MirrorState.REPLAY,facade.runtimeStatus().state());
            assertEquals(org.tzi.use.plugins.jacamo.bridge.BridgeClientState.DISCONNECTED,facade.authorityStatus().readiness());
            assertThrows(IllegalStateException.class,facade::startRuntime);
            assertThrows(IllegalStateException.class,facade::connectRuntime);
            assertThrows(IllegalStateException.class,facade::resyncRuntime);
            assertThrows(IllegalStateException.class,()->facade.loadVerificationProfile(profile));
            assertThrows(IllegalStateException.class,()->facade.importProject(jcm));
            try(var foreign=DefaultJaCaMoFacade.forSession(session)) {
                assertTrue(assertThrows(IllegalStateException.class,()->foreign.importProject(jcm)).getMessage().startsWith("RECORDED_REPLAY_READ_ONLY"));
                assertTrue(assertThrows(IllegalStateException.class,foreign::startRuntime).getMessage().startsWith("RECORDED_REPLAY_READ_ONLY"));
                assertThrows(IllegalStateException.class,()->foreign.loadVerificationProfile(profile));
                assertSame(facade.materializedSystem(),session.system());
            }
            facade.resetStepReplay();facade.nextStepReplay();facade.previousStepReplay();facade.nextStepReplay();
            assertEquals(facade.verificationSnapshot().result(),facade.runtimeVerificationResult());
            assertEquals(session.system().state().numObjects(),facade.formalStateStatus().objectCount());
            facade.exportNativeSoil(root.resolve("selected.cmd"));facade.exportNativeUse(root.resolve("selected.use"));
            assertEquals(new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().export(session.system()).commands(),Files.readString(root.resolve("selected.cmd")));
            assertEquals(bytes,Files.readString(recording.resolve("runtime.jsonl")));
            // Original disconnect evidence remains present; opening replay never repairs/clears it.
            var field=facade.getClass().getDeclaredField("nativeWorkspace");field.setAccessible(true);Object workspace=field.get(facade);
            var projectorField=workspace.getClass().getDeclaredField("runtimeProjector");projectorField.setAccessible(true);
            var projector=(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector)projectorField.get(workspace);
            assertEquals(before,projector.coordinator().verificationSnapshot());
        }
        assertFalse(session.hasSystem());
    }
}
