package org.jacamo.bridge.adapter;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import cartago.ArtifactId;
import cartago.CartagoEnvironment;
import jason.asSemantics.*;
import jason.asSyntax.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentProgramSemantic;

/** Agent-owned execution evidence. No domain mutation, polling, inferred success
 * or Boolean default. Trigger and ActionExec keys use actual object identity. */
public class JasonStepObservation implements JasonPlanInstrumentation.Reporter,GoalListener,AutoCloseable {
    public record Input(String name,String variable,String javaType) {
        public Input {if(name==null||!name.matches("[A-Za-z_][A-Za-z0-9_]*")||variable==null||!variable.matches("[A-Z_][A-Za-z0-9_]*")||!"java.lang.String".equals(javaType))throw new IllegalArgumentException("JASON_STEP_INPUT_UNSUPPORTED");}
    }
    public record Capture(String task,List<String> variables,String artifactVariable,String artifactClass,String schemeVariable,boolean allowMissingArtifactAtEntry,List<Input> inputs) {
        public Capture {
            variables=List.copyOf(variables);inputs=List.copyOf(inputs);
            if(task==null||!task.matches("[A-Za-z_][A-Za-z0-9_]*")||variables.size()>32||new HashSet<>(variables).size()!=variables.size()||inputs.size()>16
                    ||variables.stream().anyMatch(v->!v.matches("[A-Z_][A-Za-z0-9_]*"))||!artifactVariable.isEmpty()&&!variables.contains(artifactVariable)
                    ||!schemeVariable.isEmpty()&&!variables.contains(schemeVariable)||artifactVariable.isEmpty()!=artifactClass.isEmpty()
                    ||inputs.stream().map(Input::name).distinct().count()!=inputs.size())throw new IllegalArgumentException("JASON_STEP_CAPTURE_INVALID");
            for(var input:inputs)if(!variables.contains(input.variable()))throw new IllegalArgumentException("JASON_STEP_INPUT_CAPTURE_REQUIRED");
        }
    }
    @FunctionalInterface protected interface Preparation {JasonPlanInstrumentation.Prepared prepare(Agent agent,AgentProgramSemantic source,Path root,JasonPlanInstrumentation.Reporter reporter)throws Exception;}
    private final Map<String,Capture> captures;
    private final String qualityVersion;
    private final java.util.function.BiConsumer<Map<String,Object>,Map<String,Object>> inputExposure;
    private static final int MAX_GOALS=1024,MAX_ACTIONS=4096;
    private final TransitionSystem ts;private final BridgeEntityId agent;private final String run,revision;private final long generation;
    private final String declaration;private final Consumer<VerificationObservation> sink;private final Consumer<String> gap;
    private final JasonPlanInstrumentation.Prepared prepared;
    private final IdentityHashMap<Trigger,Episode> episodes=new IdentityHashMap<>();
    private final IdentityHashMap<ActionExec,Step> actions=new IdentityHashMap<>();
    private long nextGoal,nextStep;private boolean closed;private volatile String failure="";
    private long actionStarts,actionStartsMatched,actionEnds,actionEndsMatched;private String unmatchedAction="";
    private static final class Episode {
        final IntendedMeans means;final String token;Step active;
        Episode(IntendedMeans means,String token){this.means=means;this.token=token;}
    }
    private static final class Step {
        final JasonPlanInstrumentation.Site site;final String token;final Episode episode;
        final List<Map<String,Object>> receipts=new ArrayList<>();String phase="ENABLED",operation="";
        Step(JasonPlanInstrumentation.Site site,String token,Episode episode){this.site=site;this.token=token;this.episode=episode;}
    }
    public JasonStepObservation(TransitionSystem ts,BridgeEntityId agent,String run,long generation,String revision,AgentProgramSemantic source,Path root,
            Consumer<VerificationObservation> sink,Consumer<String> gap,JasonPlanInstrumentation.Specification specification,List<Capture> captures)throws Exception {
        this(ts,agent,run,generation,revision,source,root,sink,gap,captures,"JASON_STEP_VALUES/2.0",(bindings,inputs)->bindings.put("inputs",inputs),
            (actual,program,directory,reporter)->JasonPlanInstrumentation.prepare(actual,program,directory,reporter,specification));
    }
    protected JasonStepObservation(TransitionSystem ts,BridgeEntityId agent,String run,long generation,String revision,AgentProgramSemantic source,Path root,
            Consumer<VerificationObservation> sink,Consumer<String> gap,List<Capture> selected,String qualityVersion,
            java.util.function.BiConsumer<Map<String,Object>,Map<String,Object>> inputExposure,Preparation preparation)throws Exception {
        var captureMap=new LinkedHashMap<String,Capture>();for(var capture:selected)if(captureMap.putIfAbsent(capture.task(),capture)!=null)throw new IllegalArgumentException("JASON_STEP_CAPTURE_DUPLICATE");
        captures=Map.copyOf(captureMap);this.qualityVersion=qualityVersion;this.inputExposure=inputExposure;
        this.ts=Objects.requireNonNull(ts);this.agent=Objects.requireNonNull(agent);this.run=Objects.requireNonNull(run);this.generation=generation;this.revision=Objects.requireNonNull(revision);
        this.sink=Objects.requireNonNull(sink);this.gap=Objects.requireNonNull(gap);declaration=source.declarationId();
        prepared=preparation.prepare(ts.getAg(),source,root,this);
        if(!captures.keySet().equals(prepared.sites().stream().map(JasonPlanInstrumentation.Site::task).collect(java.util.stream.Collectors.toSet()))){prepared.close();throw new IllegalArgumentException("JASON_STEP_CAPTURE_SITE_COVERAGE");}
        try {
            ts.addGoalListener(this);
            if(ts.getGoalListeners()==null||!ts.getGoalListeners().contains(this))throw new IllegalStateException("GOAL_LISTENER_NOT_REGISTERED");
            prepared.install();
        }catch(Exception rejected){ts.removeGoalListener(this);prepared.close();throw rejected;}
    }
    public String recipeHash(){return prepared.hash();}
    public Map<String,Object> manifest(){return prepared.manifest();}
    public synchronized int activeGoals(){return episodes.size();}
    public String failure(){return failure;}
    @Override public synchronized void marker(TransitionSystem actual,JasonPlanInstrumentation.Site site,String phase) {
        if(closed)return;
        if(actual!=ts||!prepared.sites().contains(site))throw new IllegalArgumentException("STEP_OWNER_OR_SITE_MISMATCH");
        var intention=ts.getC().getSelectedIntention();if(intention==null||intention.isFinished())throw new IllegalArgumentException("STEP_SELECTED_INTENTION_UNAVAILABLE");
        var means=intention.peek();var trigger=means.getTrigger();
        if(trigger==null)throw new IllegalArgumentException("STEP_TRIGGER_UNAVAILABLE");
        Episode episode=episodes.get(trigger);
        if(episode==null) {
            if(!phase.equals("ENABLED"))throw new IllegalArgumentException("STEP_WITHOUT_ENTRY");
            if(episodes.size()>=MAX_GOALS)throw new IllegalArgumentException("STEP_GOAL_CAPACITY");
            episode=new Episode(means,"jason-goal:"+run+":"+agent.incarnation()+":"+(++nextGoal));episodes.put(trigger,episode);
        }
        if(episode.means!=means)throw new IllegalArgumentException("STEP_INTENDED_MEANS_MISMATCH");
        if(phase.equals("ENABLED")) {
            if(episode.active!=null)throw new IllegalArgumentException("STEP_ENTRY_WITHOUT_PRIOR_TERMINAL");
            episode.active=new Step(site,"jason-step:"+run+":"+agent.incarnation()+":"+(++nextStep),episode);publish(episode.active,"");
        }else {
            var active=episode.active;if(active==null||!active.site.equals(site))throw new IllegalArgumentException("STEP_TERMINAL_WITHOUT_MATCHING_ENTRY");
            active.phase="SUCCEEDED";publish(active,"");episode.active=null;
        }
    }
    /** STARTED runs on the actual agent action path. Completion callbacks only
     * copy results for that very ActionExec; they never read live BB/unifiers. */
    public synchronized void action(ActionExec action,RuntimeEventKind kind,String correlation) {
        if(closed)return;
        if(kind==RuntimeEventKind.STARTED) {
            actionStarts++;var intention=action.getIntention();if(intention==null||intention.isFinished()){unmatchedAction="ACTION_INTENTION_UNAVAILABLE";return;}
            var episode=episodes.get(intention.peek().getTrigger());if(episode==null||episode.active==null){unmatchedAction="ACTION_ACTIVE_STEP_UNAVAILABLE:"+intention.peek().getTrigger();return;}actionStartsMatched++;
            if(actions.size()>=MAX_ACTIONS){unavailable("STEP_ACTION_CAPACITY");return;}actions.put(action,episode.active);
        }else {
            actionEnds++;var step=actions.remove(action);if(step==null){unmatchedAction="ACTION_COMPLETION_WITHOUT_CAPTURED_DISPATCH";return;}actionEndsMatched++;
            if(step.receipts.size()>=32){unavailable("STEP_RECEIPT_CAPACITY");return;}
            step.operation=correlation;
            step.receipts.add(Map.of("correlation",correlation,"authority","JASON_ACTIONEXEC_IDENTITY","action",action.getActionTerm().toString(),"operationName",action.getActionTerm().getFunctor(),"result",kind.name(),"failure",Objects.toString(action.getFailureReason(),"")));
        }
    }
    @Override public synchronized void goalFailed(Trigger trigger,Term reason) {try{failed(trigger,"GOAL_LISTENER_FAILED:"+Objects.toString(reason,""));}catch(RuntimeException missing){unavailable("GOAL_FAILURE_CAPTURE:"+missing.getClass().getSimpleName());}}
    private void failed(Trigger trigger,String reason) {
        if(closed)return;var episode=episodes.get(trigger);if(episode==null||episode.active==null)return;
        var active=episode.active;active.phase="FAILED";publish(active,reason);episode.active=null;
    }
    @Override public synchronized void goalFinished(Trigger trigger,GoalStates state) {
        if(closed)return;
        try {
            if(state==GoalStates.failed)failed(trigger,"GOAL_LISTENER_FINISHED_FAILED");
            var episode=episodes.remove(trigger);if(episode!=null)actions.values().removeIf(s->s.episode==episode);
        }catch(RuntimeException missing){unavailable("GOAL_TERMINAL_CAPTURE:"+missing.getClass().getSimpleName());}
        // Dropped/finished without an after marker is not source execution false.
    }
    private void publish(Step step,String sourceFailure) {
        var capture=captures.get(step.site.task());
        var bindings=new LinkedHashMap<String,Object>();bindings.put("taskCell",step.site.task());bindings.put("intentionAuthority","ACTUAL_INTENDED_MEANS_TRIGGER_IDENTITY");
        bindings.put("agentDeclaration",declaration);bindings.put("agentName",ts.getAgArch().getAgName());bindings.put("sourceFailure",sourceFailure);
        bindings.put("stepValueQuality",Map.of("version",qualityVersion,"enabled","COMPLETE_CALLBACK","succeeded",step.phase.equals("ENABLED")?"UNAVAILABLE":"COMPLETE_CALLBACK",
                "enabledAuthority","PINNED_PLAN_BODY_ENTRY_MARKER","succeededAuthority",step.phase.equals("ENABLED")?"UNAVAILABLE":step.phase.equals("SUCCEEDED")?"PINNED_PLAN_BODY_EXIT_MARKER":"PUBLIC_GOAL_LISTENER_FAILURE"));
        bindings.put("capturedRun",Map.of("sessionId",run,"generation",generation,"modelRevision",revision));
        bindings.put("instrumentationHash",prepared.hash());bindings.put("instrumentation",prepared.manifest());bindings.put("actions",List.copyOf(step.receipts));
        bindings.put("actionCapture",Map.of("dispatches",actionStarts,"matchedDispatches",actionStartsMatched,"completions",actionEnds,"matchedCompletions",actionEndsMatched,"unmatched",unmatchedAction,"architectureChain",ts.getAgArch().getAgArchClassesChain()));
        bindings.put("sourceLocation",Map.of("planId",step.site.planId(),"firstOrdinal",step.site.first(),"afterOrdinal",step.site.after(),"line",step.site.line()));
        var values=new LinkedHashMap<String,Object>();var substitution=step.episode.means.getUnif();
        for(String variable:capture.variables()) {
            Term value=ASSyntax.createVar(variable).capply(substitution);if(value!=null&&!value.isVar())values.put(variable,JasonStructuredTerms.term(value));
        }
        bindings.put("values",values);
        var inputs=new LinkedHashMap<String,Object>();
        for(var input:capture.inputs()) {
            var arch=jaca.CAgentArch.getCartagoAgArch(ts);Term actual=ASSyntax.createVar(input.variable()).capply(substitution);
            // Match the public CAgentArch conversion only for an already-ground literal.
            // Arithmetic/random terms are never re-evaluated to manufacture an argument.
            if(arch!=null&&actual!=null&&actual.isGround()&&(actual instanceof StringTerm||actual instanceof Literal&&!actual.isNumeric()&&!actual.isInternalAction())) {
                Object converted=arch.getJavaLib().termToObject(actual);
                if(converted instanceof String string)inputs.put(input.name(),Map.of("authority","JACA_PUBLIC_TERM_TO_OBJECT_GROUND_LITERAL_V1","javaType",input.javaType(),"value",string,"sourceTermHash",sha256(values.get(input.variable()))));
            }
        }
        inputExposure.accept(bindings,Collections.unmodifiableMap(inputs));
        ArtifactId artifact=capture.artifactVariable().isEmpty()?null:artifact(substitution,capture.artifactVariable(),capture.artifactClass());
        String artifactId=artifact==null?"":OfficialCartagoAdapter.artifactId(CartagoEnvironment.getInstance().getId().toString(),artifact);
        var scheme=capture.schemeVariable().isEmpty()?null:scheme(ASSyntax.createVar(capture.schemeVariable()).capply(substitution));
        String schemeId=scheme==null?"":new BridgeEntityId("moise","organisation","scheme-board",scheme.getWorkspaceId().getName(),scheme.getName(),run+":"+scheme.getId()).canonical();
        String environment=CartagoEnvironment.getInstance().getId().toString();
        bindings.put("schemeArtifact",scheme==null?Map.of():CartagoArgumentObservation.value(scheme,environment));
        bindings.put("artifact",artifact==null?Map.of():CartagoArgumentObservation.value(artifact,environment));
        boolean beforeCreation=capture.allowMissingArtifactAtEntry()&&step.phase.equals("ENABLED");
        String unavailable=!capture.schemeVariable().isEmpty()&&schemeId.isBlank()?"EXACT_SCHEME_PERCEPT_REFERENCE_UNAVAILABLE":!capture.artifactVariable().isEmpty()&&artifactId.isBlank()&&!beforeCreation?"EXACT_ARTIFACT_REFERENCE_UNAVAILABLE":"";
        sink.accept(new VerificationObservation(VerificationObservation.Kind.JASON_STEP,step.token,unavailable.isBlank()?Completeness.COMPLETE:Completeness.PARTIAL,unavailable,
                VerificationObservation.SourceTime.unavailable("STEP_CALLBACK_NOT_A_QUALIFIED_NPL_CLOCK"),Map.of("agentIdentity",agent.canonical(),"goalInvocation",step.episode.token,"stepInvocation",step.token,
                "sourceStepId",step.site.task(),"phase",step.phase,"artifactIdentity",artifactId,"schemeIdentity",schemeId,"operationInvocation",step.operation,"bindings",bindings)));
    }
    private static String sha256(Object value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(CanonicalJson.encode(value)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private ArtifactId artifact(Unifier unifier,String variable,String requestedClass) {
        var arch=jaca.CAgentArch.getCartagoAgArch(ts);if(arch==null)return null;
        Term term=ASSyntax.createVar(variable).capply(unifier);if(term.isVar())return null;
        return arch.getJavaLib().termToObject(term) instanceof ArtifactId id&&requestedClass.equals(id.getArtifactType())?id:null;
    }
    private ArtifactId scheme(Term term) {
        if(term==null||!term.isGround())return null;var arch=jaca.CAgentArch.getCartagoAgArch(ts);if(arch==null)return null;
        var candidates=new LinkedHashMap<String,ArtifactId>();
        for(var belief:ts.getAg().getBB())if(!belief.isRule()&&belief.getFunctor().equals("goalArgument")&&belief.getArity()==4&&belief.getTerm(0).equals(term)) {
            var annotation=belief.getAnnot("artifact_id");
            if(annotation==null||annotation.getArity()!=1||!annotation.getTerm(0).isGround())continue;
            Object value=arch.getJavaLib().termToObject(annotation.getTerm(0));
            if(value instanceof ArtifactId id&&"ora4mas.nopl.SchemeBoard".equals(id.getArtifactType()))candidates.put(id.getId().toString(),id);
        }
        return candidates.size()==1?candidates.values().iterator().next():null;
    }
    @Override public synchronized void unavailable(String diagnostic){if(closed)return;failure=diagnostic;try{gap.accept("JASON_STEP_CAPTURE_UNAVAILABLE:"+diagnostic);}catch(RuntimeException transportRetired){/* Retain failure/capability downgrade without changing Jason behavior. */}}
    @Override public void close() {
        synchronized(this){if(closed)return;closed=true;episodes.clear();actions.clear();}
        // GoalListener is backed by ArrayList in Jason3.3.2. Do not remove it
        // concurrently with the interpreter's listener iteration.
        var cleanup=(jason.util.RunnableSerializable)()->{ts.removeGoalListener(this);prepared.close();};
        var local=BridgeRuntimeRegistry.agentArchitecture(ts.getAgArch().getAgName());
        var thread=local.map(jason.infra.local.LocalAgArch::getThread).orElse(null);
        if(thread==null||!thread.isAlive()||thread==Thread.currentThread())cleanup.run();else{ts.runAtBeginOfNextCycle(cleanup);ts.getAgArch().wake();}
    }
}
