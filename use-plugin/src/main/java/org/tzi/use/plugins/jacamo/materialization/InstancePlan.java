package org.tzi.use.plugins.jacamo.materialization;

import java.util.Comparator;
import java.util.List;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;

public record InstancePlan(List<ObjectPlan> objects, List<LinkPlan> links,
                           List<Diagnostic> diagnostics) {
    public InstancePlan {
        objects = objects.stream().sorted(Comparator.comparing(ObjectPlan::semanticId)).toList();
        links = links.stream().sorted(Comparator.comparing(LinkPlan::association)
                .thenComparing(LinkPlan::sourceObject).thenComparing(LinkPlan::targetObject)).toList();
        diagnostics = diagnostics.stream().sorted(Comparator.comparing((Diagnostic diagnostic) ->
                        diagnostic.semanticId() == null ? "" : diagnostic.semanticId())
                .thenComparing(Diagnostic::code)).toList();
    }
}
