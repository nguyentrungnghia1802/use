package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.util.*;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.OrganizationSemantic;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MLinkObject;
import org.tzi.use.uml.ocl.value.*;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.SchemeSemantic;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.uml.mm.MElementAnnotation;
import static org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.RelationDisposition.*;

/** Specification-derived types, contextual association classes and flattened functional state. */
public final class MoiseDomainProjection {
    public static final String ANNOTATION = "MoiseProjection";
    public static final Map<String,String> INSPECTION_RULES = Map.ofEntries(
        Map.entry("Organization","M01"), Map.entry("StructuralSpecification","M02"),
        Map.entry("FunctionalSpecification","M03"), Map.entry("NormativeSpecification","M04"),
        Map.entry("Group","M05"), Map.entry("Role","M06"), Map.entry("RoleRelation","M07"),
        Map.entry("Link","M08"), Map.entry("Compatibility","M09"), Map.entry("Scheme","M10"),
        Map.entry("Mission","M11"), Map.entry("OrganizationalGoal","M12"), Map.entry("OrganizationalPlan","M13"),
        Map.entry("Norm","M14"), Map.entry("GroupRoleCardinality","M15"), Map.entry("SubGroupCardinality","M16"),
        Map.entry("SchemeMissionCardinality","M17"));
    public record Result(Map<String,String> classNames, List<NativeConstraintSpec> constraints) {
        public Result { classNames=Collections.unmodifiableMap(new LinkedHashMap<>(classNames)); constraints=List.copyOf(constraints); }
    }
    public static final String ROLE_ASSOCIATION = "RoleInGroup";
    public static String roleRelationId(String organisation, String group, String role) {
        return "role-in-group:" + NativeUseModelBuilder.canonicalJson(List.of(organisation, group, role));
    }
    /** Presentation names are never used to resolve a role relation. */
    public static org.tzi.use.uml.mm.MAssociationClass roleAssociation(org.tzi.use.uml.mm.MModel model,
            String organisation, String group, String role) {
        String identity = DomainProjection.encode(roleRelationId(organisation, group, role));
        var matches = model.associations().stream()
            .filter(a -> identity.equals(a.getAnnotationValue(ROLE_ASSOCIATION, "identity64"))).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("ROLE_IN_GROUP_CONTEXT_UNRESOLVED:" + identity);
        if (!(matches.get(0) instanceof org.tzi.use.uml.mm.MAssociationClass result))
            throw new IllegalArgumentException("ROLE_NATIVE_ASSOCIATION_CLASS_REQUIRED:"+identity);
        return result;
    }
    public static Result install(UseModelApi api, List<OrganizationSemantic> organizations,
            CodeGroundedTraceCollector trace) throws UseApiException {
        List<MoiseUseSymbols.Candidate> candidates=new ArrayList<>();
        candidates(organizations,candidates);
        Set<String> reserved=new TreeSet<>(); api.getModel().classes().forEach(c->reserved.add(c.name()));
        return install(api,organizations,trace,MoiseUseSymbols.allocate(candidates,reserved));
    }
    static void candidates(List<OrganizationSemantic> organizations, List<MoiseUseSymbols.Candidate> candidates) {
        for(var org:organizations) {
            validate(org);
            candidates.add(new MoiseUseSymbols.Candidate(org.metadata().semanticId(),"organisation",org.name()+"_Organization"));
            for(var group:org.structuralSpecification().groups())
                candidates.add(new MoiseUseSymbols.Candidate(group.metadata().semanticId(),"group",group.groupId()));
            for(var scheme:org.functionalSpecification().schemes())
                candidates.add(new MoiseUseSymbols.Candidate(scheme.metadata().semanticId(),"scheme",scheme.schemeId()+"_Scheme"));
        }
    }
    static Result install(UseModelApi api,List<OrganizationSemantic> organizations,CodeGroundedTraceCollector trace,
            Map<String,String> symbols) throws UseApiException {
        Map<String,String> classes=new LinkedHashMap<>(); List<NativeConstraintSpec> constraints=new ArrayList<>();
        // Pass 1: all classifiers and their source identity, before any relation.
        for(var org:organizations) {
            create(api,trace,classes,symbols,org.metadata(),"organisation","M01",false);
            for(var role:org.structuralSpecification().roles())
                DomainProjection.trace(trace,"M06",role.metadata(),"RoleDefinition",role.metadata().semanticId(),
                    IGNORE_NOT_NEEDED,"SEMANTIC_KIND=role-definition","SOURCE_SEMANTICS_RETAINED",
                    role.abstractRole() || !role.superRoleSemanticIds().isEmpty()
                        ? "MOISE_ADVANCED_ROLE_SEMANTICS_DEFERRED:abstract/inheritance" : "NO_EXPOSED_ROLE_CLASS");
            for(var group:org.structuralSpecification().groups())
                create(api,trace,classes,symbols,group.metadata(),"group","M05",false);
            for(var scheme:org.functionalSpecification().schemes())
                create(api,trace,classes,symbols,scheme.metadata(),"scheme","M10",false);
        }
        List<MoiseUseSymbols.Candidate> relations = new ArrayList<>();
        for (var org : organizations) for (var card : org.structuralSpecification().groupRoleCardinalities()) {
            var group = org.structuralSpecification().groups().stream().filter(g -> g.metadata().semanticId().equals(card.groupId())).findFirst().orElseThrow();
            var role = org.structuralSpecification().roles().stream().filter(r -> r.metadata().semanticId().equals(card.roleId())).findFirst().orElseThrow();
            relations.add(new MoiseUseSymbols.Candidate(roleRelationId(org.metadata().semanticId(), card.groupId(), card.roleId()),
                "role_in_group", role.roleId()));
        }
        Set<String> reservedRelations = new TreeSet<>(); api.getModel().associations().forEach(a -> reservedRelations.add(a.name()));
        api.getModel().classes().forEach(c -> reservedRelations.add(c.name()));
        Map<String,String> relationSymbols = MoiseUseSymbols.allocate(relations, reservedRelations);
        // Pass 2: only relations whose domain endpoints exist.
        for(var org:organizations) {
            var ss=org.structuralSpecification(); String os=required(symbols,org.metadata().semanticId());
            for(var group:ss.groups()) {
                String cls=required(symbols,group.metadata().semanticId());
                String association=DomainProjection.relation("containsGroup",os,cls);
                if (api.getModel().getAssociation(association) == null)
                    DomainProjection.association(api,association,"Organization","organization","1","Group","groups","*");
                DomainProjection.trace(trace,"M22",group.metadata(),"MAssociation","association:"+association,PRESERVE_AS_ASSOCIATION);
            }
            for(var card:ss.groupRoleCardinalities()) {
                String group=required(symbols,card.groupId());
                checkBounds(card.min(),card.max(),card.metadata().semanticId());
                String identity=roleRelationId(org.metadata().semanticId(),card.groupId(),card.roleId());
                String association=relationSymbols.get(identity), suffix=DomainProjection.hash(identity);
                var target=api.createAssociationClass(association,false,"Agent","players_"+suffix,
                    card.min()+".."+(unlimited(card.max()) ? "*" : card.max()),0,group,"roleGroups_"+suffix,"0..*",0);
                DomainProjection.annotate(target,"role-association",identity,"");
                api.createAttribute(association,"semanticId","String");
                api.createAttribute(association,"agentSemanticId","String");
                api.createAttribute(association,"groupInstanceId","String");
                target.addAnnotation(new MElementAnnotation(ANNOTATION,new TreeMap<>(Map.of("ruleId","M15",
                    "sourceId64",DomainProjection.encode(card.metadata().semanticId()),"min",Integer.toString(card.min()),"max",Integer.toString(card.max()),
                    "runtimeSource","moise"))));
                target.addAnnotation(new MElementAnnotation(ROLE_ASSOCIATION,new TreeMap<>(Map.of(
                    "kind","role-in-group","identity64",DomainProjection.encode(identity),
                    "organisationId64",DomainProjection.encode(org.metadata().semanticId()),
                    "groupId64",DomainProjection.encode(card.groupId()),"roleId64",DomainProjection.encode(card.roleId())))));
                DomainProjection.trace(trace,"M15",card.metadata(),"MAssociationClass","association-class:"+association,
                    PRESERVE_AS_ASSOCIATION_CLASS,"SEMANTIC_KIND=role-in-group","CONTEXT="+identity,
                    "BOUNDS_ENFORCED_BY_NATIVE_MULTIPLICITY","min="+card.min(),"max="+card.max());
                DomainProjection.trace(trace,"M15",card.metadata(),"MClass","class:"+association,PRESERVE_AS_ASSOCIATION_CLASS,"CONTEXT="+identity);
                DomainProjection.trace(trace,"M15",card.metadata(),"MAssociation","association:"+association,PRESERVE_AS_ASSOCIATION_CLASS,"CONTEXT="+identity);
            }
            for(var card:ss.subGroupCardinalities()) {
                checkBounds(card.min(),card.max(),card.metadata().semanticId());
                String parent=required(symbols,card.parentGroupId()),child=required(symbols,card.subGroupId());
                String navigation="subgroups_"+DomainProjection.hash(card.metadata().semanticId());
                String association=DomainProjection.relation("containsSubgroup",parent,child);
                DomainProjection.association(api,association,parent,"parent_"+DomainProjection.hash(card.metadata().semanticId()),"0..1",
                    child,navigation,card.min()+".."+(unlimited(card.max()) ? "*" : card.max()));
                api.getModel().getAssociation(association).addAnnotation(new MElementAnnotation("SubgroupContext",Map.of(
                    "sourceId64",DomainProjection.encode(card.metadata().semanticId()),"organisationId64",DomainProjection.encode(org.metadata().semanticId()),
                    "parentSpecId64",DomainProjection.encode(card.parentGroupId()),"childSpecId64",DomainProjection.encode(card.subGroupId()))));
                DomainProjection.trace(trace,"M16",card.metadata(),"MAssociation","association:"+association,PRESERVE_AS_ASSOCIATION);
            }
            policy(api,trace,os,"M02",ss.metadata(),FLATTEN,Map.of("structuralSpecification",ss));
            for(var relation:ss.roleRelations()) policy(api,trace,os,"M07",relation.metadata(),IGNORE_NOT_NEEDED,Map.of("relation",relation,"status","MOISE_ADVANCED_ROLE_SEMANTICS_DEFERRED"));
            for(var link:ss.links()) policy(api,trace,os,"M08",link.metadata(),IGNORE_NOT_NEEDED,Map.of("link",link,"status","MOISE_ADVANCED_ROLE_SEMANTICS_DEFERRED"));
            for(var compatibility:ss.compatibilities()) policy(api,trace,os,"M09",compatibility.metadata(),IGNORE_NOT_NEEDED,Map.of("compatibility",compatibility,"status","MOISE_ADVANCED_ROLE_SEMANTICS_DEFERRED"));
            var fs=org.functionalSpecification();
            DomainProjection.trace(trace,"M03",fs.metadata(),"SemanticEvidence",fs.metadata().semanticId(),FLATTEN);
            for(var scheme:fs.schemes()) {
                scheme.goals().forEach(g->DomainProjection.trace(trace,"M12",g.metadata(),"MClass","class:OrganizationalGoal",PRESERVE_AS_CLASS));
                scheme.missions().forEach(m->DomainProjection.trace(trace,"M11",m.metadata(),"MClass","class:Mission",PRESERVE_AS_CLASS));
                scheme.plans().forEach(p->DomainProjection.trace(trace,"M13",p.metadata(),"MAssociation",
                    "association:"+DomainProjection.relation("subGoals","OrganizationalGoal","OrganizationalGoal"),FLATTEN,
                    "ORDER=EXACT_CHILD_ORDINAL_ATTRIBUTE","OPERATOR="+p.operator()));
            }
            fs.schemeMissionCardinalities().forEach(c->DomainProjection.trace(trace,"M17",c.metadata(),"MAttribute","Mission.min,max",CONVERT_TO_ATTRIBUTE));
            for(var norm:org.normativeSpecification().norms()) {
                Map<String,Object> fields=new LinkedHashMap<>(); fields.put("norm",norm); fields.put("role",norm.roleSemanticId());
                fields.put("mission",norm.missionSemanticId()); fields.put("condition",norm.condition()); fields.put("deadline",norm.timeConstraint());
                fields.put("status","UNSUPPORTED_NORM_TRANSLATION");
                policy(api,trace,os,"M14",norm.metadata(),CONVERT_TO_OCL,fields);
            }
        }
        return new Result(classes,constraints);
    }
    private static void create(UseModelApi api,CodeGroundedTraceCollector trace,Map<String,String> classes,Map<String,String> symbols,
            SemanticMetadata source,String kind,String rule,boolean abstractType) throws UseApiException {
        String name=required(symbols,source.semanticId()); var cls=api.createClass(name,abstractType);
        DomainProjection.annotate(cls,kind,source.semanticId(),"");
        cls.addAnnotation(new MElementAnnotation(ANNOTATION,new TreeMap<>(Map.of("ruleId",rule,"runtimeSource","moise","runtimeProjection","MATERIALIZED"))));
        api.createGeneralization(name,switch(kind) { case "organisation" -> "Organization"; case "group" -> "Group"; case "scheme" -> "Scheme"; default -> throw new IllegalArgumentException("MOISE_PROJECTION_KIND_UNSUPPORTED:"+kind); });
        classes.put(source.semanticId(),name);
        DomainProjection.trace(trace,rule,source,"MClass","class:"+name,PRESERVE_AS_ASSOCIATION,"SEMANTIC_KIND="+kind);
    }
    private static void policy(UseModelApi api,CodeGroundedTraceCollector trace,String owner,String rule,SemanticMetadata source,
            DomainProjection.RelationDisposition disposition,Map<String,?> values) {
        Map<String,Object> payload=new LinkedHashMap<>(values); payload.put("sourceSemanticId",source.semanticId());
        String annotation="MoisePolicy_"+rule+"_"+DomainProjection.hash(source.semanticId());
        // Arbitrary normative text must not become executable OCL or corrupt USE annotations.
        String json=NativeUseModelBuilder.canonicalJson(payload);
        api.getModel().getClass(owner).addAnnotation(new MElementAnnotation(annotation,Map.of("payload64",DomainProjection.encode(json))));
        DomainProjection.trace(trace,rule,source,disposition==CONVERT_TO_OCL ? "OclGenerationHook" : "SemanticEvidence",
            "class:"+owner+"@"+annotation,disposition,disposition==CONVERT_TO_OCL ? "STATUS=UNSUPPORTED_NORM_TRANSLATION"
                : values.containsKey("status") ? values.get("status").toString() : "SOURCE_SEMANTICS_RETAINED");
    }
    private static boolean unlimited(int max) { return max==-1 || max==Integer.MAX_VALUE; }
    private static void checkBounds(int min,int max,String id) {
        if(min<0 || (!unlimited(max) && max<min)) throw new IllegalArgumentException("MOISE_CARDINALITY_INVALID:"+id);
    }
    private static String required(Map<String,String> names,String id) {
        String name=names.get(id); if(name==null) throw badReference(id); return name;
    }
    private static IllegalArgumentException badReference(String id) { return new IllegalArgumentException("MOISE_EXACT_SCHEMA_REFERENCE_MISSING:"+id); }

