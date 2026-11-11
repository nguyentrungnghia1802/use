package org.tzi.use.plugins.jacamo.semantics.mapping.compact.runtime;

import org.tzi.use.plugins.jacamo.semantics.mapping.compact.*;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.NativeTaskBindings;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.*;
import org.tzi.use.uml.ocl.value.*;

/** Typed input boundary for compact mapping v3 Task PRE/POST conditions.
 * Compiles slot conditions directly from authored iStar models and compact correspondences.
 * Retains exact PRE/POST pairing, late-self, and operation receipts in the native journal. */
public final class CompactTaskBindings implements NativeTaskBindings {
    public record Slots(String task, NativeOclBackend.Compiled pre, NativeOclBackend.Compiled post,
                        String selfClass, String entryPolicy, String failurePolicy,
                        String stepSchemeColumn, String stepArtifactColumn,
                        Map<String, String> stepInputMatches, List<String> operations) {
        public Slots {
            stepInputMatches = Map.copyOf(stepInputMatches);
            operations = List.copyOf(operations);
        }
    }

    private final SemanticProgram program;
    private final MModel model;
    private final Map<String, Slots> slots;
    private final ConstraintCoverageResolver coverage = new ConstraintCoverageResolver();
    private volatile boolean closed;

    public CompactTaskBindings(SemanticProgram program, MModel model, NativeOclBackend backend) {
        this.program = Objects.requireNonNull(program);
        this.model = Objects.requireNonNull(model);
        if (!program.isCompact() || !backend.program().equals(program.hash()))
            throw new IllegalArgumentException("COMPACT_TASK_PROGRAM_OWNER_MISMATCH");

        var contracts = CompactNativePreparation.intentional(program, model).contracts();
        var result = new TreeMap<String, Slots>();

        for (var binding : program.compactPlan().istarBindings()) {
            if (binding.kind() != IStarBinding.BindingKind.TASK_MAPPING) continue;
            String task = binding.targetSymbol().name();
            var pre = contracts.stream().filter(c -> c.elementId().equals(task) && c.specification().kind() == OclContractSpec.Kind.TASK_PRE).findFirst().orElse(null);
            var post = contracts.stream().filter(c -> c.elementId().equals(task) && c.specification().kind() == OclContractSpec.Kind.TASK_POST).findFirst().orElse(null);
            if (pre == null || post == null) throw new IllegalArgumentException("COMPACT_TASK_AUTHORED_PRE_POST_REQUIRED:"+task);

            var corr = program.compactPlan().specification().istarMappings().stream()
                    .filter(m -> m.source().equals(binding.sourceRef().raw()) && m.target().equals(binding.targetRef().raw()))
                    .findFirst().orElse(null);
            var attrs = corr != null ? corr.attributes() : Map.<String, String>of();

            String allowMissing = attrs.getOrDefault("allowMissingArtifactAtEntry", "false");
            String entryPolicy = "true".equalsIgnoreCase(allowMissing) ? "ALLOW_UNBOUND_SELF_FOR_PURE_CALLBACK" : "EXACT_SELF";
            String failurePolicy = "EVALUATE_PURE_CALLBACK_POST";
            String schemeVar = attrs.getOrDefault("schemeVariable", "");
            String artifactVar = attrs.getOrDefault("artifactVariable", "");
            String stepSchemeCol = schemeVar.isBlank() ? "" : "scheme";
            String stepArtifactCol = artifactVar.isBlank() ? "" : "artifact";

            var inputMatches = new TreeMap<String, String>();
            for (String input : words(attrs.getOrDefault("inputs", ""))) {
                var parts = input.split(":", -1);
                if (parts.length >= 2) inputMatches.put(parts[0], parts[1]);
            }

            var operations = new ArrayList<String>();
            for (String op : words(attrs.getOrDefault("actionReceipts", ""))) {
                operations.add(op);
            }

            var selfCls = pre.specification().self() != null ? pre.specification().self().name() : "";
            var slot=new Slots(task,
                    NativeExpressionInputs.compile(backend, model, pre.specification()),
                    NativeExpressionInputs.compile(backend, model, post.specification()),
                    selfCls, entryPolicy, failurePolicy, stepSchemeCol, stepArtifactCol, inputMatches, operations);
            if(result.putIfAbsent(task,slot)!=null)throw new IllegalArgumentException("COMPACT_TASK_SOURCE_MAPPING_NOT_UNIQUE:"+task);
        }
        slots = new java.util.concurrent.ConcurrentSkipListMap<>(result);
    }

