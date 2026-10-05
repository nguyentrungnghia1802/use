package org.tzi.use.plugins.jacamo.codegrounded.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract;
import org.jacamo.bridge.contract.semantic.SemanticContractCodec;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;

/** Immutable code-grounded source model. It contains contract DTOs, never USE or live JaCaMo objects. */
public final class JacamoSpecificationModel {
    private final JacamoSemanticSnapshot snapshot;
    private final List<JasonSemanticContract.AgentProgramSemantic> programs;
    private final String revision;

    public JacamoSpecificationModel(JacamoSemanticSnapshot snapshot) {
        this.snapshot = java.util.Objects.requireNonNull(snapshot, "snapshot");
        if (!JacamoSemanticSnapshot.CURRENT_VERSION.equals(snapshot.contractVersion()))
            throw new IllegalArgumentException("SEMANTIC_CONTRACT_VERSION_UNSUPPORTED: " + snapshot.contractVersion());
        if (snapshot.project() == null) throw new IllegalArgumentException("J01_PROJECT_REQUIRED");
        this.programs = snapshot.jasonPrograms().stream()
                .sorted(Comparator.comparing(value -> value.metadata().semanticId())).toList();
        validateUniqueSemanticIdentities();
        validateReferences();
        this.revision = sha256(SemanticContractCodec.encode(snapshot));
    }

    public JacamoSemanticSnapshot snapshot() { return snapshot; }
    public org.jacamo.bridge.contract.semantic.JcmSemanticContract.ProjectSemantic project() { return snapshot.project(); }
    public List<JasonSemanticContract.AgentProgramSemantic> programs() { return programs; }
    public String revision() { return revision; }

    private void validateUniqueSemanticIdentities() {
        Set<String> identities = new HashSet<>();
        register(identities, snapshot.project().metadata());
        snapshot.agentDeclarations().forEach(value -> register(identities, value.metadata()));
        snapshot.workspaceDeclarations().forEach(value -> register(identities, value.metadata()));
        snapshot.artifactDeclarations().forEach(value -> register(identities, value.metadata()));
        snapshot.organizationDeployments().forEach(value -> register(identities, value.metadata()));
        snapshot.groupDeployments().forEach(value -> register(identities, value.metadata()));
        snapshot.schemeDeployments().forEach(value -> register(identities, value.metadata()));
        snapshot.institutionDeployments().forEach(value -> register(identities, value.metadata()));
        snapshot.rawRoleTuples().forEach(value -> register(identities, value.metadata()));
        snapshot.rawFocusTuples().forEach(value -> register(identities, value.metadata()));
        snapshot.exactBindings().forEach(value -> {
            register(identities, value.metadata());
            if (!value.ruleId().matches("X0[1-9]"))
                throw new IllegalArgumentException("CROSS_RULE_ID_UNSUPPORTED: " + value.ruleId());
            if (value.sourceIds().isEmpty() || value.targetIds().isEmpty())
                throw new IllegalArgumentException("CROSS_BINDING_ENDPOINTS_REQUIRED: " + value.metadata().semanticId());
        });
        snapshot.importProvenance().forEach(value -> register(identities, value.metadata()));
        for (var environment : snapshot.cartagoEnvironments()) {
            register(identities, environment.metadata());
            environment.workspaces().forEach(value -> register(identities, value.metadata()));
            environment.artifactTypes().forEach(value -> register(identities, value.metadata()));
            environment.artifacts().forEach(value -> register(identities, value.metadata()));
            environment.operations().forEach(value -> register(identities, value.metadata()));
            environment.backingOperations().forEach(value -> register(identities, value.metadata()));
            environment.guards().forEach(value -> register(identities, value.metadata()));
            environment.liveProperties().forEach(value -> register(identities, value.metadata()));
            environment.propertySnapshots().forEach(value -> register(identities, value.metadata()));
            environment.artifactInfos().forEach(value -> register(identities, value.metadata()));
            environment.signals().forEach(value -> register(identities, value.metadata()));
            environment.agents().forEach(value -> register(identities, value.metadata()));
            environment.focuses().forEach(value -> register(identities, value.metadata()));
        }
        for (var organization : snapshot.moiseOrganizations()) {
            register(identities, organization.metadata());
            var structural = organization.structuralSpecification();
            register(identities, structural.metadata());
            structural.roles().forEach(value -> register(identities, value.metadata()));
            structural.groups().forEach(value -> register(identities, value.metadata()));
            structural.roleRelations().forEach(value -> register(identities, value.metadata()));
            structural.links().forEach(value -> register(identities, value.metadata()));
            structural.compatibilities().forEach(value -> register(identities, value.metadata()));
            structural.groupRoleCardinalities().forEach(value -> register(identities, value.metadata()));
            structural.subGroupCardinalities().forEach(value -> register(identities, value.metadata()));
            var functional = organization.functionalSpecification();
            register(identities, functional.metadata());
            functional.schemeMissionCardinalities().forEach(value -> register(identities, value.metadata()));
            functional.schemes().forEach(scheme -> {
                register(identities, scheme.metadata());
                scheme.missions().forEach(value -> register(identities, value.metadata()));
                scheme.goals().forEach(value -> register(identities, value.metadata()));
                scheme.plans().forEach(value -> register(identities, value.metadata()));
            });
            var normative = organization.normativeSpecification();
            register(identities, normative.metadata());
            normative.norms().forEach(value -> register(identities, value.metadata()));
        }
        for (var program : programs) {
            register(identities, program.metadata());
            register(identities, program.planLibrary().metadata());
            for (var plan : program.planLibrary().plans()) {
                register(identities, plan.metadata());
                if (plan.trigger() != null) register(identities, plan.trigger().metadata());
                plan.body().forEach(value -> register(identities, value.metadata()));
            }
            program.actions().forEach(value -> register(identities, value.metadata()));
            program.beliefs().forEach(value -> register(identities, value.metadata()));
            program.goals().forEach(value -> register(identities, value.metadata()));
            program.beliefRules().forEach(value -> register(identities, value.metadata()));
        }
    }

