package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.adapter.JasonStructuredTerms;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;

/** Interpreter/codec units use explicit normalized fixtures, not live claims.
 * AuctionRuntimeProjectionTest exercises the actual producer-to-writer path. */
class AuctionRuntimeRulesTest {
    private static final String RUN="fixture-run:7",ORG="organization-uuid",GROUP="group-native-uuid";
    private record Prepared(AuctionLiveFixture source,NativeRuntimeProjector projector,Port port,AuctionRuntimeRules rules,String xml)implements AutoCloseable {
        @Override public void close()throws Exception {try{projector.close();}finally{source.close();}}
        AuctionRuntimeRules variant(String text) {
            assertNotEquals(xml,text,"A mutation test must actually change the input");var spec=new AuctionMappingXmlLoader().parse(text.getBytes(StandardCharsets.UTF_8),"runtime-mutation.xml");
            new AuctionMappingValidator().validateStructure(spec);return new AuctionRuntimeRules(spec,projector.system().model());
        }
    }
    private Prepared prepared()throws Exception {
        var source=new AuctionLiveFixture();var program=new AuctionStaticMappingAdapter().map(source.source,new AuctionStaticMappingAdapter.Selection(source.bundle,source.specification));
        var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(source.source,NativeProjectionMode.AUTO,program);
        var projector=new NativeRuntimeProjector(pipeline,source.run.id(),source.run.generation(),source.source.modelRevision());
        var method=projector.mutations().getClass().getDeclaredMethod("cslPort");assertTrue(method.trySetAccessible());
        return new Prepared(source,projector,(Port)method.invoke(projector.mutations()),new AuctionRuntimeRules(source.specification,projector.system().model()),new String(source.bundle.require("case-rules").bytes(),StandardCharsets.UTF_8));
    }
    private static AuctionRuntimeRules.Input input(String kind,Map<String,Object> values,Map<String,String> proofs){return new AuctionRuntimeRules.Input(kind,values,"explicit-normalized-unit-cut",proofs);}
    private static Map<String,Object> organization(){return Map.of("runId",RUN,"organizationUid",ORG,"instanceName","aorg","osId","auction");}
    private static Map<String,Object> artifact(){return Map.of("runId",RUN,"organizationUid",ORG,"workspaceUid","workspace-uuid","artifactUid","artifact-uuid","artifactName","a1","auctionId","a1","className","auction_env.AuctionArtifact","schemeUid","scheme-uuid","schemeArtifactBindingVerified",true);}
    private static Map<String,Object> binary(double value){return Map.of("kind","NUMBER","javaType","java.lang.Double","encoding","IEEE754_BINARY64","value",Double.toString(value),"rawBits",String.format(Locale.ROOT,"%016x",Double.doubleToRawLongBits(value)));}
    private static Map<String,Object> atom(String name){var actual=new jason.asSyntax.Atom(name);return Map.of("kind","JASON_TERM","javaType",actual.getClass().getName(),"term",JasonStructuredTerms.term(actual),"atomName",actual.getFunctor(),"atomNameAuthority","JASON_ATOM_GET_FUNCTOR");}
    private static Map<String,Object> state(){var fields=new LinkedHashMap<String,Object>();fields.put("runId",RUN);fields.put("workspaceUid","workspace-uuid");fields.put("artifactUid","artifact-uuid");fields.put("sourceRevision",1);fields.put("sourceCutAccepted",true);fields.put("internalFieldRead",true);fields.put("running",true);fields.put("task","service 'quoted' \\ path");fields.put("best_bid",binary(Double.MAX_VALUE));fields.put("winner",atom("no_winner"));fields.put("currentWinner","actual-candidate");return fields;}

