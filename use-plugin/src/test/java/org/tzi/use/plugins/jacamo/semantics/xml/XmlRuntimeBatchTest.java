package org.tzi.use.plugins.jacamo.semantics.xml;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.XmlRuntimeBatch;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;
class XmlRuntimeBatchTest {
    @TempDir java.nio.file.Path temp;
    static final String PLAN=M0RuleExecutorTest.RUNTIME.replace("<runtime>","""
      <runtime><datasets><facts id="raw-objects" authority="cartago" kind="ARTIFACT"/></datasets>
       <tables><table id="object-table" dataset="raw-objects">
        <column name="uuid" path="values.uuid" required="true"/><column name="label" path="values.name" required="true"/>
        <column name="flag" path="values.creatorAgentSemanticId" required="true"/><identity mode="PHYSICAL" path="identity"/>
       </table></tables>
      """).replace("id=\"objects\" identityKind", "id=\"objects\" tableRef=\"object-table\" identityKind")
      .replace("proof=\"actual-field-cut\"","proof=\"source-cut\"");
    M0RuleExecutorTest.Fixture fixture()throws Exception {var helper=new M0RuleExecutorTest();helper.temp=temp;return helper.fixture(M0RuleExecutorTest.RUNTIME,false);}
    XmlRuntimeBatch batch(M0RuleExecutorTest.Fixture f,String plan){return new XmlRuntimeBatch(MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",plan)),f.engine().system().model(),f.engine().canonicalProgramHash());}
    RuntimeFact fact(String uuid,String name,Object value) {
        return new RuntimeFact(new BridgeEntityId("cartago","environment","artifact","/warehouse",name,uuid),RuntimeFactKind.ARTIFACT,
          Map.of("uuid",uuid,"name",name,"creatorAgentSemanticId",value),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
    }
    @Test void xmlControlsBatchOnSameNativeStateAndMissingPopulationInvalidatesOldValues()throws Exception {
        // This deliberately ill-typed producer slot is never accepted as a Boolean by the batch.
        var f=fixture();var batch=batch(f,PLAN);
        var bad=batch.apply(f.port(),"run",0,RuntimeFactSourcesTest.cut(List.of(fact("A","inventory","not-Boolean")),Map.of("cartago",Completeness.COMPLETE)));
        assertEquals(0,f.engine().system().state().numObjects());assertTrue(bad.stream().anyMatch(o->o.status().equals("INCOMPLETE")));
        // Use a real Boolean slot from a workspace public API with an OrgCtx target in a separate fixture below;
        // here the same framework fact table drives String/Integer writes through explicit converters.
        String plan=PLAN.replace("<field name=\"flag\" type=\"Boolean\" required=\"true\"/>","")
          .replace("<column name=\"flag\" path=\"values.creatorAgentSemanticId\" required=\"true\"/>","")
          .replace("<assign target=\"flag\" sourceField=\"flag\" converter=\"truth\"/>","");
        batch=batch(f,plan);var cut=RuntimeFactSourcesTest.cut(List.of(fact("A","inventory","agent-A"),fact("B","stock","agent-B")),Map.of("cartago",Completeness.COMPLETE));
        var result=batch.apply(f.port(),"run",0,cut);assertEquals(2,result.stream().filter(o->o.status().equals("MAPPED")).count());
        assertEquals(2,f.engine().system().state().numObjects());
        var object=result.stream().filter(o->o.object()!=null).findFirst().orElseThrow().object();assertTrue(f.port().currentValue(object,"flag").isUndefined());
        batch.apply(f.port(),"run",0,RuntimeFactSourcesTest.cut(List.of(),Map.of()));
        assertEquals("INCOMPLETE",f.port().metadata(object,"xml:m0:quality"));assertEquals(new StringValue("inventory"),f.port().currentValue(object,"label"));
    }
    @Test void malformedLineageAndAgentAsEntityAreRejectedBeforeMutation()throws Exception {
        var f=fixture();
        assertThrows(IllegalArgumentException.class,()->batch(f,PLAN.replace("tableRef=\"object-table\"","tableRef=\"unknown\"")));
        assertThrows(IllegalArgumentException.class,()->batch(f,PLAN.replace("kind=\"ARTIFACT\"","kind=\"AGENT\"")));
        assertThrows(IllegalArgumentException.class,()->batch(f,PLAN.replace("<upsertInstance targetType=\"Counter\" keyFields=\"uuid\"><assign target=\"label\" sourceField=\"label\" converter=\"text\" write=\"create-or-identity-check\"/><assign target=\"flag\" sourceField=\"flag\" converter=\"truth\"/></upsertInstance>","<removeInstance targetType=\"Counter\" keyFields=\"uuid\" authorityProof=\"physical-identity\"/>")));
        assertEquals(0,f.engine().system().state().numObjects());
    }
    static final String ROLES="""
      <runtime><datasets><facts id="boards" authority="moise" kind="GROUP_BOARD"/></datasets><tables>
       <table id="teams" dataset="boards"><column name="uuid" path="identity" required="true"/><identity mode="PHYSICAL" path="identity"/></table>
       <table id="members" dataset="boards"><expand path="values.players" as="player"/>
        <column name="group" path="identity" required="true"/><column name="actor" path="player.agentSemanticId" required="true"/><column name="role" path="player.roleSemanticId" required="true"/>
        <identity mode="LOGICAL_ROLE" fields="group actor role"/>
       </table></tables><inputs>
       <input id="team-input" identityKind="PHYSICAL" identityFields="uuid" tableRef="teams"><field name="uuid" type="String" required="true"/></input>
       <input id="member-input" identityKind="LOGICAL_ROLE" identityFields="group actor role" tableRef="members" reconcileMissing="retire-logical-role">
        <field name="group" type="String" required="true"/><field name="actor" type="String" required="true"/><field name="role" type="String" required="true"/>
       </input></inputs><converters/>
       <rule id="team-instance" inputRef="team-input"><requireEvidence proof="source-cut"/><upsertInstance targetType="Team" keyFields="uuid" aliasFrom="uuid"/></rule>
       <rule id="member-instance" inputRef="member-input"><requireEvidence proof="source-cut"/><upsertInstance targetType="Worker" keyFields="group actor role" contextType="Team" contextFrom="group" membership="roles" playerFrom="actor"/></rule>
      </runtime>
      """;
    RuntimeSnapshot members(int sequence,List<Map<String,String>> players,Completeness population) {
        var id=new BridgeEntityId("moise","organisation","group-board","organization","team","run:board-uuid");
        var fact=new RuntimeFact(id,RuntimeFactKind.GROUP_BOARD,Map.of("boardArtifactIdentity","board-uuid","organisationName","organization","players",players),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var cut=RuntimeFactSourcesTest.cut(List.of(fact),Map.of("moise:organization",population));
        return new RuntimeSnapshot("members-"+sequence,cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),1,cut.facts(),cut.sourceCompleteness(),"members-state-"+sequence);
    }
    @Test void exportedLogicalKeyIsTheNativeRoleAliasAndCanBeReusedByAnotherTable()throws Exception {
        var helper=new M0RuleExecutorTest();helper.temp=temp;var f=helper.fixture(M0RuleExecutorTest.RUNTIME,true);
        String plan=ROLES.replace("fields=\"group actor role\"/>","fields=\"group actor role\" as=\"occurrence\"/>")
            .replace("<field name=\"role\" type=\"String\" required=\"true\"/>","<field name=\"role\" type=\"String\" required=\"true\"/><field name=\"occurrence\" type=\"String\" required=\"true\"/>")
            .replace("playerFrom=\"actor\"/>","playerFrom=\"actor\" aliasFrom=\"occurrence\"/>");
        var player=f.port().upsert("actual-agent-A","AtomicAgent","ActualA");f.port().metadata(player,"semanticId","actual-agent-A");
        var cut=members(1,List.of(Map.of("agentSemanticId","actual-agent-A","roleSemanticId","worker")),Completeness.COMPLETE);
        var result=batch(f,plan).apply(f.port(),"run",1,cut);var role=result.stream().filter(o->o.object()!=null&&o.object().cls().name().equals("Worker")).findFirst().orElseThrow().object();
        var spec=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",plan));
        var tables=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables(spec).execute(new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources(spec).capture(cut,"run",1));
        var identity=tables.get("members").rows().getFirst().values().get("occurrence");assertSame(role,f.port().find((String)identity));
        assertTrue(f.engine().system().state().allLinks().stream().anyMatch(l->l.association().name().equals("Plays")&&l.linkedObjects().equals(List.of(player,role))));
    }
    @Test void onlyDeclaredCompleteLogicalPopulationCanRetireRolesAndReenactmentGetsNewNativeLifetime()throws Exception {
        var helper=new M0RuleExecutorTest();helper.temp=temp;var f=helper.fixture(M0RuleExecutorTest.RUNTIME,true);var batch=batch(f,ROLES);
        var player=f.port().upsert("actual-agent-A","AtomicAgent","ActualA");f.port().metadata(player,"semanticId","actual-agent-A");
        var tuple=List.of(Map.of("agentSemanticId","actual-agent-A","roleSemanticId","worker"));
        var first=batch.apply(f.port(),"run",1,members(1,tuple,Completeness.COMPLETE));
        var role=first.stream().filter(o->o.object()!=null&&o.object().cls().name().equals("Worker")).findFirst().orElseThrow().object();
        assertEquals(1,f.engine().system().state().allObjects().stream().filter(o->o.cls().name().equals("Worker")).count());
        batch.apply(f.port(),"run",1,members(2,List.of(),Completeness.PARTIAL));
        assertTrue(f.engine().system().state().allObjects().contains(role));assertEquals("INCOMPLETE",f.port().metadata(role,"xml:m0:quality"));
        batch.apply(f.port(),"run",1,members(3,List.of(),Completeness.COMPLETE));
        assertFalse(f.engine().system().state().allObjects().contains(role));
        var next=batch.apply(f.port(),"run",1,members(4,tuple,Completeness.COMPLETE)).stream().filter(o->o.object()!=null&&o.object().cls().name().equals("Worker")).findFirst().orElseThrow().object();
        assertNotEquals(role.name(),next.name());assertEquals(1,f.engine().system().state().allObjects().stream().filter(o->o.cls().name().equals("Worker")).count());
    }
    @Test void duplicateLogicalPopulationCannotChooseLastValueOrRetireAnExistingRole()throws Exception {
        var helper=new M0RuleExecutorTest();helper.temp=temp;var f=helper.fixture(M0RuleExecutorTest.RUNTIME,true);var batch=batch(f,ROLES);
        var tuple=Map.of("agentSemanticId","actual-agent-A","roleSemanticId","worker");
        assertThrows(IllegalArgumentException.class,()->batch.apply(f.port(),"run",1,members(1,List.of(tuple,tuple),Completeness.COMPLETE)));
        assertEquals(0,f.engine().system().state().numObjects());
        assertThrows(IllegalArgumentException.class,()->batch(f,ROLES.replace("reconcileMissing=\"retire-logical-role\"","reconcileMissing=\"delete-anything\"")));
    }
    @Test void scopedRetirementRequiresThisBoardsCompletePlayersAndRollsBackWithNativeMetadata()throws Exception {
        var helper=new M0RuleExecutorTest();helper.temp=temp;var f=helper.fixture(M0RuleExecutorTest.RUNTIME,true);
        String plan=ROLES.replace("reconcileMissing=\"retire-logical-role\"","reconcileMissing=\"retire-logical-role\" reconcileScopeField=\"group\"");var batch=batch(f,plan);
        for(String name:List.of("A","B")){var player=f.port().upsert("actual-agent-"+name,"AtomicAgent","Actual"+name);f.port().metadata(player,"semanticId","actual-agent-"+name);}
        var a=members(1,List.of(Map.of("agentSemanticId","actual-agent-A","roleSemanticId","worker")),Completeness.PARTIAL).facts().getFirst();
        var secondId=new BridgeEntityId("moise","organisation","group-board","organization","other","run:other-uuid");
        var b=new RuntimeFact(secondId,a.kind(),Map.of("boardArtifactIdentity","other-uuid","organisationName","organization","players",List.of(Map.of("agentSemanticId","actual-agent-B","roleSemanticId","worker"))),a.relations(),a.projectionStatus(),a.completeness(),a.evidence());
        batch.apply(f.port(),"run",1,scopedCut(1,List.of(a,b)));
        var roles=f.engine().system().state().allObjects().stream().filter(o->o.cls().name().equals("Worker")).toList();assertEquals(2,roles.size());
        var roleB=roles.stream().filter(o->f.port().metadata(o,"rolePlayer").equals("actual-agent-B")).findFirst().orElseThrow();
        var emptyA=members(2,List.of(),Completeness.PARTIAL).facts().getFirst();var before=f.engine().savepoint();
        batch.apply(f.port(),"run",1,scopedCut(2,List.of(emptyA)));
        assertEquals(List.of(roleB),f.engine().system().state().allObjects().stream().filter(o->o.cls().name().equals("Worker")).toList(),"Missing board B grants no authority to retire its roles");
        assertEquals("INCOMPLETE",f.port().metadata(roleB,"xml:m0:quality"));
        f.engine().rollback(before);assertTrue(f.engine().system().state().allObjects().containsAll(roles));
        batch.apply(f.port(),"run",1,scopedCut(2,List.of(emptyA)));
        var unknownB=new RuntimeFact(secondId,b.kind(),Map.of("boardArtifactIdentity","other-uuid","organisationName","organization","players",List.of()),b.relations(),b.projectionStatus(),Completeness.PARTIAL,b.evidence());
        batch.apply(f.port(),"run",1,scopedCut(3,List.of(unknownB)));assertTrue(f.engine().system().state().allObjects().contains(roleB));
        var malformedB=new RuntimeFact(secondId,b.kind(),Map.of("boardArtifactIdentity","other-uuid","organisationName","organization","players",List.of(Map.of("roleSemanticId","worker"))),b.relations(),b.projectionStatus(),Completeness.COMPLETE,b.evidence());
        batch.apply(f.port(),"run",1,scopedCut(4,List.of(malformedB)));assertTrue(f.engine().system().state().allObjects().contains(roleB),"An incomplete selected row cannot certify absence");
        var emptyB=new RuntimeFact(secondId,b.kind(),unknownB.values(),b.relations(),b.projectionStatus(),Completeness.COMPLETE,b.evidence());
        batch.apply(f.port(),"run",1,scopedCut(5,List.of(emptyB)));assertFalse(f.engine().system().state().allObjects().contains(roleB));
        assertThrows(IllegalArgumentException.class,()->batch(f,plan.replace("reconcileScopeField=\"group\"","reconcileScopeField=\"actor\"")));
        assertThrows(IllegalArgumentException.class,()->batch(f,plan.replace("name=\"group\" path=\"identity\"","name=\"group\" path=\"values.organisationName\"")));
    }
    RuntimeSnapshot scopedCut(int sequence,List<RuntimeFact> facts) {
        var cut=RuntimeFactSourcesTest.cut(facts,Map.of("moise:organization",Completeness.PARTIAL));
        return new RuntimeSnapshot("scope-cut-"+sequence,cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),1,cut.facts(),cut.sourceCompleteness(),"scope-state-"+sequence);
    }
}
