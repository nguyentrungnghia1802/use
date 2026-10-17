package org.tzi.use.plugins.jacamo.semantics.roundtrip;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.mde.JacamoEcoreSourceLoader;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import static org.tzi.use.plugins.jacamo.semantics.roundtrip.SourcePatchPlan.Status.*;

/** Restricted structural lens on the supplied JaCaMo Ecore vocabulary; no claim of executable JaCaMo reconstruction. */
public final class SourceLens {
    public record Projection(SupportedEditProfile profile,Map<String,Map<String,Object>> target,TraceComplement complement) {
        public Projection{if(profile==null)throw new IllegalArgumentException("LENS_PROJECTION_PROFILE_REQUIRED");
            if(target==null)throw new IllegalArgumentException("LENS_PROJECTION_TARGET_REQUIRED");
            if(complement==null)throw new IllegalArgumentException("LENS_PROJECTION_COMPLEMENT_REQUIRED");var copy=new TreeMap<String,Map<String,Object>>();target.forEach((k,v)->{if(k==null || v==null)throw new IllegalArgumentException("LENS_PROJECTION_TARGET_ENTRY_REQUIRED");copy.put(k,SemanticJson.object(v));});target=Collections.unmodifiableMap(copy);}
    }
    private final JacamoEcoreSourceLoader loader=new JacamoEcoreSourceLoader();
    public Projection get(byte[] source,SupportedEditProfile profile) {
        if(source==null || profile==null)throw new IllegalArgumentException("LENS_GET_SOURCE_AND_PROFILE_REQUIRED");
        var model=loader.load(loader.bundledMetamodel(),source);var target=new TreeMap<String,Map<String,Object>>();var decisions=new ArrayList<TraceComplement.Decision>();var catalog=new MappingRuleCatalog();
        for(var owner:profile.owners()){var node=node(model,owner.sourceFragment());requireConcept(node,owner.kind());var fields=fields(model,node,owner.kind());var previous=target.putIfAbsent(owner.targetId(),fields);if(previous!=null && !previous.equals(fields))throw new IllegalArgumentException("LENS_GET_MULTIPLE_OWNERS_DIFFERENT_VALUES:"+owner.targetId());}
        for(var node:model.nodes())for(var feature:node.features().entrySet()) {var owners=profile.owners().stream().filter(o->node.id().endsWith(":"+o.sourceFragment())).toList();decisions.add(new TraceComplement.Decision(node.id(),feature.getKey(),catalog.require(node.concept()).id(),owners.isEmpty()?MappingRuleCatalog.Disposition.TRACE_ONLY:MappingRuleCatalog.Disposition.PARTIAL,owners.stream().map(SupportedEditProfile.Owner::targetId).toList(),"STRUCTURAL_LENS_EXPLICIT_OWNERSHIP; UNSELECTED_BEHAVIOR_RETAINED_OPAQUE",feature.getValue().value(),feature.getValue().origin()));}
        return new Projection(profile,target,new TraceComplement("1.0",MappingRuleCatalog.VERSION,model.sourceHash(),model.metamodelHash(),List.of(TraceComplement.Asset.of("source-xmi","SOURCE_XMI_BYTES",source)),decisions));
    }
    /** edit contains full/partial target fields; source compare-and-set and exact complement are mandatory. */
    public SourcePatchPlan put(byte[] current,Map<String,Map<String,Object>> edit,Projection original) {
        if(current==null || edit==null || original==null)throw new IllegalArgumentException("LENS_PUT_SOURCE_EDIT_AND_PROJECTION_REQUIRED");
        String digest=ExternalOclConstraintService.sha256(current);var k=original.complement();
        if(!digest.equals(k.sourceHash()))return fail(CONFLICT,digest,original,"SOURCE_CHANGED_SINCE_GET");
        var asset=k.assets().stream().filter(a->a.format().equals("SOURCE_XMI_BYTES")).toList();if(asset.size()!=1 || !Arrays.equals(asset.getFirst().bytes(),current))return fail(CONFLICT,digest,original,"SOURCE_COMPLEMENT_ASSET_MISMATCH");
        if(!loader.inspect(loader.bundledMetamodel()).hash().equals(k.metamodelHash()))return fail(CONFLICT,digest,original,"METAMODEL_CHANGED_SINCE_GET");
        Projection verified;
        try{verified=get(current,original.profile());}catch(IllegalArgumentException e){return fail(CONFLICT,digest,original,"SOURCE_PROFILE_INVALID:"+e.getMessage());}
        if(!verified.equals(original))return fail(CONFLICT,digest,original,"TARGET_OR_COMPLEMENT_CHANGED_SINCE_GET");
        var model=loader.load(loader.bundledMetamodel(),current);var edits=new ArrayList<JacamoEcoreSourceLoader.DefinitionEdit>();var changes=new ArrayList<SourcePatchPlan.Change>();
        for(var entry:edit.entrySet()) {
            if(!original.target().containsKey(entry.getKey()))return fail(UNSUPPORTED,digest,original,"TARGET_ELEMENT_OUTSIDE_OWNERSHIP:"+entry.getKey());
            var owners=original.profile().owners().stream().filter(o->o.targetId().equals(entry.getKey())).toList();if(owners.size()!=1)return fail(AMBIGUOUS,digest,original,"MULTIPLE_SOURCE_OWNERS:"+entry.getKey());
            var owner=owners.getFirst();var node=node(model,owner.sourceFragment());var before=original.target().get(entry.getKey());var desired=new TreeMap<String,Object>(before);desired.putAll(SemanticJson.object(entry.getValue()));
            if(!before.keySet().containsAll(desired.keySet()))return fail(UNSUPPORTED,digest,original,"BEHAVIOR_OR_TYPED_VALUE_NOT_EXPRESSED_BY_SOURCE_ECORE:"+desired.keySet());
            if(owner.kind()==SupportedEditProfile.Kind.ISTAR_OPLAN_DESCRIPTOR) {
                String oldOperator=(String)before.get("operator"),operator=(String)desired.get("operator"),outcome=(String)desired.get("outcome");
                boolean outcomeChanged=!Objects.equals(before.get("outcome"),outcome);boolean explicitOperator=entry.getValue().containsKey("operator");
                if(outcomeChanged && !explicitOperator){if(outcome.equals("OR"))operator="CHOICE";else if(outcome.equals("AND"))return fail(AMBIGUOUS,digest,original,"AND_REQUIRES_EXPLICIT_SEQUENCE_OR_PARALLEL");else return fail(UNSUPPORTED,digest,original,"REFINEMENT_OPERATOR_UNSUPPORTED");desired.put("operator",operator);}
                if(!Set.of("SEQUENCE","PARALLEL","CHOICE").contains(operator))return fail(UNSUPPORTED,digest,original,"OPLAN_OPERATOR_UNSUPPORTED");String derived=operator.equals("CHOICE")?"OR":"AND";
                if(explicitOperator && !outcomeChanged)desired.put("outcome",derived);else if(!derived.equals(outcome))return fail(CONFLICT,digest,original,"OPERATOR_OUTCOME_CONFLICT");
                if(!Objects.equals(desired.get("orderedChildren"),before.get("orderedChildren"))) {if(!(desired.get("orderedChildren") instanceof List<?> list) || list.size()!=((List<?>)before.get("orderedChildren")).size() || !new HashSet<>(list).equals(new HashSet<>((List<?>)before.get("orderedChildren"))))return fail(UNSUPPORTED,digest,original,"OPLAN_EDIT_REQUIRES_EXACT_CHILD_PERMUTATION");edits.add(new JacamoEcoreSourceLoader.DefinitionEdit(owner.sourceFragment(),"subGoals",desired.get("orderedChildren")));}
                if(!oldOperator.equals(operator))edits.add(new JacamoEcoreSourceLoader.DefinitionEdit(owner.sourceFragment(),"operator",operator.toLowerCase(Locale.ROOT)));
            } else {
                try{validateFields(desired,owner.kind());}catch(IllegalArgumentException e){return fail(CONFLICT,digest,original,e.getMessage());}
                for(var field:desired.entrySet())if(!Objects.equals(before.get(field.getKey()),field.getValue()))edits.add(new JacamoEcoreSourceLoader.DefinitionEdit(owner.sourceFragment(),sourceFeature(field.getKey(),owner.kind()),field.getValue()));
            }
            for(var field:desired.entrySet())if(!Objects.equals(before.get(field.getKey()),field.getValue()))changes.add(new SourcePatchPlan.Change(entry.getKey(),owner.sourceFragment(),field.getKey(),before.get(field.getKey()),field.getValue()));
        }
        if(edits.isEmpty())return new SourcePatchPlan(APPLIED,digest,digest,Base64.getEncoder().encodeToString(current),original.profile(),original.target(),List.of(),List.of("GET_PUT_UNCHANGED_BYTES"),"BYTE_EXACT_UNCHANGED_SOURCE_ONLY");
        try {
            var rewritten=loader.rewrite(loader.bundledMetamodel(),current,edits);var profile=new SupportedEditProfile(original.profile().version(),original.profile().owners().stream().map(o->new SupportedEditProfile.Owner(o.targetId(),rewritten.fragmentRebase().get(o.sourceFragment()),o.kind())).toList());var next=get(rewritten.bytes(),profile);var nextModel=loader.load(loader.bundledMetamodel(),rewritten.bytes());validateNames(nextModel);validateUnchanged(model,nextModel,rewritten.fragmentRebase(),edits);
            return new SourcePatchPlan(APPLIED,digest,next.complement().sourceHash(),Base64.getEncoder().encodeToString(rewritten.bytes()),profile,next.target(),changes,List.of("EMF_VALIDATED_STRUCTURAL_DEFINITION_ONLY","COMMENTS_AND_LAYOUT_NOT_PRESERVED_ON_EDIT","NO_JAVA_ASL_GENERATION_OR_RUNTIME_RESTORE"),"NORMALIZED_AST_STRUCTURE; UNCHANGED_FEATURE_VALUES_RETAINED; ORIGINAL_BYTES_IN_COMPLEMENT");
        }catch(IllegalArgumentException e){return fail(CONFLICT,digest,original,"SOURCE_EDIT_REJECTED:"+e.getMessage());}
    }
    private static String sourceFeature(String target,SupportedEditProfile.Kind kind){return switch(target){case "name"->kind==SupportedEditProfile.Kind.CSL_ROLE_DEFINITION || kind==SupportedEditProfile.Kind.CSL_CONTEXT_DECLARATION?"id":"name";case "minimum"->"minCardinality";case "maximum"->"maxCardinality";default->target;};}
    private static Map<String,Object> fields(NormalizedSourceModel model,NormalizedSourceModel.Node n,SupportedEditProfile.Kind kind) {
        var out=new TreeMap<String,Object>();
        switch(kind){
            case CSL_ROLE_DEFINITION,CSL_CONTEXT_DECLARATION -> {out.put("name",value(n,"id"));out.put("minimum",value(n,"minCardinality"));out.put("maximum",value(n,"maxCardinality"));if(kind==SupportedEditProfile.Kind.CSL_ROLE_DEFINITION)out.put("isAbstract",value(n,"isAbstract"));}
            case ISTAR_OPLAN_DESCRIPTOR -> {var operator=(Map<?,?>)value(n,"operator");String name=(String)operator.get("name");out.put("operator",name);out.put("outcome",name.equals("CHOICE")?"OR":"AND");out.put("orderedChildren",((List<?>)value(n,"subGoals")).stream().map(ref->value(model.require((String)((Map<?,?>)ref).get("reference")),"id")).toList());}
            case ARTIFACT_INTERFACE -> {out.put("name",value(n,"name"));out.put("type",value(n,"type"));}
            case PROPERTY_INTERFACE,OPERATION_INTERFACE -> {out.put("name",value(n,"name"));out.put("arity",value(n,"arity"));}
        }return SemanticJson.object(out);
    }
    private static void requireConcept(NormalizedSourceModel.Node n,SupportedEditProfile.Kind kind) {String expected=switch(kind){case CSL_ROLE_DEFINITION->"Role";case CSL_CONTEXT_DECLARATION->"Group";case ISTAR_OPLAN_DESCRIPTOR->"OPlan";case ARTIFACT_INTERFACE->"Artifact";case PROPERTY_INTERFACE->"Property";case OPERATION_INTERFACE->"Operation";};if(!expected.equals(n.concept()))throw new IllegalArgumentException("LENS_OWNER_KIND_MISMATCH:"+kind);}
    private static void validateFields(Map<String,Object> fields,SupportedEditProfile.Kind kind) {
        if(!(fields.get("name") instanceof String name) || !name.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IllegalArgumentException("LENS_INVALID_DECLARATION_NAME");
        if(fields.containsKey("minimum")){if(!(fields.get("minimum") instanceof String) || !(fields.get("maximum") instanceof String))throw new IllegalArgumentException("LENS_CARDINALITY_TEXT_REQUIRED");String min=String.valueOf(fields.get("minimum")),max=String.valueOf(fields.get("maximum"));if(!min.matches("0|[1-9][0-9]*") || !max.equals("unlimited") && !max.matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("LENS_CARDINALITY_INVALID");if(!max.equals("unlimited") && new java.math.BigInteger(min).compareTo(new java.math.BigInteger(max))>0)throw new IllegalArgumentException("LENS_CARDINALITY_MIN_GT_MAX");}
        if(fields.containsKey("isAbstract") && !(fields.get("isAbstract") instanceof Boolean))throw new IllegalArgumentException("LENS_BOOLEAN_REQUIRED");
        if(fields.containsKey("arity")){var value=fields.get("arity");if(!(value instanceof Number number) || new java.math.BigDecimal(number.toString()).scale()>0 || number.longValue()<0 || number.longValue()>Integer.MAX_VALUE)throw new IllegalArgumentException("LENS_ARITY_INVALID");}
        if(kind==SupportedEditProfile.Kind.ARTIFACT_INTERFACE && (!(fields.get("type") instanceof String type) || !type.matches("[A-Za-z_$][A-Za-z0-9_$.]*")))throw new IllegalArgumentException("LENS_ARTIFACT_INTERFACE_TYPE_INVALID");
    }
    private static void validateUnchanged(NormalizedSourceModel old,NormalizedSourceModel next,Map<String,String> rebase,List<JacamoEcoreSourceLoader.DefinitionEdit> edits) {
        var inverse=new HashMap<String,String>();rebase.forEach((before,after)->inverse.put("emf:"+next.sourceHash()+":"+after,"emf:"+old.sourceHash()+":"+before));
        for(var previous:old.nodes()){String fragment=previous.id().substring(("emf:"+old.sourceHash()+":").length());var current=node(next,rebase.get(fragment));if(!previous.concept().equals(current.concept()))throw new IllegalArgumentException("LENS_UNSELECTED_CONCEPT_CHANGED");
            for(var field:previous.features().entrySet())if(edits.stream().noneMatch(e->e.fragment().equals(fragment) && e.feature().equals(field.getKey())))if(!Objects.equals(field.getValue().value(),rebaseReferences(current.features().get(field.getKey()).value(),inverse)))throw new IllegalArgumentException("LENS_UNSELECTED_FEATURE_CHANGED:"+fragment+":"+field.getKey());
        }
    }
    private static Object rebaseReferences(Object value,Map<String,String> inverse) {if(value instanceof Map<?,?> map){if(map.size()==1 && map.get("reference") instanceof String reference)return Map.of("reference",Objects.requireNonNull(inverse.get(reference),"LENS_REFERENCE_REBASE_MISSING"));var copy=new TreeMap<String,Object>();map.forEach((k,v)->copy.put((String)k,rebaseReferences(v,inverse)));return copy;}if(value instanceof List<?> list)return list.stream().map(v->rebaseReferences(v,inverse)).toList();return value;}
    private static void validateNames(NormalizedSourceModel model) {var names=new HashSet<List<String>>();for(var n:model.nodes()){String feature=n.features().containsKey("id")?"id":n.features().containsKey("name")?"name":"";if(!feature.isBlank() && !names.add(List.of(n.container(),n.concept(),String.valueOf(value(n,feature)))))throw new IllegalArgumentException("LENS_SIBLING_NAME_COLLISION:"+value(n,feature));}}
    private static Object value(NormalizedSourceModel.Node n,String feature){return n.features().get(feature).value();}
    private static NormalizedSourceModel.Node node(NormalizedSourceModel model,String fragment){return model.require("emf:"+model.sourceHash()+":"+fragment);}
    private static SourcePatchPlan fail(SourcePatchPlan.Status status,String digest,Projection original,String diagnostic){return new SourcePatchPlan(status,digest,"","",original.profile(),original.target(),List.of(),List.of(diagnostic),"NO_SOURCE_CHANGE");}
}
