package org.tzi.use.plugins.jacamo.trace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.tzi.use.plugins.jacamo.mapping.MappingModel;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlan;
import org.tzi.use.plugins.jacamo.materialization.InstancePlan;
import org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel;
import org.tzi.use.plugins.jacamo.semantic.SemanticElement;

public final class TraceBuilder {
    public TraceIndex build(JaCaMoSemanticModel semantic, MappingModel mapping,
                            TransformationPlan transformation, InstancePlan instances) {
        List<TraceRecord> records = new ArrayList<>();
        mapping.classes().forEach(entry -> records.add(declaration(entry.source(), "class:" + entry.name(),
                "ECLASS", "CLASS", entry.id(), null)));
        mapping.attributes().forEach(entry -> records.add(declaration(entry.source(),
                "attribute:" + entry.owner() + "." + entry.name(), "EATTRIBUTE", "ATTRIBUTE", entry.id(), null)));
        mapping.associations().forEach(entry -> records.add(declaration(entry.source(),
                "association:" + entry.name(), "EREFERENCE", "ASSOCIATION", entry.id(), null,
                entry.reverse() ? entry.source() : null)));
        Map<String, SemanticElement> elements = semantic.elements().stream().collect(
                java.util.stream.Collectors.toMap(e -> e.id().value(), e -> e));
        instances.objects().forEach(object -> {
            SemanticElement source = elements.get(object.semanticId());
            if (source == null && transformation.orderProjections().stream().anyMatch(p -> p.entryClass().equals(object.className()))) return;
            records.add(new TraceRecord(id("object:" + object.name()), object.semanticId(), "object:" + object.name(),
                    source.kind().name(), "OBJECT", classRule(source, mapping), object.className().equals(source.kind().name())
                    ? null : "VP001", source.provenance().getFirst().span(), source.provenance().getFirst().sourceHash(),
                    null, object.className().equals(source.kind().name()) ? TraceRecord.Status.RESOLVED : TraceRecord.Status.PROJECTED));
            mapping.attributes().stream().filter(a -> object.values().containsKey(a.name())
                    && a.sourceOwner().equals(source.kind().name())
                    && !source.attributes().containsKey(a.sourceName()) && a.explicitDefault() != null).forEach(a -> {
                String target = "value:" + object.name() + "." + a.name();
                records.add(new TraceRecord(id(target), object.semanticId(), target, "ECORE_EXPLICIT_DEFAULT", "ATTRIBUTE_VALUE",
                        a.id(), null, source.provenance().getFirst().span(), source.provenance().getFirst().sourceHash(),
                        null, TraceRecord.Status.RESOLVED));
            });
        });
        instances.links().stream().filter(link -> mapping.associations().stream()
                .anyMatch(a -> !a.reverse() && a.id().equals(link.mappingRuleId()) && a.name().equals(link.association())))
                .forEach(link -> {
                    SemanticElement source = elements.get(link.sourceSemanticId());
                    String target = "link:" + link.association() + ":" + link.sourceObject() + ":" + link.targetObject();
                    records.add(new TraceRecord(id(target), link.sourceSemanticId(), target, "EREFERENCE", "LINK",
                            link.mappingRuleId(), null, source.provenance().getFirst().span(), source.provenance().getFirst().sourceHash(),
                            null, TraceRecord.Status.RESOLVED));
                });
        transformation.attributes().stream().filter(attribute -> attribute.ruleId().startsWith("VP"))
            .forEach(attribute -> records.add(declaration(attribute.sourceIdentity(),
                "attribute:" + attribute.owner() + "." + attribute.name(), "PROJECTION_SOURCE", "ATTRIBUTE", null, attribute.ruleId(), attribute.sourceIdentity())));
        transformation.operations().forEach(operation -> {
            SemanticElement source = elements.get(operation.sourceIdentity());
            records.add(new TraceRecord(id("operation:" + operation.owner() + "." + operation.name() + "|source:" + operation.sourceIdentity()),
                    operation.sourceIdentity(), "operation:" + operation.owner() + "." + operation.name(),
                    source == null ? "PROJECTION_SOURCE" : source.kind().name(), "OPERATION", null, operation.ruleId(),
                    source == null ? null : source.provenance().getFirst().span(),
                    source == null ? null : source.provenance().getFirst().sourceHash(), null, TraceRecord.Status.PROJECTED));
        });
        records.addAll(new OrderProjectionTrace().build(transformation, instances));
        return new TraceIndex(records);
    }
    private TraceRecord declaration(String source, String target, String sourceKind, String targetKind,
                                    String mappingRule, String projectionRule) {
        return declaration(source, target, sourceKind, targetKind, mappingRule, projectionRule, null);
    }
    private TraceRecord declaration(String source, String target, String sourceKind, String targetKind,
                                    String mappingRule, String projectionRule, String aliasSource) {
        return new TraceRecord(id(aliasSource == null ? target : target + "|source:" + aliasSource), source, target, sourceKind, targetKind, mappingRule, projectionRule,
                null, null, null, projectionRule == null ? TraceRecord.Status.RESOLVED : TraceRecord.Status.PROJECTED);
    }
    private String classRule(SemanticElement element, MappingModel mapping) {
        return mapping.classes().stream().filter(entry -> entry.name().equals(element.kind().name()))
                .map(MappingModel.ClassMapping::id).findFirst().orElse(null);
    }
    private String id(String value) {
        try { return "trace:" + java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
