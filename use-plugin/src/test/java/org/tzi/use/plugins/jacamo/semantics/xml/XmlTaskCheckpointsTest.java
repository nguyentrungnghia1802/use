package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.*;
import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.XmlTaskOclBindings;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeInvocationEvaluation;
import org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.IntegerValue;
import static org.junit.jupiter.api.Assertions.*;

/** Actual source extraction/Jason callbacks and real native cuts. Domain values are
 * deliberately supplied through the native component Port; this is not live TCP acceptance. */
class XmlTaskCheckpointsTest {
    @TempDir Path temp;
    final Map<String,Completeness> coverage=Map.of("cartago",Completeness.COMPLETE,"jason",Completeness.COMPLETE,VerificationObservation.SOURCE_ID,Completeness.COMPLETE);
    final class Fixture implements AutoCloseable {
        final XmlTaskDemandTest helper=new XmlTaskDemandTest();final XmlTaskDemandTest.Fixture data;
        final ModelSnapshot source;final List<VerificationObservation> frames=new ArrayList<>();final NativeOclBackend backend;final XmlTaskCheckpoints checkpoints;
        Fixture()throws Exception {
            this(false);
        }
        Fixture(boolean resources)throws Exception {
            this(resources,false);
        }
        Fixture(boolean resources,boolean lateSelf)throws Exception {
            this(resources,lateSelf,java.util.function.UnaryOperator.identity());
        }
        Fixture(boolean resources,boolean lateSelf,java.util.function.UnaryOperator<String> configure)throws Exception {
            Files.createDirectories(temp.resolve("src/agt"));Files.createDirectories(temp.resolve("src/org"));
            Files.copy(GenericMappingEngineTest.JCM.getParent().resolve("src/org/monitor.xml"),temp.resolve("src/org/monitor.xml"));
            Path behavior=Files.writeString(temp.resolve("src/agt/a.asl"),"+!work <- .print(\"source execution\").\n");
            Path jcm=Files.writeString(temp.resolve("monitor.jcm"),Files.readString(GenericMappingEngineTest.JCM).replace("agent a : a.asl","agent a : a.asl\n    agent b : a.asl"));
            helper.temp=temp;helper.project=jcm;
            data=helper.fixture(s->{String xml=(resources?s.replace("engineProfile=\"USE4GOAL_OBSERVED_V1\"","engineProfile=\"DERIVED_AND_RESOURCE_V1\"").replace("</actor></istar>","<resource id=\"parcel-resource\" name=\"Parcel\"><neededBy task=\"Positive\"/></resource></actor></istar>")
                .replace("<self target=\"Positive\" origin=\"COLUMN\" column=\"domain\"/>","<self target=\"Positive\" origin=\"COLUMN\" column=\"domain\"/><resource target=\"Parcel\" column=\"domain\" type=\"Counter\"/>"):s).replace("selfKind=\"ARTIFACT\"","selfKind=\"ARTIFACT\""+(lateSelf?XmlTaskOclBindingsTest.POLICY.replace("EXACT_SELF","ALLOW_UNBOUND_SELF_FOR_PURE_CALLBACK"):XmlTaskOclBindingsTest.POLICY))
                .replace("</sources>","<file id=\"behavior\" kind=\"ASL\" path=\"src/agt/a.asl\" sha256=\""+uncheckedHash(behavior)+"\"/></sources>")
                .replace("<requirements/>","<requirements><capability id=\"steps\" provider=\"jason.plan-boundaries.v1\" required=\"true\" sourceRef=\"behavior\"><plan trigger=\"work\"><step task=\"Positive\" first=\"0\" after=\"1\" variables=\"\"/></plan></capability></requirements>");
                if(lateSelf)xml=xml.replace("anchorColumn=\"domain\" contextColumn=\"group\" keyFields=\"group domain\"","anchorColumn=\"group\" contextColumn=\"group\" keyFields=\"group\"")
                    .replace("<self target=\"Positive\" origin=\"COLUMN\" column=\"domain\"/>","<self target=\"Positive\" origin=\"COLUMN\" column=\"domain\" optional=\"true\"/>")
                    .replace("anchorColumn=\"domain\" playerColumn=\"player\"","anchorColumn=\"group\" playerColumn=\"player\"")
                    .replace("<table id=\"demands\" dataset=\"raw-artifacts\">","<table id=\"demands\" dataset=\"raw-artifacts\"><column name=\"group\" path=\"group\" required=\"true\"/>")
                    .replace("<pre>self.p >= 0</pre>","<pre>receipt.entryConfirmed</pre>")
                    .replace("reason=\"Authored world condition, not an agent mental goal\"/>","reason=\"Explicit delayed self\"><variable name=\"receipt\" viewRef=\"entry-proof\" origin=\"STEP_TUPLE\"/></elementRuntime>")
                    .replace("<observers/>","<observers><view id=\"entry-proof\" name=\"EntryProof\" category=\"TUPLE\" keyFields=\"invocation\"><field name=\"entryConfirmed\" type=\"Boolean\" origin=\"STEP_ENABLED\"/></view></observers>");
                return configure.apply(xml);});
            helper.apply(data,helper.rows(data,Completeness.COMPLETE,true,"activation-1"));source=GenericMappingEngineTest.official(jcm);
            var engine=data.nativeFixture().nativeFixture().engine();var port=data.nativeFixture().nativeFixture().port();
            backend=new NativeOclBackend(engine.system().model(),engine.canonicalProgramHash(),engine.canonicalProgram().profile().engine().name());
            checkpoints=new XmlTaskCheckpoints(engine.canonicalProgram(),engine,new XmlTaskOclBindings(engine.canonicalProgram(),engine.system().model(),backend));
            for(var role:data.nativeFixture().roles())port.metadata(role,"xml:m0:observer-floor","0");
            var environment=cartago.CartagoEnvironment.getInstance();environment.init();
            try {
                for(String name:List.of("a","b")) {
                    var agent=new Agent();agent.setConsiderToAddMIForThisAgent(false);agent.initAg();agent.setASLSrc(behavior.toUri().toString());try(var input=Files.newInputStream(behavior)){agent.parseAS(input,behavior.toUri().toString());}
                    var ts=new TransitionSystem(agent,new Circumstance(),new jason.runtime.Settings(),new jason.architecture.AgArch(){@Override public String getAgName(){return name;}});
                    var identity=new BridgeEntityId("jason","agent","runtime-agent",source.semanticContract().project().name(),name,"incarnation-"+name);port.alias(identity.canonical(),data.nativeFixture().players().get(name.equals("a")?0:1));
                    var program=source.semanticContract().jasonPrograms().stream().filter(p->p.declarationId().equals(name)).findFirst().orElseThrow();
                    var context=new FrameworkObservationProviders.Context(environment,source,temp,"run",1,frames::add,reason->fail(reason));
                    var registry=FrameworkObservationProviders.agent(context,ts,identity,program);
                    try(var session=registry.open(engine.canonicalProgram().xmlPlan().requirements())) {
                        assertTrue(session.requiredUnavailable().isEmpty(),session.capabilities().toString());
                        for(int execution=0;execution<2;execution++){ts.getC().addAchvGoal(ASSyntax.parseLiteral("work"),Intention.EmptyInt);for(int i=0;i<20;i++)ts.reasoningCycle();}
                    }finally{agent.stopAg();}
                }
            }finally{environment.shutdown();}
            assertEquals(8,frames.size());set(1);
        }
        void set(int value)throws Exception {var n=data.nativeFixture();var port=n.nativeFixture().port();port.value(n.domain(),"p",IntegerValue.valueOf(value));CanonicalObservationMappingBindings.recordPropertyWritten(port,n.domain(),n.domain().cls().attribute("p",true),port.currentValue(n.domain(),"p"));}
        RuntimeEvent event(int frame,long sequence){return frames.get(frame).event("run",1,source.modelRevision(),sequence,java.time.Instant.EPOCH,"","",true);}
        void observe(int frame,long sequence){checkpoints.observe(event(frame,sequence),coverage,false);}
        @Override public void close(){checkpoints.clear();backend.close();}
    }
    static String uncheckedHash(Path file){try{return GenericMappingEngineTest.hash(file);}catch(Exception error){throw new IllegalStateException(error);}}
    @Test void actualFramesRetainExactPreAndDoNotBorrowOtherPerformersOrDemandEpisode()throws Exception {
        try(var f=new Fixture()) {
            f.observe(0,1);var entry=f.checkpoints.drain();assertEquals(1,entry.size());assertEquals("TASK_PRE",entry.getFirst().conditions().getFirst().kind());
            var image=f.checkpoints.savepoint();f.set(2);f.observe(1,2);var work=f.checkpoints.drain();
            var rows=work.stream().flatMap(w->NativeInvocationEvaluation.evaluate(w,2).stream()).toList();assertEquals(2,rows.stream().filter(r->r.kind().equals("TASK_PRE")||r.kind().equals("TASK_POST")).count());assertTrue(rows.stream().allMatch(r->r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS));
            f.checkpoints.restore(image);f.helper.apply(f.data,f.helper.rows(f.data,Completeness.COMPLETE,true,"activation-2"));f.observe(1,3);
            assertTrue(f.checkpoints.drain().stream().flatMap(w->w.conditions().stream()).noneMatch(c->c.kind().equals("TASK_POST")),"A terminal from an older activation cannot borrow a newer demand episode");
            f.observe(4,4);assertTrue(f.checkpoints.drain().stream().flatMap(w->w.conditions().stream()).noneMatch(c->c.unavailable().isBlank()),"Another performer cannot use A's demand/PRE");
        }
    }
    @Test void missingPreDuplicateEntryAndCoverageLossNeverCreateSuccessfulTaskTuple()throws Exception {
        try(var f=new Fixture()) {
            f.observe(1,1);assertTrue(f.checkpoints.drain().stream().flatMap(w->w.conditions().stream()).noneMatch(c->c.kind().equals("TASK_POST")));
            f.checkpoints.clear();f.observe(0,2);f.checkpoints.drain();f.observe(0,3);f.observe(1,4);
            assertTrue(f.checkpoints.drain().stream().flatMap(w->w.conditions().stream()).noneMatch(c->c.kind().equals("TASK_POST")),"Duplicated entry invalidates the original PRE");
            f.checkpoints.clear();f.observe(0,5);f.checkpoints.drain();f.checkpoints.clear();f.observe(1,6);
            assertTrue(f.checkpoints.drain().stream().flatMap(w->w.conditions().stream()).noneMatch(c->c.kind().equals("TASK_POST")));
        }
    }
    @Test void originalSessionConsumesOnlyTheExactActorsCorrelatedNativeTaskTuple()throws Exception {
        try(var f=new Fixture(true)) {
            var engine=f.data.nativeFixture().nativeFixture().engine();
            var owner=new org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner(engine,"run",1,f.source.modelRevision());
            try(owner) {
                assertEquals(2,owner.metrics().get("expressions"));
                owner.taskCheckpoint(f.event(0,1),coverage,false,null);evaluate(owner,engine,1,owner.drainTaskPrepared());var admission=owner.admission();
                owner.taskCheckpoint(f.event(0,2),coverage,false,null);owner.drainTaskPrepared();owner.restoreAdmission(admission);
                f.set(2);owner.taskCheckpoint(f.event(1,3),coverage,false,null);var result=evaluate(owner,engine,2,owner.drainTaskPrepared());
                var core=CanonicalJson.object(result.evidence().observation().get("originalEngine"));var context=CanonicalJson.object(((List<?>)core.get("contexts")).getFirst());var values=CanonicalJson.object(context.get("occurrences")).values().stream().map(CanonicalJson::object).toList();
                var alpha=values.stream().filter(v->v.get("sourceElement").equals("Positive")&&v.get("player").toString().contains("alpha")).findFirst().orElseThrow();var beta=values.stream().filter(v->v.get("sourceElement").equals("Positive")&&v.get("player").toString().contains("beta")).findFirst().orElseThrow();
                assertEquals("FULFILLED",alpha.get("leafStatus"));assertEquals("KNOWN",alpha.get("quality"));assertNotEquals("FULFILLED",beta.get("leafStatus"));
                assertTrue(((List<?>)context.get("originalApiCalls")).contains("TaskMarking.update"));assertTrue(result.evidence().constraints().stream().anyMatch(r->r.kind().equals("TASK_POST")&&r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS));
                assertFalse(CanonicalJson.object(context.get("inputs")).get("resources").toString().equals("{}"),"Resource needed-by is admitted by an exact XML/native witness");
                owner.taskCheckpoint(f.event(1,4),coverage,false,null);var duplicate=evaluate(owner,engine,3,owner.drainTaskPrepared());
                var duplicateCore=CanonicalJson.object(duplicate.evidence().observation().get("originalEngine"));var duplicateContext=CanonicalJson.object(((List<?>)duplicateCore.get("contexts")).getFirst());var duplicateActor=CanonicalJson.object(duplicateContext.get("occurrences")).values().stream().map(CanonicalJson::object).filter(v->v.get("sourceElement").equals("Positive")&&v.get("player").toString().contains("alpha")).findFirst().orElseThrow();
                assertNotEquals("KNOWN",duplicateActor.get("quality"),"A repeated terminal without active PRE must not reuse the earlier invocation certificate");
            }
            long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);while(!Boolean.TRUE.equals(owner.metrics().get("terminated"))&&System.nanoTime()<until)Thread.sleep(10);
            assertEquals(true,owner.metrics().get("terminated"));assertEquals(0,owner.metrics().get("expressions"));assertEquals(0,owner.metrics().get("pendingPins"));assertTrue(owner.admission().xmlSteps().active().isEmpty());assertTrue(owner.admission().xmlSteps().seen().isEmpty());
        }
    }
    @Test void declaredPureEntryCanPrecedeCreationWithoutReadingOrInventingDomainSelf()throws Exception {
        try(var f=new Fixture(false,true)) {
            var n=f.data.nativeFixture();var engine=n.nativeFixture().engine();var port=n.nativeFixture().port();
            port.delete(port.metadata(n.domain(),"semanticId"));
            n.projection().apply(port,n.epoch(),"cut-1",f.data.helper().tables(n,Completeness.COMPLETE,false));
            var demandRows=new ArrayList<RuntimeTables.Row>();for(String player:List.of("alpha","beta"))demandRows.add(f.data.helper().row("demand-"+player,Map.of("group","team","player","player:"+player,"required",player.equals("alpha"),"activation","creation"),Completeness.COMPLETE));
            f.data.demands().apply(port,n.epoch(),"cut-1",Map.of("demands",new RuntimeTables.Result(demandRows,Completeness.COMPLETE,List.of())),n.projection().resolve(port,n.epoch()));
            f.observe(0,1);var pre=f.checkpoints.drain();assertEquals(1,pre.size());var condition=pre.getFirst().conditions().getFirst();assertTrue(condition.unavailable().isBlank(),condition.unavailable());assertTrue(condition.variables().get("self").isUndefined());assertTrue(condition.after().allObjects().stream().noneMatch(o->o.cls().name().equals("Counter")));
            var created=n.nativeFixture().rules().execute(port,"create",new M0RuleExecutor.Input("objects",n.epoch(),"physical:new-parcel","cut-1",Completeness.COMPLETE,Map.of("uuid","new-parcel","label","new","flag",true),Map.of("actual-field-cut","proof"))).object();port.alias("parcel",created);port.value(created,"p",IntegerValue.valueOf(2));CanonicalObservationMappingBindings.recordPropertyWritten(port,created,created.cls().attribute("p",true),port.currentValue(created,"p"));
            n.projection().apply(port,n.epoch(),"cut-1",f.data.helper().tables(n,Completeness.COMPLETE,false));f.data.demands().apply(port,n.epoch(),"cut-1",Map.of("demands",new RuntimeTables.Result(demandRows,Completeness.COMPLETE,List.of())),n.projection().resolve(port,n.epoch()));
            f.observe(1,2);var terminal=f.checkpoints.drain();assertTrue(terminal.stream().flatMap(w->w.conditions().stream()).anyMatch(c->c.kind().equals("TASK_POST")),"Exact same invocation/role/demand may bind a newly observed self; its PRE still uses undefined self and the earlier native cut");
            assertTrue(terminal.stream().flatMap(w->w.conditions().stream()).filter(c->c.kind().equals("TASK_PRE")).allMatch(c->c.variables().get("self").isUndefined()));
            var result=terminal.stream().flatMap(w->NativeInvocationEvaluation.evaluate(w,2).stream()).filter(r->r.kind().equals("TASK_POST")).findFirst().orElseThrow();
            assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.SKIPPED,result.verdict(),"Unavailable @pre receiver is a coverage decision before native equality can turn undefined into false");assertTrue(result.diagnostic().startsWith("PRE_STATE_ATTRIBUTE_RECEIVER_UNAVAILABLE"));
        }
    }
    @Test void delayedSelfPolicyCannotAuthorizeAWorldReadingPrecondition()throws Exception {
        try(var f=new Fixture(false,true,s->s.replace("<pre>receipt.entryConfirmed</pre>","<pre>self.p.oclIsUndefined()</pre>"))) {
            var n=f.data.nativeFixture();var port=n.nativeFixture().port();port.delete(port.metadata(n.domain(),"semanticId"));n.projection().apply(port,n.epoch(),"cut-1",f.data.helper().tables(n,Completeness.COMPLETE,false));
            var rows=new RuntimeTables.Result(List.of(f.data.helper().row("demand",Map.of("group","team","player","player:alpha","required",true,"activation","creation"),Completeness.COMPLETE)),Completeness.COMPLETE,List.of());
            f.data.demands().apply(port,n.epoch(),"cut-1",Map.of("demands",rows),n.projection().resolve(port,n.epoch()));f.observe(0,1);
            var pre=f.checkpoints.drain().stream().flatMap(w->w.conditions().stream()).findFirst().orElseThrow();assertFalse(pre.unavailable().isBlank());
            assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.SKIPPED,NativeInvocationEvaluation.evaluate(NativeInvocationEvaluation.prepare(List.of(pre),List.of(),List.of()),1).getFirst().verdict());
        }
    }
    @Test void missingQualifiedResourceDefersOriginalTaskDespitePassingOperationPredicates()throws Exception {
        try(var f=new Fixture(true,false,s->s.replace("<resource target=\"Parcel\" column=\"domain\" type=\"Counter\"/>","<resource target=\"Parcel\" column=\"domain\" type=\"Team\"/>"))) {
            var engine=f.data.nativeFixture().nativeFixture().engine();
            try(var owner=new org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner(engine,"run",1,f.source.modelRevision())) {
                owner.taskCheckpoint(f.event(0,1),coverage,false,null);evaluate(owner,engine,1,owner.drainTaskPrepared());f.set(2);owner.taskCheckpoint(f.event(1,2),coverage,false,null);var result=evaluate(owner,engine,2,owner.drainTaskPrepared());
                var core=CanonicalJson.object(result.evidence().observation().get("originalEngine"));var context=CanonicalJson.object(((List<?>)core.get("contexts")).getFirst());var occurrence=CanonicalJson.object(context.get("occurrences")).values().stream().map(CanonicalJson::object).filter(v->v.get("sourceElement").equals("Positive")&&v.get("player").toString().contains("alpha")).findFirst().orElseThrow();
                assertNotEquals("FULFILLED",occurrence.get("leafStatus"));assertEquals("UNAVAILABLE:ORIGINAL_ENGINE_DEFERRED_INPUT_OR_RESOURCE",occurrence.get("quality"));assertFalse(((List<?>)context.get("deferred")).isEmpty());
                assertTrue(result.evidence().constraints().stream().anyMatch(r->r.kind().equals("TASK_POST")&&r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS));
            }
        }
    }
    @Test void postPreFieldQualityIsFrozenAtEntryAndCannotBeRepairedByTerminalObservation()throws Exception {
        try(var f=new Fixture(false,false,s->s.replace("<pre>self.p >= 0</pre>","<pre>true</pre>"))) {
            var n=f.data.nativeFixture();var port=n.nativeFixture().port();
            port.value(n.domain(),"p",IntegerValue.valueOf(9)); // Simulate a host edit: source proof still pins the previously observed value 1.
            f.observe(0,1);var entry=f.checkpoints.drain();assertTrue(entry.getFirst().conditions().getFirst().unavailable().isBlank(),"The authored constant PRE itself has no field dependency");
            f.set(10);f.observe(1,2);var rows=f.checkpoints.drain().stream().flatMap(w->NativeInvocationEvaluation.evaluate(w,2).stream()).toList();
            var post=rows.stream().filter(r->r.kind().equals("TASK_POST")).findFirst().orElseThrow();assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.SKIPPED,post.verdict(),"Correct current p=10 cannot retrospectively certify the unobserved PRE p=9");assertTrue(post.diagnostic().startsWith("PRE_STATE_"));
        }
    }
    @Test void observedRoleFloorReenactmentAndEpochFenceCannotReuseAnEarlierEntry()throws Exception {
        try(var f=new Fixture()) {
            var n=f.data.nativeFixture();var port=n.nativeFixture().port();port.metadata(n.roles().getFirst(),"xml:m0:observer-floor","20");f.observe(0,1);
            assertTrue(f.checkpoints.drain().stream().flatMap(w->w.conditions().stream()).findAny().isEmpty());port.metadata(n.roles().getFirst(),"xml:m0:observer-floor","0");f.observe(0,2);f.checkpoints.drain();
            port.metadata(n.roles().getFirst(),"xml:m0:lifetime","rejoined");n.projection().apply(port,n.epoch(),"cut-1",f.data.helper().tables(n,Completeness.COMPLETE,false));f.helper.apply(f.data,f.helper.rows(f.data,Completeness.COMPLETE,true,"activation-1"));f.observe(1,3);
            assertTrue(f.checkpoints.drain().stream().flatMap(w->w.conditions().stream()).noneMatch(c->c.kind().equals("TASK_POST")));
            var wrong=f.frames.get(2).event("another-run",2,f.source.modelRevision(),4,java.time.Instant.EPOCH,"","",true);assertThrows(IllegalArgumentException.class,()->f.checkpoints.observe(wrong,coverage,false));
            f.checkpoints.clear();assertNull(f.checkpoints.savepoint().epoch());assertTrue(f.checkpoints.savepoint().active().isEmpty());assertTrue(f.checkpoints.savepoint().latest().isEmpty());assertTrue(f.checkpoints.savepoint().seen().isEmpty());
        }
    }
    org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner.Evaluation evaluate(org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner owner,org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine engine,int version,List<NativeInvocationEvaluation.Work> work)throws Exception {
        String hash=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().exportState(engine.system().state()).commands().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var checks=new org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.PreparedEvaluation(engine.system().model(),List.of());
        return owner.submit(owner.prepare(version,"checkpoint-"+version,hash,coverage,false,true,List.of(),checks,work,4096)).get(10,java.util.concurrent.TimeUnit.SECONDS);
    }
}
