package org.jacamo.bridge.contract.semantic;

/** Typed Moise contract surface. Relation-scoped cardinality records retain their owner context. */
public final class MoiseSemanticContract {
    private MoiseSemanticContract() { }
    public record OrganizationSemantic(SemanticNode value) { }
    public record StructuralSpecificationSemantic(SemanticNode value) { }
    public record FunctionalSpecificationSemantic(SemanticNode value) { }
    public record NormativeSpecificationSemantic(SemanticNode value) { }
    public record GroupSemantic(SemanticNode value) { }
    public record RoleSemantic(SemanticNode value) { }
    public record RoleRelationSemantic(SemanticNode value) { }
    public record LinkSemantic(SemanticNode value) { }
    public record CompatibilitySemantic(SemanticNode value) { }
    public record SchemeSemantic(SemanticNode value) { }
    public record MissionSemantic(SemanticNode value) { }
    public record OrganizationalGoalSemantic(SemanticNode value) { }
    public record OrganizationalPlanSemantic(SemanticNode value) { }
    public record NormSemantic(SemanticNode value) { }
    public record GroupRoleCardinalitySemantic(SemanticMetadata metadata, String groupId, String roleId, int min, int max) { }
    public record SubGroupCardinalitySemantic(SemanticMetadata metadata, String parentGroupId, String subGroupId, int min, int max) { }
    public record SchemeMissionCardinalitySemantic(SemanticMetadata metadata, String schemeId, String missionId, int min, int max) { }
}
