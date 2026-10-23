package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.XmlTaskOclBindings;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeInvocationEvaluation;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native predicate boundary given an already qualified decoder result. Real frame/source
 * qualification is tested separately by XmlStepCapabilityTest; this is not a live MAS test. */
class XmlTaskOclBindingsTest {
    @TempDir Path temp;
    private String revision;
    static final String POLICY=" stepScope=\"delivery\" scopePolicy=\"UNIQUE_CURRENT_PLAYER_SCOPE\" entryPolicy=\"EXACT_SELF\" failurePolicy=\"SKIP_WORLD_POST\"";
    XmlTaskDemandTest.Fixture fixture()throws Exception {
        var helper=new XmlTaskDemandTest();helper.temp=temp;var f=helper.fixture(s->s.replace("selfKind=\"ARTIFACT\"","selfKind=\"ARTIFACT\""+POLICY));
        helper.apply(f,helper.rows(f,Completeness.COMPLETE,true,"activation-1"));revision=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM).modelRevision();return f;
    }
    XmlTaskDemandTest.Fixture callbacks(String pre)throws Exception {
        return callbacks(pre,java.util.function.UnaryOperator.identity());
    }
    XmlTaskDemandTest.Fixture callbacks(String pre,java.util.function.UnaryOperator<String> configure)throws Exception {
        var helper=new XmlTaskDemandTest();helper.temp=temp;var f=helper.fixture(s->configure.apply(s
            .replace("<pre>self.p >= 0</pre><post>self.p = self.p@pre + 1</post>","<pre>"+pre+"</pre><post>receipt.successful</post>")
            .replace("selfKind=\"ARTIFACT\" reason=\"Authored world condition, not an agent mental goal\"/>","selfKind=\"ARTIFACT\""+POLICY.replace("SKIP_WORLD_POST","EVALUATE_PURE_CALLBACK_POST")+" reason=\"Explicit callback predicate\"><variable name=\"receipt\" viewRef=\"callback\" origin=\"STEP_TUPLE\"/></elementRuntime>")
            .replace("<observers/>","<observers><view id=\"callback\" name=\"Receipt\" category=\"TUPLE\" keyFields=\"invocation\"><field name=\"entryConfirmed\" type=\"Boolean\" origin=\"STEP_ENABLED\"/><field name=\"successful\" type=\"Boolean\" origin=\"STEP_SUCCEEDED\"/></view></observers>")));
        helper.apply(f,helper.rows(f,Completeness.COMPLETE,true,"activation-1"));revision=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM).modelRevision();return f;
    }
    @Test void lookupEntryMayLackItsHandleOnlyForAnExplicitPurePredicateAndTerminalMustMatch()throws Exception {
        referenceEntry("receipt.entryConfirmed",true);
    }
    @Test void anUnboundHandleCannotBypassAWorldReadingEntryPredicate()throws Exception {
        referenceEntry("receipt.entryConfirmed and self.p >= 0",false);
    }
    void referenceEntry(String pre,boolean allowed)throws Exception {
        var f=callbacks(pre,s->s.replace("scopePolicy=\"UNIQUE_CURRENT_PLAYER_SCOPE\"","scopePolicy=\"EXACT_REFERENCES\" stepArtifactColumn=\"domain\"").replace("entryPolicy=\"EXACT_SELF\"","entryPolicy=\"ALLOW_UNBOUND_SELF_FOR_PURE_CALLBACK\""));
        var n=f.nativeFixture();var engine=n.nativeFixture().engine();var scope=n.projection().resolve(n.nativeFixture().port(),n.epoch()).getFirst();
        try(var backend=new NativeOclBackend(engine.system().model(),engine.canonicalProgramHash(),engine.canonicalProgram().profile().engine().name())) {
            var binding=new XmlTaskOclBindings(engine.canonicalProgram(),engine.system().model(),backend);var raw=step("qualified-agent:a","ENABLED",Completeness.COMPLETE);
            assertEquals(allowed,binding.references("Positive",scope,raw,engine,true));
            assertFalse(binding.references("Positive",scope,raw,engine,false),"No absent handle at terminal");
            var foreign=new JasonStepCutDecoder.Step(raw.task(),raw.agent(),raw.declaration(),raw.goalInvocation(),raw.invocation(),raw.scheme(),"foreign-handle",raw.operation(),raw.phase(),raw.quality(),raw.diagnostic(),raw.enabled(),raw.succeeded(),raw.bindings());
            assertFalse(binding.references("Positive",scope,foreign,engine,true),"An unverified nonempty handle cannot use the pure-entry exception");
        }
    }
    JasonStepCutDecoder.Step step(String agent,String phase,Completeness quality) {
        return new JasonStepCutDecoder.Step("Positive",agent,"a","goal:one","step:one","","","",phase,quality,quality==Completeness.COMPLETE?"":"UNAVAILABLE_FIELD",true,phase.equals("ENABLED")?null:phase.equals("SUCCEEDED"),Map.of("capturedRun",Map.of("sessionId","run","generation",1,"modelRevision",revision),"stepValueQuality",Map.of("version","JASON_STEP_VALUES/2.0","enabled","COMPLETE_CALLBACK","succeeded",phase.equals("ENABLED")?"UNAVAILABLE":"COMPLETE_CALLBACK")));
    }
    @Test void exactActorAndBorrowedNativeBeforeAfterProduceActualAtPreContract()throws Exception {
        var f=fixture();var n=f.nativeFixture();var engine=n.nativeFixture().engine();var port=n.nativeFixture().port();var scope=n.projection().resolve(port,n.epoch()).getFirst();var member=scope.members().stream().filter(m->m.player().contains("alpha")).findFirst().orElseThrow();var demand=f.demands().resolve(port,n.epoch()).stream().filter(d->d.player().equals(member.player())).findFirst().orElseThrow();
        port.alias("qualified-agent:a",port.find(member.player()));port.value(n.domain(),"p",IntegerValue.valueOf(1));org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.recordPropertyWritten(port,n.domain(),n.domain().cls().attribute("p",true),port.currentValue(n.domain(),"p"));
        try(var backend=new NativeOclBackend(engine.system().model(),engine.canonicalProgramHash(),engine.canonicalProgram().profile().engine().name())) {
            var binding=new XmlTaskOclBindings(engine.canonicalProgram(),engine.system().model(),backend);
            var entry=UseStateReadView.pin(null,engine.system().state());var pre=binding.prepare("Positive",true,engine,scope,member,demand,step("qualified-agent:a","ENABLED",Completeness.COMPLETE),entry,Map.of("cartago",Completeness.COMPLETE),false);
            assertTrue(pre.unavailable().isBlank(),pre.unavailable());
            port.value(n.domain(),"p",IntegerValue.valueOf(2));org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.recordPropertyWritten(port,n.domain(),n.domain().cls().attribute("p",true),port.currentValue(n.domain(),"p"));
            var view=UseStateReadView.pin(entry.after(),engine.system().state());var post=binding.prepare("Positive",false,engine,scope,member,demand,step("qualified-agent:a","SUCCEEDED",Completeness.COMPLETE),view,Map.of("cartago",Completeness.COMPLETE),false);assertTrue(post.unavailable().isBlank(),post.unavailable());
            var rows=NativeInvocationEvaluation.evaluate(NativeInvocationEvaluation.prepare(List.of(pre,post),List.of(),List.of()),2);assertTrue(rows.stream().allMatch(r->r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS));
            assertEquals(IntegerValue.valueOf(1),pre.after().objectByName(n.domain().name()).state(pre.after()).attributeValue("p"));assertSame(engine.system(),pre.after().system());assertSame(engine.system(),post.before().system());
            var wrong=binding.prepare("Positive",true,engine,scope,scope.members().stream().filter(m->!m.player().equals(member.player())).findFirst().orElseThrow(),demand,step("qualified-agent:a","ENABLED",Completeness.COMPLETE),entry,Map.of("cartago",Completeness.COMPLETE),false);assertFalse(wrong.unavailable().isBlank());
            var missing=binding.prepare("Positive",false,engine,scope,member,demand,step("qualified-agent:a","SUCCEEDED",Completeness.COMPLETE),UseStateReadView.pin(null,engine.system().state()),Map.of("cartago",Completeness.COMPLETE),false);assertTrue(missing.unavailable().contains("PRE"));
            var partial=binding.prepare("Positive",true,engine,scope,member,demand,step("qualified-agent:a","ENABLED",Completeness.PARTIAL),entry,Map.of("cartago",Completeness.COMPLETE),false);assertFalse(partial.unavailable().isBlank());
        }
    }
    @Test void callbackPartsUseDeclaredOriginsAndNeverBooleanizeAnUnavailableTuplePart()throws Exception {
        var f=callbacks("receipt.entryConfirmed");var n=f.nativeFixture();var engine=n.nativeFixture().engine();var port=n.nativeFixture().port();var scope=n.projection().resolve(port,n.epoch()).getFirst();var member=scope.members().stream().filter(m->m.player().contains("alpha")).findFirst().orElseThrow();var demand=f.demands().resolve(port,n.epoch()).stream().filter(d->d.player().equals(member.player())).findFirst().orElseThrow();port.alias("qualified-agent:a",port.find(member.player()));
        try(var backend=new NativeOclBackend(engine.system().model(),engine.canonicalProgramHash(),engine.canonicalProgram().profile().engine().name())) {
            var binding=new XmlTaskOclBindings(engine.canonicalProgram(),engine.system().model(),backend);var before=UseStateReadView.pin(null,engine.system().state());
            var pre=binding.prepare("Positive",true,engine,scope,member,demand,step("qualified-agent:a","ENABLED",Completeness.PARTIAL),before,Map.of(),false);assertTrue(pre.unavailable().isBlank(),pre.unavailable());
            var post=binding.prepare("Positive",false,engine,scope,member,demand,step("qualified-agent:a","FAILED",Completeness.PARTIAL),UseStateReadView.pin(before.after(),engine.system().state()),Map.of(),false);assertTrue(post.unavailable().isBlank(),post.unavailable());
            var rows=NativeInvocationEvaluation.evaluate(NativeInvocationEvaluation.prepare(List.of(pre,post),List.of(),List.of()),1);assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS,rows.getFirst().verdict());assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL,rows.getLast().verdict(),"Explicit source failure is known false; a missing success value is not");
        }
    }
    @Test void wholeTupleUndefinedTestCannotTurnMissingSuccessAtEntryIntoNativeFalse()throws Exception {
        var f=callbacks("receipt.oclIsUndefined()");var n=f.nativeFixture();var engine=n.nativeFixture().engine();var port=n.nativeFixture().port();var scope=n.projection().resolve(port,n.epoch()).getFirst();var member=scope.members().stream().filter(m->m.player().contains("alpha")).findFirst().orElseThrow();var demand=f.demands().resolve(port,n.epoch()).stream().filter(d->d.player().equals(member.player())).findFirst().orElseThrow();port.alias("qualified-agent:a",port.find(member.player()));
        try(var backend=new NativeOclBackend(engine.system().model(),engine.canonicalProgramHash(),engine.canonicalProgram().profile().engine().name())) {
            var binding=new XmlTaskOclBindings(engine.canonicalProgram(),engine.system().model(),backend);var condition=binding.prepare("Positive",true,engine,scope,member,demand,step("qualified-agent:a","ENABLED",Completeness.COMPLETE),UseStateReadView.pin(null,engine.system().state()),Map.of(),false);
            assertFalse(condition.unavailable().isBlank(),"A whole-tuple read also depends on unavailable callback fields");
            assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.SKIPPED,NativeInvocationEvaluation.evaluate(NativeInvocationEvaluation.prepare(List.of(condition),List.of(),List.of()),1).getFirst().verdict());
        }
    }
    @Test void declaredOperationReceiptCannotBeReplacedByAValueChangeOrAnotherAction()throws Exception {
        var helper=new XmlTaskDemandTest();helper.temp=temp;
        var f=helper.fixture(s->{try {
            Path ocl=temp.resolve("requirements.ocl");String old=GenericMappingEngineTest.hash(ocl);java.nio.file.Files.writeString(ocl,java.nio.file.Files.readString(ocl)+"context Counter::increment()\npre IncrementAllowed: self.p >= 0\npost Incremented: self.p = self.p@pre + 1\n");
            String type=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM).semanticContract().artifactDeclarations().getFirst().javaClass();
            return s.replace(old,GenericMappingEngineTest.hash(ocl)).replace("selfKind=\"ARTIFACT\" reason=\"Authored world condition, not an agent mental goal\"/>","selfKind=\"ARTIFACT\""+POLICY+" reason=\"Exact source call\"><actionReceipt operationRef=\"increment-interface\"/></elementRuntime>")
                .replace("</bindings>","<oclClause name=\"IncrementAllowed\" context=\"context Counter::increment()\"/><oclClause name=\"Incremented\" context=\"context Counter::increment()\"/><operation id=\"increment-interface\" owner=\"Counter\" signature=\"[&quot;"+type+"&quot;,&quot;increment&quot;,[],&quot;void&quot;]\" clauses=\"IncrementAllowed Incremented\" task=\"Positive\"/></bindings>");
        }catch(Exception invalid){throw new IllegalStateException(invalid);}});
        helper.apply(f,helper.rows(f,Completeness.COMPLETE,true,"activation-1"));revision=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM).modelRevision();var n=f.nativeFixture();var engine=n.nativeFixture().engine();var port=n.nativeFixture().port();var scope=n.projection().resolve(port,n.epoch()).getFirst();var member=scope.members().stream().filter(m->m.player().contains("alpha")).findFirst().orElseThrow();var demand=f.demands().resolve(port,n.epoch()).stream().filter(d->d.player().equals(member.player())).findFirst().orElseThrow();port.alias("qualified-agent:a",port.find(member.player()));
        port.value(n.domain(),"p",IntegerValue.valueOf(1));org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.recordPropertyWritten(port,n.domain(),n.domain().cls().attribute("p",true),port.currentValue(n.domain(),"p"));var before=UseStateReadView.pin(null,engine.system().state());
        port.value(n.domain(),"p",IntegerValue.valueOf(2));org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.recordPropertyWritten(port,n.domain(),n.domain().cls().attribute("p",true),port.currentValue(n.domain(),"p"));var view=UseStateReadView.pin(before.after(),engine.system().state());
        try(var backend=new NativeOclBackend(engine.system().model(),engine.canonicalProgramHash(),engine.canonicalProgram().profile().engine().name())) {
            var binding=new XmlTaskOclBindings(engine.canonicalProgram(),engine.system().model(),backend);var raw=step("qualified-agent:a","SUCCEEDED",Completeness.COMPLETE);
            assertFalse(binding.prepare("Positive",false,engine,scope,member,demand,raw,view,Map.of(),false).unavailable().isBlank(),"A matching @pre value change is not a successful source action receipt");
            String correlation=UUID.randomUUID().toString();var values=new LinkedHashMap<>(raw.bindings());
            values.put("actions",List.of(Map.of("authority","JASON_ACTIONEXEC_IDENTITY","correlation",correlation,"operationName","increment","result","SUCCEEDED","action","increment","failure","")));
            var observed=new JasonStepCutDecoder.Step(raw.task(),raw.agent(),raw.declaration(),raw.goalInvocation(),raw.invocation(),raw.scheme(),raw.artifact(),correlation,raw.phase(),raw.quality(),raw.diagnostic(),raw.enabled(),raw.succeeded(),values);
            assertTrue(binding.prepare("Positive",false,engine,scope,member,demand,observed,view,Map.of(),false).unavailable().isBlank());
            var wrong=new JasonStepCutDecoder.Step(raw.task(),raw.agent(),raw.declaration(),raw.goalInvocation(),raw.invocation(),raw.scheme(),raw.artifact(),"another-action",raw.phase(),raw.quality(),raw.diagnostic(),raw.enabled(),raw.succeeded(),values);
            assertFalse(binding.prepare("Positive",false,engine,scope,member,demand,wrong,view,Map.of(),false).unavailable().isBlank());
        }
    }
}
