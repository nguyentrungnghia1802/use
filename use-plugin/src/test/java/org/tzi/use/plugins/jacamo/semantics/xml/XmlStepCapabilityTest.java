package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import static org.junit.jupiter.api.Assertions.*;
/** Actual Jason component, not a live MAS/TCP acceptance replacement. */
class XmlStepCapabilityTest {
    @TempDir Path temp;
    @Test void xmlCompilesExactStepRequestAndRegistryObservesActualSourceExecutionWithoutCaseJava()throws Exception {
        Path jcm=Files.writeString(temp.resolve("demo.jcm"),"mas dispatch {\n agent worker : worker.asl\n}\n"),asl=Files.writeString(temp.resolve("worker.asl"),"+!deliver <- .print(\"ready\"); .print(\"sent\").\n"),ocl=Files.writeString(temp.resolve("case.ocl"),"context X inv Valid: true\n");
        String xml=MappingXmlReaderTest.XML.replace("1".repeat(64),GenericMappingEngineTest.hash(jcm)).replace("2".repeat(64),GenericMappingEngineTest.hash(ocl))
            .replace("</sources>","<file id=\"behavior\" path=\"worker.asl\" kind=\"ASL\" sha256=\""+GenericMappingEngineTest.hash(asl)+"\"/></sources>")
            .replace("<requirements/>","<requirements><capability id=\"steps\" provider=\"jason.plan-boundaries.v1\" required=\"true\" sourceRef=\"behavior\"><plan trigger=\"deliver\"><step task=\"PrepareDispatch\" first=\"0\" after=\"1\" variables=\"\"/><step task=\"SendDispatch\" first=\"1\" after=\"2\" variables=\"\"/></plan></capability></requirements>");
        Path mapping=Files.writeString(temp.resolve("mapping.xml"),xml);
        var inputs=MappingInputs.read(jcm,mapping,ocl);var request=CapabilityRequirements.compile(inputs).getFirst();
        var model=GenericMappingEngineTest.official(jcm);var program=new OfficialJasonAdapter().adapt(temp,asl,"dispatch","worker").program();
        jason.util.Config.get().setProperty(jason.util.Config.START_WEB_MI,"false");var actor=new Agent();actor.setConsiderToAddMIForThisAgent(false);actor.initAg();actor.setASLSrc(asl.toUri().toString());try(var input=Files.newInputStream(asl)){actor.parseAS(input,asl.toUri().toString());}
        var ts=new TransitionSystem(actor,new Circumstance(),new jason.runtime.Settings(),new jason.architecture.AgArch(){@Override public String getAgName(){return "worker";}});
        var frames=new ArrayList<VerificationObservation>();var environment=cartago.CartagoEnvironment.getInstance();environment.init();
        var context=new FrameworkObservationProviders.Context(environment,model,temp,"run",1,frames::add,reason->fail(reason));
        var registry=FrameworkObservationProviders.agent(context,ts,new BridgeEntityId("jason","agent","runtime-agent","dispatch","worker","incarnation"),program);
        try {
            try(var session=registry.open(List.of(request))) {
                assertTrue(session.requiredUnavailable().isEmpty(),session.capabilities().toString());
                ts.getC().addAchvGoal(ASSyntax.parseLiteral("deliver"),Intention.EmptyInt);for(int i=0;i<30;i++)ts.reasoningCycle();
                assertEquals(List.of("PrepareDispatch","PrepareDispatch","SendDispatch","SendDispatch"),frames.stream().map(f->f.data().get("sourceStepId")).toList());
                var decoder=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.JasonStepCutDecoder(model,request);
                var decoded=frames.stream().map(f->decoder.decode(f,"run",1,model.modelRevision())).toList();
                assertEquals(List.of("PrepareDispatch","PrepareDispatch","SendDispatch","SendDispatch"),decoded.stream().map(s->s.task()).toList());
                assertTrue(decoded.getFirst().enabled());assertNull(decoded.getFirst().succeeded());assertEquals(true,decoded.get(1).succeeded());
                assertEquals(decoded.getFirst().invocation(),decoded.get(1).invocation());assertNotEquals(decoded.getFirst().invocation(),decoded.get(2).invocation());
                assertThrows(IllegalArgumentException.class,()->decoder.decode(frames.getFirst(),"other-run",1,model.modelRevision()));
                var changed=new LinkedHashMap<>(frames.getFirst().data());var binding=new LinkedHashMap<>(CanonicalJson.object(changed.get("bindings")));var manifest=new LinkedHashMap<>(CanonicalJson.object(binding.get("instrumentation")));
                manifest.put("plans",List.of());binding.put("instrumentation",manifest);binding.put("instrumentationHash",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(manifest));changed.put("bindings",binding);
                var fake=new VerificationObservation(frames.getFirst().kind(),frames.getFirst().subject(),frames.getFirst().quality(),frames.getFirst().diagnostic(),frames.getFirst().sourceTime(),changed);
                assertThrows(IllegalArgumentException.class,()->decoder.decode(fake,"run",1,model.modelRevision()),"A self-consistent manifest hash cannot replace actual requested source plan evidence");
                String datasets=xml.replace("<runtime/>","<runtime><datasets><jasonSteps id=\"step-frames\" requestRef=\"steps\"/></datasets></runtime>");
                Files.writeString(mapping,datasets);
                var sources=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources(new MappingXmlReader().parse(Files.readAllBytes(mapping),mapping.toString()),model,List.of(request));
                var facts=new ArrayList<RuntimeFact>();
                var current=new BridgeEntityId("jason","agent","runtime-agent","dispatch","worker","incarnation");
                facts.add(new RuntimeFact(current,RuntimeFactKind.AGENT,Map.of("name","worker"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
                long sequence=0;
                for(var frame:frames){var event=frame.event("run",1,model.modelRevision(),++sequence,java.time.Instant.EPOCH,"","",true);facts.add(new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),List.of()));}
                var marks=Map.of(VerificationObservation.SOURCE_ID,new SourceWatermark(VerificationObservation.SOURCE_ID,sequence),"jason",new SourceWatermark("jason",1));
                // Entry and exit of a single invocation replace one evidence-index slot in production.
                var latest=new LinkedHashMap<String,RuntimeFact>();facts.forEach(f->latest.put(f.id().canonical(),f));
                var cut=new RuntimeSnapshot("cut",model.modelRevision(),java.time.Instant.EPOCH,java.time.Instant.EPOCH,marks,marks,1,new ArrayList<>(latest.values()),Map.of(VerificationObservation.SOURCE_ID,Completeness.COMPLETE,"jason",Completeness.COMPLETE),"state");
                var data=sources.capture(cut,"run",1).get("step-frames");
                assertEquals(Completeness.PARTIAL,data.populationQuality(),"Callbacks do not certify absence of all possible invocations");
                assertEquals(2,data.rows().size());assertTrue(data.rows().stream().allMatch(r->r.quality()==Completeness.COMPLETE));
                assertEquals(Set.of("PrepareDispatch","SendDispatch"),data.rows().stream().map(r->r.values().get("task")).collect(java.util.stream.Collectors.toSet()));
                assertTrue(data.rows().stream().allMatch(r->Boolean.TRUE.equals(r.values().get("succeeded"))&&current.canonical().equals(r.values().get("agent"))&&r.proofs().containsKey("capability:steps")));
                var absent=new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),marks,marks,1,latest.values().stream().filter(f->f.kind()!=RuntimeFactKind.AGENT).toList(),cut.sourceCompleteness(),cut.stateFingerprint());
                assertTrue(sources.capture(absent,"run",1).get("step-frames").rows().stream().allMatch(r->r.quality()!=Completeness.COMPLETE&&!r.values().containsKey("succeeded")),"Stale agent incarnation must not receive a current success tuple");
            }
            assertTrue(ts.getGoalListeners().stream().noneMatch(JasonStepObservation.class::isInstance));
            Files.writeString(mapping,xml.replace("after=\"2\"","after=\"3\""));
            try(var unavailable=registry.open(CapabilityRequirements.compile(MappingInputs.read(jcm,mapping,ocl)))) {assertEquals(1,unavailable.requiredUnavailable().size());}
            Files.writeString(asl,Files.readString(asl)+"// changed source\n");
            try(var unavailable=registry.open(List.of(request))) {assertEquals(1,unavailable.requiredUnavailable().size());assertTrue(unavailable.capabilities().getFirst().reason().contains("CHECKSUM"));}
        }finally{actor.stopAg();environment.shutdown();}
    }
}
