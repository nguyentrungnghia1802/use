package org.tzi.use.plugins.jacamo.verification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conservative dependency index: constraints without declared dependencies are always reevaluated. */
public final class ConstraintDependencyIndex {
    private final Map<String, Set<String>> byDependency = new LinkedHashMap<>();
    private final Set<String> alwaysEvaluate = new LinkedHashSet<>();

    public ConstraintDependencyIndex(Collection<ConstraintDescriptor> descriptors) {
        if (descriptors == null) throw new IllegalArgumentException("CONSTRAINT_DEPENDENCY_INDEX_INVALID");
        for (ConstraintDescriptor descriptor : descriptors) {
            if (!descriptor.enabled() || descriptor.kind() != ConstraintKind.INV) continue;
            if (descriptor.dependencies().isEmpty()) alwaysEvaluate.add(descriptor.id());
            else for (String dependency : descriptor.dependencies())
                byDependency.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(descriptor.id());
        }
    }

    public Selection select(Collection<String> changedDependencies) {
        if (changedDependencies == null || changedDependencies.isEmpty())
            return new Selection(Set.of(), true, "RUNTIME_DEPENDENCY_UNKNOWN");
        LinkedHashSet<String> selected = new LinkedHashSet<>(alwaysEvaluate);
        boolean known = false;
        for (String dependency : changedDependencies) {
            Set<String> matches = byDependency.get(dependency);
            if (matches != null) {
                known = true;
                selected.addAll(matches);
            }
        }
        if (!known && alwaysEvaluate.isEmpty())
            return new Selection(Set.of(), true, "RUNTIME_DEPENDENCY_UNINDEXED");
        return new Selection(selected, false, known ? "RUNTIME_DEPENDENCY_TARGETED"
                : "RUNTIME_DEPENDENCY_GLOBAL_ONLY");
    }

    public List<String> dependencies() { return new ArrayList<>(byDependency.keySet()); }

    public record Selection(Set<String> constraintIds, boolean fullCheckFallback, String reason) {
        public Selection { constraintIds = Set.copyOf(constraintIds); }
    }
}
