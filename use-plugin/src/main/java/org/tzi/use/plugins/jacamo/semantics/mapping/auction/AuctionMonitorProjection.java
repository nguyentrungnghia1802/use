package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.semantics.observer.FunctionalObservation;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MObject;

/** Stateless observer-view writer on the existing native transaction/Port.
 * History/provenance is source evidence and native metadata, never a second
 * MSystem or a replacement goal evaluation algorithm. */
public final class AuctionMonitorProjection {
    private final AuctionSchemeObservationDecoder decoder;private final String nplHash;
    public AuctionMonitorProjection(SemanticProgram program){decoder=new AuctionSchemeObservationDecoder(program);nplHash=program.casePlan().bundle().runtimeRequirements().get("sha256:npl-0.6.1.jar");}
    public AuctionRuntimeRules.Outcome apply(Port port,AuctionArtifactBindingResolver.Binding binding,RuntimeSnapshot cut)throws UseApiException {
        var run=binding.run();String key="auction-monitor:"+SemanticJson.hash(List.of(run.id(),run.generation(),binding.schemeIdentity(),binding.artifactIdentity()));
        var physical=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&binding.schemeUuid().equals(f.values().get("uuid"))).toList();
        var candidates=new ArrayList<AuctionSchemeObservationDecoder.Cut>();boolean unavailable=false;
        for(var fact:physical)if(fact.values().get("auctionSchemeObservation") instanceof Map<?,?> raw) {
            var map=CanonicalJson.object(raw);if(!"COMPLETE".equals(map.get("status")))unavailable=true;else candidates.add(decoder.decode(map,run.id(),run.generation(),run.modelRevision()));
        }
        for(var fact:cut.facts())if(fact.values().containsKey(VerificationObservation.KEY)) {
            var observation=VerificationObservation.require(fact,cut,run.id(),run.generation());
            if(observation.kind()==VerificationObservation.Kind.GOAL_TRANSITION&&binding.schemeIdentity().equals(observation.data().get("boardIdentity")))candidates.add(decoder.decode(observation,run.id(),run.generation(),run.modelRevision()));
        }
        if(candidates.isEmpty()&&port.find(key)==null)return null; // Feature unavailable in old profiles; no fictitious baseline.
        var monitor=port.upsert(key,"AuctionMonitor","AuctionMonitor");
        port.metadata(monitor,"auctionMonitorScheme",binding.schemeIdentity());port.metadata(monitor,"auctionMonitorArtifact",binding.artifactIdentity());port.metadata(monitor,"auctionMonitorRun",run.id()+":"+run.generation());port.metadata(monitor,"auctionMonitorLive","true");port.metadata(monitor,"auctionMonitorLifecycle","LIVE");
        port.metadata(monitor,"auctionMonitorBoardUuid",binding.schemeUuid());
        for(var attribute:monitor.cls().allAttributes())write(port,monitor,attribute.name(),UndefinedValue.instance,"OBSERVER_CURRENT_CUT_UNAVAILABLE");
        if(unavailable||candidates.isEmpty())return new AuctionRuntimeRules.Outcome("OBSERVER_MONITOR","MAPPED",key,monitor,"INCOMPLETE:SCHEME_CURRENT_CUT_UNAVAILABLE");
        var selected=candidates.stream().max(Comparator.comparingLong(AuctionSchemeObservationDecoder.Cut::sequence)).orElseThrow();
        if(!selected.scheme().equals(binding.schemeIdentity())||!selected.uuid().equals(binding.schemeUuid())||physical.size()!=1||!selected.artifact().equals(physical.getFirst().values().get("semanticId")))throw new IllegalArgumentException("AUCTION_MONITOR_SCHEME_CROSSWALK");
        var functional=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.SCHEME_BOARD&&f.id().canonical().equals(binding.schemeIdentity())).findFirst().orElseThrow();
        var publicBoard=FunctionalObservation.decode(functional.id(),functional.values());if(!publicBoard.artifactUuid().equals(selected.uuid()))throw new IllegalArgumentException("AUCTION_MONITOR_FUNCTIONAL_IDENTITY");
        port.metadata(monitor,"auctionMonitorFunctionalSource",json(functional.values()));
        port.metadata(monitor,"auctionMonitorResponsibleContexts",json(publicBoard.responsibleContexts()));
        port.metadata(monitor,"auctionMonitorSource",json(selected.raw()));port.metadata(monitor,"auctionMonitorCut",selected.hash());
        port.metadata(monitor,"auctionMonitorHistoryQuality",selected.continuous()?"COMPLETE":"INCOMPLETE");
        AuctionTaskDemand.observe(port,monitor,selected);
        if(selected.continuous()&&currentPlayers(port,cut,selected.doneBy()))write(port,monitor,"bidDoneBy",set(selected.doneBy()),"");
        else write(port,monitor,"bidDoneBy",UndefinedValue.instance,"DONE_HISTORY_OR_CURRENT_PLAYER_UNAVAILABLE");
        var bid=selected.goal("bid");List<String> committed=bid.state().equals("SATISFIED")?selected.frozenCommitted():bid.committed();
        if(committed!=null&&currentPlayers(port,cut,committed))write(port,monitor,"bidCommittedBy",set(committed),"");
        else write(port,monitor,"bidCommittedBy",UndefinedValue.instance,"COMMITMENT_FREEZE_OR_CURRENT_PLAYER_UNAVAILABLE");
        if(selected.adminOverride()!=null) {
            if(Boolean.TRUE.equals(selected.adminOverride()))requireAdminReceipts(binding,selected,physical.getFirst().values().get("auctionOperationObservation"));
            write(port,monitor,"adminBidOverride",BooleanValue.get(selected.adminOverride()),"");
        }
        var artifact=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&binding.artifactUuid().equals(f.values().get("uuid"))).findFirst().orElseThrow();
        var actual=actualBids(port,binding,artifact.values().get("auctionOperationObservation"),cut);
        port.metadata(monitor,"auctionActualBidWitnesses",json(actual.witnesses()));
        if(actual.complete())write(port,monitor,"actualBidBy",set(actual.names()),"");
        else write(port,monitor,"actualBidBy",UndefinedValue.instance,"ACTUAL_BID_HISTORY_OR_PLAYER_UNAVAILABLE");
        // The XML explicitly asks for internal MOISE transition time. A source
        // publication timestamp is preserved above, but cannot fill that field.
        for(var entry:Map.of("startSatisfiedAt","start:SATISFIED","bidEnabledAt","bid:ENABLED","bidSatisfiedAt","bid:SATISFIED","decideEnabledAt","decide:ENABLED","decideSatisfiedAt","decide:SATISFIED").entrySet()) {
            var parts=entry.getValue().split(":");var goal=selected.goal(parts[0]);
            boolean observedAbsent=selected.continuous()&&(parts[1].equals("SATISFIED")&&!goal.state().equals("SATISFIED")
                    ||parts[1].equals("ENABLED")&&goal.state().equals("WAITING")&&!goal.publicationTimes().containsKey("ENABLED"));
            write(port,monitor,entry.getKey(),observedAbsent?IntegerValue.valueOf(-1):UndefinedValue.instance,observedAbsent?"":"INTERNAL_TRANSITION_TIME_UNAVAILABLE");
        }
        var clock=AuctionSourceClock.fromCut(cut,run.id(),run.generation(),run.modelRevision(),nplHash);
        if(clock.clockIdentity().equals(selected.raw().get("clockIdentity"))) {
            var now=clock.relative(clock.nowEpochMillis());
            if(clock.quality()==Completeness.COMPLETE&&now.value()!=null)write(port,monitor,"now",IntegerValue.valueOf(now.value()),"");
            port.metadata(monitor,"auctionMonitorClock",json(clock.proof()));
        }
        remember(port,monitor);
        return new AuctionRuntimeRules.Outcome("OBSERVER_MONITOR","MAPPED",key,monitor,selected.continuous()?"SOURCE_HISTORY_OBSERVED_INTERNAL_TIMESTAMPS_UNAVAILABLE":"INCOMPLETE:SCHEME_HISTORY_UNPROVEN");
    }
    /** Observer history in the native metadata savepoint; never a live world copy. */
    private static void remember(Port port,MObject monitor) {
        var values=new TreeMap<String,Object>();String prior=port.metadata(monitor,"auctionMonitorLastKnownValues");if(!prior.isBlank())values.putAll(CanonicalJson.object(CanonicalJson.decode(prior.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        for(var field:monitor.cls().allAttributes())if("COMPLETE".equals(port.metadata(monitor,"auctionObserverQuality:"+field.name()))) {
            var value=port.currentValue(monitor,field.name());
            if(value instanceof IntegerValue integer)values.put(field.name(),Map.of("kind","INTEGER","value",integer.value()));
            else if(value instanceof BooleanValue bool)values.put(field.name(),Map.of("kind","BOOLEAN","value",bool.value()));
            else if(value instanceof SetValue set)values.put(field.name(),Map.of("kind","STRING_SET","value",set.collection().stream().map(v->((StringValue)v).value()).sorted().toList()));
        }
        port.metadata(monitor,"auctionMonitorLastKnownValues",json(values));port.metadata(monitor,"auctionMonitorLastKnownHash",SemanticJson.hash(values));
    }
    static void archive(Port port,MObject monitor,String revision)throws UseApiException {
        String stored=port.metadata(monitor,"auctionMonitorLastKnownValues");
        if(!stored.isBlank()) {
            var values=CanonicalJson.object(CanonicalJson.decode(stored.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            need(SemanticJson.hash(values).equals(port.metadata(monitor,"auctionMonitorLastKnownHash")),"ARCHIVE_HASH");
            for(var entry:values.entrySet()) {
                var row=CanonicalJson.object(entry.getValue());Value value=switch((String)row.get("kind")) {
                    case "INTEGER"->IntegerValue.valueOf(((Number)row.get("value")).intValue());
                    case "BOOLEAN"->BooleanValue.get((Boolean)row.get("value"));
                    case "STRING_SET"->set(((List<?>)row.get("value")).stream().map(v->(String)v).toList());
                    default->throw new IllegalArgumentException("AUCTION_MONITOR_ARCHIVE_VALUE_TYPE");
                };
                write(port,monitor,entry.getKey(),value,"ARCHIVED_LAST_KNOWN_NOT_CURRENT");
            }
        }
        port.metadata(monitor,"auctionMonitorLifecycle","ARCHIVED");port.metadata(monitor,"auctionMonitorLive","false");port.metadata(monitor,"auctionMonitorRetirement",revision);
    }
    private record Bids(List<String> names,boolean complete,List<Map<String,Object>> witnesses) { }
    private void requireAdminReceipts(AuctionArtifactBindingResolver.Binding binding,AuctionSchemeObservationDecoder.Cut selected,Object raw) {
        need(raw instanceof Map<?,?>,"ADMIN_OPERATION_HISTORY_UNAVAILABLE");var history=CanonicalJson.object(raw);var unsigned=new LinkedHashMap<>(history);Object hash=unsigned.remove("hash");need(SemanticJson.hash(unsigned).equals(hash),"ADMIN_OPERATION_HISTORY_HASH");
        var effect=CanonicalJson.object(selected.raw().get("administrativeEffect"));var receipts=((List<?>)history.get("events")).stream().map(CanonicalJson::object).map(ContractPayloads::event).filter(e->e.correlationId().equals(effect.get("invocation"))).toList();
        var started=receipts.stream().filter(e->e.kind()==RuntimeEventKind.STARTED).toList();var succeeded=receipts.stream().filter(e->e.kind()==RuntimeEventKind.SUCCEEDED).toList();need(started.size()==1&&succeeded.size()==1,"ADMIN_CORRELATION");
        for(var event:receipts)need(event.sessionId().equals(binding.run().id())&&event.generation()==binding.run().generation()&&event.modelRevision().equals(binding.run().modelRevision())&&event.entityId().incarnation().equals(binding.schemeUuid())
                &&selected.artifact().equals(event.after().get("artifactSemanticId"))&&effect.get("performer").equals(event.after().get("performerSemanticId"))&&"CARTAGO_OPID_AGENT_BODY_ID".equals(event.after().get("performerAuthority"))
                &&List.of("ora4mas.nopl.SchemeBoard","admCommand",List.of("java.lang.String"),"void").equals(event.after().get("operationSignature"))&&List.of("goalSatisfied(bid)").equals(event.after().get("argumentValues")),"ADMIN_INVOCATION_BINDING");
    }
    private Bids actualBids(Port port,AuctionArtifactBindingResolver.Binding binding,Object raw,RuntimeSnapshot cut) {
        if(!(raw instanceof Map<?,?>))return new Bids(List.of(),false,List.of());var map=CanonicalJson.object(raw);var run=binding.run();
        need("AUCTION_OPERATION_HISTORY/1.0".equals(map.get("version"))&&run.id().equals(map.get("sessionId"))&&run.generation()==((Number)map.get("generation")).longValue()&&run.modelRevision().equals(map.get("modelRevision"))&&binding.artifactUuid().equals(map.get("artifactUuid")),"OPERATION_HISTORY_SCOPE");
        var unsigned=new LinkedHashMap<>(map);Object hash=unsigned.remove("hash");need(SemanticJson.hash(unsigned).equals(hash),"OPERATION_HISTORY_HASH");
        need(map.get("events") instanceof List<?> events&&events.size()<=4096,"OPERATION_HISTORY_CAPACITY");
        var names=new TreeSet<String>();var witnesses=new ArrayList<Map<String,Object>>();boolean complete=Boolean.TRUE.equals(map.get("fromCreation"))&&"COMPLETE".equals(map.get("historyQuality"));var ids=new HashSet<String>();
        for(Object row:(List<?>)map.get("events")) {
            var event=ContractPayloads.event(CanonicalJson.object(row));need(ids.add(event.eventId()),"DUPLICATE_OPERATION_RECEIPT");
            need(event.sessionId().equals(run.id())&&event.generation()==run.generation()&&event.modelRevision().equals(run.modelRevision())&&event.subsystem().equals("cartago")&&event.entityId()!=null&&event.entityId().incarnation().equals(binding.artifactUuid())&&binding.artifactIdentity().equals(event.after().get("artifactSemanticId")),"OPERATION_RECEIPT_IDENTITY");
            if(event.kind()!=RuntimeEventKind.SUCCEEDED||!List.of("auction_env.AuctionArtifact","bid",List.of("double"),"void").equals(event.after().get("operationSignature")))continue;
            need(event.completeness()==Completeness.COMPLETE&&!event.correlationId().isBlank()&&event.correlationId().equals(event.after().get("opId"))&&"CARTAGO_OPID_AGENT_BODY_ID".equals(event.after().get("performerAuthority")),"BID_PERFORMER_OR_INVOCATION");
            String performer=Objects.toString(event.after().get("performerSemanticId"),""),name=Objects.toString(event.after().get("performerName"),"");var player=port.find(performer);
            if(player==null||name.isBlank()||!name.equals(port.metadata(player,"name"))||!currentPlayer(port,cut,player))complete=false;
            else names.add(name);witnesses.add(Map.of("eventId",event.eventId(),"invocation",event.correlationId(),"performer",performer,"name",name,"artifact",binding.artifactIdentity()));
        }
        return new Bids(List.copyOf(names),complete,List.copyOf(witnesses));
    }
    static boolean currentPlayer(Port port,RuntimeSnapshot cut,MObject expected) {
        if(expected==null||port.objects().stream().noneMatch(object->object==expected))return false;
        String name=port.metadata(expected,"name");if(name.isBlank()||!currentPlayers(port,cut,List.of(name)))return false;
        return cut.facts().stream().anyMatch(fact->fact.kind()==RuntimeFactKind.AGENT&&fact.completeness()==Completeness.COMPLETE&&name.equals(fact.values().get("name"))&&port.find(fact.id().canonical())==expected);
    }
    static boolean currentPlayers(Port port,RuntimeSnapshot cut,Collection<String> names) {
        for(String name:names) {
            var players=new HashSet<MObject>();
            for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.AGENT&&fact.completeness()==Completeness.COMPLETE&&name.equals(fact.values().get("name"))) {
                var player=port.find(fact.id().canonical());
                if(player!=null&&player.cls().name().equals("AtomicAgent")&&name.equals(port.metadata(player,"name")))players.add(player);
            }
            if(players.size()!=1)return false;
        }
        return true;
    }
    public static void write(Port port,MObject object,String field,Value value,String diagnostic)throws UseApiException {
        port.value(object,field,value);port.metadata(object,"auctionObserverQuality:"+field,diagnostic.isEmpty()?"COMPLETE":diagnostic);
        port.metadata(object,"auctionObserverHash:"+field,org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.valueHash(value));
    }
    private static SetValue set(Collection<String> names){return new SetValue(TypeFactory.mkString(),names.stream().map(StringValue::new).toArray(Value[]::new));}
    private static String json(Object value){return new String(CanonicalJson.encode(value),java.nio.charset.StandardCharsets.UTF_8);}
    private static void need(boolean condition,String reason){if(!condition)throw new IllegalArgumentException("AUCTION_MONITOR_"+reason);}
}
