package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;

/**
 * Immutable snapshot of physical facts observed at a runtime cut.
 * Represents admitted facts from CArtAgO, Jason, and Moise collectors without legacy procedural tables.
 */
public record PhysicalCutFact(
        String cutId,
        long sequence,
        List<ArtifactInstance> artifacts,
        List<AgentInstance> agents,
        List<MoiseInstance> moiseInstances,
        List<StepReceipt> stepReceipts,
        List<String> disposedObjects) {

    public PhysicalCutFact(
            String cutId,
            long sequence,
            List<ArtifactInstance> artifacts,
            List<AgentInstance> agents,
            List<MoiseInstance> moiseInstances,
            List<StepReceipt> stepReceipts) {
        this(cutId, sequence, artifacts, agents, moiseInstances, stepReceipts, List.of());
    }

    public record ArtifactInstance(
            String name,
            String className,
            String workspace,
            String runId,
            String incarnation,
            Map<String, Object> properties,
            Map<String, Object> fields) {

        public ArtifactInstance(
                String name,
                String className,
                String workspace,
                Map<String, Object> properties,
                Map<String, Object> fields) {
            this(name, className, workspace, "", "", properties, fields);
        }

        public ArtifactInstance {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(className, "className");
            runId = runId == null ? "" : runId.trim();
            incarnation = incarnation == null ? "" : incarnation.trim();
            properties = Map.copyOf(properties == null ? Map.of() : properties);
            fields = Map.copyOf(fields == null ? Map.of() : fields);
        }
    }

    public record AgentInstance(
            String name,
            String aslSource,
            Set<String> roles,
            Set<String> activeGoals,
            Map<String, Object> beliefs) {

        public AgentInstance(
                String name,
                Set<String> roles,
                Set<String> activeGoals,
                Map<String, Object> beliefs) {
            this(name, "", roles, activeGoals, beliefs);
        }

        public AgentInstance {
            Objects.requireNonNull(name, "name");
            aslSource = aslSource == null ? "" : aslSource.trim();
            roles = Set.copyOf(roles == null ? Set.of() : roles);
            activeGoals = Set.copyOf(activeGoals == null ? Set.of() : activeGoals);
            beliefs = Map.copyOf(beliefs == null ? Map.of() : beliefs);
        }
    }

    public record MoiseInstance(
            String orgName,
            String groupName,
            String schemeName,
            Map<String, String> rolePlayers,
            Map<String, String> goalStates) {

        public MoiseInstance {
            Objects.requireNonNull(orgName, "orgName");
            rolePlayers = Map.copyOf(rolePlayers == null ? Map.of() : rolePlayers);
            goalStates = Map.copyOf(goalStates == null ? Map.of() : goalStates);
        }
    }

    public record StepReceipt(
            String planTrigger,
            String stepName,
            String taskName,
            int stepIndex,
            String status,
            String agentName,
            String workspace,
            String schemeName,
            String invocationId) {

        public StepReceipt(
                String planTrigger,
                String stepName,
                String taskName,
                int stepIndex,
                String status) {
            this(planTrigger, stepName, taskName, stepIndex, status, "", "", "", "");
        }

        public StepReceipt {
            Objects.requireNonNull(taskName, "taskName");
            status = (status == null || status.isBlank()) ? "UNKNOWN" : status.trim();
            agentName = agentName == null ? "" : agentName.trim();
            workspace = workspace == null ? "" : workspace.trim();
            schemeName = schemeName == null ? "" : schemeName.trim();
            invocationId = invocationId == null ? "" : invocationId.trim();
        }
    }

    public PhysicalCutFact {
        Objects.requireNonNull(cutId, "cutId");
        artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
        agents = List.copyOf(agents == null ? List.of() : agents);
        moiseInstances = List.copyOf(moiseInstances == null ? List.of() : moiseInstances);
        stepReceipts = List.copyOf(stepReceipts == null ? List.of() : stepReceipts);
        disposedObjects = List.copyOf(disposedObjects == null ? List.of() : disposedObjects);
    }

    public List<ArtifactInstance> findArtifactsByClass(String className) {
        return artifacts.stream()
                .filter(a -> a.className().equals(className) || a.className().endsWith("." + className))
                .toList();
    }

    public Optional<ArtifactInstance> findArtifactByClass(String className) {
        return findArtifactsByClass(className).stream().findFirst();
    }

    public Optional<ArtifactInstance> findArtifactByName(String name) {
        return artifacts.stream().filter(a -> a.name().equals(name)).findFirst();
    }
}

