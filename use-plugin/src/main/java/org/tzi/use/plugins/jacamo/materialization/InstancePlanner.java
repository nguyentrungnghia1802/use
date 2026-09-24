package org.tzi.use.plugins.jacamo.materialization;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.plugins.jacamo.mapping.MappingModel;
import org.tzi.use.plugins.jacamo.mapping.TargetAttributeSpec;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlan;
import org.tzi.use.plugins.jacamo.mapping.UseNameAllocator;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;
import org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticElement;

/** Creates objects, scalar assignments and links without inventing absent source values. */
public final class InstancePlanner {
    public InstancePlan plan(JaCaMoSemanticModel semantic, MappingModel mapping, TransformationPlan transformation) {
        UseNameAllocator names = new UseNameAllocator();
        Map<String, String> objectNames = new LinkedHashMap<>();
        Map<String, SemanticElement> elements = new LinkedHashMap<>();
        for (SemanticElement element : semantic.elements()) {
            elements.put(element.id().value(), element);
            objectNames.put(element.id().value(), names.allocate(element.name(), element.id().value()));
        }
        List<ObjectPlan> objects = new ArrayList<>();
        Map<String, Map<String, AttributeValue>> valuesById = new LinkedHashMap<>();
        for (SemanticElement element : semantic.elements()) {
            Map<String, AttributeValue> values = new LinkedHashMap<>();
            Set<String> owners = ownersFor(element, transformation);
            mapping.attributes().stream().filter(entry -> owners.contains(entry.sourceOwner()))
                    .forEach(entry -> {
                        AttributeValue value = element.attributes().get(entry.sourceName());
                        if (value != null) values.put(entry.name(), value);
                    });
            valuesById.put(element.id().value(), values);
            objects.add(new ObjectPlan(objectNames.get(element.id().value()), targetClass(element, transformation),
                    element.id().value(), values));
        }
        applyProjectedState(semantic, transformation, valuesById, objects);

        List<LinkPlan> links = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Set<String> uniqueLinks = new HashSet<>();
        for (SemanticElement source : semantic.elements()) {
            Set<String> owners = ownersFor(source, transformation);
            for (var reference : source.references()) {
                var mappingEntry = mapping.associations().stream().filter(entry ->
                        owners.contains(entry.sourceOwner()) && entry.sourceName().equals(reference.feature()))
                        .findFirst();
                if (mappingEntry.isEmpty()) {
                    diagnostics.add(diagnostic("MATERIALIZATION_REFERENCE_UNMAPPED", Severity.ERROR, source, null,
                            "Semantic reference has no frozen Ecore mapping", reference.feature(),
                            "Correct the parser feature identity or reconcile the frozen mapping"));
                    continue;
                }
                if (reference.targetId() == null) {
                    diagnostics.add(diagnostic("MATERIALIZATION_REFERENCE_UNRESOLVED", Severity.WARNING, source,
                            mappingEntry.get().id(), "Reference remains unresolved and no link was invented",
                            reference.originalSpelling(), "Provide an exact source reference or explicit binding"));
                    continue;
                }
                String targetId = reference.targetId().value();
                if (!objectNames.containsKey(targetId)) {
                    diagnostics.add(diagnostic("MATERIALIZATION_TARGET_MISSING", Severity.ERROR, source,
                            mappingEntry.get().id(), "Resolved reference target is absent from the semantic model", targetId,
                            "Rebuild the semantic model and resolver indexes"));
                    continue;
                }
                var entry = mappingEntry.get();
                String from = entry.reverse() ? targetId : source.id().value();
                String to = entry.reverse() ? source.id().value() : targetId;
                String key = entry.name() + "|" + from + "|" + to;
                String canonicalRule = entry.reverse() ? mapping.associations().stream().filter(r -> !r.reverse() && r.name().equals(entry.name()))
                        .findFirst().orElseThrow().id() : entry.id();
                if (uniqueLinks.add(key)) links.add(new LinkPlan(entry.name(), objectNames.get(from),
                        objectNames.get(to), entry.containment(), from, to, canonicalRule));
            }
        }
        validateRequiredLinks(semantic, transformation, links, diagnostics);
        validateCompositionOwnership(elements, links, diagnostics);
        InstancePlan membership = new InstancePlan(objects, links, diagnostics);
        if (transformation.orderProjections().isEmpty()) return membership;
        List<org.tzi.use.plugins.jacamo.mapping.OrderProjectionPlanner.SourceOrder> orders = new ArrayList<>();
        for (var spec : transformation.orderProjections()) for (var source : semantic.elements()) {
            if (!ownersFor(source, transformation).contains(spec.owner())) continue;
            String feature = spec.sourceIdentity().substring(spec.sourceIdentity().indexOf('#') + 1);
            if (source.sourceFacts().containsKey("orderUnresolved:" + feature))
                throw new MaterializationException("ORDER_SOURCE_UNRESOLVED", spec.sourceIdentity() + ":" + source.id().value());
            var references = source.references().stream().filter(r -> r.feature().equals(feature)).toList();
            if (references.stream().anyMatch(r -> r.targetId() == null || !objectNames.containsKey(r.targetId().value())))
                throw new MaterializationException("ORDER_SOURCE_UNRESOLVED", spec.sourceIdentity() + ":" + source.id().value());
            orders.add(new org.tzi.use.plugins.jacamo.mapping.OrderProjectionPlanner.SourceOrder(spec.sourceIdentity(),
                    objectNames.get(source.id().value()), references.stream().map(r -> objectNames.get(r.targetId().value())).toList()));
        }
        return new org.tzi.use.plugins.jacamo.mapping.OrderProjectionPlanner().project(transformation, membership, orders);
    }