    private static void register(Set<String> identities, SemanticMetadata metadata) {
        if (metadata == null || !identities.add(metadata.semanticId()))
            throw new IllegalArgumentException("DUPLICATE_SEMANTIC_IDENTITY: "
                    + (metadata == null ? "<null>" : metadata.semanticId()));
    }
    /** Validate retained source references even when the corresponding concept has no USE class. */
    private void validateReferences() {
        java.util.Map<String,Set<String>> categories=new java.util.HashMap<>();
        categories.put("agent",ids(snapshot.agentDeclarations(),v -> v.metadata()));
        categories.put("artifact-declaration",ids(snapshot.artifactDeclarations(),v -> v.metadata()));
        for(var program:programs) {
            add(categories,"action",ids(program.actions(),v -> v.metadata()));
            add(categories,"belief",ids(program.beliefs(),v -> v.metadata()));
            add(categories,"agent-goal",ids(program.goals(),v -> v.metadata()));
            for(var plan:program.planLibrary().plans()) if(plan.trigger()!=null)
                add(categories,"trigger",Set.of(plan.trigger().metadata().semanticId()));
        }
        for(var org:snapshot.moiseOrganizations()) {
            add(categories,"role",ids(org.structuralSpecification().roles(),v -> v.metadata()));
            for(var scheme:org.functionalSpecification().schemes()) add(categories,"organisational-goal",ids(scheme.goals(),v -> v.metadata()));
        }
        for(var env:snapshot.cartagoEnvironments()) {
            var types=ids(env.artifactTypes(),v -> v.metadata()); var workspaces=ids(env.workspaces(),v -> v.metadata());
            var artifacts=ids(env.artifacts(),v -> v.metadata());
            for(var artifact:env.artifacts()) {
                if(!types.contains(artifact.artifactTypeSemanticId())) throw new IllegalArgumentException("C15_TYPE_REFERENCE:"+artifact.artifactTypeSemanticId());
                if(!workspaces.contains(artifact.workspaceSemanticId())) throw new IllegalArgumentException("C14_WORKSPACE_REFERENCE:"+artifact.workspaceSemanticId());
            }
            for(var property:env.propertySnapshots()) if(!artifacts.contains(property.artifactSemanticId()))
                throw new IllegalArgumentException("C09_PROPERTY_OWNER_REFERENCE:"+property.artifactSemanticId());
            add(categories,"workspace",workspaces); add(categories,"artifact",artifacts);
            add(categories,"operation",ids(env.operations(),v -> v.metadata()));
            add(categories,"property",ids(env.propertySnapshots(),v -> v.metadata()));
            add(categories,"signal",ids(env.signals(),v -> v.metadata()));
            add(categories,"cartago-agent",ids(env.agents(),v -> v.metadata()));
        }
        java.util.Map<String,List<String>> endpoints=java.util.Map.of("X01",List.of("action","operation"),"X02",List.of("belief","property"),
            "X03",List.of("trigger","signal"),"X04",List.of("agent","role"),"X05",List.of("agent","workspace"),"X06",List.of("agent","artifact"),
            "X07",List.of("agent-goal","organisational-goal"),"X08",List.of("artifact-declaration","artifact"),"X09",List.of("agent","cartago-agent"));
        for(var binding:snapshot.exactBindings()) {
            var expected=endpoints.get(binding.ruleId());
            if(!categories.getOrDefault(expected.get(0),Set.of()).containsAll(binding.sourceIds()))
                throw new IllegalArgumentException("CROSS_SOURCE_CLASS_MISMATCH_"+binding.ruleId());
            if(!categories.getOrDefault(expected.get(1),Set.of()).containsAll(binding.targetIds()))
                throw new IllegalArgumentException("CROSS_TARGET_CLASS_MISMATCH_"+binding.ruleId());
        }
    }
    private static <T> Set<String> ids(List<T> values,Function<T,SemanticMetadata> metadata) {
        return values.stream().map(v -> metadata.apply(v).semanticId()).collect(java.util.stream.Collectors.toSet());
    }
    private static void add(java.util.Map<String,Set<String>> categories,String kind,Set<String> ids) {
        categories.computeIfAbsent(kind,k -> new HashSet<>()).addAll(ids);
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
