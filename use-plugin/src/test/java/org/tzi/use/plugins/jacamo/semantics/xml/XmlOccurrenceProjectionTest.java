package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.uml.sys.MObject;
import static org.junit.jupiter.api.Assertions.*;

/** Qualified table/native-Port component gate, not a replacement for live admission. */
class XmlOccurrenceProjectionTest {
    @TempDir Path temp;
    Path project=GenericMappingEngineTest.JCM;
    static final String OCCURRENCES="""
      <runtime><tables>
       <table id="scopes" dataset="unused"><column name="group" path="group" required="true"/><column name="domain" path="domain" required="true"/></table>
       <table id="cast" dataset="unused"><column name="group" path="group" required="true"/><column name="player" path="player" required="true"/><column name="role" path="role" required="true"/></table>
      </tables><occurrences><scope id="delivery" tableRef="scopes" anchorColumn="domain" contextColumn="group" keyFields="group domain">
       <actor actor="Worker" tableRef="cast" scopeColumn="group" scopeKey="group" playerColumn="player" roleColumn="role" kind="ROLE_OCCURRENCE" cardinality="MANY_NONEMPTY"/>
       <self target="Positive" origin="COLUMN" column="domain"/>
      </scope></occurrences></runtime>
      """;
    record Fixture(M0RuleExecutorTest.Fixture nativeFixture,OccurrenceProjection projection,String epoch,MObject domain,MObject context,List<MObject> players,List<MObject> roles) { }
    Fixture fixture()throws Exception {
        return fixture(false);
    }
    Fixture fixture(boolean frozen)throws Exception {
        return fixture(frozen,java.util.function.UnaryOperator.identity());
    }
    Fixture fixture(boolean frozen,java.util.function.UnaryOperator<String> configure)throws Exception {
        String runtime=XmlRuntimeMappingTest.PLAN.replace("</tables>",OCCURRENCES.substring(OCCURRENCES.indexOf("<tables>")+8,OCCURRENCES.indexOf("</tables>")).replace("dataset=\"unused\"","dataset=\"raw-artifacts\"")+"</tables>")
            .replace("</runtime>",OCCURRENCES.substring(OCCURRENCES.indexOf("<occurrences>"),OCCURRENCES.indexOf("</runtime>"))+"</runtime>");
        var helper=new M0RuleExecutorTest();helper.temp=temp;helper.project=project;var f=helper.fixture(M0RuleExecutorTest.RUNTIME,true,s->configure.apply(frozen?s.replace("<runtime/>",runtime).replace("roleBindingRef=\"actor-binding\"","roleBindingRef=\"role-binding\""):s));String epoch=OccurrenceProjection.epoch("run",1,GenericMappingEngineTest.official(project).modelRevision());
        var domain=f.rules().execute(f.port(),"create",new M0RuleExecutor.Input("objects",epoch,"physical:parcel","cut-1",Completeness.COMPLETE,Map.of("uuid","parcel","label","parcel","flag",true),Map.of("actual-field-cut","proof"))).object();f.port().alias("parcel",domain);
        var context=f.port().upsert("team","Team","TeamInstance");
        for(String[] field:List.of(new String[]{"xml:m0:program",f.engine().canonicalProgramHash()},new String[]{"xml:m0:epoch",epoch},new String[]{"xml:m0:quality","COMPLETE"},new String[]{"xml:m0:cut","cut-1"}))f.port().metadata(context,field[0],field[1]);
        var players=new ArrayList<MObject>();var roles=new ArrayList<MObject>();
        for(String name:List.of("alpha","beta")) {
            var player=f.port().upsert("player:"+name,"AtomicAgent","Player_"+name);var role=f.port().upsert("role:"+name,"Worker","Role_"+name);players.add(player);roles.add(role);
            f.port().connect("Plays",player,role);f.port().connect("InContext",context,role);
            f.port().metadata(role,"rolePlayer",f.port().metadata(player,"semanticId"));f.port().metadata(role,"roleContext",f.port().metadata(context,"semanticId"));f.port().metadata(role,"xml:m0:lifetime","interval:"+name);
            for(String key:List.of("xml:m0:program","xml:m0:epoch","xml:m0:quality","xml:m0:cut"))f.port().metadata(role,key,f.port().metadata(context,key));
        }
        String xml=Files.readString(temp.resolve("mapping.xml")).replace("<runtime/>",OCCURRENCES).replace("roleBindingRef=\"actor-binding\"","roleBindingRef=\"role-binding\"");
        return new Fixture(f,new OccurrenceProjection(MappingXmlReaderTest.parse(xml),f.engine().system().model(),f.engine().canonicalProgramHash()),epoch,domain,context,players,roles);
    }
    RuntimeTables.Row row(String id,Map<String,Object> values,Completeness quality){return new RuntimeTables.Row(id,values,quality,Map.of("source-cut","accepted"),Set.of(id));}
    Map<String,RuntimeTables.Result> tables(Fixture f,Completeness population,boolean tamper) {
        var scopes=new RuntimeTables.Result(List.of(row("scope-row",Map.of("domain","parcel","group","team"),Completeness.COMPLETE)),Completeness.COMPLETE,List.of());
        var rows=new ArrayList<RuntimeTables.Row>();for(int i=0;i<2;i++)rows.add(row("cast-row-"+i,Map.of("group","team","player","player:"+(tamper&&i==1?"alpha":i==0?"alpha":"beta"),"role","role:"+(i==0?"alpha":"beta")),Completeness.COMPLETE));
        return Map.of("scopes",scopes,"cast",new RuntimeTables.Result(rows,population,List.of()));
    }
    @Test void declaredPlayersUseExactNativeEnactmentAndSameRollbackOwnedMetadata()throws Exception {
        var f=fixture();var nativeState=f.nativeFixture().engine().system().state();int objects=nativeState.numObjects();
        f.projection().apply(f.nativeFixture().port(),f.epoch(),"cut-1",tables(f,Completeness.COMPLETE,false));
        var scope=f.projection().resolve(f.nativeFixture().port(),f.epoch()).getFirst();assertTrue(scope.complete(),scope.unavailable().toString());assertEquals(2,scope.members().size());
        assertEquals(2,scope.members().stream().map(m->m.player()).distinct().count());assertEquals(2,scope.members().stream().map(m->m.interval()).distinct().count());
        assertTrue(scope.members().stream().allMatch(m->m.selves().get("Positive").equals(f.nativeFixture().port().metadata(f.domain(),"semanticId"))));assertEquals(objects,nativeState.numObjects());
        var before=f.nativeFixture().engine().savepoint();
        f.projection().apply(f.nativeFixture().port(),f.epoch(),"cut-1",tables(f,Completeness.COMPLETE,true));
        assertFalse(f.projection().resolve(f.nativeFixture().port(),f.epoch()).getFirst().complete(),"A's source row cannot borrow B's native role");
        f.nativeFixture().engine().rollback(before);assertEquals(scope,f.projection().resolve(f.nativeFixture().port(),f.epoch()).getFirst());
        assertTrue(f.projection().resolve(f.nativeFixture().port(),OccurrenceProjection.epoch("new-run",2,"revision")).isEmpty());
    }
    @Test void declaredNativeLookupMatchesExactTypePlayerContextAndCurrentIntervalWithoutHashRecipes()throws Exception {
        var f=fixture(true,s->s.replace("roleColumn=\"role\"","roleLookup=\"EXACT_NATIVE_ENACTMENT\""));var port=f.nativeFixture().port();
        var original=tables(f,Completeness.COMPLETE,false);var cast=original.get("cast");var rows=cast.rows().stream().map(row->{var values=new LinkedHashMap<>(row.values());values.put("role","display labels are not native keys");return new RuntimeTables.Row(row.identity(),values,row.quality(),row.proofs(),row.aliases());}).toList();
        var selected=new HashMap<>(original);selected.put("cast",new RuntimeTables.Result(rows,cast.populationQuality(),List.of()));
        f.projection().apply(port,f.epoch(),"cut-1",selected);var scope=f.projection().resolve(port,f.epoch()).getFirst();assertTrue(scope.complete(),scope.unavailable().toString());
        assertEquals(2,scope.members().size());
        for(var member:scope.members())assertEquals(member.player(),port.metadata(port.find(member.role()),"rolePlayer"));
        port.disconnect("Plays",f.players().getFirst(),f.roles().getFirst());f.projection().apply(port,f.epoch(),"cut-1",selected);assertFalse(f.projection().resolve(port,f.epoch()).getFirst().complete(),"Metadata does not replace the actual Plays link");port.connect("Plays",f.players().getFirst(),f.roles().getFirst());
        assertThrows(IllegalArgumentException.class,()->f.projection().apply(port,f.epoch(),"cut-1",tables(f,Completeness.COMPLETE,true)),"Two rows for A cannot become two distinct players");
        var shadow=port.upsert("shadow","Worker","Shadow");for(String key:List.of("rolePlayer","roleContext","xml:m0:program","xml:m0:epoch","xml:m0:quality","xml:m0:cut"))port.metadata(shadow,key,port.metadata(f.roles().getFirst(),key));port.metadata(shadow,"xml:m0:lifetime","shadow-interval");port.connect("Plays",f.players().getFirst(),shadow);port.connect("InContext",f.context(),shadow);
        f.projection().apply(port,f.epoch(),"cut-1",selected);assertFalse(f.projection().resolve(port,f.epoch()).getFirst().complete(),"Ambiguous native enactments must not choose the first one");
    }
    @Test void partialOrMissingCastNeverReusesPriorCompletenessAndRoleRecreationChangesEpisode()throws Exception {
        var f=fixture();var port=f.nativeFixture().port();f.projection().apply(port,f.epoch(),"cut-1",tables(f,Completeness.COMPLETE,false));var original=f.projection().resolve(port,f.epoch()).getFirst();
        f.projection().apply(port,f.epoch(),"cut-1",tables(f,Completeness.PARTIAL,false));assertFalse(f.projection().resolve(port,f.epoch()).getFirst().complete());
        f.projection().apply(port,f.epoch(),"cut-1",Map.of("scopes",new RuntimeTables.Result(List.of(),Completeness.PARTIAL,List.of()),"cast",new RuntimeTables.Result(List.of(),Completeness.PARTIAL,List.of())));assertFalse(f.projection().resolve(port,f.epoch()).getFirst().complete());
        port.metadata(f.roles().getFirst(),"xml:m0:lifetime","recreated-interval");f.projection().apply(port,f.epoch(),"cut-1",tables(f,Completeness.COMPLETE,false));var recreated=f.projection().resolve(port,f.epoch()).getFirst();assertTrue(recreated.complete());assertNotEquals(original.revision(),recreated.revision());
    }
    @Test void xmlCastReachesOriginalGoalEngineOnSameAuthoritativeSystemWithoutCanonicalFallback()throws Exception {
        var f=fixture(true);var engine=f.nativeFixture().engine();var port=f.nativeFixture().port();
        port.value(f.domain(),"p",org.tzi.use.uml.ocl.value.IntegerValue.valueOf(1));
        org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.recordPropertyWritten(port,f.domain(),f.domain().cls().attribute("p",true),port.currentValue(f.domain(),"p"));
        f.projection().apply(port,f.epoch(),"cut-1",tables(f,Completeness.COMPLETE,false));
        String revision=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM).modelRevision();
        assertEquals(1,engine.xmlOccurrences("run",1,revision).size());
        try(var owner=new org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner(engine,"run",1,revision)) {
            String hash=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().exportState(engine.system().state()).commands().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var nativeChecks=new org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.PreparedEvaluation(engine.system().model(),List.of());
            var cut=owner.prepare(1,"cut-1",hash,Map.of("cartago",Completeness.COMPLETE),false,true,List.of(),nativeChecks,4096);
            assertEquals(1,cut.casts().size(),"XML profile must select its declared occurrence plan, not the historical groupSpec adapter");
            var result=owner.submit(cut).get(10,java.util.concurrent.TimeUnit.SECONDS);var core=org.jacamo.bridge.contract.CanonicalJson.object(result.evidence().observation().get("originalEngine"));
            var context=org.jacamo.bridge.contract.CanonicalJson.object(((List<?>)core.get("contexts")).getFirst());
            var occurrences=org.jacamo.bridge.contract.CanonicalJson.object(context.get("occurrences"));assertEquals(2,occurrences.size());
            for(var value:occurrences.values()) {var occurrence=org.jacamo.bridge.contract.CanonicalJson.object(value);assertEquals("Positive",occurrence.get("sourceElement"));assertEquals("FULFILLED",occurrence.get("leafStatus"));assertEquals("KNOWN",occurrence.get("quality"));}
            assertTrue(((List<?>)context.get("originalApiCalls")).contains("GoalMarking.update"));assertSame(engine.system(),cut.state().after().system());
        }
    }
    @Test void replacingSelfInsideSameScopeDoesNotInheritAnEarlierObjectsAchievementHistory()throws Exception {
        var f=fixture(true,s->s.replace("anchorColumn=\"domain\" contextColumn=\"group\"","anchorColumn=\"group\" contextColumn=\"group\""));var engine=f.nativeFixture().engine();var port=f.nativeFixture().port();
        port.value(f.domain(),"p",org.tzi.use.uml.ocl.value.IntegerValue.valueOf(1));org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.recordPropertyWritten(port,f.domain(),f.domain().cls().attribute("p",true),port.currentValue(f.domain(),"p"));f.projection().apply(port,f.epoch(),"cut-1",tables(f,Completeness.COMPLETE,false));
        String revision=GenericMappingEngineTest.official(project).modelRevision();try(var owner=new org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner(engine,"run",1,revision)) {
            var first=goalStep(owner,engine,1);assertTrue(first.stream().allMatch(v->v.get("leafStatus").equals("FULFILLED")));
            var next=f.nativeFixture().rules().execute(port,"create",new M0RuleExecutor.Input("objects",f.epoch(),"physical:replacement","cut-1",Completeness.COMPLETE,Map.of("uuid","replacement","label","replacement","flag",true),Map.of("actual-field-cut","proof"))).object();port.unalias("parcel");port.alias("parcel",next);port.value(next,"p",org.tzi.use.uml.ocl.value.IntegerValue.valueOf(-1));org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.recordPropertyWritten(port,next,next.cls().attribute("p",true),port.currentValue(next,"p"));
            f.projection().apply(port,f.epoch(),"cut-1",tables(f,Completeness.COMPLETE,false));var second=goalStep(owner,engine,2);assertTrue(second.stream().allMatch(v->v.get("leafStatus").equals("PENDING")),"A new physical self starts its own original history even under unchanged scope and role identities");
        }
    }
    List<Map<String,Object>> goalStep(org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner owner,org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine engine,int version)throws Exception {
        String hash=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().exportState(engine.system().state()).commands().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var checks=new org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.PreparedEvaluation(engine.system().model(),List.of());var result=owner.submit(owner.prepare(version,"cut-"+version,hash,Map.of("cartago",Completeness.COMPLETE),false,true,List.of(),checks,4096)).get(10,java.util.concurrent.TimeUnit.SECONDS);
        var core=org.jacamo.bridge.contract.CanonicalJson.object(result.evidence().observation().get("originalEngine"));var context=org.jacamo.bridge.contract.CanonicalJson.object(((List<?>)core.get("contexts")).getFirst());return org.jacamo.bridge.contract.CanonicalJson.object(context.get("occurrences")).values().stream().map(org.jacamo.bridge.contract.CanonicalJson::object).toList();
    }
    @Test void authoredResourceBindingsUseCanonicalGoalModelAndRejectUnresolvedResource()throws Exception {
        var f=fixture();var engine=f.nativeFixture().engine();
        String xml=Files.readString(temp.resolve("mapping.xml")).replace("<runtime/>",OCCURRENCES)
            .replace("roleBindingRef=\"actor-binding\"","roleBindingRef=\"role-binding\"")
            .replaceFirst("(?s)<istar .*?</istar>","<istar name=\"Requirements\"/>")
            .replace("<selectors>","<models mode=\"AUTHORED\" cslSha256=\""+"1".repeat(64)+"\" istarSha256=\""+"2".repeat(64)+"\"/><selectors>")
            .replaceFirst("(?s)<modelRules .*?</modelRules>","<modelRules targetVersion=\"v4.0\" targetModel=\"CounterView\"/>")
            .replace("</scope>","<resource target=\"tool\" column=\"domain\" type=\"Counter\"/></scope>");
        var goals=new org.tzi.use.semantic.engine.frontend.IStarFrontend().compile("istar Requirements { role Worker { goal Positive : Achieve condition {[ true ]} resource tool } }","authored.istar").requireModel();
        var spec=MappingXmlReaderTest.parse(xml);
        assertThrows(IllegalArgumentException.class,()->new OccurrenceProjection(spec,engine.system().model(),engine.canonicalProgramHash()));
        assertDoesNotThrow(()->new OccurrenceProjection(spec,engine.system().model(),engine.canonicalProgramHash(),goals));
        var wrong=MappingXmlReaderTest.parse(xml.replace("target=\"tool\"","target=\"absent\""));
        assertThrows(IllegalArgumentException.class,()->new OccurrenceProjection(wrong,engine.system().model(),engine.canonicalProgramHash(),goals));
    }
}
