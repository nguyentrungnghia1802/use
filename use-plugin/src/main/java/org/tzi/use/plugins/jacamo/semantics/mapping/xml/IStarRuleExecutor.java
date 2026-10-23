package org.tzi.use.plugins.jacamo.semantics.mapping.xml;

import java.util.*;
import java.util.regex.*;
import org.tzi.use.semantic.engine.api.EngineSemanticsProfile;
import org.tzi.use.semantic.engine.frontend.IStarFrontend;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.SourceSelectors.Row;

/** Renders admitted XML declarations in memory; only the original frontend builds the M1. */
public final class IStarRuleExecutor {
    private static final Pattern TEMPLATE=Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    public record Trace(String ruleId,String target,MappingSpecification.Location location,
                        Map<String,String> captures,String description) {
        public Trace { captures=Map.copyOf(captures); }
    }
    public record Result(GoalModel model,String generatedSource,EngineSemanticsProfile profile,List<Trace> traces) {
        public Result { traces=List.copyOf(traces); }
    }
    private record Element(Node node,String name,Map<String,String> values) { }
    private record Actor(Node node,String name,Map<String,String> values,List<Element> elements) { }

    public GoalModel generate(MappingInputs inputs) { return prepare(inputs).model(); }

    public Result prepare(MappingInputs inputs) {
        var spec=inputs.specification();var graph=spec.root().child("istar");
        var selected=SourceSelections.execute(inputs);
        var profile=EngineSemanticsProfile.valueOf(spec.root().attr("engineProfile"));
        var actors=new LinkedHashMap<String,Actor>();var globalNames=new HashSet<String>();
        var traces=new ArrayList<Trace>();
        for(Node node:graph.children("actor")) {
            if(!Set.of("agent","role").contains(node.attr("kind")))throw node.location().error("ISTAR_ACTOR_KIND_UNSUPPORTED");
            for(var values:expand(node,Map.of(),selected)) {
                String name=id(node.attr("name"),values,node);unique(globalNames,name,node);
                var elements=new ArrayList<Element>();
                for(Node child:node.children())for(var captures:expand(child,values,selected)) {
                    String element=id(child.attr("name"),captures,child);unique(globalNames,element,child);
                    elements.add(new Element(child,element,captures));
                    var descriptions=child.children("description");
                    if(descriptions.size()>1)throw child.location().error("ISTAR_DESCRIPTION_MULTIPLICITY");
                    traces.add(new Trace(child.attr("id"),name+"."+element,child.location(),captures,
                        descriptions.isEmpty()?"":render(descriptions.getFirst().text(),captures,child)));
                }
                actors.put(name,new Actor(node,name,values,List.copyOf(elements)));
                traces.add(new Trace(node.attr("id"),name,node.location(),values,""));
            }
        }
        if(actors.isEmpty())throw graph.location().error("ISTAR_ACTOR_REQUIRED");
        StringBuilder source=new StringBuilder("istar "+id(graph.attr("name"),Map.of(),graph)+" {\n");
        for(Actor actor:actors.values()) {
            source.append(actor.node.attr("kind")).append(' ').append(actor.name).append(" {\n");
            for(Element element:actor.elements)emitElement(source,actor,element);
            // The original factory lifts the in-actor syntax into Actor.associations().
            for(Node relation:graph.children("association"))if(relation.attr("from").equals(actor.name)) {
                String kind=relation.attr("kind");
                if(!Set.of("is-a","participates-in").contains(kind))throw relation.location().error("ISTAR_ASSOCIATION_UNSUPPORTED");
                requireActor(actors,relation.attr("to"),relation);
                source.append(actor.name).append(' ').append(kind).append(' ').append(relation.attr("to")).append('\n');
            }
            source.append("}\n");
        }
        for(Node relation:graph.children("association"))requireActor(actors,relation.attr("from"),relation);
        for(Node dependency:graph.children("dependency")) {
            String kind=dependency.attr("kind"),name=id(dependency.attr("name"),Map.of(),dependency);
            if(!Set.of("goal","task","resource").contains(kind))throw dependency.location().error("ISTAR_DEPENDUM_UNSUPPORTED");
            unique(globalNames,name,dependency);
            Actor from=requireActor(actors,dependency.attr("fromActor"),dependency),to=requireActor(actors,dependency.attr("toActor"),dependency);
            requireElement(from,dependency.attr("from"),dependency);requireElement(to,dependency.attr("to"),dependency);
            source.append("depend ").append(from.name).append('.').append(dependency.attr("from")).append(" -> ")
                .append(kind).append(' ').append(name).append(" -> ").append(to.name).append('.').append(dependency.attr("to")).append('\n');
            traces.add(new Trace(dependency.attr("id"),name,dependency.location(),Map.of(),""));
        }
        source.append("}\n");
        GoalModel model=new IStarFrontend().compile(source.toString(),"generated-from:"+graph.location().source(),profile).requireModel();
        return new Result(model,source.toString(),profile,traces);
    }

