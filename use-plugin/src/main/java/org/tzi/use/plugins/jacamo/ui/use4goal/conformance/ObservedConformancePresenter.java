package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Evaluation;

/** Presentation adapter only. It never creates or changes a model, state or engine. */
public final class ObservedConformancePresenter {
    private final JaCaMoFacade facade;
    // Presentation history is keyed by accepted boundary, not by the bounded RAM tail.
    // The existing hash-checked disk journal remains the history authority.
    private final LinkedHashMap<String,RuntimeVerificationResult> persistedResults=new LinkedHashMap<>();
    private final Map<RuntimeVerificationResult,String> hashes=new IdentityHashMap<>();
    private record Rendered(RuntimeVerificationResult result,boolean image,String category,Cut cut,Map<String,String> certifications,String priorCertificates,String nextCertificates) { }
    private final Map<String,Rendered> rendered=new HashMap<>();
    private final Map<String,String> persistedStateCommands=new HashMap<>();
    private String historyOwner="";
    private long nextOrdinal, lastVersion;
    private volatile boolean historyPending;
    public boolean historyPending(){return historyPending;}
    public ObservedConformancePresenter(JaCaMoFacade facade) { this.facade = Objects.requireNonNull(facade); }

    private String liveOwner="";
    private final Map<String,String> liveCertificates=new HashMap<>();
    /** Live window has no historical navigation: never parse the disk prefix or
     * hash every retained snapshot on a refresh. All audit cuts stay in capture/replay. */
    public synchronized List<Cut> captureCurrent(){
        var snapshot=facade.verificationSnapshot();var result=snapshot.result();if(result==null)return List.of();
        String owner=result.sessionId()+":"+result.generation()+":"+result.modelRevision();
        if(!owner.equals(liveOwner)){liveCertificates.clear();liveOwner=owner;}
        // A checked completion from the in-memory writer tail may settle the
        // exact pending anchor; never borrow another boundary/profile/state.
        for(var entry:facade.runtimeHistoryTail().entries())if(boundaryKey(result).equals(boundaryKey(entry.result()))&&pendingCopyOfCompleted(result,entry.result()))result=entry.result();
        VerificationSnapshot.StateImage image=null;
        if(snapshot.currentVersion()==result.stateVersion()&&snapshot.result().stateHash().equals(result.stateHash()))image=snapshot.image();
        if(liveCertificates.size()>8192)liveCertificates.clear();
        var updates=new HashMap<String,String>();var cut=cut(result,image,"Current accepted verification cut",result.resultHash(),liveCertificates,updates);
        return List.of(cut);
    }

