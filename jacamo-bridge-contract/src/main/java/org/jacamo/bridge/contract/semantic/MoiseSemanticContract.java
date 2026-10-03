package org.jacamo.bridge.contract.semantic;

import java.util.List;

/**
 * Typed Moise contract surface copied from the official {@code moise.os} object graph.
 *
 * <p>The three specifications are explicit containers. Cardinality records keep both
 * endpoints because the official API scopes cardinality to a pair, not to a Role,
 * Group, or Mission in isolation. Descriptive roleId/groupId/schemeId/missionId/goalId/normId
 * fields are the official local ids. All references and deduplication use metadata.semanticId
 * (and the explicitly named semantic-id fields), never those display/local ids.</p>
 */
public final class MoiseSemanticContract {
    private MoiseSemanticContract() { }

    public record OrganizationSemantic(SemanticMetadata metadata, String name, String sourceUri,
            StructuralSpecificationSemantic structuralSpecification,
            FunctionalSpecificationSemantic functionalSpecification,
            NormativeSpecificationSemantic normativeSpecification) {
        public OrganizationSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            name = SemanticSupport.required(name, "name");
            sourceUri = SemanticSupport.text(sourceUri);
            structuralSpecification = SemanticSupport.required(structuralSpecification, "structuralSpecification");
            functionalSpecification = SemanticSupport.required(functionalSpecification, "functionalSpecification");
            normativeSpecification = SemanticSupport.required(normativeSpecification, "normativeSpecification");
        }
    }

    public record StructuralSpecificationSemantic(SemanticMetadata metadata, String organizationSemanticId,
            String specificationId, String rootGroupSemanticId, List<RoleSemantic> roles, List<GroupSemantic> groups,
            List<RoleRelationSemantic> roleRelations, List<LinkSemantic> links,
            List<CompatibilitySemantic> compatibilities,
            List<GroupRoleCardinalitySemantic> groupRoleCardinalities,
            List<SubGroupCardinalitySemantic> subGroupCardinalities) {
        public StructuralSpecificationSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            organizationSemanticId = SemanticSupport.required(organizationSemanticId, "organizationSemanticId");
            specificationId = SemanticSupport.required(specificationId, "specificationId");
            rootGroupSemanticId = SemanticSupport.text(rootGroupSemanticId);
            roles = List.copyOf(roles == null ? List.of() : roles);
            groups = List.copyOf(groups == null ? List.of() : groups);
            roleRelations = List.copyOf(roleRelations == null ? List.of() : roleRelations);
            links = List.copyOf(links == null ? List.of() : links);
            compatibilities = List.copyOf(compatibilities == null ? List.of() : compatibilities);
            groupRoleCardinalities = List.copyOf(groupRoleCardinalities == null ? List.of() : groupRoleCardinalities);
            subGroupCardinalities = List.copyOf(subGroupCardinalities == null ? List.of() : subGroupCardinalities);
        }
    }

    public record FunctionalSpecificationSemantic(SemanticMetadata metadata, String organizationSemanticId,
            String specificationId, List<SchemeSemantic> schemes,
            List<SchemeMissionCardinalitySemantic> schemeMissionCardinalities) {
        public FunctionalSpecificationSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            organizationSemanticId = SemanticSupport.required(organizationSemanticId, "organizationSemanticId");
            specificationId = SemanticSupport.required(specificationId, "specificationId");
            schemes = List.copyOf(schemes == null ? List.of() : schemes);
            schemeMissionCardinalities = List.copyOf(schemeMissionCardinalities == null
                    ? List.of() : schemeMissionCardinalities);
        }
    }

    public record NormativeSpecificationSemantic(SemanticMetadata metadata, String organizationSemanticId,
            String specificationId, List<NormSemantic> norms) {
        public NormativeSpecificationSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            organizationSemanticId = SemanticSupport.required(organizationSemanticId, "organizationSemanticId");
            specificationId = SemanticSupport.required(specificationId, "specificationId");
            norms = List.copyOf(norms == null ? List.of() : norms);
        }
    }

    public record GroupSemantic(SemanticMetadata metadata, String groupId, String parentGroupSemanticId,
            List<String> roleSemanticIds, List<String> subgroupSemanticIds) {
        public GroupSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            groupId = SemanticSupport.required(groupId, "groupId");
            parentGroupSemanticId = SemanticSupport.text(parentGroupSemanticId);
            roleSemanticIds = List.copyOf(roleSemanticIds == null ? List.of() : roleSemanticIds);
            subgroupSemanticIds = List.copyOf(subgroupSemanticIds == null ? List.of() : subgroupSemanticIds);
        }
    }

    public record RoleSemantic(SemanticMetadata metadata, String roleId, boolean abstractRole,
            List<String> superRoleSemanticIds) {
        public RoleSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            roleId = SemanticSupport.required(roleId, "roleId");
            superRoleSemanticIds = List.copyOf(superRoleSemanticIds == null ? List.of() : superRoleSemanticIds);
        }
    }

    /** Common RoleRel fields. Link and Compatibility retain their own concrete DTOs. */
    public record RoleRelationSemantic(SemanticMetadata metadata, String relationId, String relationKind,
            String groupSemanticId, String sourceRoleSemanticId, String targetRoleSemanticId,
            String scope, boolean extendsToSubGroups, boolean bidirectional) {
        public RoleRelationSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            relationId = SemanticSupport.required(relationId, "relationId");
            relationKind = SemanticSupport.required(relationKind, "relationKind");
            groupSemanticId = SemanticSupport.required(groupSemanticId, "groupSemanticId");
            sourceRoleSemanticId = SemanticSupport.text(sourceRoleSemanticId);
            targetRoleSemanticId = SemanticSupport.text(targetRoleSemanticId);
            scope = SemanticSupport.text(scope);
        }
    }

    public record LinkSemantic(SemanticMetadata metadata, String roleRelationSemanticId, String linkType) {
        public LinkSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            roleRelationSemanticId = SemanticSupport.required(roleRelationSemanticId, "roleRelationSemanticId");
            linkType = SemanticSupport.required(linkType, "linkType");
        }
    }

    public record CompatibilitySemantic(SemanticMetadata metadata, String roleRelationSemanticId) {
        public CompatibilitySemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            roleRelationSemanticId = SemanticSupport.required(roleRelationSemanticId, "roleRelationSemanticId");
        }
    }

    public record SchemeSemantic(SemanticMetadata metadata, String schemeId,
            String functionalSpecificationSemanticId, String rootGoalSemanticId,
            List<MissionSemantic> missions, List<OrganizationalGoalSemantic> goals,
            List<OrganizationalPlanSemantic> plans) {
        public SchemeSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            schemeId = SemanticSupport.required(schemeId, "schemeId");
            functionalSpecificationSemanticId = SemanticSupport.required(functionalSpecificationSemanticId,
                    "functionalSpecificationSemanticId");
            rootGoalSemanticId = SemanticSupport.text(rootGoalSemanticId);
            missions = List.copyOf(missions == null ? List.of() : missions);
            goals = List.copyOf(goals == null ? List.of() : goals);
            plans = List.copyOf(plans == null ? List.of() : plans);
        }
    }

    public record MissionSemantic(SemanticMetadata metadata, String missionId, String schemeSemanticId,
            List<String> goalSemanticIds) {
        public MissionSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            missionId = SemanticSupport.required(missionId, "missionId");
            schemeSemanticId = SemanticSupport.required(schemeSemanticId, "schemeSemanticId");
            goalSemanticIds = List.copyOf(goalSemanticIds == null ? List.of() : goalSemanticIds);
        }
    }

    public record OrganizationalGoalSemantic(SemanticMetadata metadata, String goalId, String schemeSemanticId,
            String goalType, String description, String arguments, int minAgentsToSatisfy, String ttf,
            String location, List<String> dependencySemanticIds, String planSemanticId,
            String inPlanSemanticId) {
        public OrganizationalGoalSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            goalId = SemanticSupport.required(goalId, "goalId");
            schemeSemanticId = SemanticSupport.required(schemeSemanticId, "schemeSemanticId");
            goalType = SemanticSupport.text(goalType);
            description = SemanticSupport.text(description);
            arguments = SemanticSupport.text(arguments);
            if (minAgentsToSatisfy < -1) throw new IllegalArgumentException("minAgentsToSatisfy");
            ttf = SemanticSupport.text(ttf);
            location = SemanticSupport.text(location);
            dependencySemanticIds = List.copyOf(dependencySemanticIds == null ? List.of() : dependencySemanticIds);
            planSemanticId = SemanticSupport.text(planSemanticId);
            inPlanSemanticId = SemanticSupport.text(inPlanSemanticId);
        }
    }

    public record OrganizationalPlanSemantic(SemanticMetadata metadata, String planId, String schemeSemanticId,
            String targetGoalSemanticId, String operator, double successRate,
            List<String> orderedSubGoalSemanticIds) {
        public OrganizationalPlanSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            planId = SemanticSupport.required(planId, "planId");
            schemeSemanticId = SemanticSupport.required(schemeSemanticId, "schemeSemanticId");
            targetGoalSemanticId = SemanticSupport.required(targetGoalSemanticId, "targetGoalSemanticId");
            operator = SemanticSupport.required(operator, "operator");
            if (!Double.isFinite(successRate)) throw new IllegalArgumentException("successRate");
            orderedSubGoalSemanticIds = List.copyOf(orderedSubGoalSemanticIds == null
                    ? List.of() : orderedSubGoalSemanticIds);
        }
    }

    public record NormSemantic(SemanticMetadata metadata, String normId, String normativeSpecificationSemanticId,
            String roleSemanticId, String missionSemanticId, String operationType, String condition,
            String timeConstraint) {
        public NormSemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            normId = SemanticSupport.required(normId, "normId");
            normativeSpecificationSemanticId = SemanticSupport.required(normativeSpecificationSemanticId,
                    "normativeSpecificationSemanticId");
            roleSemanticId = SemanticSupport.text(roleSemanticId);
            missionSemanticId = SemanticSupport.text(missionSemanticId);
            operationType = SemanticSupport.text(operationType);
            condition = SemanticSupport.text(condition);
            timeConstraint = SemanticSupport.text(timeConstraint);
        }
    }

    public record GroupRoleCardinalitySemantic(SemanticMetadata metadata, String groupId, String roleId,
            int min, int max) {
        public GroupRoleCardinalitySemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            groupId = SemanticSupport.required(groupId, "groupId");
            roleId = SemanticSupport.required(roleId, "roleId");
            validate(min, max);
        }
    }

    public record SubGroupCardinalitySemantic(SemanticMetadata metadata, String parentGroupId, String subGroupId,
            int min, int max) {
        public SubGroupCardinalitySemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            parentGroupId = SemanticSupport.required(parentGroupId, "parentGroupId");
            subGroupId = SemanticSupport.required(subGroupId, "subGroupId");
            validate(min, max);
        }
    }

    public record SchemeMissionCardinalitySemantic(SemanticMetadata metadata, String schemeId, String missionId,
            int min, int max) {
        public SchemeMissionCardinalitySemantic {
            metadata = SemanticSupport.required(metadata, "metadata");
            schemeId = SemanticSupport.required(schemeId, "schemeId");
            missionId = SemanticSupport.required(missionId, "missionId");
            validate(min, max);
        }
    }

    private static void validate(int min, int max) {
        if (min < 0 || (max != -1 && max < min)) throw new IllegalArgumentException("invalid cardinality");
    }
}
