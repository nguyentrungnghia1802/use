package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract;
import org.jacamo.bridge.contract.semantic.SemanticContractCodec;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseStructure;
import org.tzi.use.api.UseSystemApi;

class CodeGroundedPhase5Test {
    @Test
    void officialHelloMoiseGraphIsTypedAndMaterialized() throws Exception {
        var result = new CodeGroundedNativePipeline().build(CodeGroundedTestFixtures.helloSnapshot(),
                org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.FULL);
        var organizations = result.source().snapshot().moiseOrganizations();

        assertEquals(1, organizations.size());
        assertEquals("o1", organizations.get(0).name());
        assertFalse(organizations.get(0).structuralSpecification().roles().isEmpty());
        assertFalse(organizations.get(0).functionalSpecification().schemes().isEmpty());
        assertFalse(organizations.get(0).normativeSpecification().norms().isEmpty());

        for (String className : List.of("Organization", "StructuralSpecification", "FunctionalSpecification",
                "NormativeSpecification", "Group", "Role", "RoleRelation", "Link", "Compatibility", "Scheme",
                "Mission", "OrganizationalGoal", "OrganizationalPlan", "Norm", "GroupRoleCardinality",
                "SubGroupCardinality", "SchemeMissionCardinality"))
            assertNotNull(result.model().model().getClass(className), className);
        assertEquals(List.of("sequence", "choice", "parallel"),
                result.model().model().enumType("MoisePlanOperator").getLiterals());
        assertEquals(List.of("performance", "achievement", "maintenance"),
                result.model().model().enumType("MoiseGoalType").getLiterals());
        assertEquals(List.of("obligation", "permission"),
                result.model().model().enumType("MoiseNormType").getLiterals());

        for (int index = 1; index <= 43; index++) {
            String ruleId = "M%02d".formatted(index);
            assertTrue(result.trace().records().stream().anyMatch(record -> record.ruleId().equals(ruleId)), ruleId);
        }
        assertTrue(result.state().structureValid());
        assertTrue(result.state().invariantsValid());
        assertEquals(result.source().snapshot(), SemanticContractCodec.decode(
                SemanticContractCodec.encode(result.source().snapshot())));
        assertEquals(result.model().structuralHash(), result.export().recompiledStructuralHash());
    }

