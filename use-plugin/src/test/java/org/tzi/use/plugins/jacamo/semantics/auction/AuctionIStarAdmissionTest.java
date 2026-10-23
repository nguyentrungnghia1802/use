package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.semantic.engine.api.*;
import org.tzi.use.semantic.engine.frontend.IStarFrontend;
import org.tzi.use.semantic.engine.runtime.*;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.dsl.istar.parser.IStarCompiler;
import org.vnu.sme.goal.verify.conformance.semantics.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionIStarAdmissionTest {
    private static final AuctionCaseBundle BUNDLE = AuctionCaseBundle.read(Path.of("examples/auction"));
    private static final EngineSemanticsProfile POLICY = EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1;
    @Test void exactOracleIsAdmittedOnlyByExplicitPolicyAndAllEndpointsArePreserved() {
        var prepared = new AuctionIStarSourceAdapter().prepare(BUNDLE);
        assertEquals(3, IStarCompiler.compile(prepared.executableSource()).errors().size());
        var result = new IStarFrontend().compile(prepared.executableSource(), "Auction executable view", POLICY);
        assertTrue(result.ok(), result.diagnostics().toString());
        var model = result.takeOwnership().model();
        assertEquals(Set.of("A_start", "A_decide", "P_bid"), model.getDependencies().stream().map(Dependency::dependeeElmt).collect(java.util.stream.Collectors.toSet()));
        assertEquals(3, model.getDependencies().size());
        assertEquals(4, model.getActors().stream().mapToInt(a -> a.refinements().size()).sum());
        assertEquals(3, model.getActors().stream().mapToInt(a -> a.neededBys().size()).sum());
        assertEquals(2, model.getActors().stream().mapToInt(a -> a.associations().size()).sum());
        assertTrue(OwnedGoalModel.validateDefinition(model, "oracle", POLICY).isEmpty());
        assertEquals(3, OwnedGoalModel.validateDefinition(model, "strict").size());
        var cast = model.getActors().stream().map(a -> new ScenarioInstance("instance_" + a.name(), a.name())).toList();
        try (var session = new Use4GoalRuntimeSessionAdapter(model, cast, (a,x,b,y)->true, "verified-single-scope-fixture", POLICY)) {
            assertEquals(3, session.topology().instance().model().getDependencies().size());
            var tasks = new LinkedHashMap<String,EngineInputs.TaskInput>();
            var resources = new LinkedHashMap<EngineInputs.ResourceKey,EngineInputs.ResourceInput>();
            var needed = new HashMap<String,Set<String>>();
            model.getActors().forEach(a -> a.neededBys().forEach(n -> needed.computeIfAbsent(n.task(), k -> new HashSet<>()).add(n.resource())));
            session.topology().occurrences().forEach((id,b) -> {
                if (!(model.findElement(b.sourceElementId()).orElseThrow() instanceof Task)) return;
                tasks.put(id,new EngineInputs.TaskInput("invocation-fixture",true,true,true));
                needed.getOrDefault(b.sourceElementId(),Set.of()).forEach(r -> resources.put(new EngineInputs.ResourceKey(id,r),
                        new EngineInputs.ResourceInput("invocation-fixture",true,"explicit-artifact-binding-fixture")));
            });
            var step = session.step(new EngineInputs(Map.of(),tasks,Map.of(),resources));
            for (var dependency : session.topology().instance().model().getDependencies())
                assertEquals(GoalTaskStatus.FULFILLED,step.aggregate().goalTaskStatus(dependency.dependerElmt()));
            assertTrue(step.originalApiCalls().contains("TaskMarking.update"));
            assertTrue(step.originalApiCalls().contains("IStarPropagation.closePending"));
            assertTrue(step.originalApiCalls().contains("GoalModelInstanceTranslator.propagateDependencies"));
        }
    }
}
