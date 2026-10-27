package org.jacamo.bridge.contract;

import java.util.*;

/** Complete local CArtAgO capture for its explicitly declared fact kinds, on the ordered event source.
 * It makes no claim about an atomic cut across other frameworks. */
public final class CartagoSourceCut {
    public static final String KIND="APPLY_CARTAGO_SOURCE_CUT",VERSION="CARTAGO_SOURCE_CUT/1.0";
    private CartagoSourceCut(){ }
    public static Map<String,Object> payload(RuntimeSnapshot snapshot) {
        return Map.of("normalizedEventKind",KIND,"sourceCutVersion",VERSION,"sourceCut",ContractPayloads.runtime(snapshot));
    }
    public static RuntimeSnapshot require(RuntimeEvent event) {
        var after=event.after();
        if(!after.keySet().equals(Set.of("normalizedEventKind","sourceCutVersion","sourceCut"))||!KIND.equals(after.get("normalizedEventKind"))||!VERSION.equals(after.get("sourceCutVersion"))
                ||!"cartago".equals(event.sourceId())||!"cartago".equals(event.subsystem())||event.kind()!=RuntimeEventKind.CHANGED||event.factKind()!=RuntimeFactKind.RUNTIME_EVENT
                ||event.entityId()==null||!event.entityId().authority().equals("cartago")||!event.entityId().kind().equals("source-cut")||!event.entityId().incarnation().equals(event.sessionId())
                ||event.completeness()!=Completeness.COMPLETE||event.projectionStatus()!=ProjectionStatus.EVIDENCE_ONLY)throw invalid("SOURCE_OR_SHAPE");
        var cut=ContractPayloads.runtime(CanonicalJson.object(after.get("sourceCut")));
        var mark=new SourceWatermark("cartago",event.sourceSequence());
        SnapshotAdmission.require(cut,Map.of());
        if(!cut.modelRevision().equals(event.modelRevision())||!cut.startWatermarks().equals(Map.of("cartago",mark))||!cut.endWatermarks().equals(Map.of("cartago",mark))
                ||!cut.sourceCompleteness().equals(Map.of("cartago",Completeness.COMPLETE))||cut.captureEndedAt().isAfter(event.observedAt())||cut.facts().size()>16384
                ||cut.facts().stream().map(f->f.id().canonical()).distinct().count()!=cut.facts().size()||cut.facts().stream().anyMatch(f->!f.id().authority().equals("cartago")))throw invalid("CUT_SCOPE_OR_POPULATION");
        var kinds=kinds(cut);var supplemental=supplemental(cut);
        if(cut.facts().stream().anyMatch(f->!kinds.contains(f.kind())&&!(supplemental.contains(f.kind())&&f.completeness()==Completeness.COMPLETE&&f.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY)))throw invalid("FACT_OUTSIDE_DECLARED_CAPTURE");
        return cut;
    }
    public static Set<RuntimeFactKind> supplemental(RuntimeSnapshot cut) {
        Object raw=cut.captureMetadata().get("supplementalFactKinds");if(raw==null)return Set.of();
        if(!(raw instanceof List<?> names)||!(names.isEmpty()||names.equals(List.of("OPERATION"))))throw invalid("SUPPLEMENTAL_SCOPE_UNSUPPORTED");
        return names.isEmpty()?Set.of():Set.of(RuntimeFactKind.OPERATION);
    }
    public static Set<RuntimeFactKind> kinds(RuntimeSnapshot cut) {
        Object raw=cut.captureMetadata().get("capturedFactKinds");if(raw==null)return Set.of(RuntimeFactKind.values());
        if(!(raw instanceof List<?> names)||names.isEmpty()||new HashSet<>(names).size()!=names.size())throw invalid("CAPTURE_KINDS_INVALID");
        var result=EnumSet.noneOf(RuntimeFactKind.class);
        try{for(Object name:names)result.add(RuntimeFactKind.valueOf((String)name));}catch(RuntimeException invalid){throw invalid("CAPTURE_KIND_UNSUPPORTED");}
        return Set.copyOf(result);
    }
    private static ContractException invalid(String reason){return new ContractException("CARTAGO_SOURCE_CUT:"+reason);}
}
