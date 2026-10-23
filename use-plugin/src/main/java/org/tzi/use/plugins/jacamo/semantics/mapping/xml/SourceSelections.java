package org.tzi.use.plugins.jacamo.semantics.mapping.xml;
import java.util.*;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.SourceSelectors.Row;

/** Source-only selection and assertions; never a runtime population/completeness claim. */
public final class SourceSelections {
    private SourceSelections() { }
    public static Map<String,List<Row>> execute(MappingInputs inputs) {
        var spec=inputs.specification();var rows=new LinkedHashMap<String,List<Row>>();int total=0;
        for(var selector:spec.selectors()) {
            var selected=new SourceSelectors().select(selector,inputs.require(selector.attr("sourceRef")).bytes(),spec.namespaces());
            total+=selected.size();if(total>16384)throw selector.location().error("TOTAL_SELECTOR_CAPACITY");rows.put(selector.attr("id"),selected);
        }
        requireConditions(spec.nodes(),rows);
        return Collections.unmodifiableMap(rows);
    }
    public static void requireConditions(List<Node> nodes,Map<String,List<Row>> rows) {
        for(Node node:nodes)switch(node.name()) {
            case "require", "initializerDependency" -> {
                var selected=require(rows,node.attr("selectorRef"),node);
                if(selected.isEmpty()||selected.stream().anyMatch(r->!node.attr("equals").equals(r.values().get(node.attr("field")))))throw node.location().error("REQUIRE_VALUE_MISMATCH");
            }
            case "requireSet" -> {if(!values(rows,node.attr("selectorRef"),node.attr("field"),node).equals(Set.of(node.attr("values").split("\\s+"))))throw node.location().error("REQUIRE_SET_MISMATCH");}
            case "requireSameSet" -> {if(!values(rows,node.attr("leftSelectorRef"),node.attr("leftField"),node).equals(values(rows,node.attr("rightSelectorRef"),node.attr("rightField"),node)))throw node.location().error("REQUIRE_SAME_SET_MISMATCH");}
            default -> { }
        }
    }
    public static List<Row> require(Map<String,List<Row>> rows,String id,Node at) {
        var selected=rows.get(id);if(selected==null)throw at.location().error("SELECTOR_RESULT_REQUIRED:"+id);return selected;
    }
    private static Set<String> values(Map<String,List<Row>> rows,String selector,String field,Node at) {
        var result=new HashSet<String>();for(Row row:require(rows,selector,at)){String value=row.values().get(field);if(value==null)throw at.location().error("CAPTURE_REQUIRED:"+field);result.add(value);}return result;
    }
}
