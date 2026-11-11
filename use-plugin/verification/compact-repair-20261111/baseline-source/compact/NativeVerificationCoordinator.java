package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.jacamo.bridge.contract.VerifiedCheckpointProtocol;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol.Check;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;
import org.vnu.sme.goal.dsl.csl.mm.CslInvariant;
import org.vnu.sme.goal.dsl.istar.mm.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 * Evaluates native CSL/OCL invariants and iStar goal/task boundaries over authoritative MSystem state.
 * Synthesizes exact-token VerifiedCheckpointProtocol checks and enforces decision gates.
 */
public final class NativeVerificationCoordinator {

    public record InvariantResult(
            String invariantName,
            String className,
            String verdict, // "PASS", "FAIL", "UNKNOWN", "ERROR"
            String diagnostic,
            List<String> violatingObjects) {}

    public record VerificationAssessment(
            String cutId,
            boolean allPassed,
            boolean hasFailures,
            boolean hasUnknowns,
            Map<String, Check> checkpointChecks,
            List<InvariantResult> invariantResults,
            VerifiedCheckpointProtocol.Action recommendedAction,
            Map<String, String> intentionalVerdicts) {

        public VerificationAssessment(
                String cutId,
                boolean allPassed,
                boolean hasFailures,
                boolean hasUnknowns,
                Map<String, Check> checkpointChecks,
                List<InvariantResult> invariantResults,
                VerifiedCheckpointProtocol.Action recommendedAction) {
            this(cutId, allPassed, hasFailures, hasUnknowns, checkpointChecks, invariantResults, recommendedAction, Map.of());
        }

        public VerificationAssessment {
            checkpointChecks = Map.copyOf(checkpointChecks == null ? Map.of() : checkpointChecks);
            invariantResults = List.copyOf(invariantResults == null ? List.of() : invariantResults);
            intentionalVerdicts = Map.copyOf(intentionalVerdicts == null ? Map.of() : intentionalVerdicts);
        }
    }

    public static VerificationAssessment evaluate(
            CompiledObservationPlan plan,
            MSystem system,
            PhysicalCutFact cut) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(cut, "cut");

        List<InvariantResult> invResults = new ArrayList<>();
        Map<String, Check> checks = new LinkedHashMap<>();
        Map<String, String> intentionalVerdicts = new LinkedHashMap<>();
        MSystemState state = system.state();
        Set<String> evaluatedInvariantKeys = new HashSet<>();

        // 1. Evaluate CSL / OCL class invariants installed on MSystem
        for (MClassInvariant inv : system.model().classInvariants()) {
            String invName = inv.name();
            String clsName = inv.cls().name();
            evaluatedInvariantKeys.add(clsName + "::" + invName);
            List<String> violatingObjects = new ArrayList<>();
            boolean pass = true;
            String diagnostic = "PASS";

            for (MObject obj : state.allObjects()) {
                if (obj.cls().conformsTo(inv.cls())) {
                    try {
                        Evaluator evaluator = new Evaluator();
                        VarBindings bindings = new VarBindings();
                        bindings.push("self", obj.value());
                        Value v = evaluator.eval(inv.bodyExpression(), state, bindings);
                        if (v instanceof BooleanValue bv) {
                            if (!bv.value()) {
                                pass = false;
                                violatingObjects.add(obj.name());
                                diagnostic = "INVARIANT_VIOLATION on " + obj.name();
                            }
                        } else if (v == null || v.isUndefined()) {
                            pass = false;
                            violatingObjects.add(obj.name());
                            diagnostic = "INVARIANT_EVALUATED_TO_UNDEFINED on " + obj.name();
                        }
                    } catch (Exception e) {
                        pass = false;
                        violatingObjects.add(obj.name());
                        diagnostic = "EVALUATION_ERROR: " + e.getMessage();
                    }
                }
            }

            String verdict = pass ? "PASS" : "FAIL";
            invResults.add(new InvariantResult(invName, clsName, verdict, diagnostic, violatingObjects));
        }

