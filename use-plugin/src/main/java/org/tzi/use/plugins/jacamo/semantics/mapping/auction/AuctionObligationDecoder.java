package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.semantics.normative.NormativeObservation;

/** Exact structured MOISE goal obligations. Commitment norms, permissions and
 * unrelated aims are not renamed to bid/decide obligations. No framework load. */
public final class AuctionObligationDecoder {
    public record Observed(NormativeObservation norm,String scheme,String goal,String player,Long created,Long deadline,Long ended,
            boolean unfulfilled,boolean timeComplete,String clock,Map<String,Object> proof,long sourceSequence) { }
    private final org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.NplLifecycleCut lifecycle;
    public AuctionObligationDecoder(SemanticProgram program){lifecycle=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.NplLifecycleCut(program.casePlan().bundle().runtimeRequirements().get("sha256:npl-0.6.1.jar"));}
    public Observed decode(BridgeEntityId identity,Map<String,Object> raw,String run,long generation) {
        if(!"NPL_OBSERVATION/1.0".equals(raw.get("observationVersion"))||!"BOUND_PUBLIC_CREATED_ANNOTATION".equals(raw.get("activationIdentityStatus"))||!raw.containsKey("sourceLifecycle"))return null;
        var evidence=lifecycle.decode(identity,raw,run,generation);var norm=evidence.norm();var proof=evidence.proof();
        if(!"OBLIGATION".equals(norm.modality()))return null;
        var aim=literal(proof.get("aimTerm"));if(aim==null||!"done".equals(aim.get("functor")))return null;
        var arguments=list(aim.get("terms"));if(arguments.size()!=3)return null;
        String scheme=atom(arguments.get(0)),goal=atom(arguments.get(1)),player=atom(arguments.get(2));if(!Set.of("bid","decide").contains(goal))return null;
        var subject=literal(proof.get("subjectTerm"));need(subject!=null&&list(subject.get("terms")).isEmpty()&&player.equals(subject.get("functor"))&&player.equals(norm.subject()),"SUBJECT");
        var maintenance=literal(proof.get("maintenanceTerm"));need(maintenance!=null&&"enabled".equals(maintenance.get("functor"))&&list(maintenance.get("terms")).size()==2
                &&scheme.equals(atom(list(maintenance.get("terms")).get(0)))&&goal.equals(atom(list(maintenance.get("terms")).get(1))),"MOISE_GOAL_MAINTENANCE");
        var board=CanonicalJson.object(raw.get("boardObservationProof"));need("NPL_BOARD_SUBSCRIPTION/1.0".equals(board.get("version"))&&norm.boardUuid().equals(board.get("boardArtifactIdentity"))&&scheme.equals(board.get("artifactName"))
                &&"ora4mas.nopl.SchemeBoard".equals(board.get("artifactClass"))&&identity.scope().startsWith(text(board,"workspaceFullName")+":"),"BOARD_BINDING");
        // Historical case selection stays here; framework activation/time checks are shared.
        var terminal=CanonicalJson.object(proof.get("terminalTimes"));
        return new Observed(norm,scheme,goal,player,evidence.createdEpochMillis(),evidence.deadlineEpochMillis(),evidence.terminalEpochMillis(),
            terminal.containsKey("unfulfilled")||norm.state()==NormativeObservation.State.UNFULFILLED,evidence.timeComplete(),evidence.clockIdentity(),proof,evidence.sourceSequence());
    }
    public Observed decode(VerificationObservation observation,String run,long generation) {
        need(observation.kind()==VerificationObservation.Kind.NPL_LIFECYCLE,"KIND");var raw=CanonicalJson.object(observation.data().get("sourceObservation"));if(!raw.containsKey("sourceLifecycle"))return null;
        var identity=BridgeEntityId.parse(observation.subject());var result=decode(identity,raw,run,generation);if(result==null)return null;
        need(result.norm().boardUuid().equals(observation.data().get("boardIdentity"))&&result.norm().activation().equals(observation.data().get("activationKey"))&&result.norm().state().name().equals(observation.data().get("state")),"FRAME_BINDING");
        if(observation.sourceTime().status().equals("OBSERVED")) {
            Long expected=result.norm().state()==NormativeObservation.State.ACTIVE?result.created():epoch(CanonicalJson.object(result.proof().get("terminalTimes")),result.norm().state().name().toLowerCase(Locale.ROOT));
            need(expected!=null&&expected.toString().equals(observation.sourceTime().rawValue())&&result.clock().equals(observation.sourceTime().clockId())&&result.proof().get("authority").equals(observation.sourceTime().authority()),"FRAME_TIME");
        }
        if(observation.quality()==Completeness.COMPLETE)need(result.timeComplete()&&result.norm().history().equals("CREATED_EVENT_OBSERVED")&&observation.sourceTime().status().equals("OBSERVED"),"FRAME_COMPLETENESS");return result;
    }
    private static Map<String,Object> literal(Object value){var term=CanonicalJson.object(value);if(!Boolean.TRUE.equals(term.get("supported")))return null;var key=CanonicalJson.object(term.get("key"));return "LITERAL".equals(key.get("kind"))&&Boolean.FALSE.equals(key.get("negative"))?key:null;}
    private static String atom(Object value){var key=CanonicalJson.object(value);need("LITERAL".equals(key.get("kind"))&&Boolean.FALSE.equals(key.get("negative"))&&list(key.get("terms")).isEmpty(),"ATOM");return text(key,"functor");}
    private static Long epoch(Map<String,Object> value,String field){String raw=text(value,field);if(raw.isBlank())return null;long epoch=Long.parseLong(raw);need(epoch>=0&&epoch<9007199254740992L,"EPOCH_RANGE");return epoch;}
    private static String text(Map<String,Object> map,String key){need(map.get(key) instanceof String,"TEXT:"+key);return (String)map.get(key);}
    private static List<?> list(Object raw){need(raw instanceof List<?>,"LIST");return (List<?>)raw;}
    private static void need(boolean condition,String reason){if(!condition)throw new IllegalArgumentException("AUCTION_OBLIGATION_"+reason);}
}
