package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.observer.FunctionalObservation;

/** Pinned public MOISE evidence, not goal credit, administrative policy or internal transition time. */
public final class MoiseSchemeCutDecoder {
    private static final String PROVIDER="moise.scheme-lifecycle.v1",VERSION="MOISE_SCHEME_CUT/2.3";
    private static final String MOISE_HASH="2b7d0415c5ec0bcf060a430c7d12753aa02bdf769172922d10ff2909870161a8",CARTAGO_HASH="67830177f2cb450876ac3b43aa90741cbc0b72079f5fcdc8320ff2f11b17c012";
    private static final Set<String> STATES=Set.of("WAITING","ENABLED","SATISFIED");
    private static final String TIME="PUBLICATION_ONLY_INTERNAL_TRANSITION_TIME_UNAVAILABLE";
    public record Result(Completeness quality,Map<String,Object> values,String cutId,String diagnostic) {
        public Result {values=SemanticJson.object(values);}
    }
    private final ObservationCapabilityRequest request;
    private final SchemeSemantic definition;
    private final String osHash,project;
    private final Set<String> declarations;
    private final MoiseFactQualification publicFacts;
    private final Map<String,OrganizationalGoalSemantic> goals;
    public boolean acceptsDefinition(Object id){return definition.metadata().semanticId().equals(id);}
    public MoiseSchemeCutDecoder(ModelSnapshot source,ObservationCapabilityRequest request) {
        this.request=request;publicFacts=new MoiseFactQualification(source);project=source.semanticContract().project().name();
        declarations=source.semanticContract().agentDeclarations().stream().map(a->a.metadata().semanticId()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if(!request.provider().equals(PROVIDER))throw invalid("PROVIDER_UNSUPPORTED");
        var organizations=source.semanticContract().moiseOrganizations().stream().filter(o->o.name().equals(request.parameters().get("organization"))).toList();
        if(organizations.size()!=1)throw invalid("SOURCE_ORGANIZATION_NOT_UNIQUE");
        var schemes=organizations.getFirst().functionalSpecification().schemes().stream().filter(s->s.schemeId().equals(request.parameters().get("scheme"))).toList();
        if(schemes.size()!=1)throw invalid("SOURCE_SCHEME_NOT_UNIQUE");definition=schemes.getFirst();
        osHash=organizations.getFirst().metadata().evidence().stream().map(e->e.sourceDigest()).distinct().reduce((a,b)->{if(!a.equals(b))throw invalid("SOURCE_OS_HASH_AMBIGUOUS");return a;}).orElseThrow();
        if(!osHash.matches("[0-9a-f]{64}")||request.parameters().containsKey("sourceHash")&&!osHash.equals(request.parameters().get("sourceHash")))throw invalid("REQUEST_SOURCE_OS_HASH_MISMATCH");
        var known=new LinkedHashMap<String,OrganizationalGoalSemantic>();definition.goals().forEach(g->known.put(g.goalId(),g));goals=Map.copyOf(known);
    }
    public Result decode(Object raw,RuntimeFact artifact,RuntimeFact workspace,RuntimeFact board,RuntimeSnapshot cut,String run,long generation) {
        try {
            var receipt=CapabilityObservation.require(raw,request);var scope=receipt.scope();
            need(receipt.quality()==Completeness.COMPLETE,"INCOMPLETE:"+receipt.diagnostic());
            need(run.equals(scope.run())&&generation==scope.generation()&&cut.modelRevision().equals(scope.revision())&&scope.sourceId().equals("cartago")
                &&new SourceWatermark("cartago",scope.sourceSequence()).equals(cut.endWatermarks().get("cartago")),"RECEIPT_SCOPE_MISMATCH");
            need(faithful(artifact,RuntimeFactKind.ARTIFACT,"cartago")&&faithful(workspace,RuntimeFactKind.WORKSPACE,"cartago")&&faithful(board,RuntimeFactKind.SCHEME_BOARD,"moise"),"ACTUAL_PUBLIC_OBJECT_UNAVAILABLE");
            need(cut.facts().contains(artifact)&&cut.facts().contains(workspace)&&cut.facts().contains(board),"OBJECT_NOT_IN_CUT");
            need("ora4mas.nopl.SchemeBoard".equals(artifact.values().get("artifactTypeJavaClassName"))&&Objects.equals(artifact.values().get("workspaceSemanticId"),workspace.values().get("semanticId"))
                &&artifact.id().incarnation().equals(artifact.values().get("uuid"))&&workspace.id().incarnation().equals(workspace.values().get("uuid"))&&artifact.id().incarnation().equals(board.values().get("boardArtifactIdentity")),"PHYSICAL_BOARD_CROSSWALK_MISMATCH");
            publicFacts.require(board,run);var publicBoard=FunctionalObservation.decode(board.id(),board.values());
            var data=receipt.data();boolean clockProof=VERSION.equals(data.get("version")),typed=clockProof||"MOISE_SCHEME_CUT/2.2".equals(data.get("version"));
            need(typed||"MOISE_SCHEME_CUT/2.1".equals(data.get("version")),"SOURCE_SCOPE_OR_VERSION");
            if(typed)need(data.get("commitmentBindings") instanceof Map<?,?>,"PLAYER_BINDING_OBJECT_REQUIRED");
            var expectedKeys=new HashSet<>(bindingKeys(typed,Set.of("version","status","sessionId","generation","modelRevision","schemeIdentity","artifactIdentity","artifactUuid","workspaceUuid","schemeDefinition","sourceOsSha256","moiseArtifactSha256","cartagoArtifactClassSha256","coherence","lockAlreadyHeld","origin","sequence","fromCreation","historyQuality","birthSequence","historyMeaning","timeAuthority","clockIdentity","publicationEpochMillis","goals","done","commitments","administrativeEffect","administrativeEffects","transitions","cutHash")));
            if(clockProof)expectedKeys.add("publicationClockProof");keys(data,expectedKeys);
            need("COMPLETE".equals(data.get("status"))&&run.equals(data.get("sessionId"))&&generation==integer(data.get("generation"))&&cut.modelRevision().equals(data.get("modelRevision")),"SOURCE_SCOPE_OR_VERSION");
            need(board.id().canonical().equals(data.get("schemeIdentity"))&&Objects.equals(artifact.values().get("semanticId"),data.get("artifactIdentity"))&&artifact.id().incarnation().equals(data.get("artifactUuid"))&&workspace.id().incarnation().equals(data.get("workspaceUuid")),"SOURCE_IDENTITY_MISMATCH");
            need(osHash.equals(data.get("sourceOsSha256"))&&MOISE_HASH.equals(data.get("moiseArtifactSha256"))&&CARTAGO_HASH.equals(data.get("cartagoArtifactClassSha256"))&&definition.metadata().semanticId().equals(data.get("schemeDefinition"))
                &&"PINNED_CARTAGO_TRYLOCK_PUBLIC_SCHEME_READ".equals(data.get("coherence"))&&data.get("lockAlreadyHeld") instanceof Boolean,"SOURCE_BINARY_OR_DEFINITION_MISMATCH");
            need(TIME.equals(data.get("timeAuthority"))&&("npl-system-wall:"+run+":"+generation).equals(data.get("clockIdentity")),"TIME_AUTHORITY_MISMATCH");
            epoch(text(data,"publicationEpochMillis"));
            var unsigned=new LinkedHashMap<>(data);String hash=text(unsigned,"cutHash");unsigned.remove("cutHash");need(hash.equals(SemanticJson.hash(unsigned)),"CUT_HASH_MISMATCH");
            long sequence=integer(data.get("sequence")),birth=integer(data.get("birthSequence"));need(sequence>0&&birth>=0&&birth<=sequence,"SEQUENCE_INVALID");
            need(data.get("fromCreation") instanceof Boolean&&Set.of("COMPLETE","INCOMPLETE").contains(data.get("historyQuality"))&&"PUBLIC_STATE_PREFIX_SINCE_OBSERVED_POST_INIT_CREATION".equals(data.get("historyMeaning")),"HISTORY_BASIS_INVALID");
            boolean continuous=Boolean.TRUE.equals(data.get("fromCreation"))&&data.get("historyQuality").equals("COMPLETE");
            need(!data.get("historyQuality").equals("COMPLETE")||continuous&&birth>0,"FALSE_HISTORY_COMPLETENESS");
            var states=new LinkedHashMap<String,String>();
            for(Object rawGoal:list(data.get("goals"),4096)) {
                var goal=CanonicalJson.object(rawGoal);keys(goal,Set.of("name","definition","previousState","state","committed","achieved","publicationTimes"));String name=text(goal,"name");requireGoal(goal,name);
                need(states.putIfAbsent(name,text(goal,"state"))==null,"DUPLICATE_GOAL");
                String previous=text(goal,"previousState");need(previous.isEmpty()||STATES.contains(previous),"PREVIOUS_STATE_UNSUPPORTED");
                var times=CanonicalJson.object(goal.get("publicationTimes"));for(var time:times.entrySet()){need(STATES.contains(time.getKey())&&time.getValue() instanceof String,"PUBLICATION_TIME_INVALID");epoch((String)time.getValue());}
                var actual=publicBoard.goals().stream().filter(g->g.definition().equals(goal.get("definition"))).findFirst().orElseThrow();
                need(actual.state().name().equals(goal.get("state")),"PUBLIC_GOAL_STATE_DIFFERS");
                requirePlayers(strings(goal.get("committed")),actual.committed(),workspace,cut);requirePlayers(strings(goal.get("achieved")),actual.achieved(),workspace,cut);
            }
            need(states.keySet().equals(goals.keySet()),"GOAL_POPULATION_INCOMPLETE");
            var transitions=new ArrayList<Map<String,Object>>();var chain=new HashMap<String,String>();var baselines=new HashSet<String>();var ids=new HashSet<String>();long prior=0;
            for(Object rawTransition:list(data.get("transitions"),512)) {
                var transition=new LinkedHashMap<>(CanonicalJson.object(rawTransition));
                if(typed)need(transition.get("commitmentBindings") instanceof Map<?,?>,"PLAYER_BINDING_OBJECT_REQUIRED");
                keys(transition,bindingKeys(typed,Set.of("id","goal","definition","previousState","state","kind","sequence","origin","committed","achieved","done","commitments","publicationEpochMillis","timeAuthority","orderAuthority")));
                String name=text(transition,"goal");requireGoal(transition,name);long number=integer(transition.get("sequence"));String previous=text(transition,"previousState");
                need(number>0&&number<=sequence&&number>=prior,"TRANSITION_CAPTURE_ORDER_INVALID");prior=number;
                need(previous.isEmpty()||STATES.contains(previous),"TRANSITION_PREVIOUS_STATE_INVALID");
                String expected=SemanticJson.hash(Map.of("artifactUuid",artifact.id().incarnation(),"sequence",number,"goal",name,"previous",previous,"state",transition.get("state")));
                need(expected.equals(transition.get("id"))&&ids.add(expected),"TRANSITION_HASH_OR_DUPLICATE");
                need(TIME.equals(transition.get("timeAuthority"))&&"SOURCE_CAPTURE_SEQUENCE_NO_INTERNAL_CAUSAL_ORDER".equals(transition.get("orderAuthority")),"TRANSITION_TIME_OR_ORDER_AUTHORITY");epoch(text(transition,"publicationEpochMillis"));
                done(transition.get("done"),board.id().localId());commitments(transition.get("commitments"));
                transition.put("commitmentBindings",bindings(typed?transition.get("commitmentBindings"):null,transition.get("commitments"),null,null));
                if(birth>0&&number>=birth) {
                    String kind=text(transition,"kind");
                    if(number==birth)need(kind.equals("CREATION_BASELINE")&&text(transition,"origin").equals("ARTIFACT_CREATED")&&baselines.add(name),"CREATION_BASELINE_MISMATCH");
                    else {
                        need(kind.equals("STATE_CHANGE")&&!previous.isEmpty()&&!previous.equals(transition.get("state")),"POST_CREATION_TRANSITION_INVALID");
                        if(continuous)need(previous.equals(chain.get(name))&&text(transition,"origin").equals("PROPERTY_PUBLICATION"),"CONTINUOUS_PUBLIC_CHAIN_BROKEN");
                    }
                    chain.put(name,text(transition,"state"));transitions.add(transition);
                }
            }
            if(continuous)need(baselines.equals(goals.keySet())&&chain.equals(states),"CONTINUOUS_BASELINE_OR_FINAL_STATE_MISMATCH");
            var effects=new ArrayList<Map<String,Object>>();var effectIds=new HashSet<String>();
            for(Object effect:list(data.get("administrativeEffects"),256)) {
                var value=CanonicalJson.object(effect);keys(value,Set.of("invocation","performer","command","outcome","beforeGoals","afterGoals","authority","sequence"));
                need(!text(value,"invocation").isBlank()&&effectIds.add(text(value,"invocation"))&&!text(value,"performer").isBlank()&&!text(value,"command").isBlank()
                    &&Set.of("OP_SUCCEEDED","OP_FAILED").contains(value.get("outcome"))&&"CORRELATED_PUBLIC_SCHEMEBOARD_OPERATION_AND_STATE".equals(value.get("authority")),"ADMINISTRATIVE_RECEIPT_MISMATCH");
                long number=integer(value.get("sequence"));need(number>0&&number<=sequence,"ADMINISTRATIVE_SEQUENCE_INVALID");stateMap(value.get("beforeGoals"));stateMap(value.get("afterGoals"));effects.add(value);
            }
            var latestEffect=CanonicalJson.object(data.get("administrativeEffect"));need(latestEffect.isEmpty()||effects.contains(latestEffect),"ADMINISTRATIVE_CURRENT_EFFECT_NOT_IN_PREFIX");
            var values=new LinkedHashMap<String,Object>();values.put("schemeIdentity",board.id().canonical());values.put("artifactIdentity",artifact.id().canonical());values.put("artifactSemanticId",artifact.values().get("semanticId"));
            values.put("continuous",continuous);values.put("birthSequence",birth);values.put("sequence",sequence);values.put("goals",data.get("goals"));values.put("done",done(data.get("done"),board.id().localId()));
            values.put("commitmentBindings",bindings(typed?data.get("commitmentBindings"):null,data.get("commitments"),workspace,cut));
            values.put("sourceVersion",data.get("version"));
            values.put("commitments",commitments(data.get("commitments")));values.put("transitions",transitions);values.put("administrativeEffects",effects);
            values.put("clockIdentity",data.get("clockIdentity"));values.put("publicationEpochMillis",data.get("publicationEpochMillis"));values.put("internalTransitionTimes","UNSUPPORTED");
            values.put("confirmation",Map.of("version",MoisePublicConfirmation.VERSION,"complete",clockProof&&MoisePublicConfirmation.clockQualified(data.get("publicationClockProof"))&&MoisePublicConfirmation.complete(continuous,transitions)));
            values.put("administrativeAbsenceCoverage","UNAVAILABLE"); // State history is not proof that every operation callback was observed.
            return new Result(Completeness.COMPLETE,values,hash,"");
        }catch(IllegalArgumentException|ContractException|NoSuchElementException unavailable){return new Result(Completeness.UNAVAILABLE,Map.of(),"",unavailable.getMessage());}
    }
    private void requireGoal(Map<String,Object> row,String name) {
        need(goals.containsKey(name)&&goals.get(name).metadata().semanticId().equals(row.get("definition"))&&STATES.contains(row.get("state")),"SOURCE_GOAL_OR_STATE_UNSUPPORTED");
        strings(row.get("committed"));strings(row.get("achieved"));
    }
    private Map<String,Object> bindings(Object raw,Object source,RuntimeFact workspace,RuntimeSnapshot cut) {
        if(raw==null)return Map.of("complete",false,"rows",List.of(),"authority","UNSUPPORTED_LEGACY_CAPTURE");
        var value=CanonicalJson.object(raw);keys(value,Set.of("complete","rows","authority"));
        need(value.get("complete") instanceof Boolean&&"BRIDGE_REGISTERED_AGENT_AT_SCHEME_CAPTURE".equals(value.get("authority")),"PLAYER_BINDING_AUTHORITY");
        var expected=commitments(source).stream().map(r->SemanticJson.hash(List.of(r.get("agent"),r.get("mission")))).collect(java.util.stream.Collectors.toSet());
        var seen=new HashSet<String>();boolean complete=true;
        for(Object entry:list(value.get("rows"),4096)) {
            var row=CanonicalJson.object(entry);keys(row,Set.of("agent","mission","sourcePlayer","runtimePlayer","quality"));
            String name=text(row,"agent"),mission=text(row,"mission"),sourcePlayer=text(row,"sourcePlayer"),runtime=text(row,"runtimePlayer");
            need(expected.contains(SemanticJson.hash(List.of(name,mission)))&&seen.add(SemanticJson.hash(List.of(name,mission))),"PLAYER_BINDING_COMMITMENT_TUPLE");
            if(row.get("quality").equals("COMPLETE")) {
                var id=BridgeEntityId.parse(runtime);
                need(id.authority().equals("jason")&&id.dimension().equals("agent")&&id.kind().equals("runtime-agent")&&id.scope().equals(project)&&id.localId().equals(name)&&!id.incarnation().isBlank(),"PLAYER_BINDING_INCARNATION");
                need(runtime.equals(sourcePlayer)||declarations.contains(sourcePlayer),"PLAYER_BINDING_DECLARATION");
                if(cut!=null) {
                    var actual=cut.facts().stream().filter(f->faithful(f,RuntimeFactKind.AGENT,"cartago")&&name.equals(f.values().get("name"))&&Objects.equals(f.values().get("workspaceSemanticId"),workspace.values().get("semanticId"))).toList();
                    need(actual.size()==1&&runtime.equals(actual.getFirst().values().get("agentRuntimeSemanticId"))&&(runtime.equals(sourcePlayer)||sourcePlayer.equals(actual.getFirst().values().get("agentDeclarationSemanticId"))),"CURRENT_PLAYER_BINDING_DIFFERS");
                }
            }else {need(row.get("quality").equals("INCOMPLETE")&&runtime.isEmpty()&&sourcePlayer.isEmpty(),"PLAYER_BINDING_UNKNOWN_MUST_NOT_GUESS");complete=false;}
        }
        need(seen.equals(expected)&&Boolean.valueOf(complete).equals(value.get("complete")),"PLAYER_BINDING_POPULATION");return value;
    }
    private static Set<String> bindingKeys(boolean typed,Set<String> original){if(!typed)return original;var keys=new HashSet<>(original);keys.add("commitmentBindings");return Set.copyOf(keys);}
    private void requirePlayers(List<String> names,List<String> actual,RuntimeFact workspace,RuntimeSnapshot cut) {
        need(names.size()==actual.size(),"PUBLIC_PLAYER_POPULATION_DIFFERS");var matched=new HashSet<String>();
        for(String name:names) {
            var candidates=cut.facts().stream().filter(f->faithful(f,RuntimeFactKind.AGENT,"cartago")&&name.equals(f.values().get("name"))&&Objects.equals(f.values().get("workspaceSemanticId"),workspace.values().get("semanticId"))).toList();
            need(candidates.size()==1,"ACTUAL_PLAYER_IDENTITY_UNAVAILABLE");var values=candidates.getFirst().values();
            var identifiers=actual.stream().filter(id->id.equals(values.get("agentDeclarationSemanticId"))||id.equals(values.get("agentRuntimeSemanticId"))).toList();
            need(identifiers.size()==1&&matched.add(identifiers.getFirst()),"PUBLIC_PLAYER_IDENTITY_DIFFERS");
        }
    }
    private List<Map<String,Object>> done(Object raw,String scheme) {
        var rows=new ArrayList<Map<String,Object>>();
        for(Object value:list(raw,4096)) {
            var term=CanonicalJson.object(value);need(Boolean.TRUE.equals(term.get("supported")),"DONE_TERM_UNSUPPORTED");var literal=CanonicalJson.object(term.get("key"));
            need("LITERAL".equals(literal.get("kind"))&&"done".equals(literal.get("functor"))&&Boolean.FALSE.equals(literal.get("negative")),"DONE_FACT_REQUIRED");
            var terms=list(literal.get("terms"),3);need(terms.size()==3&&scheme.equals(atom(terms.get(0))),"DONE_SCHEME_MISMATCH");String goal=atom(terms.get(1));need(goals.containsKey(goal),"DONE_GOAL_MISMATCH");
            rows.add(Map.of("scheme",scheme,"goal",goal,"player",atom(terms.get(2))));
        }
        return List.copyOf(rows);
    }
    private List<Map<String,Object>> commitments(Object raw) {
        var rows=new ArrayList<Map<String,Object>>();var seen=new HashSet<String>();
        for(Object value:list(raw,4096)) {
            var row=CanonicalJson.object(value);keys(row,Set.of("agent","mission"));String agent=text(row,"agent"),mission=text(row,"mission");
            need(!agent.isBlank()&&definition.missions().stream().anyMatch(m->m.missionId().equals(mission))&&seen.add(SemanticJson.hash(row)),"MISSION_COMMITMENT_INVALID");rows.add(row);
        }
        return List.copyOf(rows);
    }
    private void stateMap(Object raw){var states=CanonicalJson.object(raw);need(states.keySet().equals(goals.keySet())&&states.values().stream().allMatch(STATES::contains),"ADMINISTRATIVE_STATE_MAP_INVALID");}
    private static boolean faithful(RuntimeFact fact,RuntimeFactKind kind,String authority){return fact!=null&&fact.kind()==kind&&fact.id().authority().equals(authority)&&fact.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY&&fact.completeness()==Completeness.COMPLETE;}
    private static String atom(Object raw){var map=CanonicalJson.object(raw);need("LITERAL".equals(map.get("kind"))&&Boolean.FALSE.equals(map.get("negative"))&&list(map.get("terms"),0).isEmpty(),"ATOM_REQUIRED");return text(map,"functor");}
    private static List<String> strings(Object raw){var list=list(raw,4096).stream().map(v->{if(!(v instanceof String s)||s.isBlank())throw invalid("STRING_LIST_REQUIRED");return s;}).toList();need(new HashSet<>(list).size()==list.size(),"STRING_LIST_DUPLICATE");return list;}
    private static List<?> list(Object raw,int capacity){if(!(raw instanceof List<?> values)||values.size()>capacity)throw invalid("LIST_OR_CAPACITY_INVALID");return values;}
    private static void keys(Map<String,Object> map,Set<String> expected){need(map.keySet().equals(expected),"KEYS_UNSUPPORTED");}
    private static String text(Map<String,Object> map,String key){if(!(map.get(key) instanceof String value))throw invalid("TEXT_REQUIRED:"+key);return value;}
    private static long integer(Object value){try{if(!(value instanceof Number))throw invalid("INTEGER_REQUIRED");return new java.math.BigDecimal(value.toString()).longValueExact();}catch(ArithmeticException|NumberFormatException e){throw invalid("INTEGER_REQUIRED");}}
    private static void epoch(String value){if(!value.isEmpty())try{need(Long.parseLong(value)>0,"PUBLICATION_EPOCH_INVALID");}catch(NumberFormatException e){throw invalid("PUBLICATION_EPOCH_INVALID");}}
    private static void need(boolean valid,String reason){if(!valid)throw invalid(reason);}
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("MOISE_SCHEME_CUT:"+reason);}
}
