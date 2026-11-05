package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol.Check;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

/** Applicable checks from the existing native result; no second verifier. */
public final class VerifiedCheckpointInventory {
    /** A source-declared settled scope cannot claim future progress forever.
     * Read the original engine's selected roots; never rewrite its markings. */
    public static Map<String,Check> settled(VerificationSnapshot cut,Map<String,Check> existing,Collection<String> required) {
        var markers=required.stream().filter(id->id.startsWith(VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX)).toList();
        if(markers.isEmpty())return existing;
        var rows=new TreeMap<>(existing);
        var semantic=cut.result()==null?null:cut.result().semantic();
        var observed=semantic==null?Map.<String,Object>of():semantic.observation();
        var inventory=CanonicalJson.object(observed.getOrDefault("requirementInventory",Map.of()));
        var core=CanonicalJson.object(observed.getOrDefault("originalEngine",Map.of()));
        var targets=inventory.get("selectedIntentionalElements") instanceof List<?> list?list:List.of();
        var contexts=core.get("contexts") instanceof List<?> list?list:List.of();
        for(String marker:markers) {
            String scope=marker.substring(VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX.length());
            boolean found=false,unknown=targets.isEmpty(),failed=false;
            for(Object raw:contexts) {
                var context=CanonicalJson.object(raw);var view=CanonicalJson.object(context.getOrDefault("scopeView",Map.of()));
                var bindings=CanonicalJson.object(view.getOrDefault("bindings",Map.of()));
                if(bindings.values().stream().noneMatch(value->value instanceof String id&&scope.equals("source:"+id)))continue;
                found=true;var occurrences=CanonicalJson.object(context.getOrDefault("occurrences",Map.of()));
                for(Object target:targets) {
                    var roots=occurrences.values().stream().map(CanonicalJson::object).filter(o->target.equals(o.get("sourceElement"))).toList();
                    if(roots.isEmpty())unknown=true;
                    for(var root:roots) {
                        String status=Objects.toString(root.get("aggregateStatus"),""),quality=Objects.toString(root.get("quality"),"");
                        boolean nativeCut="PINNED_NATIVE_CUT".equals(core.get("evaluated"));
                        boolean prefix="OBSERVED_PREFIX".equals(core.get("historyQuality"));
                        boolean known=nativeCut&&prefix&&Set.of("KNOWN","KNOWN_DERIVED").contains(quality);
                        String executionQuality=Objects.toString(root.getOrDefault("executionQuality",quality));
                        var evidence=CanonicalJson.object(root.getOrDefault("executionEvidence",Map.of()));
                        known|=nativeCut&&prefix&&Set.of("KNOWN","KNOWN_DERIVED").contains(executionQuality)
                                &&"ORIGINAL_PROPAGATION_ON_ADMITTED_LEAVES/1.0".equals(context.get("executionQualification"));
                        known|=nativeCut&&!evidence.isEmpty()&&Set.of("KNOWN_HISTORY","KNOWN_DERIVED_HISTORY").contains(executionQuality);
                        failed|=known&&Set.of("VIOLATED","FAILED").contains(status);
                        unknown|=!known||!Set.of("FULFILLED","VIOLATED","FAILED").contains(status);
                    }
                }
            }
            rows.put(marker,new Check(failed?"FAIL":!found||unknown?"UNKNOWN":"PASS","APPLICABLE",
                    failed?"SETTLED_NATIVE_ROOT_FAILED":!found||unknown?"SETTLED_SELECTED_NATIVE_ROOT_NOT_FULFILLED":"SETTLED_SELECTED_NATIVE_ROOT_FULFILLED"));
        }
        boolean failed=rows.values().stream().anyMatch(c->c.applicability().equals("APPLICABLE")&&c.truth().equals("FAIL"));
        boolean unknown=rows.values().stream().anyMatch(c->c.applicability().equals("APPLICABLE")&&!Set.of("PASS","FAIL").contains(c.truth()));
        rows.put(VerifiedCheckpointProtocol.REQUIRED_CHECK,new Check(failed?"FAIL":unknown?"UNKNOWN":"PASS","APPLICABLE",
                failed?"APPLICABLE_NATIVE_CHECK_FAILED":unknown?"APPLICABLE_REQUIRED_EVIDENCE_INCOMPLETE":"APPLICABLE_NATIVE_INVENTORY_PASSED"));
        return Map.copyOf(rows);
    }
    public static Map<String,Check> evaluate(VerificationSnapshot cut) {
        var rows=new TreeMap<String,Check>();boolean unknown=false,failed=false;
        var result=cut.result();
        if(result==null||!"CURRENT_OBSERVED".equals(result.freshness())||cut.image()==null||!org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(cut.image().soil().getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(result.stateHash()))
            return Map.of(VerifiedCheckpointProtocol.REQUIRED_CHECK,new Check("UNKNOWN","APPLICABLE","EXACT_NATIVE_RESULT_UNAVAILABLE"));
        var semantic=result.semantic();
        if(semantic==null)return Map.of(VerifiedCheckpointProtocol.REQUIRED_CHECK,new Check("UNKNOWN","APPLICABLE","NATIVE_REQUIREMENT_INVENTORY_UNAVAILABLE"));
        var observed=semantic.observation();var inventory=CanonicalJson.object(observed.getOrDefault("requirementInventory",Map.of()));
        if(!"NATIVE_CONTRACT_INVENTORY/1.0".equals(inventory.get("version")))unknown=true;
        Set<String> required=new TreeSet<>();Object rawRequired=inventory.get("requiredInvariants");
        if(rawRequired instanceof List<?> list)list.forEach(id->required.add((String)id));else unknown=true;
        Set<String> seen=new HashSet<>();
        for(var outcome:result.outcomes()) {
            if(!required.contains(outcome.constraintId())&&outcome.outcome()!=VerificationOutcome.FAIL&&outcome.outcome()!=VerificationOutcome.ERROR)continue;
            seen.add(outcome.constraintId());String truth=switch(outcome.outcome()){case PASS->"PASS";case FAIL->"FAIL";case ERROR->"ERROR";default->"UNKNOWN";};
            rows.put(outcome.constraintId(),new Check(truth,"APPLICABLE",outcome.diagnostic()));failed|=truth.equals("FAIL");unknown|=!Set.of("PASS","FAIL").contains(truth);
        }
        for(String id:required)if(!seen.contains(id)){rows.put(id,new Check("UNKNOWN","APPLICABLE","REQUIRED_NATIVE_INVARIANT_MISSING"));unknown=true;}
        for(var constraint:semantic.constraints()) {
            // Goal conditions are progress predicates: false can mean a future
            // pending goal. Their original evaluated value remains in history.
            if(constraint.kind().equals("GOAL_CONDITION")) {
                rows.put("condition:"+constraint.id(),new Check("PENDING","FUTURE",constraint.verdict()+":"+constraint.diagnostic()));continue;
            }
            if(constraint.kind().equals("INVARIANT_BODY"))continue; // represented above by its native aggregate
            boolean relevant=Set.of("PRE","POST","TASK_PRE","TASK_POST","INVOCATION_CONFORMANCE","TASK_INVOCATION","RUNTIME_BINDING").contains(constraint.kind())
                    ||constraint.kind().equals("INVOCATION")&&!constraint.contextObject().isBlank();
            if(!relevant)continue;
            String truth=switch(constraint.verdict()){case PASS->"PASS";case FAIL->"FAIL";case ERROR->"ERROR";default->"UNKNOWN";};
            rows.put("semantic:"+constraint.id()+":"+constraint.invocation(),new Check(truth,"APPLICABLE",constraint.diagnostic()));
            failed|=truth.equals("FAIL");unknown|=!Set.of("PASS","FAIL").contains(truth);
        }
        if(!"OBSERVED_PREFIX".equals(semantic.coverage().get("history")))unknown=true;
        // Preserve the original engine's terminal failures. PENDING remains
        // progress; it never masks a violated selected Goal/Task occurrence.
        Object original=observed.get("originalEngine");
        if(original instanceof Map<?,?> core&&core.get("contexts") instanceof List<?> contexts)for(Object rawContext:contexts) {
            var context=CanonicalJson.object(rawContext);Object rawOccurrences=context.get("occurrences");
            if(!(rawOccurrences instanceof Map<?,?>))continue;
            for(var entry:CanonicalJson.object(rawOccurrences).entrySet()) {
                var occurrence=CanonicalJson.object(entry.getValue());String status=Objects.toString(occurrence.get("aggregateStatus"),"");
                if(Set.of("VIOLATED","FAILED").contains(status)) {
                    rows.put("original:"+Objects.toString(context.get("context"))+":"+entry.getKey(),new Check("FAIL","APPLICABLE","ORIGINAL_ENGINE_TERMINAL_"+status));failed=true;
                }
            }
        }
        rows.put(VerifiedCheckpointProtocol.REQUIRED_CHECK,new Check(failed?"FAIL":unknown?"UNKNOWN":"PASS","APPLICABLE",failed?"APPLICABLE_NATIVE_CHECK_FAILED":unknown?"APPLICABLE_REQUIRED_EVIDENCE_INCOMPLETE":"APPLICABLE_NATIVE_INVENTORY_PASSED"));
        return Map.copyOf(rows);
    }
    /** Invocation checks belong to their captured PRE/POST states in the accepted
     * prefix. Applying the frozen state snapshot must not erase those outcomes. */
    public static Map<String,Check> evaluatePrefix(VerificationSnapshot cut,List<RuntimeHistoryPage.Entry> prefix,long fromOrdinal,long endOrdinal) {
        var rows=new TreeMap<>(evaluate(cut));boolean missing=endOrdinal>fromOrdinal&&(prefix.isEmpty()||prefix.getFirst().ordinal()>fromOrdinal);boolean unknown=missing;
        boolean failed=rows.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth().equals("FAIL");
        unknown|=!Set.of("PASS","FAIL").contains(rows.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
        for(var entry:prefix)if(!entry.kind().equals("CHECKPOINT_AUDIT")&&!entry.result().freshness().equals("TRACE_ONLY")&&entry.ordinal()>=fromOrdinal&&entry.ordinal()<endOrdinal&&entry.result().semantic()!=null
                &&entry.result().sessionId().equals(cut.result().sessionId())&&entry.result().generation()==cut.result().generation()&&entry.result().modelRevision().equals(cut.result().modelRevision()))for(var check:entry.result().semantic().constraints()) {
            // A transient cast-readiness diagnostic has no captured invocation
            // PRE/POST state. Its applicability is checked at the frozen cut.
            // Actual invocation failures/unknowns cannot be repaired by that cut.
            if(!Set.of("PRE","POST","TASK_PRE","TASK_POST","INVOCATION_CONFORMANCE","TASK_INVOCATION").contains(check.kind())
                    &&!(check.kind().equals("RUNTIME_BINDING")&&!check.invocation().isBlank())
                    &&!(check.kind().equals("INVOCATION")&&!check.contextObject().isBlank()))continue;
            String truth=switch(check.verdict()){case PASS->"PASS";case FAIL->"FAIL";case ERROR->"ERROR";default->"UNKNOWN";};
            rows.put("prefix:"+entry.ordinal()+":"+check.id()+":"+check.invocation(),new Check(truth,"APPLICABLE",check.diagnostic()));
            failed|=truth.equals("FAIL");unknown|=!Set.of("PASS","FAIL").contains(truth);
        }
        if(missing)rows.put("native.invocation-prefix",new Check("UNKNOWN","APPLICABLE","REQUIRED_INVOCATION_PREFIX_NOT_RETAINED"));
        rows.put(VerifiedCheckpointProtocol.REQUIRED_CHECK,new Check(failed?"FAIL":unknown?"UNKNOWN":"PASS","APPLICABLE",failed?"APPLICABLE_NATIVE_CHECK_FAILED":unknown?"APPLICABLE_REQUIRED_EVIDENCE_INCOMPLETE":"APPLICABLE_NATIVE_INVENTORY_PASSED"));
        return Map.copyOf(rows);
    }
    public static Map<String,Check> evaluatePrefix(VerificationSnapshot cut,RuntimeEventJournal journal,long from,long end){
        var tail=journal.retainedEntries();
        if(end==from||!tail.isEmpty()&&tail.getFirst().ordinal()<=from)return evaluatePrefix(cut,tail,from,end);
        var rows=new TreeMap<>(evaluate(cut));
        // Preserve every invocation result without keeping a second full history
        // in RAM. Eviction of a disk-persisted record is not missing evidence.
        journal.visitRange(from,end,entry->{
            if(entry.kind().equals("CHECKPOINT_AUDIT")||entry.result().freshness().equals("TRACE_ONLY")||entry.result().semantic()==null||entry.result().semantic().constraints().stream().noneMatch(c->Set.of("PRE","POST","TASK_PRE","TASK_POST","INVOCATION_CONFORMANCE","TASK_INVOCATION","RUNTIME_BINDING","INVOCATION").contains(c.kind())))return;
            var one=evaluatePrefix(cut,List.of(entry),entry.ordinal(),entry.ordinal()+1);
            one.forEach((id,value)->{if(id.startsWith("prefix:"))rows.put(id,value);});
        });
        boolean fail=rows.values().stream().anyMatch(c->c.applicability().equals("APPLICABLE")&&c.truth().equals("FAIL"));
        boolean unknown=rows.values().stream().anyMatch(c->c.applicability().equals("APPLICABLE")&&!Set.of("PASS","FAIL").contains(c.truth()));
        rows.put(VerifiedCheckpointProtocol.REQUIRED_CHECK,new Check(fail?"FAIL":unknown?"UNKNOWN":"PASS","APPLICABLE",fail?"APPLICABLE_NATIVE_CHECK_FAILED":unknown?"APPLICABLE_REQUIRED_EVIDENCE_INCOMPLETE":"APPLICABLE_NATIVE_INVENTORY_PASSED"));return Map.copyOf(rows);
    }
    private VerifiedCheckpointInventory(){ }
}
