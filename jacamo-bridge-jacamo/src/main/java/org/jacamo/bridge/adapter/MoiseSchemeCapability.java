package org.jacamo.bridge.adapter;
import java.nio.file.Path;
import java.util.*;
import cartago.*;
import org.jacamo.bridge.contract.*;

/** Public source facts/history only. Freeze sets, administrative policy and goal credit belong to XML observers. */
public final class MoiseSchemeCapability implements ObservationCapabilityRegistry.Handle {
    public static final String PROVIDER="moise.scheme-lifecycle.v1",VERSION="MOISE_SCHEME_CUT/2.2";
    private final ObservationCapabilityRequest request;
    private final MoiseSchemeRead reader;
    private final String run,revision;private final long generation;
    private final Map<String,History> histories=new LinkedHashMap<>();private boolean closed;
    private record Invocation(String performer,String command,Map<String,String> before) { }
    private static final class History {
        boolean fromCreation;long sequence,birthSequence;boolean broken;
        final Map<String,String> states=new TreeMap<>();final Map<String,Map<String,String>> publication=new TreeMap<>();
        final Map<String,Invocation> invocations=new HashMap<>();
        final List<Map<String,Object>> transitions=new ArrayList<>(),administrativeEffects=new ArrayList<>();
        History(){ }
    }
    public MoiseSchemeCapability(ObservationCapabilityRequest request,CartagoEnvironment environment,ModelSnapshot source,Path root,String run,long generation)throws Exception {
        this.request=request;this.run=run;this.generation=generation;revision=source.modelRevision();
        if(!request.provider().equals(PROVIDER)||!Set.of("organization","scheme","sourceRef","sourceHash","sourcePath").containsAll(request.parameters().keySet()))throw new IllegalArgumentException("MOISE_CAPABILITY_REQUEST_UNSUPPORTED");
        ProviderSourcePin.require(request,root);
        reader=new MoiseSchemeRead(environment,source,text(request.parameters(),"organization"),text(request.parameters(),"scheme"),Objects.requireNonNull(root));
        if(request.parameters().containsKey("sourceHash")&&!reader.osHash.equals(text(request.parameters(),"sourceHash")))throw new IllegalArgumentException("MOISE_CAPABILITY_SOURCE_HASH_MISMATCH");
    }
    public Map<String,Object> capture(ArtifactId identity,String origin,long publicationTime,String invocation,AgentId performer,String command) {
        if(!identity.getArtifactType().equals(ora4mas.nopl.SchemeBoard.class.getName()))return Map.of();
        synchronized(this){if(closed)return unavailable(identity,"PROVIDER_CLOSED");}
        try{return reader.read(identity,raw->record(identity,origin,publicationTime,invocation,performer,command,raw));}
        catch(Exception|LinkageError failure){synchronized(this){var history=histories.get(identity.getId().toString());if(history!=null&&!origin.equals("SNAPSHOT"))history.broken=true;}return unavailable(identity,"INCOMPLETE:"+failure.getClass().getSimpleName()+":"+failure.getMessage());}
    }
    private synchronized Map<String,Object> record(ArtifactId identity,String origin,long time,String invocation,AgentId performer,String command,MoiseSchemeRead.Raw raw) {
        if(closed)return unavailable(identity,"PROVIDER_CLOSED");
        String key=identity.getId().toString();if(!histories.containsKey(key)&&histories.size()>=256)throw new IllegalArgumentException("MOISE_HISTORY_CAPACITY");
        var history=histories.computeIfAbsent(key,k->new History());
        // Artifact.init commits observable changes before Workspace emits artifactCreated.
        // The exact post-init creation callback establishes a public-state baseline;
        // it never retroactively certifies initialization transitions or their timestamps.
        boolean birth=origin.equals("ARTIFACT_CREATED");
        if(birth) {
            if(history.fromCreation)throw new IllegalArgumentException("MOISE_CREATION_BASELINE_ALREADY_OBSERVED");
            history.fromCreation=true;history.birthSequence=history.sequence+1;
            history.broken=history.transitions.size()>=512;
        }
        var current=new TreeMap<String,String>();raw.cut().goals().forEach(g->current.put(g.id(),g.state()));
        String player=performer==null?"":OfficialCartagoAdapter.agentId(reader.environment.getId().toString(),performer);
        if(origin.equals("OP_STARTED")&&!command.isBlank()&&!invocation.isBlank()&&!player.isBlank()) {
            if(history.invocations.size()>=256)throw new IllegalArgumentException("MOISE_INVOCATION_CAPACITY");
            if(history.invocations.putIfAbsent(invocation,new Invocation(player,command,Map.copyOf(current)))!=null)history.broken=true;
        }
        var effect=new LinkedHashMap<String,Object>();
        if(origin.equals("OP_SUCCEEDED")||origin.equals("OP_FAILED")) {
            var before=history.invocations.remove(invocation);
            if(before!=null&&before.command.equals(command)&&before.performer.equals(player)) {
                effect.put("invocation",invocation);effect.put("performer",player);effect.put("command",command);effect.put("outcome",origin);
                effect.put("beforeGoals",before.before);effect.put("afterGoals",Map.copyOf(current));effect.put("authority","CORRELATED_PUBLIC_SCHEMEBOARD_OPERATION_AND_STATE");
                effect.put("sequence",history.sequence+1);
                if(history.administrativeEffects.size()<256)history.administrativeEffects.add(Map.copyOf(effect));else history.broken=true;
            }else if(before!=null)history.broken=true;
        }
        var goals=new ArrayList<Map<String,Object>>();
        for(var goal:raw.cut().goals()) {
            String previous=history.states.getOrDefault(goal.id(),"");
            if(!birth&&!previous.isBlank()&&!previous.equals(goal.state())&&!origin.equals("PROPERTY_PUBLICATION"))history.broken=true;
            if(origin.equals("PROPERTY_PUBLICATION")&&!previous.isBlank()&&!previous.equals(goal.state())&&time>0)
                history.publication.computeIfAbsent(goal.id(),g->new TreeMap<>()).putIfAbsent(goal.state(),Long.toString(time));
            var row=new LinkedHashMap<String,Object>();row.put("name",goal.id());
            row.put("definition",reader.definition.goals().stream().filter(g->g.goalId().equals(goal.id())).findFirst().orElseThrow().metadata().semanticId());
            row.put("previousState",previous);row.put("state",goal.state());row.put("committed",goal.committed());row.put("achieved",goal.achieved());row.put("publicationTimes",history.publication.getOrDefault(goal.id(),Map.of()));
            if(birth||previous.isBlank()||!previous.equals(goal.state())) {
                if(history.transitions.size()>=512)history.broken=true;
                else {
                    var transition=new LinkedHashMap<String,Object>();
                    transition.put("id",AdapterEvidence.digest(CanonicalJson.encode(Map.of("artifactUuid",key,"sequence",history.sequence+1,"goal",goal.id(),"previous",previous,"state",goal.state()))));
                    transition.put("goal",goal.id());transition.put("definition",row.get("definition"));transition.put("previousState",previous);transition.put("state",goal.state());
                    transition.put("kind",birth?"CREATION_BASELINE":previous.isBlank()?"INITIAL_PUBLIC_STATE":"STATE_CHANGE");transition.put("sequence",history.sequence+1);transition.put("origin",origin);
                    transition.put("committed",goal.committed());transition.put("achieved",goal.achieved());transition.put("done",raw.done());transition.put("commitments",raw.commitments());
                    transition.put("commitmentBindings",raw.commitmentBindings());
                    transition.put("publicationEpochMillis",origin.equals("PROPERTY_PUBLICATION")&&time>0?Long.toString(time):"");
                    transition.put("timeAuthority","PUBLICATION_ONLY_INTERNAL_TRANSITION_TIME_UNAVAILABLE");transition.put("orderAuthority","SOURCE_CAPTURE_SEQUENCE_NO_INTERNAL_CAUSAL_ORDER");
                    history.transitions.add(JasonStructuredTerms.immutable(transition));
                }
            }
            goals.add(row);history.states.put(goal.id(),goal.state());
        }
        var result=new LinkedHashMap<String,Object>();result.put("version",VERSION);result.put("status","COMPLETE");result.put("sessionId",run);result.put("generation",generation);result.put("modelRevision",revision);
        result.put("schemeIdentity",new BridgeEntityId("moise","organisation","scheme-board",identity.getWorkspaceId().getName(),identity.getName(),run+":"+identity.getId()).canonical());
        result.put("artifactIdentity",OfficialCartagoAdapter.artifactId(reader.environment.getId().toString(),identity));result.put("artifactUuid",key);result.put("workspaceUuid",identity.getWorkspaceId().getUUID().toString());
        result.put("schemeDefinition",reader.definition.metadata().semanticId());result.put("sourceOsSha256",reader.osHash);result.put("moiseArtifactSha256",MoiseSchemeRead.MOISE_SHA);
        result.put("cartagoArtifactClassSha256",CartagoArtifactReadScope.ARTIFACT_SHA);result.put("coherence","PINNED_CARTAGO_TRYLOCK_PUBLIC_SCHEME_READ");result.put("lockAlreadyHeld",raw.held());
        result.put("origin",origin);result.put("sequence",++history.sequence);result.put("fromCreation",history.fromCreation);result.put("historyQuality",history.fromCreation&&!history.broken?"COMPLETE":"INCOMPLETE");
        result.put("birthSequence",history.birthSequence);result.put("historyMeaning","PUBLIC_STATE_PREFIX_SINCE_OBSERVED_POST_INIT_CREATION");
        result.put("timeAuthority","PUBLICATION_ONLY_INTERNAL_TRANSITION_TIME_UNAVAILABLE");result.put("clockIdentity",NplSourceClockObservation.clockIdentity(run,generation));result.put("publicationEpochMillis",time>0?Long.toString(time):"");
        result.put("goals",goals);result.put("done",raw.done());result.put("commitments",raw.commitments());result.put("administrativeEffect",effect);
        result.put("commitmentBindings",raw.commitmentBindings());
        result.put("transitions",List.copyOf(history.transitions));result.put("administrativeEffects",List.copyOf(history.administrativeEffects));
        result.put("cutHash",AdapterEvidence.digest(CanonicalJson.encode(result)));CanonicalJson.encode(result,128*1024);return JasonStructuredTerms.immutable(result);
    }
    public synchronized void retire(ArtifactId identity){histories.remove(identity.getId().toString());}
    @Override public synchronized Capability capability(){return new Capability(request.id(),closed?CapabilityStatus.UNAVAILABLE:CapabilityStatus.COMPLETE,List.of(reader.osHash,MoiseSchemeRead.MOISE_SHA),closed?"PROVIDER_CLOSED":"Public MOISE state; internal transition times remain unavailable");}
    @Override public synchronized void close(){closed=true;histories.clear();}
    private static String text(Map<String,Object> map,String name){if(!(map.get(name) instanceof String value)||value.isBlank())throw new IllegalArgumentException("MOISE_PARAMETER_REQUIRED:"+name);return value;}
    private static Map<String,Object> unavailable(ArtifactId identity,String diagnostic){return Map.of("version",VERSION,"status","INCOMPLETE","artifactUuid",identity.getId().toString(),"diagnostic",diagnostic);}
}
