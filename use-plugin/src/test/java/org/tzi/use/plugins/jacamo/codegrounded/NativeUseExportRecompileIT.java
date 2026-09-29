package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NativeUseExportRecompileIT {
    @Test void officialPrinterOutputRecompilesToTheSameStructure() throws Exception {
        var result = CodeGroundedTestFixtures.helloPipeline();
        assertTrue(result.export().useText().startsWith("model helloworld_CodeGrounded"));
        assertEquals(result.export().originalStructuralHash(), result.export().recompiledStructuralHash());
        assertEquals(result.model().structuralHash(), result.export().originalStructuralHash());
        assertNotNull(result.export().recompiledModel().getClass("PlanBodyElement"));
    }
}
