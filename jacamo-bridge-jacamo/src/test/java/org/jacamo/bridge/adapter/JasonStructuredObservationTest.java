package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.runtime.Settings;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.CanonicalJson;

class JasonStructuredObservationTest {
    @Test void completeStoredCutPreservesNamespaceNegationAnnotationRulesAndNumericTermIdentity()throws Exception {
        var agent=new Agent();agent.initAg();for(String literal:List.of("price(10)[source(percept),sensor(a)]","~price(10)[source(self)]","shop::price(12)"))agent.getBB().add(ASSyntax.parseLiteral(literal));agent.getBB().add(ASSyntax.parseRule("eligible(X) :- price(X)."));
        var observed=JasonCycleObservation.mental(agent,"agent",7);assertEquals(Boolean.TRUE,observed.get("complete"));var beliefs=(List<?>)observed.get("beliefs");assertEquals(4,beliefs.size());
        assertTrue(beliefs.stream().map(CanonicalJson::object).anyMatch(b->b.get("namespace").equals("shop")));assertTrue(beliefs.stream().map(CanonicalJson::object).anyMatch(b->b.get("negative").equals(true)));assertTrue(beliefs.stream().map(CanonicalJson::object).anyMatch(b->b.get("rule").equals(true) && !b.get("ruleBody").toString().isBlank()));
        assertEquals(JasonStructuredTerms.literal(ASSyntax.parseLiteral("price(10)")).get("key"),JasonStructuredTerms.literal(ASSyntax.parseLiteral("price(10.0)[source(percept)]")).get("key"));
        assertNotEquals(JasonStructuredTerms.literal(ASSyntax.parseLiteral("price(10)")).get("key"),JasonStructuredTerms.literal(ASSyntax.parseLiteral("shop::price(10)")).get("key"));
        assertThrows(UnsupportedOperationException.class,()->((List)observed.get("beliefs")).clear());
    }
    @Test void logicalGroundFragmentUsesOfficialAstAndRejectsOpenAnnotatedInternalOrArithmeticQueries()throws Exception {
        var compiler=new JasonGroundQueryCompiler();for(String source:List.of("price(10)","shop::price(10) & not ~price(10)","true","false","price(10) | ready"))assertNotEquals("UNSUPPORTED",CanonicalJson.object(compiler.compile(source).get("ast")).get("op"),source);
        for(String source:List.of("price(X)","price(10)[source(percept)]",".print(hello)","10 > 2"))assertEquals("UNSUPPORTED",CanonicalJson.object(compiler.compile(source).get("ast")).get("op"),source);
    }
    @Test void nestedAndSuspendedIntendedMeansAndEqualTestEventsKeepSeparateOccurrenceTokens()throws Exception {
        var agent=new Agent();agent.initAg();var circumstance=new Circumstance();var architecture=new jason.architecture.AgArch();var ts=new TransitionSystem(agent,circumstance,new Settings(),architecture);
        var intention=new Intention();var parent=ASSyntax.parsePlan("+!outer <- act(1).");var child=ASSyntax.parsePlan("+?inner : ready <- act(2).");intention.push(new IntendedMeans(new Option(parent,new Unifier()),parent.getTrigger()));intention.push(new IntendedMeans(new Option(child,new Unifier()),child.getTrigger()));intention.setSuspended(true);circumstance.addRunningIntention(intention);
        circumstance.addEvent(new Event(ASSyntax.parseTrigger("+?probe"),Intention.EmptyInt));circumstance.addEvent(new Event(ASSyntax.parseTrigger("+?probe"),Intention.EmptyInt));circumstance.addEvent(new Event(ASSyntax.parseTrigger("-!failed"),Intention.EmptyInt));
        var tokens=new IdentityHashMap<Object,String>();java.util.function.Function<Object,String> ids=o->tokens.computeIfAbsent(o,k->"occurrence:"+tokens.size());var cut=JasonCycleObservation.execution(ts,"agent",7,ids);var intents=(List<?>)cut.get("intentions");assertEquals(1,intents.size());var row=CanonicalJson.object(intents.getFirst());assertEquals(true,row.get("suspended"));assertEquals(2,((List<?>)row.get("means")).size());
        var events=(List<?>)cut.get("events");assertEquals(3,events.size());assertEquals(3,events.stream().map(CanonicalJson::object).map(e->e.get("occurrence")).distinct().count());assertEquals(cut,JasonCycleObservation.execution(ts,"agent",7,ids));
        var trigger=CanonicalJson.object(CanonicalJson.object(events.getFirst()).get("trigger"));assertEquals("test",trigger.get("type"));assertTrue(events.stream().map(CanonicalJson::object).map(e->CanonicalJson.object(e.get("trigger"))).anyMatch(t->t.get("operator").equals("del")));
    }
}
