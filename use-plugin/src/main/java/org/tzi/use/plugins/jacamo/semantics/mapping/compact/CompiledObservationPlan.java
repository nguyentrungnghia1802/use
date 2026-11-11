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

    public static final String COMPILER_VERSION = "3.2.0";

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
    public Set<String> unmappedTraceSteps() {
        var mapped=istarBindings.stream().filter(b->b.kind()==IStarBinding.BindingKind.TASK_MAPPING)
                .map(b->b.targetSymbol().name()).collect(java.util.stream.Collectors.toSet());
        var traces=new TreeSet<String>();
        for(var request:capabilityRequests)if(request.provider().equals("jason.plan-boundaries.v1"))
            for(Object plan:(List<?>)request.parameters().get("plans"))
                for(Object raw:(List<?>)org.jacamo.bridge.contract.CanonicalJson.object(plan).get("steps")) {
                    String step=(String)org.jacamo.bridge.contract.CanonicalJson.object(raw).get("task");
                    if(!mapped.contains(step))traces.add(step);
                }
        return Collections.unmodifiableSet(traces);
    }

    /** Acquisition bounds are derived from admitted selectors, never from a case name.
     * An empty selection means none, not an implicit wildcard population. */
    public org.jacamo.bridge.contract.ObservationCaptureScope captureScope() {
        var classes=new TreeSet<String>();var properties=new TreeSet<String>();
        var moise=EnumSet.noneOf(org.jacamo.bridge.contract.RuntimeFactKind.class);
        var refs=new ArrayList<SourceReference>();
        cslBindings.forEach(b->refs.add(b.sourceRef()));istarBindings.forEach(b->refs.add(b.sourceRef()));
        specification.observerViews().forEach(v->refs.add(SourceReference.parse(v.source())));
        for(var ref:refs) {
            if(ref instanceof SourceReference.ArtifactRef a)classes.add(a.className());
            if(ref instanceof SourceReference.ArtifactPropertyRef p){classes.add(p.className());properties.add(p.propertyName());}
            if(ref instanceof SourceReference.ArtifactFieldRef f)classes.add(f.className());
            if(ref instanceof SourceReference.ArtifactIdentityRef i)classes.add(i.className());
            if(ref instanceof SourceReference.MoiseGroupRef||ref instanceof SourceReference.MoiseRoleRef)
                moise.add(org.jacamo.bridge.contract.RuntimeFactKind.GROUP_BOARD);
            if(ref instanceof SourceReference.MoiseSchemeRef||ref instanceof SourceReference.MoiseGoalRef)
                moise.add(org.jacamo.bridge.contract.RuntimeFactKind.SCHEME_BOARD);
        }
        for(var request:capabilityRequests) {
            if(request.provider().equals("cartago.operation-history.v1")&&request.parameters().get("class") instanceof String type)classes.add(type);
            if(request.provider().equals("moise.scheme-lifecycle.v1"))moise.add(org.jacamo.bridge.contract.RuntimeFactKind.SCHEME_BOARD);
        }
        if(moise.contains(org.jacamo.bridge.contract.RuntimeFactKind.GROUP_BOARD))classes.add("ora4mas.nopl.GroupBoard");
        if(moise.contains(org.jacamo.bridge.contract.RuntimeFactKind.SCHEME_BOARD))classes.add("ora4mas.nopl.SchemeBoard");
        boolean npl=capabilityRequests.stream().anyMatch(r->r.provider().startsWith("npl."));
        return new org.jacamo.bridge.contract.ObservationCaptureScope(false,classes,false,properties,moise,npl,planFingerprint);
    }
    public Set<org.jacamo.bridge.contract.RuntimeFactKind> cartagoKinds() {
        return Set.of(org.jacamo.bridge.contract.RuntimeFactKind.WORKSPACE,
                org.jacamo.bridge.contract.RuntimeFactKind.ARTIFACT,org.jacamo.bridge.contract.RuntimeFactKind.PROPERTY,
                org.jacamo.bridge.contract.RuntimeFactKind.AGENT);
    }
    public Map<String,List<String>> producerFields() {
        // Compact selectors currently require player identity, not a full
        // reasoning-cycle/belief dump. Task captures have their own pinned provider.
        return Map.of("jason",List.of("normalizedEventKind","semanticId","name","active","agentDeclarationSemanticId","agentRuntimeSemanticId"));
    }

    public Optional<CompactObserverSchema> observerSchema() {
        if (specification == null || specification.observerViews() == null || specification.observerViews().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CompactObserverSchema(specification.observerViews(), specification.sha256()));
    }

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