        // 1b. Evaluate any CslInvariant in plan.cslModel() not yet installed as MClassInvariant on MSystem
        if (plan.cslModel().isPresent()) {
            for (CslInvariant cslInv : plan.cslModel().get().invariants()) {
                String clsName = cslInv.contextType();
                String invName = cslInv.name();
                if (evaluatedInvariantKeys.contains(clsName + "::" + invName)) {
                    continue;
                }
                MClass cls = system.model().getClass(clsName);
                if (cls == null) {
                    continue;
                }
                List<String> violatingObjects = new ArrayList<>();
                boolean pass = true;
                String diagnostic = "PASS";
                for (MObject obj : state.allObjects()) {
                    if (obj.cls().conformsTo(cls)) {
                        Value v = evalOclForObject(system, state, cls, obj, cslInv.expression().expression());
                        if (v instanceof BooleanValue bv) {
                            if (!bv.value()) {
                                pass = false;
                                violatingObjects.add(obj.name());
                                diagnostic = "INVARIANT_VIOLATION on " + obj.name();
                            }
                        } else {
                            pass = false;
                            violatingObjects.add(obj.name());
                            diagnostic = "INVARIANT_EVALUATED_TO_UNDEFINED on " + obj.name();
                        }
                    }
                }
                invResults.add(new InvariantResult(invName, clsName, pass ? "PASS" : "FAIL", diagnostic, violatingObjects));
            }
        }

        // 2. Synthesize Checkpoint Protocol Checks based on compiled plan rules
        boolean hasFailures = false;
        boolean hasUnknowns = false;

        // If any invariant failed, record failure
        for (InvariantResult ir : invResults) {
            String checkId = "csl.inv:" + ir.className() + "." + ir.invariantName();
            if ("FAIL".equals(ir.verdict())) {
                checks.put(checkId, new Check("FAIL", "APPLICABLE", ir.diagnostic()));
                hasFailures = true;
            } else {
                checks.put(checkId, new Check("PASS", "APPLICABLE", ir.diagnostic()));
            }
        }

        // Index tasks from GoalModel if available
        Map<String, Task> tasksByName = new LinkedHashMap<>();
        Map<String, Actor> taskOwnerByName = new LinkedHashMap<>();
        if (plan.goalModel().isPresent()) {
            for (Actor actor : plan.goalModel().get().getActors()) {
                for (IntentionalElement elem : actor.elements()) {
                    if (elem instanceof Task t) {
                        tasksByName.putIfAbsent(t.id(), t);
                        taskOwnerByName.putIfAbsent(t.id(), actor);
                    }
                }
            }
        }

        // Evaluate task receipt boundaries from cut
        for (VerifiedCheckpointProtocol.Rule rule : plan.checkpointRules()) {
            if ("TASK_PRE".equals(rule.selectorKind()) || "TASK_POST".equals(rule.selectorKind())) {
                boolean isPost = "TASK_POST".equals(rule.selectorKind());
                for (String task : rule.operations()) {
                    String checkKey = rule.id();
                    List<PhysicalCutFact.StepReceipt> matchingReceipts = selectActiveInvocationReceipts(cut.stepReceipts(), task);

                    if (!matchingReceipts.isEmpty()) {
                        boolean anyFailed = matchingReceipts.stream().anyMatch(r -> "FAILED".equalsIgnoreCase(r.status()));
                        boolean allSucceeded = matchingReceipts.stream().allMatch(r -> "SUCCEEDED".equalsIgnoreCase(r.status()));
                        boolean allPreReady = matchingReceipts.stream().allMatch(r ->
                                "ENABLED".equalsIgnoreCase(r.status()) || "SUCCEEDED".equalsIgnoreCase(r.status()));
                        String statuses = matchingReceipts.stream().map(PhysicalCutFact.StepReceipt::status).distinct().toList().toString();

                        if (anyFailed) {
                            checks.put(checkKey, new Check("FAIL", "APPLICABLE", "Task " + task + " FAILED"));
                            hasFailures = true;
                        } else if (isPost) {
                            if (allSucceeded) {
                                String oclVerdict = evaluateTaskCondition(plan, system, state, tasksByName.get(task), taskOwnerByName.get(task), true);
                                if ("FAIL".equals(oclVerdict)) {
                                    checks.put(checkKey, new Check("FAIL", "APPLICABLE", "Task " + task + " postcondition violated"));
                                    hasFailures = true;
                                } else if ("UNKNOWN".equals(oclVerdict)) {
                                    checks.put(checkKey, new Check("UNKNOWN", "APPLICABLE", "Task " + task + " postcondition undefined"));
                                    hasUnknowns = true;
                                } else {
                                    checks.put(checkKey, new Check("PASS", "APPLICABLE", "Task " + task + " post-boundary SUCCEEDED"));
                                }
                            } else {
                                checks.put(checkKey, new Check("UNKNOWN", "APPLICABLE", "Task " + task + " post-boundary not yet succeeded (status: " + statuses + ")"));
                                hasUnknowns = true;
                            }
                        } else {
                            if (allPreReady) {
                                String oclVerdict = evaluateTaskCondition(plan, system, state, tasksByName.get(task), taskOwnerByName.get(task), false);
                                if ("FAIL".equals(oclVerdict)) {
                                    checks.put(checkKey, new Check("FAIL", "APPLICABLE", "Task " + task + " precondition violated"));
                                    hasFailures = true;
                                } else if ("UNKNOWN".equals(oclVerdict)) {
                                    checks.put(checkKey, new Check("UNKNOWN", "APPLICABLE", "Task " + task + " precondition undefined"));
                                    hasUnknowns = true;
                                } else {
                                    checks.put(checkKey, new Check("PASS", "APPLICABLE", "Task " + task + " pre-boundary observed " + statuses));
                                }
                            } else {
                                checks.put(checkKey, new Check("UNKNOWN", "APPLICABLE", "Task " + task + " status: " + statuses));
                                hasUnknowns = true;
                            }
                        }
                    } else {
                        checks.put(checkKey, new Check("UNKNOWN", "APPLICABLE", "Task " + task + " evidence missing in cut"));
                        hasUnknowns = true;
                    }
                }
            } else if ("OPERATION_UNMAPPED_POST".equals(rule.selectorKind())) {
                checks.put(rule.id(), new Check("PASS", "APPLICABLE", "Operation boundary registered"));
            }
        }