    private String targetClass(SemanticElement element, TransformationPlan transformation) {
        if (element.kind() == MetamodelKind.Artifact
                && element.attributes().get("className") instanceof AttributeValue.Text type
                && element.attributes().get("artifactTypeConfirmed") instanceof AttributeValue.Bool confirmed && confirmed.value()) {
            return transformation.classes().stream().filter(spec -> spec.ruleId().equals("VP001")
                    && spec.sourceIdentity().equals(type.value())).map(spec -> spec.name()).findFirst().orElse("Artifact");
        }
        return element.kind().name();
    }

    private void applyProjectedState(JaCaMoSemanticModel semantic, TransformationPlan transformation,
                                     Map<String, Map<String, AttributeValue>> valuesById, List<ObjectPlan> objects) {
        Map<String, TargetAttributeSpec> projected = new LinkedHashMap<>();
        transformation.attributes().stream().filter(attribute -> attribute.ruleId().equals("VP002"))
                .forEach(attribute -> projected.put(attribute.sourceIdentity(), attribute));
        for (SemanticElement artifact : semantic.elements().stream().filter(element -> element.kind() == MetamodelKind.Artifact).toList()) {
            for (var reference : artifact.references()) {
                TargetAttributeSpec attribute = reference.targetId() == null ? null : projected.get(reference.targetId().value());
                if (attribute == null) continue;
                SemanticElement property = semantic.elements().stream().filter(element -> element.id().equals(reference.targetId()))
                        .findFirst().orElse(null);
                AttributeValue initial = property == null ? null : literal(property);
                if (initial != null) valuesById.get(artifact.id().value()).put(attribute.name(), initial);
            }
        }
        for (int i = 0; i < objects.size(); i++) {
            ObjectPlan old = objects.get(i);
            objects.set(i, new ObjectPlan(old.name(), old.className(), old.semanticId(), valuesById.get(old.semanticId())));
        }
    }

