package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.sys.MObject;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;

/** Declarative casts stored in the existing native metadata/savepoint. No live state,
 * independent history, actor-name resolution or engine recurrence is created here. */
public final class OccurrenceProjection {
    public record Member(String actor,String actorType,String instance,String player,String role,String context,String interval,Map<String,String> selves) {
        public Member {selves=Map.copyOf(selves);}
    }
    public record Scope(String id,String definition,String anchor,String context,String revision,String cut,List<Member> members,List<String> unavailable,Map<String,Object> bindings,Map<String,String> resources) {
        public Scope {members=List.copyOf(members);unavailable=List.copyOf(unavailable);bindings=SemanticJson.object(bindings);resources=Map.copyOf(resources);}
        public boolean complete(){return unavailable.isEmpty();}
    }
    private record ActorRule(Node node,MClass roleType) { }
    private record Definition(Node node,List<ActorRule> actors,List<Node> selves,List<Node> resources) { }
    private final String program;
    private final MModel model;
    private final List<Definition> definitions;
    private final Map<String,Node> elements;
    public OccurrenceProjection(MappingSpecification specification,MModel model,String program) {
        this.program=Objects.requireNonNull(program);this.model=Objects.requireNonNull(model);
        var bindings=specification.root().child("bindings");var elementIndex=new TreeMap<String,Node>();
        for(Node element:bindings.children("elementRuntime"))if(elementIndex.putIfAbsent(element.attr("target"),element)!=null)throw element.location().error("OCCURRENCE_ELEMENT_DUPLICATE");elements=Map.copyOf(elementIndex);
        var actorBindings=new TreeMap<String,Node>();for(Node actor:bindings.children("actorRuntime"))if(actorBindings.putIfAbsent(actor.attr("actor"),actor)!=null)throw actor.location().error("OCCURRENCE_ACTOR_DUPLICATE");
        var compiled=new ArrayList<Definition>();var sections=specification.root().child("runtime").children("occurrences");
        if(sections.size()>1)throw specification.root().location().error("OCCURRENCE_SECTION_DUPLICATE");
        if(!sections.isEmpty())for(Node scope:sections.getFirst().children("scope")) {
            var columns=columns(specification,scope.attr("tableRef"));var keys=words(scope.attr("keyFields"));
            if(keys.isEmpty()||keys.size()>16||new HashSet<>(keys).size()!=keys.size()||!columns.containsAll(keys)||!keys.contains(scope.attr("anchorColumn"))||!columns.contains(scope.attr("contextColumn")))throw scope.location().error("OCCURRENCE_SCOPE_KEY_INVALID");
            var selected=new HashSet<String>();var actors=new ArrayList<ActorRule>();
            for(Node actor:scope.children("actor")) {
                Node binding=actorBindings.get(actor.attr("actor"));var fields=columns(specification,actor.attr("tableRef"));
                if(binding==null||!selected.add(actor.attr("actor"))||!fields.containsAll(List.of(actor.attr("scopeColumn"),actor.attr("playerColumn")))||!keys.contains(actor.attr("scopeKey"))
                        ||!Set.of("ONE","MANY_NONEMPTY").contains(actor.attr("cardinality"))||!actor.optional("completeColumn").isEmpty()&&!columns.contains(actor.attr("completeColumn")))throw actor.location().error("OCCURRENCE_ACTOR_PLAN_INVALID");
                MClass role=null;
                switch(actor.attr("kind")) {
                    case "ROLE_OCCURRENCE" -> {
                        if(binding.optional("roleBindingRef").isEmpty())throw actor.location().error("OCCURRENCE_ROLE_BINDING_REQUIRED");
                        if(actor.optional("roleLookup").isEmpty()) {
                            if(!fields.contains(actor.optional("roleColumn")))throw actor.location().error("OCCURRENCE_ROLE_BINDING_REQUIRED");
                        }else if(!actor.attr("roleLookup").equals("EXACT_NATIVE_ENACTMENT")||!actor.optional("roleColumn").isEmpty())throw actor.location().error("UNSUPPORTED:OCCURRENCE_ROLE_LOOKUP_POLICY");
                        Node roleBinding=specification.requireId(binding.attr("roleBindingRef"));
                        role=model.getClass(roleBinding.attr("target"));
                        if(!roleBinding.name().equals("static")||!roleBinding.attr("kind").equals("ROLE")||role==null||!role.isSubClassifierOf(model.getClass("RoleOccurrence")))throw actor.location().error("OCCURRENCE_EXACT_ROLE_TYPE_REQUIRED");
                    }
                    case "CONTEXT_VIEWPOINT" -> {if(!actor.optional("roleColumn").isEmpty()||!actor.optional("roleLookup").isEmpty()||!actor.attr("cardinality").equals("ONE"))throw actor.location().error("OCCURRENCE_CONTEXT_VIEWPOINT_INVALID");}
                    default -> throw actor.location().error("UNSUPPORTED:OCCURRENCE_ACTOR_KIND");
                }
                actors.add(new ActorRule(actor,role));
            }
            if(!selected.equals(actorBindings.keySet()))throw scope.location().error("OCCURRENCE_COMPLETE_ACTOR_PLAN_REQUIRED");
            var leaves=elements.values().stream().filter(e->e.attr("derived").equals("false")).map(e->e.attr("target")).collect(java.util.stream.Collectors.toSet());var assigned=new HashSet<String>();
            for(Node self:scope.children("self")) {
                Node element=elements.get(self.attr("target"));
                if(element==null||!leaves.contains(self.attr("target"))||!assigned.add(self.attr("target"))||model.getClass(element.attr("self"))==null)throw self.location().error("OCCURRENCE_SELF_TARGET_INVALID");
                if(self.attr("origin").equals("COLUMN")){if(!columns.contains(self.optional("column")))throw self.location().error("OCCURRENCE_SELF_COLUMN_REQUIRED");}
                else if(!Set.of("ROLE","PLAYER","CONTEXT").contains(self.attr("origin"))||!self.optional("column").isEmpty())throw self.location().error("UNSUPPORTED:OCCURRENCE_SELF_ORIGIN");
            }
            if(!assigned.equals(leaves))throw scope.location().error("OCCURRENCE_LEAF_SELF_COVERAGE");
            var resources=new HashSet<String>();
            var originalResources=specification.root().child("istar").children("actor").stream().flatMap(a->a.children("resource").stream()).map(r->r.attr("name")).collect(java.util.stream.Collectors.toSet());
            for(Node resource:scope.children("resource")) {
                MClass type=model.getClass(resource.attr("type"));
                if(!resources.add(resource.attr("target"))||!originalResources.contains(resource.attr("target"))||!columns.contains(resource.attr("column"))||type==null||!(type.isSubClassifierOf(model.getClass("DomainEntity"))||type.isSubClassifierOf(model.getClass("OrgContext"))))throw resource.location().error("UNSUPPORTED:OCCURRENCE_NATIVE_RESOURCE_BINDING");
            }
            compiled.add(new Definition(scope,List.copyOf(actors),scope.children("self"),scope.children("resource")));
        }
        if(compiled.size()>64)throw specification.root().location().error("OCCURRENCE_DEFINITION_CAPACITY");definitions=List.copyOf(compiled);
    }
    public static String epoch(String run,long generation,String revision) {
        if(run==null||run.isBlank()||generation<0||revision==null||revision.isBlank())throw new IllegalArgumentException("OCCURRENCE_EPOCH_REQUIRED");
        return SemanticJson.hash(Map.of("run",run,"generation",generation,"revision",revision));
    }
    public List<M0RuleExecutor.Outcome> apply(Port port,String epoch,String cut,Map<String,RuntimeTables.Result> tables) {
        if(definitions.isEmpty())return List.of();var pending=new LinkedHashMap<MObject,List<Scope>>();var outcomes=new ArrayList<M0RuleExecutor.Outcome>();long work=0;
        // Ephemeral writer-local index over the authoritative state, not a role store or history.
        var enactments=new HashMap<List<String>,List<MObject>>();
        if(definitions.stream().flatMap(d->d.actors().stream()).anyMatch(a->a.node().optional("roleLookup").equals("EXACT_NATIVE_ENACTMENT")))for(var object:port.objects()) {
            if(++work>1_000_000)throw new IllegalArgumentException("OCCURRENCE_WORK_CAPACITY");
            if(object.cls().isSubClassifierOf(model.getClass("RoleOccurrence"))&&current(port,object,epoch,cut)) {
                String player=port.metadata(object,"rolePlayer"),context=port.metadata(object,"roleContext");
                if(!player.isBlank()&&!context.isBlank())enactments.computeIfAbsent(List.of(object.cls().name(),player,context),ignored->new ArrayList<>()).add(object);
            }
        }
        for(var object:port.objects())if(program.equals(port.metadata(object,"xml:occurrence:program"))&&epoch.equals(port.metadata(object,"xml:occurrence:epoch"))) {
            var stale=new ArrayList<Scope>();for(var scope:read(port,object))stale.add(new Scope(scope.id(),scope.definition(),scope.anchor(),scope.context(),scope.revision(),cut,scope.members(),List.of("CURRENT_SCOPE_ROW_UNAVAILABLE"),scope.bindings(),Map.of()));pending.put(object,stale);
        }
        var seen=new HashSet<String>();
        for(Definition definition:definitions) {
            var node=definition.node();var source=table(tables,node.attr("tableRef"));
            for(var row:source.rows()) {
                if(++work>1_000_000)throw node.location().error("OCCURRENCE_WORK_CAPACITY");
                String alias=text(row.values().get(node.attr("anchorColumn")));MObject anchor=port.find(alias);
                if(anchor==null){outcomes.add(new M0RuleExecutor.Outcome(node.attr("id"),"INCOMPLETE","",null,"OCCURRENCE_ANCHOR_UNAVAILABLE"));continue;}
                var issues=new ArrayList<String>();var members=new ArrayList<Member>();var key=new TreeMap<String,String>();
                for(String field:words(node.attr("keyFields"))){String value=text(row.values().get(field));if(value.isBlank())issues.add("SCOPE_KEY_UNAVAILABLE:"+field);key.put(field,value);}
                String id="xml-occurrence:"+SemanticJson.hash(List.of(program,epoch,node.attr("id"),key,primary(port,anchor)));
                if(!seen.add(id))throw node.location().error("OCCURRENCE_SCOPE_IDENTITY_AMBIGUOUS");
                if(row.quality()!=Completeness.COMPLETE||!current(port,anchor,epoch,cut))issues.add("CURRENT_SCOPE_ANCHOR_UNAVAILABLE");
                MObject context=port.find(text(row.values().get(node.attr("contextColumn"))));
                if(context==null||!context.cls().isSubClassifierOf(model.getClass("OrgContext"))||!current(port,context,epoch,cut))issues.add("EXACT_SCOPE_CONTEXT_UNAVAILABLE");
                for(ActorRule actor:definition.actors()) {
                    Node rule=actor.node();var candidates=table(tables,rule.attr("tableRef"));String selected=key.get(rule.attr("scopeKey"));int count=0;
                    boolean complete=rule.optional("completeColumn").isEmpty()?candidates.populationQuality()==Completeness.COMPLETE:Boolean.TRUE.equals(row.values().get(rule.attr("completeColumn")));
                    if(!complete)issues.add("ACTOR_POPULATION_INCOMPLETE:"+rule.attr("actor"));
                    for(var candidate:candidates.rows()) {
                        if(++work>1_000_000)throw node.location().error("OCCURRENCE_WORK_CAPACITY");
                        String scope=text(candidate.values().get(rule.attr("scopeColumn")));if(scope.isBlank()){issues.add("ACTOR_SCOPE_KEY_UNAVAILABLE:"+rule.attr("actor"));continue;}if(!scope.equals(selected))continue;count++;
                        if(candidate.quality()!=Completeness.COMPLETE){issues.add("ACTOR_ROW_INCOMPLETE:"+rule.attr("actor"));continue;}
                        MObject player=port.find(text(candidate.values().get(rule.attr("playerColumn")))),role=null;String interval="",actorContext=context==null?"":primary(port,context);
                        if(player==null){issues.add("EXACT_PLAYER_UNAVAILABLE:"+rule.attr("actor"));continue;}
                        if(actor.roleType()!=null) {
                            if(rule.optional("roleLookup").equals("EXACT_NATIVE_ENACTMENT")) {
                                var found=context==null?List.<MObject>of():enactments.getOrDefault(List.of(actor.roleType().name(),primary(port,player),primary(port,context)),List.of());
                                if(found.size()==1)role=found.getFirst();
                            }else role=port.find(text(candidate.values().get(rule.attr("roleColumn"))));
                            if(role==null||!role.cls().isSubClassifierOf(actor.roleType())||!player.cls().isSubClassifierOf(model.getClass("RuntimePlayer"))||!current(port,role,epoch,cut)||context==null
                                    ||!primary(port,player).equals(port.metadata(role,"rolePlayer"))||!primary(port,context).equals(port.metadata(role,"roleContext"))||!linked(port,"Plays",player,role)||!linked(port,"InContext",context,role)) {issues.add("EXACT_NATIVE_ENACTMENT_UNAVAILABLE:"+rule.attr("actor"));continue;}
                            interval=port.metadata(role,"xml:m0:lifetime");if(interval.isBlank()){issues.add("ENACTMENT_INTERVAL_UNAVAILABLE");continue;}
                        }else {
                            if(!player.cls().isSubClassifierOf(model.getClass("OrgContext"))||!current(port,player,epoch,cut)){issues.add("CONTEXT_VIEWPOINT_UNAVAILABLE");continue;}
                            actorContext=primary(port,player);
                        }
                        var selves=new TreeMap<String,String>();
                        for(Node self:definition.selves())if(elements.get(self.attr("target")).attr("actor").equals(rule.attr("actor"))) {
                            MObject value=switch(self.attr("origin")){case "ROLE"->role;case "PLAYER"->player;case "CONTEXT"->context;case "COLUMN"->port.find(text(row.values().get(self.attr("column"))));default->throw self.location().error("UNSUPPORTED:SELF_ORIGIN");};
                            MClass expected=model.getClass(elements.get(self.attr("target")).attr("self"));
                            if(value==null||!value.cls().isSubClassifierOf(expected)||!current(port,value,epoch,cut)) {
                                if(!self.optional("optional").equals("true"))issues.add("EXACT_NATIVE_SELF_UNAVAILABLE:"+self.attr("target"));
                            }else selves.put(self.attr("target"),primary(port,value));
                        }
                        String roleId=role==null?"":primary(port,role),fingerprint=SemanticJson.hash(List.of(id,rule.attr("actor"),primary(port,player),roleId,interval));
                        members.add(new Member(rule.attr("actor"),rule.attr("actor")+"_"+fingerprint.substring(0,24),"cast_"+fingerprint,primary(port,player),roleId,actorContext,interval,selves));
                    }
                    if(count==0||rule.attr("cardinality").equals("ONE")&&count!=1)issues.add("EXACT_ACTOR_CARDINALITY_UNAVAILABLE:"+rule.attr("actor"));
                }
                if(members.size()>4096)throw node.location().error("OCCURRENCE_MEMBER_CAPACITY");
                if(members.stream().map(Member::instance).distinct().count()!=members.size())throw node.location().error("OCCURRENCE_MEMBER_IDENTITY_AMBIGUOUS");
                var resources=new TreeMap<String,String>();
                for(Node resource:definition.resources()) {
                    MObject value=port.find(text(row.values().get(resource.attr("column"))));
                    if(value!=null&&value.cls().isSubClassifierOf(model.getClass(resource.attr("type")))&&current(port,value,epoch,cut))resources.put(resource.attr("target"),primary(port,value));
                }
                members.sort(Comparator.comparing(Member::instance));String revision=SemanticJson.hash(List.of(program,epoch,id,SemanticJson.tree(members),resources));
                var record=new Scope(id,node.attr("id"),primary(port,anchor),context==null?"":primary(port,context),revision,cut,members,issues.stream().distinct().toList(),row.values(),resources);
                var records=pending.computeIfAbsent(anchor,ignored->new ArrayList<>());records.removeIf(s->s.id().equals(id));records.add(record);
            }
        }
        if(seen.size()>4096)throw new IllegalArgumentException("OCCURRENCE_SCOPE_CAPACITY");
        for(var entry:pending.entrySet()) {
            if(entry.getValue().size()>64)throw new IllegalArgumentException("OCCURRENCE_ANCHOR_SCOPE_CAPACITY");
            byte[] bytes=CanonicalJson.encode(SemanticJson.tree(entry.getValue()),128*1024);
            port.metadata(entry.getKey(),"xml:occurrence:program",program);port.metadata(entry.getKey(),"xml:occurrence:epoch",epoch);port.metadata(entry.getKey(),"xml:occurrence:records",new String(bytes,StandardCharsets.UTF_8));
            outcomes.add(new M0RuleExecutor.Outcome("OCCURRENCE-CAST","MAPPED",primary(port,entry.getKey()),entry.getKey(),"QUALIFIED_CAST_METADATA_NOT_GOAL_TRUTH"));
        }
        return List.copyOf(outcomes);
    }
    public List<Scope> resolve(Port port,String epoch) {
        if(definitions.isEmpty())return List.of();
        var result=new ArrayList<Scope>();
        for(var object:port.objects())if(program.equals(port.metadata(object,"xml:occurrence:program"))&&epoch.equals(port.metadata(object,"xml:occurrence:epoch")))for(var scope:read(port,object)) {
            if(definitions.stream().noneMatch(d->d.node().attr("id").equals(scope.definition())))throw new IllegalArgumentException("OCCURRENCE_FROZEN_DEFINITION_UNRESOLVED");
            var issues=new ArrayList<>(scope.unavailable());
            if(!scope.anchor().equals(primary(port,object))||!current(port,object,epoch,scope.cut()))issues.add("CURRENT_SCOPE_ANCHOR_UNAVAILABLE");
            for(var member:scope.members()) {
                var player=port.find(member.player());var context=port.find(member.context());
                if(player==null||context==null||!current(port,context,epoch,scope.cut()))issues.add("CURRENT_CAST_BINDING_UNAVAILABLE");
                if(!member.role().isBlank()) {var role=port.find(member.role());if(role==null||!current(port,role,epoch,scope.cut())||!member.interval().equals(port.metadata(role,"xml:m0:lifetime"))||player==null||context==null||!linked(port,"Plays",player,role)||!linked(port,"InContext",context,role))issues.add("CURRENT_ENACTMENT_INTERVAL_UNAVAILABLE");}
                for(var self:member.selves().values()) {var nativeSelf=port.find(self);if(nativeSelf==null||!current(port,nativeSelf,epoch,scope.cut()))issues.add("CURRENT_NATIVE_SELF_UNAVAILABLE");}
            }
            result.add(new Scope(scope.id(),scope.definition(),scope.anchor(),scope.context(),scope.revision(),scope.cut(),scope.members(),issues.stream().distinct().toList(),scope.bindings(),qualifiedResources(port,scope,epoch)));
        }
        if(result.size()>4096)throw new IllegalArgumentException("OCCURRENCE_SCOPE_CAPACITY");return result.stream().sorted(Comparator.comparing(Scope::id)).toList();
    }
    private List<Scope> read(Port port,MObject owner) {
        Object raw=CanonicalJson.decode(port.metadata(owner,"xml:occurrence:records").getBytes(StandardCharsets.UTF_8),128*1024,CanonicalJson.DEFAULT_MAX_DEPTH,16384);
        if(!(raw instanceof List<?> list)||list.size()>64)throw new IllegalArgumentException("OCCURRENCE_RECORD_CAPACITY");var result=new ArrayList<Scope>();
        for(Object value:list) {
            var record=CanonicalJson.object(value);var members=new ArrayList<Member>();
            for(Object item:(List<?>)record.get("members")) {var m=CanonicalJson.object(item);var selves=new TreeMap<String,String>();CanonicalJson.object(m.get("selves")).forEach((k,v)->selves.put(k,(String)v));members.add(new Member((String)m.get("actor"),(String)m.get("actorType"),(String)m.get("instance"),(String)m.get("player"),(String)m.get("role"),(String)m.get("context"),(String)m.get("interval"),selves));}
            result.add(new Scope((String)record.get("id"),(String)record.get("definition"),(String)record.get("anchor"),(String)record.get("context"),(String)record.get("revision"),(String)record.get("cut"),members,((List<?>)record.get("unavailable")).stream().map(String.class::cast).toList(),CanonicalJson.object(record.get("bindings")),stringMap(record.get("resources"))));
        }
        return result;
    }
    private Map<String,String> qualifiedResources(Port port,Scope scope,String epoch) {
        var result=new TreeMap<String,String>();scope.resources().forEach((name,id)->{var object=port.find(id);if(object!=null&&current(port,object,epoch,scope.cut()))result.put(name,id);});return result;
    }
    private static Map<String,String> stringMap(Object raw){var result=new TreeMap<String,String>();CanonicalJson.object(raw).forEach((k,v)->result.put(k,(String)v));return result;}
    private boolean current(Port port,MObject object,String epoch,String cut){return program.equals(port.metadata(object,"xml:m0:program"))&&epoch.equals(port.metadata(object,"xml:m0:epoch"))&&"COMPLETE".equals(port.metadata(object,"xml:m0:quality"))&&cut.equals(port.metadata(object,"xml:m0:cut"));}
    private static boolean linked(Port port,String association,MObject first,MObject second){return port.readState()!=null&&port.readState().allLinks().stream().anyMatch(l->l.association().name().equals(association)&&l.linkedObjects().equals(List.of(first,second)));}
    private static String primary(Port port,MObject object){String id=port.metadata(object,"semanticId");if(id.isBlank())throw new IllegalArgumentException("OCCURRENCE_NATIVE_IDENTITY_REQUIRED");return id;}
    private static RuntimeTables.Result table(Map<String,RuntimeTables.Result> tables,String id){var value=tables.get(id);if(value==null)throw new IllegalArgumentException("OCCURRENCE_TABLE_UNAVAILABLE:"+id);return value;}
    private static Set<String> columns(MappingSpecification spec,String id){Node table=spec.requireId(id);if(!table.name().equals("table"))throw table.location().error("OCCURRENCE_TABLE_REQUIRED");return RuntimeTables.outputColumns(table);}
    private static List<String> words(String value){return value.isBlank()?List.of():List.of(value.split(" +"));}
    private static String text(Object value){return value instanceof String string?string:"";}
}
