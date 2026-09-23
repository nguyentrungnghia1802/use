package org.tzi.use.plugins.jacamo.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Validates declared V2 identities and ordering against one immutable Ecore byte snapshot. */
final class V2MappingValidator {
    void validate(JsonNode mapping, byte[] ecore) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ecore));
        require(hash.equals(mapping.path("sourceMetamodel").path("sha256").asText()), "MAPPING_ECORE_MISMATCH");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(ecore)).getDocumentElement();
        String pkg = root.getAttribute("name");
        require(pkg.equals(mapping.path("sourceMetamodel").path("packageName").asText()) &&
                root.getAttribute("nsURI").equals(mapping.path("sourceMetamodel").path("nsURI").asText()), "MAPPING_NAMESPACE_MISMATCH");
        Map<String, Element> elements = new TreeMap<>();
        var classifiers = root.getElementsByTagName("eClassifiers");
        for (int i = 0; i < classifiers.getLength(); i++) {
            var cls = (Element) classifiers.item(i); String id = pkg + "::" + cls.getAttribute("name");
            require(elements.put(id, cls) == null, "ECORE_DUPLICATE_SOURCE");
            var features = cls.getElementsByTagName("eStructuralFeatures");
            for (int j = 0; j < features.getLength(); j++) {
                var feature = (Element) features.item(j);
                require(elements.put(id + "#" + feature.getAttribute("name"), feature) == null, "ECORE_DUPLICATE_SOURCE");
            }
            require(cls.getAttribute("eSuperTypes").isBlank(), "V2_INHERITANCE_RECONCILE_REQUIRED");
        }
        Map<String, JsonNode> rules = new TreeMap<>(); Set<String> ids = new HashSet<>();
        for (String section : List.of("classMappings", "enumMappings", "attributeMappings", "referenceMappings"))
            for (var rule : mapping.path(section)) {
                String source = rule.path("source").asText();
                require(rules.put(source, rule) == null && ids.add(rule.path("id").asText()), "MAPPING_DUPLICATE_SOURCE_OR_ID");
                Element element = elements.get(source); require(element != null, "MAPPING_ORPHAN_SOURCE:" + source);
                String kind = element.getAttribute("xsi:type");
                String expected = switch (section) { case "classMappings" -> "EClass"; case "enumMappings" -> "EEnum";
                    case "attributeMappings" -> "EAttribute"; default -> "EReference"; };
                require(kind.endsWith(":" + expected), "MAPPING_SOURCE_KIND_MISMATCH:" + source);
                if (expected.equals("EClass")) require(Boolean.parseBoolean(element.getAttribute("abstract")) == rule.path("target").path("abstract").asBoolean(), "MAPPING_ABSTRACT_MISMATCH");
                if (expected.equals("EEnum")) {
                    var literals = element.getElementsByTagName("eLiterals");
                    require(literals.getLength() == rule.path("sourceLiterals").size() && literals.getLength() == rule.path("target").path("literals").size(), "MAPPING_ENUM_MISMATCH");
                    for (int j = 0; j < literals.getLength(); j++) {
                        Element literal = (Element) literals.item(j); var mapped = rule.path("sourceLiterals").get(j);
                        String name = literal.getAttribute("name");
                        require(name.equals(mapped.path("name").asText()) && name.equals(rule.path("target").path("literals").get(j).asText()) &&
                                integer(literal, "value", 0) == mapped.path("value").asInt() &&
                                (literal.hasAttribute("literal") ? literal.getAttribute("literal") : name).equals(mapped.path("literal").asText()), "MAPPING_ENUM_LITERAL_MISMATCH");
                    }
                }
                if (expected.equals("EAttribute") || expected.equals("EReference")) {
                    require(source.equals(pkg + "::" + rule.path("sourceOwner").asText() + "#" + rule.path("sourceName").asText()), "MAPPING_OWNER_MISMATCH");
                    require(integer(element, "lowerBound", 0) == rule.path("sourceMultiplicity").path("lower").asInt() &&
                            integer(element, "upperBound", 1) == rule.path("sourceMultiplicity").path("upper").asInt(), "MAPPING_BOUNDS_MISMATCH");
                    String type = element.getAttribute("eType"); type = type.substring(type.lastIndexOf('/') + 1);
                    if (expected.equals("EReference")) {
                        require(type.equals(rule.path("sourceTarget").asText()), "MAPPING_REFERENCE_TYPE_MISMATCH");
                        require(bool(element, "ordered", true) == rule.path("sourceOrdered").asBoolean() && bool(element, "unique", true) == rule.path("sourceUnique").asBoolean(), "MAPPING_ORDER_MISMATCH");
                        require(bool(element, "containment", false) == rule.path("sourceContainment").asBoolean(), "MAPPING_CONTAINMENT_MISMATCH");
                        String opposite = element.getAttribute("eOpposite");
                        if (!opposite.isBlank()) {
                            String[] parts = opposite.substring(3).split("/");
                            require((pkg + "::" + parts[0] + "#" + parts[1]).equals(rule.path("sourceEOpposite").asText()), "MAPPING_OPPOSITE_MISMATCH");
                        } else require(!rule.has("sourceEOpposite"), "MAPPING_INVENTED_OPPOSITE");
                    } else {
                        String declared = rule.path("sourceType").asText();
                        require(type.equals(declared.startsWith("EEnum:") ? declared.substring(6) : declared), "MAPPING_ATTRIBUTE_TYPE_MISMATCH");
                        String targetType = switch (type) { case "EString" -> "String"; case "EInt" -> "Integer"; case "EBoolean" -> "Boolean"; default -> type; };
                        require(targetType.equals(rule.path("target").path("type").asText()), "MAPPING_DATATYPE_MISMATCH");
                        require(element.hasAttribute("defaultValueLiteral") == rule.has("sourceExplicitDefaultLiteral"), "MAPPING_DEFAULT_MISMATCH");
                        if (element.hasAttribute("defaultValueLiteral")) require(element.getAttribute("defaultValueLiteral").equals(rule.path("sourceExplicitDefaultLiteral").asText()), "MAPPING_DEFAULT_MISMATCH");
                    }
                }
            }
        require(elements.keySet().equals(rules.keySet()), "MAPPING_SOURCE_COVERAGE_MISMATCH");
        require(mapping.path("inheritanceMappings").isEmpty(), "MAPPING_INVENTED_INHERITANCE");
        for (var projection : mapping.path("verificationProjections")) require(ids.add(projection.path("id").asText()), "MAPPING_DUPLICATE_ID");
        for (var rule : mapping.path("referenceMappings")) {
            var target = rule.path("target");
            if (target.has("aliasOf")) {
                var opposite = rules.get(rule.path("sourceEOpposite").asText());
                require(opposite != null && opposite.path("id").asText().equals(target.path("aliasOf").asText()) &&
                        opposite.path("target").path("name").asText().equals(target.path("associationName").asText()) && !target.path("emit").asBoolean(true), "MAPPING_ALIAS_MISMATCH");
            } else {
                require(rule.path("sourceOwner").asText().equals(target.path("firstEnd").path("class").asText()) && rule.path("sourceTarget").asText().equals(target.path("secondEnd").path("class").asText()), "MAPPING_DIRECTION_MISMATCH");
                require(bounds(rule).equals(target.path("secondEnd").path("multiplicity").asText()), "MAPPING_TARGET_BOUNDS_MISMATCH");
                if (rule.has("sourceEOpposite")) require(bounds(rules.get(rule.path("sourceEOpposite").asText())).equals(target.path("firstEnd").path("multiplicity").asText()), "MAPPING_OPPOSITE_BOUNDS_MISMATCH");
            }
        }
    }
    private String bounds(JsonNode rule) {
        require(rule != null, "MAPPING_OPPOSITE_MISSING");
        int lo = rule.path("sourceMultiplicity").path("lower").asInt(), hi = rule.path("sourceMultiplicity").path("upper").asInt();
        return hi == -1 ? (lo == 0 ? "*" : lo + "..*") : lo == hi ? "" + lo : lo + ".." + hi;
    }
    private int integer(Element e, String key, int fallback) { return e.hasAttribute(key) ? Integer.parseInt(e.getAttribute(key)) : fallback; }
    private boolean bool(Element e, String key, boolean fallback) { return e.hasAttribute(key) ? Boolean.parseBoolean(e.getAttribute(key)) : fallback; }
    private void require(boolean condition, String code) { if (!condition) throw new MappingException(code, code); }
}
