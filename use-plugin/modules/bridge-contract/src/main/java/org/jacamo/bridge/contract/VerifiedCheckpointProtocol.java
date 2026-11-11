package org.jacamo.bridge.contract;

import java.util.*;

/** Exact-cut extension; deliberately separate from Jason Pause/Resume. */
public final class VerifiedCheckpointProtocol {
    public static final String VERSION="1.0.0",CAPABILITY="runtime.verified-checkpoints.v1";
    public static final String INVENTORY="APPLICABLE_NATIVE_REQUIRED_INVENTORY/1.0",REQUIRED_CHECK="native.applicable";
    public static final String SETTLED_CHECK_PREFIX="native.settled:";
    public enum Action { STATUS, APPROVE, HOLD_FAILURE, HOLD_UNVERIFIED, ACKNOWLEDGE_AND_CONTINUE, MONITOR_CLOSED }
    public record Identity(String session,String run,long generation,String revision,String token,long sequence,long barrierEpoch,
                           String policyHash,String producerHash,String requestHash,Set<String> executionScope) {
        public Identity {
            for(String value:List.of(session,run,revision,token))required(value);
            if(generation<1||sequence<1||barrierEpoch<1)throw bad("IDENTITY_COUNTER");
            digest(policyHash);if(!producerHash.isEmpty())digest(producerHash);if(!requestHash.isEmpty())digest(requestHash);
            executionScope=Set.copyOf(executionScope);if(executionScope.isEmpty())throw bad("EXECUTION_SCOPE_REQUIRED");
        }
        public Map<String,Object> payload(){return Map.ofEntries(Map.entry("session",session),Map.entry("run",run),Map.entry("generation",generation),Map.entry("revision",revision),Map.entry("token",token),Map.entry("sequence",sequence),Map.entry("barrierEpoch",barrierEpoch),Map.entry("policyHash",policyHash),Map.entry("producerHash",producerHash),Map.entry("requestHash",requestHash),Map.entry("executionScope",executionScope.stream().sorted().toList()));}
        public static Identity decode(Map<String,Object> map){
            keys(map,"session","run","generation","revision","token","sequence","barrierEpoch","policyHash","producerHash","requestHash","executionScope");
            return new Identity(text(map,"session"),text(map,"run"),number(map,"generation"),text(map,"revision"),text(map,"token"),number(map,"sequence"),number(map,"barrierEpoch"),text(map,"policyHash"),text(map,"producerHash"),text(map,"requestHash"),strings(map.get("executionScope")));
        }
    }
    public record Check(String truth,String applicability,String diagnostic) {
        public Check {if(!Set.of("PASS","FAIL","UNKNOWN","ERROR","SKIPPED","PENDING").contains(truth)||!Set.of("APPLICABLE","FUTURE","NOT_APPLICABLE").contains(applicability)||diagnostic==null)throw bad("CHECK_VALUE");}
        public Map<String,Object> payload(){return Map.of("truth",truth,"applicability",applicability,"diagnostic",diagnostic);}
    }
    public record Command(Action action,String commandId,Identity identity,String acceptedUseCutHash,String resultHash,
                          Map<String,Check> checks,String diagnostic) {
        public Command {
            Objects.requireNonNull(action);required(commandId);Objects.requireNonNull(diagnostic);checks=Map.copyOf(checks);
            if(action!=Action.STATUS&&action!=Action.MONITOR_CLOSED){Objects.requireNonNull(identity);digest(identity.producerHash());digest(identity.requestHash());digest(acceptedUseCutHash);digest(resultHash);}
            if(checks.size()>16384||commandId.length()>128||diagnostic.length()>8192)throw bad("COMMAND_CAPACITY");
        }
        public Map<String,Object> payload(){
            var rows=new TreeMap<String,Object>();checks.forEach((id,check)->rows.put(id,check.payload()));var map=new LinkedHashMap<String,Object>();
            map.put("version",VERSION);map.put("action",action.name());map.put("commandId",commandId);map.put("identity",identity==null?null:identity.payload());map.put("acceptedUseCutHash",acceptedUseCutHash);map.put("resultHash",resultHash);map.put("checks",rows);map.put("diagnostic",diagnostic);return Collections.unmodifiableMap(map);
        }
        public static Command decode(Map<String,Object> map){
            keys(map,"version","action","commandId","identity","acceptedUseCutHash","resultHash","checks","diagnostic");
            if(!VERSION.equals(map.get("version")))throw bad("VERSION");var checks=new TreeMap<String,Check>();
            CanonicalJson.object(map.get("checks")).forEach((id,row)->{var c=CanonicalJson.object(row);keys(c,"truth","applicability","diagnostic");checks.put(id,new Check(text(c,"truth"),text(c,"applicability"),text(c,"diagnostic")));});
            return new Command(Action.valueOf(text(map,"action")),text(map,"commandId"),map.get("identity")==null?null:Identity.decode(CanonicalJson.object(map.get("identity"))),text(map,"acceptedUseCutHash"),text(map,"resultHash"),checks,text(map,"diagnostic"));
        }
        public static Command status(){return new Command(Action.STATUS,UUID.randomUUID().toString(),null,"","",Map.of(),"");}
    }
    public record Rule(String id,String sourceIdentity,String artifactClass,Set<String> operations,Set<String> requiredChecks,String selectorKind) {
        public Rule(String id,String sourceIdentity,String artifactClass,Set<String> operations,Set<String> requiredChecks){this(id,sourceIdentity,artifactClass,operations,requiredChecks,"OPERATION_POST");}
        public Rule {
            required(id);required(sourceIdentity);Objects.requireNonNull(artifactClass);operations=Set.copyOf(operations);requiredChecks=Set.copyOf(requiredChecks);
            if(!Set.of("OPERATION_POST","OPERATION_FAILURE","OPERATION_UNMAPPED_POST","TASK_PRE","TASK_POST","SCHEME_POST","NORM_TERMINAL").contains(selectorKind))throw bad("POLICY_SELECTOR");
            if(selectorKind.startsWith("OPERATION_"))required(artifactClass);
            if(selectorKind.equals("SCHEME_POST")&&!Set.of("terminal","administrative").containsAll(operations)
                    ||selectorKind.equals("NORM_TERMINAL")&&!Set.of("unfulfilled").containsAll(operations))throw bad("POLICY_LIFECYCLE_SELECTION");
            if(operations.isEmpty()||requiredChecks.isEmpty()||operations.stream().anyMatch(op->!op.matches("[A-Za-z_$][A-Za-z0-9_$]*")))throw bad("POLICY_RULE");
        }
        public Map<String,Object> payload(){return Map.of("id",id,"sourceIdentity",sourceIdentity,"artifactClass",artifactClass,"operations",operations.stream().sorted().toList(),"requiredChecks",requiredChecks.stream().sorted().toList(),"selectorKind",selectorKind);}
    }
    public record Policy(String programHash,Map<String,String> inputHashes,List<Rule> rules) {
        public Policy {
            digest(programHash);inputHashes=Map.copyOf(inputHashes);inputHashes.values().forEach(VerifiedCheckpointProtocol::digest);rules=List.copyOf(rules);
            if(inputHashes.isEmpty()||rules.isEmpty()||rules.size()>256||rules.stream().map(Rule::id).distinct().count()!=rules.size())throw bad("POLICY_INVENTORY");
            if(rules.stream().anyMatch(rule->!rule.requiredChecks().contains(REQUIRED_CHECK)))throw bad("POLICY_NATIVE_INVENTORY_REQUIRED");
        }
        public Map<String,Object> payload(){return Map.of("version","CHECKPOINT_POLICY/1.2","inventory",INVENTORY,"programHash",programHash,"inputHashes",inputHashes,"rules",rules.stream().map(Rule::payload).toList());}
        public String hash(){return VerifiedCheckpointProtocol.hash(payload());}
        public static Policy decode(Map<String,Object> map){
            keys(map,"version","inventory","programHash","inputHashes","rules");
            if(!"CHECKPOINT_POLICY/1.2".equals(map.get("version"))||!INVENTORY.equals(map.get("inventory")))throw bad("POLICY_VERSION");
            var hashes=new TreeMap<String,String>();CanonicalJson.object(map.get("inputHashes")).forEach((k,v)->{if(!(v instanceof String value))throw bad("POLICY_HASH_VALUE");hashes.put(k,value);});
            if(!(map.get("rules") instanceof List<?> list))throw bad("POLICY_RULES");
            var rules=new ArrayList<Rule>();for(Object raw:list){var r=CanonicalJson.object(raw);keys(r,"id","sourceIdentity","artifactClass","operations","requiredChecks","selectorKind");rules.add(new Rule(text(r,"id"),text(r,"sourceIdentity"),text(r,"artifactClass"),strings(r.get("operations")),strings(r.get("requiredChecks")),text(r,"selectorKind")));}
            return new Policy(text(map,"programHash"),hashes,rules);
        }
    }
    public static String hash(Object value){
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(CanonicalJson.encode(value)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static void requireRequest(Map<String,Object> request){
        keys(request,"identity","checkpointId","sourceOccurrences","requiredChecks","inputHashes","watermarks","barrier","snapshot","ready");
        var identity=Identity.decode(CanonicalJson.object(request.get("identity")));
        if(!(request.get("ready") instanceof Boolean ready))throw bad("READY_VALUE");
        var unhashed=new LinkedHashMap<>(request);var unsigned=new LinkedHashMap<>(identity.payload());unsigned.put("requestHash","");unhashed.put("identity",unsigned);
        if(!identity.requestHash().equals(hash(unhashed)))throw bad("REQUEST_HASH");
        if(ready) {
            if(!identity.producerHash().equals(hash(CanonicalJson.object(request.get("snapshot")))))throw bad("SNAPSHOT_HASH");
            var proof=CanonicalJson.object(request.get("barrier"));
            if(!Boolean.TRUE.equals(proof.get("admissionClosed"))||!Boolean.TRUE.equals(proof.get("clockFrozen"))||!CanonicalJson.object(proof.get("active")).isEmpty()||number(proof,"pending")!=0||number(proof,"epoch")!=identity.barrierEpoch())throw bad("BARRIER_PROOF");
        }
    }
    private static Set<String> strings(Object raw){if(!(raw instanceof List<?> list)||list.size()>16384||list.stream().anyMatch(v->!(v instanceof String s)||s.isBlank()))throw bad("STRINGS");var set=new HashSet<String>();for(Object v:list)if(!set.add((String)v))throw bad("DUPLICATE_STRING");return Set.copyOf(set);}
    private static String text(Map<String,Object> map,String key){if(!(map.get(key) instanceof String value))throw bad("FIELD:"+key);return value;}
    private static long number(Map<String,Object> map,String key){if(!(map.get(key) instanceof Number value))throw bad("NUMBER:"+key);try{return new java.math.BigDecimal(value.toString()).longValueExact();}catch(ArithmeticException invalid){throw bad("INTEGER:"+key);}}
    private static void keys(Map<String,Object> map,String... keys){if(!map.keySet().equals(Set.of(keys)))throw bad("SCHEMA");}
    private static void required(String value){if(value==null||value.isBlank())throw bad("VALUE_REQUIRED");}
    private static void digest(String value){if(value==null||!value.matches("[0-9a-f]{64}"))throw bad("HASH_REQUIRED");}
    private static ContractException bad(String reason){return new ContractException("VERIFIED_CHECKPOINT:"+reason);}
    private VerifiedCheckpointProtocol(){ }
}
