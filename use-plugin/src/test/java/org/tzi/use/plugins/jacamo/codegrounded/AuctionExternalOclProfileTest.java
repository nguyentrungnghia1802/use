package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.jacamo.bridge.contract.Completeness;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

/** Schema/compiler evidence against the original example; this is not live runtime evidence. */
class AuctionExternalOclProfileTest {
    private static final Path PROFILE = Path.of("src/test/resources/jacamo/ocl/auction/auction_demo.ocl")
            .toAbsolutePath().normalize();

    @Test void auditedProfileCompilesAgainstActualOfficialAuctionNativeSchema() throws Exception {
        var entry = Path.of("..", "..", "jacamo", "examples", "auction", "auction.jcm").toAbsolutePath().normalize();
        var snapshot = new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(entry), entry);
        // The existing profile checks specification objects, so its explicit contract is FULL inspection.
        var system = new CodeGroundedNativePipeline().build(snapshot,
                org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.FULL).state().system();
        assertEquals(5, system.state().objectsOfClass(system.model().getClass("Agent")).size());
        var roles = system.state().objectsOfClass(system.model().getClass("Role"));
        System.out.println("AUCTION_NATIVE_ROLES=" + roles.stream().map(role -> role.state(system.state()).attributeValue("roleId")
                + ":abstract=" + role.state(system.state()).attributeValue("isAbstract")).sorted().toList());
        assertEquals(3, roles.size(), "Official Moise retains its implicit abstract soc Role as well as the two declared Roles");
        assertTrue(roles.stream().anyMatch(role -> role.state(system.state()).attributeValue("roleId").toString()
                .equals("'soc'")
                && role.state(system.state()).attributeValue("isAbstract").toString().equals("true")));
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("Group")).size());
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("Scheme")).size());
        assertEquals(2, system.state().objectsOfClass(system.model().getClass("Mission")).size());
        assertEquals(4, system.state().objectsOfClass(system.model().getClass("OrganizationalGoal")).size());
        assertEquals(2, system.state().objectsOfClass(system.model().getClass("Norm")).size());
        var service = new ExternalOclConstraintService(system);
        var profile = service.install(PROFILE, snapshot.modelRevision());
        assertSame(system, service.system());
        assertEquals(12, profile.constraints().size());
        assertEquals(PROFILE.toString(), profile.sourceFile());
        assertEquals(ExternalOclConstraintService.sha256(Files.readAllBytes(PROFILE)), profile.sourceHash());
        // Explicit complete-source fixture tests the OCL expressions only; no live coverage claim.
        var outcomes = service.evaluate(Map.of("jason", Completeness.COMPLETE, "cartago", Completeness.COMPLETE), false)
                .stream().filter(outcome -> outcome.constraintId().startsWith("EXTERNAL:")).toList();
        assertEquals(12, outcomes.size());
        for (var outcome : outcomes) assertEquals(outcome.constraintId().contains("DEMO_FAIL_")
                ? VerificationOutcome.FAIL : VerificationOutcome.PASS, outcome.outcome(), outcome.toString());
        assertEquals(5, system.state().objectsOfClass(system.model().getClass("Agent")).size(), "FAIL cannot delete faithful declarations");
    }

    @Test void incompleteSourcesCannotPassRuntimeDependentChecksEvenWithEmptyPropertyClass() throws Exception {
        var entry = Path.of("..", "..", "jacamo", "examples", "auction", "auction.jcm").toAbsolutePath().normalize();
        var snapshot = new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(entry), entry);
        var service = new ExternalOclConstraintService(new CodeGroundedNativePipeline().build(snapshot,
                org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.FULL).state().system());
        service.install(PROFILE, snapshot.modelRevision());
        var outcomes = service.evaluate(Map.of(), false).stream()
                .filter(outcome -> outcome.constraintId().startsWith("EXTERNAL:")).toList();
        assertEquals(4, outcomes.stream().filter(outcome -> outcome.outcome() == VerificationOutcome.SKIPPED).count());
        assertTrue(outcomes.stream().filter(outcome -> outcome.contextClass().equals("Agent")
                || outcome.contextClass().equals("Artifact")
                || outcome.contextClass().equals("ObservablePropertySnapshot"))
                .allMatch(outcome -> outcome.outcome() == VerificationOutcome.SKIPPED));
        assertTrue(service.evaluate(Map.of("jason", Completeness.COMPLETE, "cartago", Completeness.COMPLETE), true)
                .stream().allMatch(outcome -> outcome.outcome() == VerificationOutcome.SKIPPED));
    }

    @Test void runtimePolicyUsesCompiledArtifactTypeAndPropertyAssociationsWithCartagoCoverage() throws Exception {
        var entry = Path.of("..", "..", "jacamo", "examples", "auction", "auction.jcm").toAbsolutePath().normalize();
        var snapshot = new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(entry), entry);
        var service = new ExternalOclConstraintService(new CodeGroundedNativePipeline().build(snapshot,
                org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.FULL).state().system());
        var profile = service.install(PROFILE, snapshot.modelRevision());
        var runtimePolicy = profile.constraints().stream().filter(constraint -> constraint.constraintId()
                .equals("Artifact::DEMO_RUNTIME_NoRunningAuction")).findFirst().orElseThrow();
        assertEquals("Artifact", runtimePolicy.contextClass());
        assertEquals(Set.of("Artifact", "ArtifactType", "ObservablePropertySnapshot"), runtimePolicy.requiredClasses());
        assertEquals(Set.of("C03", "C04", "C09"), runtimePolicy.requiredRules());
        assertEquals(Set.of("cartago"), runtimePolicy.requiredSources());
        var outcome = service.evaluate(Map.of("jason", Completeness.COMPLETE), false).stream()
                .filter(item -> item.constraintId().equals("EXTERNAL:" + runtimePolicy.constraintId()))
                .findFirst().orElseThrow();
        assertEquals(VerificationOutcome.SKIPPED, outcome.outcome(), "No live Cartago coverage cannot become a vacuous PASS");
    }
}
