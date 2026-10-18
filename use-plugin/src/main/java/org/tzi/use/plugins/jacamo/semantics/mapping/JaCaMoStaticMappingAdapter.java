package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.tzi.use.semantic.engine.frontend.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Transport-independent mapping outside the original CSL/iStar engine. */
public final class JaCaMoStaticMappingAdapter {
    public SemanticProgram map(ModelSnapshot input,JaCaMoMappingProfile profile,MappingRuleSet rules) {
        org.tzi.use.semantic.engine.api.EngineInstallationGuard.verify();
        if(!profile.id().equals(rules.profileId())||!profile.version().equals(rules.profileVersion()))throw new IllegalArgumentException("MAPPING_PROFILE_RULESET_MISMATCH");
        var source=new SourceModelNormalizer().normalize(input);
        if(!rules.expectedSourceHash().isBlank()&&!rules.expectedSourceHash().equals(source.sourceHash()))throw new IllegalArgumentException("MAPPING_SOURCE_HASH_MISMATCH");
        for(var decision:rules.decisions()) {
            var node=source.require(decision.sourceId());var feature=node.features().get(decision.feature());
            if(feature==null||!SemanticJson.hash(feature.value()).equals(SemanticJson.hash(decision.preservedValue()))||!feature.origin().equals(decision.origin()))throw new IllegalArgumentException("UNSUPPORTED_DECISION_SOURCE_MISMATCH");
            if(decision.mandatory())throw new MappingAdmissionException(List.of(decision.diagnostic()));
        }
        var csl=new CslFrontend().compile(rules.cslSource(),rules.id()+".csl").requireModel();
        var cslMapping=new JaCaMoCslMappingRules().map(input,source,rules,csl);csl=cslMapping.model();
        var goals=new IStarFrontend().compile(rules.iStarSource(),rules.id()+".istar").requireModel();
        var goalMapping=new JaCaMoGoalMappingRules().map(input,source,rules,csl,goals);
        var bindings=new SemanticBindingIndex(rules.bindings());
        var complement=new SemanticTransformationService().preserveCanonical(input,source,bindings);
        var diagnostics=new ArrayList<>(rules.decisions().stream().map(UnsupportedMappingDecision::diagnostic).toList());diagnostics.addAll(cslMapping.diagnostics());
        diagnostics.addAll(goalMapping.diagnostics());return new SemanticProgram(profile,rules,source,csl,goals,bindings,complement,diagnostics,goalMapping.bindings());
    }
}
