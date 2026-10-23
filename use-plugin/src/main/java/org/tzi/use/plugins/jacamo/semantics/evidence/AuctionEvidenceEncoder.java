package org.tzi.use.plugins.jacamo.semantics.evidence;

import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.uml.sys.MObject;

/** Case provenance copied on the native writer. It neither evaluates a
 * predicate nor reads mutable native state later from the UI/engine lane. */
public final class AuctionEvidenceEncoder {
    private AuctionEvidenceEncoder(){ }
    public static Map<String,String> element(SemanticProgram program,String element,String actor) {
        var result=new TreeMap<String,String>();result.put("sourceCell",element);
        program.bindings().bindings().stream().filter(b->b.targetId().equals(element)).findFirst().ifPresent(b->result.put("sourceRule",b.ruleId()));
        var goalPolicy=program.casePlan().specification().root().child("verificationBindings").child("goalEvaluation");
        if(!result.containsKey("sourceRule")&&Arrays.asList(goalPolicy.attr("istarCells").split("\\s+")).contains(element))result.put("sourceRule","verificationBindings/goalEvaluation");
        program.casePlan().actors().stream().filter(a->a.actor().equals(actor)).findFirst().ifPresent(a->result.put("sourceActorCell",a.cell()));
        return Map.copyOf(result);
    }
    public static Map<String,String> invariant(NativeRuntimeMutationEngine engine,MObject object,String invariant) {
        var result=new TreeMap<String,String>();var program=engine.canonicalProgram();
        result.put("casePlanHash",program.casePlan().hash());result.put("sourceCell",invariant);
        for(var node:program.casePlan().specification().root().child("verificationBindings").children("externalInvariant"))
            if(invariant.endsWith("::"+node.attr("name")))result.put("sourceRule","verificationBindings/externalInvariant[name="+node.attr("name")+"]");
        var fields=new LinkedHashMap<String,String>();
        if(object.cls().name().equals("AuctionMonitor")) {
            fields.put("scheme","auctionMonitorScheme");fields.put("artifact","auctionMonitorArtifact");fields.put("sourceCutHash","auctionMonitorCut");
            fields.put("rawObservationQuality","auctionMonitorHistoryQuality");fields.put("observerLifecycle","auctionMonitorLifecycle");fields.put("clockProof","auctionMonitorClock");
        }else if(object.cls().name().equals("AuctionObligation")) {
            result.put("obligation",engine.metadata(object,"semanticId"));
            fields.put("scheme","auctionObligationScheme");fields.put("artifact","auctionObligationArtifact");fields.put("playerName","auctionObligationPlayerName");
            fields.put("activation","auctionObligationActivation");fields.put("rawTimes","auctionObligationRawTimes");fields.put("sourceNormState","normReportedState");
            fields.put("rawObservationQuality","auctionObligationPlayerQuality");fields.put("sourceLifecycleVerdict","normLifecycleVerdict");fields.put("historyGap","normHistoryGap");
        }
        fields.forEach((name,key)->{String value=engine.metadata(object,key);if(!value.isBlank())result.put(name,value);});
        return Map.copyOf(result);
    }
    /** Physical origins are deployment evidence, not semantic fingerprints:
     * an offline replay may legitimately run from a different install path. */
    public static Map<String,String> currentClassOrigins() {
        var result=new TreeMap<String,String>();
        for(Class<?> type:List.of(org.vnu.sme.goal.verify.conformance.semantics.GoalMarking.class,org.vnu.sme.goal.verify.conformance.semantics.TaskMarking.class,org.vnu.sme.goal.verify.conformance.semantics.IStarMarking.class,org.vnu.sme.goal.verify.conformance.semantics.IStarPropagation.class)) {
            var source=type.getProtectionDomain().getCodeSource();result.put(type.getName(),source==null?"UNAVAILABLE":source.getLocation().toExternalForm());
        }
        return Map.copyOf(result);
    }
}
