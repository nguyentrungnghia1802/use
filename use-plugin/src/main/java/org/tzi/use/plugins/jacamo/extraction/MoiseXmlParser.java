package org.tzi.use.plugins.jacamo.extraction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.plugins.jacamo.project.SourceKind;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticReference;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Secure static Moise XML extraction; external entities and DTDs are disabled. */
final class MoiseXmlParser {
    void parse(ExtractionContext context) {
        List<ElementDraft> organisations = context.elements.stream()
                .filter(element -> element.kind == MetamodelKind.Organization).toList();
        for (ElementDraft organisation : organisations) {
            String sourceName = ((AttributeValue.Text) organisation.sourceFacts.get("source")).value();
            List<Path> matches = context.graph.sources().stream().filter(source -> source.kind() == SourceKind.MOISE_XML
                    && source.path().getFileName().toString().equals(Path.of(sourceName).getFileName().toString()))
                    .map(source -> source.path()).toList();
            if (matches.size() == 1) parseDocument(context, organisation, matches.getFirst());
        }
    }

    private void parseDocument(ExtractionContext context, ElementDraft organisation, Path path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException exception) throws SAXException { throw exception; }
                @Override public void fatalError(SAXParseException exception) throws SAXException { throw exception; }
            });
            String source = context.read(path);
            Document document = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(source)));
            XmlSourcePositions.attach(document, source);
            Element root = document.getDocumentElement();
            String specificationId = attr(root, "id", organisation.name + "-spec");
            organisation.sourceFacts.put("specificationId", new AttributeValue.Text(specificationId));
            for (Element definition : descendants(root, "role-def")) {
                String id = attr(definition, "id", null);
                if (id != null) organisation.sourceFacts.put("roleDefinition:" + id, new AttributeValue.Text(xml(definition)));
            }
            for (Element group : descendants(root, "group-specification")) {
                if (ancestor(group, "group-specification") == null) extractGroup(context, organisation, null, group, path);
            }
            extractSchemes(context, organisation, root, path, organisation.name);
            extractNorms(context, organisation, root, path, organisation.name);

        } catch (Exception exception) {
            context.diagnostic("MOISE_XML_INVALID", Severity.ERROR, Phase.PARSING,
                    organisation.provenance.getFirst().span(), organisation.id.value(),
                    "Cannot parse Moise organisation XML", path + ": " + exception.getMessage(),
                    "Provide well-formed Moise XML without external entities");
        }
    }

    private void extractGroup(ExtractionContext context, ElementDraft organisation, ElementDraft parent,
                                      Element xmlGroup, Path path) {
        String name = attr(xmlGroup, "id", null); if (name == null) return;
        var instances = parent == null ? declaredInstances(context, MetamodelKind.Group, organisation.name, name) : List.<ElementDraft>of();
        if (instances.isEmpty()) instances = List.of(context.element(MetamodelKind.Group, name,
                parent == null ? owner(organisation) : owner(parent), path, sourceLine(xmlGroup), sourceColumn(xmlGroup), "moise-xml-parser", xml(xmlGroup)));
        for (ElementDraft group : instances) {
        if (group.sourceFacts.containsKey("declarationKind")) context.addProvenance(group, path, sourceLine(xmlGroup), sourceColumn(xmlGroup), "moise-xml-parser", xml(xmlGroup));
        group.sourceFacts.put("specificationId", new AttributeValue.Text(name));
        group.attributes.put("id", new AttributeValue.Text(group.name));
        cardinality(group, xmlGroup);
        link(parent == null ? organisation : parent, parent == null ? "groups" : "subGroups", group);
        for (Element roleXml : descendants(xmlGroup, "role")) {
            if (ancestor(roleXml, "group-specification") != xmlGroup) continue;
            String roleName = attr(roleXml, "id", null); if (roleName == null) continue;
            ElementDraft role = context.element(MetamodelKind.Role, roleName, owner(group), path, sourceLine(roleXml), sourceColumn(roleXml), "moise-xml-parser", xml(roleXml));
            role.attributes.put("id", new AttributeValue.Text(roleName));
            role.sourceFacts.put("declarationKind", new AttributeValue.Text("GROUP_ROLE_OCCURRENCE"));
            cardinality(role, roleXml);
            for (Element card : descendants(xmlGroup, "cardinality")) {
                if (ancestor(card, "group-specification") == xmlGroup && roleName.equals(attr(card, "object", null))) cardinality(role, card);
            }
            var definitions = descendants(xmlGroup.getOwnerDocument().getDocumentElement(), "role-def").stream()
                    .filter(e -> roleName.equals(attr(e, "id", null))).toList();
            if (definitions.size() == 1) {
                Element definition = definitions.getFirst();
                context.addProvenance(role, path, sourceLine(definition), sourceColumn(definition), "moise-xml-parser", xml(definition));
                String parentRole = attr(definition, "extends", null);
                if (parentRole != null) role.references.add(new SemanticReference("superRoles", group.name + "." + parentRole, null));
                booleanAttribute(context, role, definition, "abstract", "isAbstract");
            }
            link(group, "roles", role);
            if (group.sourceFacts.get("players") instanceof AttributeValue.Text players) {
                String[] tokens = players.value().replace(",", " ").trim().split("\\s+");
                for (int i = 0; i + 1 < tokens.length; i += 2) if (tokens[i + 1].equals(roleName))
                    role.references.add(new SemanticReference("agents", tokens[i], null));
            }
        }
        int index = 0;
        for (Element linkXml : descendants(xmlGroup, "link")) {
            if (ancestor(linkXml, "group-specification") != xmlGroup) continue;
            ElementDraft relation = context.element(MetamodelKind.Link, "link@" + (++index), owner(group), path, sourceLine(linkXml), sourceColumn(linkXml), "moise-xml-parser", xml(linkXml));
            enumAttribute(context, relation, linkXml, "type", "type", "LinkType");
            enumAttribute(context, relation, linkXml, "scope", "scope", "LinkScope");
            booleanAttribute(context, relation, linkXml, "bi-dir", "bidirectional");
            booleanAttribute(context, relation, linkXml, "extends-subgroups", "extendsSubgroups");
            for (String side : List.of("from", "to")) {
                String role = attr(linkXml, side, null);
                if (role != null) relation.references.add(new SemanticReference(side.equals("from") ? "sourceRole" : "targetRole", group.name + "." + role, null));
            }
            link(group, "links", relation);
        }
        for (Element child : descendants(xmlGroup, "group-specification"))
            if (ancestor(child, "group-specification") == xmlGroup) extractGroup(context, organisation, group, child, path);
        }
    }

    private void extractSchemes(ExtractionContext context, ElementDraft organisation, Element root,
                                Path path, String organisationName) {
        for (Element element : descendants(root, "scheme")) {
            String name = attr(element, "id", null); if (name == null) continue;
            var instances = declaredInstances(context, MetamodelKind.Scheme, organisationName, name);
            if (instances.isEmpty()) instances = List.of(context.element(MetamodelKind.Scheme, name, owner(organisation), path, sourceLine(element), sourceColumn(element), "moise-xml-parser", xml(element)));
            for (ElementDraft scheme : instances) {
            if (scheme.sourceFacts.containsKey("declarationKind")) context.addProvenance(scheme, path, sourceLine(element), sourceColumn(element), "moise-xml-parser", xml(element));
            scheme.sourceFacts.put("specificationId", new AttributeValue.Text(name));
            scheme.attributes.put("id", new AttributeValue.Text(scheme.name)); link(organisation, "schemes", scheme);
            var roots = children(element, "goal");
            for (Element goal : roots) {
                ElementDraft declared = extractGoal(context, scheme, goal, path);
                if (roots.size() == 1 || "true".equals(attr(goal, "root", null))) link(scheme, "rootGoal", declared);
            }
            for (Element missionXml : descendants(element, "mission")) {
                String missionName = attr(missionXml, "id", null); if (missionName == null) continue;
                ElementDraft mission = context.element(MetamodelKind.Mission, missionName, owner(scheme), path, sourceLine(missionXml), sourceColumn(missionXml), "moise-xml-parser", xml(missionXml));
                mission.attributes.put("id", new AttributeValue.Text(missionName)); cardinality(mission, missionXml);
                for (Element goal : descendants(missionXml, "goal")) {
                    String goalName = attr(goal, "id", null);
                    if (goalName != null) mission.references.add(new SemanticReference("goals", scheme.name + "." + goalName, null));
                }
                link(scheme, "missions", mission);
            }
            int unsupported = 0;
            for (Element plan : children(element, "plan")) {
                scheme.sourceFacts.put("unownedPlan@" + (++unsupported), new AttributeValue.Text(xml(plan)));
                context.diagnostic("MOISE_PLAN_OWNER_UNRESOLVED", Severity.WARNING, Phase.PARSING,
                        scheme.provenance.getFirst().span(), scheme.id.value(), "V2 OPlan requires an explicit owning OGoal",
                        xml(plan), "Retain source plan; do not infer ownership from matching goal names");
            }
            }
        }
    }

    private ElementDraft extractGoal(ExtractionContext context, ElementDraft scheme, Element goal, Path path) {
        String name = attr(goal, "id", null);
        if (name == null) throw new IllegalArgumentException("Moise goal ID required");
        ElementDraft node = context.element(MetamodelKind.OGoal, name, owner(scheme), path, sourceLine(goal), sourceColumn(goal), "moise-xml-parser", xml(goal));
        node.attributes.put("id", new AttributeValue.Text(name));
        copy(node, goal, "description");
        enumAttribute(context, node, goal, "type", "type", "GoalType");
        for (String[] names : List.of(new String[]{"min", "minAgentsToSatisfy"}, new String[]{"ttf", "timeToFulfill"})) {
            String value = attr(goal, names[0], null); if (value != null) node.attributes.put(names[1], new AttributeValue.Text(value));
        }
        int index = 0;
        for (Element planXml : children(goal, "plan")) {
            ElementDraft plan = context.element(MetamodelKind.OPlan, "plan@" + (++index), owner(node), path, sourceLine(planXml), sourceColumn(planXml), "moise-xml-parser", xml(planXml));
            enumAttribute(context, plan, planXml, "operator", "operator", "OPlanOperator");
            link(node, "plan", plan);
            for (Element child : children(planXml, "goal")) link(plan, "subGoals", extractGoal(context, scheme, child, path));
        }
        return node;
    }

    private void extractNorms(ExtractionContext context, ElementDraft organisation, Element root,
                              Path path, String organisationName) {
        for (Element element : descendants(root, "norm")) {
            String name = attr(element, "id", null); if (name == null) continue;
            ElementDraft norm = context.element(MetamodelKind.Norm, name, owner(organisation), path, sourceLine(element), sourceColumn(element), "moise-xml-parser", xml(element));
            norm.attributes.put("id", new AttributeValue.Text(name));
            enumAttribute(context, norm, element, "type", "type", "NormType");
            copy(norm, element, "condition");
            String time = attr(element, "time-constraint", null);
            if (time != null) norm.attributes.put("timeConstraint", new AttributeValue.Text(time));
            for (String feature : List.of("role", "mission")) {
                String value = attr(element, feature, null);
                if (value != null) norm.references.add(new SemanticReference(feature, value, null));
            }
            link(organisation, "norms", norm);
        }
    }
    private void cardinality(ElementDraft node, Element xml) {
        for (String key : List.of("min", "max")) {
            String value = attr(xml, key, null);
            if (value != null) node.attributes.put(key + "Cardinality", new AttributeValue.Text(value));
        }
    }
    private void enumAttribute(ExtractionContext context, ElementDraft node, Element xml, String source, String target, String enumeration) {
        String value = attr(xml, source, null); if (value == null) return;
        var parsed = MetamodelKind.registry().enumValue(enumeration, value);
        if (parsed.isPresent()) node.attributes.put(target, parsed.get());
        else invalidScalar(context, node, source, value, "declared " + enumeration + " literal");
    }
    private static List<String> owner(ElementDraft node) {
        List<String> path = new ArrayList<>(node.id.ownerPath()); path.add(node.name); return path;
    }
    private void link(ElementDraft from, String feature, ElementDraft to) {
        if (from.references.stream().noneMatch(r -> r.feature().equals(feature) && to.id.equals(r.targetId())))
            from.references.add(new SemanticReference(feature, to.name, to.id));
    }
    private Element ancestor(Element node, String tag) {
        for (Node parent = node.getParentNode(); parent instanceof Element e; parent = parent.getParentNode())
            if (e.getTagName().equals(tag)) return e;
        return null;
    }
    private List<Element> children(Element root, String tag) {
        List<Element> result = new ArrayList<>();
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element e && e.getTagName().equals(tag)) result.add(e);
        return result;
    }
    private int sourceLine(Element element) { return (Integer) element.getUserData("sourceLine"); }
    private int sourceColumn(Element element) { return (Integer) element.getUserData("sourceColumn"); }
    private String xml(Element element) {
        if (element.getUserData("sourceText") instanceof String text) return text;
        try {
            var transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            var text = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(element), new javax.xml.transform.stream.StreamResult(text));
            return text.toString();
        } catch (javax.xml.transform.TransformerException e) { throw new IllegalArgumentException("Cannot preserve XML source", e); }
    }

    private List<Element> descendants(Element root, String tag) {
        List<Element> matches = new ArrayList<>();
        var nodes = root.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) if (nodes.item(i) instanceof Element element) matches.add(element);
        return matches;
    }

    private List<ElementDraft> declaredInstances(ExtractionContext context, MetamodelKind kind,
                                          String organisation, String specificationType) {
        List<ElementDraft> matches = context.elements.stream().filter(candidate -> candidate.kind == kind
                && candidate.id.ownerPath().equals(List.of("MAS", organisation))
                && candidate.sourceFacts.get("specificationType") instanceof AttributeValue.Text type
                && type.value().equals(specificationType)).toList();
        return matches;
    }

    private void copy(ElementDraft draft, Element element, String... names) {
        for (String name : names) {
            String value = attr(element, name, null);
            if (value != null) draft.attributes.put(name, new AttributeValue.Text(value));
        }
    }

    private void integerAttribute(ExtractionContext context, ElementDraft draft, Element element, String name) {
        String value = attr(element, name, null);
        if (value != null) try { draft.attributes.put(name, new AttributeValue.IntegerNumber(Long.parseLong(value))); }
        catch (NumberFormatException invalid) { invalidScalar(context, draft, name, value, "integer"); }
    }

    private void booleanAttribute(ExtractionContext context, ElementDraft draft, Element element, String source, String target) {
        String value = attr(element, source, null);
        if (value == null) return;
        if (value.equals("true") || value.equals("1")) draft.attributes.put(target, new AttributeValue.Bool(true));
        else if (value.equals("false") || value.equals("0")) draft.attributes.put(target, new AttributeValue.Bool(false));
        else invalidScalar(context, draft, source, value, "XML boolean (true, false, 1 or 0)");
    }

    private void invalidScalar(ExtractionContext context, ElementDraft draft, String field, String value, String type) {
        context.diagnostic("MOISE_ATTRIBUTE_INVALID", Severity.ERROR, Phase.PARSING,
                draft.provenance.getFirst().span(), draft.id.value(), "Invalid Moise " + type + " attribute",
                field + "=" + value, "Supply a valid " + type + "; no value was inferred");
    }

    private String attr(Element element, String name, String fallback) {
        String value = element.getAttribute(name);
        return value.isBlank() ? fallback : value;
    }
}
