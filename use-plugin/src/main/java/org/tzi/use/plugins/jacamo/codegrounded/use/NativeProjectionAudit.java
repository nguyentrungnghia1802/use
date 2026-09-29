package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/**
 * Evidence for why every native projection item is retained.
 *
 * <p>The audit is deliberately explicit and fail-closed.  A new native class or association
 * cannot silently enter AUTO: it must first be assigned a dependency category here and in the
 * projection tests.  Semantic concepts that are not materialized are represented by the
 * projection trace and are not returned by this audit.</p>
 */
public final class NativeProjectionAudit {
    private static final Set<String> OCL_CLASSES = Set.of(
            "AgentProgram", "PlanLibrary", "Plan", "Trigger", "PlanBodyElement", "Action", "Belief",
            "AgentGoal", "BeliefRule", "Guard", "Signal", "ObservablePropertySnapshot",
            "OrganizationalGoal", "OrganizationalPlan");
    private static final Set<String> RUNTIME_CLASSES = Set.of(
            "Agent", "Environment", "Workspace", "ArtifactType", "Artifact", "Operation", "CartagoAgentIdentity");
    private static final Set<String> DOMAIN_CLASSES = Set.of(
            "Organization", "StructuralSpecification", "FunctionalSpecification", "NormativeSpecification",
            "Group", "Role", "RoleRelation", "Link", "Compatibility", "Scheme", "Mission", "Norm",
            "GroupRoleCardinality", "SubGroupCardinality", "SchemeMissionCardinality");
    private static final Set<String> EVIDENCE_CLASSES = Set.of(
            "WorkspaceDeclaration", "ArtifactDeclaration", "OrganizationDeployment", "GroupDeployment",
            "SchemeDeployment", "InstitutionDeployment", "BackingJavaOperation", "ArtifactInfo",
            "ExactBindingEvidence", "LiveObservableProperty", "A17PlanOrderEntry", "A19BodyOrderEntry");

    public Audit audit(NativeUseModelBuilder.Result schema, MSystem system) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(system, "system");
        List<Item> classes = schema.model().classes().stream()
                .sorted(Comparator.comparing(MClass::name))
                .map(value -> new Item("class", value.name(), classReason(value.name(), schema),
                        schema.profile().statusFor(value.name()).name()))
                .toList();
        List<Item> associations = schema.model().associations().stream()
                .sorted(Comparator.comparing(MAssociation::name))
                .map(value -> new Item("association", value.name(), associationReason(value.name()), "MATERIALIZED"))
                .toList();
        List<Item> objects = system.state().allObjects().stream()
                .sorted(Comparator.comparing(MObject::name))
                .map(value -> new Item("object", value.name(), classReason(value.cls().name(), schema), "MATERIALIZED"))
                .toList();
        Map<String, Long> objectCounts = new TreeMap<>();
        system.state().allObjects().forEach(value -> objectCounts.merge(value.cls().name(), 1L, Long::sum));
        return new Audit(schema.profile().mode(), classes, associations, objects,
                Map.copyOf(objectCounts));
    }

    private static String classReason(String name, NativeUseModelBuilder.Result schema) {
        if (schema.nativeArtifactTypeClassNames().containsValue(name)) return "RUNTIME_MUTATION_TARGET:artifact-subtype";
        if (OCL_CLASSES.contains(name)) return "OCL_TARGET:code-grounded-constraint";
        if (RUNTIME_CLASSES.contains(name)) return "RUNTIME_MUTATION_TARGET:faithful-event";
        if (DOMAIN_CLASSES.contains(name)) return "DOMAIN_RELATION:current-supported-semantics";
        if (EVIDENCE_CLASSES.contains(name)) return "TRACE_EVIDENCE_OR_PROFILE_BOUNDARY:" + name;
        throw new IllegalStateException("NATIVE_PROJECTION_AUDIT_CLASS_REASON_MISSING:" + name);
    }

    private static String associationReason(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("NATIVE_PROJECTION_ASSOCIATION_NAME_REQUIRED");
        if (name.startsWith("A")) return "OCL_OR_ORDER_RELATION:" + name;
        if (name.startsWith("C")) return "RUNTIME_RELATION:" + name;
        if (name.startsWith("M")) return "DOMAIN_RELATION:" + name;
        if (name.startsWith("X")) return "EXACT_CROSS_DIMENSIONAL_RELATION:" + name;
        throw new IllegalStateException("NATIVE_PROJECTION_AUDIT_ASSOCIATION_REASON_MISSING:" + name);
    }

    public record Item(String kind, String identity, String reason, String status) {
        public Item {
            kind = require(kind, "kind");
            identity = require(identity, "identity");
            reason = require(reason, "reason");
            status = require(status, "status");
        }
        private static String require(String value, String label) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "_REQUIRED");
            return value;
        }
    }

    public record Audit(NativeProjectionMode mode, List<Item> classes, List<Item> associations,
                        List<Item> objects, Map<String, Long> objectCountsByClass) {
        public Audit {
            mode = Objects.requireNonNull(mode, "mode");
            classes = List.copyOf(classes);
            associations = List.copyOf(associations);
            objects = List.copyOf(objects);
            objectCountsByClass = Map.copyOf(new LinkedHashMap<>(objectCountsByClass));
        }

        public long classCount() { return classes.size(); }
        public long associationCount() { return associations.size(); }
        public long objectCount() { return objects.size(); }

        public String summary() {
            return "mode=" + mode + " classes=" + classCount() + " associations=" + associationCount()
                    + " objects=" + objectCount() + " objectClasses=" + objectCountsByClass.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));
        }
    }
}
