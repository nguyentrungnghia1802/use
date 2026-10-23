package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import cartago.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionObligationProjectionTest {
    @Test void actualTenSecondFailureIsRetainedThroughAdministrativeSchemeRecoveryOnTheSameNativeSystem()throws Exception {
        String old=System.getProperty(AuctionSchemeObservation.PROPERTY);System.setProperty(AuctionSchemeObservation.PROPERTY,"true");
        try(var fixture=new AuctionOrgFixture(true)) {
            var live=fixture.live;var clocks=new ArrayList<VerificationObservation>();
            try(var clock=new AuctionClockObservation(live.run.id(),live.run.generation(),live.source.modelRevision(),clocks::add)) {
                clock.pulse(false);fixture.organizationBoard();var group=fixture.group();var scheme=fixture.scheme("deadline","service");live.auction("deadline");var participant=AuctionMonitorProjectionTest.participant(fixture,group);
                CslRuntimeTestRegistry.bind(live.source.semanticContract(),participant.getName(),"alice");
                try {
                    live.actor.doAction(group,new Op("addScheme",scheme.getName()),5000);var board=live.boards.getFirst();
                    AuctionMonitorProjectionTest.await(()->board.getSchState().getGroupsResponsibleFor().stream().anyMatch(g->g.hasPlayer(participant.getName(),"participant")));
                    live.actor.doAction(scheme,new Op("commitMission","mAuctioneer"),5000);participant.doAction(scheme,new Op("commitMission","mParticipant"),5000);live.actor.doAction(scheme,new Op("goalAchieved","start"),5000);
                    AuctionMonitorProjectionTest.await(()->live.events.stream().anyMatch(e->e.subsystem().equals("npl")&&e.kind()==RuntimeEventKind.NORM_ACTIVATED&&e.after().get("aim").toString().contains(",bid,")));
                    var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
                    try(var projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                        projector.applySnapshot(withClock(fixture.cut(),clocks.getLast(),live,1),live.run.id(),live.run.generation());var system=projector.system();
                        var obligation=system.state().allObjects().stream().filter(o->o.cls().name().equals("AuctionObligation")).findFirst().orElseThrow();
                        assertEquals(new StringValue("bid"),obligation.state(system.state()).attributeValue("goal"));assertEquals(BooleanValue.FALSE,obligation.state(system.state()).attributeValue("unfulfilled"));
                        assertEquals(IntegerValue.valueOf(-1),obligation.state(system.state()).attributeValue("endedAt"));
                        long limit=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<limit&&live.events.stream().noneMatch(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED&&e.after().get("aim").toString().contains(",bid,"))){clock.pulse(true);Thread.sleep(100);}
                        assertTrue(live.events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED&&e.after().get("aim").toString().contains(",bid,")));clock.pulse(false);
                        projector.applySnapshot(withClock(fixture.cut(),clocks.getLast(),live,2),live.run.id(),live.run.generation());
                        assertEquals(BooleanValue.TRUE,obligation.state(system.state()).attributeValue("unfulfilled"));assertFalse(obligation.state(system.state()).attributeValue("endedAt").isUndefined());
                        var ended=obligation.state(system.state()).attributeValue("endedAt");live.actor.doAction(scheme,new Op("admCommand","goalSatisfied(bid)"),5000);
                        AuctionMonitorProjectionTest.await(()->live.events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED&&e.after().get("aim").toString().contains(",decide,")));
                        projector.applySnapshot(withClock(fixture.cut(),clocks.getLast(),live,3),live.run.id(),live.run.generation());assertSame(system,projector.system());
                        assertEquals(BooleanValue.TRUE,obligation.state(system.state()).attributeValue("unfulfilled"));assertEquals(ended,obligation.state(system.state()).attributeValue("endedAt"));
                        var decide=system.state().allObjects().stream().filter(o->o.cls().name().equals("AuctionObligation")&&new StringValue("decide").equals(o.state(system.state()).attributeValue("goal"))).findFirst().orElseThrow();
                        var decideRaw=CanonicalJson.object(CanonicalJson.decode(projector.mutations().metadata(decide,"auctionObligationSource").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        long decideDeadline=Long.parseLong((String)decideRaw.get("deadline")),decideCreated=Long.parseLong((String)decideRaw.get("createdEpochMillis"));
                        assertTrue(Math.abs(decideDeadline-decideCreated-3600000L)<1000,"Actual original one-hour annotation is preserved; no shortened deadline");
                        assertTrue(projector.mutations().metadata(obligation,"auctionObligationSource").contains("NPL_TIME_ANNOTATIONS/1.0"));
                        try(var backend=new NativeOclBackend(system.model(),"N03","AUCTION_XML/1.0")) {
                            var clause=program.casePlan().ocl().clauses().stream().filter(c->c.name().equals("WithinDeadline")).findFirst().orElseThrow();
                            var predicate=backend.compile(new OclContractSpec("actual-deadline",OclContractSpec.Kind.INVARIANT,obligation.cls(),null,clause.body()));
                            assertEquals(NativeOclOutcome.Status.FAIL,backend.evaluate(predicate,UseStateReadView.pin(null,system.state()),Map.of("self",obligation.value()),"").status(),"Source administrative progress must not repair the retained violated activation");
                        }
                        java.nio.file.Files.write(java.nio.file.Path.of("target/auction-implementation/N03-actual-expiry.json"),CanonicalJson.encode(Map.of("scope","ACTUAL_SCHEME_NPL_NATIVE_COMPONENT_NOT_E01","events",live.events.stream().filter(e->e.subsystem().equals("npl")).map(ContractPayloads::event).toList(),"source",projector.mutations().metadata(obligation,"auctionObligationSource"))));
                        assertReplay(projector,"actual-expiry-admin");
                        var current=fixture.cut();var omitted=new ArrayList<RuntimeFact>();
                        for(var fact:current.facts())if(fact.kind()==RuntimeFactKind.NORM_INSTANCE&&fact.id().canonical().equals(projector.mutations().metadata(obligation,"semanticId"))) {
                            var raw=new LinkedHashMap<>(fact.values());var incomplete=new LinkedHashMap<>(CanonicalJson.object(raw.get("sourceLifecycle")));incomplete.put("terminalTimes",Map.of());incomplete.put("terminalEpochMillis","");incomplete.put("status","INCOMPLETE");incomplete.put("diagnostic","TEST_EXTRACTION_TERMINAL_UNAVAILABLE");incomplete.remove("hash");incomplete.put("hash",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(incomplete));raw.put("sourceLifecycle",incomplete);
                            omitted.add(new RuntimeFact(fact.id(),fact.kind(),raw,List.of(),fact.projectionStatus(),Completeness.PARTIAL,List.of()));
                        }else if(fact.kind()==RuntimeFactKind.NORM_INSTANCE)omitted.add(fact);
                        projector.applySnapshot(withClock(replaceNorms(current,omitted),clocks.getLast(),live,4),live.run.id(),live.run.generation());
                        assertTrue(obligation.state(system.state()).attributeValue("endedAt").isUndefined());assertEquals(BooleanValue.TRUE,obligation.state(system.state()).attributeValue("unfulfilled"),"Positive source failure remains latched even when a later extraction omits terminal time");
                        assertFalse(projector.mutations().metadata(obligation,"auctionObligationFirstTerminalEpoch").isBlank());
                        assertReplay(projector,"partial-terminal-time");
                    }
                }finally{CslRuntimeTestRegistry.closeCartagoContext(participant);}
            }
        }finally{if(old==null)System.clearProperty(AuctionSchemeObservation.PROPERTY);else System.setProperty(AuctionSchemeObservation.PROPERTY,old);}
    }
    private static void assertReplay(NativeRuntimeProjector projector,String prefix)throws Exception {
        var coordinator=projector.coordinator();var accepted=coordinator.awaitEvaluation(coordinator.latest(),15000);
        var recording=java.nio.file.Files.createTempDirectory(java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/auction-implementation")),"Q06-"+prefix+"-");
        var replay=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay();
        replay.exportBundle(projector,new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseExporter().export(projector.system().model()).useText(),recording);
        var report=replay.replay(recording);assertTrue(report.complete(),report.diagnostics().toString());assertEquals(accepted.stateHash(),report.finalStateHash());assertEquals(accepted.resultHash(),report.finalResultHash());
    }
    @Test void activeCutMissingNormOrClockAndLateAttachmentStayUnknownAndBadProofsCannotRebindAPlayer()throws Exception {
        String old=System.getProperty(AuctionSchemeObservation.PROPERTY);System.setProperty(AuctionSchemeObservation.PROPERTY,"true");
        try(var fixture=new AuctionOrgFixture(true)) {
            var live=fixture.live;var clocks=new ArrayList<VerificationObservation>();
            try(var clock=new AuctionClockObservation(live.run.id(),live.run.generation(),live.source.modelRevision(),clocks::add)) {
                clock.pulse(false);fixture.organizationBoard();var group=fixture.group();var scheme=fixture.scheme("partial","service");live.auction("partial");var participant=AuctionMonitorProjectionTest.participant(fixture,group);
                CslRuntimeTestRegistry.bind(live.source.semanticContract(),participant.getName(),"alice");
                try {
                    var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
                    live.actor.doAction(group,new Op("addScheme",scheme.getName()),5000);var board=live.boards.getFirst();AuctionMonitorProjectionTest.await(()->board.getSchState().getGroupsResponsibleFor().stream().anyMatch(g->g.hasPlayer(participant.getName(),"participant")));
                    live.actor.doAction(scheme,new Op("commitMission","mAuctioneer"),5000);participant.doAction(scheme,new Op("commitMission","mParticipant"),5000);live.actor.doAction(scheme,new Op("goalAchieved","start"),5000);
                    AuctionMonitorProjectionTest.await(()->live.events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED&&e.after().get("aim").toString().contains(",bid,")));
                    var decoder=new AuctionObligationDecoder(program);var cut=fixture.cut();var bid=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.NORM_INSTANCE&&f.values().get("aim").toString().contains(",bid,")).findFirst().orElseThrow();
                    assertNotNull(decoder.decode(bid.id(),bid.values(),live.run.id(),live.run.generation()));
                    var wrong=new LinkedHashMap<>(bid.values());var proof=new LinkedHashMap<>(CanonicalJson.object(wrong.get("sourceLifecycle")));proof.put("clockIdentity","foreign-clock");proof.remove("hash");proof.put("hash",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(proof));wrong.put("sourceLifecycle",proof);
                    var wrongClock=wrong;assertTrue(assertThrows(IllegalArgumentException.class,()->decoder.decode(bid.id(),wrongClock,live.run.id(),live.run.generation())).getMessage().contains("TIME_BASIS"));
                    wrong=new LinkedHashMap<>(bid.values());proof=new LinkedHashMap<>(CanonicalJson.object(wrong.get("sourceLifecycle")));var subjectTerm=new LinkedHashMap<>(CanonicalJson.object(proof.get("subjectTerm")));var subjectKey=new LinkedHashMap<>(CanonicalJson.object(subjectTerm.get("key")));subjectKey.put("functor","somebody_else");subjectTerm.put("key",subjectKey);subjectTerm.put("raw","somebody_else");proof.put("subjectTerm",subjectTerm);proof.remove("hash");proof.put("hash",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(proof));wrong.put("sourceLifecycle",proof);var wrongSubject=wrong;
                    assertTrue(assertThrows(IllegalArgumentException.class,()->decoder.decode(bid.id(),wrongSubject,live.run.id(),live.run.generation())).getMessage().contains("SUBJECT"),"A correctly rehashed proof must still bind aim and subject to the same actual player");
                    try(var projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                        projector.applySnapshot(withClock(cut,clocks.getLast(),live,1),live.run.id(),live.run.generation());var obligation=projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("AuctionObligation")).findFirst().orElseThrow();
                        var missing=replaceNorms(cut,List.of());projector.applySnapshot(withClock(missing,clocks.getLast(),live,2),live.run.id(),live.run.generation());
                        assertTrue(obligation.state(projector.system().state()).attributeValue("unfulfilled").isUndefined());assertTrue(obligation.state(projector.system().state()).attributeValue("endedAt").isUndefined());
                        var previousClock=withClock(cut,clocks.getLast(),live,2);var noClock=new RuntimeSnapshot("clock-omitted",cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),previousClock.startWatermarks(),previousClock.endWatermarks(),1,cut.facts(),cut.sourceCompleteness(),"actual-clock-omission");
                        projector.applySnapshot(noClock,live.run.id(),live.run.generation());assertTrue(obligation.state(projector.system().state()).attributeValue("deadlineAt").isUndefined(),"Missing current clock is not a receiver wall-clock fallback");
                        var ambiguous=new RuntimeFact(bid.id(),bid.kind(),Map.of("observationVersion","NPL_OBSERVATION/1.0","activationIdentityStatus","AMBIGUOUS_NORM_INSTANCE","candidateObservations",List.of(bid.values(),bid.values())),List.of(),ProjectionStatus.EVIDENCE_ONLY,Completeness.PARTIAL,List.of());
                        projector.applySnapshot(withClock(replaceNorms(cut,List.of(ambiguous)),clocks.getLast(),live,3),live.run.id(),live.run.generation());
                        assertEquals("true",projector.mutations().metadata(obligation,"auctionObligationIdentityAmbiguous"));
                        projector.applySnapshot(withClock(cut,clocks.getLast(),live,4),live.run.id(),live.run.generation());
                        assertEquals("ERROR",projector.mutations().metadata(obligation,"normLifecycleVerdict"));assertTrue(obligation.state(projector.system().state()).attributeValue("unfulfilled").isUndefined(),"An ambiguous source token must not resurrect a previously complete pending activation");
                    }
                    // A fresh consumer of a late source has no earlier native
                    // runtime. Do not reset an accepted source sequence in place.
                    live.npl.close();
                    try(var late=new NplBoardSnapshotSource(live.run.id(),()->List.copyOf(live.boards))) {
                        late.attach(e->{});var lateFacts=late.capture();assertTrue(lateFacts.stream().allMatch(f->"ACTIVATION_HISTORY_UNPROVEN".equals(f.values().get("history"))));
                        var lateBase=replaceNorms(cut,lateFacts);var lateMarks=new TreeMap<>(lateBase.endWatermarks());lateMarks.put(late.sourceId(),late.watermark());
                        var lateCut=new RuntimeSnapshot("late-native",cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),lateMarks,lateMarks,1,lateBase.facts(),cut.sourceCompleteness(),"actual-late-source");
                        var latePipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
                        try(var lateProjector=new NativeRuntimeProjector(latePipeline,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                            lateProjector.applySnapshot(withClock(lateCut,clocks.getLast(),live,1),live.run.id(),live.run.generation());
                            var obligation=lateProjector.system().state().allObjects().stream().filter(o->o.cls().name().equals("AuctionObligation")).findFirst().orElseThrow();
                            assertTrue(obligation.state(lateProjector.system().state()).attributeValue("unfulfilled").isUndefined(),"Late attachment cannot synthesize no prior violation");
                            assertEquals("NORM_ACTIVATION_HISTORY_UNPROVEN",lateProjector.mutations().metadata(obligation,"normLifecycleDiagnostic"));
                        }
                    }
                }finally{CslRuntimeTestRegistry.closeCartagoContext(participant);}
            }
        }finally{if(old==null)System.clearProperty(AuctionSchemeObservation.PROPERTY);else System.setProperty(AuctionSchemeObservation.PROPERTY,old);}
    }
    static RuntimeSnapshot replaceNorms(RuntimeSnapshot cut,List<RuntimeFact> norms) {
        var facts=new ArrayList<>(cut.facts().stream().filter(f->f.kind()!=RuntimeFactKind.NORM_INSTANCE).toList());facts.addAll(norms);
        return new RuntimeSnapshot(cut.snapshotId()+":partial",cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),1,facts,cut.sourceCompleteness(),cut.stateFingerprint()+":partial");
    }
    static RuntimeSnapshot withClock(RuntimeSnapshot cut,VerificationObservation clock,AuctionLiveFixture live,long sequence) {
        var event=clock.event(live.run.id(),live.run.generation(),live.source.modelRevision(),sequence,java.time.Instant.now(),"","");var facts=new ArrayList<>(cut.facts());facts.add(new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),List.of()));
        var marks=new TreeMap<>(cut.endWatermarks());marks.put(event.sourceId(),event.watermark());return new RuntimeSnapshot(cut.snapshotId()+":clock",cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),marks,marks,1,facts,cut.sourceCompleteness(),cut.stateFingerprint()+":source-clock");
    }
}
