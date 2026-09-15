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
                .filter(element -> element.kind == MetamodelKind.Organisation).toList();
        for (ElementDraft organisation : organisations) {
            String sourceName = ((AttributeValue.Text) organisation.attributes.get("source")).value();
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
            Document document = builder.parse(path.toFile());
            Element root = document.getDocumentElement();
            String specificationId = attr(root, "id", organisation.name + "-spec");
            List<String> owner = List.of("MAS", organisation.name);
            ElementDraft structural = context.element(MetamodelKind.StructuralSpecification, "structural",
                    owner, path, 1, 1, "moise-xml-parser", "structural-specification");
            ElementDraft functional = context.element(MetamodelKind.FunctionalSpecification, "functional",
                    owner, path, 1, 1, "moise-xml-parser", "functional-specification");
            ElementDraft normative = context.element(MetamodelKind.NormativeSpecification, "normative",
                    owner, path, 1, 1, "moise-xml-parser", "normative-specification");
            structural.attributes.put("specificationId", new AttributeValue.Text(specificationId));
            functional.attributes.put("specificationId", new AttributeValue.Text(specificationId));
            normative.attributes.put("specificationId", new AttributeValue.Text(specificationId));
            organisation.references.add(new SemanticReference("structuralspecification", structural.name, structural.id));
            organisation.references.add(new SemanticReference("functionalspecification", functional.name, functional.id));
            organisation.references.add(new SemanticReference("normativespecification", normative.name, normative.id));
            extractRoles(context, structural, root, path, organisation.name);
            extractGroups(context, structural, root, path, organisation.name);
            extractSchemes(context, functional, root, path, organisation.name);
            extractNorms(context, normative, root, path, organisation.name);
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            context.diagnostic("MOISE_XML_INVALID", Severity.ERROR, Phase.PARSING,
                    organisation.provenance.getFirst().span(), organisation.id.value(),
                    "Cannot parse Moise organisation XML", path + ": " + exception.getMessage(),
                    "Provide well-formed Moise XML without external entities");
        }
    }

    private void extractRoles(ExtractionContext context, ElementDraft structural, Element root,
                              Path path, String organisation) {
        for (Element element : descendants(root, "role-def")) {
            String name = attr(element, "id", null); if (name == null) continue;
            ElementDraft role = context.element(MetamodelKind.Role, name,
                    List.of("MAS", organisation, "structural"), path, 1, 1, "moise-xml-parser", name);
            role.attributes.put("Name", new AttributeValue.Text(name));
            integerAttribute(role, element, "min"); integerAttribute(role, element, "max");
            String parent = attr(element, "extends", null);
            if (parent != null) role.references.add(new SemanticReference("Extendsrole", parent, null));
            structural.references.add(new SemanticReference("role", name, role.id));
        }
    }

    private void extractGroups(ExtractionContext context, ElementDraft structural, Element root,
                               Path path, String organisation) {
        int formation = 0, link = 0;
        for (Element element : descendants(root, "group-specification")) {
            String name = attr(element, "id", null); if (name == null) continue;
            ElementDraft group = declaredInstance(context, MetamodelKind.Group, organisation, name);
            if (group == null) group = context.element(MetamodelKind.Group, name,
                    List.of("MAS", organisation, "structural"), path, 1, 1, "moise-xml-parser", name);
            else context.addProvenance(group, path, 1, 1, "moise-xml-parser", name);
            group.attributes.put("Name", new AttributeValue.Text(name));
            integerAttribute(group, element, "min"); integerAttribute(group, element, "max");
            structural.references.add(new SemanticReference("group", name, group.id));
            for (Element role : descendants(element, "role")) {
                String roleId = attr(role, "id", null);
                if (roleId != null) group.references.add(new SemanticReference("RefRole", roleId, null));
            }
            for (Element child : descendants(element, "group-specification")) {
                String childId = attr(child, "id", null);
                if (childId != null && !childId.equals(name)) group.references.add(new SemanticReference("hasSubGroups", childId, null));
            }
            for (Element linkElement : descendants(element, "link")) {
                String local = "link@" + (++link);
                ElementDraft relation = context.element(MetamodelKind.Link, local,
                        List.of("MAS", organisation, name), path, 1, 1, "moise-xml-parser", local);
                copy(relation, linkElement, "type", "scope", "from", "to");
                booleanAttribute(relation, linkElement, "bi-dir", "biDir");
                group.references.add(new SemanticReference("link", local, relation.id));
            }
            for (Element constraint : descendants(element, "cardinality")) {
                String local = "formation@" + (++formation);
                ElementDraft rule = context.element(MetamodelKind.FormationConstraints, local,
                        List.of("MAS", organisation, name), path, 1, 1, "moise-xml-parser", local);
                rule.attributes.put("Name", new AttributeValue.Text(local));
                copy(rule, constraint, "object"); integerAttribute(rule, constraint, "min"); integerAttribute(rule, constraint, "max");
                group.references.add(new SemanticReference("formationconstraints", local, rule.id));
            }
        }
    }

    private void extractSchemes(ExtractionContext context, ElementDraft functional, Element root,
                                Path path, String organisation) {
        int planIndex = 0;
        for (Element element : descendants(root, "scheme")) {
            String name = attr(element, "id", null); if (name == null) continue;
            ElementDraft scheme = declaredInstance(context, MetamodelKind.Scheme, organisation, name);
            if (scheme == null) scheme = context.element(MetamodelKind.Scheme, name,
                    List.of("MAS", organisation, "functional"), path, 1, 1, "moise-xml-parser", name);
            else context.addProvenance(scheme, path, 1, 1, "moise-xml-parser", name);
            functional.references.add(new SemanticReference("scheme", name, scheme.id));
            for (Element goal : descendants(element, "goal")) {
                String goalName = attr(goal, "id", null); if (goalName == null) continue;
                if (context.elements.stream().anyMatch(draft -> draft.kind == MetamodelKind.OGoal && draft.name.equals(goalName))) continue;
                ElementDraft oGoal = context.element(MetamodelKind.OGoal, goalName,
                        List.of("MAS", organisation, name), path, 1, 1, "moise-xml-parser", goalName);
                oGoal.attributes.put("Name", new AttributeValue.Text(goalName));
                oGoal.attributes.put("isRootGoal", new AttributeValue.Bool(Boolean.parseBoolean(attr(goal, "root", "false"))));
                scheme.references.add(new SemanticReference("SchemeOgoal", goalName, oGoal.id));
            }
            for (Element missionElement : descendants(element, "mission")) {
                String missionName = attr(missionElement, "id", null); if (missionName == null) continue;
                ElementDraft mission = context.element(MetamodelKind.Mission, missionName,
                        List.of("MAS", organisation, name), path, 1, 1, "moise-xml-parser", missionName);
                integerAttribute(mission, missionElement, "min"); integerAttribute(mission, missionElement, "max");
                for (Element goal : descendants(missionElement, "goal")) {
                    String goalName = attr(goal, "id", null);
                    if (goalName != null) mission.references.add(new SemanticReference("ogoal", goalName, null));
                }
                scheme.references.add(new SemanticReference("mission", missionName, mission.id));
            }
            for (Element planElement : descendants(element, "plan")) {
                String planName = "oplan@" + (++planIndex);
                ElementDraft plan = context.element(MetamodelKind.OPlan, planName,
                        List.of("MAS", organisation, name), path, 1, 1, "moise-xml-parser", planName);
                String operator = attr(planElement, "operator", "");
                plan.attributes.put("Sequence", new AttributeValue.Bool(operator.equalsIgnoreCase("sequence")));
                plan.attributes.put("Parallel", new AttributeValue.Bool(operator.equalsIgnoreCase("parallel")));
                List<Element> goals = descendants(planElement, "goal");
                if (!goals.isEmpty()) plan.references.add(new SemanticReference("FirstOgoal", attr(goals.getFirst(), "id", ""), null));
                scheme.references.add(new SemanticReference("SchemeOPlan", planName, plan.id));
            }
        }
    }

    private void extractNorms(ExtractionContext context, ElementDraft normative, Element root,
                              Path path, String organisation) {
        for (Element element : descendants(root, "norm")) {
            String name = attr(element, "id", null); if (name == null) continue;
            ElementDraft norm = context.element(MetamodelKind.Norm, name,
                    List.of("MAS", organisation, "normative"), path, 1, 1, "moise-xml-parser", name);
            copy(norm, element, "type");
            String time = attr(element, "time-constraint", null);
            if (time != null) norm.attributes.put("timeConstraint", new AttributeValue.Text(time));
            String role = attr(element, "role", null), mission = attr(element, "mission", null);
            if (role != null) norm.references.add(new SemanticReference("Nrole", role, null));
            if (mission != null) norm.references.add(new SemanticReference("NMission", mission, null));
            normative.references.add(new SemanticReference("norm", name, norm.id));
        }
    }

    private List<Element> descendants(Element root, String tag) {
        List<Element> matches = new ArrayList<>();
        var nodes = root.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) if (nodes.item(i) instanceof Element element) matches.add(element);
        return matches;
    }

    private ElementDraft declaredInstance(ExtractionContext context, MetamodelKind kind,
                                          String organisation, String specificationType) {
        List<ElementDraft> matches = context.elements.stream().filter(candidate -> candidate.kind == kind
                && candidate.id.ownerPath().contains(organisation)
                && candidate.attributes.get("specificationType") instanceof AttributeValue.Text type
                && type.value().equals(specificationType)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private void copy(ElementDraft draft, Element element, String... names) {
        for (String name : names) {
            String value = attr(element, name, null);
            if (value != null) draft.attributes.put(name, new AttributeValue.Text(value));
        }
    }

    private void integerAttribute(ElementDraft draft, Element element, String name) {
        String value = attr(element, name, null);
        if (value != null) try { draft.attributes.put(name, new AttributeValue.IntegerNumber(Long.parseLong(value))); }
        catch (NumberFormatException ignored) { draft.attributes.put(name, new AttributeValue.Text(value)); }
    }

    private void booleanAttribute(ElementDraft draft, Element element, String source, String target) {
        String value = attr(element, source, null);
        if (value != null) draft.attributes.put(target, new AttributeValue.Bool(Boolean.parseBoolean(value)));
    }

    private String attr(Element element, String name, String fallback) {
        String value = element.getAttribute(name);
        return value.isBlank() ? fallback : value;
    }
}
