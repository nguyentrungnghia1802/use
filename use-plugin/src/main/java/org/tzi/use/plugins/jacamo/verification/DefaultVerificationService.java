package org.tzi.use.plugins.jacamo.verification;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MPrePostCondition;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.CollectionValue;
import org.tzi.use.uml.ocl.value.ObjectValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/** Correctness-first offline verifier that evaluates every compiled USE constraint independently. */
public final class DefaultVerificationService implements VerificationService {
    @Override
    public VerificationReport runFullVerification(MSystem system, ConstraintRegistry registry, TraceIndex trace) {
        String runId = UUID.randomUUID().toString();
        List<VerificationResult> results = new ArrayList<>();
        StringWriter structureOutput = new StringWriter();
        boolean structure = system.state().checkStructure(new PrintWriter(structureOutput));
        results.add(new VerificationResult("USE_STRUCTURE", structure ? VerificationOutcome.PASS : VerificationOutcome.FAIL,
                null, structureOutput.toString().strip(), "", List.of(), null, List.of()));
        for (MClassInvariant invariant : system.model().classInvariants()) {
            ConstraintDescriptor descriptor = registry.descriptor(invariant);
            if (descriptor == null) continue;
            if (!descriptor.enabled() || !invariant.isActive()) {
                results.add(result(descriptor, VerificationOutcome.SKIPPED, null, "constraint disabled", List.of(), null, List.of()));
                continue;
            }
            try {
                results.add(evaluateInvariant(invariant, descriptor, system.state(), trace));
            } catch (RuntimeException exception) {
                results.add(result(descriptor, VerificationOutcome.ERROR, null, exception.getMessage(), List.of(), null, List.of()));
            }
        }
        return VerificationReport.offline(runId, structure, results, registry.fingerprints());
    }

    @Override
    public VerificationReport runTargetedVerification(MSystem system, ConstraintRegistry registry, TraceIndex trace,
                                                      Set<String> constraintIds, String runId) {
        if (constraintIds == null || constraintIds.isEmpty())
            return VerificationReport.runtime(runId, "RUNTIME_TARGETED", true, List.of(), registry.fingerprints());
        List<VerificationResult> results = new ArrayList<>();
        for (MClassInvariant invariant : system.model().classInvariants()) {
            ConstraintDescriptor descriptor = registry.descriptor(invariant);
            if (descriptor == null || !constraintIds.contains(descriptor.id())) continue;
            if (!descriptor.enabled() || !invariant.isActive()) {
                results.add(result(descriptor, VerificationOutcome.SKIPPED, null, "constraint disabled",
                        List.of(), null, List.of()));
                continue;
            }
            try { results.add(evaluateInvariant(invariant, descriptor, system.state(), trace)); }
            catch (RuntimeException exception) {
                results.add(result(descriptor, VerificationOutcome.ERROR, null, exception.getMessage(),
                        List.of(), null, List.of()));
            }
        }
        return VerificationReport.runtime(runId, "RUNTIME_TARGETED", true, results, registry.fingerprints());
    }

    @Override
    public OperationCheck beginOperation(MSystem system, ConstraintRegistry registry, TraceIndex trace,
                                         OperationRequest request) {
        MObject object = system.state().objectByName(request.objectName());
        if (object == null) throw new IllegalArgumentException("OPERATION_CONTEXT_MISSING: " + request.objectName());
        var operation = object.cls().operation(request.operationName(), true);
        if (operation == null) throw new IllegalArgumentException("OPERATION_MISSING: " + request.operationName());
        if (operation.paramList().size() != request.arguments().size())
            throw new IllegalArgumentException("OPERATION_ARGUMENT_COUNT");
        MSystemState preState = new MSystemState("operation-pre-" + request.correlationId(), system.state());
        List<VerificationResult> results = new ArrayList<>();
        for (MPrePostCondition condition : operation.preConditions())
            results.add(evaluateCondition(condition, registry, trace, preState, preState, request, null,
                    request.runtimeEventIds()));
        return new OperationCheck(request, operation, preState, registry, trace,
                VerificationReport.operation(request.correlationId() + ":pre", results, registry.fingerprints()));
    }

    @Override
    public VerificationReport completeOperation(OperationCheck check, MSystemState postState, Value result,
                                                List<String> exitRuntimeEventIds) {
        List<String> events = new ArrayList<>(check.request().runtimeEventIds());
        events.addAll(exitRuntimeEventIds);
        List<VerificationResult> results = new ArrayList<>();
        for (MPrePostCondition condition : check.operation().postConditions())
            results.add(evaluateCondition(condition, check.registry(), check.trace(), check.preState(), postState,
                    check.request(), result, events));
        return VerificationReport.operation(check.request().correlationId() + ":post", results,
                check.registry().fingerprints());
    }

