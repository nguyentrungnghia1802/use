package org.tzi.use.plugins.jacamo.verification.profile;

import java.util.ArrayList;
import java.util.List;
import org.tzi.use.plugins.jacamo.mapping.MappingModel;
import org.tzi.use.plugins.jacamo.mapping.TargetAssociationSpec;
import org.tzi.use.plugins.jacamo.mapping.TargetClassSpec;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlan;

/** Applies explicit verification semantics while preserving the frozen baseline plan as an immutable input. */
public final class VerificationSemanticLayer {
    public EffectivePlan apply(TransformationPlan baseline, MappingModel mapping, VerificationProfile profile) {
        if (!mapping.mappingId().equals(profile.baselineMappingId()))
            throw new VerificationProfileException("VERIFICATION_PROFILE_BASELINE_MISMATCH",
                    profile.profileId() + " does not target " + mapping.mappingId());
        return apply(baseline, profile);
    }
    public EffectivePlan apply(TransformationPlan baseline, VerificationProfile profile) {
        List<TargetClassSpec> classes = new ArrayList<>(baseline.classes());
        List<TargetAssociationSpec> associations = new ArrayList<>(baseline.associations());
        for (VerificationProfile.Decision decision : profile.decisions()) {
            switch (decision.kind()) {
                case REMOVE_INHERITANCE -> removeInheritance(classes, decision);
                case OVERRIDE_FORWARD_MULTIPLICITY -> overrideMultiplicity(associations, decision);
            }
        }
        TransformationPlan effective = new TransformationPlan(classes, baseline.attributes(), associations,
                baseline.operations(), baseline.diagnostics(), baseline.orderProjections(), baseline.enums());
        return new EffectivePlan(profile, baseline, effective);
    }

    private void removeInheritance(List<TargetClassSpec> classes, VerificationProfile.Decision decision) {
        int index = indexOfClass(classes, decision.subclass());
        TargetClassSpec original = classes.get(index);
        if (!original.superclasses().contains(decision.superclass())) throw new VerificationProfileException(
                "VERIFICATION_PROFILE_BASELINE_MISMATCH", decision.id() + " expected " + decision.superclass());
        List<String> supers = original.superclasses().stream().filter(value -> !value.equals(decision.superclass())).toList();
        classes.set(index, new TargetClassSpec(original.name(), original.abstractClass(), supers,
                original.sourceIdentity(), original.ruleId()));
    }

    private void overrideMultiplicity(List<TargetAssociationSpec> associations, VerificationProfile.Decision decision) {
        int index = -1;
        for (int i = 0; i < associations.size(); i++) if (associations.get(i).ruleId().equals(decision.mappingRuleId())) index = i;
        if (index < 0) throw new VerificationProfileException("VERIFICATION_PROFILE_BASELINE_MISMATCH", decision.mappingRuleId());
        TargetAssociationSpec original = associations.get(index);
        if (!original.secondEnd().multiplicity().equals(decision.from())) throw new VerificationProfileException(
                "VERIFICATION_PROFILE_BASELINE_MISMATCH", decision.id() + " expected " + decision.from());
        MappingModel.AssociationEnd end = new MappingModel.AssociationEnd(original.secondEnd().className(),
                decision.to(), original.secondEnd().role(), original.secondEnd().ordered());
        associations.set(index, new TargetAssociationSpec(original.name(), original.kind(), original.firstEnd(), end,
                original.sourceIdentity(), original.ruleId()));
    }

    private int indexOfClass(List<TargetClassSpec> classes, String name) {
        for (int i = 0; i < classes.size(); i++) if (classes.get(i).name().equals(name)) return i;
        throw new VerificationProfileException("VERIFICATION_PROFILE_BASELINE_MISMATCH", name);
    }

    public record EffectivePlan(VerificationProfile profile, TransformationPlan baseline,
                                TransformationPlan transformation) { }
}
