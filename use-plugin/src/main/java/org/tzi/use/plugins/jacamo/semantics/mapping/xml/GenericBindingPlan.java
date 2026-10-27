package org.tzi.use.plugins.jacamo.semantics.mapping.xml;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.semantic.engine.api.*;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;

/** Frozen static preparation, not permission to project runtime observations. */
public final class GenericBindingPlan {
    public static final String PROFILE_ID="JACAMO_DECLARATIVE_XML",VERSION="2.0";
    private final MappingInputs inputs;
    private final String sourceHash,cslHash,goalHash,cslSource,iStarSource,hash;
    private final SemanticBindingIndex bindings;
    private final GoalRuntimeBindingPlan goalBindings;
    private final Map<String,Object> evidence;
    private final List<org.jacamo.bridge.contract.ObservationCapabilityRequest> requirements;
    private final Map<String,org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander.Quantifier> dependencyPopulations;

    GenericBindingPlan(MappingInputs inputs,NormalizedSourceModel source,org.jacamo.bridge.contract.ModelSnapshot official,CslRuleExecutor.Result csl,
            IStarRuleExecutor.Result goals,SemanticBindingIndex bindings,GoalRuntimeBindingPlan goalBindings) {
        this.inputs=inputs;requirements=CapabilityRequirements.compile(inputs,official);sourceHash=source.sourceHash();cslHash=csl.fingerprint();goalHash=OwnedGoalModel.fingerprint(goals.model());
        var observedSteps=new HashSet<String>();
        for(var request:requirements)if(request.provider().equals("jason.plan-boundaries.v1"))for(Object raw:(List<?>)request.parameters().get("plans"))
            for(Object step:(List<?>)org.jacamo.bridge.contract.CanonicalJson.object(raw).get("steps"))observedSteps.add((String)org.jacamo.bridge.contract.CanonicalJson.object(step).get("task"));
        var omittedSteps=new HashSet<String>();
        for(var omission:inputs.specification().root().child("omittedConcepts").children("omit"))if(!omission.optional("sourceStep").isEmpty()) {
            String step=omission.attr("sourceStep");
            if(!omission.optional("handledBy").equals("TRACE_ONLY")||!observedSteps.contains(step)||!omittedSteps.add(step)||goals.model().findElement(step).isPresent())throw omission.location().error("TRACE_ONLY_SOURCE_STEP_MUST_BE_DECLARED_AND_NOT_AN_INTENTIONAL_ELEMENT");
        }
        cslSource=csl.generatedSource();iStarSource=goals.generatedSource();this.bindings=bindings;this.goalBindings=goalBindings;
        dependencyPopulations=org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander.policies(inputs.specification(),goals.model());
        var values=new TreeMap<String,Object>();
        values.put("format","JACAMO_MAPPING_XML/2.0");values.put("sourceHash",sourceHash);
        values.put("specificationHash",inputs.xml().sha256());values.put("oclHash",inputs.ocl().sha256());
        if(inputs.authoredModels())values.put("modelInputMode","AUTHORED_CSL_ISTAR/1.0");
        values.put("sourceAssets",inputs.sources().values().stream().map(a->Map.of("id",a.id(),"sha256",a.sha256())).toList());
        if(!inputs.projectDerivation().isEmpty())values.put("jcmDerivation",inputs.projectDerivation());
        values.put("cslHash",cslHash);values.put("goalHash",goalHash);values.put("engineProfile",goals.profile().name());
        values.put("bindingsHash",bindings.hash());values.put("goalBindings",SemanticJson.tree(goalBindings));
        values.put("dependencyPopulations",SemanticJson.tree(dependencyPopulations));
        // Located traces remain evidence, never mutable model/state or semantic aliases.
        values.put("cslTraces",SemanticJson.tree(csl.traces()));values.put("goalTraces",SemanticJson.tree(goals.traces()));
        values.put("omissions",SemanticJson.tree(csl.omissions()));
        values.put("capabilityRequirements",requirements.stream().map(org.jacamo.bridge.contract.ObservationCapabilityRequest::evidence).toList());
        values.put("producerFields",org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources.producerFields(inputs.specification()));
        values.put("producerCartagoKinds",org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources.producerCartagoKinds(inputs.specification()).stream().map(Enum::name).sorted().toList());
        evidence=SemanticJson.object(values);hash=SemanticJson.hash(evidence);
    }
    public MappingInputs inputs(){return inputs;}
    public SemanticBindingIndex bindings(){return bindings;}
    public GoalRuntimeBindingPlan goalBindings(){return goalBindings;}
    public String hash(){return hash;}
    public List<org.jacamo.bridge.contract.ObservationCapabilityRequest> requirements(){return requirements;}
    public Map<String,org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander.Quantifier> dependencyPopulations(){return dependencyPopulations;}
    public Map<String,Object> evidence(){return evidence;}
    public Map<String,String> ruleOptions(){return Map.of("inputFormat","JACAMO_MAPPING_XML/2.0","specificationHash",inputs.xml().sha256(),"genericPlanHash",hash);}
    public void requireComplement(org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement complement) {
        var required=new ArrayList<>(inputs.sources().values());required.add(inputs.xml());required.add(inputs.ocl());
        required.add(org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement.Asset.of("generic-plan","GENERIC_MAPPING_PLAN_JSON",org.jacamo.bridge.contract.CanonicalJson.encode(evidence)));
        for(var asset:required)if(complement.assets().stream().noneMatch(asset::equals))throw new IllegalArgumentException("GENERIC_PROGRAM_FROZEN_ASSET_MISMATCH:"+asset.id());
    }
    public void requireOwnership(JaCaMoMappingProfile profile,MappingRuleSet rules,NormalizedSourceModel source,CslModel csl,
            GoalModel goals,SemanticBindingIndex actualBindings,GoalRuntimeBindingPlan actualGoals) {
        if(!profile.id().equals(PROFILE_ID)||!profile.version().equals(VERSION)
                ||!profile.engine().name().equals(inputs.specification().root().attr("engineProfile"))
                ||!rules.profileId().equals(PROFILE_ID)||!rules.profileVersion().equals(VERSION)
                ||!rules.id().equals(inputs.specification().root().attr("id"))||!sourceHash.equals(source.sourceHash())
                ||!sourceHash.equals(rules.expectedSourceHash())||!cslHash.equals(CslModelFingerprint.of(csl))
                ||!goalHash.equals(OwnedGoalModel.fingerprint(goals))||!rules.cslSource().equals(cslSource)||!rules.iStarSource().equals(iStarSource)
                ||!actualBindings.hash().equals(bindings.hash())||!rules.bindings().equals(bindings.bindings())
                ||!SemanticJson.hash(SemanticJson.tree(actualGoals)).equals(SemanticJson.hash(SemanticJson.tree(goalBindings)))
                ||!rules.options().equals(ruleOptions())||!rules.contracts().isEmpty()||!rules.decisions().isEmpty())
            throw new IllegalArgumentException("GENERIC_PROGRAM_OWNERSHIP_MISMATCH");
    }
}
