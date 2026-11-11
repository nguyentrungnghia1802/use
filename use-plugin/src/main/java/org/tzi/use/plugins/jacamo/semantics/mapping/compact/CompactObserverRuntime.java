package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MObject;
import static org.tzi.use.plugins.jacamo.semantics.mapping.compact.CompactSourceCut.*;

/** Source reductions only. Native engines evaluate all authored conditions. */
public final class CompactObserverRuntime {
    private record Operations(ObservationCapabilityRequest request,OperationHistoryCutDecoder decoder) { }
    private final SemanticProgram program;
    private final CompactSourceCut source;
    private final List<Operations> operations;
    public CompactObserverRuntime(SemanticProgram program) {
        this.program=program;source=new CompactSourceCut(program);
        operations=program.compactPlan().capabilityRequests().stream().filter(r->r.provider().equals("cartago.operation-history.v1")).map(r->new Operations(r,new OperationHistoryCutDecoder(r))).toList();
    }
    public void apply(Port port,RuntimeSnapshot cut,String run,long generation,boolean physicalInventory) throws UseApiException {
        String epoch=run+":"+generation;var frames=source.frames(cut,run,generation);var schemes=source.schemes(cut,run,generation);
        var clock=org.tzi.use.plugins.jacamo.semantics.observer.SourceClock.fromCut(cut,run,generation,cut.modelRevision(),PinnedRuntimeClassProfiles.nplObservationContractHash());
        for(var view:program.compactPlan().specification().observerViews()) {
            var ref=SourceReference.parse(view.source());var live=new HashSet<String>();
            if(ref instanceof SourceReference.ArtifactRef selected)for(var artifact:cut.facts())if(artifact.kind()==RuntimeFactKind.ARTIFACT&&artifact.id().authority().equals("cartago")&&selected.className().equals(artifact.values().get("artifactTypeJavaClassName"))) {
                var workspace=workspace(cut,artifact);if(workspace==null)continue;
                var history=operations(artifact,workspace,cut,run,generation);String identity=artifact.id().canonical();live.add(identity);
                write(port,view,identity,workspace,artifact,null,history,frames,cut,epoch,faithful(artifact)&&faithful(workspace),physicalInventory,clock);
            }
            if(ref instanceof SourceReference.MoiseSchemeRef selected)for(var scheme:schemes)if(selected.schemeName().equals(scheme.publicState().definition().schemeId())&&selected.organization().equals(scheme.publicState().organization().name())&&scheme.workspace()!=null&&scheme.artifact()!=null) {
                String identity=scheme.board().id().canonical();live.add(identity);
                var history=operations(scheme.artifact(),scheme.workspace(),cut,run,generation);
                if(view.population().equals("NPL_GOAL_OBLIGATIONS/1.0")) {
                    live.remove(identity);
                    for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.NORM_INSTANCE&&fact.id().authority().equals("npl")&&fact.completeness()==Completeness.COMPLETE&&scheme.board().values().get("boardArtifactIdentity").equals(fact.values().get("boardArtifactIdentity"))) {
                        var evidence=new NplLifecycleCut(PinnedRuntimeClassProfiles.nplObservationContractHash()).decode(fact.id(),fact.values(),run,generation);
                        String goal=goal(evidence,scheme);if(goal==null)continue;
                        live.add(fact.id().canonical());writeNorm(port,view,fact,scheme,evidence,goal,clock,cut,epoch);
                    }
                }else write(port,view,identity,scheme.workspace(),scheme.artifact(),scheme,history,frames,cut,epoch,scheme.history().quality()==Completeness.COMPLETE,
                        MoisePopulationClosure.complete(cut,RuntimeFactKind.SCHEME_BOARD,run),clock);
            }
            boolean closed=view.population().equals("NPL_GOAL_OBLIGATIONS/1.0")?NplPopulationCut.closed(cut,"ora4mas.nopl.SchemeBoard"):ref instanceof SourceReference.ArtifactRef?physicalInventory:MoisePopulationClosure.complete(cut,RuntimeFactKind.SCHEME_BOARD,run);
            if(closed)for(var object:List.copyOf(port.objects()))if(program.hash().equals(port.metadata(object,"compact:program"))&&epoch.equals(port.metadata(object,"compact:epoch"))
                    &&view.id().equals(port.metadata(object,"compact:observer-view"))&&!live.contains(port.metadata(object,"compact:instance-source")))port.delete(port.metadata(object,"semanticId"));
        }
    }
    private void write(Port port,CompactMappingSpecification.ObserverView view,String physical,RuntimeFact workspace,RuntimeFact artifact,CompactSourceCut.Scheme scheme,
            OperationHistoryCutDecoder.Result history,List<CompactSourceCut.Frame> frames,RuntimeSnapshot cut,String epoch,boolean qualified,boolean inventory,org.tzi.use.plugins.jacamo.semantics.observer.SourceClock clock) throws UseApiException {
        String id="compact:observer:"+SemanticJson.hash(List.of(program.hash(),epoch,view.id(),physical));
        var object=port.upsert(id,view.name(),"observer");port.metadata(object,"semanticId",id);
        port.metadata(object,"compact:program",program.hash());port.metadata(object,"compact:epoch",epoch);port.metadata(object,"compact:kind","OBSERVER");
        port.metadata(object,"compact:source-owner",physical);port.metadata(object,"compact:observer-view",view.id());port.metadata(object,"compact:sourceRef",view.source());
        port.metadata(object,"compact:instance-source",physical);
        port.metadata(object,"compact:workspace",workspace.id().canonical());port.metadata(object,"compact:cut",cut.snapshotId());
        port.metadata(object,"compact:quality",qualified?"COMPLETE":"UNAVAILABLE");port.metadata(object,"compact:inventory",inventory?"COMPLETE":"UNPROVEN");
        for(var field:view.fields()) {
            var binding=CompactObserverBindings.parse(view,field);Value value=UndefinedValue.instance;
            if(qualified)value=binding.kind()==CompactObserverBindings.Kind.NOW?relative(clock,clock.nowEpochMillis()):value(binding,workspace,scheme,history,frames);
            if(binding.kind()==CompactObserverBindings.Kind.SELF) {
                String association=CompactObserverSchema.association(view,field);port.disconnect(association,object,null);
                if(qualified)port.connect(association,object,object);
                port.metadata(object,"compact:field-quality:"+field.name(),qualified?"COMPLETE":"UNAVAILABLE");continue;
            }
            port.value(object,field.name(),value);
            port.metadata(object,"compact:field-quality:"+field.name(),value.isUndefined()?"UNAVAILABLE":"COMPLETE");
            port.metadata(object,"compact:field-hash:"+field.name(),CanonicalObservationMappingBindings.valueHash(value));
        }
    }
    private String goal(NplLifecycleCut.Evidence evidence,CompactSourceCut.Scheme scheme) {
        if(!evidence.norm().modality().equals("OBLIGATION"))return null;
        try {
            var term=jason.asSyntax.ASSyntax.parseLiteral(text(evidence.norm().raw(),"aim"));
            if(!term.getFunctor().equals("done")||term.getArity()!=3||!term.isGround()||!term.getTerm(0).toString().equals(scheme.board().id().localId()))return null;
            String goal=term.getTerm(1).toString();
            return scheme.publicState().definition().goals().stream().anyMatch(g->g.goalId().equals(goal))?goal:null;
        }catch(Exception malformed){throw new IllegalArgumentException("COMPACT_NPL_AIM_SOURCE_TERM_UNSUPPORTED",malformed);}
    }
    private void writeNorm(Port port,CompactMappingSpecification.ObserverView view,RuntimeFact fact,CompactSourceCut.Scheme scheme,NplLifecycleCut.Evidence evidence,String goal,
            org.tzi.use.plugins.jacamo.semantics.observer.SourceClock clock,RuntimeSnapshot cut,String epoch)throws UseApiException {
        if(!evidence.clockIdentity().equals(clock.clockIdentity()))clock=org.tzi.use.plugins.jacamo.semantics.observer.SourceClock.unavailable();
        String id="compact:observer:"+SemanticJson.hash(List.of(program.hash(),epoch,view.id(),fact.id().canonical()));
        var object=port.upsert(id,view.name(),"activation");port.metadata(object,"semanticId",id);
        port.metadata(object,"compact:program",program.hash());port.metadata(object,"compact:epoch",epoch);port.metadata(object,"compact:kind","OBSERVER");
        port.metadata(object,"compact:observer-view",view.id());port.metadata(object,"compact:sourceRef",view.source());port.metadata(object,"compact:source-owner",scheme.board().id().canonical());
        port.metadata(object,"compact:activation",fact.id().canonical());port.metadata(object,"compact:workspace",scheme.workspace().id().canonical());port.metadata(object,"compact:cut",cut.snapshotId());
        port.metadata(object,"compact:instance-source",fact.id().canonical());
        String subscription=object(fact.values().get("boardObservationProof")).get("sourceSubscriptionNumber").toString();
        String previous=port.metadata(object,"compact:npl:subscription");
        if(!previous.isBlank()&&!previous.equals(subscription))throw new IllegalArgumentException("COMPACT_NPL_ACTIVATION_SUBSCRIPTION_CHANGED");
        port.metadata(object,"compact:npl:subscription",subscription);
        if(Boolean.TRUE.equals(evidence.reportedUnfulfilled()))port.metadata(object,"compact:npl:failed","true");
        port.metadata(object,"compact:quality","COMPLETE");port.metadata(object,"compact:inventory",NplPopulationCut.closed(cut,"ora4mas.nopl.SchemeBoard")?"COMPLETE":"UNPROVEN");
        for(var field:view.fields()) {
            var origin=CompactObserverBindings.parse(view,field);Value value=UndefinedValue.instance;
            switch(origin.kind()) {
                case NPL_GOAL->value=new StringValue(goal);
                case NPL_ACTIVATED->value=relative(clock,evidence.createdEpochMillis());
                case NPL_DEADLINE->value=relative(clock,evidence.deadlineEpochMillis());
                case NPL_ENDED->value=evidence.norm().state()==org.tzi.use.plugins.jacamo.semantics.normative.NormativeObservation.State.ACTIVE&&evidence.timeComplete()?IntegerValue.valueOf(-1):relative(clock,evidence.terminalEpochMillis());
                case NPL_UNFULFILLED->{if("true".equals(port.metadata(object,"compact:npl:failed")))value=BooleanValue.TRUE;else if(evidence.reportedUnfulfilled()!=null)value=BooleanValue.get(evidence.reportedUnfulfilled());}
                case NPL_MONITOR->{
                    var target=program.compactPlan().specification().observerViews().stream().filter(v->v.id().equals(origin.arguments().getFirst())).findFirst().orElseThrow();
                    String targetId="compact:observer:"+SemanticJson.hash(List.of(program.hash(),epoch,target.id(),scheme.board().id().canonical()));
                    var monitor=port.upsert(targetId,target.name(),"observer");String association=CompactObserverSchema.association(view,field);port.disconnect(association,object,null);port.connect(association,object,monitor);
                    port.metadata(object,"compact:field-quality:"+field.name(),"COMPLETE");continue;
                }
                default->throw field.location().error("COMPACT_NPL_FIELD_SOURCE_UNSUPPORTED");
            }
            port.value(object,field.name(),value);port.metadata(object,"compact:field-quality:"+field.name(),value.isUndefined()?"UNAVAILABLE":"COMPLETE");
            port.metadata(object,"compact:field-hash:"+field.name(),CanonicalObservationMappingBindings.valueHash(value));
        }
    }
    private static Value relative(org.tzi.use.plugins.jacamo.semantics.observer.SourceClock clock,Long raw) {
        if(clock.quality()!=Completeness.COMPLETE)return UndefinedValue.instance;var value=clock.relative(raw).value();return value==null?UndefinedValue.instance:IntegerValue.valueOf(value);
    }
    private Value value(CompactObserverBindings.Binding origin,RuntimeFact workspace,CompactSourceCut.Scheme scheme,OperationHistoryCutDecoder.Result operations,List<CompactSourceCut.Frame> frames) {
        var args=origin.arguments();
        if(origin.kind()==CompactObserverBindings.Kind.WORKSPACE)return new StringValue(text(workspace.values(),"fullName"));
        if(origin.kind()==CompactObserverBindings.Kind.FIRST_SUCCESS) {
            if(operations.quality()!=Completeness.COMPLETE||!Boolean.TRUE.equals(operations.values().get("continuous")))return UndefinedValue.instance;
            var sequence=rows(operations.values().get("events")).stream().filter(e->e.get("operation").equals(args.getFirst())&&e.get("outcome").equals("SUCCEEDED")&&Boolean.TRUE.equals(e.get("correlated")))
                    .map(e->((Number)e.get("sourceSequence")).longValue()).min(Long::compareTo).orElse(-1L);
            return sequence(sequence);
        }
        if(scheme==null||scheme.history().quality()!=Completeness.COMPLETE)return UndefinedValue.instance;
        var data=scheme.history().values();
        boolean continuous=Boolean.TRUE.equals(data.get("continuous"));
        switch(origin.kind()) {
            case FIRST_STATE,COMMITTED_AT_FIRST_STATE -> {
                if(!continuous)return UndefinedValue.instance;
                var first=rows(data.get("transitions")).stream().filter(t->args.get(0).equals(t.get("goal"))&&args.get(1).equals(t.get("state"))).min(Comparator.comparingLong(t->((Number)t.get("sequence")).longValue()));
                if(origin.kind()==CompactObserverBindings.Kind.FIRST_STATE)return sequence(first.map(t->((Number)t.get("sequence")).longValue()).orElse(-1L));
                if(first.isEmpty())return strings(Set.of());
                return players(first.get().get("committed"),object(first.get().get("commitmentBindings")));
            }
            case DONE_PLAYERS -> {
                if(!continuous)return UndefinedValue.instance;
                var names=new TreeSet<String>();for(var done:rows(data.get("done")))if(args.getFirst().equals(done.get("goal")))names.add(text(done,"player"));
                return players(names,object(data.get("commitmentBindings")));
            }
            case ACTION_PLAYERS -> {
                var players=new TreeSet<String>();
                for(var frame:frames)if(frame.step().scheme().equals(scheme.board().id().canonical())&&frame.step().task().equals(args.getFirst())&&!frame.step().artifact().isBlank())
                    for(var action:rows(frame.step().bindings().get("actions")))if(args.get(1).equals(action.get("operationName"))&&"SUCCEEDED".equals(action.get("result")))players.add(frame.step().agent());
                // An empty set is proved only for a continuous observed source prefix.
                if(players.isEmpty()&&!continuous)return UndefinedValue.instance;
                return strings(players);
            }
            case ADMIN_SATISFIED -> {
                for(var effect:rows(data.get("administrativeEffects")))if("OP_SUCCEEDED".equals(effect.get("outcome"))&&("goalSatisfied("+args.getFirst()+")").equals(text(effect,"command").replaceAll("\\s+",""))
                        &&"SATISFIED".equals(object(effect.get("afterGoals")).get(args.getFirst()))&&!"SATISFIED".equals(object(effect.get("beforeGoals")).get(args.getFirst())))return BooleanValue.TRUE;
                if(operations.quality()==Completeness.COMPLETE&&Boolean.TRUE.equals(operations.values().get("continuous")))return BooleanValue.FALSE;
                return UndefinedValue.instance;
            }
            default -> {return UndefinedValue.instance;}
        }
    }
    private OperationHistoryCutDecoder.Result operations(RuntimeFact artifact,RuntimeFact workspace,RuntimeSnapshot cut,String run,long generation) {
        var selected=operations.stream().filter(o->Objects.equals(o.request().parameters().get("class"),artifact.values().get("artifactTypeJavaClassName"))).toList();
        if(selected.size()>1)throw new IllegalArgumentException("COMPACT_OBSERVER_OPERATION_HISTORY_AMBIGUOUS");
        if(selected.isEmpty())return new OperationHistoryCutDecoder.Result(Completeness.UNAVAILABLE,Map.of(),"","NOT_SELECTED");
        var request=selected.getFirst();return request.decoder().decode(object(artifact.values().get(CapabilityObservation.KEY)).get(request.request().id()),artifact,workspace,cut,run,generation);
    }
    private Value players(Object raw,Map<String,Object> bindings) {
        if(!(raw instanceof Collection<?> names)||!Boolean.TRUE.equals(bindings.get("complete")))return UndefinedValue.instance;
        var result=new TreeSet<String>();
        for(var name:names) {
            var ids=rows(bindings.get("rows")).stream().filter(r->name.equals(r.get("agent"))&&"COMPLETE".equals(r.get("quality"))).map(r->text(r,"runtimePlayer")).distinct().toList();
            if(ids.size()!=1)return UndefinedValue.instance;result.add(ids.getFirst());
        }
        return strings(result);
    }
    private static Value sequence(long sequence){return sequence>=-1&&sequence<=Integer.MAX_VALUE?IntegerValue.valueOf((int)sequence):UndefinedValue.instance;}
    private static Value strings(Collection<String> values){return new SetValue(org.tzi.use.uml.ocl.type.TypeFactory.mkString(),values.stream().map(StringValue::new).toArray(Value[]::new));}
    private static List<Map<String,Object>> rows(Object raw){return raw instanceof List<?> values?values.stream().map(CanonicalJson::object).toList():List.of();}
}