    @Test
    void nativeMoisePreservesRelationContextOrderingTextAndNoNormOcl() throws Exception {
        var base = CodeGroundedTestFixtures.helloSnapshot();
        var result = new CodeGroundedNativePipeline().build(withMoise(base, syntheticOrganization()),
                org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.FULL);
        var model = result.model().model();
        var system = result.state().system();

        assertEquals(1, objects(system, "Organization"));
        assertEquals(1, objects(system, "StructuralSpecification"));
        assertEquals(1, objects(system, "FunctionalSpecification"));
        assertEquals(1, objects(system, "NormativeSpecification"));
        assertEquals(2, objects(system, "Group"));
        assertEquals(2, objects(system, "Role"));
        assertEquals(2, objects(system, "RoleRelation"));
        assertEquals(1, objects(system, "Link"));
        assertEquals(1, objects(system, "Compatibility"));
        assertEquals(1, objects(system, "Scheme"));
        assertEquals(1, objects(system, "Mission"));
        assertEquals(3, objects(system, "OrganizationalGoal"));
        assertEquals(1, objects(system, "OrganizationalPlan"));
        assertEquals(1, objects(system, "Norm"));

        for (String association : List.of("M18OrganizationSS", "M19OrganizationFS", "M20OrganizationNS",
                "M22SSGroup", "M23GroupSubgroup", "M24RoleSuperRole", "M07RoleRelationLink",
                "M07RoleRelationCompatibility", "M25LinkSource", "M26LinkTarget", "M27CompatibilitySource",
                "M28CompatibilityTarget", "M29CardinalityOwner", "M30CardinalityMember",
                "M31SubgroupCardinalityOwner", "M32SubgroupCardinalityMember", "M33FSScheme",
                "M34SchemeMission", "M35SchemeRootGoal", "M36SchemeCardinality", "M37MissionCardinality",
                "M39GoalPlan", "M41NSNorm", "M42NormRole", "M43NormMission"))
            assertEquals(1, links(system, association), association);
        assertEquals(2, links(system, "M21SSRole"));
        assertEquals(2, links(system, "M38MissionGoal"));
        assertEquals(2, links(system, "M40PlanSubGoals"));

        assertEquals(1, links(system, "M29CardinalityOwner"));
        assertEquals(1, links(system, "M30CardinalityMember"));
        assertEquals(1, links(system, "M31SubgroupCardinalityOwner"));
        assertEquals(1, links(system, "M32SubgroupCardinalityMember"));

        assertTrue(model.getAssociation("M24RoleSuperRole").associatedClasses().size() == 1);
        assertTrue(model.getClass("Role").parents().isEmpty(), "role hierarchy must not become UML generalization");
        for (String className : List.of("Group", "Role", "Mission"))
            assertTrue(model.getClass(className).attributes().stream()
                    .noneMatch(attribute -> List.of("min", "max", "minCardinality", "maxCardinality")
                            .contains(attribute.name())), className);

        assertEquals("true", UseSystemApi.create(system, false).evaluate(
                "OrganizationalGoal.allInstances->exists(g | g.ttf = 'within 5 minutes' and "
                        + "g.goalType = MoiseGoalType::achievement)").toString());
        assertEquals("true", UseSystemApi.create(system, false).evaluate(
                "OrganizationalPlan.allInstances->exists(p | p.planOperator = MoisePlanOperator::sequence and "
                        + "p.m40SubGoals->collect(goalId)->asSequence() = Sequence{'phase5:goal:one', 'phase5:goal:two'})")
                .toString());
        assertEquals("true", UseSystemApi.create(system, false).evaluate(
                "Norm.allInstances->exists(n | n.normType = MoiseNormType::obligation and "
                        + "n.timeConstraint = 'within 5 minutes')").toString());
        assertTrue(result.trace().records().stream().anyMatch(record -> record.ruleId().equals("M14")
                && record.diagnostics().contains("NORM_RETAINED_AS_DATA_NO_OCL")));
        assertTrue(result.model().constraints().stream()
                .noneMatch(constraint -> constraint.requiredRuleIds().contains("M14")));
        assertTrue(result.state().structureValid());
        assertTrue(result.state().invariantsValid());
        assertEquals(NativeUseStructure.sha256(model), result.export().recompiledStructuralHash());
    }

    @Test
    void multipleNormsCanShareRoleAndMissionWhileEachNormHasOptionalSingleReferences() throws Exception {
        var original = syntheticOrganization();
        var ns = original.normativeSpecification();
        var norm = ns.norms().get(0);
        var shared = new MoiseSemanticContract.NormSemantic(meta("phase5:shared-norm", "MOISE_NORM"),
                "phase5:shared-norm", ns.specificationId(), norm.roleSemanticId(), norm.missionSemanticId(),
                "permission", "true", "");
        var unbound = new MoiseSemanticContract.NormSemantic(meta("phase5:unbound-norm", "MOISE_NORM"),
                "phase5:unbound-norm", ns.specificationId(), "", "", "permission", "true", "");
        var normative = new MoiseSemanticContract.NormativeSpecificationSemantic(ns.metadata(),
                ns.organizationSemanticId(), ns.specificationId(), List.of(norm, shared, unbound));
        var organization = new MoiseSemanticContract.OrganizationSemantic(original.metadata(), original.name(),
                original.sourceUri(), original.structuralSpecification(), original.functionalSpecification(), normative);
        var result = new CodeGroundedNativePipeline().build(
                withMoise(CodeGroundedTestFixtures.helloSnapshot(), organization),
                org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.FULL);

        assertEquals(3, objects(result.state().system(), "Norm"));
        assertEquals(2, links(result.state().system(), "M42NormRole"));
        assertEquals(2, links(result.state().system(), "M43NormMission"));
        for (String name : List.of("M42NormRole", "M43NormMission")) {
            var ends = result.model().model().getAssociation(name).associationEnds();
            assertEquals("Norm", ends.get(0).cls().name());
            assertTrue(ends.get(0).multiplicity().contains(2), "Role/Mission may be referenced by multiple norms");
            assertTrue(ends.get(1).multiplicity().contains(0));
            assertTrue(ends.get(1).multiplicity().contains(1));
            assertFalse(ends.get(1).multiplicity().contains(2), "Each norm has at most one Role/Mission");
            assertFalse(ends.get(1).isOrdered(), "Scalar references must survive USE export/recompile");
        }
        assertTrue(result.state().structureValid());
        assertTrue(result.state().invariantsValid());
        assertEquals(result.model().structuralHash(), result.export().recompiledStructuralHash());
    }

