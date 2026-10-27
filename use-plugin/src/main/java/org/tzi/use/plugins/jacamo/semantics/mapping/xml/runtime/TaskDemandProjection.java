package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;

/** Typed source demand and activation identity; neither required=true nor a row
 * is a Task execution/satisfaction certificate. Metadata belongs to native rollback. */
public final class TaskDemandProjection {
    public record Demand(String rule,String target,String scope,String actor,String player,String role,String roleInterval,Boolean required,String episode,String cut,Completeness quality,String diagnostic,Map<String,String> witness) {
        public Demand {witness=Map.copyOf(witness);}
    }
    private record Rule(Node node,String actor) { }
    private final String program;
    private final List<Rule> rules;
    public TaskDemandProjection(MappingSpecification specification,String program) {
        this(specification,program,null);
    }
    public TaskDemandProjection(MappingSpecification specification,String program,org.vnu.sme.goal.dsl.istar.mm.GoalModel goals) {
        this.program=Objects.requireNonNull(program);var compiled=new ArrayList<Rule>();var sections=specification.root().child("runtime").children("taskDemands");
        if(sections.size()>1)throw specification.root().location().error("TASK_DEMAND_SECTION_DUPLICATE");
        var selected=new HashSet<String>();
        if(!sections.isEmpty())for(Node node:sections.getFirst().children("demand")) {
            Node scope=specification.requireId(node.attr("scopeRef")),table=specification.requireId(node.attr("tableRef"));
            if(!scope.name().equals("scope")||!table.name().equals("table")||!selected.add(node.attr("scopeRef")+":"+node.attr("target")))throw node.location().error("TASK_DEMAND_SCOPE_TABLE_OR_DUPLICATE");
            var elements=specification.root().child("bindings").children("elementRuntime").stream().filter(e->e.attr("target").equals(node.attr("target"))).toList();
            if(elements.size()!=1||!elements.getFirst().attr("derived").equals("false"))throw node.location().error("TASK_DEMAND_TARGET_BINDING_REQUIRED");
            String actor=elements.getFirst().attr("actor");
            if(goals==null&&!specification.root().children("models").isEmpty())throw node.location().error("AUTHORED_CANONICAL_GOAL_MODEL_REQUIRED");
            boolean originalTask;
            if(goals==null) {
                var original=specification.root().child("istar").children("actor").stream().filter(a->a.attr("name").equals(actor)).toList();
                originalTask=original.size()==1&&original.getFirst().children("task").stream().anyMatch(t->t.attr("name").equals(node.attr("target")));
            }else originalTask=goals.findElement(node.attr("target")).orElse(null) instanceof org.vnu.sme.goal.dsl.istar.mm.Task&&goals.ownerOf(node.attr("target")).orElse("").equals(actor);
            if(!originalTask||scope.children("actor").stream().noneMatch(a->a.attr("actor").equals(actor)))throw node.location().error("TASK_DEMAND_ORIGINAL_TASK_OWNER_REQUIRED");
            var columns=RuntimeTables.outputColumns(table);var episode=words(node.attr("episodeFields"));
            if(!columns.containsAll(List.of(node.attr("anchorColumn"),node.attr("playerColumn"),node.attr("requiredColumn")))||episode.isEmpty()||episode.size()>16||new HashSet<>(episode).size()!=episode.size()||!columns.containsAll(episode)
                    ||!node.optional("completeColumn").isBlank()&&!columns.contains(node.attr("completeColumn")))throw node.location().error("TASK_DEMAND_COLUMNS_REQUIRED");
            if(node.optional("scopeColumn").isBlank()!=node.optional("scopeKey").isBlank()
                    ||!node.optional("scopeColumn").isBlank()&&(!columns.contains(node.attr("scopeColumn"))||!words(scope.attr("keyFields")).contains(node.attr("scopeKey"))))throw node.location().error("TASK_DEMAND_EXACT_SCOPE_KEY_REQUIRED");
            compiled.add(new Rule(node,actor));
        }
        if(compiled.size()>128)throw specification.root().location().error("TASK_DEMAND_RULE_CAPACITY");rules=List.copyOf(compiled);
    }
    public List<M0RuleExecutor.Outcome> apply(Port port,String epoch,String cut,Map<String,RuntimeTables.Result> tables,List<OccurrenceProjection.Scope> scopes) {
        if(rules.isEmpty())return List.of();var pending=new LinkedHashMap<MObject,List<Demand>>();long work=0;
        for(var object:port.objects())if(program.equals(port.metadata(object,"xml:task-demand:program"))&&epoch.equals(port.metadata(object,"xml:task-demand:epoch"))) {
            var stale=new ArrayList<Demand>();for(var prior:read(port,object))stale.add(unknown(prior.rule(),prior.target(),prior.scope(),prior.actor(),prior.player(),prior.role(),prior.roleInterval(),cut,"CURRENT_TASK_SCOPE_UNAVAILABLE",prior.witness()));pending.put(object,stale);
        }
        for(Rule rule:rules) {
            Node node=rule.node();var table=tables.get(node.attr("tableRef"));if(table==null)throw node.location().error("TASK_DEMAND_TABLE_UNAVAILABLE");
            for(var scope:scopes)if(scope.definition().equals(node.attr("scopeRef"))) {
                MObject anchor=port.find(scope.anchor());if(anchor==null)continue;var records=pending.computeIfAbsent(anchor,ignored->new ArrayList<>());
                for(var member:scope.members())if(member.actor().equals(rule.actor())) {
                    var candidates=new ArrayList<RuntimeTables.Row>();String reason=scope.complete()?"":"TASK_DEMAND_CAST_UNAVAILABLE";
                    MObject player=port.find(member.player());
                    for(var row:table.rows()) {
                        if(++work>1_000_000)throw node.location().error("TASK_DEMAND_WORK_CAPACITY");
                        String rawAnchor=text(row.values().get(node.attr("anchorColumn"))),rawPlayer=text(row.values().get(node.attr("playerColumn")));
                        if(rawAnchor.isBlank()||rawPlayer.isBlank()){reason="TASK_DEMAND_IDENTITY_UNAVAILABLE";continue;}
                        if(!node.optional("scopeColumn").isBlank()) {
                            Object expected=scope.bindings().get(node.attr("scopeKey")),actual=row.values().get(node.attr("scopeColumn"));
                            if(!(expected instanceof String key)||key.isBlank()||!(actual instanceof String value)||value.isBlank()){reason="TASK_DEMAND_SCOPE_KEY_UNAVAILABLE";continue;}
                            if(!expected.equals(actual))continue;
                        }
                        if(port.find(rawAnchor)==anchor&&player!=null&&port.find(rawPlayer)==player)candidates.add(row);
                    }
                    if(candidates.size()>1)throw node.location().error("TASK_DEMAND_AMBIGUOUS_SOURCE_TUPLE");
                    var witness=new TreeMap<String,String>();witness.put("programHash",program);witness.put("scope",scope.id());witness.put("targetTaskId",node.attr("target"));witness.put("player",member.player());witness.put("roleInterval",member.interval());witness.put("demandRule",node.attr("id"));witness.put("demandAuthority","EXPLICIT_QUALIFIED_SOURCE_ROW_NOT_ROLE_MEMBERSHIP");
                    Boolean required=null;String episode="";
                    if(candidates.isEmpty())reason="TASK_DEMAND_SOURCE_ROW_UNAVAILABLE";
                    else {
                        var row=candidates.getFirst();
                        boolean complete=node.optional("completeColumn").isBlank()?table.populationQuality()==Completeness.COMPLETE:Boolean.TRUE.equals(row.values().get(node.attr("completeColumn")));
                        if(row.quality()!=Completeness.COMPLETE||!complete||!row.proofs().containsKey("source-cut"))reason="TASK_DEMAND_SOURCE_INCOMPLETE";
                        witness.put("demandSourceHash",SemanticJson.hash(row.values()));witness.put("demandSourceProofs",SemanticJson.hash(row.proofs()));
                        Object actual=row.values().get(node.attr("requiredColumn"));
                        if(actual!=null&&!(actual instanceof Boolean))reason="ERROR:TASK_DEMAND_BOOLEAN_TYPE";else if(actual==null)reason="TASK_DEMAND_REQUIRED_VALUE_UNAVAILABLE";
                        var activation=new TreeMap<String,Object>();
                        for(String field:words(node.attr("episodeFields"))) {
                            Object value=row.values().get(field);
                            if(value==null){if(Boolean.TRUE.equals(actual))reason="TASK_DEMAND_ACTIVATION_UNAVAILABLE";continue;}
                            if(!(value instanceof String||value instanceof Boolean||value instanceof Number)||value instanceof String string&&string.isBlank())reason="ERROR:TASK_DEMAND_ACTIVATION_TYPE";
                            if(value instanceof Number number)try{new java.math.BigDecimal(number.toString()).toBigIntegerExact();}catch(ArithmeticException|NumberFormatException invalid){reason="ERROR:TASK_DEMAND_ACTIVATION_TYPE";}
                            activation.put(field,value);
                        }
                        if(reason.isBlank()) {required=(Boolean)actual;episode=SemanticJson.hash(List.of(program,epoch,scope.id(),member.instance(),member.interval(),node.attr("target"),required,activation));witness.put("demandEpisode",episode);witness.put("activationFieldsHash",SemanticJson.hash(activation));}
                    }
                    Demand demand=reason.isBlank()?new Demand(node.attr("id"),node.attr("target"),scope.id(),member.actor(),member.player(),member.role(),member.interval(),required,episode,cut,Completeness.COMPLETE,"",witness)
                            :unknown(node.attr("id"),node.attr("target"),scope.id(),member.actor(),member.player(),member.role(),member.interval(),cut,reason,witness);
                    records.removeIf(d->sameSlot(d,demand));records.add(demand);
                }
            }
        }
        if(pending.values().stream().mapToInt(List::size).sum()>4096)throw new IllegalArgumentException("TASK_DEMAND_RECORD_CAPACITY");
        // Finish all identity/uniqueness checks before writing to the native metadata.
        var encoded=new LinkedHashMap<MObject,String>();pending.forEach((object,records)->encoded.put(object,new String(CanonicalJson.encode(SemanticJson.tree(records),128*1024),StandardCharsets.UTF_8)));
        var outcomes=new ArrayList<M0RuleExecutor.Outcome>();
        encoded.forEach((object,json)->{port.metadata(object,"xml:task-demand:program",program);port.metadata(object,"xml:task-demand:epoch",epoch);port.metadata(object,"xml:task-demand:records",json);outcomes.add(new M0RuleExecutor.Outcome("TASK-DEMAND","MAPPED",port.metadata(object,"semanticId"),object,"SOURCE_DEMAND_NOT_TASK_TRUTH"));});
        return List.copyOf(outcomes);
    }
    public List<Demand> resolve(Port port,String epoch) {
        if(rules.isEmpty())return List.of();var result=new ArrayList<Demand>();
        for(var object:port.objects())if(program.equals(port.metadata(object,"xml:task-demand:program"))&&epoch.equals(port.metadata(object,"xml:task-demand:epoch")))for(var demand:read(port,object)) {
            if(rules.stream().noneMatch(r->r.node().attr("id").equals(demand.rule())&&r.node().attr("target").equals(demand.target())))throw new IllegalArgumentException("TASK_DEMAND_FROZEN_RULE_UNRESOLVED");result.add(demand);
        }
        if(result.size()>4096)throw new IllegalArgumentException("TASK_DEMAND_RECORD_CAPACITY");return result.stream().sorted(Comparator.comparing(Demand::scope).thenComparing(Demand::target).thenComparing(Demand::player).thenComparing(Demand::roleInterval)).toList();
    }
    private List<Demand> read(Port port,MObject object) {
        Object raw=CanonicalJson.decode(port.metadata(object,"xml:task-demand:records").getBytes(StandardCharsets.UTF_8),128*1024,CanonicalJson.DEFAULT_MAX_DEPTH,16384);
        if(!(raw instanceof List<?> list)||list.size()>4096)throw new IllegalArgumentException("TASK_DEMAND_RECORD_CAPACITY");var result=new ArrayList<Demand>();
        for(Object item:list) {var row=CanonicalJson.object(item);var witness=new TreeMap<String,String>();CanonicalJson.object(row.get("witness")).forEach((k,v)->witness.put(k,(String)v));result.add(new Demand((String)row.get("rule"),(String)row.get("target"),(String)row.get("scope"),(String)row.get("actor"),(String)row.get("player"),(String)row.get("role"),(String)row.get("roleInterval"),(Boolean)row.get("required"),(String)row.get("episode"),(String)row.get("cut"),Completeness.valueOf((String)row.get("quality")),(String)row.get("diagnostic"),witness));}
        return result;
    }
    private static Demand unknown(String rule,String target,String scope,String actor,String player,String role,String interval,String cut,String reason,Map<String,String> witness){return new Demand(rule,target,scope,actor,player,role,interval,null,"",cut,Completeness.PARTIAL,reason,witness);}
    private static boolean sameSlot(Demand first,Demand second){return first.rule().equals(second.rule())&&first.scope().equals(second.scope())&&first.actor().equals(second.actor())&&first.player().equals(second.player())&&first.role().equals(second.role());}
    private static List<String> words(String value){return value.isBlank()?List.of():List.of(value.split(" +"));}
    private static String text(Object value){return value instanceof String string?string:"";}
}
