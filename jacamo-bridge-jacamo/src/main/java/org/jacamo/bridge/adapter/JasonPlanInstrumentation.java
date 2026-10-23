package org.jacamo.bridge.adapter;

import java.nio.file.*;
import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.*;

/** Source-pinned preparation of disposable runtime plan bodies. Uses Jason's
 * public AST API; never rewrites an ASL file, context, query, branch or action.
 * A caller must install before reasoning starts and own the returned cleanup. */
public class JasonPlanInstrumentation {
    public static final String RECIPE="JASON_PLAN_BODY_MARKERS/2.0";
    public record Boundary(String task,int first,int after) {
        public Boundary {if(task==null||!task.matches("[A-Za-z_][A-Za-z0-9_]*")||first<0||after<=first||after>256)throw invalid("BOUNDARY_INVALID");}
    }
    public record PlanSelection(String planId,List<Boundary> boundaries) {
        public PlanSelection {
            boundaries=List.copyOf(boundaries);
            if(planId==null||planId.isBlank()||boundaries.isEmpty()||boundaries.size()>64)throw invalid("PLAN_SELECTION_INVALID");
            int next=0;var names=new HashSet<String>();
            for(var boundary:boundaries){if(boundary.first()!=next||!names.add(boundary.task()))throw invalid("BOUNDARY_PARTITION_INVALID");next=boundary.after();}
        }
    }
    public record Specification(String recipe,String sourceHash,List<PlanSelection> plans) {
        public Specification {
            plans=List.copyOf(plans);
            if(recipe==null||recipe.isBlank()||sourceHash==null||!sourceHash.matches("[0-9a-f]{64}")||plans.isEmpty()||plans.size()>64
                    ||plans.stream().map(PlanSelection::planId).distinct().count()!=plans.size())throw invalid("SPECIFICATION_INVALID");
        }
    }
    @FunctionalInterface protected interface MarkerFactory {PlanBody create(Site site,String phase,Reporter reporter);}
    public record Site(String task,String planId,int first,int after,int line) { }
    public interface Reporter {
        void marker(TransitionSystem ts,Site site,String phase);
        void unavailable(String diagnostic);
    }
    private record Change(Plan plan,PlanBody original,PlanBody executable,String before,List<Map<String,Object>> rows,List<Site> sites,Map<String,Object> proof) { }
    public static final class Prepared implements AutoCloseable {
        private final Agent agent;private final Path source;private final Specification specification;private final List<Change> changes;
        private final Map<String,Object> manifest;private final String hash;private boolean installed,closed;
        private Prepared(Agent agent,Path source,List<Change> changes,Specification specification) {
            this.agent=agent;this.source=source;this.specification=specification;this.changes=List.copyOf(changes);
            manifest=CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(Map.of("recipe",specification.recipe(),"sourceSha256",specification.sourceHash(),"plans",changes.stream().map(Change::proof).toList()))));
            hash=AdapterEvidence.digest(CanonicalJson.encode(manifest));
        }
        public Map<String,Object> manifest(){return manifest;}
        public String hash(){return hash;}
        public List<Site> sites(){return changes.stream().flatMap(c->c.sites().stream()).toList();}
        public void install()throws java.io.IOException {
            if(installed||closed)throw invalid("OWNER_ALREADY_USED");
            if(!specification.sourceHash().equals(AdapterEvidence.digest(Files.readAllBytes(source))))throw invalid("SOURCE_CHANGED_AFTER_PREPARATION");
            if(agent.getTS()!=null&&agent.getTS().getC()!=null) {
                // Circumstance also enumerates EmptyInt attached to pending
                // initial goal events; that is not an instantiated plan body.
                for(var view:JasonIntentionViews.capture(agent.getTS().getC()))if(!view.intention().isFinished())throw invalid("REASONING_ALREADY_STARTED");
            }
            for(var c:changes)if(c.plan().getBody()!=c.original()||!c.before().equals(c.original().toString()))throw invalid("PLAN_CHANGED_AFTER_PREPARATION");
            for(var c:changes)c.plan().setTerm(3,c.executable());
            installed=true;
        }
        @Override public void close() {
            if(closed)return;closed=true;
            if(installed)for(var c:changes)if(c.plan().getBody()==c.executable())c.plan().setTerm(3,c.original());
        }
    }
    protected JasonPlanInstrumentation(){ }
    public static Prepared prepare(Agent agent,AgentProgramSemantic source,Path projectRoot,Reporter reporter,Specification specification)throws Exception {
        return prepare(agent,source,projectRoot,reporter,specification,JasonPlanInstrumentation::marker);
    }
    protected static Prepared prepare(Agent agent,AgentProgramSemantic source,Path projectRoot,Reporter reporter,Specification specification,MarkerFactory factory)throws Exception {
        Objects.requireNonNull(agent);Objects.requireNonNull(reporter);
        if(!specification.sourceHash().equals(source.sourceDigest())||!source.sourceUri().startsWith("project:/"))throw invalid("SOURCE_NOT_ADMITTED");
        Path root=projectRoot.toRealPath(),file=root.resolve(source.sourceUri().substring("project:/".length())).normalize().toRealPath();
        if(!file.startsWith(root)||Files.size(file)>1024*1024||!specification.sourceHash().equals(AdapterEvidence.digest(Files.readAllBytes(file))))throw invalid("SOURCE_BYTES_MISMATCH");
        var originals=source.planLibrary().plans().stream().filter(p->p.metadata().evidence().stream().anyMatch(e->e.sourceDigest().equals(specification.sourceHash()))).toList();
        var runtime=new ArrayList<Plan>();
        for(var plan:agent.getPL())if(plan.getSrcInfo()!=null&&sameFile(root,file,plan.getSrcInfo().getSrcFile()))runtime.add(plan);
        if(runtime.size()!=originals.size())throw invalid("SOURCE_PLAN_SET_CHANGED");
        var changes=new ArrayList<Change>();var matched=new HashSet<String>();
        for(var plan:runtime) {
            var matches=originals.stream().filter(p->matches(plan,p)).toList();
            if(matches.size()!=1||!matched.add(matches.getFirst().metadata().semanticId()))throw invalid("SOURCE_AST_CHANGED");
            var original=matches.getFirst();var selections=specification.plans().stream().filter(s->s.planId().equals(original.metadata().semanticId())).toList();
            if(selections.isEmpty())continue;
            if(!plan.getTrigger().isAchvGoal()||!plan.getTrigger().isAddition())throw invalid("REQUESTED_TRIGGER_KIND_UNSUPPORTED");
            var boundaries=selections.getFirst().boundaries();
            var nodes=nodes(plan.getBody());if(boundaries.getLast().after()!=nodes.size())throw invalid("SOURCE_BOUNDARY_CHANGED");
            var sites=boundaries.stream().map(b->new Site(b.task(),original.metadata().semanticId(),b.first(),b.after(),nodes.get(b.first()).getSrcInfo()==null?plan.getSrcInfo().getBeginSrcLine():nodes.get(b.first()).getSrcInfo().getBeginSrcLine())).toList();
            var proofRows=new ArrayList<Map<String,Object>>();PlanBody executable=null,last=null;
            for(int i=0;i<=nodes.size();i++) {
                var inserted=new ArrayList<PlanBody>();
                for(var site:sites)if(site.after()==i){inserted.add(factory.create(site,"SUCCEEDED",reporter));proofRows.add(Map.of("marker",site.task(),"phase","SUCCEEDED","boundary",i));}
                for(var site:sites)if(site.first()==i){inserted.add(factory.create(site,"ENABLED",reporter));proofRows.add(Map.of("marker",site.task(),"phase","ENABLED","boundary",i));}
                if(i<nodes.size()) {
                    var copy=nodes.get(i).clonePB();copy.setBodyNext(null);inserted.add(copy);
                    proofRows.add(Map.of("ordinal",i,"type",nodes.get(i).getBodyType().name(),"term",OfficialJasonAdapter.canonicalAst(nodes.get(i).getBodyTerm().toString())));
                }
                for(var part:inserted){if(executable==null)executable=part;else last.setBodyNext(part);last=part;}
            }
            var proof=new LinkedHashMap<String,Object>();proof.put("planId",original.metadata().semanticId());proof.put("sourceUri",source.sourceUri());proof.put("sourceLine",plan.getSrcInfo().getBeginSrcLine());
            proof.put("originalRows",rows(plan.getBody()));proof.put("executableRows",proofRows);proof.put("originalBody",plan.getBody().toString());proof.put("executableBody",executable.toString());
            proof.put("originalBodySha256",AdapterEvidence.digest(plan.getBody().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));proof.put("executableBodySha256",AdapterEvidence.digest(executable.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            changes.add(new Change(plan,plan.getBody(),executable,plan.getBody().toString(),List.copyOf(proofRows),sites,Map.copyOf(proof)));
        }
        if(changes.size()!=specification.plans().size())throw invalid("REQUESTED_PLAN_SITES_UNRESOLVED");
        return new Prepared(agent,file,changes,specification);
    }
    private static boolean matches(Plan plan,PlanSemantic source) {
        var trigger=plan.getTrigger();return trigger.getType().name().equals(source.trigger().type())&&trigger.getOperator().name().equals(source.trigger().operator())
                &&OfficialJasonAdapter.canonicalAst(trigger.getLiteral().toString()).equals(source.trigger().literal())&&OfficialJasonAdapter.canonicalAst(String.valueOf(plan.getContext())).equals(source.context())
                &&rows(plan.getBody()).equals(source.body().stream().map(b->Map.<String,Object>of("ordinal",b.ordinal(),"type",b.bodyType(),"term",b.term())).toList());
    }
    private static List<Map<String,Object>> rows(PlanBody body) {
        var result=new ArrayList<Map<String,Object>>();int i=0;for(var n:nodes(body))result.add(Map.of("ordinal",i++,"type",n.getBodyType().name(),"term",n.getBodyTerm()==null?"":OfficialJasonAdapter.canonicalAst(n.getBodyTerm().toString())));return List.copyOf(result);
    }
    private static List<PlanBody> nodes(PlanBody body) {
        var result=new ArrayList<PlanBody>();var seen=Collections.newSetFromMap(new IdentityHashMap<PlanBody,Boolean>());
        for(var n=body;n!=null;n=n.getBodyNext()){if(!seen.add(n)||result.size()>=256)throw invalid("BODY_CYCLE_OR_CAPACITY");result.add(n);}return List.copyOf(result);
    }
    private static boolean sameFile(Path root,Path expected,String source)throws java.io.IOException {
        if(source.startsWith("jar:")||source.startsWith("ClassResource:"))return false;
        Path actual;if(source.startsWith("file:")){var uri=java.net.URI.create(source.replace(" ","%20"));actual=uri.isOpaque()?Path.of(uri.getSchemeSpecificPart()):Path.of(uri);}else actual=Path.of(source);
        if(!actual.isAbsolute())actual=root.resolve(actual);return Files.isRegularFile(actual)&&actual.toRealPath().equals(expected);
    }
    private static PlanBody marker(Site site,String phase,Reporter reporter) {
        var literal=new InternalActionLiteral(Marker.class.getName());literal.addTerm(ASSyntax.createString(site.task()));literal.addTerm(ASSyntax.createString(phase));literal.setIA(new Marker(site,phase,reporter));
        return new PlanBodyImpl(PlanBody.BodyType.internalAction,literal);
    }
    private static final class Marker extends DefaultInternalAction {
        private final Site site;private final String phase;private final Reporter reporter;
        Marker(Site site,String phase,Reporter reporter){this.site=site;this.phase=phase;this.reporter=reporter;}
        @Override public Object execute(TransitionSystem ts,Unifier unifier,Term[] args) {
            try {reporter.marker(ts,site,phase);}catch(Exception missing){try{reporter.unavailable("STEP_MARKER_CAPTURE_FAILED:"+missing.getClass().getSimpleName());}catch(RuntimeException retired){/* Retired transport cannot grant a Boolean result. */}}
            return Boolean.TRUE; // Observation never changes the original deed's outcome.
        }
    }
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("JASON_STAGING_"+reason);}
}
