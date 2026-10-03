package org.jacamo.bridge.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import jason.mas2j.AgentParameters;
import jason.mas2j.ClassParameters;
import jacamo.project.JaCaMoAgentParameters;
import jacamo.project.JaCaMoGroupParameters;
import jacamo.project.JaCaMoOrgParameters;
import jacamo.project.JaCaMoProject;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.ContractPayloads;
import org.jacamo.bridge.contract.ModelFact;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.RelationCardinality;
import org.jacamo.bridge.contract.UnresolvedFact;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentProgramSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.OrganizationSemantic;
import org.jacamo.bridge.contract.semantic.JcmSemanticContract.AgentDeclarationSemantic;
import org.jacamo.bridge.contract.semantic.JcmSemanticContract.AgentFocusTupleSemantic;
import org.jacamo.bridge.contract.semantic.JcmSemanticContract.AgentRoleTupleSemantic;
import org.jacamo.bridge.contract.semantic.JcmSemanticContract.ArtifactDeclarationSemantic;
import org.jacamo.bridge.contract.semantic.JcmSemanticContract.GroupDeploymentSemantic;
import org.jacamo.bridge.contract.semantic.JcmSemanticContract.InstitutionDeploymentSemantic;
import org.jacamo.bridge.contract.semantic.JcmSemanticContract.OrganizationDeploymentSemantic;
import org.jacamo.bridge.contract.semantic.JcmSemanticContract.ProjectSemantic;
import org.jacamo.bridge.contract.semantic.JcmSemanticContract.SchemeDeploymentSemantic;
import org.jacamo.bridge.contract.semantic.JcmSemanticContract.WorkspaceDeclarationSemantic;

/** Copies the merged official JaCaMoProject into immutable neutral and typed semantic facts. */
public final class OfficialProjectAdapter {
    private final OfficialJasonAdapter jason=new OfficialJasonAdapter();
    private final OfficialMoiseAdapter moise=new OfficialMoiseAdapter();

