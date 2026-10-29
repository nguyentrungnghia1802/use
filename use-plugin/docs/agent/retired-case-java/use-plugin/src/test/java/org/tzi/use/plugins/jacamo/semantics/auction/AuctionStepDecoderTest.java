package org.tzi.use.plugins.jacamo.semantics.auction;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import jason.asSemantics.*;
import jason.asSyntax.ASSyntax;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import static org.junit.jupiter.api.Assertions.*;

class AuctionStepDecoderTest {
    @Test void capturedMissingContextRetainsItsQualityAndHasAnIndependentlyCheckedSourceRecipe()throws Exception {
        try(var fixture=new Recorded()) {
            var decoded=fixture.decoder.decode(fixture.event(fixture.frames.getFirst(),1));
            assertEquals("A_start_t1",decoded.task());assertEquals("ENABLED",decoded.phase());assertEquals(Completeness.PARTIAL,decoded.quality());assertEquals("",decoded.scheme());
            var failed=fixture.decoder.decode(fixture.event(fixture.frames.getLast(),2));assertEquals("FAILED",failed.phase());assertEquals(decoded.invocation(),failed.invocation());assertEquals(decoded.goalInvocation(),failed.goalInvocation());
            assertEquals(Completeness.PARTIAL,failed.quality());
        }
    }
    @Test void recipeSourceRowsLocationActorAndEpochTamperingCannotGainAdmissionByRehashing()throws Exception {
        try(var fixture=new Recorded()) {
            for(String tamper:List.of("source","rows","location","actor","epoch","recipe","counter")) {
                var frame=variant(fixture.frames.getFirst(),data->{var bindings=map(data.get("bindings"));var manifest=map(bindings.get("instrumentation"));
                    switch(tamper) {
                        case "source" -> manifest.put("sourceSha256","0".repeat(64));
                        case "rows" -> {var plan=map(((List<?>)manifest.get("plans")).getFirst());var rows=(List<?>)plan.get("executableRows");map(rows.getFirst()).put("phase","FAILED");}
                        case "location" -> map(bindings.get("sourceLocation")).put("afterOrdinal",6);
                        case "actor" -> {var id=BridgeEntityId.parse((String)data.get("agentIdentity"));data.put("agentIdentity",new BridgeEntityId(id.authority(),id.dimension(),id.kind(),id.scope(),"alice",id.incarnation()).canonical());bindings.put("agentName","alice");bindings.put("agentDeclaration","alice");}
                        case "epoch" -> map(bindings.get("capturedRun")).put("generation",fixture.live.run.generation()+1);
                        case "recipe" -> manifest.put("recipe","UNAPPROVED/2.0");
                        case "counter" -> data.put("goalInvocation","jason-goal:"+fixture.live.run.id()+":"+fixture.agentIdentity.incarnation()+":");
                    }
                    bindings.put("instrumentationHash",SemanticJson.hash(manifest));});
                assertThrows(IllegalArgumentException.class,()->fixture.decoder.decode(fixture.event(frame,2)),tamper);
            }
            var raw=fixture.frames.getFirst();
            var promoted=new VerificationObservation(raw.kind(),raw.subject(),Completeness.COMPLETE,"",raw.sourceTime(),raw.data());
            assertTrue(assertThrows(IllegalArgumentException.class,()->fixture.decoder.decode(fixture.event(promoted,2))).getMessage().contains("COMPLETE_SCOPE_PROOF"));
        }
    }
    @Test void nativeAuctionIngressValidatesCaseProofBeforeMutationAndCannotPromoteTheObserverPopulation()throws Exception {
        try(var fixture=new Recorded()) {
            var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(fixture.live.source,NativeProjectionMode.AUTO,fixture.program);
            try(var projector=new NativeRuntimeProjector(pipeline,fixture.live.run.id(),fixture.live.run.generation(),fixture.live.source.modelRevision())) {
                projector.applySnapshot(fixture.live.cut(),fixture.live.run.id(),fixture.live.run.generation());var before=projector.mutations().savepoint();
                assertFalse(projector.apply(fixture.event(fixture.frames.getFirst(),1)));assertEquals(before,projector.mutations().savepoint());
                var malformed=variant(fixture.frames.getLast(),data->map(map(data.get("bindings")).get("capturedRun")).put("generation",100));
                assertTrue(assertThrows(NativeRuntimeProtocolException.class,()->projector.apply(fixture.event(malformed,2))).getMessage().contains("AUCTION_OBSERVER_PROOF_REJECTED"));
                assertEquals(before,projector.mutations().savepoint());assertTrue(projector.resyncRequired());
            }
        }
    }
    static final class Recorded implements AutoCloseable {
        final AuctionLiveFixture live=new AuctionLiveFixture();final org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));
        final AuctionStepObservationDecoder decoder=new AuctionStepObservationDecoder(program);final Agent agent=AuctionStagingInstrumentationTest.parse();
        final BridgeEntityId agentIdentity=new BridgeEntityId("jason","agent","runtime-agent","auction","bob",UUID.randomUUID().toString());
        final List<VerificationObservation> frames=new ArrayList<>();final List<String> gaps=new ArrayList<>();final AuctionStepObservation owner;
        Recorded()throws Exception {
            var arch=new jason.architecture.AgArch(){@Override public String getAgName(){return "bob";}};var ts=new TransitionSystem(agent,new Circumstance(),new jason.runtime.Settings(),arch);
            var source=live.source.semanticContract().jasonPrograms().stream().filter(p->p.declarationId().equals("bob")).findFirst().orElseThrow();
            owner=new AuctionStepObservation(ts,agentIdentity,live.run.id(),live.run.generation(),live.source.modelRevision(),source,AuctionStagingInstrumentationTest.ROOT,frames::add,gaps::add);
            ts.getC().addAchvGoal(ASSyntax.parseLiteral("start[scheme(\"missing\")]"),Intention.EmptyInt);for(int i=0;i<20&&frames.size()<2;i++)ts.reasoningCycle();assertEquals(2,frames.size());assertTrue(gaps.isEmpty(),gaps.toString());
        }
        RuntimeEvent event(VerificationObservation frame,long sequence){return frame.event(live.run.id(),live.run.generation(),live.source.modelRevision(),sequence,Instant.EPOCH,"","");}
        public void close()throws Exception {try{owner.close();agent.stopAg();}finally{live.close();}}
    }
    static VerificationObservation variant(VerificationObservation frame,Consumer<Map<String,Object>> change)throws Exception {
        var data=new com.fasterxml.jackson.databind.ObjectMapper().readValue(CanonicalJson.encode(frame.data()),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});change.accept(data);
        return new VerificationObservation(frame.kind(),frame.subject(),frame.quality(),frame.diagnostic(),frame.sourceTime(),data);
    }
    static Map<String,Object> map(Object value){return CanonicalJson.object(value);}
}
