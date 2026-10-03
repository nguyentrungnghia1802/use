package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jacamo.bridge.adapter.OfficialMoiseAdapter;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseStructure;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MAggregationKind;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.sys.MSystem;

class MoiseDomainProjectionTest {
    private static final String AN = MoiseDomainProjection.ANNOTATION;
    private static Path fixture() { return Path.of("src/test/resources/jacamo/moise/domain-projection.xml").toAbsolutePath().normalize(); }

    @Test void originalAuctionProducesSpecializedNativeSchema() throws Exception {
        caseEvidence("auction", Path.of("../../jacamo/examples/auction/auction.jcm"), "src/org/auction-os.xml", 3, 1, 2, 4, 2);
    }
    @Test void originalHelloProducesSpecializedNativeSchema() throws Exception {
        caseEvidence("hello-world", Path.of("../../jacamo/doc/tutorials/hello-world/code/helloworld/helloworld.jcm"), "src/org/o1.xml", 5, 1, 4, 13, 4);
    }
    @Test void originalHouseOsWorksWithoutFabricatingDynamicJcmOrganization() throws Exception {
        var entry = Path.of("../../jacamo/examples/house-building/house-building.jcm").toAbsolutePath().normalize();
        var original = load(entry);
        assertTrue(original.semanticContract().moiseOrganizations().isEmpty());
        var pipeline = new CodeGroundedNativePipeline().build(original);
        assertTrue(pipeline.model().moiseProjection().classNames().isEmpty(), "no ASL string/path guessing for runtime-created House OS");
        caseEvidence("house-building", entry, "src/org/house-os.xml", 11, 1, 10, 13, 10);
    }

    @Test void genericIdsScopesRoleInheritanceAndNormPoliciesAreLossless(@TempDir Path directory) throws Exception {
        var org = organization();
        Path other = directory.resolve("other.xml");
        Files.writeString(other, Files.readString(fixture()).replace("id=\"factory\"", "id=\"warehouse\""));
        var second = new OfficialMoiseAdapter().load(directory, other, "generic").organization();
        var result = build(List.of(second, org), NativeProjectionMode.AUTO);
        var reversed = build(List.of(org, second), NativeProjectionMode.AUTO);
        assertEquals(result.model().moiseProjection().classNames(), reversed.model().moiseProjection().classNames());
        assertEquals(NativeUseStructure.signature(result.model().model()), NativeUseStructure.signature(reversed.model().model()));
        var symbols = result.model().moiseProjection().classNames();
        assertEquals(symbols.size(), Set.copyOf(symbols.values()).size());
        assertTrue(org.structuralSpecification().roles().stream().filter(r -> r.roleId().equals("Agent"))
                .allMatch(r -> !symbols.get(r.metadata().semanticId()).equals("Agent")), "USE/JCM type names cannot collide");
        var base = org.structuralSpecification().roles().stream().filter(r -> r.roleId().equals("base")).findFirst().orElseThrow();
        var worker = org.structuralSpecification().roles().stream().filter(r -> r.roleId().equals("worker")).findFirst().orElseThrow();
        assertTrue(result.model().model().getClass(symbols.get(worker.metadata().semanticId())).parents()
                .contains(result.model().model().getClass(symbols.get(base.metadata().semanticId()))));
        var sameNames = org.structuralSpecification().roles().stream().filter(r -> r.roleId().startsWith("same")).toList();
        assertNotEquals(symbols.get(sameNames.get(0).metadata().semanticId()), symbols.get(sameNames.get(1).metadata().semanticId()));
        assertNull(result.model().model().getClass("Norm"));
        var norm = org.normativeSpecification().norms().get(0);
        var policy = policies(result.state().system(), "M14").stream().filter(p -> norm.metadata().semanticId().equals(p.get("sourceSemanticId"))).findFirst().orElseThrow();
        assertEquals(norm.roleSemanticId(), policy.get("role")); assertEquals(norm.missionSemanticId(), policy.get("mission"));
        assertEquals("ready(S)", policy.get("condition")); assertEquals("10 minutes", policy.get("deadline"));
        assertEquals(symbols.get(norm.roleSemanticId()), policy.get("roleClass"));
        assertTrue(result.model().constraints().stream().noneMatch(c -> c.requiredRuleIds().contains("M14")));
        assertTrue(result.trace().records().stream().anyMatch(t -> t.ruleId().equals("M42") && t.sourceIdentity().equals(norm.metadata().semanticId())));
        var sourceRules = result.trace().records().stream().filter(t -> !t.sourceEvidence().isEmpty())
                .map(t -> t.ruleId()).collect(Collectors.toSet());
        assertTrue(sourceRules.containsAll(Set.of("M18", "M19", "M20", "M21", "M25", "M26", "M27", "M28", "M31", "M32", "M36", "M37", "M39", "M41", "M42", "M43")), sourceRules.toString());
        for (var relation : org.structuralSpecification().roleRelations()) {
            var policyRelation = policies(result.state().system(), "M07").stream()
                    .filter(p -> p.get("sourceSemanticId").equals(relation.metadata().semanticId())).findFirst().orElseThrow();
            assertEquals(relation.sourceRoleSemanticId(), policyRelation.get("sourceRole"));
            assertEquals(relation.targetRoleSemanticId(), policyRelation.get("targetRole"));
            assertEquals(relation.bidirectional(), policyRelation.get("bidirectional"));
            assertEquals(relation.scope(), policyRelation.get("scope"));
        }
    }

