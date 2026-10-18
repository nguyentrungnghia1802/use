import java.nio.file.*;
import java.util.*;
import org.vnu.sme.goal.dsl.csl.parser.CslCompiler;
import org.vnu.sme.goal.dsl.istar.parser.IStarCompiler;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.dsl.iscn.mm.*;
import org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser.GoalModelInstanceTranslator;
import org.vnu.sme.goal.verify.conformance.semantics.*;

/** Independent original-source oracle. Not part of the production engine. */
public final class OriginalCoreOracle {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var csl = CslCompiler.compile("""
                csl v4.0 Oracle {
                  enum Status { DRAFT, ACTIVE }
                  entity Counter { p : Integer; }
                  orgContext Team { role Worker; }
                }
                """, "original-positive");
        require(csl.ok(), "CSL original compiler: " + csl.errors());
        require(csl.model().entities().size() == 1, "CSL entity count");
        require(csl.model().roles().size() == 1, "CSL role count");
        require(csl.model().orgContexts().size() == 1, "CSL context count");
        require(csl.model().enums().size() == 1, "CSL enum count");
        require(!CslCompiler.compile("csl v4.0 Bad { entity A extends Missing; }").ok(),
                "CSL unknown parent rejected");
        require(!CslCompiler.compile("csl v2.0 Legacy { group Classroom { Teacher [1]; } }").ok(),
                "legacy group syntax rejected by current grammar");
        var istar = IStarCompiler.compile("""
                istar Oracle {
                  role Worker { goal supplied : Achieve condition {[ true ]} }
                  role Reviewer { goal requested : Achieve }
                  depend Reviewer.requested -> goal commitment -> Worker.supplied
                }
                """);
        require(istar.ok(), "iStar original compiler: " + istar.errors());
        require(istar.model().getActors().size() == 2, "iStar actors");
        require(istar.model().getDependencies().size() == 1, "iStar dependency");
        require(GoalModelValidator.validate(istar.model()).isEmpty(), "original iStar validator");
        IStarScenarioModel input = new IStarScenarioModel("oracle", "<memory>",
                List.of(new ScenarioInstance("alice", "Worker"), new ScenarioInstance("bob", "Reviewer")),
                List.of(new ScenarioStmt.Assign("alice", "supplied", "Fulfilled")));
        Map<String,String> contexts = Map.of("alice", "team", "bob", "team");
        var instantiated = GoalModelInstanceTranslator.evaluate(istar.model(), input,
                (leftType, left, rightType, right) -> contexts.get(left).equals(contexts.get(right)));
        require(instantiated.instanceModel().getDependencies().size() == 1,
                "contextual dependency instantiated");
        Files.writeString(Path.of(args[0] + ".topology.txt"),
                instantiated.nodeLabels() + "\n" + instantiated.instanceMarking() + "\n"
                + instantiated.instanceModel().getDependencies().stream().map(d ->
                    d.dependerElmt() + " <- " + d.dependeeElmt()).toList() + "\n");
        Dependency concrete = instantiated.instanceModel().getDependencies().getFirst();
        require(instantiated.instanceMarking().goalTaskStatus(concrete.dependeeElmt()) == GoalTaskStatus.FULFILLED,
                "exact dependency supplier occurrence");
        require(instantiated.instanceMarking().goalTaskStatus(concrete.dependerElmt()) == GoalTaskStatus.FULFILLED,
                "exact dependency requester occurrence");
        int requested = 0, standalone = 0;
        for (var entry : instantiated.goalModelInstance().elementInstanceOf().entrySet()) {
            if (entry.getValue().id().equals("requested")) {
                if (!entry.getKey().equals(concrete.dependerElmt())) {
                    require(instantiated.instanceMarking().goalTaskStatus(entry.getKey()) == GoalTaskStatus.UNKNOWN,
                            "standalone requester is not the dependency-bound requester");
                    standalone++;
                }
                requested++;
            }
        }
        require(requested == 2 && standalone == 1,
                "original preserves standalone and dependency-bound binding stacks separately");
        var separated = GoalModelInstanceTranslator.evaluate(istar.model(), input,
                (leftType, left, rightType, right) -> left.equals(right));
        require(separated.instanceModel().getDependencies().isEmpty(), "no cross-context dependency");
        var none = GoalMarking.initial(GoalType.NONE).update(true, false);
        require(none.status() == GoalTaskStatus.PENDING, "original NONE remains PENDING");
        var task = TaskMarking.initial().update(true, true, false).update(true, false, true);
        require(task.status() == GoalTaskStatus.FULFILLED, "historical Task");
        String observation = "{\"scope\":\"ISOLATED_PINNED_ORIGINAL_SOURCE\",\"checks\":" + checks + ","
                + "\"csl\":\"" + csl.model().name() + "\",\"entity\":\""
                + csl.model().entities().getFirst().name() + "\",\"role\":\""
                + csl.model().roles().getFirst().name() + "\",\"context\":\""
                + csl.model().orgContexts().getFirst().name()
                + "\",\"istarActors\":2,\"contextualDependency\":\"FULFILLED\","
                + "\"requestedOccurrences\":2,\"standaloneRequester\":\"UNKNOWN\","
                + "\"noneFalse\":\"PENDING\",\"taskHistorical\":\"FULFILLED\"}";
        Files.writeString(Path.of(args[0]), observation + "\n");
        System.out.println("ORIGINAL_CORE_ORACLE_OK");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
