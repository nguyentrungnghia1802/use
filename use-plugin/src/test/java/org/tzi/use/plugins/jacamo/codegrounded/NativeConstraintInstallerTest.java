package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ConstraintMigrationStatus;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.NativeConstraintInstaller;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.NativeConstraintSpec;

class NativeConstraintInstallerTest {
    @Test void installsOnlyFirstSliceNativeConstraintsThroughTheModelApi() throws Exception {
        var result = CodeGroundedTestFixtures.helloPipeline();
        assertEquals(Set.of("A17OrderConsistent", "A19OrderConsistent", "A20NextAgreesWithA19"),
                result.model().model().classInvariants().stream().map(value -> value.name()).collect(Collectors.toSet()));
        assertEquals(Set.of("Plan", "PlanLibrary"), result.model().model().classInvariants().stream()
                .map(value -> value.cls().name()).collect(Collectors.toSet()));
        assertTrue(result.model().constraints().stream().allMatch(value -> "CODE_GROUNDED".equals(value.origin())));
        assertEquals(Set.of("C08LiveObservablePropertyAvailable"), result.model().skippedConstraints().stream()
                .map(value -> value.name()).collect(Collectors.toSet()));
    }

    @Test void skippedCapabilityIsReportedAndNeverInstalledAsOcl() throws Exception {
        UseModelApi api = new UseModelApi("SkippedCapability");
        api.createClass("Artifact", false);
        NativeConstraintSpec skipped = new NativeConstraintSpec("C08Unavailable", "Artifact", "true",
                List.of("C08"), List.of("cartago.live-observable-property"), Fidelity.EXACT,
                "CODE_GROUNDED", ConstraintMigrationStatus.SKIPPED_CAPABILITY);

        NativeConstraintInstaller.InstallationResult result =
                new NativeConstraintInstaller().installWithReport(api, List.of(skipped));

        assertTrue(result.installed().isEmpty());
        assertEquals(List.of("C08Unavailable"), result.skipped().stream().map(value -> value.name()).toList());
        assertTrue(api.getModel().classInvariants().isEmpty());
    }
}
