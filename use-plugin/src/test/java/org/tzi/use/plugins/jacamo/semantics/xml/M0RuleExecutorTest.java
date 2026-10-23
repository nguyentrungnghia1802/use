package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.M0RuleExecutor;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;
/** Native Port component fixture; accepted-cut admission/TCP is a separate gate. */
class M0RuleExecutorTest {
    @TempDir Path temp;
    Path project=GenericMappingEngineTest.JCM;
    static final String RUNTIME="""
      <runtime>
        <inputs><input id="objects" identityKind="PHYSICAL" identityFields="uuid">
          <field name="uuid" type="String" required="true"/><field name="label" type="String" required="true"/>
          <field name="flag" type="Boolean" required="true"/>
        </input></inputs>
        <converters><converter id="text" inputType="String" outputType="String" algorithm="preserve-exact-content" onMismatch="incomplete"/>
          <converter id="truth" inputType="Boolean" outputType="Boolean" algorithm="identity" onMismatch="incomplete"/></converters>
        <rule id="create" inputRef="objects"><requireEvidence proof="actual-field-cut"/>
          <upsertInstance targetType="Counter" keyFields="uuid"><assign target="label" sourceField="label" converter="text" write="create-or-identity-check"/><assign target="flag" sourceField="flag" converter="truth"/></upsertInstance>
        </rule>
      </runtime>
      """;
    record Fixture(NativeRuntimeMutationEngine engine,Port port,M0RuleExecutor rules) { }
    private Fixture fixture()throws Exception {return fixture(RUNTIME,false);}
    Fixture fixture(String runtime,boolean organization)throws Exception {
        return fixture(runtime,organization,java.util.function.UnaryOperator.identity());
    }
    Fixture fixture(String runtime,boolean organization,java.util.function.UnaryOperator<String> configure)throws Exception {
        var helper=new GenericMappingEngineTest();helper.temp=temp;helper.input(project);
        Path xml=temp.resolve("mapping.xml");String text=Files.readString(xml)
          .replace("<createAttribute name=\"p\"", "<createAttribute name=\"label\" owner=\"Counter\" type=\"String\" optional=\"false\" mutable=\"false\"/><createAttribute name=\"flag\" owner=\"Counter\" type=\"Boolean\" optional=\"false\" mutable=\"true\"/><createAttribute name=\"p\"")
          .replace("</bindings>","<oclClause name=\"Nonnegative\" context=\"context Counter\"/></bindings>");
        if(organization)text=text.replace("<createClassifier kind=\"Entity\" name=\"Counter\"/>","<createClassifier kind=\"OrgCtx\" name=\"Team\"/><createClassifier kind=\"Role\" name=\"Worker\" owner=\"Team\"/><createClassifier kind=\"Entity\" name=\"Counter\"/>")
            .replace("</bindings>","<static id=\"team-binding\" kind=\"ORG_CONTEXT\" target=\"Team\" sourceKind=\"MOISE_GROUP\" feature=\"groupId\" value=\"team\" reason=\"Explicit group context\"/><static id=\"role-binding\" kind=\"ROLE\" target=\"Worker\" sourceKind=\"MOISE_ROLE\" feature=\"roleId\" value=\"worker\" reason=\"Explicit role declaration\"/></bindings>");
        Files.writeString(xml,configure.apply(text));var source=GenericMappingEngineTest.official(project);
        var program=new GenericMappingEngine().map(source,MappingInputs.read(project,xml,temp.resolve("requirements.ocl")));
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        var engine=new NativeRuntimeMutationEngine(built.nativeResult().state().system(),built.nativeResult().state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry(),program);
        var method=NativeRuntimeMutationEngine.class.getDeclaredMethod("cslPort");assertTrue(method.trySetAccessible());var port=(Port)method.invoke(engine);
        var spec=new MappingXmlReader().parse(text.replace("<runtime/>",runtime).getBytes(StandardCharsets.UTF_8),"m0.xml");
        return new Fixture(engine,port,new M0RuleExecutor(spec,built.binding().model(),program.hash()));
    }
    private M0RuleExecutor.Input input(String identity,Map<String,Object> values,Completeness quality,Map<String,String> proof) {
        return new M0RuleExecutor.Input("objects","run:2","physical:"+identity,"accepted-cut",quality,values,proof);
    }
    @Test void sameInterpreterUsesRealPortTwoIdentitiesAndNoMissingFalse()throws Exception {
        var f=fixture();var first=input("one",Map.of("uuid","one","label","one","flag",true),Completeness.COMPLETE,Map.of("actual-field-cut","verified"));
        var one=f.rules.execute(f.port,"create",first);assertEquals("MAPPED",one.status());
        assertEquals("MAPPED",f.rules.execute(f.port,"create",input("two",Map.of("uuid","two","label","two","flag",false),Completeness.COMPLETE,Map.of("actual-field-cut","verified"))).status());
        assertEquals(2,f.engine.system().state().numObjects());assertSame(one.object(),f.rules.execute(f.port,"create",first).object());
        var missing=f.rules.execute(f.port,"create",input("one",Map.of("uuid","one","label","one"),Completeness.PARTIAL,Map.of()));
        assertEquals("INCOMPLETE",missing.status());assertTrue(f.port.currentValue(one.object(),"flag").isUndefined());assertEquals(new StringValue("one"),f.port.currentValue(one.object(),"label"));
        assertEquals("MAPPED",f.rules.execute(f.port,"create",first).status());assertEquals(BooleanValue.TRUE,f.port.currentValue(one.object(),"flag"));
    }
    @Test void immutableIdentityAndUnprovedInputsCannotCreateOrOverwrite()throws Exception {
        var f=fixture();var good=input("one",Map.of("uuid","one","label","one","flag",true),Completeness.COMPLETE,Map.of("actual-field-cut","verified"));
        var object=f.rules.execute(f.port,"create",good).object();
        assertThrows(IllegalArgumentException.class,()->f.rules.execute(f.port,"create",input("one",Map.of("uuid","one","label","changed","flag",false),Completeness.COMPLETE,Map.of("actual-field-cut","verified"))));
        assertEquals(new StringValue("one"),f.port.currentValue(object,"label"));assertEquals(BooleanValue.TRUE,f.port.currentValue(object,"flag"));
        assertEquals("INCOMPLETE",f.rules.execute(f.port,"create",input("new",Map.of("uuid","new","label","new","flag",true),Completeness.COMPLETE,Map.of())).status());
        assertEquals(1,f.engine.system().state().numObjects());
    }
    @Test void completeMutableSnapshotsAndNativeRollbackRestoreValuesMetadataAndAliases()throws Exception {
        String runtime=RUNTIME.replace("</input>","<field name=\"p\" type=\"Integer\" required=\"true\"/></input>")
            .replace("</converters>","<converter id=\"integer\" inputType=\"Integer\" outputType=\"Integer\" algorithm=\"preserve-exact-integer\" onMismatch=\"incomplete\"/></converters>")
            .replace("</runtime>","<rule id=\"state\" inputRef=\"objects\"><assignSnapshot targetType=\"Counter\" keyFields=\"uuid\" atomic=\"true\" missingValuePolicy=\"incomplete\"><assign target=\"flag\" sourceField=\"flag\" converter=\"truth\"/><assign target=\"p\" sourceField=\"p\" converter=\"integer\"/></assignSnapshot></rule></runtime>");
        var f=fixture(runtime,false);var row=input("one",Map.of("uuid","one","label","one","flag",true,"p",1),Completeness.COMPLETE,Map.of("actual-field-cut","verified"));
        var object=f.rules.execute(f.port,"create",row).object();assertEquals("MAPPED",f.rules.execute(f.port,"state",row).status());
        var before=f.engine.savepoint();
        assertEquals("INCOMPLETE",f.rules.execute(f.port,"state",input("one",Map.of("uuid","one","label","one","flag",false),Completeness.COMPLETE,Map.of())).status());
        assertTrue(f.port.currentValue(object,"p").isUndefined());assertTrue(f.port.currentValue(object,"flag").isUndefined());
        f.engine.rollback(before);object=f.port.find(f.rules.execute(f.port,"state",row).nativeKey());
        assertEquals(IntegerValue.valueOf(1),f.port.currentValue(object,"p"));assertEquals(BooleanValue.TRUE,f.port.currentValue(object,"flag"));assertEquals("false",f.port.metadata(object,"propertyAbsent:p"));
    }
    @Test void removalUsesExactAuthorityAndNeverResurrectsDisposedPhysicalIdentity()throws Exception {
        String runtime=RUNTIME.replace("</inputs>","<input id=\"removed\" identityKind=\"PHYSICAL\" identityFields=\"uuid\"><field name=\"uuid\" type=\"String\" required=\"true\"/></input></inputs>")
            .replace("</runtime>","<rule id=\"remove\" inputRef=\"removed\"><removeInstance targetType=\"Counter\" keyFields=\"uuid\" authorityProof=\"removed-identity\"/></rule></runtime>");
        var f=fixture(runtime,false);var row=input("one",Map.of("uuid","one","label","one","flag",true),Completeness.COMPLETE,Map.of("actual-field-cut","verified"));
        var first=f.rules.execute(f.port,"create",row);var missing=new M0RuleExecutor.Input("removed","run:2","physical:one","remove-cut",Completeness.COMPLETE,Map.of("uuid","one"),Map.of("removed-identity","physical:other"));
        assertEquals("INCOMPLETE",f.rules.execute(f.port,"remove",missing).status());assertNotNull(f.port.find(first.nativeKey()));
        var removed=new M0RuleExecutor.Input("removed","run:2","physical:one","remove-cut",Completeness.COMPLETE,Map.of("uuid","one"),Map.of("removed-identity","physical:one"));
        assertEquals("MAPPED",f.rules.execute(f.port,"remove",removed).status());assertNull(f.port.find(first.nativeKey()));
        assertTrue(assertThrows(NativeRuntimeProtocolException.class,()->f.rules.execute(f.port,"create",row)).getMessage().contains("DISPOSED_INCARNATION"));
        assertEquals("MAPPED",f.rules.execute(f.port,"create",input("new-uuid",Map.of("uuid","new-uuid","label","one","flag",true),Completeness.COMPLETE,Map.of("actual-field-cut","verified"))).status());
    }
    @Test void roleContextPlayerLinksAndReenactmentUseOriginalNativeRoleLifetimeRules()throws Exception {
        String runtime=RUNTIME.replace("</inputs>","""
            <input id="context" identityKind="PHYSICAL" identityFields="uuid"><field name="uuid" type="String" required="true"/></input>
            <input id="role" identityKind="LOGICAL_ROLE" identityFields="group actor role"><field name="group" type="String" required="true"/><field name="actor" type="String" required="true"/><field name="role" type="String" required="true"/></input></inputs>
            """).replace("</runtime>","""
            <rule id="context-create" inputRef="context"><upsertInstance targetType="Team" keyFields="uuid" aliasFrom="uuid"/></rule>
            <rule id="role-create" inputRef="role"><requireEvidence proof="current-player"/><upsertInstance targetType="Worker" keyFields="group actor role" contextType="Team" contextFrom="group" membership="roles" playerFrom="actor"/></rule>
            <rule id="role-remove" inputRef="role"><removeInstance targetType="Worker" keyFields="group actor role" authorityProof="removed-role"/></rule></runtime>
            """);
        var f=fixture(runtime,true);f.rules.execute(f.port,"context-create",new M0RuleExecutor.Input("context","run:2","physical:team","context-cut",Completeness.COMPLETE,Map.of("uuid","physical:team"),Map.of()));
        var actor=f.port.upsert("actual-player:one","AtomicAgent","actual_player");
        var fields=Map.<String,Object>of("group","physical:team","actor","actual-player:one","role","worker");
        var first=f.rules.execute(f.port,"role-create",new M0RuleExecutor.Input("role","run:2","role:one","first-cut",Completeness.COMPLETE,fields,Map.of("current-player","accepted-agent")));
        assertEquals("MAPPED",first.status());assertEquals("actual-player:one",f.port.metadata(first.object(),"rolePlayer"));
        assertEquals(2,f.engine.system().state().allLinks().stream().filter(l->l.linkedObjects().contains(first.object())).count());
        f.rules.execute(f.port,"role-remove",new M0RuleExecutor.Input("role","run:2","role:one","remove-cut",Completeness.COMPLETE,fields,Map.of("removed-role","role:one")));
        var next=f.rules.execute(f.port,"role-create",new M0RuleExecutor.Input("role","run:2","role:one","next-cut",Completeness.COMPLETE,fields,Map.of("current-player","accepted-agent")));
        assertNotEquals(first.object().name(),next.object().name());assertEquals(1,f.engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("Worker")).count());assertSame(actor,f.port.find("actual-player:one"));
    }
}
