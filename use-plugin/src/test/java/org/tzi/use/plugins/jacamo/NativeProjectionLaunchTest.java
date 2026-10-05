package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.ui.JaCaMoWorkbenchPanel;

/** Launch configuration reaches the real GUI facade, native state, resync and exports. */
class NativeProjectionLaunchTest {
    @Test void configurationDefaultsToAutoAndRejectsUnsupportedModes() {
        assertEquals(NativeProjectionMode.AUTO, NativeProjectionMode.configured(null));
        assertEquals(NativeProjectionMode.AUTO, NativeProjectionMode.configured(" "));
        assertEquals(NativeProjectionMode.AUTO, NativeProjectionMode.configured("auto"));
        assertEquals(NativeProjectionMode.FULL, NativeProjectionMode.configured(" full "));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> NativeProjectionMode.configured("ALL")).getMessage()
                .startsWith("NATIVE_PROJECTION_MODE_UNSUPPORTED:"));
    }

    @Test void fullLaunchActivatesAndExportsTheSameNativeSystemInWorkbench(@TempDir Path directory) throws Exception {
        String previousMode = System.getProperty(NativeProjectionMode.PROPERTY);
        String readyProperty = "use.jacamo.workbench.ready-file";
        String previousReady = System.getProperty(readyProperty);
        Path jcm = hello();
        Path ready = directory.resolve("gui-ready.json");
        Session session = new Session();
        try {
            System.setProperty(NativeProjectionMode.PROPERTY, "full");
            try (var facade = BridgeFacadeTestSupport.nativeFacade(jcm, session, () -> false)) {
                assertEquals(NativeProjectionMode.FULL, facade.projectionMode());
                // A later global-property edit must not change this facade's projection on resync.
                System.setProperty(NativeProjectionMode.PROPERTY, "AUTO");
                System.setProperty(readyProperty, ready.toString());
                assertTrue(facade.importProject(jcm).structureValid());
                var panel = new JaCaMoWorkbenchPanel(facade);
                panel.importProject(jcm);
                var system = session.system();
                assertNotNull(system);
                assertSame(system, facade.materializedSystem());
                assertSame(system, system.state().system());
                for (String concept : List.of("Agent", "hello_Agent", "hf_Agent", "o1_Organization", "team", "Workspace", "AgentGoal", "Belief"))
                    assertNotNull(system.model().getClass(concept), concept);
                assertEquals(5, system.state().objectsOfClassAndSubClasses(system.model().getClass("Agent")).size());
                for (String concept : List.of("AgentProgram", "PlanLibrary", "Plan", "Trigger", "Action", "Role"))
                    assertNull(system.model().getClass(concept), concept);
                assertTrue(Files.readString(ready).contains("\"projectionMode\":\"FULL\""));
                assertEquals(facade.formalStateStatus().objectCount(), system.state().numObjects());
                facade.resyncRuntime();
                assertSame(system, session.system());
                assertSame(system, facade.materializedSystem());
                assertEquals(NativeProjectionMode.FULL, facade.projectionMode());
                facade.runFullVerification();
                Path use = directory.resolve("full.use");
                Path cmd = directory.resolve("full.cmd");
                facade.exportNativeUse(use);
                facade.exportNativeSoil(cmd);
                assertTrue(Files.readString(use).contains("class Agent"));
                assertEquals(5, Files.readString(cmd).lines().filter(line -> line.startsWith("!new hello_Agent(") || line.startsWith("!new hf_Agent(")).count());
            }
        } finally {
            restore(NativeProjectionMode.PROPERTY, previousMode);
            restore(readyProperty, previousReady);
        }
    }

    @Test void invalidLaunchConfigurationDoesNotTouchTheActiveSession() {
        String previousMode = System.getProperty(NativeProjectionMode.PROPERTY);
        Session session = new Session();
        // Initialize the facade class with the valid default before testing constructor rejection.
        try {
            System.clearProperty(NativeProjectionMode.PROPERTY);
            try (var ignored = BridgeFacadeTestSupport.nativeFacade(hello(), session, () -> false)) {
                assertEquals(NativeProjectionMode.AUTO, ignored.projectionMode());
            }
            var previousSystem = new org.tzi.use.uml.sys.MSystem(new org.tzi.use.api.UseModelApi("Existing").getModel());
            session.setSystem(previousSystem);
            System.setProperty(NativeProjectionMode.PROPERTY, "INVALID");
            assertThrows(IllegalArgumentException.class,
                    () -> BridgeFacadeTestSupport.nativeFacade(hello(), session, () -> false));
            assertSame(previousSystem, session.system());
        } finally {
            restore(NativeProjectionMode.PROPERTY, previousMode);
        }
    }

    private static Path hello() {
        return Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().normalize();
    }

    private static void restore(String key, String previous) {
        if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
    }
}
