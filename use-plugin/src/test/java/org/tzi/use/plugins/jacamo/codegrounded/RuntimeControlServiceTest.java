package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.jacamo.bridge.contract.RuntimeControlContract;
import org.jacamo.bridge.contract.RuntimeControlContract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class RuntimeControlServiceTest {
    private static final Set<String> AGENTS=Set.of("agent#one","agent#two");
    private static final class Port implements RuntimeControlService.Port {
        final org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector projector;
        final AtomicInteger pauses=new AtomicInteger(),resumes=new AtomicInteger(),resyncs=new AtomicInteger();
        volatile boolean ack=true,owned=true,connected=true,restore=true,failResync=false,failControl=false;
        volatile State state=State.RUNNING;volatile String requestId="";
        Port(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector p){projector=p;}
        @Override public Status control(Request r){
            assertNotNull(projector.coordinator().snapshots().failure(),"Failing cut must exist before ANY control request");
            if(!connected)throw new IllegalStateException("disconnect");if(failControl)throw new IllegalStateException("API throws");
            if(r.action()==Action.PAUSE){pauses.incrementAndGet();state=State.PAUSE_REQUESTED;requestId=r.requestId();}
            if(r.action()==Action.RESUME){resumes.incrementAndGet();state=State.RESUME_REQUESTED;requestId=r.requestId();}
            if(ack && state==State.PAUSE_REQUESTED)state=State.PAUSED;
            if(ack && state==State.RESUME_REQUESTED)state=State.RUNNING;
            return new Status(RuntimeControlContract.VERSION,SESSION,1,REVISION,true,state,requestId,AGENTS,
                    ack?AGENTS:Set.of("agent#one"),Set.of(),"",Instant.now());
        }
        @Override public VerificationSnapshot authoritativeResync(){resyncs.incrementAndGet();if(failResync)throw new IllegalStateException("snapshot unavailable");
            if(restore)projector.applySnapshot(snapshot("authoritative-confirmation",projector.sourceWatermarks().get("cartago")));
            else projector.coordinator().manualVerify();
            return projector.coordinator().verificationSnapshot();}
        @Override public boolean ownsRuntime(String s,long g,String r){return owned;}
        @Override public boolean liveAvailable(){return connected;}
    }
    private RuntimeControlService service(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector p,Port port,long deadline){
        return new RuntimeControlService(p.coordinator(),port,List::of,SESSION,1,REVISION,true,deadline);
    }
    private String setup(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector p,RuntimeControlService service){
        String owner=p.mutations().objectForSemanticId(ARTIFACT).cls().name();
        p.coordinator().loadProfileSource("hard.ocl","context "+owner+" inv State: self.status = 'A'");
        String id="EXTERNAL:"+owner+"::State";
        service.approve(id,new RuntimeConstraintPolicy(ConstraintOrigin.CASE,RuntimeConstraintPolicy.Severity.HARD,RuntimeConstraintPolicy.Enforcement.PAUSE_ON_FAIL,
                Set.of(CheckpointType.SNAPSHOT,CheckpointType.AFTER_MUTATION,CheckpointType.STREAM_BOUNDARY),Set.of(),true,"explicit executable fixture policy"));return id;
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!condition.getAsBoolean() && System.nanoTime()<until)Thread.sleep(5);assertTrue(condition.getAsBoolean(),"Control condition timeout");}
    @Test void diagnosticEvidenceCannotReplaceTheCompiledFalseContext() throws Exception {
        try(var p=projector()) {
            p.coordinator().loadProfileSource("context.ocl","context Workspace inv Context: self.artifacts->notEmpty() implies false");
            p.coordinator().configurePolicy("EXTERNAL:Workspace::Context",new RuntimeConstraintPolicy(ConstraintOrigin.CASE,
                    RuntimeConstraintPolicy.Severity.SOFT,RuntimeConstraintPolicy.Enforcement.REPORT_ONLY,
                    Set.of(CheckpointType.SNAPSHOT),Set.of(),false,"Explicit context diagnostic fixture",
                    Map.of(ARTIFACT,"Exact additional artifact evidence, not the Workspace invariant context")));
            var port=new Port(p);
            try(var service=service(p,port,1000)) {
                p.coordinator().manualVerify();
                var violation=service.violations().stream().filter(v->v.constraintId().equals("EXTERNAL:Workspace::Context")).findFirst().orElseThrow();
                assertEquals(p.mutations().objectForSemanticId(WORKSPACE).name(),violation.contextObject());
                assertTrue(violation.involvedObjects().containsKey(p.mutations().objectForSemanticId(ARTIFACT).name()));
                assertEquals(0,port.pauses.get());
            }
        }
    }
    @Test void distinctSimultaneousHardViolationsHaveOnePauseAndAnExplicitConfirmationPolicy() throws Exception {
        for(var policy:RuntimeControlService.DistinctViolationPolicy.values())try(var p=projector()) {
            var port=new Port(p);port.ack=false;port.restore=false;
            try(var service=new RuntimeControlService(p.coordinator(),port,List::of,SESSION,1,REVISION,true,3000,policy)) {
                String owner=p.mutations().objectForSemanticId(ARTIFACT).cls().name();
                p.coordinator().loadProfileSource("two.ocl","context "+owner+" inv First: self.status = 'A'\ncontext "+owner+" inv Second: self.status <> 'B'");
                for(String name:List.of("First","Second"))service.approve("EXTERNAL:"+owner+"::"+name,new RuntimeConstraintPolicy(ConstraintOrigin.CASE,
                        RuntimeConstraintPolicy.Severity.HARD,RuntimeConstraintPolicy.Enforcement.PAUSE_ON_FAIL,
                        Set.of(CheckpointType.SNAPSHOT,CheckpointType.AFTER_MUTATION),Set.of(),true,"Explicit distinct-HARD test approval"));
                p.apply(delta("distinct-fail",1,"B"));await(()->port.pauses.get()==1);
                assertEquals(2,service.violations().size());String original=p.coordinator().snapshots().failure().snapshotId();
                for(int i=0;i<12;i++)p.coordinator().manualVerify();assertEquals(1,port.pauses.get());
                port.ack=true;
                await(()->service.violations().stream().noneMatch(v->v.confirmation()==VerificationViolation.Confirmation.PENDING));
                long confirmed=service.violations().stream().filter(v->v.confirmation()==VerificationViolation.Confirmation.CONFIRMED).count();
                assertEquals(policy==RuntimeControlService.DistinctViolationPolicy.COALESCE_CONFIRM?2:1,confirmed);
                assertEquals(1,port.resyncs.get());assertEquals(original,p.coordinator().snapshots().failure().snapshotId());
                assertTrue(service.violations().stream().allMatch(v->v.failingSnapshotId().equals(original)));
                if(policy==RuntimeControlService.DistinctViolationPolicy.REPORT_DISTINCT)assertTrue(service.violations().stream()
                        .anyMatch(v->v.confirmation()==VerificationViolation.Confirmation.REPORT_ONLY && v.diagnostic().contains("DISTINCT_HARD")));
            }
        }
    }
    @Test void reportOnlyFailureAndItsPreviousCutAreInspectableAcrossLiveTailEviction() throws Exception {
        try(var p=projector()) {var port=new Port(p);
            try(var service=service(p,port,1000)) {
                var previous=p.coordinator().verificationSnapshot();
                String owner=p.mutations().objectForSemanticId(ARTIFACT).cls().name();
                p.coordinator().loadProfileSource("report.ocl","context "+owner+" inv Report: false");
                var failed=p.coordinator().snapshots().failure();assertNotNull(failed);
                assertEquals(previous.snapshotId(),p.coordinator().snapshots().previousFailure().snapshotId());
                for(int i=1;i<=20;i++)p.apply(delta("live-"+i,i,"value-"+i));
                assertSame(failed,p.coordinator().snapshots().failure());assertEquals(0,port.pauses.get());
                assertTrue(service.violations().stream().allMatch(v->v.confirmation()==VerificationViolation.Confirmation.REPORT_ONLY));
            }
        }
    }
    @Test void failingCutPrecedesAllAckConfirmationAndResumeResyncAndStormIsDeduplicated() throws Exception {
        try(var p=projector()){var port=new Port(p);port.ack=false;
            try(var service=service(p,port,3000)) {
                setup(p,service);p.apply(delta("hard-failure",1,"B"));
                await(()->port.pauses.get()==1);var failed=p.coordinator().snapshots().failure();assertNotNull(failed);
                assertEquals(State.PAUSE_REQUESTED,service.state().state());assertEquals(0,port.resyncs.get());
                for(int i=0;i<25;i++)p.coordinator().manualVerify();assertEquals(1,port.pauses.get());
                port.ack=true;await(()->!service.violations().isEmpty() && service.violations().getFirst().confirmation()==VerificationViolation.Confirmation.TRANSIENT_NOT_REPRODUCED);
                assertEquals(State.PAUSED,service.state().state());assertFalse(service.state().liveReady());
                var confirmation=p.coordinator().snapshots().confirmation();assertNotEquals(failed.snapshotId(),confirmation.snapshotId());
                assertEquals(VerificationSnapshot.SynchronizationState.LIVE,confirmation.metadata().lifecycle());
                assertEquals("PAUSED",confirmation.metadata().evidence().get("controlState"));
                assertEquals("'B'",failed.image().objects().get(p.mutations().objectForSemanticId(ARTIFACT).name()).attributes().get("status"));
                assertEquals(VerificationOutcome.PASS,confirmation.result().outcomes().stream().filter(o->o.constraintId().startsWith("EXTERNAL:")).findFirst().orElseThrow().outcome());
                var first=service.requestResume();assertSame(first,service.requestResume());first.get(5,TimeUnit.SECONDS);
                assertEquals(1,port.resumes.get());assertEquals(2,port.resyncs.get());assertEquals(State.RUNNING,service.state().state());assertTrue(service.state().liveReady());
                assertEquals(1,service.violations().size());assertSame(failed,p.coordinator().snapshots().failure());
            }
        }
    }
    @Test void unchangedFalseIsConfirmedAndDisconnectedPausedDoesNotResumeProducer() throws Exception {
        try(var p=projector()){var port=new Port(p);port.restore=false;
            try(var service=service(p,port,3000)) {
                setup(p,service);p.apply(delta("false",1,"B"));await(()->service.violations().stream().anyMatch(v->v.confirmation()==VerificationViolation.Confirmation.CONFIRMED));
                port.connected=false;service.disconnected();assertEquals(State.PAUSED,service.state().state());assertFalse(service.state().connected());assertEquals(0,port.resumes.get());
                assertThrows(IllegalStateException.class,service::requestResume);
                port.connected=true;service.refresh().get(5,TimeUnit.SECONDS);assertEquals(State.PAUSED,service.state().state());
                service.requestResume().get(5,TimeUnit.SECONDS);assertEquals(1,port.resumes.get());
            }
        }
    }
    @Test void stalePausedObservationCannotResumeUntilAuthoritativeRecovery() throws Exception {
        try(var p=projector()) {var port=new Port(p);port.restore=false;
            try(var service=service(p,port,3000)) {
                setup(p,service);p.apply(delta("failure",1,"B"));
                await(()->service.violations().stream().anyMatch(v->v.confirmation()==VerificationViolation.Confirmation.CONFIRMED));
                p.coordinator().coverageGap("STALE_OBSERVATION");
                assertThrows(IllegalStateException.class,service::requestResume);assertEquals(0,port.resumes.get());
                p.applySnapshot(snapshot("explicit-recovery",1));service.requestResume().get(5,TimeUnit.SECONDS);
                assertEquals(1,port.resumes.get());assertEquals(State.RUNNING,service.state().state());
            }
        }
    }
    @Test void partialAckTimeoutDoesNotBecomePausedAndSyncFailureIsConfirmationError() throws Exception {
        for(boolean partial:List.of(true,false))try(var p=projector()){
            var port=new Port(p);port.ack=!partial;port.failResync=!partial;
            try(var service=service(p,port,200)) {
                setup(p,service);p.apply(delta("fail",1,"B"));
                await(()->service.violations().stream().anyMatch(v->v.confirmation()==VerificationViolation.Confirmation.CONFIRMATION_ERROR));
                assertEquals(partial?State.PAUSE_REQUESTED:State.PAUSED,service.state().state());assertFalse(service.state().liveReady());
                assertEquals(partial?0:1,port.resyncs.get());assertEquals(0,port.resumes.get());
                p.coordinator().manualVerify();assertEquals(1,port.pauses.get());
            }
        }
    }
    @Test void domainEvaluationErrorIsNotEligibleFailAndReplacedOwnerCannotReceiveCommands() throws Exception {
        try(var p=projector()){var port=new Port(p);
            try(var service=service(p,port,1000)) {
                String id=setup(p,service);String owner=p.mutations().objectForSemanticId(ARTIFACT).cls().name();
                p.coordinator().loadProfileSource("hard.ocl","context "+owner+" inv State: oclUndefined(Boolean)");
                service.approve(id,new RuntimeConstraintPolicy(ConstraintOrigin.CASE,RuntimeConstraintPolicy.Severity.HARD,
                        RuntimeConstraintPolicy.Enforcement.PAUSE_ON_FAIL,Set.of(CheckpointType.SNAPSHOT),Set.of(),true,"explicit undefined test"));
                p.coordinator().manualVerify();
                assertTrue(p.coordinator().latest().outcomes().stream().anyMatch(o->o.constraintId().equals(id) && o.outcome()==VerificationOutcome.ERROR));
                assertTrue(service.violations().isEmpty());assertEquals(0,port.pauses.get());
                port.owned=false;assertThrows(IllegalStateException.class,()->service.requestPause("old owner"));assertEquals(0,port.pauses.get());
            }
        }
    }
    @Test void unavailableControlReportsErrorWithoutPauseAndThrowingPauseCannotBecomePaused() throws Exception {
        for(boolean capable:List.of(false,true))try(var p=projector()) {
            var port=new Port(p);port.failControl=capable;
            try(var service=new RuntimeControlService(p.coordinator(),port,List::of,SESSION,1,REVISION,capable,300)) {
                setup(p,service);p.apply(delta("control-failure",1,"B"));
                await(()->service.violations().stream().anyMatch(v->v.confirmation()==VerificationViolation.Confirmation.CONFIRMATION_ERROR));
                assertNotEquals(State.PAUSED,service.state().state());assertEquals(0,port.resumes.get());assertEquals(0,port.resyncs.get());
                if(!capable)assertThrows(IllegalStateException.class,()->service.requestPause("unsupported"));
            }
        }
    }
    @Test void disconnectAndRuntimeReplacementDuringAckFailClosedAndResumeErrorKeepsOriginalConfirmation() throws Exception {
        for(boolean disconnect:List.of(true,false))try(var p=projector()) {
            var port=new Port(p);port.ack=false;
            try(var service=service(p,port,1000)) {
                setup(p,service);p.apply(delta("pending",1,"B"));await(()->port.pauses.get()==1);
                if(disconnect){port.connected=false;service.disconnected();}else port.owned=false;
                await(()->service.violations().getFirst().confirmation()==VerificationViolation.Confirmation.CONFIRMATION_ERROR);
                assertEquals(State.PAUSE_REQUESTED,service.state().state());assertEquals(0,port.resumes.get());assertEquals(0,port.resyncs.get());
            }
        }
        try(var p=projector()) {var port=new Port(p);port.restore=false;
            try(var service=service(p,port,1000)) {
                setup(p,service);p.apply(delta("confirmed",1,"B"));await(()->service.violations().getFirst().confirmation()==VerificationViolation.Confirmation.CONFIRMED);
                port.failControl=true;assertThrows(ExecutionException.class,()->service.requestResume().get(5,TimeUnit.SECONDS));
                assertEquals(State.RESUME_REQUESTED,service.state().state());assertFalse(service.state().liveReady());
                assertEquals(VerificationViolation.Confirmation.CONFIRMED,service.violations().getFirst().confirmation());
                assertNotNull(p.coordinator().snapshots().failure());
            }
        }
    }
}
