package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.main.Session;
import org.tzi.use.uml.sys.MSystem;

class NativeUseSessionActivationTest {
    @Test void facadeActivatesTheExactValidatedNativeSystem() {
        Session session = new Session();
        try (var facade = BridgeFacadeTestSupport.nativeFacade(hello(), session, () -> false)) {
            var summary = facade.importProject(hello());
            assertEquals(PipelineMode.CODE_GROUNDED_NATIVE, facade.pipelineMode());
            assertTrue(summary.structureValid());
            assertEquals("CODE_GROUNDED_NATIVE-1.0.0", summary.metamodelVersion());
            assertSame(facade.materializedSystem(), session.system());
            assertSame(session.system(), session.system().state().system());
            assertEquals(facade.formalStateStatus().objectCount(), session.system().state().numObjects());
            MSystem imported = session.system();
            assertNotNull(facade.runFullVerification());
            assertSame(imported, facade.materializedSystem());
            assertSame(imported, session.system());
            assertSame(imported, imported.state().system());

            facade.connectRuntime();
            MSystem resynchronized = facade.materializedSystem();
            assertSame(resynchronized, session.system());
            assertSame(resynchronized, resynchronized.state().system());
            assertNotNull(facade.runFullVerification());
            assertSame(resynchronized, facade.materializedSystem());
            assertSame(resynchronized, session.system());
            assertNotNull(session.system().model().getClass("AgentProgram"));
            assertNull(session.system().model().getClass("Organization"), "Phase 2+ classes must not leak into Phase 1B");
        }
    }

    @Test void failedNativeImportLeavesThePreviousSessionSystemUntouched() throws Exception {
        Session session = new Session();
        MSystem previous = new MSystem(new UseModelApi("Previous").getModel());
        session.setSystem(previous);
        try (var facade = BridgeFacadeTestSupport.nativeFacade(hello(), session, () -> true)) {
            assertThrows(IllegalStateException.class, () -> facade.importProject(hello()));
            assertSame(previous, session.system());
            assertNull(facade.projectSummary());
        }
    }

    private static java.nio.file.Path hello() {
        return java.nio.file.Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm")
                .toAbsolutePath().normalize();
    }
}
