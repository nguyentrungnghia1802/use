package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.Completeness;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class AuctionExternalOclProfileTest {
    static final Path PROFILE=Path.of("src/test/resources/jacamo/ocl/auction/auction_demo.ocl").toAbsolutePath();
    static final Path RUNTIME_PROFILE=Path.of("src/test/resources/jacamo/ocl/auction/auction_runtime.ocl").toAbsolutePath();
    @Test void auditedProfileCompilesAgainstActualOfficialAuctionNativeSchema() throws Exception {
        var snapshot=DomainRuntimeProjectionTest.load(Path.of("../../jacamo/examples/auction/auction.jcm"));
        var pipeline=new CodeGroundedNativePipeline().build(snapshot); var system=pipeline.state().system();
        var service=new ExternalOclConstraintService(system); var profile=service.install(PROFILE,snapshot.modelRevision());
        assertEquals(5,profile.constraints().size()); assertSame(system,service.system());
        assertEquals(ExternalOclConstraintService.sha256(Files.readAllBytes(PROFILE)),profile.sourceHash());
        var outcomes=service.evaluate(Map.of("jason",Completeness.COMPLETE,"moise",Completeness.PARTIAL,
                "moise.domain",Completeness.COMPLETE),false);
        var external=outcomes.stream().filter(o->o.constraintId().startsWith("EXTERNAL:")).toList();
        assertEquals(5,external.size());
        for(var outcome:external) assertEquals(outcome.constraintId().contains("DEMO_FAIL_") ? VerificationOutcome.FAIL : VerificationOutcome.PASS,outcome.outcome(),outcome.toString());
        var nativeGoals=outcomes.stream().filter(o->o.constraintId().startsWith("NATIVE:")).toList();
        assertEquals(6,nativeGoals.size());
        assertTrue(nativeGoals.stream().allMatch(o->o.outcome()==VerificationOutcome.PASS
                || o.outcome()==VerificationOutcome.SKIPPED && o.diagnostic().equals("REQUIRED_CAPABILITY_UNAVAILABLE")));
        assertEquals(5,system.state().objectsOfClassAndSubClasses(system.model().getClass("Agent")).size());
        assertNull(system.model().getClass("Role")); assertNull(system.model().getClass("Norm"));
    }
    @Test void incompleteSourcesCannotPassRuntimeDependentChecks() throws Exception {
        try(var observed=new AuctionDomainFixture()) {
            var service=new ExternalOclConstraintService(observed.projector.system());
            service.install(RUNTIME_PROFILE,observed.pipeline.source().revision());
            var incomplete=service.evaluate(Map.of(),false);
            var external=incomplete.stream().filter(o->o.constraintId().startsWith("EXTERNAL:")).toList();
            assertFalse(external.isEmpty());
            assertTrue(external.stream().allMatch(o->o.outcome()==VerificationOutcome.SKIPPED));
            assertTrue(incomplete.stream().filter(o->o.constraintId().startsWith("NATIVE:")).allMatch(o->
                    o.outcome()==VerificationOutcome.PASS || o.outcome()==VerificationOutcome.SKIPPED));
            assertTrue(service.evaluate(Map.of("cartago",Completeness.COMPLETE),true).stream().allMatch(o->o.outcome()==VerificationOutcome.SKIPPED));
            assertEquals(Set.of("cartago"),service.profile().constraints().getFirst().requiredSources());
        }
    }
    @Test void originalArtifactProjectsTypedObservableValuesAndStartStopViolationOnSameSystem() throws Exception {
        try(var observed=new AuctionDomainFixture()) {
            var projector=observed.projector; var system=projector.system(); var model=system.model();
            assertTrue(model.classes().stream().allMatch(NativeProjectionPolicy::allowsClass));
            assertTrue(model.getClass("AuctionArtifact").parents().contains(model.getClass("Artifact")));
            var cls=model.getClass("AuctionArtifact");
            assertEquals("Boolean",cls.attribute("running",false).type().toString());
            assertEquals("String",cls.attribute("task",false).type().toString());
            assertEquals("Real",cls.attribute("best_bid",false).type().toString());
            assertEquals("String",cls.attribute("winner",false).type().toString());
            assertNull(cls.attribute("currentWinner",false)); assertFalse(cls.operations().isEmpty());
            assertEquals(1,system.state().objectsOfClass(cls).size());
            assertEquals(5,system.state().allLinks().stream().filter(l->l.association().getAnnotation("RoleInGroup")!=null).count());
            assertEquals(5,system.state().allObjects().stream().filter(o->o instanceof org.tzi.use.uml.sys.MLinkObject).count());
            for(String forbidden:List.of("Plan","PlanLibrary","PlanBodyElement","Trigger","ObservablePropertySnapshot","CartagoAgentIdentity","ArtifactType","Role","RoleEnactment","AuctionRoleEnactment","AuctionGroupInstance","AuctionMissionCommitment","AuctionSchemeInstance","DoAuction","DoAuctionAuction","DoAuctionStart","DoAuctionBid","DoAuctionDecide","DoAuctionMAuctioneer","DoAuctionMParticipant","Norm"))
                assertNull(model.getClass(forbidden),forbidden);
            assertTrue(system.state().allLinks().stream().filter(l->l.association().getAnnotation("RoleInGroup")!=null).allMatch(l->l instanceof org.tzi.use.uml.sys.MLinkObject));
            projector.coordinator().loadProfile(RUNTIME_PROFILE);
            assertEquals(VerificationOutcome.PASS,runtimeOutcome(projector));
            observed.context.doAction(observed.artifact,new cartago.Op("start","test-task")); observed.refresh();
            assertEquals(VerificationOutcome.FAIL,runtimeOutcome(projector));
            observed.context.doAction(observed.artifact,new cartago.Op("bid",42.0)); observed.refresh();
            assertEquals("42.0",system.state().objectsOfClass(cls).iterator().next().state(system.state()).attributeValue("best_bid").toString());
            observed.context.doAction(observed.artifact,new cartago.Op("stop")); observed.refresh();
            assertEquals(VerificationOutcome.PASS,runtimeOutcome(projector)); assertSame(system,projector.system());
            Path evidence=Path.of("target/domain-projection/auction"); Files.createDirectories(evidence);
            new NativeUseExporter().export(model,evidence.resolve("runtime.use"));
            new NativeUseSoilExporter().export(system,evidence.resolve("runtime.cmd"));
            System.out.println("DOMAIN_AUCTION_RUNTIME_CLASSES="+model.classes().stream().map(c->c.name()).sorted().toList());
            System.out.println("DOMAIN_AUCTION_RUNTIME_OBJECTS="+system.state().allObjects().stream().map(o->o.name()+":"+o.cls().name()).sorted().toList());
        }
    }
    private static VerificationOutcome runtimeOutcome(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector projector) {
        return projector.coordinator().latest().outcomes().stream().filter(o->o.constraintId().equals("EXTERNAL:AuctionArtifact::DEMO_RUNTIME_NoRunningAuction")).findFirst().orElseThrow().outcome();
    }
}