    public Map<String, Slots> slots() {
        if (closed) throw new IllegalStateException("COMPACT_TASK_BINDINGS_CLOSED");
        return Map.copyOf(slots);
    }

    @Override public Set<String> tasks() { return slots().keySet(); }

    @Override public int expressionCount() { return slots.size() * 2; }

    @Override public boolean traceOnly(String sourceStep) {
        return program.compactPlan().unmappedTraceSteps().contains(sourceStep);
    }

    @Override public boolean permitsLateSelf(String task, NativeInvocationEvaluation.Condition entry) {
        var slot = slots.get(task);
        return slot != null && slot.entryPolicy().equals("ALLOW_UNBOUND_SELF_FOR_PURE_CALLBACK")
                && entry.unavailable().isBlank() && entry.expression() == slot.pre().expression()
                && (entry.variables().get("self").isUndefined() || !slot.stepSchemeColumn().isBlank())
                && pureCallback(slot.pre());
    }

    @Override public boolean references(String task, OccurrenceProjection.Scope scope, JasonStepCutDecoder.Step step, NativeRuntimeMutationEngine engine, boolean pre) {
        var slot = slots.get(task);
        if (slot == null) return false;
        if(!(scope.bindings().get("goalInvocations") instanceof List<?> invocations)||!invocations.contains(step.goalInvocation()))return false;
        if (!slot.stepSchemeColumn().isBlank() && !Objects.equals(scope.bindings().get(slot.stepSchemeColumn()), step.scheme())) {
            if (!((pre || step.phase().equals("FAILED")) && slot.entryPolicy().equals("ALLOW_UNBOUND_SELF_FOR_PURE_CALLBACK") && step.scheme().isBlank() && pureCallback(slot.pre())))
                return false;
        }
        if (!slot.stepArtifactColumn().isBlank()) {
            Object raw = scope.bindings().get(slot.stepArtifactColumn());
            var expected = raw instanceof String identity ? engine.objectForSemanticId(identity) : null;
            var actual = engine.objectForSemanticId(step.artifact());
            if (expected == null || actual != expected) {
                return (pre || step.phase().equals("FAILED")) && slot.entryPolicy().equals("ALLOW_UNBOUND_SELF_FOR_PURE_CALLBACK") && step.artifact().isBlank() && pureCallback(slot.pre());
            }
        }
        for (var entry : slot.stepInputMatches().entrySet()) {
            Object inputVal = stepInputValue(step, entry.getKey());
            if (!Objects.equals(scope.bindings().get("input:"+entry.getKey()), inputVal)) return false;
        }
        return true;
    }

    @Override public Map<String, Object> referenceIdentity(OccurrenceProjection.Scope scope, OccurrenceProjection.Member member, String task) {
        var slot = slots().get(task);
        var refs = new TreeMap<String, Object>();
        if (slot != null) {
            if (!slot.stepSchemeColumn().isBlank()) refs.put("stepSchemeColumn", scope.bindings().getOrDefault(slot.stepSchemeColumn(), ""));
            if (!slot.stepArtifactColumn().isBlank()) refs.put("stepArtifactColumn", scope.bindings().getOrDefault(slot.stepArtifactColumn(), ""));
            refs.put("stepInputMatches", slot.stepInputMatches());
        }
        refs.put("self", member.selves().getOrDefault(task, ""));
        var resources=new TreeMap<String,String>();
        for(var actor:program.goalModel().getActors())for(var needed:actor.neededBys())
            if(needed.task().equals(task)&&scope.resources().containsKey(needed.resource()))resources.put(needed.resource(),scope.resources().get(needed.resource()));
        refs.put("resources", resources);
        return Collections.unmodifiableMap(refs);
    }

