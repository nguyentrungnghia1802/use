package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.List;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.sys.MObject;

/** Admitted case adapter on the existing native writer. Owns no MSystem/history.
 * Case rules and diagnostic identifiers stay outside the shared mutation engine. */
public interface CaseRuntimeMapping {
    record Outcome(boolean materialized, String ruleId, String identity, MObject object, String diagnostic) { }
    void validateObservation(VerificationObservation observation, String run, long generation, String revision);
    List<Outcome> applyCut(Port port, String run, long generation, RuntimeSnapshot cut,
                          List<RuntimeEvent> prefix, RuntimeEvent event) throws UseApiException;
    String rawInputRuleId();
    String observationRejectionCode();
    String mappingRejectionCode();
    boolean ownsRemoval(Port port, MObject object);
}
