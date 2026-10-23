package org.jacamo.bridge.adapter;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import jason.asSemantics.*;
import jason.asSyntax.ASSyntax;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentProgramSemantic;

/** Typed capability request to actual source AST/listeners. No case-specific graph or variable names. */
public final class JasonStepCapability implements ObservationCapabilityRegistry.Handle {
    public static final String PROVIDER="jason.plan-boundaries.v1";
    private final ObservationCapabilityRequest request;
    private final JasonStepObservation observer;
    private boolean closed;
    public JasonStepCapability(ObservationCapabilityRequest request,TransitionSystem ts,BridgeEntityId agent,String run,long generation,String revision,
            AgentProgramSemantic source,Path root,Consumer<VerificationObservation> sink,Consumer<String> gap)throws Exception {
        this.request=request;
        if(!request.provider().equals(PROVIDER)||!Set.of("sourceRef","sourceHash","sourcePath","plans").containsAll(request.parameters().keySet()))throw new IllegalArgumentException("JASON_STEP_REQUEST_UNSUPPORTED");
        ProviderSourcePin.require(request,root);
        if(!source.sourceDigest().equals(text(request.parameters(),"sourceHash"))||!source.sourceUri().equals("project:/"+text(request.parameters(),"sourcePath")))throw new IllegalArgumentException("JASON_STEP_SOURCE_IDENTITY_MISMATCH");
        var plans=new ArrayList<JasonPlanInstrumentation.PlanSelection>();var captures=new ArrayList<JasonStepObservation.Capture>();
        for(Object raw:list(request.parameters().get("plans"))) {
            var plan=CanonicalJson.object(raw);keys(plan,Set.of("trigger","context","steps"));
            String trigger=OfficialJasonAdapter.canonicalAst(ASSyntax.parseLiteral(text(plan,"trigger")).toString()),context=text(plan,"context");
            var matches=source.planLibrary().plans().stream().filter(p->p.trigger().literal().equals(trigger)
                &&(context.isEmpty()||p.context().equals(context))&&p.metadata().evidence().stream().anyMatch(e->e.sourceDigest().equals(source.sourceDigest()))).toList();
            if(matches.size()!=1)throw new IllegalArgumentException("JASON_STEP_PLAN_NOT_UNIQUE");
            var boundaries=new ArrayList<JasonPlanInstrumentation.Boundary>();
            for(Object rawStep:list(plan.get("steps"))) {
                var step=CanonicalJson.object(rawStep);keys(step,Set.of("task","first","after","variables","artifactVariable","artifactClass","schemeVariable","allowMissingArtifactAtEntry","inputs"));
                String task=text(step,"task");boundaries.add(new JasonPlanInstrumentation.Boundary(task,integer(step.get("first")),integer(step.get("after"))));
                var inputs=new ArrayList<JasonStepObservation.Input>();
                for(Object rawInput:list(step.get("inputs"))){var input=CanonicalJson.object(rawInput);keys(input,Set.of("name","variable","type"));inputs.add(new JasonStepObservation.Input(text(input,"name"),text(input,"variable"),text(input,"type")));}
                if(!(step.get("allowMissingArtifactAtEntry") instanceof Boolean allow))throw new IllegalArgumentException("JASON_STEP_ENTRY_BOOLEAN_REQUIRED");
                var variables=new ArrayList<String>();for(Object variable:list(step.get("variables"))){if(!(variable instanceof String value))throw new IllegalArgumentException("JASON_STEP_VARIABLE_INVALID");variables.add(value);}
                captures.add(new JasonStepObservation.Capture(task,variables,text(step,"artifactVariable"),text(step,"artifactClass"),text(step,"schemeVariable"),allow,inputs));
            }
            plans.add(new JasonPlanInstrumentation.PlanSelection(matches.getFirst().metadata().semanticId(),boundaries));
        }
        var specification=new JasonPlanInstrumentation.Specification(JasonPlanInstrumentation.RECIPE,source.sourceDigest(),plans);
        observer=new JasonStepObservation(ts,agent,run,generation,revision,source,root,sink,gap,specification,captures);
    }
    public void action(ActionExec action,RuntimeEventKind kind,String correlation){observer.action(action,kind,correlation);}
    public Map<String,Object> manifest(){return observer.manifest();}
    @Override public synchronized Capability capability(){String reason=closed?"PROVIDER_CLOSED":observer.failure();return new Capability(request.id(),reason.isEmpty()?CapabilityStatus.COMPLETE:CapabilityStatus.UNAVAILABLE,List.of(observer.recipeHash()),reason);}
    @Override public synchronized void close(){if(closed)return;closed=true;observer.close();}
    private static String text(Map<String,Object> map,String name){if(!(map.get(name) instanceof String value))throw new IllegalArgumentException("JASON_STEP_TEXT_REQUIRED:"+name);return value;}
    private static List<?> list(Object value){if(!(value instanceof List<?> list)||list.size()>64)throw new IllegalArgumentException("JASON_STEP_LIST_REQUIRED_OR_CAPACITY");return list;}
    private static int integer(Object value){try{if(!(value instanceof Number))throw new ArithmeticException();return new java.math.BigDecimal(value.toString()).intValueExact();}catch(ArithmeticException error){throw new IllegalArgumentException("JASON_STEP_ORDINAL_INVALID",error);}}
    private static void keys(Map<String,Object> map,Set<String> keys){if(!map.keySet().equals(keys))throw new IllegalArgumentException("JASON_STEP_PARAMETER_SHAPE_UNSUPPORTED");}
}
