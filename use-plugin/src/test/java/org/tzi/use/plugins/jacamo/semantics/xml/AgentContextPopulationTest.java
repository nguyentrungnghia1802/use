package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import static org.junit.jupiter.api.Assertions.*;

class AgentContextPopulationTest {
    @TempDir java.nio.file.Path temp;
    static final String PLAN="""
        <runtime><datasets><facts id="agents" authority="jason" kind="AGENT"/></datasets><tables>
        <table id="contexts" dataset="agents"><column name="player" path="identity" required="true"/><identity mode="PHYSICAL" path="identity"/></table>
        </tables><inputs><input id="context-input" tableRef="contexts" identityKind="PHYSICAL" identityFields="player"><field name="player" type="String" required="true"/></input></inputs>
        <converters/><rule id="single-agent" inputRef="context-input"><requireEvidence proof="source-cut"/><upsertInstance targetType="Team" keyFields="player" playerFrom="player"/></rule></runtime>
        """;
    static RuntimeFact agent(String name){return new RuntimeFact(new BridgeEntityId("jason","agent","runtime-agent","neutral",name,"incarnation-"+name),RuntimeFactKind.AGENT,Map.of("name",name),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());}
    static RuntimeSnapshot cut(List<RuntimeFact> actual,List<RuntimeFact> roster,int sequence,boolean certificate) {
        var base=RuntimeFactSourcesTest.cut(actual,Map.of("jason",Completeness.COMPLETE,"cartago",Completeness.COMPLETE,"moise:organization",Completeness.PARTIAL));
        var marks=new TreeMap<>(base.endWatermarks());marks.put("jason",new SourceWatermark("jason",sequence));
        return new RuntimeSnapshot("cut-"+sequence,base.modelRevision(),base.captureStartedAt(),base.captureEndedAt(),marks,marks,1,actual,base.sourceCompleteness(),"state-"+sequence,
                certificate?Map.of("sourceEvidence",Map.of("jason",Map.of("agentInventory",JasonAgentInventory.payload("run",sequence,roster.stream().map(f->f.id().canonical()).toList())))):Map.of());
    }
    @Test void actualNativeCompatibilityUsesCurrentAgentInventoryAndRejectsLostOrStaleMapping()throws Exception {
        var helper=new M0RuleExecutorTest();helper.temp=temp;
        var f=helper.fixture(PLAN,true,text->text.replace("<runtime/>",PLAN));var engine=f.engine();
        var batch=new XmlRuntimeBatch(engine.canonicalProgram().xmlPlan().inputs().specification(),engine.system().model(),engine.canonicalProgramHash());
        var a=agent("one");var b=agent("two");
        for(var fact:List.of(a,b))f.port().upsert(fact.id().canonical(),"AtomicAgent",(String)fact.values().get("name"));
        batch.apply(f.port(),"run",0,cut(List.of(a,b),List.of(a,b),1,true));
        var type=engine.system().model().getClass("OrgContext");var resolver=new ConstraintCoverageResolver();
        assertTrue(resolver.populationComplete(type,Map.of("cartago",Completeness.COMPLETE,"jason",Completeness.COMPLETE,"moise:organization",Completeness.PARTIAL),engine));
        var invariant=engine.system().model().getClassInvariant("OrgContext::CslV4RoleCompatibility");assertNotNull(invariant);
        var prepared=new SemanticInvariantEvaluator().prepareScoped("native-compatibility",invariant,engine,Map.of("cartago",Completeness.COMPLETE,"jason",Completeness.COMPLETE),false,Set.of(),"");
        assertEquals(SemanticVerdict.PASS,SemanticInvariantEvaluator.evaluateScoped(prepared,engine.system().state()).getLast().verdict());
        var savepoint=engine.savepoint();var context=engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("Team")).findFirst().orElseThrow();
        f.port().delete(f.port().metadata(context,"semanticId"));
        assertFalse(MappedPopulationInventory.complete(type,engine),"A dropped mapped object cannot be hidden by a complete source roster");
        engine.rollback(savepoint);assertTrue(MappedPopulationInventory.complete(type,engine));
        assertThrows(IllegalArgumentException.class,()->batch.apply(f.port(),"run",0,cut(List.of(a),List.of(a,b),2,true)),"A contradicted source roster is a protocol error, not a partial success");
        batch.apply(f.port(),"run",0,cut(List.of(a,b),List.of(a,b),3,false));
        assertFalse(MappedPopulationInventory.complete(type,engine),"Legacy/event-prefix facts cannot borrow a previous snapshot certificate");
        batch.apply(f.port(),"run",0,cut(List.of(a,b),List.of(a,b),4,true));assertTrue(MappedPopulationInventory.complete(type,engine));
        batch.apply(f.port(),"run",0,cut(List.of(a),List.of(a),5,true));
        assertFalse(MappedPopulationInventory.complete(type,engine),"A retained old agent context must be withdrawn by an admitted mapping rule, never silently certified");
    }
    @Test void emptyFilteredMappingNeedsTheSameCurrentSourceInventoryAsNonemptyMappings()throws Exception {
        String plan=PLAN.replace("</tables>","""
            <table id="absent-contexts" dataset="agents"><where path="values.name" type="String" equals="unobserved"/>
            <column name="player" path="identity" required="true"/><identity mode="PHYSICAL" path="identity"/></table></tables>
            """).replace("</inputs>","""
            <input id="absent-input" tableRef="absent-contexts" identityKind="PHYSICAL" identityFields="player">
            <field name="player" type="String" required="true"/></input></inputs>
            """).replace("</runtime>","""
            <rule id="absent-program" inputRef="absent-input"><requireEvidence proof="source-cut"/>
            <upsertInstance targetType="Team" keyFields="player" playerFrom="player"/></rule></runtime>
            """);
        var helper=new M0RuleExecutorTest();helper.temp=temp;
        var f=helper.fixture(plan,true,text->text.replace("<runtime/>",plan));var engine=f.engine();
        var batch=new XmlRuntimeBatch(engine.canonicalProgram().xmlPlan().inputs().specification(),engine.system().model(),engine.canonicalProgramHash());
        var actual=agent("one");f.port().upsert(actual.id().canonical(),"AtomicAgent","one");
        var type=engine.system().model().getClass("OrgContext");
        batch.apply(f.port(),"run",0,cut(List.of(actual),List.of(actual),1,true));
        assertTrue(MappedPopulationInventory.complete(type,engine),"An exact filtered empty subset must not invent missing performers");
        batch.apply(f.port(),"run",0,cut(List.of(actual),List.of(actual),2,false));
        assertFalse(MappedPopulationInventory.complete(type,engine),"No certificate means no proof of the empty subset either");
    }

}
