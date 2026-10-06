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
import org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection;
import org.tzi.use.api.UseSystemApi;

class CodeGroundedPhase5Test {
    @Test void officialMoiseGraphRetainsFunctionalNormSemanticsWithOnlyDomainTypes() throws Exception {
        var result=CodeGroundedTestFixtures.helloPipeline(); var org=result.source().snapshot().moiseOrganizations().get(0);
        assertEquals("o1",org.name()); assertFalse(org.functionalSpecification().schemes().isEmpty()); assertFalse(org.normativeSpecification().norms().isEmpty());
        assertEquals(result.source().snapshot(),SemanticContractCodec.decode(SemanticContractCodec.encode(result.source().snapshot())));
        assertNotNull(result.model().model().getClass("o1_Organization")); assertNotNull(result.model().model().getClass("team"));
        for(String name:List.of("Scheme","Mission","OrganizationalGoal")) assertNotNull(result.model().model().getClass(name));
        for(String name:List.of("Role","OrganizationalPlan","Norm","GroupInstance","RoleEnactment")) assertNull(result.model().model().getClass(name));
        assertTrue(result.state().structureValid()); assertTrue(result.state().invariantsValid());
    }
    @Test void sourceHierarchyRelationsOrderingAndTimeTextAreRetainedWithDeferredDiagnostics() throws Exception {
        var org=syntheticOrganization(); var result=new CodeGroundedNativePipeline().build(withMoise(CodeGroundedTestFixtures.helloSnapshot(),org));
        assertEquals(org,result.source().snapshot().moiseOrganizations().get(0)); assertEquals(4,result.model().moiseProjection().classNames().size());
        for(var card:org.structuralSpecification().groupRoleCardinalities()) {
            var relation=MoiseDomainProjection.roleAssociation(result.model().model(),org.metadata().semanticId(),card.groupId(),card.roleId());
            assertEquals("Agent",relation.associationEnds().get(0).cls().name()); assertEquals("*",relation.associationEnds().get(1).multiplicity().toString());
            assertTrue(relation.associationEnds().get(0).multiplicity().contains(card.min()));
        }
        assertTrue(result.trace().records().stream().anyMatch(r -> r.diagnostics().contains("STATUS=UNSUPPORTED_NORM_TRANSLATION")));
        assertTrue(result.trace().records().stream().anyMatch(r -> r.diagnostics().contains("MOISE_ADVANCED_ROLE_SEMANTICS_DEFERRED")));
        assertEquals(List.of("phase5:goal:one","phase5:goal:two"),org.functionalSpecification().schemes().get(0).plans().get(0).orderedSubGoalSemanticIds());
        assertEquals("within 5 minutes",org.normativeSpecification().norms().get(0).timeConstraint());
        assertEquals(6,result.model().constraints().size());
        assertTrue(result.model().constraints().stream().allMatch(c->c.origin().startsWith("CORE:GOAL:")),
                "Authored structure rules must not invent deferred temporal or Norm semantics");
        assertEquals(result.model().structuralHash(),result.export().recompiledStructuralHash());
    }
    @Test void sharedAndOptionalNormReferencesStaySourceOnlyWithIndependentOclHooks() throws Exception {
        var original=syntheticOrganization(); var ns=original.normativeSpecification(); var norm=ns.norms().get(0);
        var shared=new MoiseSemanticContract.NormSemantic(meta("phase5:shared-norm","MOISE_NORM"),"shared",ns.metadata().semanticId(),norm.roleSemanticId(),norm.missionSemanticId(),"permission","true","");
        var unbound=new MoiseSemanticContract.NormSemantic(meta("phase5:unbound-norm","MOISE_NORM"),"unbound",ns.metadata().semanticId(),"","","permission","true","");
        var changed=new MoiseSemanticContract.OrganizationSemantic(original.metadata(),original.name(),original.sourceUri(),original.structuralSpecification(),original.functionalSpecification(),
            new MoiseSemanticContract.NormativeSpecificationSemantic(ns.metadata(),ns.organizationSemanticId(),ns.specificationId(),List.of(norm,shared,unbound)));
        var result=new CodeGroundedNativePipeline().build(withMoise(CodeGroundedTestFixtures.helloSnapshot(),changed));
        assertEquals(List.of(norm,shared,unbound),result.source().snapshot().moiseOrganizations().get(0).normativeSpecification().norms());
        assertNull(result.model().model().getClass("Norm"));
        assertEquals(3,result.trace().records().stream().filter(r -> r.ruleId().equals("M14") && r.diagnostics().contains("STATUS=UNSUPPORTED_NORM_TRANSLATION")).count());
        assertTrue(result.state().structureValid());
    }
    private static int objects(org.tzi.use.uml.sys.MSystem system, String className) {
        return system.state().objectsOfClass(system.model().getClass(className)).size();
    }

    private static int links(org.tzi.use.uml.sys.MSystem system, String association) {
        return system.state().linksOfAssociation(system.model().getAssociation(association)).size();
    }

    private static ModelSnapshot withMoise(ModelSnapshot base, MoiseSemanticContract.OrganizationSemantic organization) {
        return MoiseDomainProjectionTest.withOrganizations(base,List.of(organization));
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
                        "MOISE_GROUP_ROLE_CARDINALITY"), root, baseRole, 1, 2),
                        new MoiseSemanticContract.GroupRoleCardinalitySemantic(meta("phase5:child-role-cardinality",
                        "MOISE_GROUP_ROLE_CARDINALITY"), child, childRole, 0, 4)),
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
