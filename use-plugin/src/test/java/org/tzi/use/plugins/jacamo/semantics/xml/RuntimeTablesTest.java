package org.tzi.use.plugins.jacamo.semantics.xml;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables;
import static org.junit.jupiter.api.Assertions.*;
/** Bounded relational interpreter units; source qualification has its own executable gates. */
class RuntimeTablesTest {
    @Test void typedMembershipPreservesUnknownAndExplicitAbsence() {
        String plan="<runtime><tables><table id=\"assignment\" dataset=\"source\"><testContains as=\"assigned\" path=\"players\" valuePath=\"player\" type=\"String\"/><column name=\"assigned\" path=\"assigned\" required=\"true\"/></table></tables></runtime>";
        var data=new RuntimeTables.Dataset(List.of(row("yes",Map.of("players",List.of("p"),"player","p")),row("no",Map.of("players",List.of(),"player","p")),row("missing",Map.of("player","p"))),Completeness.COMPLETE);
        var result=tables(plan).execute(Map.of("source",data)).get("assignment");
        assertEquals(true,result.rows().get(0).values().get("assigned"));assertEquals(false,result.rows().get(1).values().get("assigned"));
        assertEquals(Completeness.PARTIAL,result.rows().get(2).quality());assertFalse(result.rows().get(2).values().containsKey("assigned"));
    }
    @Test void typedComparisonProducesAnExplicitMappingBooleanWithoutGuessingMissingSourceState() {
        String plan="<runtime><tables><table id=\"demands\" dataset=\"goals\"><test as=\"required\" path=\"state\" type=\"String\" equals=\"WAITING\" negate=\"true\"/><column name=\"required\" path=\"required\" required=\"true\"/></table></tables></runtime>";
        var rows=new ArrayList<RuntimeTables.Row>();for(String state:List.of("WAITING","ENABLED","SATISFIED"))rows.add(row(state,Map.of("state",state)));rows.add(row("missing",Map.of()));
        var result=tables(plan).execute(Map.of("goals",new RuntimeTables.Dataset(rows,Completeness.PARTIAL))).get("demands");
        assertEquals(List.of(false,true,true),result.rows().subList(0,3).stream().map(r->r.values().get("required")).toList());assertEquals(Completeness.PARTIAL,result.rows().getLast().quality());assertFalse(result.rows().getLast().values().containsKey("required"));
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("negate=\"true\"","negate=\"maybe\"")));
    }
    @Test void optionalPhysicalLookupCanLeaveOneValueUnknownWithoutInvalidatingIndependentSourceColumns() {
        String plan="""
          <runtime><tables><table id="domains" dataset="domain-source"><column name="id" path="id" required="true"/></table>
           <table id="scopes" dataset="scheme-source"><join tableRef="domains" leftPath="domain" rightPath="id" as="target" cardinality="ONE" required="false" knowledge="QUALIFIED_PHYSICAL_REFERENCE" onMissing="unavailable-value"/>
            <column name="scheme" path="id" required="true"/><column name="self" path="target.id" required="false"/>
           </table></tables></runtime>
          """;
        var source=new RuntimeTables.Dataset(List.of(row("scheme",Map.of("id","scheme","domain","domain"))),Completeness.PARTIAL);
        var tables=tables(plan);var absent=tables.execute(Map.of("scheme-source",source,"domain-source",new RuntimeTables.Dataset(List.of(),Completeness.PARTIAL))).get("scopes");
        assertEquals(Completeness.COMPLETE,absent.rows().getFirst().quality());assertEquals("scheme",absent.rows().getFirst().values().get("scheme"));assertFalse(absent.rows().getFirst().values().containsKey("self"));assertEquals(Completeness.PARTIAL,absent.populationQuality());
        var unknown=new RuntimeTables.Row("domain",Map.of("id","domain"),Completeness.PARTIAL,Map.of(),Set.of("domain"));
        assertEquals(Completeness.PARTIAL,tables.execute(Map.of("scheme-source",source,"domain-source",new RuntimeTables.Dataset(List.of(unknown),Completeness.PARTIAL))).get("scopes").rows().getFirst().quality());
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("required=\"false\" knowledge","required=\"true\" knowledge")));
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("onMissing=\"unavailable-value\"","onMissing=\"assume-absent\"")));
    }
    @Test void exactCollectionSizeRetainsMissingAsUnknownAndRejectsDifferentArity() {
        String plan="<runtime><tables><table id=\"terms\" dataset=\"source\"><whereSize path=\"args\" equals=\"2\"/><column name=\"id\" path=\"id\" required=\"true\"/></table></tables></runtime>";
        var data=new RuntimeTables.Dataset(List.of(row("exact",Map.of("args",List.of("one","two"),"id","exact")),row("extra",Map.of("args",List.of("one","two","three"),"id","extra")),row("missing",Map.of("id","missing"))),Completeness.COMPLETE);
        var result=tables(plan).execute(Map.of("source",data)).get("terms");
        assertEquals(List.of("exact","missing"),result.rows().stream().map(RuntimeTables.Row::identity).toList());
        assertEquals(Completeness.COMPLETE,result.rows().getFirst().quality());assertEquals(Completeness.PARTIAL,result.rows().get(1).quality());
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("equals=\"2\"","equals=\"-1\"")));
    }
    @Test void typedAlternativesAreExplicitAndUnknownIsNotAnExcludedFalse() {
        String plan="<runtime><tables><table id=\"choices\" dataset=\"source\"><whereAny path=\"stage\" type=\"String\"><value equals=\"offer\"/><value equals=\"close\"/></whereAny><column name=\"id\" path=\"id\" required=\"true\"/></table></tables></runtime>";
        var data=new RuntimeTables.Dataset(List.of(row("one",Map.of("id","one","stage","offer")),row("two",Map.of("id","two","stage","close")),row("excluded",Map.of("id","excluded","stage","other")),row("missing",Map.of("id","missing"))),Completeness.COMPLETE);
        var result=tables(plan).execute(Map.of("source",data)).get("choices");assertEquals(List.of("one","two","missing"),result.rows().stream().map(RuntimeTables.Row::identity).toList());assertEquals(Completeness.PARTIAL,result.rows().getLast().quality());
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("<value equals=\"offer\"/><value equals=\"close\"/>","")));
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("type=\"String\"","type=\"Boolean\"")));
    }
    static final String TABLES="""
      <runtime><tables>
        <table id="scopes" dataset="fact.scope"><column name="id" path="id" required="true"/></table>
        <table id="entities" dataset="fact.domain">
          <where path="class" type="String" equals="Inventory"/>
          <join tableRef="scopes" leftPath="scope" rightPath="id" as="context" cardinality="ONE" required="true"/>
          <column name="uuid" path="id" required="true"/><column name="contextId" path="context.id" required="true"/>
          <column name="value" path="p" required="true"/>
          <identity mode="PHYSICAL" path="id"/>
        </table>
        <table id="roles" dataset="fact.group"><expand path="players" as="player"/>
          <column name="group" path="id" required="true"/><column name="actor" path="player.id" required="true"/><column name="role" path="player.role" required="true"/>
          <identity mode="LOGICAL_ROLE" fields="group actor role"/>
        </table>
      </tables></runtime>
      """;
    private RuntimeTables tables(String runtime){return new RuntimeTables(MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",runtime)));}
    private RuntimeTables.Row row(String id,Map<String,Object> values){return new RuntimeTables.Row(id,values,Completeness.COMPLETE,Map.of("source-cut","verified-cut"),Set.of(id));}
    private Map<String,RuntimeTables.Dataset> data() {
        return Map.of("fact.scope",new RuntimeTables.Dataset(List.of(row("scope",Map.of("id","scope"))),Completeness.COMPLETE),
          "fact.domain",new RuntimeTables.Dataset(List.of(row("one",Map.of("id","one","scope","scope","class","Inventory","p",true)),row("two",Map.of("id","two","scope","scope","class","Inventory","p",false))),Completeness.COMPLETE),
          "fact.group",new RuntimeTables.Dataset(List.of(row("group",Map.of("id","group","players",List.of(Map.of("id","actor-A","role","worker"),Map.of("id","actor-B","role","worker"))))),Completeness.PARTIAL));
    }
    @Test void collectionPredicatesSelectExactSuccessfulKnownPerformersWithoutGuessingMissingFields() {
        String plan="""
          <runtime><tables><table id="bidders" dataset="receipts">
           <selectValues path="events" as="players" valuePath="player" valueType="String" completePath="continuous" distinct="true">
            <match path="operation" type="String" equals="offer"/><match path="outcome" type="String" equals="SUCCEEDED"/><match path="known" type="Boolean" equals="true"/>
           </selectValues><column name="players" path="players" required="true"/>
          </table></tables></runtime>
          """;
        var success=Map.of("operation","offer","outcome","SUCCEEDED","known",true,"player","actor-A");
        var irrelevant=Map.of("operation","inspect","outcome","SUCCEEDED","player","actor-B");
        var failed=Map.of("operation","offer","outcome","FAILED","player","actor-C");
        var rows=new ArrayList<Object>(List.of(success,success,irrelevant,failed));
        var result=tables(plan).execute(Map.of("receipts",new RuntimeTables.Dataset(List.of(row("artifact",Map.of("continuous",true,"events",rows))),Completeness.COMPLETE))).get("bidders");
        assertEquals(Completeness.COMPLETE,result.populationQuality());assertEquals(List.of("actor-A"),result.rows().getFirst().values().get("players"));
        rows.add(Map.of("operation","offer","outcome","SUCCEEDED","player","unknown-incarnation"));
        var unknown=tables(plan).execute(Map.of("receipts",new RuntimeTables.Dataset(List.of(row("artifact",Map.of("continuous",true,"events",rows))),Completeness.COMPLETE))).get("bidders");
        assertEquals(Completeness.PARTIAL,unknown.populationQuality());assertFalse(unknown.rows().getFirst().values().containsKey("players"));
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("distinct=\"true\"","distinct=\"true\" matchPath=\"operation\" matchType=\"String\" equals=\"offer\"")));
    }
    @Test void firstMatchingHistoryIsLocalTypedAndDoesNotTurnMissingHistoryIntoKnownAbsence() {
        String plan="""
          <runtime><tables><table id="summary" dataset="boards">
           <firstMatch path="history" as="first" orderPath="sequence" completePath="continuous">
            <match path="goal" type="String" equals="stage"/><match path="state" type="String" equals="SATISFIED"/>
           </firstMatch>
           <choose as="players" conditionPath="first.found" truePath="first.value.players" falsePath="current"/>
           <column name="members" path="players" required="true"/><column name="found" path="first.found" required="false"/>
          </table></tables></runtime>
          """;
        var first=Map.<String,Object>of("sequence",2,"goal","stage","state","SATISFIED","players",List.of("first-member"));
        var last=Map.<String,Object>of("sequence",3,"goal","stage","state","SATISFIED","players",List.of("late-member"));
        var current=List.of("current-member");
        for(var history:List.of(List.of(),List.of(last,first))) {
            var result=tables(plan).execute(Map.of("boards",new RuntimeTables.Dataset(List.of(row("scheme",Map.of("history",history,"continuous",true,"current",current))),Completeness.PARTIAL))).get("summary");
            assertEquals(Completeness.COMPLETE,result.rows().getFirst().quality());assertEquals(history.isEmpty()?current:List.of("first-member"),result.rows().getFirst().values().get("members"));assertEquals(!history.isEmpty(),result.rows().getFirst().values().get("found"));
            assertEquals(Completeness.PARTIAL,result.populationQuality(),"A complete local prefix does not enumerate every scheme");
        }
        for(var value:List.of(Map.<String,Object>of("continuous",false,"history",List.of(first),"current",current),
            Map.<String,Object>of("continuous",true,"current",current),
            Map.<String,Object>of("continuous",true,"history",List.of(first,first),"current",current),
            Map.<String,Object>of("continuous",true,"history",List.of(Map.of("sequence",1.5,"goal","stage","state","SATISFIED","players",List.of())),"current",current),
            Map.<String,Object>of("continuous",true,"history",List.of(Map.of("sequence",1,"goal","stage","players",List.of())),"current",current))) {
            var result=tables(plan).execute(Map.of("boards",new RuntimeTables.Dataset(List.of(row("scheme",value)),Completeness.PARTIAL))).get("summary");
            assertEquals(Completeness.PARTIAL,result.rows().getFirst().quality());assertFalse(result.rows().getFirst().values().containsKey("found"));assertFalse(result.rows().getFirst().values().containsKey("members"),"An unknown match must not select the fallback branch");
        }
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("type=\"String\"","type=\"Any\"")));
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("<match path=\"goal\" type=\"String\" equals=\"stage\"/><match path=\"state\" type=\"String\" equals=\"SATISFIED\"/>","")));
    }
    @Test void explicitTermRenderingUsesPairedGroundSourceTermsAndNeverParsesMissingValues()throws Exception {
        String plan="""
          <runtime><tables><table id="arguments" dataset="boards">
           <termString path="typed" rawPath="raw" as="argument" mode="JACA_3_1_STRING_OR_ATOM"/>
           <column name="value" path="argument" required="true"/>
          </table></tables></runtime>
          """;
        for(String syntax:List.of("\"a 'quoted' value\"","stock_id")) {
            var term=jason.asSyntax.ASSyntax.parseTerm(syntax);
            var result=tables(plan).execute(Map.of("boards",new RuntimeTables.Dataset(List.of(row("board",Map.of("typed",org.jacamo.bridge.adapter.JasonStructuredTerms.term(term),"raw",term.toString()))),Completeness.COMPLETE))).get("arguments");
            assertEquals(Completeness.COMPLETE,result.populationQuality());
            assertEquals(term instanceof jason.asSyntax.StringTerm s?s.getString():syntax,result.rows().getFirst().values().get("value"));
        }
        var structure=jason.asSyntax.ASSyntax.parseTerm(new jaca.JavaLibrary().termToObject(jason.asSyntax.ASSyntax.createString("delivery(city,date(15,12,2015))")).toString());
        var typed=org.jacamo.bridge.adapter.JasonStructuredTerms.term(structure);
        var data=Map.of("boards",new RuntimeTables.Dataset(List.of(row("board",Map.of("typed",typed,"raw",structure.toString()))),Completeness.COMPLETE));
        assertEquals(Completeness.PARTIAL,tables(plan).execute(data).get("arguments").populationQuality(),"A scalar identity must not become a structured argument");
        var ground=tables(plan.replace("JACA_3_1_STRING_OR_ATOM","JACA_3_1_STRING_OR_GROUND_TERM")).execute(data).get("arguments");
        assertEquals(structure.toString(),ground.rows().getFirst().values().get("value"));assertEquals(Completeness.COMPLETE,ground.populationQuality());
        for(String syntax:List.of("true","false","42","X","[one,two]","~stock_id","ns::stock_id")) {
            var term=jason.asSyntax.ASSyntax.parseTerm(syntax);
            var result=tables(plan).execute(Map.of("boards",new RuntimeTables.Dataset(List.of(row("board",Map.of("typed",org.jacamo.bridge.adapter.JasonStructuredTerms.term(term),"raw",term.toString()))),Completeness.COMPLETE))).get("arguments");
            assertEquals(Completeness.PARTIAL,result.populationQuality(),syntax);assertFalse(result.rows().getFirst().values().containsKey("value"));
        }
        var mismatch=tables(plan).execute(Map.of("boards",new RuntimeTables.Dataset(List.of(row("board",Map.of("typed",typed,"raw","another"))),Completeness.COMPLETE))).get("arguments");
        assertEquals(Completeness.PARTIAL,mismatch.populationQuality());
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("JACA_3_1_STRING_OR_ATOM","pretty-print-anything")));
    }
    @Test void identityColumnExportsTheSameQualifiedKeyWithoutPromotingJoinedAliases() {
        String plan=TABLES.replace("mode=\"LOGICAL_ROLE\" fields=\"group actor role\"","mode=\"LOGICAL_ROLE\" fields=\"group actor role\" as=\"occurrence\"")
            .replace("mode=\"PHYSICAL\" path=\"id\"","mode=\"PHYSICAL\" path=\"id\" as=\"physical\"");
        var results=tables(plan).execute(data());
        for(var row:results.get("roles").rows()){assertEquals(row.identity(),row.values().get("occurrence"));assertEquals(Set.of(row.identity()),row.aliases());}
        for(var row:results.get("entities").rows()){assertEquals(row.identity(),row.values().get("physical"));assertFalse(row.aliases().contains("scope"));}
        var unknown=tables(plan.replace("mode=\"PHYSICAL\" path=\"id\"","mode=\"PHYSICAL\" path=\"scope\"")).execute(data()).get("entities");
        assertTrue(unknown.rows().stream().noneMatch(r->r.values().containsKey("physical")),"An unresolved identity cannot export its earlier fallback row key");
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("as=\"occurrence\"","as=\"actor\"")));
    }
    @Test void declarationFilterUsesFrozenSourceIdentityAndExistenceRequiresAQualifiedWitness() {
        String plan="""
          <runtime><tables>
           <table id="members" dataset="boards"><whereBound path="definition" bindingRef="declared-role"/>
            <column name="group" path="group" required="true"/>
           </table>
           <table id="contexts" dataset="contexts"><whereExists tableRef="members" leftPath="identity" rightPath="group" as="membership"/>
            <column name="context" path="identity" required="true"/>
           </table>
          </tables></runtime>
          """;
        var spec=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",plan).replace("<bindings/>","<bindings><static id=\"declared-role\" kind=\"ROLE\" target=\"Worker\" sourceKind=\"MOISE_ROLE\" feature=\"roleId\" value=\"worker\" reason=\"Explicit role\"/></bindings>"));
        var binding=new org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex.Binding("actual-definition",org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex.Kind.ROLE,"Worker","Worker","declared-role","Explicit role",Map.of());
        var index=new org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex(List.of(binding));
        var executor=new RuntimeTables(spec,index);
        var contexts=new RuntimeTables.Dataset(List.of(row("one",Map.of("identity","one")),row("two",Map.of("identity","two"))),Completeness.COMPLETE);
        var data=Map.of("contexts",contexts,"boards",new RuntimeTables.Dataset(List.of(row("member",Map.of("definition","actual-definition","group","one"))),Completeness.PARTIAL));
        var selected=executor.execute(data).get("contexts");
        assertEquals(Completeness.COMPLETE,selected.rows().getFirst().quality(),"Positive membership is witnessed even when the board population is partial");
        assertEquals(Completeness.PARTIAL,selected.rows().getLast().quality(),"Absence from a partial population is unknown, not false");
        assertEquals(Set.of("one"),selected.rows().getFirst().aliases(),"An existential witness grants no identity alias");
        assertTrue(selected.rows().getFirst().proofs().containsKey("join:membership:source-cut"));
        var empty=executor.execute(Map.of("contexts",contexts,"boards",new RuntimeTables.Dataset(List.of(),Completeness.COMPLETE))).get("contexts");assertTrue(empty.rows().isEmpty());
        var wrong=executor.execute(Map.of("contexts",contexts,"boards",new RuntimeTables.Dataset(List.of(row("member",Map.of("definition","worker","group","one"))),Completeness.COMPLETE))).get("contexts");assertTrue(wrong.rows().isEmpty(),"A role name cannot substitute for its frozen source identity");
        assertThrows(IllegalArgumentException.class,()->new RuntimeTables(spec));
        assertThrows(IllegalArgumentException.class,()->new RuntimeTables(MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",plan)),index));
    }
    @Test void explicitlyTypedScalarExpansionKeepsUnknownDistinctFromACompleteEmptyList() {
        String plan="""
          <runtime><tables><table id="scopes" dataset="boards"><expand path="responsible" as="member" valueType="String"/>
           <column name="group" path="member.value" required="true"/><column name="board" path="identity" required="true"/>
          </table></tables></runtime>
          """;
        var good=tables(plan).execute(Map.of("boards",new RuntimeTables.Dataset(List.of(row("scheme",Map.of("identity","scheme","responsible",List.of("one","two")))),Completeness.COMPLETE))).get("scopes");
        assertEquals(List.of("one","two"),good.rows().stream().map(r->r.values().get("group")).toList());assertEquals(Completeness.COMPLETE,good.populationQuality());
        for(Object value:List.of(List.of(),List.of("one",2),"not a list")) {
            var result=tables(plan).execute(Map.of("boards",new RuntimeTables.Dataset(List.of(row("scheme",Map.of("identity","scheme","responsible",value))),Completeness.COMPLETE))).get("scopes");
            if(value.equals(List.of())){assertTrue(result.rows().isEmpty());assertEquals(Completeness.COMPLETE,result.populationQuality());}
            else {assertEquals(Completeness.PARTIAL,result.populationQuality());assertTrue(result.rows().stream().anyMatch(r->r.quality()==Completeness.PARTIAL));}
        }
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("valueType=\"String\"","valueType=\"Any\"")));
    }
    @Test void physicalReferenceJoinUsesExactQualifiedAliasWithoutClaimingPopulationCompleteness() {
        String plan=TABLES.replace("cardinality=\"ONE\" required=\"true\"","cardinality=\"ONE\" required=\"true\" knowledge=\"QUALIFIED_PHYSICAL_REFERENCE\"");
        var data=new HashMap<>(data());var physical=new RuntimeTables.Row("scope",Map.of("id","scope"),Completeness.COMPLETE,Map.of("source-cut","verified-cut","physical-identity","scope"),Set.of("scope"));
        data.put("fact.scope",new RuntimeTables.Dataset(List.of(physical),Completeness.PARTIAL));
        assertTrue(tables(plan).execute(data).get("entities").rows().stream().allMatch(r->r.quality()==Completeness.COMPLETE));
        assertTrue(tables(TABLES).execute(data).get("entities").rows().stream().allMatch(r->r.quality()==Completeness.PARTIAL),"Enumeration joins retain their original completeness requirement");
        for(var unknown:List.of(new RuntimeTables.Row("scope",physical.values(),Completeness.COMPLETE,Map.of("source-cut","other-cut","physical-identity","scope"),Set.of("scope")),
                new RuntimeTables.Row("human-label",physical.values(),Completeness.COMPLETE,Map.of("source-cut","verified-cut","physical-identity","human-label"),Set.of("human-label")),
                new RuntimeTables.Row("scope",physical.values(),Completeness.PARTIAL,physical.proofs(),physical.aliases()))) {
            data.put("fact.scope",new RuntimeTables.Dataset(List.of(unknown),Completeness.PARTIAL));assertTrue(tables(plan).execute(data).get("entities").rows().stream().allMatch(r->r.quality()==Completeness.PARTIAL));
        }
        data.put("fact.scope",new RuntimeTables.Dataset(List.of(physical,physical),Completeness.PARTIAL));assertTrue(tables(plan).execute(data).get("entities").rows().stream().allMatch(r->r.quality()==Completeness.PARTIAL));
        data.put("fact.scope",new RuntimeTables.Dataset(List.of(),Completeness.PARTIAL));assertTrue(tables(plan.replace("required=\"true\" knowledge=", "required=\"false\" knowledge=")).execute(data).get("entities").rows().stream().allMatch(r->r.quality()==Completeness.PARTIAL));
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("QUALIFIED_PHYSICAL_REFERENCE","ASSUME_COMPLETE")));
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("cardinality=\"ONE\"","cardinality=\"MANY\"")));
    }
    @Test void negativeLiteralFilterPreservesUnknownRatherThanTreatingMissingAsNotEqual() {
        String plan="""
          <runtime><tables><table id="scopes" dataset="terms"><whereNot path="value" type="String" equals="false"/>
           <column name="value" path="value" required="false"/></table></tables></runtime>
          """;
        var result=tables(plan).execute(Map.of("terms",new RuntimeTables.Dataset(List.of(row("valid",Map.of("value","token")),row("excluded",Map.of("value","false")),row("missing",Map.of())),Completeness.COMPLETE))).get("scopes");
        assertEquals(Set.of("valid","missing"),result.rows().stream().map(RuntimeTables.Row::identity).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Completeness.COMPLETE,result.rows().getFirst().quality());assertEquals(Completeness.PARTIAL,result.rows().getLast().quality());
    }
    @Test void declarativeJoinExpansionColumnsAndIdentityPreserveRowsAndPopulationQuality() {
        var result=tables(TABLES).execute(data());assertEquals(2,result.get("entities").rows().size());
        assertEquals(Set.of("one","two"),result.get("entities").rows().stream().map(RuntimeTables.Row::identity).collect(java.util.stream.Collectors.toSet()));
        assertEquals(false,result.get("entities").rows().getLast().values().get("value"));
        assertEquals("verified-cut",result.get("entities").rows().getFirst().proofs().get("join:context:source-cut"),"A joined provider/source proof must remain in the mapping witness without granting its identity aliases");
        var roles=result.get("roles");assertEquals(2,roles.rows().size());assertNotEquals(roles.rows().getFirst().identity(),roles.rows().getLast().identity());
        assertEquals(Completeness.PARTIAL,roles.populationQuality());assertTrue(roles.rows().stream().allMatch(r->r.quality()==Completeness.COMPLETE));
    }
    @Test void ambiguousMissingJoinsAndDatasetsRemainUnavailableRatherThanFalseOrFirstMatch() {
        var data=new HashMap<>(data());data.put("fact.scope",new RuntimeTables.Dataset(List.of(row("scope1",Map.of("id","scope")),row("scope2",Map.of("id","scope"))),Completeness.COMPLETE));
        var ambiguous=tables(TABLES).execute(data).get("entities");assertEquals(Completeness.PARTIAL,ambiguous.populationQuality());
        assertTrue(ambiguous.rows().stream().allMatch(r->r.quality()==Completeness.PARTIAL));assertTrue(ambiguous.rows().stream().noneMatch(r->r.values().containsKey("contextId")));
        data.remove("fact.scope");assertEquals(Completeness.UNAVAILABLE,tables(TABLES).execute(data).get("scopes").populationQuality());
    }
    @Test void unknownInstructionsBadPathsForwardJoinsAndUnverifiedPhysicalIdentityFailClosed() {
        assertThrows(IllegalArgumentException.class,()->tables(TABLES.replace("path=\"p\"","path=\"p()\"")));
        assertThrows(IllegalArgumentException.class,()->tables(TABLES.replace("tableRef=\"scopes\"","tableRef=\"future\"")));
        assertThrows(IllegalArgumentException.class,()->tables(TABLES.replace("cardinality=\"ONE\"","cardinality=\"FIRST\"")));
        var changed=TABLES.replace("mode=\"PHYSICAL\" path=\"id\"","mode=\"PHYSICAL\" path=\"scope\"");
        assertTrue(tables(changed).execute(data()).get("entities").rows().stream().allMatch(r->r.quality()==Completeness.PARTIAL));
    }
    @Test void incompleteJoinPopulationDoesNotCertifyUniquenessAndKnownFalseIsRetainedAsData() {
        var data=new HashMap<>(data());data.put("fact.scope",new RuntimeTables.Dataset(List.of(row("scope",Map.of("id","scope"))),Completeness.PARTIAL));
        var joined=tables(TABLES).execute(data).get("entities");
        assertEquals(Completeness.PARTIAL,joined.populationQuality());assertTrue(joined.rows().stream().allMatch(r->r.quality()==Completeness.PARTIAL));
        assertEquals(false,joined.rows().getLast().values().get("value"));
        assertEquals(Set.of("two"),joined.rows().getLast().aliases());
        assertThrows(UnsupportedOperationException.class,()->joined.rows().getFirst().values().put("value",false));
    }
    @Test void missingFilterAndEmptyExpansionHaveDifferentKnowledgeAndExplosionIsBounded() {
        var data=new HashMap<>(data());data.put("fact.domain",new RuntimeTables.Dataset(List.of(row("one",Map.of("id","one","scope","scope","p",true))),Completeness.COMPLETE));
        var unknown=tables(TABLES).execute(data).get("entities");assertEquals(1,unknown.rows().size());assertEquals(Completeness.PARTIAL,unknown.populationQuality());
        data.put("fact.group",new RuntimeTables.Dataset(List.of(row("group",Map.of("id","group","players",List.of()))),Completeness.COMPLETE));
        var empty=tables(TABLES).execute(data).get("roles");assertTrue(empty.rows().isEmpty());assertEquals(Completeness.COMPLETE,empty.populationQuality());
        data.put("fact.group",new RuntimeTables.Dataset(List.of(row("group",Map.of("id","group","players",Collections.nCopies(4097,Map.of("id","A","role","worker"))))),Completeness.COMPLETE));
        assertTrue(assertThrows(IllegalArgumentException.class,()->tables(TABLES).execute(data)).getMessage().contains("ROW_LIMIT"));
    }
    @Test void explicitFirstByNeedsCompleteSourcePrefixAndUniqueMinimumInsteadOfFirstArrayElement() {
        String plan="""
          <runtime><tables><table id="first-transitions" dataset="history"><expand path="transitions" as="transition"/>
           <where path="transition.state" type="String" equals="SATISFIED"/>
           <firstBy path="transition.sequence" groupPaths="identity transition.goal" historyPath="continuous"/>
           <column name="goal" path="transition.goal" required="true"/><column name="members" path="transition.members" required="true"/>
          </table></tables></runtime>
          """;
        var transitions=List.of(Map.of("goal","ship","state","SATISFIED","sequence",9,"members",List.of("late")),Map.of("goal","ship","state","SATISFIED","sequence",3,"members",List.of("first")),Map.of("goal","approve","state","SATISFIED","sequence",2,"members",List.of("reviewer")));
        var input=new RuntimeTables.Dataset(List.of(row("board",Map.of("identity","board","continuous",true,"transitions",transitions))),Completeness.COMPLETE);
        var selected=tables(plan).execute(Map.of("history",input)).get("first-transitions");assertEquals(2,selected.rows().size());assertEquals(Completeness.COMPLETE,selected.populationQuality());
        assertEquals(List.of("first"),selected.rows().stream().filter(r->r.values().get("goal").equals("ship")).findFirst().orElseThrow().values().get("members"));
        var late=new RuntimeTables.Dataset(List.of(row("board",Map.of("identity","board","continuous",false,"transitions",transitions))),Completeness.COMPLETE);
        assertTrue(tables(plan).execute(Map.of("history",late)).get("first-transitions").rows().stream().allMatch(r->r.quality()==Completeness.PARTIAL));
        var tie=new RuntimeTables.Dataset(List.of(row("board",Map.of("identity","board","continuous",true,"transitions",List.of(transitions.get(1),transitions.get(1))))),Completeness.COMPLETE);
        assertEquals(Completeness.PARTIAL,tables(plan).execute(Map.of("history",tie)).get("first-transitions").populationQuality());
        var fractionalTransition=Map.of("goal","ship","state","SATISFIED","sequence",new java.math.BigDecimal("3.5"),"members",List.of("unknown"));
        var fractionalRow=row("board",Map.of("identity","board","continuous",true,"transitions",List.of(fractionalTransition)));
        var fractional=new RuntimeTables.Dataset(List.of(fractionalRow),Completeness.COMPLETE);
        assertEquals(Completeness.PARTIAL,tables(plan).execute(Map.of("history",fractional)).get("first-transitions").populationQuality(),"Fractional capture sequence cannot certify first occurrence");
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("path=\"transition.sequence\"","path=\"sort()\"")));
    }
    @Test void selectedTypedValueSetRequiresExplicitCollectionCoverageAndNeverDropsUnknownCandidates() {
        String plan="""
          <runtime><tables><table id="evidence" dataset="history">
            <selectValues path="receipts" as="players" valuePath="player" valueType="String" completePath="continuous" distinct="true" matchPath="goal" matchType="String" equals="ship"/>
            <column name="players" path="players" required="true"/>
          </table></tables></runtime>
          """;
        var receipts=List.of(Map.of("goal","ship","player","actor-A"),Map.of("goal","other","player","actor-B"),Map.of("goal","ship","player","actor-A"));
        var source=row("board",Map.of("receipts",receipts,"continuous",true));
        var result=tables(plan).execute(Map.of("history",new RuntimeTables.Dataset(List.of(source),Completeness.PARTIAL))).get("evidence");
        assertEquals(List.of("actor-A"),result.rows().getFirst().values().get("players"));assertEquals(Completeness.COMPLETE,result.rows().getFirst().quality());assertEquals(Completeness.PARTIAL,result.populationQuality());
        var empty=row("board",Map.of("receipts",List.of(),"continuous",true));
        assertEquals(List.of(),tables(plan).execute(Map.of("history",new RuntimeTables.Dataset(List.of(empty),Completeness.COMPLETE))).get("evidence").rows().getFirst().values().get("players"));
        for(var unknown:List.of(row("board",Map.of("receipts",receipts,"continuous",false)),row("board",Map.of("receipts",List.of(Map.of("player","unknown")),"continuous",true)),row("board",Map.of("receipts",List.of(Map.of("goal","ship","player",false)),"continuous",true)))) {
            var selected=tables(plan).execute(Map.of("history",new RuntimeTables.Dataset(List.of(unknown),Completeness.COMPLETE))).get("evidence");
            assertEquals(Completeness.PARTIAL,selected.rows().getFirst().quality());assertFalse(selected.rows().getFirst().values().containsKey("players"));
        }
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("matchType=\"String\" ","")));
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("valueType=\"String\"","valueType=\"Script\"")));
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("valuePath=\"player\"","valuePath=\"player()\"")));
    }
    @Test void exactFieldEqualityCorrelatesInvocationActorsAndKeepsMissingFieldsUnknown() {
        String plan="""
          <runtime><tables><table id="correlated" dataset="receipts">
           <whereFieldsEqual leftPath="sourceActor" rightPath="receiptActor" type="String"/>
           <column name="invocation" path="invocation" required="true"/>
          </table></tables></runtime>
          """;
        var data=new RuntimeTables.Dataset(List.of(row("a",Map.of("sourceActor","physical-A","receiptActor","physical-A","invocation","one")),row("b",Map.of("sourceActor","physical-A","receiptActor","physical-B","invocation","two")),row("c",Map.of("sourceActor","physical-A","invocation","three"))),Completeness.COMPLETE);
        var result=tables(plan).execute(Map.of("receipts",data)).get("correlated");assertEquals(2,result.rows().size());assertEquals(Completeness.COMPLETE,result.rows().getFirst().quality());assertEquals(Completeness.PARTIAL,result.rows().getLast().quality());
        assertEquals("one",result.rows().getFirst().values().get("invocation"));assertEquals("three",result.rows().getLast().values().get("invocation"));
        assertThrows(IllegalArgumentException.class,()->tables(plan.replace("type=\"String\"","type=\"Script\"")));
    }
}
