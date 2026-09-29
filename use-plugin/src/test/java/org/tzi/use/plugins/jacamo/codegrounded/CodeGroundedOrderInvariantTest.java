package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CodeGroundedOrderInvariantTest {
    @Test void helloRetainsEveryPlanBodyNodeAndExactA17A19A20Order() throws Exception {
        var result = new CodeGroundedNativePipeline().build(CodeGroundedTestFixtures.helloSnapshot(),
                org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.FULL);
        long sourcePlans = result.source().programs().stream()
                .mapToLong(program -> program.planLibrary().plans().size()).sum();
        long sourceBodies = result.source().programs().stream().flatMap(program -> program.planLibrary().plans().stream())
                .mapToLong(plan -> plan.body().size()).sum();
        long sourceNext = result.source().programs().stream().flatMap(program -> program.planLibrary().plans().stream())
                .mapToLong(plan -> Math.max(0, plan.body().size() - 1)).sum();
        var system = result.state().system();
        assertEquals(sourcePlans, system.state().objectsOfClass(system.model().getClass("Plan")).size());
        assertEquals(sourcePlans, system.state().objectsOfClass(system.model().getClass("A17PlanOrderEntry")).size());
        assertEquals(sourceBodies, system.state().objectsOfClass(system.model().getClass("PlanBodyElement")).size());
        assertEquals(sourceBodies, system.state().objectsOfClass(system.model().getClass("A19BodyOrderEntry")).size());
        assertEquals(sourceNext, system.state().linksOfAssociation(system.model().getAssociation("A20PlanBodyNext")).size());
        assertTrue(result.state().structureValid());
        assertTrue(result.state().invariantsValid());
    }
}
