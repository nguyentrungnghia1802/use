package org.tzi.use.plugins.jacamo.verify.use75;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.verify.api.EngineDiagnostic;
import org.tzi.use.plugins.jacamo.verify.api.EngineDiagnostic.AdmissionException;
import org.tzi.use.plugins.jacamo.verify.api.EngineSemanticsProfile;
import org.vnu.sme.goal.dsl.csl.parser.CslFrontend;
import org.tzi.use.uml.mm.*;
import org.vnu.sme.goal.dsl.csl.mm.*;

/** CSL v4 state schema backend. No legacy translator, mapping rules, or runtime state. */
public final class CslModelCompiler {
    public static final String PROFILE = "Use4GoalCslV4";
    public static final String DECLARATION = "OriginalCslDeclaration";
    public static final String ATTRIBUTE = "OriginalCslAttribute";
    public static final String HELPER = "SemanticRuntimeHelper";
    private static final Set<String> RESERVED = Set.of("String", "Integer", "Real", "Boolean", "OclAny", "OclVoid");
    public static boolean active(MModel model) { return model.getAnnotation(PROFILE) != null; }
    @FunctionalInterface public interface ModelPreparation {
        /** Construction-only extension, before authored invariants compile. No runtime state exists here. */
        void prepare(UseModelApi api, Binding binding) throws UseApiException;
    }
    public Binding compile(CslModel source) { return compile(source, ModelExtension.schemaOnly()); }

    public Binding compile(CslModel source, ModelExtension extension) {
        return compile(source, extension, (api, binding) -> { });
    }

