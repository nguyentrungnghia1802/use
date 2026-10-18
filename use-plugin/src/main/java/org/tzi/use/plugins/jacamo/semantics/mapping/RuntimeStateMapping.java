package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.Map;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.*;

/** Input has already passed bridge admission/identity/sequence checks; this is not a transport receiver. */
public interface RuntimeStateMapping {
    SemanticMutationPlan plan(SemanticObservationMapper.Port reads,BridgeEntityId runtime,String normalizedKind,Completeness completeness,Map<String,Object> payload);
}
