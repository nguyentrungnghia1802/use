package org.tzi.use.semantic.engine.use75;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.tzi.use.api.*;
import org.tzi.use.semantic.engine.api.EngineSemanticsProfile;
import org.tzi.use.semantic.engine.api.EngineAdmissionException;
import org.tzi.use.semantic.engine.api.EngineDiagnostic;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.uml.mm.*;
import org.vnu.sme.goal.dsl.csl.mm.*;

/** CSL v4 state schema backend. No legacy translator, mapping rules, or runtime state. */
public final class Use75CslModelBackend {
    public static final String PROFILE = "Use4GoalCslV4";
    public static final String DECLARATION = "OriginalCslDeclaration";
    public static final String ATTRIBUTE = "OriginalCslAttribute";
    public static final String HELPER = "SemanticRuntimeHelper";
    private static final Set<String> RESERVED = Set.of("String", "Integer", "Real", "Boolean", "OclAny", "OclVoid");
    public static boolean active(MModel model) { return model.getAnnotation(PROFILE) != null; }
    public Use75ModelBinding compile(CslModel source) { return compile(source, M0ExtensionSpec.schemaOnly()); }

    public Use75ModelBinding compile(CslModel source, M0ExtensionSpec extension) {
        org.tzi.use.semantic.engine.api.EngineInstallationGuard.verify();
        new CslFrontend().validate(source, "<canonical-csl>").requireModel(); Objects.requireNonNull(extension);
        validateGeneralizationMetadata(source);
        var api = new UseModelApi(source.name());
        var classifiers = new LinkedHashMap<String, Use75ModelBinding.Classifier>();
        var owners = new LinkedHashMap<String, String>();
        var attributes = new LinkedHashMap<String, Use75ModelBinding.Attribute>();
        var ends = new LinkedHashMap<String, List<String>>();
        source.entities().forEach(e -> classifiers.put(e.name(), new Use75ModelBinding.Classifier(e.name(), e.name(), Use75ModelBinding.Kind.ENTITY)));
        source.roles().forEach(e -> classifiers.put(e.name(), new Use75ModelBinding.Classifier(e.name(), e.name(), Use75ModelBinding.Kind.ROLE)));
        source.orgContexts().forEach(e -> classifiers.put(e.name(), new Use75ModelBinding.Classifier(e.name(), e.name(), Use75ModelBinding.Kind.ORG_CONTEXT)));
        source.orgContexts().forEach(context -> context.members().forEach(member -> owners.put(member.type(), context.name())));
        var names = new HashSet<>(classifiers.keySet()); source.enums().forEach(e -> names.add(e.name()));
        source.namedDataTypes().forEach(e -> names.add(e.sourceName()));
        for (String name : names) if (RESERVED.contains(name) || extension.players() && M0ExtensionSpec.HELPER_TYPES.contains(name))
            throw new EngineAdmissionException(List.of(EngineDiagnostic.unsupported(EngineDiagnostic.Stage.CAPABILITY,
                    "CSL_NATIVE_RESERVED_NAME", name, "Name collides with the explicitly selected host/M0 namespace")));
        if (extension.players()) for (var relation : source.relations()) if (Set.of("Plays", "InContext").contains(relation.name()))
            throw new EngineAdmissionException(List.of(EngineDiagnostic.unsupported(EngineDiagnostic.Stage.CAPABILITY,
                    "CSL_NATIVE_RESERVED_ASSOCIATION", relation.name(), "Name collides with the explicit M0 extension")));
        try {
            api.getModel().addAnnotation(new MElementAnnotation(PROFILE, Map.of("profile", EngineSemanticsProfile.USE4GOAL_OBSERVED_V1.name(),
                    "version", source.version(), "players", Boolean.toString(extension.players()),
                    "compatibility", Boolean.toString(extension.enforceRoleCompatibility()))));
            if (extension.players()) installHelpers(api);
            for (var enumeration : source.enums()) api.createEnumeration(enumeration.name(), enumeration.literals());
            for (var datatype : source.namedDataTypes()) api.createDataType(datatype.sourceName(), false);
            for (var declaration : classifiers.values()) {
                var cls = api.createClass(declaration.symbol(), false);
                var metadata = new LinkedHashMap<String,String>(); metadata.put("id64", encode(declaration.declaration()));
                metadata.put("kind", declaration.kind().name()); metadata.put("owner64", encode(owners.getOrDefault(declaration.declaration(), "")));
                cls.addAnnotation(new MElementAnnotation(DECLARATION, metadata));
            }
            // All types exist before resolving inheritance, attributes and links.
            for (var generalization : source.generalizations()) api.createGeneralization(generalization.specific(), generalization.general());
            if (extension.players()) for (var declaration : classifiers.values()) {
                boolean root = source.generalizations().stream().noneMatch(g -> g.specific().equals(declaration.declaration()));
                if (root) api.createGeneralization(declaration.symbol(), switch (declaration.kind()) {
                    case ENTITY -> "DomainEntity"; case ROLE -> "RoleOccurrence"; case ORG_CONTEXT -> "OrgContext";
                });
            }
            for (var entity : source.entities()) installAttributes(api, entity.name(), entity.attributes(), attributes);
            for (var role : source.roles()) installAttributes(api, role.name(), role.attributes(), attributes);
            for (var context : source.orgContexts()) installAttributes(api, context.name(), context.attributes(), attributes);
            for (var relation : source.relations()) {
                int arity = relation.endpoints().size(); int[] aggregation = new int[arity];
                aggregation[0] = switch (relation.kind()) { case ASSOCIATION -> MAggregationKind.NONE; case AGGREGATION -> MAggregationKind.AGGREGATION; case COMPOSITION -> MAggregationKind.COMPOSITION; };
                var roles = new ArrayList<String>();
                for (int i = 0; i < arity; i++) roles.add(relation.endpoints().get(i).roleName().orElse("end" + i + "_" + relation.name()));
                api.createAssociation(relation.name(), relation.endpoints().stream().map(CslEndpoint::type).toArray(String[]::new),
                        roles.toArray(String[]::new), relation.endpoints().stream().map(e -> multiplicity(e.multiplicity())).toArray(String[]::new),
                        aggregation, new boolean[arity], new String[0][][]);
                api.getModel().getAssociation(relation.name()).addAnnotation(new MElementAnnotation("OriginalCslAssociation", Map.of("id64", encode(relation.name()), "kind", relation.kind().name())));
                ends.put(relation.name(), roles);
            }
            for (int i = 0; i < source.compatibilities().size(); i++) {
                var compatibility = source.compatibilities().get(i);
                api.getModel().addAnnotation(new MElementAnnotation("OriginalCslCompatibility_" + i, Map.of(
                        "from64", encode(compatibility.fromRole()), "to64", encode(compatibility.toRole()),
                        "placement64", encode(compatibility.groupName()), "semantics", "SYMMETRIC_SAME_PLAYER_CONTEXT_M0")));
            }
            for (var invariant : source.invariants()) api.createInvariant(invariant.name(), invariant.contextType(), invariant.expression(), false);
            if (extension.enforceRoleCompatibility()) installCompatibility(api, source);
            return new Use75ModelBinding(source, api.getModel(), extension, classifiers, owners, attributes, ends);
        } catch (UseApiException error) {
            throw new EngineAdmissionException(List.of(EngineDiagnostic.error(EngineDiagnostic.Stage.BACKEND,
                    "CSL_NATIVE_BACKEND_REJECTED", source.name(), error.getMessage())), error);
        }
    }
    private static void installAttributes(UseModelApi api, String owner, List<CslAttribute> declarations,
            Map<String, Use75ModelBinding.Attribute> bindings) throws UseApiException {
        for (var attribute : declarations) {
            var nativeAttribute = api.createAttribute(owner, attribute.name(), attribute.type().sourceName());
            nativeAttribute.addAnnotation(new MElementAnnotation(ATTRIBUTE, Map.of("id64", encode(owner + "::" + attribute.name()),
                    "optional", Boolean.toString(attribute.optional()), "mutable", Boolean.toString(attribute.mutable()),
                    "hasDefault", Boolean.toString(attribute.defaultValue().isPresent()), "default64", encode(attribute.defaultValue().orElse("")))));
            // Authored defaults do not synthesize runtime facts or native init expressions.
            bindings.put(owner + "::" + attribute.name(), new Use75ModelBinding.Attribute(owner, attribute));
        }
    }
    private static void validateGeneralizationMetadata(CslModel source) {
        var declared = new HashSet<CslGeneralization>();
        source.entities().forEach(e -> e.specializes().ifPresent(p -> declared.add(new CslGeneralization(e.name(), p))));
        source.roles().forEach(r -> r.parentRoles().forEach(p -> declared.add(new CslGeneralization(r.name(), p))));
        source.groups().forEach(c -> c.specializes().ifPresent(p -> declared.add(new CslGeneralization(c.name(), p))));
        if (!declared.equals(new HashSet<>(source.generalizations())))
            throw new EngineAdmissionException(List.of(EngineDiagnostic.error(EngineDiagnostic.Stage.MODEL_VALIDATION,
                    "CSL_NONCANONICAL_GENERALIZATION_METADATA", source.name(), "Classifier parents and generalization list disagree")));
    }
    private static void installHelpers(UseModelApi api) throws UseApiException {
        for (String helper : M0ExtensionSpec.HELPER_TYPES.stream().sorted().toList()) {
            var cls = api.createClass(helper, !Set.of("AtomicAgent", "WorkspaceObservation").contains(helper));
            cls.addAnnotation(new MElementAnnotation(HELPER, Map.of("id64", encode("helper:" + helper), "kind", "M0_EXTENSION")));
        }
        api.createGeneralization("AtomicAgent", "RuntimePlayer"); api.createGeneralization("OrgContext", "RuntimePlayer");
        api.createAssociation("Plays", "RuntimePlayer", "player", "1", 0, "RoleOccurrence", "enactedRoles", "*", 0);
        api.createAssociation("InContext", "OrgContext", "orgContext", "1", 0, "RoleOccurrence", "roles", "*", 0);
    }
    private static void installCompatibility(UseModelApi api, CslModel source) throws UseApiException {
        var permissions = new ArrayList<String>();
        for (var first : source.roles()) for (var second : source.roles()) {
            var a = api.getModel().getClass(first.name()); var b = api.getModel().getClass(second.name());
            if (a.isSubClassifierOf(b) || b.isSubClassifierOf(a) || source.compatibilities().stream().anyMatch(c ->
                    c.fromRole().equals(first.name()) && c.toRole().equals(second.name()) || c.toRole().equals(first.name()) && c.fromRole().equals(second.name())))
                permissions.add("(r1.oclIsKindOf(" + first.name() + ") and r2.oclIsKindOf(" + second.name() + "))");
        }
        String allowed = permissions.isEmpty() ? "false" : String.join(" or ", permissions);
        api.createInvariant("CslV4RoleCompatibility", "OrgContext", "self.roles->forAll(r1,r2 | r1 = r2 or r1.player <> r2.player or " + allowed + ")", false);
    }
    public static String multiplicity(CslCardinality cardinality) {
        return cardinality.max().isPresent() && cardinality.max().getAsInt() == cardinality.min() ? Integer.toString(cardinality.min())
                : cardinality.min() + ".." + (cardinality.max().isPresent() ? cardinality.max().getAsInt() : "*");
    }
    public static String encode(String text) { return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)); }
    public static String decode(String text) { return text == null ? "" : new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8); }
}