        // 3. Evaluate iStar Goal conditions, AND/OR Refinements, Realizations, and Dependencies
        if (plan.goalModel().isPresent()) {
            evaluateIStarModel(plan, system, state, cut, intentionalVerdicts);
        }

        // Global check: native.applicable
        if (hasFailures) {
            checks.put(VerifiedCheckpointProtocol.REQUIRED_CHECK, new Check("FAIL", "APPLICABLE", "One or more invariants or tasks failed"));
        } else if (hasUnknowns) {
            checks.put(VerifiedCheckpointProtocol.REQUIRED_CHECK, new Check("UNKNOWN", "APPLICABLE", "One or more checks pending/unknown"));
        } else {
            checks.put(VerifiedCheckpointProtocol.REQUIRED_CHECK, new Check("PASS", "APPLICABLE", "All native checks satisfied"));
        }

        VerifiedCheckpointProtocol.Action action;
        if (hasFailures) {
            action = VerifiedCheckpointProtocol.Action.HOLD_FAILURE;
        } else if (hasUnknowns) {
            action = VerifiedCheckpointProtocol.Action.HOLD_UNVERIFIED;
        } else {
            action = VerifiedCheckpointProtocol.Action.APPROVE;
        }

        return new VerificationAssessment(
                cut.cutId(),
                !hasFailures && !hasUnknowns,
                hasFailures,
                hasUnknowns,
                checks,
                invResults,
                action,
                intentionalVerdicts
        );
    }

    /**
     * Correlates step receipts for a task by invocation identity (agent, workspace, scheme, invocationId).
     * When multiple receipts arrive on the same correlation stream, a new ENABLED receipt after a terminal
     * SUCCEEDED/FAILED starts a new invocation episode so earlier invocations never contaminate later ones.
     */
    private static List<PhysicalCutFact.StepReceipt> selectActiveInvocationReceipts(
            List<PhysicalCutFact.StepReceipt> allReceipts,
            String taskName) {
        List<PhysicalCutFact.StepReceipt> matching = allReceipts.stream()
                .filter(r -> r.taskName().equals(taskName))
                .toList();
        if (matching.isEmpty()) {
            return List.of();
        }
        Map<String, PhysicalCutFact.StepReceipt> latestPerStream = new LinkedHashMap<>();
        int episodeCounter = 0;
        String lastUntaggedStatus = "";
        for (PhysicalCutFact.StepReceipt r : matching) {
            String baseKey = r.agentName() + "|" + r.workspace() + "|" + r.schemeName() + "|" + r.invocationId();
            if ("|||".equals(baseKey)) {
                if (("SUCCEEDED".equalsIgnoreCase(lastUntaggedStatus) || "FAILED".equalsIgnoreCase(lastUntaggedStatus))
                        && "ENABLED".equalsIgnoreCase(r.status())) {
                    episodeCounter++;
                }
                lastUntaggedStatus = r.status();
                // Keep only the active (most recent) episode on an untagged stream
                latestPerStream.clear();
                latestPerStream.put("untagged#" + episodeCounter, r);
            } else {
                latestPerStream.put(baseKey, r);
            }
        }
        return List.copyOf(latestPerStream.values());
    }

    private static String evaluateTaskCondition(
            CompiledObservationPlan plan,
            MSystem system,
            MSystemState state,
            Task task,
            Actor owner,
            boolean isPost) {
        if (task == null) return "PASS";
        Optional<OclExpression> exprOpt = isPost ? task.postCondition() : task.preCondition();
        if (exprOpt.isEmpty() || exprOpt.get().expression() == null || exprOpt.get().expression().isBlank()) {
            return "PASS";
        }
        return evaluateActorScopedOcl(plan, system, state, owner, exprOpt.get().expression());
    }

    private static void evaluateIStarModel(
            CompiledObservationPlan plan,
            MSystem system,
            MSystemState state,
            PhysicalCutFact cut,
            Map<String, String> verdicts) {
        GoalModel gm = plan.goalModel().get();
        for (Actor actor : gm.getActors()) {
            Map<String, String> localVerdicts = new LinkedHashMap<>();
            // 1. Leaf tasks and leaf goals with OCL conditions
            for (IntentionalElement elem : actor.elements()) {
                if (elem instanceof Task t) {
                    List<PhysicalCutFact.StepReceipt> receipts = selectActiveInvocationReceipts(cut.stepReceipts(), t.id());
                    if (receipts.isEmpty()) {
                        localVerdicts.put(t.id(), "UNKNOWN");
                    } else if (receipts.stream().anyMatch(r -> "FAILED".equalsIgnoreCase(r.status()))) {
                        localVerdicts.put(t.id(), "FAIL");
                    } else if (receipts.stream().allMatch(r -> "SUCCEEDED".equalsIgnoreCase(r.status()))) {
                        localVerdicts.put(t.id(), evaluateTaskCondition(plan, system, state, t, actor, true));
                    } else {
                        localVerdicts.put(t.id(), "UNKNOWN");
                    }
                } else if (elem instanceof Goal g && g.condition().isPresent()) {
                    localVerdicts.put(g.id(), evaluateActorScopedOcl(plan, system, state, actor, g.condition().get().expression()));
                }
            }
            // 2. Propagate refinements iteratively to fixed point
            boolean changed = true;
            int guard = 0;
            while (changed && guard++ < 32) {
                changed = false;
                for (Refinement ref : actor.refinements()) {
                    String parentId = ref.parent().id();
                    List<String> childVerdicts = ref.children().stream()
                            .map(c -> localVerdicts.getOrDefault(c.id(), "UNKNOWN"))
                            .toList();
                    String computed;
                    if (ref instanceof AndRefinement) {
                        if (childVerdicts.stream().anyMatch("FAIL"::equals)) computed = "FAIL";
                        else if (!childVerdicts.isEmpty() && childVerdicts.stream().allMatch("PASS"::equals)) computed = "PASS";
                        else computed = "UNKNOWN";
                    } else {
                        if (childVerdicts.stream().anyMatch("PASS"::equals)) computed = "PASS";
                        else if (!childVerdicts.isEmpty() && childVerdicts.stream().allMatch("FAIL"::equals)) computed = "FAIL";
                        else computed = "UNKNOWN";
                    }
                    // If parent also has an explicit OCL condition, combine via AND
                    if (ref.parent() instanceof Goal pg && pg.condition().isPresent()) {
                        String condVerdict = evaluateActorScopedOcl(plan, system, state, actor, pg.condition().get().expression());
                        if ("FAIL".equals(condVerdict) || "FAIL".equals(computed)) computed = "FAIL";
                        else if ("PASS".equals(condVerdict) && "PASS".equals(computed)) computed = "PASS";
                        else computed = "UNKNOWN";
                    }
                    if (!computed.equals(localVerdicts.put(parentId, computed))) {
                        changed = true;
                    }
                }
            }
            for (var e : localVerdicts.entrySet()) {
                verdicts.put(actor.name() + "." + e.getKey(), e.getValue());
                verdicts.putIfAbsent(e.getKey(), e.getValue());
            }
        }
        // 3. Evaluate Dependency links with ANY/ALL population quantifiers
        Map<String, String> depQuantifiers = new LinkedHashMap<>();
        for (CompactMappingSpecification.DependencyPopulation dp : plan.specification().dependencyPopulations()) {
            depQuantifiers.put(dp.dependum(), dp.quantifier().toUpperCase(Locale.ROOT));
        }
        for (Dependency dep : gm.getDependencies()) {
            String depName = dep.dependum().id();
            String quantifier = depQuantifiers.getOrDefault(depName, "ALL");
            String dependeeKey = dep.dependee().name() + "." + dep.dependeeElement().id();
            String baseVerdict = verdicts.getOrDefault(dependeeKey, verdicts.getOrDefault(dep.dependeeElement().id(), "UNKNOWN"));
            if (dep.dependum() instanceof Goal dg && dg.condition().isPresent()) {
                baseVerdict = evaluateQuantifiedOcl(system, state, findRealizationClass(plan, dep.dependee().name()), quantifier, dg.condition().get().expression());
            }
            verdicts.put("dep:" + depName, baseVerdict);
        }
    }

    private static String findRealizationClass(CompiledObservationPlan plan, String actorName) {
        if (actorName == null) return null;
        for (CompactMappingSpecification.GoalRealization gr : plan.specification().goalRealizations()) {
            if (gr.actor().equals(actorName)) {
                return gr.rootClass();
            }
        }
        return null;
    }

    private static String evaluateActorScopedOcl(
            CompiledObservationPlan plan,
            MSystem system,
            MSystemState state,
            Actor actor,
            String rawOcl) {
        String rootClass = actor != null ? findRealizationClass(plan, actor.name()) : null;
        String quantifier = "ALL";
        if (actor != null) {
            for (CompactMappingSpecification.GoalRealization gr : plan.specification().goalRealizations()) {
                if (gr.actor().equals(actor.name())) {
                    quantifier = gr.population().toUpperCase(Locale.ROOT);
                    break;
                }
            }
        }
        return evaluateQuantifiedOcl(system, state, rootClass, quantifier, rawOcl);
    }

    private static String evaluateQuantifiedOcl(
            MSystem system,
            MSystemState state,
            String rootClass,
            String quantifier,
            String rawOcl) {
        if (rawOcl == null || rawOcl.isBlank()) return "PASS";
        MClass cls = rootClass != null ? system.model().getClass(rootClass) : null;
        if (cls != null && !rawOcl.contains(".allInstances()")) {
            List<MObject> objs = state.allObjects().stream()
                    .filter(o -> o.cls().conformsTo(cls))
                    .toList();
            if (objs.isEmpty()) {
                return "UNKNOWN";
            }
            List<String> objVerdicts = new ArrayList<>();
            for (MObject obj : objs) {
                Value v = evalOclForObject(system, state, cls, obj, rawOcl);
                if (v instanceof BooleanValue bv) {
                    objVerdicts.add(bv.value() ? "PASS" : "FAIL");
                } else {
                    objVerdicts.add("UNKNOWN");
                }
            }
            if ("ANY".equalsIgnoreCase(quantifier)) {
                if (objVerdicts.contains("PASS")) return "PASS";
                if (objVerdicts.stream().allMatch("FAIL"::equals)) return "FAIL";
                return "UNKNOWN";
            } else {
                if (objVerdicts.contains("FAIL")) return "FAIL";
                if (objVerdicts.stream().allMatch("PASS"::equals)) return "PASS";
                return "UNKNOWN";
            }
        }
        // Global expression (e.g., AuctionArtifact.allInstances()->forAll(...))
        Value v = evalGlobalOcl(system, state, rawOcl);
        if (v instanceof BooleanValue bv) {
            return bv.value() ? "PASS" : "FAIL";
        }
        return "UNKNOWN";
    }

    private static Value evalOclForObject(
            MSystem system,
            MSystemState state,
            MClass cls,
            MObject obj,
            String rawOcl) {
        try {
            org.tzi.use.uml.ocl.type.Symtable vars = new org.tzi.use.uml.ocl.type.Symtable();
            vars.add("self", cls, null);
            StringWriter err = new StringWriter();
            Expression expr = OCLCompiler.compileExpression(
                    system.model(), state, rawOcl, "compact-ocl", new PrintWriter(err), vars);
            if (expr == null) {
                return evalGlobalOcl(system, state, rawOcl);
            }
            VarBindings bindings = new VarBindings();
            bindings.push("self", obj.value());
            return new Evaluator().eval(expr, state, bindings);
        } catch (Exception e) {
            return UndefinedValue.instance;
        }
    }

    private static Value evalGlobalOcl(
            MSystem system,
            MSystemState state,
            String rawOcl) {
        try {
            org.tzi.use.uml.ocl.type.Symtable vars = new org.tzi.use.uml.ocl.type.Symtable();
            StringWriter err = new StringWriter();
            Expression expr = OCLCompiler.compileExpression(
                    system.model(), state, rawOcl, "compact-ocl", new PrintWriter(err), vars);
            if (expr == null) {
                return UndefinedValue.instance;
            }
            return new Evaluator().eval(expr, state, new VarBindings());
        } catch (Exception e) {
            return UndefinedValue.instance;
        }
    }

    private NativeVerificationCoordinator() {}
}