    public synchronized List<Cut> capture() {
        return capture(Integer.MAX_VALUE);
    }
    public synchronized List<Cut> captureIncremental(){return capture(64);}
    private List<Cut> capture(int budget) {
        // This immutable snapshot is the ownership anchor. History may advance
        // concurrently, so do not join evidence from a different epoch/revision.
        var snapshot = facade.verificationSnapshot();
        var latest = snapshot.result();
        var results = new LinkedHashMap<String, RuntimeVerificationResult>();
        if(latest!=null) {
            loadPersistedHistory(latest,budget);
            for(var result:persistedResults.values())if(result.stateVersion()<=latest.stateVersion())results.put(boundaryKey(result),result);
        }
        for (var result : historyPending?List.<RuntimeVerificationResult>of():facade.runtimeVerificationHistory()) {
            if (latest == null || sameOwner(latest, result) && result.stateVersion() <= latest.stateVersion())
                results.put(boundaryKey(result), result);
        }
        if (latest != null && !historyPending) {
            var completed=results.get(boundaryKey(latest));
            // An immutable pending anchor may lag the checked journal completion
            // for this SAME cut. This never certifies a different/newer cut.
            if(!pendingCopyOfCompleted(latest,completed))results.put(boundaryKey(latest),latest);
        }
        var images=new HashMap<String,VerificationSnapshot.StateImage>();
        var checkpoints=new HashMap<String,String>();
        var retainedSnapshots=facade.retainedVerificationSnapshots();
        for(var retained:retainedSnapshots){rememberImage(images,retained);rememberCheckpoint(checkpoints,retained);}
        rememberImage(images,snapshot);
        rememberCheckpoint(checkpoints,snapshot);
        var cuts=new ArrayList<Cut>();String previousHash="";var certificates=new HashMap<String,String>();String certificatePrefix="";
        for(var result:results.values()){
            String hash=hash(result),key=boundaryKey(result);
            String kind=checkpoints.getOrDefault(hash,"HISTORY_RECORD");
            String category=Set.of("OPERATION_PRE","OPERATION_POST","TASK_PRE","TASK_POST").contains(kind)
                    ?(result.stateHash().equals(previousHash)?"Execution-only boundary":"Execution and state boundary"):
                    result.stateHash().equals(previousHash)?"Evidence boundary (state unchanged)":"State boundary";
            String description=category+" / "+kind;var image=images.get(hash);
            if(image==null&&persistedStateCommands.containsKey(result.stateHash()))image=new VerificationSnapshot.StateImage(Map.of(),List.of(),persistedStateCommands.get(result.stateHash()));
            var cached=rendered.get(key);
            String priorCertificates=certificatePrefix;
            if(cached==null||cached.result()!=result||cached.image()!=(image!=null)||!cached.category().equals(description)||!cached.priorCertificates().equals(priorCertificates)) {
                var updates=new HashMap<String,String>();
                var renderedCut=cut(result,image,description,hash,certificates,updates);
                String next=updates.isEmpty()?certificatePrefix:org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(List.of(certificatePrefix,updates));
                cached=new Rendered(result,image!=null,description,renderedCut,Map.copyOf(updates),priorCertificates,next);rendered.put(key,cached);
            }
            certificates.putAll(cached.certifications());
            certificatePrefix=cached.nextCertificates();
            cuts.add(cached.cut());previousHash=result.stateHash();
        }
        rendered.keySet().retainAll(results.keySet());
        var retainedResults=Collections.newSetFromMap(new IdentityHashMap<RuntimeVerificationResult,Boolean>());
        retainedResults.addAll(results.values());retainedResults.addAll(persistedResults.values());
        for(var retained:retainedSnapshots)if(retained.result()!=null)retainedResults.add(retained.result());
        if(latest!=null)retainedResults.add(latest);
        hashes.keySet().removeIf(result->!retainedResults.contains(result));
        return List.copyOf(cuts);
    }
    private static boolean pendingCopyOfCompleted(RuntimeVerificationResult pending,RuntimeVerificationResult completed) {
        return completed!=null&&sameOwner(pending,completed)&&"EVALUATION_PENDING".equals(pending.coverage())&&"PENDING_EVALUATION".equals(pending.freshness())
                &&pending.stateHash().equals(completed.stateHash())&&pending.constraintSetHash().equals(completed.constraintSetHash())
                &&pending.semantic()!=null&&completed.semantic()!=null&&pending.semantic().boundary().equals(completed.semantic().boundary())
                &&"CURRENT_OBSERVED".equals(completed.freshness())&&completed.semantic().observation().get("originalEngine") instanceof Map<?,?> core&&"PINNED_NATIVE_CUT".equals(core.get("evaluated"));
    }
    private String hash(RuntimeVerificationResult result){return hashes.computeIfAbsent(result,RuntimeVerificationResult::resultHash);}
    private void loadPersistedHistory(RuntimeVerificationResult latest,int budget) {
        String owner=latest.sessionId()+":"+latest.generation()+":"+latest.modelRevision();
        var tail=facade.runtimeHistoryTail();
        // A stopped monitor exposes its frozen snapshot and RAM tail, with no live
        // journal API. Keep the already-read prefix until ownership actually changes.
        if(!owner.equals(historyOwner)||(tail.persistedEntries()>0&&tail.persistedEntries()<nextOrdinal)||latest.stateVersion()<lastVersion) {
            persistedResults.clear();persistedStateCommands.clear();hashes.clear();rendered.clear();nextOrdinal=0;historyOwner=owner;
        }
        // Capture a finite prefix so an active producer cannot keep this worker busy forever.
        long end=Math.min(tail.persistedEntries(),nextOrdinal+(long)budget), offset=nextOrdinal;
        var additions=new LinkedHashMap<String,RuntimeVerificationResult>();
        while(offset<end) {
            // Normal refreshes can advance from the successfully persisted writer
            // tail. Avoid rescanning a large JSONL prefix on every UI poll. Initial
            // load and any interval no longer in the tail still use checked disk pages.
            if(offset>0) {
                long position=offset;
                var recent=new LinkedHashMap<String,RuntimeVerificationResult>();
                for(var entry:tail.entries())if(entry.persisted()&&entry.ordinal()>=offset&&entry.ordinal()<end) {
                    if(entry.ordinal()!=position)break;
                    if(sameOwner(latest,entry.result())){recent.put(boundaryKey(entry.result()),entry.result());rememberCommands(entry);}
                    position++;
                }
                if(position==end){additions.putAll(recent);offset=end;break;}
            }
            var page=facade.runtimeHistoryPage(offset,(int)Math.min(128,end-offset));
            if(page.nextOrdinal()<=offset)throw new IllegalStateException("OBSERVED_HISTORY_PAGE_NOT_ADVANCING");
            for(var entry:page.entries())if(entry.ordinal()>=offset&&entry.ordinal()<end&&sameOwner(latest,entry.result())){
                additions.put(boundaryKey(entry.result()),entry.result());rememberCommands(entry);
            }
            offset=Math.min(end,page.nextOrdinal());
        }
        // Publish the cache/cursor only after all pages passed the existing integrity checks.
        persistedResults.putAll(additions);nextOrdinal=offset;lastVersion=latest.stateVersion();
        historyPending=nextOrdinal<tail.persistedEntries();
    }
    private void rememberCommands(org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeHistoryPage.Entry entry){
        if(!entry.stateCommands().isBlank())persistedStateCommands.putIfAbsent(entry.result().stateHash(),entry.stateCommands());
    }
    private static String boundaryKey(RuntimeVerificationResult result){return result.sessionId()+":"+result.generation()+":"+result.modelRevision()+":"+result.stateVersion()+":"+result.eventId()+":"+result.checkpointId();}
    private void rememberCheckpoint(Map<String,String> checkpoints,VerificationSnapshot cut){
        if(cut!=null&&cut.result()!=null&&cut.metadata()!=null)checkpoints.put(hash(cut.result()),cut.metadata().checkpoint().name());
    }
    private void rememberImage(Map<String,VerificationSnapshot.StateImage> images,VerificationSnapshot cut) {
        if(cut!=null&&cut.result()!=null&&cut.image()!=null&&cut.currentVersion()==cut.result().stateVersion())
            images.put(hash(cut.result()),cut.image());
    }