    public ModelSnapshot adapt(JaCaMoProject project,Path jcm)throws Exception{
        Path exactJcm=jcm.toAbsolutePath().normalize();Path root=exactJcm.getParent();String projectKey=project.getSocName();
        var sourceEvidence=AdapterEvidence.file("jacamo-project-parser",root,exactJcm,"JaCaMoProjectParser merged official project");
        String projectId="jcm:project:"+projectKey+":"+sourceEvidence.sourceDigest();
        var projectMetadata=SemanticEvidence.metadata(projectId,"JACAMO_PROJECT","jacamo.project.JaCaMoProject",
                EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,sourceEvidence,1,1,List.of());
        var typedProject=new ProjectSemantic(projectMetadata,projectKey,sourceEvidence.sourceDigest());

        var sources=new ArrayList<ModelFact>();
        sources.add(new ModelFact("jcm-source",id("jacamo","project","source",projectKey,exactJcm.getFileName().toString()),
                Map.of("uri",sourceEvidence.sourceUri(),"digest",sourceEvidence.sourceDigest(),"kind","JCM"),Map.of(),
                CapabilityStatus.COMPLETE,List.of(sourceEvidence)));
        var agents=new ArrayList<ModelFact>();var workspaces=new ArrayList<ModelFact>();var artifacts=new ArrayList<ModelFact>();
        var organisations=new ArrayList<ModelFact>();var rawRelations=new ArrayList<ModelFact>();var unresolved=new ArrayList<UnresolvedFact>();
        var typedAgents=new ArrayList<AgentDeclarationSemantic>();var typedWorkspaces=new ArrayList<WorkspaceDeclarationSemantic>();
        var typedArtifacts=new ArrayList<ArtifactDeclarationSemantic>();var typedOrganisations=new ArrayList<OrganizationDeploymentSemantic>();
        var typedGroups=new ArrayList<GroupDeploymentSemantic>();var typedSchemes=new ArrayList<SchemeDeploymentSemantic>();
        var typedInstitutions=new ArrayList<InstitutionDeploymentSemantic>();var rawRoles=new ArrayList<AgentRoleTupleSemantic>();
        var rawFocus=new ArrayList<AgentFocusTupleSemantic>();var programs=new ArrayList<AgentProgramSemantic>();
        var typedMoiseOrganizations=new ArrayList<OrganizationSemantic>();
        var moiseSpecificationsByIdentity=new java.util.LinkedHashMap<String,OrganizationSemantic>();

        for(AgentParameters base:project.getAgents().stream().sorted(Comparator.comparing(AgentParameters::getAgName)).toList()){
            JaCaMoAgentParameters agent=(JaCaMoAgentParameters)base;
            var agentId=id("jacamo","agent","declaration",projectKey,agent.getAgName());
            agents.add(new ModelFact("agent-declaration",agentId,Map.of("instances",Integer.toString(agent.getNbInstances()),
                    "source",String.valueOf(agent.getSource())),Map.of(),CapabilityStatus.COMPLETE,List.of(sourceEvidence)));
            var metadata=SemanticEvidence.metadata(agentId.canonical(),"JACAMO_AGENT_DECLARATION","jacamo.project.JaCaMoAgentParameters",
                    EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,sourceEvidence,1,1,List.of());
            typedAgents.add(new AgentDeclarationSemantic(metadata,agent.getAgName(),String.valueOf(agent.getSource()),
                    agent.getOptions(),agent.getAgArchClasses(),className(agent.agClass),className(agent.getBBClass()),
                    agent.getHost(),agent.getNbInstances()));

            Path source=resolveAgentSource(project,root,agent);
            if(Files.exists(source)){
                OfficialJasonAdapter.Result parsed=jason.adapt(root,source,projectKey,agent.getAgName());
                agents.addAll(parsed.facts());programs.add(parsed.program());
            }else unresolved.add(new UnresolvedFact("agent-source",agent.getAgName(),CapabilityStatus.UNAVAILABLE,
                    "official source path is unavailable: "+source,List.of(sourceEvidence)));

            int ordinal=0;
            for(String[] tuple:agent.getFocus()){
                String artifact=safe(tuple,0),workspace=safe(tuple,1),namespace=safe(tuple,2);
                var relation=id("jacamo","cross","focus-tuple",projectKey,agent.getAgName()+":"+ordinal);
                rawRelations.add(new ModelFact("focus-tuple",relation,Map.of("agent",agentId.canonical(),"artifact",artifact,
                        "workspace",workspace,"namespace",namespace,"ordinal",Integer.toString(ordinal)),Map.of(),
                        CapabilityStatus.COMPLETE,List.of(sourceEvidence)));
                var tupleMetadata=SemanticEvidence.metadata(relation.canonical(),"JACAMO_AGENT_FOCUS_TUPLE","java.lang.String[]",
                        EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,sourceEvidence,1,1,List.of());
                rawFocus.add(new AgentFocusTupleSemantic(tupleMetadata,agentId.canonical(),artifact,workspace,namespace,ordinal++));
            }
            ordinal=0;
            for(String[] tuple:agent.getRoles()){
                String organization=safe(tuple,0),group=safe(tuple,1),role=safe(tuple,2);
                CapabilityStatus status=organization.isBlank()?CapabilityStatus.PARTIAL:CapabilityStatus.COMPLETE;
                List<String> diagnostics=organization.isBlank()?List.of("ORGANIZATION_UNRESOLVED"):List.of();
                var relation=id("jacamo","cross","role-tuple",projectKey,agent.getAgName()+":"+ordinal);
                rawRelations.add(new ModelFact("role-tuple",relation,Map.of("agent",agentId.canonical(),"organization",organization,
                        "group",group,"role",role,"ordinal",Integer.toString(ordinal)),Map.of(),status,List.of(sourceEvidence)));
                var tupleMetadata=SemanticEvidence.metadata(relation.canonical(),"JACAMO_AGENT_ROLE_TUPLE","java.lang.String[]",
                        EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.EXACT,status,sourceEvidence,1,1,diagnostics);
                rawRoles.add(new AgentRoleTupleSemantic(tupleMetadata,agentId.canonical(),organization,group,role,ordinal));
                if(status!=CapabilityStatus.COMPLETE)unresolved.add(new UnresolvedFact("player-role",relation.canonical(),status,
                        "organisation was not resolved by official parser",List.of(sourceEvidence)));
                ordinal++;
            }
        }

        String rootWorkspace=cartago.CartagoEnvironment.ROOT_WSP_DEFAULT_NAME;
        var rootWorkspaceId=id("cartago","environment","workspace-declaration",projectKey,rootWorkspace);
        workspaces.add(new ModelFact("workspace-declaration",rootWorkspaceId,Map.of("implicitPlatformRoot","true"),Map.of(),
                CapabilityStatus.COMPLETE,List.of(sourceEvidence)));
        typedWorkspaces.add(workspace(rootWorkspaceId,rootWorkspace,"",false,sourceEvidence));
        for(var workspace:project.getWorkspaces().stream().sorted(Comparator.comparing(value->value.getName())).toList()){
            var workspaceId=workspace.getName().equals(rootWorkspace)?rootWorkspaceId:
                    id("jacamo","environment","workspace-declaration",projectKey,workspace.getName());
            if(!workspace.getName().equals(rootWorkspace)){
                workspaces.add(new ModelFact("workspace-declaration",workspaceId,Map.of(),Map.of(),CapabilityStatus.COMPLETE,List.of(sourceEvidence)));
                typedWorkspaces.add(workspace(workspaceId,workspace.getName(),workspace.getHost(),workspace.hasDebug(),sourceEvidence));
            }
            workspace.getArtifacts().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
                ClassParameters parameters=entry.getValue();
                var artifactId=id("jacamo","environment","artifact-declaration",projectKey,workspace.getName()+"/"+entry.getKey());
                artifacts.add(new ModelFact("artifact-declaration",artifactId,Map.of("configuredClass",parameters.getClassName()),
                        Map.of("workspace",List.of(workspaceId)),CapabilityStatus.COMPLETE,List.of(sourceEvidence)));
                var metadata=SemanticEvidence.metadata(artifactId.canonical(),"JACAMO_ARTIFACT_DECLARATION","jason.mas2j.ClassParameters",
                        EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,sourceEvidence,1,1,List.of());
                typedArtifacts.add(new ArtifactDeclarationSemantic(metadata,entry.getKey(),workspace.getName(),parameters.getClassName(),
                        List.copyOf(parameters.getParameters())));
            });
        }

        var roleCards=new ArrayList<RelationCardinality>();var subgroupCards=new ArrayList<RelationCardinality>();
        for(JaCaMoOrgParameters org:project.getOrgs().stream().sorted(Comparator.comparing(JaCaMoOrgParameters::getName)).toList()){
            var orgId=id("jacamo","organisation","organization-deployment",projectKey,org.getName());
            var orgMetadata=SemanticEvidence.metadata(orgId.canonical(),"JACAMO_ORGANIZATION_DEPLOYMENT","jacamo.project.JaCaMoOrgParameters",
                    EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,sourceEvidence,1,1,List.of());
            typedOrganisations.add(new OrganizationDeploymentSemantic(orgMetadata,org.getName(),text(org.getParameter("source")),
                    text(org.getInstitution()),text(org.getDebugConf())));
            for(JaCaMoGroupParameters group:org.getGroups().stream().sorted(Comparator.comparing(JaCaMoGroupParameters::getName)).toList())
                collectGroup(projectKey,org.getName(),group,sourceEvidence,typedGroups);
            org.getSchemes().stream().sorted(Comparator.comparing(value->value.getName())).forEach(scheme->{
                var schemeId=id("jacamo","organisation","scheme-deployment",projectKey,org.getName()+"/"+scheme.getName());
                var schemeMetadata=SemanticEvidence.metadata(schemeId.canonical(),"JACAMO_SCHEME_DEPLOYMENT","jacamo.project.JaCaMoSchemeParameters",
                        EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,sourceEvidence,1,1,List.of());
                typedSchemes.add(new SchemeDeploymentSemantic(schemeMetadata,org.getName(),scheme.getName(),text(scheme.getType())));
            });
            String orgSource=org.getParameter("source");
            if(orgSource==null){unresolved.add(new UnresolvedFact("organisation-source",org.getName(),CapabilityStatus.UNAVAILABLE,
                    "official project has no OS source",List.of(sourceEvidence)));continue;}
            Path path=resolveProjectPath(root,orgSource);if(!Files.exists(path))path=root.resolve("src/org").resolve(orgSource).normalize();
            if(!Files.exists(path)){unresolved.add(new UnresolvedFact("organisation-source",org.getName(),CapabilityStatus.UNAVAILABLE,
                    "OS source does not exist: "+orgSource,List.of(sourceEvidence)));continue;}
            var result=moise.load(root,path,projectKey);
            var previous=moiseSpecificationsByIdentity.putIfAbsent(result.organization().metadata().semanticId(),result.organization());
            if(previous!=null&&!previous.equals(result.organization()))
                throw new IllegalArgumentException("MOISE_OS_SPECIFICATION_ID_CONFLICT:"+result.organization().metadata().semanticId());
            if(previous==null){
                organisations.addAll(result.facts()); typedMoiseOrganizations.add(result.organization());
                roleCards.addAll(result.groupRoleCardinalities()); subgroupCards.addAll(result.parentSubGroupCardinalities());
            }
        }
        project.getInstitutions().stream().sorted(Comparator.comparing(value->value.getName())).forEach(institution->{
            var institutionId=id("jacamo","organisation","institution-deployment",projectKey,institution.getName());
            var metadata=SemanticEvidence.metadata(institutionId.canonical(),"JACAMO_INSTITUTION_DEPLOYMENT","jacamo.project.JaCaMoInstParameters",
                    EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.SUPPORTED_SUBSET,CapabilityStatus.PARTIAL,sourceEvidence,1,1,
                    List.of("OPAQUE_RULE_ENGINE_NOT_SERIALIZED"));
            typedInstitutions.add(new InstitutionDeploymentSemantic(metadata,institution.getName(),institution.getWorkspaces(),Map.of()));
        });

        var imports=new OfficialImportGraphCollector().collect(exactJcm,root);
        Comparator<ModelFact> order=Comparator.comparing(fact->fact.id().canonical());
        for(var list:List.of(sources,agents,workspaces,artifacts,organisations,rawRelations))list.sort(order);
        typedAgents.sort(Comparator.comparing(value->value.metadata().semanticId()));
        typedWorkspaces.sort(Comparator.comparing(value->value.metadata().semanticId()));
        typedArtifacts.sort(Comparator.comparing(value->value.metadata().semanticId()));
        typedOrganisations.sort(Comparator.comparing(value->value.metadata().semanticId()));
        typedGroups.sort(Comparator.comparing(value->value.metadata().semanticId()));
        typedSchemes.sort(Comparator.comparing(value->value.metadata().semanticId()));
        typedInstitutions.sort(Comparator.comparing(value->value.metadata().semanticId()));
        rawRoles.sort(Comparator.comparing(value->value.metadata().semanticId()));rawFocus.sort(Comparator.comparing(value->value.metadata().semanticId()));
        programs.sort(Comparator.comparing(value->value.metadata().semanticId()));
        typedMoiseOrganizations.sort(Comparator.comparing(value->value.metadata().semanticId()));
        var semantic=new JacamoSemanticSnapshot(JacamoSemanticSnapshot.CURRENT_VERSION,typedProject,typedAgents,typedWorkspaces,
                typedArtifacts,typedOrganisations,typedGroups,typedSchemes,typedInstitutions,rawRoles,rawFocus,imports,programs,
                List.of(),typedMoiseOrganizations,List.of(),List.of());
        var provenance=Map.of("authority","official-jacamo-objects","frozenV2","projection-downstream",
                "semanticContract",JacamoSemanticSnapshot.CURRENT_VERSION);
        var pending=new ModelSnapshot("pending",sources,agents,workspaces,artifacts,organisations,roleCards,subgroupCards,
                rawRelations,unresolved,provenance,semantic);
        String revision=AdapterEvidence.digest(CanonicalJson.encode(ContractPayloads.model(pending)));
        return new ModelSnapshot(revision,sources,agents,workspaces,artifacts,organisations,roleCards,subgroupCards,
                rawRelations,unresolved,provenance,semantic);
    }

    private WorkspaceDeclarationSemantic workspace(BridgeEntityId id,String name,String host,boolean debug,
                                                    org.jacamo.bridge.contract.Evidence evidence){
        var metadata=SemanticEvidence.metadata(id.canonical(),"JACAMO_WORKSPACE_DECLARATION","jacamo.project.JaCaMoWorkspaceParameters",
                EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,evidence,1,1,List.of());
        return new WorkspaceDeclarationSemantic(metadata,name,text(host),debug);
    }
    private void collectGroup(String project,String organization,JaCaMoGroupParameters group,
                              org.jacamo.bridge.contract.Evidence evidence,List<GroupDeploymentSemantic> result){
        var groupId=id("jacamo","organisation","group-deployment",project,organization+"/"+group.getName());
        var metadata=SemanticEvidence.metadata(groupId.canonical(),"JACAMO_GROUP_DEPLOYMENT","jacamo.project.JaCaMoGroupParameters",
                EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,evidence,1,1,List.of());
        result.add(new GroupDeploymentSemantic(metadata,organization,group.getName(),text(group.getType()),group.getResponsibleFor()));
        group.getSubGroups().stream().sorted(Comparator.comparing(JaCaMoGroupParameters::getName))
                .forEach(child->collectGroup(project,organization,child,evidence,result));
    }
    private Path resolveAgentSource(JaCaMoProject project,Path root,JaCaMoAgentParameters agent){
        String reference=agent.getSource().isAbsolute()?agent.getSource().toString():agent.getSource().getSchemeSpecificPart();
        if("file".equals(agent.getSource().getScheme())&&agent.getSource().isOpaque())reference=agent.getSource().getSchemeSpecificPart();
        String officialPath=project.getSourcePaths().fixPath(reference);java.net.URI uri=officialPath.startsWith("file:")?java.net.URI.create(officialPath):null;
        Path source=uri==null?Path.of(officialPath):uri.isOpaque()?Path.of(uri.getSchemeSpecificPart()):Path.of(uri);
        return source.isAbsolute()?source.normalize():root.resolve(source).normalize();
    }
    private String className(ClassParameters value){return value==null?"":text(value.getClassName());}
    private String safe(String[] tuple,int index){return tuple!=null&&index<tuple.length?text(tuple[index]):"";}
    private String text(String value){return value==null?"":value;}
    private BridgeEntityId id(String authority,String dimension,String kind,String scope,String local){return new BridgeEntityId(authority,dimension,kind,scope,local,"model");}
    private Path resolveProjectPath(Path root,String source){
        try{java.net.URI uri=java.net.URI.create(source);if("file".equalsIgnoreCase(uri.getScheme())){Path parsed=uri.isOpaque()?Path.of(uri.getSchemeSpecificPart()):Path.of(uri);return parsed.isAbsolute()?parsed.normalize():root.resolve(parsed).normalize();}}
        catch(IllegalArgumentException ignored){ }
        Path parsed=Path.of(source);return parsed.isAbsolute()?parsed.normalize():root.resolve(parsed).normalize();
    }
}
