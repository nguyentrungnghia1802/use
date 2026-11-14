package org.tzi.use.plugins.jacamo.dsl.state;

import java.nio.file.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.tzi.use.plugins.jacamo.dsl.oal.mm.AcceptedAolState;

/** Versioned presentation snapshots. The native evaluator remains the authority;
 * loading a saved diagram never resumes a producer or certifies fresh state. */
public record ModelStateDocument(Kind kind, Path model, String checkpoint, AcceptedAolState state, List<Marking> markings) {
    public enum Kind { OAL, ISTAR_SCN }
    public record Marking(String context, String actor, String element, String status) { }
    public ModelStateDocument { model = model.toAbsolutePath().normalize(); markings = List.copyOf(markings); }
    public static AcceptedAolState objects(List<Map<String,Object>> rows) {
        var objects = new LinkedHashMap<String,AcceptedAolState.ObjectValue>();
        var links = new LinkedHashSet<AcceptedAolState.AssociationLink>();
        for (var row : rows) {
            String id = Objects.toString(row.get("object")), type = Objects.toString(row.get("type"));
            var attributes = new TreeMap<String,Object>();
            row.forEach((key,value) -> { if (!Set.of("object","type","sourceIdentity","relations","_links","_kind").contains(key)) attributes.put(key,value); });
            String name = Objects.toString(row.getOrDefault("name", row.getOrDefault("id", id))).replace("'", "");
            objects.put(id, new AcceptedAolState.ObjectValue(id, type, AcceptedAolState.Kind.valueOf(Objects.toString(row.getOrDefault("_kind", "ENTITY"))), attributes, name + " : " + type));
            if (row.get("_links") instanceof List<?> rawLinks) for (Object raw : rawLinks) {
                var link = (Map<?,?>) raw; var ends = (List<?>) link.get("ends");
                if (ends.size() == 2) links.add(new AcceptedAolState.AssociationLink(Objects.toString(link.get("relation")), ends.get(0).toString(), ends.get(1).toString()));
            }
        }
        return new AcceptedAolState(objects, List.copyOf(links), List.of());
    }
    public void write(Path file) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        var document = factory.newDocumentBuilder().newDocument();
        var root = document.createElement("use-model-state"); document.appendChild(root);
        root.setAttribute("version", "1"); root.setAttribute("kind", kind.name()); root.setAttribute("checkpoint", checkpoint);
        Path parent = file.toAbsolutePath().getParent(); String reference;
        try { reference = parent.relativize(model).toString(); } catch (IllegalArgumentException differentRoots) { reference = model.toString(); }
        root.setAttribute("model", reference);
        for (var object : state.objects().values().stream().sorted(Comparator.comparing(AcceptedAolState.ObjectValue::id)).toList()) {
            var item = child(document, root, "object", Map.of("id",object.id(),"type",object.type(),"kind",object.kind().name(),"label",object.displayName()));
            for (var attribute : new TreeMap<>(object.attributes()).entrySet()) child(document,item,"attribute",Map.of("name",attribute.getKey(),"value",Objects.toString(attribute.getValue())));
        }
        for (var link : state.associationLinks()) child(document,root,"link",Map.of("relation",link.relationName(),"source",link.sourceId(),"target",link.targetId()));
        for (var link : state.playLinks()) child(document,root,"play",Map.of("source",link.parentRoleId(),"target",link.childRoleId()));
        for (var marking : markings) child(document,root,"marking",Map.of("context",marking.context(),"actor",marking.actor(),"element",marking.element(),"status",marking.status()));
        var transformerFactory = TransformerFactory.newDefaultInstance();
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = transformerFactory.newTransformer(); transformer.setOutputProperty(OutputKeys.INDENT,"yes");
        try (var stream = Files.newOutputStream(file)) { transformer.transform(new DOMSource(document),new StreamResult(stream)); }
    }
    public static ModelStateDocument read(Path file) throws Exception {
        if (Files.size(file) > 16 * 1024 * 1024) throw new IllegalArgumentException("State document exceeds 16 MiB");
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var root = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
        if (!root.getTagName().equals("use-model-state") || !root.getAttribute("version").equals("1"))
            throw new IllegalArgumentException("Select a USE model-state v1 snapshot exported by the evaluation view");
        var objects = new LinkedHashMap<String,AcceptedAolState.ObjectValue>();
        var links = new ArrayList<AcceptedAolState.AssociationLink>(); var plays = new ArrayList<AcceptedAolState.PlayLink>(); var markings = new ArrayList<Marking>();
        for (var item : children(root)) switch (item.getTagName()) {
            case "object" -> {
                var attributes = new TreeMap<String,Object>();
                for (var attribute : children(item)) {
                    if (!attribute.getTagName().equals("attribute") || attributes.putIfAbsent(required(attribute,"name"),attribute.getAttribute("value")) != null) throw new IllegalArgumentException("Invalid or duplicate state attribute");
                }
                var object = new AcceptedAolState.ObjectValue(required(item,"id"),required(item,"type"),AcceptedAolState.Kind.valueOf(required(item,"kind")),attributes,required(item,"label"));
                if (objects.putIfAbsent(object.id(),object) != null) throw new IllegalArgumentException("Duplicate state object: " + object.id());
            }
            case "link" -> links.add(new AcceptedAolState.AssociationLink(required(item,"relation"),required(item,"source"),required(item,"target")));
            case "play" -> plays.add(new AcceptedAolState.PlayLink(required(item,"source"),required(item,"target")));
            case "marking" -> markings.add(new Marking(required(item,"context"),required(item,"actor"),required(item,"element"),required(item,"status")));
            default -> throw new IllegalArgumentException("Unknown state element: " + item.getTagName());
        }
        for (var link : links) if (!objects.containsKey(link.sourceId()) || !objects.containsKey(link.targetId())) throw new IllegalArgumentException("Unresolved state link endpoint");
        for (var link : plays) if (!objects.containsKey(link.parentRoleId()) || !objects.containsKey(link.childRoleId())) throw new IllegalArgumentException("Unresolved play endpoint");
        return new ModelStateDocument(Kind.valueOf(required(root,"kind")),file.toAbsolutePath().getParent().resolve(required(root,"model")),root.getAttribute("checkpoint"),new AcceptedAolState(objects,links,plays),markings);
    }
    private static String required(Element element,String name) { String value=element.getAttribute(name); if(value.isBlank())throw new IllegalArgumentException("Missing state attribute: " + name); return value; }
    private static List<Element> children(Element parent) { var result=new ArrayList<Element>(); for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e)result.add(e); return result; }
    private static Element child(Document document,Element parent,String name,Map<String,String> attributes) { var result=document.createElement(name); attributes.forEach(result::setAttribute);parent.appendChild(result);return result; }
}
