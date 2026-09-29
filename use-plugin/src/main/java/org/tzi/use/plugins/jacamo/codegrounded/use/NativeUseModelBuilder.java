package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.util.List;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.CodeGroundedConstraintPlanner;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.NativeConstraintInstaller;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.NativeConstraintSpec;
import org.tzi.use.plugins.jacamo.codegrounded.model.JacamoSpecificationModel;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRule;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceIndex;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceRecord;
import org.tzi.use.plugins.jacamo.codegrounded.trace.TracePhase;
import org.tzi.use.uml.mm.MAggregationKind;
import org.tzi.use.uml.mm.MModel;

/** Builds the Phase-1B schema directly through {@link UseModelApi}; no .use text participates in authority. */
public final class NativeUseModelBuilder {
    public static final List<String> BODY_TYPES = List.of("none", "action", "internalAction", "achieve", "test",
            "addBel", "addBelNewFocus", "addBelBegin", "addBelEnd", "delBel", "delBelNewFocus", "delAddBel",
            "achieveNF", "constraint");
    public static final List<String> TRIGGER_OPERATORS = List.of("add", "del", "goalState");
    public static final List<String> TRIGGER_TYPES = List.of("belief", "achieve", "test", "signal");
    public static final List<String> ACTION_KINDS = List.of("EXTERNAL", "INTERNAL");

    public Result build(JacamoSpecificationModel source) {
        CodeGroundedTraceCollector trace = new CodeGroundedTraceCollector();
        return build(source, trace);
    }

    public Result build(JacamoSpecificationModel source, CodeGroundedTraceCollector trace) {
        var catalog = new CodeGroundedRuleCatalog();
        UseModelApi api = new UseModelApi(modelName(source.project().name()));
        try {
            api.createEnumeration("TriggerOperator", TRIGGER_OPERATORS);
            api.createEnumeration("TriggerType", TRIGGER_TYPES);
            api.createEnumeration("PlanBodyType", BODY_TYPES);
            api.createEnumeration("ActionKind", ACTION_KINDS);

            for (String name : List.of("AgentProgram", "PlanLibrary", "Plan", "Trigger", "PlanBodyElement",
                    "Action", "Belief", "AgentGoal", "BeliefRule", "A17PlanOrderEntry", "A19BodyOrderEntry"))
                api.createClass(name, false);
            for (String name : List.of("AgentProgram", "PlanLibrary", "Plan", "Trigger", "PlanBodyElement",
                    "Action", "Belief", "AgentGoal", "BeliefRule", "A17PlanOrderEntry", "A19BodyOrderEntry"))
                api.createAttribute(name, "semanticId", "String");
            api.createAttribute("AgentProgram", "declarationId", "String");
            api.createAttribute("AgentProgram", "sourceUri", "String");
            api.createAttribute("AgentProgram", "sourceDigest", "String");
            api.createAttribute("Plan", "ordinal", "Integer");
            api.createAttribute("Plan", "label", "String");
            // "context" is a USE grammar keyword. Keep source semantics exact while using a target-only safe name.
            api.createAttribute("Plan", "jasonContext", "String");
            api.createAttribute("Trigger", "operator", "TriggerOperator");
            api.createAttribute("Trigger", "triggerType", "TriggerType");
            api.createAttribute("Trigger", "literal", "String");
            api.createAttribute("PlanBodyElement", "ordinal", "Integer");
            api.createAttribute("PlanBodyElement", "bodyType", "PlanBodyType");
            api.createAttribute("PlanBodyElement", "term", "String");
            api.createAttribute("Action", "planBodySemanticId", "String");
            api.createAttribute("Action", "term", "String");
            api.createAttribute("Action", "functor", "String");
            api.createAttribute("Action", "arity", "Integer");
            api.createAttribute("Action", "kind", "ActionKind");
            api.createAttribute("Belief", "ordinal", "Integer");
            api.createAttribute("Belief", "literal", "String");
            api.createAttribute("AgentGoal", "ordinal", "Integer");
            api.createAttribute("AgentGoal", "literal", "String");
            api.createAttribute("AgentGoal", "goalKind", "String");
            api.createAttribute("BeliefRule", "ordinal", "Integer");
            api.createAttribute("BeliefRule", "head", "String");
            api.createAttribute("BeliefRule", "body", "String");
            api.createAttribute("A17PlanOrderEntry", "rank", "Integer");
            api.createAttribute("A19BodyOrderEntry", "rank", "Integer");

            association(api, "A16AgentProgramPlanLibrary", "AgentProgram", "agentProgram", "1",
                    MAggregationKind.COMPOSITION, "PlanLibrary", "planLibrary", "1", false, false);
            association(api, "A17PlanLibraryPlan", "PlanLibrary", "planLibrary", "1",
                    MAggregationKind.COMPOSITION, "Plan", "plans", "*", false, true);
            association(api, "A18PlanTrigger", "Plan", "plan", "1",
                    MAggregationKind.COMPOSITION, "Trigger", "trigger", "1", false, false);
            association(api, "A19PlanBodyElement", "Plan", "plan", "1",
                    MAggregationKind.COMPOSITION, "PlanBodyElement", "bodyElements", "*", false, true);
            association(api, "A20PlanBodyNext", "PlanBodyElement", "current", "0..1",
                    MAggregationKind.NONE, "PlanBodyElement", "next", "0..1", false, false);
            association(api, "A21ProgramBelief", "AgentProgram", "program", "1",
                    MAggregationKind.COMPOSITION, "Belief", "initialBeliefs", "*", false, true);
            association(api, "A22ProgramGoal", "AgentProgram", "program", "1",
                    MAggregationKind.COMPOSITION, "AgentGoal", "initialGoals", "*", false, true);
            association(api, "A17OrderOwner", "PlanLibrary", "owner", "1", MAggregationKind.COMPOSITION,
                    "A17PlanOrderEntry", "a17Entries", "*", false, false);
            association(api, "A17OrderMember", "Plan", "member", "1", MAggregationKind.NONE,
                    "A17PlanOrderEntry", "a17Memberships", "0..1", false, false);
            association(api, "A19OrderOwner", "Plan", "owner", "1", MAggregationKind.COMPOSITION,
                    "A19BodyOrderEntry", "a19Entries", "*", false, false);
            association(api, "A19OrderMember", "PlanBodyElement", "member", "1", MAggregationKind.NONE,
                    "A19BodyOrderEntry", "a19Memberships", "0..1", false, false);

            List<NativeConstraintSpec> constraints = new CodeGroundedConstraintPlanner().plan(catalog);
            new NativeConstraintInstaller().install(api, constraints);
            modelTraces(trace, catalog, source);
            MModel model = api.getModel();
            return new Result(model, trace.index(), constraints, NativeUseStructure.sha256(model));
        } catch (UseApiException error) {
            throw new IllegalStateException("NATIVE_USE_MODEL_BUILD_FAILED: " + error.getMessage(), error);
        }
    }

