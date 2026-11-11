package org.tzi.use.semantic.engine.runtime;

import java.util.*;

/** Null Boolean means no known input. Quality/coverage enums stay outside the original Boolean core. */
public record EngineInputs(Map<String,GoalInput> goals,Map<String,TaskInput> tasks,Map<String,Boolean> qualities,
                           Map<ResourceKey,ResourceInput> resources) {
    public record GoalInput(String episode,Boolean active,Boolean condition) { }
    public record TaskInput(String episode,Boolean required,Boolean pre,Boolean post) { }
    /** External resource evidence is scoped to the exact task occurrence and source relation. */
    public record ResourceKey(String taskOccurrenceId,String resourceSourceId) {
        public ResourceKey {requireText(taskOccurrenceId);requireText(resourceSourceId);}
    }
    public record ResourceInput(String episode,Boolean available,String evidenceRef) {
        public ResourceInput {
            requireText(episode);
            if(available!=null)requireText(evidenceRef);
        }
    }
    public EngineInputs(Map<String,GoalInput> goals,Map<String,TaskInput> tasks,Map<String,Boolean> qualities) {
        this(goals,tasks,qualities,Map.of());
    }
    public EngineInputs {goals=Map.copyOf(goals);tasks=Map.copyOf(tasks);qualities=Collections.unmodifiableMap(new LinkedHashMap<>(qualities));resources=Map.copyOf(resources);}
    private static void requireText(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("ENGINE_RESOURCE_IDENTITY_REQUIRED");}
    public static EngineInputs empty(){return new EngineInputs(Map.of(),Map.of(),Map.of());}
}