    @Test void arbitraryNormTextSurvivesNativeExportWithoutBecomingAnOclExpression(@TempDir Path directory) throws Exception {
        Path source=directory.resolve("quoted.xml");
        Files.writeString(source,Files.readString(fixture()).replace("condition=\"ready(S)\"",
                "condition=\"ready(&quot;A&quot;, 'B') &amp; path(C:\\tmp)\""));
        var org=new OfficialMoiseAdapter().load(directory,source,"generic").organization();
        var result=build(List.of(org),NativeProjectionMode.AUTO);
        String condition=org.normativeSpecification().norms().get(0).condition();
        assertTrue(condition.contains("\"A\"")); assertTrue(condition.contains("C:\\tmp"));
        assertEquals(condition,policies(result.state().system(),"M14").get(0).get("condition"));
        assertEquals(result.model().structuralHash(),result.export().recompiledStructuralHash());
        assertTrue(result.model().constraints().stream().noneMatch(c->c.requiredRuleIds().contains("M14")));
    }

    @Test void formationOclCountsExactRoleNotSubroleAndDetectsDuplicateEnactments() throws Exception {
        var org = organization(); var result = build(List.of(org), NativeProjectionMode.AUTO); var system = result.state().system();
        var api = UseSystemApi.create(system, false); var symbols = result.model().moiseProjection().classNames();
        String os = symbols.get(org.metadata().semanticId());
        String team = symbols.get(org.structuralSpecification().groups().stream().filter(g -> g.groupId().equals("team")).findFirst().orElseThrow().metadata().semanticId());
        String worker = symbols.get(org.structuralSpecification().roles().stream().filter(r -> r.roleId().equals("worker")).findFirst().orElseThrow().metadata().semanticId());
        String base = symbols.get(org.structuralSpecification().roles().stream().filter(r -> r.roleId().equals("base")).findFirst().orElseThrow().metadata().semanticId());
        api.createObject(os, "oe"); api.createObject(team, "teamInstance");
        link(api, system, "M22", os, support(system, "M05", org).name(), "oe", "teamInstance");
        api.setAttributeValue("teamInstance", "wellFormed", "false");
        var agents = system.state().objectsOfClass(system.model().getClass("Agent")).stream().map(o -> o.name()).sorted().toList();
        rolePlayer(api, system, org, worker, "workerPlayer", agents.get(0));
        var lower = result.model().constraints().stream().filter(c -> c.targetContext().equals(team)
                && c.requiredRuleIds().contains("M15") && c.name().endsWith("MinimumWhenWellFormed") && c.oclBody().contains("(" + base + ")")).findFirst().orElseThrow();
        assertEquals("true", invariant(system, lower.name()));
        api.setAttributeValue("teamInstance", "wellFormed", "true");
        assertEquals("false", invariant(system, lower.name()), "official getPlayers(roleId) does not count subroles as that role");
        rolePlayer(api, system, org, base, "basePlayer", agents.get(1));
        assertEquals("true", invariant(system, lower.name()));
        rolePlayer(api, system, org, base, "duplicate", agents.get(1));
        var unique = result.model().constraints().stream().filter(c -> c.targetContext().equals(base) && c.name().endsWith("UniqueEnactment")).findFirst().orElseThrow();
        assertEquals("false", invariant(system, unique.name()));
        var otherCard = org.structuralSpecification().groupRoleCardinalities().stream().filter(c -> c.roleId().equals(
                org.structuralSpecification().roles().stream().filter(r -> r.roleId().equals("base")).findFirst().orElseThrow().metadata().semanticId())).toList();
        assertEquals(Set.of(1, 3), otherCard.stream().map(c -> c.max()).collect(Collectors.toSet()), "bounds stay on group-role tuples");
    }

