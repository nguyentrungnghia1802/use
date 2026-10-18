package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.uml.ocl.value.Value;

/** Immutable, prevalidated high-level write command. Identity/link execution reuses
 * the existing mapper and native Port inside the writer transaction. No live state is owned. */
public record SemanticMutationPlan(String programHash,BridgeEntityId runtime,String normalizedKind,Completeness completeness,
        Map<String,Object> payload,List<PropertyAssignment> assignments,List<EnactmentCandidate> enactments) {
    public record PropertyAssignment(String ownerId,String attribute,Value value){public PropertyAssignment{Objects.requireNonNull(value);}}
    public record EnactmentCandidate(String roleSourceId,String playerReference) { }
    public SemanticMutationPlan {
        Objects.requireNonNull(programHash);Objects.requireNonNull(runtime);Objects.requireNonNull(normalizedKind);Objects.requireNonNull(completeness);
        payload=SemanticJson.object(payload);assignments=List.copyOf(assignments);enactments=List.copyOf(enactments);
        if(completeness!=Completeness.COMPLETE)throw new IllegalArgumentException("NON_AUTHORITATIVE_MUTATION_PLAN");
    }
}
