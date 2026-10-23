package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import org.tzi.use.plugins.jacamo.codegrounded.trace.NativeObjectBindings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.trace.TracePhase;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeObservationCut;
import org.tzi.use.plugins.jacamo.semantics.observer.SourceClock;
import org.tzi.use.plugins.jacamo.semantics.observer.SourceClockCut;

/** Exact typed identities only. All runtime state and verification pass through the single writer. */
public final class NativeRuntimeProjector implements AutoCloseable {
    public record RuntimeAlias(String runtimeIdentity, String targetSemanticId, String targetUseId,
            String sessionId, long generation, String modelRevision) { }
    public record ProjectionResult(int materialized, List<String> evidenceOnly, List<String> unavailable, List<String> rejected) {
        public ProjectionResult { evidenceOnly = List.copyOf(evidenceOnly); unavailable = List.copyOf(unavailable); rejected = List.copyOf(rejected); }
    }
    private final NativeRuntimeMutationEngine mutations;
    private final RuntimeVerificationCoordinator coordinator;
    private final java.util.concurrent.atomic.AtomicBoolean deliveryRevoked=new java.util.concurrent.atomic.AtomicBoolean();
    public void revokeDelivery(){deliveryRevoked.set(true);}
    private String sessionId, modelRevision, snapshotId = "";
    private long generation;
    private boolean resyncRequired;
    private EventLedger ledger = new EventLedger(8192);
    private final Map<String, Long> sourceWatermarks = new LinkedHashMap<>();
    private final Map<String, RuntimeFact> evidence = new LinkedHashMap<>();
    private final Map<String, RuntimeAlias> aliases = new LinkedHashMap<>();
    private final List<NativeRuntimeTraceRecord> trace = new ArrayList<>();
    private final List<RuntimeEvent> auctionDiscovery = new ArrayList<>();
    // Epoch proof only, never a current observation or a second runtime state.
    // A PARTIAL resync may omit CLOCK; it must not forget the frozen anchor or
    // a latched regression and later admit a fresh anchor under the same run.
    private final Map<String,RuntimeFact> auctionClockProofs=new LinkedHashMap<>();
    private record ProtocolState(String session, long generation, String snapshotId, EventLedger ledger,
            Map<String, Long> watermarks, Map<String, RuntimeFact> evidence,
            Map<String, RuntimeAlias> aliases, List<NativeRuntimeTraceRecord> trace,List<RuntimeEvent> auctionDiscovery,Map<String,RuntimeFact> auctionClockProofs) { }

    private ProtocolState saveProtocol() {
        return new ProtocolState(sessionId, generation, snapshotId, ledger, Map.copyOf(sourceWatermarks),
                Map.copyOf(evidence), Map.copyOf(aliases), List.copyOf(trace),List.copyOf(auctionDiscovery),Map.copyOf(auctionClockProofs));
    }
    private void restoreProtocol(ProtocolState state) {
        sessionId = state.session(); generation = state.generation(); snapshotId = state.snapshotId(); ledger = state.ledger();
        sourceWatermarks.clear(); sourceWatermarks.putAll(state.watermarks());
        evidence.clear(); evidence.putAll(state.evidence()); aliases.clear(); aliases.putAll(state.aliases());
        trace.clear(); trace.addAll(state.trace()); resyncRequired = true;
        auctionDiscovery.clear();auctionDiscovery.addAll(state.auctionDiscovery());
        auctionClockProofs.clear();auctionClockProofs.putAll(state.auctionClockProofs());
    }

