package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.bridge.BridgeConnectionConfig;
import org.tzi.use.plugins.jacamo.bridge.BridgeTransportFactory;
import org.tzi.use.main.Session;

/** Phase 8 authority boundary: native is the only implicit production mode. */
class ProductionAuthorityPhase8Test {
    @Test
    void allImplicitFacadeEntryPointsUseNativeAuthority() {
        try (var defaultFacade = new DefaultJaCaMoFacade(Path.of("."))) {
            assertEquals(PipelineMode.CODE_GROUNDED_NATIVE, defaultFacade.pipelineMode());
        }
        try (var sessionFacade = DefaultJaCaMoFacade.forSession(new Session())) {
            assertEquals(PipelineMode.CODE_GROUNDED_NATIVE, sessionFacade.pipelineMode());
        }
        try (var configuredFacade = new DefaultJaCaMoFacade(Path.of("."), SemanticAuthority.BRIDGE,
                failingConfiguration(), failingTransport())) {
            assertEquals(PipelineMode.CODE_GROUNDED_NATIVE, configuredFacade.pipelineMode());
        }
    }

    @Test
    void nativeIsTheOnlyProductionPipelineAndSessionActionsShareItsFacade() {
        assertEquals(java.util.List.of(PipelineMode.CODE_GROUNDED_NATIVE),java.util.List.of(PipelineMode.values()));
        Session session=new Session();
        var facade=DefaultJaCaMoFacade.forSession(session);
        org.junit.jupiter.api.Assertions.assertSame(facade,DefaultJaCaMoFacade.forSession(session));
        facade.close();
        try(var replacement=DefaultJaCaMoFacade.forSession(session)) {
            org.junit.jupiter.api.Assertions.assertNotSame(facade,replacement);
        }
    }

    @Test
    void nativeFailureIsFailClosedAndDoesNotCreateACompatibilityWorkspace() {
        try (var facade = new DefaultJaCaMoFacade(Path.of("."), SemanticAuthority.BRIDGE,
                () -> { throw new IllegalStateException("PHASE8_NATIVE_CONFIGURATION_FAILURE"); },
                failingTransport())) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> facade.importProject(Path.of("phase8.jcm")));
            assertEquals("PHASE8_NATIVE_CONFIGURATION_FAILURE", error.getMessage());
            assertEquals(PipelineMode.CODE_GROUNDED_NATIVE, facade.pipelineMode());
            assertNull(facade.projectSummary());
        }
    }

    private static Supplier<BridgeConnectionConfig> failingConfiguration() {
        return () -> { throw new IllegalStateException("PHASE8_CONFIGURATION_NOT_USED"); };
    }

    private static BridgeTransportFactory failingTransport() {
        return ignored -> { throw new AssertionError("transport must not open"); };
    }
}
