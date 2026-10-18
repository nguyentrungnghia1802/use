package org.tzi.use.semantic.engine.runtime;

import java.util.*;

/** Null Boolean means no known input. Quality/coverage enums stay outside the original Boolean core. */
public record EngineInputs(Map<String,GoalInput> goals,Map<String,TaskInput> tasks,Map<String,Boolean> qualities) {
    public record GoalInput(String episode,Boolean active,Boolean condition) { }
    public record TaskInput(String episode,Boolean required,Boolean pre,Boolean post) { }
    public EngineInputs {goals=Map.copyOf(goals);tasks=Map.copyOf(tasks);qualities=Collections.unmodifiableMap(new LinkedHashMap<>(qualities));}
    public static EngineInputs empty(){return new EngineInputs(Map.of(),Map.of(),Map.of());}
}
