package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

class XmlTaskDemandTest {
    @TempDir Path temp;
    Path project=GenericMappingEngineTest.JCM;
    static final String TABLE="""
      <table id="demands" dataset="raw-artifacts"><column name="domain" path="domain" required="true"/><column name="player" path="player" required="true"/>
       <column name="required" path="required" required="false"/><column name="activation" path="activation" required="false"/></table>
      """;
    static final String DEMAND="""
      <taskDemands><demand id="execution-demand" target="Positive" scopeRef="delivery" tableRef="demands" anchorColumn="domain" playerColumn="player" requiredColumn="required" episodeFields="activation"/></taskDemands>
      """;
    record Fixture(XmlOccurrenceProjectionTest helper,XmlOccurrenceProjectionTest.Fixture nativeFixture,TaskDemandProjection demands) { }
    Fixture fixture()throws Exception {
        return fixture(java.util.function.UnaryOperator.identity());
    }
    Fixture fixture(java.util.function.UnaryOperator<String> configure)throws Exception {
        var helper=new XmlOccurrenceProjectionTest();helper.temp=temp;helper.project=project;
        var f=helper.fixture(true,s->configure.apply(s.replace("<goal id=\"positive\" name=\"Positive\" type=\"Achieve\"><condition>self.p >= 0</condition></goal>","<task id=\"positive\" name=\"Positive\"><pre>self.p >= 0</pre><post>self.p = self.p@pre + 1</post></task>")
            .replace("</tables>",TABLE+"</tables>").replace("</runtime>",DEMAND+"</runtime>")));
        f.projection().apply(f.nativeFixture().port(),f.epoch(),"cut-1",helper.tables(f,Completeness.COMPLETE,false));
        var spec=MappingXmlReaderTest.parse(Files.readString(temp.resolve("mapping.xml")));
        return new Fixture(helper,f,new TaskDemandProjection(spec,f.nativeFixture().engine().canonicalProgramHash()));
    }
    RuntimeTables.Result rows(Fixture f,Completeness population,Object requiredA,String activation) {
        var a=new LinkedHashMap<String,Object>();a.put("domain","parcel");a.put("player","player:alpha");a.put("activation",activation);if(requiredA!=null)a.put("required",requiredA);
        return new RuntimeTables.Result(List.of(f.helper().row("demand-a",a,Completeness.COMPLETE),f.helper().row("demand-b",Map.of("domain","parcel","player","player:beta","required",false,"activation","first"),Completeness.COMPLETE)),population,List.of());
    }
    void apply(Fixture f,RuntimeTables.Result rows) {
        var n=f.nativeFixture();f.demands().apply(n.nativeFixture().port(),n.epoch(),"cut-1",Map.of("demands",rows),n.projection().resolve(n.nativeFixture().port(),n.epoch()));
    }
    List<TaskDemandProjection.Demand> read(Fixture f){return f.demands().resolve(f.nativeFixture().nativeFixture().port(),f.nativeFixture().epoch());}
    @Test void demandSelectionCannotBorrowAnotherExecutionOnTheSameAnchorAndPlayer()throws Exception {
        var f=fixture(s->s.replace("requiredColumn=\"required\" episodeFields", "requiredColumn=\"required\" scopeColumn=\"execution\" scopeKey=\"group\" episodeFields")
            .replace("<column name=\"activation\" path=\"activation\" required=\"false\"/>","<column name=\"activation\" path=\"activation\" required=\"false\"/><column name=\"execution\" path=\"execution\" required=\"true\"/>"));
        var data=new ArrayList<RuntimeTables.Row>();
        for(String player:List.of("alpha","beta"))for(String execution:List.of("team","other-team"))data.add(f.helper().row(execution+player,Map.of("domain","parcel","player","player:"+player,"required",execution.equals("team"),"activation",execution,"execution",execution),Completeness.COMPLETE));
        apply(f,new RuntimeTables.Result(data,Completeness.COMPLETE,List.of()));assertEquals(2,read(f).size());assertTrue(read(f).stream().allMatch(d->Boolean.TRUE.equals(d.required())&&d.quality()==Completeness.COMPLETE));
        apply(f,new RuntimeTables.Result(data.stream().filter(r->r.values().get("execution").equals("other-team")).toList(),Completeness.COMPLETE,List.of()));
        assertTrue(read(f).stream().allMatch(d->d.required()==null),"A foreign execution's false demand cannot become a local inactive value");
    }
    @Test void onlyExplicitQualifiedBooleanAndActivationIdentifyDemandOnSameNativeSavepoint()throws Exception {
        var f=fixture();apply(f,rows(f,Completeness.COMPLETE,true,"first"));var initial=read(f);assertEquals(2,initial.size());
        assertEquals(Set.of(true,false),initial.stream().map(TaskDemandProjection.Demand::required).collect(java.util.stream.Collectors.toSet()));assertTrue(initial.stream().allMatch(d->d.quality()==Completeness.COMPLETE));
        var before=f.nativeFixture().nativeFixture().engine().savepoint();apply(f,rows(f,Completeness.COMPLETE,null,"first"));
        assertNull(read(f).stream().filter(d->Boolean.TRUE.equals(initial.stream().filter(old->old.player().equals(d.player())).findFirst().orElseThrow().required())).findFirst().orElseThrow().required());
        f.nativeFixture().nativeFixture().engine().rollback(before);assertEquals(initial,read(f));
        apply(f,rows(f,Completeness.COMPLETE,true,"second"));var later=read(f);
        for(var old:initial){var next=later.stream().filter(d->d.player().equals(old.player())).findFirst().orElseThrow();if(Boolean.TRUE.equals(old.required()))assertNotEquals(old.episode(),next.episode());else assertEquals(old.episode(),next.episode());}
        var n=f.nativeFixture();n.nativeFixture().port().metadata(n.roles().getFirst(),"xml:m0:lifetime","rejoined-interval");n.projection().apply(n.nativeFixture().port(),n.epoch(),"cut-1",f.helper().tables(n,Completeness.COMPLETE,false));
        apply(f,rows(f,Completeness.COMPLETE,true,"second"));assertEquals(2,read(f).size(),"Retire the old demand interval of this same native role; do not accumulate stale slots on re-enactment");
        assertTrue(f.demands().resolve(f.nativeFixture().nativeFixture().port(),OccurrenceProjection.epoch("restart",2,"revision")).isEmpty());
    }
    @Test void partialDuplicateAndInvalidValuesCannotDefaultToInactiveOrBorrowAnotherPlayersDemand()throws Exception {
        var f=fixture();apply(f,rows(f,Completeness.PARTIAL,true,"first"));assertTrue(read(f).stream().allMatch(d->d.required()==null&&d.quality()!=Completeness.COMPLETE));
        apply(f,rows(f,Completeness.COMPLETE,"true","first"));assertNull(read(f).stream().filter(d->d.player().contains("alpha")).findFirst().orElseThrow().required());
        var rows=rows(f,Completeness.COMPLETE,true,"first");var doubled=new ArrayList<>(rows.rows());doubled.add(rows.rows().getFirst());
        var before=f.nativeFixture().nativeFixture().engine().savepoint();assertThrows(IllegalArgumentException.class,()->apply(f,new RuntimeTables.Result(doubled,Completeness.COMPLETE,List.of())));
        assertEquals(before.bindings().toMap(),f.nativeFixture().nativeFixture().engine().savepoint().bindings().toMap(),"Reject ambiguous demand before metadata writes");
    }
}