    /** A role instance is one native object AND link, identified by exact endpoints and context. */
    public static MLinkObject linkObject(UseSystemApi api, Map<String,MObject> index,
            org.tzi.use.uml.mm.MAssociationClass association, MObject agent, MObject group) throws UseApiException {
        String context=DomainProjection.decode(association.getAnnotationValue(ROLE_ASSOCIATION,"identity64"));
        String agentId=stringAttribute(api,agent,"semanticId"), groupId=stringAttribute(api,group,"semanticId");
        String id="role-instance:"+NativeUseModelBuilder.canonicalJson(List.of(context,agentId,groupId));
        if(index.get(id) instanceof MLinkObject old) return old;
        MObject[] pair={agent,group};
        var existing=api.getSystem().state().allLinks().stream().filter(l->l.association()==association && l.linkedObjects().equals(List.of(pair))).toList();
        if(!existing.isEmpty()) {
            if(existing.size()!=1 || !(existing.getFirst() instanceof MLinkObject result)) throw badReference(id);
            index.put(id,result); return result;
        }
        var object=api.createLinkObjectEx(association,"role_"+DomainProjection.hash(id),pair);
        text(api,object,"semanticId",id); text(api,object,"agentSemanticId",agentId); text(api,object,"groupInstanceId",groupId);
        index.put(id,object); return object;
    }
    private static String stringAttribute(UseSystemApi api,MObject object,String name) {
        var value=object.state(api.getSystem().state()).attributeValue(name);
        if(!(value instanceof StringValue string) || string.value().isBlank()) throw badReference(object.name()+"."+name);
        return string.value();
    }
    public static String functionalObjectId(String kind,String schemeInstance,String sourceId) {
        return schemeInstance==null ? sourceId : DomainProjection.occurrenceId(kind,schemeInstance,sourceId);
    }
    /** Source definitions and runtime occurrences share the same typed graph projection. */
    public static void materializeFunctional(UseSystemApi api, Map<String,MObject> index,
            CodeGroundedTraceCollector trace, OrganizationSemantic org, SchemeSemantic scheme, MObject schemeObject) throws UseApiException {
        String instance=schemeObject==null ? null : stringAttribute(api,schemeObject,"semanticId");
        if(schemeObject!=null) {
            text(api,schemeObject,"specSemanticId",scheme.metadata().semanticId());
            if(schemeObject.state(api.getSystem().state()).attributeValue("sourceLayer").isUndefined())
                text(api,schemeObject,"sourceLayer","DECLARATION");
        }
        Map<String,MObject> goals=new LinkedHashMap<>(),missions=new LinkedHashMap<>();
        for(var goal:scheme.goals()) {
            String id=functionalObjectId("organisational-goal",instance,goal.metadata().semanticId());
            var object=valueObject(api,index,"OrganizationalGoal",goal.goalId(),id);
            goals.put(goal.metadata().semanticId(),object);
            text(api,object,"id",goal.goalId()); text(api,object,"description",goal.description()); text(api,object,"goalType",goal.goalType());
            text(api,object,"ttf",goal.ttf()); text(api,object,"arguments",goal.arguments());
            text(api,object,"sourceLayer",instance==null ? "SPECIFICATION" : "INSTANCE_SPECIFICATION");
            text(api,object,"specSemanticId",goal.metadata().semanticId());text(api,object,"schemeSpecSemanticId",scheme.metadata().semanticId());
            if(instance!=null)text(api,object,"schemeInstanceIdentity",instance);
            api.setAttributeValueEx(object,object.cls().attribute("minAgentsToSatisfy",true),IntegerValue.valueOf(goal.minAgentsToSatisfy()));
            valueTrace(trace,"M12",goal.metadata(),object);
            if(schemeObject!=null) functionalLink(api,trace,"M35",goal.metadata(),"schemeGoals","Scheme","OrganizationalGoal",schemeObject,object);
            if(!goal.dependencySemanticIds().isEmpty())
                DomainProjection.trace(trace,"M12",goal.metadata(),"Diagnostic",id,UNSUPPORTED,"GOAL_DEPENDENCIES_RETAINED_IN_IR:"+goal.dependencySemanticIds());
        }
        for(var plan:scheme.plans()) {
            var parent=goals.get(plan.targetGoalSemanticId()); text(api,parent,"decompositionOperator",plan.operator());
            for(int ordinal=0;ordinal<plan.orderedSubGoalSemanticIds().size();ordinal++) {
                var child=goals.get(plan.orderedSubGoalSemanticIds().get(ordinal));
                api.setAttributeValueEx(child,child.cls().attribute("orderInParent",true),IntegerValue.valueOf(ordinal));
                functionalLink(api,trace,"M40",plan.metadata(),"subGoals","OrganizationalGoal","OrganizationalGoal",parent,child);
            }
            DomainProjection.trace(trace,"M13",plan.metadata(),"AttributeValue",parent.name()+".decompositionOperator",CONVERT_TO_ATTRIBUTE,
                    "ORDER=EXACT_CHILD_ORDINAL_ATTRIBUTE","OPERATOR="+plan.operator());
        }
        for(var mission:scheme.missions()) {
            String id=functionalObjectId("mission",instance,mission.metadata().semanticId());
            var object=valueObject(api,index,"Mission",mission.missionId(),id); missions.put(mission.metadata().semanticId(),object);
            text(api,object,"id",mission.missionId()); text(api,object,"sourceLayer",instance==null ? "SPECIFICATION" : "INSTANCE_SPECIFICATION");
            text(api,object,"specSemanticId",mission.metadata().semanticId());text(api,object,"schemeSpecSemanticId",scheme.metadata().semanticId());
            if(instance!=null)text(api,object,"schemeInstanceIdentity",instance);
            valueTrace(trace,"M11",mission.metadata(),object);
            var cards=org.functionalSpecification().schemeMissionCardinalities().stream()
                    .filter(c->c.schemeId().equals(scheme.metadata().semanticId()) && c.missionId().equals(mission.metadata().semanticId())).toList();
            if(cards.size()!=1) throw badReference(id+":cardinality");
            var card=cards.getFirst(); checkBounds(card.min(),card.max(),card.metadata().semanticId());
            api.setAttributeValueEx(object,object.cls().attribute("min",true),IntegerValue.valueOf(card.min()));
            api.setAttributeValueEx(object,object.cls().attribute("max",true),IntegerValue.valueOf(card.max()));
            DomainProjection.trace(trace,"M17",card.metadata(),"AttributeValue",object.name()+".min,max",CONVERT_TO_ATTRIBUTE);
            if(schemeObject!=null) functionalLink(api,trace,"M34",mission.metadata(),"schemeMissions","Scheme","Mission",schemeObject,object);
            for(String goal:mission.goalSemanticIds()) functionalLink(api,trace,"M38",mission.metadata(),"missionGoals","Mission","OrganizationalGoal",object,goals.get(goal));
        }
    }
    private static MObject valueObject(UseSystemApi api,Map<String,MObject> index,String type,String label,String id) throws UseApiException {
        var old=index.get(id); if(old!=null) return old;
        String name=DomainProjection.symbol(label)+"_"+DomainProjection.hash(id);
        var object=api.createObject(type,name); index.put(id,object); text(api,object,"semanticId",id); text(api,object,"name",label); return object;
    }
    private static void text(UseSystemApi api,MObject object,String name,String value) throws UseApiException {
        api.setAttributeValueEx(object,object.cls().attribute(name,true),new StringValue(value));
    }
    private static void functionalLink(UseSystemApi api,CodeGroundedTraceCollector trace,String rule,SemanticMetadata source,
            String kind,String firstType,String secondType,MObject first,MObject second) throws UseApiException {
        var association=api.getSystem().model().getAssociation(DomainProjection.relation(kind,firstType,secondType)); MObject[] pair={first,second};
        if(!api.getSystem().state().hasLinkBetweenObjects(association,pair)) api.createLinkEx(association,pair);
        DomainProjection.trace(trace,rule,source,"MLink",association.name()+":"+first.name()+"->"+second.name(),PRESERVE_AS_LINK);
    }
    private static void valueTrace(CodeGroundedTraceCollector trace,String rule,SemanticMetadata source,MObject object) {
        DomainProjection.trace(trace,rule,source,"MObject",object.name(),PRESERVE_AS_CLASS);
        for(var attribute:object.cls().allAttributes()) DomainProjection.trace(trace,rule,source,"MValue","value:"+object.name()+"."+attribute.name(),CONVERT_TO_ATTRIBUTE);
    }
    /** Typed, owner-scoped foreign keys; a globally present symbol is not sufficient evidence. */
    public static void validate(OrganizationSemantic org) {
        var ss = org.structuralSpecification(); var fs = org.functionalSpecification(); var ns = org.normativeSpecification();
        String osId = org.metadata().semanticId();
        if (!ss.organizationSemanticId().equals(osId) || !fs.organizationSemanticId().equals(osId)
                || !ns.organizationSemanticId().equals(osId)) throw badReference(osId);
        var roles = ss.roles().stream().collect(java.util.stream.Collectors.toMap(r -> r.metadata().semanticId(), r -> r));
        var groups = ss.groups().stream().collect(java.util.stream.Collectors.toMap(g -> g.metadata().semanticId(), g -> g));
        if (!ss.rootGroupSemanticId().isEmpty() && !groups.containsKey(ss.rootGroupSemanticId())) throw badReference(ss.rootGroupSemanticId());
        for (var role : ss.roles()) for (String parent : role.superRoleSemanticIds())
            if (!roles.containsKey(parent)) throw badReference(parent);
        for (var group : ss.groups()) {
            if (!group.parentGroupSemanticId().isEmpty() && (!groups.containsKey(group.parentGroupSemanticId())
                    || !groups.get(group.parentGroupSemanticId()).subgroupSemanticIds().contains(group.metadata().semanticId())))
                throw badReference(group.parentGroupSemanticId());
            for (String child : group.subgroupSemanticIds()) if (!groups.containsKey(child)
                    || !groups.get(child).parentGroupSemanticId().equals(group.metadata().semanticId())) throw badReference(child);
            for (String role : group.roleSemanticIds()) if (!roles.containsKey(role)) throw badReference(role);
        }
        Set<String> tuples = new TreeSet<>();
        for (var card : ss.groupRoleCardinalities()) {
            if (!roles.containsKey(card.roleId()) || !groups.containsKey(card.groupId()) || !groups.get(card.groupId()).roleSemanticIds().contains(card.roleId())) throw badReference(card.metadata().semanticId());
            if (!tuples.add(card.groupId() + "\u0000" + card.roleId())) throw new IllegalArgumentException("MOISE_DUPLICATE_CARDINALITY_TUPLE:" + card.metadata().semanticId());
        }
        for (var group : ss.groups()) for (String role : group.roleSemanticIds())
            if (!tuples.contains(group.metadata().semanticId() + "\u0000" + role)) throw badReference(group.metadata().semanticId() + ":" + role);
        tuples.clear();
        for (var card : ss.subGroupCardinalities()) {
            if (!groups.containsKey(card.parentGroupId()) || !groups.get(card.parentGroupId()).subgroupSemanticIds().contains(card.subGroupId())) throw badReference(card.metadata().semanticId());
            if (!tuples.add(card.parentGroupId() + "\u0000" + card.subGroupId())) throw new IllegalArgumentException("MOISE_DUPLICATE_CARDINALITY_TUPLE:" + card.metadata().semanticId());
        }
        Set<String> allMissions = new TreeSet<>();
        Map<String, Set<String>> missionsByScheme = new LinkedHashMap<>();
        for (var scheme : fs.schemes()) {
            if (!scheme.functionalSpecificationSemanticId().equals(fs.metadata().semanticId())) throw badReference(scheme.metadata().semanticId());
            Set<String> goals = scheme.goals().stream().map(g -> g.metadata().semanticId()).collect(java.util.stream.Collectors.toSet());
            Set<String> plans = scheme.plans().stream().map(p -> p.metadata().semanticId()).collect(java.util.stream.Collectors.toSet());
            if (!scheme.rootGoalSemanticId().isEmpty() && !goals.contains(scheme.rootGoalSemanticId())) throw badReference(scheme.rootGoalSemanticId());
            var missions = scheme.missions().stream().map(m -> m.metadata().semanticId()).collect(java.util.stream.Collectors.toSet());
            missionsByScheme.put(scheme.metadata().semanticId(), missions); allMissions.addAll(missions);
            for (var mission : scheme.missions()) {
                if(!mission.schemeSemanticId().equals(scheme.metadata().semanticId())) throw badReference(mission.metadata().semanticId());
                for (String goal : mission.goalSemanticIds()) if (!goals.contains(goal)) throw badReference(goal);
            }
            for (var goal : scheme.goals()) {
                if(!goal.schemeSemanticId().equals(scheme.metadata().semanticId())) throw badReference(goal.metadata().semanticId());
                for (String dependency : goal.dependencySemanticIds()) if (!goals.contains(dependency)) throw badReference(dependency);
                if (!goal.planSemanticId().isEmpty() && !plans.contains(goal.planSemanticId())) throw badReference(goal.planSemanticId());
                if (!goal.inPlanSemanticId().isEmpty() && !plans.contains(goal.inPlanSemanticId())) throw badReference(goal.inPlanSemanticId());
            }
            for (var plan : scheme.plans()) {
                if(!plan.schemeSemanticId().equals(scheme.metadata().semanticId())) throw badReference(plan.metadata().semanticId());
                if (!goals.contains(plan.targetGoalSemanticId())) throw badReference(plan.targetGoalSemanticId());
                for (String child : plan.orderedSubGoalSemanticIds()) if (!goals.contains(child)) throw badReference(child);
            }
            Map<String,String> parents=new LinkedHashMap<>(); Set<String> targets=new HashSet<>();
            for(var plan:scheme.plans()) {
                if(!Set.of("sequence","parallel","choice").contains(plan.operator()) || !targets.add(plan.targetGoalSemanticId()))
                    throw new IllegalArgumentException("MOISE_DECOMPOSITION_UNSUPPORTED:"+plan.metadata().semanticId());
                for(String child:plan.orderedSubGoalSemanticIds()) if(parents.putIfAbsent(child,plan.targetGoalSemanticId())!=null)
                    throw new IllegalArgumentException("MOISE_DECOMPOSITION_MULTIPLE_PARENTS:"+child);
            }
            for(String child:parents.keySet()) {
                Set<String> visited=new HashSet<>(); String current=child;
                while(current!=null) { if(!visited.add(current)) throw new IllegalArgumentException("MOISE_DECOMPOSITION_CYCLE:"+child); current=parents.get(current); }
            }
        }
        tuples.clear();
        for (var card : fs.schemeMissionCardinalities()) {
            if (!missionsByScheme.containsKey(card.schemeId()) || !missionsByScheme.get(card.schemeId()).contains(card.missionId())) throw badReference(card.metadata().semanticId());
            if (!tuples.add(card.schemeId() + "\u0000" + card.missionId())) throw new IllegalArgumentException("MOISE_DUPLICATE_CARDINALITY_TUPLE:" + card.metadata().semanticId());
        }
        for (var norm : ns.norms()) {
            if (!norm.normativeSpecificationSemanticId().equals(ns.metadata().semanticId())) throw badReference(norm.metadata().semanticId());
            if (!norm.roleSemanticId().isEmpty() && !roles.containsKey(norm.roleSemanticId())) throw badReference(norm.roleSemanticId());
            if (!norm.missionSemanticId().isEmpty() && !allMissions.contains(norm.missionSemanticId())) throw badReference(norm.missionSemanticId());
        }
    }

}
