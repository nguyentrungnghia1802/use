package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.Outcome;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.verification.*;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.uml.ocl.value.*;

/** Correlated native @pre evidence. Timeout policy advances on recorded source-event times for deterministic replay. */
final class NativeOperationCheckpoints {
    private record Active(OperationCheck check,long started,List<SemanticConstraintResult> pre,InvocationBoundary boundary,Set<String> interference,List<String> phases,String descriptor,String performer,Map<String,String> taskBindings,Map<String,String> postCoverage,List<NativeInvocationEvaluation.Condition> preparedPre) {
        Active {pre=List.copyOf(pre);interference=Set.copyOf(interference);phases=List.copyOf(phases);taskBindings=Map.copyOf(taskBindings);postCoverage=Map.copyOf(postCoverage);preparedPre=List.copyOf(preparedPre);}
        Active overlap(String key){var next=new TreeSet<>(interference);next.add(key);return new Active(check,started,pre,boundary,next,phases,descriptor,performer,taskBindings,postCoverage,preparedPre);}
        Active phase(String phase){var next=new ArrayList<>(phases);next.add(phase);return new Active(check,started,pre,boundary,interference,next,descriptor,performer,taskBindings,postCoverage,preparedPre);}
    }
    record Savepoint(Map<String,Active> active,Set<String> features,List<SemanticConstraintResult> detailed,List<NativeInvocationEvaluation.Work> prepared){}
    private final NativeRuntimeMutationEngine engine;
    private final DefaultVerificationService verifier=new DefaultVerificationService();
    private final LinkedHashMap<String,Active> active=new LinkedHashMap<>();
    private final InvocationCoveragePolicy policy;
    private List<SemanticConstraintResult> detailed=List.of();
    private List<NativeInvocationEvaluation.Work> prepared=List.of();
    private boolean deferCanonical;
    private Set<String> features=Set.of();
    private Map<String,Completeness> observationSources=Map.of();
    private boolean observationLost;
    private Set<String> observationFresh=Set.of();
    private final ConstraintCoverageResolver sourceCoverage=new ConstraintCoverageResolver();
    private java.util.function.Function<org.tzi.use.uml.sys.MObject,Map<String,String>> canonicalBindings=actor->Map.of();
    void canonicalBindings(java.util.function.Function<org.tzi.use.uml.sys.MObject,Map<String,String>> resolver){canonicalBindings=java.util.Objects.requireNonNull(resolver);}
    NativeOperationCheckpoints(NativeRuntimeMutationEngine engine){this(engine,InvocationCoveragePolicy.standard());}
    NativeOperationCheckpoints(NativeRuntimeMutationEngine engine,InvocationCoveragePolicy policy){this.engine=engine;this.policy=policy;}
    private boolean semanticProfile(){return engine.cslProfile()||engine.canonicalProfile();}
    void deferCanonical(){if(!engine.canonicalMappingConfigured())throw new IllegalArgumentException("CANONICAL_PROGRAM_REQUIRED");deferCanonical=true;}
    Savepoint savepoint(){return new Savepoint(Map.copyOf(active),features,detailed,prepared);}
    void restore(Savepoint image){active.clear();active.putAll(image.active());features=image.features();detailed=image.detailed();prepared=image.prepared();}
    List<SemanticConstraintResult> drainDetailed(){var result=detailed;detailed=List.of();return result;}
    List<NativeInvocationEvaluation.Work> drainPrepared(){var result=prepared;prepared=List.of();return result;}
    static String kind(RuntimeEvent event) {
        if(!"cartago".equals(event.subsystem()) || event.correlationId().isBlank())return "EVENT";
        return event.kind()==RuntimeEventKind.STARTED && "started".equals(event.after().get("phase"))?"OPERATION_PRE":event.kind()==RuntimeEventKind.SUCCEEDED || event.kind()==RuntimeEventKind.FAILED?"OPERATION_POST":"EVENT";
    }
    String eligibleKind(RuntimeEvent event){return operationReceiver(event)!=null?kind(event):"EVENT";}
    private org.tzi.use.uml.sys.MObject operationReceiver(RuntimeEvent event) {
        if(engine.xmlCase()) {
            // Raw XML M0 rules deliberately expose no unverified framework
            // alias. Resolve the admitted physical envelope identity instead;
            // ownership still requires its complete scope and incarnation.
            var identity=event.entityId();if(identity==null)return null;
            var object=engine.objectForSemanticId(identity.canonical());
            return object!=null&&artifactOwner(object,identity)?object:null;
        }
        Object id=event.after().get("artifactSemanticId");
        return id instanceof String value?engine.objectForSemanticId(value):null;
    }
    Set<String> features(){return features;}
    private void features(String name){var object=engine.system().state().objectByName(name);if(object==null){features=Set.of();return;}var keys=new HashSet<String>();keys.add("class:"+object.cls().name());object.cls().allParents().forEach(p->keys.add("class:"+p.name()));object.cls().allAttributes().forEach(a->keys.add("attribute:"+a.owner().name()+"."+a.name()));engine.system().state().allLinks().stream().filter(l->l.linkedObjects().contains(object)).forEach(l->keys.add("association:"+l.association().name()));features=Set.copyOf(keys);}
    private void addDetailed(Collection<SemanticConstraintResult> rows){var all=new ArrayList<>(detailed);all.addAll(rows);detailed=List.copyOf(all);}
    List<Outcome> observe(RuntimeEvent event,Map<String,Completeness> sources,boolean lost,Set<String> fresh) {
        observationSources=Map.copyOf(sources);observationLost=lost;observationFresh=Set.copyOf(fresh);
        features=Set.of();detailed=List.of();prepared=List.of();expire(event);
        String phase=Objects.toString(event.after().get("phase"),"");String kind=kind(event);
        if(kind.equals("EVENT")) {
            retireUnavailableContexts(event.eventId());
            if(Set.of("suspended","resumed").contains(phase) && event.entityId()!=null && !event.correlationId().isBlank()) {
                String key=InvocationBoundary.key(event);var before=active.get(key);
                if(before==null)return classified(SemanticVerdict.INCONCLUSIVE,"PHASE_MATCHING_PRE_UNAVAILABLE",event);
                active.put(key,before.phase(phase));addDetailed(List.of(detail("OPERATION:"+event.correlationId(),"INVOCATION_PHASE",before.check(),SemanticVerdict.SKIPPED,"",phase.toUpperCase(Locale.ROOT)+"_PRE_STATE_RETAINED",Map.of("phase",phase))));
            }
            return List.of();
        }
        String key=InvocationBoundary.key(event);
        try {
            InvocationBoundary boundary=null;
            if(semanticProfile())try{boundary=InvocationBoundary.from(event,active.keySet());}catch(IllegalArgumentException unavailable){return classified(SemanticVerdict.INCONCLUSIVE,unavailable.getMessage(),event);}
            if(kind.equals("OPERATION_PRE")) {
                if(active.containsKey(key)){active.remove(key);return classified(SemanticVerdict.INCONCLUSIVE,"PRE_DUPLICATE_CORRELATION_INVALIDATED",event);}
                if(active.size()>=policy.capacity())return classified(SemanticVerdict.INCONCLUSIVE,"PRE_CORRELATION_CAPACITY",event);
                var object=operationReceiver(event);
                if(object==null)return classified(SemanticVerdict.SKIPPED,"PRE_EXACT_CONTEXT_UNAVAILABLE",event);
                if(!artifactOwner(object,event.entityId()))return classified(SemanticVerdict.INCONCLUSIVE,"PRE_ARTIFACT_INCARNATION_MISMATCH",event);
                if(!(event.after().get("operationSignature") instanceof List<?> signature))return classified(SemanticVerdict.UNSUPPORTED,"PRE_EXACT_SIGNATURE_UNAVAILABLE",event);
                String canonical=NativeUseModelBuilder.canonicalJson(signature);var matches=object.cls().allOperations().stream().filter(op->canonical.equals(DomainProjection.decode(op.getAnnotationValue("CartagoOperation","signature64")))).toList();
                if(matches.size()!=1)return classified(SemanticVerdict.UNSUPPORTED,"PRE_EXACT_OPERATION_UNAVAILABLE",event);var operation=matches.getFirst();
                if(!(event.after().get("argumentValues") instanceof List<?> raw) || !(event.after().get("argumentTypes") instanceof List<?> types) || raw.size()!=types.size() || raw.size()!=operation.paramList().size())return classified(SemanticVerdict.UNSUPPORTED,"PRE_ARGUMENT_EVIDENCE_UNAVAILABLE",event);
                var arguments=new ArrayList<Value>();for(int i=0;i<raw.size();i++) {
                    if(!(raw.get(i) instanceof String text) || !(types.get(i) instanceof String type))return classified(SemanticVerdict.ERROR,"PRE_ARGUMENT_EVIDENCE_INVALID",event);
                    String oclType=switch(type){case "java.lang.Boolean"->"Boolean";case "java.lang.Byte","java.lang.Short","java.lang.Integer"->"Integer";case "java.lang.Float","java.lang.Double"->"Real";case "java.lang.String","java.lang.Character"->"String";default->"";};
                    if(oclType.isBlank())return classified(SemanticVerdict.UNSUPPORTED,"PRE_ARGUMENT_TYPE_UNSUPPORTED:"+type,event);
                    Value value=semanticProfile()?ScalarValueCodec.decode(oclType,List.of(text),List.of(type)):DomainProjection.propertyValue(oclType,text);
                    if(!value.type().conformsTo(operation.paramList().varDecl(i).type()))return classified(SemanticVerdict.ERROR,"PRE_ARGUMENT_TYPE_MISMATCH",event);arguments.add(value);
                }
                var request=new OperationRequest(object.name(),operation.name(),arguments,event.correlationId(),List.of(event.eventId()));
                var check=deferCanonical?new OperationCheck(request,operation,new org.tzi.use.uml.sys.MSystemState("canonical-invocation-pre",engine.system().state()),ConstraintRegistry.nativeModel(engine.system().model()),new TraceIndex(List.of()),VerificationReport.operation(event.correlationId(),List.of()))
                        :verifier.beginOperation(engine.system(),ConstraintRegistry.nativeModel(engine.system().model()),new TraceIndex(List.of()),request);
                var actor="CARTAGO_OPID_AGENT_BODY_ID".equals(event.after().get("performerAuthority"))
                    ?engine.objectForSemanticId(Objects.toString(event.after().get("performerSemanticId"),"")):null;
                String performer=actor==null?"":engine.metadata(actor,"semanticId");
                var interval=operationWitness(event,check,check.preState(),performer,semanticProfile()?Map.of("boundary",boundary.authority(),"sourceSequence",Long.toString(event.sourceSequence())):Map.<String,String>of());
                var pre=semanticProfile()&&!deferCanonical?detailedConditions(check,true,interval):List.<SemanticConstraintResult>of();addDetailed(pre);
                var nativePre=deferCanonical?preparedConditions(check,true,check.preState(),interval,Map.of(),false):List.<NativeInvocationEvaluation.Condition>of();
                if(deferCanonical)prepared=List.of(NativeInvocationEvaluation.prepare(nativePre,List.of(),List.of()));
                var overlap=Set.copyOf(active.keySet());active.replaceAll((k,v)->v.overlap(key));
                active.put(key,new Active(check,event.observedAt().toEpochMilli(),pre,boundary,overlap,List.of("started"),invocationDescriptor(event),performer,taskBindings(actor),postCoverage(check),nativePre));features(object.name());return engine.canonicalProfile()?semanticOutcomes(pre):outcomes(check.preconditions());
            }
            var before=active.remove(key);if(before==null)return classified(SemanticVerdict.INCONCLUSIVE,"POST_MATCHING_PRE_UNAVAILABLE",event);
            if(semanticProfile() && !before.descriptor().equals(invocationDescriptor(event)))return classified(SemanticVerdict.INCONCLUSIVE,"POST_INVOCATION_DESCRIPTOR_MISMATCH",event);
            var object=engine.system().state().objectByName(before.check().request().objectName());if(object==null)return classified(SemanticVerdict.INCONCLUSIVE,"POST_CONTEXT_REMOVED",event);
            if(!artifactOwner(object,event.entityId()))return classified(SemanticVerdict.INCONCLUSIVE,"POST_CONTEXT_INCARNATION_CHANGED",event);features(object.name());
            if(semanticProfile() && boundary.sourceSequence()<=before.boundary().sourceSequence())return classified(SemanticVerdict.ERROR,"POST_SOURCE_BOUNDARY_NOT_AFTER_PRE",event);
            var interval=operationWitness(event,before.check(),engine.system().state(),before.performer(),Map.of("interference",NativeUseModelBuilder.canonicalJson(before.interference().stream().sorted().toList()),"phases",NativeUseModelBuilder.canonicalJson(before.phases()),"intervalPolicy","OBSERVED_SHARED_STATE_NO_EFFECT_ISOLATION","operationLifecycleCoverage",Objects.toString(event.after().get("operationLifecycleCoverage"),"UNPROVEN"),"suspendResumeObservation",Objects.toString(event.after().get("suspendResumeObservation"),"UNPROVEN")));
            if(deferCanonical) {
                boolean failed=event.kind()!=RuntimeEventKind.SUCCEEDED;var after=new org.tzi.use.uml.sys.MSystemState("canonical-invocation-post",engine.system().state());
                var conditions=new ArrayList<>(before.preparedPre());conditions.addAll(preparedConditions(before.check(),false,after,interval,before.postCoverage(),failed));
                var contracts=new ArrayList<NativeInvocationEvaluation.Contract>();for(var condition:before.check().operation().postConditions())if("true".equals(condition.getAnnotationValue("CslConstraint","canonical")))
                    contracts.add(new NativeInvocationEvaluation.Contract(DomainProjection.decode(condition.getAnnotationValue("CslConstraint","contractBase64")),DomainProjection.decode(condition.getAnnotationValue("CslConstraint","targetTask64")),object.cls().name(),object.name(),engine.metadata(object,"semanticId"),condition.expression().toString(),before.check().request().correlationId(),taskWitness(before,interval),failed));
                var fixed=failed?List.of(detail("OPERATION:"+event.correlationId(),"INVOCATION",before.check(),SemanticVerdict.FAIL,"","SOURCE_REPORTED_OPERATION_FAILURE:"+Objects.toString(event.after().get("message"),""),interval)):List.<SemanticConstraintResult>of();
                prepared=List.of(NativeInvocationEvaluation.prepare(conditions,contracts,fixed));return List.of();
            }
            if(event.kind()!=RuntimeEventKind.SUCCEEDED) {
                if(semanticProfile()) {
                    var rows=new ArrayList<SemanticConstraintResult>();for(var condition:before.check().operation().postConditions())rows.add(detail(conditionId(condition),"POST",before.check(),SemanticVerdict.SKIPPED,"","OPERATION_FAILED_OR_ABORTED",interval));
                    rows.add(detail("OPERATION:"+event.correlationId(),"INVOCATION",before.check(),SemanticVerdict.FAIL,"","SOURCE_REPORTED_OPERATION_FAILURE:"+Objects.toString(event.after().get("message"),""),interval));rows.addAll(tasks(before,object,rows,true));addDetailed(rows);
                }
                return before.check().operation().postConditions().stream().map(c->new Outcome("COMPILED:"+c,c.cls().name(),VerificationOutcome.SKIPPED,"OPERATION_FAILED_OR_ABORTED",c.expression().toString(),object.name())).toList();
            }
            if(semanticProfile()) {
                List<SemanticConstraintResult> post;
                if(before.check().operation().hasResultType())post=before.check().operation().postConditions().stream().map(c->detail(conditionId(c),"POST",before.check(),SemanticVerdict.UNSUPPORTED,"","OPERATION_RETURN_VALUE_NOT_OBSERVED_BY_ICARTAGOLOGGER",interval)).toList();
                else post=detailedConditions(before.check(),false,interval).stream().map(r->{String reason=before.postCoverage().getOrDefault(r.id(),"");return reason.isBlank()?r:new SemanticConstraintResult(r.id(),r.kind(),r.contextClass(),r.contextObject(),r.sourceIdentity(),r.expression(),SemanticVerdict.SKIPPED,"","PRE_STATE_"+reason,r.stateVersion(),r.invocation(),r.witness());}).toList();
                var rows=new ArrayList<>(post);rows.addAll(tasks(before,object,post,false));addDetailed(rows);
            }
            return engine.canonicalProfile()?semanticOutcomes(detailed.stream().filter(r->r.kind().equals("POST")).toList()):outcomes(verifier.completeOperation(before.check(),engine.system().state(),null,List.of(event.eventId())));
        } catch(RuntimeException error){return classified(SemanticVerdict.ERROR,"OPERATION_CHECKPOINT_ERROR:"+error.getClass().getSimpleName()+":"+error.getMessage(),event);}
    }
    private static String invocationDescriptor(RuntimeEvent event){var payload=event.after();return NativeUseModelBuilder.canonicalJson(Map.of("artifact",payload.getOrDefault("artifactSemanticId",""),"signature",payload.getOrDefault("operationSignature",List.of()),"values",payload.getOrDefault("argumentValues",List.of()),"types",payload.getOrDefault("argumentTypes",List.of()),"performer",payload.getOrDefault("performerSemanticId",""),"performerAuthority",payload.getOrDefault("performerAuthority","")));}
    /** Additive case provenance. The original profiles retain their evidence
     * format; no hash implies isolation beyond the admitted shared-state cut. */
    private Map<String,String> operationWitness(RuntimeEvent event,OperationCheck check,org.tzi.use.uml.sys.MSystemState after,String performer,Map<String,String> interval) {
        if(!engine.declarativeMapping())return interval;
        var result=new TreeMap<>(interval);
        result.put("preStateHash",nativeStateHash(check.preState()));result.put("postStateHash",nativeStateHash(after));
        result.put("operationDescriptorHash",ExternalOclConstraintService.sha256(invocationDescriptor(event).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        result.put("performerSourceId",Objects.toString(event.after().get("performerSemanticId"),""));result.put("performerSourceAuthority",Objects.toString(event.after().get("performerAuthority"),"UNAVAILABLE"));
        result.put("performerSemanticId",performer);result.put("performerBinding",performer.isBlank()?"UNAVAILABLE":"EXACT_NATIVE_PLAYER_AT_PRE");
        result.put("artifact",engine.metadata(engine.system().state().objectByName(check.request().objectName()),"semanticId"));
        if(engine.xmlCase()) {
            result.put("sourceRule",check.operation().getAnnotationValue("DeclarativeOperationBinding","id"));
            result.put("mappingPlanHash",engine.canonicalProgram().xmlPlan().hash());
            String task=check.operation().getAnnotationValue("DeclarativeOperationBinding","task");if(!task.isBlank())result.put("targetTaskId",task);
        }
        result.put("preEventId",check.request().runtimeEventIds().getFirst());result.put("currentEventId",event.eventId());
        return Map.copyOf(result);
    }
    private static String nativeStateHash(org.tzi.use.uml.sys.MSystemState state){return ExternalOclConstraintService.sha256(new NativeUseSoilExporter().exportState(state).commands().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private void expire(RuntimeEvent event) {
        long now=event.observedAt().toEpochMilli();for(var entry:new ArrayList<>(active.entrySet()))if(now-entry.getValue().started()>policy.timeout().toMillis()) {
            var value=active.remove(entry.getKey());if(semanticProfile())addDetailed(List.of(detail("OPERATION:"+value.check().request().correlationId(),"INVOCATION_TIMEOUT",value.check(),SemanticVerdict.INCONCLUSIVE,"","MONITOR_TIMEOUT_AT_RECORDED_SOURCE_BOUNDARY",Map.of("timeoutMillis",Long.toString(policy.timeout().toMillis()),"sourceEvent",event.eventId()))));
        }
    }
    /** Called within the existing native transaction, after accepted projection.
     * Missing observation alone does not remove a native object and cannot
     * trigger this release. It is not evidence of a failed operation. */
    void retireUnavailableContexts(String boundary) {
        for(var entry:new ArrayList<>(active.entrySet())) {
            var value=entry.getValue();var object=engine.system().state().objectByName(value.check().request().objectName());
            if(object!=null&&(value.boundary()==null||value.boundary().artifactUuid().equals(artifactIncarnation(object))))continue;
            active.remove(entry.getKey());
            if(semanticProfile())addDetailed(List.of(detail("OPERATION:"+value.check().request().correlationId(),"INVOCATION_RETIRED",value.check(),SemanticVerdict.INCONCLUSIVE,"","EXACT_NATIVE_CONTEXT_RETIRED",Map.of("retirementBoundary",boundary,"invocationKey",entry.getKey()))));
        }
    }
    private boolean artifactOwner(org.tzi.use.uml.sys.MObject object,BridgeEntityId identity) {
        if(!engine.xmlCase())return engine.metadata(object,"uuid").equals(identity.incarnation());
        return identity.canonical().equals(engine.metadata(object,"xml:m0:identity"))
                &&identity.incarnation().equals(artifactIncarnation(object));
    }
    /** Generic M0 owns an exact physical BridgeEntityId; legacy UUID metadata is
     * not its contract. An alias alone cannot redirect invocation ownership. */
    private String artifactIncarnation(org.tzi.use.uml.sys.MObject object) {
        if(!engine.xmlCase())return engine.metadata(object,"uuid");
        if(!engine.canonicalProgramHash().equals(engine.metadata(object,"xml:m0:program")))return "";
        try {
            var identity=BridgeEntityId.parse(engine.metadata(object,"xml:m0:identity"));
            return identity.authority().equals("cartago")&&identity.dimension().equals("environment")&&identity.kind().equals("artifact")
                    &&engine.objectForSemanticId(identity.canonical())==object?identity.incarnation():"";
        }catch(IllegalArgumentException unavailable){return "";}
    }
    private Map<String,String> postCoverage(OperationCheck check) {
        var result=new TreeMap<String,String>();for(var condition:check.operation().postConditions())
            result.put(conditionId(condition),condition.expression().requiresPreState()?sourceCoverage.unavailableSelf(condition.expression(),engine,engine.system().state().objectByName(check.request().objectName()),observationSources,observationLost,observationFresh):"");
        return Map.copyOf(result);
    }
    private Map<String,String> taskBindings(org.tzi.use.uml.sys.MObject actor) {
        if(engine.canonicalMappingConfigured())return canonicalBindings.apply(actor);
        if(actor==null || !engine.cslProfile())return Map.of();
        var contexts=observationFresh;
        var bindings=new TreeMap<String,String>();var resolver=new org.tzi.use.plugins.jacamo.semantics.istar.RequirementBindingResolver();
        for(var node:CslUseBinding.configuration(engine.system().model()).requirements().nodes())
            if(node.kind()==org.tzi.use.plugins.jacamo.semantics.istar.RequirementModel.Kind.TASK && engine.objectForSemanticId(node.binding().playerIdentity())==actor) {
                var resolved=resolver.resolve(engine,node.binding(),contexts);if(resolved.available())bindings.put(node.id(),resolved.token());
            }
        return Map.copyOf(bindings);
    }
    private List<SemanticConstraintResult> tasks(Active before,org.tzi.use.uml.sys.MObject object,List<SemanticConstraintResult> post,boolean failed) {
        if(engine.canonicalProfile())return canonicalConformance(before,object,post,failed);
        var rows=new ArrayList<SemanticConstraintResult>();for(var contract:CslUseBinding.configuration(engine.system().model()).schema().contracts()) {
            var conditions=post.stream().filter(r->r.id().equals(contract.id()+":post")).toList();if(conditions.isEmpty())continue;var pre=before.pre().stream().filter(r->r.id().equals(contract.id()+":pre")).toList();boolean enabled=!pre.isEmpty() && pre.stream().allMatch(r->r.verdict()==SemanticVerdict.PASS);
            var verdict=!enabled?SemanticVerdict.SKIPPED:failed?SemanticVerdict.FAIL:conditions.getFirst().verdict();rows.add(new SemanticConstraintResult(contract.id(),"TASK_CONFORMANCE",object.cls().name(),object.name(),engine.metadata(object,"semanticId"),contract.post(),verdict,conditions.getFirst().backendValue(),!enabled?"PRECONDITION_NOT_TRUE":failed?"SOURCE_REPORTED_OPERATION_FAILURE":conditions.getFirst().diagnostic(),0,before.check().request().correlationId(),taskWitness(before,conditions.getFirst().witness())));
        }return rows;
    }
    private List<SemanticConstraintResult> canonicalConformance(Active before,org.tzi.use.uml.sys.MObject object,List<SemanticConstraintResult> post,boolean failed) {
        var rows=new ArrayList<SemanticConstraintResult>();
        for(var condition:before.check().operation().postConditions())if("true".equals(condition.getAnnotationValue("CslConstraint","canonical"))) {
            String base=DomainProjection.decode(condition.getAnnotationValue("CslConstraint","contractBase64"));
            var terminal=post.stream().filter(r->r.id().equals(conditionId(condition))).findFirst();if(terminal.isEmpty())continue;
            var pre=before.pre().stream().filter(r->r.id().equals(base+":pre")).findFirst();boolean enabled=pre.isPresent()&&pre.get().verdict()==SemanticVerdict.PASS;
            var verdict=!enabled?SemanticVerdict.SKIPPED:failed?SemanticVerdict.FAIL:terminal.get().verdict();
            var witness=new TreeMap<>(taskWitness(before,terminal.get().witness()));witness.put("targetTaskId",DomainProjection.decode(condition.getAnnotationValue("CslConstraint","targetTask64")));
            witness.put("historySemantics","ORIGINAL_TASK_MARKING_IS_SEPARATE");
            witness.put("preVerdict",pre.map(r->r.verdict().name()).orElse("SKIPPED"));
            witness.put("postVerdict",failed?"SKIPPED":terminal.get().verdict().name());
            rows.add(new SemanticConstraintResult(base,"INVOCATION_CONFORMANCE",object.cls().name(),object.name(),engine.metadata(object,"semanticId"),condition.expression().toString(),verdict,terminal.get().backendValue(),
                    !enabled?"PRECONDITION_NOT_TRUE":failed?"SOURCE_REPORTED_OPERATION_FAILURE":terminal.get().diagnostic(),0,before.check().request().correlationId(),witness));
        }
        return List.copyOf(rows);
    }
    private Map<String,String> taskWitness(Active before,Map<String,String> interval) {
        var witness=new TreeMap<>(interval);witness.put("performerSemanticId",before.performer());
        witness.put("performerAuthority",before.performer().isBlank()?"UNAVAILABLE":"PINNED_OPID_PLAYER_CROSSWALK");
        witness.put("taskBindingTokens",NativeUseModelBuilder.canonicalJson(before.taskBindings()));
        if(engine.canonicalMappingConfigured())witness.put("taskContextAdmission",before.taskBindings().isEmpty()?"EXACT_CONTEXT_UNAVAILABLE_OR_AMBIGUOUS":"UNIQUE_ELIGIBLE_CONTEXT_AT_PRE");return witness;
    }
    private List<Outcome> outcomes(VerificationReport report){return report.results().stream().map(r->{var object=r.contextObject()==null?null:engine.system().state().objectByName(r.contextObject());return new Outcome(r.constraintId(),object==null?"":object.cls().name(),r.outcome(),r.explanation(),r.oclSource(),r.contextObject());}).toList();}
    private List<Outcome> semanticOutcomes(List<SemanticConstraintResult> rows){return rows.stream().map(r->new Outcome(r.id(),r.contextClass(),r.verdict()==SemanticVerdict.PASS?VerificationOutcome.PASS:r.verdict()==SemanticVerdict.FAIL?VerificationOutcome.FAIL:VerificationOutcome.SKIPPED,r.diagnostic(),r.expression(),r.contextObject())).toList();}
    private static String conditionId(org.tzi.use.uml.mm.MPrePostCondition condition){String id=DomainProjection.decode(condition.getAnnotationValue("CslConstraint","id64"));return id.isBlank()?"COMPILED:"+condition:id;}
    private List<SemanticConstraintResult> detailedConditions(OperationCheck check,boolean pre,Map<String,String> interval) {
        var state=pre?check.preState():engine.system().state();var object=state.objectByName(check.request().objectName());var rows=new ArrayList<SemanticConstraintResult>();
        for(var condition:pre?check.operation().preConditions():check.operation().postConditions()) {
            var vars=new VarBindings(state);vars.push("self",object.value());for(int i=0;i<check.request().arguments().size();i++)vars.push(check.operation().paramNames().get(i),check.request().arguments().get(i));String unavailable=sourceCoverage.unavailableSelf(condition.expression(),engine,object,observationSources,observationLost,observationFresh);
            var value=unavailable.isBlank()?new OclEvaluationAdapter().evaluate(condition.expression(),check.preState(),state,vars):new OclEvaluationAdapter.Result(ConstraintCoverageResolver.unavailableVerdict(unavailable),"",unavailable);
            var witness=new TreeMap<String,String>(interval);if(engine.declarativeMapping())witness.putAll(sourceCoverage.witness(condition.expression(),engine,object,observationSources,unavailable));for(var attribute:object.cls().allAttributes()){witness.put("before."+attribute.name(),object.state(check.preState()).attributeValue(attribute).toString());witness.put("after."+attribute.name(),object.state(state).attributeValue(attribute).toString());}rows.add(detail(conditionId(condition),pre?"PRE":"POST",check,value.verdict(),value.backendValue(),value.diagnostic(),witness));
        }return List.copyOf(rows);
    }
    private List<NativeInvocationEvaluation.Condition> preparedConditions(OperationCheck check,boolean pre,org.tzi.use.uml.sys.MSystemState state,Map<String,String> interval,Map<String,String> preCoverage,boolean failed) {
        var object=state.objectByName(check.request().objectName());var rows=new ArrayList<NativeInvocationEvaluation.Condition>();
        for(var condition:pre?check.operation().preConditions():check.operation().postConditions()) {
            var variables=new TreeMap<String,Value>();variables.put("self",object.value());for(int i=0;i<check.request().arguments().size();i++)variables.put(check.operation().paramNames().get(i),check.request().arguments().get(i));
            String unavailable=sourceCoverage.unavailableSelf(condition.expression(),engine,object,observationSources,observationLost,observationFresh);var unavailableVerdict=SemanticVerdict.SKIPPED;
            String beforeCoverage=preCoverage.getOrDefault(conditionId(condition),"");if(!beforeCoverage.isBlank())unavailable="PRE_STATE_"+beforeCoverage;
            if(!unavailable.isBlank())unavailableVerdict=ConstraintCoverageResolver.unavailableVerdict(unavailable);
            if(!pre&&check.operation().hasResultType()){unavailable="OPERATION_RETURN_VALUE_NOT_OBSERVED_BY_ICARTAGOLOGGER";unavailableVerdict=SemanticVerdict.UNSUPPORTED;}
            if(!pre&&failed){unavailable="OPERATION_FAILED_OR_ABORTED";unavailableVerdict=SemanticVerdict.SKIPPED;}
            var witness=new TreeMap<>(interval);if(engine.declarativeMapping())witness.putAll(sourceCoverage.witness(condition.expression(),engine,object,observationSources,unavailable));for(var attribute:object.cls().allAttributes()){witness.put("before."+attribute.name(),object.state(check.preState()).attributeValue(attribute).toString());witness.put("after."+attribute.name(),object.state(state).attributeValue(attribute).toString());}
            rows.add(new NativeInvocationEvaluation.Condition(conditionId(condition),pre?"PRE":"POST",object.cls().name(),object.name(),engine.metadata(object,"semanticId"),condition.expression().toString(),condition.expression(),check.preState(),state,variables,unavailable,unavailableVerdict,check.request().correlationId(),witness));
        }
        return List.copyOf(rows);
    }
    private SemanticConstraintResult detail(String id,String kind,OperationCheck check,SemanticVerdict verdict,String backend,String diagnostic,Map<String,String> witness){var object=engine.system().state().objectByName(check.request().objectName());String expression=java.util.stream.Stream.concat(check.operation().preConditions().stream(),check.operation().postConditions().stream()).filter(c->conditionId(c).equals(id)).map(c->c.expression().toString()).findFirst().orElse("");return new SemanticConstraintResult(id,kind,check.operation().cls().name(),check.request().objectName(),object==null?"":engine.metadata(object,"semanticId"),expression,verdict,backend,diagnostic,0,check.request().correlationId(),witness);}
    private List<Outcome> classified(SemanticVerdict verdict,String reason,RuntimeEvent event){if(semanticProfile())addDetailed(List.of(new SemanticConstraintResult("OPERATION:"+event.correlationId(),"INVOCATION","","","","",verdict,"",reason,0,event.correlationId(),Map.of())));return List.of(new Outcome("OPERATION:"+event.correlationId(),"",VerificationOutcome.SKIPPED,reason,""));}
    void clear(){active.clear();detailed=List.of();prepared=List.of();}
}
