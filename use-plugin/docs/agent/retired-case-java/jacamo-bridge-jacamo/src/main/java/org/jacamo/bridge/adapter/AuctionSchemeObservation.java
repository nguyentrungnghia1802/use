package org.jacamo.bridge.adapter;

import cartago.*;
import java.util.*;
import ora4mas.nopl.SchemeBoard;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.*;

/** Producer-only passive SchemeBoard history. Uses actual public goalState,
 * getDoneGoals/getPlayers and correlated real operations. No goals are set by
 * this observer. Publication time is explicitly not an internal engine time. */
public final class AuctionSchemeObservation implements AutoCloseable {
    public static final String PROPERTY="jacamo.bridge.auction.scheme-observer",VERSION="AUCTION_SCHEME_CUT/1.0";
    private static final String MOISE_SHA=MoiseSchemeRead.MOISE_SHA;
    private final MoiseSchemeRead reader;
    public static final String TIME_AUTHORITY="ICARTAGOLOGGER_SYSTEM_EPOCH_PUBLICATION_NOT_MOISE_INTERNAL_TRANSITION";
    private final CartagoEnvironment environment;private final String run,revision,osHash;private final long generation;private final SchemeSemantic definition;
    private final Map<String,History> histories=new LinkedHashMap<>();private boolean closed;
    private static final class History {
        final boolean fromCreation;long sequence;boolean broken,admin;List<String> frozen;Map<String,Object> adminProof=Map.of();
        final Map<String,Map<String,String>> publication=new TreeMap<>();final Map<String,String> states=new TreeMap<>();
        final Map<String,Admin> administrative=new HashMap<>();
        History(boolean created){fromCreation=created;}
    }
    private record Admin(String before,String performer,String command) { }
    public AuctionSchemeObservation(CartagoEnvironment environment,ModelSnapshot source,String run,long generation) {
        this.environment=Objects.requireNonNull(environment);this.run=run;this.generation=generation;revision=source.modelRevision();
        reader=new MoiseSchemeRead(environment,source,"auction","doAuction");definition=reader.definition;osHash=reader.osHash;
    }
    public Map<String,Object> capture(ArtifactId identity,String origin,long publicationTime,String invocation,AgentId performer,String administrativeCommand) {
        if(!identity.getArtifactType().equals(SchemeBoard.class.getName()))return Map.of();
        synchronized(this){if(closed)return unavailable(identity,"SCHEME_OBSERVER_CLOSED");}
        try {return reader.read(identity,raw->record(identity,origin,publicationTime,invocation,performer,administrativeCommand,raw.cut(),raw.done(),raw.commitments(),raw.held()));}catch(Exception|LinkageError unavailable){synchronized(this){var history=histories.get(identity.getId().toString());if(history!=null&&!origin.equals("SNAPSHOT"))history.broken=true;}return unavailable(identity,unavailable.getClass().getSimpleName()+":"+unavailable.getMessage());}
    }
    private synchronized Map<String,Object> record(ArtifactId identity,String origin,long time,String invocation,AgentId performer,String command,
            MoiseGoalObservation.Cut cut,List<Map<String,Object>> done,List<Map<String,String>> commitments,boolean held) {
        if(closed)return unavailable(identity,"SCHEME_OBSERVER_CLOSED");String key=identity.getId().toString();
        if(!histories.containsKey(key)&&histories.size()>=256)throw new IllegalArgumentException("SCHEME_HISTORY_CAPACITY");
        var history=histories.computeIfAbsent(key,k->new History(origin.equals("ARTIFACT_CREATED")));
        var bid=cut.goals().stream().filter(g->g.id().equals("bid")).findFirst().orElseThrow();
        if(origin.equals("OP_STARTED")&&command.equals("goalSatisfied(bid)")&&!invocation.isBlank()&&performer!=null) {
            if(history.administrative.size()>=256)throw new IllegalArgumentException("SCHEME_INVOCATION_CAPACITY");
            history.administrative.put(invocation,new Admin(bid.state(),OfficialCartagoAdapter.agentId(environment.getId().toString(),performer),command));
        }
        var administrative=new LinkedHashMap<String,Object>();
        if(origin.equals("OP_SUCCEEDED")||origin.equals("OP_FAILED")) {
            var before=history.administrative.remove(invocation);
            if(before!=null) {
                boolean effect=origin.equals("OP_SUCCEEDED")&&before.command().equals(command)&&performer!=null
                        &&before.performer().equals(OfficialCartagoAdapter.agentId(environment.getId().toString(),performer))&&!before.before().equals("SATISFIED")&&bid.state().equals("SATISFIED");
                if(effect)history.admin=true;
                administrative.putAll(Map.of("invocation",invocation,"performer",before.performer(),"command",command,"before",before.before(),"after",bid.state(),"effect",effect,"authority","PINNED_SCHEMEBOARD_ADMCOMMAND_PERMISSION_AND_EFFECT"));
                if(effect)history.adminProof=Map.copyOf(administrative);
            }
        }
        var goals=new ArrayList<Map<String,Object>>();
        for(var goal:cut.goals()) {
            String previous=history.states.getOrDefault(goal.id(),"");
            if(previous.equals("SATISFIED")&&!goal.state().equals("SATISFIED"))history.broken=true;
            if(origin.equals("PROPERTY_PUBLICATION")&&!previous.isBlank()&&!previous.equals(goal.state())&&time>0)
                history.publication.computeIfAbsent(goal.id(),g->new TreeMap<>()).putIfAbsent(goal.state(),Long.toString(time));
            // If satisfaction is first seen in a poll/op callback, its first
            // property publication was missed and freeze/history is unproven.
            if(goal.id().equals("bid")&&goal.state().equals("SATISFIED")&&history.frozen==null&&history.fromCreation&&!previous.isBlank()) {
                if(origin.equals("PROPERTY_PUBLICATION"))history.frozen=List.copyOf(goal.committed());else history.broken=true;
            }
            var row=new LinkedHashMap<String,Object>();row.put("name",goal.id());row.put("definition",definition.goals().stream().filter(g->g.goalId().equals(goal.id())).findFirst().orElseThrow().metadata().semanticId());
            row.put("previousState",previous);row.put("state",goal.state());row.put("committed",goal.committed());row.put("achieved",goal.achieved());row.put("publicationTimes",history.publication.getOrDefault(goal.id(),Map.of()));goals.add(row);history.states.put(goal.id(),goal.state());
        }
        String scheme=new BridgeEntityId("moise","organisation","scheme-board",identity.getWorkspaceId().getName(),identity.getName(),run+":"+identity.getId()).canonical();
        var result=new LinkedHashMap<String,Object>();result.put("version",VERSION);result.put("status","COMPLETE");result.put("sessionId",run);result.put("generation",generation);result.put("modelRevision",revision);
        result.put("schemeIdentity",scheme);result.put("artifactIdentity",OfficialCartagoAdapter.artifactId(environment.getId().toString(),identity));result.put("artifactUuid",key);result.put("workspaceUuid",identity.getWorkspaceId().getUUID().toString());result.put("schemeDefinition",definition.metadata().semanticId());
        result.put("sourceOsSha256",osHash);result.put("moiseArtifactSha256",MOISE_SHA);result.put("cartagoArtifactClassSha256",CartagoArtifactReadScope.ARTIFACT_SHA);result.put("coherence","PINNED_CARTAGO_TRYLOCK_PUBLIC_SCHEME_READ");result.put("lockAlreadyHeld",held);
        result.put("origin",origin);result.put("sequence",++history.sequence);result.put("fromCreation",history.fromCreation);result.put("historyQuality",history.fromCreation&&!history.broken?"COMPLETE":"INCOMPLETE");
        result.put("timeAuthority","PUBLICATION_ONLY_INTERNAL_TRANSITION_TIME_UNAVAILABLE");result.put("clockIdentity",AuctionClockObservation.clockIdentity(run,generation));result.put("publicationEpochMillis",time>0?Long.toString(time):"");
        result.put("goals",goals);result.put("done",done);result.put("commitments",commitments);result.put("adminBidOverride",history.admin);result.put("administrativeEffect",history.admin?history.adminProof:administrative);
        if(history.frozen!=null)result.put("bidCommittedAtSatisfaction",history.frozen);
        result.put("cutHash",AdapterEvidence.digest(CanonicalJson.encode(result)));return JasonStructuredTerms.immutable(result);
    }
    public synchronized void retire(ArtifactId identity){retire(identity.getId().toString());}
    public synchronized void retire(String artifactUuid){histories.remove(artifactUuid);}
    private static Map<String,Object> unavailable(ArtifactId identity,String diagnostic){return Map.of("version",VERSION,"status","INCOMPLETE","artifactUuid",identity.getId().toString(),"diagnostic",diagnostic);}
    @Override public synchronized void close(){closed=true;histories.clear();}
}
