package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.profile.CslMonitorConfiguration;

public record MappingBundle(NormalizedSourceModel source,CslMonitorConfiguration configuration,TraceComplement complement) {
    public MappingBundle {
        Objects.requireNonNull(source);Objects.requireNonNull(configuration);Objects.requireNonNull(complement);
        validateComplement(source,complement);
    }
    public static void validateComplement(NormalizedSourceModel source,TraceComplement complement) {
        if(!source.sourceHash().equals(complement.sourceHash()))throw new IllegalArgumentException("MAPPING_SOURCE_COMPLEMENT_HASH_MISMATCH");
        var expected=new HashSet<List<String>>();for(var node:source.nodes())for(String field:node.features().keySet())expected.add(List.of(node.id(),field));
        var actual=new HashSet<List<String>>();complement.decisions().forEach(d->actual.add(List.of(d.sourceId(),d.feature())));
        if(!expected.equals(actual))throw new IllegalArgumentException("MAPPING_FEATURE_DECISION_COVERAGE_MISMATCH");
        var features=new HashMap<List<String>,NormalizedSourceModel.Feature>();
        for(var node:source.nodes())node.features().forEach((name,value)->features.put(List.of(node.id(),name),value));
        for(var decision:complement.decisions()) {
            var feature=features.get(List.of(decision.sourceId(),decision.feature()));
            if(!SemanticJson.hash(feature.value()).equals(SemanticJson.hash(decision.preservedValue())) || !feature.origin().equals(decision.origin()))throw new IllegalArgumentException("MAPPING_PRESERVED_FEATURE_MISMATCH:"+decision.sourceId()+":"+decision.feature());
        }
    }
}