    private static void emitElement(StringBuilder source,Actor actor,Element element) {
        Node node=element.node;var values=element.values;
        source.append(node.name()).append(' ').append(element.name);
        if(node.name().equals("goal")) {
            String type=node.optional("type");
            if(!Set.of("Achieve","Maintain","Sustain","None").contains(type))throw node.location().error("ISTAR_GOAL_TYPE_REQUIRED");
            source.append(" : ").append(type);
        } else if(node.attributes().containsKey("type"))throw node.location().error("ISTAR_TYPE_ON_NON_GOAL");
        source.append('\n');
        for(Node refine:node.children("refine")) {
            if(!Set.of("goal","task").contains(node.name()))throw refine.location().error("ISTAR_REFINEMENT_SOURCE_UNSUPPORTED");
            String target=id(refine.attr("target"),values,refine);
            var parent=requireElement(actor,target,refine);
            if(!parent.node.name().equals("goal"))throw refine.location().error("ISTAR_REFINEMENT_PARENT_NOT_GOAL");
            String kind=refine.attr("kind");
            if(!Set.of("AND","OR").contains(kind))throw refine.location().error("ISTAR_REFINEMENT_UNSUPPORTED:"+kind);
            source.append("> ").append(kind.equals("OR")?"or ":"").append(target).append('\n');
        }
        for(Node needed:node.children("neededBy")) {
            if(!node.name().equals("resource"))throw needed.location().error("ISTAR_NEEDED_BY_SOURCE_NOT_RESOURCE");
            String target=id(needed.attr("task"),values,needed);
            if(!requireElement(actor,target,needed).node.name().equals("task"))throw needed.location().error("ISTAR_NEEDED_BY_TARGET_NOT_TASK");
            source.append("> needed-by ").append(target).append('\n');
        }
        for(String slot:List.of("condition","pre","post")) {
            var clauses=node.children(slot);
            if(clauses.size()>1)throw node.location().error("ISTAR_CONTRACT_MULTIPLICITY:"+slot);
            for(Node clause:clauses) {
                String body=render(clause.text(),values,clause);
                if(body.isBlank()||body.contains("]}"))throw clause.location().error("ISTAR_CONTRACT_BLOCK_INVALID");
                source.append(slot).append(" {[ ").append(body).append(" ]}\n");
            }
        }
    }
    private static Actor requireActor(Map<String,Actor> actors,String name,Node at) {
        var actor=actors.get(name);if(actor==null)throw at.location().error("ISTAR_ACTOR_UNRESOLVED:"+name);return actor;
    }
    private static Element requireElement(Actor actor,String name,Node at) {
        return actor.elements.stream().filter(e->e.name.equals(name)).findFirst().orElseThrow(()->at.location().error("ISTAR_ELEMENT_WRONG_OWNER_OR_UNRESOLVED:"+actor.name+"."+name));
    }
    private static void unique(Set<String> names,String name,Node at) {
        if(!names.add(name))throw at.location().error("ISTAR_NAME_DUPLICATE:"+name);
    }
    private static List<Map<String,String>> expand(Node node,Map<String,String> inherited,Map<String,List<Row>> selected) {
        if(node.optional("selectRef").isEmpty())return List.of(inherited);
        var result=new ArrayList<Map<String,String>>();
        for(Row row:SourceSelections.require(selected,node.attr("selectRef"),node)) {
            var merged=new LinkedHashMap<>(inherited);
            row.values().forEach((key,value)->{String previous=merged.putIfAbsent(key,value);
                if(previous!=null&&!previous.equals(value))throw node.location().error("ISTAR_CAPTURE_COLLISION:"+key);});
            result.add(Map.copyOf(merged));
        }
        return List.copyOf(result);
    }
    private static String id(String template,Map<String,String> values,Node at) {
        String value=render(template,values,at);
        if(!value.matches("[A-Za-z_][A-Za-z0-9_]*"))throw at.location().error("ISTAR_IDENTIFIER_INVALID");return value;
    }
    private static String render(String template,Map<String,String> values,Node at) {
        var matcher=TEMPLATE.matcher(template);var result=new StringBuilder();
        while(matcher.find()) {
            String value=values.get(matcher.group(1));if(value==null)throw at.location().error("ISTAR_CAPTURE_REQUIRED:"+matcher.group(1));
            matcher.appendReplacement(result,Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);if(result.indexOf("${")>=0)throw at.location().error("ISTAR_TEMPLATE_UNSUPPORTED");return result.toString();
    }
}