    @Test void conditionsResolvedParentsAndEvidenceAreExecutedFromXml()throws Exception {
        try(var p=prepared()) {
            var filtered=p.variant(p.xml.replace("field=\"instanceName\" equals=\"aorg\"","field=\"instanceName\" equals=\"another-org\""));
            assertEquals("FILTERED",filtered.execute(p.port,input("in-org",organization(),Map.of())).status());assertEquals(0,p.projector.system().state().numObjects());
            assertEquals("MAPPED",p.rules.execute(p.port,input("in-org",organization(),Map.of())).status());
            var group=Map.<String,Object>of("runId",RUN,"groupUid",GROUP,"organizationUid","missing-organization","instanceName","agrp","specId","auctionGroup");
            assertEquals("INCOMPLETE",p.rules.execute(p.port,input("in-group",group,Map.of())).status());
            assertEquals("INCOMPLETE",p.rules.execute(p.port,input("in-artifact",artifact(),Map.of())).status());
            var unequal=new LinkedHashMap<>(artifact());unequal.put("auctionId","a2");assertEquals("FILTERED",p.rules.execute(p.port,input("in-artifact",unequal,Map.of(AuctionRuntimeRules.CROSSWALK,"explicit-fixture-proof"))).status());
            var role=Map.<String,Object>of("runId",RUN,"groupUid",GROUP,"roleName","unknown-role","agentUid","actor","agentName","actor");
            assertEquals("FILTERED",p.rules.execute(p.port,input("in-role",role,Map.of())).status());
            assertEquals(1,p.projector.system().state().numObjects());
        }
    }
    @Test void assignmentSnapshotPreflightsAllValuesAndImmutableIdIsNotSilentlyRepaired()throws Exception {
        try(var p=prepared()) {
            p.rules.execute(p.port,input("in-org",organization(),Map.of()));var instance=p.rules.execute(p.port,input("in-artifact",artifact(),Map.of(AuctionRuntimeRules.CROSSWALK,"explicit-fixture-proof")));
            var object=instance.object();assertTrue(object.state(p.projector.system().state()).attributeValue("running").isUndefined(),"Creation must not reuse the model's false/default values as observations");
            assertEquals("MAPPED",p.rules.execute(p.port,input("in-state",state(),Map.of(AuctionRuntimeRules.FIELD_CUT,"explicit-fixture-proof"))).status());
            var before=object.state(p.projector.system().state()).attributeValue("task");var missing=new LinkedHashMap<>(state());missing.remove("currentWinner");missing.put("task","should-not-be-written");
            assertEquals("INCOMPLETE",p.rules.execute(p.port,input("in-state",missing,Map.of(AuctionRuntimeRules.FIELD_CUT,"explicit-fixture-proof"))).status());assertEquals(before,object.state(p.projector.system().state()).attributeValue("task"));
            var malformed=new LinkedHashMap<>(state());malformed.put("task","should-not-be-written");malformed.put("best_bid",binary(Double.POSITIVE_INFINITY));
            assertEquals("INCOMPLETE",p.rules.execute(p.port,input("in-state",malformed,Map.of(AuctionRuntimeRules.FIELD_CUT,"explicit-fixture-proof"))).status());assertEquals(before,object.state(p.projector.system().state()).attributeValue("task"));
            var api=UseSystemApi.create(p.projector.system(),false);api.setAttributeValueEx(object,object.cls().attribute("id",true),new StringValue("manual-identity-drift"));
            assertTrue(assertThrows(IllegalArgumentException.class,()->p.rules.execute(p.port,input("in-artifact",artifact(),Map.of(AuctionRuntimeRules.CROSSWALK,"explicit-fixture-proof")))).getMessage().contains("IMMUTABLE_ID_CHANGED"));
            assertEquals("manual-identity-drift",((StringValue)object.state(p.projector.system().state()).attributeValue("id")).value());
        }
    }
    @Test void removalRejectsWrongOwnerKindLifetimeAndProofAndDoesNotResurrectPhysicalKeys()throws Exception {
        try(var p=prepared()) {
            p.rules.execute(p.port,input("in-org",organization(),Map.of()));var object=p.rules.execute(p.port,input("in-artifact",artifact(),Map.of(AuctionRuntimeRules.CROSSWALK,"explicit-fixture-proof"))).object();
            String key=p.port.metadata(object,"auctionRuleKey"),lifetime=p.port.metadata(object,"auctionLifetimeToken"),revision="accepted-fixture-removal";
            var values=new LinkedHashMap<String,Object>(Map.of("runId",RUN,"objectKind","auctionArtifact","nativeKey",key,"sourceRevision",revision));
            var proofs=Map.of(AuctionRuntimeRules.REMOVAL,revision,AuctionRuntimeRules.LIFETIME,lifetime);
            for(var changed:List.of(Map.entry("runId","other-run:8"),Map.entry("objectKind","group"),Map.entry("objectKind","roleEnactment"))) {
                var wrong=new LinkedHashMap<>(values);wrong.put(changed.getKey(),changed.getValue());assertEquals("INCOMPLETE",p.rules.execute(p.port,input("in-removal",wrong,proofs)).status());assertSame(object,p.port.find(key));
            }
            for(var changed:List.of(Map.of(AuctionRuntimeRules.REMOVAL,"stale-cut",AuctionRuntimeRules.LIFETIME,lifetime),Map.of(AuctionRuntimeRules.REMOVAL,revision,AuctionRuntimeRules.LIFETIME,"prior-incarnation"),Map.<String,String>of())) {
                assertEquals("INCOMPLETE",p.rules.execute(p.port,input("in-removal",values,changed)).status());assertSame(object,p.port.find(key));
            }
            var unknown=new LinkedHashMap<>(values);unknown.put("nativeKey","unknown-exact-key");assertEquals("ABSENT",p.rules.execute(p.port,input("in-removal",unknown,proofs)).status());assertSame(object,p.port.find(key));
            assertEquals("MAPPED",p.rules.execute(p.port,input("in-removal",values,proofs)).status());assertNull(p.port.find(key));assertEquals("ABSENT",p.rules.execute(p.port,input("in-removal",values,proofs)).status());
            assertThrows(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProtocolException.class,
                    ()->p.rules.execute(p.port,input("in-artifact",artifact(),Map.of(AuctionRuntimeRules.CROSSWALK,"explicit-fixture-proof"))),"A disposed physical UUID cannot be resurrected");
        }
    }
    @Test void nativeRoleMembershipAndTruthfulPopulationViolationArePreserved()throws Exception {
        try(var p=prepared()) {
            p.rules.execute(p.port,input("in-org",organization(),Map.of()));
            var context=p.rules.execute(p.port,input("in-group",Map.of("runId",RUN,"groupUid",GROUP,"organizationUid",ORG,"instanceName","agrp","specId","auctionGroup"),Map.of())).object();
            for(int i=0;i<2;i++) {
                var actor=p.port.upsert("explicit-fixture-player:"+i,"AtomicAgent","player"+i);p.port.metadata(actor,"name","player"+i);
                var row=Map.<String,Object>of("runId",RUN,"groupUid",GROUP,"roleName","auctioneer","agentUid",p.port.metadata(actor,"semanticId"),"agentName","player"+i);
                assertEquals("MAPPED",p.rules.execute(p.port,input("in-role",row,Map.of())).status());
                assertEquals("MAPPED",p.rules.execute(p.port,input("in-role",row,Map.of())).status(),"Same occurrence is idempotent");
                var invariant=p.projector.system().model().classInvariants().stream().filter(inv->inv.name().equals("AuctioneerPopulation")).findFirst().orElseThrow();
                assertEquals(BooleanValue.get(i==0),new Evaluator().eval(invariant.expandedExpression(),p.projector.system().state()));
            }
            assertEquals(2,p.projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("auctioneer")).count());
            assertEquals(2,p.projector.system().state().allLinks().stream().filter(l->l.association().name().equals("InContext")&&l.linkedObjects().contains(context)).count());
        }
    }
    @Test void convertersPreserveActualAtomNamesAndEveryTestedFiniteBinary64BitPattern()throws Exception {
        try(var p=prepared()) {
            for(double value:new double[]{Double.MAX_VALUE,Double.MIN_VALUE,Double.MIN_NORMAL,-0.0,0.0,42.5,-17.25})assertEquals(Double.doubleToRawLongBits(value),Double.doubleToRawLongBits(((RealValue)p.rules.decode("finite-binary64-to-real",binary(value))).value()));
            for(String name:List.of("no_winner","bidder 'quoted' \\ å","UPPER name"))assertEquals(name,((StringValue)p.rules.decode("jason-atom-name",atom(name))).value());
            assertEquals("service 'quoted' \\ å",((StringValue)p.rules.decode("java-string","service 'quoted' \\ å")).value());
            assertEquals(BooleanValue.FALSE,p.rules.decode("java-boolean",false));
            for(var bad:List.of(binary(Double.NaN),binary(Double.NEGATIVE_INFINITY),Map.of("kind","NUMBER","value","0.0","encoding","IEEE754_BINARY64","rawBits","7fefffffffffffff","javaType","java.lang.Double")))assertThrows(RuntimeException.class,()->p.rules.decode("finite-binary64-to-real",bad));
            assertThrows(RuntimeException.class,()->p.rules.decode("java-boolean","false"));assertThrows(RuntimeException.class,()->p.rules.decode("java-string",false));
            assertThrows(RuntimeException.class,()->p.rules.decode("jason-atom-name",Map.of("kind","JASON_TERM","javaType","jason.asSyntax.Atom","atomName","guessed")));
        }
    }
    @Test void unsupportedHandlersConvertersMissingAssignmentsAndLostIncarnationKeysFailClosed()throws Exception {
        try(var p=prepared()) {
            for(String changed:List.of(
                    p.xml.replace("keyFields=\"runId workspaceUid artifactUid\"","keyFields=\"runId workspaceUid\""),
                    p.xml.replace("algorithm=\"preserve-exact-content\"","algorithm=\"guess-string\""),
                    p.xml.replace("atomic=\"true\" missingValuePolicy=\"incomplete\"","atomic=\"false\" missingValuePolicy=\"incomplete\""),
                    p.xml.replace("<assign target=\"currentWinner\" sourceField=\"currentWinner\" converter=\"java-string\"/>",""),
                    p.xml.replace("field=\"sourceCutAccepted\" equals=\"true\"","field=\"unknown-runtime-field\" equals=\"true\""),
                    p.xml.replace("converter=\"java-boolean\"","converter=\"java-string\""),
                    p.xml.replace("all five values share the same accepted source cut; internal-field access is authorized and available","trust-the-artifact-name")))
                assertThrows(IllegalArgumentException.class,()->p.variant(changed),changed);
        }
    }
}