    private static int objects(org.tzi.use.uml.sys.MSystem system, String className) {
        return system.state().objectsOfClass(system.model().getClass(className)).size();
    }

    private static int links(org.tzi.use.uml.sys.MSystem system, String association) {
        return system.state().linksOfAssociation(system.model().getAssociation(association)).size();
    }

    private static ModelSnapshot withMoise(ModelSnapshot base, MoiseSemanticContract.OrganizationSemantic organization) {
        var contract = base.semanticContract();
        var semantic = new JacamoSemanticSnapshot(contract.contractVersion(), contract.project(),
                contract.agentDeclarations(), contract.workspaceDeclarations(), contract.artifactDeclarations(),
                contract.organizationDeployments(), contract.groupDeployments(), contract.schemeDeployments(),
                contract.institutionDeployments(), contract.rawRoleTuples(), contract.rawFocusTuples(),
                contract.importProvenance(), contract.jasonPrograms(), contract.cartagoEnvironments(),
                List.of(organization), contract.exactBindings(), contract.diagnostics());
        return new ModelSnapshot("phase5-test", base.sources(), base.agentDeclarations(), base.workspaces(),
                base.configuredArtifacts(), base.organisationFacts(), base.groupRoleCardinalities(),
                base.parentSubGroupCardinalities(), base.crossDimensionalRelations(), base.unresolvedFacts(),
                base.projectionProvenance(), semantic);
    }

