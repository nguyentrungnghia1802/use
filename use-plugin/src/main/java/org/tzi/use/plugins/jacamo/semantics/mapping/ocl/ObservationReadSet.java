package org.tzi.use.plugins.jacamo.semantics.mapping.ocl;

import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.uml.ocl.expr.Expression;

/** Frozen compiler-AST dependency inventory. No interpretation of OCL text or
 * acquisition/completeness authority is provided by this value object. */
public record ObservationReadSet(List<Map<String,Object>> attributes,
        List<Map<String,Object>> navigation, List<Map<String,Object>> populationReads) {
    public static final String VERSION = "OBSERVATION_READ_SET/1.0";
    public ObservationReadSet {
        attributes = freeze(attributes); navigation = freeze(navigation); populationReads = freeze(populationReads);
    }
    private static List<Map<String,Object>> freeze(List<Map<String,Object>> values) {
        return values.stream().map(SemanticJson::object).distinct()
                .sorted(Comparator.comparing(SemanticJson::hash)).toList();
    }
    public List<String> fields() { return attributes.stream().map(r -> (String)r.get("field")).distinct().sorted().toList(); }
    public List<String> populations() { return populationReads.stream().map(r -> (String)r.get("population")).distinct().sorted().toList(); }
    public Map<String,Object> evidence() {
        return SemanticJson.object(Map.of("version", VERSION, "attributes", attributes, "navigation", navigation,
                "populations", populationReads));
    }
    public String hash() { return SemanticJson.hash(evidence()); }
    public static ObservationReadSet compile(Map<String,Expression> expressions) {
        var attributes = new ArrayList<Map<String,Object>>();
        var navigation = new ArrayList<Map<String,Object>>();
        var populations = new ArrayList<Map<String,Object>>();
        for (var entry : new TreeMap<>(expressions).entrySet()) {
            var visitor = ExternalOclConstraintService.dependencyVisitor();
            entry.getValue().processWithVisitor(visitor);
            if(!visitor.unavailableReason().isEmpty())throw new IllegalArgumentException(visitor.unavailableReason());
            for (var read : visitor.attributeReads()) {
                var attribute = read.attribute();
                attributes.add(Map.of("contract", entry.getKey(), "field", attribute.owner().name() + "::" + attribute.name(),
                        "directSelf", read.directSelf(), "preState", read.preState()));
            }
            for (var scoped : visitor.scopedNavigationReads()) {
                var read=scoped.expression();
                navigation.add(Map.of("contract", entry.getKey(), "association", read.getDestination().association().name(),
                        "fromType", read.getSource().cls().name(), "toType", read.getDestination().cls().name(),
                        "fromRole", read.getSource().nameAsRolename(), "toRole", read.getDestination().nameAsRolename(),
                        "preState", scoped.preState()));
            }
            for (var population : visitor.populationTypes()) populations.add(Map.of("contract", entry.getKey(),
                    "population", population.name(), "status", "REQUIRES_COMPLETE_POPULATION"));
        }
        return new ObservationReadSet(attributes, navigation, populations);
    }
}
