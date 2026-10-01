package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.jacamo.bridge.contract.Completeness;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class ExternalOclConstraintServiceTest {
    @Test void validMultiContextCompilesIntoCurrentModelAndEvaluatesIndividually() throws Exception {
        var pipeline = CodeGroundedTestFixtures.helloPipeline();
        var system = pipeline.state().system();
        var service = new ExternalOclConstraintService(system);
        var profile = service.installSource("constraints.ocl", "context Agent inv Defined: not self.name.oclIsUndefined()\n"
                + "context Plan inv Failing: false\ncontext Agent inv Invalid: if true then null else true endif\n", "revision");
        assertSame(system, service.system()); assertEquals(3, profile.constraints().size());
        var results = service.evaluate(Map.of("jason", Completeness.COMPLETE), false);
        assertEquals(VerificationOutcome.PASS, outcome(results, "EXTERNAL:Agent::Defined"));
        assertEquals(VerificationOutcome.FAIL, outcome(results, "EXTERNAL:Plan::Failing"));
        assertEquals(VerificationOutcome.ERROR, outcome(results, "EXTERNAL:Agent::Invalid"));
        assertEquals(64, profile.sourceHash().length());
        assertEquals("revision", profile.constraints().getFirst().modelRevision());
    }

    @Test void rejectedProfilesLeaveAllOriginalInvariantsAndProfileIntact() throws Exception {
        var system = CodeGroundedTestFixtures.helloPipeline().state().system();
        var service = new ExternalOclConstraintService(system);
        var old = service.installSource("old.ocl", "context Agent inv Old: true", "one");
        var names = system.model().classInvariants().stream().map(inv -> inv.qualifiedName()).sorted().toList();
        for (String invalid : java.util.List.of("context Agent inv Broken: (", "context Missing inv Broken: true",
                "context Agent inv Broken: self.missingAttribute = 1", "context Agent inv Broken: 42",
                "context Agent inv Dup: true inv Dup: false", "context PlanLibrary inv A17OrderConsistent: true",
                "context Agent inv First: true\ncontext Missing inv Second: true", "true", "context Agent inv: true")) {
            assertThrows(IllegalArgumentException.class, () -> service.installSource("bad.ocl", invalid, "two"), invalid);
            assertSame(old, service.profile());
            assertEquals(names, system.model().classInvariants().stream().map(inv -> inv.qualifiedName()).sorted().toList());
        }
    }

    @Test void resyncRebindsExactProfileAndUnavailableProjectionCannotPass() throws Exception {
        var system = CodeGroundedTestFixtures.helloPipeline().state().system();
        var service = new ExternalOclConstraintService(system);
        service.installSource("user.ocl", "context Agent inv Same: true", "one");
        var invariant = system.model().classInvariants().stream().filter(inv -> inv.qualifiedName().equals("Agent::Same")).findFirst().orElseThrow();
        invariant.setActive(false);
        assertFalse(service.profile().constraints().getFirst().enabled());
        service.rebind("two");
        assertEquals("two", service.profile().modelRevision());
        assertFalse(service.profile().constraints().getFirst().enabled());
        system.model().classInvariants().stream().filter(inv -> inv.qualifiedName().equals("Agent::Same"))
                .findFirst().orElseThrow().setActive(true);
        assertEquals(VerificationOutcome.SKIPPED, outcome(service.evaluate(Map.of("jason", Completeness.PARTIAL), false), "EXTERNAL:Agent::Same"));
        assertThrows(IllegalArgumentException.class, () -> service.installSource("unavailable.ocl",
                "context LiveObservableProperty inv NoStub: true", "two"));
        assertNull(system.model().getClass("LiveObservableProperty"));
        assertEquals(VerificationOutcome.SKIPPED, outcome(service.evaluate(Map.of("jason", Completeness.COMPLETE), true), "EXTERNAL:Agent::Same"));
    }
    private static VerificationOutcome outcome(java.util.List<ExternalOclConstraintService.Outcome> outcomes, String id) {
        return outcomes.stream().filter(value -> value.constraintId().equals(id)).findFirst().orElseThrow().outcome();
    }
}
