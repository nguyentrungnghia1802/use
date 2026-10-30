package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MObject;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;

/** Compiled XML cut plan. The existing native writer owns the savepoint/transaction. */
public final class XmlRuntimeBatch {
    private record BoundRule(Node rule,Node input,Node table) { }
    private record Retirement(BoundRule rule,boolean qualified,Set<String> present,Set<String> qualifiedScopes) { }
    private final RuntimeFactSources sources;
    private final RuntimeTables tables;
    private final M0RuleExecutor writer;
    private final ObserverProjection observers;
    private final InvariantActivation invariantActivation;
    private final OccurrenceProjection occurrences;
    private final TaskDemandProjection taskDemands;
    private final RoleBeliefProjection beliefViews;
    private final List<BoundRule> rules;
    private final boolean currentInventory;
    public XmlRuntimeBatch(MappingSpecification specification,MModel model,String programHash) {
        this(specification,model,programHash,null,List.of());
    }
    public XmlRuntimeBatch(MappingSpecification specification,MModel model,String programHash,ModelSnapshot source,List<ObservationCapabilityRequest> requests) {
        this(specification,model,programHash,source,requests,null);
    }
    public XmlRuntimeBatch(MappingSpecification specification,MModel model,String programHash,ModelSnapshot source,List<ObservationCapabilityRequest> requests,org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex bindings) {
        this(specification,model,programHash,source,requests,bindings,null);
    }
    public XmlRuntimeBatch(MappingSpecification specification,MModel model,String programHash,ModelSnapshot source,List<ObservationCapabilityRequest> requests,org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex bindings,org.vnu.sme.goal.dsl.istar.mm.GoalModel goals) {
        sources=new RuntimeFactSources(specification,source,requests);tables=new RuntimeTables(specification,bindings);
        currentInventory=specification.nodes().stream().anyMatch(n->MoisePopulationClosure.POLICY.equals(n.optional("population")));
        observers=new ObserverProjection(specification,model,programHash);
        invariantActivation=new InvariantActivation(specification,model,programHash);
        occurrences=new OccurrenceProjection(specification,model,programHash,goals);
        taskDemands=new TaskDemandProjection(specification,programHash,goals);
        beliefViews=new RoleBeliefProjection(specification,model,programHash);
        var runtime=specification.root().child("runtime");var result=new ArrayList<BoundRule>();
        if(runtime.children("rule").isEmpty()) {
            if(!runtime.children().isEmpty())throw runtime.location().error("XML_RUNTIME_RULE_REQUIRED");
            writer=null;rules=List.of();return;
        }
        writer=new M0RuleExecutor(specification,model,programHash);
        var tableIndex=new LinkedHashMap<String,Node>();
        for(Node section:runtime.children("tables"))for(Node table:section.children("table")) {
            sources.definition(table.attr("dataset"));tableIndex.put(table.attr("id"),table);
        }
        for(Node rule:runtime.children("rule")) {
            Node input=specification.requireId(rule.attr("inputRef"));
            if(!input.name().equals("input"))throw rule.location().error("XML_RUNTIME_INPUT_REQUIRED");
            Node table=tableIndex.get(input.optional("tableRef"));
            if(table==null)throw input.location().error("XML_RUNTIME_TABLE_REQUIRED");
            var columns=RuntimeTables.outputColumns(table);
            var fields=input.children("field").stream().map(n->n.attr("name")).collect(java.util.stream.Collectors.toSet());
            if(!columns.equals(fields))throw input.location().error("XML_RUNTIME_INPUT_COLUMN_MISMATCH");
            var identities=table.children("identity");String mode=identities.isEmpty()?"PHYSICAL":identities.getFirst().attr("mode");
            if(!mode.equals(input.attr("identityKind")))throw input.location().error("XML_RUNTIME_IDENTITY_MODE_MISMATCH");
            Node dataset=sources.definition(table.attr("dataset"));
            String category=sources.authority(table.attr("dataset"))+":"+sources.kind(table.attr("dataset")).name();
            for(Node write:rule.children())if(Set.of("upsertInstance","assignSnapshot","removeInstance").contains(write.name())) {
                if(write.name().equals("removeInstance")!=dataset.name().equals("removals"))throw write.location().error("XML_REMOVAL_REQUIRES_SOURCE_REMOVAL_DATASET");
                if(write.name().equals("removeInstance")&&!write.attr("authorityProof").equals("removed-identity"))throw write.location().error("XML_REMOVAL_RECEIPT_PROOF_REQUIRED");
                var target=model.getClass(write.attr("targetType"));
                if(target==null)throw write.location().error("XML_RUNTIME_TARGET_UNRESOLVED");
                boolean entity=target.isSubClassifierOf(model.getClass("DomainEntity"));
                boolean role=target.isSubClassifierOf(model.getClass("RoleOccurrence"));
                boolean context=target.isSubClassifierOf(model.getClass("OrgContext"));
                if(entity&&!category.equals("cartago:ARTIFACT")
                    ||role&&!(category.equals("moise:GROUP_BOARD")&&mode.equals("LOGICAL_ROLE"))
                    ||context&&!Set.of("cartago:WORKSPACE","moise:GROUP_BOARD","moise:SCHEME_BOARD").contains(category)
                        &&!(category.equals("jason:AGENT")&&write.name().equals("upsertInstance")&&mode.equals("PHYSICAL")
                            &&write.optional("playerFrom").equals(write.attr("keyFields"))&&!write.optional("playerFrom").isBlank())
                    ||!entity&&!role&&!context)throw write.location().error("XML_RUNTIME_SOURCE_TARGET_CATEGORY_MISMATCH");
                if(!input.optional("reconcileMissing").isEmpty()&&(!role||!write.name().equals("upsertInstance")))throw input.location().error("XML_ONLY_LOGICAL_ROLE_POPULATION_RECONCILIATION_SUPPORTED");
                if(!input.optional("reconcileScopeField").isEmpty()) {
                    String scope=input.attr("reconcileScopeField");
                    var origin=table.children("column").stream().filter(c->c.attr("name").equals(scope)&&c.attr("path").equals("identity")&&c.attr("required").equals("true")).toList();
                    if(!role||!dataset.name().equals("facts")||!category.equals("moise:GROUP_BOARD")||!scope.equals(write.optional("contextFrom"))||origin.size()!=1
                            ||!List.of(write.attr("keyFields").split("\\s+")).contains(scope)
                            ||table.children("expand").stream().noneMatch(e->e.attr("path").equals("values.players")&&e.optional("valueType").isEmpty()))throw input.location().error("XML_ROLE_SCOPE_REQUIRES_EXACT_PUBLIC_GROUP_PLAYERS");
                }
            }
            result.add(new BoundRule(rule,input,table));
        }
        rules=List.copyOf(result);
    }
    public List<M0RuleExecutor.Outcome> apply(Port port,String run,long generation,RuntimeSnapshot cut)throws UseApiException {
        return apply(port,run,generation,cut,null);
    }
    public List<M0RuleExecutor.Outcome> apply(Port port,String run,long generation,RuntimeSnapshot cut,RuntimeEvent event)throws UseApiException {
        if(writer==null)throw new IllegalArgumentException("XML_RUNTIME_PLAN_REQUIRED");
        // Query/identity compilation finishes before any native write. No second state/cache is retained.
        var capture=sources.captureWithClocks(cut,run,generation,event);var captured=capture.datasets();var selected=tables.execute(captured,capture.clocks());
        for(BoundRule rule:rules) {
            var rows=selected.get(rule.table().attr("id")).rows();
            if(rows.stream().map(RuntimeTables.Row::identity).distinct().count()!=rows.size())throw rule.table().location().error("XML_M0_DUPLICATE_TABLE_IDENTITY");
            String scope=rule.input().optional("reconcileScopeField");
            if(!scope.isEmpty())for(var row:rows)if(row.values().containsKey(scope)&&!Objects.equals(row.values().get(scope),row.proofs().get("physical-identity")))throw rule.input().location().error("XML_ROLE_SCOPE_SOURCE_IDENTITY_MISMATCH");
        }
        String epoch=SemanticJson.hash(Map.of("run",run,"generation",generation,"revision",cut.modelRevision()));
        var invalidated=new LinkedHashMap<MObject,String>();
        for(MObject object:List.copyOf(port.objects()))if(writer.owns(port,object)) {
            invalidated.put(object,port.metadata(object,"xml:m0:key"));writer.invalidate(port,object,"CURRENT_CUT_NOT_YET_QUALIFIED");
        }
        var outcomes=new ArrayList<M0RuleExecutor.Outcome>();var retirements=new ArrayList<Retirement>();
        var roleScopes=new IdentityHashMap<MObject,Boolean>();
        // A retained player object is not evidence of its presence in this cut.
        var currentPlayers=Collections.newSetFromMap(new IdentityHashMap<MObject,Boolean>());
        if(currentInventory)for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.AGENT
                &&fact.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY&&fact.completeness()==Completeness.COMPLETE) {
            var player=port.find(fact.id().canonical());if(player!=null)currentPlayers.add(player);
        }
        for(BoundRule rule:rules) {
            var table=selected.get(rule.table().attr("id"));
            var writes=rule.rule().children("upsertInstance");
            Node roleWrite=writes.size()==1&&writes.getFirst().optional("membership").equals("roles")?writes.getFirst():null;
            if(currentInventory&&roleWrite!=null)for(var object:port.objects())if(writer.owns(port,object)&&object.cls().name().equals(roleWrite.attr("contextType")))
                roleScopes.merge(object,table.populationQuality()==Completeness.COMPLETE,(a,b)->a&&b);
            boolean qualified=table.populationQuality()==Completeness.COMPLETE;var present=new HashSet<String>();
            var scoped=new HashSet<String>();String scopeField=rule.input().optional("reconcileScopeField");
            if(!scopeField.isEmpty())for(var row:captured.get(rule.table().attr("dataset")).rows())
                if(row.quality()==Completeness.COMPLETE&&row.identity().equals(row.proofs().get("physical-identity"))
                        &&row.values().get("values") instanceof Map<?,?> data&&data.get("players") instanceof List<?>)scoped.add(row.identity());
            if(table.populationQuality()!=Completeness.COMPLETE)outcomes.add(new M0RuleExecutor.Outcome(rule.rule().attr("id"),"INCOMPLETE","",null,"POPULATION_"+table.populationQuality()));
            for(var row:table.rows()) {
                var quality=row.quality();
                if(currentInventory&&roleWrite!=null&&!currentPlayers.contains(port.find(Objects.toString(row.values().get(roleWrite.attr("playerFrom")),""))))quality=Completeness.PARTIAL;
                var input=new M0RuleExecutor.Input(rule.input().attr("id"),epoch,row.identity(),cut.snapshotId(),quality,row.values(),row.proofs(),row.aliases());
                var outcome=writer.execute(port,rule.rule().attr("id"),input);outcomes.add(outcome);
                if(currentInventory&&roleWrite!=null&&!outcome.status().equals("MAPPED")) {
                    Object identity=row.values().get(roleWrite.attr("contextFrom"));MObject context=identity instanceof String id?port.find(id):null;
                    if(context!=null)roleScopes.put(context,false);
                }
                if(outcome.status().equals("INCOMPLETE"))qualified=false;
                if(row.quality()!=Completeness.COMPLETE||outcome.status().equals("INCOMPLETE"))scoped.remove(row.proofs().get("physical-identity"));
                if(outcome.status().equals("MAPPED"))present.add(outcome.nativeKey());
                if(outcome.object()!=null&&!rule.rule().children("removeInstance").isEmpty())continue;
                if(outcome.object()!=null) {
                    port.metadata(outcome.object(),"xml:m0:population",table.populationQuality().name());
                    String inventory=row.proofs().getOrDefault(MoisePopulationClosure.PROOF,"");
                    String authority=!inventory.isBlank()?MoisePopulationClosure.POLICY:
                            currentInventory&&sources.definition(rule.table().attr("dataset")).name().equals("facts")
                            &&sources.authority(rule.table().attr("dataset")).equals("cartago")
                            &&sources.kind(rule.table().attr("dataset"))==RuntimeFactKind.WORKSPACE?"CARTAGO_WORKSPACE_INVENTORY_V1":"";
                    port.metadata(outcome.object(),"xml:m0:population-authority",table.populationQuality()==Completeness.COMPLETE?authority:"");
                    port.metadata(outcome.object(),"xml:m0:population-proof",table.populationQuality()==Completeness.COMPLETE?row.proofs().getOrDefault("source-cut",""):"");
                    port.metadata(outcome.object(),"xml:m0:population-cut",cut.snapshotId());
                    port.metadata(outcome.object(),"xml:m0:table",rule.table().attr("id"));
                    port.metadata(outcome.object(),"xml:m0:input",rule.input().attr("id"));
                    if(!scopeField.isEmpty()&&outcome.status().equals("MAPPED"))port.metadata(outcome.object(),"xml:m0:reconcile-scope",(String)row.values().get(scopeField));
                    if((outcome.object().cls().isSubClassifierOf(port.readState().system().model().getClass("RoleOccurrence"))
                            ||!port.metadata(outcome.object(),"xml:m0:representedPlayer").isBlank())
                            &&Set.of("","UNAVAILABLE").contains(port.metadata(outcome.object(),"xml:m0:observer-floor"))) {
                        var mark=cut.endWatermarks().get(VerificationObservation.SOURCE_ID);
                        boolean known=mark!=null&&mark.equals(cut.startWatermarks().get(VerificationObservation.SOURCE_ID))&&cut.sourceCompleteness().getOrDefault(VerificationObservation.SOURCE_ID,Completeness.UNAVAILABLE)!=Completeness.UNAVAILABLE;
                        port.metadata(outcome.object(),"xml:m0:observer-floor",known?Long.toString(mark.sequence()):"UNAVAILABLE");
                    }
                }
            }
            if(!rule.input().optional("reconcileMissing").isEmpty())retirements.add(new Retirement(rule,qualified,Set.copyOf(present),Set.copyOf(scoped)));
        }
        // This instruction concerns observed role membership intervals only. Physical disposal still
        // requires an exact source removal receipt; a missing Artifact is never inferred disposed.
        for(Retirement retirement:retirements) {
            var rule=retirement.rule();String type=rule.rule().child("upsertInstance").attr("targetType");
            for(MObject old:List.copyOf(port.objects()))if(writer.owns(port,old)&&old.cls().name().equals(type)
                    &&epoch.equals(port.metadata(old,"xml:m0:epoch"))&&rule.input().attr("id").equals(port.metadata(old,"xml:m0:input"))
                    &&rule.table().attr("id").equals(port.metadata(old,"xml:m0:table"))&&!retirement.present().contains(port.metadata(old,"xml:m0:key"))
                    &&(rule.input().optional("reconcileScopeField").isEmpty()?retirement.qualified():retirement.qualifiedScopes().contains(port.metadata(old,"xml:m0:reconcile-scope")))) {
                String key=port.metadata(old,"xml:m0:key");port.deleteRebindableRole(key);
                invalidated.remove(old);
                outcomes.add(new M0RuleExecutor.Outcome(rule.rule().attr("id"),"MAPPED",key,old,rule.input().optional("reconcileScopeField").isEmpty()?"COMPLETE_OBSERVED_ROLE_POPULATION_RETIREMENT":"COMPLETE_OBSERVED_ROLE_SCOPE_RETIREMENT"));
            }
        }
        for(var entry:invalidated.entrySet())if("INCOMPLETE".equals(port.metadata(entry.getKey(),"xml:m0:quality"))&&outcomes.stream().noneMatch(o->o.object()==entry.getKey()&&Set.of("MAPPED","INCOMPLETE").contains(o.status())))
            outcomes.add(new M0RuleExecutor.Outcome("CURRENT-CUT-INVALIDATION","INCOMPLETE",entry.getValue(),entry.getKey(),"CURRENT_SOURCE_ROW_UNAVAILABLE"));
        if(currentInventory)for(var object:port.objects())if(writer.owns(port,object)&&object.cls().isSubClassifierOf(port.readState().system().model().getClass("OrgContext"))) {
            port.metadata(object,"xml:m0:roles-quality",roleScopes.getOrDefault(object,true)?"COMPLETE":"INCOMPLETE");
            port.metadata(object,"xml:m0:roles-cut",cut.snapshotId());
        }
        invariantActivation.apply(port,epoch,cut.snapshotId(),selected);
        outcomes.addAll(beliefViews.apply(port,epoch,cut.snapshotId(),captured));
        outcomes.addAll(observers.apply(port,epoch,cut.snapshotId(),selected,run,generation));
        outcomes.addAll(occurrences.apply(port,epoch,cut.snapshotId(),selected));
        outcomes.addAll(taskDemands.apply(port,epoch,cut.snapshotId(),selected,occurrences.resolve(port,epoch)));
        return List.copyOf(outcomes);
    }
    public boolean owns(Port port,MObject object){return writer!=null&&writer.owns(port,object);}
    public List<OccurrenceProjection.Scope> occurrences(Port port,String run,long generation,String revision){return occurrences.resolve(port,OccurrenceProjection.epoch(run,generation,revision));}
    public List<TaskDemandProjection.Demand> taskDemands(Port port,String run,long generation,String revision){return taskDemands.resolve(port,OccurrenceProjection.epoch(run,generation,revision));}
}