    private static void modelTraces(CodeGroundedTraceCollector trace, CodeGroundedRuleCatalog catalog,
                                    JacamoSpecificationModel source) {
        trace.add(catalog.require("J01"), TracePhase.MODEL_DECLARATION, source.project().metadata(),
                "MModel", "model:" + modelName(source.project().name()), List.of());
        for (String id : List.of("A01", "A02", "A03", "A04", "A05", "A06", "A07", "A08", "A09", "A10",
                "A11", "A16", "A17", "A18", "A19", "A20", "A21", "A22")) {
            CodeGroundedRule rule = catalog.require(id);
            trace.add(new CodeGroundedTraceRecord(id, TracePhase.MODEL_DECLARATION, rule.sourceKindFqcn(),
                    rule.sourceKindFqcn(), "schema:" + id, targetKind(rule.targetUseKind()),
                    declarationIdentity(rule.targetUseKind()), rule.sourceAuthority(), rule.fidelity(),
                    rule.capabilityStatus(), List.of()));
        }
    }

    private static String targetKind(String target) {
        int space = target.indexOf(' ');
        return space < 0 ? target : target.substring(0, space);
    }

    private static String declarationIdentity(String target) {
        return target.replace(' ', ':');
    }

    private static void association(UseModelApi api, String name, String firstClass, String firstRole,
                                    String firstMultiplicity, int firstAggregation, String secondClass,
                                    String secondRole, String secondMultiplicity, boolean firstOrdered,
                                    boolean secondOrdered) throws UseApiException {
        api.createAssociation(name, new String[] {firstClass, secondClass}, new String[] {firstRole, secondRole},
                new String[] {firstMultiplicity, secondMultiplicity},
                new int[] {firstAggregation, MAggregationKind.NONE}, new boolean[] {firstOrdered, secondOrdered},
                new String[][][] {});
    }

    public static String modelName(String source) {
        String safe = source == null ? "JaCaMo" : source.replaceAll("[^A-Za-z0-9_]", "_");
        if (safe.isBlank()) safe = "JaCaMo";
        if (Character.isDigit(safe.charAt(0))) safe = "JaCaMo_" + safe;
        return safe + "_CodeGrounded";
    }

    public record Result(MModel model, CodeGroundedTraceIndex trace, List<NativeConstraintSpec> constraints,
                         String structuralHash) {
        public Result { constraints = List.copyOf(constraints); }
    }
}
