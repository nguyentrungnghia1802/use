package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NativeConstraintInstallerTest {
    @Test void installsOnlyFirstSliceNativeConstraintsThroughTheModelApi() throws Exception {
        var result = CodeGroundedTestFixtures.helloPipeline();
        assertEquals(Set.of("A17OrderConsistent", "A19OrderConsistent", "A20NextAgreesWithA19"),
                result.model().model().classInvariants().stream().map(value -> value.name()).collect(Collectors.toSet()));
        assertEquals(Set.of("Plan", "PlanLibrary"), result.model().model().classInvariants().stream()
                .map(value -> value.cls().name()).collect(Collectors.toSet()));
        assertTrue(result.model().constraints().stream().allMatch(value -> "CODE_GROUNDED".equals(value.origin())));
    }
}
