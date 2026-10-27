package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;

/** Conservative acquisition bounds from validated declarative datasets and
 * provider requests. Native AST dependencies supply the pinned readSetHash.
 * No case name, Java source parsing, OCL text matching or runtime guessing. */
public final class SelectedObservationPlanner {
    private SelectedObservationPlanner() { }
    /** Explain the source/capability closure of an explicit runtime input.
     * The query engine still owns execution and type/cardinality validation. */
    public static List<Map<String,Object>> origins(MappingSpecification specification, String input) {
        var origins = new TreeMap<String,Map<String,Object>>();
        var pending = new ArrayDeque<String>(); var visited = new HashSet<String>(); pending.add(input);
        while(!pending.isEmpty()) {
            String id = pending.removeFirst(); if(!visited.add(id))continue;
            var node = specification.requireId(id);
            if(node.name().equals("table"))pending.add(node.attr("dataset"));
            if(!node.optional("tableRef").isEmpty())pending.add(node.attr("tableRef"));
            for(var child:node.children())if(!child.optional("tableRef").isEmpty())pending.add(child.attr("tableRef"));
            if(!node.optional("authority").isEmpty() || !node.optional("requestRef").isEmpty() || node.name().equals("beliefViews")) {
                String request=node.optional("requestRef");
                String provider=request.isEmpty()?"":specification.requireId(request).attr("provider");
                origins.put(id,Map.of("dataset",id,"source",node.name().equals("beliefViews")?"jason":node.optional("authority"),
                        "kind",node.name(),"factKind",node.optional("kind"),"request",request,"provider",provider));
            }
        }
        return List.copyOf(origins.values());
    }
    public static ObservationCaptureScope compile(MappingSpecification specification, String readSetHash,
            List<ObservationCapabilityRequest> requests) {
        var classes = new TreeSet<String>(); var properties = new TreeSet<String>();
        var moiseKinds=EnumSet.noneOf(RuntimeFactKind.class);boolean nplObservation=false;
        boolean allClasses = false, allProperties = false;
        var runtime = specification.root().child("runtime");
        List<Node> tables = runtime.children("tables").stream().flatMap(n -> n.children("table").stream()).toList();
        for (var section : runtime.children("datasets")) for (var dataset : section.children("facts")) {
            if(dataset.attr("authority").equals("moise"))moiseKinds.add(RuntimeFactKind.valueOf(dataset.attr("kind")));
            if (!dataset.attr("authority").equals("cartago")) continue;
            if (dataset.attr("kind").equals("ARTIFACT")) {
                var selected = exactFilters(tables, dataset.attr("id"), "values.artifactTypeJavaClassName");
                if (selected == null) allClasses = true; else classes.addAll(selected);
            }
            if (dataset.attr("kind").equals("PROPERTY")) {
                // A standalone property population is global. Do not infer an
                // artifact-class restriction from a downstream join: another
                // consumer may count that same table independently.
                allClasses = true;
                var selected = exactFilters(tables, dataset.attr("id"), "values.name");
                if (selected == null) allProperties = true; else properties.addAll(selected);
            }
        }
        for (var request : requests) {
            if(request.provider().equals("moise.scheme-lifecycle.v1"))moiseKinds.add(RuntimeFactKind.SCHEME_BOARD);
            if(Set.of("npl.lifecycle.v1","npl.source-clock.v1").contains(request.provider()))nplObservation=true;
            if (request.parameters().get("class") instanceof String type) classes.add(type);
            if (request.parameters().get("properties") instanceof Map<?,?> fields)
                for (Object field : fields.keySet()) properties.add((String)field);
        }
        // Responsible-group and player identity resolution is part of a scheme cut.
        if(moiseKinds.contains(RuntimeFactKind.SCHEME_BOARD))moiseKinds.add(RuntimeFactKind.GROUP_BOARD);
        if(moiseKinds.contains(RuntimeFactKind.GROUP_BOARD))classes.add("ora4mas.nopl.GroupBoard");
        if(moiseKinds.contains(RuntimeFactKind.SCHEME_BOARD))classes.add("ora4mas.nopl.SchemeBoard");
        // NPL discovers OrgArt subclasses from the actual official inventory;
        // an exact-name list cannot certify this polymorphic population.
        if(nplObservation)allClasses=true;
        return new ObservationCaptureScope(allClasses, allClasses ? Set.of() : classes,
                allProperties, allProperties ? Set.of() : properties,moiseKinds,nplObservation,readSetHash);
    }

    /** null means an explicitly broad dataset; empty means no matching rows.
     * Every direct consumer must have the positive exact predicate before the
     * union may narrow acquisition. Other predicates stay in the query engine. */
    private static Set<String> exactFilters(List<Node> tables, String dataset, String path) {
        var consumers = tables.stream().filter(t -> t.attr("dataset").equals(dataset)).toList();
        if (consumers.isEmpty()) return null;
        var values = new TreeSet<String>();
        for (var table : consumers) {
            var filters = table.children("where").stream().filter(n -> n.attr("path").equals(path)
                    && n.attr("type").equals("String")).toList();
            if (filters.isEmpty()) return null;
            values.add(filters.getFirst().attr("equals"));
        }
        return Set.copyOf(values);
    }
}
