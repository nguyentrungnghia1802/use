package org.tzi.use.plugins.jacamo.semantics.mapping.ocl;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.type.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.semantic.engine.use75.*;

/** XML-selected original Task predicates on borrowed native cuts. The caller supplies
 * a source-qualified decoder result and owns PRE/terminal pairing and ordered evaluation. */
public final class XmlTaskOclBindings implements AutoCloseable {
    public record Slots(String task,Node definition,NativeOclBackend.Compiled pre,NativeOclBackend.Compiled post,List<String> operations) {public Slots{operations=List.copyOf(operations);}}
    private final SemanticProgram program;
    private final MModel model;
    private final Map<String,Slots> slots;
    private volatile boolean closed;
    private final ConstraintCoverageResolver coverage=new ConstraintCoverageResolver();
    public XmlTaskOclBindings(SemanticProgram program,MModel model,NativeOclBackend backend) {
        this.program=program;this.model=model;
        if(program.xmlPlan()==null||!backend.program().equals(program.hash()))throw new IllegalArgumentException("XML_TASK_PROGRAM_OWNER_MISMATCH");
        var contracts=new XmlIntentionalOclBindings().compile(program,model).contracts();var result=new TreeMap<String,Slots>();var spec=program.xmlPlan().inputs().specification();
        for(Node node:spec.root().child("bindings").children("elementRuntime"))if(program.goalModel().findElement(node.attr("target")).orElse(null) instanceof org.vnu.sme.goal.dsl.istar.mm.Task) {
            String task=node.attr("target");
            if(node.optional("stepScope").isBlank()) {
                if(!node.children("actionReceipt").isEmpty()||List.of("scopePolicy","entryPolicy","failurePolicy","stepSchemeColumn","stepArtifactColumn").stream().anyMatch(k->!node.optional(k).isBlank()))throw node.location().error("XML_TASK_STEP_SCOPE_REQUIRED");
                continue; // Unconfigured Tasks remain unavailable; no actor/operation fallback.
            }
            var scope=spec.requireId(node.attr("stepScope"));
            if(!scope.name().equals("scope")||!Set.of("UNIQUE_CURRENT_PLAYER_SCOPE","EXACT_REFERENCES").contains(node.optional("scopePolicy"))
                    ||!Set.of("EXACT_SELF","ALLOW_UNBOUND_SELF_FOR_PURE_CALLBACK").contains(node.optional("entryPolicy"))
                    ||!Set.of("SKIP_WORLD_POST","EVALUATE_PURE_CALLBACK_POST").contains(node.optional("failurePolicy")))throw node.location().error("UNSUPPORTED:XML_TASK_SCOPE_OR_ENTRY_POLICY");
            var fields=RuntimeTables.outputColumns(spec.requireId(scope.attr("tableRef")));
            for(String key:List.of("stepSchemeColumn","stepArtifactColumn"))if(!node.optional(key).isBlank()&&!fields.contains(node.attr(key)))throw node.location().error("XML_TASK_REFERENCE_COLUMN_UNRESOLVED");
            boolean references=!node.optional("stepSchemeColumn").isBlank()||!node.optional("stepArtifactColumn").isBlank();
            if(references!=node.attr("scopePolicy").equals("EXACT_REFERENCES"))throw node.location().error("XML_TASK_REFERENCE_POLICY_MISMATCH");
            var pre=contracts.stream().filter(c->c.elementId().equals(task)&&c.specification().kind()==OclContractSpec.Kind.TASK_PRE).findFirst().orElseThrow();
            var post=contracts.stream().filter(c->c.elementId().equals(task)&&c.specification().kind()==OclContractSpec.Kind.TASK_POST).findFirst().orElseThrow();
            for(Node variable:node.children("variable")) {
                String origin=variable.attr("origin");
                if(!Set.of("PLAYER","ROLE","CONTEXT","SELF","STEP_TUPLE").contains(origin)&&!origin.startsWith("STEP_INPUT:")&&!origin.startsWith("SCOPE_COLUMN:"))throw variable.location().error("UNSUPPORTED:XML_TASK_VARIABLE_ORIGIN");
                if(origin.startsWith("SCOPE_COLUMN:")&&!fields.contains(origin.substring("SCOPE_COLUMN:".length())))throw variable.location().error("XML_TASK_VARIABLE_COLUMN_UNRESOLVED");
                if(origin.equals("STEP_TUPLE")) {
                    Node view=spec.requireId(variable.attr("viewRef"));
                    if(!view.attr("category").equals("TUPLE")||view.children("field").stream().anyMatch(f->!f.attr("type").equals("Boolean")||!Set.of("STEP_ENABLED","STEP_SUCCEEDED").contains(f.attr("origin"))))throw variable.location().error("UNSUPPORTED:XML_TASK_CALLBACK_VIEW");
                }
            }
            var operations=new ArrayList<String>();var selected=new HashSet<String>();
            for(Node receipt:node.children("actionReceipt")) {
                Node operation=spec.requireId(receipt.attr("operationRef"));
                if(!selected.add(operation.attr("id"))||!operation.name().equals("operation")||!operation.attr("owner").equals(node.attr("self"))||!operation.optional("task").equals(task))throw receipt.location().error("TASK_EXACT_OPERATION_BINDING_REQUIRED");
                var signature=CanonicalJson.decode(operation.attr("signature").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var installed=model.getClass(node.attr("self")).allOperations().stream().filter(o->operation.attr("id").equals(o.getAnnotationValue("DeclarativeOperationBinding","id"))&&spec.sourceHash().equals(o.getAnnotationValue("DeclarativeOperationBinding","mappingHash"))).toList();
                if(installed.size()!=1||!(signature instanceof List<?> list)||list.size()!=4||!(list.get(1) instanceof String name)||operations.contains(name))throw receipt.location().error("UNSUPPORTED:TASK_NATIVE_OPERATION_UNRESOLVED_OR_ACTION_OVERLOAD_AMBIGUOUS");
                operations.add((String)((List<?>)signature).get(1));
            }
            result.put(task,new Slots(task,node,NativeExpressionInputs.compile(backend,model,pre.specification()),NativeExpressionInputs.compile(backend,model,post.specification()),operations));
        }
        slots=new java.util.concurrent.ConcurrentSkipListMap<>(result);
    }
    public Map<String,Slots> slots(){if(closed)throw new IllegalStateException("XML_TASK_BINDINGS_CLOSED");return Map.copyOf(slots);}
    public int expressionCount(){return slots.size()*2;}
    @Override public void close(){closed=true;slots.clear();}
    public boolean permitsLateSelf(String task,NativeInvocationEvaluation.Condition entry) {
        var slot=slots.get(task);return slot!=null&&slot.definition().attr("entryPolicy").equals("ALLOW_UNBOUND_SELF_FOR_PURE_CALLBACK")&&entry.unavailable().isBlank()&&entry.expression()==slot.pre().expression()&&entry.variables().get("self").isUndefined()&&pureCallback(slot.pre(),slot.definition());
    }
    public boolean references(String task,OccurrenceProjection.Scope scope,JasonStepCutDecoder.Step step,NativeRuntimeMutationEngine engine,boolean pre){var slot=slots.get(task);return slot!=null&&scope.definition().equals(slot.definition().attr("stepScope"))&&references(slot.definition(),scope,step,engine,pre);}
    public NativeInvocationEvaluation.Condition prepare(String task,boolean pre,NativeRuntimeMutationEngine engine,OccurrenceProjection.Scope scope,OccurrenceProjection.Member member,
            TaskDemandProjection.Demand demand,JasonStepCutDecoder.Step step,UseStateReadView view,Map<String,Completeness> sources,boolean lost) {
        if(closed)throw new IllegalStateException("XML_TASK_BINDINGS_CLOSED");program.assertUnchanged();var slot=slots.get(task);if(slot==null)throw new IllegalArgumentException("UNSUPPORTED:XML_TASK_OBSERVATION_BINDING_REQUIRED:"+task);
        var compiled=pre?slot.pre():slot.post();var node=slot.definition();
        if(engine.system().model()!=model||view.after().system()!=engine.system())throw new IllegalArgumentException("XML_TASK_NATIVE_OWNER_MISMATCH");
        var variables=new TreeMap<String,Value>();var witness=new TreeMap<String,String>();
        witness.put("targetTaskId",task);witness.put("scope",scope.id());witness.put("player",member.player());witness.put("role",member.role());witness.put("roleInterval",member.interval());witness.put("mappingPlanHash",program.xmlPlan().hash());
        var self=engine.objectForSemanticId(member.selves().getOrDefault(task,""));variables.put("self",self==null?UndefinedValue.instance:self.value());String reason="";
        if(lost)reason="COVERAGE_LOST";
        else if(step==null)reason="EXACT_STEP_OBSERVATION_UNAVAILABLE";
        else if(!scope.complete()||!scope.definition().equals(node.attr("stepScope"))||!scope.members().contains(member)||!program.goalBindings().elements().get(task).actorId().equals(member.actor()))reason="EXACT_TASK_CAST_UNAVAILABLE";
        else if(engine.objectForSemanticId(step.agent())!=engine.objectForSemanticId(member.player()))reason="EXACT_STEP_PLAYER_MISMATCH";
        else if(!step.task().equals(task))reason="EXACT_TASK_SOURCE_SITE_MISMATCH";
        else if(demand==null||!demand.target().equals(task)||!demand.scope().equals(scope.id())||!demand.player().equals(member.player())||!demand.role().equals(member.role())||!demand.roleInterval().equals(member.interval())||demand.quality()!=Completeness.COMPLETE||!Boolean.TRUE.equals(demand.required())||demand.episode().isBlank())reason="EXACT_TASK_DEMAND_UNAVAILABLE";
        if(step!=null) {
            witness.put("stepInvocation",step.invocation());witness.put("goalInvocation",step.goalInvocation());witness.put("sourceActionCorrelation",step.operation());witness.put("sourceActionNamespace","JASON_ACTIONEXEC_IDENTITY_NOT_CARTAGO_OPID");witness.put("phase",step.phase());witness.put("rawStepQuality",step.quality().name());witness.put("stepBindingHash",SemanticJson.hash(step.bindings()));
            try {
                var captured=CanonicalJson.object(step.bindings().get("capturedRun"));String run=(String)captured.get("sessionId"),revision=(String)captured.get("modelRevision");Object rawGeneration=captured.get("generation");if(!(rawGeneration instanceof Number))throw new IllegalArgumentException("GENERATION_REQUIRED");long generation=new java.math.BigDecimal(rawGeneration.toString()).longValueExact();
                var current=engine.xmlOccurrences(run,generation,revision);
                if(reason.isBlank()&&current.stream().noneMatch(scope::equals))reason="EXACT_CURRENT_TASK_CAST_UNAVAILABLE";
                if(reason.isBlank()&&engine.xmlTaskDemands(run,generation,revision).stream().noneMatch(demand::equals))reason="EXACT_CURRENT_TASK_DEMAND_UNAVAILABLE";
                if(reason.isBlank()) {
                    long count=current.stream().filter(s->s.complete()&&s.definition().equals(node.attr("stepScope"))&&references(node,s,step,engine,pre)).flatMap(s->s.members().stream()).filter(m->m.actor().equals(member.actor())&&m.player().equals(member.player())).count();
                    if(count!=1||!references(node,scope,step,engine,pre))reason="EXACT_STEP_SCOPE_AMBIGUOUS_OR_UNAVAILABLE";
                }
            }catch(RuntimeException invalid){if(reason.isBlank())reason="ERROR:STEP_EPOCH_OR_CURRENT_BINDING_REJECTED:"+invalid.getMessage();}
            if(reason.isBlank()&&(pre&&!step.phase().equals("ENABLED")||!pre&&step.phase().equals("ENABLED")))reason="EXACT_STEP_PHASE_UNAVAILABLE";
            boolean pure=pureCallback(compiled,node);
            if(reason.isBlank()&&step.quality()!=Completeness.COMPLETE&&!(pure&&step.quality()==Completeness.PARTIAL&&callbackAuthority(step,pre)))reason="STEP_SOURCE_"+step.quality();
            if(reason.isBlank()&&self==null&&!(pre&&node.attr("entryPolicy").equals("ALLOW_UNBOUND_SELF_FOR_PURE_CALLBACK")&&pure))reason="EXACT_NATIVE_TASK_SELF_UNAVAILABLE";
            if(reason.isBlank()&&!pre&&step.phase().equals("FAILED")&&(!node.attr("failurePolicy").equals("EVALUATE_PURE_CALLBACK_POST")||!pure))reason="SOURCE_FAILURE_WORLD_POST_UNAVAILABLE";
            if(reason.isBlank()&&!pre&&step.phase().equals("SUCCEEDED")&&!operationReceipts(step,slot.operations()))reason="EXACT_SUCCESSFUL_SOURCE_ACTION_RECEIPT_UNAVAILABLE";
            for(Node binding:node.children("variable"))if(compiled.specification().bindings().containsKey(binding.attr("name"))) {
                String name=binding.attr("name"),origin=binding.attr("origin");Type type=compiled.specification().bindings().get(name);Value value=UndefinedValue.instance;
                switch(origin) {
                    case "PLAYER","ROLE","CONTEXT","SELF" -> {String id=switch(origin){case "PLAYER"->member.player();case "ROLE"->member.role();case "CONTEXT"->member.context();default->member.selves().getOrDefault(task,"");};var object=engine.objectForSemanticId(id);if(object!=null)value=object.value();}
                    case "STEP_TUPLE" -> {
                        var tuple=(TupleType)type;Node schema=program.xmlPlan().inputs().specification().requireId(binding.attr("viewRef"));var parts=new ArrayList<TupleValue.Part>();var used=NativeExpressionInputs.wholeTupleVariables(compiled.expression()).contains(name)?tuple.getParts().keySet():NativeExpressionInputs.tupleParts(compiled.expression()).getOrDefault(name,Set.of());
                        for(var part:tuple.getParts().values()) {var field=schema.children("field").stream().filter(f->f.attr("name").equals(part.name())).findFirst().orElseThrow();Boolean known=field.attr("origin").equals("STEP_ENABLED")?Boolean.valueOf(step.enabled()):step.succeeded();
                            if(used.contains(part.name())&&known==null&&reason.isBlank())reason="STEP_CALLBACK_VALUE_UNAVAILABLE:"+part.name();parts.add(new TupleValue.Part(part.getPosition(),part.name(),known==null?UndefinedValue.instance:BooleanValue.get(known)));}
                        value=new TupleValue(tuple,parts);
                    }
                    default -> {
                        Object raw=null;
                        if(origin.startsWith("SCOPE_COLUMN:"))raw=scope.bindings().get(origin.substring("SCOPE_COLUMN:".length()));
                        if(origin.startsWith("STEP_INPUT:")) {var inputs=CanonicalJson.object(step.bindings().getOrDefault("inputs",Map.of()));Object proof=inputs.get(origin.substring("STEP_INPUT:".length()));if(proof instanceof Map<?,?>)raw=CanonicalJson.object(proof).get("value");}
                        value=scalar(type,raw,engine);
                    }
                }
                if((value.isUndefined()&&!origin.equals("STEP_TUPLE")||!value.isUndefined()&&!value.type().conformsTo(type))&&reason.isBlank())reason="OCL_BINDING_UNAVAILABLE_OR_TYPE_MISMATCH:"+name;
                variables.put(name,value);
            }
        }
        if(reason.isBlank()&&!pre&&view.before()==null)reason="POST_MATCHING_PRE_UNAVAILABLE";
        if(reason.isBlank()&&!pre)reason=NativePreStateCoverage.unavailable(compiled.expression(),view.before(),view.after(),variables);
        if(reason.isBlank()&&self!=null)reason=coverage.unavailableSelf(compiled.expression(),engine,self,sources,lost);
        if(pre) {
            String beforeQuality="";
            for(var read:NativePreStateCoverage.attributeReads(slot.post().expression())) {
                if(self==null){beforeQuality="ATTRIBUTE_RECEIVER_UNAVAILABLE:"+read.attr().qualifiedName();break;}
                beforeQuality=coverage.unavailableSelf(read,engine,self,sources,lost);
                if(!beforeQuality.isBlank())break;
            }
            witness.put("postPreCoverage",beforeQuality);witness.put("postPreFieldCount",Integer.toString(NativePreStateCoverage.attributeReads(slot.post().expression()).size()));
        }
        if(demand!=null)witness.putAll(demand.witness());witness.put("taskPredicateId",compiled.specification().id());
        if(self!=null)witness.putAll(coverage.witness(compiled.expression(),engine,self,sources,reason));
        String invocation=step==null?"":step.invocation();String id=compiled.specification().id()+":"+SemanticJson.hash(List.of(scope.id(),member.instance(),invocation));
        return new NativeInvocationEvaluation.Condition(id,pre?"TASK_PRE":"TASK_POST",compiled.specification().self().name(),self==null?"":self.name(),member.selves().getOrDefault(task,""),compiled.specification().body(),compiled.expression(),view.before()==null?view.after():view.before(),view.after(),variables,reason,ConstraintCoverageResolver.unavailableVerdict(reason),invocation,witness);
    }
    private boolean pureCallback(NativeOclBackend.Compiled compiled,Node node) {
        var origins=new HashMap<String,String>();node.children("variable").forEach(v->origins.put(v.attr("name"),v.attr("origin")));
        if(NativeExpressionInputs.variables(compiled.expression()).stream().anyMatch(v->!origins.getOrDefault(v,"").equals("STEP_TUPLE")&&!origins.getOrDefault(v,"").startsWith("STEP_INPUT:")))return false;
        try{NativeObserverExpression.requirePureValue(compiled.expression());return true;}catch(IllegalArgumentException worldRead){return false;}
    }
    private static boolean callbackAuthority(JasonStepCutDecoder.Step step,boolean pre) {var quality=CanonicalJson.object(step.bindings().getOrDefault("stepValueQuality",Map.of()));return "JASON_STEP_VALUES/2.0".equals(quality.get("version"))&&"COMPLETE_CALLBACK".equals(quality.get(pre?"enabled":"succeeded"));}
    private static boolean operationReceipts(JasonStepCutDecoder.Step step,List<String> required) {
        if(required.isEmpty())return true;
        if(step.operation().isBlank()||!(step.bindings().get("actions") instanceof List<?> values)||values.size()>32)return false;
        var rows=values.stream().map(CanonicalJson::object).toList();
        for(String operation:required)if(rows.stream().filter(r->"JASON_ACTIONEXEC_IDENTITY".equals(r.get("authority"))&&operation.equals(r.get("operationName"))&&"SUCCEEDED".equals(r.get("result"))&&r.get("correlation") instanceof String value&&!value.isBlank()).count()!=1)return false;
        return rows.stream().anyMatch(r->step.operation().equals(r.get("correlation"))&&"JASON_ACTIONEXEC_IDENTITY".equals(r.get("authority"))&&"SUCCEEDED".equals(r.get("result"))&&required.contains(r.get("operationName")));
    }
    private boolean references(Node node,OccurrenceProjection.Scope scope,JasonStepCutDecoder.Step step,NativeRuntimeMutationEngine engine,boolean pre) {
        if(node.attr("scopePolicy").equals("UNIQUE_CURRENT_PLAYER_SCOPE"))return step.scheme().isBlank()&&step.artifact().isBlank();
        if(node.optional("stepSchemeColumn").isBlank()&&!step.scheme().isBlank()||node.optional("stepArtifactColumn").isBlank()&&!step.artifact().isBlank())return false;
        if(!node.optional("stepSchemeColumn").isBlank()&&!Objects.equals(scope.bindings().get(node.attr("stepSchemeColumn")),step.scheme()))return false;
        if(!node.optional("stepArtifactColumn").isBlank()) {Object raw=scope.bindings().get(node.attr("stepArtifactColumn"));var expected=raw instanceof String identity?engine.objectForSemanticId(identity):null;var actual=engine.objectForSemanticId(step.artifact());
            if(expected==null||actual!=expected)return pre&&node.attr("entryPolicy").equals("ALLOW_UNBOUND_SELF_FOR_PURE_CALLBACK")&&step.artifact().isBlank()&&pureCallback(slots.get(node.attr("target")).pre(),node);
        }
        return true;
    }
    private static Value scalar(Type type,Object raw,NativeRuntimeMutationEngine engine) {
        if(raw==null)return UndefinedValue.instance;if(type.isTypeOfString()&&raw instanceof String text)return new StringValue(text);if(type.isTypeOfBoolean()&&raw instanceof Boolean bool)return BooleanValue.get(bool);
        if(type.isTypeOfInteger()&&raw instanceof Number number)try{return IntegerValue.valueOf(new java.math.BigDecimal(number.toString()).intValueExact());}catch(ArithmeticException|NumberFormatException invalid){return UndefinedValue.instance;}
        if(type instanceof org.tzi.use.uml.mm.MClass target&&raw instanceof String id){var object=engine.objectForSemanticId(id);if(object!=null&&object.cls().isSubClassifierOf(target))return object.value();}
        return UndefinedValue.instance;
    }
}
