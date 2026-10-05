package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.ASSyntax;
import jason.runtime.Settings;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;

class JasonLiteralObservationTest {
    @Test void provenanceComesFromAuthoredAstAndOfficialAnnotations(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        var file=directory.resolve("agent.asl");
        java.nio.file.Files.writeString(file,"domain(0).\nfocused(domain).\n+!work <- +changed(1).\n");
        var program=new OfficialJasonAdapter().adapt(directory,file,"generic","worker").program();
        assertTrue(program.beliefs().stream().filter(b->b.literal().equals("domain(0)")).allMatch(b->b.metadata().sourceKind().equals("JASON_DOMAIN_BELIEF")));
        var authored=JasonBeliefEvidence.authoredPredicates(program);
        assertEquals(Boolean.TRUE,JasonBeliefEvidence.provenance(ASSyntax.parseLiteral("changed(5)[source(self)]"),authored).get("domainAuthored"));
        var similar=JasonBeliefEvidence.provenance(ASSyntax.parseLiteral("focused(domain)[source(self)]"),authored);
        assertEquals(Boolean.TRUE,similar.get("domainAuthored"));assertEquals(Boolean.FALSE,similar.get("authoritativeElsewhere"));
        assertEquals(Boolean.FALSE,JasonBeliefEvidence.provenance(ASSyntax.parseLiteral("internal(5)[source(self)]"),authored).get("domainAuthored"));
        assertEquals(Boolean.TRUE,JasonBeliefEvidence.provenance(ASSyntax.parseLiteral("changed(5)[source(percept),artifact_id(cobj_1)]"),authored).get("authoritativeElsewhere"));
        assertEquals(Boolean.FALSE,JasonBeliefEvidence.provenance(ASSyntax.parseLiteral("changed(5)[hide_in_mind_inspector,source(self)]"),authored).get("domainAuthored"));
        assertFalse(JasonBeliefEvidence.projectSource("jar:file:/runtime.jar!/library.asl"));
        assertFalse(JasonBeliefEvidence.projectSource("project:/../external.asl"));
    }
    @Test void equalGoalLiteralsKeepDistinctOccurrenceIdentitiesAndIncompleteCutIsExplicit() throws Exception {
        var source=Path.of("../use-plugin/src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath();
        var project=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(source),source).semanticContract();
        var events=new ArrayList<RuntimeEvent>(); var agent=new Agent(); agent.initAg();
        var circumstance=new Circumstance(); var architecture=new BridgeAgArch();
        architecture.setTS(new TransitionSystem(agent,circumstance,new Settings(),architecture));
        try(var attached=BridgeRuntimeRegistry.attach(events::add)) {
            BridgeRuntimeRegistry.configureDeclarations(project); architecture.init();
            BridgeRuntimeRegistry.registerAgent(architecture.getAgName(),BridgeRuntimeRegistry.agentIdentity(architecture.getAgName()).orElseThrow(),project.agentDeclarations().getFirst().name());
            agent.getBB().add(ASSyntax.parseLiteral("ready(a)")); agent.getBB().add(ASSyntax.parseLiteral("ready(b)"));
            circumstance.addEvent(new Event(ASSyntax.parseTrigger("+!deliver(a)"),Intention.EmptyInt));
            circumstance.addEvent(new Event(ASSyntax.parseTrigger("+!deliver(a)"),Intention.EmptyInt));
            architecture.reasoningCycleFinished();
            var observed=BridgeRuntimeRegistry.jasonState(architecture.getAgName()).orElseThrow();
            assertEquals(2,((List<?>)observed.get("beliefs")).size());
            var goals=(List<?>)observed.get("goals"); assertEquals(2,goals.size());
            assertEquals(2,goals.stream().map(g->((Map<?,?>)g).get("semanticId")).distinct().count());
            architecture.reasoningCycleFinished(); assertEquals(observed,BridgeRuntimeRegistry.jasonState(architecture.getAgName()).orElseThrow());
            BridgeRuntimeRegistry.invalidateJasonState(architecture.getAgName(),"JASON_CYCLE_OBSERVATION_INCOMPLETE:fixture");
            assertTrue(BridgeRuntimeRegistry.jasonState(architecture.getAgName()).isEmpty()); assertTrue(BridgeRuntimeRegistry.jasonStateError(architecture.getAgName()).isPresent());
            architecture.reasoningCycleFinished(); assertEquals(observed,BridgeRuntimeRegistry.jasonState(architecture.getAgName()).orElseThrow());
            assertTrue(BridgeRuntimeRegistry.jasonStateError(architecture.getAgName()).isEmpty());
            assertTrue(events.stream().anyMatch(e->e.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY && e.completeness()==Completeness.COMPLETE));
        } finally { architecture.stop(); BridgeRuntimeRegistry.clearAgents(); }
    }
}
