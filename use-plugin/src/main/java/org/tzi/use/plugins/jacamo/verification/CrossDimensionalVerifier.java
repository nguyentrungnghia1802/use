package org.tzi.use.plugins.jacamo.verification;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantic.*;
import org.tzi.use.plugins.jacamo.trace.*;
import org.tzi.use.uml.sys.MSystem;

/** Verifies source-declared cross-dimensional bindings, not invented behavioral obligations. */
public final class CrossDimensionalVerifier {
    private static final Set<String> REFERENCES=Set.of("Agent.artifact","Agent.joinWorkspace",
        "ExternalAction.operation","Plan.RefArtifact","ObsProperty.obsproperty","Role.players",
        "Organisation.deploysAgent","OGoal.OGoalToGoal");

    public List<VerificationResult> verify(JaCaMoSemanticModel semantic, MSystem system, TraceIndex trace) {
        List<VerificationResult> results=new ArrayList<>();
        for(var source:semantic.elements()) for(var reference:source.references()) {
            if(!REFERENCES.contains(source.kind()+"."+reference.feature())) continue;
            results.add(verifyBinding(system,trace,"dSML4JaCaMo::"+source.kind()+"#"+reference.feature(),
                source.id().value(),reference.targetId()==null ? null : reference.targetId().value()));
        }
        return List.copyOf(results);
    }

    /** The expectation must come from a declared source relation or explicit case specification. */
    public VerificationResult verifyBinding(MSystem system, TraceIndex trace, String referenceIdentity,
                                            String sourceId, String targetId) {
        String rule="CROSS_DIMENSION:"+referenceIdentity;
        List<String> ids=targetId==null ? List.of(sourceId) : List.of(sourceId,targetId);
        if(targetId==null) return result(rule,VerificationOutcome.SKIPPED,null,"UNSUPPORTED_EXACT_RELATION_UNBOUND",ids);
        try {
            var source=object(trace,sourceId);var target=object(trace,targetId);
            var associations=trace.bySemanticId(referenceIdentity).stream()
                .filter(r->r.targetKind().equals("ASSOCIATION") && resolved(r)).toList();
            if(associations.size()!=1) throw new IllegalArgumentException("CROSS_RELATION_TRACE_MISSING_OR_AMBIGUOUS");
            var association=system.model().getAssociation(associations.getFirst().targetUseId().substring("association:".length()));
            var a=system.state().objectByName(source);var b=system.state().objectByName(target);
            if(association==null || a==null || b==null) throw new IllegalArgumentException("CROSS_RELATION_TARGET_MISSING");
            boolean linked=system.state().hasLinkBetweenObjects(association,a,b);
            return result(rule,linked ? VerificationOutcome.PASS : VerificationOutcome.FAIL,source,
                linked ? "Exact source relation is materialized" : "Exact source relation is absent; similar names cannot substitute",ids);
        } catch(RuntimeException error) {
            return result(rule,VerificationOutcome.ERROR,null,error.getMessage(),ids);
        }
    }

    public VerificationResult unsupportedRuntime(String capability, org.tzi.use.plugins.jacamo.runtime.RuntimeEvent event) {
        return new VerificationResult("CROSS_RUNTIME_UNSUPPORTED:"+capability,VerificationOutcome.SKIPPED,null,
            "UNSUPPORTED: exact cross-runtime correlation/delivery or instance semantics not established", "",
            event.semanticSourceId()==null ? List.of() : List.of(event.semanticSourceId()),event.correlationId(),List.of(event.eventId()));
    }
    private String object(TraceIndex trace,String id) {
        var candidates=trace.bySemanticId(id).stream().filter(r->r.targetKind().equals("OBJECT") && resolved(r)).toList();
        if(candidates.size()!=1) throw new IllegalArgumentException("CROSS_OBJECT_TRACE_MISSING_OR_AMBIGUOUS:"+id);
        var target=candidates.getFirst().targetUseId();
        if(!target.startsWith("object:")) throw new IllegalArgumentException("CROSS_OBJECT_TARGET_KIND");
        return target.substring("object:".length());
    }
    private boolean resolved(TraceRecord record) { return record.status()==TraceRecord.Status.RESOLVED || record.status()==TraceRecord.Status.PROJECTED; }
    private VerificationResult result(String id,VerificationOutcome outcome,String object,String explanation,List<String> ids) {
        return new VerificationResult(id,outcome,object,explanation,"",ids,null,List.of());
    }
}
