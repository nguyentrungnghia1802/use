package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.jacamo.bridge.adapter.OfficialMoiseAdapter;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;

/** Phase 9 proves bounded native activation for every checked-in canonical case. */
class Phase9CaseStudyAcceptanceTest {
    private record Case(String name, Path entry) { }

    @Test
    void canonicalCasesActivateOneNativeSessionAndVerificationSystem() throws Exception {
        Path examples = Path.of("..", "..", "JaCaMo", "examples").toAbsolutePath().normalize();
        List<Case> cases = List.of(
                new Case("hello", Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm")
                        .toAbsolutePath().normalize()),
                new Case("auction", examples.resolve("auction/auction.jcm")),
                new Case("house-building", examples.resolve("house-building/house-building.jcm")));
        for (Case value : cases) {
            Session session = new Session();
            try (var facade = BridgeFacadeTestSupport.nativeFacade(value.entry(), session, () -> false)) {
                var summary = facade.importProject(value.entry());
                assertEquals(PipelineMode.CODE_GROUNDED_NATIVE, facade.pipelineMode(), value.name());
                assertTrue(summary.structureValid(), value.name());
                assertTrue(summary.generatedClasses() > 0, value.name());
                assertTrue(summary.generatedObjects() > 0, value.name());
                assertSame(session.system(), facade.materializedSystem(), value.name());
                assertSame(session.system(), facade.materializedSystem().state().system(), value.name());
                assertEquals(0, objects(session.system(), "Artifact"),
                        value.name() + " must not fabricate a live CArtAgO artifact from static declarations");
                assertNotNull(facade.runFullVerification(), value.name());
                facade.connectRuntime();
                assertSame(session.system(), facade.materializedSystem(), value.name());
                assertNotNull(facade.runFullVerification(), value.name());
            }
        }
    }

    @Test
    void officialSnapshotsRetainCaseBoundariesAndUnavailableDynamicFacts() throws Exception {
        ModelSnapshot hello = load(hello());
        assertFalse(hello.semanticContract().agentDeclarations().isEmpty());
        assertFalse(hello.semanticContract().workspaceDeclarations().isEmpty());
        assertFalse(hello.semanticContract().artifactDeclarations().isEmpty());
        assertTrue(hello.semanticContract().jasonPrograms().stream()
                .flatMap(program -> program.planLibrary().plans().stream())
                .anyMatch(plan -> plan.trigger() != null && !plan.body().isEmpty()));
        assertFalse(hello.semanticContract().moiseOrganizations().isEmpty());

        var auction = load(auction());
        assertFalse(auction.semanticContract().moiseOrganizations().isEmpty());
        var auctionOrganization = auction.semanticContract().moiseOrganizations().getFirst();
        assertFalse(auctionOrganization.functionalSpecification().schemes().isEmpty());
        assertFalse(auctionOrganization.structuralSpecification().groupRoleCardinalities().isEmpty());
        var auctionPipeline = new CodeGroundedNativePipeline().build(auction);
        assertTrue(auctionPipeline.model().constraints().stream()
                .noneMatch(constraint -> constraint.requiredRuleIds().contains("M14")),
                "Moise Norm data must not become native OCL");
        assertTrue(auction.semanticContract().cartagoEnvironments().isEmpty(),
                "no live CArtAgO environment is available from the static official project snapshot");
        assertEquals(0, objects(auctionPipeline.state().system(), "Artifact"));

        var house = load(houseBuilding());
        assertTrue(house.semanticContract().moiseOrganizations().isEmpty(),
                "House Building .jcm must not be treated as the complete dynamic organization specification");
        assertTrue(house.semanticContract().artifactDeclarations().isEmpty());
        var houseOs = new OfficialMoiseAdapter().load(houseBuilding().getParent(),
                houseBuilding().getParent().resolve("src/org/house-os.xml"), "house_building");
        var houseOrganization = houseOs.organization();
        assertTrue(houseOrganization.structuralSpecification().roles().stream()
                .anyMatch(role -> !role.superRoleSemanticIds().isEmpty()));
        assertFalse(houseOs.groupRoleCardinalities().isEmpty());
        Set<String> operators = houseOrganization.functionalSpecification().schemes().stream()
                .flatMap(scheme -> scheme.plans().stream())
                .map(plan -> plan.operator().toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        assertTrue(operators.contains("sequence"));
        assertTrue(operators.contains("parallel"));
        var housePipeline = new CodeGroundedNativePipeline().build(house);
        assertEquals(0, objects(housePipeline.state().system(), "Organization"),
                "dynamic House Building organization remains unavailable until an official runtime/OS snapshot is supplied");
    }

    private static ModelSnapshot load(Path entry) throws Exception {
        return new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(entry), entry);
    }

    private static int objects(org.tzi.use.uml.sys.MSystem system, String className) {
        return system.model().getClass(className) == null ? 0
                : system.state().objectsOfClass(system.model().getClass(className)).size();
    }

    private static Path hello() {
        return Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm")
                .toAbsolutePath().normalize();
    }

    private static Path auction() {
        return Path.of("..", "..", "JaCaMo", "examples", "auction", "auction.jcm")
                .toAbsolutePath().normalize();
    }

    private static Path houseBuilding() {
        return Path.of("..", "..", "JaCaMo", "examples", "house-building", "house-building.jcm")
                .toAbsolutePath().normalize();
    }
}
