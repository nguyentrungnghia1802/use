package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol.Check;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MSystem;

/** Component-level evidence inspection only. Intentional evaluation and release
 * authority remain with RuntimeEngineSessionOwner and VerifiedCheckpointInventory.
 * This class does not implement an alternative iStar recurrence or release protocol. */
public final class NativeVerificationCoordinator {
    public record InvariantResult(String invariantName,String className,String verdict,String diagnostic,List<String> violatingObjects) {
        public InvariantResult {violatingObjects=List.copyOf(violatingObjects);}
    }
    public record VerificationAssessment(String cutId,boolean allPassed,boolean hasFailures,boolean hasUnknowns,
            Map<String,Check> checkpointChecks,List<InvariantResult> invariantResults,
            VerifiedCheckpointProtocol.Action recommendedAction,Map<String,String> intentionalVerdicts) {
        public VerificationAssessment {checkpointChecks=Map.copyOf(checkpointChecks);invariantResults=List.copyOf(invariantResults);intentionalVerdicts=Map.copyOf(intentionalVerdicts);}
    }
    public static VerificationAssessment evaluate(CompiledObservationPlan plan,MSystem system,PhysicalCutFact cut) {
        Objects.requireNonNull(plan);Objects.requireNonNull(system);Objects.requireNonNull(cut);
        var checks=new LinkedHashMap<String,Check>();var results=new ArrayList<InvariantResult>();boolean failed=false;
        for(var inv:system.model().classInvariants()) {
            String truth="PASS",reason="NATIVE_INVARIANT_EVALUATED";var objects=new ArrayList<String>();
            for(var obj:system.state().allObjects())if(obj.cls().conformsTo(inv.cls())) {
                try {
                    var vars=new VarBindings();vars.push("self",obj.value());
                    var value=new Evaluator().eval(inv.bodyExpression(),system.state(),vars);
                    if(value instanceof BooleanValue b) {if(!b.value()){truth="FAIL";objects.add(obj.name());}}
                    else if(!truth.equals("FAIL")){truth="UNKNOWN";reason="NATIVE_INVARIANT_UNDEFINED";}
                } catch(RuntimeException error) {if(!truth.equals("FAIL"))truth="ERROR";reason="NATIVE_INVARIANT_EVALUATION_ERROR:"+error.getClass().getSimpleName();}
            }
            failed|=truth.equals("FAIL");
            results.add(new InvariantResult(inv.name(),inv.cls().name(),truth,reason,objects));
            checks.put("csl.inv:"+inv.qualifiedName(),new Check(truth,"APPLICABLE",reason));
        }
        // Neither a source receipt nor a successful invariant evaluation is a
        // native intentional result or a population-completeness certificate.
        // In particular, absent receipts/statuses cannot authorize a release.
        for(var rule:plan.checkpointRules())checks.put(rule.id(),new Check("UNKNOWN","APPLICABLE","NATIVE_CHECKPOINT_INVENTORY_REQUIRED"));
        checks.put(VerifiedCheckpointProtocol.REQUIRED_CHECK,new Check(failed?"FAIL":"UNKNOWN","APPLICABLE",
                failed?"NATIVE_INVARIANT_VIOLATION":"NATIVE_CHECKPOINT_INVENTORY_REQUIRED"));
        return new VerificationAssessment(cut.cutId(),false,failed,!failed,checks,results,
                failed?VerifiedCheckpointProtocol.Action.HOLD_FAILURE:VerifiedCheckpointProtocol.Action.HOLD_UNVERIFIED,Map.of());
    }
    private NativeVerificationCoordinator() {}
}
