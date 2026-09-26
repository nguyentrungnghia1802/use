package org.tzi.use.plugins.jacamo.bridge;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.Evidence;
import org.jacamo.bridge.contract.ModelFact;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.RelationCardinality;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.plugins.jacamo.mapping.ActiveBaseline;
import org.tzi.use.plugins.jacamo.project.ProjectRoot;
import org.tzi.use.plugins.jacamo.project.SourceFile;
import org.tzi.use.plugins.jacamo.project.SourceKind;
import org.tzi.use.plugins.jacamo.project.SourceSpan;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;
import org.tzi.use.plugins.jacamo.semantic.Dimension;
import org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.ProjectDeclaration;
import org.tzi.use.plugins.jacamo.semantic.SemanticElement;
import org.tzi.use.plugins.jacamo.semantic.SemanticId;
import org.tzi.use.plugins.jacamo.semantic.SemanticKindRegistry;
import org.tzi.use.plugins.jacamo.semantic.SemanticReference;
import org.tzi.use.plugins.jacamo.semantic.SourceProvenance;

/** Exact-ID Bridge-to-native adapter outside every frozen mapping artifact. */
public final class NativeSemanticAdapter {
    public record Result(JaCaMoSemanticModel model, String modelRevision,
                         List<RelationCardinality> groupRoleCardinalities,
                         List<RelationCardinality> parentSubGroupCardinalities,
                         List<org.jacamo.bridge.contract.UnresolvedFact> unresolvedFacts,
                         Map<String,SemanticId> bridgeIdentityMap) { }
    private record Draft(ModelFact fact, SemanticId id, MetamodelKind kind, String name,
                         Map<String,AttributeValue> attributes, Map<String,AttributeValue> sourceFacts,
                         List<SourceProvenance> provenance) { }

    public Result adapt(ModelSnapshot snapshot, Path projectRoot, String projectId) {
        var root=new ProjectRoot(projectRoot,projectId); var registry=MetamodelKind.registry();
        var sourceByPath=new TreeMap<Path,SourceFile>(Comparator.comparing(Path::toString));
        List<ModelFact> facts=allFacts(snapshot); var drafts=new LinkedHashMap<String,Draft>(); var diagnostics=new ArrayList<Diagnostic>();
        for(ModelFact fact:facts){
            MetamodelKind kind=kind(fact.factKind(),registry); if(kind==null){diagnostics.add(diag("BRIDGE_FACT_EVIDENCE_ONLY",fact.id().canonical(),fact.factKind(),"No faithful V2 class target; fact remains in Bridge evidence"));continue;}
            List<SourceProvenance> provenance=provenance(root,fact.evidence(),sourceByPath,fact.id().canonical());
            if(provenance.isEmpty()){diagnostics.add(diag("BRIDGE_PROVENANCE_REQUIRED",fact.id().canonical(),fact.factKind(),"Official adapter emitted no source evidence"));continue;}
            SemanticId id=semanticId(projectId,kind,fact.id()); Map<String,AttributeValue> attrs=attributes(fact,kind,registry);
            Map<String,AttributeValue> sourceFacts=sourceFacts(fact);
            Draft draft=new Draft(fact,id,kind,name(fact),attrs,sourceFacts,provenance);
            if(drafts.putIfAbsent(fact.id().canonical(),draft)!=null)throw new BridgeProtocolException("BRIDGE_SEMANTIC_ID_DUPLICATE:"+fact.id().canonical());
        }
        Evidence anchor=facts.stream().flatMap(f->f.evidence().stream()).findFirst().orElseThrow(()->new BridgeProtocolException("BRIDGE_MODEL_EVIDENCE_REQUIRED"));
        var anchorProv=provenance(root,List.of(anchor),sourceByPath,"project");
        BridgeEntityId environmentBridge=new BridgeEntityId("bridge","environment","environment",projectId,projectId,"model");
        BridgeEntityId organisationBridge=new BridgeEntityId("bridge","organisation","organization",projectId,projectId,"model");
        addSynthetic(drafts,environmentBridge,registry.require("Environment"),projectId,projectId,anchorProv,snapshot.modelRevision());
        addSynthetic(drafts,organisationBridge,registry.require("Organization"),projectId,projectId,anchorProv,snapshot.modelRevision());
        List<SemanticElement> elements=new ArrayList<>();
        for(Draft draft:drafts.values())elements.add(new SemanticElement(draft.id(),draft.kind(),draft.name(),draft.provenance(),draft.attributes(),references(draft,drafts,facts,snapshot.groupRoleCardinalities(),environmentBridge,organisationBridge,diagnostics),draft.sourceFacts()));
        var declaration=new ProjectDeclaration(projectId,anchorProv,Map.of("bridgeModelRevision",new AttributeValue.Text(snapshot.modelRevision()),"bridgeAuthority",new AttributeValue.Text(snapshot.projectionProvenance().getOrDefault("authority","unknown"))));
        var model=new JaCaMoSemanticModel(root,declaration,registry,elements,new ArrayList<>(sourceByPath.values()),diagnostics);
        var identities=new TreeMap<String,SemanticId>();drafts.forEach((canonical,draft)->identities.put(canonical,draft.id()));
        return new Result(model,snapshot.modelRevision(),snapshot.groupRoleCardinalities(),snapshot.parentSubGroupCardinalities(),snapshot.unresolvedFacts(),Map.copyOf(identities));
    }

