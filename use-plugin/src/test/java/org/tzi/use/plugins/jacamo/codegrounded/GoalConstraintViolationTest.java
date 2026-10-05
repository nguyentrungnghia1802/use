package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.jacamo.bridge.adapter.OfficialMoiseAdapter;
import org.jacamo.bridge.contract.Completeness;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.uml.ocl.value.*;

/** Deliberate USE-only diagnostic fixtures; no domain state in a JaCaMo runtime is edited. */
class GoalConstraintViolationTest {
    @Test void representedSequenceOrdinalsAndCurrentResponsibilityFailOnExactContexts() throws Exception {
        Path source=Path.of("../../jacamo/examples/auction/src/org/auction-os.xml").toAbsolutePath().normalize();
        var organization=new OfficialMoiseAdapter().load(source.getParent(),source,"verification-fixture").organization();
        var pipeline=new CodeGroundedNativePipeline().build(MoiseDomainProjectionTest.withOrganizations(CodeGroundedTestFixtures.helloSnapshot(),List.of(organization)));
        var system=pipeline.state().system();var state=system.state();var api=UseSystemApi.create(system,false);
        try(var coordinator=new RuntimeVerificationCoordinator(new NativeRuntimeMutationEngine(system,pipeline.state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry()),"fixture",1,"revision")) {
            coordinator.acceptedSnapshot("fixture",1,"revision",Map.of("moise.domain",Completeness.COMPLETE,"jason",Completeness.COMPLETE));
            coordinator.runtimeCapabilities(Map.of("runtime.moise.goalState.v1","COMPLETE"),Map.of());coordinator.manualVerify();
            String sequence="NATIVE:OrganizationalGoal::GoalSequenceOrdinals";
            assertEquals(VerificationOutcome.PASS,coordinator.latest().outcomes().stream().filter(o->o.constraintId().equals(sequence)).findFirst().orElseThrow().outcome());
            var goal=state.allObjects().stream().filter(o->o.cls().name().equals("OrganizationalGoal")
                    && new StringValue("sequence").equals(o.state(state).attributeValue("decompositionOperator"))).findFirst().orElseThrow();
            var children=state.allLinks().stream().filter(l->l.association().name().equals(DomainProjection.relation("subGoals","OrganizationalGoal","OrganizationalGoal"))
                    && l.linkedObjects().getFirst()==goal).map(l->l.linkedObjects().get(1)).toList();
            assertTrue(children.size()>1);
            coordinator.read(()->{for(var child:children)api.setAttributeValueEx(child,child.cls().attribute("orderInParent",true),IntegerValue.valueOf(0));return null;});
            coordinator.manualVerify();
            var failed=coordinator.latest().outcomes().stream().filter(o->o.constraintId().equals(sequence)).findFirst().orElseThrow();
            assertEquals(VerificationOutcome.FAIL,failed.outcome());assertTrue(coordinator.read(()->coordinator.constraints().failingContexts(failed)).contains(goal.name()));
            var responsibility="NATIVE:OrganizationalGoal::GoalCommittedResponsibility";var occurrence=children.getFirst();
            var agent=state.allObjects().stream().filter(o->DomainProjection.kind(o.cls()).equals("agent-program")).findFirst().orElseThrow();
            coordinator.read(()->{
                api.setAttributeValueEx(occurrence,occurrence.cls().attribute("stateEvidence",true),new StringValue("OFFICIAL_SCHEME_BOARD_OBSERVABLE_V1"));
                api.createLink(DomainProjection.relation("goalCommitment","Agent","OrganizationalGoal"),new String[]{agent.name(),occurrence.name()});return null;
            });
            coordinator.manualVerify();var broken=coordinator.latest().outcomes().stream().filter(o->o.constraintId().equals(responsibility)).findFirst().orElseThrow();
            assertEquals(VerificationOutcome.FAIL,broken.outcome());assertTrue(coordinator.read(()->coordinator.constraints().failingContexts(broken)).contains(occurrence.name()));
            assertSame(state,system.state());assertTrue(pipeline.trace().records().stream().filter(t->t.sourceIdentity().equals(organization.metadata().semanticId()))
                    .flatMap(t->t.sourceEvidence().stream()).allMatch(e->e.startLine()==0));
        }
    }
}
