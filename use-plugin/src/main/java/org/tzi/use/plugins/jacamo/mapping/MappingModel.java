package org.tzi.use.plugins.jacamo.mapping;

import java.util.Comparator;
import java.util.List;

/** Typed, immutable subset of the frozen mapping contract used by transformation. */
public record MappingModel(String schemaVersion, String mappingId, String status,
                           List<ClassMapping> classes, List<AttributeMapping> attributes,
                           List<ReferenceMapping> associations, List<InheritanceMapping> inheritance,
                           List<ProjectionMapping> projections) {
    public MappingModel {
        classes = sorted(classes, ClassMapping::id);
        attributes = sorted(attributes, AttributeMapping::id);
        associations = sorted(associations, ReferenceMapping::id);
        inheritance = sorted(inheritance, InheritanceMapping::id);
        projections = sorted(projections, ProjectionMapping::id);
    }

    private static <T> List<T> sorted(List<T> values, java.util.function.Function<T, String> id) {
        return values.stream().sorted(Comparator.comparing(id)).toList();
    }

    public record ClassMapping(String id, String source, String name, boolean abstractClass) { }
    public record AttributeMapping(String id, String source, String sourceOwner, String sourceName,
                                   String owner, String name, String type) { }
    public record AssociationEnd(String className, String multiplicity, String role, boolean ordered) { }
    public record ReferenceMapping(String id, String source, String sourceOwner, String sourceName,
                                   String sourceTarget, boolean containment, String kind, String name,
                                   AssociationEnd firstEnd, AssociationEnd secondEnd) { }
    public record InheritanceMapping(String id, String source, String subclass, String superclass) { }
    public record ProjectionMapping(String id, String name, String status) { }
}
