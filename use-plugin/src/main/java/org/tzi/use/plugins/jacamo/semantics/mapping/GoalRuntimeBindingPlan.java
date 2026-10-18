package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.model.ExecutionDescriptor;

/** Definition templates only; no player UUID, runtime cast or temporal marking. */
public record GoalRuntimeBindingPlan(Map<String,ActorTemplate> actors,Map<String,ElementTemplate> elements,List<ExecutionDescriptor> executionDescriptors) {
    public record ActorTemplate(String actorId,String sourceId,String roleSourceId,String contextSourceId,String playerSourceId,String activationPolicy,String episodePolicy) { }
    public record ElementTemplate(String elementId,String actorId,boolean derived,String selfDeclaration,String selfSourceId,String selfKind,String contractId) { }
    public GoalRuntimeBindingPlan {actors=Collections.unmodifiableMap(new TreeMap<>(actors));elements=Collections.unmodifiableMap(new TreeMap<>(elements));executionDescriptors=List.copyOf(executionDescriptors);}
}
