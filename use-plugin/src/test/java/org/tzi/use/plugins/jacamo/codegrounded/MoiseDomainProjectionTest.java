package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.uml.sys.MSystem;

/** Source fidelity and native contextual relations, independent of case-study names. */
class MoiseDomainProjectionTest {
    private static Path fixture() { return Path.of("src/test/resources/jacamo/moise/domain-projection.xml").toAbsolutePath(); }
    @Test void originalAuctionProducesDomainSchema() throws Exception {
        caseEvidence("auction",Path.of("../../jacamo/examples/auction/auction.jcm"),"src/org/auction-os.xml",3,1,2,4,2);
    }
    @Test void originalHelloProducesDomainSchema() throws Exception {
        caseEvidence("hello-world",CodeGroundedTestFixtures.hello(),"src/org/o1.xml",5,1,4,13,4);
    }
    @Test void originalHouseOsWorksWithoutGuessingDynamicJcmOrganisation() throws Exception {
        var original=DomainRuntimeProjectionTest.load(Path.of("../../jacamo/examples/house-building/house-building.jcm"));
        assertTrue(original.semanticContract().moiseOrganizations().isEmpty());
        assertTrue(new CodeGroundedNativePipeline().build(original).model().moiseProjection().classNames().isEmpty());
        caseEvidence("house-building",Path.of("../../jacamo/examples/house-building/house-building.jcm"),"src/org/house-os.xml",11,1,10,13,10);
    }
    @Test void contextualIdentityDoesNotCollideAcrossGroupsOrOrganisations(@TempDir Path directory) throws Exception {
        var first=organization(); Path other=directory.resolve("other.xml");
        Files.writeString(other,Files.readString(fixture()).replace("id=\"factory\"","id=\"warehouse\""));
        var second=new OfficialMoiseAdapter().load(directory,other,"generic").organization();
        var result=build(List.of(first,second)); var reversed=build(List.of(second,first));
        assertEquals(NativeUseStructure.signature(result.model().model()),NativeUseStructure.signature(reversed.model().model()));
        var relations=result.model().model().associations().stream().filter(a -> a.getAnnotation(MoiseDomainProjection.ROLE_ASSOCIATION)!=null).toList();
        assertEquals(6,relations.size()); assertEquals(6,relations.stream().map(a -> a.name()).distinct().count());
        assertEquals(6,relations.stream().map(a -> a.getAnnotationValue(MoiseDomainProjection.ROLE_ASSOCIATION,"identity64")).distinct().count());
        for(var org:List.of(first,second)) for(var card:org.structuralSpecification().groupRoleCardinalities()) {
            var relation=MoiseDomainProjection.roleAssociation(result.model().model(),org.metadata().semanticId(),card.groupId(),card.roleId());
            assertEquals("Agent",relation.associationEnds().get(0).cls().name());
            assertEquals("*",relation.associationEnds().get(1).multiplicity().toString());
            assertEquals(DomainProjection.classFor(result.model().model(),"group",card.groupId()),relation.associationEnds().get(1).cls().name());
            assertFalse(result.model().moiseProjection().classNames().containsKey(card.roleId()));
        }
        assertEquals(1,result.model().model().classes().stream().filter(c -> DomainProjection.kind(c).equals("agent-base")).count());
        assertTrue(result.trace().records().stream().anyMatch(t -> t.diagnostics().stream().anyMatch(d -> d.startsWith("MOISE_ADVANCED_ROLE_SEMANTICS_DEFERRED"))));
        assertTrue(result.source().snapshot().moiseOrganizations().stream().anyMatch(o -> !o.structuralSpecification().roleRelations().isEmpty()));
    }
    @Test void normAndFunctionalSourceSurviveWithoutInventedClassOrOcl(@TempDir Path directory) throws Exception {
        Path source=directory.resolve("quoted.xml");
        Files.writeString(source,Files.readString(fixture()).replace("condition=\"ready(S)\"","condition=\"ready(&quot;A&quot;, 'B') &amp; path(C:\\tmp)\""));
        var org=new OfficialMoiseAdapter().load(directory,source,"generic").organization(); var result=build(List.of(org));
        var policy=policies(result.state().system(),"M14").get(0); var norm=org.normativeSpecification().norms().get(0);
        assertEquals(norm.condition(),policy.get("condition")); assertEquals(norm.timeConstraint(),policy.get("deadline"));
        assertEquals(norm.roleSemanticId(),policy.get("role")); assertEquals(norm.missionSemanticId(),policy.get("mission"));
        assertEquals("UNSUPPORTED_NORM_TRANSLATION",policy.get("status"));
        assertTrue(result.model().constraints().stream().allMatch(c->c.origin().startsWith("CORE:GOAL:")),"only documented generic Goal rules; normative translation remains deferred");
        assertTrue(result.trace().records().stream().anyMatch(t -> t.diagnostics().contains("ORDER=EXACT_CHILD_ORDINAL_ATTRIBUTE")));
        assertEquals(org,result.source().snapshot().moiseOrganizations().get(0));
        assertEquals(result.model().structuralHash(),result.export().recompiledStructuralHash());
    }
    @Test void nativeMultiplicityCountsDirectPlayersAndAllowsSameAgentInDifferentGroups() throws Exception {
        var org=organization(); var result=build(List.of(org)); var system=result.state().system(); var api=UseSystemApi.create(system,false);
        var team=org.structuralSpecification().groups().stream().filter(g -> g.groupId().equals("team")).findFirst().orElseThrow();
        var leaf=org.structuralSpecification().groups().stream().filter(g -> g.groupId().equals("leaf")).findFirst().orElseThrow();
        var base=org.structuralSpecification().roles().stream().filter(r -> r.roleId().equals("base")).findFirst().orElseThrow();
        String osClass=DomainProjection.classFor(system.model(),"organisation",org.metadata().semanticId());
        String teamClass=DomainProjection.classFor(system.model(),"group",team.metadata().semanticId());
        String leafClass=DomainProjection.classFor(system.model(),"group",leaf.metadata().semanticId());
        api.createObject(osClass,"oe"); api.createObject(teamClass,"teamOne"); api.createObject(teamClass,"teamTwo"); api.createObject(leafClass,"leafOne");
        for(String object:List.of("teamOne","teamTwo")) api.createLink(DomainProjection.relation("containsGroup",osClass,teamClass),"oe",object);
        api.createLink(DomainProjection.relation("containsGroup",osClass,leafClass),"oe","leafOne");
        var players=system.state().allObjects().stream().filter(o -> DomainProjection.kind(o.cls()).equals("agent-program")).toList();
        var teamRelation=MoiseDomainProjection.roleAssociation(system.model(),org.metadata().semanticId(),team.metadata().semanticId(),base.metadata().semanticId());
        var leafRelation=MoiseDomainProjection.roleAssociation(system.model(),org.metadata().semanticId(),leaf.metadata().semanticId(),base.metadata().semanticId());
        assertFalse(validStructure(system),"a required player is missing");
        api.createLink(teamRelation.name(),players.get(0).name(),"teamOne"); api.createLink(teamRelation.name(),players.get(0).name(),"teamTwo");
        api.createLink(leafRelation.name(),players.get(0).name(),"leafOne"); assertTrue(validStructure(system));
        assertThrows(org.tzi.use.api.UseApiException.class,() -> api.createLink(teamRelation.name(),players.get(0).name(),"teamOne"));
        api.createLink(teamRelation.name(),players.get(1).name(),"teamOne"); assertFalse(validStructure(system),"native upper bound is enforced");
        api.deleteLink(teamRelation.name(),new String[]{players.get(1).name(),"teamOne"}); assertTrue(validStructure(system));
        assertEquals(0,system.state().allObjects().stream().filter(o -> DomainProjection.kind(o.cls()).equals("role")).count());
    }
    @Test void sourcePoliciesAndDirectLinksSurviveNativeExportReplay() throws Exception {
        var result=new CodeGroundedNativePipeline().build(DomainRuntimeProjectionTest.load(Path.of("../../jacamo/examples/auction/auction.jcm")));
        var system=result.state().system(); var soil=new NativeUseSoilExporter().export(system);
        var replay=new NativeUseSoilExporter().replay(result.export().recompiledModel(),soil.commands());
        assertEquals(system.state().numObjects(),replay.state().numObjects()); assertEquals(system.state().allLinks().size(),replay.state().allLinks().size());
        assertTrue(validStructure(replay)); assertEquals(policies(system,"M14"),policies(replay,"M14"));
        assertEquals(result.model().structuralHash(),NativeUseStructure.sha256(replay.model()));
    }
    @Test void incompleteRuntimeSourceDoesNotGiveVacuousExternalPass() throws Exception {
        var result=build(List.of(organization())); var service=new ExternalOclConstraintService(result.state().system());
        service.installSource("generic.ocl","context factory_Organization inv RequiresRuntime: factory_Organization.allInstances()->size() >= 0",result.source().revision());
        assertEquals(Set.of("moise.domain"),service.profile().constraints().get(0).requiredSources());
        assertEquals(VerificationOutcome.SKIPPED,service.evaluate(Map.of(),false).stream().filter(o->o.constraintId().startsWith("EXTERNAL:")).findFirst().orElseThrow().outcome());
        assertEquals(VerificationOutcome.SKIPPED,service.evaluate(Map.of("moise.domain",Completeness.PARTIAL),false).stream().filter(o->o.constraintId().startsWith("EXTERNAL:")).findFirst().orElseThrow().outcome());
        for(var mode:NativeProjectionMode.values()) assertNull(new CodeGroundedNativePipeline().build(withOrganizations(CodeGroundedTestFixtures.helloSnapshot(),List.of(organization())),mode).model().model().getClass("Norm"));
    }
    @Test void crossSchemeGoalReferenceFailsClosed() throws Exception {
        var org=organization(); var fs=org.functionalSpecification(); var one=fs.schemes().get(0); var two=fs.schemes().get(1); var mission=one.missions().get(0);
        var badMission=new MoiseSemanticContract.MissionSemantic(mission.metadata(),mission.missionId(),mission.schemeSemanticId(),List.of(two.goals().get(0).metadata().semanticId()));
        var badScheme=new MoiseSemanticContract.SchemeSemantic(one.metadata(),one.schemeId(),one.functionalSpecificationSemanticId(),one.rootGoalSemanticId(),List.of(badMission),one.goals(),one.plans());
        var badFs=new MoiseSemanticContract.FunctionalSpecificationSemantic(fs.metadata(),fs.organizationSemanticId(),fs.specificationId(),List.of(badScheme,two),fs.schemeMissionCardinalities());
        var bad=new MoiseSemanticContract.OrganizationSemantic(org.metadata(),org.name(),org.sourceUri(),org.structuralSpecification(),badFs,org.normativeSpecification());
        assertTrue(assertThrows(IllegalArgumentException.class,() -> build(List.of(bad))).getMessage().startsWith("MOISE_EXACT_SCHEMA_REFERENCE_MISSING:"));
    }
    @Test void foreignSchemeOwnerOfMissionGoalOrPlanFailsClosed() throws Exception {
        var org=organization(); var fs=org.functionalSpecification(); var one=fs.schemes().getFirst(); var two=fs.schemes().get(1);
        var mission=one.missions().getFirst(); var goal=one.goals().getFirst(); var plan=one.plans().getFirst();
        var badMission=new MoiseSemanticContract.MissionSemantic(mission.metadata(),mission.missionId(),two.metadata().semanticId(),mission.goalSemanticIds());
        var badGoal=new MoiseSemanticContract.OrganizationalGoalSemantic(goal.metadata(),goal.goalId(),two.metadata().semanticId(),goal.goalType(),goal.description(),goal.arguments(),
                goal.minAgentsToSatisfy(),goal.ttf(),goal.location(),goal.dependencySemanticIds(),goal.planSemanticId(),goal.inPlanSemanticId());
        var badPlan=new MoiseSemanticContract.OrganizationalPlanSemantic(plan.metadata(),plan.planId(),two.metadata().semanticId(),plan.targetGoalSemanticId(),plan.operator(),plan.successRate(),plan.orderedSubGoalSemanticIds());
        for(int kind=0;kind<3;kind++) {
            var missions=new java.util.ArrayList<>(one.missions()); var goals=new java.util.ArrayList<>(one.goals()); var plans=new java.util.ArrayList<>(one.plans());
            if(kind==0) missions.set(0,badMission); else if(kind==1) goals.set(0,badGoal); else plans.set(0,badPlan);
            var badScheme=new MoiseSemanticContract.SchemeSemantic(one.metadata(),one.schemeId(),one.functionalSpecificationSemanticId(),one.rootGoalSemanticId(),missions,goals,plans);
            var schemes=new java.util.ArrayList<>(fs.schemes()); schemes.set(0,badScheme);
            var badFs=new MoiseSemanticContract.FunctionalSpecificationSemantic(fs.metadata(),fs.organizationSemanticId(),fs.specificationId(),schemes,fs.schemeMissionCardinalities());
            var bad=new MoiseSemanticContract.OrganizationSemantic(org.metadata(),org.name(),org.sourceUri(),org.structuralSpecification(),badFs,org.normativeSpecification());
            assertTrue(assertThrows(IllegalArgumentException.class,()->build(List.of(bad))).getMessage().startsWith("MOISE_EXACT_SCHEMA_REFERENCE_MISSING:"));
        }
    }
    private static boolean validStructure(MSystem system) { return system.state().checkStructure(new java.io.PrintWriter(java.io.Writer.nullWriter())); }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> policies(MSystem system,String rule) {
        return system.model().classes().stream().flatMap(c -> c.getAllAnnotations().values().stream())
            .filter(a -> a.getName().startsWith("MoisePolicy_"+rule+"_"))
            .map(a -> (Map<String,Object>)CanonicalJson.decode(Base64.getUrlDecoder().decode(a.getAnnotationValue("payload64"))))
            .sorted(Comparator.comparing(p -> p.get("sourceSemanticId").toString())).toList();
    }
    static MoiseSemanticContract.OrganizationSemantic organization() throws Exception { return new OfficialMoiseAdapter().load(fixture().getParent(),fixture(),"generic").organization(); }
    private static CodeGroundedNativePipeline.Result build(List<MoiseSemanticContract.OrganizationSemantic> organizations) throws Exception {
        return new CodeGroundedNativePipeline().build(withOrganizations(CodeGroundedTestFixtures.helloSnapshot(),organizations));
    }
    static ModelSnapshot withOrganizations(ModelSnapshot base,List<MoiseSemanticContract.OrganizationSemantic> organizations) {
        var c=base.semanticContract();
        var semantic=new JacamoSemanticSnapshot(c.contractVersion(),c.project(),c.agentDeclarations(),c.workspaceDeclarations(),c.artifactDeclarations(),
            List.of(),List.of(),List.of(),c.institutionDeployments(),List.of(),c.rawFocusTuples(),c.importProvenance(),c.jasonPrograms(),c.cartagoEnvironments(),organizations,
            c.exactBindings().stream().filter(b -> !Set.of("X02","X03","X04","X07","X08").contains(b.ruleId())).toList(),c.diagnostics());
        return new ModelSnapshot("moise-projection-test",base.sources(),base.agentDeclarations(),base.workspaces(),base.configuredArtifacts(),base.organisationFacts(),
            base.groupRoleCardinalities(),base.parentSubGroupCardinalities(),base.crossDimensionalRelations(),base.unresolvedFacts(),base.projectionProvenance(),semantic);
    }
    private static void caseEvidence(String name,Path entryPath,String osPath,int roles,int groups,int missions,int goals,int norms) throws Exception {
        Path entry=entryPath.toAbsolutePath().normalize(); byte[] before=Files.readAllBytes(entry); var base=DomainRuntimeProjectionTest.load(entry);
        var org=new OfficialMoiseAdapter().load(entry.getParent(),entry.getParent().resolve(osPath),base.semanticContract().project().name()).organization();
        assertEquals(roles,org.structuralSpecification().roles().size()); assertEquals(groups,org.structuralSpecification().groups().size());
        assertEquals(missions,org.functionalSpecification().schemes().stream().mapToInt(s -> s.missions().size()).sum());
        assertEquals(goals,org.functionalSpecification().schemes().stream().mapToInt(s -> s.goals().size()).sum()); assertEquals(norms,org.normativeSpecification().norms().size());
        var result=new CodeGroundedNativePipeline().build(base.semanticContract().moiseOrganizations().isEmpty() ? withOrganizations(base,List.of(org)) : base);
        assertSame(result.model().model(),result.state().system().model()); assertEquals(1+groups+org.functionalSpecification().schemes().size(),result.model().moiseProjection().classNames().size());
        assertTrue(result.state().structureValid()); assertTrue(result.state().invariantsValid());
        assertTrue(result.model().model().classes().stream().allMatch(NativeProjectionPolicy::allowsClass));
        Path output=Path.of("target/domain-projection",name); Files.createDirectories(output);
        Files.writeString(output.resolve("native.use"),result.export().useText()); new NativeUseSoilExporter().export(result.state().system(),output.resolve("state.cmd"));
        Files.write(output.resolve("inventory.json"),CanonicalJson.encode(Map.of("classes",result.model().model().classes().stream().map(c -> c.name()).sorted().toList(),
            "objects",result.state().system().state().allObjects().stream().map(o -> o.name()+":"+o.cls().name()).sorted().toList(),
            "sourceCounts",Map.of("roles",roles,"groups",groups,"missions",missions,"goals",goals,"norms",norms))));
        assertArrayEquals(before,Files.readAllBytes(entry)); System.out.println("DOMAIN_CASE="+name+" "+result.model().model().classes().stream().map(c -> c.name()).sorted().toList());
    }
}
