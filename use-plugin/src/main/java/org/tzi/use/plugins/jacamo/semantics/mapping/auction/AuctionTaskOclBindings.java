package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.analysis.coverage.CoverageCalculationVisitor;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.semantics.ocl.ConstraintCoverageResolver;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.type.*;
import org.tzi.use.uml.ocl.value.*;
import org.vnu.sme.goal.dsl.istar.mm.Task;

/** Case binding only. Original predicates and native evaluator are reused;
 * this class owns neither source execution, marking history nor runtime state. */
public final class AuctionTaskOclBindings implements AutoCloseable {
    public enum Kind { CARTAGO_OPERATION, JASON_STEP_COMPOSITE }
    public record Slots(String task,Kind kind,NativeOclBackend.Compiled pre,NativeOclBackend.Compiled post,List<String> operations) {
        public Slots {operations=List.copyOf(operations);}
    }
    private final SemanticProgram program;private final MModel model;private final TupleType stepType;
    private final AuctionStepObservationDecoder decoder;private final Map<String,Slots> slots;
    private final AuctionOccurrenceBindingAdapter casts;
    private volatile boolean closed;
    private final ConstraintCoverageResolver coverage=new ConstraintCoverageResolver();

    public AuctionTaskOclBindings(SemanticProgram program,MModel model,NativeOclBackend backend) {
        this.program=program;this.model=model;program.assertUnchanged();
        if(program.casePlan()==null||!program.hash().equals(backend.program()))throw invalid("PROGRAM_OWNER");
        decoder=new AuctionStepObservationDecoder(program);casts=new AuctionOccurrenceBindingAdapter(program);stepType=new AuctionObserverUseBinding(program.casePlan().specification()).valueType("view-step");
        if(!stepType.getParts().keySet().equals(Set.of("enabled","succeeded"))||stepType.getParts().values().stream().anyMatch(p->!p.type().equals(TypeFactory.mkBoolean())))throw invalid("UNSUPPORTED:STEP_VALUE_TYPE");
        var result=new TreeMap<String,Slots>();
        for(var binding:program.casePlan().tasks()) {
            var task=(Task)program.goalModel().findElement(binding.task()).orElseThrow();
            var variables=new TreeMap<String,Type>();if(!binding.observerViews().isEmpty())variables.put("step",stepType);
            var operations=binding.operations().stream().map(id->program.casePlan().specification().requireId(id).attr("sourceOperation")).toList();
            for(String name:operations) {
                var operation=model.getClass(binding.selfDeclaration()).operation(name,false);if(operation==null)throw invalid("NATIVE_OPERATION_REQUIRED:"+name);
                // The XML/static interface seam already verifies source aliases.
                // Only authored task variables are exposed, never an invented result.
                for(int i=0;i<operation.paramList().size();i++){var parameter=operation.paramList().varDecl(i);if(parameter.name().equals("service"))variables.put(parameter.name(),parameter.type());}
            }
            var pre=compile(backend,binding,task.preconditions().getFirst().oclBody(),true,variables);
            var post=compile(backend,binding,task.postconditions().getFirst().oclBody(),false,variables);
            result.put(task.id(),new Slots(task.id(),binding.observerViews().isEmpty()?Kind.CARTAGO_OPERATION:Kind.JASON_STEP_COMPOSITE,pre,post,operations));
        }
        if(result.size()!=6)throw invalid("TASK_SLOT_COVERAGE");slots=new java.util.concurrent.ConcurrentSkipListMap<>(result);
    }
    private NativeOclBackend.Compiled compile(NativeOclBackend backend,AuctionBindingPlan.TaskBinding binding,String body,boolean pre,Map<String,Type> candidates) {
        String id="AUCTION:TASK:"+binding.task()+":"+(pre?"pre":"post");var kind=pre?OclContractSpec.Kind.TASK_PRE:OclContractSpec.Kind.TASK_POST;
        var probe=new OclContractSpec(id,kind,model.getClass(binding.selfDeclaration()),null,body,candidates);
        return org.tzi.use.plugins.jacamo.semantics.ocl.NativeExpressionInputs.compile(backend,model,probe);
    }
    public Map<String,Slots> slots(){if(closed)throw invalid("CLOSED");return Map.copyOf(slots);}
    public int expressionCount(){return slots.size()*2;}
    @Override public void close(){closed=true;slots.clear();}