    private void addSynthetic(Map<String,Draft> drafts,BridgeEntityId bridge,MetamodelKind kind,String name,String project,List<SourceProvenance> provenance,String revision){
        var source=Map.<String,AttributeValue>of("bridgeCanonicalId",new AttributeValue.Text(bridge.canonical()),"bridgeModelRevision",new AttributeValue.Text(revision),"bridgeCompleteness",new AttributeValue.Text(CapabilityStatus.COMPLETE.name()));
        drafts.put(bridge.canonical(),new Draft(new ModelFact(kind==MetamodelKind.Environment?"environment":"organization",bridge,Map.of(),Map.of(),CapabilityStatus.COMPLETE,List.of()),semanticId(project,kind,bridge),kind,name,Map.of(),source,provenance));
    }
    private List<SemanticReference> references(Draft draft,Map<String,Draft> index,List<ModelFact> allFacts,List<RelationCardinality> roleCards,BridgeEntityId environment,BridgeEntityId organisation,List<Diagnostic> diagnostics){
        var refs=new ArrayList<SemanticReference>(); ModelFact fact=draft.fact();
        switch(fact.factKind()){
            case "environment" -> addOwners(refs,"workspaces",index.values().stream().filter(d->d.kind()==MetamodelKind.Workspace).toList());
            case "organization" -> {addOwners(refs,"groups",index.values().stream().filter(d->d.kind()==MetamodelKind.Group&&d.fact().references().get("parent")==null).toList());addOwners(refs,"schemes",index.values().stream().filter(d->d.kind()==MetamodelKind.Scheme).toList());addOwners(refs,"norms",index.values().stream().filter(d->d.kind()==MetamodelKind.Norm).toList());}
            case "workspace-declaration" -> {addReverse(refs,"artifacts",draft,index,"workspace",MetamodelKind.Artifact);addCrossReverse(refs,"agents",draft,index,allFacts,"workspace","agent");}
            case "agent-declaration" -> {addReverse(refs,"plans",draft,index,"agent",MetamodelKind.Plan);addReverse(refs,"beliefs",draft,index,"agent",MetamodelKind.Belief);addReverse(refs,"goals",draft,index,"agent",MetamodelKind.AGoal);addCross(refs,"roles",draft,index,allFacts,"agent","role");addCross(refs,"artifacts",draft,index,allFacts,"agent","artifact");addCross(refs,"workspaces",draft,index,allFacts,"agent","workspace");}
            case "artifact-declaration" -> addCrossReverse(refs,"agents",draft,index,allFacts,"artifact","agent");
            case "role" -> addCrossReverse(refs,"agents",draft,index,allFacts,"role","agent");
            case "group" -> {addReverse(refs,"subGroups",draft,index,"parent",MetamodelKind.Group);roleCards.stream().filter(c->c.context().canonical().equals(draft.fact().id().canonical())).map(c->index.get(c.member().canonical())).filter(java.util.Objects::nonNull).sorted(Comparator.comparing(d->d.id().value())).forEach(d->refs.add(new SemanticReference("roles",d.fact().id().canonical(),d.id())));}
            default -> { }
        }
        fact.references().forEach((feature,targets)->{
            String mapped=feature(feature,draft.kind()); if(mapped==null)return;
            for(BridgeEntityId target:targets){Draft found=index.get(target.canonical());if(found==null){refs.add(new SemanticReference(mapped,target.canonical(),null));diagnostics.add(diag("BRIDGE_REFERENCE_UNRESOLVED",draft.id().value(),target.canonical(),"Exact Bridge target is absent"));}else refs.add(new SemanticReference(mapped,target.canonical(),found.id()));}
        });
        return List.copyOf(refs);
    }
    private void addOwners(List<SemanticReference> refs,String feature,List<Draft> owned){owned.stream().sorted(Comparator.comparing(d->d.id().value())).forEach(d->refs.add(new SemanticReference(feature,d.fact().id().canonical(),d.id())));}
    private void addReverse(List<SemanticReference> refs,String feature,Draft owner,Map<String,Draft> index,String sourceFeature,MetamodelKind targetKind){index.values().stream().filter(d->d.kind().equals(targetKind)).filter(d->d.fact().references().getOrDefault(sourceFeature,List.of()).stream().anyMatch(id->id.canonical().equals(owner.fact().id().canonical()))).sorted(Comparator.comparing(d->d.id().value())).forEach(d->refs.add(new SemanticReference(feature,d.fact().id().canonical(),d.id())));}
    private void addCross(List<SemanticReference> refs,String feature,Draft owner,Map<String,Draft> index,List<ModelFact> facts,String ownerEnd,String targetEnd){facts.stream().filter(d->d.factKind().equals("focus")||d.factKind().equals("player-role")).filter(d->d.references().getOrDefault(ownerEnd,List.of()).stream().anyMatch(id->id.canonical().equals(owner.fact().id().canonical()))).flatMap(d->d.references().getOrDefault(targetEnd,List.of()).stream()).map(id->index.get(id.canonical())).filter(java.util.Objects::nonNull).distinct().sorted(Comparator.comparing(d->d.id().value())).forEach(d->refs.add(new SemanticReference(feature,d.fact().id().canonical(),d.id())));}
    private void addCrossReverse(List<SemanticReference> refs,String feature,Draft owner,Map<String,Draft> index,List<ModelFact> facts,String ownerEnd,String targetEnd){addCross(refs,feature,owner,index,facts,ownerEnd,targetEnd);}
    private String feature(String feature,MetamodelKind owner){
        if(feature.equals("parent")||feature.equals("scheme")||feature.equals("agent")||feature.equals("artifact")||feature.equals("workspace")||feature.equals("group")||feature.equals("targetGoal"))return null;
        if(owner==MetamodelKind.Role&&feature.equals("superRoles"))return "superRoles";
        if(owner==MetamodelKind.Scheme&&(feature.equals("rootGoal")||feature.equals("missions")))return feature;
        if(owner==MetamodelKind.Mission&&feature.equals("goals"))return "goals";
        if(owner==MetamodelKind.OGoal&&feature.equals("plan"))return "plan";
        if(owner==MetamodelKind.OPlan&&feature.equals("subGoals"))return "subGoals";
        if(owner==MetamodelKind.Norm&&(feature.equals("role")||feature.equals("mission")))return feature;
        if(owner==MetamodelKind.Plan&&(feature.equals("triggeringEvent")||feature.equals("actions")))return feature;
        return null;
    }
    private Map<String,AttributeValue> attributes(ModelFact fact,MetamodelKind kind,SemanticKindRegistry registry){var out=new LinkedHashMap<String,AttributeValue>();String local=fact.id().localId();
        if(kind==MetamodelKind.Agent){out.put("name",new AttributeValue.Text(local));putText(out,"source",fact,"source");}
        else if(kind==MetamodelKind.Plan){putText(out,"label",fact,"label");putText(out,"context",fact,"context");}
        else if(kind==MetamodelKind.Event){putText(out,"operator",fact,"operator");putText(out,"type",fact,"type");putText(out,"literal",fact,"literal");}
        else if(kind==MetamodelKind.Action){putText(out,"name",fact,"name");putIntAs(out,"arity",fact,"arity");putEnum(out,"kind",fact,"kind","ActionKind",registry);}
        else if(kind==MetamodelKind.Belief)putTextAs(out,"literal",fact,"ast");
        else if(kind==MetamodelKind.AGoal){putTextAs(out,"literal",fact,"ast");putEnum(out,"type",fact,"type","AGoalType",registry);}
        else if(kind==MetamodelKind.Workspace)out.put("name",new AttributeValue.Text(local));
        else if(kind==MetamodelKind.Artifact){out.put("name",new AttributeValue.Text(last(local)));putTextAs(out,"type",fact,"configuredClass");}
        else if(kind==MetamodelKind.Role){out.put("id",new AttributeValue.Text(local));putBoolAs(out,"isAbstract",fact,"abstract");}
        else if(kind==MetamodelKind.Group)out.put("id",new AttributeValue.Text(local));
        else if(kind==MetamodelKind.Scheme)out.put("id",new AttributeValue.Text(local));
        else if(kind==MetamodelKind.Mission)out.put("id",new AttributeValue.Text(local));
        else if(kind==MetamodelKind.OGoal){out.put("id",new AttributeValue.Text(local));putText(out,"description",fact,"description");putEnum(out,"type",fact,"type","GoalType",registry);putTextAs(out,"minAgentsToSatisfy",fact,"minAgents");putTextAs(out,"timeToFulfill",fact,"ttf");}
        else if(kind==MetamodelKind.OPlan)putEnum(out,"operator",fact,"operator","OPlanOperator",registry);
        else if(kind==MetamodelKind.Norm){out.put("id",new AttributeValue.Text(local));putEnum(out,"type",fact,"type","NormType",registry);putText(out,"condition",fact,"condition");putText(out,"timeConstraint",fact,"timeConstraint");}
        return Map.copyOf(out);}
    private Map<String,AttributeValue> sourceFacts(ModelFact fact){var out=new LinkedHashMap<String,AttributeValue>();out.put("bridgeCanonicalId",new AttributeValue.Text(fact.id().canonical()));out.put("bridgeCompleteness",new AttributeValue.Text(fact.completeness().name()));fact.attributes().forEach((k,v)->out.put("bridge:"+k,new AttributeValue.Text(v)));return Map.copyOf(out);}
    private MetamodelKind kind(String fact,SemanticKindRegistry registry){String name=switch(fact){case "agent-declaration"->"Agent";case "plan"->"Plan";case "event"->"Event";case "action"->"Action";case "belief"->"Belief";case "goal"->"AGoal";case "workspace-declaration"->"Workspace";case "artifact-declaration"->"Artifact";case "role"->"Role";case "group"->"Group";case "scheme"->"Scheme";case "mission"->"Mission";case "organisational-goal"->"OGoal";case "organisational-plan"->"OPlan";case "norm"->"Norm";default->null;};return name==null?null:registry.require(name);}
    private SemanticId semanticId(String project,MetamodelKind kind,BridgeEntityId id){return SemanticId.of(project,kind.dimension(),kind.name(),List.of(id.authority(),id.scope()),id.localId()+"@"+id.incarnation());}
    private String name(ModelFact fact){String label=fact.attributes().get("label");return label!=null&&!label.isBlank()?label:last(fact.id().localId());}
    private String last(String value){int slash=Math.max(value.lastIndexOf('/'),value.lastIndexOf(':'));return slash<0?value:value.substring(slash+1);}
    private List<ModelFact> allFacts(ModelSnapshot value){var list=new ArrayList<ModelFact>();list.addAll(value.sources());list.addAll(value.agentDeclarations());list.addAll(value.workspaces());list.addAll(value.configuredArtifacts());list.addAll(value.organisationFacts());list.addAll(value.crossDimensionalRelations());return list.stream().sorted(Comparator.comparing(f->f.id().canonical())).toList();}
    private List<SourceProvenance> provenance(ProjectRoot root,List<Evidence> evidence,Map<Path,SourceFile> sources,String spelling){var result=new ArrayList<SourceProvenance>();for(Evidence item:evidence){Path path=sourcePath(root,item.sourceUri());SourceFile source=new SourceFile(path,sourceKind(path),item.sourceDigest(),0);SourceFile known=sources.putIfAbsent(path,source);if(known!=null&&!known.sha256().equals(source.sha256()))throw new BridgeProtocolException("BRIDGE_SOURCE_DIGEST_CONFLICT:"+path);var span=new SourceSpan(path,1,1,1,1);result.add(new SourceProvenance(span,item.authority(),item.sourceDigest(),spelling));}return result.stream().distinct().toList();}
    private Path sourcePath(ProjectRoot root,String uri){if(uri.startsWith("project:/"))return root.path().resolve(uri.substring("project:/".length())).normalize();try{URI parsed=URI.create(uri);if(parsed.getScheme()!=null&&parsed.getScheme().equals("file"))return Path.of(parsed).toAbsolutePath().normalize();}catch(Exception ignored){ }return root.path().resolve(".bridge-evidence").resolve(Integer.toHexString(uri.hashCode())).normalize();}
    private SourceKind sourceKind(Path path){String n=path.getFileName().toString().toLowerCase();if(n.endsWith(".jcm"))return SourceKind.JCM;if(n.endsWith(".asl"))return SourceKind.ASL;if(n.endsWith(".xml"))return SourceKind.MOISE_XML;if(n.endsWith(".java"))return SourceKind.JAVA;return SourceKind.OTHER;}
    private Diagnostic diag(String code,String id,String evidence,String message){return new Diagnostic(code,Severity.WARNING,Phase.SEMANTIC_MODEL,null,id,null,message,evidence,"Keep the exact Bridge evidence explicit or provide a reviewed target projection");}
    private void putText(Map<String,AttributeValue> out,String target,ModelFact fact,String key){String value=fact.attributes().get(key);if(value!=null&&!value.equals("null")&&!value.isBlank())out.put(target,new AttributeValue.Text(value));}
    private void putTextAs(Map<String,AttributeValue> out,String target,ModelFact fact,String key){putText(out,target,fact,key);}
    private void putBoolAs(Map<String,AttributeValue> out,String target,ModelFact fact,String key){String value=fact.attributes().get(key);if(value!=null)out.put(target,new AttributeValue.Bool(Boolean.parseBoolean(value)));}
    private void putIntAs(Map<String,AttributeValue> out,String target,ModelFact fact,String key){String value=fact.attributes().get(key);if(value!=null)try{out.put(target,new AttributeValue.IntegerNumber(Long.parseLong(value)));}catch(NumberFormatException ignored){}}
    private void putEnum(Map<String,AttributeValue> out,String target,ModelFact fact,String key,String enumeration,SemanticKindRegistry registry){String value=fact.attributes().get(key);if(value==null)return;registry.enumValue(enumeration,value).or(()->registry.enumValue(enumeration,value.toLowerCase())).ifPresent(v->out.put(target,v));}
}
