package org.tzi.use.plugins.jacamo.semantic;

import java.util.*;
import org.tzi.use.plugins.jacamo.mapping.ActiveBaseline;
import org.tzi.use.plugins.jacamo.mapping.MappingModel;

/** Descriptor-derived exact kinds/features. Unknown names remain a diagnostic, never a fuzzy match. */
public final class SemanticKindRegistry {
    private final MappingModel mapping;
    private final String ecoreHash;
    private final Map<String, MetamodelKind> kinds = new TreeMap<>();
    public SemanticKindRegistry(ActiveBaseline.Selection selection) {
        mapping = selection.mapping(); ecoreHash = selection.hashes().get(ActiveBaseline.ECORE);
        for (var cls : mapping.classes()) {
            Dimension dimension = switch (cls.dimension()) {
                case "agent" -> Dimension.AGENT;
                case "environment" -> Dimension.ENVIRONMENT;
                case "organisation" -> Dimension.ORGANISATION;
                default -> throw new IllegalArgumentException("SEMANTIC_DIMENSION_UNKNOWN:" + cls.source());
            };
            String sourceName = cls.source().substring(cls.source().lastIndexOf("::") + 2);
            if (kinds.put(sourceName, new MetamodelKind(sourceName, dimension)) != null)
                throw new IllegalArgumentException("SEMANTIC_KIND_DUPLICATE:" + cls.source());
        }
    }
    public record Resolution(MetamodelKind kind, String diagnostic) { }
    public Resolution resolve(String name) {
        var result = kinds.get(name);
        return new Resolution(result, result == null ? "SEMANTIC_KIND_UNKNOWN:" + name : null);
    }
    public MetamodelKind require(String name) {
        var result = resolve(name);
        if (result.kind() == null) throw new IllegalArgumentException(result.diagnostic());
        return result.kind();
    }
    public Collection<MetamodelKind> kinds() { return List.copyOf(kinds.values()); }
    public MappingModel mapping() { return mapping; }
    public String ecoreHash() { return ecoreHash; }
    public Set<String> owners(String kind) {
        Set<String> result = new TreeSet<>(); collectOwners(kind, result); return Set.copyOf(result);
    }
    private void collectOwners(String kind, Set<String> result) {
        if (!result.add(kind)) return;
        mapping.inheritance().stream().filter(e -> e.subclass().equals(kind)).forEach(e -> collectOwners(e.superclass(), result));
    }
    public Optional<MappingModel.ReferenceMapping> reference(MetamodelKind kind, String feature) {
        var owners = owners(kind.name());
        var matches = mapping.associations().stream().filter(r -> owners.contains(r.sourceOwner()) && r.sourceName().equals(feature)).toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    /** Partial source IR is allowed. Supplied facts must be typed; absent/unresolved facts stay explicit. */
    public void validate(List<SemanticElement> elements) {
        Map<SemanticId, SemanticElement> index = new HashMap<>();
        for (var element : elements) {
            if (!require(element.kind().name()).equals(element.kind()))
                throw new IllegalArgumentException("SEMANTIC_DIMENSION_MISMATCH:" + element.id());
            if (index.put(element.id(), element) != null)
                throw new IllegalArgumentException("SEMANTIC_ID_DUPLICATE:" + element.id());
            var owners = owners(element.kind().name());
            for (var entry : element.attributes().entrySet()) {
                var attrs = mapping.attributes().stream().filter(a -> owners.contains(a.sourceOwner())
                        && a.sourceName().equals(entry.getKey())).toList();
                if (attrs.size() != 1) throw new IllegalArgumentException("SEMANTIC_ATTRIBUTE_UNKNOWN:" + element.id() + ":" + entry.getKey());
                String type = attrs.getFirst().type();
                AttributeValue value = entry.getValue();
                boolean valid = switch (type) {
                    case "String" -> value instanceof AttributeValue.Text;
                    case "Integer" -> value instanceof AttributeValue.IntegerNumber;
                    case "Boolean" -> value instanceof AttributeValue.Bool;
                    default -> value instanceof AttributeValue.EnumLiteral e && e.enumeration().equals(type)
                            && mapping.enums().stream().anyMatch(en -> en.name().equals(type) && en.literals().contains(e.literal()));
                };
                if (!valid) throw new IllegalArgumentException("SEMANTIC_ATTRIBUTE_TYPE:" + element.id() + ":" + entry.getKey());
            }
        }
        for (var element : elements) for (var ref : element.references()) {
            var descriptor = reference(element.kind(), ref.feature()).orElseThrow(() ->
                    new IllegalArgumentException("SEMANTIC_REFERENCE_UNKNOWN:" + element.id() + ":" + ref.feature()));
            if (ref.targetId() == null) continue;
            var target = index.get(ref.targetId());
            if (target == null) throw new IllegalArgumentException("SEMANTIC_REFERENCE_MISSING:" + ref.targetId());
            if (!owners(target.kind().name()).contains(descriptor.sourceTarget()))
                throw new IllegalArgumentException("SEMANTIC_REFERENCE_TYPE:" + element.id() + ":" + ref.feature());
        }
    }
}