    @Override public String intervalFloor(NativeRuntimeMutationEngine engine, OccurrenceProjection.Member member) {
        var role = engine.objectForSemanticId(member.role());
        var owner=role==null?engine.objectForSemanticId(member.player()):role;
        if(owner!=null&&!engine.metadata(owner,"compact:observer-floor").isBlank())return engine.metadata(owner,"compact:observer-floor");
        if (role == null && !member.player().isBlank()) {
            var playerObj = engine.objectForSemanticId(member.player());
            if (playerObj != null) return engine.metadata(playerObj, "xml:m0:observer-floor");
        }
        return role == null ? "" : engine.metadata(role, "xml:m0:observer-floor");
    }

    @Override public boolean currentInterval(NativeRuntimeMutationEngine engine, String player, String roleId, String interval) {
        if (roleId.isBlank()) return true;
        var role = engine.objectForSemanticId(roleId);
        return role != null && (interval.equals(engine.metadata(role, "compact:lifetime"))||interval.equals(engine.metadata(role, "xml:m0:lifetime"))) && player.equals(engine.metadata(role, "rolePlayer"));
    }

    @Override public NativeInvocationEvaluation.Condition prepare(String task, boolean pre, NativeRuntimeMutationEngine engine,
            OccurrenceProjection.Scope scope, OccurrenceProjection.Member member,
            TaskDemandProjection.Demand demand, JasonStepCutDecoder.Step step, UseStateReadView view,
            Map<String, Completeness> sources, boolean lost) {
        if (closed) throw new IllegalStateException("COMPACT_TASK_BINDINGS_CLOSED");
        program.assertUnchanged();
        var slot = slots.get(task);
        if (slot == null) throw new IllegalArgumentException("UNSUPPORTED:COMPACT_TASK_OBSERVATION_BINDING_REQUIRED:" + task);

        var compiled = pre ? slot.pre() : slot.post();
        if (engine.system().model() != model || view.after().system() != engine.system())
            throw new IllegalArgumentException("COMPACT_TASK_NATIVE_OWNER_MISMATCH");

        var variables = new TreeMap<String, Value>();
        var witness = new TreeMap<String, String>();
        witness.put("targetTaskId", task);
        witness.put("scope", scope.id());
        witness.put("player", member.player());
        witness.put("role", member.role());
        witness.put("roleInterval", member.interval());
        witness.put("mappingPlanHash", program.hash());
        if(demand!=null){witness.putAll(demand.witness());witness.put("demandEpisode",demand.episode());}

        var self = engine.objectForSemanticId(member.selves().getOrDefault(task, ""));
        variables.put("self", self == null ? UndefinedValue.instance : self.value());
        String reason = "";

        if (lost) reason = "COVERAGE_LOST";
        else if (step == null) reason = "EXACT_STEP_OBSERVATION_UNAVAILABLE";
        else if (!scope.complete() || !scope.members().contains(member)) reason = "EXACT_TASK_CAST_UNAVAILABLE";
        else if (engine.objectForSemanticId(step.agent()) == null || engine.objectForSemanticId(step.agent()) != engine.objectForSemanticId(member.player())) reason = "EXACT_STEP_PLAYER_MISMATCH";
        else if (!step.task().equals(task)) reason = "EXACT_TASK_SOURCE_SITE_MISMATCH";
        else if (demand == null || !demand.target().equals(task) || !demand.scope().equals(scope.id())
                || demand.quality() != Completeness.COMPLETE || !Boolean.TRUE.equals(demand.required())) reason = "EXACT_TASK_DEMAND_UNAVAILABLE";

        if (step != null) {
            witness.put("stepInvocation", step.invocation());
            witness.put("goalInvocation", step.goalInvocation());
            witness.put("sourceActionCorrelation", step.operation());
            witness.put("phase", step.phase());
            witness.put("rawStepQuality", step.quality().name());

            if (reason.isBlank() && (pre && !step.phase().equals("ENABLED") || !pre && step.phase().equals("ENABLED")))
                reason = "EXACT_STEP_PHASE_UNAVAILABLE";

            boolean pure = pureCallback(compiled);
            if (reason.isBlank() && !pre && step.phase().equals("FAILED") && (!slot.failurePolicy().equals("EVALUATE_PURE_CALLBACK_POST") || !pure))
                reason = "SOURCE_FAILURE_WORLD_POST_UNAVAILABLE";

            if (reason.isBlank() && step.quality() != Completeness.COMPLETE && !(pure && step.quality() == Completeness.PARTIAL && callbackAuthority(step, pre)))
                reason = "STEP_SOURCE_" + step.quality();

            if (reason.isBlank() && self == null && !pure)
                reason = "EXACT_NATIVE_TASK_SELF_UNAVAILABLE";

            if (reason.isBlank() && !pre && step.phase().equals("SUCCEEDED") && !operationReceipts(step, slot.operations()))
                reason = "EXACT_SUCCESSFUL_SOURCE_ACTION_RECEIPT_UNAVAILABLE";

            // Bind step tuple variable
            if (compiled.specification().bindings().containsKey("step")) {
                var tupleType = (TupleType) compiled.specification().bindings().get("step");
                Boolean enabled = Boolean.valueOf(step.enabled());
                Boolean succeeded = step.succeeded();
                Boolean completed = step.phase().equals("ENABLED") ? null : (step.phase().equals("SUCCEEDED") ? Boolean.TRUE : (step.phase().equals("FAILED") ? Boolean.FALSE : null));
                var parts = List.of(
                        new TupleValue.Part(1, "enabled", enabled == null ? UndefinedValue.instance : BooleanValue.get(enabled)),
                        new TupleValue.Part(2, "succeeded", succeeded == null ? UndefinedValue.instance : BooleanValue.get(succeeded)),
                        new TupleValue.Part(3, "completed", completed == null ? UndefinedValue.instance : BooleanValue.get(completed))
                );
                variables.put("step", new TupleValue(tupleType, parts));
            }

            // Bind parameter inputs
            for (var entry : compiled.specification().bindings().entrySet()) {
                String varName = entry.getKey();
                if (varName.equals("step") || varName.equals("self")) continue;
                Type varType = entry.getValue();
                Object rawVal = null;
                var inputs = CanonicalJson.object(step.bindings().getOrDefault("inputs", Map.of()));
                Object proof = inputs.get(varName);
                if (proof instanceof Map<?,?> pm) rawVal = CanonicalJson.object(pm).get("value");
                else if (inputs.containsKey(varName)) rawVal = inputs.get(varName);

                Value boundVal = scalar(varType, rawVal, engine);
                if (boundVal.isUndefined() && reason.isBlank())
                    reason = "OCL_BINDING_UNAVAILABLE_OR_TYPE_MISMATCH:" + varName;
                variables.put(varName, boundVal);
            }
        }

        if (reason.isBlank() && !pre && view.before() == null) reason = "POST_MATCHING_PRE_UNAVAILABLE";
        if (reason.isBlank() && !pre) reason = NativePreStateCoverage.unavailable(compiled.expression(), view.before(), view.after(), variables);
        if (reason.isBlank() && self != null) reason = coverage.unavailableSelf(compiled.expression(), engine, self, sources, lost);

        if (pre) {
            String beforeQuality = "";
            for (var read : NativePreStateCoverage.attributeReads(slot.post().expression())) {
                if (self == null) { beforeQuality = "ATTRIBUTE_RECEIVER_UNAVAILABLE:" + read.attr().qualifiedName(); break; }
                beforeQuality = coverage.unavailableSelf(read, engine, self, sources, lost);
                if (!beforeQuality.isBlank()) break;
            }
            witness.put("postPreCoverage", beforeQuality);
        }

        witness.put("taskPredicateId", compiled.specification().id());
        if (self != null) witness.putAll(coverage.witness(compiled.expression(), engine, self, sources, reason));
        String invocation = step == null ? "" : step.invocation();
        String id = compiled.specification().id() + ":" + SemanticJson.hash(List.of(scope.id(), member.instance(), invocation));

        return new NativeInvocationEvaluation.Condition(id, pre ? "TASK_PRE" : "TASK_POST",
                compiled.specification().self().name(), self == null ? "" : self.name(),
                member.selves().getOrDefault(task, ""), compiled.specification().body(),
                compiled.expression(), view.before() == null ? view.after() : view.before(),
                view.after(), variables, reason,
                ConstraintCoverageResolver.unavailableVerdict(reason), invocation, witness);
    }

