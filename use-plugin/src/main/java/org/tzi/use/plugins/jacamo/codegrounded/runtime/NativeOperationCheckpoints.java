package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.time.Duration;
import java.util.*;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.Outcome;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseModelBuilder;
import org.tzi.use.plugins.jacamo.verification.*;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.Value;

/** Correlated @pre evidence on the coordinator's active system, using the existing USE evaluator. */
final class NativeOperationCheckpoints {
    private record Active(OperationCheck check,long started) { }
    private final NativeRuntimeMutationEngine engine;
    private final DefaultVerificationService verifier=new DefaultVerificationService();
    private final LinkedHashMap<String,Active> active=new LinkedHashMap<>();
    private final int capacity=32;
    private Set<String> features=Set.of();
    NativeOperationCheckpoints(NativeRuntimeMutationEngine engine) {this.engine=engine;}
    static String kind(RuntimeEvent e) {
        if(!"cartago".equals(e.subsystem()) || e.correlationId().isBlank()) return "EVENT";
        return e.kind()==RuntimeEventKind.STARTED && "started".equals(e.after().get("phase"))?"OPERATION_PRE":
                e.kind()==RuntimeEventKind.SUCCEEDED || e.kind()==RuntimeEventKind.FAILED?"OPERATION_POST":"EVENT";
    }
    String eligibleKind(RuntimeEvent event) {
        Object identity=event.after().get("artifactSemanticId");
        return identity instanceof String id && engine.objectForSemanticId(id)!=null?kind(event):"EVENT";
    }
    Set<String> features(){return features;}
    private void features(String name) {
        var object=engine.system().state().objectByName(name);if(object==null){features=Set.of();return;}
        var keys=new HashSet<String>();keys.add("class:"+object.cls().name());object.cls().allParents().forEach(p->keys.add("class:"+p.name()));
        object.cls().allAttributes().forEach(a->keys.add("attribute:"+a.owner().name()+"."+a.name()));
        engine.system().state().allLinks().stream().filter(l->l.linkedObjects().contains(object)).forEach(l->keys.add("association:"+l.association().name()));features=Set.copyOf(keys);
    }
    List<Outcome> observe(RuntimeEvent event) {
        features=Set.of();
        String kind=kind(event);if(kind.equals("EVENT"))return List.of();
        String key=event.sessionId()+":"+event.generation()+":"+event.entityId().canonical()+":"+event.correlationId();
        active.entrySet().removeIf(e->System.nanoTime()-e.getValue().started()>Duration.ofMinutes(5).toNanos());
        try {
            if(kind.equals("OPERATION_PRE")) {
                if(active.containsKey(key)) return skipped("PRE_DUPLICATE_CORRELATION",event);
                if(active.size()>=capacity) return skipped("PRE_CORRELATION_CAPACITY",event);
                Object semantic=event.after().get("artifactSemanticId");
                var object=semantic instanceof String id?engine.objectForSemanticId(id):null;
                if(object==null) return skipped("PRE_EXACT_CONTEXT_UNAVAILABLE",event);
                var uuid=object.state(engine.system().state()).attributeValue("uuid");
                if(!(uuid instanceof StringValue s) || !s.value().equals(event.entityId().incarnation()))
                    return skipped("PRE_ARTIFACT_INCARNATION_MISMATCH",event);
                Object signature=event.after().get("operationSignature");
                if(!(signature instanceof List<?>)) return skipped("PRE_EXACT_SIGNATURE_UNAVAILABLE",event);
                String canonical=NativeUseModelBuilder.canonicalJson(signature);
                var matches=object.cls().allOperations().stream().filter(op->canonical.equals(DomainProjection.decode(
                        op.getAnnotationValue("CartagoOperation","signature64")))).toList();
                if(matches.size()!=1) return skipped("PRE_EXACT_OPERATION_UNAVAILABLE",event);
                var operation=matches.getFirst();
                if(!(event.after().get("argumentValues") instanceof List<?> raw) || !(event.after().get("argumentTypes") instanceof List<?> types)
                        || raw.size()!=types.size() || raw.size()!=operation.paramList().size()) return skipped("PRE_ARGUMENT_EVIDENCE_UNAVAILABLE",event);
                var arguments=new ArrayList<Value>();
                for(int i=0;i<raw.size();i++) {
                    if(!(raw.get(i) instanceof String text) || !(types.get(i) instanceof String type))return skipped("PRE_ARGUMENT_EVIDENCE_INVALID",event);
                    String oclType=switch(type) {case "java.lang.Boolean"->"Boolean";case "java.lang.Byte","java.lang.Short","java.lang.Integer"->"Integer";
                        case "java.lang.Float","java.lang.Double"->"Real";case "java.lang.String","java.lang.Character"->"String";default->throw new IllegalArgumentException("PRE_ARGUMENT_TYPE_UNSUPPORTED:"+type);};
                    var value=DomainProjection.propertyValue(oclType,text);
                    if(!value.type().conformsTo(operation.paramList().varDecl(i).type()))return skipped("PRE_ARGUMENT_TYPE_MISMATCH",event);
                    arguments.add(value);
                }
                var registry=ConstraintRegistry.nativeModel(engine.system().model());
                var check=verifier.beginOperation(engine.system(),registry,new TraceIndex(List.of()),
                        new OperationRequest(object.name(),operation.name(),arguments,event.correlationId(),List.of(event.eventId())));
                active.put(key,new Active(check,System.nanoTime()));
                features(object.name());
                return outcomes(check.preconditions());
            }
            var before=active.remove(key);
            if(before==null)return skipped("POST_MATCHING_PRE_UNAVAILABLE",event);
            var context=engine.system().state().objectByName(before.check().request().objectName());
            if(context==null)return skipped("POST_CONTEXT_REMOVED",event);
            if(!(context.state(engine.system().state()).attributeValue("uuid") instanceof StringValue uuid)
                    || !uuid.value().equals(event.entityId().incarnation()))return skipped("POST_CONTEXT_INCARNATION_CHANGED",event);
            features(before.check().request().objectName());
            if(event.kind()!=RuntimeEventKind.SUCCEEDED) {
                return before.check().operation().postConditions().stream().map(condition->new Outcome("COMPILED:"+condition,condition.cls().name(),
                        VerificationOutcome.SKIPPED,"OPERATION_FAILED_OR_ABORTED",condition.expression().toString(),context.name())).toList();
            }
            return outcomes(verifier.completeOperation(before.check(),engine.system().state(),null,List.of(event.eventId())));
        } catch(RuntimeException unsupported) {return skipped("OPERATION_CHECKPOINT_UNAVAILABLE:"+unsupported.getMessage(),event);}
    }
    private List<Outcome> outcomes(VerificationReport report) {return report.results().stream().map(r->{
        var object=r.contextObject()==null?null:engine.system().state().objectByName(r.contextObject());
        return new Outcome(r.constraintId(),object==null?"":object.cls().name(),r.outcome(),r.explanation(),r.oclSource(),r.contextObject());
    }).toList();}
    private static List<Outcome> skipped(String reason,RuntimeEvent e) {return List.of(new Outcome("OPERATION:"+e.correlationId(),"",VerificationOutcome.SKIPPED,reason,""));}
    void clear() {active.clear();}
}