    private static boolean sameOwner(RuntimeVerificationResult a, RuntimeVerificationResult b) {
        return a.sessionId().equals(b.sessionId()) && a.generation() == b.generation()
                && a.modelRevision().equals(b.modelRevision());
    }

    private static Cut cut(RuntimeVerificationResult result, VerificationSnapshot.StateImage image,String category,String hash,Map<String,String> certificates,Map<String,String> updates) {
        var rows = new ArrayList<Evaluation>();
        boolean failed = false;
        if (result.semantic() != null) {
            var semantic = result.semantic();
            var inventory=CanonicalJson.object(semantic.observation().getOrDefault("requirementInventory",Map.of()));
            var requiredInvariants=(List<?>)inventory.getOrDefault("requiredInvariants",List.of());
            var requiredElements=(List<?>)inventory.getOrDefault("selectedIntentionalElements",List.of());
            var supportingInvariants=(List<?>)inventory.getOrDefault("supportingInvariants",List.of());
            if (semantic.observation().get("originalEngine") instanceof Map<?,?> raw) {
                var core = CanonicalJson.object(raw);
                for (Object item : (List<?>) core.getOrDefault("contexts", List.of())) {
                    var context = CanonicalJson.object(item);
                    var occurrences = CanonicalJson.object(context.getOrDefault("occurrences", Map.of()));
                    for (var entry : new TreeMap<>(occurrences).entrySet()) {
                        var occurrence = CanonicalJson.object(entry.getValue());
                        if(!Set.of("GOAL","TASK","LEGACY").contains(Objects.toString(occurrence.getOrDefault("elementKind","LEGACY"))))continue;
                        // Population receivers implement ANY/ALL inside the
                        // execution graph; they are not extra source Goal instances.
                        if(Set.of("ANY_DEPENDENCY_RECEIVER","ALL_DEPENDENCY_RECEIVER").contains(Objects.toString(occurrence.get("expansionKind"),"")))continue;
                        String quality = Objects.toString(core.getOrDefault("qualityOverlay", occurrence.getOrDefault("quality", "UNAVAILABLE")));
                        String propagated = Objects.toString(occurrence.getOrDefault("aggregateStatus", "UNAVAILABLE"));
                        boolean known = Set.of("KNOWN","KNOWN_DERIVED").contains(quality) && "OBSERVED_PREFIX".equals(core.get("historyQuality"))
                                && "PINNED_NATIVE_CUT".equals(core.get("evaluated"));
                        String qualified = known ? propagated : "UNKNOWN";
                        String executionQuality=Objects.toString(occurrence.getOrDefault("executionQuality",quality));
                        var executionEvidence=CanonicalJson.object(occurrence.getOrDefault("executionEvidence",Map.of()));
                        boolean currentProof=Set.of("KNOWN","KNOWN_DERIVED").contains(executionQuality)
                                &&"ORIGINAL_PROPAGATION_ON_ADMITTED_LEAVES/1.0".equals(context.get("executionQualification"))
                                &&"OBSERVED_PREFIX".equals(core.get("historyQuality"))&&"PINNED_NATIVE_CUT".equals(core.get("evaluated"));
                        // Historical qualification is issued by the ordered runtime
                        // owner, never inferred from a stale raw marking or UI cache.
                        boolean historical=Set.of("KNOWN_HISTORY","KNOWN_DERIVED_HISTORY").contains(executionQuality)
                                &&!executionEvidence.isEmpty()&&"PINNED_NATIVE_CUT".equals(core.get("evaluated"));
                        if(historical||currentProof){qualified=propagated;quality=executionQuality;}
                        boolean terminalFailure=historical&&"FAILED".equals(occurrence.get("executionStatus"))&&executionEvidence.values().stream().map(CanonicalJson::object).anyMatch(p->"TASK_FAILED".equals(p.get("kind"))&&"CERTIFIED_TERMINAL_EXECUTION/1.0".equals(p.get("policy"))&&!Objects.toString(p.get("invocation"),"").isBlank());
                        if(terminalFailure)qualified="FAILED";
                        if("SOURCE_NOT_ASSIGNED".equals(executionQuality)&&"PINNED_NATIVE_CUT".equals(core.get("evaluated"))&&"OBSERVED_PREFIX".equals(core.get("historyQuality"))){qualified="NOT_ASSIGNED";quality=executionQuality;}
                        boolean requiredElement=requiredElements.isEmpty()||requiredElements.contains(occurrence.getOrDefault("sourceElement",entry.getKey()));
                        failed |= requiredElement&&qualified.equals("VIOLATED");
                        String identity = "context=" + context.getOrDefault("context", "UNAVAILABLE")
                                + "; occurrence=" + entry.getKey() + "; player=" + occurrence.getOrDefault("player", "UNAVAILABLE")
                                + "; role=" + occurrence.getOrDefault("role", "UNAVAILABLE")
                                + "; raw propagated=" + propagated
                                + "; current observation="+occurrence.getOrDefault("quality","UNAVAILABLE")
                                + "; execution evidence="+executionEvidence
                                + "; assignment evidence="+occurrence.getOrDefault("assignmentEvidence",Map.of());
                        String certificateKey=result.sessionId()+":"+result.generation()+":"+result.modelRevision()+":"+context.get("context")+":"+entry.getKey()+":"+occurrence.get("player")+":"+occurrence.get("role")+":"+Objects.toString(occurrence.get("key"),"NO_EPISODE");
                        if(known) {
                            String certificate=qualified+" @ state "+result.stateVersion()+"; result="+hash;
                            certificates.put(certificateKey,certificate);updates.put(certificateKey,certificate);
                        }
                        rows.add(new Evaluation(Objects.toString(occurrence.getOrDefault("sourceElement", entry.getKey())),
                                Objects.toString(occurrence.getOrDefault("leafStatus", "UNAVAILABLE")), qualified, quality, identity,historical?"Retained runtime execution certificate: "+executionEvidence:certificates.getOrDefault(certificateKey,"No certified result for this episode"),requiredElement,"GOAL_TASK",propagated,Objects.toString(occurrence.getOrDefault("player","")),Objects.toString(context.getOrDefault("context","")),entry.getKey()));
                    }
                }
            }
            if(semantic.observation().get("originalEngine") instanceof Map<?,?> original&&original.get("expectedIntentionalElements") instanceof Number expected&&expected.intValue()>0&&rows.isEmpty())
                rows.add(new Evaluation("Goal/Task occurrences","UNAVAILABLE","UNKNOWN","NO_ADMITTED_GOAL_OCCURRENCES","No current qualified intentional occurrences"));
            for (var constraint : semantic.constraints()) {
                boolean condition=Set.of("GOAL_CONDITION","TASK_PRE","TASK_POST").contains(constraint.kind());
                boolean execution=constraint.kind().equals("EXECUTION_OUTCOME");
                failed |= !condition&&!execution&&constraint.verdict() == org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL;
                // Predicate truth feeds original marking. An achievement condition
                // being false is not itself an iStar violation.
                String status=execution?"EXECUTION_FAILED":condition&&constraint.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL?"CONDITION_FALSE":condition&&constraint.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS?"CONDITION_TRUE":constraint.verdict().name();
                rows.add(new Evaluation(constraint.id(), constraint.backendValue(), status,
                        constraint.diagnostic(), constraint.contextObject() + " / " + constraint.invocation() + " / expression="+constraint.expression()+" / witness=" + constraint.witness(),"",!condition&&!execution&&(!constraint.kind().equals("INVARIANT_BODY")||semantic.constraints().stream().noneMatch(c->c.id().equals(constraint.id())&&c.kind().equals("INVARIANT")))&&(!Set.of("INVARIANT","INVARIANT_BODY").contains(constraint.kind())||requiredInvariants.contains(constraint.id())||!supportingInvariants.contains(constraint.id())),constraint.kind(),"","",constraint.contextObject(),constraint.invocation()));
            }
            for(var required:requiredElements)if(rows.stream().noneMatch(r->r.kind().equals("GOAL_TASK")&&r.element().equals(required)))
                rows.add(new Evaluation(Objects.toString(required),"UNAVAILABLE","UNKNOWN","REQUIRED_INTENTIONAL_RESULT_MISSING","No admitted occurrence for selected requirement","",true,"GOAL_TASK"));
            for(var required:requiredInvariants)if(semantic.constraints().stream().noneMatch(c->c.id().equals(required)&&c.kind().equals("INVARIANT")))
                rows.add(new Evaluation(Objects.toString(required),"UNAVAILABLE","UNKNOWN","REQUIRED_CONTRACT_RESULT_MISSING","Declared native invariant has no aggregate result at this cut","",true,"INVARIANT"));
        }
        for (var outcome : result.outcomes()) {
            if(result.semantic()!=null&&result.semantic().constraints().stream().anyMatch(c->c.id().equals(outcome.constraintId())))continue;
            failed |= outcome.outcome() == org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL;
            rows.add(new Evaluation(outcome.constraintId(), "native OCL", outcome.outcome().name(),
                    outcome.diagnostic(), outcome.contextObject()));
        }
        String state = "Accepted state hash: " + result.stateHash() + "\nState image not retained for this cut.";
        if(image!=null)state=image.objects().isEmpty()&&!image.soil().isBlank()?image.soil():new String(CanonicalJson.encode(image.toMap()),StandardCharsets.UTF_8);
        String trace = category+"\nObserved prefix | state " + result.stateVersion() + " | event " + result.eventId()
                + " | source " + result.sourceId() + ":" + result.sourceSequence()
                + " | checkpoint " + result.checkpointId() + "\n" + result.diagnostic()
                + "\nSource observed="+result.observedAt()+"; USE accepted="+result.appliedAt()+"; verified="+result.verifiedAt()
                + "; source-to-verification delay="+Math.max(0,java.time.Duration.between(result.observedAt(),result.verifiedAt()).toMillis())+" ms (completion latency, not proof of loss or of stream drain)";
        // Older profiles without an explicit presentation projection retain every native state change.
        boolean executionMilestones=true,businessPopulationEmpty=false;
        String businessKey=result.semantic()!=null&&result.semantic().observation().get("originalEngine") instanceof Map<?,?> engine&&!"PINNED_NATIVE_CUT".equals(engine.get("evaluated"))?"":result.stateHash(),businessState="";
        if(result.semantic()!=null&&result.semantic().observation().get("presentationState") instanceof Map<?,?> projection&&!projection.isEmpty()) {
            executionMilestones=!Boolean.FALSE.equals(projection.get("executionMilestones"));
            businessKey=Boolean.TRUE.equals(projection.get("qualifiedValues"))?Objects.toString(projection.get("hash"),""):"";var text=new StringBuilder("Selected business state\n");
            for(var entry:new TreeMap<>(CanonicalJson.object(projection.containsKey("objects")?projection.get("objects"):Map.of())).entrySet()) {
                text.append(entry.getKey()).append('\n');
                for(var attribute:new TreeMap<>(CanonicalJson.object(entry.getValue())).entrySet())text.append("  ").append(attribute.getKey()).append(" = ").append(attribute.getValue()).append('\n');
            }
            businessPopulationEmpty=CanonicalJson.object(projection.containsKey("objects")?projection.get("objects"):Map.of()).isEmpty();
            businessState=Boolean.TRUE.equals(projection.get("pending"))||!Boolean.TRUE.equals(projection.get("qualifiedValues"))
                    ?"Selected CSL state unavailable at this audit boundary (pending or incomplete projection). Use audit details for the native state image.":text.toString();
        }
        long missing=rows.stream().filter(Evaluation::required).filter(r->Set.of("UNKNOWN","SKIPPED","UNSUPPORTED","INCONCLUSIVE","UNDEFINED","ERROR").contains(r.status())).count();
        String scoped="Requirements: "+(rows.isEmpty()?"NOT_EVALUATED":failed?"VIOLATION":missing>0?"INCOMPLETE ("+missing+" unavailable)":"KNOWN")+" | Overall source coverage: "+result.coverage()+" | Last completion delay: "+Math.max(0,java.time.Duration.between(result.observedAt(),result.verifiedAt()).toMillis())+" ms ["+result.sourceId()+"] ("+(result.eventId().equals("manual")?"local check, not producer lag":"not a caught-up assertion")+")";
        boolean selectedRequirements=result.semantic()!=null&&"SELECTED_REQUIREMENTS/1.0".equals(result.semantic().observation().get("requirementCoveragePolicy"))
                &&result.semantic().observation().get("originalEngine") instanceof Map<?,?> engine&&"PINNED_NATIVE_CUT".equals(engine.get("evaluated"))&&"OBSERVED_PREFIX".equals(engine.get("historyQuality"));
        return new Cut(boundaryKey(result), trace+"\nResult hash: "+hash, state, rows, result.coverage(), result.freshness(), failed,businessKey,scoped,businessState,executionMilestones,selectedRequirements,businessPopulationEmpty,result.semantic()==null?Map.of():result.semantic().observation());
    }
}
