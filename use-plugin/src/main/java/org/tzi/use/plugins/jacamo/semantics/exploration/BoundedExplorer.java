package org.tzi.use.plugins.jacamo.semantics.exploration;

import java.util.*;
import java.io.*;
import org.tzi.use.api.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.sys.*;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import static org.tzi.use.plugins.jacamo.semantics.exploration.ConformanceSummary.*;

/** Offline finite-table DFS. No state merging or partial-order reduction; every path keeps its own monitor history. */
public final class BoundedExplorer {
    public ConformanceSummary explore(MModel schema,TransitionProfile profile,ExplorationBounds bounds) {
        var run=new Run(schema,profile,bounds);
        try {return run.execute();}catch(UnsupportedOperationException|IllegalArgumentException e){return run.unsupported(e.getMessage());}
        catch(Exception e){run.diagnostics.add("EXPLORER_ERROR:"+e.getClass().getName()+":"+e.getMessage());run.cut(List.of(),"","BACKEND_ERROR");return run.summary(false);}
    }
    private record Predicates(Expression activation,Expression condition){}
    private record Edge(Expression guard,Expression post){}
    private record Work(String state,TemporalGoalMonitor.Snapshot history,List<String> path,Map<String,Integer> visits,List<Check> checks,Map<String,SemanticVerdict> tasks){}
    private static final class Run {
        final MModel schema;final TransitionProfile p;final ExplorationBounds b;final long started=System.nanoTime();final String hash;
        final List<String> diagnostics=new ArrayList<>();final List<Frontier> frontier=new ArrayList<>();final List<Witness> witnesses=new ArrayList<>();
        final Map<String,MObject> objects=new HashMap<>();final Map<String,MSystemState> images=new HashMap<>();final Map<String,Predicates> predicates=new HashMap<>();final Map<String,Edge> edges=new HashMap<>();
        final OclEvaluationAdapter evaluator=new OclEvaluationAdapter();final TemporalGoalMonitor monitor;MSystem isolated;UseSystemApi api;
        long nodes,complete,ok,bad,unknown,cutoffs;boolean risk;
        Run(MModel schema,TransitionProfile p,ExplorationBounds b){this.schema=Objects.requireNonNull(schema);this.p=Objects.requireNonNull(p);this.b=Objects.requireNonNull(b);hash=SemanticJson.hash(SemanticJson.tree(p));monitor=new TemporalGoalMonitor(p.requirements());}
        ConformanceSummary execute()throws Exception {
            if(!TransitionProfile.VERSION.equals(p.version()) || !TransitionProfile.APPROVAL.equals(p.approval()))return unsupported("UNAPPROVED_TRANSITION_ABSTRACTION");
            if(p.population().size()>b.population()){cut(List.of(),"","POPULATION_BOUND");return summary(false);}
            // This isolated evaluator owns no live coordinator and never receives bridge events.
            isolated=new MSystem(schema);api=UseSystemApi.create(isolated,false);
            for(var obj:p.population()) {var cls=schema.getClass(obj.type());if(cls==null || cls.isAbstract())throw new UnsupportedOperationException("FINITE_CLASS_UNRESOLVED:"+obj.type());objects.put(obj.identity(),api.createObject(obj.type(),obj.identity()));}
            for(var link:p.fixedLinks()){var assoc=schema.getAssociation(link.association());if(assoc==null || assoc.associationEnds().stream().anyMatch(e->!e.getQualifiers().isEmpty()))throw new UnsupportedOperationException("FINITE_LINK_UNRESOLVED_OR_QUALIFIED:"+link.association());var participants=new ArrayList<MObject>();for(var id:link.participants()){var obj=objects.get(id);if(obj==null)throw new IllegalArgumentException("FINITE_LINK_OBJECT_UNRESOLVED:"+id);participants.add(obj);}api.createLinkEx(assoc,participants.toArray(MObject[]::new));}
            for(var state:p.states().values())validate(state);
            for(var node:p.requirements().nodes()) {
                var obj=objects.get(node.binding().selfIdentity());if(obj==null)throw new IllegalArgumentException("FINITE_REQUIREMENT_SELF_UNRESOLVED:"+node.id());
                predicates.put(node.id(),new Predicates(SemanticOclCompiler.predicate(schema,obj.cls(),node.id()+":activation",node.activation()),node.condition().isBlank()?null:SemanticOclCompiler.predicate(schema,obj.cls(),node.id()+":condition",node.condition())));
            }
            for(var t:p.transitions()){var self=objects.get(t.self());if(self==null)throw new IllegalArgumentException("FINITE_TRANSITION_SELF_UNRESOLVED:"+t.id());edges.put(t.id(),new Edge(SemanticOclCompiler.predicate(schema,self.cls(),t.id()+":guard",t.guard()),SemanticOclCompiler.transitionPredicate(schema,self.cls(),t.id()+":post",t.post())));for(var task:t.taskOutcomes().keySet())if(p.requirements().nodes().stream().noneMatch(n->n.id().equals(task) && n.kind()==RequirementModel.Kind.TASK))throw new IllegalArgumentException("FINITE_TASK_CONTRACT_BINDING_UNRESOLVED:"+task);}
            var initialHistory=monitor.savepoint();var stack=new ArrayDeque<Work>();for(var id:new TreeSet<>(p.initial()))stack.push(new Work(id,initialHistory,List.of(),Map.of(),List.of(),Map.of()));
            while(!stack.isEmpty()) {
                var w=stack.pop();
                if(nodes>=b.nodes() || complete>=b.completeScenarios() || (System.nanoTime()-started)/1_000_000>=b.timeoutMillis()) {cut(w.path(),w.state(),nodes>=b.nodes()?"NODE_BOUND":complete>=b.completeScenarios()?"COMPLETE_SCENARIO_BOUND":"TIMEOUT_BETWEEN_EVALUATIONS");for(var pending:stack)cut(pending.path(),pending.state(),"UNEXPLORED_FRONTIER");break;}
                if(p.states().get(w.state()).clock()>b.logicalClock()){cut(w.path(),w.state(),"LOGICAL_CLOCK_BOUND");continue;}
                int visited=w.visits().getOrDefault(w.state(),0)+1;if(visited>b.visitsPerState()){cut(w.path(),w.state(),"LOOP_VISIT_BOUND");continue;}
                var visits=new HashMap<>(w.visits());visits.put(w.state(),visited);nodes++;
                var image=image(w.state());var checks=new ArrayList<>(w.checks());boolean admissible=true;
                for(var invariant:schema.classInvariants().stream().filter(MClassInvariant::isActive).sorted(Comparator.comparing(MClassInvariant::qualifiedName)).toList()) {
                    var value=evaluator.evaluate(invariant.flaggedExpression(),image,image,new VarBindings());checks.add(new Check(invariant.qualifiedName(),invariant.flaggedExpression().toString(),value.verdict(),value.backendValue(),value.diagnostic()));if(value.verdict()!=SemanticVerdict.PASS)admissible=false;
                }
                if(!admissible){cut(w.path(),w.state(),"STATE_NOT_PROVEN_ADMISSIBLE");record(w.path(),w.state(),Map.of(),checks);continue;}
                monitor.restore(w.history());var inputs=new TreeMap<String,TemporalGoalMonitor.Input>();
                for(var node:p.requirements().nodes()) {var vars=vars(node.binding().selfIdentity());var expr=predicates.get(node.id());var active=evaluator.evaluate(expr.activation(),image,image,vars);var condition=node.kind()==RequirementModel.Kind.TASK?w.tasks().getOrDefault(node.id(),SemanticVerdict.INCONCLUSIVE):expr.condition()==null?SemanticVerdict.INCONCLUSIVE:evaluator.evaluate(expr.condition(),image,image,vars).verdict();inputs.put(node.id(),new TemporalGoalMonitor.Input(active.verdict(),condition,SemanticJson.hash(SemanticJson.tree(node.binding())),"FINITE_PROFILE_AUTHORED_BINDING"));}
                var markings=monitor.evaluate(w.path().size(),inputs,false);boolean violated=p.roots().stream().anyMatch(r->markings.get(r).evaluated()==IntentionalMarking.Status.VIOLATED);if(violated){risk=true;record(w.path(),w.state(),markings,checks);}
                if(p.terminal().contains(w.state())) {complete++;if(p.roots().stream().allMatch(r->markings.get(r).lifecycle()==IntentionalMarking.Lifecycle.ACTIVE && markings.get(r).evaluated()==IntentionalMarking.Status.SATISFIED))ok++;else if(violated)bad++;else unknown++;if(!violated && witnesses.size()<128)record(w.path(),w.state(),markings,checks);continue;}
                if(w.path().size()>=b.steps()){cut(w.path(),w.state(),"STEP_BOUND");continue;}
                int enabled=0;var history=monitor.savepoint();
                for(var transition:p.transitions())if(transition.from().equals(w.state())) {
                    var vars=vars(transition.self());var guard=evaluator.evaluate(edges.get(transition.id()).guard(),image,image,vars);if(guard.verdict()==SemanticVerdict.FAIL)continue;
                    if(guard.verdict()!=SemanticVerdict.PASS){cut(w.path(),w.state(),"GUARD_"+guard.verdict()+":"+transition.sourceId());continue;}enabled++;
                    var next=image(transition.to());var post=evaluator.evaluate(edges.get(transition.id()).post(),image,next,vars);var pair=new Check(transition.sourceId(),transition.post(),post.verdict(),post.backendValue(),post.diagnostic());var nextChecks=new ArrayList<>(checks);nextChecks.add(pair);var path=new ArrayList<>(w.path());path.add(transition.id());
                    if(post.verdict()!=SemanticVerdict.PASS){cut(path,transition.to(),"TRANSITION_POST_"+post.verdict());record(path,transition.to(),markings,nextChecks);continue;}
                    stack.push(new Work(transition.to(),history,List.copyOf(path),Map.copyOf(visits),List.copyOf(nextChecks),transition.taskOutcomes()));
                }
                if(enabled==0)cut(w.path(),w.state(),"NONTERMINAL_DEADLOCK_OR_UNKNOWN_GUARD");
            }
            if(!p.transitionCoverageComplete())diagnostics.add("TRANSITION_TABLE_COVERAGE_UNPROVEN");if(frontier.size()==128)diagnostics.add("FRONTIER_DISPLAY_LIMIT_128:total="+cutoffs);if(witnesses.size()==128)diagnostics.add("WITNESS_DISPLAY_LIMIT_128");
            return summary(cutoffs==0 && p.transitionCoverageComplete());
        }
        void validate(TransitionProfile.State state) {
            if(!state.values().keySet().equals(objects.keySet()))throw new IllegalArgumentException("FINITE_COMPLETE_POPULATION_VECTOR_REQUIRED");
            for(var e:state.values().entrySet()){var obj=objects.get(e.getKey());var required=new TreeSet<String>();obj.cls().allAttributes().forEach(a->required.add(a.name()));if(!required.equals(e.getValue().keySet()))throw new IllegalArgumentException("FINITE_COMPLETE_ATTRIBUTE_VECTOR_REQUIRED:"+e.getKey());for(var attribute:e.getValue().entrySet()){var definition=obj.cls().attribute(attribute.getKey(),true);if(!definition.type().toString().equals(attribute.getValue().type()))throw new IllegalArgumentException("FINITE_SCALAR_TYPE_MISMATCH:"+attribute.getKey());attribute.getValue().value();}}
        }
        MSystemState image(String id)throws Exception {
            if(images.containsKey(id))return images.get(id);for(var object:p.states().get(id).values().entrySet())for(var attr:object.getValue().entrySet())api.setAttributeValueEx(objects.get(object.getKey()),objects.get(object.getKey()).cls().attribute(attr.getKey(),true),attr.getValue().value());
            var copy=new MSystemState("finite:"+id,isolated.state());var errors=new StringWriter();if(!copy.checkStructure(new PrintWriter(errors),true))throw new UnsupportedOperationException("FINITE_STRUCTURAL_FRAME_INVALID:"+errors);images.put(id,copy);return copy;
        }
        VarBindings vars(String self){var vars=new VarBindings();vars.push("self",objects.get(self).value());return vars;}
        void cut(List<String> path,String state,String reason){cutoffs++;if(frontier.size()<128)frontier.add(new Frontier(path,state,reason));}
        void record(List<String> path,String state,Map<String,IntentionalMarking> marks,List<Check> checks){if(witnesses.size()<128)witnesses.add(new Witness(path,state,p.states().get(state),marks,checks,"ABSTRACT_RELATION_ONLY_REQUIRES_OBSERVED_TRACE_MATCH"));}
        ConformanceSummary unsupported(String reason){diagnostics.add(reason);return new ConformanceSummary(Status.UNSUPPORTED,"EXPLICIT_FINITE_ABSTRACTION_ONLY",hash,b,p.assumptions(),nodes,complete,ok,bad,unknown,false,risk?Risk.VIOLATED_ROOT_WITNESS:Risk.UNKNOWN,frontier,witnesses,diagnostics);}
        ConformanceSummary summary(boolean exhaustive) {var status=!exhaustive || complete==0 || unknown>0?Status.INCONCLUSIVE:bad==0?Status.CONFORMANT_WITHIN_BOUNDS:ok==0?Status.NON_CONFORMANT:Status.WEAK;return new ConformanceSummary(status,"EXPLICIT_FINITE_ABSTRACTION_ONLY_NO_JACAMO_BEHAVIORAL_EQUIVALENCE",hash,b,p.assumptions(),nodes,complete,ok,bad,unknown,exhaustive,risk?Risk.VIOLATED_ROOT_WITNESS:exhaustive && unknown==0 && complete>0?Risk.NO_VIOLATED_ROOT_WITHIN_EXHAUSTIVE_BOUNDS:Risk.UNKNOWN,frontier,witnesses,diagnostics);}
    }
}
