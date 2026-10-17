package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Neutral semantic view of the existing transport snapshot; it owns no mutable subsystem state. */
public record SemanticObservationEnvelope(String version,String run,long generation,String revision,String profile,String configurationHash,
        String snapshot,Map<String,Object> capture,List<ObservationCoverage> scopes,Map<String,Object> payload) {
    public SemanticObservationEnvelope {if(!"1.0".equals(version)||run.isBlank()||generation<0||revision.isBlank()||profile.isBlank())throw new IllegalArgumentException("SEMANTIC_OBSERVATION_ENVELOPE_INVALID");capture=SemanticJson.object(capture);scopes=List.copyOf(scopes);payload=SemanticJson.object(payload);}
    public static SemanticObservationEnvelope from(RuntimeSnapshot source,String run,long generation,String profile,String configurationHash) {
        return new SemanticObservationEnvelope("1.0",run,generation,source.modelRevision(),profile,configurationHash,source.snapshotId(),source.captureMetadata(),source.facts().stream().sorted(Comparator.comparing(f->f.id().canonical())).map(f->ObservationCoverage.from(f,source.sourceCompleteness())).toList(),ContractPayloads.runtime(source));
    }
}
