package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.*;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Consumer-only verification of request-defined Jason source boundaries. No framework class loading or task policy. */
public final class JasonStepCutDecoder {
    public record Step(String task,String agent,String declaration,String goalInvocation,String invocation,String scheme,String artifact,
            String operation,String phase,Completeness quality,String diagnostic,boolean enabled,Boolean succeeded,Map<String,Object> bindings) {
        public Step {bindings=SemanticJson.object(bindings);}
    }
    private record Site(PlanSemantic plan,Map<String,Object> capture,int first,int after) { }
    private final ModelSnapshot source;
    private final ObservationCapabilityRequest request;
    private final String hash,path;
    private final Map<String,AgentProgramSemantic> programs;
    private final Map<String,Map<String,Site>> siteIndex;
    public JasonStepCutDecoder(ModelSnapshot source,ObservationCapabilityRequest request) {
        this.source=source;this.request=request;
        need(request.provider().equals("jason.plan-boundaries.v1")&&request.parameters().keySet().equals(Set.of("sourceRef","sourceHash","sourcePath","plans")),"REQUEST_UNSUPPORTED");
        hash=text(request.parameters(),"sourceHash");path=text(request.parameters(),"sourcePath");need(hash.matches("[0-9a-f]{64}")&&!path.isBlank(),"SOURCE_PIN_REQUIRED");
        var programs=new TreeMap<String,AgentProgramSemantic>();var index=new TreeMap<String,Map<String,Site>>();
        for(var program:source.semanticContract().jasonPrograms())if(program.sourceDigest().equals(hash)&&program.sourceUri().equals("project:/"+path)) {
            need(programs.putIfAbsent(program.declarationId(),program)==null,"SOURCE_PROGRAM_AMBIGUOUS");index.put(program.declarationId(),sites(program));
        }
        need(!programs.isEmpty(),"OFFICIAL_SOURCE_PROGRAM_REQUIRED");this.programs=Map.copyOf(programs);siteIndex=Map.copyOf(index);
    }
    private Map<String,Site> sites(AgentProgramSemantic program) {
        var sites=new LinkedHashMap<String,Site>();var plans=new HashSet<String>();
        for(Object raw:list(request.parameters().get("plans"),64)) {
            var selected=CanonicalJson.object(raw);keys(selected,Set.of("trigger","context","steps"));
            var matches=program.planLibrary().plans().stream().filter(p->p.trigger().literal().equals(selected.get("trigger"))&&(text(selected,"context").isEmpty()||p.context().equals(selected.get("context")))&&p.metadata().evidence().stream().anyMatch(e->hash.equals(e.sourceDigest()))).toList();
            need(matches.size()==1,"CANONICAL_SOURCE_PLAN_NOT_UNIQUE");var plan=matches.getFirst();
            need(plan.trigger().type().equals("achieve")&&plan.trigger().operator().equals("add")&&plans.add(plan.metadata().semanticId()),"PLAN_TRIGGER_OR_DUPLICATE");int next=0;
            for(Object rawStep:list(selected.get("steps"),64)) {
                var step=CanonicalJson.object(rawStep);keys(step,Set.of("task","first","after","variables","artifactVariable","artifactClass","schemeVariable","allowMissingArtifactAtEntry","inputs"));
                int first=integer(step.get("first")),after=integer(step.get("after"));String task=text(step,"task");
                need(task.matches("[A-Za-z_][A-Za-z0-9_]*")&&first==next&&after>first&&after<=plan.body().size()&&after<=256,"BOUNDARY_PARTITION_INVALID");next=after;
                var variables=new HashSet<String>();for(Object variable:list(step.get("variables"),32))need(variable instanceof String name&&name.matches("[A-Z_][A-Za-z0-9_]*")&&variables.add(name),"VARIABLE_CAPTURE_INVALID");
                String artifact=text(step,"artifactVariable"),scheme=text(step,"schemeVariable");need((artifact.isEmpty()||variables.contains(artifact))&&(scheme.isEmpty()||variables.contains(scheme))&&artifact.isEmpty()==text(step,"artifactClass").isEmpty()&&step.get("allowMissingArtifactAtEntry") instanceof Boolean,"REFERENCE_CAPTURE_INVALID");
                var inputs=new HashSet<String>();for(Object rawInput:list(step.get("inputs"),16)) {var input=CanonicalJson.object(rawInput);keys(input,Set.of("name","variable","type"));need(inputs.add(text(input,"name"))&&variables.contains(text(input,"variable"))&&text(input,"type").equals("java.lang.String"),"INPUT_CAPTURE_INVALID");}
                need(sites.putIfAbsent(task,new Site(plan,step,first,after))==null,"TASK_SITE_DUPLICATE");
            }
            need(next==plan.body().size(),"PARTITION_DOES_NOT_COVER_SOURCE_BODY");
        }
        need(!sites.isEmpty(),"SOURCE_BOUNDARY_REQUIRED");return Collections.unmodifiableMap(sites);
    }
    public boolean accepts(VerificationObservation observation) {
        if(observation.kind()!=VerificationObservation.Kind.JASON_STEP)return false;
        var sites=siteIndex.get(CanonicalJson.object(observation.data().get("bindings")).get("agentDeclaration"));return sites!=null&&sites.containsKey(observation.data().get("sourceStepId"));
    }
    public Step decode(VerificationObservation observation,String run,long generation,String revision) {
        need(observation.kind()==VerificationObservation.Kind.JASON_STEP&&source.modelRevision().equals(revision)&&run!=null&&!run.isBlank()&&generation>=0,"KIND_OR_SCOPE");
        var data=observation.data();var bindings=CanonicalJson.object(data.get("bindings"));String task=text(data,"sourceStepId"),declaration=text(bindings,"agentDeclaration"),phase=text(data,"phase");
        var original=programs.get(declaration);need(original!=null,"SOURCE_DECLARATION_NOT_IN_REQUEST");var selected=siteIndex.get(declaration);var site=selected.get(task);need(site!=null,"TASK_NOT_REQUESTED");
        need(task.equals(bindings.get("taskCell"))&&"ACTUAL_INTENDED_MEANS_TRIGGER_IDENTITY".equals(bindings.get("intentionAuthority")),"SITE_AUTHORITY");
        var scope=CanonicalJson.object(bindings.get("capturedRun"));need(run.equals(scope.get("sessionId"))&&generation==longInteger(scope.get("generation"))&&revision.equals(scope.get("modelRevision")),"CAPTURED_EPOCH_MISMATCH");
        var agent=BridgeEntityId.parse(text(data,"agentIdentity"));need(agent.authority().equals("jason")&&agent.dimension().equals("agent")&&agent.kind().equals("runtime-agent")&&agent.scope().equals(source.semanticContract().project().name())&&agent.localId().equals(bindings.get("agentName")),"AGENT_IDENTITY_MISMATCH");
        need(source.semanticContract().agentDeclarations().stream().anyMatch(a->a.name().equals(declaration)&&a.instances()>0),"OFFICIAL_AGENT_DECLARATION_REQUIRED");
        String goal=text(data,"goalInvocation"),invocation=text(data,"stepInvocation");need(token(goal,"jason-goal:"+run+":"+agent.incarnation()+":")&&token(invocation,"jason-step:"+run+":"+agent.incarnation()+":")&&invocation.equals(observation.subject()),"INVOCATION_OWNER");
        var manifest=CanonicalJson.object(bindings.get("instrumentation"));keys(manifest,Set.of("recipe","sourceSha256","plans"));
        need("JASON_PLAN_BODY_MARKERS/2.0".equals(manifest.get("recipe"))&&hash.equals(manifest.get("sourceSha256"))&&SemanticJson.hash(manifest).equals(bindings.get("instrumentationHash")),"INSTRUMENTATION_RECIPE_OR_HASH");
        var plans=selected.values().stream().map(Site::plan).map(p->p.metadata().semanticId()).collect(java.util.stream.Collectors.toSet());var verified=new HashSet<String>();
        for(Object raw:list(manifest.get("plans"),64)) {
            var proof=CanonicalJson.object(raw);keys(proof,Set.of("planId","sourceUri","sourceLine","originalRows","executableRows","originalBody","executableBody","originalBodySha256","executableBodySha256"));String id=text(proof,"planId");need(plans.contains(id)&&verified.add(id),"PLAN_PROOF_NOT_REQUESTED_OR_DUPLICATE");
            var plan=selected.values().stream().map(Site::plan).filter(p->p.metadata().semanticId().equals(id)).findFirst().orElseThrow();
            var rows=plan.body().stream().map(b->Map.<String,Object>of("ordinal",b.ordinal(),"type",b.bodyType(),"term",b.term())).toList();
            need(SemanticJson.hash(rows).equals(SemanticJson.hash(proof.get("originalRows")))&&original.sourceUri().equals(proof.get("sourceUri"))&&line(plan.metadata())==integer(proof.get("sourceLine")),"ORIGINAL_ROWS_OR_SOURCE_LOCATION");
            var executable=new ArrayList<Map<String,Object>>();var planSites=selected.entrySet().stream().filter(e->e.getValue().plan()==plan).toList();
            for(int i=0;i<=rows.size();i++) {
                for(var entry:planSites)if(entry.getValue().after()==i)executable.add(Map.of("marker",entry.getKey(),"phase","SUCCEEDED","boundary",i));
                for(var entry:planSites)if(entry.getValue().first()==i)executable.add(Map.of("marker",entry.getKey(),"phase","ENABLED","boundary",i));
                if(i<rows.size())executable.add(rows.get(i));
            }
            need(SemanticJson.hash(executable).equals(SemanticJson.hash(proof.get("executableRows")))&&textHash(text(proof,"originalBody")).equals(proof.get("originalBodySha256"))&&textHash(text(proof,"executableBody")).equals(proof.get("executableBodySha256")),"EXECUTABLE_SOURCE_PARTITION_OR_HASH");
        }
        need(verified.equals(plans),"REQUESTED_SOURCE_PLAN_EVIDENCE_MISSING");
        var location=CanonicalJson.object(bindings.get("sourceLocation"));int expectedLine=line(site.plan().body().get(site.first()).metadata());if(expectedLine==0)expectedLine=line(site.plan().metadata());
        need(site.plan().metadata().semanticId().equals(location.get("planId"))&&site.first()==integer(location.get("firstOrdinal"))&&site.after()==integer(location.get("afterOrdinal"))&&expectedLine==integer(location.get("line")),"SITE_SOURCE_LOCATION");
        var valueQuality=CanonicalJson.object(bindings.get("stepValueQuality"));
        need("JASON_STEP_VALUES/2.0".equals(valueQuality.get("version"))&&"COMPLETE_CALLBACK".equals(valueQuality.get("enabled"))&&"PINNED_PLAN_BODY_ENTRY_MARKER".equals(valueQuality.get("enabledAuthority"))&&Set.of("ENABLED","SUCCEEDED","FAILED").contains(phase),"ENTRY_OR_PHASE_PROOF");
        need((phase.equals("ENABLED")?"UNAVAILABLE":"COMPLETE_CALLBACK").equals(valueQuality.get("succeeded"))&&(phase.equals("ENABLED")?"UNAVAILABLE":phase.equals("SUCCEEDED")?"PINNED_PLAN_BODY_EXIT_MARKER":"PUBLIC_GOAL_LISTENER_FAILURE").equals(valueQuality.get("succeededAuthority")),"TERMINAL_CALLBACK_PROOF");
        if(phase.equals("FAILED"))need(text(bindings,"sourceFailure").startsWith("GOAL_LISTENER_"),"FAILURE_AUTHORITY");
        if(bindings.containsKey("failureSite")) {
            var failure=CanonicalJson.object(bindings.get("failureSite"));keys(failure,Set.of("authority","term","bodyType","line","sourceHash"));
            need(phase.equals("FAILED")&&"ACTUAL_INTENDED_MEANS_CURRENT_INSTRUCTION/1.0".equals(failure.get("authority"))&&hash.equals(failure.get("sourceHash"))&&integer(failure.get("line"))>0&&!text(failure,"term").isBlank()&&text(failure,"term").length()<=4096&&!text(failure,"bodyType").isBlank(),"FAILURE_SITE_PROOF");
        }
        var values=CanonicalJson.object(bindings.get("values"));need(new HashSet<>(list(site.capture().get("variables"),32)).containsAll(values.keySet()),"UNREQUESTED_VARIABLE_CAPTURE");
        var inputs=CanonicalJson.object(bindings.get("inputs"));var requestedInputs=new HashMap<String,Map<String,Object>>();for(Object input:list(site.capture().get("inputs"),16)){var row=CanonicalJson.object(input);requestedInputs.put(text(row,"name"),row);}
        need(requestedInputs.keySet().containsAll(inputs.keySet()),"UNREQUESTED_INPUT_CAPTURE");
        for(var input:inputs.entrySet()) {var proof=CanonicalJson.object(input.getValue());var selectedInput=requestedInputs.get(input.getKey());need("JACA_PUBLIC_TERM_TO_OBJECT_GROUND_LITERAL_V1".equals(proof.get("authority"))&&selectedInput.get("type").equals(proof.get("javaType"))&&proof.get("value") instanceof String&&values.containsKey(selectedInput.get("variable"))&&SemanticJson.hash(values.get(selectedInput.get("variable"))).equals(proof.get("sourceTermHash")),"INPUT_SOURCE_TERM_PROOF");}
        var correlations=new HashSet<String>();var actions=list(bindings.get("actions"),32);
        for(Object raw:actions) {
            var action=CanonicalJson.object(raw);keys(action,Set.of("correlation","authority","action","operationName","result","failure"));
            String correlation=text(action,"correlation");UUID.fromString(correlation);
            need(correlations.add(correlation)&&"JASON_ACTIONEXEC_IDENTITY".equals(action.get("authority"))&&text(action,"operationName").matches("[A-Za-z_][A-Za-z0-9_]*")&&Set.of("SUCCEEDED","FAILED").contains(action.get("result"))&&!text(action,"action").isBlank(),"ACTION_RECEIPT_INVALID_OR_DUPLICATE");text(action,"failure");
        }
        String operation=text(data,"operationInvocation");need(operation.isBlank()==actions.isEmpty()&&(operation.isBlank()||operation.equals(CanonicalJson.object(actions.getLast()).get("correlation"))),"ACTION_CORRELATION_NOT_LAST_RECEIPT");
        String scheme=text(data,"schemeIdentity"),artifact=text(data,"artifactIdentity");
        if(text(site.capture(),"schemeVariable").isEmpty())need(scheme.isBlank(),"UNREQUESTED_SCHEME_REFERENCE");
        else if(!scheme.isBlank()) {var typed=CanonicalJson.object(bindings.get("schemeArtifact"));var id=BridgeEntityId.parse(scheme);artifactReference(typed,"ora4mas.nopl.SchemeBoard");need(id.authority().equals("moise")&&id.dimension().equals("organisation")&&id.kind().equals("scheme-board")&&id.localId().equals(typed.get("name"))&&id.incarnation().equals(run+":"+typed.get("uuid"))&&id.scope().equals(lastName(text(typed,"workspaceName"))),"SCHEME_REFERENCE_IDENTITY");}
        if(text(site.capture(),"artifactVariable").isEmpty())need(artifact.isBlank(),"UNREQUESTED_ARTIFACT_REFERENCE");
        else if(!artifact.isBlank()){var typed=CanonicalJson.object(bindings.get("artifact"));artifactReference(typed,text(site.capture(),"artifactClass"));need(artifact.equals(typed.get("semanticId")),"ARTIFACT_REFERENCE_IDENTITY");}
        if(observation.quality()==Completeness.COMPLETE)need((text(site.capture(),"schemeVariable").isEmpty()||!scheme.isBlank())&&(text(site.capture(),"artifactVariable").isEmpty()||!artifact.isBlank()||phase.equals("ENABLED")&&Boolean.TRUE.equals(site.capture().get("allowMissingArtifactAtEntry"))),"FALSE_SCOPE_COMPLETENESS");
        return new Step(task,agent.canonical(),declaration,goal,invocation,scheme,artifact,text(data,"operationInvocation"),phase,observation.quality(),observation.diagnostic(),true,phase.equals("ENABLED")?null:phase.equals("SUCCEEDED"),bindings);
    }
    private static void artifactReference(Map<String,Object> typed,String type){need("ARTIFACT_ID".equals(typed.get("kind"))&&type.equals(typed.get("typeHint"))&&!text(typed,"semanticId").isBlank(),"TYPED_ARTIFACT_REFERENCE");UUID.fromString(text(typed,"uuid"));UUID.fromString(text(typed,"workspaceUuid"));}
    private static String lastName(String path){return path.substring(path.lastIndexOf('/')+1);}
    private int line(SemanticMetadata metadata){return metadata.evidence().stream().filter(e->e.sourceDigest().equals(hash)).mapToInt(e->e.startLine()).findFirst().orElseThrow(()->new IllegalArgumentException("JASON_STEP_CUT:SOURCE_LINE_UNAVAILABLE"));}
    private static String textHash(String text){return org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(text.getBytes(StandardCharsets.UTF_8));}
    private static boolean token(String value,String prefix){if(!value.startsWith(prefix))return false;try{return Long.parseLong(value.substring(prefix.length()))>0;}catch(NumberFormatException invalid){return false;}}
    private static String text(Map<String,Object> value,String key){need(value.get(key) instanceof String,"TEXT_REQUIRED:"+key);return (String)value.get(key);}
    private static List<?> list(Object value,int capacity){need(value instanceof List<?> list&&list.size()<=capacity,"LIST_OR_CAPACITY");return (List<?>)value;}
    private static int integer(Object value){return Math.toIntExact(longInteger(value));}
    private static long longInteger(Object value){try{need(value instanceof Number,"INTEGER_REQUIRED");return new java.math.BigDecimal(value.toString()).longValueExact();}catch(ArithmeticException|NumberFormatException invalid){throw new IllegalArgumentException("JASON_STEP_CUT:INTEGER_REQUIRED");}}
    private static void keys(Map<String,Object> value,Set<String> expected){need(value.keySet().equals(expected),"FIELDS_UNSUPPORTED");}
    private static void need(boolean valid,String reason){if(!valid)throw new IllegalArgumentException("JASON_STEP_CUT:"+reason);}
}
