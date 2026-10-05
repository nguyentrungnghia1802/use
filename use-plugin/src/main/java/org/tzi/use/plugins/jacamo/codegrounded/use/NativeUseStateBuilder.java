package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.io.*;
import java.util.*;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.codegrounded.model.JacamoSpecificationModel;
import org.tzi.use.plugins.jacamo.codegrounded.trace.*;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.sys.*;
import static org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.RelationDisposition.*;

/** Declaration and observed runtime objects, typed by their semantic specifications. */
public final class NativeUseStateBuilder {
    public Result build(JacamoSpecificationModel source,NativeUseModelBuilder.Result schema) {
        var trace=new CodeGroundedTraceCollector(); schema.trace().records().forEach(trace::add); return build(source,schema,trace);
    }
    public Result build(JacamoSpecificationModel source,NativeUseModelBuilder.Result schema,CodeGroundedTraceCollector trace) {
        var system=new MSystem(schema.model()); var api=UseSystemApi.create(system,false); Map<String,MObject> objects=new LinkedHashMap<>();
        var snapshot=source.snapshot();
        try {
            // Pass 1: objects and exact source identity.
            for(var agent:snapshot.agentDeclarations()) {
                if(agent.instances()!=1) {
                    DomainProjection.trace(trace,"J02",agent.metadata(),"SemanticEvidence",agent.metadata().semanticId(),PRESERVE_AS_LINK,
                        "RUNTIME_AGENT_INSTANCE_IDENTITIES_REQUIRED");
                    continue;
                }
                var programs=source.programs().stream().filter(p->p.declarationId().equals(agent.name())).toList();
                if(programs.size()!=1) throw new IllegalArgumentException("AGENT_PROGRAM_IDENTITY_UNRESOLVED:"+agent.metadata().semanticId());
                var program=programs.get(0);
                String cls=DomainProjection.classFor(schema.model(),"agent-program",DomainProjection.programId(program.sourceUri(),program.sourceDigest()));
                MObject object=create(api,objects,cls,agent.name(),agent.metadata().semanticId());
                text(api,object,"sourceUri",program.sourceUri()); text(api,object,"host",agent.host());
                instanceTrace(trace,"J02",agent.metadata(),object);
                for(var belief:program.beliefs()) {
                    DomainProjection.trace(trace,"A08",belief.metadata(),"SemanticEvidence",belief.metadata().semanticId(),IGNORE_NOT_NEEDED,
                        "BELIEF_REQUIRES_DOMAIN_PROVENANCE_AND_ACTIVE_OCL_DEMAND");
                }
                for(var goal:program.goals()) {
                    String id=DomainProjection.occurrenceId("initial-goal",agent.metadata().semanticId(),goal.metadata().semanticId());
                    var value=create(api,objects,"AgentGoal","goal_"+DomainProjection.hash(id),id);
                    text(api,value,"literal",goal.literal()); text(api,value,"sourceLayer","INITIAL");
                    link(api,DomainProjection.relation("hasGoal","Agent","AgentGoal"),object,value);
                    instanceTrace(trace,"A09",goal.metadata(),value);
                    DomainProjection.trace(trace,"A22",goal.metadata(),"MLink",object.name()+"->"+value.name(),PRESERVE_AS_LINK);
                }
            }
            Map<String,String> deploymentOs=new LinkedHashMap<>();
            for(var deployment:snapshot.organizationDeployments()) {
                var org=DomainProjection.organization(snapshot,deployment); String cls=DomainProjection.classFor(schema.model(),"organisation",org.metadata().semanticId());
                var object=create(api,objects,cls,deployment.name(),deployment.metadata().semanticId());
                deploymentOs.put(deployment.name(),org.metadata().semanticId()); instanceTrace(trace,"J05",deployment.metadata(),object);
            }
            Map<String,String> groupTypes=new LinkedHashMap<>();
            for(var deployment:snapshot.groupDeployments()) {
                String os=deploymentOs.get(deployment.organization());
                var org=snapshot.moiseOrganizations().stream().filter(o->o.metadata().semanticId().equals(os)).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("GROUP_ORGANISATION_UNRESOLVED:"+deployment.metadata().semanticId()));
                var groups=org.structuralSpecification().groups().stream().filter(g->g.groupId().equals(deployment.type())).toList();
                if(groups.size()!=1) throw new IllegalArgumentException("GROUP_SPECIFICATION_UNRESOLVED:"+deployment.metadata().semanticId());
                String cls=DomainProjection.classFor(schema.model(),"group",groups.get(0).metadata().semanticId());
                var object=create(api,objects,cls,deployment.name(),deployment.metadata().semanticId());
                groupTypes.put(deployment.metadata().semanticId(),groups.get(0).metadata().semanticId()); instanceTrace(trace,"J06",deployment.metadata(),object);
            }
            Set<String> deployedSchemes=new HashSet<>();
            for(var deployment:snapshot.schemeDeployments()) {
                String os=deploymentOs.get(deployment.organization());
                var org=snapshot.moiseOrganizations().stream().filter(o->o.metadata().semanticId().equals(os)).findFirst().orElseThrow();
                var matches=org.functionalSpecification().schemes().stream().filter(s->s.schemeId().equals(deployment.type())).toList();
                if(matches.size()!=1) throw new IllegalArgumentException("SCHEME_SPECIFICATION_UNRESOLVED:"+deployment.metadata().semanticId());
                var specification=matches.getFirst();
                String cls=DomainProjection.classFor(schema.model(),"scheme",specification.metadata().semanticId());
                var object=create(api,objects,cls,deployment.name(),deployment.metadata().semanticId()); instanceTrace(trace,"J07",deployment.metadata(),object);
                link(api,DomainProjection.relation("containsScheme","Organization","Scheme"),required(objects,snapshot.organizationDeployments().stream()
                    .filter(o->o.name().equals(deployment.organization())).findFirst().orElseThrow().metadata().semanticId()),object);
                DomainProjection.trace(trace,"M33",specification.metadata(),"MLink",object.name()+":organisation",PRESERVE_AS_LINK);
                MoiseDomainProjection.materializeFunctional(api,objects,trace,org,specification,object);
                deployedSchemes.add(specification.metadata().semanticId());
            }
            for(var org:snapshot.moiseOrganizations()) for(var scheme:org.functionalSpecification().schemes())
                if(!deployedSchemes.contains(scheme.metadata().semanticId())) MoiseDomainProjection.materializeFunctional(api,objects,trace,org,scheme,null);
            for(var declaration:snapshot.workspaceDeclarations()) {
                var object=create(api,objects,"Workspace",declaration.name(),declaration.metadata().semanticId());
                text(api,object,"fullName",declaration.name()); instanceTrace(trace,"J03",declaration.metadata(),object);
            }
            for(var declaration:snapshot.artifactDeclarations()) {
                String cls;
                try { cls=DomainProjection.artifactClass(schema.model(),declaration.javaClass()); }
                catch(IllegalArgumentException unavailable) { continue; } // The type diagnostic already records missing evidence.
                var workspaces=snapshot.workspaceDeclarations().stream().filter(w->w.name().equals(declaration.workspace())).toList();
                if(workspaces.size()!=1) throw new IllegalArgumentException("ARTIFACT_DECLARATION_WORKSPACE_UNRESOLVED:"+declaration.metadata().semanticId());
                var object=create(api,objects,cls,declaration.name(),declaration.metadata().semanticId()); instanceTrace(trace,"J04",declaration.metadata(),object);
                var workspace=required(objects,workspaces.getFirst().metadata().semanticId());
                text(api,object,"workspaceSemanticId",workspaces.getFirst().metadata().semanticId());
                text(api,object,"artifactTypeSemanticId",DomainProjection.artifactId(declaration.javaClass()));
                link(api,DomainProjection.relation("locatedIn","Workspace","Artifact"),workspace,object);
                DomainProjection.trace(trace,"J04",declaration.metadata(),"MLink",workspace.name()+"->"+object.name(),PRESERVE_AS_LINK);
            }
            for(var environment:snapshot.cartagoEnvironments()) {
                for(var workspace:environment.workspaces()) {
                    var roots=snapshot.workspaceDeclarations().stream().filter(d->workspace.parentSemanticId().isBlank()
                            && org.jacamo.bridge.contract.BridgeEntityId.parse(d.metadata().semanticId()).authority().equals("cartago")
                            && d.name().equals(workspace.name())).toList();
                    if(roots.size()>1) throw new IllegalArgumentException("CARTAGO_ROOT_DECLARATION_AMBIGUOUS");
                    var object=roots.isEmpty() ? create(api,objects,"Workspace",workspace.name(),workspace.metadata().semanticId())
                            : required(objects,roots.getFirst().metadata().semanticId());
                    objects.put(workspace.metadata().semanticId(),object);
                    text(api,object,"uuid",workspace.uuid()); text(api,object,"fullName",workspace.fullName()); text(api,object,"environmentSemanticId",workspace.environmentSemanticId());
                    instanceTrace(trace,"C02",workspace.metadata(),object);
                }
                for(var artifact:environment.artifacts()) {
                    String cls=schema.nativeArtifactTypeClassNames().get(artifact.artifactTypeSemanticId());
                    if(cls==null) {
                        var type=environment.artifactTypes().stream().filter(t->t.metadata().semanticId().equals(artifact.artifactTypeSemanticId())).findFirst().orElseThrow();
                        if(type.metadata().sourceKind().equals("CARTAGO_PLATFORM_ARTIFACT_TYPE") || !DomainProjection.domainArtifact(type.javaClassName())) continue;
                        throw new IllegalArgumentException("ARTIFACT_TYPE_UNRESOLVED:"+artifact.artifactTypeSemanticId());
                    }
                    var object=create(api,objects,cls,artifact.name(),artifact.metadata().semanticId());
                    text(api,object,"uuid",artifact.uuid()); text(api,object,"workspaceSemanticId",artifact.workspaceSemanticId());
                    text(api,object,"artifactTypeSemanticId",artifact.artifactTypeSemanticId()); text(api,object,"creatorAgentSemanticId",artifact.creatorAgentSemanticId());
                    instanceTrace(trace,"C04",artifact.metadata(),object);
                }
                for(var property:environment.propertySnapshots()) {
                    var artifact=objects.get(property.artifactSemanticId()); if(artifact==null) continue;
                    String name=DomainProjection.propertyName(property.name());
                    var attribute=artifact.cls().attribute(name,true);
                    if(attribute==null) continue; // A source diagnostic already records an unsupported property schema.
                    if(property.values().size()!=1) throw new IllegalArgumentException("OBS_PROPERTY_VALUE_ARITY_MISMATCH:"+property.metadata().semanticId());
                    api.setAttributeValueEx(artifact,attribute,DomainProjection.propertyValue(attribute.type().toString(),property.values().get(0)));
                    DomainProjection.trace(trace,"C09",property.metadata(),"AttributeValue",artifact.name()+"."+name,CONVERT_TO_ATTRIBUTE);
                }
            }
            // Pass 2: object relations, never a source-object-graph copy.
            for(var declaration:snapshot.groupDeployments()) {
                var parents=declaration.metadata().evidence().stream()
                    .filter(e->e.diagnostics().contains("JCM_PARENT_GROUP_DECLARATION")).toList();
                if(parents.isEmpty()) continue;
                if(parents.size()!=1) throw new IllegalArgumentException("JCM_PARENT_GROUP_AMBIGUOUS:"+declaration.metadata().semanticId());
                var parentDeclarations=snapshot.groupDeployments().stream().filter(g->g.metadata().semanticId().equals(parents.getFirst().sourceSemanticId())
                        && g.organization().equals(declaration.organization())).toList();
                if(parentDeclarations.size()!=1) throw new IllegalArgumentException("JCM_PARENT_GROUP_UNRESOLVED:"+declaration.metadata().semanticId());
                var parent=required(objects,parentDeclarations.getFirst().metadata().semanticId()); var child=required(objects,declaration.metadata().semanticId());
                link(api,DomainProjection.relation("containsSubgroup",parent.cls().name(),child.cls().name()),parent,child);
                DomainProjection.trace(trace,"M16",declaration.metadata(),"MLink",parent.name()+"->"+child.name(),PRESERVE_AS_LINK,"EXACT_JCM_PARENT_DECLARATION");
            }
            for(var deployment:snapshot.groupDeployments()) for(String schemeName:deployment.responsibleFor()) {
                var matches=snapshot.schemeDeployments().stream().filter(s->s.organization().equals(deployment.organization()) && s.name().equals(schemeName)).toList();
                if(matches.size()!=1) throw new IllegalArgumentException("GROUP_RESPONSIBLE_SCHEME_UNRESOLVED:"+deployment.metadata().semanticId()+":"+schemeName);
                var group=required(objects,deployment.metadata().semanticId()); var scheme=required(objects,matches.getFirst().metadata().semanticId());
                link(api,DomainProjection.relation("responsibleFor","Group","Scheme"),group,scheme);
                DomainProjection.trace(trace,"J06",deployment.metadata(),"MLink",group.name()+"->"+scheme.name(),PRESERVE_AS_LINK);
            }
            for(var deployment:snapshot.groupDeployments()) {
                var group=required(objects,deployment.metadata().semanticId());
                var owner=snapshot.organizationDeployments().stream().filter(o->o.name().equals(deployment.organization())).findFirst().orElseThrow();
                var organisation=required(objects,owner.metadata().semanticId());
                link(api,DomainProjection.relation("containsGroup",organisation.cls().name(),group.cls().name()),organisation,group);
                DomainProjection.trace(trace,"J06",deployment.metadata(),"MLink",organisation.name()+"->"+group.name(),PRESERVE_AS_LINK);
            }
            for(var tuple:snapshot.rawRoleTuples()) {
                var deployments=snapshot.groupDeployments().stream().filter(g->g.organization().equals(tuple.organization()) && g.name().equals(tuple.group())).toList();
                if(deployments.size()!=1) throw new IllegalArgumentException("PLAYER_GROUP_CONTEXT_UNRESOLVED:"+tuple.metadata().semanticId());
                var group=required(objects,deployments.get(0).metadata().semanticId());
                String os=deploymentOs.get(tuple.organization());
                var org=snapshot.moiseOrganizations().stream().filter(o->o.metadata().semanticId().equals(os)).findFirst().orElseThrow();
                var roles=org.structuralSpecification().roles().stream().filter(r->r.roleId().equals(tuple.role())).toList();
                if(roles.size()!=1) throw new IllegalArgumentException("PLAYER_ROLE_UNRESOLVED:"+tuple.metadata().semanticId());
                String roleId=roles.get(0).metadata().semanticId();
                var specification=org.structuralSpecification().groups().stream().filter(g->g.metadata().semanticId().equals(groupTypes.get(deployments.get(0).metadata().semanticId()))).findFirst().orElseThrow();
                if(!specification.roleSemanticIds().contains(roleId)) throw new IllegalArgumentException("PLAYER_ROLE_NOT_IN_GROUP:"+tuple.metadata().semanticId());
                var agent=required(objects,tuple.agentDeclarationId());
                var relation=MoiseDomainProjection.roleAssociation(schema.model(),os,specification.metadata().semanticId(),roleId);
                var roleObject=MoiseDomainProjection.linkObject(api,objects,relation,agent,group);
                instanceTrace(trace,"X04",tuple.metadata(),roleObject);
                trace.add(new org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog().require("X04"),
                    TracePhase.INSTANCE_MATERIALIZATION,tuple.metadata(),"MLink",relation.name()+":"+agent.name()+"->"+group.name(),
                    List.of("DISPOSITION=PRESERVE_AS_LINK","SEMANTIC_KIND=player-role-assignment",
                        "CONTEXT="+MoiseDomainProjection.roleRelationId(os,specification.metadata().semanticId(),roleId),
                        "GROUP_INSTANCE="+deployments.get(0).metadata().semanticId()));
            }
            for(var environment:snapshot.cartagoEnvironments()) for(var artifact:environment.artifacts()) {
                var object=objects.get(artifact.metadata().semanticId()); if(object==null) continue;
                var workspace=required(objects,artifact.workspaceSemanticId());
                link(api,DomainProjection.relation("locatedIn","Workspace",object.cls().name()),workspace,object);
            }
            for(var binding:snapshot.exactBindings()) if(binding.ruleId().equals("X09"))
                for(String first:binding.sourceIds()) for(String second:binding.targetIds()) objects.put(second,required(objects,first));
            for(var binding:snapshot.exactBindings()) {
                switch(binding.ruleId()) {
                    case "X04" -> DomainProjection.trace(trace,"X04",binding.metadata(),"SemanticEvidence",binding.metadata().semanticId(),PRESERVE_AS_LINK,"EXACT_CONTEXTUAL_PLAYER_LINKS");
                    case "X05","X06" -> {
                        for(String first:binding.sourceIds()) for(String second:binding.targetIds()) {
                            var one=required(objects,first); var two=required(objects,second);
                            link(api,DomainProjection.relation(binding.ruleId().equals("X05") ? "memberOf" : "focuses","Agent",two.cls().name()),one,two);
                        }
                        DomainProjection.trace(trace,binding.ruleId(),binding.metadata(),"MLink",binding.metadata().semanticId(),PRESERVE_AS_LINK);
                    }
                    case "X09" -> {
                        DomainProjection.trace(trace,"X09",binding.metadata(),"MObjectAlias",binding.metadata().semanticId(),FLATTEN);
                    }
                    case "X07" -> DomainProjection.trace(trace,"X07",binding.metadata(),"SemanticEvidence",binding.metadata().semanticId(),FLATTEN,"EXACT_GOAL_SOURCE_BINDING_RETAINED");
                    default -> DomainProjection.trace(trace,binding.ruleId(),binding.metadata(),"SemanticEvidence",binding.metadata().semanticId(),IGNORE_NOT_NEEDED);
                }
            }
            StringWriter validation=new StringWriter(); var output=new PrintWriter(validation,true);
            for(var environment:snapshot.cartagoEnvironments()) for(var focus:environment.focuses()) {
                var agent=objects.get(focus.agentSemanticId()); var artifact=objects.get(focus.artifactSemanticId());
                if(agent==null || artifact==null) {
                    DomainProjection.trace(trace,"C20",focus.metadata(),"SemanticEvidence",focus.metadata().semanticId(),IGNORE_NOT_NEEDED,
                            "FOCUS_ENDPOINT_INTERNAL_OR_UNBOUND");
                    continue;
                }
                if(focus.focused()) link(api,DomainProjection.relation("focuses","Agent","Artifact"),agent,artifact);
                DomainProjection.trace(trace,"C20",focus.metadata(),"MLink",agent.name()+"->"+artifact.name(),PRESERVE_AS_LINK);
            }
            boolean structure=system.state().checkStructure(output);
            // State violations are verification results, not a reason to fabricate or discard source state.
            boolean invariants=system.state().check(output,false,true,true,List.of());
            if(!structure) throw new IllegalStateException("NATIVE_USE_STATE_STRUCTURE_INVALID:"+validation);
            return new Result(system,objects,trace.index(),structure,invariants,validation.toString(),schema.profile());
        } catch(UseApiException error) { throw new IllegalStateException("NATIVE_USE_STATE_BUILD_FAILED:"+error.getMessage(),error); }
    }
    private static MObject create(UseSystemApi api,Map<String,MObject> objects,String cls,String human,String id) throws UseApiException {
        if(objects.containsKey(id)) throw new IllegalArgumentException("RUNTIME_IDENTITY_DUPLICATED:"+id);
        String name=objectName(cls,human,id);
        if(api.getSystem().state().objectByName(name)!=null) name=name+"_"+DomainProjection.hash(id);
        var object=api.createObject(cls,name); objects.put(id,object);
        text(api,object,"semanticId",id);
        if(object.cls().attribute("name",true)!=null) text(api,object,"name",human);
        return object;
    }
    private static MObject required(Map<String,MObject> objects,String id) {
        var object=objects.get(id); if(object==null) throw new IllegalArgumentException("DOMAIN_LINK_ENDPOINT_UNRESOLVED:"+id); return object;
    }
    private static void text(UseSystemApi api,MObject object,String name,String text) throws UseApiException {
        api.setAttributeValueEx(object,object.cls().attribute(name,true),new StringValue(text==null ? "" : text));
    }
    private static void link(UseSystemApi api,String association,MObject one,MObject two) throws UseApiException {
        var target=api.getSystem().model().getAssociation(association);
        if(target==null) throw new IllegalArgumentException("DOMAIN_ASSOCIATION_UNRESOLVED:"+association);
        MObject[] pair={one,two}; if(!api.getSystem().state().hasLinkBetweenObjects(target,pair)) api.createLinkEx(target,pair);
    }
    private static void instanceTrace(CodeGroundedTraceCollector trace,String rule,SemanticMetadata source,MObject object) {
        trace.add(new org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog().require(rule),TracePhase.INSTANCE_MATERIALIZATION,
            source,"MObject",object.name(),List.of("SEMANTIC_KIND="+DomainProjection.kind(object.cls())));
        for(var attribute:object.cls().allAttributes()) trace.add(new org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog().require(rule),
            TracePhase.INSTANCE_MATERIALIZATION,source,"MValue","value:"+object.name()+"."+attribute.name(),List.of("DISPOSITION=CONVERT_TO_ATTRIBUTE"));
    }
    public static String objectName(String kind,String human,String semanticId) { return DomainProjection.symbol(human==null || human.isBlank() ? kind : human); }
    public record Result(MSystem system,Map<String,MObject> semanticObjectIndex,CodeGroundedTraceIndex trace,boolean structureValid,
        boolean invariantsValid,String validationOutput,NativeProjectionProfile profile) {
        public Result { semanticObjectIndex=Collections.unmodifiableMap(new LinkedHashMap<>(semanticObjectIndex)); }
    }
}
