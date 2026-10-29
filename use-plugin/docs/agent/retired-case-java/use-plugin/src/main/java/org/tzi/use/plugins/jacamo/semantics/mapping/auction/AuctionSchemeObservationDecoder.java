package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;

/** Framework-free, frozen-source decoder. Reported source publication times
 * never gain internal MOISE clock authority at this boundary. */
public final class AuctionSchemeObservationDecoder {
    public record Goal(String name,String definition,String previous,String state,List<String> committed,List<String> achieved,Map<String,Object> publicationTimes) { }
    public record Cut(String scheme,String artifact,String uuid,long sequence,boolean continuous,Boolean adminOverride,
            List<String> doneBy,List<String> frozenCommitted,Map<String,Goal> goals,String hash,Map<String,Object> raw) {
        public Goal goal(String name){return Objects.requireNonNull(goals.get(name));}
    }
    private final SemanticProgram program;private final String osHash,moiseHash,revision,organization;private final SchemeSemantic definition;
    public AuctionSchemeObservationDecoder(SemanticProgram program) {
        if(program.casePlan()==null)throw invalid("CASE_REQUIRED");this.program=program;
        var source=ContractPayloads.model(CanonicalJson.object(CanonicalJson.decode(program.complement().assets().stream().filter(a->a.id().equals("official-model")).findFirst().orElseThrow().bytes())));
        revision=source.modelRevision();osHash=program.casePlan().bundle().require("src-os").sha256();moiseHash=program.casePlan().bundle().runtimeRequirements().get("sha256:moise-1.1.jar");
        organization=program.casePlan().selectors().require("sel-org").getFirst().values().get("instance");
        definition=source.semanticContract().moiseOrganizations().stream().filter(o->o.name().equals("auction")).findFirst().orElseThrow().functionalSpecification().schemes().stream().filter(s->s.schemeId().equals("doAuction")).findFirst().orElseThrow();
    }
    public Cut decode(Map<String,Object> raw,String run,long generation,String modelRevision) {
        program.assertUnchanged();need("AUCTION_SCHEME_CUT/1.0".equals(raw.get("version"))&&"COMPLETE".equals(raw.get("status")),"VERSION_OR_STATUS");
        need(run.equals(raw.get("sessionId"))&&generation==integer(raw.get("generation"))&&revision.equals(modelRevision)&&revision.equals(raw.get("modelRevision")),"EPOCH");
        need(osHash.equals(raw.get("sourceOsSha256"))&&moiseHash.equals(raw.get("moiseArtifactSha256"))&&"67830177f2cb450876ac3b43aa90741cbc0b72079f5fcdc8320ff2f11b17c012".equals(raw.get("cartagoArtifactClassSha256"))
                &&"PINNED_CARTAGO_TRYLOCK_PUBLIC_SCHEME_READ".equals(raw.get("coherence"))&&definition.metadata().semanticId().equals(raw.get("schemeDefinition")),"SOURCE_OR_BINARY");
        String scheme=text(raw,"schemeIdentity"),uuid=text(raw,"artifactUuid"),artifact=text(raw,"artifactIdentity");UUID.fromString(uuid);UUID.fromString(text(raw,"workspaceUuid"));var identity=BridgeEntityId.parse(scheme);
        need(identity.authority().equals("moise")&&identity.dimension().equals("organisation")&&identity.kind().equals("scheme-board")&&identity.scope().equals(organization)&&identity.incarnation().equals(run+":"+uuid),"IDENTITY");
        // Physical Artifact semantic identity is verified again against the
        // admitted Cartago fact by the projector/case crosswalk.
        need(artifact.startsWith("cartago:artifact:")&&artifact.endsWith(":"+uuid),"PHYSICAL_IDENTITY");
        need("PUBLICATION_ONLY_INTERNAL_TRANSITION_TIME_UNAVAILABLE".equals(raw.get("timeAuthority"))&&("npl-system-wall:"+run+":"+generation).equals(raw.get("clockIdentity")),"TIME_AUTHORITY");
        String publication=text(raw,"publicationEpochMillis");if(!publication.isEmpty())need(Long.parseLong(publication)>0,"PUBLICATION_TIME");
        need(raw.get("fromCreation") instanceof Boolean&&Set.of("COMPLETE","INCOMPLETE").contains(raw.get("historyQuality"))&&raw.get("adminBidOverride") instanceof Boolean,"HISTORY_QUALITY");
        boolean continuous=Boolean.TRUE.equals(raw.get("fromCreation"))&&raw.get("historyQuality").equals("COMPLETE");
        need(!raw.get("historyQuality").equals("COMPLETE")||continuous,"FALSE_HISTORY_COMPLETENESS");
        long sequence=integer(raw.get("sequence"));need(sequence>0,"SEQUENCE");
        var unsigned=new LinkedHashMap<>(raw);String hash=text(unsigned,"cutHash");unsigned.remove("cutHash");need(hash.equals(SemanticJson.hash(unsigned)),"CUT_HASH");
        var goals=new TreeMap<String,Goal>();var known=new TreeSet<String>();definition.goals().forEach(g->known.add(g.goalId()));
        for(Object value:list(raw.get("goals"))) {
            var row=CanonicalJson.object(value);String name=text(row,"name");var source=definition.goals().stream().filter(g->g.goalId().equals(name)).findFirst().orElseThrow(()->invalid("GOAL"));
            need(source.metadata().semanticId().equals(row.get("definition"))&&Set.of("WAITING","ENABLED","SATISFIED").contains(row.get("state")),"GOAL_SOURCE_OR_STATE");
            String previous=text(row,"previousState");need(previous.isEmpty()||Set.of("WAITING","ENABLED","SATISFIED").contains(previous),"PREVIOUS_STATE");
            var times=CanonicalJson.object(row.get("publicationTimes"));for(var time:times.entrySet())need(Set.of("WAITING","ENABLED","SATISFIED").contains(time.getKey())&&Long.parseLong((String)time.getValue())>0,"PUBLICATION_TIME");
            need(goals.putIfAbsent(name,new Goal(name,source.metadata().semanticId(),previous,text(row,"state"),strings(row.get("committed")),strings(row.get("achieved")),times))==null,"DUPLICATE_GOAL");
        }
        need(goals.keySet().equals(known),"GOAL_POPULATION");var doneBy=new TreeSet<String>();
        for(Object value:list(raw.get("done"))) {
            var term=CanonicalJson.object(value);need(Boolean.TRUE.equals(term.get("supported")),"DONE_UNSUPPORTED");var literal=CanonicalJson.object(term.get("key"));
            need("LITERAL".equals(literal.get("kind"))&&"done".equals(literal.get("functor"))&&Boolean.FALSE.equals(literal.get("negative")),"DONE_FACT");
            var terms=list(literal.get("terms"));need(terms.size()==3&&atom(terms.get(0)).equals(identity.localId()),"DONE_SCHEME");String goal=atom(terms.get(1));need(known.contains(goal),"DONE_GOAL");if(goal.equals("bid"))doneBy.add(atom(terms.get(2)));
        }
        for(Object value:list(raw.get("commitments"))) {var row=CanonicalJson.object(value);need(!text(row,"agent").isBlank()&&definition.missions().stream().anyMatch(m->m.missionId().equals(row.get("mission"))),"MISSION_COMMITMENT");}
        List<String> frozen=raw.containsKey("bidCommittedAtSatisfaction")?strings(raw.get("bidCommittedAtSatisfaction")):null;
        // The pinned producer retains first-satisfaction membership after an
        // actual reset and marks the whole history INCOMPLETE. It is historical
        // evidence only; the cast gate rejects that history before using it.
        need(frozen==null||goals.get("bid").state().equals("SATISFIED")||!continuous&&raw.get("historyQuality").equals("INCOMPLETE"),"FREEZE_WITHOUT_SATISFACTION");
        if(continuous&&goals.get("bid").state().equals("SATISFIED"))need(frozen!=null,"SATISFACTION_FREEZE_UNPROVEN");
        if(Boolean.TRUE.equals(raw.get("adminBidOverride"))) {
            var effect=CanonicalJson.object(raw.get("administrativeEffect"));need(Boolean.TRUE.equals(effect.get("effect"))&&"goalSatisfied(bid)".equals(effect.get("command"))&&"SATISFIED".equals(effect.get("after"))
                    &&Set.of("WAITING","ENABLED").contains(effect.get("before"))&&!text(effect,"invocation").isBlank()&&!text(effect,"performer").isBlank()&&"PINNED_SCHEMEBOARD_ADMCOMMAND_PERMISSION_AND_EFFECT".equals(effect.get("authority")),"ADMIN_EFFECT_PROOF");
        }
        return new Cut(scheme,artifact,uuid,sequence,continuous,continuous?(Boolean)raw.get("adminBidOverride"):Boolean.TRUE.equals(raw.get("adminBidOverride"))?Boolean.TRUE:null,
                List.copyOf(doneBy),frozen,Map.copyOf(goals),hash,SemanticJson.object(raw));
    }
    public Cut decode(VerificationObservation observation,String run,long generation,String revision) {
        need(observation.kind()==VerificationObservation.Kind.GOAL_TRANSITION,"KIND");var data=observation.data();var cut=decode(CanonicalJson.object(data.get("goalEvidence")),run,generation,revision);
        var rows=cut.goals().values().stream().filter(g->g.definition().equals(data.get("goalDefinition"))).toList();need(rows.size()==1,"FRAME_GOAL");var goal=rows.getFirst();
        need(cut.scheme().equals(data.get("boardIdentity"))&&cut.hash().equals(data.get("sourceCutId"))&&goal.previous().equals(data.get("previousState"))&&goal.state().equals(data.get("state"))&&observation.subject().equals(cut.scheme()+":"+goal.definition()),"FRAME_CROSSWALK");
        if(observation.sourceTime().status().equals("OBSERVED"))need(observation.sourceTime().authority().equals("ICARTAGOLOGGER_SYSTEM_EPOCH_PUBLICATION_NOT_MOISE_INTERNAL_TRANSITION")&&observation.sourceTime().rawValue().equals(cut.raw().get("publicationEpochMillis"))&&observation.sourceTime().clockId().equals(cut.raw().get("clockIdentity")),"FRAME_TIME");
        if(observation.quality()==Completeness.COMPLETE)need(cut.continuous()&&!goal.previous().isBlank()&&observation.sourceTime().status().equals("OBSERVED"),"FRAME_COMPLETENESS");return cut;
    }
    private static String atom(Object raw){var map=CanonicalJson.object(raw);need("LITERAL".equals(map.get("kind"))&&Boolean.FALSE.equals(map.get("negative"))&&list(map.get("terms")).isEmpty(),"ATOM");return text(map,"functor");}
    private static List<String> strings(Object raw){var result=list(raw).stream().map(v->{if(!(v instanceof String s)||s.isBlank())throw invalid("STRING_LIST");return s;}).sorted().toList();need(new HashSet<>(result).size()==result.size(),"DUPLICATE_NAMES");return result;}
    private static List<?> list(Object raw){if(!(raw instanceof List<?> values))throw invalid("LIST");need(values.size()<=4096,"CAPACITY");return values;}
    private static String text(Map<String,Object> map,String key){if(!(map.get(key) instanceof String value))throw invalid("TEXT:"+key);return value;}
    private static long integer(Object value){try{if(!(value instanceof Number))throw invalid("INTEGER");return new java.math.BigDecimal(value.toString()).longValueExact();}catch(ArithmeticException invalid){throw invalid("INTEGER");}}
    private static void need(boolean condition,String reason){if(!condition)throw invalid(reason);}
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("AUCTION_SCHEME_"+reason);}
}
