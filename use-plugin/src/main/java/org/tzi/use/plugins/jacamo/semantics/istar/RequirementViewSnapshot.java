package org.tzi.use.plugins.jacamo.semantics.istar;

import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationCoordinator;

/** Intentional presentation data is separate from the legacy organizational GoalViewSnapshot. */
public record RequirementViewSnapshot(String profile,String boundary,String freshness,List<Requirement> requirements) {
    public record Requirement(String occurrence,String raw,String evaluated,String lifecycle,String quality,String episode,String bindingToken,boolean historyGap,long firstSatisfied,long firstViolated,String diagnostic){}
    public RequirementViewSnapshot {requirements=List.copyOf(requirements);}
    public static RequirementViewSnapshot read(RuntimeVerificationCoordinator coordinator) {
        return coordinator.read(()->{var latest=coordinator.latest();if(latest==null || latest.semantic()==null)return new RequirementViewSnapshot("","","UNAVAILABLE",List.of());var semantic=latest.semantic();return new RequirementViewSnapshot(semantic.profile(),semantic.boundary(),latest.freshness(),semantic.requirements().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->{var m=e.getValue();return new Requirement(e.getKey(),m.raw().name(),m.evaluated().name(),m.lifecycle().name(),m.quality().name(),m.episodeIdentity(),m.bindingToken(),m.historyGap(),m.firstSatisfied(),m.firstViolated(),m.diagnostic());}).toList());});
    }
}
