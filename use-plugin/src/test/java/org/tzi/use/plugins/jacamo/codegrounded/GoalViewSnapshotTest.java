package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.uml.ocl.value.StringValue;

class GoalViewSnapshotTest {
    @Test void sameSpecInTwoInstancesUsesDistinctActiveObjectsAndReadingViewDoesNotMutateState() throws Exception {
        var org=MoiseDomainProjectionTest.organization();var source=org.functionalSpecification().schemes().getFirst();
        var pipeline=new CodeGroundedNativePipeline().build(MoiseDomainProjectionTest.withOrganizations(CodeGroundedTestFixtures.helloSnapshot(),List.of(org)));
        var system=pipeline.state().system();var api=UseSystemApi.create(system,false);var index=new LinkedHashMap<>(pipeline.state().semanticObjectIndex());
        for(String id:List.of("instance:one","instance:two")) {
            var scheme=api.createObject(DomainProjection.classFor(system.model(),"scheme",source.metadata().semanticId()),id.endsWith("one")?"firstInstance":"secondInstance");
            api.setAttributeValueEx(scheme,scheme.cls().attribute("semanticId",true),new StringValue(id));index.put(id,scheme);
            api.setAttributeValueEx(scheme,scheme.cls().attribute("name",true),new StringValue(id));
            api.setAttributeValueEx(scheme,scheme.cls().attribute("sourceLayer",true),new StringValue("RUNTIME"));
            api.setAttributeValueEx(scheme,scheme.cls().attribute("runtimeIdentity",true),new StringValue(id+":exact-incarnation"));
            MoiseDomainProjection.materializeFunctional(api,index,new CodeGroundedTraceCollector(),org,source,scheme);
            for(var goal:source.goals()) {
                var object=index.get(MoiseDomainProjection.functionalObjectId("organisational-goal",id,goal.metadata().semanticId()));
                api.setAttributeValueEx(object,object.cls().attribute("runtimeState",true),new StringValue("WAITING"));
                api.setAttributeValueEx(object,object.cls().attribute("stateEvidence",true),new StringValue("OFFICIAL_SCHEME_BOARD_OBSERVABLE_V1"));
            }
        }
        var engine=new NativeRuntimeMutationEngine(system,index,new CodeGroundedRuntimeRuleRegistry());
        try(var coordinator=new RuntimeVerificationCoordinator(engine,"s",1,"r")) {
            coordinator.manualVerify();var state=system.state();var before=new NativeUseSoilExporter().export(system).commands();
            var view=GoalViewSnapshot.read(coordinator,List.of(),List.of(),null);
            var runtime=view.schemes().stream().filter(GoalViewSnapshot.Scheme::runtime).toList();assertEquals(2,runtime.size());
            assertEquals(runtime.getFirst().specId(),runtime.getLast().specId());
            assertTrue(Collections.disjoint(runtime.getFirst().goals().stream().map(GoalViewSnapshot.Goal::object).toList(),runtime.getLast().goals().stream().map(GoalViewSnapshot.Goal::object).toList()));
            assertTrue(runtime.stream().flatMap(s->s.goals().stream()).allMatch(g->g.state().equals("UNKNOWN / UNAVAILABLE")
                    && g.evidence().contains("last observed WAITING")),"Copied attributes alone do not establish current SchemeBoard availability");
            assertTrue(view.schemes().stream().filter(s->!s.runtime()).flatMap(s->s.goals().stream()).allMatch(g->g.state().equals("UNKNOWN / UNAVAILABLE")));
            assertTrue(runtime.getFirst().goals().stream().anyMatch(g->g.operator().equals("choice") && !g.children().isEmpty()));
            assertSame(state,system.state());assertEquals(before,new NativeUseSoilExporter().export(system).commands());
        }
    }
}
