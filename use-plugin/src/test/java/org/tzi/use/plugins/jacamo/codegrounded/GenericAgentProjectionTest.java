package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.uml.ocl.value.StringValue;

class GenericAgentProjectionTest {
    @Test void jcmInitialLiteralsUseOfficialJasonParserAndKeepDeclarationEvidence(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        // Official project source resolution can retain an ASL stream on Windows.
        // Run the real parser and assertions in a child JVM so its handles close at exit.
        var process=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),getClass().getName(),directory.toString())
                .redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS),"initial literal parser timeout");
            String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0,process.exitValue(),output); assertTrue(output.contains("JCM_INITIAL_LITERALS_PASS"),output);
        } finally { if(process.isAlive()) process.destroyForcibly(); }
    }
    public static void main(String[] args) throws Exception {
        checkInitialLiterals(java.nio.file.Path.of(args[0]));
        System.out.println("JCM_INITIAL_LITERALS_PASS");
    }
    private static void checkInitialLiterals(java.nio.file.Path directory) throws Exception {
        java.nio.file.Files.writeString(directory.resolve("agent.asl"),"source_ready.\n!source_goal.\n+!source_goal <- .print(\"ok\").\n");
        var jcm=directory.resolve("generic.jcm");
        java.nio.file.Files.writeString(jcm,"mas generic { agent worker: agent.asl {\n beliefs: ready(a)\n ready(b)\n q(X)\n goals: deliver(a)\n deliver(b)\n } }\n");
        var source=new org.jacamo.bridge.adapter.OfficialProjectAdapter().adapt(new org.jacamo.bridge.adapter.OfficialProjectLoader().load(jcm),jcm);
        var program=source.semanticContract().jasonPrograms().getFirst();
        assertEquals(3,program.beliefs().size()); assertEquals(3,program.goals().size()); assertEquals(1,program.beliefRules().size());
        assertTrue(program.beliefs().stream().filter(b->b.literal().startsWith("ready(")).allMatch(b->b.metadata().evidence().stream()
                .allMatch(e->e.sourceDigest().equals(source.semanticContract().project().sourceDigest()))));
        var projected=new CodeGroundedNativePipeline().build(source); var state=projected.state().system().state();
        assertEquals(0,state.objectsOfClass(projected.model().model().getClass("Belief")).size());
        demandBeliefs(projected);
        assertEquals(3,state.objectsOfClass(projected.model().model().getClass("Belief")).size());
        assertEquals(3,state.objectsOfClass(projected.model().model().getClass("AgentGoal")).size());
        assertNull(projected.model().model().getClass("BeliefRule"));
        assertTrue(state.allObjects().stream().filter(o->o.cls().name().equals("Belief"))
                .noneMatch(o->o.state(state).attributeValue("literal").toString().contains("q(")));
    }
    @Test void productionProjectionHasNoCaseSelectors() throws Exception {
        for(String relative:List.of("use/NativeProjectionPolicy.java","use/DomainProjection.java",
                "use/NativeUseModelBuilder.java","use/NativeUseStateBuilder.java","use/MoiseDomainProjection.java",
                "runtime/NativeRuntimeMutationEngine.java","runtime/CodeGroundedRuntimeRuleRegistry.java")) {
            String source=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/org/tzi/use/plugins/jacamo/codegrounded",relative));
            assertFalse(java.util.regex.Pattern.compile("(?i)\\b(auction|auctioneer|participant|helloworld|hello-world|house-building)\\b").matcher(source).find(),relative);
        }
    }
    @Test void onePolicyRejectsUnknownConceptsAndNeverReintroducesExecutionClasses() throws Exception {
        assertEquals(NativeProjectionPolicy.Decision.UNSUPPORTED, NativeProjectionPolicy.decide("UnknownRuntimeThing"));
        for (var mode : NativeProjectionMode.values()) {
            var result = new CodeGroundedNativePipeline().build(CodeGroundedTestFixtures.helloSnapshot(), mode);
            assertEquals(NativeProjectionStatus.UNSUPPORTED, result.model().profile().statusFor("UnknownRuntimeThing"));
            assertFalse(result.model().profile().materializesClass("UnknownRuntimeThing"));
            for (String base : NativeProjectionPolicy.BASE_CLASSES) assertNotNull(result.model().model().getClass(base), base);
            for (String forbidden : List.of("Plan", "PlanLibrary", "PlanBody", "Event", "Action", "Intention"))
                assertNull(result.model().model().getClass(forbidden), forbidden);
            assertEquals(result.model().structuralHash(), result.export().recompiledStructuralHash());
            assertDoesNotThrow(() -> new NativeProjectionAudit().audit(result.model(), result.state().system()));
        }
    }

    @Test void canonicalProgramTypesAndSimultaneousLiteralOccurrencesHaveExactOwners() throws Exception {
        var base = CodeGroundedTestFixtures.helloSnapshot(); var s = base.semanticContract();
        var p = s.jasonPrograms().get(0);
        var beliefs = List.of(new JasonSemanticContract.BeliefSemantic(metadata(p.metadata(), ":test-belief:0"), 0, "ready(a)"),
                new JasonSemanticContract.BeliefSemantic(metadata(p.metadata(), ":test-belief:1"), 1, "ready(b)"));
        var goals = List.of(new JasonSemanticContract.AgentGoalSemantic(metadata(p.metadata(), ":test-goal:0"), 0, "deliver(a)", "achievement"),
                new JasonSemanticContract.AgentGoalSemantic(metadata(p.metadata(), ":test-goal:1"), 1, "deliver(b)", "achievement"));
        var programs = new ArrayList<>(s.jasonPrograms());
        programs.set(0, new JasonSemanticContract.AgentProgramSemantic(p.metadata(), p.declarationId(), p.sourceUri(), p.sourceDigest(),
                p.planLibrary(), p.actions(), beliefs, goals, p.beliefRules()));
        var semantic = new JacamoSemanticSnapshot(s.contractVersion(), s.project(), s.agentDeclarations(), s.workspaceDeclarations(),
                s.artifactDeclarations(), s.organizationDeployments(), s.groupDeployments(), s.schemeDeployments(), s.institutionDeployments(),
                s.rawRoleTuples(), s.rawFocusTuples(), s.importProvenance(), programs, s.cartagoEnvironments(), s.moiseOrganizations(), s.exactBindings(), s.diagnostics());
        var snapshot = new ModelSnapshot("generic-agent-literals", base.sources(), base.agentDeclarations(), base.workspaces(), base.configuredArtifacts(),
                base.organisationFacts(), base.groupRoleCardinalities(), base.parentSubGroupCardinalities(), base.crossDimensionalRelations(),
                base.unresolvedFacts(), base.projectionProvenance(), semantic);
        var result = new CodeGroundedNativePipeline().build(snapshot); var state = result.state().system().state();
        var engine = demandBeliefs(result);
        var declaration = s.agentDeclarations().stream().filter(a -> a.name().equals(p.declarationId())).findFirst().orElseThrow();
        var agent = result.state().semanticObjectIndex().get(declaration.metadata().semanticId());
        assertTrue(agent.cls().name().endsWith("_Agent")); assertTrue(agent.cls().parents().contains(result.model().model().getClass("Agent")));
        for (var belief : beliefs) {
            String id = DomainProjection.occurrenceId("initial-belief", declaration.metadata().semanticId(), belief.metadata().semanticId());
            var object = engine.objectForSemanticId(id); assertNotNull(object);
            assertEquals(new StringValue(belief.literal()), object.state(state).attributeValue("literal"));
            assertTrue(state.hasLinkBetweenObjects(result.model().model().getAssociation(DomainProjection.relation("hasBelief", "Agent", "Belief")), new org.tzi.use.uml.sys.MObject[]{agent, object}));
            assertTrue(engine.targetBindings().stream().anyMatch(t -> t.sourceIdentity().equals(id) && t.targetIdentity().equals(object.name())));
        }
        for (var goal : goals) {
            String id = DomainProjection.occurrenceId("initial-goal", declaration.metadata().semanticId(), goal.metadata().semanticId());
            var object = result.state().semanticObjectIndex().get(id); assertNotNull(object);
            assertEquals(new StringValue(goal.literal()), object.state(state).attributeValue("literal"));
            assertTrue(state.hasLinkBetweenObjects(result.model().model().getAssociation(DomainProjection.relation("hasGoal", "Agent", "AgentGoal")), new org.tzi.use.uml.sys.MObject[]{agent, object}));
            assertNull(object.cls().attribute("status", true));
        }
        var soil = new NativeUseSoilExporter().export(result.state().system());
        var replay = new NativeUseSoilExporter().replay(result.export().recompiledModel(), soil.commands());
        assertEquals(state.numObjects(), replay.state().numObjects()); assertEquals(state.allLinks().size(), replay.state().allLinks().size());
    }
    private static SemanticMetadata metadata(SemanticMetadata source, String suffix) {
        return new SemanticMetadata(source.semanticId() + suffix, suffix.contains("test-belief") ? "JASON_DOMAIN_BELIEF" : source.sourceKind(), source.sourceJavaFqcn(),
                source.evidenceAuthority(), source.fidelity(), source.capabilityStatus(), source.evidence(), List.of());
    }
    private static org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine demandBeliefs(CodeGroundedNativePipeline.Result pipeline) {
        var system=pipeline.state().system();
        new org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService(system).installSource(
                "beliefs.ocl","context Agent inv BeliefIdentity: self.beliefs->isUnique(semanticId)",pipeline.source().revision());
        var engine=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine(system,pipeline.state().semanticObjectIndex(),
                new org.tzi.use.plugins.jacamo.codegrounded.runtime.CodeGroundedRuntimeRuleRegistry());
        engine.refreshBeliefProjection(); return engine;
    }
}
