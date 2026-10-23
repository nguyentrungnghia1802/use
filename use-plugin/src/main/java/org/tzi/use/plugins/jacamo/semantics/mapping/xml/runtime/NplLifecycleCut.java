package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.normative.NormativeObservation;

/** Source NPL activation/annotation mechanics shared by mapping profiles. No aim/goal/modality policy. */
public final class NplLifecycleCut {
    public record Evidence(NormativeObservation norm,Long createdEpochMillis,Long deadlineEpochMillis,Long terminalEpochMillis,
            Boolean reportedUnfulfilled,boolean timeComplete,boolean continuous,String clockIdentity,Map<String,Object> proof,long sourceSequence) {
        public Evidence {proof=SemanticJson.object(proof);}
    }
    private final String nplHash;
    public NplLifecycleCut(String nplHash) {if(nplHash==null||!nplHash.matches("[0-9a-f]{64}"))throw invalid("PINNED_BINARY_REQUIRED");this.nplHash=nplHash;}
    public Evidence decode(BridgeEntityId identity,Map<String,Object> raw,String run,long generation) {
        need("NPL_OBSERVATION/1.0".equals(raw.get("observationVersion"))&&"BOUND_PUBLIC_CREATED_ANNOTATION".equals(raw.get("activationIdentityStatus"))&&raw.containsKey("sourceLifecycle"),"UNSUPPORTED_ACTIVATION_EVIDENCE");
        var norm=NormativeObservation.decode(identity,raw);need(identity.incarnation().equals(run+":"+norm.boardUuid()),"EPOCH_MISMATCH");
        var binary=CanonicalJson.object(raw.get("sourceArtifact"));
        need(nplHash.equals(binary.get("artifactSha256"))&&"0.6.1".equals(binary.get("artifactVersion"))&&"SUPPORTED_PINNED_BINARY".equals(binary.get("admission"))
            &&"1a14085372cf52cc96b49f8835f6b5ac987763c0d3b240b3bf6424d86be38e43".equals(raw.get("nplNormInstanceClassSha256")),"BINARY_MISMATCH");
        var proof=CanonicalJson.object(raw.get("sourceLifecycle"));var unsigned=new LinkedHashMap<>(proof);Object hash=unsigned.remove("hash");need(SemanticJson.hash(unsigned).equals(hash),"TIME_PROOF_HASH_MISMATCH");
        need("NPL_TIME_ANNOTATIONS/1.0".equals(proof.get("version"))&&"NPL061_PUBLIC_TIMETERM_ANNOTATION_EPOCH_MILLISECONDS".equals(proof.get("authority"))&&norm.state().name().equals(proof.get("state")),"TIME_PROOF_STATE_OR_VERSION");
        var basis=CanonicalJson.object(raw.get("timeBasis"));String clock="npl-system-wall:"+run+":"+generation;
        need(clock.equals(proof.get("clockIdentity"))&&clock.equals(basis.get("clockIdentity"))&&"EPOCH_MILLISECONDS".equals(basis.get("unit"))&&"JAVA_SYSTEM_CURRENT_TIME_MILLIS_SAME_PROCESS_AS_PINNED_NPL".equals(basis.get("authority"))
            &&"UNPROVEN_WALL_CLOCK".equals(basis.get("monotonicity"))&&nplHash.equals(CanonicalJson.object(basis.get("nplArtifact")).get("artifactSha256")),"TIME_BASIS_MISMATCH");
        var board=CanonicalJson.object(raw.get("boardObservationProof"));
        need("NPL_BOARD_SUBSCRIPTION/1.0".equals(board.get("version"))&&norm.boardUuid().equals(board.get("boardArtifactIdentity"))
            &&!text(board,"artifactName").isBlank()&&!text(board,"artifactClass").isBlank()&&identity.scope().equals(text(board,"workspaceFullName")+":"+text(board,"artifactName")),"BOARD_BINDING_MISMATCH");
        UUID.fromString(text(board,"workspaceUuid"));need(integer(board.get("sourceSubscriptionNumber"))>0&&board.get("emptyBaselineAfterListener") instanceof Boolean,"SUBSCRIPTION_PROOF_INVALID");
        boolean continuous=Boolean.TRUE.equals(board.get("emptyBaselineAfterListener"));
        need(!continuous||"CARTAGO_CREATION_BEFORE_RETURN".equals(board.get("origin"))&&"CONTINUOUS_FROM_EMPTY_CREATION".equals(board.get("scopeQuality")),"FALSE_SUBSCRIPTION_COMPLETENESS");
        Long created=epoch(proof,"createdEpochMillis"),deadline="ABSOLUTE_NPL_TIME".equals(raw.get("deadlineKind"))?epoch(raw,"deadline"):null,ended=epoch(proof,"terminalEpochMillis");
        if(created!=null)need(created.toString().equals(raw.get("createdEpochMillis")),"CREATED_TIME_CROSSWALK_MISMATCH");
        var terminal=CanonicalJson.object(proof.get("terminalTimes"));Long first=null;
        for(var entry:terminal.entrySet()) {
            need(Set.of("fulfilled","unfulfilled","inactive").contains(entry.getKey()),"TERMINAL_ANNOTATION_UNSUPPORTED");Long value=epoch(terminal,entry.getKey());need(value!=null,"TERMINAL_EPOCH_REQUIRED");first=first==null?value:Math.min(first,value);
        }
        need(Objects.equals(first,ended),"FIRST_TERMINAL_EPOCH_MISMATCH");
        boolean timeComplete="COMPLETE".equals(proof.get("status"));need(timeComplete||"INCOMPLETE".equals(proof.get("status")),"TIME_STATUS_UNSUPPORTED");
        if(timeComplete)need(created!=null&&(norm.state()==NormativeObservation.State.ACTIVE?ended==null:terminal.containsKey(norm.state().name().toLowerCase(Locale.ROOT))),"FALSE_TIME_COMPLETENESS");
        boolean observedFailure=terminal.containsKey("unfulfilled")||norm.state()==NormativeObservation.State.UNFULFILLED;
        Boolean unfulfilled=observedFailure?Boolean.TRUE:continuous&&norm.history().equals("CREATED_EVENT_OBSERVED")?Boolean.FALSE:null;
        return new Evidence(norm,created,deadline,ended,unfulfilled,timeComplete,continuous,clock,proof,integer(raw.get("sourceSequence")));
    }
    private static Long epoch(Map<String,Object> value,String field){String raw=text(value,field);if(raw.isBlank())return null;long epoch=Long.parseLong(raw);need(epoch>=0&&epoch<9007199254740992L,"EPOCH_RANGE_UNSUPPORTED");return epoch;}
    private static String text(Map<String,Object> map,String key){if(!(map.get(key) instanceof String value))throw invalid("TEXT_REQUIRED:"+key);return value;}
    private static long integer(Object value){try{if(!(value instanceof Number))throw invalid("INTEGER_REQUIRED");return new java.math.BigDecimal(value.toString()).longValueExact();}catch(ArithmeticException|NumberFormatException e){throw invalid("INTEGER_REQUIRED");}}
    private static void need(boolean valid,String reason){if(!valid)throw invalid(reason);}
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("NPL_LIFECYCLE_CUT:"+reason);}
}
