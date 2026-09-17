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
            Map.entry("deploysAgent", EnumSet.of(MetamodelKind.Agent)),
            Map.entry("RefRole", EnumSet.of(MetamodelKind.Role)),
            Map.entry("hasSubGroups", EnumSet.of(MetamodelKind.Group)),
            Map.entry("ogoal", EnumSet.of(MetamodelKind.OGoal)),
            Map.entry("FirstOgoal", EnumSet.of(MetamodelKind.OGoal)),
            Map.entry("NextOgoal", EnumSet.of(MetamodelKind.OGoal)),
            Map.entry("Nrole", EnumSet.of(MetamodelKind.Role)),
            Map.entry("NMission", EnumSet.of(MetamodelKind.Mission)),
            Map.entry("OGoalToGoal", EnumSet.of(MetamodelKind.Goal)),
            Map.entry("obsproperty", EnumSet.of(MetamodelKind.Belief)));

    void resolve(ExtractionContext context) { resolve(context, null); }

    void resolve(ExtractionContext context, org.tzi.use.plugins.jacamo.binding.BindingFile bindings) {
        var exact = bindings == null ? null : new org.tzi.use.plugins.jacamo.resolution.ExactSemanticResolver(
                context.elements.stream().map(ElementDraft::freeze).toList(), bindings);
        if (bindings != null) {
            for (var binding : bindings.entries()) {
                ElementDraft source = context.elements.stream().filter(e -> e.id.value().equals(binding.source())).findFirst().orElse(null);
                boolean valid = source != null && source.references.stream().anyMatch(ref -> TARGETS.containsKey(ref.feature())
                        && candidates(context, ref.originalSpelling(), TARGETS.get(ref.feature())).stream()
                        .anyMatch(target -> target.id.value().equals(binding.target())));
                boolean stale = binding.status() != org.tzi.use.plugins.jacamo.binding.BindingEntry.Status.ACTIVE;
                boolean duplicate = bindings.entries().stream().filter(b -> b.source().equals(binding.source())).count() != 1;
                if (stale || !valid || duplicate) context.diagnostic(stale ? "BINDING_STALE" : "BINDING_INVALID",
                        Severity.ERROR, Phase.RESOLUTION, source == null ? null : source.provenance.getFirst().span(),
                        binding.source(), "Explicit binding cannot be used", binding.target(),
                        "Regenerate the binding against current source hashes and exact typed candidates");
            }
        }
        for (ElementDraft source : context.elements) {
            List<SemanticReference> resolved = new ArrayList<>();
            for (SemanticReference reference : source.references) {
                if (source.kind == MetamodelKind.Agent && reference.feature().equals("role")) {
                    promoteRoleAssignment(context, source, reference);
                    continue;
                }
                if (reference.targetId() != null || !TARGETS.containsKey(reference.feature())) {
                    resolved.add(reference); continue;
                }
                List<ElementDraft> candidates = candidates(context, reference.originalSpelling(), TARGETS.get(reference.feature()));
                if (exact != null) {
                    var result = exact.resolve(new org.tzi.use.plugins.jacamo.resolution.ResolutionRequest(
                            source.id.value(), reference.originalSpelling(), TARGETS.get(reference.feature()), null, List.of()));
                    if (result.status() == org.tzi.use.plugins.jacamo.resolution.ResolutionResult.Status.RESOLVED) {
                        resolved.add(new SemanticReference(reference.feature(), reference.originalSpelling(), result.target().id()));
                        continue;
                    }
                }
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

    /** JCM declares Agent -> Role, while the frozen Ecore stores the evidenced relation as Role.players -> Agent. */
    private void promoteRoleAssignment(ExtractionContext context, ElementDraft agent, SemanticReference reference) {
        List<ElementDraft> roles = candidates(context, reference.originalSpelling(), EnumSet.of(MetamodelKind.Role));
        if (roles.size() == 1) {
            ElementDraft role = roles.getFirst();
            boolean exists = role.references.stream().anyMatch(candidate -> candidate.feature().equals("players")
                    && agent.id.equals(candidate.targetId()));
            if (!exists) role.references.add(new SemanticReference("players", agent.name, agent.id));
            return;
        }
        context.diagnostic(roles.isEmpty() ? "RESOLUTION_UNRESOLVED" : "RESOLUTION_AMBIGUOUS", Severity.WARNING,
                Phase.RESOLUTION, agent.provenance.getFirst().span(), agent.id.value(),
                roles.isEmpty() ? "JCM role assignment has no exact Role declaration"
                        : "JCM role assignment has multiple exact Role declarations",
                "role=" + reference.originalSpelling() + "; candidates="
                        + roles.stream().map(candidate -> candidate.id.value()).toList(),
                "Provide an owner-qualified role assignment when the source is ambiguous");
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
