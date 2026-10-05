package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter;
import org.tzi.use.uml.sys.events.AtomicStateChangedEvent;
import com.google.common.eventbus.Subscribe;

class NativeGenericStepReplayTest {
    @TempDir Path root;
    @ParameterizedTest @ValueSource(strings={"north-17","south_900","định-danh-exact"})
    void exactVariantsCreateDeleteAttributesUnsetLinksAtomicPropertyBatchAndNonSteps(String variant)throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        String sessionId="session-"+variant,revision="model-"+variant;
        try(var original=new NativeRuntimeProjector(pipeline,sessionId,1,revision)) {
            String agent=pipeline.source().snapshot().agentDeclarations().getFirst().metadata().semanticId();
            var runtime=new BridgeEntityId("jason","agent","runtime-agent",variant,"agent-"+variant,"inc-"+variant);
            var binding=new BridgeRelationId("runtime-model-binding",List.of(runtime,BridgeEntityId.parse(agent)),"exact evidence","inc-"+variant);
            var sample=RuntimeVerificationFixtures.snapshot("snapshot-"+variant,0);
            String workspace=RuntimeVerificationFixtures.WORKSPACE,artifact="cartago:artifact:env:/main:"+variant;
            var facts=sample.facts().stream().filter(fact->fact.kind()==RuntimeFactKind.WORKSPACE).toList();
            var snapshot=new RuntimeSnapshot("snapshot-"+variant,revision,Instant.EPOCH,Instant.EPOCH,sample.startWatermarks(),sample.endWatermarks(),1,facts,
                    Map.of("cartago",Completeness.COMPLETE,"jason",Completeness.PARTIAL),"fingerprint-"+variant);
            original.applySnapshot(snapshot);
            assertEquals("INCOMPLETE",original.coordinator().latest().coverage());
            String profile="context Agent inv Disabled: false\ncontext Agent inv Negated: false\n";
            original.coordinator().loadProfileSource("flags-"+variant+".ocl",profile,Map.of("Agent::Disabled",false),Map.of("Agent::Negated",true));
            long sequence=0;
            assertTrue(original.apply(event(sessionId,revision,++sequence,RuntimeFactKind.AGENT,runtime,binding,Map.of(
                    "normalizedEventKind","SET_ATTRIBUTE","attribute","host","valueType","STRING","value",variant))));
            // No-op commits a version, duplicate commits nothing, neither is a semantic Step.
            var noop=event(sessionId,revision,++sequence,RuntimeFactKind.AGENT,runtime,binding,Map.of(
                    "normalizedEventKind","SET_ATTRIBUTE","attribute","host","valueType","STRING","value",variant));
            assertTrue(original.apply(noop));assertFalse(original.apply(noop));
            assertTrue(original.apply(event(sessionId,revision,++sequence,RuntimeFactKind.AGENT,runtime,binding,Map.of("normalizedEventKind","UNSET_ATTRIBUTE","attribute","host"))));
            var link=Map.<String,Object>of("normalizedEventKind","INSERT_LINK","association",org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("memberOf","Agent","Workspace"),"participantSemanticIds",List.of(agent,workspace));
            assertTrue(original.apply(event(sessionId,revision,++sequence,RuntimeFactKind.RELATION_STATE,runtime,binding,link)));
            assertTrue(original.apply(event(sessionId,revision,++sequence,RuntimeFactKind.RELATION_STATE,runtime,binding,Map.of(
                    "normalizedEventKind","DELETE_LINK","association",org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("memberOf","Agent","Workspace"),"participantSemanticIds",List.of(agent,workspace)))));
            var artifactId=new BridgeEntityId("cartago","environment","artifact","/main","box",variant);
            assertTrue(original.apply(event(sessionId,revision,++sequence,RuntimeFactKind.ARTIFACT,artifactId,null,RuntimeVerificationFixtures.artifact(artifact,variant))));
            var first=property(artifact,"p-"+variant,variant);
            var second=property(artifact,"q-"+variant,"value-"+variant);
            assertTrue(original.apply(event(sessionId,revision,++sequence,RuntimeFactKind.ARTIFACT,artifactId,null,Map.of(
                    "normalizedEventKind","APPLY_CARTAGO_PROPERTY_DELTA","semanticId",artifact,"properties",List.of(first,second),"removedPropertySemanticIds",List.of()))));
            assertTrue(original.apply(event(sessionId,revision,++sequence,RuntimeFactKind.ARTIFACT,artifactId,null,Map.of(
                    "normalizedEventKind","APPLY_CARTAGO_PROPERTY_DELTA","semanticId",artifact,"properties",List.of(property(artifact,"p-"+variant,"new-"+variant)),
                    "removedPropertySemanticIds",List.of(second.get("semanticId"))))));
            long evidenceOrdinal=original.coordinator().journal().persistedEntries();
            String evidenceState=original.coordinator().latest().stateHash();
            assertFalse(original.apply(event(sessionId,revision,++sequence,RuntimeFactKind.GOAL,runtime,null,Map.of("evidence",variant))),"Evidence is accepted/journaled, not materialized");
            assertEquals(evidenceOrdinal+1,original.coordinator().journal().persistedEntries());
            assertEquals(evidenceState,original.coordinator().lastObservation().stateHash());
            original.coordinator().manualVerify();
            assertTrue(original.apply(event(sessionId,revision,++sequence,RuntimeFactKind.ARTIFACT,artifactId,null,Map.of("normalizedEventKind","DELETE_CARTAGO_ARTIFACT","semanticId",artifact))));
            Path recording=root.resolve("recording");var replay=new NativeRuntimeReplay();replay.exportBundle(original,pipeline.export().useText(),recording);
            String originalSoil=new NativeUseSoilExporter().export(original.system()).commands();
            var expected=new ArrayList<String>();var results=new ArrayList<RuntimeVerificationResult>();var versions=new ArrayList<Long>();
            try(var bundle=replay.openBundle(recording);var cursor=bundle.atStep(0)) {
                assertTrue(bundle.report().complete());assertEquals(10,bundle.steps().size()); // 0 + snapshot + eight supported state changes
                for(var step:bundle.steps()) {cursor.forward(step.endOrdinal());expected.add(new NativeUseSoilExporter().export(cursor.projector().system()).commands());
                    results.add(cursor.projector().coordinator().latest());versions.add(cursor.projector().coordinator().verificationSnapshot().currentVersion());}
            }
            var session=new Session();session.setSystem(original.system());
            try(var controller=new NativeReplayStepController(session)) {
                controller.open(recording);
                for(int iteration=0;iteration<3;iteration++) {
                    controller.reset();verify(controller,expected,results,versions);
                    controller.next();verify(controller,expected,results,versions);controller.next();verify(controller,expected,results,versions);
                    controller.previous();verify(controller,expected,results,versions);controller.next();verify(controller,expected,results,versions);
                    while(controller.status().step()<controller.status().total()){controller.next();verify(controller,expected,results,versions);}
                }
                assertEquals("INCOMPLETE",controller.verificationSnapshot().result().coverage());
                assertTrue(controller.verificationSnapshot().result().outcomes().stream().anyMatch(outcome->outcome.diagnostic().equals("CONSTRAINT_DISABLED")));
                assertTrue(session.system().model().getClassInvariant("Agent::Negated").isNegated());
                assertEquals(originalSoil,new NativeUseSoilExporter().export(original.system()).commands());
            }
        }
    }
    private static RuntimeEvent event(String session,String revision,long ordinal,RuntimeFactKind kind,BridgeEntityId identity,BridgeRelationId binding,Map<String,Object> payload) {
        return new RuntimeEvent("event-"+ordinal,session,1,revision,"generic-test","synthetic",ordinal,Instant.EPOCH,RuntimeEventKind.CHANGED,
                kind,ProjectionStatus.MATERIALIZED_FAITHFULLY,identity,binding,"","",Map.of(),payload,new SourceWatermark("synthetic",ordinal),Completeness.COMPLETE,List.of());
    }
    private static Map<String,Object> property(String artifact,String id,String value){return Map.of("normalizedEventKind","UPSERT_CARTAGO_PROPERTY_SNAPSHOT",
            "semanticId","cartago:property:"+artifact+":"+id,"artifactSemanticId",artifact,"propertyId",id,"name",id,"values",List.of(value),"valueTypes",List.of("java.lang.String"),"annotations",List.of());}
    private static void verify(NativeReplayStepController controller,List<String> soil,List<RuntimeVerificationResult> results,List<Long> versions) {
        int step=controller.status().step();var current=controller.verificationSnapshot();
        assertEquals(soil.get(step),new NativeUseSoilExporter().export(controller.system()).commands());assertEquals(versions.get(step),current.currentVersion());
        assertEquals(results.get(step).semanticEvidence(),current.result().semanticEvidence());assertEquals(results.get(step).resultHash(),current.result().resultHash());
        var state=controller.system().state();assertEquals(state.numObjects(),state.allObjects().stream().map(object->object.state(state).attributeValue("semanticId")).distinct().count());
        assertTrue(state.allLinks().stream().allMatch(link->state.allObjects().containsAll(link.linkedObjects())));
    }

    @Test void parityFailureDuringNextRestoresValidStateAndPublishesNoHalfState() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try(var original=new NativeRuntimeProjector(pipeline,RuntimeVerificationFixtures.SESSION,1,RuntimeVerificationFixtures.REVISION)) {
            original.applySnapshot(RuntimeVerificationFixtures.snapshot("initial",0));original.apply(RuntimeVerificationFixtures.delta("B",1,"B"));
            var recording=root.resolve("valid");new NativeRuntimeReplay().exportBundle(original,pipeline.export().useText(),recording);
            var session=new Session();session.setSystem(original.system());try(var controller=new NativeReplayStepController(session)) {
                controller.open(recording);controller.next();var before=controller.verificationSnapshot();var system=session.system();
                String soil=new NativeUseSoilExporter().export(system).commands();var notifications=new AtomicInteger();
                system.getEventBus().register(new Object(){@Subscribe public void commit(AtomicStateChangedEvent event){notifications.incrementAndGet();}});
                Object selection=((java.util.concurrent.atomic.AtomicReference<?>)StepReplayProof.field(controller,"selected")).get();
                var accessor=selection.getClass().getDeclaredMethod("cursor");accessor.setAccessible(true);Object cursor=accessor.invoke(selection);
                var body=new LinkedHashMap<>(CanonicalJson.object(CanonicalJson.decode(((String)StepReplayProof.field(cursor,"nextLine")).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                body.remove("entryHash");var result=new LinkedHashMap<>(CanonicalJson.object(body.get("result")));result.put("stateHash","0".repeat(64));body.put("result",result);
                body.put("entryHash",ExternalOclConstraintService.sha256(CanonicalJson.encode(body)));
                var next=cursor.getClass().getDeclaredField("nextLine");next.setAccessible(true);next.set(cursor,new String(CanonicalJson.encode(body),java.nio.charset.StandardCharsets.UTF_8));
                assertThrows(RuntimeException.class,controller::next);assertSame(system,session.system());assertEquals(before,controller.verificationSnapshot());
                assertEquals(soil,new NativeUseSoilExporter().export(system).commands());assertEquals(0,notifications.get());
                assertTrue(controller.historyTail().entries().stream().allMatch(entry->entry.result().stateVersion()<=before.currentVersion()));
                assertTrue(controller.historyTail().diagnostic().contains("FAILED_REPLAY_CONTEXT"));
                assertTrue(controller.status().reconstructionRequired());assertThrows(IllegalStateException.class,controller::next);
                controller.reset();assertFalse(controller.status().reconstructionRequired());controller.next();controller.next();
                assertEquals(original.coordinator().latest().stateHash(),controller.verificationSnapshot().result().stateHash());
            }
        }
    }

    @ParameterizedTest @ValueSource(strings={"GAP","CHAIN","PAYLOAD","STATE_HASH","SCHEMA","SCOPE","TRUNCATE"})
    void corruptAndIncompatibleControlsCannotReplaceSession(String control)throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();try(var original=new NativeRuntimeProjector(pipeline,RuntimeVerificationFixtures.SESSION,1,RuntimeVerificationFixtures.REVISION)) {
            original.applySnapshot(RuntimeVerificationFixtures.snapshot("initial",0));Path bundle=root.resolve("negative");new NativeRuntimeReplay().exportBundle(original,pipeline.export().useText(),bundle);
            var entries=Files.readAllLines(bundle.resolve("runtime.jsonl")).stream().map(line->new LinkedHashMap<>(CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8))))).toList();
            var manifest=new LinkedHashMap<>(CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(bundle.resolve("manifest.json")))));
            switch(control) {
                case "GAP" -> {entries.getLast().put("kind","GAP");entries.getLast().put("diagnostic","real missing record control");}
                case "CHAIN" -> entries.getLast().put("previousHash","0".repeat(64));
                case "PAYLOAD" -> {entries.getLast().put("kind","EVENT");entries.getLast().put("payload",Map.of("incompatible",true));}
                case "STATE_HASH" -> {var result=new LinkedHashMap<>(CanonicalJson.object(entries.getLast().get("result")));result.put("stateHash","0".repeat(64));entries.getLast().put("result",result);}
                case "SCHEMA" -> manifest.put("schemaVersion","999");
                case "SCOPE" -> manifest.put("scope","FULL_JACAMO_SEMANTICS");
                case "TRUNCATE" -> { }
            }
            String previous="";var encoded=new ArrayList<String>();
            for(var entry:entries){entry.remove("entryHash");if(!control.equals("CHAIN"))entry.put("previousHash",previous);previous=ExternalOclConstraintService.sha256(CanonicalJson.encode(entry));entry.put("entryHash",previous);encoded.add(new String(CanonicalJson.encode(entry),java.nio.charset.StandardCharsets.UTF_8));}
            if(control.equals("TRUNCATE"))encoded.removeLast();
            Files.writeString(bundle.resolve("runtime.jsonl"),String.join("\n",encoded)+"\n");
            var hashes=new LinkedHashMap<>(CanonicalJson.object(manifest.get("files")));hashes.put("runtime.jsonl",ExternalOclConstraintService.sha256(Files.readAllBytes(bundle.resolve("runtime.jsonl"))));manifest.put("files",hashes);
            Files.write(bundle.resolve("manifest.json"),CanonicalJson.encode(manifest));
            var session=new Session();session.setSystem(original.system());try(var controller=new NativeReplayStepController(session)) {
                assertThrows(RuntimeException.class,()->controller.open(bundle),control);assertSame(original.system(),session.system());assertFalse(controller.active());assertFalse(controller.busy());
            }
        }
    }
}
