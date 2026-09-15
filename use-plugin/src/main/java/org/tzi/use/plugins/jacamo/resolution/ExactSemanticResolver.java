package org.tzi.use.plugins.jacamo.resolution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tzi.use.plugins.jacamo.binding.BindingEntry;
import org.tzi.use.plugins.jacamo.binding.BindingFile;
import org.tzi.use.plugins.jacamo.semantic.SemanticElement;

/** Formal resolver implementing exact identity and typed scope only; no normalization or similarity. */
public final class ExactSemanticResolver {
    private final List<SemanticElement> elements;
    private final Map<String, SemanticElement> byId = new LinkedHashMap<>();
    private final BindingFile bindings;
    public ExactSemanticResolver(List<SemanticElement> elements, BindingFile bindings) {
        this.elements = List.copyOf(elements); this.bindings = bindings;
        elements.forEach(element -> byId.put(element.id().value(), element));
    }
    public ResolutionResult resolve(ResolutionRequest request) {
        SemanticElement exact = byId.get(request.spelling());
        if (valid(exact, request)) return resolved(exact, ResolutionResult.Strategy.EXACT_ID);
        SemanticElement explicit = byId.get(request.explicitTargetId());
        if (request.explicitTargetId() != null)
            return valid(explicit, request) ? resolved(explicit, ResolutionResult.Strategy.EXPLICIT_REFERENCE)
                    : invalid("explicit target is absent or has an invalid target kind", explicit);
        int separator = Math.max(request.spelling().lastIndexOf('.'), request.spelling().lastIndexOf('/'));
        if (separator >= 0) {
            String owner = request.spelling().substring(0, separator);
            String local = request.spelling().substring(separator + 1);
            List<SemanticElement> matches = named(local, request).stream()
                    .filter(candidate -> candidate.id().ownerPath().contains(owner)).toList();
            if (matches.size() == 1) return resolved(matches.getFirst(), ResolutionResult.Strategy.OWNER_QUALIFIED);
            if (matches.size() > 1) return ambiguous(matches);
        }
        List<SemanticElement> scoped = named(request.spelling(), request).stream().filter(candidate ->
                request.typedScopeOwners().stream().allMatch(candidate.id().ownerPath()::contains)).toList();
        if (scoped.size() == 1) return resolved(scoped.getFirst(), ResolutionResult.Strategy.UNIQUE_TYPED_SCOPE);
        if (scoped.isEmpty()) return new ResolutionResult(ResolutionResult.Status.UNRESOLVED,
                ResolutionResult.Strategy.FAILURE, null, List.of(),
                "no exact candidate; binding cannot fabricate a source relation");
        List<BindingEntry> sourceBindings = bindings.entries().stream().filter(binding ->
                binding.status() == BindingEntry.Status.ACTIVE && binding.source().equals(request.sourceSemanticId())).toList();
        if (!sourceBindings.isEmpty()) {
            if (sourceBindings.size() != 1) return invalid("multiple active bindings for one source", null);
            SemanticElement target = byId.get(sourceBindings.getFirst().target());
            if (!valid(target, request)) return invalid("binding target is absent or has an invalid target kind", target);
            return resolved(target, ResolutionResult.Strategy.EXPLICIT_BINDING);
        }
        return ambiguous(scoped);
    }
    private List<SemanticElement> named(String name, ResolutionRequest request) {
        return elements.stream().filter(candidate -> candidate.name().equals(name)
                && request.targetKinds().contains(candidate.kind())).toList();
    }
    private boolean valid(SemanticElement element, ResolutionRequest request) {
        return element != null && request.targetKinds().contains(element.kind());
    }
    private ResolutionResult resolved(SemanticElement target, ResolutionResult.Strategy strategy) {
        return new ResolutionResult(ResolutionResult.Status.RESOLVED, strategy, target, List.of(target), null);
    }
    private ResolutionResult ambiguous(List<SemanticElement> candidates) {
        return new ResolutionResult(ResolutionResult.Status.AMBIGUOUS, ResolutionResult.Strategy.FAILURE, null,
                candidates, "exact candidates=" + candidates.stream().map(e -> e.id().value()).toList()
                + "; provide owner qualification or explicit binding");
    }
    private ResolutionResult invalid(String message, SemanticElement target) {
        List<SemanticElement> candidates = new ArrayList<>(); if (target != null) candidates.add(target);
        return new ResolutionResult(ResolutionResult.Status.INVALID_BINDING, ResolutionResult.Strategy.FAILURE,
                null, candidates, message);
    }
}
