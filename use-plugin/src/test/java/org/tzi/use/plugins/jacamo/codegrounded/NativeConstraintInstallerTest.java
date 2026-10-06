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
    @Test void nativeMultiplicityReplacesFormationOclAndNoJasonOrNormInvariantIsInvented() throws Exception {
        var result = CodeGroundedTestFixtures.helloPipeline();
        assertEquals(Set.of("GoalDecompositionContext","GoalAcyclic","GoalSequenceOrdinals","MissionGoalContext",
                "GoalStateContext","GoalCommittedResponsibility"),
                result.model().constraints().stream().map(NativeConstraintSpec::name).collect(Collectors.toSet()));
        assertEquals(6,result.model().model().classInvariants().size());
        assertTrue(result.model().constraints().stream().allMatch(c->c.origin().startsWith("CORE:GOAL:")),
                "Generic Goal rules do not imply Jason AST or deontic Norm equivalence");
        assertTrue(result.model().model().classInvariants().stream().allMatch(i->"REPORT_ONLY".equals(
                i.getAnnotationValue("RuntimeConstraint","enforcement"))));
        assertTrue(result.model().model().associations().stream().anyMatch(a -> a.getAnnotation(
            org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.ROLE_ASSOCIATION)!=null));
        assertTrue(result.trace().records().stream().anyMatch(t -> t.diagnostics().contains("STATUS=UNSUPPORTED_NORM_TRANSLATION")));
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