    @Test void nativeEnumDerivationsAndPolicySurviveUseAndSoilRoundTrip() throws Exception {
        var org = organization(); var result = build(List.of(org), NativeProjectionMode.AUTO); var system = result.state().system();
        var api = UseSystemApi.create(system, false); var symbols = result.model().moiseProjection().classNames();
        var scheme = org.functionalSpecification().schemes().get(0);
        String os = symbols.get(org.metadata().semanticId()), schemeClass = symbols.get(scheme.metadata().semanticId());
        api.createObject(os, "oe"); api.createObject(schemeClass, "schemeInstance");
        link(api, system, "M33", os, support(system, "M10", org).name(), "oe", "schemeInstance");
        api.setAttributeValue("schemeInstance", "wellFormed", "false");
        int index = 0;
        Map<String, String> goalObjects = new TreeMap<>();
        for (var goal : scheme.goals()) {
            String cls = symbols.get(goal.metadata().semanticId()), obj = "goal" + index++;
            api.createObject(cls, obj);
            goalObjects.put(goal.metadata().semanticId(), obj);
            api.setAttributeValue(obj, "satisfied", "false");
            link(api, system, "M35", schemeClass, cls, "schemeInstance", obj);
            assertEquals("MoiseGoalType::" + goal.goalType(), api.evaluate(obj + ".goalType").toString());
        }
        for (var plan : scheme.plans()) for (String child : plan.orderedSubGoalSemanticIds())
            link(api, system, "M40", symbols.get(plan.targetGoalSemanticId()), symbols.get(child),
                    goalObjects.get(plan.targetGoalSemanticId()), goalObjects.get(child));
        assertTrue(system.state().checkStructure(new java.io.PrintWriter(java.io.Writer.nullWriter()), true));
        var soil = new NativeUseSoilExporter().export(system);
        assertFalse(soil.commands().contains(".goalType :="));
        assertFalse(soil.commands().contains(".planOperator :="));
        MSystem replay = new NativeUseSoilExporter().replay(result.export().recompiledModel(), soil.commands());
        assertEquals(system.state().numObjects(), replay.state().numObjects());
        assertEquals(system.state().allLinks().size(), replay.state().allLinks().size());
        assertEquals(policies(system, "M14"), policies(replay, "M14"));
        assertEquals(result.model().structuralHash(), NativeUseStructure.sha256(replay.model()));
        assertTrue(NativeUseStructure.signature(replay.model()).stream().anyMatch(row -> row.startsWith("generalization|")));
    }

    @Test void missingMoiseRuntimeCanNeverBecomeVacuousFormalPass() throws Exception {
        var result = build(List.of(organization()), NativeProjectionMode.AUTO); var service = new ExternalOclConstraintService(result.state().system());
        service.installSource("generic.ocl", "context Factory inv RequiresObservedOrganization: Factory.allInstances()->size() >= 0", result.source().revision());
        assertEquals(Set.of("moise"), service.profile().constraints().get(0).requiredSources());
        for (var sources : List.of(Map.<String, Completeness>of(), Map.of("moise", Completeness.COMPLETE))) {
            var outcomes = service.evaluate(sources, false);
            assertTrue(outcomes.stream().filter(o -> !o.contextClass().equals("Plan") && !o.contextClass().equals("PlanLibrary"))
                    .allMatch(o -> o.outcome() == VerificationOutcome.SKIPPED && o.diagnostic().equals("MOISE_DOMAIN_RUNTIME_EVIDENCE_ONLY")));
        }
        var full = build(List.of(organization()), NativeProjectionMode.FULL);
        assertNotNull(full.model().model().getClass("Norm"));
        assertEquals("SPECIFICATION_INSPECTION", full.model().model().getClass("Norm").getAnnotationValue(AN, "representation"));
        assertFalse(full.state().system().state().allObjects().stream().anyMatch(o -> "moise".equals(o.cls().getAnnotationValue(AN, "runtimeSource"))));
    }

