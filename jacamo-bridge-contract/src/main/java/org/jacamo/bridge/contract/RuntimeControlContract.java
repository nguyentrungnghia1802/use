package org.jacamo.bridge.contract;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned narrow execution-control extension. No platform or USE objects cross this boundary. */
public final class RuntimeControlContract {
    public static final String VERSION = "1.0.0";
    public static final String CAPABILITY = "runtime.control.jason-cycle.v1";
    public static final String GUARANTEE = "JASON_REASONING_CYCLE_BOUNDARY";
    public static final String LIMITATION = "Jason agents paused; in-flight CArtAgO operations and Moise/OrgBoard activity may complete. This is not atomic whole-platform suspension.";
    private RuntimeControlContract() { }

    public enum State { RUNNING, PAUSE_REQUESTED, PAUSED, RESUME_REQUESTED }
    public enum Action { STATUS, PAUSE, RESUME }
    public record Request(String version, String sessionId, long generation, String modelRevision,
                          String requestId, Action action, String reason) {
        public Request {
            if (!VERSION.equals(version) || sessionId == null || sessionId.isBlank() || generation < 1
                    || modelRevision == null || modelRevision.isBlank() || requestId == null || requestId.isBlank()
                    || action == null || reason == null || reason.length() > 2048 || requestId.length() > 128)
                throw new ContractException("RUNTIME_CONTROL_REQUEST_INVALID");
        }
        public Map<String,Object> payload() { return Map.of("version",version,"sessionId",sessionId,
                "generation",generation,"modelRevision",modelRevision,"requestId",requestId,"action",action.name(),"reason",reason); }
        public static Request decode(Map<String,Object> p) {
            if (!p.keySet().equals(Set.of("version","sessionId","generation","modelRevision","requestId","action","reason")))
                throw new ContractException("RUNTIME_CONTROL_REQUEST_SCHEMA");
            return new Request(text(p,"version"),text(p,"sessionId"),number(p,"generation"),text(p,"modelRevision"),
                    text(p,"requestId"),Action.valueOf(text(p,"action")),text(p,"reason"));
        }
    }
    public record Status(String version, String sessionId, long generation, String modelRevision,
                         boolean capable, State state, String requestId, Set<String> requiredAgents,
                         Set<String> acknowledgedAgents, Set<String> departedAgents, String diagnostic,
                         Instant observedAt) {
        public Status {
            if (!VERSION.equals(version) || sessionId == null || sessionId.isBlank() || generation < 1
                    || modelRevision == null || modelRevision.isBlank() || state == null || requestId == null
                    || diagnostic == null || observedAt == null) throw new ContractException("RUNTIME_CONTROL_STATUS_INVALID");
            requiredAgents=Set.copyOf(requiredAgents); acknowledgedAgents=Set.copyOf(acknowledgedAgents); departedAgents=Set.copyOf(departedAgents);
            if (!requiredAgents.containsAll(acknowledgedAgents) || !requiredAgents.containsAll(departedAgents)
                    || state == State.PAUSED && (!capable || requiredAgents.isEmpty() || !acknowledgedAgents.containsAll(requiredAgents)))
                throw new ContractException("RUNTIME_CONTROL_FALSE_PAUSED");
        }
        public Map<String,Object> payload() {
            var p=new java.util.LinkedHashMap<String,Object>();
            p.put("version",version); p.put("sessionId",sessionId); p.put("generation",generation); p.put("modelRevision",modelRevision);
            p.put("capable",capable); p.put("state",state.name()); p.put("requestId",requestId);
            p.put("requiredAgents",requiredAgents.stream().sorted().toList()); p.put("acknowledgedAgents",acknowledgedAgents.stream().sorted().toList());
            p.put("departedAgents",departedAgents.stream().sorted().toList()); p.put("diagnostic",diagnostic);
            p.put("observedAt",observedAt.toString()); p.put("guarantee",GUARANTEE); p.put("limitation",LIMITATION); return Map.copyOf(p);
        }
        public static Status decode(Map<String,Object> p) {
            if (!p.keySet().equals(Set.of("version","sessionId","generation","modelRevision","capable","state","requestId",
                    "requiredAgents","acknowledgedAgents","departedAgents","diagnostic","observedAt","guarantee","limitation"))
                    || !GUARANTEE.equals(p.get("guarantee")) || !LIMITATION.equals(p.get("limitation")) || !(p.get("capable") instanceof Boolean))
                throw new ContractException("RUNTIME_CONTROL_STATUS_SCHEMA");
            return new Status(text(p,"version"),text(p,"sessionId"),number(p,"generation"),text(p,"modelRevision"),
                    (Boolean)p.get("capable"),State.valueOf(text(p,"state")),text(p,"requestId"),strings(p,"requiredAgents"),
                    strings(p,"acknowledgedAgents"),strings(p,"departedAgents"),text(p,"diagnostic"),Instant.parse(text(p,"observedAt")));
        }
    }
    private static String text(Map<String,Object> p,String key) {
        if (!(p.get(key) instanceof String s)) throw new ContractException("RUNTIME_CONTROL_FIELD:"+key); return s;
    }
    private static long number(Map<String,Object> p,String key) {
        Object n=p.get(key);
        if (!(n instanceof Number value) || value.doubleValue()!=value.longValue()) throw new ContractException("RUNTIME_CONTROL_FIELD:"+key);
        return value.longValue();
    }
    private static Set<String> strings(Map<String,Object> p,String key) {
        if (!(p.get(key) instanceof List<?> values) || values.size()>4096 || values.stream().anyMatch(v->!(v instanceof String s)||s.isBlank()))
            throw new ContractException("RUNTIME_CONTROL_FIELD:"+key);
        var result=new java.util.HashSet<String>(); for(Object v:values) if(!result.add((String)v)) throw new ContractException("RUNTIME_CONTROL_DUPLICATE_AGENT");
        return Set.copyOf(result);
    }
}
