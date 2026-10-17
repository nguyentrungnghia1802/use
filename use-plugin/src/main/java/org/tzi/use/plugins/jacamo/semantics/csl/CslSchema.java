package org.tzi.use.plugins.jacamo.semantics.csl;

import java.util.*;

/** M1 structure. Atomic players, enactments and interpreter observations are deliberately not Entities. */
public record CslSchema(String id, List<Classifier> classifiers, List<Association> associations,
        List<Invariant> invariants, List<OperationContract> contracts,
        @com.fasterxml.jackson.annotation.JsonInclude(value=com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM,valueFilter=CslStructuralPolicy.EmptyFilter.class) CslStructuralPolicy policy) {
    public CslSchema(String id,List<Classifier> classifiers,List<Association> associations,List<Invariant> invariants,List<OperationContract> contracts) {
        this(id,classifiers,associations,invariants,contracts,CslStructuralPolicy.empty());
    }
    public enum Kind { ENTITY, ROLE, ORG_CONTEXT }
    public record Attribute(String id, String name, String type, String sourceName, boolean optional) {
        public Attribute { required(id); symbol(name); required(sourceName); ScalarValueCodec.requireType(type); }
    }
    public record Classifier(String id, String sourceId, String symbol, Kind kind, String parentId,
            boolean abstractType, List<Attribute> attributes) {
        public Classifier {
            required(id); required(sourceId); CslSchema.symbol(symbol); Objects.requireNonNull(kind);
            parentId = Objects.requireNonNullElse(parentId, ""); attributes = List.copyOf(attributes);
            unique(attributes.stream().map(Attribute::name).toList(), "ATTRIBUTE_NAME");
            unique(attributes.stream().map(Attribute::sourceName).toList(), "PROPERTY_SLOT_BINDING");
        }
    }
    public record End(String typeId, String roleName, String multiplicity) {
        public End { required(typeId); symbol(roleName); CslSchemaValidator.multiplicity(multiplicity); }
    }
    public record Association(String id, String symbol, List<End> ends, int aggregationKind) {
        public Association {
            required(id); CslSchema.symbol(symbol); ends = List.copyOf(ends);
            if (ends.size() < 2 || aggregationKind < 0 || aggregationKind > 2
                    || aggregationKind != 0 && ends.size() != 2)
                throw new IllegalArgumentException("CSL_ASSOCIATION_SHAPE:" + id);
            unique(ends.stream().map(End::roleName).toList(), "ASSOCIATION_END_ROLE");
            if(aggregationKind==2)CslSchemaValidator.compositionMultiplicity(ends.getFirst().multiplicity());
        }
    }
    public record Invariant(String id, String typeId, String name, String body) {
        public Invariant { required(id); required(typeId); symbol(name); required(body); }
    }
    public record OperationContract(String id, String typeId, List<Object> signature, String pre, String post) {
        public OperationContract {
            required(id); required(typeId); signature = List.copyOf(signature);
            required(pre); required(post);
            if (signature.size() != 4 || !(signature.get(2) instanceof List<?>))
                throw new IllegalArgumentException("OPERATION_SIGNATURE_REQUIRED:" + id);
            signature = List.of(signature.get(0), signature.get(1), List.copyOf((List<?>) signature.get(2)), signature.get(3));
        }
    }
    public CslSchema {
        required(id); classifiers = List.copyOf(classifiers); associations = List.copyOf(associations);
        invariants = List.copyOf(invariants); contracts = List.copyOf(contracts);
        policy=Objects.requireNonNullElse(policy,CslStructuralPolicy.empty());
        CslSchemaValidator.validatePolicy(classifiers,policy);
        unique(classifiers.stream().map(Classifier::id).toList(), "CLASSIFIER_ID");
        unique(classifiers.stream().map(Classifier::symbol).toList(), "CLASSIFIER_SYMBOL");
        unique(classifiers.stream().map(Classifier::sourceId).toList(), "SOURCE_TYPE_BINDING");
        unique(invariants.stream().map(Invariant::id).toList(), "INVARIANT_ID");
        unique(contracts.stream().map(OperationContract::id).toList(), "CONTRACT_ID");
        unique(associations.stream().map(Association::symbol).toList(), "ASSOCIATION_SYMBOL");
        unique(associations.stream().map(Association::id).toList(), "ASSOCIATION_ID");
        var byId = new HashMap<String, Classifier>(); classifiers.forEach(c -> byId.put(c.id(), c));
        for (var classifier : classifiers) {
            var seen = new HashSet<String>();
            for (var current = classifier; !current.parentId().isBlank();) {
                if (!seen.add(current.id())) throw new IllegalArgumentException("CSL_GENERALIZATION_CYCLE:" + classifier.id());
                var parent = byId.get(current.parentId());
                if (parent == null || parent.kind() != classifier.kind() || parent.kind() == Kind.ORG_CONTEXT)
                    throw new IllegalArgumentException("CSL_GENERALIZATION_KIND_OR_REFERENCE:" + classifier.id());
                current = parent;
            }
        }
        for (var association : associations) {
            long nonEntity = association.ends().stream().map(e -> {
                var c = byId.get(e.typeId());
                if (c == null) throw new IllegalArgumentException("CSL_END_TYPE_UNRESOLVED:" + e.typeId());
                return c;
            }).filter(c -> c.kind() != Kind.ENTITY).count();
            if (nonEntity > 1) throw new IllegalArgumentException("CSL_ENTITY_CENTERED_ASSOCIATION_REQUIRED:" + association.id());
        }
        invariants.forEach(i -> { if (!byId.containsKey(i.typeId())) throw new IllegalArgumentException("INVARIANT_TYPE_UNRESOLVED:" + i.id()); });
        contracts.forEach(i -> { if (!byId.containsKey(i.typeId())) throw new IllegalArgumentException("CONTRACT_TYPE_UNRESOLVED:" + i.id()); });
    }
    public Classifier classifier(String id) {
        return classifiers.stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("CSL_TYPE_UNRESOLVED:" + id));
    }
    public static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("SEMANTIC_ID_OR_TEXT_REQUIRED");
        return value;
    }
    private static void symbol(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z_0-9]*"))
            throw new IllegalArgumentException("CSL_SYMBOL_INVALID:" + value);
    }
    private static void unique(List<String> ids, String kind) {
        if (new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("CSL_DUPLICATE_" + kind);
    }
}