    @Test void crossSchemeGoalReferenceFailsClosed() throws Exception {
        var org = organization(); var fs = org.functionalSpecification(); var s1 = fs.schemes().get(0); var s2 = fs.schemes().get(1);
        var original = s1.missions().get(0);
        var badMission = new MoiseSemanticContract.MissionSemantic(original.metadata(), original.missionId(), original.schemeSemanticId(),
                List.of(s2.goals().get(0).metadata().semanticId()));
        var badScheme = new MoiseSemanticContract.SchemeSemantic(s1.metadata(), s1.schemeId(), s1.functionalSpecificationSemanticId(),
                s1.rootGoalSemanticId(), List.of(badMission), s1.goals(), s1.plans());
        var badFs = new MoiseSemanticContract.FunctionalSpecificationSemantic(fs.metadata(), fs.organizationSemanticId(), fs.specificationId(),
                List.of(badScheme, s2), fs.schemeMissionCardinalities());
        var bad = new MoiseSemanticContract.OrganizationSemantic(org.metadata(), org.name(), org.sourceUri(), org.structuralSpecification(), badFs, org.normativeSpecification());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> build(List.of(bad), NativeProjectionMode.AUTO))
                .getMessage().startsWith("MOISE_EXACT_SCHEMA_REFERENCE_MISSING:"));
    }

    private static void caseEvidence(String name, Path entryPath, String osPath, int roles, int groups, int missions, int goals, int norms) throws Exception {
        Path entry = entryPath.toAbsolutePath().normalize(); byte[] jcmBefore = Files.readAllBytes(entry);
        var base = load(entry); var org = new OfficialMoiseAdapter().load(entry.getParent(), entry.getParent().resolve(osPath),
                base.semanticContract().project().name()).organization();
        assertEquals(roles, org.structuralSpecification().roles().size()); assertEquals(groups, org.structuralSpecification().groups().size());
        assertEquals(missions, org.functionalSpecification().schemes().stream().mapToInt(s -> s.missions().size()).sum());
        assertEquals(goals, org.functionalSpecification().schemes().stream().mapToInt(s -> s.goals().size()).sum());
        assertEquals(norms, org.normativeSpecification().norms().size());
        if (!base.semanticContract().moiseOrganizations().isEmpty()) assertEquals(List.of(org), base.semanticContract().moiseOrganizations());
        var result = new CodeGroundedNativePipeline().build(base.semanticContract().moiseOrganizations().isEmpty()
                ? withOrganizations(base, List.of(org)) : base, NativeProjectionMode.AUTO);
        var model = result.model().model(); var system = result.state().system();
        assertSame(model, system.model()); assertSame(system, system.state().system());
        for (String generic : MoiseDomainProjection.INSPECTION_RULES.keySet()) assertNull(model.getClass(generic), name + " " + generic);
        assertEquals(1 + roles + groups + missions + goals + org.functionalSpecification().schemes().size(), result.model().moiseProjection().classNames().size());
        assertTrue(system.state().allObjects().stream().noneMatch(o -> o.cls().getAnnotation(AN) != null));
        assertTrue(result.state().structureValid()); assertTrue(result.state().invariantsValid());
        assertEquals(result.model().structuralHash(), result.export().recompiledStructuralHash());
        var service = new ExternalOclConstraintService(system);
        assertTrue(service.evaluate(Map.of("moise", Completeness.COMPLETE), false).stream().filter(o -> model.getClass(o.contextClass()).getAnnotation(AN) != null)
                .allMatch(o -> o.outcome() == VerificationOutcome.SKIPPED));
        Path output = Path.of("target/moise-domain-evidence", name).toAbsolutePath(); Files.createDirectories(output);
        Files.writeString(output.resolve("native.use"), result.export().useText(), StandardCharsets.UTF_8);
        new NativeUseSoilExporter().export(system, output.resolve("baseline.cmd"));
        Map<String, Object> evidence = new TreeMap<>();
        evidence.put("source", entry.toString()); evidence.put("os", entry.getParent().resolve(osPath).toString());
        evidence.put("sourceCounts", Map.of("roles", roles, "groups", groups, "missions", missions, "goals", goals, "norms", norms));
        evidence.put("domainClasses", result.model().moiseProjection().classNames());
        evidence.put("classCount", model.classes().size()); evidence.put("associations", model.associations().size());
        evidence.put("attributes", model.classes().stream().mapToInt(c -> c.attributes().size()).sum());
        evidence.put("operations", model.classes().stream().mapToInt(c -> c.operations().size()).sum());
        evidence.put("inputBoundary", base.semanticContract().moiseOrganizations().isEmpty() ? "EXPLICIT_OFFICIAL_OS_API" : "ORIGINAL_JCM_OFFICIAL_API");
        evidence.put("compositions", model.associations().stream().filter(a -> a.aggregationKind() == MAggregationKind.COMPOSITION).count());
        evidence.put("invariants", model.classInvariants().size()); evidence.put("nativeObjects", system.state().numObjects()); evidence.put("nativeLinks", system.state().allLinks().size());
        evidence.put("enums", model.enumTypes().stream().map(e -> e.name()).sorted().toList());
        evidence.put("structuralHash", result.model().structuralHash()); evidence.put("recompiledHash", result.export().recompiledStructuralHash());
        evidence.put("moiseRuntime", "EVIDENCE_ONLY_NO_STATIC_ENACTMENTS");
        Files.write(output.resolve("inventory.json"), CanonicalJson.encode(evidence));
        assertArrayEquals(jcmBefore, Files.readAllBytes(entry));
        System.out.printf("MOISE_DOMAIN_CASE %s classes=%d associations=%d invariants=%d domain=%d moiseObjects=0 hash=PASS%n",
                name, model.classes().size(), model.associations().size(), model.classInvariants().size(), result.model().moiseProjection().classNames().size());
    }

    private static void rolePlayer(UseSystemApi api, MSystem system, MoiseSemanticContract.OrganizationSemantic org,
                                   String type, String object, String agent) throws Exception {
        api.createObject(type, object);
        link(api, system, "X04", "Agent", support(system, "M06", org).name(), agent, object);
        link(api, system, "M29", support(system, "M05", org).name(), support(system, "M06", org).name(), "teamInstance", object);
    }
    private static MClass support(MSystem system, String rule, MoiseSemanticContract.OrganizationSemantic org) {
        return system.model().classes().stream().filter(c -> rule.equals(c.getAnnotationValue(AN, "ruleId"))
                && c.getAnnotationValue(AN, "representation").equals("SUPPORTING_ENACTMENT_IDENTITY")
                && new String(Base64.getUrlDecoder().decode(c.getAnnotationValue(AN, "semanticId64")), StandardCharsets.UTF_8).equals(org.metadata().semanticId())).findFirst().orElseThrow();
    }
    private static void link(UseSystemApi api, MSystem system, String rule, String first, String second, String one, String two) throws Exception {
        var association = system.model().associations().stream().filter(a -> a.getAnnotationValue(AN, "ruleId").equals(rule)
                && a.associationEnds().get(0).cls().name().equals(first) && a.associationEnds().get(1).cls().name().equals(second)).findFirst().orElseThrow();
        api.createLink(association.name(), one, two);
    }
    private static String invariant(MSystem system, String name) {
        return new Evaluator().eval(system.model().classInvariants().stream().filter(i -> i.name().equals(name)).findFirst().orElseThrow().expandedExpression(), system.state()).toString();
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> policies(MSystem system, String rule) {
        return system.model().classes().stream().flatMap(c -> c.getAllAnnotations().values().stream())
                .filter(a -> a.getName().startsWith("MoisePolicy_" + rule + "_"))
                .map(a -> (Map<String, Object>) CanonicalJson.decode(Base64.getUrlDecoder().decode(a.getAnnotationValue("payload64"))))
                .sorted(java.util.Comparator.comparing(p -> p.get("sourceSemanticId").toString())).toList();
    }
    private static MoiseSemanticContract.OrganizationSemantic organization() throws Exception {
        return new OfficialMoiseAdapter().load(fixture().getParent(), fixture(), "generic").organization();
    }
    private static CodeGroundedNativePipeline.Result build(List<MoiseSemanticContract.OrganizationSemantic> organizations, NativeProjectionMode mode) throws Exception {
        return new CodeGroundedNativePipeline().build(withOrganizations(CodeGroundedTestFixtures.helloSnapshot(), organizations), mode);
    }
    private static ModelSnapshot load(Path entry) throws Exception { return new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(entry), entry); }
    private static ModelSnapshot withOrganizations(ModelSnapshot base, List<MoiseSemanticContract.OrganizationSemantic> organizations) {
        var c = base.semanticContract();
        var semantic = new JacamoSemanticSnapshot(c.contractVersion(), c.project(), c.agentDeclarations(), c.workspaceDeclarations(), c.artifactDeclarations(),
                c.organizationDeployments(), c.groupDeployments(), c.schemeDeployments(), c.institutionDeployments(), c.rawRoleTuples(), c.rawFocusTuples(),
                c.importProvenance(), c.jasonPrograms(), c.cartagoEnvironments(), organizations, c.exactBindings(), c.diagnostics());
        return new ModelSnapshot("moise-projection-test", base.sources(), base.agentDeclarations(), base.workspaces(), base.configuredArtifacts(),
                base.organisationFacts(), base.groupRoleCardinalities(), base.parentSubGroupCardinalities(), base.crossDimensionalRelations(), base.unresolvedFacts(), base.projectionProvenance(), semantic);
    }
}
