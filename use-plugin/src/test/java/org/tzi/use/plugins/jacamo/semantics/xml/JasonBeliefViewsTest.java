package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import org.junit.jupiter.api.Test;
import jason.asSemantics.Agent;
import jason.asSyntax.ASSyntax;
import org.jacamo.bridge.adapter.JasonCycleObservation;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources;
import static org.junit.jupiter.api.Assertions.*;

class JasonBeliefViewsTest {
    @Test void lexicalProviderPreservesTheSameStoredLiteralsWithoutDuplicatingTermTrees()throws Exception {
        var actual=new Agent();actual.initAg();
        for(String literal:List.of("~owns(a(1,[two,three]))[source(sensor),meta(x)]","inventory::owns(two)[source(sensor)]","alias(X) :- owns(X)."))actual.getBB().add(literal.contains(":-")?ASSyntax.parseRule(literal):ASSyntax.parseLiteral(literal));
        var lexical=JasonCycleObservation.storedLiterals(actual,PLAYER.canonical(),7);
        var full=JasonCycleObservation.mental(actual,PLAYER.canonical(),7);
        var old=capture(xml("STORED","*","*"),agent(full,Completeness.COMPLETE),Completeness.COMPLETE).rows().getFirst();
        var fact=new RuntimeFact(PLAYER,RuntimeFactKind.AGENT,Map.of("name","alice","cycles",7,"storedBeliefObservation",lexical),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var row=capture(xml("STORED","*","*"),fact,Completeness.COMPLETE).rows().getFirst();
        assertEquals(Completeness.COMPLETE,row.quality());assertEquals(old.values().get("beliefJson"),row.values().get("beliefJson"));
        for(Object literal:(List<?>)lexical.get("beliefs")){var item=CanonicalJson.object(literal);assertFalse(item.containsKey("terms"));assertTrue(item.get("raw") instanceof String);assertTrue(item.get("arity") instanceof Number);}
    }
    private static final BridgeEntityId PLAYER=new BridgeEntityId("jason","agent","runtime-agent","project","alice","incarnation-one");
    private static String xml(String selection,String predicate,String arity) {
        return MappingXmlReaderTest.XML.replace("<runtime/>","<runtime><datasets><beliefViews id=\"knowledge\" namespace=\"*\" predicate=\""+predicate+"\" arity=\""+arity+"\" selection=\""+selection+"\"/></datasets></runtime>");
    }
    private static RuntimeFact agent(Map<String,Object> mental,Completeness quality) {
        return new RuntimeFact(PLAYER,RuntimeFactKind.AGENT,Map.of("name","alice","cycles",7,"mentalObservation",mental),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,quality,List.of());
    }
    private static Map<String,Object> mental(String... literals)throws Exception {
        Agent agent=new Agent();agent.initAg();
        for(String literal:literals)agent.getBB().add(literal.contains(":-")?ASSyntax.parseRule(literal):ASSyntax.parseLiteral(literal));
        return JasonCycleObservation.mental(agent,PLAYER.canonical(),7);
    }
    private static org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables.Dataset capture(String xml,RuntimeFact fact,Completeness population) {
        var sources=new RuntimeFactSources(MappingXmlReaderTest.parse(xml));
        return sources.capture(RuntimeFactSourcesTest.cut(List.of(fact),Map.of("jason",population)),"run",1).get("knowledge");
    }
    @Test void actualStoredLiteralViewsPreserveNamespacesNegationAnnotationsAndDistinctArguments()throws Exception {
        var full=mental("running(a1)[source(percept)]","running(a2)[source(percept)]","~running(a3)[source(bob)]","other::running(a4)[source(self)]","different(1)");
        var result=capture(xml("FACTS","running","1"),agent(full,Completeness.COMPLETE),Completeness.COMPLETE);
        assertEquals(Completeness.COMPLETE,result.rows().getFirst().quality());
        String json=(String)result.rows().getFirst().values().get("beliefJson");
        assertEquals(4,((List<?>)CanonicalJson.decode(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))).size());
        assertTrue(json.contains("a1"));assertTrue(json.contains("a2"));assertTrue(json.contains("~running"));assertTrue(json.contains("other::running"));assertFalse(json.contains("different"));
        assertEquals(PLAYER.canonical(),result.rows().getFirst().values().get("player"));
        var fields=RuntimeFactSources.producerFields(MappingXmlReaderTest.parse(xml("STORED","*","*"))).get("jason");
        assertTrue(fields.containsAll(List.of("cycles","storedBeliefObservation")));
    }
    @Test void completeEmptyIsDistinctFromMissingPartialForeignOwnerAndWrongCycle()throws Exception {
        var full=mental("something(1)");
        assertEquals("[]",capture(xml("FACTS","absent","2"),agent(full,Completeness.COMPLETE),Completeness.COMPLETE).rows().getFirst().values().get("beliefJson"));
        for(var bad:List.of(Map.<String,Object>of(),new LinkedHashMap<>(full){{put("complete",false);}},new LinkedHashMap<>(full){{put("owner","other-player");}},new LinkedHashMap<>(full){{put("cycle",6);}})) {
            var row=capture(xml("FACTS","absent","2"),agent(bad,Completeness.COMPLETE),Completeness.COMPLETE).rows().getFirst();
            assertNotEquals(Completeness.COMPLETE,row.quality());assertFalse(row.values().containsKey("beliefJson"));
        }
        var partial=capture(xml("STORED","*","*"),agent(full,Completeness.PARTIAL),Completeness.PARTIAL);
        assertNotEquals(Completeness.COMPLETE,partial.populationQuality());assertFalse(partial.rows().getFirst().values().containsKey("beliefJson"));
        assertThrows(IllegalArgumentException.class,()->new RuntimeFactSources(MappingXmlReaderTest.parse(xml("ENTAILMENT","*","*"))));
    }
    @Test void storedRulesStayRulesAndNoEntailedAnswersAreMaterialized()throws Exception {
        var full=mental("source_value(1)","alias(X) :- source_value(X).");
        assertEquals("[]",capture(xml("FACTS","alias","1"),agent(full,Completeness.COMPLETE),Completeness.COMPLETE).rows().getFirst().values().get("beliefJson"));
        String rules=(String)capture(xml("RULES","alias","1"),agent(full,Completeness.COMPLETE),Completeness.COMPLETE).rows().getFirst().values().get("beliefJson");
        assertTrue(rules.contains(":-"));assertFalse(rules.contains("alias(1)"));
    }
}
