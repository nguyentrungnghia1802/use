package org.jacamo.bridge.adapter;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.*;
import jacamo.checkpoint.VerifiedCheckpointCoordinator;
import jacamo.checkpoint.VerifiedCheckpointCoordinator.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol.Command;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol.Identity;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;

/** Official adapter: admitted source selectors, durable held-token history and wire values. */
final class VerifiedCheckpointBridge implements AutoCloseable {
    private record Cached(String hash,Map<String,Object> receipt){ }
    private final VerifiedCheckpointProtocol.Policy policy;
    private final VerifiedCheckpointCoordinator coordinator;
    private final DurableFrameLog history;
    private final Map<String,Cached> receipts=new LinkedHashMap<>();
    private volatile RuntimeSnapshot frozenSnapshot;
    private Request encodedRequest;private Map<String,Object> encodedWire;
    VerifiedCheckpointBridge(String session,String run,long generation,String revision,ObservationRequestPlan plan,
            Supplier<RuntimeSnapshot> capture,BooleanSupplier unchanged) throws Exception {
        Path file=Path.of(System.getProperty("jacamo.checkpoint.policy"));
        policy=VerifiedCheckpointProtocol.Policy.decode(CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(file))));
        if(!policy.programHash().equals(plan.programHash()))throw new IllegalArgumentException("CHECKPOINT_POLICY_PROGRAM_NOT_ADMITTED");
        for(var rule:policy.rules()) {
            var provider=rule.selectorKind().equals("TASK_POST")?"jason.plan-boundaries.v1":CartagoOperationHistory.PROVIDER;
            var selected=plan.requests().stream().filter(r->rule.sourceIdentity().equals("observation:"+r.id())&&r.provider().equals(provider)).toList();
            if(selected.size()!=1)throw new IllegalArgumentException("CHECKPOINT_SOURCE_SELECTION_NOT_ADMITTED");
            var parameters=selected.getFirst().parameters();
            if(rule.selectorKind().equals("TASK_POST")){
                var tasks=new HashSet<String>();
                for(Object raw:(List<?>)parameters.get("plans"))for(Object step:(List<?>)CanonicalJson.object(raw).get("steps")){
                    var site=CanonicalJson.object(step);if(rule.artifactClass().equals(site.get("artifactClass"))&&!Boolean.TRUE.equals(site.get("allowMissingArtifactAtEntry")))tasks.add((String)site.get("task"));
                }
                if(!tasks.containsAll(rule.operations()))throw new IllegalArgumentException("CHECKPOINT_TASK_SELECTION_NOT_ADMITTED");continue;
            }
            if(!rule.artifactClass().equals(parameters.get("class"))||!(parameters.get("operations") instanceof List<?> operations)||!new HashSet<>(operations).containsAll(rule.operations()))
                throw new IllegalArgumentException("CHECKPOINT_OPERATION_SELECTION_NOT_ADMITTED");
        }
        history=DurableFrameLog.create("verified-checkpoints-",CanonicalJson.DEFAULT_MAX_BYTES);
        coordinator=new VerifiedCheckpointCoordinator(new Owner(session,run,generation,revision),new VerifiedCheckpointCoordinator.Port(){
            @Override public SourceCut capture(ExecutionBarrier.Proof proof) {
                RuntimeSnapshot cut=capture.get();frozenSnapshot=cut;byte[] bytes=CanonicalJson.encode(ContractPayloads.runtime(cut));
                var watermarks=new TreeMap<String,Long>();cut.endWatermarks().forEach((id,mark)->watermarks.put(id,mark.sequence()));
                return new SourceCut(AdapterEvidence.digest(bytes),bytes,watermarks);
            }
            @Override public boolean unchanged(Request request){return unchanged.getAsBoolean();}
            @Override public void append(Map<String,Object> event){history.append(CanonicalJson.encode(event,CanonicalJson.DEFAULT_MAX_BYTES));}
        },Duration.ofMillis(Long.getLong("jacamo.checkpoint.verificationTimeoutMillis",30000L)));
        jacamo.checkpoint.CheckpointBoot.registerCoordinator(coordinator);
    }
    void observe(RuntimeEvent event) {
        if(event.kind()==RuntimeEventKind.GAP) {
            synchronized(this) { // Serialize the loss latch with authenticated decisions.
            coordinator.trigger(new Boundary(policy.hash(),"source-evidence-unavailable",event.eventId(),Set.of("mas:"+coordinator.owner().run()),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),policy.inputHashes()));
            // A broken observation prefix is observer loss, even if a later
            // snapshot restores current fields. Preserve the exact GAP payload
            // and latch the hold independently of a subsequent native verdict.
            coordinator.disconnected();
            history.append(CanonicalJson.encode(Map.of("kind","SOURCE_EVIDENCE_GAP","run",coordinator.owner().run(),"event",ContractPayloads.event(event))));
            }
            return;
        }
        if(VerificationObservation.present(event)){
            beforeObservation(VerificationObservation.require(event));
        }
        if(event.subsystem().equals("cartago")&&event.entityId()!=null&&(event.kind()==RuntimeEventKind.SUCCEEDED||event.kind()==RuntimeEventKind.FAILED)) {
            for(var rule:policy.rules())if(!rule.selectorKind().equals("TASK_POST")&&(!rule.selectorKind().equals("OPERATION_FAILURE")||event.kind()==RuntimeEventKind.FAILED)&&rule.artifactClass().equals(event.after().get("artifactTypeJavaClassName"))&&rule.operations().contains(event.after().get("operation"))) {
                coordinator.trigger(new Boundary(policy.hash(),rule.id(),event.eventId(),Set.of("source:"+event.entityId().canonical()),rule.requiredChecks(),policy.inputHashes()));
                break;
            }
        }
    }
    /** Source-only, nonblocking selection. No capture, network or verifier wait. */
    synchronized void beforeObservation(VerificationObservation observation){
        if(observation.kind()!=VerificationObservation.Kind.JASON_STEP||"ENABLED".equals(observation.data().get("phase")))return;
        String occurrence="observation:"+observation.subject()+":"+observation.data().get("phase");
        var active=coordinator.view().request();
        if(active!=null&&(active.boundary().sourceOccurrence().equals(occurrence)||active.drainedBoundaries().stream().anyMatch(b->b.sourceOccurrence().equals(occurrence))))return;
        for(var rule:policy.rules())if(rule.selectorKind().equals("TASK_POST")&&rule.operations().contains(observation.data().get("sourceStepId"))){
            String artifact=Objects.toString(observation.data().get("artifactIdentity"),"");
            coordinator.trigger(new Boundary(policy.hash(),rule.id(),occurrence,Set.of(artifact.isBlank()?"mas:"+coordinator.owner().run():"source:"+artifact),rule.requiredChecks(),policy.inputHashes()));break;
        }
    }
    RuntimeSnapshot frozenSnapshot(){var view=coordinator.view();return view.request()!=null&&view.request().cut()!=null?frozenSnapshot:null;}
    boolean active(){return coordinator.view().request()!=null;}
    synchronized void disconnected(){
        if(coordinator.view().state()==State.STOPPED)return;
        if(!active())coordinator.trigger(new Boundary(policy.hash(),"observer-disconnected","observer-loss:"+UUID.randomUUID(),Set.of("mas:"+coordinator.owner().run()),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),policy.inputHashes()));
        coordinator.disconnected();
    }
    synchronized Map<String,Object> command(Command command) {
        Map<String,Object> receipt=null;
        if(command.action()==VerifiedCheckpointProtocol.Action.MONITOR_CLOSED)disconnected();
        else if(command.action()!=VerifiedCheckpointProtocol.Action.STATUS) {
            String hash=VerifiedCheckpointProtocol.hash(command.payload());var prior=receipts.get(command.commandId());
            if(prior!=null) {
                if(!prior.hash().equals(hash))throw new IllegalArgumentException("CHECKPOINT_COMMAND_ID_COLLISION");receipt=prior.receipt();
            } else {
                var view=coordinator.view();Map<String,Object> request=wireRequest(view.request());
                if(request==null||!Identity.decode(CanonicalJson.object(request.get("identity"))).equals(command.identity()))throw new IllegalArgumentException("CHECKPOINT_WIRE_IDENTITY_STALE_OR_FOREIGN");
                var identity=command.identity();var checks=new TreeMap<String,VerifiedCheckpointCoordinator.Check>();
                command.checks().forEach((id,c)->checks.put(id,new VerifiedCheckpointCoordinator.Check(Truth.valueOf(c.truth()),Applicability.valueOf(c.applicability()),c.diagnostic())));
                var decided=coordinator.decide(new Decision(new Owner(identity.session(),identity.run(),identity.generation(),identity.revision()),identity.token(),identity.sequence(),identity.barrierEpoch(),identity.policyHash(),identity.producerHash(),command.acceptedUseCutHash(),command.resultHash(),identity.executionScope(),command.commandId(),Action.valueOf(command.action().name()),checks,command.diagnostic()));
                receipt=Map.of("commandId",decided.commandId(),"token",decided.token(),"released",decided.released(),"state",decided.state().name(),"resultHash",decided.originalResultHash(),"acceptedUseCutHash",decided.acceptedUseCutHash(),"diagnostic",decided.diagnostic(),"barrierEpoch",decided.barrierEpoch());
                receipts.put(command.commandId(),new Cached(hash,receipt));
            }
        }
        var view=coordinator.view();var rows=new TreeMap<String,Object>();view.checks().forEach((id,c)->rows.put(id,Map.of("truth",c.truth().name(),"applicability",c.applicability().name(),"diagnostic",c.diagnostic())));
        var response=new LinkedHashMap<String,Object>();response.put("version",VerifiedCheckpointProtocol.VERSION);response.put("state",view.state().name());response.put("request",wireRequest(view.request()));response.put("resultHash",view.resultHash());response.put("acceptedUseCutHash",view.acceptedUseCutHash());response.put("checks",rows);response.put("diagnostic",view.reason());response.put("ledgerSequence",view.ledgerSequence());response.put("receipt",receipt);
        return Collections.unmodifiableMap(response);
    }
    private Map<String,Object> wireRequest(Request request) {
        if(request==null)return null;
        if(request==encodedRequest)return encodedWire;
        var owner=request.owner();var cut=request.cut();var proof=request.frozen();
        var identity=new Identity(owner.session(),owner.run(),owner.generation(),owner.revision(),request.token(),request.sequence(),request.barrierEpoch(),request.boundary().policyHash(),cut==null?"":cut.hash(),"",request.executionScope());
        var payload=new LinkedHashMap<String,Object>();payload.put("identity",identity.payload());payload.put("checkpointId",request.boundary().checkpointId());
        var occurrences=new ArrayList<String>();occurrences.add(request.boundary().sourceOccurrence());request.drainedBoundaries().forEach(b->occurrences.add(b.sourceOccurrence()));
        payload.put("sourceOccurrences",occurrences);payload.put("requiredChecks",request.requiredChecks().stream().sorted().toList());payload.put("inputHashes",request.boundary().inputHashes());payload.put("watermarks",cut==null?Map.of():cut.watermarks());
        var barrier=new LinkedHashMap<String,Object>();
        if(proof!=null) {
            var active=new TreeMap<String,Object>();proof.active().forEach((id,lane)->active.put(Long.toString(id),lane));
            var agents=new TreeMap<String,Object>();var runner=jason.infra.local.BaseLocalMAS.getRunner();
            if(runner==null)throw new IllegalStateException("CHECKPOINT_AGENT_COHORT_UNAVAILABLE");
            for(var local:runner.getAgs().values()) {
                var id=BridgeRuntimeRegistry.agentIdentity(local.getAgName()).orElseThrow(()->new IllegalStateException("CHECKPOINT_AGENT_INCARNATION_UNAVAILABLE"));
                if(local.getClass()!=jason.infra.local.LocalAgArch.class||local.getThread()!=null&&proof.active().containsKey(local.getThread().threadId()))throw new IllegalStateException("CHECKPOINT_AGENT_COHORT_NOT_QUIESCENT");
                agents.put(id.canonical(),Map.of("thread",local.getThread()==null?"NOT_STARTED":Long.toString(local.getThread().threadId()),"receipt","KERNEL_ADMISSION_CLOSED_WITH_NO_ACTIVE_STEP"));
            }
            barrier.putAll(Map.of("run",proof.run(),"epoch",proof.epoch(),"requestedSequence",request.requestedSequence(),"frozenSequence",proof.sequence(),"sourceMillis",proof.sourceMillis(),"admissionClosed",proof.admissionClosed(),"clockFrozen",proof.clockFrozen(),"active",active,"pending",proof.pending(),"cohortReceipts",proof.completed()));barrier.put("agentCohort",agents);
        }
        payload.put("barrier",barrier);
        payload.put("snapshot",cut==null?null:CanonicalJson.object(CanonicalJson.decode(cut.snapshot())));payload.put("ready",cut!=null&&proof!=null&&proof.clockFrozen());
        var signed=new LinkedHashMap<>(identity.payload());signed.put("requestHash",VerifiedCheckpointProtocol.hash(payload));payload.put("identity",signed);
        var value=Collections.unmodifiableMap(payload);
        if(Boolean.TRUE.equals(value.get("ready")))history.append(CanonicalJson.encode(Map.of("kind","WIRE_REQUEST","request",value)));
        encodedRequest=request;encodedWire=value;return encodedWire;
    }
    @Override public synchronized void close(){coordinator.close();history.close();}
}
