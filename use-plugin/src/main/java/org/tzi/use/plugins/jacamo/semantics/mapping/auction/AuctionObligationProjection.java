package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.semantics.normative.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.value.*;

/** Archived source activations on the existing native writer. All latches and
 * raw evidence live in its metadata/savepoint, not an independent history. */
public final class AuctionObligationProjection {
    private final AuctionObligationDecoder decoder;private final String nplHash,monitorAssociation;
    private final AuctionSchemeObservationDecoder schemeDecoder;
    private record Scope(String schemeIdentity,String artifactIdentity,String organizationWorkspaceUuid,org.tzi.use.uml.sys.MObject monitor) { }
    public AuctionObligationProjection(SemanticProgram program,MModel model) {
        decoder=new AuctionObligationDecoder(program);schemeDecoder=new AuctionSchemeObservationDecoder(program);nplHash=program.casePlan().bundle().runtimeRequirements().get("sha256:npl-0.6.1.jar");
        monitorAssociation=new AuctionObserverUseBinding(program.casePlan().specification()).referenceAssociation("view-obligation","monitor");
        if(model.getAssociation(monitorAssociation)==null)throw new IllegalArgumentException("AUCTION_OBLIGATION_MONITOR_SCHEMA_UNAVAILABLE");
    }
    public List<AuctionRuntimeRules.Outcome> apply(Port port,AuctionArtifactBindingResolver.Run run,RuntimeSnapshot cut,List<AuctionArtifactBindingResolver.Binding> bindings)throws UseApiException {
        var results=new ArrayList<AuctionRuntimeRules.Outcome>();var clock=AuctionSourceClock.fromCut(cut,run.id(),run.generation(),run.modelRevision(),nplHash);
        for(var old:List.copyOf(port.objects()))if(old.cls().name().equals("AuctionObligation")&&"ACTIVE".equals(port.metadata(old,"normReportedState"))) {
            AuctionMonitorProjection.write(port,old,"endedAt",UndefinedValue.instance,"CURRENT_ACTIVATION_CUT_UNAVAILABLE");
            AuctionMonitorProjection.write(port,old,"unfulfilled",UndefinedValue.instance,"CURRENT_ACTIVATION_CUT_UNAVAILABLE");
        }
        for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.NORM_INSTANCE) {
            var observed=decoder.decode(fact.id(),fact.values(),run.id(),run.generation());
            if(observed==null) {
                var old=port.find(fact.id().canonical());if(old!=null&&old.cls().name().equals("AuctionObligation")) {
                    if("AMBIGUOUS_NORM_INSTANCE".equals(fact.values().get("activationIdentityStatus")))port.metadata(old,"auctionObligationIdentityAmbiguous","true");
                    for(var field:old.cls().allAttributes())AuctionMonitorProjection.write(port,old,field.name(),port.currentValue(old,field.name()),"NPL_CURRENT_ACTIVATION_IDENTITY_UNPROVEN");
                }
                continue;
            }
            var binding=scope(port,run,observed,bindings);if(binding==null) {
                results.add(new AuctionRuntimeRules.Outcome("OBSERVER_OBLIGATION","INCOMPLETE",observed.norm().identity(),null,"ACTIVATION_ARTIFACT_EPOCH_UNPROVEN"));continue;
            }
            var board=CanonicalJson.object(fact.values().get("boardObservationProof"));
            if(!binding.organizationWorkspaceUuid().equals(board.get("workspaceUuid")))throw new IllegalArgumentException("AUCTION_OBLIGATION_ORGANIZATION_WORKSPACE");
            var monitor=binding.monitor();String key=observed.norm().identity();
            if(!"true".equals(port.metadata(monitor,"auctionMonitorLive"))) {
                var now=clock.relative(clock.nowEpochMillis());boolean known=clock.quality()==Completeness.COMPLETE&&clock.clockIdentity().equals(observed.clock())&&now.value()!=null;
                AuctionMonitorProjection.write(port,monitor,"now",known?IntegerValue.valueOf(now.value()):UndefinedValue.instance,known?"":"NPL_SOURCE_TIME_OR_CLOCK_UNAVAILABLE");
                if(known)port.metadata(monitor,"auctionMonitorClock",json(clock.proof()));
            }
            if(port.find(key)==null&&port.objects().stream().filter(o->o.cls().name().equals("AuctionObligation")).count()>=8192)throw new IllegalArgumentException("AUCTION_OBLIGATION_ARCHIVE_CAPACITY");
            var object=port.upsert(key,"AuctionObligation","AuctionObligation");
            String originalArtifact=port.metadata(object,"auctionObligationArtifact");
            if(!originalArtifact.isBlank()&&!originalArtifact.equals(binding.artifactIdentity())) {
                if(observed.unfulfilled())port.metadata(object,"auctionObligationUnfulfilled","true");
                for(var field:object.cls().allAttributes())AuctionMonitorProjection.write(port,object,field.name(),port.currentValue(object,field.name()),"ACTIVATION_DOMAIN_BINDING_REPLACED");
                results.add(new AuctionRuntimeRules.Outcome("OBSERVER_OBLIGATION","INCOMPLETE",key,object,"ACTIVATION_DOMAIN_BINDING_REPLACED"));continue;
            }
            var previous=port.metadata(object,"normReportedState");var lifecycle=new NormLifecycleMonitor().observe(previous.isBlank()?null:NormativeObservation.State.valueOf(previous),observed.norm(),"true".equals(port.metadata(object,"normHistoryGap")));
            boolean conflict="ERROR".equals(port.metadata(object,"normLifecycleVerdict"))||lifecycle.verdict()==SemanticVerdict.ERROR||"true".equals(port.metadata(object,"auctionObligationIdentityAmbiguous"));
            String storedSubscription=port.metadata(object,"auctionObligationSubscription"),subscription=board.get("sourceSubscriptionNumber").toString();
            if(!storedSubscription.isBlank()&&!storedSubscription.equals(subscription))conflict=true;
            String priorTerminal=port.metadata(object,"auctionObligationFirstTerminalEpoch");Long ended=priorTerminal.isBlank()?observed.ended():Long.valueOf(priorTerminal);
            if(!priorTerminal.isBlank()&&observed.ended()!=null&&!priorTerminal.equals(observed.ended().toString()))conflict=true;
            if(ended!=null)port.metadata(object,"auctionObligationFirstTerminalEpoch",ended.toString());
            boolean unfulfilled=observed.unfulfilled()||"true".equals(port.metadata(object,"auctionObligationUnfulfilled"));
            boolean historyComplete=lifecycle.verdict()==SemanticVerdict.PASS&&!conflict;
            port.metadata(object,"authority","npl");port.metadata(object,"auctionObligationSource",json(fact.values()));port.metadata(object,"auctionObligationScheme",binding.schemeIdentity());port.metadata(object,"auctionObligationArtifact",binding.artifactIdentity());
            port.metadata(object,"auctionObligationRun",run.id()+":"+run.generation());port.metadata(object,"auctionObligationPlayerName",observed.player());port.metadata(object,"auctionObligationActivation",observed.norm().activation());
            port.metadata(object,"auctionObligationPlayerQuality",AuctionMonitorProjection.currentPlayers(port,cut,List.of(observed.player()))?"CURRENT_NATIVE_PLAYER":"CURRENT_PLAYER_UNAVAILABLE");
            port.metadata(object,"auctionObligationSubscription",subscription);port.metadata(object,"auctionObligationUnfulfilled",Boolean.toString(unfulfilled));
            port.metadata(object,"normReportedState",observed.norm().state().name());port.metadata(object,"normHistoryGap",Boolean.toString(!historyComplete));
            port.metadata(object,"normLifecycleVerdict",conflict?"ERROR":lifecycle.verdict().name());port.metadata(object,"normLifecycleDiagnostic",conflict?"NPL_STATE_OR_ENGINE_SUBSCRIPTION_CONFLICT":lifecycle.diagnostic());
            AuctionMonitorProjection.write(port,object,"goal",new StringValue(observed.goal()),conflict?"NPL_ACTIVATION_IDENTITY_OR_STATE_CONFLICT":"");
            port.connect(monitorAssociation,object,monitor);
            boolean time=observed.timeComplete()&&clock.quality()==Completeness.COMPLETE&&clock.clockIdentity().equals(observed.clock())&&!conflict;
            relative(port,object,"activatedAt",observed.created(),clock,time);relative(port,object,"deadlineAt",observed.deadline(),clock,time);
            if(ended==null&&observed.norm().state()==NormativeObservation.State.ACTIVE&&historyComplete)AuctionMonitorProjection.write(port,object,"endedAt",IntegerValue.valueOf(-1),"");
            else relative(port,object,"endedAt",ended,clock,time);
            if(unfulfilled||historyComplete)AuctionMonitorProjection.write(port,object,"unfulfilled",BooleanValue.get(unfulfilled),conflict?"NPL_ACTIVATION_IDENTITY_OR_STATE_CONFLICT":"");
            else AuctionMonitorProjection.write(port,object,"unfulfilled",UndefinedValue.instance,"ACTIVATION_HISTORY_UNPROVEN");
            // Preserve the actual arithmetic, including an upstream epoch
            // offset. Never replace created by deadline-duration for OCL.
            port.metadata(object,"auctionObligationRawTimes",json(Map.of("created",Objects.toString(observed.created(),""),"deadline",Objects.toString(observed.deadline(),""),"ended",Objects.toString(ended,""),"clock",observed.clock())));
            results.add(new AuctionRuntimeRules.Outcome("OBSERVER_OBLIGATION","MAPPED",key,object,conflict?"ERROR:NPL_STATE_OR_ENGINE_SUBSCRIPTION_CONFLICT":historyComplete?"SOURCE_ACTIVATION_HISTORY_OBSERVED":"INCOMPLETE:ACTIVATION_HISTORY_UNPROVEN"));
        }
        return List.copyOf(results);
    }
    private Scope scope(Port port,AuctionArtifactBindingResolver.Run run,AuctionObligationDecoder.Observed observed,List<AuctionArtifactBindingResolver.Binding> bindings) {
        var existing=port.find(observed.norm().identity());String original=existing==null?"":port.metadata(existing,"auctionObligationArtifact");
        var monitors=port.objects().stream().filter(o->o.cls().name().equals("AuctionMonitor")&&(run.id()+":"+run.generation()).equals(port.metadata(o,"auctionMonitorRun")))
                .filter(o->{var id=BridgeEntityId.parse(port.metadata(o,"auctionMonitorScheme"));return id.incarnation().equals(run.id()+":"+observed.norm().boardUuid())&&id.localId().equals(observed.scheme());}).toList();
        if(!original.isBlank()) {
            var historical=monitors.stream().filter(o->original.equals(port.metadata(o,"auctionMonitorArtifact"))).toList();if(historical.size()!=1)return null;var monitor=historical.getFirst();
            var raw=CanonicalJson.object(CanonicalJson.decode(port.metadata(monitor,"auctionMonitorSource").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var source=schemeDecoder.decode(raw,run.id(),run.generation(),run.modelRevision());
            if(!source.uuid().equals(observed.norm().boardUuid())||!source.scheme().equals(port.metadata(monitor,"auctionMonitorScheme")))throw new IllegalArgumentException("AUCTION_OBLIGATION_ARCHIVED_SCHEME_PROOF");
            return new Scope(source.scheme(),original,(String)source.raw().get("workspaceUuid"),monitor);
        }
        // With no accepted activation binding, another artifact incarnation in
        // this same scheme is not proof of which artifact the norm concerned.
        if(monitors.stream().anyMatch(o->"ARCHIVED".equals(port.metadata(o,"auctionMonitorLifecycle"))))return null;
        var candidates=bindings.stream().filter(b->b.schemeUuid().equals(observed.norm().boardUuid())&&BridgeEntityId.parse(b.schemeIdentity()).localId().equals(observed.scheme())).toList();if(candidates.size()!=1)return null;var binding=candidates.getFirst();
        var live=monitors.stream().filter(o->binding.artifactIdentity().equals(port.metadata(o,"auctionMonitorArtifact"))&&"true".equals(port.metadata(o,"auctionMonitorLive"))).toList();
        return live.size()==1?new Scope(binding.schemeIdentity(),binding.artifactIdentity(),binding.organizationWorkspaceUuid(),live.getFirst()):null;
    }
    private static void relative(Port port,org.tzi.use.uml.sys.MObject object,String field,Long epoch,AuctionSourceClock clock,boolean qualified)throws UseApiException {
        var value=clock.relative(epoch);AuctionMonitorProjection.write(port,object,field,qualified&&value.value()!=null?IntegerValue.valueOf(value.value()):UndefinedValue.instance,
                qualified&&value.value()!=null?"":qualified?value.diagnostic():"NPL_SOURCE_TIME_OR_CLOCK_UNAVAILABLE");
    }
    private static String json(Object value){return new String(CanonicalJson.encode(value),java.nio.charset.StandardCharsets.UTF_8);}
}