    private static boolean pureCallback(NativeOclBackend.Compiled compiled) {
        if (compiled == null || compiled.expression() == null) return false;
        try {
            NativeObserverExpression.requirePureValue(compiled.expression());
            return true;
        } catch (IllegalArgumentException worldRead) {
            return false;
        }
    }

    private static boolean callbackAuthority(JasonStepCutDecoder.Step step, boolean pre) {
        var quality = CanonicalJson.object(step.bindings().getOrDefault("stepValueQuality", Map.of()));
        return "JASON_STEP_VALUES/2.0".equals(quality.get("version")) && "COMPLETE_CALLBACK".equals(quality.get(pre ? "enabled" : "succeeded"));
    }

    private static boolean operationReceipts(JasonStepCutDecoder.Step step, List<String> required) {
        if (required.isEmpty()) return true;
        if (step.operation().isBlank() || !(step.bindings().get("actions") instanceof List<?> values) || values.isEmpty()) return false;
        var rows = values.stream().map(CanonicalJson::object).toList();
        for (String op : required) {
            if (rows.stream().noneMatch(r -> "JASON_ACTIONEXEC_IDENTITY".equals(r.get("authority")) && op.equals(r.get("operationName")) && "SUCCEEDED".equals(r.get("result"))))
                return false;
        }
        return true;
    }

