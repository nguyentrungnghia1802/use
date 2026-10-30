package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;
import org.tzi.use.semantic.engine.api.EngineSemanticsProfile;
import org.tzi.use.semantic.engine.api.OwnedGoalModel;
import org.tzi.use.semantic.engine.api.RoleSpecialization;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance;

/** Explicit contextual execution M1 over the original graph. Quantification is mapping data;
 * the original CSL/iStar engines and their marking recurrence are untouched. */
public final class OccurrenceGoalModelExpander {
    public static final String VERSION="DECLARED_OCCURRENCE_GRAPH/1.0";
    public enum Quantifier { ALL, ANY, SAME_PLAYER }
    public record Player(String sourceActor,String actorType,String instance,String player,String role,String roleInterval) {
        public Player {for(String value:List.of(sourceActor,actorType,instance,player))if(value.isBlank())throw new IllegalArgumentException("OCCURRENCE_PLAYER_IDENTITY_REQUIRED");Objects.requireNonNull(role);Objects.requireNonNull(roleInterval);}
    }
    public record Trace(String expanded,String source,String actor,String player,String role,String kind) { }
    public record Expansion(OwnedGoalModel owned,List<ScenarioInstance> cast,Map<String,Trace> trace,String hash) {
        public Expansion {cast=List.copyOf(cast);trace=Map.copyOf(trace);}
        public GoalModel model(){return owned.model();}
    }
    private final GoalModel source;private final String canonicalHash;private final EngineSemanticsProfile profile;private final Map<String,Quantifier> policies;
    public OccurrenceGoalModelExpander(GoalModel source,EngineSemanticsProfile profile,Map<String,Quantifier> policies) {
        this.source=new OwnedGoalModel(source,profile).model();this.profile=profile;canonicalHash=OwnedGoalModel.fingerprint(source);this.policies=Map.copyOf(policies);
        for(String target:policies.keySet())if(source.getDependencies().stream().noneMatch(d->d.dependum().id().equals(target)))throw new IllegalArgumentException("OCCURRENCE_QUANTIFIER_TARGET_UNRESOLVED:"+target);
        for(var actor:source.getActors())for(var relation:actor.associations())if(relation.kind()==AssocKind.IS_A
                &&(!profile.roleSpecialization()||!RoleSpecialization.admitted(source,actor,relation)))throw new IllegalArgumentException("UNSUPPORTED:INTENTIONAL_ROLE_INHERITANCE");
    }
    public static Map<String,Quantifier> policies(MappingSpecification specification,GoalModel model) {
        var result=new TreeMap<String,Quantifier>();
        for(var node:specification.root().child("bindings").children("dependencyPopulation")) {
            if(model.getDependencies().stream().noneMatch(d->d.dependum().id().equals(node.attr("target"))))throw node.location().error("DEPENDENCY_POPULATION_TARGET_UNRESOLVED");
            Quantifier policy;try{policy=Quantifier.valueOf(node.attr("quantifier"));}catch(IllegalArgumentException bad){throw node.location().error("DEPENDENCY_QUANTIFIER_UNSUPPORTED");}
            if(result.putIfAbsent(node.attr("target"),policy)!=null)throw node.location().error("DEPENDENCY_QUANTIFIER_DUPLICATE");
        }
        return Collections.unmodifiableMap(result);
    }
    private void assertUnchanged(){if(!canonicalHash.equals(OwnedGoalModel.fingerprint(source)))throw new IllegalStateException("OCCURRENCE_SOURCE_MODEL_MUTATED");}
    public Expansion expand(List<Player> members,String executionName) {
        assertUnchanged();if(members.isEmpty()||members.size()>4096||executionName==null||!executionName.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IllegalArgumentException("OCCURRENCE_CAST_OR_MODEL_NAME_INVALID");
        if(members.stream().map(Player::actorType).distinct().count()!=members.size()||members.stream().map(Player::instance).distinct().count()!=members.size())throw new IllegalArgumentException("OCCURRENCE_ACTOR_OR_INSTANCE_AMBIGUOUS");
        for(var member:members) {
            var actor=source.findActor(member.sourceActor()).orElseThrow(()->new IllegalArgumentException("OCCURRENCE_SOURCE_ACTOR_UNRESOLVED"));
            if(profile.roleSpecialization()&&RoleSpecialization.definitionOnly(source,actor))throw new IllegalArgumentException("MODEL_ONLY_ROLE_MUST_NOT_HAVE_RUNTIME_CAST");
        }
        var actors=new LinkedHashMap<String,Actor>();var traces=new TreeMap<String,Trace>();var cast=new ArrayList<ScenarioInstance>();
        var rows=members.stream().sorted(Comparator.comparing(Player::actorType)).toList();
        var bySource=new TreeMap<String,List<Player>>();rows.forEach(p->bySource.computeIfAbsent(p.sourceActor(),k->new ArrayList<>()).add(p));
        for(var original:source.getActors())if(bySource.getOrDefault(original.name(),List.of()).isEmpty()
                &&!(profile.roleSpecialization()&&RoleSpecialization.definitionOnly(source,original)))throw new IllegalArgumentException("OCCURRENCE_EXPANSION_MISSING_ACTOR:"+original.name());
        var targets=source.getDependencies().stream().filter(d->bySource.get(d.dependee()).size()>1&&policies.get(d.dependum().id())!=Quantifier.SAME_PLAYER).toList();
        for(var dependency:targets) {
            if(!policies.containsKey(dependency.dependum().id()))throw new IllegalArgumentException("OCCURRENCE_EXPLICIT_QUANTIFIER_REQUIRED:"+dependency.dependum().id());
            if(!(source.findElement(dependency.dependerElmt()).orElse(null) instanceof Goal)||!(dependency.dependum() instanceof Goal))throw new IllegalArgumentException("OCCURRENCE_POPULATION_GOAL_DEPENDENCY_REQUIRED");
        }
        for(var player:rows) {
            var original=source.findActor(player.sourceActor()).orElseThrow();var elements=new ArrayList<IntentionalElement>();var refinements=new ArrayList<Refinement>();
            for(var element:original.elements()) {String id=id(player,element.id());elements.add(copy(element,id));traces.put(id,new Trace(id,element.id(),player.sourceActor(),player.player(),player.role(),"SOURCE_ELEMENT"));}
            for(var refinement:original.refinements()) {
                if(refinement instanceof AndRefinement and)refinements.add(new AndRefinement(id(player,and.parent()),and.children().stream().map(c->id(player,c)).toList()));
                else if(refinement instanceof OrRefinement or)refinements.add(new OrRefinement(id(player,or.parent()),id(player,or.child())));
                else throw new IllegalArgumentException("OCCURRENCE_EXPANSION_REFINEMENT_UNSUPPORTED");
            }
            for(var dependency:targets)if(dependency.depender().equals(player.sourceActor())) {
                var children=new ArrayList<String>();
                for(var recipient:bySource.get(dependency.dependee())) {
                    String child=receiver(dependency,recipient);children.add(child);var type=(Goal)source.findElement(dependency.dependerElmt()).orElseThrow();elements.add(new Goal(child,type.goalType()));
                    traces.put(child,new Trace(child,dependency.dependerElmt(),player.sourceActor(),recipient.player(),recipient.role(),policies.get(dependency.dependum().id())+"_DEPENDENCY_RECEIVER"));
                }
                if(policies.get(dependency.dependum().id())==Quantifier.ALL)refinements.add(new AndRefinement(id(player,dependency.dependerElmt()),children));
                else children.forEach(child->refinements.add(new OrRefinement(id(player,dependency.dependerElmt()),child)));
            }
            var resources=original.neededBys().stream().map(n->new NeededBy(id(player,n.resource()),id(player,n.task()))).toList();
            var associations=new ArrayList<Association>();
            for(var relation:original.associations()) {
                if(relation.kind()==AssocKind.IS_A) {
                    // Canonical metadata is retained in the source hash and trace.
                    // An empty capability parent creates no player or enactment.
                    String key="specialization:"+player.actorType()+":"+relation.target();
                    traces.put(key,new Trace(key,relation.target(),player.sourceActor(),player.player(),player.role(),"DEFINITION_ONLY_SPECIALIZATION"));
                    continue;
                }
                var target=bySource.getOrDefault(relation.target(),List.of());
                if(relation.kind()==AssocKind.PARTICIPATES_IN) {
                    if(!(original instanceof Agent)||!(source.findActor(relation.target()).orElseThrow() instanceof Role))throw new IllegalArgumentException("OCCURRENCE_PARTICIPATION_REQUIRES_AGENT_ROLE");
                    var matching=target.stream().filter(p->p.player().equals(player.player())).toList();
                    if(matching.size()>1)throw new IllegalArgumentException("OCCURRENCE_PARTICIPATION_AMBIGUOUS");
                    matching.forEach(p->associations.add(new Association(player.actorType(),relation.kind(),p.actorType())));
                }else {
                    if(target.size()!=1)throw new IllegalArgumentException("OCCURRENCE_EXPANSION_ASSOCIATION_AMBIGUOUS");
                    associations.add(new Association(player.actorType(),relation.kind(),target.getFirst().actorType()));
                }
            }
            if(!original.contributions().isEmpty()||!original.qualifications().isEmpty())throw new IllegalArgumentException("OCCURRENCE_EXPANSION_RELATION_UNSUPPORTED");
            Actor actor=original instanceof Role?new Role(player.actorType(),elements,refinements,List.of(),List.of(),resources,associations):new Agent(player.actorType(),elements,refinements,List.of(),List.of(),resources,associations);
            actors.put(player.actorType(),actor);cast.add(new ScenarioInstance(player.instance(),player.actorType()));
        }
        var expanded=new GoalModel(executionName);actors.values().forEach(expanded::addActor);
        for(var dependency:source.getDependencies()) {
            var dependers=bySource.get(dependency.depender());
            boolean same=policies.get(dependency.dependum().id())==Quantifier.SAME_PLAYER;
            if(!same&&dependers.size()!=1)throw new IllegalArgumentException("OCCURRENCE_EXPANSION_DEPENDER_AMBIGUOUS");
            for(var depender:dependers) {
            var recipients=bySource.get(dependency.dependee());
            if(same) {
                recipients=recipients.stream().filter(p->p.player().equals(depender.player())).toList();
                if(recipients.size()!=1)throw new IllegalArgumentException("OCCURRENCE_SAME_PLAYER_DEPENDENCY_NOT_UNIQUE:"+dependency.dependum().id());
            }
            for(var recipient:recipients) {
                String dependum=same?dependency.dependum().id()+"_"+suffix(depender):recipients.size()==1?dependency.dependum().id():dependency.dependum().id()+"_"+suffix(recipient);
                expanded.addDependency(new Dependency(depender.actorType(),recipients.size()==1?id(depender,dependency.dependerElmt()):receiver(dependency,recipient),copy(dependency.dependum(),dependum),recipient.actorType(),id(recipient,dependency.dependeeElmt())));
                traces.put(dependum,new Trace(dependum,dependency.dependum().id(),depender.sourceActor(),recipient.player(),recipient.role(),recipients.size()==1?"SOURCE_DEPENDENCY":policies.get(dependency.dependum().id())+"_DEPENDENCY"));
            }
            }
        }
        var owned=new OwnedGoalModel(expanded,profile);assertUnchanged();
        String hash=SemanticJson.hash(List.of(VERSION,canonicalHash,policies,owned.hash(),SemanticJson.tree(cast),SemanticJson.tree(traces)));
        return new Expansion(owned,cast,traces,hash);
    }
    private static String receiver(Dependency dependency,Player player){return dependency.dependerElmt()+"_required_"+suffix(player);}
    private static String suffix(Player player){return SemanticJson.hash(List.of(player.actorType(),player.role(),player.roleInterval())).substring(0,24);}
    private static String id(Player player,String source){return player.actorType().equals(player.sourceActor())?source:source+"_"+suffix(player);}
    private static IntentionalElement copy(IntentionalElement element,String id) {
        if(element instanceof Goal goal)return new Goal(id,goal.goalType(),goal.conditions().isEmpty()?null:new IStarOclConstraint(goal.conditions().getFirst().oclBody()));
        if(element instanceof Task task)return new Task(id,task.preconditions().isEmpty()?null:new IStarOclConstraint(task.preconditions().getFirst().oclBody()),task.postconditions().isEmpty()?null:new IStarOclConstraint(task.postconditions().getFirst().oclBody()));
        if(element instanceof Resource)return new Resource(id);
        throw new IllegalArgumentException("OCCURRENCE_EXPANSION_ELEMENT_UNSUPPORTED:"+element.getClass().getName());
    }
}