    /** Writer-only preparation. The supplied view is borrowed from the existing
     * native owner; evaluation can later use NativeInvocationEvaluation on its lane. */
    public NativeInvocationEvaluation.Condition prepare(String task,boolean pre,NativeRuntimeMutationEngine engine,
            AuctionOccurrenceBindingAdapter.Scope scope,AuctionOccurrenceBindingAdapter.Player actor,AuctionArtifactBindingResolver.Run run,
            VerificationObservation observation,UseStateReadView view,Map<String,Completeness> sources,boolean lost) {
        if(closed)throw invalid("CLOSED");var definition=slots.get(task);if(definition==null)throw invalid("TASK_UNRESOLVED:"+task);
        var compiled=pre?definition.pre():definition.post();
        if(engine.system().model()!=model||view.after().system()!=engine.system())throw invalid("NATIVE_OWNER");
        var self=engine.objectForSemanticId(scope.artifact());var player=engine.objectForSemanticId(actor.player());
        var variables=new TreeMap<String,Value>();variables.put("self",self==null?UndefinedValue.instance:self.value());
        String reason="";AuctionStepObservationDecoder.Step step=null;
        var witness=new TreeMap<String,String>();witness.put("targetTaskId",task);witness.put("bindingKind",definition.kind().name());witness.put("scope",scope.id());
        witness.put("scheme",scope.scheme());witness.put("artifact",scope.artifact());witness.put("player",actor.player());witness.put("role",actor.role());witness.put("roleInterval",actor.roleInterval());witness.put("programHash",program.hash());
        witness.putAll(AuctionEvidenceEncoder.element(program,task,actor.sourceActor()));
        if(lost)reason="COVERAGE_LOST";
        else if(!scope.program().equals(program.hash())||!scope.complete()||!scope.actors().contains(actor)
                ||casts.resolve(engine,run).stream().noneMatch(scope::equals))reason="EXACT_TASK_CAST_UNAVAILABLE";
        else if(self==null||player==null||!"RESOLVED".equals(engine.metadata(self,"auctionExistence")))reason="EXACT_TASK_RESOURCE_UNAVAILABLE";
        else if(observation==null)reason="EXACT_STEP_OBSERVATION_UNAVAILABLE";
        else try {
            step=decoder.decode(observation,run.id(),run.generation(),run.modelRevision());
            if(!task.equals(step.task())||!actor.sourceActor().equals(program.casePlan().tasks().stream().filter(t->t.task().equals(task)).findFirst().orElseThrow().actor()))reason="TASK_OR_ACTOR_BINDING_MISMATCH";
            else if(engine.objectForSemanticId(step.agent())!=player)reason="EXACT_STEP_PLAYER_MISMATCH";
            else if(!scope.scheme().equals(step.scheme()))reason="EXACT_STEP_SCHEME_MISMATCH";
            else if(!step.artifact().isBlank()&&engine.objectForSemanticId(step.artifact())!=self)reason="EXACT_STEP_ARTIFACT_MISMATCH";
            else if(step.quality()!=Completeness.COMPLETE&&!qualifiedTupleValue(step,compiled,pre))reason="STEP_SOURCE_"+step.quality()+":"+step.diagnostic();
            else if(pre&&!step.phase().equals("ENABLED")||!pre&&step.phase().equals("ENABLED"))reason="EXACT_STEP_PHASE_UNAVAILABLE";
            else if(!pre&&definition.kind()==Kind.CARTAGO_OPERATION&&step.phase().equals("FAILED"))reason="SOURCE_REPORTED_OPERATION_FAILURE_POST_NOT_EVALUATED";
            else if(!pre&&step.phase().equals("SUCCEEDED")&&!definition.operations().isEmpty()&&!operationReceipt(step,definition.operations()))reason="EXACT_SOURCE_ACTION_RECEIPT_UNAVAILABLE";
            if(reason.isBlank()&&!pre&&view.before()==null)reason="POST_MATCHING_PRE_UNAVAILABLE";
        }catch(IllegalArgumentException rejected){reason="ERROR:STEP_BINDING_PROOF_REJECTED:"+rejected.getMessage();}
        if(step!=null) {
            witness.put("goalInvocation",step.goalInvocation());witness.put("stepInvocation",step.invocation());witness.put("sourceActionCorrelation",step.operation());witness.put("sourceActionNamespace","JASON_ACTIONEXEC_IDENTITY_NOT_CARTAGO_OPID");witness.put("phase",step.phase());
            witness.put("rawStepFrameQuality",step.quality().name());witness.put("rawStepFrameDiagnostic",step.diagnostic());witness.put("stepValueQuality",new String(CanonicalJson.encode(step.bindings().getOrDefault("stepValueQuality",Map.of())),java.nio.charset.StandardCharsets.UTF_8));
            if(step.quality()!=Completeness.COMPLETE&&reason.isBlank())witness.put("tupleDependencyAdmission","COMPLETE_SOURCE_CALLBACK_PLUS_EXACT_NATIVE_SCHEME_PLAYER_RESOURCE;RAW_FRAME_REMAINS_PARTIAL");
            if(compiled.variables().containsKey("step"))variables.put("step",tuple(step));
            if(compiled.variables().containsKey("service")) {
                var service=service(step);if(service==null){variables.put("service",UndefinedValue.instance);if(reason.isBlank())reason="EXACT_START_SERVICE_INPUT_UNAVAILABLE";}else variables.put("service",new StringValue(service));
            }
        }
        if(reason.isBlank())reason=coverage.unavailableSelf(compiled.expression(),engine,self,sources,false);
        if(self!=null)witness.putAll(coverage.witness(compiled.expression(),engine,self,sources,reason));
        else {witness.put("observationQuality","UNAVAILABLE");witness.put("coverageReason",reason);}
        if(observation!=null)witness.put("stepObservationHash",SemanticJson.hash(observation.data()));
        var before=view.before()==null?view.after():view.before();witness.put("taskPredicateId",compiled.specification().id());
        String id=compiled.specification().id()+":"+SemanticJson.hash(List.of(scope.id(),actor.instance(),step==null?"missing":step.invocation()));
        return new NativeInvocationEvaluation.Condition(id,pre?"TASK_PRE":"TASK_POST",compiled.specification().self().name(),self==null?"":self.name(),scope.artifact(),compiled.specification().body(),compiled.expression(),before,view.after(),variables,reason,ConstraintCoverageResolver.unavailableVerdict(reason),step==null?"":step.invocation(),witness);
    }
    private TupleValue tuple(AuctionStepObservationDecoder.Step step) {
        var parts=new ArrayList<TupleValue.Part>();
        for(var part:stepType.getParts().values()) {
            Value value=part.name().equals("enabled")?BooleanValue.TRUE:step.phase().equals("ENABLED")?UndefinedValue.instance:BooleanValue.get(step.phase().equals("SUCCEEDED"));
            parts.add(new TupleValue.Part(part.getPosition(),part.name(),value));
        }
        return new TupleValue(stepType,parts);
    }
    private static boolean qualifiedTupleValue(AuctionStepObservationDecoder.Step step,NativeOclBackend.Compiled expression,boolean pre) {
        if(step.quality()!=Completeness.PARTIAL||!step.diagnostic().equals("EXACT_ARTIFACT_REFERENCE_UNAVAILABLE")||!step.artifact().isBlank()||!(step.bindings().get("stepValueQuality") instanceof Map<?,?> raw))return false;
        if(pre&&!step.phase().equals("ENABLED")||!pre&&!step.phase().equals("FAILED"))return false;
        var quality=CanonicalJson.object(raw);if(!"AUCTION_STEP_VALUES/1.0".equals(quality.get("version"))||!"COMPLETE_CALLBACK".equals(quality.get(pre?"enabled":"succeeded")))return false;
        var dependencies=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.dependencyVisitor();expression.expression().processWithVisitor(dependencies);
        return expression.specification().bindings().keySet().equals(Set.of("step"))&&dependencies.attributeReads().isEmpty()&&dependencies.populationTypes().isEmpty();
    }
    private static boolean operationReceipt(AuctionStepObservationDecoder.Step step,List<String> operations) {
        if(step.operation().isBlank()||!(step.bindings().get("actions") instanceof List<?> receipts))return false;
        return receipts.stream().map(CanonicalJson::object).anyMatch(r->step.operation().equals(r.get("correlation"))&&"JASON_ACTIONEXEC_IDENTITY".equals(r.get("authority"))&&"SUCCEEDED".equals(r.get("result"))&&operations.contains(r.get("operationName")));
    }
    private static String service(AuctionStepObservationDecoder.Step step) {
        if(!(step.bindings().get("serviceInput") instanceof Map<?,?> raw)||!(step.bindings().get("values") instanceof Map<?,?> values))return null;
        var proof=CanonicalJson.object(raw);var value=proof.get("value");
        if(!"JACA_PUBLIC_TERM_TO_OBJECT_START_VARIABLE_V1".equals(proof.get("authority"))||!"java.lang.String".equals(proof.get("javaType"))||!(value instanceof String string)
                ||!SemanticJson.hash(values.get("S")).equals(proof.get("sourceTermHash")))return null;
        return string;
    }
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("AUCTION_TASK_BINDING_"+reason);}
}