    public Binding compile(CslModel source, ModelExtension extension, ModelPreparation preparation) {
        org.tzi.use.plugins.jacamo.verify.api.EngineInstallationGuard.verify();
        new CslFrontend().validate(source, "<canonical-csl>").requireModel(); Objects.requireNonNull(extension); Objects.requireNonNull(preparation);
        validateGeneralizationMetadata(source);
        var api = new UseModelApi(source.name());
        var classifiers = new LinkedHashMap<String, Binding.Classifier>();
        var owners = new LinkedHashMap<String, String>();
        var attributes = new LinkedHashMap<String, Binding.Attribute>();
        var ends = new LinkedHashMap<String, List<String>>();
        source.entities().forEach(e -> classifiers.put(e.name(), new Binding.Classifier(e.name(), e.name(), Binding.Kind.ENTITY)));
        source.roles().forEach(e -> classifiers.put(e.name(), new Binding.Classifier(e.name(), e.name(), Binding.Kind.ROLE)));
        source.orgContexts().forEach(e -> classifiers.put(e.name(), new Binding.Classifier(e.name(), e.name(), Binding.Kind.ORG_CONTEXT)));
        source.orgContexts().forEach(context -> context.members().forEach(member -> owners.put(member.type(), context.name())));
        var names = new HashSet<>(classifiers.keySet()); source.enums().forEach(e -> names.add(e.name()));
        source.namedDataTypes().forEach(e -> names.add(e.sourceName()));
        for (String name : names) if (RESERVED.contains(name) || extension.players() && ModelExtension.HELPER_TYPES.contains(name))
            throw new AdmissionException(List.of(EngineDiagnostic.unsupported(EngineDiagnostic.Stage.CAPABILITY,
                    "CSL_NATIVE_RESERVED_NAME", name, "Name collides with the explicitly selected host/M0 namespace")));
        if (extension.players()) for (var relation : source.relations()) if (Set.of("Plays", "InContext").contains(relation.name()))
            throw new AdmissionException(List.of(EngineDiagnostic.unsupported(EngineDiagnostic.Stage.CAPABILITY,
                    "CSL_NATIVE_RESERVED_ASSOCIATION", relation.name(), "Name collides with the explicit M0 extension")));
        try {
            api.getModel().addAnnotation(new MElementAnnotation(PROFILE, Map.of("profile", EngineSemanticsProfile.USE4GOAL_OBSERVED_V1.name(),
                    "version", source.version(), "players", Boolean.toString(extension.players()),
                    "compatibility", Boolean.toString(extension.enforceRoleCompatibility()),"unifiedOrgCtx",Boolean.toString(extension.unifiedOrgCtx()))));
            if (extension.players()) installHelpers(api,extension);
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
            var binding = new Binding(source, api.getModel(), extension, classifiers, owners, attributes, ends);
            if(extension.unifiedOrgCtx())CslContextSchema.installUnified(api,binding);
            preparation.prepare(api, binding);
            for (var invariant : source.invariants()) api.createInvariant(invariant.name(), invariant.contextType(), invariant.expression(), false);
            if (extension.enforceRoleCompatibility()) installCompatibility(api, source);
            return binding;
        } catch (UseApiException error) {
            throw new AdmissionException(List.of(EngineDiagnostic.error(EngineDiagnostic.Stage.BACKEND,
                    "CSL_NATIVE_BACKEND_REJECTED", source.name(), error.getMessage())), error);
        }
    }
    private static void installAttributes(UseModelApi api, String owner, List<CslAttribute> declarations,
            Map<String, Binding.Attribute> bindings) throws UseApiException {
        for (var attribute : declarations) {
            var nativeAttribute = api.createAttribute(owner, attribute.name(), attribute.type().sourceName());
            nativeAttribute.addAnnotation(new MElementAnnotation(ATTRIBUTE, Map.of("id64", encode(owner + "::" + attribute.name()),
                    "optional", Boolean.toString(attribute.optional()), "mutable", Boolean.toString(attribute.mutable()),
                    "hasDefault", Boolean.toString(attribute.defaultValue().isPresent()), "default64", encode(attribute.defaultValue().orElse("")))));
            // Authored defaults do not synthesize runtime facts or native init expressions.
            bindings.put(owner + "::" + attribute.name(), new Binding.Attribute(owner, attribute));
        }
    }
    private static void validateGeneralizationMetadata(CslModel source) {
        var declared = new HashSet<CslGeneralization>();
        source.entities().forEach(e -> e.specializes().ifPresent(p -> declared.add(new CslGeneralization(e.name(), p))));
        source.roles().forEach(r -> r.parentRoles().forEach(p -> declared.add(new CslGeneralization(r.name(), p))));
        source.groups().forEach(c -> c.specializes().ifPresent(p -> declared.add(new CslGeneralization(c.name(), p))));
        if (!declared.equals(new HashSet<>(source.generalizations())))
            throw new AdmissionException(List.of(EngineDiagnostic.error(EngineDiagnostic.Stage.MODEL_VALIDATION,
                    "CSL_NONCANONICAL_GENERALIZATION_METADATA", source.name(), "Classifier parents and generalization list disagree")));
    }
    private static void installHelpers(UseModelApi api,ModelExtension extension) throws UseApiException {
        for (String helper : ModelExtension.HELPER_TYPES.stream().sorted().toList()) {
            if(extension.unifiedOrgCtx()&&helper.equals("AtomicAgent"))continue;
            var cls = api.createClass(helper, !Set.of("AtomicAgent", "WorkspaceObservation").contains(helper));
            cls.addAnnotation(new MElementAnnotation(HELPER, Map.of("id64", encode("helper:" + helper), "kind", "M0_EXTENSION")));
        }
        if(!extension.unifiedOrgCtx())api.createGeneralization("AtomicAgent", "RuntimePlayer");
        api.createGeneralization("OrgContext", "RuntimePlayer");
        api.createAssociation("Plays",extension.unifiedOrgCtx()?"OrgContext":"RuntimePlayer", "player", "1", 0, "RoleOccurrence", "enactedRoles", "*", 0);
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

    /** Frozen schema/binding metadata; it neither creates nor owns a runtime MSystem. */
    public static final class Binding {
        public enum Kind { ENTITY, ROLE, ORG_CONTEXT }
        public record Classifier(String declaration, String symbol, Kind kind) { }
        public record Attribute(String declaration, CslAttribute specification) { }
        private final CslModel csl;
        private final MModel model;
        private final ModelExtension extension;
        private final Map<String, Classifier> classifiers;
        private final Map<String, String> owners;
        private final Map<String, Attribute> attributes;
        private final Map<String, List<String>> endRoles;

        Binding(CslModel csl, MModel model, ModelExtension extension,
                Map<String, Classifier> classifiers, Map<String, String> owners,
                Map<String, Attribute> attributes, Map<String, List<String>> endRoles) {
            this.csl = csl; this.model = model; this.extension = extension;
            this.classifiers = Collections.unmodifiableMap(new LinkedHashMap<>(classifiers));
            this.owners = Collections.unmodifiableMap(new LinkedHashMap<>(owners));
            this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
            var ends = new LinkedHashMap<String, List<String>>(); endRoles.forEach((k,v) -> ends.put(k, List.copyOf(v)));
            this.endRoles = Collections.unmodifiableMap(ends);
        }
        public CslModel cslModel() { return csl; }
        public MModel model() { return model; }
        public ModelExtension extension() { return extension; }
        public Map<String, Classifier> classifiers() { return classifiers; }
        public Map<String, String> declarationOwners() { return owners; }
        public Map<String, Attribute> attributes() { return attributes; }
        public Map<String, List<String>> associationEndRoles() { return endRoles; }
        public MClass classifier(String declaration) {
            var binding = classifiers.get(declaration);
            if (binding == null) throw new IllegalArgumentException("CSL_DECLARATION_UNRESOLVED:" + declaration);
            return model.getClass(binding.symbol());
        }
        /** Schema support and live writer/type-codec support are separate admission gates. */
        public List<EngineDiagnostic> liveDiagnostics(Set<String> exactCodecNames, boolean naryWriterAvailable) {
            var diagnostics = new ArrayList<EngineDiagnostic>();
            for (var attribute : attributes.values()) {
                String type = attribute.specification().type().sourceName();
                if (!exactCodecNames.contains(type)) diagnostics.add(EngineDiagnostic.unsupported(EngineDiagnostic.Stage.CAPABILITY, "EXACT_RUNTIME_CODEC_REQUIRED",
                        attribute.declaration() + "::" + attribute.specification().name(), type));
            }
            if (!naryWriterAvailable) for (var relation : csl.relations()) if (relation.endpoints().size() > 2)
                diagnostics.add(EngineDiagnostic.unsupported(EngineDiagnostic.Stage.CAPABILITY, "NARY_RUNTIME_WRITER_REQUIRED", relation.name(), "Schema is supported; live link writer must be verified separately"));
            return List.copyOf(diagnostics);
        }
        public void requireLiveCompatible(Set<String> exactCodecNames, boolean naryWriterAvailable) {
            var diagnostics = liveDiagnostics(exactCodecNames, naryWriterAvailable);
            if (!diagnostics.isEmpty()) throw new AdmissionException(diagnostics);
        }
    }

    /** Explicit neutral player/enactment extension. These helpers are not CSL M1 declarations. */
    public record ModelExtension(boolean players, boolean enforceRoleCompatibility, boolean unifiedOrgCtx) {
        public ModelExtension(boolean players,boolean enforceRoleCompatibility){this(players,enforceRoleCompatibility,false);}
        public static final Set<String> HELPER_TYPES = Set.of("RuntimePlayer", "AtomicAgent", "OrgContext",
                "RoleOccurrence", "DomainEntity", "WorkspaceObservation");
        public static ModelExtension schemaOnly() { return new ModelExtension(false, false); }
        public static ModelExtension observedPlayers() { return new ModelExtension(true, true); }
        /** Canonical players are OrgCtx instances for both individual agents and organizations. */
        public static ModelExtension unifiedContexts() { return new ModelExtension(true,true,true); }
        public ModelExtension {
            if (!players && enforceRoleCompatibility) throw new IllegalArgumentException("COMPATIBILITY_REQUIRES_PLAYER_CONTEXT_EXTENSION");
            if(!players&&unifiedOrgCtx)throw new IllegalArgumentException("UNIFIED_CONTEXT_REQUIRES_PLAYERS");
        }
    }
}