    private static MoiseSemanticContract.OrganizationSemantic syntheticOrganization() {
        String organization = "phase5:organization";
        String structural = "phase5:structural";
        String functional = "phase5:functional";
        String normative = "phase5:normative";
        String root = "phase5:group:root";
        String child = "phase5:group:child";
        String baseRole = "phase5:role:base";
        String childRole = "phase5:role:child";
        String scheme = "phase5:scheme";
        String mission = "phase5:mission";
        String rootGoal = "phase5:goal:root";
        String goalOne = "phase5:goal:one";
        String goalTwo = "phase5:goal:two";
        String plan = "phase5:plan";
        String norm = "phase5:norm";
        String linkRelation = "phase5:relation:link";
        String compatibilityRelation = "phase5:relation:compatibility";

        var roles = List.of(
                new MoiseSemanticContract.RoleSemantic(meta(baseRole, "MOISE_ROLE"), baseRole, false, List.of()),
                new MoiseSemanticContract.RoleSemantic(meta(childRole, "MOISE_ROLE"), childRole, true,
                        List.of(baseRole)));
        var groups = List.of(
                new MoiseSemanticContract.GroupSemantic(meta(root, "MOISE_GROUP"), root, "",
                        List.of(baseRole), List.of(child)),
                new MoiseSemanticContract.GroupSemantic(meta(child, "MOISE_GROUP"), child, root,
                        List.of(childRole), List.of()));
        var relations = List.of(
                new MoiseSemanticContract.RoleRelationSemantic(meta(linkRelation, "MOISE_ROLE_RELATION"),
                        linkRelation, "link", root, baseRole, childRole, "IntraGroup", false, true),
                new MoiseSemanticContract.RoleRelationSemantic(meta(compatibilityRelation, "MOISE_ROLE_RELATION"),
                        compatibilityRelation, "compatibility", root, childRole, baseRole, "InterGroup", true, false));
        var structuralSpecification = new MoiseSemanticContract.StructuralSpecificationSemantic(
                meta(structural, "MOISE_SS"), organization, structural, root, roles, groups, relations,
                List.of(new MoiseSemanticContract.LinkSemantic(meta("phase5:link", "MOISE_LINK"), linkRelation,
                        "authority")),
                List.of(new MoiseSemanticContract.CompatibilitySemantic(meta("phase5:compatibility", "MOISE_COMPATIBILITY"),
                        compatibilityRelation)),
                List.of(new MoiseSemanticContract.GroupRoleCardinalitySemantic(meta("phase5:group-role-cardinality",
                        "MOISE_GROUP_ROLE_CARDINALITY"), root, baseRole, 1, 2)),
                List.of(new MoiseSemanticContract.SubGroupCardinalitySemantic(meta("phase5:subgroup-cardinality",
                        "MOISE_SUBGROUP_CARDINALITY"), root, child, 0, 1)));

        var missions = List.of(new MoiseSemanticContract.MissionSemantic(meta(mission, "MOISE_MISSION"), mission,
                scheme, List.of(goalOne, goalTwo)));
        var goals = List.of(
                new MoiseSemanticContract.OrganizationalGoalSemantic(meta(rootGoal, "MOISE_GOAL"), rootGoal, scheme,
                        "performance", "root", "", 1, "", "", List.of(), plan, ""),
                new MoiseSemanticContract.OrganizationalGoalSemantic(meta(goalOne, "MOISE_GOAL"), goalOne, scheme,
                        "achievement", "one", "", 1, "within 5 minutes", "", List.of(), "", plan),
                new MoiseSemanticContract.OrganizationalGoalSemantic(meta(goalTwo, "MOISE_GOAL"), goalTwo, scheme,
                        "maintenance", "two", "", 1, "within 5 minutes", "", List.of(rootGoal), "", plan));
        var plans = List.of(new MoiseSemanticContract.OrganizationalPlanSemantic(meta(plan, "MOISE_PLAN"), plan,
                scheme, rootGoal, "sequence", 0.75, List.of(goalOne, goalTwo)));
        var schemes = List.of(new MoiseSemanticContract.SchemeSemantic(meta(scheme, "MOISE_SCHEME"), scheme,
                functional, rootGoal, missions, goals, plans));
        var functionalSpecification = new MoiseSemanticContract.FunctionalSpecificationSemantic(
                meta(functional, "MOISE_FS"), organization, functional, schemes,
                List.of(new MoiseSemanticContract.SchemeMissionCardinalitySemantic(meta("phase5:scheme-mission-cardinality",
                        "MOISE_SCHEME_MISSION_CARDINALITY"), scheme, mission, 1, 1)));
        var normativeSpecification = new MoiseSemanticContract.NormativeSpecificationSemantic(meta(normative, "MOISE_NS"),
                organization, normative, List.of(new MoiseSemanticContract.NormSemantic(meta(norm, "MOISE_NORM"),
                        norm, normative, childRole, mission, "obligation", "goal completed", "within 5 minutes")));
        return new MoiseSemanticContract.OrganizationSemantic(meta(organization, "MOISE_ORGANIZATION"),
                "phase5-org", "phase5://organization.xml", structuralSpecification, functionalSpecification,
                normativeSpecification);
    }

    private static SemanticMetadata meta(String id, String kind) {
        return new SemanticMetadata(id, kind, CodeGroundedPhase5Test.class.getName(),
                EvidenceAuthority.OFFICIAL_MOISE_API, Fidelity.EXACT, CapabilityStatus.COMPLETE, List.of(), List.of());
    }
}
