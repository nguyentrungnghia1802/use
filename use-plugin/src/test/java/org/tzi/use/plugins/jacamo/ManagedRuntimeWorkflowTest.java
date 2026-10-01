package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.ManagedStartupControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.bridge.BridgeClientState;

class ManagedRuntimeWorkflowTest {
    @TempDir Path root;
    JaCaMoFacade.AuthorityStatus authority() { return new JaCaMoFacade.AuthorityStatus(SemanticAuthority.BRIDGE,
            BridgeClientState.LIVE, java.util.Map.of(), "PARTIAL", "revision", "session", 1, "tcp://127.0.0.1:1", ""); }
    ManagedRuntimeWorkflow configured(long pid, long timeout) {
        System.setProperty(ManagedStartupControl.DIRECTORY_PROPERTY, root.toString());
        System.setProperty(ManagedStartupControl.RUN_PROPERTY, "run");
        ManagedStartupControl.waiting(root, ManagedStartupControl.owner("run", root.resolve("p.jcm"), "session", 1, "revision", pid), timeout);
        try { return new ManagedRuntimeWorkflow(); }
        finally { System.clearProperty(ManagedStartupControl.DIRECTORY_PROPERTY); System.clearProperty(ManagedStartupControl.RUN_PROPERTY); }
    }
    @Test void modelOclStartRequireRealAckAndCannotStartTwiceOrReturnFromLiveOnLateProfile() throws Exception {
        var workflow = configured(ProcessHandle.current().pid(), 5);
        workflow.importing(); assertEquals("IMPORTING", workflow.status().state());
        workflow.imported(root.resolve("p.jcm"), authority(), true);
        assertEquals("MODEL_READY", workflow.status().state()); assertTrue(workflow.status().startAvailable());
        workflow.profileReady(); assertEquals("OCL_READY", workflow.status().state());
        try (var pool = Executors.newSingleThreadExecutor()) {
            var start = pool.submit(workflow::start);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (ManagedStartupControl.read(root, "request.json").isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals("STARTING", workflow.status().state());
            ManagedStartupControl.acknowledged(root, ManagedStartupControl.read(root, "request.json"));
            start.get(2, TimeUnit.SECONDS);
        }
        assertEquals("LIVE", workflow.status().state()); assertFalse(workflow.status().startAvailable());
        assertThrows(IllegalStateException.class, workflow::start);
        workflow.profileReady(); assertEquals("LIVE", workflow.status().state());
    }
    @Test void missingSessionAndWrongProjectCannotReleaseAgents() {
        var workflow = configured(ProcessHandle.current().pid(), 5);
        assertThrows(IllegalStateException.class, () -> workflow.imported(root.resolve("p.jcm"), authority(), false));
        assertThrows(IllegalStateException.class, () -> workflow.imported(root.resolve("other.jcm"), authority(), true));
        assertFalse(workflow.status().startAvailable());
    }
    @Test void importFailureCancelsWhileProducerDeadAndAutonomousAreUnavailable() {
        var workflow = configured(ProcessHandle.current().pid(), 5);
        workflow.importFailed("INVALID_MODEL"); assertEquals("ERROR", workflow.status().state());
        assertFalse(ManagedStartupControl.read(root, "cancel.json").isEmpty());
        var external = new ManagedRuntimeWorkflow(); external.imported(root.resolve("p.jcm"), authority(), true);
        assertEquals("LIVE", external.status().state()); assertFalse(external.status().startAvailable());
        assertThrows(IllegalStateException.class, external::start);
    }
    @Test void deadProducerAndTimeoutCannotBecomeLive() throws Exception {
        var workflow = configured(Long.MAX_VALUE, 1);
        workflow.imported(root.resolve("p.jcm"), authority(), true);
        assertEquals("STOPPED", workflow.status().state()); assertFalse(workflow.status().startAvailable());
    }
    @Test void expiredWaitingAndWorkbenchCloseCannotReleaseReasoning() throws Exception {
        var workflow=configured(ProcessHandle.current().pid(),1);
        workflow.imported(root.resolve("p.jcm"),authority(),true);
        Thread.sleep(1100);
        assertEquals("ERROR",workflow.status().state());
        assertThrows(IllegalStateException.class,workflow::start);
        workflow.cancel();
        assertFalse(ManagedStartupControl.read(root,"cancel.json").isEmpty());
        assertFalse(java.nio.file.Files.exists(root.resolve("request.json")));
    }
    @Test void invalidFacadeImportAndCloseCancelAnUnstartedOwnedProducer() {
        configured(ProcessHandle.current().pid(),5);
        System.setProperty(ManagedStartupControl.DIRECTORY_PROPERTY,root.toString());
        System.setProperty(ManagedStartupControl.RUN_PROPERTY,"run");
        try(var facade=new DefaultJaCaMoFacade(root,new org.tzi.use.main.Session())) {
            System.clearProperty(ManagedStartupControl.DIRECTORY_PROPERTY);System.clearProperty(ManagedStartupControl.RUN_PROPERTY);
            assertThrows(IllegalArgumentException.class,()->facade.importProject(root.resolve("invalid.txt")));
            assertEquals("ERROR",facade.workflowStatus().state());
            assertFalse(ManagedStartupControl.read(root,"cancel.json").isEmpty());
        } finally {
            System.clearProperty(ManagedStartupControl.DIRECTORY_PROPERTY);System.clearProperty(ManagedStartupControl.RUN_PROPERTY);
        }
        assertFalse(java.nio.file.Files.exists(root.resolve("request.json")));
    }
}
