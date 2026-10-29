package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.semantics.observer.FunctionalObservation;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MObject;

/** Case-specific structural cast from one accepted native boundary. This does
 * not turn membership, a bid receipt or scheme satisfaction into Task truth. */
public final class AuctionOccurrenceBindingAdapter {
    public record Player(String sourceActor,String actorType,String instance,String role,String player,String name,String context,String roleInterval,String episodeAuthority) { }
    public record Scope(String program,String id,String scheme,String artifact,String context,String organization,List<Player> actors,String revision,List<String> unavailable) {
        public Scope {actors=List.copyOf(actors);unavailable=List.copyOf(unavailable);}
        public boolean complete(){return unavailable.isEmpty();}
    }
    private final SemanticProgram program;private final AuctionSchemeObservationDecoder decoder;
    public AuctionOccurrenceBindingAdapter(SemanticProgram program){this.program=program;decoder=new AuctionSchemeObservationDecoder(program);}
    public List<Scope> resolve(NativeRuntimeMutationEngine engine,AuctionArtifactBindingResolver.Run run) {
        program.assertUnchanged();if(!program.hash().equals(engine.canonicalProgramHash()))throw new IllegalArgumentException("AUCTION_CAST_PROGRAM_OWNER");
        var result=new ArrayList<Scope>();var objects=engine.system().state().allObjects();
        for(var monitor:objects.stream().filter(o->o.cls().name().equals("AuctionMonitor")).sorted(Comparator.comparing(o->engine.metadata(o,"semanticId"))).toList()) {
            var issues=new ArrayList<String>();var actors=new ArrayList<Player>();String id=engine.metadata(monitor,"semanticId"),scheme=engine.metadata(monitor,"auctionMonitorScheme"),artifact=engine.metadata(monitor,"auctionMonitorArtifact"),context="",organization="";
            if(!(run.id()+":"+run.generation()).equals(engine.metadata(monitor,"auctionMonitorRun")))continue;
            try {
                need(!"ARCHIVED".equals(engine.metadata(monitor,"auctionMonitorLifecycle")),"ARCHIVED");
                need("true".equals(engine.metadata(monitor,"auctionMonitorLive")),"SCHEME_CURRENT_BINDING_UNAVAILABLE");
                var source=decoder.decode(json(engine.metadata(monitor,"auctionMonitorSource")),run.id(),run.generation(),run.modelRevision());
                need(source.hash().equals(engine.metadata(monitor,"auctionMonitorCut"))&&scheme.equals(source.scheme()),"SOURCE_CUT");need(source.continuous(),"SCHEME_HISTORY_UNPROVEN");
                var functional=FunctionalObservation.decode(BridgeEntityId.parse(scheme),json(engine.metadata(monitor,"auctionMonitorFunctionalSource")));
                need(source.uuid().equals(functional.artifactUuid()),"FUNCTIONAL_SCHEME");
                var domain=engine.objectForSemanticId(artifact);need(domain!=null&&scheme.equals(engine.metadata(domain,"schemeIdentity"))&&"RESOLVED".equals(engine.metadata(domain,"auctionExistence")),"ARTIFACT_BINDING");
                var contexts=functional.responsibleContexts().stream().map(engine::objectForSemanticId).filter(Objects::nonNull).distinct().toList();
                need(!contexts.isEmpty()&&contexts.size()==functional.responsibleContexts().size(),"RESPONSIBLE_CONTEXT_UNAVAILABLE");
                for(var current:contexts)need("COMPLETE_OBSERVED_BOARD".equals(engine.metadata(current,"auctionPopulationQuality")),"CONTEXT_POPULATION_INCOMPLETE");
                var viewpoint=program.casePlan().actors().stream().filter(a->a.kind().equals("ORGANIZATION_VIEWPOINT")).findFirst().orElseThrow();
                var roots=objects.stream().filter(o->o.cls().name().equals(viewpoint.declaration())&&functional.organizationIdentity().equals(engine.metadata(o,"organizationDeclaration"))).toList();need(roots.size()==1,"ORGANIZATION_VIEWPOINT_AMBIGUOUS");
                need("COMPLETE_OBSERVED_ORGANIZATION".equals(engine.metadata(roots.getFirst(),"auctionOrganizationQuality")),"ORGANIZATION_VIEWPOINT_UNAVAILABLE");
                organization=engine.metadata(roots.getFirst(),"semanticId");
                actors.add(new Player(viewpoint.actor(),viewpoint.actor(),instance(run,scheme,artifact,viewpoint.actor(),organization),"",organization,"",organization,"","ORGANIZATION_VIEWPOINT_NOT_AGENT"));
                var bid=source.goal("bid");List<String> participants=bid.state().equals("SATISFIED")?source.frozenCommitted():bid.committed();need(participants!=null&&!participants.isEmpty(),"RELEVANT_COMMITMENTS_EMPTY_OR_UNPROVEN");
                var value=monitor.state(engine.system().state()).attributeValue("bidCommittedBy");need(value instanceof SetValue&&"COMPLETE".equals(engine.metadata(monitor,"auctionObserverQuality:bidCommittedBy"))
                        &&org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.valueHash(value).equals(engine.metadata(monitor,"auctionObserverHash:bidCommittedBy")),"COMMITMENT_BINDING_UNAVAILABLE");
                need(new TreeSet<>(participants).equals(new TreeSet<>(((SetValue)value).collection().stream().map(v->((StringValue)v).value()).toList())),"COMMITMENT_DIFFERS_FROM_SOURCE");
                for(var actor:program.casePlan().actors())if(actor.kind().equals("ROLE_OCCURRENCE")) {
                    boolean participant=actor.declaration().equals("participant");
                    var names=participant?participants:((List<?>)source.raw().get("commitments")).stream().map(CanonicalJson::object).filter(c->c.get("mission").equals("mAuctioneer")).map(c->(String)c.get("agent")).distinct().sorted().toList();
                    need(!names.isEmpty()&&(participant||names.size()==1),"EXACT_AUCTIONEER_COMMITMENT_REQUIRED");
                    for(String name:names) {
                        var players=objects.stream().filter(o->o.cls().name().equals("AtomicAgent")&&name.equals(engine.metadata(o,"name"))).toList();need(players.size()==1,"EXACT_PLAYER_UNAVAILABLE:"+name);
                        String player=engine.metadata(players.getFirst(),"semanticId");
                        var roles=objects.stream().filter(o->contexts.stream().anyMatch(c->engine.metadata(c,"semanticId").equals(engine.metadata(o,"roleContext")))&&actor.roleSourceId().equals(engine.metadata(o,"roleSourceId"))&&player.equals(engine.metadata(o,"rolePlayer"))&&"true".equals(engine.metadata(o,"auctionLiveEnactment"))).toList();
                        need(roles.size()==1,"EXACT_RELEVANT_ENACTMENT_REQUIRED:"+name+":"+roles.size());var role=roles.getFirst();String roleId=engine.metadata(role,"semanticId"),groupId=engine.metadata(role,"roleContext"),interval=engine.metadata(role,"auctionObservedRoleInterval");need(!interval.isBlank(),"OBSERVED_ROLE_INTERVAL_UNAVAILABLE");
                        need(link(engine,"Plays",players.getFirst(),role)&&contexts.stream().anyMatch(c->engine.metadata(c,"semanticId").equals(groupId)&&link(engine,"InContext",c,role)),"ENACTMENT_LINK_DIFFERS_FROM_ACCEPTED_SOURCE");
                        String type=participant?actor.actor()+"_"+SemanticJson.hash(List.of(roleId,interval)).substring(0,24):actor.actor();
                        actors.add(new Player(actor.actor(),type,instance(run,scheme,artifact,type,roleId),roleId,player,name,groupId,interval,engine.metadata(role,"auctionRoleIntervalAuthority")));
                        if(context.isBlank())context=groupId;
                    }
                }
            }catch(IllegalArgumentException unavailable){issues.add(unavailable.getMessage());}
            String revision=SemanticJson.hash(List.of(program.goalHash(),run.id(),run.generation(),scheme,artifact,SemanticJson.tree(actors)));
            result.add(new Scope(program.hash(),id,scheme,artifact,context,organization,actors,revision,issues));
        }
        return List.copyOf(result);
    }
    private static String instance(AuctionArtifactBindingResolver.Run run,String scheme,String artifact,String type,String owner){return "cast_"+SemanticJson.hash(List.of(run.id(),run.generation(),scheme,artifact,type,owner));}
    private static boolean link(NativeRuntimeMutationEngine engine,String name,MObject first,MObject second){return engine.system().state().allLinks().stream().anyMatch(l->l.association().name().equals(name)&&l.linkedObjects().equals(List.of(first,second)));}
    private static Map<String,Object> json(String text){return CanonicalJson.object(CanonicalJson.decode(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    private static void need(boolean value,String reason){if(!value)throw new IllegalArgumentException("AUCTION_CAST_"+reason);}
}