    private VerificationResult evaluateCondition(MPrePostCondition condition, ConstraintRegistry registry,
                                                 TraceIndex trace, MSystemState preState, MSystemState postState,
                                                 OperationRequest request, Value operationResult, List<String> events) {
        ConstraintDescriptor descriptor = registry.descriptor(condition);
        if (descriptor == null) throw new IllegalArgumentException("CONSTRAINT_NOT_REGISTERED: " + condition);
        if (!descriptor.enabled())
            return result(descriptor, VerificationOutcome.SKIPPED, request.objectName(), "constraint disabled",
                    sourceTrace(trace, request.objectName()), request.correlationId(), events);
        try {
            VarBindings bindings = new VarBindings(postState);
            MObject self = postState.objectByName(request.objectName());
            bindings.push("self", self.value());
            for (int i = 0; i < condition.operation().paramNames().size(); i++)
                bindings.push(condition.operation().paramNames().get(i), request.arguments().get(i));
            if (operationResult != null) bindings.push("result", operationResult);
            Value value = new Evaluator().eval(condition.expression(), preState, postState, bindings, null);
            if (!(value instanceof BooleanValue booleanValue))
                return result(descriptor, VerificationOutcome.ERROR, request.objectName(),
                        "OCL evaluated to undefined/non-Boolean: " + value, sourceTrace(trace, request.objectName()),
                        request.correlationId(), events);
            return result(descriptor, booleanValue.isTrue() ? VerificationOutcome.PASS : VerificationOutcome.FAIL,
                    request.objectName(), booleanValue.isTrue() ? "condition holds" : "condition violated",
                    sourceTrace(trace, request.objectName()), request.correlationId(), events);
        } catch (RuntimeException exception) {
            return result(descriptor, VerificationOutcome.ERROR, request.objectName(), exception.getMessage(),
                    sourceTrace(trace, request.objectName()), request.correlationId(), events);
        }
    }

    private VerificationResult evaluateInvariant(MClassInvariant invariant, ConstraintDescriptor descriptor,
                                                 MSystemState state, TraceIndex trace) {
        boolean sawUndefined = false;
        String undefinedContext = null;
        boolean sawTrue = false;
        for (MObject object : state.objectsOfClassAndSubClasses(invariant.cls())) {
            VarBindings bindings = new VarBindings(state);
            String variable = invariant.hasVar() ? invariant.var() : "self";
            if (variable != null && variable.contains(",")) return evaluateExpandedInvariant(invariant, descriptor, state, trace);
            bindings.push(variable, object.value());
            Value value = new Evaluator().eval(invariant.bodyExpression(), state, bindings, null);
            if (!(value instanceof BooleanValue booleanValue)) {
                sawUndefined = true;
                if (undefinedContext == null) undefinedContext = object.name();
                continue;
            }
            boolean truth = invariant.isNegated() ? booleanValue.isFalse() : booleanValue.isTrue();
            if (truth) {
                sawTrue = true;
                if (invariant.isExistential())
                    return result(descriptor, VerificationOutcome.PASS, null, "constraint holds", List.of(), null, List.of());
            } else if (!invariant.isExistential()) {
                return result(descriptor, VerificationOutcome.FAIL, object.name(), "constraint violated",
                        sourceTrace(trace, object.name()), null, List.of());
            }
        }
        if (invariant.isExistential() && !sawTrue && !sawUndefined)
            return result(descriptor, VerificationOutcome.FAIL, null, "existential constraint has no satisfying context",
                    List.of(), null, List.of());
        if (sawUndefined)
            return result(descriptor, VerificationOutcome.ERROR, undefinedContext, "OCL evaluated to undefined",
                    sourceTrace(trace, undefinedContext), null, List.of());
        return result(descriptor, VerificationOutcome.PASS, null, "constraint holds", List.of(), null, List.of());
    }

    private VerificationResult evaluateExpandedInvariant(MClassInvariant invariant, ConstraintDescriptor descriptor,
                                                         MSystemState state, TraceIndex trace) {
        Value value = new Evaluator().eval(invariant.flaggedExpression(), state);
        if (!(value instanceof BooleanValue booleanValue))
            return result(descriptor, VerificationOutcome.ERROR, null,
                    "OCL evaluated to undefined/non-Boolean: " + value, List.of(), null, List.of());
        if (booleanValue.isTrue())
            return result(descriptor, VerificationOutcome.PASS, null, "constraint holds", List.of(), null, List.of());
        String context = violatingObject(invariant, state);
        return result(descriptor, VerificationOutcome.FAIL, context, "constraint violated",
                sourceTrace(trace, context), null, List.of());
    }

    private String violatingObject(MClassInvariant invariant, MSystemState state) {
        Value value = new Evaluator().eval(invariant.getExpressionForViolatingInstances(), state);
        if (value instanceof CollectionValue collection) {
            for (Value item : collection) if (item instanceof ObjectValue object) return object.value().name();
        }
        return null;
    }

    private List<String> sourceTrace(TraceIndex trace, String objectName) {
        if (objectName == null) return List.of();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        trace.byUseId("object:" + objectName).forEach(record -> ids.add(record.sourceSemanticId()));
        return List.copyOf(ids);
    }

    private VerificationResult result(ConstraintDescriptor descriptor, VerificationOutcome outcome,
                                      String context, String explanation, List<String> traces,
                                      String correlationId, List<String> eventIds) {
        return new VerificationResult(descriptor.id(), outcome, context, explanation, descriptor.oclSource(),
                traces, correlationId, eventIds);
    }
}
