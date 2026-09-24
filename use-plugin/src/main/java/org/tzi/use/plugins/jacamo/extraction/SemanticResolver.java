package org.tzi.use.plugins.jacamo.extraction;

import java.util.*;
import org.tzi.use.plugins.jacamo.binding.BindingFile;
import org.tzi.use.plugins.jacamo.diagnostics.*;
import org.tzi.use.plugins.jacamo.resolution.*;
import org.tzi.use.plugins.jacamo.semantic.*;

/** Exact source features and target kinds come from the active descriptor. */
final class SemanticResolver {
    void resolve(ExtractionContext context) { resolve(context, null); }
    void resolve(ExtractionContext context, BindingFile supplied) {
        var registry = MetamodelKind.registry();
        BindingFile bindings = supplied == null ? BindingFile.empty() : supplied;
        var frozen = context.elements.stream().map(ElementDraft::freeze).toList();
        var resolver = new ExactSemanticResolver(frozen, bindings);
        for (ElementDraft source : context.elements) {
            List<SemanticReference> resolved = new ArrayList<>();
            for (var reference : source.references) {
                var descriptor = registry.reference(source.kind, reference.feature());
                if (descriptor.isEmpty()) {
                    context.diagnostic("RESOLUTION_FEATURE_UNKNOWN", Severity.ERROR, Phase.RESOLUTION,
                            source.provenance.getFirst().span(), source.id.value(), "Source reference is absent from active V2",
                            reference.feature(), "Correct extractor source feature; do not use fuzzy mapping");
                    resolved.add(reference); continue;
                }
                if (reference.targetId() != null) { resolved.add(reference); continue; }
                Set<MetamodelKind> targets = new HashSet<>();
                for (var kind : registry.kinds()) if (registry.owners(kind.name()).contains(descriptor.get().sourceTarget())) targets.add(kind);
                var result = resolver.resolve(new ResolutionRequest(source.id.value(), reference.originalSpelling(), targets, null, List.of()));
                if (result.status() == ResolutionResult.Status.RESOLVED)
                    resolved.add(new SemanticReference(reference.feature(), reference.originalSpelling(), result.target().id()));
                else {
                    resolved.add(reference);
                    String code = switch (result.status()) {
                        case AMBIGUOUS -> "RESOLUTION_AMBIGUOUS";
                        case INVALID_BINDING -> "BINDING_INVALID";
                        default -> "RESOLUTION_UNRESOLVED";
                    };
                    context.diagnostic(code, reference.feature().equals("operation") ? Severity.ERROR : Severity.WARNING,
                            Phase.RESOLUTION, source.provenance.getFirst().span(), source.id.value(),
                            "Reference has no unique exact typed target", reference.feature() + "=" + reference.originalSpelling()
                                    + "; " + result.diagnostic(), "Provide exact owner qualification or a binding within existing candidates");
                }
            }
            source.references.clear(); source.references.addAll(resolved);
        }
        for (var binding : bindings.entries()) {
            var source = context.elements.stream().filter(e -> e.id.value().equals(binding.source())).findFirst().orElse(null);
            boolean used = source != null && source.references.stream().anyMatch(r -> r.targetId() != null && r.targetId().value().equals(binding.target()));
            if (binding.status() != org.tzi.use.plugins.jacamo.binding.BindingEntry.Status.ACTIVE || !used)
                context.diagnostic(binding.status() == org.tzi.use.plugins.jacamo.binding.BindingEntry.Status.ACTIVE ? "BINDING_INVALID" : "BINDING_STALE",
                        Severity.ERROR, Phase.RESOLUTION, source == null ? null : source.provenance.getFirst().span(), binding.source(),
                        "Binding is stale or outside an exact source relation", binding.target(), "Regenerate binding against current source identities and hashes");
        }
        completeOppositeMembership(context, registry);
    }

    /** Opposites entail membership, not independent order. Multi-member inferred order stays unresolved. */
    private void completeOppositeMembership(ExtractionContext context, SemanticKindRegistry registry) {
        Map<SemanticId, ElementDraft> elements = new HashMap<>(); context.elements.forEach(e -> elements.put(e.id, e));
        Map<ElementDraft, Set<String>> inferred = new LinkedHashMap<>();
        for (var alias : registry.mapping().associations().stream().filter(r -> r.reverse()).toList()) {
            var canonical = registry.mapping().associations().stream().filter(r -> !r.reverse() && r.name().equals(alias.name())).findFirst().orElseThrow();
            for (var direction : List.of(List.of(canonical, alias), List.of(alias, canonical))) {
                var from = direction.get(0); var to = direction.get(1);
                for (var source : context.elements) {
                    if (!registry.owners(source.kind.name()).contains(from.sourceOwner())) continue;
                    for (var ref : List.copyOf(source.references)) {
                        if (!ref.feature().equals(from.sourceName()) || ref.targetId() == null) continue;
                        var target = elements.get(ref.targetId()); if (target == null) continue;
                        if (target.references.stream().noneMatch(r -> r.feature().equals(to.sourceName()) && source.id.equals(r.targetId()))) {
                            target.references.add(new SemanticReference(to.sourceName(), source.id.value(), source.id));
                            inferred.computeIfAbsent(target, ignored -> new HashSet<>()).add(to.sourceName());
                        }
                    }
                }
            }
        }
        inferred.forEach((owner, features) -> features.forEach(feature -> {
            boolean ordered = registry.mapping().orderProjections().stream().anyMatch(p ->
                    registry.owners(owner.kind.name()).contains(p.owner()) && p.sourceIdentity().endsWith("#" + feature));
            if (ordered && owner.references.stream().filter(r -> r.feature().equals(feature)).count() > 1) {
                owner.sourceFacts.put("orderUnresolved:" + feature, new AttributeValue.Text("Opposite membership does not establish independent source order"));
                context.diagnostic("ORDER_SOURCE_UNRESOLVED", Severity.WARNING, Phase.RESOLUTION, owner.provenance.getFirst().span(),
                        owner.id.value(), "Independent opposite order is not supplied by source", feature,
                        "Provide authoritative order evidence; membership insertion order is not source order");
            }
        }));
    }
}
