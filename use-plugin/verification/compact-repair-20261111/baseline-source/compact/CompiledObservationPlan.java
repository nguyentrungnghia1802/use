package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.jacamo.bridge.contract.ObservationCapabilityRequest;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;

import java.util.*;

/**
 * Immutable compiled observation plan produced by ObservationPlanCompiler.
 * Contains all synthesized capability requests, checkpoint rules, and typed bindings
 * directly from compact mapping specifications and native model catalogues.
 */
public record CompiledObservationPlan(
        String planId,
        String planFingerprint,
        CompactMappingSpecification specification,
        List<CslBinding> cslBindings,
        List<IStarBinding> istarBindings,
        List<ActorRuntimeBinding> actorRuntimes,
        List<ObservationCapabilityRequest> capabilityRequests,
        List<VerifiedCheckpointProtocol.Rule> checkpointRules,
        List<BindingExplanation> explanations,
        Optional<CslModel> cslModel,
        Optional<GoalModel> goalModel,
        Optional<org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingInputs> mappingInputs) {

    public static final String COMPILER_VERSION = "3.0.0";

    public CompiledObservationPlan(
            String planId,
            String planFingerprint,
            CompactMappingSpecification specification,
            List<CslBinding> cslBindings,
            List<IStarBinding> istarBindings,
            List<ActorRuntimeBinding> actorRuntimes,
            List<ObservationCapabilityRequest> capabilityRequests,
            List<VerifiedCheckpointProtocol.Rule> checkpointRules,
            List<BindingExplanation> explanations) {
        this(planId, planFingerprint, specification, cslBindings, istarBindings, actorRuntimes,
                capabilityRequests, checkpointRules, explanations, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public CompiledObservationPlan(
            String planId,
            String planFingerprint,
            CompactMappingSpecification specification,
            List<CslBinding> cslBindings,
            List<IStarBinding> istarBindings,
            List<ActorRuntimeBinding> actorRuntimes,
            List<ObservationCapabilityRequest> capabilityRequests,
            List<VerifiedCheckpointProtocol.Rule> checkpointRules,
            List<BindingExplanation> explanations,
            Optional<CslModel> cslModel,
            Optional<GoalModel> goalModel) {
        this(planId, planFingerprint, specification, cslBindings, istarBindings, actorRuntimes,
                capabilityRequests, checkpointRules, explanations, cslModel, goalModel, Optional.empty());
    }

    public CompiledObservationPlan {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(planFingerprint, "planFingerprint");
        Objects.requireNonNull(specification, "specification");
        cslBindings = List.copyOf(cslBindings);
        istarBindings = List.copyOf(istarBindings);
        actorRuntimes = List.copyOf(actorRuntimes);
        capabilityRequests = List.copyOf(capabilityRequests);
        checkpointRules = List.copyOf(checkpointRules);
        explanations = List.copyOf(explanations);
        cslModel = cslModel == null ? Optional.empty() : cslModel;
        goalModel = goalModel == null ? Optional.empty() : goalModel;
        mappingInputs = mappingInputs == null ? Optional.empty() : mappingInputs;
    }

    public CompiledObservationPlan withInputs(org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingInputs inputs) {
        return new CompiledObservationPlan(planId, planFingerprint, specification, cslBindings, istarBindings,
                actorRuntimes, capabilityRequests, checkpointRules, explanations, cslModel, goalModel, Optional.ofNullable(inputs));
    }

    public String fingerprint() { return planFingerprint; }
    public List<ObservationCapabilityRequest> capabilityDemands() { return capabilityRequests; }
    public List<ActorRuntimeBinding> actorBindings() { return actorRuntimes; }
    public List<IStarBinding> iStarBindings() { return istarBindings; }

    public Map<String, Object> evidenceSummary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("compilerVersion", COMPILER_VERSION);
        map.put("planId", planId);
        map.put("planFingerprint", planFingerprint);
        map.put("specificationSha256", specification.sha256());
        map.put("cslBindingsCount", cslBindings.size());
        map.put("istarBindingsCount", istarBindings.size());
        map.put("actorRuntimesCount", actorRuntimes.size());
        map.put("capabilityRequestsCount", capabilityRequests.size());
        map.put("checkpointRulesCount", checkpointRules.size());
        map.put("explanations", explanations.stream().map(BindingExplanation::toString).toList());
        return Collections.unmodifiableMap(map);
    }
}

