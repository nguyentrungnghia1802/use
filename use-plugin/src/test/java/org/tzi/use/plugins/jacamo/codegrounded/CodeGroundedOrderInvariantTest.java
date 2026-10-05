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
        assertEquals(sourcePlans,result.trace().records().stream().filter(t -> t.ruleId().equals("A03")).count());
        assertEquals(sourceBodies,result.trace().records().stream().filter(t -> t.ruleId().equals("A05")).count());
        assertEquals(sourceNext,result.trace().records().stream().filter(t -> t.ruleId().equals("A20")).count());
        for(String name:java.util.List.of("Plan","PlanLibrary","PlanBodyElement","A17PlanOrderEntry","A19BodyOrderEntry")) assertNull(system.model().getClass(name));
        for(var program:result.source().programs()) for(var plan:program.planLibrary().plans()) for(int i=0;i<plan.body().size();i++)
            assertEquals(i+1<plan.body().size() ? plan.body().get(i+1).metadata().semanticId() : "",plan.body().get(i).nextSemanticId());
        assertTrue(result.state().structureValid());
        assertTrue(result.state().invariantsValid());
    }
}
