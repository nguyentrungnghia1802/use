package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;

/** Consumer-only, source-anchored evidence decoder. No Jason/CArtAgO classes,
 * domain state, execution history or Boolean fallback belongs here. */
public final class AuctionStepObservationDecoder {
    public record Step(String task,String agent,String declaration,String goalInvocation,String invocation,String scheme,String artifact,
            String operation,String phase,Completeness quality,String diagnostic,Map<String,Object> bindings) { }
    private record Boundary(String goal,int first,int after) { }
    private static final Map<String,Boundary> SITES=Map.of(
            "A_start_t1",new Boundary("start",0,5),"A_start_t2",new Boundary("start",5,6),
            "A_decide_t1",new Boundary("decide",0,2),"A_decide_t2",new Boundary("decide",2,3),
            "P_bid_t1",new Boundary("bid",0,3),"P_bid_t2",new Boundary("bid",3,4));
    private static final String RECIPE="AUCTION_PLAN_BODY_MARKERS/1.0";
    public static String organizationalGoal(String task){var site=SITES.get(task);if(site==null)throw invalid("TASK_BINDING");return site.goal();}
    private final SemanticProgram program;private final ModelSnapshot source;private final String aslHash,organization;
    public AuctionStepObservationDecoder(SemanticProgram program) {
        this(program,ContractPayloads.model(CanonicalJson.object(CanonicalJson.decode(program.complement().assets().stream().filter(a->a.id().equals("official-model")).findFirst().orElseThrow().bytes()))));
    }
    AuctionStepObservationDecoder(SemanticProgram program,ModelSnapshot source) {
        this.program=program;this.source=source;if(program.casePlan()==null)throw invalid("CASE_PROGRAM_REQUIRED");
        program.requireSourceHash(new org.tzi.use.plugins.jacamo.semantics.mapping.SourceModelNormalizer().normalize(source).sourceHash());
        aslHash=program.casePlan().bundle().require("src-asl").sha256();organization=program.casePlan().selectors().require("sel-org").getFirst().values().get("instance");
    }
    public Step decode(RuntimeEvent event) {
        return decode(VerificationObservation.require(event),event.sessionId(),event.generation(),event.modelRevision());
    }
    public Step decode(VerificationObservation observation,String run,long generation,String revision) {
        program.assertUnchanged();need(observation.kind()==VerificationObservation.Kind.JASON_STEP,"KIND");need(source.modelRevision().equals(revision),"REVISION");
        var data=observation.data();String task=text(data,"sourceStepId");var boundary=SITES.get(task);need(boundary!=null&&program.casePlan().tasks().stream().anyMatch(t->t.task().equals(task)),"TASK_BINDING");
        var bindings=map(data.get("bindings"));need(task.equals(bindings.get("taskCell"))&&"ACTUAL_INTENDED_MEANS_TRIGGER_IDENTITY".equals(bindings.get("intentionAuthority")),"SITE_OR_AUTHORITY");
        var scope=map(bindings.get("capturedRun"));need(run.equals(scope.get("sessionId"))&&generation==integer(scope.get("generation"))&&revision.equals(scope.get("modelRevision")),"MIXED_SCOPE");
        var agent=BridgeEntityId.parse(text(data,"agentIdentity"));String declaration=text(bindings,"agentDeclaration");
        need(agent.authority().equals("jason")&&agent.dimension().equals("agent")&&agent.kind().equals("runtime-agent")&&agent.scope().equals(source.semanticContract().project().name())
                &&agent.localId().equals(bindings.get("agentName"))&&agent.localId().equals(declaration),"AGENT_IDENTITY");
        need(source.semanticContract().agentDeclarations().stream().anyMatch(a->a.name().equals(declaration)&&a.instances()==1),"AGENT_DECLARATION");
        String goal=text(data,"goalInvocation"),invocation=text(data,"stepInvocation");
        need(token(goal,"jason-goal:"+run+":"+agent.incarnation()+":")&&token(invocation,"jason-step:"+run+":"+agent.incarnation()+":")&&invocation.equals(observation.subject()),"INVOCATION_OWNER");
        var originals=source.semanticContract().jasonPrograms().stream().filter(p->p.declarationId().equals(declaration)&&p.sourceDigest().equals(aslHash)).toList();need(originals.size()==1,"SOURCE_PROGRAM");
        var original=originals.getFirst();var manifest=map(bindings.get("instrumentation"));need(RECIPE.equals(manifest.get("recipe"))&&aslHash.equals(manifest.get("sourceSha256"))&&SemanticJson.hash(manifest).equals(bindings.get("instrumentationHash")),"RECIPE_HASH");
        var proofs=list(manifest.get("plans"));need(proofs.size()==3,"PLAN_SET");var validated=new HashMap<String,PlanSemantic>();
        for(Object raw:proofs) {
            var proof=map(raw);String id=text(proof,"planId");var matches=original.planLibrary().plans().stream().filter(p->p.metadata().semanticId().equals(id)&&p.metadata().evidence().stream().anyMatch(e->e.sourceDigest().equals(aslHash))).toList();need(matches.size()==1,"PLAN_SOURCE");
            var plan=matches.getFirst();var candidates=SITES.entrySet().stream().filter(e->plan.trigger().literal().equals(e.getValue().goal()+"[scheme(Sch)]")).toList();
            need(candidates.size()==2&&plan.trigger().type().equals("achieve")&&plan.trigger().operator().equals("add")&&!validated.containsKey(id),"PLAN_TRIGGER");
            var rows=plan.body().stream().map(b->Map.<String,Object>of("ordinal",b.ordinal(),"type",b.bodyType(),"term",b.term())).toList();
            need(SemanticJson.hash(rows).equals(SemanticJson.hash(proof.get("originalRows")))&&original.sourceUri().equals(proof.get("sourceUri"))&&line(plan.metadata())==integer(proof.get("sourceLine")),"ORIGINAL_ROWS_OR_LOCATION");
            var executable=new ArrayList<Map<String,Object>>();
            for(int i=0;i<=rows.size();i++) {
                for(var site:candidates.stream().sorted(Map.Entry.comparingByKey()).toList())if(site.getValue().after()==i)executable.add(Map.of("marker",site.getKey(),"phase","SUCCEEDED","boundary",i));
                for(var site:candidates.stream().sorted(Map.Entry.comparingByKey()).toList())if(site.getValue().first()==i)executable.add(Map.of("marker",site.getKey(),"phase","ENABLED","boundary",i));
                if(i<rows.size())executable.add(rows.get(i));
            }
            need(SemanticJson.hash(executable).equals(SemanticJson.hash(proof.get("executableRows"))),"EXECUTABLE_ROWS");
            need(hashText(text(proof,"originalBody")).equals(proof.get("originalBodySha256"))&&hashText(text(proof,"executableBody")).equals(proof.get("executableBodySha256")),"BODY_HASH");validated.put(id,plan);
        }
        var location=map(bindings.get("sourceLocation"));var plan=validated.get(text(location,"planId"));
        need(plan!=null&&plan.trigger().literal().equals(boundary.goal()+"[scheme(Sch)]")&&integer(location.get("firstOrdinal"))==boundary.first()&&integer(location.get("afterOrdinal"))==boundary.after(),"TASK_LOCATION");
        long line=line(plan.body().get(boundary.first()).metadata());if(line==0)line=line(plan.metadata());need(integer(location.get("line"))==line,"SITE_LINE");
        String scheme=text(data,"schemeIdentity"),artifact=text(data,"artifactIdentity");
        if(!scheme.isBlank()) {
            var typed=map(bindings.get("schemeArtifact"));var id=BridgeEntityId.parse(scheme);
            need("ARTIFACT_ID".equals(typed.get("kind"))&&"ora4mas.nopl.SchemeBoard".equals(typed.get("typeHint"))&&id.authority().equals("moise")&&id.dimension().equals("organisation")&&id.kind().equals("scheme-board")
                    &&id.scope().equals(organization)&&id.localId().equals(typed.get("name"))&&id.incarnation().equals(run+":"+typed.get("uuid")),"SCHEME_REFERENCE");
        }
        if(!artifact.isBlank()) {var typed=map(bindings.get("artifact"));need("ARTIFACT_ID".equals(typed.get("kind"))&&"auction_env.AuctionArtifact".equals(typed.get("typeHint"))&&artifact.equals(typed.get("semanticId")),"ARTIFACT_REFERENCE");}
        boolean beforeCreation=task.equals("A_start_t1")&&data.get("phase").equals("ENABLED");
        if(bindings.containsKey("stepValueQuality")) {
            var quality=map(bindings.get("stepValueQuality"));String phase=text(data,"phase");
            need("AUCTION_STEP_VALUES/1.0".equals(quality.get("version"))&&"COMPLETE_CALLBACK".equals(quality.get("enabled"))&&"PINNED_PLAN_BODY_ENTRY_MARKER".equals(quality.get("enabledAuthority")),"STEP_VALUE_QUALITY_VERSION_OR_ENTRY");
            need((phase.equals("ENABLED")?"UNAVAILABLE":"COMPLETE_CALLBACK").equals(quality.get("succeeded"))&&(phase.equals("ENABLED")?"UNAVAILABLE":phase.equals("SUCCEEDED")?"PINNED_PLAN_BODY_EXIT_MARKER":"PUBLIC_GOAL_LISTENER_FAILURE").equals(quality.get("succeededAuthority")),"STEP_VALUE_QUALITY_TERMINAL");
            if(phase.equals("FAILED"))need(bindings.get("sourceFailure") instanceof String failure&&failure.startsWith("GOAL_LISTENER_"),"STEP_FAILURE_CALLBACK_AUTHORITY");
        }
        if(observation.quality()==Completeness.COMPLETE)need(!scheme.isBlank()&&(!artifact.isBlank()||beforeCreation),"COMPLETE_SCOPE_PROOF");
        return new Step(task,agent.canonical(),declaration,goal,invocation,scheme,artifact,text(data,"operationInvocation"),text(data,"phase"),observation.quality(),observation.diagnostic(),bindings);
    }
    private static Map<String,Object> map(Object value){return CanonicalJson.object(value);}
    private static List<?> list(Object value){if(!(value instanceof List<?> list))throw invalid("LIST_REQUIRED");return list;}
    private static String text(Map<String,Object> map,String key){if(!(map.get(key) instanceof String text))throw invalid("TEXT_REQUIRED:"+key);return text;}
    private static long integer(Object value){try{if(!(value instanceof Number))throw invalid("INTEGER_REQUIRED");return new java.math.BigDecimal(value.toString()).longValueExact();}catch(RuntimeException invalid){throw invalid("INTEGER_REQUIRED");}}
    private static boolean token(String value,String prefix){if(!value.startsWith(prefix))return false;try{return Long.parseLong(value.substring(prefix.length()))>0;}catch(NumberFormatException invalid){return false;}}
    private long line(org.jacamo.bridge.contract.semantic.SemanticMetadata metadata){return metadata.evidence().stream().filter(e->e.sourceDigest().equals(aslHash)).mapToInt(org.jacamo.bridge.contract.semantic.SourceEvidence::startLine).findFirst().orElseThrow(()->invalid("SOURCE_LINE_UNAVAILABLE"));}
    private static String hashText(String text){return org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static void need(boolean condition,String reason){if(!condition)throw invalid(reason);}
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("AUCTION_STEP_"+reason);}
}
