package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.profile.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MObject;

/** Semantic participant in the native coordinator's commit, not another model/state owner. */
public final class SemanticRuntimeVerifier {
    private final NativeRuntimeMutationEngine engine;
    private final CslMonitorConfiguration config;
    private final TemporalGoalMonitor monitor;
    private final OclEvaluationAdapter evaluator=new OclEvaluationAdapter();
    private final ConstraintCoverageResolver coverage=new ConstraintCoverageResolver();
    private final RequirementBindingResolver resolver;
    private final boolean bindingCache;private final Map<String,String> requirementTypes=new HashMap<>();
    private final Map<String,Object> staticProvenance;
    private long lastIntentionalNanos;public long lastIntentionalNanos(){return lastIntentionalNanos;}
    private final SemanticConstraintRegistry compiled;
    private final Map<String,Expression> predicates=new HashMap<>(),activations=new HashMap<>();
    private Map<String,Object> observation=Map.of();
    private final Set<String> freshContexts=new HashSet<>();
    private final Set<String> normHistoryGaps=new HashSet<>();
    public record Savepoint(TemporalGoalMonitor.Snapshot monitor,Set<String> freshContexts,Map<String,Object> observation,Set<String> normHistoryGaps) { }
    public SemanticRuntimeVerifier(NativeRuntimeMutationEngine engine) {
        this.engine=engine;config=CslUseBinding.configuration(engine.system().model());monitor=new TemporalGoalMonitor(config.requirements());
        bindingCache=!Boolean.getBoolean("use.jacamo.semantics.disableBindingCache");resolver=new RequirementBindingResolver(engine.system().model(),config,bindingCache);staticProvenance=SemanticMappingAttachment.provenance(engine.system().model());
        compiled=new SemanticConstraintRegistry(engine.system().model(),org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseModelBuilder.canonicalJson(config.schema()),ExternalOclConstraintService.sha256(config.bytes()));
        for(var condition:engine.system().model().prePostConditions())compiled.nativeExpression("COMPILED:"+condition,condition.isPre()?"PRE":"POST",condition.cls(),condition.expression());
        for(var node:config.requirements().nodes()) {
            var self=engine.system().model().getClass(CslUseBinding.typeName(config,node.binding().selfTypeId()));requirementTypes.put(node.id(),self.name());
            activations.put(node.id(),compiled.predicate(node.id()+":activation","ACTIVATION",self,node.activation()).expression());
            if(!node.condition().isBlank())predicates.put(node.id(),compiled.predicate(node.id(),"GOAL_CONDITION",self,node.condition()).expression());
        }
    }
    public void lost(long version,String reason) {
        rememberNormGap();freshContexts.clear();var inputs=new TreeMap<String,TemporalGoalMonitor.Input>();for(var node:config.requirements().nodes())inputs.put(node.id(),new TemporalGoalMonitor.Input(SemanticVerdict.SKIPPED,SemanticVerdict.SKIPPED,"",reason));monitor.evaluate(version,inputs,true);
    }
    private void rememberNormGap(){for(var object:engine.system().state().allObjects())if(object.cls().name().equals(org.tzi.use.plugins.jacamo.semantics.normative.NormativeUseBinding.TYPE))normHistoryGaps.add(engine.metadata(object,"semanticId"));}
    public java.util.List<CompiledSemanticConstraint> compiledConstraints(){return compiled.inventory();}
    public Savepoint savepoint(){return new Savepoint(monitor.savepoint(),Set.copyOf(freshContexts),observation,Set.copyOf(normHistoryGaps));}
    public void restore(Savepoint image){monitor.restore(image.monitor());freshContexts.clear();freshContexts.addAll(image.freshContexts());observation=image.observation();normHistoryGaps.clear();normHistoryGaps.addAll(image.normHistoryGaps());}
    public void reset(){monitor.clear();freshContexts.clear();normHistoryGaps.clear();observation=Map.of();}
    public void observedSnapshot(RuntimeSnapshot snapshot,String run,long generation) {
        var envelope=SemanticObservationEnvelope.from(snapshot,run,generation,config.profile().name(),ExternalOclConstraintService.sha256(config.bytes()));
        // Facts remain in the authoritative runtime journal; evidence carries their IDs/digests/scoped coverage without duplicating the payload.
        observation=SemanticJson.object(Map.of("version",envelope.version(),"run",run,"generation",generation,"revision",snapshot.modelRevision(),"profile",envelope.profile(),"configurationHash",envelope.configurationHash(),"snapshot",snapshot.snapshotId(),"capture",envelope.capture(),"scopes",SemanticJson.tree(envelope.scopes()),"sourceWatermarks",snapshot.endWatermarks().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().sequence()))));
    }
    public void observedEvent(RuntimeEvent event) {
        if(observation.isEmpty())return;
        var updated=new TreeMap<String,Object>(observation);var watermarks=new TreeMap<String,Object>();
        if(observation.get("sourceWatermarks") instanceof Map<?,?> values)values.forEach((k,v)->watermarks.put((String)k,v));watermarks.put(event.sourceId(),event.sourceSequence());
        updated.put("sourceWatermarks",watermarks);updated.put("lastEvent",Map.of("id",event.eventId(),"source",event.sourceId(),"sequence",event.sourceSequence(),"kind",event.kind().name(),"digest",SemanticJson.hash(ContractPayloads.event(event))));updated.put("scopeEvidencePolicy","LAST_VALIDATED_SNAPSHOT_PLUS_ACCEPTED_SOURCE_PREFIX");observation=SemanticJson.object(updated);
    }
    public void observed(List<RuntimeFact> facts,boolean replacement) {
        if(replacement)freshContexts.clear();
        for(var fact:facts)if(fact.kind()==RuntimeFactKind.NORM_INSTANCE) {
            freshContexts.remove(fact.id().canonical());var object=engine.objectForSemanticId(fact.id().canonical());
            if(object!=null && fact.completeness()==Completeness.COMPLETE && "BOUND_PUBLIC_CREATED_ANNOTATION".equals(fact.values().get("activationIdentityStatus")) && SemanticJson.hash(fact.values()).equals(engine.metadata(object,"lastNormCut")))freshContexts.add(fact.id().canonical());
        }
        for(var fact:facts)if(fact.kind()==RuntimeFactKind.GROUP_BOARD || fact.kind()==RuntimeFactKind.SCHEME_BOARD || fact.kind()==RuntimeFactKind.AGENT) {
            freshContexts.remove(fact.id().canonical());
            if(fact.completeness()==Completeness.COMPLETE && fact.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY
                    && engine.objectForSemanticId(fact.id().canonical())!=null
                    && ExternalOclConstraintService.sha256(CanonicalJson.encode(fact.values())).equals(engine.metadata(engine.objectForSemanticId(fact.id().canonical()),fact.kind()==RuntimeFactKind.GROUP_BOARD?"lastGroupCut":fact.kind()==RuntimeFactKind.SCHEME_BOARD?"lastFunctionalCut":"lastAgentCut")))freshContexts.add(fact.id().canonical());
        }
    }
    public SemanticVerificationEnvelope evaluate(String boundary,long version,Map<String,Completeness> sources,boolean lost,
            List<SemanticConstraintResult> invocation) {
        var results=new ArrayList<SemanticConstraintResult>();
        var state=engine.system().state();
        var currentNorms=state.allObjects().stream().filter(o->o.cls().name().equals(org.tzi.use.plugins.jacamo.semantics.normative.NormativeUseBinding.TYPE)).map(o->engine.metadata(o,"semanticId")).collect(java.util.stream.Collectors.toSet());normHistoryGaps.retainAll(currentNorms);if(lost)normHistoryGaps.addAll(currentNorms);
        for(var inv:engine.system().model().classInvariants().stream().sorted(Comparator.comparing(org.tzi.use.uml.mm.MClassInvariant::qualifiedName)).toList()) {
            compiled.nativeExpression("NATIVE:"+inv.qualifiedName(),"INVARIANT",inv.cls(),inv.bodyExpression());
            results.addAll(new SemanticInvariantEvaluator().evaluate(inv,engine,sources,lost,freshContexts,version));
        }
        invocation=invocation.stream().map(r->new SemanticConstraintResult(r.id(),r.kind(),r.contextClass(),r.contextObject(),r.sourceIdentity(),r.expression(),
            lost || sources.get("cartago")!=Completeness.COMPLETE?SemanticVerdict.SKIPPED:r.verdict(),r.backendValue(),lost?"COVERAGE_LOST":sources.get("cartago")!=Completeness.COMPLETE?"REQUIRED_OPERATION_SOURCE_INCOMPLETE":r.diagnostic(),version,r.invocation(),r.witness())).toList();
        results.addAll(invocation);
        for(var object:state.allObjects().stream().filter(o->o.cls().name().equals(org.tzi.use.plugins.jacamo.semantics.observer.FunctionalUseBinding.SCHEME)).sorted(Comparator.comparing(MObject::name)).toList()) {
            String identity=engine.metadata(object,"semanticId"),error=engine.metadata(object,"executionDescriptorError");
            if(lost || !freshContexts.contains(identity))results.add(result("OPLAN:"+identity,"OPLAN_COVERAGE",object.cls().name(),object,"",OclEvaluationAdapter.Result.skipped("EXACT_FUNCTIONAL_BOARD_CUT_UNAVAILABLE"),version,""));
            else if(!error.isBlank())results.add(result("OPLAN:"+identity,"OPLAN_DESCRIPTOR",object.cls().name(),object,"",new OclEvaluationAdapter.Result(SemanticVerdict.ERROR,"",error),version,""));
            else {
                var raw=CanonicalJson.object(CanonicalJson.decode(engine.metadata(object,"functionalSource").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                var cut=org.tzi.use.plugins.jacamo.semantics.observer.FunctionalObservation.decode(BridgeEntityId.parse(identity),raw);
                var descriptor=org.tzi.use.plugins.jacamo.semantics.model.ExecutionDescriptor.from(cut.definition());
                new OPlanTransitionMonitor().checkCut(descriptor,cut).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{var check=entry.getValue();results.add(new SemanticConstraintResult("OPLAN:"+identity+":"+entry.getKey(),entry.getKey().equals("execution-history")?"OPLAN_HISTORY":"OPLAN_ENABLEDNESS",object.cls().name(),object.name(),identity,"MOISE dep(and/or)",check.verdict(),"",check.diagnostic(),version,"",check.witness()));});
            }
        }
        for(var object:state.allObjects().stream().filter(o->o.cls().name().equals(org.tzi.use.plugins.jacamo.semantics.normative.NormativeUseBinding.TYPE)).sorted(Comparator.comparing(MObject::name)).toList()) {
            String identity=engine.metadata(object,"semanticId"),reported=engine.metadata(object,"normReportedState");boolean available=!lost && freshContexts.contains(identity);
            var status=!available?SemanticVerdict.SKIPPED:reported.equals("UNFULFILLED")?SemanticVerdict.FAIL:reported.equals("FULFILLED")?SemanticVerdict.PASS:SemanticVerdict.INCONCLUSIVE;
            results.add(result("NPL_REPORTED:"+identity,"NORM_REPORTED_STATE",object.cls().name(),object,"SOURCE_REPORTED_LIFECYCLE",new OclEvaluationAdapter.Result(status,reported,available?"SOURCE_REPORTED_ONLY_NO_INDEPENDENT_DEONTIC_PROOF":"EXACT_NPL_OBSERVATION_UNAVAILABLE"),version,""));
            results.add(result("NPL_LIFECYCLE:"+identity,"NORM_LIFECYCLE_TRACE_CONSISTENCY",object.cls().name(),object,"SOURCE_REPORTED_TRANSITION",new OclEvaluationAdapter.Result(available?normHistoryGaps.contains(identity) && !"ERROR".equals(engine.metadata(object,"normLifecycleVerdict"))?SemanticVerdict.INCONCLUSIVE:SemanticVerdict.valueOf(engine.metadata(object,"normLifecycleVerdict")):SemanticVerdict.SKIPPED,"",normHistoryGaps.contains(identity)?"NORM_LIFECYCLE_HISTORY_GAP_NOT_REPAIRED_BY_CURRENT_RESYNC":engine.metadata(object,"normLifecycleDiagnostic")),version,""));
            results.add(result("NPL_DERIVATION:"+identity,"NORM_DERIVATION",object.cls().name(),object,"NPL condition/aim/deadline",new OclEvaluationAdapter.Result(SemanticVerdict.INCONCLUSIVE,"","INDEPENDENT_NORMATIVE_EQUIVALENCE_UNSUPPORTED; recorded clock only"),version,""));
        }
        var inputs=new LinkedHashMap<String,TemporalGoalMonitor.Input>();
        for(var node:config.requirements().nodes()) {
            var binding=resolver.resolve(engine,node.binding(),freshContexts);
            var activation=binding.available()?predicate(activations.get(node.id()),binding.self(),sources,lost):OclEvaluationAdapter.Result.skipped(binding.diagnostic());
            OclEvaluationAdapter.Result condition;
            if(node.kind()==RequirementModel.Kind.TASK && config.jasonTasks().stream().anyMatch(t->t.targetContract().equals(node.contractId()) && t.occurrence().equals(node.binding()))) {
                condition=new OclEvaluationAdapter.Result(SemanticVerdict.UNSUPPORTED,"","JASON_ACTION_TO_CARTAGO_INVOCATION_CAUSATION_UNAVAILABLE");
            } else if(node.kind()==RequirementModel.Kind.TASK) {
                var matches=invocation.stream().filter(r->r.kind().equals("TASK_CONFORMANCE") && r.id().equals(node.contractId()) && binding.self()!=null && r.contextObject().equals(binding.self().name())).toList();
                if(!binding.available())condition=OclEvaluationAdapter.Result.skipped(binding.diagnostic());
                else {
                    var attributed=matches.stream().filter(r->{
                        String performer=r.witness().getOrDefault("performerSemanticId","");
                        if(performer.isBlank() || !"PINNED_OPID_PLAYER_CROSSWALK".equals(r.witness().get("performerAuthority"))
                                || engine.objectForSemanticId(performer)!=engine.objectForSemanticId(node.binding().playerIdentity()))return false;
                        var tokens=CanonicalJson.object(CanonicalJson.decode(r.witness().getOrDefault("taskBindingTokens","{}").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        return binding.token().equals(tokens.get(node.id()));
                    }).toList();
                    condition=attributed.size()==1?new OclEvaluationAdapter.Result(attributed.getFirst().verdict(),attributed.getFirst().backendValue(),attributed.getFirst().diagnostic())
                        :attributed.size()>1?new OclEvaluationAdapter.Result(SemanticVerdict.INCONCLUSIVE,"","MULTIPLE_MATCHING_TASK_INVOCATIONS")
                        :matches.stream().anyMatch(r->r.witness().getOrDefault("performerSemanticId","").isBlank())?new OclEvaluationAdapter.Result(SemanticVerdict.UNSUPPORTED,"","TASK_PERFORMER_IDENTITY_UNAVAILABLE")
                        :OclEvaluationAdapter.Result.skipped(matches.isEmpty()?"MATCHING_TASK_INVOCATION_UNAVAILABLE":"TASK_PERFORMER_OR_PRE_BINDING_MISMATCH");
                }
            } else condition=binding.available() && predicates.containsKey(node.id())?predicate(predicates.get(node.id()),binding.self(),sources,lost):OclEvaluationAdapter.Result.skipped(binding.available()?"STRUCTURAL_PROPAGATION":binding.diagnostic());
            inputs.put(node.id(),new TemporalGoalMonitor.Input(activation.verdict(),condition.verdict(),binding.token(),binding.available()?condition.diagnostic():binding.diagnostic()));
            results.add(result("REQUIREMENT:"+node.id(),node.kind().name(),bindingCache?requirementTypes.get(node.id()):CslUseBinding.typeName(config,node.binding().selfTypeId()),binding.self(),node.condition(),condition,version,""));
        }
        long propagationStarted=System.nanoTime();var marking=monitor.evaluate(version,inputs,lost);lastIntentionalNanos=System.nanoTime()-propagationStarted;
        var coverageEvidence=new TreeMap<String,String>();sources.forEach((k,v)->coverageEvidence.put(k,v.name()));coverageEvidence.put("history",lost?"GAP":"OBSERVED_BOUNDARIES");
        if(!engine.structureValid())results.add(new SemanticConstraintResult("NATIVE:STRUCTURE","STRUCTURE","","","","MSystemState.checkStructure",lost?SemanticVerdict.SKIPPED:sources.values().stream().allMatch(c->c==Completeness.COMPLETE)?SemanticVerdict.FAIL:SemanticVerdict.INCONCLUSIVE,"","OBSERVED_STRUCTURE_INVALID_POPULATION_SCOPE_MAY_BE_PARTIAL",version,"",Map.of()));
        var decisions=new TreeSet<String>();config.decisions().forEach(d->decisions.add(d.sourceId()+":"+d.code()+":"+d.explanation()));
        for(var object:state.allObjects())engine.objectMetadata(object).forEach((k,v)->{if(k.equals("continuity") && v.contains("UNPROVEN"))decisions.add(engine.metadata(object,"semanticId")+":CONTINUITY_UNPROVEN:"+v);
            if((k.startsWith("propertyDecision:") || k.equals("mentalDecision")) && v.startsWith("UNSUPPORTED:"))decisions.add(engine.metadata(object,"semanticId")+":"+k+":"+v);});
        for(var cls:engine.system().model().classes())if(cls.getAnnotation("CslDeclarationOwner")!=null)decisions.add("UNSUPPORTED:RUNTIME_DECLARATION_OWNERSHIP:"+cls.name());
        for(var association:engine.system().model().associations())if("UNSUPPORTED_COMPOSITION_DESTRUCTION".equals(association.getAnnotationValue("CslAssociation","lifecycle")))decisions.add("UNSUPPORTED:COMPOSITION_DESTRUCTION:"+association.name());
        var evidence=new TreeMap<String,Object>(observation);var mapping=bindingCache?staticProvenance:SemanticMappingAttachment.provenance(engine.system().model());if(!mapping.isEmpty())evidence.put("staticMapping",mapping);
        return new SemanticVerificationEnvelope("1.0",config.profile().name(),ExternalOclConstraintService.sha256(config.bytes()),boundary,results,marking,coverageEvidence,List.copyOf(decisions),evidence);
    }
    public SemanticVerificationEnvelope failure(String boundary,long version,String reason,SemanticVerificationEnvelope previous) {
        var results=new ArrayList<SemanticConstraintResult>();var markings=new TreeMap<String,IntentionalMarking>();var quality=new TreeMap<String,String>();var evidence=new TreeMap<String,Object>(observation);
        if(previous!=null){var stale=previous.failClosed(reason);for(var row:stale.constraints()){var witness=new TreeMap<String,String>(row.witness());witness.put("lastObservedVersion",Long.toString(row.stateVersion()));results.add(new SemanticConstraintResult(row.id(),row.kind(),row.contextClass(),row.contextObject(),row.sourceIdentity(),row.expression(),row.verdict(),row.backendValue(),reason,version,row.invocation(),witness));}markings.putAll(stale.requirements());quality.putAll(stale.coverage());}
        for(var node:config.requirements().nodes())markings.putIfAbsent(node.id(),new IntentionalMarking(node.id(),IntentionalMarking.Status.UNDETERMINED,IntentionalMarking.Status.UNDETERMINED,IntentionalMarking.Lifecycle.UNKNOWN,SemanticVerdict.SKIPPED,node.binding().episodeId(),"",-1,-1,true,reason));
        results.add(new SemanticConstraintResult("SEMANTIC:EVALUATION","EVALUATION_ERROR","","","","",SemanticVerdict.ERROR,"",reason,version,"",Map.of()));quality.put("history","GAP");quality.put("failure",reason);evidence.put("failedBoundary",boundary);
        return new SemanticVerificationEnvelope("1.0",config.profile().name(),ExternalOclConstraintService.sha256(config.bytes()),boundary,results,markings,quality,List.of("SEMANTIC_EVALUATION_FAILED_CLOSED"),evidence);
    }
    public Set<String> freshContexts() {return Set.copyOf(freshContexts);}
    private OclEvaluationAdapter.Result predicate(Expression expression,MObject self,Map<String,Completeness> sources,boolean lost) {
        String unavailable=coverage.unavailableSelf(expression,engine,self,sources,lost,freshContexts);if(!unavailable.isBlank())return OclEvaluationAdapter.Result.skipped(unavailable);
        var bindings=new VarBindings(engine.system().state());bindings.push("self",self.value());
        return evaluator.evaluate(expression,engine.system().state(),engine.system().state(),bindings);
    }
    private SemanticConstraintResult result(String id,String kind,String type,MObject object,String expression,OclEvaluationAdapter.Result value,long version,String invocation) {
        var witness=new TreeMap<String,String>();
        if(object!=null)for(var attribute:object.cls().allAttributes())witness.put(attribute.name(),object.state(engine.system().state()).attributeValue(attribute).toString());
        return new SemanticConstraintResult(id,kind,type,object==null?"":object.name(),object==null?"":engine.metadata(object,"semanticId"),expression,value.verdict(),value.backendValue(),value.diagnostic(),version,invocation,witness);
    }
}
