package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.semantic.engine.api.*;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;

/** Canonical static M1 program. No legacy schema/requirement authority and no MSystem. */
public final class SemanticProgram {
    private final JaCaMoMappingProfile profile;private final MappingRuleSet rules;private final NormalizedSourceModel source;
    private final CslModel csl;private final OwnedGoalModel goals;private final SemanticBindingIndex bindings;private final TraceComplement complement;
    private final List<MappingDiagnostic> diagnostics;private final String cslHash,hash;
    private final GoalRuntimeBindingPlan goalBindings;
    private final org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericBindingPlan xmlPlan;
    private final org.tzi.use.plugins.jacamo.semantics.mapping.compact.CompiledObservationPlan compactPlan;
    public SemanticProgram(JaCaMoMappingProfile profile,MappingRuleSet rules,NormalizedSourceModel source,CslModel csl,GoalModel goals,
            SemanticBindingIndex bindings,TraceComplement complement,List<MappingDiagnostic> diagnostics,GoalRuntimeBindingPlan goalBindings) {
        this(profile,rules,source,csl,goals,bindings,complement,diagnostics,goalBindings,null,null);
    }
    public static SemanticProgram fromXml(JaCaMoMappingProfile profile,MappingRuleSet rules,NormalizedSourceModel source,CslModel csl,GoalModel goals,
            SemanticBindingIndex bindings,TraceComplement complement,GoalRuntimeBindingPlan goalBindings,
            org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericBindingPlan xmlPlan) {
        return new SemanticProgram(profile,rules,source,csl,goals,bindings,complement,List.of(),goalBindings,Objects.requireNonNull(xmlPlan),null);
    }
    public static SemanticProgram fromCompact(JaCaMoMappingProfile profile,MappingRuleSet rules,NormalizedSourceModel source,CslModel csl,GoalModel goals,
            SemanticBindingIndex bindings,TraceComplement complement,GoalRuntimeBindingPlan goalBindings,
            org.tzi.use.plugins.jacamo.semantics.mapping.compact.CompiledObservationPlan compactPlan) {
        return new SemanticProgram(profile,rules,source,csl,goals,bindings,complement,List.of(),goalBindings,null,Objects.requireNonNull(compactPlan));
    }
    private SemanticProgram(JaCaMoMappingProfile profile,MappingRuleSet rules,NormalizedSourceModel source,CslModel csl,GoalModel goals,
            SemanticBindingIndex bindings,TraceComplement complement,List<MappingDiagnostic> diagnostics,GoalRuntimeBindingPlan goalBindings,
            org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericBindingPlan xmlPlan,
            org.tzi.use.plugins.jacamo.semantics.mapping.compact.CompiledObservationPlan compactPlan) {
        this.profile=profile;this.rules=rules;this.source=source;this.csl=csl;this.goals=new OwnedGoalModel(goals,profile.engine());this.bindings=bindings;this.complement=complement;this.diagnostics=List.copyOf(diagnostics);
        this.goalBindings=goalBindings;
        this.xmlPlan=xmlPlan;
        this.compactPlan=compactPlan;
        if(xmlPlan!=null){xmlPlan.requireOwnership(profile,rules,source,csl,goals,bindings,goalBindings);xmlPlan.requireComplement(complement);}
        else if(compactPlan==null && profile.id().equals(org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericBindingPlan.PROFILE_ID))throw new IllegalArgumentException("GENERIC_PROGRAM_PLAN_REQUIRED");
        MappingBundle.validateComplement(source,complement);bindings.validate(source,csl,goals,rules.contracts());
        cslHash=CslModelFingerprint.of(csl);
        if(compactPlan!=null) {
            hash=SemanticJson.hash(Map.ofEntries(Map.entry("format","SEMANTIC_PROGRAM/3.0"),Map.entry("source",source.sourceHash()),
                    Map.entry("profile",profile.hash()),Map.entry("rules",rules.hash()),Map.entry("cslM1",cslHash),
                    Map.entry("goalM1",this.goals.hash()),Map.entry("bindings",bindings.hash()),
                    Map.entry("trace",SemanticJson.hash(SemanticJson.tree(complement))),Map.entry("engineCommit",profile.engine().upstreamCommit()),
                    Map.entry("intentionalBindings",SemanticJson.hash(SemanticJson.tree(goalBindings))),Map.entry("compactPlanFingerprint",compactPlan.fingerprint())));
        } else {
            hash=SemanticJson.hash(Map.of("format","SEMANTIC_PROGRAM/2.0","source",source.sourceHash(),"profile",profile.hash(),"rules",rules.hash(),
                    "cslM1",cslHash,"goalM1",this.goals.hash(),"bindings",bindings.hash(),"trace",SemanticJson.hash(SemanticJson.tree(complement)),"engineCommit",profile.engine().upstreamCommit(),"intentionalBindings",SemanticJson.hash(SemanticJson.tree(goalBindings))));
        }
    }
    public JaCaMoMappingProfile profile(){return profile;}public MappingRuleSet rules(){return rules;}public NormalizedSourceModel source(){return source;}
    public CslModel cslModel(){return csl;}public GoalModel goalModel(){return goals.model();}public String cslHash(){return cslHash;}public String goalHash(){return goals.hash();}
    public List<MappingRuleSet.Contract> contracts(){return rules.contracts();}public SemanticBindingIndex bindings(){return bindings;}public TraceComplement complement(){return complement;}
    public GoalRuntimeBindingPlan goalBindings(){return goalBindings;}
    public org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericBindingPlan xmlPlan(){return xmlPlan;}
    public Object casePlan(){return null;}
    public org.tzi.use.plugins.jacamo.semantics.mapping.compact.CompiledObservationPlan compactPlan(){return compactPlan;}
    public boolean isCompact(){return compactPlan!=null;}
    public List<MappingDiagnostic> diagnostics(){return diagnostics;}public String hash(){return hash;}public void assertUnchanged(){goals.assertUnchanged();if(!cslHash.equals(CslModelFingerprint.of(csl)))throw new IllegalStateException("CANONICAL_CSL_MODEL_MUTATED_AFTER_OWNERSHIP");}
    public void requireSourceHash(String hash){if(!source.sourceHash().equals(hash))throw new IllegalArgumentException("SEMANTIC_PROGRAM_SOURCE_MISMATCH");}
}
