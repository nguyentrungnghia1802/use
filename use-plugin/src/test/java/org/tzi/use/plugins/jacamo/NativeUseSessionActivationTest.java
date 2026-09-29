package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseExporter;
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
            assertNull(session.system().model().getClass("AgentProgram"),
                    "AUTO projection keeps AgentProgram as evidence-only; FULL remains the audit profile");
            assertNotNull(session.system().model().getClass("Organization"),
                    "Phase 5 Moise classes are part of the native session schema");
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

    @Test void facadeExportsTheCurrentNativeModelAndActiveStateOnly(@TempDir Path directory) throws Exception {
        Session session = new Session();
        try (var facade = BridgeFacadeTestSupport.nativeFacade(hello(), session, () -> false)) {
            assertTrue(facade.importProject(hello()).structureValid());
            Path use = directory.resolve("hello.use");
            Path cmd = directory.resolve("hello.cmd");
            facade.exportNativeUse(use);
            facade.exportNativeSoil(cmd);
            assertTrue(Files.isRegularFile(use));
            assertTrue(Files.isRegularFile(cmd));
            assertTrue(Files.readString(use).contains("model"));
            assertTrue(Files.readString(cmd).contains("!new "));
            assertEquals(facade.formalStateStatus().objectCount(),
                    new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter()
                            .export(session.system()).objectCount());
            assertEquals(new NativeUseExporter().export(session.system().model()).originalStructuralHash(),
                    new NativeUseExporter().export(session.system().model()).recompiledStructuralHash());
        }
    }

    private static java.nio.file.Path hello() {
        return java.nio.file.Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm")
                .toAbsolutePath().normalize();
    }
}