    private static Object stepInputValue(JasonStepCutDecoder.Step step, String name) {
        var inputs = CanonicalJson.object(step.bindings().getOrDefault("inputs", Map.of()));
        Object proof = inputs.get(name);
        return proof instanceof Map<?,?> ? CanonicalJson.object(proof).get("value") : proof;
    }

    private static List<String> words(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.trim().split("\\s+"));
    }

    private static Value scalar(Type type, Object raw, NativeRuntimeMutationEngine engine) {
        if (raw == null) return UndefinedValue.instance;
        if (type.isTypeOfString() && raw instanceof String text) return new StringValue(text);
        if (type.isTypeOfBoolean() && raw instanceof Boolean bool) return BooleanValue.get(bool);
        if (type.isTypeOfInteger() && raw instanceof Number number) {
            try { return IntegerValue.valueOf(new java.math.BigDecimal(number.toString()).intValueExact()); }
            catch (ArithmeticException | NumberFormatException ex) { return UndefinedValue.instance; }
        }
        if (type.isTypeOfReal() && raw instanceof Number number) {
            double value=number.doubleValue();return Double.isFinite(value)?new RealValue(value):UndefinedValue.instance;
        }
        if (type instanceof MClass target && raw instanceof String id) {
            var object = engine.objectForSemanticId(id);
            if (object != null && object.cls().isSubClassifierOf(target)) return object.value();
        }
        return UndefinedValue.instance;
    }

    @Override public void close() {
        closed = true;
        slots.clear();
    }
}