    private AttributeValue literal(SemanticElement property) {
        if (!(property.attributes().get("initialExpression") instanceof AttributeValue.Text expression)
                || !(property.attributes().get("resolvedType") instanceof AttributeValue.Text type)) return null;
        String text = expression.value();
        try {
            return switch (type.value()) {
                case "String" -> text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")
                        ? new AttributeValue.Text(text.substring(1, text.length() - 1)) : null;
                case "Boolean" -> text.equals("true") || text.equals("false")
                        ? new AttributeValue.Bool(Boolean.parseBoolean(text)) : null;
                case "Integer", "Long", "Short", "Byte" -> new AttributeValue.IntegerNumber(Long.parseLong(text.replaceAll("[lL]$", "")));
                default -> null;
            };
        } catch (NumberFormatException exception) { return null; }
    }

    private void validateRequiredLinks(JaCaMoSemanticModel semantic, TransformationPlan transformation, List<LinkPlan> links,
                                       List<Diagnostic> diagnostics) {
        for (SemanticElement element : semantic.elements()) {
            Set<String> owners = ownersFor(element, transformation);
            for (var association : transformation.associations()) {
                if (!owners.contains(association.firstEnd().className()) || lower(association.secondEnd().multiplicity()) == 0) continue;
                long count = links.stream().filter(link -> link.sourceSemanticId().equals(element.id().value())
                        && link.mappingRuleId().equals(association.ruleId())).count();
                if (count < lower(association.secondEnd().multiplicity())) diagnostics.add(diagnostic(
                        "MATERIALIZATION_REQUIRED_LINK_MISSING", Severity.ERROR, element, association.ruleId(),
                        "Resolved source data does not satisfy the effective verification multiplicity",
                        association.sourceIdentity() + " requires " + association.secondEnd().multiplicity() + " but has " + count,
                        "Supply explicit source evidence; do not fabricate a target link"));
            }
        }
    }

    private Set<String> ownersFor(SemanticElement element, TransformationPlan transformation) {
        Map<String, List<String>> superclasses = transformation.classes().stream().collect(
                java.util.stream.Collectors.toMap(spec -> spec.name(), spec -> spec.superclasses()));
        Set<String> owners = new HashSet<>();
        owners.add(targetClass(element, transformation));
        boolean changed;
        do {
            changed = false;
            for (String owner : List.copyOf(owners))
                changed |= owners.addAll(superclasses.getOrDefault(owner, List.of()));
        } while (changed);
        return owners;
    }

    private void validateCompositionOwnership(Map<String, SemanticElement> elements, List<LinkPlan> links,
                                              List<Diagnostic> diagnostics) {
        Map<String, List<LinkPlan>> incoming = links.stream().filter(LinkPlan::composition)
                .collect(java.util.stream.Collectors.groupingBy(LinkPlan::targetSemanticId));
        for (var entry : incoming.entrySet()) {
            Set<String> owners = entry.getValue().stream().map(LinkPlan::sourceSemanticId).collect(java.util.stream.Collectors.toSet());
            if (owners.size() <= 1) continue;
            SemanticElement target = elements.get(entry.getKey());
            diagnostics.add(diagnostic("MATERIALIZATION_COMPOSITION_CONFLICT", Severity.ERROR, target, null,
                    "One semantic object has multiple composition owners under the frozen mapping",
                    entry.getValue().stream().map(LinkPlan::association).sorted().toList().toString(),
                    "Reconcile the source containment contract before materializing this object"));
        }
    }

    private int lower(String multiplicity) {
        if (multiplicity.equals("*")) return 0;
        int separator = multiplicity.indexOf("..");
        return Integer.parseInt(separator < 0 ? multiplicity : multiplicity.substring(0, separator));
    }

    private Diagnostic diagnostic(String code, Severity severity, SemanticElement element, String rule,
                                  String message, String evidence, String remediation) {
        return new Diagnostic(code, severity, Phase.MATERIALIZATION,
                element == null ? null : element.provenance().getFirst().span(),
                element == null ? null : element.id().value(), rule, message, evidence, remediation);
    }
}
