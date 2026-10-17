package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Per-scope evidence; COMPLETE selected fields never imply a complete global MOISE population. */
public record ObservationCoverage(String identity,String authority,String family,Completeness selectedFields,boolean populationComplete,String cutDigest,String continuity,List<String> diagnostics) {
    public ObservationCoverage {diagnostics=List.copyOf(diagnostics);}
    public static ObservationCoverage from(RuntimeFact fact,Map<String,Completeness> sources) {
        String family=fact.kind().name(),authority=fact.id().authority();boolean faithful=fact.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY;
        var diagnostics=new ArrayList<String>();if(!faithful)diagnostics.add("NOT_FAITHFULLY_MATERIALIZED:"+fact.projectionStatus());if(fact.values().get("diagnostic") instanceof String value)diagnostics.add(value);
        boolean population=Set.of("cartago","jason").contains(authority) && sources.get(authority)==Completeness.COMPLETE;
        return new ObservationCoverage(fact.id().canonical(),authority,family,faithful?fact.completeness():Completeness.PARTIAL,population,SemanticJson.hash(fact.values()),Objects.toString(fact.values().get("continuity"),"SELECTED_OBSERVATION_ONLY"),diagnostics);
    }
}
