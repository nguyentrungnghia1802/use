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
    @Test void projectSummaryCountsOfficialMoiseRecordsAndOnlyProvidedExactBindings() throws Exception {
        for (Path project : java.util.List.of(hello(), Path.of("../../jacamo/examples/auction/auction.jcm")
                .toAbsolutePath().normalize())) {
            var source = new org.jacamo.bridge.adapter.OfficialProjectAdapter().adapt(
                    new org.jacamo.bridge.adapter.OfficialProjectLoader().load(project), project).semanticContract();
            assertFalse(source.moiseOrganizations().isEmpty());
            // Parsed JCM role tuples are not, by themselves, accepted exact cross bindings.
            // These static fixtures intentionally supply no such binding; never inflate CROSS.
            assertTrue(source.exactBindings().isEmpty());
            Session session = new Session();
            try (var facade = BridgeFacadeTestSupport.nativeFacade(project, session, () -> false)) {
                var summary = facade.importProject(project);
                assertTrue(summary.dimensionCounts().get("MOISE") > 0, "typed source records, not enactment objects");
                assertTrue(session.system().model().classes().stream().anyMatch(cls -> "DOMAIN_SCHEMA".equals(
                        cls.getAnnotationValue("MoiseProjection", "representation"))));
                assertFalse(session.system().state().allObjects().stream().anyMatch(object ->
                        object.cls().getAnnotation("MoiseProjection") != null),
                        "static import must not fabricate Moise runtime enactments");
                assertEquals((long) source.exactBindings().size(), summary.dimensionCounts().get("CROSS"));
                assertEquals("NATIVE_CURRENT", summary.mappingStatus());
                assertSame(session.system(), facade.materializedSystem());
            }
        }
    }

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
            assertNull(session.system().model().getClass("Organization"), "AUTO is domain-specific, not a metamodel inspector");
            assertNotNull(session.system().model().getClass("O1"), "the official OS id specializes the schema");
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
