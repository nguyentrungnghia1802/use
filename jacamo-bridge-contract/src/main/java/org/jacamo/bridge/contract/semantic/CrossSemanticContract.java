package org.jacamo.bridge.contract.semantic;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Exact cross-dimension evidence; same-name candidates are never bindings. */
public final class CrossSemanticContract {
    private CrossSemanticContract() { }

    public record ExactBindingSemantic(SemanticMetadata metadata, String ruleId,
            List<String> sourceIds, List<String> targetIds, Map<String,String> context) {
        public ExactBindingSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            ruleId = SemanticSupport.required(ruleId, "ruleId");
            if (!ruleId.matches("X0[1-9]"))
                throw new IllegalArgumentException("CROSS_RULE_ID_REQUIRED: " + ruleId);
            sourceIds = nonEmptyIds(sourceIds, "sourceIds");
            targetIds = nonEmptyIds(targetIds, "targetIds");
            context = SemanticSupport.stringMap(context);
        }

        private static List<String> nonEmptyIds(List<String> values, String field) {
            if (values == null || values.isEmpty()) throw new IllegalArgumentException(field + " is required");
            return values.stream().map(value -> SemanticSupport.required(value, field + " item")).toList();
        }
    }

    /** X01: an Action is bound to an Operation only by dispatch/binding evidence. */
    public record ActionOperationBindingSemantic(SemanticMetadata metadata, String actionSemanticId,
            String operationSemanticId, String dispatchEvidence, Map<String,String> context) {
        public ActionOperationBindingSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            actionSemanticId = SemanticSupport.required(actionSemanticId, "actionSemanticId");
            operationSemanticId = SemanticSupport.required(operationSemanticId, "operationSemanticId");
            dispatchEvidence = SemanticSupport.required(dispatchEvidence, "dispatchEvidence");
            context = with(context, "dispatchEvidence", dispatchEvidence);
        }
        public ExactBindingSemantic toExactBinding() {
            return binding(metadata, "X01", actionSemanticId, operationSemanticId, context);
        }
    }

    /** X02: a Belief is bound to a property only by exact percept/property provenance. */
    public record BeliefObservablePropertyBindingSemantic(SemanticMetadata metadata, String beliefSemanticId,
            String observablePropertySemanticId, String provenanceEvidence, Map<String,String> context) {
        public BeliefObservablePropertyBindingSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            beliefSemanticId = SemanticSupport.required(beliefSemanticId, "beliefSemanticId");
            observablePropertySemanticId = SemanticSupport.required(observablePropertySemanticId,
                    "observablePropertySemanticId");
            provenanceEvidence = SemanticSupport.required(provenanceEvidence, "provenanceEvidence");
            context = with(context, "provenanceEvidence", provenanceEvidence);
        }
        public ExactBindingSemantic toExactBinding() {
            return binding(metadata, "X02", beliefSemanticId, observablePropertySemanticId, context);
        }
    }

    /** X03: a Trigger is bound to a Signal only by exact signal/percept provenance. */
    public record TriggerSignalBindingSemantic(SemanticMetadata metadata, String triggerSemanticId,
            String signalSemanticId, String provenanceEvidence, Map<String,String> context) {
        public TriggerSignalBindingSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            triggerSemanticId = SemanticSupport.required(triggerSemanticId, "triggerSemanticId");
            signalSemanticId = SemanticSupport.required(signalSemanticId, "signalSemanticId");
            provenanceEvidence = SemanticSupport.required(provenanceEvidence, "provenanceEvidence");
            context = with(context, "provenanceEvidence", provenanceEvidence);
        }
        public ExactBindingSemantic toExactBinding() {
            return binding(metadata, "X03", triggerSemanticId, signalSemanticId, context);
        }
    }

    /** X04: an Agent is bound to a Moise Role with organization/group context retained. */
    public record AgentRoleBindingSemantic(SemanticMetadata metadata, String agentSemanticId,
            String roleSemanticId, String organizationSemanticId, String groupSemanticId,
            String evidenceKind, Map<String,String> context) {
        public AgentRoleBindingSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            agentSemanticId = SemanticSupport.required(agentSemanticId, "agentSemanticId");
            roleSemanticId = SemanticSupport.required(roleSemanticId, "roleSemanticId");
            organizationSemanticId = SemanticSupport.required(organizationSemanticId, "organizationSemanticId");
            groupSemanticId = SemanticSupport.required(groupSemanticId, "groupSemanticId");
            evidenceKind = SemanticSupport.required(evidenceKind, "evidenceKind");
            context = with(with(with(context, "organizationSemanticId", organizationSemanticId),
                    "groupSemanticId", groupSemanticId), "evidenceKind", evidenceKind);
        }
        public ExactBindingSemantic toExactBinding() {
            return binding(metadata, "X04", agentSemanticId, roleSemanticId, context);
        }
    }

    /** X05: an Agent is bound to a Workspace by membership/runtime evidence. */
    public record AgentWorkspaceBindingSemantic(SemanticMetadata metadata, String agentSemanticId,
            String workspaceSemanticId, String evidenceKind, Map<String,String> context) {
        public AgentWorkspaceBindingSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            agentSemanticId = SemanticSupport.required(agentSemanticId, "agentSemanticId");
            workspaceSemanticId = SemanticSupport.required(workspaceSemanticId, "workspaceSemanticId");
            evidenceKind = SemanticSupport.required(evidenceKind, "evidenceKind");
            context = with(context, "evidenceKind", evidenceKind);
        }
        public ExactBindingSemantic toExactBinding() {
            return binding(metadata, "X05", agentSemanticId, workspaceSemanticId, context);
        }
    }

    /** X06: an Agent is bound to an Artifact only by focus evidence. */
    public record AgentArtifactFocusBindingSemantic(SemanticMetadata metadata, String agentSemanticId,
            String artifactSemanticId, String focusEvidence, Map<String,String> context) {
        public AgentArtifactFocusBindingSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            agentSemanticId = SemanticSupport.required(agentSemanticId, "agentSemanticId");
            artifactSemanticId = SemanticSupport.required(artifactSemanticId, "artifactSemanticId");
            focusEvidence = SemanticSupport.required(focusEvidence, "focusEvidence");
            context = with(context, "focusEvidence", focusEvidence);
        }
        public ExactBindingSemantic toExactBinding() {
            return binding(metadata, "X06", agentSemanticId, artifactSemanticId, context);
        }
    }

    /** X07: an AgentGoal is bound to an organizational goal only by explicit evidence. */
    public record AgentGoalOrganizationalGoalBindingSemantic(SemanticMetadata metadata,
            String agentGoalSemanticId, String organizationalGoalSemanticId, String evidenceKind,
            Map<String,String> context) {
        public AgentGoalOrganizationalGoalBindingSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            agentGoalSemanticId = SemanticSupport.required(agentGoalSemanticId, "agentGoalSemanticId");
            organizationalGoalSemanticId = SemanticSupport.required(organizationalGoalSemanticId,
                    "organizationalGoalSemanticId");
            evidenceKind = SemanticSupport.required(evidenceKind, "evidenceKind");
            context = with(context, "evidenceKind", evidenceKind);
        }
        public ExactBindingSemantic toExactBinding() {
            return binding(metadata, "X07", agentGoalSemanticId, organizationalGoalSemanticId, context);
        }
    }

    /** X08: a declaration is bound to a runtime artifact by creation correlation. */
    public record ArtifactDeclarationBindingSemantic(SemanticMetadata metadata,
            String artifactDeclarationSemanticId, String artifactSemanticId, String creationEvidence,
            Map<String,String> context) {
        public ArtifactDeclarationBindingSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            artifactDeclarationSemanticId = SemanticSupport.required(artifactDeclarationSemanticId,
                    "artifactDeclarationSemanticId");
            artifactSemanticId = SemanticSupport.required(artifactSemanticId, "artifactSemanticId");
            creationEvidence = SemanticSupport.required(creationEvidence, "creationEvidence");
            context = with(context, "creationEvidence", creationEvidence);
        }
        public ExactBindingSemantic toExactBinding() {
            return binding(metadata, "X08", artifactDeclarationSemanticId, artifactSemanticId, context);
        }
    }

    /** X09: a declared agent is aliased to one observed CArtAgO identity/incarnation. */
    public record AgentIdentityBindingSemantic(SemanticMetadata metadata, String agentSemanticId,
            String cartagoAgentIdentitySemanticId, String incarnationId, String observationKind,
            Map<String,String> context) {
        public AgentIdentityBindingSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            agentSemanticId = SemanticSupport.required(agentSemanticId, "agentSemanticId");
            cartagoAgentIdentitySemanticId = SemanticSupport.required(cartagoAgentIdentitySemanticId,
                    "cartagoAgentIdentitySemanticId");
            incarnationId = SemanticSupport.required(incarnationId, "incarnationId");
            observationKind = SemanticSupport.required(observationKind, "observationKind");
            context = with(with(context, "incarnationId", incarnationId), "observationKind", observationKind);
        }
        public ExactBindingSemantic toExactBinding() {
            return binding(metadata, "X09", agentSemanticId, cartagoAgentIdentitySemanticId, context);
        }
    }

    private static ExactBindingSemantic binding(SemanticMetadata metadata, String ruleId,
                                                String sourceId, String targetId, Map<String,String> context) {
        return new ExactBindingSemantic(metadata, ruleId, List.of(sourceId), List.of(targetId), context);
    }

    private static Map<String,String> with(Map<String,String> context, String key, String value) {
        Map<String,String> copy = new TreeMap<>();
        if (context != null) copy.putAll(context);
        copy.put(key, SemanticSupport.required(value, key));
        return copy;
    }
}
