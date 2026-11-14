package org.tzi.use.plugins.jacamo.dsl.csl.view;

import java.util.*;
import org.tzi.use.uml.mm.MClass;

/** Read-only M1 presentation metadata; independent of any source runtime. */
public final class CslEnactmentMetadata {
    private CslEnactmentMetadata() { }
    public static Set<String> enactedRoles(MClass type) {
        if (type.getAnnotation("CslEnactmentView") == null) return Set.of();
        if (!"SINGLE_AGENT_ENACTMENT/1.0".equals(type.getAnnotationValue("CslEnactmentView", "version")))
            throw new IllegalArgumentException("ENACTMENT_VIEW_VERSION");
        return Set.copyOf(Arrays.asList(type.getAnnotationValue("CslEnactmentView", "roles").split("\\s+")));
    }
}