    public NativeRuntimeProjector(CodeGroundedNativePipeline.Result pipeline, String session, long generation, String revision) {
        this(pipeline, session, generation, revision, new CodeGroundedRuntimeRuleRegistry());
    }
    public NativeRuntimeProjector(CodeGroundedNativePipeline.Result pipeline, String session, long generation,
            String revision, CodeGroundedRuntimeRuleRegistry registry) {
        this(pipeline, session, generation, revision, registry, null);
    }
    public NativeRuntimeProjector(CodeGroundedNativePipeline.Result pipeline, String session, long generation,
            String revision, Path runtimeDirectory) {
        this(pipeline, session, generation, revision, new CodeGroundedRuntimeRuleRegistry(), runtimeDirectory);
    }
    public NativeRuntimeProjector(CodeGroundedNativePipeline.Result pipeline, String session, long generation,
            String revision, CodeGroundedRuntimeRuleRegistry registry, Path runtimeDirectory) {
        this(pipeline.state().system(), pipeline.state().semanticObjectIndex(), session, generation, revision, registry,
                runtimeDirectory);
    }
    public NativeRuntimeProjector(MSystem system, NativeObjectBindings index, String session, long generation,
            String revision, CodeGroundedRuntimeRuleRegistry registry) {
        this(system, index, session, generation, revision, registry, null);
    }
    public NativeRuntimeProjector(MSystem system, NativeObjectBindings index, String session, long generation,
            String revision, CodeGroundedRuntimeRuleRegistry registry, Path runtimeDirectory) {
        this(system,index,session,generation,revision,registry,runtimeDirectory,null);
    }
    public NativeRuntimeProjector(CodeGroundedNativePipeline.CanonicalBuildResult pipeline,String session,long generation,String revision) {
        this(pipeline.nativeResult().state().system(),pipeline.nativeResult().state().semanticObjectIndex(),session,generation,revision,new CodeGroundedRuntimeRuleRegistry(),null,pipeline.program());
    }
    public NativeRuntimeProjector(CodeGroundedNativePipeline.CanonicalBuildResult pipeline,String session,long generation,String revision,Path directory) {
        this(pipeline.nativeResult().state().system(),pipeline.nativeResult().state().semanticObjectIndex(),session,generation,revision,new CodeGroundedRuntimeRuleRegistry(),directory,pipeline.program());
    }
    NativeRuntimeProjector(CodeGroundedNativePipeline.CanonicalBuildResult pipeline,String session,long generation,String revision,Path directory,long journalBytes) {
        this(pipeline.nativeResult().state().system(),pipeline.nativeResult().state().semanticObjectIndex(),session,generation,revision,new CodeGroundedRuntimeRuleRegistry(),directory,pipeline.program(),journalBytes);
    }
    private NativeRuntimeProjector(MSystem system,NativeObjectBindings index,String session,long generation,String revision,
            CodeGroundedRuntimeRuleRegistry registry,Path runtimeDirectory,org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram program) {
        this(system,index,session,generation,revision,registry,runtimeDirectory,program,RuntimeVerificationCoordinator.defaultJournalMaxBytes());
    }
    private NativeRuntimeProjector(MSystem system,NativeObjectBindings index,String session,long generation,String revision,
            CodeGroundedRuntimeRuleRegistry registry,Path runtimeDirectory,org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram program,long journalBytes) {
        if (session == null || session.isBlank() || revision == null || revision.isBlank() || generation < 0)
            throw new IllegalArgumentException("NATIVE_RUNTIME_IDENTITY_REQUIRED");
        this.sessionId = session; this.generation = generation; this.modelRevision = revision;
        mutations = new NativeRuntimeMutationEngine(system, index, registry,program);
        coordinator = new RuntimeVerificationCoordinator(mutations, session, generation, revision,
                runtimeDirectory==null?RuntimeVerificationCoordinator.createEphemeralDirectory(RuntimeVerificationCoordinator.defaultRuntimeRoot()):runtimeDirectory,
                512, journalBytes, true);
    }
    @Override public void close() { coordinator.close(); }
    public MSystem system() { return mutations.system(); }
    public NativeRuntimeMutationEngine mutations() { return mutations; }
    public RuntimeVerificationCoordinator coordinator() { return coordinator; }
    public String snapshotId() { return coordinator.read(() -> snapshotId); }
    public boolean resyncRequired() { return coordinator.read(() -> resyncRequired); }
    public Map<String, Long> sourceWatermarks() { return coordinator.read(() -> Map.copyOf(sourceWatermarks)); }
    public Map<String, RuntimeFact> evidence() { return coordinator.read(() -> Map.copyOf(evidence)); }
    public Map<String, RuntimeAlias> runtimeAliases() { return coordinator.read(() -> Map.copyOf(aliases)); }
    public List<NativeRuntimeTraceRecord> trace() { return coordinator.read(() -> List.copyOf(trace)); }
    /** Current native targets; separate from the bounded protocol event history. */
    public List<NativeRuntimeMutationEngine.TargetBinding> projectionBindings() {
        return coordinator.read(mutations::targetBindings);
    }
    public NativeRuntimeMutationEngine.OclGate lastOclGate() {
        var latest = coordinator.latest();
        boolean passed = latest != null && !latest.outcomes().isEmpty() && latest.outcomes().stream().allMatch(item ->
                item.outcome() == VerificationOutcome.PASS);
        return new NativeRuntimeMutationEngine.OclGate(mutations.structureValid(), passed, latest == null ? "" : latest.diagnostic());
    }
    public ProjectionResult applySnapshot(RuntimeSnapshot snapshot) { return coordinator.read(()->applySnapshot(snapshot, sessionId, generation)); }
    public ProjectionResult applySnapshot(RuntimeSnapshot snapshot, String session, long nextGeneration) {
        return coordinator.read(() -> {
        if(deliveryRevoked.get())throw new IllegalStateException("NATIVE_RUNTIME_OWNER_REVOKED");
        ProtocolState before = saveProtocol();
        long acceptedBefore=coordinator.acceptedBoundaries();
        try {
        return coordinator.transaction("SNAPSHOT", snapshot.snapshotId(), "snapshot", 0, snapshot.captureEndedAt(),
                snapshotPayload(snapshot,session,nextGeneration), true, () -> {
            requireRevision(snapshot.modelRevision());
            if (session == null || session.isBlank() || nextGeneration < 0 || sessionId.equals(session) && nextGeneration < generation)
                throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_SNAPSHOT_IDENTITY_STALE");
            boolean sameEpoch=sessionId.equals(session) && generation==nextGeneration;
            if(!sameEpoch){auctionDiscovery.clear();auctionClockProofs.clear();}
            boolean repeatedCut=mutations.canonicalMappingConfigured()&&!snapshotId.isBlank()&&sameEpoch
                    &&sourceWatermarks.equals(snapshot.endWatermarks().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().sequence())));
            try { SnapshotAdmission.require(snapshot,sameEpoch?sourceWatermarks:Map.of()); }
            catch(ContractException invalid) {throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_SNAPSHOT_CUT_REJECTED:"+invalid.getMessage(),invalid);}
            if(snapshot.facts().stream().map(f->f.id().canonical()).distinct().count()!=snapshot.facts().size())throw new NativeRuntimeProtocolException("SNAPSHOT_FACT_ID_COLLISION");
            if(sameEpoch&&mutations.auctionCase()&&!mutations.xmlCase())org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionObservationCut.validateClockContinuation(auctionClockProofs,snapshot,session,nextGeneration);
            if(sameEpoch&&mutations.xmlCase())SourceClockCut.validateContinuation(auctionClockProofs,snapshot,session,nextGeneration);
            if(!(mutations.cslProfile()||mutations.canonicalMappingConfigured()) || !sessionId.equals(session) || generation!=nextGeneration)mutations.resetToBaseline();
            var baseline=mutations.savepoint();
            mutations.prepareSnapshot(snapshot);
            for(var fact:snapshot.facts())if(fact.values().containsKey(org.jacamo.bridge.contract.VerificationObservation.KEY))
                try {var observation=org.jacamo.bridge.contract.VerificationObservation.require(fact,snapshot,session,nextGeneration);mutations.validateVerificationObservation(observation,session,nextGeneration,snapshot.modelRevision());}
                catch(org.jacamo.bridge.contract.ContractException invalid){throw new NativeRuntimeProtocolException(invalid.getMessage(),invalid);}
            List<String> materialized = new ArrayList<>(), evidenceOnly = new ArrayList<>(), unavailable = new ArrayList<>();
            List<Map.Entry<RuntimeFact, NativeRuntimeMutationEngine.ApplyResult>> applied = new ArrayList<>();
            for (RuntimeFact fact : snapshot.facts().stream().sorted(Comparator.comparingInt(NativeRuntimeProjector::rank)
                    .thenComparing(value -> value.id().canonical())).toList()) {
                if (fact.projectionStatus() == ProjectionStatus.UNAVAILABLE || fact.completeness() == Completeness.UNAVAILABLE) {
                    unavailable.add(fact.id().canonical()); continue;
                }
                var result = mutations.apply(fact.id(), binding(fact), fact.kind(), fact.projectionStatus(),
                        fact.completeness(), fact.values(), false);
                if (result.status() == NativeRuntimeMutationEngine.Status.REJECTED)
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_SNAPSHOT_MUTATION_REJECTED:" + result.diagnostic());
                applied.add(Map.entry(fact, result));
                (result.status() == NativeRuntimeMutationEngine.Status.MATERIALIZED ? materialized : evidenceOnly).add(fact.id().canonical());
            }
            var caseApplied=mutations.applyAuctionCut(snapshot,session,nextGeneration,auctionDiscovery);
            caseApplied.stream().filter(r->r.status()==NativeRuntimeMutationEngine.Status.MATERIALIZED).forEach(r->materialized.add(r.targetSemanticId()));
            mutations.reconcileAuthoritativeCartago(snapshot);
            boolean observedMoise=applied.stream().anyMatch(entry -> NativeRuntimeMutationEngine.observedMoiseRelation(entry.getValue().ruleId())
                && entry.getValue().status()==NativeRuntimeMutationEngine.Status.MATERIALIZED);
            if (!mutations.structureTransitionValid(baseline,observedMoise)) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_SNAPSHOT_STRUCTURE_INVALID");
            coordinator.acceptedSnapshot(session, nextGeneration, snapshot.modelRevision(), snapshot.sourceCompleteness());
            coordinator.observedSources(snapshot.facts(),true);
            coordinator.observedSemanticSnapshot(snapshot,session,nextGeneration);
            sessionId = session; generation = nextGeneration;
            if (!sameEpoch) { sourceWatermarks.clear(); ledger = new EventLedger(8192); }
            aliases.clear(); evidence.clear(); trace.clear();
            snapshot.facts().forEach(fact -> evidence.put(fact.id().canonical(), fact));
            if(mutations.auctionCase()&&!mutations.xmlCase())for(var fact:snapshot.facts())if(fact.values().containsKey(VerificationObservation.KEY)&&org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionSourceClock.qualified(VerificationObservation.require(fact,snapshot,session,nextGeneration)))
                auctionClockProofs.put(fact.id().canonical(),fact);
            if(mutations.xmlCase())for(var fact:snapshot.facts())if(fact.values().containsKey(VerificationObservation.KEY)&&SourceClock.qualified(VerificationObservation.require(fact,snapshot,session,nextGeneration)))
                auctionClockProofs.put(fact.id().canonical(),fact);
            snapshot.endWatermarks().forEach((source, watermark) -> sourceWatermarks.put(source, watermark.sequence()));
            int ordinal = 0;
            for (var entry : applied) record(entry.getKey().id().canonical() + ":snapshot", entry.getKey().id().authority(),
                    entry.getKey().id().canonical(), entry.getValue(), ordinal++);
            for(var result:caseApplied)record(snapshot.snapshotId()+":xml:"+ordinal,"auction-xml",result.targetSemanticId().isBlank()?snapshot.snapshotId():result.targetSemanticId(),result,ordinal++);
            snapshotId = snapshot.snapshotId(); resyncRequired = false;
            return new RuntimeVerificationCoordinator.Mutation<>(new ProjectionResult(materialized.size(), evidenceOnly, unavailable, List.of()), true,observedMoise,repeatedCut);
        });
        } catch (RuntimeException error) {if(coordinator.acceptedBoundaries()==acceptedBefore)restoreProtocol(before);else{resyncRequired=true;coordinator.coverageGap("POST_COMMIT_PROTOCOL_FAILURE:"+error.getMessage());}throw error; }
        });
    }
    public boolean apply(RuntimeEvent event) {
        return coordinator.read(() -> {
            if(deliveryRevoked.get())return false;
            try {
                requireIdentity(event);
                if(org.jacamo.bridge.contract.VerificationObservation.present(event))try {var observation=org.jacamo.bridge.contract.VerificationObservation.require(event);mutations.validateVerificationObservation(observation,event.sessionId(),event.generation(),event.modelRevision());}
                catch(org.jacamo.bridge.contract.ContractException invalid){throw new NativeRuntimeProtocolException(invalid.getMessage(),invalid);}
                if(mutations.auctionCase()&&!mutations.xmlCase())org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionObservationCut.validateClockContinuation(auctionClockProofs,event);
                if(mutations.xmlCase())SourceClockCut.validateContinuation(auctionClockProofs,event);
                if (event.projectionStatus() == ProjectionStatus.MATERIALIZED_FAITHFULLY && event.completeness() != Completeness.COMPLETE)
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_AUTHORITATIVE_EVENT_INCOMPLETE");
                if (event.kind() == RuntimeEventKind.GAP || event.kind() == RuntimeEventKind.MODEL_REVISION_CHANGED)
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_RESYNC_REQUIRED:" + event.kind());
                if (event.kind() == RuntimeEventKind.STREAM_BOUNDARY)
                    org.jacamo.bridge.contract.VerificationBoundary.require(event,sourceWatermarks);
                if (ledger.inspect(event, ContractPayloads.event(event)) == EventLedger.Result.DUPLICATE) return false;
                if (resyncRequired) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_RESYNC_REQUIRED");
                long previous = sourceWatermarks.getOrDefault(event.sourceId(), -1L);
                if (event.sourceSequence() <= previous)
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_EVENT_STALE:" + event.sourceSequence());
                if (previous >= 0 && event.sourceSequence() != previous + 1)
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_SEQUENCE_GAP:" + previous + "->" + event.sourceSequence());
            } catch (RuntimeException error) {
                resyncRequired = true; coordinator.coverageGap(event, error.getMessage()); throw error;
            }
            ProtocolState before = saveProtocol();
            long acceptedBefore=coordinator.acceptedBoundaries();
            try {
            boolean boundary=event.kind()==RuntimeEventKind.STREAM_BOUNDARY;
            String kind=boundary?"STREAM_BOUNDARY":coordinator.operationKind(event);
            boolean materialized = coordinator.transaction(kind, event.eventId(), event.sourceId(), event.sourceSequence(),
                    event.observedAt(), ContractPayloads.event(event), !kind.equals("EVENT"), () -> {
                if(boundary) {
                    sourceWatermarks.put(event.sourceId(),event.sourceSequence());
                    return new RuntimeVerificationCoordinator.Mutation<>(false,false);
                }
                if(mutations.xmlCase()) {
                    var advanced=RuntimeObservationCut.advance(evidence,event);evidence.clear();evidence.putAll(advanced);
                    if(VerificationObservation.present(event)&&SourceClock.qualified(VerificationObservation.require(event)))auctionClockProofs.put(event.entityId().canonical(),evidence.get(event.entityId().canonical()));
                } else if(mutations.auctionCase()) {
                    var advanced=org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionObservationCut.advance(evidence,auctionDiscovery,event);
                    evidence.clear();evidence.putAll(advanced.facts());auctionDiscovery.clear();auctionDiscovery.addAll(advanced.discovery());
                    if(VerificationObservation.present(event)&&org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionSourceClock.qualified(VerificationObservation.require(event)))
                        auctionClockProofs.put(event.entityId().canonical(),evidence.get(event.entityId().canonical()));
                }
                var result = mutations.apply(event.entityId(), event.relationId(), event.factKind(), event.projectionStatus(),
                        event.completeness(), event.after(), false);
                if (result.status() == NativeRuntimeMutationEngine.Status.REJECTED) {
                    resyncRequired = true;
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_MUTATION_REJECTED:" + result.diagnostic());
                }
                var xmlCut=mutations.xmlCase()?RuntimeObservationCut.eventCut(evidence,sourceWatermarks,coordinator.admittedSourceCoverage(),event):null;
                var caseApplied=xmlCut!=null?mutations.applyAuctionCut(xmlCut,sessionId,generation,List.of(),event)
                    :mutations.auctionCase()&&org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionObservationCut.relevant(event)?mutations.applyAuctionCut(org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionObservationCut.eventCut(evidence,sourceWatermarks,event),sessionId,generation,auctionDiscovery,event):List.<NativeRuntimeMutationEngine.ApplyResult>of();
                if(!kind.equals("EVENT") || event.subsystem().equals("cartago")) coordinator.operationCheckpoint(event,
                        xmlCut!=null?xmlCut:mutations.auctionCase()&&VerificationObservation.present(event)&&VerificationObservation.require(event).kind()==VerificationObservation.Kind.JASON_STEP
                                ?org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionObservationCut.eventCut(evidence,sourceWatermarks,event):null);
                coordinator.observedSource(event);
                sourceWatermarks.put(event.sourceId(), event.sourceSequence());
                record(event.eventId(), event.sourceId(), event.entityId() == null ? "event" : event.entityId().canonical(), result, event.sourceSequence());
                for(var mapped:caseApplied)record(event.eventId()+":xml:"+mapped.ruleId(),"auction-xml",mapped.targetSemanticId().isBlank()?event.eventId():mapped.targetSemanticId(),mapped,event.sourceSequence());
                boolean changed=result.status()==NativeRuntimeMutationEngine.Status.MATERIALIZED||caseApplied.stream().anyMatch(r->r.status()==NativeRuntimeMutationEngine.Status.MATERIALIZED);
                return new RuntimeVerificationCoordinator.Mutation<>(changed,changed,NativeRuntimeMutationEngine.observedMoiseRelation(result.ruleId()));
            });
            ledger.accept(event, ContractPayloads.event(event));
            return materialized;
            } catch (RuntimeException error) {if(coordinator.acceptedBoundaries()==acceptedBefore)restoreProtocol(before);else{resyncRequired=true;coordinator.coverageGap("POST_COMMIT_PROTOCOL_FAILURE:"+error.getMessage());}throw error; }
        });
    }
    private Map<String,Object> snapshotPayload(RuntimeSnapshot snapshot,String session,long nextGeneration) {
        var payload=new LinkedHashMap<>(ContractPayloads.runtime(snapshot));
        payload.put("admissionEpoch",Map.of("sessionId",java.util.Objects.toString(session,""),"generation",nextGeneration));
        return payload;
    }
    private void record(String eventId, String source, String identity, NativeRuntimeMutationEngine.ApplyResult result, long sequence) {
        if (result.status() == NativeRuntimeMutationEngine.Status.MATERIALIZED) {
            RuntimeAlias alias = new RuntimeAlias(identity, result.targetSemanticId(), result.targetUseId(), sessionId, generation, modelRevision);
            var previous = aliases.putIfAbsent(identity, alias);
            if (previous != null && !previous.equals(alias)) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ALIAS_COLLISION:" + identity);
            if (system().state().objectByName(result.targetUseId()) == null) aliases.remove(identity);
            aliases.entrySet().removeIf(entry -> system().state().objectByName(entry.getValue().targetUseId()) == null);
        }
        if (trace.size() == 8192) trace.remove(0);
        trace.add(new NativeRuntimeTraceRecord(result.ruleId(), TracePhase.RUNTIME_MUTATION, eventId, source, identity,
                result.targetSemanticId().isBlank() ? "evidence:" + identity : result.targetSemanticId(),
                result.targetUseId().isBlank() ? "evidence" : result.targetUseId(), result.status().name(), sequence,
                sessionId, generation, modelRevision, List.of(result.diagnostic())));
    }
    private BridgeRelationId binding(RuntimeFact fact) {
        var bindings = fact.relations().stream().filter(value -> value.relationKind().equals("runtime-model-binding")).toList();
        if (bindings.size() > 1 && fact.projectionStatus() == ProjectionStatus.MATERIALIZED_FAITHFULLY)
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BINDING_AMBIGUOUS");
        return bindings.size() == 1 ? bindings.getFirst() : null;
    }
    private void requireIdentity(RuntimeEvent event) {
        if (!sessionId.equals(event.sessionId())) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_SESSION_STALE");
        if (generation != event.generation()) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_GENERATION_STALE");
        requireRevision(event.modelRevision());
    }
    private void requireRevision(String revision) {
        if (!modelRevision.equals(revision)) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_MODEL_REVISION_STALE");
    }
    private static int rank(RuntimeFact fact) {
        Object value = fact.values().get("normalizedEventKind");
        if (!(value instanceof String kind)) return 100;
        return switch (kind) {
            case "UPSERT_CARTAGO_WORKSPACE" -> 10;
            case "UPSERT_JASON_AGENT_STATE" -> 15;
            case "UPSERT_CARTAGO_AGENT_IDENTITY" -> 20;
            case "UPSERT_CARTAGO_ARTIFACT" -> 30;
            case "UPSERT_CARTAGO_PROPERTY_SNAPSHOT" -> 40;
            case "UPSERT_CARTAGO_OPERATION" -> 45;
            case "SET_CARTAGO_FOCUS" -> 50;
            case "UPSERT_MOISE_GROUP" -> 60;
            case "UPSERT_MOISE_SCHEME" -> 70;
            case "UPSERT_MOISE_GROUP_PARENT" -> 80;
            default -> 50;
        };
    }
}
