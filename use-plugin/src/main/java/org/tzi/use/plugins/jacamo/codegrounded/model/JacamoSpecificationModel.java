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

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
