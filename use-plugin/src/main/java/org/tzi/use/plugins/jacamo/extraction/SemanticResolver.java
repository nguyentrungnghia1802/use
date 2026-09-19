package org.tzi.use.plugins.jacamo.extraction;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticReference;

/** Exact and typed resolution only. No similarity or case-folded matching is used. */
final class SemanticResolver {
    private static final Map<String, Set<MetamodelKind>> TARGETS = Map.ofEntries(
            Map.entry("operation", EnumSet.of(MetamodelKind.AbsOperation, MetamodelKind.Operation,
                    MetamodelKind.GuardOperation, MetamodelKind.InternalOperation, MetamodelKind.LinkedOperation)),
            Map.entry("guardedBy", EnumSet.of(MetamodelKind.GuardOperation)),
            Map.entry("artifact", EnumSet.of(MetamodelKind.Artifact)),
            Map.entry("joinWorkspace", EnumSet.of(MetamodelKind.Workspace)),
            Map.entry("role", EnumSet.of(MetamodelKind.Role)),
            Map.entry("players", EnumSet.of(MetamodelKind.Agent)),
            Map.entry("RefRole", EnumSet.of(MetamodelKind.Role)),
            Map.entry("hasSubGroups", EnumSet.of(MetamodelKind.Group)),
            Map.entry("ogoal", EnumSet.of(MetamodelKind.OGoal)),
            Map.entry("FirstOgoal", EnumSet.of(MetamodelKind.OGoal)),
            Map.entry("NextOgoal", EnumSet.of(MetamodelKind.OGoal)),
            Map.entry("Nrole", EnumSet.of(MetamodelKind.Role)),
            Map.entry("NMission", EnumSet.of(MetamodelKind.Mission)),
            Map.entry("OGoalToGoal", EnumSet.of(MetamodelKind.Goal)),
            Map.entry("obsproperty", EnumSet.of(MetamodelKind.Belief)));

    void resolve(ExtractionContext context) {
        for (ElementDraft source : context.elements) {
            List<SemanticReference> resolved = new ArrayList<>();
            for (SemanticReference reference : source.references) {
                if (reference.targetId() != null || !TARGETS.containsKey(reference.feature())) {
                    resolved.add(reference); continue;
                }
                List<ElementDraft> candidates = candidates(context, reference.originalSpelling(), TARGETS.get(reference.feature()));
                if (candidates.size() == 1) {
                    resolved.add(new SemanticReference(reference.feature(), reference.originalSpelling(), candidates.getFirst().id));
                } else {
                    resolved.add(reference);
                    boolean formal = reference.feature().equals("operation");
                    String code = candidates.isEmpty() ? "RESOLUTION_UNRESOLVED" : "RESOLUTION_AMBIGUOUS";
                    context.diagnostic(code, formal ? Severity.ERROR : Severity.WARNING, Phase.RESOLUTION,
                            source.provenance.getFirst().span(), source.id.value(),
                            candidates.isEmpty() ? "Reference has no exact typed target" : "Reference has multiple exact typed targets",
                            reference.feature() + "=" + reference.originalSpelling() + "; candidates="
                                    + candidates.stream().map(candidate -> candidate.id.value()).toList(),
                            formal ? "Add an owner-qualified reference or explicit binding"
                                    : "Keep unresolved or provide an explicit source relation");
                }
            }
            source.references.clear();
            source.references.addAll(resolved);
        }
    }

    private List<ElementDraft> candidates(ExtractionContext context, String spelling, Set<MetamodelKind> kinds) {
        List<ElementDraft> exactId = context.elements.stream().filter(candidate ->
                kinds.contains(candidate.kind) && candidate.id.value().equals(spelling)).toList();
        if (!exactId.isEmpty()) return exactId;
        String local = spelling;
        String owner = null;
        int separator = Math.max(spelling.lastIndexOf('.'), spelling.lastIndexOf('/'));
        if (separator >= 0) { owner = spelling.substring(0, separator); local = spelling.substring(separator + 1); }
        final String wanted = local;
        final String ownerName = owner;
        return context.elements.stream().filter(candidate -> kinds.contains(candidate.kind)
                && candidate.name.equals(wanted)
                && (ownerName == null || candidate.id.ownerPath().contains(ownerName))).toList();
    }
}
